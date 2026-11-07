import { useEffect, useRef, useState } from 'react'
import { measurementStatus, measurementSummary, metric } from './metrics'
import { desktopGames } from './gameCatalog'
import { native, type GamerResult, type Measurement, type MonitorResult, type MonitorStatus, type NetworkSnapshot, type SpeedtestProgress, type ToolResult } from './native'

type Page = 'home' | 'speed' | 'data' | 'gamer' | 'network' | 'monitor' | 'history' | 'tools' | 'settings'
const nav: Array<[Page, string, string]> = [
  ['home', 'Visão geral', '⌂'], ['speed', 'Velocidade', '◴'], ['data', 'Dados da conexão', '◇'],
  ['gamer', 'Modo gamer', '◈'], ['network', 'Minha rede', '⌁'], ['monitor', 'Monitoramento', '⌇'],
  ['history', 'Histórico', '◷'], ['tools', 'Ferramentas', '⌘'], ['settings', 'Configurações', '⚙'],
]
const unavailable = 'Indisponível'

function Card({ children, className = '' }: { children: React.ReactNode; className?: string }) { return <section className={`card ${className}`}>{children}</section> }
function Badge({ children, tone = '' }: { children: React.ReactNode; tone?: string }) { return <span className={`badge ${tone}`}>{children}</span> }
function Value({ label, value, note }: { label: string; value: string; note?: string }) { return <div className="value"><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</div> }
function Notice({ title, children, tone = '' }: { title: string; children: React.ReactNode; tone?: string }) { return <div className={`notice ${tone}`}><strong>{title}</strong><p>{children}</p></div> }
function errorText(error: unknown) { return error instanceof Error ? error.message : String(error || 'Não foi possível concluir esta operação.') }
function statusTone(record: Measurement | null) { return record?.status === 'COMPLETE' ? 'good' : record ? 'warn' : 'purple' }
function dateTime(ms: number) { return new Date(ms).toLocaleString('pt-BR') }

export function App() {
  const [page, setPage] = useState<Page>('home')
  const [theme, setTheme] = useState<'light' | 'dark'>(() => localStorage.getItem('sqw-theme') === 'dark' ? 'dark' : 'light')
  const [snapshot, setSnapshot] = useState<NetworkSnapshot | null>(null)
  const [history, setHistory] = useState<Measurement[]>([])
  const [networkFilter, setNetworkFilter] = useState('all')
  const [latest, setLatest] = useState<Measurement | null>(null)
  const [speedState, setSpeedState] = useState<'idle' | 'running' | 'error'>('idle')
  const [speedListenersReady, setSpeedListenersReady] = useState(false)
  const [progress, setProgress] = useState<SpeedtestProgress | null>(null)
  const [executionId, setExecutionId] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [includeNetworkFields, setIncludeNetworkFields] = useState(false)
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
  const searchRef = useRef<HTMLInputElement>(null)
  const speedInFlight = useRef(false)
  const activeSpeedId = useRef<string | null>(null)
  const flowCancelRequested = useRef(false)

  const refresh = async () => {
    const results = await Promise.allSettled([native.network(), native.history()])
    if (results[0].status === 'fulfilled') setSnapshot(results[0].value)
    if (results[1].status === 'fulfilled') {
      setHistory(results[1].value)
      setLatest(results[1].value[0] ?? null)
    }
    const errors = results.filter(result => result.status === 'rejected').map(result => errorText(result.reason))
    if (errors.length) setMessage(errors.join(' '))
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

  const runSpeedtest = async () => {
    if (speedInFlight.current || !speedListenersReady) return
    const id = crypto.randomUUID()
    activeSpeedId.current = id; speedInFlight.current = true; setExecutionId(id); setSpeedState('running'); setProgress(null); setMessage(null); setPage('speed')
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
    setMessage(null); setSuccessMessage(null)
    try {
      const result = await native.exportHistoryCsv(includeNetworkFields)
      if (result.saved) setSuccessMessage(`${result.rows} registros exportados para ${result.path}.`)
    } catch (error) { setMessage(errorText(error)) }
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
  const title = nav.find(([id]) => id === page)?.[1] ?? 'SignallQ'
  const networkLabel = snapshot?.ssid ?? snapshot?.interfaceName ?? snapshot?.connectionType ?? 'Verificando rede…'
  const networks = Array.from(new Map(history.filter(record => record.networkId).map(record => [record.networkId!, record.ssid || record.networkId!])).entries())
  const filteredHistory = networkFilter === 'all' ? history : history.filter(record => record.networkId === networkFilter)
  const comparable = networkFilter === 'all' ? [] : filteredHistory.filter(record => record.status === 'COMPLETE' && record.downloadMbps != null && record.latencyMs != null).slice(0, 2)
  const delta = (latestValue: number, previousValue: number, unit: string) => `${latestValue >= previousValue ? '+' : ''}${(latestValue - previousValue).toFixed(1).replace('.', ',')} ${unit}`
  const matches = nav.filter(([, label]) => label.toLocaleLowerCase('pt-BR').includes(search.toLocaleLowerCase('pt-BR')))
  const selectPage = (value: Page) => { setPage(value); setSearchOpen(false); setSearch('') }

  let content: React.ReactNode
  if (page === 'home') content = <>
    <header className="pagehead"><div><div className="eyebrow">Visão geral</div><h1>Dados da sua conexão</h1><p>Veja o que o computador conseguiu medir e coletar.</p></div><button className="btn primary" disabled={speedState === 'running' || !speedListenersReady} onClick={() => void runSpeedtest()}>Medir agora</button></header>
    <div className="grid two"><Card className="hero"><div className="card-top"><Badge tone={statusTone(latest)}>{latest ? measurementStatus(latest.status) : 'Sem medição'}</Badge></div><h2>{measurementSummary(latest)}</h2><p>{latest ? `Última coleta: ${dateTime(latest.capturedAtMs)}` : 'Inicie uma medição para registrar download, upload e latência.'}</p><div className="actions"><button className="btn primary" disabled={speedState === 'running' || !speedListenersReady} onClick={() => void runSpeedtest()}>Testar velocidade</button><button className="btn" onClick={() => setPage('data')}>Ver dados</button></div></Card><Card><h3>Conexão ativa</h3><div className="rows"><Value label="Interface" value={snapshot?.interfaceName ?? unavailable} /><Value label="Tipo" value={snapshot?.connectionType ?? unavailable} /><Value label="IP local" value={snapshot?.localIp ?? unavailable} /><Value label="Gateway" value={snapshot?.gateway ?? unavailable} /></div></Card></div>
    <div className="metrics"><Value label="Download" value={metric(latest?.downloadMbps ?? null, 'Mbps')} /><Value label="Upload" value={metric(latest?.uploadMbps ?? null, 'Mbps')} /><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /></div>
    <div className="grid two"><Card><h3>Explorar dados</h3><button className="task" onClick={() => setPage('network')}>Adaptador, IP, DNS e Wi‑Fi <span>›</span></button><button className="task" onClick={() => setPage('gamer')}>Latência para uma rota de referência <span>›</span></button><button className="task" onClick={() => setPage('history')}>Comparar medições anteriores <span>›</span></button></Card><Card><h3>Limites da coleta</h3><p>Um campo indisponível indica que o dado não foi observado. A tela mostra valores medidos e sua origem, sem atribuir causa à conexão.</p><button className="btn" onClick={() => setPage('data')}>Ver evidências</button></Card></div>
  </>
  else if (page === 'speed') content = <>
    <header className="pagehead"><div><div className="eyebrow">Medição</div><h1>Teste de velocidade</h1><p>Download, upload, latência e variação coletados nesta conexão.</p></div></header>
    <p>Teste completo: cerca de 18 segundos de download e 18 de upload, além da latência. Pode transferir até 5 GB no total.</p>
    <div className="grid speed"><Card className="speedcard"><Badge tone={speedState === 'running' ? 'purple' : statusTone(latest)}>{speedState === 'running' ? 'Medição em andamento' : latest ? measurementStatus(latest.status) : 'Pronto para medir'}</Badge><div className="speedvalue">{speedState === 'running' ? '…' : latest?.downloadMbps == null ? '—' : latest.downloadMbps.toFixed(1).replace('.', ',')}<small> Mbps</small></div><p>{speedState === 'running' ? `Etapa: ${progress?.phase ?? 'iniciando'}${progress ? ` · ${Math.round(progress.percent)}%` : ''}` : latest ? `Última medição em ${dateTime(latest.capturedAtMs)}` : 'Sem medição registrada.'}</p>{speedState === 'running' && <progress value={progress?.percent ?? 0} max={100} aria-label="Progresso da medição" />}<div className="actions center"><button className="btn primary" disabled={speedState === 'running' || !speedListenersReady} onClick={() => void runSpeedtest()}>{speedState === 'running' ? 'Medindo…' : 'Iniciar medição'}</button>{speedState === 'running' && <button className="btn" disabled={!executionId} onClick={() => void cancelSpeedtest()}>Cancelar</button>}</div>{latest?.limitation && speedState !== 'running' && <Notice title="Limitação da medição" tone="warn">{latest.limitation}</Notice>}</Card><Card><h3>Valores coletados</h3><div className="rows"><Value label="Download" value={metric(latest?.downloadMbps ?? null, 'Mbps')} /><Value label="Upload" value={metric(latest?.uploadMbps ?? null, 'Mbps')} /><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /><Value label="Perda" value={metric(latest?.packetLossPercent ?? null, '%')} note={latest?.packetLossSource ?? undefined} /><Value label="Atraso sob carga" value={metric(latest?.bufferbloatMs ?? null, 'ms')} /></div></Card></div>
    {latest && <Card><h3>Etapas da última medição</h3>{latest.phases.length ? <div className="phase-list">{latest.phases.map((phase, index) => <div className="phase" key={`${phase.name}-${index}`}><div><strong>{phase.name}</strong><Badge tone={phase.status === 'COMPLETE' ? 'good' : 'warn'}>{phase.status}</Badge></div><small>{phase.method} · {phase.endpoint} · {phase.validSamples} amostras válidas · {phase.bytesTransferred ?? 'volume indisponível'} bytes{phase.fallbackUsed ? ' · fallback' : ''}</small>{phase.fallbackTrigger && <p>Fallback após {phase.fallbackTrigger.code}{phase.fallbackTrigger.httpStatus ? ` HTTP ${phase.fallbackTrigger.httpStatus}` : ''}.</p>}{phase.error && <p>{phase.error.code}{phase.error.httpStatus ? ` HTTP ${phase.error.httpStatus}` : ''}: {phase.error.message}</p>}</div>)}</div> : <p>Detalhes das etapas indisponíveis neste registro.</p>}</Card>}
    <Notice title="Origem e limites">A perda baseada em sondagem HTTP é uma estimativa, quando disponível. Campos sem coleta ficam indisponíveis; nenhum valor ausente é convertido em zero.</Notice>
  </>
  else if (page === 'data') content = <><header className="pagehead"><div><div className="eyebrow">Evidências</div><h1>Dados da conexão</h1><p>Valores coletados localmente, sem interpretação de causa.</p></div><button className="btn" onClick={() => void refresh()}>Atualizar</button></header><div className="grid two"><Card><h3>Última medição</h3><div className="rows"><Value label="Estado" value={latest ? measurementStatus(latest.status) : unavailable} /><Value label="Horário" value={latest ? dateTime(latest.capturedAtMs) : unavailable} /><Value label="Download" value={metric(latest?.downloadMbps ?? null, 'Mbps')} /><Value label="Upload" value={metric(latest?.uploadMbps ?? null, 'Mbps')} /><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /></div><button className="btn" onClick={() => setPage('speed')}>Ver etapas</button></Card><Card><h3>Rede ativa</h3><div className="rows"><Value label="Interface" value={snapshot?.interfaceName ?? unavailable} /><Value label="IP local" value={snapshot?.localIp ?? unavailable} /><Value label="Gateway" value={snapshot?.gateway ?? unavailable} /><Value label="DNS" value={snapshot?.dnsServers.join(', ') || unavailable} /></div><button className="btn" onClick={() => setPage('network')}>Ver rede</button></Card></div></>
  else if (page === 'gamer') content = <>
    <header className="pagehead"><div><div className="eyebrow">Medição</div><h1>Rota regional de referência</h1><p>Oito sondas UDP para uma rota pública em São Paulo. Esta rota não representa uma partida.</p></div><button className="btn primary" disabled={gamerBusy} onClick={() => void runGamerProbe()}>{gamerBusy ? 'Medindo…' : 'Medir rota'}</button></header>
    <Card><div className="rows"><label>Jogo de interesse<select value={selectedGame} onChange={event => setSelectedGame(event.target.value)}><option value="">Rota regional, sem jogo selecionado</option>{desktopGames.map(game => <option key={game.id} value={game.id}>{game.name}</option>)}</select></label><Value label="Região de referência disponível" value="São Paulo (sa-east-1)" /></div><p>O jogo selecionado serve apenas como contexto. A sonda usa a mesma rota regional para todos; não mede servidor oficial nem matchmaking.</p></Card>
    <div className="grid two"><Card><h3>Resultado da sessão</h3><div className="rows"><Value label="Estado" value={gamerResult?.status ?? unavailable} /><Value label="Latência mediana" value={metric(gamerResult?.medianMs ?? null, 'ms')} /><Value label="Jitter" value={metric(gamerResult?.jitterMs ?? null, 'ms')} /><Value label="Sondas sem eco" value={metric(gamerResult?.lossPercent ?? null, '%')} /><Value label="Pico" value={metric(gamerResult?.maxMs ?? null, 'ms')} /><Value label="P95" value={metric(gamerResult?.p95Ms ?? null, 'ms')} /></div></Card><Card><h3>Origem</h3><div className="rows"><Value label="Destino" value={gamerResult?.destination ?? 'gamelift-ping.sa-east-1.api.aws:7770'} /><Value label="Método" value={gamerResult?.method ?? 'UDP echo SQPB v1'} /><Value label="Horário" value={gamerResult ? dateTime(gamerResult.capturedAtMs) : unavailable} /></div></Card></div>
    {gamerResult && <Card><h3>Amostras em sequência</h3><div className="sample-list">{gamerResult.samplesMs.map((sample, index) => <Value key={index} label={`Sonda ${index + 1}`} value={metric(sample, 'ms')} />)}</div><Notice title="Limite da rota">{gamerResult.limitation}</Notice></Card>}
  </>
  else if (page === 'network') content = <><header className="pagehead"><div><div className="eyebrow">Rede local</div><h1>Conexão ativa</h1><p>Informações disponíveis pelo Windows e pelo adaptador.</p></div><button className="btn" onClick={() => void refresh()}>Atualizar</button></header><Card><div className="rows"><Value label="Adaptador" value={snapshot?.interfaceName ?? unavailable} /><Value label="Tipo" value={snapshot?.connectionType ?? unavailable} /><Value label="IP local" value={snapshot?.localIp ?? unavailable} /><Value label="Gateway" value={snapshot?.gateway ?? unavailable} /><Value label="DNS" value={snapshot?.dnsServers.join(', ') || unavailable} /><Value label="SSID" value={snapshot?.ssid ?? unavailable} /><Value label="Sinal" value={snapshot?.signalPercent == null ? unavailable : `${snapshot.signalPercent}%`} /><Value label="Canal" value={snapshot?.channel ?? unavailable} /><Value label="Identificador da rede" value={snapshot?.networkId ?? unavailable} /></div></Card>{snapshot?.capabilityNote && <Notice title="Limite da coleta">{snapshot.capabilityNote}</Notice>}</>
  else if (page === 'monitor') content = <><header className="pagehead"><div><div className="eyebrow">Rede local</div><h1>Monitoramento</h1><p>Sondas leves a cada {monitorStatus?.intervalMinutes ?? 15} minutos, inclusive com a janela minimizada na bandeja.</p></div></header><Card><div className="card-top"><div><h3>{monitorStatus?.enabled ? 'Monitor ativo' : 'Monitor pausado'}</h3><p>Fechar o aplicativo encerra a coleta. A preferência é restaurada ao abrir novamente.</p></div><button className={`toggle ${monitorStatus?.enabled ? 'on' : ''}`} role="switch" aria-label="Ativar monitoramento" aria-checked={monitorStatus?.enabled ?? false} disabled={monitorBusy || !monitorStatus} onClick={() => void toggleMonitor()} /></div><div className="rows"><Value label="Última sonda" value={monitorStatus?.latest ? dateTime(monitorStatus.latest.capturedAtMs) : unavailable} /><Value label="Estado" value={monitorStatus?.latest?.status ?? unavailable} /><Value label="Latência HTTP" value={metric(monitorStatus?.latest?.latencyMs ?? null, 'ms')} /><Value label="Resolução DNS" value={metric(monitorStatus?.latest?.dnsMs ?? null, 'ms')} /></div></Card><Card><h3>Últimas observações</h3>{monitorHistory.length ? <table><thead><tr><th>Quando</th><th>Latência</th><th>DNS</th><th>Estado</th><th></th></tr></thead><tbody>{monitorHistory.slice(0, 30).map(record => <tr key={record.id}><td>{dateTime(record.capturedAtMs)}</td><td>{metric(record.latencyMs, 'ms')}</td><td>{metric(record.dnsMs, 'ms')}</td><td>{record.status}</td><td><button className="btn small" onClick={() => void deleteRecord('monitor', record.id)}>Excluir</button></td></tr>)}</tbody></table> : <p>Nenhuma observação registrada.</p>}</Card><Notice title="Limite da sonda">O monitor mede resposta HTTP e DNS, sem download ou upload. Falha ou ausência de eco não identifica a causa.</Notice></>
  else if (page === 'history') content = <>
    <header className="pagehead"><div><div className="eyebrow">Registros</div><h1>Histórico local</h1><p>Medições e observações guardadas neste computador.</p></div><div className="actions"><button className="btn" onClick={() => void refresh()}>Atualizar</button><button className="btn" onClick={() => void exportHistory()}>Exportar CSV</button><button className="btn" onClick={() => void deleteAllHistory()}>Excluir tudo</button></div></header>
    <Card><h3>Prévia da exportação</h3><p>O CSV local contém horário, origem, estado, métricas, método, destino, duração, tentativas, amostras, bytes e erros. Campos ausentes ficam vazios.</p><label className="checkbox"><input type="checkbox" checked={includeNetworkFields} onChange={event => setIncludeNetworkFields(event.target.checked)} /> Incluir SSID, IP local e identificador da rede</label>{includeNetworkFields && <div className="rows"><Value label="SSID da última medição" value={history[0]?.ssid ?? unavailable} /><Value label="IP local da última medição" value={history[0]?.localIp ?? unavailable} /><Value label="Identificador da última medição" value={history[0]?.networkId ?? unavailable} /></div>}<small>A prévia mostra a última medição; o arquivo inclui os registros existentes conforme a opção acima.</small></Card>
    <Card><label>Filtrar por rede<select value={networkFilter} onChange={event => setNetworkFilter(event.target.value)}><option value="all">Todas as redes e registros sem identificação</option>{networks.map(([id, label]) => <option key={id} value={id}>{label} · {id}</option>)}</select></label><p>Comparações aparecem somente entre medições completas da mesma rede identificada.</p>{comparable.length === 2 && <div className="rows"><Value label="Variação de download" value={delta(comparable[0].downloadMbps!, comparable[1].downloadMbps!, 'Mbps')} /><Value label="Variação de latência" value={delta(comparable[0].latencyMs!, comparable[1].latencyMs!, 'ms')} /></div>}</Card>
    <Card className="flush"><table><thead><tr><th>Quando</th><th>Rede</th><th>Download</th><th>Upload</th><th>Latência</th><th>Jitter</th><th>Estado</th><th></th></tr></thead><tbody>{filteredHistory.length ? filteredHistory.map(record => <tr key={record.executionId}><td>{dateTime(record.capturedAtMs)}</td><td>{record.ssid || record.networkId || 'Não identificada'}</td><td>{metric(record.downloadMbps, 'Mbps')}</td><td>{metric(record.uploadMbps, 'Mbps')}</td><td>{metric(record.latencyMs, 'ms')}</td><td>{metric(record.jitterMs, 'ms')}</td><td><Badge tone={statusTone(record)}>{measurementStatus(record.status)}</Badge></td><td><button className="btn small" onClick={() => void deleteRecord('speedtest', record.executionId)}>Excluir</button></td></tr>) : <tr><td colSpan={8}>Nenhuma medição neste filtro.</td></tr>}</tbody></table></Card><p>Observações do monitor aparecem na tela Monitoramento e também no CSV.</p>
  </>
  else if (page === 'tools') content = <><header className="pagehead"><div><div className="eyebrow">Rede</div><h1>Ferramentas</h1><p>Execute Ping, consulta DNS ou rastreamento de rota para um destino informado.</p></div></header><Card><div className="segmented">{(['ping', 'dns', 'route'] as const).map(kind => <button key={kind} className={tool === kind ? 'active' : ''} onClick={() => setTool(kind)}>{kind === 'ping' ? 'Ping' : kind === 'dns' ? 'DNS' : 'Rota'}</button>)}</div><label>Destino<input value={target} onChange={event => setTarget(event.target.value)} maxLength={253} placeholder="exemplo.com ou IP" /></label><div className="actions"><button className="btn primary" disabled={toolBusy || !target.trim()} onClick={() => void executeTool()}>{toolBusy ? 'Executando…' : 'Executar'}</button>{toolBusy && <button className="btn" onClick={() => void native.cancelTool()}>Cancelar</button>}</div>{toolResult && <><Badge tone={toolResult.status === 'COMPLETED' ? 'good' : 'warn'}>{toolResult.status}</Badge><Value label="Horário e duração" value={`${dateTime(toolResult.startedAtMs)} · ${toolResult.durationMs} ms`} /><pre>{toolResult.output || 'Nenhuma saída retornada.'}</pre>{toolResult.limitation && <Notice title="Limite">{toolResult.limitation}</Notice>}</>}</Card><Card><h3>Sequência de conectividade</h3><p>Observa gateway local quando disponível, resolução DNS e resposta de uma rota externa, nessa ordem.</p><div className="actions"><button className="btn" disabled={toolBusy} onClick={() => void runConnectivityFlow()}>Executar sequência</button>{toolBusy && <button className="btn" onClick={() => { flowCancelRequested.current = true; void native.cancelTool() }}>Cancelar</button>}</div>{!snapshot?.gateway && <Notice title="Gateway indisponível">A etapa local será omitida porque o Windows não informou um gateway.</Notice>}{flowResults.map((result, index) => <div className="phase" key={`${result.kind}-${index}`}><strong>{result.kind.toUpperCase()} · {result.target}</strong><Badge tone={result.status === 'COMPLETED' ? 'good' : 'warn'}>{result.status}</Badge><small>{dateTime(result.startedAtMs)} · {result.durationMs} ms</small><pre>{result.output || 'Sem saída.'}</pre></div>)}</Card></>
  else content = <><header className="pagehead"><div><div className="eyebrow">Preferências</div><h1>Configurações</h1><p>Escolhas locais deste computador.</p></div></header><div className="grid two"><Card><h3>Aparência</h3><div className="segmented"><button className={theme === 'light' ? 'active' : ''} onClick={() => setTheme('light')}>Claro</button><button className={theme === 'dark' ? 'active' : ''} onClick={() => setTheme('dark')}>Escuro</button></div></Card><Card><h3>Privacidade</h3><p>O histórico fica no armazenamento local do aplicativo. Nenhum dado de inventário é enviado automaticamente.</p></Card></div></>

  return <div className="shell"><div className="titlebar"><div className="brand"><i></i>SignallQ <small>Windows Desktop</small></div><div className="search-wrap"><input ref={searchRef} className="search" aria-label="Buscar tela ou ferramenta" placeholder="⌕ Buscar tela ou ferramenta · Ctrl K" value={search} onFocus={() => setSearchOpen(true)} onChange={event => setSearch(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && matches[0]) selectPage(matches[0][0]) }} />{searchOpen && <div className="search-results" role="listbox">{matches.length ? matches.map(([id, label]) => <button key={id} role="option" aria-selected={page === id} onMouseDown={event => event.preventDefault()} onClick={() => selectPage(id)}>{label}</button>) : <span>Nenhuma tela encontrada.</span>}</div>}</div></div><aside className="sidebar"><div className="navtitle">MEDIR</div>{nav.slice(0, 4).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}</button>)}<div className="navtitle">REDE</div>{nav.slice(4, 6).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}</button>)}<div className="navtitle">EXPLORAR</div>{nav.slice(6, 8).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}</button>)}<div className="sidebarBottom"><button className={page === 'settings' ? 'nav active' : 'nav'} onClick={() => setPage('settings')}><span>⚙</span>Configurações</button><div className="connection"><b>{networkLabel}</b><small>{snapshot?.connectionType ?? 'Atualizando…'}</small></div></div></aside><main><div className="page">{message && <Notice title="Ação não concluída" tone="bad">{message}</Notice>}{successMessage && <Notice title="Concluído" tone="good">{successMessage}</Notice>}{content}</div></main><footer><span>● {snapshot ? 'Rede local' : 'Rede indisponível'}</span><span>{title}</span></footer></div>
}
