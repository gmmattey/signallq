// Telemetria do site — reaproveita o pipeline de analytics já existente do
// SignallQ Admin/signallq-admin-worker (POST /ingest/analytics, whitelist
// VALID_ANALYTICS_EVENTS = feature_used|screen_view|session_start|session_end|
// feature_crash|battery_snapshot), em vez do vocabulário GA4 do protótipo
// (speed_test_started etc.), que criaria uma segunda taxonomia paralela fora
// do whitelist do worker. Sempre platform='web' (GH#442).
//
// Nunca chama o worker direto do navegador — passa pelo proxy server-side
// (functions/api/track.ts) que guarda a INGEST_KEY como secret do Pages,
// nunca exposta ao cliente.
import { TELEMETRY_ENDPOINT } from './config'
import { generateId } from './id'

const SESSION_KEY = 'signallq_site_session_id'
const APP_VERSION = 'site'

interface EventoAnalytics {
  id: string
  name: 'feature_used' | 'screen_view' | 'session_start' | 'session_end'
  timestamp: number
  session_id: string
  platform: 'web'
  app_version: string
  feature_id?: string
  screen_name?: string
  duration_ms?: number
}

function getSessionId(): string {
  try {
    let id = sessionStorage.getItem(SESSION_KEY)
    if (!id) {
      id = generateId()
      sessionStorage.setItem(SESSION_KEY, id)
    }
    return id
  } catch {
    return 'sem-sessao'
  }
}

function baseEvent(): Pick<EventoAnalytics, 'id' | 'timestamp' | 'session_id' | 'platform' | 'app_version'> {
  return {
    id: generateId(),
    timestamp: Date.now(),
    session_id: getSessionId(),
    platform: 'web',
    app_version: APP_VERSION,
  }
}

function send(events: EventoAnalytics[]) {
  if (typeof fetch === 'undefined' || events.length === 0) return
  const body = JSON.stringify({ events })
  // sendBeacon garante entrega mesmo em navegação/fechamento de aba; fetch com
  // keepalive como fallback (Safari antigo sem sendBeacon com content-type json).
  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' })
    navigator.sendBeacon(TELEMETRY_ENDPOINT, blob)
    return
  }
  fetch(TELEMETRY_ENDPOINT, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => {
    // telemetria nunca pode quebrar a experiência do usuário
  })
}

export function trackScreenView(screenName: string) {
  send([{ ...baseEvent(), name: 'screen_view', screen_name: screenName }])
}

export function trackFeatureUsed(featureId: string) {
  send([{ ...baseEvent(), name: 'feature_used', feature_id: featureId }])
}

let sessionStarted = false
let sessionStartedAt = 0

export function initTelemetryDeferred() {
  if (typeof window === 'undefined' || sessionStarted) return
  const start = () => {
    sessionStarted = true
    sessionStartedAt = Date.now()
    send([{ ...baseEvent(), name: 'session_start' }])
  }
  if ('requestIdleCallback' in window) window.requestIdleCallback(start, { timeout: 4000 })
  else setTimeout(start, 2000)

  window.addEventListener('beforeunload', () => {
    if (!sessionStarted) return
    send([
      {
        ...baseEvent(),
        name: 'session_end',
        duration_ms: Date.now() - sessionStartedAt,
      },
    ])
  })
}

// Feature ID do CTA de download da landing (dentro do mesmo evento
// feature_used já whitelistado no admin-worker — nenhuma mudança de schema).
export const FEATURE_DOWNLOAD_APP_CLICADO = 'download_app_clicado'

// Testes e ferramentas
export const FEATURE_TESTE_VELOCIDADE_INICIADO = 'teste_velocidade_iniciado'
export const FEATURE_TESTE_VELOCIDADE_CONCLUIDO = 'teste_velocidade_concluido'
export const FEATURE_TESTE_VELOCIDADE_ERRO = 'teste_velocidade_erro'

export const FEATURE_TESTE_PING_INICIADO = 'teste_ping_iniciado'
export const FEATURE_TESTE_PING_CONCLUIDO = 'teste_ping_concluido'
export const FEATURE_TESTE_PING_ERRO = 'teste_ping_erro'

export const FEATURE_TESTE_DNS_INICIADO = 'teste_dns_iniciado'
export const FEATURE_TESTE_DNS_CONCLUIDO = 'teste_dns_concluido'
export const FEATURE_TESTE_DNS_ERRO = 'teste_dns_erro'

export const FEATURE_TESTE_JITTER_INICIADO = 'teste_jitter_iniciado'
export const FEATURE_TESTE_JITTER_CONCLUIDO = 'teste_jitter_concluido'
export const FEATURE_TESTE_JITTER_ERRO = 'teste_jitter_erro'

// Descoberta do ecossistema
export const FEATURE_ECOSSISTEMA_LAGCHECK_CLICADO = 'ecossistema_lagcheck_clicado'
export const FEATURE_ECOSSISTEMA_LINKA_CLICADO = 'ecossistema_linka_clicado'
export const FEATURE_ECOSSISTEMA_WIFICASA_CLICADO = 'ecossistema_wificasa_clicado'
