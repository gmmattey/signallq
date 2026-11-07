// Run against a local Desktop process launched with
// WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9222.
// This is a developer smoke, never enabled by the shipped app.
const port = process.env.SIGNALLQ_CDP_PORT || '9222'
const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json()
const page = pages.find(item => item.type === 'page' && item.url.startsWith('http://tauri.localhost'))
if (!page) throw new Error('Janela SignallQ não encontrada no WebView2')
const socket = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})
let sequence = 0
const waiting = new Map()
socket.addEventListener('message', event => {
  const packet = JSON.parse(event.data)
  if (!packet.id || !waiting.has(packet.id)) return
  const { resolve, reject, timer } = waiting.get(packet.id)
  clearTimeout(timer)
  waiting.delete(packet.id)
  if (packet.error) reject(new Error(packet.error.message))
  else resolve(packet.result)
})
function send(method, params) {
  const id = ++sequence
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { waiting.delete(id); reject(new Error(`${method}: timeout`)) }, 40000)
    waiting.set(id, { resolve, reject, timer })
    socket.send(JSON.stringify({ id, method, params }))
  })
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text)
  return result.result.value
}
async function invoke(name, args = {}) {
  return evaluate(`window.__TAURI_INTERNALS__.invoke(${JSON.stringify(name)}, ${JSON.stringify(args)})`)
}
try {
  const pageState = await evaluate('({ title: document.title, body: document.body.innerText.slice(0, 300), native: Boolean(window.__TAURI_INTERNALS__) })')
  if (!pageState.native || pageState.title !== 'SignallQ') throw new Error('UI nativa não carregou')
  const network = await invoke('network_snapshot')
  const history = await invoke('list_history')
  const monitor = await invoke('monitor_status')
  const dns = await invoke('run_tool', { kind: 'dns', target: 'localhost' })
  const invalidTarget = await evaluate('window.__TAURI_INTERNALS__.invoke("run_tool", {kind:"ping",target:"-n"}).then(()=>"accepted",()=>"rejected")')
  if (!Array.isArray(history) || invalidTarget !== 'rejected') throw new Error('Contrato do histórico/alvo inválido')
  console.log(JSON.stringify({
    ui: pageState,
    network: { type: network.connectionType, interface: network.interfaceName, ip: network.localIp, ssid: network.ssid, gateway: network.gateway, dns: network.dnsServers, limitation: network.capabilityNote },
    historyCount: history.length,
    monitor: { enabled: monitor.enabled, intervalMinutes: monitor.intervalMinutes, latest: monitor.latest?.status },
    dns: { status: dns.status, output: dns.output, durationMs: dns.durationMs },
    invalidTarget,
  }, null, 2))
  if (process.argv.includes('--live')) {
    const ping = await invoke('run_tool', { kind: 'ping', target: '127.0.0.1' })
    const gamer = await invoke('gamer_probe')
    const executionId = crypto.randomUUID()
    await invoke('start_speedtest', { executionId })
    let measurement
    for (let attempt = 0; attempt < 50; attempt++) {
      await new Promise(resolve => setTimeout(resolve, 2000))
      measurement = (await invoke('list_history')).find(item => item.executionId === executionId)
      if (measurement) break
    }
    if (!measurement) throw new Error('Medição não concluiu nem persistiu em 100 segundos')
    let sample
    if (!monitor.enabled) {
      try {
        await invoke('monitor_start')
        for (let attempt = 0; attempt < 25; attempt++) {
          await new Promise(resolve => setTimeout(resolve, 1000))
          sample = (await invoke('monitor_status')).latest
          if (sample && sample.capturedAtMs >= Date.now() - 30000) break
        }
        if (!sample) throw new Error('Monitor não gravou a primeira sonda')
      } finally {
        await invoke('monitor_stop')
      }
    }
    console.log(JSON.stringify({ live: {
      ping: { status: ping.status, durationMs: ping.durationMs },
      gamer: { status: gamer.status, medianMs: gamer.medianMs, lossPercent: gamer.lossPercent },
      speedtest: { executionId, status: measurement.status, downloadMbps: measurement.downloadMbps, uploadMbps: measurement.uploadMbps, latencyMs: measurement.latencyMs, limitation: measurement.limitation },
      monitor: sample && { status: sample.status, latencyMs: sample.latencyMs, dnsMs: sample.dnsMs },
    } }, null, 2))
  }
  if (process.argv.includes('--monitor-hidden')) {
    if (monitor.enabled) throw new Error('Monitor já estava ligado; teste exige estado inicial desligado')
    let sample
    try {
      await invoke('monitor_start')
      const startedAt = Date.now()
      for (let attempt = 0; attempt < 25; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 1000))
        sample = (await invoke('monitor_status')).latest
        if (sample?.capturedAtMs >= startedAt) break
      }
      if (!sample || sample.capturedAtMs < startedAt) throw new Error('Monitor não gravou com a janela oculta')
      console.log(JSON.stringify({ hiddenMonitor: { status: sample.status, capturedAtMs: sample.capturedAtMs } }, null, 2))
    } finally {
      await invoke('monitor_stop')
    }
  }
  if (process.argv.includes('--export')) {
    const result = await invoke('export_history_csv', { includeNetworkFields: true })
    console.log(JSON.stringify({ export: result }, null, 2))
  }
  if (process.argv.includes('--tools')) {
    const route = await invoke('run_tool', { kind: 'route', target: '127.0.0.1' })
    const cancelled = await evaluate(`(async () => {
      const work = window.__TAURI_INTERNALS__.invoke('run_tool', {kind:'route', target:'1.1.1.1'});
      setTimeout(() => void window.__TAURI_INTERNALS__.invoke('cancel_tool'), 100);
      return await work;
    })()`)
    if (route.status !== 'COMPLETED' || cancelled.status !== 'CANCELLED') throw new Error('Rota ou cancelamento falhou')
    console.log(JSON.stringify({ tools: { route: route.status, cancelled: cancelled.status } }, null, 2))
  }
  if (process.argv.includes('--cancel-speed')) {
    const executionId = crypto.randomUUID()
    await invoke('start_speedtest', { executionId })
    const cancellationAccepted = await invoke('cancel_speedtest', { executionId })
    await new Promise(resolve => setTimeout(resolve, 3000))
    const persisted = (await invoke('list_history')).some(item => item.executionId === executionId)
    if (!cancellationAccepted || persisted) throw new Error('Cancelamento de medição falhou')
    console.log(JSON.stringify({ cancelledSpeedtest: { cancellationAccepted, persisted } }, null, 2))
  }
} finally {
  socket.close()
}
