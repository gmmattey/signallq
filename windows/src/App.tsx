import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { measurementStatus, measurementSummary, metric } from './metrics'
import { desktopGames } from './gameCatalog'
import { native, type GamerResult, type Measurement, type MonitorResult, type MonitorStatus, type NetworkSnapshot, type SpeedtestProgress, type ToolResult } from './native'
import { Badge, Card, Head, Icon, Metric, Notice, Row, Sk, Toast, type IconName } from './ui'
import symbol from './assets/symbol.png'

type Page = 'home' | 'speed' | 'network' | 'gamer' | 'history' | 'tools' | 'settings'
const nav: Array<[Page, string, IconName]> = [
  ['home', 'Início', 'home'], ['speed', 'Velocidade', 'speed'], ['network', 'Minha rede', 'wifi'],
  ['gamer', 'Modo gamer', 'game'], ['history', 'Histórico', 'history'], ['tools', 'Ferramentas', 'tools'],
]
const settingsItem: [Page, string, IconName] = ['settings', 'Configurações', 'settings']
const pageOrder: Page[] = ['home', 'speed', 'network', 'gamer', 'history', 'tools', 'settings']
// Busca por tela, com nomes antigos que agora vivem dentro de outra tela.
const searchItems: Array<{ id: Page; label: string; keywords: string }> = [
  { id: 'home', label: 'Início', keywords: 'visão geral' }, { id: 'speed', label: 'Velocidade', keywords: 'teste medir download upload' },
  { id: 'network', label: 'Minha rede', keywords: 'dados da conexão ip dns wi-fi gateway' }, { id: 'gamer', label: 'Modo gamer', keywords: 'jogos latência rota' },
  { id: 'history', label: 'Histórico', keywords: 'medições exportar csv excluir' }, { id: 'tools', label: 'Ferramentas', keywords: 'ping dns rota monitoramento' },
  { id: 'settings', label: 'Configurações', keywords: 'tema aparência privacidade' },
]
const unavailable = 'Indisponível'
const phaseLabels: Record<string, string> = { latency: 'Latência', download: 'Download', upload: 'Upload' }
const phaseOrder = ['latency', 'download', 'upload'] as const

function errorText(error: unknown) { return error instanceof Error ? error.message : String(error || 'Não foi possível concluir esta operação.') }
function statusTone(record: Measurement | null) { return record?.status === 'COMPLETE' ? 'ok' : record ? 'warn' : 'brand' }
function dateTime(ms: number) { return new Date(ms).toLocaleString('pt-BR') }
function shortDate(ms: number) { return new Date(ms).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) }
function seriesOf(records: Measurement[], pick: (record: Measurement) => number | null) {
  return records.map(pick).filter((value): value is number => value != null)
}

/** Gráfico de download e upload das medições completas, em ordem cronológica. */
function HistoryChart({ records }: { records: Measurement[] }) {
  const points = records.filter(record => record.status === 'COMPLETE' && record.downloadMbps != null).slice(0, 30).reverse()
  if (points.length < 2) return <p className="muted">Faça ao menos duas medições completas para ver a evolução.</p>
  const top = Math.max(...points.flatMap(record => [record.downloadMbps ?? 0, record.uploadMbps ?? 0]))
  const raw = Math.max(top / 4, 1), power = Math.pow(10, Math.floor(Math.log10(raw || 1)))
  const step = [1, 2, 5, 10].map(m => m * power).find(candidate => candidate >= raw) ?? power * 10
  const max = Math.max(step, Math.ceil(top / step) * step)
  const x = (index: number) => 60 + index * (720 / (points.length - 1))
  const y = (value: number) => 190 - value / max * 170
  // Quebra a linha onde não há valor medido, em vez de ligar pontos vizinhos.
  const segments = (pick: (record: Measurement) => number | null) => {
    const result: string[][] = [[]]
    points.forEach((record, index) => { const v = pick(record); if (v == null) { if (result[result.length - 1].length) result.push([]) } else result[result.length - 1].push(`${x(index)},${y(v)}`) })
    return result.filter(segment => segment.length > 1).map(segment => segment.join(' '))
  }
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const ticks = Array.from({ length: Math.round(max / step) + 1 }, (_, index) => Math.round(step * index))
  const labelEvery = Math.ceil(points.length / 8)
  return <>
    <svg className="chart" viewBox="0 0 800 230" role="img" aria-label={`Download e upload das últimas ${points.length} medições completas, em Mbps`}>
      <defs><clipPath id="chart-reveal"><rect x="0" y="0" width={reduceMotion ? 800 : 0} height="230">{!reduceMotion && <animate attributeName="width" from="0" to="800" dur="1.1s" fill="freeze" calcMode="spline" keyTimes="0;1" keySplines=".2 0 0 1" />}</rect></clipPath></defs>
      {ticks.map((tick, i) => <g key={i}><line className="gl" x1="60" x2="780" y1={y(tick)} y2={y(tick)} /><text x="48" y={y(tick) + 4} textAnchor="end">{tick}</text></g>)}
      {points.map((record, index) => index % labelEvery === 0 && <text key={record.executionId} x={x(index)} y="218" textAnchor="middle">{shortDate(record.capturedAtMs)}</text>)}
      <g clipPath="url(#chart-reveal)">
        {segments(record => record.downloadMbps).map(segment => <g key={`d${segment}`}><polygon points={`${segment.split(' ')[0].split(',')[0]},${y(0)} ${segment} ${segment.split(' ').at(-1)!.split(',')[0]},${y(0)}`} fill="var(--ph-down)" opacity=".1" /><polyline points={segment} fill="none" stroke="var(--ph-down)" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" /></g>)}
        {segments(record => record.uploadMbps).map(segment => <polyline key={`u${segment}`} points={segment} fill="none" stroke="var(--ph-up)" strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />)}
        {points.map((record, index) => record.downloadMbps != null && <circle key={record.executionId} cx={x(index)} cy={y(record.downloadMbps)} r="4" fill="var(--card)" stroke="var(--ph-down)" strokeWidth="2"><title>{`${dateTime(record.capturedAtMs)} · ${metric(record.downloadMbps, 'Mbps')}`}</title></circle>)}
      </g>
    </svg>
    <div className="legend"><span><i style={{ background: 'var(--ph-down)' }} />Download (Mbps)</span><span><i style={{ background: 'var(--ph-up)' }} />Upload (Mbps)</span></div>
  </>
}

/** Esqueleto com o formato da tela, exibido enquanto o primeiro carregamento não termina. */
function Skeleton({ variant }: { variant: 'home' | 'list' | 'history' }) {
  const rows = (n: number) => Array.from({ length: n }, (_, i) => <div className="skrow" key={i} style={{ padding: '10px 0' }}><Sk w="32px" h={32} round /><div className="skcol" style={{ flex: 1 }}><Sk w="45%" h={13} /><Sk w="28%" h={10} /></div><Sk w="64px" h={14} /></div>)
  const head = <header className="head"><div className="skcol"><Sk w="220px" h={30} /><Sk w="300px" h={14} /></div></header>
  if (variant === 'home') return <div className="page" aria-busy="true">{head}
    <section className="card hero"><div className="skcol" style={{ gap: 16 }}><Sk w="120px" h={14} /><Sk w="420px" h={40} /><Sk w="80%" h={14} /><div className="skrow" style={{ marginTop: 12 }}><Sk w="150px" h={44} /><Sk w="170px" h={44} /></div></div><div className="sk" style={{ height: 200, borderRadius: 12 }} /></section>
    <section className="card strip">{[0, 1, 2, 3].map(i => <div key={i} className="skcol" style={{ padding: 'var(--s-4) var(--s-5)', gap: 14 }}><Sk w="80px" /><Sk w="120px" h={34} /><Sk w="150px" h={11} /></div>)}</section></div>
  if (variant === 'history') return <div className="page" aria-busy="true">{head}<section className="card"><div className="sk" style={{ height: 200, borderRadius: 12 }} /></section><section className="card">{rows(4)}</section></div>
  return <div className="page" aria-busy="true">{head}<section className="grid g12"><div className="card s6">{rows(4)}</div><div className="card s6">{rows(4)}</div></section></div>
}

export function App() {
  const [page, setPage] = useState<Page>('home')
  const [theme, setTheme] = useState<'light' | 'dark'>(() => localStorage.getItem('sqw-theme') === 'dark' ? 'dark' : 'light')
  const [snapshot, setSnapshot] = useState<NetworkSnapshot | null>(null)
  const [history, setHistory] = useState<Measurement[]>([])
  const [ready, setReady] = useState(false)
  const [loading, setLoading] = useState(true)
  const [networkFilter, setNetworkFilter] = useState('all')
  const [latest, setLatest] = useState<Measurement | null>(null)
  const [speedState, setSpeedState] = useState<'idle' | 'running' | 'error'>('idle')
  const [speedListenersReady, setSpeedListenersReady] = useState(false)
  const [progress, setProgress] = useState<SpeedtestProgress | null>(null)
  const [executionId, setExecutionId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [includeNetworkFields, setIncludeNetworkFields] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [tool, setTool] = useState<'ping' | 'dns' | 'route'>('ping')
  const [target, setTarget] = useState('1.1.1.1')
  const [toolResult, setToolResult] = useState<ToolResult | null>(null)
  const [flowResults, setFlowResults] = useState<ToolResult[]>([])
  const [toolBusy, setToolBusy] = useState(false)
  const [gamerBusy, setGamerBusy] = useState(false)
  const [gamerResult, setGamerResult] = useState<GamerResult | null>(null)
  const [selectedGame, setSelectedGame] = useState('')
  const [monitorStatus, setMonitorStatus] = useState<MonitorStatus | null>(null)
  const [monitorHistory, setMonitorHistory] = useState<MonitorResult[]>([])
  const [monitorBusy, setMonitorBusy] = useState(false)
  const [search, setSearch] = useState('')
  const [searchOpen, setSearchOpen] = useState(false)
  const [indicatorTop, setIndicatorTop] = useState(0)
  const searchRef = useRef<HTMLInputElement>(null)
  const navRef = useRef<HTMLElement>(null)
  const previousPage = useRef<Page>('home')
  const speedInFlight = useRef(false)
  const activeSpeedId = useRef<string | null>(null)
  const flowCancelRequested = useRef(false)

  const refresh = async () => {
    setLoading(true)
    const results = await Promise.allSettled([native.network(), native.history()])
    if (results[0].status === 'fulfilled') setSnapshot(results[0].value)
    if (results[1].status === 'fulfilled') {
      setHistory(results[1].value)
      setLatest(results[1].value[0] ?? null)
    }
    const errors = results.filter(result => result.status === 'rejected').map(result => errorText(result.reason))
    if (errors.length) setMessage([...new Set(errors)].join(' '))
    setLoading(false); setReady(true)
  }
  useEffect(() => { void refresh() }, [])
  useEffect(() => {
    void Promise.allSettled([native.monitorStatus(), native.monitorHistory()]).then(results => {
      if (results[0].status === 'fulfilled') setMonitorStatus(results[0].value)
      if (results[1].status === 'fulfilled') setMonitorHistory(results[1].value)
    })
  }, [])
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem('sqw-theme', theme) }, [theme])
  useEffect(() => {
    if (!successMessage) return
    const timer = setTimeout(() => setSuccessMessage(null), 6000)
    return () => clearTimeout(timer)
  }, [successMessage])
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === 'k') { event.preventDefault(); setSearchOpen(true); searchRef.current?.focus() }
      if (event.key === 'Escape') { setSearchOpen(false); searchRef.current?.blur() }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
  useEffect(() => {
    let disposed = false
    const listeners: Array<() => void> = []
    Promise.all([
      native.onMonitorSample(value => {
        if (disposed) return
        setMonitorStatus(current => current ? { ...current, latest: value } : { enabled: true, intervalMinutes: 15, latest: value })
        setMonitorHistory(current => [value, ...current].slice(0, 500))
      }),
      native.onMonitorError(value => { if (!disposed) setMessage(value) }),
    ]).then(values => { if (disposed) values.forEach(stop => stop()); else listeners.push(...values) }).catch(() => {})
    return () => { disposed = true; listeners.forEach(stop => stop()) }
  }, [])
  useEffect(() => {
    let disposed = false
    const unlisten: Array<() => void> = []
    Promise.all([
      native.onSpeedtestProgress(value => { if (!disposed && value.executionId === activeSpeedId.current) { setProgress(value); setSpeedState('running') } }),
      native.onSpeedtestFinished(value => {
        if (disposed || value.executionId !== activeSpeedId.current) return
        activeSpeedId.current = null
        setLatest(value); setProgress(null); setExecutionId(null); setSpeedState('idle'); speedInFlight.current = false
        void native.history().then(setHistory).catch(error => setMessage(errorText(error)))
      }),
      native.onSpeedtestFailed(value => {
        if (disposed || value.executionId !== activeSpeedId.current) return
        activeSpeedId.current = null
        setProgress(null); setExecutionId(null); setSpeedState('error'); speedInFlight.current = false; setMessage(value.message)
      }),
    ]).then(listeners => { if (disposed) listeners.forEach(stop => stop()); else { unlisten.push(...listeners); setSpeedListenersReady(true) } })
      .catch(error => { if (!disposed && window.__TAURI_INTERNALS__) setMessage(errorText(error)) })
    return () => { disposed = true; unlisten.forEach(stop => stop()) }
  }, [])
  // O indicador da navegação desliza até o item ativo.
  useLayoutEffect(() => {
    const place = () => { const active = navRef.current?.querySelector<HTMLElement>('[aria-current=page]'); if (active) setIndicatorTop(active.offsetTop + 11) }
    place(); window.addEventListener('resize', place)
    return () => window.removeEventListener('resize', place)
  }, [page])

  const goTo = (value: Page) => { previousPage.current = page; setPage(value); setSearchOpen(false); setSearch('') }
  const runSpeedtest = async () => {
    if (speedInFlight.current || !speedListenersReady) return
    const id = crypto.randomUUID()
    activeSpeedId.current = id; speedInFlight.current = true; setExecutionId(id); setSpeedState('running'); setProgress(null); setMessage(null); goTo('speed')
    try { await native.startSpeedtest(id) }
    catch (error) { if (activeSpeedId.current === id) { activeSpeedId.current = null; speedInFlight.current = false; setExecutionId(null); setSpeedState('error'); setMessage(errorText(error)) } }
  }
  const cancelSpeedtest = async () => {
    if (!executionId) return
    try { await native.cancelSpeedtest(executionId) } catch (error) { setMessage(errorText(error)) }
  }
  const executeTool = async () => {
    setMessage(null); setToolResult(null); setToolBusy(true)
    try { setToolResult(await native.tool(tool, target)) } catch (error) { setMessage(errorText(error)) }
    finally { setToolBusy(false) }
  }
  const runConnectivityFlow = async () => {
    flowCancelRequested.current = false; setToolBusy(true); setMessage(null); setFlowResults([])
    const probes: Array<['ping' | 'dns', string]> = []
    if (snapshot?.gateway) probes.push(['ping', snapshot.gateway])
    probes.push(['dns', 'cloudflare.com'], ['ping', '1.1.1.1'])
    try {
      for (const [kind, destination] of probes) {
        if (flowCancelRequested.current) break
        const result = await native.tool(kind, destination)
        setFlowResults(current => [...current, result])
        if (flowCancelRequested.current || result.status === 'CANCELLED') break
      }
    } catch (error) { setMessage(errorText(error)) }
    finally { flowCancelRequested.current = false; setToolBusy(false) }
  }
  const runGamerProbe = async () => {
    setGamerBusy(true); setMessage(null); setGamerResult(null)
    try { setGamerResult(await native.gamerProbe()) } catch (error) { setMessage(errorText(error)) }
    finally { setGamerBusy(false) }
  }
  const toggleMonitor = async () => {
    setMonitorBusy(true); setMessage(null)
    try { setMonitorStatus(await (monitorStatus?.enabled ? native.monitorStop() : native.monitorStart())) }
    catch (error) { setMessage(errorText(error)) }
    finally { setMonitorBusy(false) }
  }
  const exportHistory = async () => {
    setMessage(null); setSuccessMessage(null); setExporting(true)
    try {
      const result = await native.exportHistoryCsv(includeNetworkFields)
      if (result.saved) setSuccessMessage(`${result.rows} registros exportados para ${result.path}.`)
    } catch (error) { setMessage(errorText(error)) }
    finally { setExporting(false) }
  }
  const deleteRecord = async (recordType: 'speedtest' | 'monitor', id: string) => {
    if (!window.confirm('Excluir este registro local?')) return
    setMessage(null)
    try {
      await native.deleteHistoryRecord(recordType, id)
      if (recordType === 'speedtest') { const records = await native.history(); setHistory(records); setLatest(records[0] ?? null) }
      else { const records = await native.monitorHistory(); setMonitorHistory(records); setMonitorStatus(current => current ? { ...current, latest: records[0] ?? null } : current) }
    } catch (error) { setMessage(errorText(error)) }
  }
  const deleteAllHistory = async () => {
    if (!window.confirm('Excluir todas as medições e observações locais? Esta ação não pode ser desfeita.')) return
    setMessage(null)
    try { await native.deleteAllHistory(); setHistory([]); setLatest(null); setMonitorHistory([]); setMonitorStatus(current => current ? { ...current, latest: null } : current); setSuccessMessage('Histórico local excluído.') }
    catch (error) { setMessage(errorText(error)) }
  }

  const networkLabel = snapshot?.ssid ?? snapshot?.interfaceName ?? snapshot?.connectionType ?? 'Verificando rede…'
  const networks = Array.from(new Map(history.filter(record => record.networkId).map(record => [record.networkId!, record.ssid || record.networkId!])).entries())
  const filteredHistory = networkFilter === 'all' ? history : history.filter(record => record.networkId === networkFilter)
  const comparable = networkFilter === 'all' ? [] : filteredHistory.filter(record => record.status === 'COMPLETE' && record.downloadMbps != null && record.latencyMs != null).slice(0, 2)
  const delta = (latestValue: number, previousValue: number, unit: string) => `${latestValue >= previousValue ? '+' : ''}${(latestValue - previousValue).toFixed(1).replace('.', ',')} ${unit}`
  const query = search.toLocaleLowerCase('pt-BR')
  const matches = searchItems.filter(item => `${item.label} ${item.keywords}`.toLocaleLowerCase('pt-BR').includes(query))
  const recent = history.filter(record => record.status === 'COMPLETE').slice(0, 8).reverse()
  const running = speedState === 'running'
  const busy = loading || running || toolBusy || gamerBusy || monitorBusy || exporting
  const dx = pageOrder.indexOf(page) >= pageOrder.indexOf(previousPage.current) ? 28 : -28

  const phaseCards = phaseOrder.map((name, index) => {
    const info = { latency: { icon: 'pulse' as IconName, value: latest?.latencyMs ?? null, unit: 'ms' }, download: { icon: 'down' as IconName, value: latest?.downloadMbps ?? null, unit: 'Mbps' }, upload: { icon: 'up' as IconName, value: latest?.uploadMbps ?? null, unit: 'Mbps' } }[name]
    const current = running ? phaseOrder.indexOf(progress?.phase as typeof phaseOrder[number]) : -1
    const recorded = latest?.phases.find(phase => phase.name === name)
    let state = 'Aguardando', cls = ''
    if (running) { if (index < current) { state = 'Concluída'; cls = 'done' } else if (index === current) { state = 'Medindo…'; cls = 'now' } }
    else if (recorded) { state = recorded.status === 'COMPLETE' ? 'Concluída' : recorded.status; cls = recorded.status === 'COMPLETE' ? 'done' : '' }
    else if (latest) state = 'Sem dados'
    const value = running ? null : info.value
    return <div className={`phase ${cls}`} key={name}><div className="pi" style={{ background: `var(--ph-${name === 'latency' ? 'lat' : name === 'download' ? 'down' : 'up'})` }}><Icon name={info.icon} size={18} /></div><div><b>{phaseLabels[name]}</b><div className="s">{state}</div></div><div className="v">{value == null ? '—' : value.toFixed(1).replace('.', ',')}<small> {info.unit}</small></div></div>
  })
  const percent = Math.round(progress?.percent ?? 0)
  const ringPhase = progress?.phase && phaseLabels[progress.phase] ? progress.phase : null
  const ringColor = running ? `var(--ph-${ringPhase === 'latency' ? 'lat' : ringPhase === 'download' ? 'down' : ringPhase === 'upload' ? 'up' : 'down'})` : latest?.status === 'COMPLETE' ? 'var(--success)' : 'var(--primary)'
  const ringOffset = running ? 754 * (1 - percent / 100) : latest?.status === 'COMPLETE' ? 0 : 754

  let content: React.ReactNode
  if (!ready && (page === 'home' || page === 'network' || page === 'history')) content = <Skeleton variant={page === 'home' ? 'home' : page === 'history' ? 'history' : 'list'} />
  else if (page === 'home') content = <div className="page" key="home">
    <Head title="Início" sub={latest ? `Última medição em ${dateTime(latest.capturedAtMs)}` : 'Nenhuma medição registrada neste computador'}><button className="btn" onClick={() => goTo('history')}>Ver histórico</button></Head>
    <Card className="hero">
      <div><span className={`state ${latest && latest.status !== 'COMPLETE' ? 'warn' : ''} ${latest ? '' : 'idle'}`}><i />{latest ? measurementStatus(latest.status) : 'Sem medição'}</span>
        <h2>{latest ? 'Confira o que foi medido.' : 'Faça sua primeira medição.'}</h2>
        <p>{latest ? measurementSummary(latest) : 'Inicie uma medição para registrar download, upload e latência desta conexão.'}</p>
        <div className="actions"><button className="btn primary lg" disabled={running || !speedListenersReady} title={speedListenersReady ? undefined : 'Disponível no aplicativo desktop'} onClick={() => void runSpeedtest()}><Icon name="play" size={16} /> {running ? 'Medindo…' : 'Medir agora'}</button><button className="btn lg" onClick={() => goTo('network')}>Ver minha rede</button></div></div>
      <div className="panel"><p className="overline">Conexão ativa</p>
        <Row title="Interface" value={snapshot?.interfaceName ?? unavailable} /><Row title="Tipo" value={snapshot?.connectionType ?? unavailable} /><Row title="IP local" value={snapshot?.localIp ?? unavailable} /><Row title="Gateway" value={snapshot?.gateway ?? unavailable} /></div>
    </Card>
    <Card className="strip">
      <div><Metric label="Download" value={latest?.downloadMbps ?? null} unit="Mbps" color="var(--ph-down)" series={seriesOf(recent, r => r.downloadMbps)} /></div>
      <div><Metric label="Upload" value={latest?.uploadMbps ?? null} unit="Mbps" color="var(--ph-up)" series={seriesOf(recent, r => r.uploadMbps)} /></div>
      <div><Metric label="Latência" value={latest?.latencyMs ?? null} unit="ms" color="var(--ph-lat)" series={seriesOf(recent, r => r.latencyMs)} /></div>
      <div><Metric label="Jitter" value={latest?.jitterMs ?? null} unit="ms" /></div>
    </Card>
    <Card><p className="overline">Caminho de rede informado pelo Windows</p>
      <div className="path">
        <div className="node" style={{ ['--k' as string]: 0 }}><div className="c"><Icon name="laptop" size={22} /></div><b>Este PC</b><small>{snapshot?.localIp ?? unavailable}</small></div><div className="link" style={{ ['--k' as string]: 0 }} />
        <div className="node" style={{ ['--k' as string]: 1 }}><div className="c"><Icon name="router" size={22} /></div><b>Gateway</b><small>{snapshot?.gateway ?? unavailable}</small></div><div className="link" style={{ ['--k' as string]: 1 }} />
        <div className="node" style={{ ['--k' as string]: 2 }}><div className="c"><Icon name="globe" size={22} /></div><b>DNS</b><small>{snapshot?.dnsServers.join(', ') || unavailable}</small></div>
      </div>
      <p className="muted note">Mostra o que o Windows informou. Não verifica a saúde de cada ponto.</p></Card>
    <section className="grid g12">
      <Card className="lift s7"><p className="overline">Explorar dados</p>
        <button className="task" onClick={() => goTo('network')}>Adaptador, IP, DNS e Wi‑Fi <Icon name="chevron" size={16} /></button>
        <button className="task" onClick={() => goTo('gamer')}>Latência para uma rota de referência <Icon name="chevron" size={16} /></button>
        <button className="task" onClick={() => goTo('history')}>Comparar medições anteriores <Icon name="chevron" size={16} /></button></Card>
      <Card className="tint s5"><p className="overline">Limites da coleta</p><p style={{ margin: 0 }}>Um campo indisponível indica que o dado não foi observado. A tela mostra valores medidos, sem atribuir causa à conexão.</p></Card>
    </section></div>
  else if (page === 'speed') content = <div className="page" key="speed">
    <Head title="Velocidade" sub="Download, upload, latência e variação coletados nesta conexão." />
    <section className="speed">
      <div className="arena"><div className="dial"><svg viewBox="0 0 280 280" aria-hidden="true"><circle className="t" cx="140" cy="140" r="120" /><circle className="p" cx="140" cy="140" r="120" style={{ stroke: ringColor, strokeDashoffset: ringOffset }} /></svg>
        <button className={`go ${running ? 'run' : ''}`} disabled={!running && !speedListenersReady} title={speedListenersReady || running ? undefined : 'Disponível no aplicativo desktop'} aria-label={running ? 'Medição em andamento' : 'Iniciar medição'} onClick={() => { if (!running) void runSpeedtest() }}>
          {running ? <span><span className="big">{percent}</span><small>%</small></span> : <span>{latest ? 'Medir de novo' : 'Iniciar'}</span>}</button></div>
        <div><h2>{running ? (phaseLabels[progress?.phase ?? ''] ?? 'Iniciando') : latest ? measurementStatus(latest.status) : 'Pronto para medir'}</h2>
          <p className="hint" style={{ margin: 'var(--s-1) 0 0' }}>{running ? `Etapa ${Math.max(1, phaseOrder.indexOf(progress?.phase as typeof phaseOrder[number]) + 1)} de 3` : latest ? `Última medição em ${dateTime(latest.capturedAtMs)}` : 'Cerca de 18 s de download e 18 s de upload, mais a latência. Pode transferir até 5 GB.'}</p>
          {running && <div className="actions center"><button className="btn" disabled={!executionId} onClick={() => void cancelSpeedtest()}>Cancelar</button></div>}</div>
        {latest?.limitation && !running && <Notice title="Limitação da medição" tone="warn">{latest.limitation}</Notice>}</div>
      <div className="col">{phaseCards}</div></section>
    <Card className="strip">
      <div><Metric label="Perda" value={latest?.packetLossPercent ?? null} unit="%" note={latest?.packetLossSource ?? undefined} /></div>
      <div><Metric label="Atraso sob carga" value={latest?.bufferbloatMs ?? null} unit="ms" /></div>
      <div><Metric label="Jitter" value={latest?.jitterMs ?? null} unit="ms" /></div>
      <div><Metric label="Latência" value={latest?.latencyMs ?? null} unit="ms" color="var(--ph-lat)" /></div></Card>
    {latest && <Card><p className="overline">Etapas da última medição</p>{latest.phases.length ? <div className="phase-list">{latest.phases.map((phase, index) => <div className="phase-detail" key={`${phase.name}-${index}`}><div className="row"><strong>{phaseLabels[phase.name] ?? phase.name}</strong><Badge tone={phase.status === 'COMPLETE' ? 'ok' : 'warn'}>{phase.status}</Badge></div><small>{phase.method} · {phase.endpoint} · {phase.validSamples} amostras válidas · {phase.bytesTransferred ?? 'volume indisponível'} bytes{phase.fallbackUsed ? ' · fallback' : ''}</small>{phase.fallbackTrigger && <p>Fallback após {phase.fallbackTrigger.code}{phase.fallbackTrigger.httpStatus ? ` HTTP ${phase.fallbackTrigger.httpStatus}` : ''}.</p>}{phase.error && <p>{phase.error.code}{phase.error.httpStatus ? ` HTTP ${phase.error.httpStatus}` : ''}: {phase.error.message}</p>}</div>)}</div> : <p className="muted">Detalhes das etapas indisponíveis neste registro.</p>}</Card>}
    <Notice title="Origem e limites">A perda baseada em sondagem HTTP é uma estimativa, quando disponível. Campos sem coleta ficam indisponíveis; nenhum valor ausente é convertido em zero.</Notice></div>
  else if (page === 'network') content = <div className="page" key="network">
    <Head title="Minha rede" sub="Informações disponíveis pelo Windows e pelo adaptador."><button className={`btn ${loading ? 'busy' : ''}`} disabled={loading} onClick={() => void refresh()}>Atualizar</button></Head>
    <section className="grid g12">
      <Card className="s6"><p className="overline">Conexão ativa</p>
        <Row title="Adaptador" value={snapshot?.interfaceName ?? unavailable} /><Row title="Tipo" value={snapshot?.connectionType ?? unavailable} /><Row title="IP local" value={snapshot?.localIp ?? unavailable} /><Row title="Gateway" value={snapshot?.gateway ?? unavailable} /><Row title="DNS" value={snapshot?.dnsServers.join(', ') || unavailable} /></Card>
      <Card className="s6"><p className="overline">Wi‑Fi</p>
        <Row title="SSID" value={snapshot?.ssid ?? unavailable} /><Row title="Sinal" value={snapshot?.signalPercent == null ? unavailable : `${snapshot.signalPercent}%`} /><Row title="Canal" value={snapshot?.channel ?? unavailable} /><Row title="Identificador da rede" value={snapshot?.networkId ?? unavailable} /></Card>
    </section>
    <Card><p className="overline">Última medição</p>
      <div className="grid g12"><div className="s6"><Row title="Estado" value={latest ? measurementStatus(latest.status) : unavailable} /><Row title="Horário" value={latest ? dateTime(latest.capturedAtMs) : unavailable} /><Row title="Download" value={metric(latest?.downloadMbps ?? null, 'Mbps')} /></div>
        <div className="s6"><Row title="Upload" value={metric(latest?.uploadMbps ?? null, 'Mbps')} /><Row title="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Row title="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /></div></div>
      <div className="row" style={{ marginTop: 'var(--s-4)' }}><button className="btn" onClick={() => goTo('speed')}>Ver etapas</button></div></Card>
    {snapshot?.capabilityNote && <Notice title="Limite da coleta">{snapshot.capabilityNote}</Notice>}</div>
  else if (page === 'gamer') content = <div className="page" key="gamer">
    <Head title="Modo gamer" sub="Oito sondas UDP para uma rota pública em São Paulo. Esta rota não representa uma partida."><button className={`btn primary ${gamerBusy ? 'busy' : ''}`} disabled={gamerBusy} onClick={() => void runGamerProbe()}>{gamerBusy ? 'Medindo…' : <><Icon name="play" size={16} /> Medir rota</>}</button></Head>
    <Card><p className="overline">Jogo de interesse</p>
      <div className="chips"><button className="chip" aria-pressed={selectedGame === ''} onClick={() => setSelectedGame('')}>Rota regional</button>{desktopGames.map(game => <button key={game.id} className="chip" aria-pressed={selectedGame === game.id} onClick={() => setSelectedGame(game.id)}>{game.name}</button>)}</div>
      <p className="muted note">O jogo serve só de contexto. A sonda usa a mesma rota regional (São Paulo, sa-east-1) para todos; não mede servidor oficial nem matchmaking.</p></Card>
    <Card className="strip">
      <div><Metric label="Latência mediana" value={gamerResult?.medianMs ?? null} unit="ms" color="var(--ph-lat)" /></div>
      <div><Metric label="Jitter" value={gamerResult?.jitterMs ?? null} unit="ms" /></div>
      <div><Metric label="Sondas sem eco" value={gamerResult?.lossPercent ?? null} unit="%" /></div>
      <div><Metric label="P95" value={gamerResult?.p95Ms ?? null} unit="ms" /></div></Card>
    <section className="grid g12">
      <Card className="s6"><p className="overline">Origem</p><Row title="Estado" value={gamerResult?.status ?? unavailable} /><Row title="Pico" value={metric(gamerResult?.maxMs ?? null, 'ms')} /><Row title="Destino" value={gamerResult?.destination ?? 'gamelift-ping.sa-east-1.api.aws:7770'} /><Row title="Método" value={gamerResult?.method ?? 'UDP echo SQPB v1'} /><Row title="Horário" value={gamerResult ? dateTime(gamerResult.capturedAtMs) : unavailable} /></Card>
      <Card className="s6 tint"><p className="overline">Amostras em sequência</p>{gamerResult ? <div className="samples">{gamerResult.samplesMs.map((sample, index) => <div className="sample" key={index}><span>Sonda {index + 1}</span><b>{metric(sample, 'ms')}</b></div>)}</div> : <p className="muted" style={{ margin: 0 }}>Meça a rota para ver as oito sondas.</p>}</Card></section>
    {gamerResult && <Notice title="Limite da rota">{gamerResult.limitation}</Notice>}</div>
  else if (page === 'history') content = <div className="page" key="history">
    <Head title="Histórico" sub="Medições e observações guardadas neste computador.">
      <button className={`btn ${loading ? 'busy' : ''}`} disabled={loading} onClick={() => void refresh()}>Atualizar</button>
      <button className={`btn ${exporting ? 'busy' : ''}`} disabled={exporting} onClick={() => void exportHistory()}>{exporting ? 'Exportando…' : 'Exportar CSV'}</button>
      <button className="btn" onClick={() => void deleteAllHistory()}>Excluir tudo</button></Head>
    <Card><div className="row" style={{ marginBottom: 'var(--s-4)', justifyContent: 'space-between' }}><p className="overline" style={{ margin: 0 }}>Evolução</p>
      <label className="inline-select">Rede<select className="field" value={networkFilter} onChange={event => setNetworkFilter(event.target.value)}><option value="all">Todas as redes e registros sem identificação</option>{networks.map(([id, label]) => <option key={id} value={id}>{label} · {id}</option>)}</select></label></div>
      <HistoryChart key={networkFilter + filteredHistory.length} records={filteredHistory} />
      {comparable.length === 2 && <div className="strip compare"><div><Row title="Variação de download" value={delta(comparable[0].downloadMbps!, comparable[1].downloadMbps!, 'Mbps')} /></div><div><Row title="Variação de latência" value={delta(comparable[0].latencyMs!, comparable[1].latencyMs!, 'ms')} /></div></div>}
      <p className="muted note">Comparações aparecem somente entre medições completas da mesma rede identificada.</p></Card>
    <Card className="flush"><table><thead><tr><th>Quando</th><th>Rede</th><th className="n">Download</th><th className="n">Upload</th><th className="n">Latência</th><th className="n">Jitter</th><th>Estado</th><th></th></tr></thead><tbody>{filteredHistory.length ? filteredHistory.map((record, index) => <tr key={record.executionId} style={{ ['--r' as string]: Math.min(index, 10) }}><td>{dateTime(record.capturedAtMs)}</td><td>{record.ssid || record.networkId || 'Não identificada'}</td><td className="n">{metric(record.downloadMbps, 'Mbps')}</td><td className="n">{metric(record.uploadMbps, 'Mbps')}</td><td className="n">{metric(record.latencyMs, 'ms')}</td><td className="n">{metric(record.jitterMs, 'ms')}</td><td><Badge tone={statusTone(record)}>{measurementStatus(record.status)}</Badge></td><td><button className="btn small" onClick={() => void deleteRecord('speedtest', record.executionId)}>Excluir</button></td></tr>) : <tr><td colSpan={8} className="muted">Nenhuma medição neste filtro.</td></tr>}</tbody></table></Card>
    <Card className="tint"><p className="overline">Exportação</p><p style={{ margin: '0 0 var(--s-3)' }}>O CSV local contém horário, origem, estado, métricas, método, destino, duração, tentativas, amostras, bytes e erros. Campos ausentes ficam vazios. Observações do monitor também entram no arquivo.</p>
      <label className="checkbox"><input type="checkbox" checked={includeNetworkFields} onChange={event => setIncludeNetworkFields(event.target.checked)} /> Incluir SSID, IP local e identificador da rede</label>
      {includeNetworkFields && <div style={{ marginTop: 'var(--s-3)' }}><Row title="SSID da última medição" value={history[0]?.ssid ?? unavailable} /><Row title="IP local da última medição" value={history[0]?.localIp ?? unavailable} /><Row title="Identificador da última medição" value={history[0]?.networkId ?? unavailable} /></div>}
      <p className="muted note">A prévia mostra a última medição; o arquivo inclui os registros existentes conforme a opção acima.</p></Card></div>
  else if (page === 'tools') content = <div className="page" key="tools">
    <Head title="Ferramentas" sub="Ping, consulta DNS, rastreamento de rota e monitoramento em segundo plano." />
    <Card><div className="row wrap"><div className="chips">{(['ping', 'dns', 'route'] as const).map(kind => <button key={kind} className="chip" aria-pressed={tool === kind} onClick={() => setTool(kind)}>{kind === 'ping' ? 'Ping' : kind === 'dns' ? 'DNS' : 'Rota'}</button>)}</div>
      <input className="field grow" aria-label="Destino" value={target} onChange={event => setTarget(event.target.value)} maxLength={253} placeholder="exemplo.com ou IP" />
      <button className={`btn primary ${toolBusy ? 'busy' : ''}`} disabled={toolBusy || !target.trim()} onClick={() => void executeTool()}>{toolBusy ? 'Executando…' : 'Executar'}</button>
      {toolBusy && <button className="btn" onClick={() => void native.cancelTool().catch(error => setMessage(errorText(error)))}>Cancelar</button>}</div>
      <div className="console" aria-live="polite">{toolBusy ? <><span className="dots"><i /><i /><i /></span>Executando…</> : toolResult ? <>{toolResult.output || 'Nenhuma saída retornada.'}</> : 'Aguardando execução.'}</div>
      {toolResult && !toolBusy && <div className="row wrap" style={{ marginTop: 'var(--s-3)' }}><Badge tone={toolResult.status === 'COMPLETED' ? 'ok' : 'warn'}>{toolResult.status}</Badge><small className="muted">{dateTime(toolResult.startedAtMs)} · {toolResult.durationMs} ms</small></div>}
      {toolResult?.limitation && !toolBusy && <Notice title="Limite">{toolResult.limitation}</Notice>}</Card>
    <Card><p className="overline">Sequência de conectividade</p><p className="muted" style={{ marginTop: 0 }}>Observa gateway local quando disponível, resolução DNS e resposta de uma rota externa, nessa ordem.</p>
      <div className="row"><button className={`btn ${toolBusy ? 'busy' : ''}`} disabled={toolBusy} onClick={() => void runConnectivityFlow()}>Executar sequência</button>{toolBusy && <button className="btn" onClick={() => { flowCancelRequested.current = true; void native.cancelTool().catch(error => setMessage(errorText(error))) }}>Cancelar</button>}</div>
      {!snapshot?.gateway && <Notice title="Gateway indisponível">A etapa local será omitida porque o Windows não informou um gateway.</Notice>}
      {flowResults.map((result, index) => <div className="phase-detail" key={`${result.kind}-${index}`}><div className="row"><strong>{result.kind.toUpperCase()} · {result.target}</strong><Badge tone={result.status === 'COMPLETED' ? 'ok' : 'warn'}>{result.status}</Badge></div><small>{dateTime(result.startedAtMs)} · {result.durationMs} ms</small><pre>{result.output || 'Sem saída.'}</pre></div>)}</Card>
    <Card><div className="row" style={{ justifyContent: 'space-between' }}><div><p className="overline" style={{ marginBottom: 'var(--s-1)' }}>Monitoramento</p><h2>{monitorStatus?.enabled ? 'Monitor ativo' : 'Monitor pausado'}</h2><p className="muted" style={{ margin: 'var(--s-1) 0 0' }}>Sondas leves a cada {monitorStatus?.intervalMinutes ?? 15} minutos, inclusive com a janela na bandeja. Fechar o aplicativo encerra a coleta; a preferência é restaurada ao abrir.</p></div>
      <button className="switch" role="switch" aria-label="Ativar monitoramento" aria-checked={monitorStatus?.enabled ?? false} disabled={monitorBusy || !monitorStatus} onClick={() => void toggleMonitor()} /></div>
      <div className="strip tight"><div><Metric label="Latência HTTP" value={monitorStatus?.latest?.latencyMs ?? null} unit="ms" /></div><div><Metric label="Resolução DNS" value={monitorStatus?.latest?.dnsMs ?? null} unit="ms" /></div><div><Row title="Última sonda" value={monitorStatus?.latest ? dateTime(monitorStatus.latest.capturedAtMs) : unavailable} /><Row title="Estado" value={monitorStatus?.latest?.status ?? unavailable} /></div></div>
      {monitorHistory.length ? <table style={{ marginTop: 'var(--s-4)' }}><thead><tr><th>Quando</th><th className="n">Latência</th><th className="n">DNS</th><th>Estado</th><th></th></tr></thead><tbody>{monitorHistory.slice(0, 30).map(record => <tr key={record.id}><td>{dateTime(record.capturedAtMs)}</td><td className="n">{metric(record.latencyMs, 'ms')}</td><td className="n">{metric(record.dnsMs, 'ms')}</td><td>{record.status}</td><td><button className="btn small" onClick={() => void deleteRecord('monitor', record.id)}>Excluir</button></td></tr>)}</tbody></table> : <p className="muted note">Nenhuma observação registrada.</p>}
      <Notice title="Limite da sonda">O monitor mede resposta HTTP e DNS, sem download ou upload. Falha ou ausência de eco não identifica a causa.</Notice></Card></div>
  else content = <div className="page" key="settings">
    <Head title="Configurações" sub="Escolhas locais deste computador." />
    <section className="grid g12">
      <Card className="s6"><p className="overline">Aparência</p><div className="chips"><button className="chip" aria-pressed={theme === 'light'} onClick={() => setTheme('light')}>Claro</button><button className="chip" aria-pressed={theme === 'dark'} onClick={() => setTheme('dark')}>Escuro</button></div></Card>
      <Card className="s6 tint"><p className="overline">Privacidade</p><p style={{ margin: 0 }}>O histórico fica no armazenamento local do aplicativo. Nenhum dado de inventário é enviado automaticamente.</p></Card></section></div>

  const navButton = ([id, label, icon]: [Page, string, IconName]) => <button key={id} className="nav-item" aria-current={page === id ? 'page' : undefined} onClick={() => goTo(id)}><Icon name={icon} /><span>{label}</span></button>
  return <div className="app" style={{ ['--dx' as string]: `${dx}px` }}>
    <header className="titlebar">
      <div className="brand"><img src={symbol} alt="" /><b>Signall<i>Q</i></b></div>
      <div className="search-wrap"><label className="search"><Icon name="search" size={16} /><input ref={searchRef} aria-label="Buscar tela ou ferramenta" placeholder="Buscar tela ou ferramenta" value={search} onFocus={() => setSearchOpen(true)} onBlur={() => setSearchOpen(false)} onChange={event => setSearch(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && matches[0]) goTo(matches[0].id) }} /><kbd>Ctrl K</kbd></label>
        {searchOpen && <div className="search-results" role="listbox">{matches.length ? matches.map(item => <button key={item.id} role="option" aria-selected={page === item.id} onMouseDown={event => event.preventDefault()} onClick={() => goTo(item.id)}>{item.label}</button>) : <span>Nenhuma tela encontrada.</span>}</div>}</div>
      <div className="tb-actions"><button className="tb-btn" aria-label={theme === 'dark' ? 'Usar tema claro' : 'Usar tema escuro'} onClick={() => { const root = document.documentElement; root.classList.add('themeing'); setTheme(theme === 'dark' ? 'light' : 'dark'); setTimeout(() => root.classList.remove('themeing'), 450) }}><Icon name={theme === 'dark' ? 'sun' : 'moon'} /></button></div>
    </header>
    <nav className="nav" ref={navRef} aria-label="Navegação principal"><span className="ind" style={{ transform: `translateY(${indicatorTop}px)` }} />
      {nav.map(navButton)}<div className="nav-sep" />{navButton(settingsItem)}
      <div className="conn"><i className={`dot ${snapshot?.localIp ? '' : 'off'}`} /><div><b>{networkLabel}</b><small>{snapshot?.connectionType ?? 'Atualizando…'}</small></div></div></nav>
    <main className="stage" tabIndex={-1}>
      <div className={`loadbar ${busy ? 'on' : ''}`} aria-hidden="true"><i /></div>
      {message && <div className="page alerts"><Notice title="Ação não concluída" tone="bad">{message}</Notice></div>}
      {content}
    </main>
    <Toast message={successMessage} />
  </div>
}
