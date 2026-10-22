import assert from 'node:assert/strict'
import test from 'node:test'
import { generateKeyPairSync, sign } from 'node:crypto'
import worker, { authenticateAccess, getAnalyticsPlatformFilter, handleDiagnosticsSummary, handleIngestAnalytics, handleIntegrationReadiness, handleProductAnalytics, handleSitesMetrics, normalizeAnalyticsTimestamp } from '../src/index.ts'
import { buildEnv, withMockedFetch } from './support.ts'

class IngestDb {
  readonly ids = new Set<string>()
  readonly statements: Array<{ sql: string; values: unknown[] }> = []

  prepare(sql: string) {
    const statement = {
      sql,
      values: [] as unknown[],
      bind(...values: unknown[]) { this.values = values; return this },
    }
    this.statements.push(statement)
    return statement
  }

  async batch(statements: Array<{ values: unknown[] }>) {
    return statements.map((statement) => {
      const id = String(statement.values[0])
      if (this.ids.has(id)) return { meta: { changes: 0 } }
      this.ids.add(id)
      return { meta: { changes: 1 } }
    })
  }
}

class ProductDb {
  readonly statements: Array<{ sql: string; values: unknown[] }> = []
  private firstCalls = 0
  private readonly sessionStartCount: number
  private readonly sessionEndCount: number

  constructor(sessionStartCount = 10, sessionEndCount = 8) {
    this.sessionStartCount = sessionStartCount
    this.sessionEndCount = sessionEndCount
  }

  prepare(sql: string) {
    const db = this
    return {
      values: [] as unknown[],
      bind(...values: unknown[]) { this.values = values; db.statements.push({ sql, values }); return this },
      async all() { return { results: [] } },
      async first() {
        db.firstCalls += 1
        switch (db.firstCalls) {
          case 1: return { avg_duration_ms: 42_000 }
          case 2: return { session_count: 8 }
          case 3: return { session_start_count: db.sessionStartCount, session_end_count: db.sessionEndCount }
          case 4: return { count: 455 }
          default: return { cohort_d1: 0, returned_d1: 0, cohort_d7: 0, returned_d7: 0, cohort_d30: 0, returned_d30: 0, total_devices: 0, avg_active_span_days: null, inactive_14d: 0, total_with_activity: 0 }
        }
      },
    }
  }
}

class DiagnosticSummaryDb {
  readonly statements: Array<{ sql: string; values: unknown[] }> = []

  prepare(sql: string) {
    const db = this
    return {
      values: [] as unknown[],
      bind(...values: unknown[]) { this.values = values; db.statements.push({ sql, values }); return this },
      async first() {
        return {
          total: 10, avg_latency_ms: 21.4, avg_jitter_ms: 2, avg_packet_loss: 0.1,
          avg_download_mbps: 100, avg_upload_mbps: 20, avg_score: 80,
          critical_count: 1, active_count: 2,
          with_score: 9, with_speed: 8, with_latency: 9, with_device: 10,
          with_dist_channel: 8, complete_count: 8,
        }
      },
    }
  }
}

class ReadinessDb {
  private readonly states: Record<string, Record<string, unknown> | null>

  constructor(states: Record<string, Record<string, unknown> | null> = {}) {
    this.states = states
  }

  prepare(_sql: string) {
    const states = this.states
    return {
      bind(key: string) {
        return {
          async first() {
            const state = states[key]
            return state ? { value: JSON.stringify(state) } : null
          },
        }
      },
    }
  }
}

test('normaliza milissegundos, aceita segundos e rejeita relógio inválido', () => {
  const now = 1_785_801_900
  assert.deepEqual(normalizeAnalyticsTimestamp(1_785_801_000_123, now), { timestamp: 1_785_801_000, normalized: true })
  assert.deepEqual(normalizeAnalyticsTimestamp(1_785_801_000, now), { timestamp: 1_785_801_000, normalized: false })
  assert.deepEqual(normalizeAnalyticsTimestamp(undefined, now), { timestamp: now, normalized: false })
  assert.deepEqual(normalizeAnalyticsTimestamp(9_999, now), { timestamp: null, normalized: false })
})

test('ingest informa normalização, rejeição parcial e idempotência por id', async () => {
  const db = new IngestDb()
  const env = buildEnv(db as any)
  const body = { events: [
    { id: 'milliseconds', name: 'session_start', timestamp: 1_785_801_000_123 },
    { id: 'missing', name: 'session_end' },
    { id: 'invalid', name: 'session_end', timestamp: 9_999 },
    { id: 'unknown', name: 'unlisted_event' },
  ] }
  const request = () => new Request('https://x/ingest/analytics', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })

  const first = await handleIngestAnalytics(request(), env)
  assert.deepEqual(await first.json(), { ok: true, inserted: 2, normalized: 1, rejected: 2, acceptedIds: ['milliseconds', 'missing'] })
  assert.equal(db.statements[0].values[3], 1_785_801_000)

  const second = await handleIngestAnalytics(request(), env)
  assert.deepEqual(await second.json(), { ok: true, inserted: 0, normalized: 1, rejected: 2, acceptedIds: ['milliseconds', 'missing'] })
})

test('ingest web usa produto derivado da credencial e só persiste eventos canônicos', async () => {
  const db = new IngestDb()
  const env = buildEnv(db as any)
  const request = new Request('https://x/ingest/analytics', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ events: [
      { id: 'view', name: 'screen_view', platform: 'web', screen_name: '/preco?utm=x#cta' },
      { id: 'cta', name: 'feature_used', platform: 'web', feature_id: 'web_store_cta_clicked', cta_id: 'app_store' },
      { id: 'invalid', name: 'feature_used', platform: 'web', feature_id: 'download' },
    ] }),
  })
  const response = await handleIngestAnalytics(request, env, 'lagcheck')
  const payload = await response.json() as { inserted: number; rejected: number }
  assert.equal(payload.inserted, 2)
  assert.equal(payload.rejected, 1)
  assert.equal(db.statements[0].values[17], 'lagcheck')
  assert.equal(db.statements[0].values[18], '/preco')
  assert.equal(db.statements[1].values[19], 'app_store')
})

class SitesDb {
  prepare(sql: string) {
    return {
      bind(..._values: unknown[]) { return this },
      async first() {
        if (sql.includes('observed_events')) return { observed_events: 0, sessions: 0, page_views: 0, store_cta_clicks: 0, test_starts: 0 }
        return null
      },
      async all() { return { results: [] } },
    }
  }
}

class PopulatedSitesDb {
  prepare(sql: string) {
    return {
      bind(..._values: unknown[]) { return this },
      async first() {
        if (sql.includes('observed_events')) return { observed_events: 4, sessions: 2, page_views: 3, store_cta_clicks: 1, test_starts: 1 }
        return null
      },
      async all() {
        if (sql.includes("strftime('%Y-%m-%d'")) return { results: [{ date: '2026-10-22', sessions: 2, page_views: 3, store_cta_clicks: 1, test_starts: 1 }] }
        if (sql.includes('GROUP BY page_path')) return { results: [{ path: '/teste', page_views: 3 }] }
        return { results: [] }
      },
    }
  }
}

test('métricas de sites mantêm fonte sem token e sem eventos explicitamente indisponível/sem dados', async () => {
  const response = await handleSitesMetrics(
    new Request('https://x/admin/metrics/sites?period=30d'),
    buildEnv(new SitesDb() as any),
  )
  const payload = await response.json() as {
    period: { key: string; from: string; to: string }
    products: Record<string, { conversions: { status: string; webSessions: number | null }; traffic: { status: string; visits: number | null; pageViews: number | null } }>
  }
  assert.equal(response.status, 200)
  assert.equal(payload.period.key, '30d')
  assert.match(payload.period.from, /^\d{4}-\d{2}-\d{2}T/)
  assert.equal(payload.products.signallq.conversions.status, 'no_data')
  assert.equal(payload.products.signallq.conversions.webSessions, null)
  assert.equal(payload.products.signallq.traffic.status, 'unavailable')
  assert.equal(payload.products.signallq.traffic.visits, null)
  assert.equal(payload.products.signallq.traffic.pageViews, null)

  const invalid = await handleSitesMetrics(new Request('https://x/admin/metrics/sites?period=1d'), buildEnv(new SitesDb() as any))
  assert.equal(invalid.status, 400)

  // O contrato canônico é 7d|30d|90d. Assim a SPA não volta a enviar
  // números puros e transformar uma resposta válida de fonte parcial em erro total.
  const missingSuffix = await handleSitesMetrics(new Request('https://x/admin/metrics/sites?period=30'), buildEnv(new SitesDb() as any))
  assert.equal(missingSuffix.status, 400)
})

test('ranking de páginas de sites preserva o contrato path/page_views', async () => {
  const response = await handleSitesMetrics(
    new Request('https://x/admin/metrics/sites?period=7d'),
    buildEnv(new PopulatedSitesDb() as any),
  )
  const payload = await response.json() as { products: { signallq: { traffic: { topPages: unknown; topReferrers: unknown } } } }
  assert.deepEqual(payload.products.signallq.traffic.topPages, [{ path: '/teste', page_views: 3 }])
  assert.equal(payload.products.signallq.traffic.topReferrers, null)
})

test('ranking de referrers Cloudflare preserva o contrato name/requests', async () => {
  const env = buildEnv(new PopulatedSitesDb() as any, {
    CLOUDFLARE_API_TOKEN: 'test-token', CLOUDFLARE_ACCOUNT_ID: 'account',
    SIGNALLQ_ZONE_TAG: 'signallq-zone', LAGCHECK_ZONE_TAG: 'lagcheck-zone', LINKA_ZONE_TAG: 'linka-zone',
  })
  const graphResponse = {
    data: { viewer: { zones: [{
      daily: [{ dimensions: { datetimeHour: '2026-10-22T00:00:00Z' }, sum: { visits: 2 } }],
      paths: [{ dimensions: { clientRequestPath: '/teste' }, count: 3 }],
      referrers: [{ dimensions: { clientRefererHost: 'google.com' }, count: 4 }],
    }] } },
  }
  await withMockedFetch((async () => new Response(JSON.stringify(graphResponse), { status: 200 })) as typeof fetch, async () => {
    const response = await handleSitesMetrics(new Request('https://x/admin/metrics/sites?period=7d'), env)
    const payload = await response.json() as { products: { signallq: { traffic: { topReferrers: unknown } } } }
    assert.deepEqual(payload.products.signallq.traffic.topReferrers, [{ name: 'google.com', requests: 4 }])
  })
})

test('Access bloqueia workers.dev, sessão legada e API admin sem JWT', async () => {
  const env = buildEnv(new SitesDb() as any)
  const direct = await worker.fetch(new Request('https://signallq-admin.buildealabs.workers.dev/admin/metrics/sites'), env)
  assert.equal(direct.status, 403)

  const legacyAuth = await worker.fetch(new Request('https://admin.signallq.com/admin/auth/login', { method: 'POST' }), env)
  assert.equal(legacyAuth.status, 410)

  const missingJwt = await worker.fetch(new Request('https://admin.signallq.com/admin/metrics/sites'), env)
  assert.equal(missingJwt.status, 401)
})

test('Access valida assinatura, issuer e audience antes de autorizar o painel', async () => {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const jwk = publicKey.export({ format: 'jwk' })
  const teamDomain = `access-${Date.now()}.cloudflareaccess.com`
  const audience = 'sites-admin-audience'
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url')
  const token = (claims: Record<string, unknown>) => {
    const header = encode({ alg: 'RS256', kid: 'test-key' })
    const payload = encode(claims)
    return `${header}.${payload}.${sign('RSA-SHA256', Buffer.from(`${header}.${payload}`), privateKey).toString('base64url')}`
  }
  const env = buildEnv(new SitesDb() as any, { TEAM_DOMAIN: teamDomain, ACCESS_AUD: audience })
  const mockFetch = (async () => new Response(JSON.stringify({ keys: [{ ...jwk, kid: 'test-key', kty: 'RSA' }] }), { status: 200 })) as typeof fetch
  const baseClaims = { iss: `https://${teamDomain}`, aud: [audience], exp: Math.floor(Date.now() / 1000) + 120, email: 'admin@signallq.com' }
  await withMockedFetch(mockFetch, async () => {
    const signedToken = token(baseClaims)
    const valid = await authenticateAccess(new Request('https://admin.signallq.com', { headers: { 'Cf-Access-Jwt-Assertion': signedToken } }), env)
    assert.equal(valid?.userId, 'admin@signallq.com')
    // Para assinatura RSA-2048, o Base64URL termina com 4 bits sem significado.
    // Alteramos apenas esses bits: o decoder permissivo antigo produzia os mesmos
    // bytes e aceitava a assinatura; o decoder canônico precisa rejeitar o JWT.
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
    const lastCharacter = signedToken.at(-1)!
    const currentIndex = alphabet.indexOf(lastCharacter)
    const alternateCharacter = alphabet[(currentIndex & 0b110000) | ((currentIndex + 1) & 0b001111)]
    const nonCanonical = `${signedToken.slice(0, -1)}${alternateCharacter}`
    const tampered = await authenticateAccess(new Request('https://admin.signallq.com', { headers: { 'Cf-Access-Jwt-Assertion': nonCanonical } }), env)
    assert.equal(tampered, null)
    const wrongAudience = await authenticateAccess(new Request('https://admin.signallq.com', { headers: { 'Cf-Access-Jwt-Assertion': token({ ...baseClaims, aud: ['other'] }) } }), env)
    assert.equal(wrongAudience, null)
    const wrongIssuer = await authenticateAccess(new Request('https://admin.signallq.com', { headers: { 'Cf-Access-Jwt-Assertion': token({ ...baseClaims, iss: 'https://other.cloudflareaccess.com' }) } }), env)
    assert.equal(wrongIssuer, null)
  })
})

test('analytics de produto assume Android, rejeita all e devolve qualidade da cobertura', async () => {
  assert.equal(getAnalyticsPlatformFilter(new URL('https://x/admin/analytics/product')), 'android')
  assert.equal(getAnalyticsPlatformFilter(new URL('https://x/admin/analytics/product?platform=web')), 'web')
  assert.equal(getAnalyticsPlatformFilter(new URL('https://x/admin/analytics/product?platform=all')), null)

  const db = new ProductDb()
  const env = buildEnv(db as any)
  const response = await handleProductAnalytics(new Request('https://x/admin/analytics/product?platform=android'), env)
  const payload = await response.json() as { platform: string; dataQuality: { sessionEndRate: number; isSessionMetricReliable: boolean; excludedHistoricalTimestampEvents: number } }
  assert.equal(response.status, 200)
  assert.equal(payload.platform, 'android')
  assert.equal(payload.dataQuality.sessionEndRate, 0.8)
  assert.equal(payload.dataQuality.isSessionMetricReliable, true)
  assert.equal(payload.dataQuality.excludedHistoricalTimestampEvents, 455)
  assert.ok(db.statements.some((statement) => statement.sql.includes('platform = ?') && statement.sql.includes("app_version = 'site'")))
  assert.ok(db.statements.some((statement) => statement.sql.includes("COUNT(DISTINCT CASE WHEN event_name = 'session_start'")))

  const invalid = await handleProductAnalytics(new Request('https://x/admin/analytics/product?platform=all'), env)
  assert.equal(invalid.status, 400)
})

test('cobertura de session_end só libera retenção e duração a partir de 80%', async () => {
  const unreliable = await handleProductAnalytics(
    new Request('https://x/admin/metrics/analytics/product?platform=web'),
    buildEnv(new ProductDb(1_000, 799) as any),
  )
  const unreliablePayload = await unreliable.json() as {
    platform: string
    avg_session_duration_ms: number | null
    retention: unknown[]
    dataQuality: { sessionEndRate: number; isSessionMetricReliable: boolean }
  }
  assert.equal(unreliablePayload.platform, 'web')
  assert.equal(unreliablePayload.dataQuality.sessionEndRate, 0.799)
  assert.equal(unreliablePayload.dataQuality.isSessionMetricReliable, false)
  assert.equal(unreliablePayload.avg_session_duration_ms, null)
  assert.deepEqual(unreliablePayload.retention, [])

  const reliable = await handleProductAnalytics(
    new Request('https://x/admin/metrics/analytics/product?platform=web'),
    buildEnv(new ProductDb(1_000, 800) as any),
  )
  const reliablePayload = await reliable.json() as {
    avg_session_duration_ms: number | null
    dataQuality: { sessionEndRate: number; isSessionMetricReliable: boolean }
  }
  assert.equal(reliablePayload.dataQuality.sessionEndRate, 0.8)
  assert.equal(reliablePayload.dataQuality.isSessionMetricReliable, true)
  assert.equal(reliablePayload.avg_session_duration_ms, 42_000)
})

test('resumo de diagnóstico expõe cobertura dos campos mínimos e limiar de interpretação', async () => {
  const db = new DiagnosticSummaryDb()
  const response = await handleDiagnosticsSummary(
    new Request('https://x/admin/metrics/diagnostics/summary?environment=production'),
    buildEnv(db as any),
  )
  const payload = await response.json() as {
    dataQuality: { completeDiagnostics: number; speed: number; isSufficientForNetworkMetrics: boolean }
  }
  assert.equal(response.status, 200)
  assert.deepEqual(payload.dataQuality, {
    completeDiagnostics: 80,
    score: 90,
    speed: 80,
    latency: 90,
    device: 100,
    distributionChannel: 80,
    isSufficientForNetworkMetrics: true,
  })
  assert.ok(db.statements[0].sql.includes('complete_count'))
  assert.ok(db.statements[0].sql.includes("NULLIF(dist_channel, '')"))
})

test('prontidão de integração expõe contrato, cobertura e fonte pronta com volume positivo', async () => {
  const response = await handleIntegrationReadiness(
    new Request('https://x/admin/integrations/readiness'),
    buildEnv(new ReadinessDb({
      firebase_sync: { syncedAt: new Date().toISOString(), eventsImported: 3, crashesImported: 2 },
    }) as any),
  )
  const payload = await response.json() as {
    generatedAt: string
    environment: string
    platform: string
    coverage: { totalIntegrations: number; readyIntegrations: number; nonReadyIntegrations: number; byState: Record<string, number> }
    integrations: Array<{ id: string; state: string; recordsReceived: number | null; apiReachability: string }>
  }
  const firebase = payload.integrations.find((item) => item.id === 'firebase_analytics')!
  const reviews = payload.integrations.find((item) => item.id === 'google_play_reviews')!
  assert.equal(firebase.state, 'ready')
  assert.equal(firebase.recordsReceived, 5)
  assert.equal(firebase.apiReachability, 'reachable')
  assert.equal(reviews.state, 'not_configured')
  assert.equal(reviews.recordsReceived, null)
  assert.match(payload.generatedAt, /^\d{4}-\d{2}-\d{2}T/)
  assert.equal(payload.environment, 'worker')
  assert.equal(payload.platform, 'android')
  assert.deepEqual(payload.coverage, { totalIntegrations: 4, readyIntegrations: 1, nonReadyIntegrations: 3, byState: { ready: 1, not_configured: 3 } })
})

test('prontidão mantém credencial ausente e API não verificada distintos de uma sincronização comprovada', async () => {
  const response = await handleIntegrationReadiness(
    new Request('https://x/admin/integrations/readiness'),
    buildEnv(new ReadinessDb() as any),
  )
  const payload = await response.json() as { integrations: Array<{ id: string; state: string; apiReachability: string }> }
  const firebase = payload.integrations.find((item) => item.id === 'firebase_analytics')!
  const vitals = payload.integrations.find((item) => item.id === 'google_play_vitals')!
  const reviews = payload.integrations.find((item) => item.id === 'google_play_reviews')!
  assert.equal(firebase.apiReachability, 'not_verified')
  assert.equal(firebase.state, 'not_synced')
  assert.equal(vitals.apiReachability, 'not_configured')
  assert.equal(vitals.state, 'not_configured')

  const noSyncResponse = await handleIntegrationReadiness(
    new Request('https://x/admin/integrations/readiness'),
    buildEnv(new ReadinessDb() as any, { GOOGLE_PLAY_CLIENT_EMAIL: 'test@signallq-test.iam.gserviceaccount.com', GOOGLE_PLAY_PRIVATE_KEY: 'test-play-key' }),
  )
  const noSyncPayload = await noSyncResponse.json() as { integrations: Array<{ id: string; state: string; apiReachability: string }> }
  const unsyncedVitals = noSyncPayload.integrations.find((item) => item.id === 'google_play_vitals')!
  assert.equal(unsyncedVitals.apiReachability, 'not_verified')
  assert.equal(unsyncedVitals.state, 'not_synced')
  assert.equal(reviews.state, 'not_configured')
})

test('prontidão nunca marca volume zero, sync ausente ou sync obsoleto como ready', async () => {
  const staleAt = new Date(Date.now() - 49 * 60 * 60 * 1000).toISOString()
  const futureAt = new Date(Date.now() + 60 * 60 * 1000).toISOString()
  const response = await handleIntegrationReadiness(
    new Request('https://x/admin/integrations/readiness'),
    buildEnv(new ReadinessDb({
      firebase_sync: { syncedAt: new Date().toISOString(), eventsImported: 0, crashesImported: 0 },
      google_play_tracks_sync: { syncedAt: staleAt, tracksCount: 2 },
      google_play_sync: { syncedAt: futureAt, reviewsSampled: 2 },
    }) as any, { GOOGLE_PLAY_CLIENT_EMAIL: 'test@signallq-test.iam.gserviceaccount.com', GOOGLE_PLAY_PRIVATE_KEY: 'test-play-key' }),
  )
  const payload = await response.json() as { coverage: { readyIntegrations: number; byState: Record<string, number> }; integrations: Array<{ id: string; state: string; recordsReceived: number | null }> }
  const firebase = payload.integrations.find((item) => item.id === 'firebase_analytics')!
  const vitals = payload.integrations.find((item) => item.id === 'google_play_vitals')!
  const tracks = payload.integrations.find((item) => item.id === 'google_play_tracks')!
  const reviews = payload.integrations.find((item) => item.id === 'google_play_reviews')!
  assert.equal(firebase.recordsReceived, 0)
  assert.equal(firebase.state, 'synced_without_data')
  assert.equal(vitals.state, 'not_synced')
  assert.equal(tracks.state, 'stale')
  assert.equal(reviews.state, 'stale')
  assert.equal(payload.coverage.readyIntegrations, 0)
  assert.deepEqual(payload.coverage.byState, { synced_without_data: 1, not_synced: 1, stale: 2 })
})
