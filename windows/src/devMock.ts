// Simulação do backend Tauri, só para desenvolvimento e validação no navegador.
// Ativada com VITE_MOCK=1 (veja main.tsx); não entra no build de produção.
import type { GamerResult, Measurement, MonitorResult, MonitorStatus, NetworkSnapshot, ToolResult } from './native'

type Handler = (payload: unknown) => void
const listeners = new Map<string, Map<number, Handler>>()
const callbacks = new Map<number, Handler>()
let nextId = 1
const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const now = () => Date.now()

function emit(event: string, payload: unknown) {
  listeners.get(event)?.forEach(handler => handler({ event, id: 0, payload }))
}

const net: NetworkSnapshot = { interfaceName: 'Wi-Fi', connectionType: 'Wi-Fi', localIp: '192.168.0.24', gateway: '192.168.0.1', dnsServers: ['187.1.10.1', '187.1.10.2'], ssid: 'Casa 5G', signalPercent: 71, channel: '36', networkId: 'net-casa', capabilityNote: 'Simulação de desenvolvimento: dados fictícios.' }
const phase = (name: string, status = 'COMPLETE') => ({ name, method: name === 'upload' ? 'HTTP POST' : 'HTTP GET', endpoint: 'speed.cloudflare.com', startedAtMs: now(), durationMs: 18000, attempts: 1, validSamples: 12, bytesTransferred: 52_000_000, status })
const record = (daysAgo: number, dl: number | null, ul: number | null, lat: number | null, status: Measurement['status'] = 'COMPLETE'): Measurement => ({
  schemaVersion: 2, executionId: crypto.randomUUID(), startedAtMs: now() - daysAgo * 864e5, capturedAtMs: now() - daysAgo * 864e5 + 40000, status,
  downloadMbps: dl, uploadMbps: ul, latencyMs: lat, jitterMs: lat == null ? null : 3.2, packetLossPercent: lat == null ? null : 0, packetLossSource: lat == null ? null : 'Estimativa por sondagem HTTP', bufferbloatMs: lat == null ? null : 22,
  limitation: status === 'PARTIAL' ? 'O upload expirou e não foi contabilizado.' : null, phases: [phase('latency'), phase('download'), phase('upload', status === 'PARTIAL' ? 'TIMEOUT' : 'COMPLETE')],
  networkId: 'net-casa', ssid: 'Casa 5G', localIp: '192.168.0.24',
})
let history: Measurement[] = [record(0.1, 312.4, 118.2, 14.1), record(0.5, 298.0, 112.5, 16.0), record(1, 176.3, 64.0, 31.2), record(2, 305.2, 110.0, 15.1), record(3, null, null, 18.4, 'PARTIAL'), record(5, 280.1, 104.8, 17.0)]
let monitorHistory: MonitorResult[] = []
let monitor: MonitorStatus = { enabled: false, intervalMinutes: 15, latest: null }
let monitorTimer: number | undefined
let activeSpeed: { id: string; cancelled: boolean } | null = null
let activeTool: { cancel: () => void } | null = null

const failing = new Set<string>()
const sample = (): MonitorResult => ({ id: crypto.randomUUID(), capturedAtMs: now(), source: 'monitor', status: 'COMPLETE', method: 'HTTP HEAD', endpoint: 'cloudflare.com', samples: [], latencyMs: 20 + Math.round(Math.random() * 15), dnsMs: 8 + Math.round(Math.random() * 6), dnsStatus: 'COMPLETE', downloadMbps: null, uploadMbps: null, limitation: 'Sonda leve; não mede download nem upload.' })

async function runSpeedtest(id: string) {
  activeSpeed = { id, cancelled: false }
  const steps: Array<[string, number]> = [['latency', 5], ['download', 33], ['upload', 66]]
  for (const [name, percent] of steps) {
    emit('speedtest:progress', { executionId: id, phase: name, percent })
    for (let i = 0; i < 8; i++) { await wait(300); if (activeSpeed?.cancelled) break }
    if (activeSpeed?.cancelled) break
  }
  const cancelled = activeSpeed?.cancelled ?? false
  activeSpeed = null
  if (failing.has('speedtest-fail')) { emit('speedtest:failed', { executionId: id, message: 'Falha simulada: sem resposta do servidor de medição.' }); return }
  const result: Measurement = cancelled
    ? { ...record(0, null, null, null, 'CANCELLED'), executionId: id, phases: [] }
    : { ...record(0, 321.7, 121.3, 13.4), executionId: id }
  if (!cancelled) history = [result, ...history]
  emit('speedtest:finished', result)
}

async function invoke(command: string, args: Record<string, unknown> = {}): Promise<unknown> {
  if (failing.has(command)) throw new Error(`Falha simulada em ${command}.`)
  switch (command) {
    case 'plugin:event|listen': { const event = args.event as string; const id = nextId++; const handler = callbacks.get(args.handler as number)!; if (!listeners.has(event)) listeners.set(event, new Map()); listeners.get(event)!.set(id, handler); return id }
    case 'plugin:event|unlisten': { listeners.get(args.event as string)?.delete(args.eventId as number); return null }
    case 'network_snapshot': await wait(900); return net
    case 'list_history': await wait(900); return history
    case 'start_speedtest': if (activeSpeed) throw new Error('Já existe uma medição em andamento.'); void runSpeedtest(args.executionId as string); return { executionId: args.executionId }
    case 'cancel_speedtest': { const current = activeSpeed; if (current && current.id === args.executionId) { current.cancelled = true; return true } return false }
    case 'gamer_probe': await wait(1500); return { capturedAtMs: now(), status: 'COMPLETE', destination: 'gamelift-ping.sa-east-1.api.aws:7770', method: 'UDP echo SQPB v1', samplesMs: [18, 19, 17, 21, 18, null, 20, 18], medianMs: 18.5, jitterMs: 1.4, lossPercent: 12.5, maxMs: 21, p95Ms: 21, limitation: 'Rota regional de referência; não representa uma partida.' } satisfies GamerResult
    case 'monitor_status': return monitor
    case 'monitor_history': return monitorHistory
    case 'monitor_start': { monitor = { ...monitor, enabled: true }; window.clearInterval(monitorTimer); monitorTimer = window.setInterval(() => { const s = sample(); monitor = { ...monitor, latest: s }; monitorHistory = [s, ...monitorHistory]; emit('monitor:sample', s) }, 6000); return monitor }
    case 'monitor_stop': { monitor = { ...monitor, enabled: false }; window.clearInterval(monitorTimer); return monitor }
    case 'export_history_csv': await wait(900); return { saved: true, rows: history.length + monitorHistory.length, path: 'C:\\Users\\Teste\\Downloads\\signallq-historico.csv' }
    case 'delete_history_record': { if (args.recordType === 'speedtest') history = history.filter(r => r.executionId !== args.id); else monitorHistory = monitorHistory.filter(r => r.id !== args.id); return true }
    case 'delete_all_history': history = []; monitorHistory = []; return null
    case 'run_tool': {
      let cancelled = false
      const started = now()
      const target = String(args.target), kind = String(args.kind)
      await new Promise<void>(resolve => { const timer = setTimeout(resolve, 1500); activeTool = { cancel: () => { cancelled = true; clearTimeout(timer); resolve() } } })
      activeTool = null
      const output = cancelled ? '' : kind === 'dns' ? `Nome: ${target}\nEndereço: 104.16.132.229` : kind === 'route' ? '1  192.168.0.1  2 ms\n2  187.1.10.1  9 ms\n3  *  tempo esgotado' : `Resposta de ${target}: 4 de 4 pacotes\nmín 12 ms · média 14 ms · máx 17 ms`
      return { target, kind, output, status: cancelled ? 'CANCELLED' : 'COMPLETED', limitation: null, startedAtMs: started, durationMs: now() - started } satisfies ToolResult
    }
    case 'cancel_tool': activeTool?.cancel(); return !!activeTool
    default: throw new Error(`Comando simulado desconhecido: ${command}`)
  }
}

export function installDevMock() {
  const w = window as unknown as Record<string, unknown>
  w.__TAURI_INTERNALS__ = {
    invoke,
    transformCallback(callback: Handler) { const id = nextId++; callbacks.set(id, callback); return id },
    unregisterCallback(id: number) { callbacks.delete(id) },
    convertFileSrc: (path: string) => path,
  }
  w.__TAURI_EVENT_PLUGIN_INTERNALS__ = { unregisterListener(event: string, id: number) { listeners.get(event)?.delete(id) } }
  // Controles para a validação manual: window.__mock.fail('network_snapshot'), window.__mock.recover(...)
  w.__mock = { fail: (command: string) => failing.add(command), recover: (command: string) => failing.delete(command), emit }
}
