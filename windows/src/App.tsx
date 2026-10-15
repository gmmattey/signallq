import { useEffect, useMemo, useRef, useState } from 'react'
import { limitationTitle, metric, qualitySummary } from './metrics'
import { native, type Measurement, type NetworkSnapshot, type ToolResult } from './native'

type Page = 'home' | 'speed' | 'diagnosis' | 'gamer' | 'network' | 'monitor' | 'history' | 'assist' | 'tools' | 'settings'
const nav: Array<[Page, string, string]> = [
  ['home', 'Visão geral', '⌂'], ['speed', 'Velocidade', '◴'], ['diagnosis', 'Diagnóstico', '◇'], ['gamer', 'Modo gamer', '◈'],
  ['network', 'Minha rede', '⌁'], ['monitor', 'Monitoramento', '⌇'], ['history', 'Histórico', '◷'], ['assist', 'Assist', '✦'],
  ['tools', 'Ferramentas', '⌘'], ['settings', 'Configurações', '⚙'],
]

function Card({ children, className = '' }: { children: React.ReactNode; className?: string }) { return <section className={`card ${className}`}>{children}</section> }
function Badge({ children, tone = '' }: { children: React.ReactNode; tone?: string }) { return <span className={`badge ${tone}`}>{children}</span> }
function Value({ label, value, note }: { label: string; value: string; note?: string }) { return <div className="value"><span>{label}</span><strong>{value}</strong>{note && <small>{note}</small>}</div> }
function Notice({ title, children, tone = '' }: { title: string; children: React.ReactNode; tone?: string }) { return <div className={`notice ${tone}`}><strong>{title}</strong><p>{children}</p></div> }
function errorText(error: unknown) { return error instanceof Error ? error.message : 'Não foi possível concluir esta operação.' }

export function App() {
  const [page, setPage] = useState<Page>('home')
  const [theme, setTheme] = useState<'light' | 'dark'>(() => localStorage.getItem('sqw-theme') === 'dark' ? 'dark' : 'light')
  const [snapshot, setSnapshot] = useState<NetworkSnapshot | null>(null)
  const [history, setHistory] = useState<Measurement[]>([])
  const [latest, setLatest] = useState<Measurement | null>(null)
  const [speedState, setSpeedState] = useState<'idle' | 'running' | 'error'>('idle')
  const [message, setMessage] = useState<string | null>(null)
  const [monitoring, setMonitoring] = useState(false)
  const [tool, setTool] = useState<'ping' | 'dns' | 'route'>('ping')
  const [target, setTarget] = useState('1.1.1.1')
  const [toolResult, setToolResult] = useState<ToolResult | null>(null)
  const speedtestInFlight = useRef(false)

  const refresh = async () => {
    try { const [network, records] = await Promise.all([native.network(), native.history()]); setSnapshot(network); setHistory(records); setLatest(records[0] ?? null) }
    catch (error) { setMessage(errorText(error)) }
  }
  useEffect(() => { void refresh() }, [])
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem('sqw-theme', theme) }, [theme])
  useEffect(() => {
    if (!monitoring) return
    const timer = window.setInterval(() => { void runSpeedtest(true) }, 15 * 60_000)
    return () => window.clearInterval(timer)
  // Monitoring is intentionally active only while the app window exists in this foundation.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [monitoring])

  const runSpeedtest = async (fromMonitor = false) => {
    if (speedtestInFlight.current) return
    speedtestInFlight.current = true
    setSpeedState('running'); setMessage(null)
    try {
      const measurement = await native.speedtest()
      setLatest(measurement); setHistory(await native.history()); setSpeedState('idle')
      if (!fromMonitor) setPage('speed')
    } catch (error) { setSpeedState('error'); setMessage(errorText(error)) }
    finally { speedtestInFlight.current = false }
  }
  const executeTool = async () => {
    setMessage(null); setToolResult(null)
    try { setToolResult(await native.tool(tool, target)) } catch (error) { setMessage(errorText(error)) }
  }
  const title = nav.find(([id]) => id === page)?.[1] ?? 'SignallQ'
  const networkLabel = snapshot?.ssid ?? snapshot?.connectionType ?? 'Verificando rede…'
  const historyRows = useMemo(() => history.slice(0, 30), [history])

  let content: React.ReactNode
  if (page === 'home') content = <>
    <header className="pagehead"><div><div className="eyebrow">Visão geral</div><h1>Sua conexão, sem mistério.</h1><p>Veja o que foi medido e saiba qual é o próximo passo.</p></div><button className="btn primary" onClick={() => void runSpeedtest()}>Verificar conexão</button></header>
    <div className="grid two"><Card className="hero"><div className="card-top"><Badge tone={latest?.status === 'COMPLETED' ? 'good' : 'purple'}>{latest?.status === 'COMPLETED' ? 'Medição disponível' : 'Aguardando medição'}</Badge></div><h2>{qualitySummary(latest)}</h2><p>O SignallQ mostra fatos medidos primeiro. Causa provável e recomendação exigem evidência suficiente.</p><div className="actions"><button className="btn primary" onClick={() => void runSpeedtest()}>Testar velocidade</button><button className="btn" onClick={() => setPage('diagnosis')}>Ver diagnóstico</button></div></Card><Card><h3>Conexão ativa</h3><div className="route"><b>Computador</b><i></i><b>Roteador</b><i></i><b>Internet</b></div><div className="rows"><Value label="Interface" value={snapshot?.interfaceName ?? 'Indisponível'} /><Value label="Conexão" value={networkLabel} /><Value label="Gateway" value={snapshot?.gateway ?? 'Indisponível'} /></div></Card></div>
    <div className="metrics"><Value label="Download" value={metric(latest?.downloadMbps ?? null, 'Mbps')} note="Última medição válida"/><Value label="Upload" value={metric(latest?.uploadMbps ?? null, 'Mbps')} /><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /></div>
    <div className="grid two"><Card><h3>O que você quer fazer?</h3><button className="task" onClick={() => setPage('gamer')}>Minha rede está pronta para jogar? <span>›</span></button><button className="task" onClick={() => setPage('diagnosis')}>Descobrir a causa de um problema <span>›</span></button><button className="task" onClick={() => setPage('monitor')}>Acompanhar a estabilidade <span>›</span></button></Card><Card><div className="card-top"><h3>✦ Assist</h3><Badge tone="purple">CONTEXTUAL</Badge></div><h2>Os números não precisam ser a parte difícil.</h2><p>O Assist será habilitado quando existir autorização desktop segura para o serviço remoto.</p><button className="btn secondary" onClick={() => setPage('assist')}>Entender disponibilidade</button></Card></div>
  </>
  else if (page === 'speed') content = <>
    <header className="pagehead"><div><div className="eyebrow">Analisar</div><h1>Teste de velocidade</h1><p>Velocidade, tempo de resposta e variação em uma medição local real.</p></div></header>
    <div className="grid speed"><Card className="speedcard"><Badge tone={speedState === 'running' ? 'purple' : latest?.status === 'COMPLETED' ? 'good' : ''}>{speedState === 'running' ? 'Medição em andamento' : latest?.status === 'COMPLETED' ? 'Última medição concluída' : 'Pronto para testar'}</Badge><div className="speedvalue">{speedState === 'running' ? '…' : latest?.downloadMbps == null ? '—' : latest.downloadMbps.toFixed(1).replace('.', ',')}<small> Mbps</small></div><p>{speedState === 'running' ? 'Medindo latência, download e upload. Mantenha a janela aberta.' : 'Download da última medição válida'}</p><button className="btn primary" disabled={speedState === 'running'} onClick={() => void runSpeedtest()}>{speedState === 'running' ? 'Medindo…' : 'Iniciar teste real'}</button>{latest?.limitation && <Notice title={limitationTitle(latest.status)} tone="warn">{latest.limitation}</Notice>}</Card><Card><h3>Detalhes medidos</h3><div className="rows"><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /><Value label="Perda estimada" value={metric(latest?.packetLossPercent ?? null, '%')} note={latest?.packetLossSource === 'estimated_http_probe' ? 'Por sondagens HTTP' : undefined}/><Value label="Atraso sob carga" value={metric(latest?.bufferbloatMs ?? null, 'ms')} /></div></Card></div>
    <Notice title="Como esta medição é interpretada">O teste não atribui culpa ao Wi-Fi, roteador ou provedor. Perda por sondagem HTTP é uma estimativa. Atraso sob carga fica indisponível até a coleta concorrente ser validada.</Notice>
  </>
  else if (page === 'diagnosis') content = <><header className="pagehead"><div><div className="eyebrow">Analisar</div><h1>Diagnóstico da conexão</h1><p>As evidências vêm antes da conclusão.</p></div></header><div className="grid two"><Card><h3>Evidências locais disponíveis</h3><div className="rows"><Value label="Interface" value={snapshot?.connectionType ?? 'Indisponível'} /><Value label="Latência" value={metric(latest?.latencyMs ?? null, 'ms')} /><Value label="Jitter" value={metric(latest?.jitterMs ?? null, 'ms')} /><Value label="Perda estimada" value={metric(latest?.packetLossPercent ?? null, '%')} /></div></Card><Card><h3>Próximo passo</h3><Notice title={latest ? 'Dados coletados, conclusão remota indisponível' : 'Ainda faltam dados'}>{latest ? 'A avaliação determinística remota exige autenticação desktop que ainda não existe. Não vamos inventar uma causa local.' : 'Execute uma medição antes de procurar padrões de instabilidade.'}</Notice><button className="btn primary" onClick={() => void runSpeedtest()}>Medir agora</button></Card></div></>
  else if (page === 'gamer') content = <><header className="pagehead"><div><div className="eyebrow">Analisar</div><h1>Modo gamer</h1><p>Estabilidade de uma rota regional de referência, não o ping de um jogo.</p></div></header><Card><h3>Sonda de rota</h3><p>Use Ping para medir uma rota pública de referência. O resultado não representa servidor oficial, matchmaking ou a partida.</p><div className="actions"><button className="btn primary" onClick={() => { setTool('ping'); setTarget('1.1.1.1'); setPage('tools') }}>Abrir sonda</button><button className="btn" onClick={() => setPage('history')}>Ver sessões salvas</button></div></Card><Notice title="Limite da medição">Sem endpoint conhecido e acessível, o SignallQ não chama uma rota de “ping do jogo”.</Notice></>
  else if (page === 'network') content = <><header className="pagehead"><div><div className="eyebrow">Sua rede</div><h1>Conexão ativa</h1><p>O Windows informa o que o adaptador e o driver disponibilizam.</p></div><button className="btn" onClick={() => void refresh()}>Atualizar</button></header><Card><div className="rows"><Value label="Adaptador" value={snapshot?.interfaceName ?? 'Indisponível'} /><Value label="Tipo" value={snapshot?.connectionType ?? 'Indisponível'} /><Value label="IP local" value={snapshot?.localIp ?? 'Indisponível'} /><Value label="Gateway" value={snapshot?.gateway ?? 'Indisponível'} /><Value label="DNS" value={snapshot?.dnsServers.join(', ') || 'Indisponível'} /><Value label="Wi‑Fi" value={snapshot?.ssid ?? 'Indisponível'} /><Value label="Sinal" value={snapshot?.signalPercent == null ? 'Indisponível' : `${snapshot.signalPercent}%`} /><Value label="Canal" value={snapshot?.channel ?? 'Indisponível'} /></div></Card>{snapshot?.capabilityNote && <Notice title="Limite da plataforma">{snapshot.capabilityNote}</Notice>}</>
  else if (page === 'monitor') content = <><header className="pagehead"><div><div className="eyebrow">Sua rede</div><h1>Monitoramento</h1><p>Medições reais são registradas a cada 15 minutos enquanto esta janela permanece aberta.</p></div></header><Card><div className="card-top"><div><h3>{monitoring ? 'Monitoramento ativo' : 'Monitoramento pausado'}</h3><p>Não continua após sair do aplicativo nesta fundação.</p></div><button className={`toggle ${monitoring ? 'on' : ''}`} role="switch" aria-checked={monitoring} onClick={() => setMonitoring(!monitoring)} /></div><div className="actions"><button className="btn primary" onClick={() => void runSpeedtest(true)}>Verificar agora</button><button className="btn" onClick={() => setPage('history')}>Abrir histórico</button></div></Card><Notice title="Consumo controlado">O monitoramento não registra um número inventado quando a rede ou o serviço de medição falha.</Notice></>
  else if (page === 'history') content = <><header className="pagehead"><div><div className="eyebrow">Explorar</div><h1>Histórico</h1><p>Medições guardadas localmente no banco SQLite deste aplicativo.</p></div><button className="btn" onClick={() => void refresh()}>Atualizar</button></header><Card className="flush"><table><thead><tr><th>Quando</th><th>Download</th><th>Latência</th><th>Jitter</th><th>Estado</th></tr></thead><tbody>{historyRows.length ? historyRows.map(record => <tr key={record.executionId}><td>{new Date(record.capturedAtMs).toLocaleString('pt-BR')}</td><td>{metric(record.downloadMbps, 'Mbps')}</td><td>{metric(record.latencyMs, 'ms')}</td><td>{metric(record.jitterMs, 'ms')}</td><td><Badge tone={record.status === 'COMPLETED' ? 'good' : 'warn'}>{record.status === 'COMPLETED' ? 'Concluído' : 'Parcial'}</Badge></td></tr>) : <tr><td colSpan={5}>Nenhuma medição válida salva ainda.</td></tr>}</tbody></table></Card></>
  else if (page === 'assist') content = <><header className="pagehead"><div><div className="eyebrow">Explorar</div><h1>Assist</h1><p>Explicações precisam de evidência e uma integração segura.</p></div></header><Card><Badge tone="warn">Indisponível nesta versão</Badge><h2>O Assist não foi simulado.</h2><p>O gateway atual não possui autenticação própria para um cliente Windows distribuído. Até esse contrato existir, nenhum token é embutido e nenhuma resposta pré-definida é exibida como análise.</p></Card></>
  else if (page === 'tools') content = <><header className="pagehead"><div><div className="eyebrow">Explorar</div><h1>Ferramentas de rede</h1><p>Operações locais com alvo validado, timeout e saída limitada.</p></div></header><Card><div className="segmented">{(['ping', 'dns', 'route'] as const).map(kind => <button key={kind} className={tool === kind ? 'active' : ''} onClick={() => setTool(kind)}>{kind === 'ping' ? 'Ping' : kind === 'dns' ? 'DNS' : 'Rota'}</button>)}</div><label>Destino<input value={target} onChange={event => setTarget(event.target.value)} maxLength={253} placeholder="exemplo.com ou IP" /></label><button className="btn primary" onClick={() => void executeTool()}>Executar</button>{toolResult && <><Badge tone={toolResult.status === 'COMPLETED' ? 'good' : 'warn'}>{toolResult.status}</Badge><pre>{toolResult.output || 'Sem endereços retornados.'}</pre>{toolResult.limitation && <Notice title="Limite">{toolResult.limitation}</Notice>}</>}</Card></>
  else content = <><header className="pagehead"><div><div className="eyebrow">Preferências</div><h1>Do seu jeito.</h1><p>As escolhas locais ficam neste computador.</p></div></header><div className="grid two"><Card><h3>Aparência</h3><div className="segmented"><button className={theme === 'light' ? 'active' : ''} onClick={() => setTheme('light')}>Claro</button><button className={theme === 'dark' ? 'active' : ''} onClick={() => setTheme('dark')}>Escuro</button></div></Card><Card><h3>Privacidade</h3><p>Histórico permanece local. Não há sincronização nem transmissão automática de inventário nesta fundação.</p></Card></div><Notice title="Bandeja e inicialização">Essas integrações dependem da implementação e validação nativas no Windows antes de serem apresentadas como ativas.</Notice></>

  return <div className="shell"><div className="titlebar"><div className="brand"><i></i>SignallQ <small>Windows Desktop</small></div><div className="search">⌕ Buscar tela ou ferramenta <kbd>Ctrl K</kbd></div></div><aside className="sidebar"><div className="navtitle">ANALISAR</div>{nav.slice(0, 4).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}</button>)}<div className="navtitle">SUA REDE</div>{nav.slice(4, 6).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}</button>)}<div className="navtitle">EXPLORAR</div>{nav.slice(6, 9).map(([id, label, icon]) => <button key={id} className={page === id ? 'nav active' : 'nav'} onClick={() => setPage(id)}><span>{icon}</span>{label}{id === 'assist' && <em>CONTEXTUAL</em>}</button>)}<div className="sidebarBottom"><button className={page === 'settings' ? 'nav active' : 'nav'} onClick={() => setPage('settings')}><span>⚙</span>Configurações</button><div className="connection"><b>{networkLabel}</b><small>{snapshot?.connectionType ?? 'Atualizando…'}</small></div></div></aside><main><div className="page">{message && <Notice title="Ação não concluída" tone="bad">{message}</Notice>}{content}</div></main><footer><span>● {snapshot ? 'Dados locais' : 'Atualizando conexão'}</span><span>{title}</span></footer></div>
}
