// SignallQ Admin API Worker
// /admin/* exige sessão httpOnly via cookie (SIG-136 — auth própria via D1).
// /ingest/* exige Bearer INGEST_KEY (chave separada, scope limitado, vai no APK)
// OU SITE_INGEST_KEY (#1155 — chave dedicada ao SignallQ Site, mesmo scope).
// /health exige Bearer ADMIN_SECRET (retrocompat dev/monitoramento externo).
// Separar os secrets reduz o blast radius: vazar INGEST_KEY nao da acesso
// aos dados do admin. INGEST_KEY so pode escrever em /ingest/*.

import { sendDailyVisitReport } from "./dailyVisitReport.ts";
import { hashPassword, verifyPassword, createSession, validateSession, revokeSession } from './auth.ts'
import { getFirebaseAccessToken } from './firebaseAuth.ts'
import {
  handleRemoteConfigAdminGet,
  handleRemoteConfigAdminVersions,
  handleRemoteConfigAdminValidate,
  handleRemoteConfigAdminPublish,
  handleRemoteConfigAdminRollback,
  handleFeatureFlagsCatalogGet,
  handleFeatureFlagsSync,
} from './remoteConfigAdmin.ts'

// #788 — tipos mínimos do Cron Trigger (workerd runtime). O projeto não tem
// @cloudflare/workers-types instalado (gap pré-existente, mesma causa dos
// erros de tsc em Request/Response/D1Database já presentes no arquivo antes
// desta mudança) — declarados aqui só para não adicionar erros novos de tsc.
interface ScheduledEvent {
  readonly cron: string;
  readonly scheduledTime: number;
}
interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
  passThroughOnException(): void;
}

export interface Env {
  ALLOWED_ORIGIN: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_GA4_PROPERTY_ID: string;
  /** "true"/"false" — ver comentário em wrangler.toml (decisão 2026-07-11, GH#877/#878). */
  FIREBASE_SYNC_ENABLED: string;
  /** Mantido apenas para /health (retrocompat). NÃO protege mais /admin/*. */
  ADMIN_SECRET: string;
  /** Chave separada para ingest do app Android. Scope: POST /ingest/* apenas. */
  INGEST_KEY: string;
  /** #1155 — chave dedicada ao SignallQ Site (Pages Function track.ts), separada da
   * INGEST_KEY do app Android para não precisar rotacionar a chave já embutida no APK
   * publicado. Opcional até ser configurada em produção via `wrangler secret put`. */
  SITE_INGEST_KEY?: string;
  FIREBASE_CLIENT_EMAIL: string;
  FIREBASE_PRIVATE_KEY: string;
  /** GH#761 — service account com acesso à Android Publisher API (reviews). */
  GOOGLE_PLAY_CLIENT_EMAIL: string;
  GOOGLE_PLAY_PRIVATE_KEY: string;
  DB: D1Database;
  /** Pepper para PBKDF2 das senhas admin — SIG-136. */
  ADMIN_AUTH_PEPPER: string;
  /** #883 — token da GraphQL Analytics API (Account Analytics:Read). Sem custo, mas exige criação manual no dashboard Cloudflare (fora do alcance do worker). */
  CLOUDFLARE_API_TOKEN?: string;
  /** #883 — não é secret (já visível em wrangler.toml/dashboard), mas fica no Env para não hardcodear no handler. */
  CLOUDFLARE_ACCOUNT_ID?: string;
  CLOUDFLARE_D1_DATABASE_ID?: string;
  /** Refs #1446 — service binding pro signallq-diagnostic-worker (proxy de /admin/diagnostic/*).
   * Worker-to-Worker fetch entre dois *.workers.dev é bloqueado pela Cloudflare (erro 1042,
   * mesmo motivo do GH#767 no ai-diagnosis-worker) — service binding é a forma correta. */
  DIAGNOSTIC_WORKER?: { fetch: typeof fetch };
  /** Refs #1446 — secret compartilhada com o signallq-diagnostic-worker: prova pro worker de
   * destino que a chamada já passou pela sessão do admin-worker, sem exigir uma segunda sessão
   * (evita duas fontes de verdade de "quem é admin"). Sem esta secret, o proxy responde 502. */
  DIAGNOSTIC_PROXY_SECRET?: string;
  /** Webhook do Discord do informe diário de visitas (mesmo canal do Lagcheck). Secret via
   * `wrangler secret put DISCORD_WEBHOOK_URL`; sem ela o informe é simplesmente ignorado. */
  DISCORD_WEBHOOK_URL?: string;
}

function corsHeaders(env: Env): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": env.ALLOWED_ORIGIN,
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Environment",
    "Access-Control-Allow-Credentials": "true",
    "Access-Control-Max-Age": "86400",
  };
}

export function json(body: unknown, status = 200, env: Env): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders(env) },
  });
}

// CORS pra endpoints públicos de leitura (sem sessão/credential, ex.: /local-ads) — GH#1422
// pós-mortem: o Site (signallq.pages.dev) nunca conseguiu ler o catálogo de anúncios em
// produção porque `corsHeaders(env)` só libera `ALLOWED_ORIGIN` (signallq-admin-panel.pages.dev,
// origem do Console) — o browser bloqueava a resposta por CORS antes mesmo do client
// processar, e o catch de `buscarAnunciosLocais` engolia o erro silenciosamente, caindo
// sempre no placeholder. Origin aberto é seguro aqui porque o endpoint não usa
// Allow-Credentials nem devolve nada além de conteúdo de anúncio já público.
function publicJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Max-Age": "86400",
    },
  });
}

export function err(message: string, status: number, env: Env): Response {
  return json({ error: message }, status, env);
}

/** Mantido apenas para /health (retrocompat). */
function authenticate(request: Request, env: Env): boolean {
  const auth = request.headers.get("Authorization") ?? "";
  const [scheme, token] = auth.split(" ");
  return scheme === "Bearer" && token === env.ADMIN_SECRET;
}

/** Autentica rotas /ingest/* com a INGEST_KEY (scope limitado — so POST /ingest/*). */
function authenticateIngest(request: Request, env: Env): boolean {
  const auth = request.headers.get("Authorization") ?? "";
  const [scheme, token] = auth.split(" ");
  if (scheme !== "Bearer") return false;
  // Aceita INGEST_KEY (chave do app) OU ADMIN_SECRET (retrocompat e dev local)
  // OU SITE_INGEST_KEY (#1155 — chave dedicada ao Site, evita rotacionar a
  // INGEST_KEY já embutida no APK Android publicado).
  return (
    token === env.INGEST_KEY ||
    token === env.ADMIN_SECRET ||
    (!!env.SITE_INGEST_KEY && token === env.SITE_INGEST_KEY)
  );
}

// --- SIG-136: Auth por sessão ---

function getSessionToken(request: Request): string | null {
  const cookie = request.headers.get('Cookie') ?? ''
  const match = cookie.match(/(?:^|;\s*)session=([^;]+)/)
  return match ? match[1] : null
}

async function authenticateSession(request: Request, env: Env): Promise<{ userId: string; role: string } | null> {
  const token = getSessionToken(request)
  if (!token) return null
  return validateSession(token, env.DB)
}

// Refs #1446 — proxy de /admin/diagnostic/* pro signallq-diagnostic-worker.
// Motivo: o Console autentica só contra este worker (D1/sessão/admin_users próprios), mas os
// endpoints administrativos do motor de diagnóstico vivem num segundo worker com D1/sessão
// próprios e completamente separados. Em vez do Console logar duas vezes (duas fontes de "quem é
// admin"), este worker repassa a chamada pro diagnostic-worker via service binding, provando com
// DIAGNOSTIC_PROXY_SECRET que a sessão já foi validada aqui — o diagnostic-worker não deve (e não
// passa a) checar a própria sessão nesse caminho. O frontend nunca fala com o diagnostic-worker.
const DIAGNOSTIC_PROXY_SECRET_HEADER = 'X-Internal-Diagnostic-Proxy-Secret'
const DIAGNOSTIC_PROXY_USER_HEADER = 'X-Internal-Admin-User'
const DIAGNOSTIC_PROXY_ROLE_HEADER = 'X-Internal-Admin-Role'

async function proxyDiagnosticAdmin(
  request: Request,
  env: Env,
  session: { userId: string; role: string }
): Promise<Response> {
  if (!env.DIAGNOSTIC_WORKER || !env.DIAGNOSTIC_PROXY_SECRET) {
    return err('Proxy do diagnostic-worker não configurado (service binding ou secret ausente).', 502, env)
  }

  const url = new URL(request.url)
  // Host arbitrário — descartado pelo runtime, o service binding roteia pelo binding, não pela URL.
  const upstreamUrl = `https://signallq-diagnostic.internal${url.pathname}${url.search}`

  const upstreamHeaders = new Headers(request.headers)
  upstreamHeaders.delete('Cookie') // sessão do admin-worker não deve vazar pro segundo worker
  upstreamHeaders.set(DIAGNOSTIC_PROXY_SECRET_HEADER, env.DIAGNOSTIC_PROXY_SECRET)
  // Prefixo pra deixar claro no audit trail do diagnostic-worker (campo `author`, texto livre,
  // sem FK) que o id pertence ao espaço de admin_users do admin-worker, não ao dele próprio.
  upstreamHeaders.set(DIAGNOSTIC_PROXY_USER_HEADER, `admin-worker:${session.userId}`)
  upstreamHeaders.set(DIAGNOSTIC_PROXY_ROLE_HEADER, session.role)

  const upstreamRequest = new Request(upstreamUrl, {
    method: request.method,
    headers: upstreamHeaders,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
  })

  const upstreamResponse = await env.DIAGNOSTIC_WORKER.fetch(upstreamRequest)

  // Repassa status e corpo de forma transparente — não inventa formato de erro novo. Só troca os
  // headers de CORS pelos deste worker (o browser só fala com o admin-worker).
  const responseHeaders = new Headers(upstreamResponse.headers)
  for (const key of Object.keys(corsHeaders(env))) responseHeaders.delete(key)
  for (const [key, value] of Object.entries(corsHeaders(env))) responseHeaders.set(key, value)

  return new Response(upstreamResponse.body, { status: upstreamResponse.status, headers: responseHeaders })
}

/** Verifica rate limit: > 5 tentativas em 15 min por IP → bloqueado. */
async function checkRateLimit(ip: string, db: D1Database): Promise<boolean> {
  const now = Math.floor(Date.now() / 1000)
  const windowSec = 15 * 60
  const row = await db.prepare(
    'SELECT count, window_start FROM auth_rate_limit WHERE ip = ?'
  ).bind(ip).first<{ count: number; window_start: number }>()
  if (!row) return false
  if (now - row.window_start > windowSec) return false
  return row.count > 5
}

async function incrementRateLimit(ip: string, db: D1Database): Promise<void> {
  const now = Math.floor(Date.now() / 1000)
  const windowSec = 15 * 60
  const row = await db.prepare(
    'SELECT count, window_start FROM auth_rate_limit WHERE ip = ?'
  ).bind(ip).first<{ count: number; window_start: number }>()
  if (!row || now - row.window_start > windowSec) {
    await db.prepare(
      'INSERT OR REPLACE INTO auth_rate_limit (ip, count, window_start) VALUES (?, 1, ?)'
    ).bind(ip, now).run()
  } else {
    await db.prepare(
      'UPDATE auth_rate_limit SET count = count + 1 WHERE ip = ?'
    ).bind(ip).run()
  }
}

async function handleAuthLogin(request: Request, env: Env): Promise<Response> {
  const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown'
  if (await checkRateLimit(ip, env.DB)) {
    return err('Muitas tentativas. Tente novamente em 15 minutos.', 429, env)
  }

  let body: { email?: string; password?: string }
  try { body = await request.json() } catch { return err('body JSON inválido', 400, env) }
  const { email, password } = body
  if (!email || !password) return err('email e password obrigatórios', 400, env)

  const user = await env.DB.prepare(
    'SELECT id, password_hash, role FROM admin_users WHERE email = ? AND active = 1'
  ).bind(email).first<{ id: string; password_hash: string; role: string }>()

  if (!user || !(await verifyPassword(password, user.password_hash, env.ADMIN_AUTH_PEPPER))) {
    await incrementRateLimit(ip, env.DB)
    return err('E-mail ou senha inválidos', 401, env)
  }

  const token = await createSession(user.id, env.DB)
  await env.DB.prepare('UPDATE admin_users SET last_login = ? WHERE id = ?')
    .bind(Math.floor(Date.now() / 1000), user.id).run()

  return new Response(JSON.stringify({ ok: true, role: user.role }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Set-Cookie': `session=${token}; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=604800`,
      ...corsHeaders(env),
    },
  })
}

async function handleAuthLogout(request: Request, env: Env): Promise<Response> {
  const token = getSessionToken(request)
  if (token) await revokeSession(token, env.DB)
  return new Response(JSON.stringify({ ok: true }), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Set-Cookie': 'session=; HttpOnly; Secure; SameSite=None; Path=/; Max-Age=0',
      ...corsHeaders(env),
    },
  })
}

async function handleAuthMe(request: Request, env: Env): Promise<Response> {
  const session = await authenticateSession(request, env)
  if (!session) return err('Unauthorized', 401, env)
  const user = await env.DB.prepare(
    'SELECT email, role FROM admin_users WHERE id = ?'
  ).bind(session.userId).first<{ email: string; role: string }>()
  if (!user) return err('Unauthorized', 401, env)
  return json({ email: user.email, role: user.role }, 200, env)
}

async function handleAuthCreateUser(request: Request, env: Env, session: { userId: string; role: string }): Promise<Response> {
  if (session.role !== 'admin') return err('Forbidden', 403, env)
  let body: { email?: string; password?: string }
  try { body = await request.json() } catch { return err('body JSON inválido', 400, env) }
  const { email, password } = body
  if (!email || !password) return err('email e password obrigatórios', 400, env)

  const hash = await hashPassword(password, env.ADMIN_AUTH_PEPPER)
  const id = crypto.randomUUID()
  const now = Math.floor(Date.now() / 1000)

  try {
    await env.DB.prepare(
      'INSERT INTO admin_users (id, email, password_hash, role, active, created_at) VALUES (?, ?, ?, ?, 1, ?)'
    ).bind(id, email, hash, 'admin', now).run()
  } catch (e) {
    const msg = String(e)
    if (msg.includes('UNIQUE')) return err('E-mail já cadastrado', 409, env)
    throw e
  }

  return json({ ok: true, id }, 201, env)
}

async function handleAuthChangePassword(request: Request, env: Env, session: { userId: string; role: string }): Promise<Response> {
  let body: { currentPassword?: string; newPassword?: string }
  try { body = await request.json() } catch { return err('body JSON inválido', 400, env) }
  const { currentPassword, newPassword } = body
  if (!currentPassword || !newPassword) return err('currentPassword e newPassword obrigatórios', 400, env)

  const user = await env.DB.prepare(
    'SELECT password_hash FROM admin_users WHERE id = ?'
  ).bind(session.userId).first<{ password_hash: string }>()

  if (!user || !(await verifyPassword(currentPassword, user.password_hash, env.ADMIN_AUTH_PEPPER))) {
    return err('Senha atual incorreta', 401, env)
  }

  const hash = await hashPassword(newPassword, env.ADMIN_AUTH_PEPPER)
  await env.DB.prepare('UPDATE admin_users SET password_hash = ? WHERE id = ?')
    .bind(hash, session.userId).run()

  return json({ ok: true }, 200, env)
}

function periodToSeconds(period: string): number {
  const map: Record<string, number> = {
    "1d": 86400, "7d": 604800, "30d": 2592000, "90d": 7776000,
  };
  return map[period] ?? 604800;
}

function nowSec(): number {
  return Math.floor(Date.now() / 1000);
}

// GH#1342/#1344 (migration 016) — grava uma linha de histórico append-only por sync, além do
// cache "última sincronização" em admin_settings.
//
// ATENÇÃO — trim de 2026-07-24 (revisão de arquitetura da Claudete, doc de decisão removido em
// docs/archive/decisoes/ na Fase 4d do épico #1623, git preserva por SHA): este helper é reservado
// EXCLUSIVAMENTE a métricas numéricas de série temporal genuína, com necessidade real (não
// hipotética) de plotar tendência. Hoje isso é só Play Developer Reporting/ANR — único caller
// autorizado é a linha ~3165. Firebase Management, Remote Config, App Check, App Distribution e
// FCM Data API foram deliberadamente REMOVIDOS como callers: são estado/config pontual, já
// servidos por admin_settings (cache de "última sincronização"), sem requisito de produto pra
// histórico. NÃO adicionar chamada nova aqui pra nenhuma dessas 5 integrações — se um requisito
// real de tendência aparecer, desenhar a modelagem daquele caso específico, não reverter o trim
// por comodidade. payload é sempre o JSON bruto do registro completo, nunca só o valor numérico,
// para preservar campos novos que a fonte adicionar depois.
async function recordIntegrationSnapshot(env: Env, params: {
  provider: string;
  service: string;
  resource: string;
  metric?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  valueNumeric?: number | null;
  payload: unknown;
}): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO integration_metric_snapshots
     (provider, service, resource, metric, period_start, period_end, value_numeric, payload, synced_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    params.provider,
    params.service,
    params.resource,
    params.metric ?? null,
    params.periodStart ?? null,
    params.periodEnd ?? null,
    params.valueNumeric ?? null,
    JSON.stringify(params.payload),
    nowSec()
  ).run();
}

// Achado ao validar #883/#884 em produção: um valor corrompido em
// admin_settings.value (ex.: escrita manual malformada) derrubava
// generateAndPersistAlerts inteiro (500 em /admin/alerts) via JSON.parse sem
// tratamento — settings ausentes/corrompidas devem degradar para "{}" (nenhum
// teto configurado), nunca derrubar a rota. Tipo solto de propósito (mesmo
// padrão já usado no resto do arquivo, ex.: `let p: any` em handleIngestAiUsage).
function parseAdminSettings(value: string | undefined | null): any {
  if (!value) return {};
  try {
    return JSON.parse(value);
  } catch {
    return {};
  }
}

/**
 * Extrai filtro de environment da query string.
 * ?environment=production → filtra por 'production'
 * ?environment=all ou ausente → sem filtro (retorna null)
 */
function getEnvironmentFilter(url: URL): string | null {
  const env = url.searchParams.get("environment");
  return env === "all" || !env ? null : env;
}

/**
 * Extrai filtro de platform da query string (GH#442).
 * ?platform=android|web → filtra por origem do dado.
 * ?platform=all ou ausente → sem filtro (retorna null, mostra as duas origens).
 */
function getPlatformFilter(url: URL): string | null {
  const platform = url.searchParams.get("platform");
  return platform === "all" || !platform ? null : platform;
}

type AnalyticsPlatform = "android" | "web";

// Analytics de produto não pode agregar plataformas: o contrato legado sem
// parâmetro permanece Android, mas `all` deixa de ser uma opção válida.
export function getAnalyticsPlatformFilter(url: URL): AnalyticsPlatform | null {
  const platform = url.searchParams.get("platform");
  if (!platform) return "android";
  return platform === "android" || platform === "web" ? platform : null;
}

const ANALYTICS_TIMESTAMP_SECONDS_MIN = 1_577_836_800; // 2020-01-01 UTC
const ANALYTICS_TIMESTAMP_MILLISECONDS_MIN = ANALYTICS_TIMESTAMP_SECONDS_MIN * 1000;
const ANALYTICS_TIMESTAMP_FUTURE_TOLERANCE_SEC = 300;
const ANALYTICS_TIMESTAMP_CONTRACT_CUTOFF_SEC = 1_785_801_600; // 2026-08-04 UTC
const ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE =
  ` AND created_at < 100000000000 AND NOT (app_version = 'site' AND created_at < ${ANALYTICS_TIMESTAMP_CONTRACT_CUTOFF_SEC})`;

export type NormalizedAnalyticsTimestamp = {
  timestamp: number;
  normalized: boolean;
} | {
  timestamp: null;
  normalized: false;
};

/** Converte milissegundos explicitamente e recusa relógios fora do contrato. */
export function normalizeAnalyticsTimestamp(value: unknown, now: number): NormalizedAnalyticsTimestamp {
  if (value == null) return { timestamp: now, normalized: false };
  if (typeof value !== "number" || !Number.isInteger(value)) return { timestamp: null, normalized: false };

  const timestamp = value >= ANALYTICS_TIMESTAMP_MILLISECONDS_MIN ? Math.floor(value / 1000) : value;
  if (timestamp < ANALYTICS_TIMESTAMP_SECONDS_MIN || timestamp > now + ANALYTICS_TIMESTAMP_FUTURE_TOLERANCE_SEC) {
    return { timestamp: null, normalized: false };
  }
  return { timestamp, normalized: value !== timestamp };
}

/**
 * Extrai filtro de trilha do Play Console da query string (migration 012_play_track.sql).
 * ?play_track=internal|alpha|beta|production → filtra por trilha já sincronizada.
 * ?play_track=all ou ausente → sem filtro (mostra todas as trilhas, inclusive não mapeadas).
 */
function getPlayTrackFilter(url: URL): string | null {
  const playTrack = url.searchParams.get("play_track");
  return playTrack === "all" || !playTrack ? null : playTrack;
}

/**
 * GH#1042 — quando o filtro de ambiente for exatamente "production", exclui registros cuja
 * play_track já foi resolvida (via backfill, migration 012_play_track.sql) para uma trilha de
 * teste fechada/aberta do Play Console (internal/alpha/beta, ou qualquer trilha != "production").
 * Essas sessões têm environment="production" (dist_channel=play_store) mas não são usuário real
 * — são tester de trilha fechada. play_track ainda NULL (não mapeado pelo backfill) continua
 * contando como produção, preservando o comportamento anterior até o backfill rodar — nunca
 * assume "não é produção" só por ausência de dado.
 *
 * Não filtra nada quando envFilter é staging/all/null — só "production" recebe este refinamento
 * (play_track é irrelevante para as outras leituras). `columnPrefix` cobre queries com alias de
 * tabela (ex.: "au." em handleAiUsageRecords).
 */
function productionTrackClause(envFilter: string | null, columnPrefix = ""): string {
  if (envFilter !== "production") return "";
  return ` AND (${columnPrefix}play_track IS NULL OR ${columnPrefix}play_track = 'production')`;
}

// getFirebaseAccessToken foi extraída pra src/firebaseAuth.ts (groundwork #1478, 2026-07-26) —
// mesmo comportamento, só movida pra módulo próprio. Ver import no topo do arquivo.

async function queryBigQuery<T = unknown>(
  env: Env,
  query: string
): Promise<{ rows: T[]; error?: string }> {
  let token: string;
  try {
    token = await getFirebaseAccessToken(env);
  } catch (e) {
    return { rows: [], error: `auth_failed: ${String(e)}` };
  }

  const resp = await fetch(
    `https://bigquery.googleapis.com/bigquery/v2/projects/${env.FIREBASE_PROJECT_ID}/queries`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ query, useLegacySql: false, timeoutMs: 10000 }),
    }
  );

  if (!resp.ok) {
    const errText = await resp.text();
    if (errText.includes("Not found") || errText.includes("notFound")) {
      return { rows: [], error: "table_not_found" };
    }
    return { rows: [], error: `bq_error_${resp.status}: ${errText.slice(0, 200)}` };
  }

  const data = (await resp.json()) as {
    rows?: Array<{ f: Array<{ v: string | null }> }>;
    schema?: { fields: Array<{ name: string }> };
    jobComplete?: boolean;
  };

  if (!data.jobComplete || !data.rows || !data.schema) return { rows: [] };

  const fields = data.schema.fields.map((f) => f.name);
  const rows = data.rows.map((row) => {
    const obj: Record<string, string | null> = {};
    row.f.forEach((cell, i) => { obj[fields[i]] = cell.v; });
    return obj as unknown as T;
  });

  return { rows };
}

// #883 — GraphQL Analytics API do Cloudflare (api.cloudflare.com/client/v4/graphql).
// Incluída em todos os planos (inclusive Free), sem custo — não confundir com Workers
// Analytics Engine (esse sim é produto pago). Requer API Token escopado
// "Account Analytics: Read", criado manualmente no dashboard Cloudflare (o token OAuth
// do wrangler CLI não tem esse escopo e não pode criar tokens novos via API).
async function queryCloudflareGraphQL<T = unknown>(
  env: Env,
  query: string,
  variables: Record<string, unknown>
): Promise<{ data: T | null; error?: string }> {
  if (!env.CLOUDFLARE_API_TOKEN || !env.CLOUDFLARE_ACCOUNT_ID) {
    return { data: null, error: "not_configured" };
  }

  const resp = await fetch("https://api.cloudflare.com/client/v4/graphql", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.CLOUDFLARE_API_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ query, variables }),
  });

  if (!resp.ok) {
    return { data: null, error: `cf_graphql_${resp.status}` };
  }

  const body = (await resp.json()) as { data?: T; errors?: Array<{ message: string }> | null };
  if (body.errors && body.errors.length > 0) {
    return { data: null, error: body.errors.map((e) => e.message).join("; ").slice(0, 200) };
  }
  if (!body.data) return { data: null, error: "empty_response" };

  return { data: body.data };
}

// --- handlers /admin ---

// GH#1042 (implementado): os handlers abaixo que filtram por `environment = ?` (envFilter)
// agora também aplicam `productionTrackClause()` quando envFilter === "production" — exclui
// sessões cuja play_track já foi resolvida para trilha de teste fechada/aberta (internal/
// alpha/beta), que hoje contavam como "produção" só por dist_channel=play_store. Efeito
// prático nulo até o app passar pelo M3 (nenhuma trilha "production" existe em
// play_console_tracks ainda), mas o mecanismo fica pronto pro lançamento — ver ADR/issue #1042.
// Rodar `POST /admin/integrations/google-play/tracks/backfill` (idempotente) continua sendo
// passo manual separado, não automático.
async function handleOverview(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const todaySince = nowSec() - 86400;
  const envFilter = getEnvironmentFilter(url);
  const platformFilter = getPlatformFilter(url);

  const envClause    = envFilter ? " AND environment = ?" : "";
  const envBinds     = envFilter ? [envFilter]            : [];
  const platformClause = platformFilter ? " AND platform = ?" : "";
  const platformBinds  = platformFilter ? [platformFilter]    : [];
  const trackClause  = productionTrackClause(envFilter);
  const filterClause = `${envClause}${platformClause}${trackClause}`;
  const filterBinds  = [...envBinds, ...platformBinds];

  const [sessions, aiRows, successRows, networkRows, issueRows] = await Promise.all([
    env.DB.prepare(
      `SELECT COUNT(*) AS total,
              SUM(CASE WHEN resolved=0 THEN 1 ELSE 0 END) AS active,
              AVG(CAST(score AS REAL)) AS avg_score
       FROM diagnostic_sessions WHERE created_at >= ?${filterClause}`
    ).bind(since, ...filterBinds).first<{ total: number; active: number; avg_score: number | null }>(),
    env.DB.prepare(
      `SELECT COUNT(*) AS calls, SUM(cost_usd) AS cost, SUM(total_tokens) AS tokens
       FROM ai_usage WHERE created_at >= ?${filterClause}`
    ).bind(todaySince, ...filterBinds).first<{ calls: number; cost: number; tokens: number }>(),
    // successRate: % de sessões com status bom/regular (não ruim/critico/inconclusivo)
    env.DB.prepare(
      `SELECT COUNT(*) AS total,
              SUM(CASE WHEN status IN ('bom','excelente','regular') THEN 1 ELSE 0 END) AS successful
       FROM diagnostic_sessions WHERE created_at >= ?${filterClause}`
    ).bind(since, ...filterBinds).first<{ total: number; successful: number }>(),
    // mostTestType: tipo de rede predominante
    env.DB.prepare(
      `SELECT network_type, COUNT(*) AS cnt
       FROM diagnostic_sessions WHERE created_at >= ?${filterClause}
         AND network_type IS NOT NULL AND network_type != '' AND network_type != 'unknown'
       GROUP BY network_type ORDER BY cnt DESC LIMIT 1`
    ).bind(since, ...filterBinds).first<{ network_type: string; cnt: number }>(),
    // topProblem: issue mais frequente no período
    env.DB.prepare(
      `SELECT issues FROM diagnostic_sessions WHERE created_at >= ?${filterClause} AND issues != '[]'`
    ).bind(since, ...filterBinds).all(),
  ]);

  // Calcula successRate
  const total = successRows?.total ?? 0;
  const successRate = total > 0
    ? Math.round(((successRows?.successful ?? 0) / total) * 100)
    : null;

  // Determina topProblem a partir dos arrays JSON de issues
  const countMap: Record<string, number> = {};
  for (const row of (issueRows.results ?? [])) {
    let issues: string[] = [];
    try { issues = JSON.parse((row as any).issues ?? "[]"); } catch { /* ignora */ }
    for (const label of issues) {
      if (isRealIssueLabel(label)) {
        countMap[label] = (countMap[label] ?? 0) + 1;
      }
    }
  }
  const topEntry = Object.entries(countMap).sort(([, a], [, b]) => b - a)[0];
  const topProblem = topEntry ? topEntry[0] : null;

  // mostTestType
  const mostTestType = networkRows?.network_type ?? null;
  const mostTestTypeCount = networkRows?.cnt ?? 0;
  const mostTestTypePercentage = total > 0 && mostTestTypeCount > 0
    ? Math.round((mostTestTypeCount / total) * 100)
    : null;

  return json({
    source: "d1", period,
    environment:          envFilter ?? "all",
    platform:             platformFilter ?? "all",
    totalDiagnostics:     sessions?.total ?? 0,
    activeSessions:       sessions?.active ?? 0,
    avgNetworkScore:      sessions?.avg_score ? Math.round(sessions.avg_score) : 0,
    aiCallsToday:         aiRows?.calls  ?? 0,
    aiCostToday:          aiRows?.cost   ?? 0,
    aiTokensToday:        aiRows?.tokens ?? 0,
    successRate,
    topProblem,
    mostTestType,
    mostTestTypePercentage,
  }, 200, env);
}

async function handleDiagnostics(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const limit     = Math.min(parseInt(url.searchParams.get("limit") ?? "50"), 200);
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);
  const platformFilter = getPlatformFilter(url);
  const playTrackFilter = getPlayTrackFilter(url);

  const envClause = envFilter ? " AND environment = ?" : "";
  const envBinds  = envFilter ? [envFilter]            : [];
  const platformClause = platformFilter ? " AND platform = ?" : "";
  const platformBinds  = platformFilter ? [platformFilter]    : [];
  const playTrackClause = playTrackFilter ? " AND play_track = ?" : "";
  const playTrackBinds  = playTrackFilter ? [playTrackFilter]    : [];
  // GH#1042 — só aplica a exclusão implícita de trilha de teste quando o caller NÃO pediu
  // uma play_track explícita (evita contradição: ?environment=production&play_track=internal
  // precisa continuar retornando o tester pedido de propósito).
  const trackClause = playTrackFilter ? "" : productionTrackClause(envFilter);
  const filterClause = `${envClause}${platformClause}${playTrackClause}${trackClause}`;
  const filterBinds  = [...envBinds, ...platformBinds, ...playTrackBinds];

  const rows = await env.DB.prepare(
    `SELECT id, created_at, network_type, status, score,
            download_mbps, upload_mbps, latency_ms, jitter_ms, packet_loss,
            issues, resolved, operator,
            device_model, os_version, app_version, ai_summary_report,
            environment, dist_channel, build_type, version_code, device_id,
            rssi, banda_wifi, padrao_wifi, platform, play_track
     FROM diagnostic_sessions WHERE created_at >= ?${filterClause}
     ORDER BY created_at DESC LIMIT ?`
  ).bind(since, ...filterBinds, limit).all();

  const sessions = (rows.results ?? []).map((r: any) => ({
    id:                r.id,
    created_at:        r.created_at,
    network_type:      r.network_type,
    status:            r.status,
    score:             r.score,
    download_mbps:     r.download_mbps,
    upload_mbps:       r.upload_mbps,
    latency_ms:        r.latency_ms,
    jitter_ms:         r.jitter_ms,
    packet_loss:       r.packet_loss,
    issues:            JSON.parse(r.issues ?? "[]"),
    resolved:          r.resolved,
    operator:          r.operator,
    device_model:      r.device_model      ?? '',
    os_version:        r.os_version        ?? '',
    app_version:       r.app_version       ?? '',
    ai_summary_report: r.ai_summary_report ?? '',
    environment:       r.environment       ?? 'production',
    dist_channel:      r.dist_channel      ?? '',
    build_type:        r.build_type        ?? 'release',
    version_code:      r.version_code      ?? 0,
    device_id:         r.device_id         ?? '',
    rssi:              r.rssi              ?? null,
    banda_wifi:        r.banda_wifi        ?? null,
    padrao_wifi:       r.padrao_wifi       ?? null,
    // GH#442: origem do dado — 'android' | 'web'. Default 'android' preserva
    // semântica de dados anteriores à migration 011_gh442.sql.
    platform:          r.platform          ?? 'android',
    // migration 012_play_track.sql — trilha do Play Console (internal/alpha/beta/
    // production), preenchida via backfill. null = ainda não mapeada, nunca assumir
    // 'production' por padrão.
    play_track:        r.play_track        ?? null,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", platform: platformFilter ?? "all", playTrack: playTrackFilter ?? "all", sessions }, 200, env);
}

async function handleAiCost(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // SIG-125: inclui contagem de chamadas com resposta real (completion_tokens > 0)
  // para calcular reliabilityPercentage por modelo.
  // A tabela ai_usage não possui coluna status — o proxy de sucesso adotado é
  // completion_tokens > 0: a chamada gerou tokens de resposta, portanto foi bem-sucedida.
  // Mesmo critério usado em handleAiCostMetrics. Com tabela vazia, o campo retorna null.
  const rows = await env.DB.prepare(
    `SELECT model,
            COUNT(*) AS calls,
            SUM(total_tokens) AS tokens,
            SUM(cost_usd) AS cost_usd,
            SUM(CASE WHEN completion_tokens > 0 THEN 1 ELSE 0 END) AS successful_calls
     FROM ai_usage WHERE created_at >= ?${envClause} GROUP BY model ORDER BY calls DESC`
  ).bind(since, ...envBinds).all();

  // #879 (achado 4): reagrega por provedor (providerEnumId) — vários model IDs
  // técnicos podem pertencer ao mesmo provedor (ex.: variações do Gemini), e o
  // frontend usa `model` diretamente como o enum AiProvider (PROVIDER_COLORS),
  // não como o ID técnico bruto. Mesmo padrão de reagregação já usado em
  // handleAiUsageTimeline (byProvider a partir de múltiplos models).
  const porProvedor = new Map<string, { calls: number; tokens: number; cost_usd: number; successful: number; label: string }>();
  for (const r of (rows.results ?? []) as any[]) {
    const providerId = providerEnumId(r.model ?? "");
    const entry = porProvedor.get(providerId) ?? { calls: 0, tokens: 0, cost_usd: 0, successful: 0, label: providerName(r.model ?? "") };
    entry.calls      += r.calls           ?? 0;
    entry.tokens      += r.tokens          ?? 0;
    entry.cost_usd    += r.cost_usd        ?? 0;
    entry.successful  += r.successful_calls ?? 0;
    porProvedor.set(providerId, entry);
  }

  const byModel = Array.from(porProvedor.entries()).map(([providerId, e]) => ({
    model:                 providerId, // ID compatível com o enum AiProvider do frontend
    providerLabel:         e.label,    // rótulo humano, ex.: "Gemini", "Qwen / Workers AI"
    calls:                 e.calls,
    tokens:                e.tokens,
    cost_usd:              e.cost_usd,
    // null quando não há registros; valor calculado a 2 casas decimais caso contrário.
    reliabilityPercentage: e.calls > 0 ? Math.round((e.successful / e.calls) * 10000) / 100 : null,
  })).sort((a, b) => b.calls - a.calls);

  const totals = byModel.reduce(
    (acc: any, r: any) => ({
      calls:  acc.calls  + r.calls,
      tokens: acc.tokens + r.tokens,
      cost:   acc.cost   + r.cost_usd,
    }),
    { calls: 0, tokens: 0, cost: 0 }
  );

  return json({ source: "d1", period, environment: envFilter ?? "all", byModel, totals }, 200, env);
}

// --- handlers /admin/metrics (SIG-110) ---

async function handleTimeline(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // Agrega diagnostic_sessions por dia (DATE unix→ISO via strftime).
  // #879 (achado 1 da auditoria 2026-07-10): completedDiagnostics e activeUsers
  // usavam o MESMO COUNT(*) — as duas linhas do grafico "Sessoes vs. Diagnosticos"
  // eram sempre identicas. device_id (hash anonimo, coluna existe desde SIG-143)
  // permite contagem de usuarios unicos de verdade via COUNT(DISTINCT device_id),
  // mesmo padrao ja usado em handleProductAnalytics (PR #878) com session_id.
  // criticalAlerts: sessões com status='failed' ou score < 40 (limiar "Fraco").
  const rows = await env.DB.prepare(
    `SELECT
       strftime('%Y-%m-%d', datetime(created_at, 'unixepoch')) AS date,
       COUNT(*) AS completedDiagnostics,
       COUNT(DISTINCT NULLIF(device_id, '')) AS activeUsers,
       SUM(CASE WHEN status = 'failed' OR (score IS NOT NULL AND score < 40) THEN 1 ELSE 0 END) AS criticalAlerts
     FROM diagnostic_sessions
     WHERE created_at >= ?${envClause}
     GROUP BY date
     ORDER BY date ASC`
  ).bind(since, ...envBinds).all();

  const timeline = (rows.results ?? []).map((r: any) => ({
    date:                 r.date,
    completedDiagnostics: r.completedDiagnostics ?? 0,
    activeUsers:          r.activeUsers          ?? 0,
    criticalAlerts:       r.criticalAlerts        ?? 0,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", timeline }, 200, env);
}

async function handleNetworkInsights(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // SIG-132: stats completas por network_type.
  // Window function SUM(COUNT(*)) OVER() calcula o total no mesmo passo do GROUP BY
  // (SQLite 3.25+ / D1 suporta). Evita subquery correlacionada com bind duplo.
  // GH#427: adicionado jitter/packet_loss/upload por network_type — únicas métricas
  // físicas que o Android realmente coleta em diagnostic_sessions (ver
  // "SignallQ Admin/docs/architecture/data-architecture.md"). Nada de contagem de
  // torres/SSID ou índice de interferência — o app não mede isso.
  const rows = await env.DB.prepare(
    `SELECT
       network_type AS name,
       COUNT(*) AS count,
       AVG(CAST(score AS REAL)) AS avg_score,
       AVG(download_mbps) AS avg_download_mbps,
       AVG(upload_mbps) AS avg_upload_mbps,
       AVG(latency_ms) AS avg_latency_ms,
       AVG(jitter_ms) AS avg_jitter_ms,
       AVG(packet_loss) AS avg_packet_loss,
       COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() AS percentage
     FROM diagnostic_sessions
     WHERE created_at >= ?${envClause}
     GROUP BY network_type
     ORDER BY count DESC`
  ).bind(since, ...envBinds).all();

  const round1 = (v: number | null) => v != null ? Math.round(v * 10) / 10 : null;

  const items = (rows.results ?? []).map((r: any) => ({
    name:              r.name              ?? "Desconhecido",
    count:             r.count             ?? 0,
    avg_score:         r.avg_score         != null ? Math.round(r.avg_score) : null,
    avg_download_mbps: round1(r.avg_download_mbps),
    avg_upload_mbps:   round1(r.avg_upload_mbps),
    avg_latency_ms:    r.avg_latency_ms    != null ? Math.round(r.avg_latency_ms) : null,
    avg_jitter_ms:     round1(r.avg_jitter_ms),
    avg_packet_loss:   round1(r.avg_packet_loss),
    percentage:        r.percentage        != null ? Math.round(r.percentage * 10) / 10 : 0,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", items }, 200, env);
}

// GH#786 — agregado de sessões por UF (mapa "Onde o app é mais usado", tela
// Redes & Provedores). uf='' (geo indisponível/fora do Brasil) fica de fora
// do array — não fabrica uma UF que o worker não conseguiu determinar.
async function handleRegionInsights(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const rows = await env.DB.prepare(
    `SELECT
       uf,
       COUNT(*) AS count,
       COUNT(*) * 100.0 / SUM(COUNT(*)) OVER() AS percentage
     FROM diagnostic_sessions
     WHERE created_at >= ? AND uf IS NOT NULL AND uf != ''${envClause}
     GROUP BY uf
     ORDER BY count DESC`
  ).bind(since, ...envBinds).all();

  const items = (rows.results ?? []).map((r: any) => ({
    uf:         r.uf,
    count:      r.count      ?? 0,
    percentage: r.percentage != null ? Math.round(r.percentage * 10) / 10 : 0,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", items }, 200, env);
}

// GH#765 — o Android manda "none" no array de issues quando não há problema
// detectado (em vez de array vazio). Sem esse filtro, "none" era contado como
// o problema mais comum do painel (ex: 42% das sessões).
const NON_ISSUE_LABELS = new Set(["none", "unknown", "null", "n/a"]);
function isRealIssueLabel(label: unknown): label is string {
  if (typeof label !== "string") return false;
  const trimmed = label.trim();
  return trimmed.length > 0 && !NON_ISSUE_LABELS.has(trimmed.toLowerCase());
}

async function handleTopIssues(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // Busca todas as sessões do período para explodir o array JSON de issues.
  // D1 não suporta json_each nativamente de forma cross-platform — fazemos no runtime.
  const rows = await env.DB.prepare(
    `SELECT issues FROM diagnostic_sessions WHERE created_at >= ?${envClause}`
  ).bind(since, ...envBinds).all();

  const countMap: Record<string, number> = {};
  let totalIssues = 0;

  for (const row of (rows.results ?? [])) {
    let issues: string[] = [];
    try { issues = JSON.parse((row as any).issues ?? "[]"); } catch { /* ignora linha malformada */ }
    for (const label of issues) {
      if (isRealIssueLabel(label)) {
        countMap[label] = (countMap[label] ?? 0) + 1;
        totalIssues++;
      }
    }
  }

  const sorted = Object.entries(countMap)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 5);

  const items = sorted.map(([problem, count], idx) => ({
    id:         `issue_${idx + 1}`,
    problem,
    count,
    percentage: totalIssues > 0 ? Math.round((count / totalIssues) * 100) : 0,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", items }, 200, env);
}

// #883 — teto do plano gratuito Cloudflare, valores publicados em
// developers.cloudflare.com/workers/platform/pricing/ e /d1/platform/pricing/
// (conferido em 2026-07-12). Reset diário é por dia UTC (é como a Cloudflare conta).
// #921: workersAiNeuronsPerDay — teto do Workers AI (10.000 neurônios/dia, CONTA
// INTEIRA), developers.cloudflare.com/workers-ai/platform/pricing/ (conferido
// 2026-07-12). Mesmo reset diário UTC.
const CF_FREE_TIER = {
  workersRequestsPerDay:   100_000,
  d1RowsReadPerDay:        5_000_000,
  d1RowsWrittenPerDay:     100_000,
  d1StorageBytesTotal:     5 * 1024 * 1024 * 1024, // 5 GB, total da conta (não por database)
  workersAiNeuronsPerDay:  10_000,
};

// #921 — Workers AI Neurons (fallback Qwen3-30B) NÃO tem dataset na GraphQL
// Analytics API nem endpoint REST de uso/quota (confirmado em 2026-07-12: página de
// datasets/discovery da GraphQL API não lista nenhum node de Workers AI, a
// referência REST /accounts/{id}/ai/* só tem run/finetunes/models/schema — nada de
// usage; a doc de pricing só recomenda "monitorar no dashboard"). Por isso o "usado"
// aqui é ESTIMATIVA: tokens reais gravados em ai_usage (o Android reporta cada
// chamada) × taxa oficial de conversão do Qwen3-30B-A3B-FP8 publicada em
// developers.cloudflare.com/workers-ai/platform/pricing/ (conferido 2026-07-12):
// 4625 neurônios por 1M tokens de entrada, 30475 por 1M de saída. Nunca é medição
// real cobrada pela Cloudflare — só o dashboard deles tem esse número exato.
const QWEN_NEURONS_PER_INPUT_TOKEN  = 4625 / 1_000_000;
const QWEN_NEURONS_PER_OUTPUT_TOKEN = 30475 / 1_000_000;

interface CloudflareResourceUsage {
  available: boolean;
  used: number | null;
  limit: number | null;
  percentage: number | null; // 0-100, arredondado
  unit: "requests" | "rows" | "bytes" | "neurons";
  reason?: string; // preenchido quando available=false
  estimated?: boolean; // #921: true quando "used" é estimativa, não medição real
}

interface CloudflareUsageGraphQLResponse {
  viewer: {
    accounts: Array<{
      workersInvocationsAdaptive: Array<{ sum: { requests: number } }>;
      d1AnalyticsAdaptiveGroups: Array<{ sum: { readQueries: number; writeQueries: number; rowsRead: number; rowsWritten: number } }>;
      d1StorageAdaptiveGroups: Array<{ max: { databaseSizeBytes: number } }>;
    }>;
  };
}

function usageOf(used: number, limit: number, unit: CloudflareResourceUsage["unit"]): CloudflareResourceUsage {
  return {
    available: true,
    used,
    limit,
    percentage: Math.round((used / limit) * 100),
    unit,
  };
}

function unavailableUsage(reason: string, unit: CloudflareResourceUsage["unit"]): CloudflareResourceUsage {
  return { available: false, used: null, limit: null, percentage: null, unit, reason };
}

interface CloudflareUsageResult {
  source: "graphql" | "not_configured" | "error";
  timestamp: string;
  resources: {
    workersRequestsDay: CloudflareResourceUsage;
    d1RowsReadDay: CloudflareResourceUsage;
    d1RowsWrittenDay: CloudflareResourceUsage;
    d1StorageTotal: CloudflareResourceUsage;
    workersAiNeuronsDay: CloudflareResourceUsage;
  };
}

// #921 — estimativa de neurônios Workers AI consumidos hoje (dia UTC) pelo
// fallback Qwen3-30B. Fonte é sempre D1 (ai_usage), nunca a GraphQL Analytics API
// (não expõe o dado — ver comentário de QWEN_NEURONS_PER_INPUT_TOKEN acima) — por
// isso roda independente de CLOUDFLARE_API_TOKEN estar configurado ou não.
// Filtro de provider replica exatamente providerEnumId()/providerName() (model
// contém "qwen" ou começa com "@cf/") para não duplicar o critério de detecção.
async function getWorkersAiNeuronsEstimate(env: Env): Promise<CloudflareResourceUsage> {
  const now = new Date();
  const todayStartUtcSec = Math.floor(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) / 1000);

  try {
    const row = await env.DB.prepare(
      `SELECT SUM(prompt_tokens) AS inputTokens, SUM(completion_tokens) AS outputTokens
       FROM ai_usage
       WHERE created_at >= ? AND (model LIKE '%qwen%' OR model LIKE '@cf/%')`
    ).bind(todayStartUtcSec).first<{ inputTokens: number | null; outputTokens: number | null }>();

    const inputTokens  = row?.inputTokens ?? 0;
    const outputTokens = row?.outputTokens ?? 0;
    const estimatedNeurons = Math.round(
      inputTokens * QWEN_NEURONS_PER_INPUT_TOKEN + outputTokens * QWEN_NEURONS_PER_OUTPUT_TOKEN
    );

    return {
      ...usageOf(estimatedNeurons, CF_FREE_TIER.workersAiNeuronsPerDay, "neurons"),
      estimated: true,
    };
  } catch (e) {
    await logError(env, 'workers-ai-neurons-estimate', String(e), e instanceof Error ? e.stack ?? '' : '');
    return unavailableUsage(`Falha ao consultar ai_usage: ${String(e)}`, "neurons");
  }
}

// #883: uso vs. teto do free tier Cloudflare (Workers requests/dia, D1 rows
// lidas/escritas por dia, D1 storage total). Conta INTEIRA, não só este worker —
// o teto de requests do Workers Free é por conta, não por script, então filtrar
// por scriptName daria um número que não reflete o risco real de estourar o
// limite gratuito. Extraída de handleCloudflareUsage para ser reaproveitada por
// generateAndPersistAlerts sem duplicar a chamada GraphQL.
async function getCloudflareUsage(env: Env): Promise<CloudflareUsageResult> {
  const workersAiNeuronsDay = await getWorkersAiNeuronsEstimate(env);

  if (!env.CLOUDFLARE_API_TOKEN || !env.CLOUDFLARE_ACCOUNT_ID) {
    const reason = "CLOUDFLARE_API_TOKEN não configurado — requer token criado manualmente no dashboard Cloudflare com escopo Account Analytics:Read.";
    return {
      source: "not_configured",
      timestamp: new Date().toISOString(),
      resources: {
        workersRequestsDay: unavailableUsage(reason, "requests"),
        d1RowsReadDay:      unavailableUsage(reason, "rows"),
        d1RowsWrittenDay:   unavailableUsage(reason, "rows"),
        d1StorageTotal:     unavailableUsage(reason, "bytes"),
        workersAiNeuronsDay,
      },
    };
  }

  const now = new Date();
  const todayStartUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const storageSince = new Date(now.getTime() - 2 * 86400 * 1000); // janela curta só p/ pegar o snapshot mais recente

  const query = `
    query($accountTag: String!, $since: Time!, $until: Time!, $dbId: String!, $storageSince: Time!) {
      viewer {
        accounts(filter: { accountTag: $accountTag }) {
          workersInvocationsAdaptive(limit: 1, filter: { datetime_geq: $since, datetime_leq: $until }) {
            sum { requests }
          }
          d1AnalyticsAdaptiveGroups(limit: 1, filter: { datetime_geq: $since, datetime_leq: $until, databaseId: $dbId }) {
            sum { readQueries writeQueries rowsRead rowsWritten }
          }
          d1StorageAdaptiveGroups(limit: 1, filter: { databaseId: $dbId, datetimeHour_geq: $storageSince }, orderBy: [datetimeHour_DESC]) {
            max { databaseSizeBytes }
          }
        }
      }
    }
  `;

  const { data, error } = await queryCloudflareGraphQL<CloudflareUsageGraphQLResponse>(env, query, {
    accountTag: env.CLOUDFLARE_ACCOUNT_ID,
    since: todayStartUtc.toISOString(),
    until: now.toISOString(),
    dbId: env.CLOUDFLARE_D1_DATABASE_ID ?? "",
    storageSince: storageSince.toISOString(),
  });

  if (error || !data) {
    await logError(env, 'cloudflare-usage', error ?? 'unknown_error', '');
    const reason = `Falha ao consultar GraphQL Analytics API: ${error ?? 'resposta vazia'}`;
    return {
      source: "error",
      timestamp: new Date().toISOString(),
      resources: {
        workersRequestsDay: unavailableUsage(reason, "requests"),
        d1RowsReadDay:      unavailableUsage(reason, "rows"),
        d1RowsWrittenDay:   unavailableUsage(reason, "rows"),
        d1StorageTotal:     unavailableUsage(reason, "bytes"),
        workersAiNeuronsDay,
      },
    };
  }

  const account = data.viewer.accounts[0];
  const requests = account?.workersInvocationsAdaptive?.[0]?.sum.requests ?? 0;
  const d1 = account?.d1AnalyticsAdaptiveGroups?.[0]?.sum;
  const storageBytes = account?.d1StorageAdaptiveGroups?.[0]?.max.databaseSizeBytes;

  return {
    source: "graphql",
    timestamp: new Date().toISOString(),
    resources: {
      workersRequestsDay: usageOf(requests, CF_FREE_TIER.workersRequestsPerDay, "requests"),
      d1RowsReadDay:      usageOf(d1?.rowsRead ?? 0, CF_FREE_TIER.d1RowsReadPerDay, "rows"),
      d1RowsWrittenDay:   usageOf(d1?.rowsWritten ?? 0, CF_FREE_TIER.d1RowsWrittenPerDay, "rows"),
      d1StorageTotal:     storageBytes != null
        ? usageOf(storageBytes, CF_FREE_TIER.d1StorageBytesTotal, "bytes")
        : unavailableUsage("Sem snapshot de storage nas últimas 48h.", "bytes"),
      workersAiNeuronsDay,
    },
  };
}

// GET /admin/cloudflare-usage — wrapper HTTP de getCloudflareUsage (ver acima).
async function handleCloudflareUsage(_req: Request, env: Env): Promise<Response> {
  const result = await getCloudflareUsage(env);
  return json(result, 200, env);
}

// SIG-133: tipos e helpers para alertas persistidos.

interface AlertRow {
  id: string;
  type: string;
  severity: string;
  title: string;
  message: string;
  created_at: number;
  resolved: number;
}

interface AlertResponse {
  id: string;
  type: string;
  severity: string;
  title: string;
  message: string;
  created_at: number;
  timestamp: string;
  resolved: boolean;
}

function alertRowToResponse(r: AlertRow): AlertResponse {
  return {
    id:         r.id,
    type:       r.type,
    severity:   r.severity,
    title:      r.title,
    message:    r.message,
    created_at: r.created_at,
    timestamp:  new Date(r.created_at * 1000).toISOString(),
    resolved:   r.resolved === 1,
  };
}

/**
 * Verifica thresholds e persiste alertas candidatos de forma idempotente.
 * Regras:
 * - Custo IA > budget diário → critical (requer budget configurado em admin_settings)
 * - Taxa de erros > threshold/hora → warning (usa system_errors.last_seen)
 * - Score médio < mínimo → warning
 *
 * Idempotência: INSERT OR IGNORE por id determinístico. Se o alerta já existe
 * e não foi resolvido, não duplica. Se foi resolvido, não reabre
 * (evita flood de alertas repetidos — operador resolve manualmente).
 */
async function generateAndPersistAlerts(env: Env): Promise<void> {
  const now        = nowSec();
  const oneDayAgo  = now - 86400;
  const oneHourAgo = now - 3600;

  const settingsRow = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'admin'"
  ).first<{ value: string }>();
  const settings = parseAdminSettings(settingsRow?.value);

  // Budget: se não configurado, não gera alerta de custo (sem fabricar).
  const AI_DAILY_BUDGET: number | null = settings.aiDailyBudgetUsd ?? null;
  const ERROR_THRESHOLD  = settings.errorSpikeThreshold    ?? 10;
  const MIN_SCORE        = settings.criticalScoreThreshold ?? 50;
  const MIN_COMPLETENESS = settings.diagnosticCompletenessThreshold ?? 80;

  const [aiCost, recentErrors, scoreRow, completenessRow] = await Promise.all([
    env.DB.prepare(
      "SELECT SUM(cost_usd) AS total FROM ai_usage WHERE created_at >= ?"
    ).bind(oneDayAgo).first<{ total: number | null }>(),

    env.DB.prepare(
      "SELECT COUNT(*) AS count FROM system_errors WHERE last_seen >= ?"
    ).bind(oneHourAgo * 1000).first<{ count: number }>(),

    env.DB.prepare(
      `SELECT AVG(CAST(score AS REAL)) AS avg FROM diagnostic_sessions
       WHERE created_at >= ? AND score IS NOT NULL`
    ).bind(oneDayAgo).first<{ avg: number | null }>(),

    env.DB.prepare(
      `SELECT COUNT(*) AS total,
              SUM(CASE WHEN score IS NOT NULL
                         AND download_mbps IS NOT NULL AND upload_mbps IS NOT NULL
                         AND latency_ms IS NOT NULL
                         AND NULLIF(device_id, '') IS NOT NULL
                         AND NULLIF(dist_channel, '') IS NOT NULL
                       THEN 1 ELSE 0 END) AS complete
       FROM diagnostic_sessions WHERE created_at >= ?`
    ).bind(oneDayAgo).first<{ total: number; complete: number | null }>(),
  ]);

  const candidates: Array<{ id: string; type: string; severity: string; title: string; message: string }> = [];

  // Alerta de custo: só dispara se budget estiver configurado e custo > 0.
  if (AI_DAILY_BUDGET !== null && (aiCost?.total ?? 0) > AI_DAILY_BUDGET) {
    candidates.push({
      id:       'ai_budget_exceeded',
      type:     'AI_BUDGET',
      severity: 'critical',
      title:    'Orçamento diário de IA excedido',
      message:  `Custo nas últimas 24h: $${(aiCost?.total ?? 0).toFixed(4)} USD (limite: $${AI_DAILY_BUDGET})`,
    });
  }

  if ((recentErrors?.count ?? 0) > ERROR_THRESHOLD) {
    candidates.push({
      id:       'error_spike',
      type:     'ERROR_SPIKE',
      severity: 'warning',
      title:    'Pico de erros detectado',
      message:  `${recentErrors?.count} erros na última hora (limite: ${ERROR_THRESHOLD})`,
    });
  }

  if (scoreRow?.avg != null && scoreRow.avg < MIN_SCORE) {
    candidates.push({
      id:       'low_avg_score',
      type:     'LOW_SCORE',
      severity: 'warning',
      title:    'Qualidade de rede baixa',
      message:  `Score médio nas últimas 24h: ${Math.round(scoreRow.avg)} (mínimo: ${MIN_SCORE})`,
    });
  }

  // P1: não confunde volume de diagnósticos com cobertura suficiente dos campos
  // que sustentam score, velocidade e latência. Sem registros no período, não há
  // base para alerta; com registros incompletos, o alerta torna a limitação visível.
  const totalDiagnostics = completenessRow?.total ?? 0;
  const completeDiagnostics = completenessRow?.complete ?? 0;
  const completenessPercentage = totalDiagnostics > 0
    ? (completeDiagnostics / totalDiagnostics) * 100
    : null;
  if (completenessPercentage != null && completenessPercentage < MIN_COMPLETENESS) {
    candidates.push({
      id:       'diagnostic_completeness_low',
      type:     'DIAGNOSTIC_COMPLETENESS',
      severity: 'warning',
      title:    'Cobertura de diagnósticos insuficiente',
      message:  `${completenessPercentage.toFixed(1)}% completos nas últimas 24h (${completeDiagnostics}/${totalDiagnostics}; mínimo: ${MIN_COMPLETENESS}%)`,
    });
  }

  // #883: alerta de uso vs. teto do free tier Cloudflare — só roda a checagem
  // se CLOUDFLARE_API_TOKEN estiver configurado (sem fabricar alerta sem dado real).
  const CF_ATTENTION_THRESHOLD = settings.cloudflareUsageAttentionThreshold ?? 80;
  const cfUsage = await getCloudflareUsage(env);
  if (cfUsage.source === "graphql") {
    const cfResourceLabels: Record<string, string> = {
      workersRequestsDay: 'Workers requests/dia',
      d1RowsReadDay:      'D1 rows lidas/dia',
      d1RowsWrittenDay:   'D1 rows escritas/dia',
      d1StorageTotal:     'D1 storage total',
    };
    for (const [key, label] of Object.entries(cfResourceLabels)) {
      const resource = cfUsage.resources[key as keyof typeof cfUsage.resources];
      if (resource.available && resource.percentage != null && resource.percentage >= CF_ATTENTION_THRESHOLD) {
        candidates.push({
          id:       `cloudflare_usage_${key}`,
          type:     'CLOUDFLARE_USAGE',
          severity: resource.percentage >= 100 ? 'critical' : 'warning',
          title:    'Uso do free tier Cloudflare em atenção',
          message:  `${label}: ${resource.percentage}% do limite gratuito (${resource.used}/${resource.limit})`,
        });
      }
    }
  }

  // #921: alerta de Workers AI Neurons (estimado) roda sempre — dado vem de D1
  // (ai_usage), não da GraphQL Analytics API, então não depende de
  // CLOUDFLARE_API_TOKEN nem do source do restante do card.
  const neurons = cfUsage.resources.workersAiNeuronsDay;
  if (neurons.available && neurons.percentage != null && neurons.percentage >= CF_ATTENTION_THRESHOLD) {
    candidates.push({
      id:       'cloudflare_usage_workersAiNeuronsDay',
      type:     'CLOUDFLARE_USAGE',
      severity: neurons.percentage >= 100 ? 'critical' : 'warning',
      title:    'Uso do free tier Cloudflare em atenção',
      message:  `Workers AI Neurons/dia (estimado): ${neurons.percentage}% do limite gratuito (${neurons.used}/${neurons.limit})`,
    });
  }

  // #884: alerta de quota Gemini — só roda se algum teto do free tier estiver
  // configurado em admin_settings.geminiFreeTierLimits (sem fabricar alerta sem dado real).
  const GEMINI_ATTENTION_THRESHOLD = settings.geminiQuotaAttentionThreshold ?? 80;
  const geminiQuota = await getGeminiQuotaUsage(env);
  if (geminiQuota.source === "d1") {
    const quotaLabels: Record<string, string> = {
      requestsPerMinute: 'Gemini RPM',
      tokensPerMinute:   'Gemini TPM',
      requestsPerDay:    'Gemini RPD',
    };
    for (const [key, label] of Object.entries(quotaLabels)) {
      const metric = geminiQuota.quota[key as keyof typeof geminiQuota.quota];
      if (metric.available && metric.percentage != null && metric.percentage >= GEMINI_ATTENTION_THRESHOLD) {
        candidates.push({
          id:       `gemini_quota_${key}`,
          type:     'GEMINI_QUOTA',
          severity: metric.percentage >= 100 ? 'critical' : 'warning',
          title:    'Uso do free tier Gemini em atenção',
          message:  `${label}: ${metric.percentage}% do limite gratuito (${metric.used}/${metric.limit})`,
        });
      }
    }
  }

  // Persiste idempotente: INSERT OR IGNORE mantém o alerta original se já existir
  // (ativo ou resolvido). Não reabre alerta resolvido automaticamente.
  for (const c of candidates) {
    await env.DB.prepare(
      `INSERT OR IGNORE INTO alerts (id, type, severity, title, message, created_at, resolved)
       VALUES (?, ?, ?, ?, ?, ?, 0)`
    ).bind(c.id, c.type, c.severity, c.title, c.message, now).run();
  }
}

// GET /admin/alerts — gera alertas + retorna ativos + histórico recente (24h).
// Compat: também serve GET /admin/metrics/alerts para o adminMetricsService legado.
async function handleAlerts(_request: Request, env: Env): Promise<Response> {
  await generateAndPersistAlerts(env);

  const since = nowSec() - 86400;
  const rows = await env.DB.prepare(
    `SELECT id, type, severity, title, message, created_at, resolved
     FROM alerts
     WHERE resolved = 0 OR created_at >= ?
     ORDER BY created_at DESC
     LIMIT 50`
  ).bind(since).all<AlertRow>();

  const items = (rows.results ?? []).map(alertRowToResponse);
  return json({ source: "d1", items }, 200, env);
}

// POST /admin/alerts/:id/resolve — marca alerta como resolvido.
async function handleResolveAlert(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  // Extrai o id do path: /admin/alerts/:id/resolve
  const match = url.pathname.match(/^\/admin\/alerts\/([^/]+)\/resolve$/);
  if (!match) return err('id inválido', 400, env);
  const id = match[1];

  const result = await env.DB.prepare(
    'UPDATE alerts SET resolved = 1 WHERE id = ?'
  ).bind(id).run();

  if ((result.meta?.changes ?? 0) === 0) {
    return err('alerta não encontrado', 404, env);
  }

  return json({ ok: true, id }, 200, env);
}

// /admin/metrics/alerts — mantido para compatibilidade; delega para handleAlerts.
async function handleRecentAlerts(request: Request, env: Env): Promise<Response> {
  return handleAlerts(request, env);
}

async function handleAiCostMetrics(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const row = await env.DB.prepare(
    `SELECT
       COUNT(*)             AS totalRequests,
       SUM(cost_usd)        AS totalCostUsd,
       SUM(prompt_tokens)   AS promptTokens,
       SUM(completion_tokens) AS completionTokens,
       SUM(total_tokens)    AS totalTokens
     FROM ai_usage
     WHERE created_at >= ?${envClause}`
  ).bind(since, ...envBinds).first<{
    totalRequests: number;
    totalCostUsd: number | null;
    promptTokens: number | null;
    completionTokens: number | null;
    totalTokens: number | null;
  }>();

  const totalRequests    = row?.totalRequests     ?? 0;
  const totalCostUsd     = row?.totalCostUsd       ?? 0;
  const promptTokens     = row?.promptTokens       ?? 0;
  const completionTokens = row?.completionTokens   ?? 0;
  const totalTokens      = row?.totalTokens        ?? 0;

  // avgCostPerRequest: guard contra divisão por zero.
  const avgCostPerRequest = totalRequests > 0 ? totalCostUsd / totalRequests : 0;

  // SIG-125: reliabilityPercentage — % de chamadas com completion_tokens > 0 (resposta real gerada).
  const reliabilityRow = await env.DB.prepare(
    `SELECT COUNT(*) AS total,
            SUM(CASE WHEN completion_tokens > 0 THEN 1 ELSE 0 END) AS successful
     FROM ai_usage WHERE created_at >= ?${envClause}`
  ).bind(since, ...envBinds).first<{ total: number; successful: number }>();
  const reliabilityPercentage = (reliabilityRow?.total ?? 0) > 0
    ? Math.round(((reliabilityRow?.successful ?? 0) / reliabilityRow!.total) * 100)
    : null;

  return json({
    source: "d1",
    period,
    environment: envFilter ?? "all",
    totalCostUsd,
    totalRequests,
    avgCostPerRequest,
    totalTokens,
    promptTokens,
    completionTokens,
    reliabilityPercentage,
  }, 200, env);
}

// Mapeia model → nome legível do provedor. Cor não vem do worker.
function providerName(model: string): string {
  const m = (model ?? "").toLowerCase();
  if (m.includes("gemini"))                      return "Gemini";
  if (m.includes("qwen") || m.startsWith("@cf/")) return "Qwen / Workers AI";
  if (m.includes("gpt"))                         return "OpenAI GPT";
  if (m.includes("claude"))                      return "Anthropic Claude";
  return model; // fallback: nome técnico do modelo
}

// #879 (achado 4 da auditoria 2026-07-10): o Admin (types/ai.ts) define o enum
// AiProvider como IDs técnicos ("gemini_flash" | "cloudflare_qwen" | "openai" |
// "local_fallback"), usados como chave direta em mapas de cor (PROVIDER_COLORS).
// providerName() acima devolve rótulo HUMANO ("Gemini", "Qwen / Workers AI"), que
// nunca bate com esse enum — daí o donut cair sempre na cor de fallback. Mesma
// lógica de deteção de providerName(), saída compatível com o enum do frontend.
function providerEnumId(model: string): string {
  const m = (model ?? "").toLowerCase();
  if (m.includes("gemini"))                      return "gemini_flash";
  if (m.includes("qwen") || m.startsWith("@cf/")) return "cloudflare_qwen";
  if (m.includes("gpt"))                         return "openai";
  return "local_fallback";
}

export async function handleDiagnosticsSummary(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);
  const platformFilter = getPlatformFilter(url);

  const envClause = envFilter ? " AND environment = ?" : "";
  const envBinds  = envFilter ? [envFilter]            : [];
  const platformClause = platformFilter ? " AND platform = ?" : "";
  const platformBinds  = platformFilter ? [platformFilter]    : [];
  const trackClause  = productionTrackClause(envFilter);
  const filterClause = `${envClause}${platformClause}${trackClause}`;
  const filterBinds  = [...envBinds, ...platformBinds];

  const row = await env.DB.prepare(
    `SELECT
       COUNT(*) AS total,
       AVG(latency_ms) AS avg_latency_ms,
       AVG(jitter_ms) AS avg_jitter_ms,
       AVG(packet_loss) AS avg_packet_loss,
       AVG(download_mbps) AS avg_download_mbps,
       AVG(upload_mbps) AS avg_upload_mbps,
       AVG(CAST(score AS REAL)) AS avg_score,
       SUM(CASE WHEN status IN ('ruim','critico') THEN 1 ELSE 0 END) AS critical_count,
       SUM(CASE WHEN resolved = 0 THEN 1 ELSE 0 END) AS active_count,
       SUM(CASE WHEN score IS NOT NULL THEN 1 ELSE 0 END) AS with_score,
       SUM(CASE WHEN download_mbps IS NOT NULL AND upload_mbps IS NOT NULL THEN 1 ELSE 0 END) AS with_speed,
       SUM(CASE WHEN latency_ms IS NOT NULL THEN 1 ELSE 0 END) AS with_latency,
       SUM(CASE WHEN NULLIF(device_id, '') IS NOT NULL THEN 1 ELSE 0 END) AS with_device,
       SUM(CASE WHEN NULLIF(dist_channel, '') IS NOT NULL THEN 1 ELSE 0 END) AS with_dist_channel,
       SUM(CASE WHEN score IS NOT NULL
                  AND download_mbps IS NOT NULL AND upload_mbps IS NOT NULL
                  AND latency_ms IS NOT NULL
                  AND NULLIF(device_id, '') IS NOT NULL
                  AND NULLIF(dist_channel, '') IS NOT NULL
                THEN 1 ELSE 0 END) AS complete_count
     FROM diagnostic_sessions
     WHERE created_at >= ?${filterClause}`
  ).bind(since, ...filterBinds).first<{
    total: number;
    avg_latency_ms: number | null;
    avg_jitter_ms: number | null;
    avg_packet_loss: number | null;
    avg_download_mbps: number | null;
    avg_upload_mbps: number | null;
    avg_score: number | null;
    critical_count: number;
    active_count: number;
    with_score: number;
    with_speed: number;
    with_latency: number;
    with_device: number;
    with_dist_channel: number;
    complete_count: number;
  }>();

  const round1 = (v: number | null) => v != null ? Math.round(v * 10) / 10 : null;
  const total = row?.total ?? 0;
  const coverage = (count: number | null | undefined) => total > 0
    ? Math.round(((count ?? 0) / total) * 1000) / 10
    : null;

  return json({
    source:                    "d1",
    period,
    environment:               envFilter ?? "all",
    platform:                  platformFilter ?? "all",
    totalDiagnostics:          total,
    criticalCount:             row?.critical_count ?? 0,
    activeSessions:            row?.active_count ?? 0,
    averageScore:              row?.avg_score != null ? Math.round(row.avg_score) : null,
    averageLatencyMs:          round1(row?.avg_latency_ms ?? null),
    averageJitterMs:           round1(row?.avg_jitter_ms ?? null),
    averagePacketLossPercentage: round1(row?.avg_packet_loss ?? null),
    averageDownloadMbps:       round1(row?.avg_download_mbps ?? null),
    averageUploadMbps:         round1(row?.avg_upload_mbps ?? null),
    // P1: médias só são interpretáveis junto da cobertura dos campos que as compõem.
    // O limiar de 80% é o mesmo já usado para métricas de sessão no endpoint de produto.
    dataQuality: {
      completeDiagnostics: coverage(row?.complete_count),
      score: coverage(row?.with_score),
      speed: coverage(row?.with_speed),
      latency: coverage(row?.with_latency),
      device: coverage(row?.with_device),
      distributionChannel: coverage(row?.with_dist_channel),
      isSufficientForNetworkMetrics: total > 0 && (row?.complete_count ?? 0) / total >= 0.8,
    },
  }, 200, env);
}

async function handleAiProviders(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const rows = await env.DB.prepare(
    `SELECT
       model,
       SUM(total_tokens) AS tokensProcessed,
       COUNT(*) AS calls,
       SUM(CASE WHEN completion_tokens > 0 THEN 1 ELSE 0 END) AS successful_calls
     FROM ai_usage
     WHERE created_at >= ?${envClause}
     GROUP BY model
     ORDER BY tokensProcessed DESC`
  ).bind(since, ...envBinds).all();

  const results = rows.results ?? [];
  const grandTotal = results.reduce((acc: number, r: any) => acc + (r.tokensProcessed ?? 0), 0);

  const items = results.map((r: any) => {
    const calls      = r.calls ?? 0;
    const successful = r.successful_calls ?? 0;
    return {
      name:                  providerName(r.model ?? ""),
      tokensProcessed:       r.tokensProcessed ?? 0,
      percentage:            grandTotal > 0 ? Math.round(((r.tokensProcessed ?? 0) / grandTotal) * 100) : 0,
      reliabilityPercentage: calls > 0 ? Math.round((successful / calls) * 10000) / 100 : null,
    };
  });

  return json({ source: "d1", period, environment: envFilter ?? "all", items }, 200, env);
}

// #884 — modelo IA primário gravado em ai_usage.model (confirmado em produção,
// 2026-07-12). Se o provider trocar de alias (ver GH#898 em ai-diagnosis-worker),
// este valor precisa acompanhar.
const GEMINI_MODEL_ID = "gemini-flash-latest";

interface QuotaMetric {
  available: boolean;
  used: number | null;
  limit: number | null;
  percentage: number | null;
  reason?: string;
}

function quotaOf(used: number, limit: number): QuotaMetric {
  return { available: true, used, limit, percentage: Math.round((used / limit) * 100) };
}

function unavailableQuota(reason: string): QuotaMetric {
  return { available: false, used: null, limit: null, percentage: null, reason };
}

// A API do Gemini não expõe consulta de quota em tempo real via REST — rate
// limits (RPM/TPM/RPD) só aparecem autenticado em Google AI Studio (confirmado
// em developers docs, 2026-07-12: "View your active rate limits in AI Studio").
// O teto do free tier, por isso, é CONFIGURÁVEL (checado manualmente em AI Studio
// e preenchido em admin_settings), nunca hardcoded — o valor muda por tier/conta
// e a Cloudflare não tem como confirmá-lo sozinha. O "usado" é sempre real,
// contado a partir de ai_usage (cada chamada do worker é registrada lá).
function pacificMidnightUnixSec(nowMs: number): number {
  const fmt = new Intl.DateTimeFormat('en-US', {
    timeZone: 'America/Los_Angeles', hour12: false,
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
  const parts = fmt.formatToParts(new Date(nowMs));
  const get = (type: string) => parseInt(parts.find((p) => p.type === type)?.value ?? '0', 10);
  const secondsSinceMidnight = get('hour') * 3600 + get('minute') * 60 + get('second');
  return Math.floor((nowMs - secondsSinceMidnight * 1000) / 1000);
}

interface GeminiQuotaResult {
  source: "d1" | "not_configured";
  timestamp: string;
  quota: {
    requestsPerMinute: QuotaMetric;
    tokensPerMinute: QuotaMetric;
    requestsPerDay: QuotaMetric;
  };
}

async function getGeminiQuotaUsage(env: Env): Promise<GeminiQuotaResult> {
  const settingsRow = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'admin'"
  ).first<{ value: string }>();
  const settings = parseAdminSettings(settingsRow?.value);
  const limits: { rpm?: number; tpm?: number; rpd?: number } = settings.geminiFreeTierLimits ?? {};

  const now = nowSec();
  const oneMinuteAgo = now - 60;
  const pacificMidnight = pacificMidnightUnixSec(Date.now());

  const [minuteRow, dayRow] = await Promise.all([
    env.DB.prepare(
      `SELECT COUNT(*) AS requests, SUM(total_tokens) AS tokens
       FROM ai_usage WHERE model = ? AND created_at >= ?`
    ).bind(GEMINI_MODEL_ID, oneMinuteAgo).first<{ requests: number; tokens: number | null }>(),

    env.DB.prepare(
      `SELECT COUNT(*) AS requests FROM ai_usage WHERE model = ? AND created_at >= ?`
    ).bind(GEMINI_MODEL_ID, pacificMidnight).first<{ requests: number }>(),
  ]);

  const requestsThisMinute = minuteRow?.requests ?? 0;
  const tokensThisMinute   = minuteRow?.tokens ?? 0;
  const requestsToday      = dayRow?.requests ?? 0;

  const reasonNotConfigured = "Teto do free tier não configurado — a API do Gemini não expõe consulta de quota em tempo real (só via login em Google AI Studio); preencher manualmente em admin_settings.geminiFreeTierLimits após checar o valor atual no AI Studio.";

  return {
    source: limits.rpm || limits.tpm || limits.rpd ? "d1" : "not_configured",
    timestamp: new Date().toISOString(),
    quota: {
      requestsPerMinute: limits.rpm ? quotaOf(requestsThisMinute, limits.rpm) : unavailableQuota(reasonNotConfigured),
      tokensPerMinute:   limits.tpm ? quotaOf(tokensThisMinute, limits.tpm)   : unavailableQuota(reasonNotConfigured),
      requestsPerDay:    limits.rpd ? quotaOf(requestsToday, limits.rpd)      : unavailableQuota(reasonNotConfigured),
    },
  };
}

// GET /admin/metrics/ai-quota — #884: uso vs. teto do free tier Gemini.
async function handleAiQuota(_req: Request, env: Env): Promise<Response> {
  const result = await getGeminiQuotaUsage(env);
  return json(result, 200, env);
}

async function handleAiUsageTimeline(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const days      = Math.min(Math.max(parseInt(url.searchParams.get("days") ?? "30"), 1), 90);
  const since     = nowSec() - days * 86400;
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // Agrega total_tokens por dia × provedor (via providerName).
  // strftime converte unix epoch → YYYY-MM-DD (UTC). Ordenado por data asc para plotagem.
  const rows = await env.DB.prepare(
    `SELECT
       strftime('%Y-%m-%d', datetime(created_at, 'unixepoch')) AS date,
       model,
       SUM(total_tokens) AS tokens
     FROM ai_usage
     WHERE created_at >= ?${envClause}
     GROUP BY date, model
     ORDER BY date ASC`
  ).bind(since, ...envBinds).all();

  // Reagrega por data e provedor (múltiplos modelos podem pertencer ao mesmo provedor).
  const byDate = new Map<string, Record<string, number>>();
  for (const r of (rows.results ?? []) as any[]) {
    const date     = r.date as string;
    const provider = providerName(r.model ?? "");
    const tokens   = (r.tokens as number) ?? 0;
    if (!byDate.has(date)) byDate.set(date, {});
    const entry = byDate.get(date)!;
    entry[provider] = (entry[provider] ?? 0) + tokens;
  }

  const series = Array.from(byDate.entries()).map(([date, byProvider]) => ({ date, byProvider }));

  return json({ source: "d1", days, environment: envFilter ?? "all", series }, 200, env);
}

// GH#421: histórico de execuções reais de IA — cada linha vem direto de `ai_usage`,
// correlacionada com `diagnostic_sessions` quando `session_id` existe. Sem invenção
// de latência: o schema não registra esse campo hoje (ver docs/architecture/data-architecture.md).
async function handleAiUsageRecords(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "7d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);
  const limit     = Math.min(Math.max(parseInt(url.searchParams.get("limit") ?? "100"), 1), 500);

  const envClause = `${envFilter ? " AND au.environment = ?" : ""}${productionTrackClause(envFilter, "au.")}`;
  const envBinds  = envFilter ? [envFilter]               : [];

  const rows = await env.DB.prepare(
    `SELECT
       au.id, au.session_id, au.created_at, au.model,
       au.prompt_tokens, au.completion_tokens, au.cost_usd,
       au.status, au.error_message, au.environment,
       ds.id AS diag_id
     FROM ai_usage au
     LEFT JOIN diagnostic_sessions ds ON ds.id = au.session_id
     WHERE au.created_at >= ?${envClause}
     ORDER BY au.created_at DESC
     LIMIT ?`
  ).bind(since, ...envBinds, limit).all();

  const records = (rows.results ?? []).map((r: any) => ({
    id:               r.id,
    timestamp:        new Date((r.created_at ?? 0) * 1000).toISOString(),
    model:            r.model ?? "",
    provider:         providerName(r.model ?? ""),
    promptTokens:     r.prompt_tokens     ?? 0,
    completionTokens: r.completion_tokens ?? 0,
    costUsd:          r.cost_usd ?? 0,
    // status/error_message podem não existir ainda em registros anteriores à migration
    // 009_gh421 — default 'success' documentado na própria coluna (DEFAULT 'success').
    status:           r.status === "error" ? "error" : "success",
    errorMessage:     r.error_message || null,
    diagnosisId:      r.diag_id ?? null,
    environment:      r.environment ?? "production",
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", records }, 200, env);
}

// SIG-139: métricas de diagnóstico agrupadas por operadora.
async function handleOperators(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "30d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter, "s1.")}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  // Gap 4/5 (SIG-164): packetLossAverage calculado do D1; type derivado do network_type
  // mais frequente por operadora via subquery correlacionada.
  const rows = await env.DB.prepare(
    `SELECT
       s1.operator,
       COUNT(*)                                                       AS total_diagnostics,
       AVG(s1.score)                                                  AS avg_score,
       AVG(s1.download_mbps)                                         AS avg_download,
       AVG(s1.upload_mbps)                                           AS avg_upload,
       AVG(s1.latency_ms)                                            AS avg_latency,
       AVG(s1.packet_loss)                                           AS avg_packet_loss,
       SUM(CASE WHEN s1.status = 'completed' THEN 1 ELSE 0 END)     AS completed,
       SUM(CASE WHEN s1.resolved = 1         THEN 1 ELSE 0 END)     AS resolved,
       (SELECT s2.network_type
        FROM diagnostic_sessions s2
        WHERE s2.operator = s1.operator
          AND s2.created_at >= ?
          AND s2.network_type IS NOT NULL AND s2.network_type != '' AND s2.network_type != 'unknown'
        GROUP BY s2.network_type
        ORDER BY COUNT(*) DESC
        LIMIT 1)                                                      AS dominant_network_type
     FROM diagnostic_sessions s1
     WHERE s1.created_at >= ?
       AND s1.operator IS NOT NULL AND s1.operator != ''${envClause}
     GROUP BY s1.operator
     ORDER BY total_diagnostics DESC`
  ).bind(since, since, ...envBinds).all();

  const operators = (rows.results ?? []).map((r: any) => ({
    operator:            r.operator,
    total_diagnostics:   r.total_diagnostics ?? 0,
    avg_score:           r.avg_score         != null ? Math.round(r.avg_score) : null,
    avg_download:        r.avg_download       ?? null,
    avg_upload:          r.avg_upload         ?? null,
    avg_latency:         r.avg_latency        != null ? Math.round(r.avg_latency) : null,
    packetLossAverage:   r.avg_packet_loss    != null ? Math.round(r.avg_packet_loss * 100) / 100 : null,
    // Não inventar "mobile" quando não há amostra suficiente para determinar o tipo dominante.
    type:                r.dominant_network_type ?? null,
    completed:           r.completed          ?? 0,
    resolved:            r.resolved           ?? 0,
  }));

  return json({ source: "d1", period, environment: envFilter ?? "all", operators }, 200, env);
}

// GH#423: agregação real de sessões por versão de app — versão em produção, canal de
// distribuição e dados por release, direto do D1 (não depende de BigQuery/Crashlytics).
async function handleAppVersions(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "30d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const rows = await env.DB.prepare(
    `SELECT
       app_version,
       version_code,
       dist_channel,
       build_type,
       COUNT(*)          AS sessions,
       AVG(score)        AS avg_score,
       MIN(created_at)   AS first_seen,
       MAX(created_at)   AS last_seen
     FROM diagnostic_sessions
     WHERE created_at >= ?
       AND app_version IS NOT NULL AND app_version != ''${envClause}
     GROUP BY app_version, version_code, dist_channel, build_type
     ORDER BY version_code DESC, last_seen DESC`
  ).bind(since, ...envBinds).all();

  const versions = (rows.results ?? []).map((r: any) => ({
    appVersion:  r.app_version,
    versionCode: r.version_code ?? null,
    distChannel: r.dist_channel || "unknown",
    buildType:   r.build_type   || "unknown",
    sessions:    r.sessions     ?? 0,
    avgScore:    r.avg_score != null ? Math.round(r.avg_score) : null,
    firstSeen:   r.first_seen  ?? null,
    lastSeen:    r.last_seen   ?? null,
  }));

  // "Versão em produção": build mais recente visto no canal play_store; sem sessões
  // desse canal ainda (ex.: só beta interno), cai para a versão mais recente disponível.
  const productionVersion =
    versions.find((v: (typeof versions)[number]) => v.distChannel === "play_store") ?? versions[0] ?? null;

  return json(
    { source: "d1", period, environment: envFilter ?? "all", versions, productionVersion },
    200,
    env
  );
}

// Gap 6 (SIG-164): Diagnostic Intelligence Panel — agrega padrões por tipo de problema.
// Retorna os tipos de issue mais comuns, frequência relativa e score médio por tipo.
async function handleDiagnosticsIntelligence(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "30d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const rows = await env.DB.prepare(
    `SELECT issues, score FROM diagnostic_sessions
     WHERE created_at >= ?${envClause} AND issues IS NOT NULL AND issues != '[]'`
  ).bind(since, ...envBinds).all();

  const issueMap = new Map<string, { count: number; totalScore: number; scoreCount: number }>();

  for (const row of (rows.results ?? [])) {
    let issues: string[] = [];
    try { issues = JSON.parse((row as any).issues ?? "[]"); } catch { continue; }
    const score = (row as any).score;
    for (const label of issues) {
      if (isRealIssueLabel(label)) {
        const entry = issueMap.get(label) ?? { count: 0, totalScore: 0, scoreCount: 0 };
        entry.count++;
        if (score != null && !isNaN(Number(score))) {
          entry.totalScore += Number(score);
          entry.scoreCount++;
        }
        issueMap.set(label, entry);
      }
    }
  }

  const totalOccurrences = Array.from(issueMap.values()).reduce((s, e) => s + e.count, 0);

  const patterns = Array.from(issueMap.entries())
    .sort(([, a], [, b]) => b.count - a.count)
    .map(([issue, entry]) => ({
      issue,
      count:     entry.count,
      frequency: totalOccurrences > 0 ? Math.round((entry.count / totalOccurrences) * 1000) / 10 : 0,
      avgScore:  entry.scoreCount > 0 ? Math.round(entry.totalScore / entry.scoreCount) : null,
    }));

  return json({ source: "d1", period, environment: envFilter ?? "all", patterns }, 200, env);
}

async function handleFirebaseAnalytics(_request: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials", activeUsersToday: 0, sessionsToday: 0 }, 200, env);
  }
  if (!env.FIREBASE_GA4_PROPERTY_ID) {
    return json({
      source: "no_ga4_property_id",
      message: "Configure FIREBASE_GA4_PROPERTY_ID no wrangler.toml.",
    }, 200, env);
  }
  try {
    const token = await getFirebaseAccessToken(env);
    const resp  = await fetch(
      `https://analyticsdata.googleapis.com/v1beta/properties/${env.FIREBASE_GA4_PROPERTY_ID}:runReport`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          dateRanges: [{ startDate: "7daysAgo", endDate: "today" }],
          metrics: [{ name: "activeUsers" }, { name: "sessions" }, { name: "crashAffectedUsers" }],
          dimensions: [{ name: "date" }],
        }),
      }
    );
    const data = await resp.json();
    return json({ source: "firebase_analytics", data }, 200, env);
  } catch (e) {
    await logError(env, 'firebase', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ source: "error", message: String(e) }, 500, env);
  }
}

// GH#1343 — ampliação do GA4 além do runReport básico: aquisição, telas, retenção. Mesmo padrão
// live/passthrough do handleFirebaseAnalytics acima (sem persistência — é dashboard consultado
// ao vivo, não série histórica gravada; property ID corrigido em 2026-07-24, ver wrangler.toml).
async function runGa4Report(env: Env, body: unknown): Promise<{ status: number; data: unknown }> {
  const token = await getFirebaseAccessToken(env);
  const resp = await fetch(
    `https://analyticsdata.googleapis.com/v1beta/properties/${env.FIREBASE_GA4_PROPERTY_ID}:runReport`,
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(body) }
  );
  return { status: resp.status, data: await resp.json() };
}

async function handleFirebaseAcquisition(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials" }, 200, env);
  }
  if (!env.FIREBASE_GA4_PROPERTY_ID) {
    return json({ source: "no_ga4_property_id", message: "Configure FIREBASE_GA4_PROPERTY_ID no wrangler.toml." }, 200, env);
  }
  try {
    const { status, data } = await runGa4Report(env, {
      dateRanges: [{ startDate: "7daysAgo", endDate: "today" }],
      dimensions: [{ name: "sessionSource" }, { name: "sessionMedium" }, { name: "sessionCampaignName" }],
      metrics: [{ name: "newUsers" }, { name: "sessions" }, { name: "engagedSessions" }],
    });
    if (status !== 200) await logError(env, 'firebase-ga4-acquisition', `runReport_${status}: ${JSON.stringify(data).slice(0, 300)}`, '');
    return json({ source: "firebase_analytics", data }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-ga4-acquisition', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ source: "error", message: String(e) }, 500, env);
  }
}

async function handleFirebaseScreens(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials" }, 200, env);
  }
  if (!env.FIREBASE_GA4_PROPERTY_ID) {
    return json({ source: "no_ga4_property_id", message: "Configure FIREBASE_GA4_PROPERTY_ID no wrangler.toml." }, 200, env);
  }
  try {
    const { status, data } = await runGa4Report(env, {
      dateRanges: [{ startDate: "7daysAgo", endDate: "today" }],
      dimensions: [{ name: "unifiedScreenName" }],
      metrics: [{ name: "screenPageViews" }, { name: "activeUsers" }],
      orderBys: [{ metric: { metricName: "screenPageViews" }, desc: true }],
      limit: "20",
    });
    if (status !== 200) await logError(env, 'firebase-ga4-screens', `runReport_${status}: ${JSON.stringify(data).slice(0, 300)}`, '');
    return json({ source: "firebase_analytics", data }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-ga4-screens', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ source: "error", message: String(e) }, 500, env);
  }
}

async function handleFirebaseRetention(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials" }, 200, env);
  }
  if (!env.FIREBASE_GA4_PROPERTY_ID) {
    return json({ source: "no_ga4_property_id", message: "Configure FIREBASE_GA4_PROPERTY_ID no wrangler.toml." }, 200, env);
  }
  try {
    const { status, data } = await runGa4Report(env, {
      dateRanges: [{ startDate: "28daysAgo", endDate: "today" }],
      dimensions: [{ name: "date" }],
      metrics: [{ name: "active1DayUsers" }, { name: "active7DayUsers" }, { name: "active28DayUsers" }],
    });
    if (status !== 200) await logError(env, 'firebase-ga4-retention', `runReport_${status}: ${JSON.stringify(data).slice(0, 300)}`, '');
    return json({ source: "firebase_analytics", data }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-ga4-retention', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ source: "error", message: String(e) }, 500, env);
  }
}

interface FirebaseSyncState {
  syncedAt: string;
  eventsImported: number;
  crashesImported: number;
}

async function readFirebaseSyncState(env: Env): Promise<FirebaseSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as FirebaseSyncState;
  } catch {
    return null;
  }
}

async function writeFirebaseSyncState(env: Env, state: FirebaseSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleFirebaseStatus(_req: Request, env: Env): Promise<Response> {
  const syncState = await readFirebaseSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: "connected",
    hasCredentials: !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY),
    ga4PropertyConfigured: !!env.FIREBASE_GA4_PROPERTY_ID,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    eventsImported: syncState?.eventsImported ?? 0,
    crashesImported: syncState?.crashesImported ?? 0,
  }, 200, env);
}

// --- GH#1344: Firebase Management API + Remote Config ---
// Mesma credencial já usada por GA4/BigQuery (FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY,
// getFirebaseAccessToken() com scope "cloud-platform" já incluso). Permissão confirmada via
// chamada real em 2026-07-24 (200 em ambas).

interface FirebaseSourceRecord {
  provider: "firebase";
  service: string;
  apiVersion: string;
  resource: string;
  endpoint: string;
}

interface FirebaseManagementSyncState {
  syncedAt: string;
  source: FirebaseSourceRecord;
  project: { projectId: string; projectNumber: string; displayName: string; state: string } | null;
  androidApps: Array<{ appId: string; displayName: string; packageName: string; state: string }>;
}

async function readFirebaseManagementSyncState(env: Env): Promise<FirebaseManagementSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_management_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as FirebaseManagementSyncState;
  } catch {
    return null;
  }
}

async function writeFirebaseManagementSyncState(env: Env, state: FirebaseManagementSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_management_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleFirebaseManagementStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const syncState = await readFirebaseManagementSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    project: syncState?.project ?? null,
    androidApps: syncState?.androidApps ?? [],
  }, 200, env);
}

async function handleFirebaseManagementSync(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." }, 200, env);
  }
  try {
    const token = await getFirebaseAccessToken(env);
    const projectResp = await fetch(
      `https://firebase.googleapis.com/v1beta1/projects/${env.FIREBASE_PROJECT_ID}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!projectResp.ok) {
      const errText = await projectResp.text();
      await logError(env, 'firebase-management', `project_${projectResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar projeto (HTTP ${projectResp.status}).` }, 200, env);
    }
    const projectData = await projectResp.json() as { projectId: string; projectNumber: string; displayName: string; state: string };

    const appsResp = await fetch(
      `https://firebase.googleapis.com/v1beta1/projects/${env.FIREBASE_PROJECT_ID}/androidApps`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!appsResp.ok) {
      const errText = await appsResp.text();
      await logError(env, 'firebase-management', `apps_${appsResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar androidApps (HTTP ${appsResp.status}).` }, 200, env);
    }
    const appsData = await appsResp.json() as { apps?: Array<{ appId: string; displayName: string; packageName: string; state: string }> };

    const syncedAt = new Date().toISOString();
    const state: FirebaseManagementSyncState = {
      syncedAt,
      source: {
        provider: "firebase", service: "firebase_management", apiVersion: "v1beta1",
        resource: `projects/${env.FIREBASE_PROJECT_ID}`,
        endpoint: `https://firebase.googleapis.com/v1beta1/projects/${env.FIREBASE_PROJECT_ID}`,
      },
      project: {
        projectId: projectData.projectId,
        projectNumber: projectData.projectNumber,
        displayName: projectData.displayName,
        state: projectData.state,
      },
      androidApps: (appsData.apps ?? []).map(a => ({
        appId: a.appId, displayName: a.displayName, packageName: a.packageName, state: a.state,
      })),
    };
    await writeFirebaseManagementSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-management', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

interface RemoteConfigSyncState {
  syncedAt: string;
  source: FirebaseSourceRecord;
  parameterCount: number;
  parameterKeys: string[];
}

async function readRemoteConfigSyncState(env: Env): Promise<RemoteConfigSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_remote_config_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as RemoteConfigSyncState;
  } catch {
    return null;
  }
}

async function writeRemoteConfigSyncState(env: Env, state: RemoteConfigSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_remote_config_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleRemoteConfigStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const syncState = await readRemoteConfigSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    parameterCount: syncState?.parameterCount ?? 0,
    parameterKeys: syncState?.parameterKeys ?? [],
  }, 200, env);
}

async function handleRemoteConfigSync(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." }, 200, env);
  }
  const endpoint = `https://firebaseremoteconfig.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/remoteConfig`;
  try {
    const token = await getFirebaseAccessToken(env);
    const resp = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
    if (!resp.ok) {
      const errText = await resp.text();
      await logError(env, 'firebase-remote-config', `template_${resp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar template (HTTP ${resp.status}).` }, 200, env);
    }
    const data = await resp.json() as { parameters?: Record<string, unknown> };
    const parameterKeys = Object.keys(data.parameters ?? {});

    const syncedAt = new Date().toISOString();
    const state: RemoteConfigSyncState = {
      syncedAt,
      source: {
        provider: "firebase", service: "remote_config", apiVersion: "v1",
        resource: `projects/${env.FIREBASE_PROJECT_ID}/remoteConfig`,
        endpoint,
      },
      parameterCount: parameterKeys.length,
      parameterKeys,
    };
    await writeRemoteConfigSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-remote-config', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

// --- GH#1344: Firebase App Check + App Distribution ---
// Mesma credencial (FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY). Firebase App Check API
// habilitada e papel "Administrador do Firebase" concedido no IAM em 2026-07-24 (ação do Luiz),
// destravando o 403 anterior.

const FIREBASE_PROJECT_NUMBER = "741421457740";
const FIREBASE_ANDROID_APP_ID = "1:741421457740:android:a8658a91308fba058fefe9";

interface AppCheckSyncState {
  syncedAt: string;
  source: FirebaseSourceRecord;
  services: unknown;
}

async function readAppCheckSyncState(env: Env): Promise<AppCheckSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_app_check_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as AppCheckSyncState;
  } catch {
    return null;
  }
}

async function writeAppCheckSyncState(env: Env, state: AppCheckSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_app_check_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleAppCheckStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const syncState = await readAppCheckSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    services: syncState?.services ?? null,
  }, 200, env);
}

async function handleAppCheckSync(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." }, 200, env);
  }
  const endpoint = `https://firebaseappcheck.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/services`;
  try {
    const token = await getFirebaseAccessToken(env);
    const resp = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
    if (!resp.ok) {
      const errText = await resp.text();
      await logError(env, 'firebase-app-check', `services_${resp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar App Check (HTTP ${resp.status}).` }, 200, env);
    }
    const services = await resp.json();
    const syncedAt = new Date().toISOString();
    const state: AppCheckSyncState = {
      syncedAt,
      source: { provider: "firebase", service: "app_check", apiVersion: "v1", resource: `projects/${env.FIREBASE_PROJECT_ID}/services`, endpoint },
      services,
    };
    await writeAppCheckSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-app-check', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

interface AppDistributionRelease {
  name: string;
  displayVersion: string;
  buildVersion: string;
  createTime: string;
  releaseNotesText: string | null;
}

interface AppDistributionSyncState {
  syncedAt: string;
  source: FirebaseSourceRecord;
  releases: AppDistributionRelease[];
}

async function readAppDistributionSyncState(env: Env): Promise<AppDistributionSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_app_distribution_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as AppDistributionSyncState;
  } catch {
    return null;
  }
}

async function writeAppDistributionSyncState(env: Env, state: AppDistributionSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_app_distribution_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleAppDistributionStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const syncState = await readAppDistributionSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    releases: syncState?.releases ?? [],
  }, 200, env);
}

async function handleAppDistributionSync(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." }, 200, env);
  }
  const resource = `projects/${FIREBASE_PROJECT_NUMBER}/apps/${FIREBASE_ANDROID_APP_ID}/releases`;
  const endpoint = `https://firebaseappdistribution.googleapis.com/v1/${resource}`;
  try {
    const token = await getFirebaseAccessToken(env);
    const resp = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
    if (!resp.ok) {
      const errText = await resp.text();
      await logError(env, 'firebase-app-distribution', `releases_${resp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar App Distribution (HTTP ${resp.status}).` }, 200, env);
    }
    const data = await resp.json() as {
      releases?: Array<{ name: string; displayVersion: string; buildVersion: string; createTime: string; releaseNotes?: { text?: string } }>;
    };
    const releases: AppDistributionRelease[] = (data.releases ?? []).map(r => ({
      name: r.name, displayVersion: r.displayVersion, buildVersion: r.buildVersion,
      createTime: r.createTime, releaseNotesText: r.releaseNotes?.text ?? null,
    }));
    const syncedAt = new Date().toISOString();
    const state: AppDistributionSyncState = {
      syncedAt,
      source: { provider: "firebase", service: "app_distribution", apiVersion: "v1", resource, endpoint },
      releases,
    };
    await writeAppDistributionSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-app-distribution', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

// --- GH#1344: Firebase Cloud Messaging Data API ---
// FCM Data API habilitada pelo Luiz em 2026-07-24 (estava desabilitada, causa do 403 anterior).
// androidDeliveryData vem por app/dia — sem envio de mensagem própria ainda (SignallQ não usa FCM
// pra push hoje), então os campos de `data` chegam vazios; guardamos o payload bruto de qualquer
// forma para não perder estrutura quando isso mudar.

interface FcmDeliveryDataSyncState {
  syncedAt: string;
  source: FirebaseSourceRecord;
  androidDeliveryData: unknown[];
}

async function readFcmDeliveryDataSyncState(env: Env): Promise<FcmDeliveryDataSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'firebase_fcm_delivery_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as FcmDeliveryDataSyncState;
  } catch {
    return null;
  }
}

async function writeFcmDeliveryDataSyncState(env: Env, state: FcmDeliveryDataSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('firebase_fcm_delivery_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleFcmDeliveryDataStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const syncState = await readFcmDeliveryDataSyncState(env);
  return json({
    source: "worker",
    projectId: env.FIREBASE_PROJECT_ID,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    androidDeliveryData: syncState?.androidDeliveryData ?? [],
  }, 200, env);
}

async function handleFcmDeliveryDataSync(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." }, 200, env);
  }
  const resource = `projects/${env.FIREBASE_PROJECT_ID}/androidApps/${FIREBASE_ANDROID_APP_ID}/deliveryData`;
  const endpoint = `https://fcmdata.googleapis.com/v1beta1/${resource}`;
  try {
    const token = await getFirebaseAccessToken(env);
    const resp = await fetch(endpoint, { headers: { Authorization: `Bearer ${token}` } });
    if (!resp.ok) {
      const errText = await resp.text();
      await logError(env, 'firebase-fcm-delivery', `delivery_${resp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar FCM delivery data (HTTP ${resp.status}).` }, 200, env);
    }
    const data = await resp.json() as { androidDeliveryData?: unknown[] };
    const syncedAt = new Date().toISOString();
    const state: FcmDeliveryDataSyncState = {
      syncedAt,
      source: { provider: "firebase", service: "fcm_data", apiVersion: "v1beta1", resource, endpoint },
      androidDeliveryData: data.androidDeliveryData ?? [],
    };
    await writeFcmDeliveryDataSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'firebase-fcm-delivery', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

// --- GH#761: integração real com Google Play (Android Publisher API) ---

const GOOGLE_PLAY_PACKAGE_NAME = "io.signallq.app";

async function getGooglePlayAccessToken(env: Env, scope: string): Promise<string> {
  const now = nowSec();
  const payload = {
    iss: env.GOOGLE_PLAY_CLIENT_EMAIL,
    sub: env.GOOGLE_PLAY_CLIENT_EMAIL,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
    scope,
  };
  const privateKey = env.GOOGLE_PLAY_PRIVATE_KEY.replace(/\\n/g, "\n");
  const keyData = privateKey
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binaryKey = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8", binaryKey,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"]
  );
  const toB64Url = (s: string) =>
    btoa(s).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const header = toB64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body   = toB64Url(JSON.stringify(payload));
  const sigInput = new TextEncoder().encode(`${header}.${body}`);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, sigInput);
  const sig = toB64Url(String.fromCharCode(...new Uint8Array(signature)));
  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${body}.${sig}`,
    }),
  });
  if (!tokenResp.ok) {
    throw new Error(`google_play_oauth_${tokenResp.status}: ${(await tokenResp.text()).slice(0, 200)}`);
  }
  const tokenData = (await tokenResp.json()) as { access_token: string };
  return tokenData.access_token;
}

interface GooglePlaySyncState {
  syncedAt: string;
  ratingAverage: number | null;
  reviewsSampled: number;
}

async function readGooglePlaySyncState(env: Env): Promise<GooglePlaySyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'google_play_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as GooglePlaySyncState;
  } catch {
    return null;
  }
}

async function writeGooglePlaySyncState(env: Env, state: GooglePlaySyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('google_play_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleGooglePlayStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const syncState = await readGooglePlaySyncState(env);
  return json({
    source: "worker",
    packageName: GOOGLE_PLAY_PACKAGE_NAME,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    ratingAverage: syncState?.ratingAverage ?? null,
    reviewsSampled: syncState?.reviewsSampled ?? 0,
  }, 200, env);
}

// GH#761 — a Android Publisher API não expõe contagem de downloads/instalações
// (isso só existe via export de relatórios CSV pro Cloud Storage, configurado
// à parte no Play Console — não implementado aqui). O dado real disponível
// via API é a lista de reviews (reviews.list), com nota (starRating) por
// review — usamos a média de uma amostra recente como sinal real de
// satisfação, sem inventar número de instalações.
// GH#1341 — reviews.list expõe nota/comentário/idioma/dispositivo/resposta do dev, não só
// starRating. Guardado em tabela própria (migration 017, google_play_reviews, review_id como
// chave) porque é lista de registros identificáveis, não estado pontual/série temporal — decisão
// da Claudete, 2026-07-24 (rationale completo nos comentários da própria migration 017).
// Upsert com ON CONFLICT preserva handling_status/first_synced_at (campo admin-side, nunca vem da
// API — sync nunca pode resetá-lo). google_play_sync em admin_settings continua como cache
// rápido de resumo pro /status.

interface GooglePlayReviewApiComment {
  userComment?: {
    text?: string;
    starRating?: number;
    reviewerLanguage?: string;
    device?: string;
    androidOsVersion?: number;
    appVersionCode?: number;
    appVersionName?: string;
    lastModified?: { seconds?: string };
  };
  developerComment?: {
    text?: string;
    lastModified?: { seconds?: string };
  };
}

interface GooglePlayReviewApiEntry {
  reviewId: string;
  comments?: GooglePlayReviewApiComment[];
}

async function upsertGooglePlayReview(env: Env, review: GooglePlayReviewApiEntry, syncedAtSec: number): Promise<number | null> {
  const userComment = review.comments?.find(c => c.userComment)?.userComment;
  const developerComment = review.comments?.find(c => c.developerComment)?.developerComment;
  if (!userComment || typeof userComment.starRating !== "number") return null;

  await env.DB.prepare(
    `INSERT INTO google_play_reviews (
       review_id, rating, comment_text, language, device, android_os_version, app_version_code,
       app_version_name, review_last_modified, developer_reply_text, developer_reply_at,
       handling_status, first_synced_at, last_synced_at
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)
     ON CONFLICT(review_id) DO UPDATE SET
       rating               = excluded.rating,
       comment_text         = excluded.comment_text,
       language             = excluded.language,
       device               = excluded.device,
       android_os_version   = excluded.android_os_version,
       app_version_code     = excluded.app_version_code,
       app_version_name     = excluded.app_version_name,
       review_last_modified = excluded.review_last_modified,
       developer_reply_text = excluded.developer_reply_text,
       developer_reply_at   = excluded.developer_reply_at,
       last_synced_at       = excluded.last_synced_at`
  ).bind(
    review.reviewId,
    userComment.starRating,
    userComment.text ?? "",
    userComment.reviewerLanguage ?? "",
    userComment.device ?? "",
    userComment.androidOsVersion ?? null,
    userComment.appVersionCode ?? null,
    userComment.appVersionName ?? "",
    userComment.lastModified?.seconds ? Number(userComment.lastModified.seconds) : null,
    developerComment?.text ?? null,
    developerComment?.lastModified?.seconds ? Number(developerComment.lastModified.seconds) : null,
    syncedAtSec,
    syncedAtSec
  ).run();
  return userComment.starRating;
}

async function handleGooglePlaySync(_req: Request, env: Env): Promise<Response> {
  if (!env.GOOGLE_PLAY_CLIENT_EMAIL || !env.GOOGLE_PLAY_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY não configurados." }, 200, env);
  }
  try {
    const token = await getGooglePlayAccessToken(env, "https://www.googleapis.com/auth/androidpublisher");
    const syncedAtSec = nowSec();
    const ratings: number[] = [];
    let pageToken: string | undefined;
    let pages = 0;

    do {
      const url = new URL(`https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${GOOGLE_PLAY_PACKAGE_NAME}/reviews`);
      url.searchParams.set("maxResults", "100");
      if (pageToken) url.searchParams.set("token", pageToken);
      const resp = await fetch(url.toString(), { headers: { Authorization: `Bearer ${token}` } });
      if (!resp.ok) {
        const errText = await resp.text();
        await logError(env, 'google-play', `reviews_${resp.status}: ${errText.slice(0, 300)}`, '');
        return json({ status: "error", message: `Falha ao consultar reviews (HTTP ${resp.status}) — app pode ainda não estar publicado.` }, 200, env);
      }
      const data = await resp.json() as {
        reviews?: GooglePlayReviewApiEntry[];
        tokenPagination?: { nextPageToken?: string };
      };
      for (const review of data.reviews ?? []) {
        const rating = await upsertGooglePlayReview(env, review, syncedAtSec);
        if (rating !== null) ratings.push(rating);
      }
      pageToken = data.tokenPagination?.nextPageToken;
      pages++;
      // Trava de segurança — reviews.list não documenta limite superior de páginas; 50 páginas
      // (até 5000 reviews) é folga generosa pro volume atual do app sem risco de loop infinito
      // caso a API devolva token repetido por bug.
    } while (pageToken && pages < 50);

    const ratingAverage = ratings.length
      ? Math.round((ratings.reduce((a, b) => a + b, 0) / ratings.length) * 100) / 100
      : null;
    const syncedAt = new Date().toISOString();
    await writeGooglePlaySyncState(env, { syncedAt, ratingAverage, reviewsSampled: ratings.length });
    return json({ status: "ok", syncedAt, ratingAverage, reviewsSampled: ratings.length, pagesFetched: pages }, 200, env);
  } catch (e) {
    await logError(env, 'google-play', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

async function handleGooglePlayReviewsList(req: Request, env: Env): Promise<Response> {
  const url = new URL(req.url);
  const handlingStatus = url.searchParams.get("handlingStatus");
  const limit = Math.min(Number(url.searchParams.get("limit") ?? "50") || 50, 200);
  let query = "SELECT * FROM google_play_reviews";
  const binds: unknown[] = [];
  if (handlingStatus) {
    query += " WHERE handling_status = ?";
    binds.push(handlingStatus);
  }
  query += " ORDER BY rating ASC, review_last_modified DESC LIMIT ?";
  binds.push(limit);
  const { results } = await env.DB.prepare(query).bind(...binds).all();
  return json({ source: "worker", reviews: results ?? [] }, 200, env);
}

// --- migration 012_play_track.sql — mapeamento version_code -> trilha do Play Console ---
// Sem isso, um tester de trilha fechada (internal/alpha/beta) instalado via link do Play
// Console gera dist_channel="play_store" + environment="production", indistinguível de um
// usuário real (ver DistributionChannel.kt). A Android Publisher API não expõe leitura de
// tracks sem abrir um edit — mesmo sendo somente-leitura, o ciclo create→read→discard é o
// único documentado (mesmo padrão usado por fastlane/supply). O edit nunca é commitado
// (sem PUT); o DELETE em `finally` garante que o edit é sempre descartado.

interface GooglePlayTrackRelease {
  versionCodes?: string[];
}

interface GooglePlayTrackEntry {
  track: string;
  releases?: GooglePlayTrackRelease[];
}

interface GooglePlayTracksListResponse {
  tracks?: GooglePlayTrackEntry[];
}

interface GooglePlayTracksSyncState {
  syncedAt: string;
  tracksCount: number;
}

async function readGooglePlayTracksSyncState(env: Env): Promise<GooglePlayTracksSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'google_play_tracks_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as GooglePlayTracksSyncState;
  } catch {
    return null;
  }
}

async function writeGooglePlayTracksSyncState(env: Env, state: GooglePlayTracksSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('google_play_tracks_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleGooglePlayTracksStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const syncState = await readGooglePlayTracksSyncState(env);
  return json({
    source: "worker",
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    tracksCount: syncState?.tracksCount ?? 0,
  }, 200, env);
}

async function handleGooglePlayTracksSync(_req: Request, env: Env): Promise<Response> {
  if (!env.GOOGLE_PLAY_CLIENT_EMAIL || !env.GOOGLE_PLAY_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY não configurados." }, 200, env);
  }

  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${GOOGLE_PLAY_PACKAGE_NAME}`;
  let token: string;
  try {
    token = await getGooglePlayAccessToken(env, "https://www.googleapis.com/auth/androidpublisher");
  } catch (e) {
    await logError(env, 'google-play', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }

  let editId: string | null = null;
  try {
    const editResp = await fetch(`${base}/edits`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!editResp.ok) {
      const errText = await editResp.text();
      await logError(env, 'google-play', `edits_insert_${editResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao criar edit (HTTP ${editResp.status}) — app pode ainda não estar publicado.` }, 200, env);
    }
    editId = ((await editResp.json()) as { id: string }).id;

    const tracksResp = await fetch(`${base}/edits/${editId}/tracks`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!tracksResp.ok) {
      const errText = await tracksResp.text();
      await logError(env, 'google-play', `tracks_list_${tracksResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao listar trilhas (HTTP ${tracksResp.status}).` }, 200, env);
    }
    const tracksData = (await tracksResp.json()) as GooglePlayTracksListResponse;

    const mapping: Array<{ versionCode: number; track: string }> = [];
    for (const t of tracksData.tracks ?? []) {
      for (const release of t.releases ?? []) {
        for (const vc of release.versionCodes ?? []) {
          const versionCode = parseInt(vc, 10);
          if (!Number.isNaN(versionCode)) mapping.push({ versionCode, track: t.track });
        }
      }
    }

    const syncedAtSec = nowSec();
    if (mapping.length > 0) {
      await env.DB.batch(
        mapping.map((m) =>
          env.DB.prepare(
            "INSERT OR REPLACE INTO play_console_tracks (version_code, track, synced_at) VALUES (?, ?, ?)"
          ).bind(m.versionCode, m.track, syncedAtSec)
        )
      );
    }

    const syncedAt = new Date().toISOString();
    await writeGooglePlayTracksSyncState(env, { syncedAt, tracksCount: mapping.length });
    return json({ status: "ok", syncedAt, tracksCount: mapping.length }, 200, env);
  } catch (e) {
    await logError(env, 'google-play', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  } finally {
    // Descarta o edit sem commitar nada (sem PUT) — sempre executa, mesmo em erro.
    if (editId) {
      try {
        await fetch(`${base}/edits/${editId}`, {
          method: "DELETE",
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch (e) {
        await logError(env, 'google-play', `edit_discard_failed: ${String(e)}`, '');
      }
    }
  }
}

// GH#1342 — store listing (título/descrição por idioma via Publisher API). Mesmo padrão de
// edit→read→discard do #761 (tracks): cria edit, lê listings, descarta sem PUT. `admin_settings`
// é suficiente aqui (estado pontual de config, não série temporal) — mesmo critério já aplicado
// em Firebase Management/Remote Config/App Check/App Distribution/FCM, revisado pela Claudete em
// 2026-07-24 (rationale completo nos comentários da migration 016).

interface StoreListingEntry {
  language: string;
  title: string;
  fullDescription: string;
  shortDescription: string;
}

interface StoreListingSyncState {
  syncedAt: string;
  source: { provider: "google_play"; service: "android_publisher"; apiVersion: "v3"; resource: string; endpoint: string };
  listings: StoreListingEntry[];
}

async function readStoreListingSyncState(env: Env): Promise<StoreListingSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'google_play_store_listing_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as StoreListingSyncState;
  } catch {
    return null;
  }
}

async function writeStoreListingSyncState(env: Env, state: StoreListingSyncState): Promise<void> {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('google_play_store_listing_sync', ?, ?)"
  ).bind(JSON.stringify(state), nowSec()).run();
}

async function handleStoreListingStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const syncState = await readStoreListingSyncState(env);
  return json({
    source: "worker",
    packageName: GOOGLE_PLAY_PACKAGE_NAME,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    listings: syncState?.listings ?? [],
  }, 200, env);
}

async function handleStoreListingSync(_req: Request, env: Env): Promise<Response> {
  if (!env.GOOGLE_PLAY_CLIENT_EMAIL || !env.GOOGLE_PLAY_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY não configurados." }, 200, env);
  }
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${GOOGLE_PLAY_PACKAGE_NAME}`;
  let token: string;
  try {
    token = await getGooglePlayAccessToken(env, "https://www.googleapis.com/auth/androidpublisher");
  } catch (e) {
    await logError(env, 'google-play-store-listing', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }

  let editId: string | null = null;
  try {
    const editResp = await fetch(`${base}/edits`, { method: "POST", headers: { Authorization: `Bearer ${token}` } });
    if (!editResp.ok) {
      const errText = await editResp.text();
      await logError(env, 'google-play-store-listing', `edits_insert_${editResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao criar edit (HTTP ${editResp.status}).` }, 200, env);
    }
    editId = ((await editResp.json()) as { id: string }).id;

    const listingsResp = await fetch(`${base}/edits/${editId}/listings`, { headers: { Authorization: `Bearer ${token}` } });
    if (!listingsResp.ok) {
      const errText = await listingsResp.text();
      await logError(env, 'google-play-store-listing', `listings_${listingsResp.status}: ${errText.slice(0, 300)}`, '');
      return json({ status: "error", message: `Falha ao consultar listings (HTTP ${listingsResp.status}).` }, 200, env);
    }
    const listingsData = await listingsResp.json() as {
      listings?: Array<{ language: string; title: string; fullDescription: string; shortDescription: string }>;
    };
    const listings: StoreListingEntry[] = (listingsData.listings ?? []).map(l => ({
      language: l.language, title: l.title, fullDescription: l.fullDescription, shortDescription: l.shortDescription,
    }));

    const syncedAt = new Date().toISOString();
    const state: StoreListingSyncState = {
      syncedAt,
      source: {
        provider: "google_play", service: "android_publisher", apiVersion: "v3",
        resource: `applications/${GOOGLE_PLAY_PACKAGE_NAME}/edits/listings`,
        endpoint: `${base}/edits/{editId}/listings`,
      },
      listings,
    };
    await writeStoreListingSyncState(env, state);
    return json({ status: "ok", ...state }, 200, env);
  } catch (e) {
    await logError(env, 'google-play-store-listing', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  } finally {
    if (editId) {
      try {
        await fetch(`${base}/edits/${editId}`, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } });
      } catch (e) {
        await logError(env, 'google-play-store-listing', `edit_discard_failed: ${String(e)}`, '');
      }
    }
  }
}

// Backfill explícito e separado do sync: só aplica o mapeamento já salvo em
// play_console_tracks aos dados históricos, sem chamar a API do Google. Idempotente —
// só toca linhas com play_track IS NULL, então rodar de novo nunca duplica/sobrescreve
// trabalho já feito; simples de re-rodar quando novas versões forem mapeadas depois.
//
// ai_usage não tem coluna `dist_channel` própria (só diagnostic_sessions e
// analytics_events têm) — a origem play_store é inferida via join em
// diagnostic_sessions.session_id. Se session_id for nulo ou não houver sessão
// correspondente, a linha de ai_usage fica sem play_track (não inventa dado).
async function handleGooglePlayTracksBackfill(_req: Request, env: Env): Promise<Response> {
  try {
    const results = await env.DB.batch([
      env.DB.prepare(
        `UPDATE diagnostic_sessions
         SET play_track = (SELECT track FROM play_console_tracks WHERE version_code = diagnostic_sessions.version_code)
         WHERE dist_channel = 'play_store' AND play_track IS NULL
           AND EXISTS (SELECT 1 FROM play_console_tracks WHERE version_code = diagnostic_sessions.version_code)`
      ),
      env.DB.prepare(
        `UPDATE ai_usage
         SET play_track = (SELECT track FROM play_console_tracks WHERE version_code = ai_usage.version_code)
         WHERE play_track IS NULL
           AND EXISTS (SELECT 1 FROM play_console_tracks WHERE version_code = ai_usage.version_code)
           AND EXISTS (
             SELECT 1 FROM diagnostic_sessions ds
             WHERE ds.id = ai_usage.session_id AND ds.dist_channel = 'play_store'
           )`
      ),
      env.DB.prepare(
        `UPDATE analytics_events
         SET play_track = (SELECT track FROM play_console_tracks WHERE version_code = analytics_events.version_code)
         WHERE dist_channel = 'play_store' AND play_track IS NULL
           AND EXISTS (SELECT 1 FROM play_console_tracks WHERE version_code = analytics_events.version_code)`
      ),
    ]);

    return json({
      status: "ok",
      updated: {
        diagnostic_sessions: results[0]?.meta?.changes ?? 0,
        ai_usage:            results[1]?.meta?.changes ?? 0,
        analytics_events:    results[2]?.meta?.changes ?? 0,
      },
    }, 200, env);
  } catch (e) {
    await logError(env, 'google-play', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

// --- GH#1342: Play Developer Reporting API v1beta1 (Android Vitals — ANR rate) ---
// Mesma service account/chave do #761 (GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY),
// scope adicional `playdeveloperreporting`. Permissão confirmada via chamada real em
// 2026-07-24 (200 com freshnessInfo real).
//
// `source` estruturado por registro (provider/service/apiVersion/resource/endpoint) —
// nunca uma string solta — pra permitir isolar cada fonte na UI sem fundir com outras
// (ex.: nunca somar/misturar com crash rate do Firebase Crashlytics).

interface PlayVitalsSource {
  provider: "google_play";
  service: "play_developer_reporting";
  apiVersion: "v1beta1";
  resource: string;
  endpoint: string;
}

interface PlayVitalsSyncState {
  syncedAt: string;
  source: PlayVitalsSource;
  freshnessInfo: unknown;
  anrRatePercent: number | null;
  aggregationPeriod: string;
  rangeStart: string;
  rangeEnd: string;
}

async function readPlayVitalsSyncState(env: Env): Promise<PlayVitalsSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'google_play_vitals_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as PlayVitalsSyncState;
  } catch {
    return null;
  }
}

async function handlePlayVitalsStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const syncState = await readPlayVitalsSyncState(env);
  return json({
    source: "worker",
    packageName: GOOGLE_PLAY_PACKAGE_NAME,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    anrRatePercent: syncState?.anrRatePercent ?? null,
    rangeStart: syncState?.rangeStart ?? null,
    rangeEnd: syncState?.rangeEnd ?? null,
  }, 200, env);
}

function ymd(date: Date): { year: number; month: number; day: number } {
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

// GH#1341 — generalização pra cobrir crash rate além de ANR: mesma API, mesma forma de query
// (sem dimensão obrigatória). slowStartRate/excessiveWakeupRate/stuckBackgroundWakelockRate
// exigem dimensão obrigatória (ex.: startType) — shape de query diferente, não cabem nesta
// função; ficam como próxima fatia documentada, não implementadas às pressas.
interface SimpleVitalsMetricConfig {
  metricSetResource: string; // ex.: "anrRateMetricSet"
  metricName: string;        // ex.: "anrRate"
  settingsKey: string;       // ex.: "google_play_vitals_sync"
}

async function syncSimpleVitalsMetric(env: Env, config: SimpleVitalsMetricConfig): Promise<PlayVitalsSyncState | { error: string }> {
  const resource = `apps/${GOOGLE_PLAY_PACKAGE_NAME}/${config.metricSetResource}`;
  const base = `https://playdeveloperreporting.googleapis.com/v1beta1/${resource}`;
  const token = await getGooglePlayAccessToken(env, "https://www.googleapis.com/auth/playdeveloperreporting");

  // Freshness primeiro — confirma que a fonte está acessível e até quando os dados chegam,
  // sem assumir um range fixo de datas que pode não ter dado disponível ainda.
  const metricSetResp = await fetch(base, { headers: { Authorization: `Bearer ${token}` } });
  if (!metricSetResp.ok) {
    const errText = await metricSetResp.text();
    await logError(env, 'google-play-vitals', `metricset_${config.metricSetResource}_${metricSetResp.status}: ${errText.slice(0, 300)}`, '');
    return { error: `Falha ao consultar ${config.metricSetResource} (HTTP ${metricSetResp.status}).` };
  }
  const metricSet = await metricSetResp.json() as {
    freshnessInfo?: { freshnesses?: Array<{ aggregationPeriod?: string; latestEndTime?: { year: number; month: number; day: number } }> };
  };
  const daily = metricSet.freshnessInfo?.freshnesses?.find(f => f.aggregationPeriod === "DAILY");
  if (!daily?.latestEndTime) {
    return { error: `${config.metricSetResource}: freshnessInfo sem aggregationPeriod DAILY — sem dado disponível ainda.` };
  }
  const endDate = new Date(Date.UTC(daily.latestEndTime.year, daily.latestEndTime.month - 1, daily.latestEndTime.day));
  const startDate = new Date(endDate);
  startDate.setUTCDate(startDate.getUTCDate() - 6); // janela de 7 dias terminando no último dia com dado

  const queryResp = await fetch(`${base}:query`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      timelineSpec: { aggregationPeriod: "DAILY", startTime: ymd(startDate), endTime: ymd(endDate) },
      metrics: [config.metricName],
      pageSize: 100,
    }),
  });
  if (!queryResp.ok) {
    const errText = await queryResp.text();
    await logError(env, 'google-play-vitals', `query_${config.metricSetResource}_${queryResp.status}: ${errText.slice(0, 300)}`, '');
    return { error: `Falha ao consultar ${config.metricSetResource}:query (HTTP ${queryResp.status}).` };
  }
  const queryData = await queryResp.json() as { rows?: Array<{ metrics?: Array<{ metric?: string; decimalValue?: { value?: string } }> }> };
  const values: number[] = [];
  for (const row of queryData.rows ?? []) {
    for (const m of row.metrics ?? []) {
      if (m.metric === config.metricName && m.decimalValue?.value) values.push(Number(m.decimalValue.value));
    }
  }
  const ratePercent = values.length
    ? Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 10000) / 100
    : null;

  const syncedAt = new Date().toISOString();
  const state: PlayVitalsSyncState = {
    syncedAt,
    source: { provider: "google_play", service: "play_developer_reporting", apiVersion: "v1beta1", resource, endpoint: `${base}:query` },
    freshnessInfo: metricSet.freshnessInfo ?? null,
    anrRatePercent: ratePercent,
    aggregationPeriod: "DAILY",
    rangeStart: startDate.toISOString().slice(0, 10),
    rangeEnd: endDate.toISOString().slice(0, 10),
  };
  // Único caller autorizado de recordIntegrationSnapshot — ver comentário do helper (trim de
  // 2026-07-24). Não replicar esta chamada para outras integrações Play/Firebase.
  await recordIntegrationSnapshot(env, {
    provider: "google_play", service: "play_developer_reporting", resource: state.source.resource,
    metric: config.metricName, periodStart: state.rangeStart, periodEnd: state.rangeEnd,
    valueNumeric: ratePercent, payload: state,
  });
  await env.DB.prepare(
    "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES (?, ?, ?)"
  ).bind(config.settingsKey, JSON.stringify(state), nowSec()).run();
  return state;
}

async function handlePlayVitalsSync(_req: Request, env: Env): Promise<Response> {
  if (!env.GOOGLE_PLAY_CLIENT_EMAIL || !env.GOOGLE_PLAY_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY não configurados." }, 200, env);
  }
  try {
    const result = await syncSimpleVitalsMetric(env, {
      metricSetResource: "anrRateMetricSet", metricName: "anrRate", settingsKey: "google_play_vitals_sync",
    });
    if ("error" in result) return json({ status: "error", message: result.error }, 200, env);
    return json({ status: "ok", ...result }, 200, env);
  } catch (e) {
    await logError(env, 'google-play-vitals', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

async function readPlayCrashRateSyncState(env: Env): Promise<PlayVitalsSyncState | null> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'google_play_crash_rate_sync'"
  ).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    return JSON.parse(row.value) as PlayVitalsSyncState;
  } catch {
    return null;
  }
}

async function handlePlayCrashRateStatus(_req: Request, env: Env): Promise<Response> {
  const hasCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const syncState = await readPlayCrashRateSyncState(env);
  return json({
    source: "worker",
    packageName: GOOGLE_PLAY_PACKAGE_NAME,
    status: hasCredentials ? "connected" : "disabled",
    hasCredentials,
    lastSyncTimestamp: syncState?.syncedAt ?? null,
    crashRatePercent: syncState?.anrRatePercent ?? null,
    rangeStart: syncState?.rangeStart ?? null,
    rangeEnd: syncState?.rangeEnd ?? null,
  }, 200, env);
}

async function handlePlayCrashRateSync(_req: Request, env: Env): Promise<Response> {
  if (!env.GOOGLE_PLAY_CLIENT_EMAIL || !env.GOOGLE_PLAY_PRIVATE_KEY) {
    return json({ status: "not_configured", message: "GOOGLE_PLAY_CLIENT_EMAIL/GOOGLE_PLAY_PRIVATE_KEY não configurados." }, 200, env);
  }
  try {
    const result = await syncSimpleVitalsMetric(env, {
      metricSetResource: "crashRateMetricSet", metricName: "crashRate", settingsKey: "google_play_crash_rate_sync",
    });
    if ("error" in result) return json({ status: "error", message: result.error }, 200, env);
    const { anrRatePercent, ...rest } = result;
    return json({ status: "ok", ...rest, crashRatePercent: anrRatePercent }, 200, env);
  } catch (e) {
    await logError(env, 'google-play-vitals', String(e), e instanceof Error ? (e.stack ?? '') : '');
    return json({ status: "error", message: String(e) }, 200, env);
  }
}

// --- GH#417 / GH#425: saúde do sistema com verificação real de cada dependência ---

interface HealthCheckResult {
  status: "ok" | "error" | "not_configured" | "idle";
  latencyMs?: number;
  message?: string;
}

async function checkD1Health(env: Env): Promise<HealthCheckResult> {
  const start = Date.now();
  try {
    await env.DB.prepare("SELECT 1 AS ok").first();
    return { status: "ok", latencyMs: Date.now() - start };
  } catch (e) {
    return { status: "error", latencyMs: Date.now() - start, message: String(e) };
  }
}

async function checkFirebaseCredentialsHealth(env: Env): Promise<HealthCheckResult> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return { status: "not_configured", message: "FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados." };
  }
  const start = Date.now();
  try {
    await getFirebaseAccessToken(env);
    return { status: "ok", latencyMs: Date.now() - start };
  } catch (e) {
    return { status: "error", latencyMs: Date.now() - start, message: String(e) };
  }
}

async function checkBigQueryHealth(env: Env, firebaseOk: boolean): Promise<HealthCheckResult> {
  if (!firebaseOk) {
    return { status: "not_configured", message: "Requer credenciais Firebase válidas para autenticar no BigQuery." };
  }
  const start = Date.now();
  const { error } = await queryBigQuery(env, "SELECT 1 AS ok");
  if (error) return { status: "error", latencyMs: Date.now() - start, message: error };
  return { status: "ok", latencyMs: Date.now() - start };
}

interface IngestHealthResult extends HealthCheckResult {
  keyConfigured: boolean;
  lastSuccessAt: string | null;
}

async function checkIngestHealth(env: Env): Promise<IngestHealthResult> {
  const keyConfigured = !!env.INGEST_KEY;
  if (!keyConfigured) {
    return { status: "not_configured", keyConfigured: false, lastSuccessAt: null, message: "INGEST_KEY não configurada." };
  }

  const row = await env.DB.prepare(
    "SELECT MAX(created_at) AS last FROM diagnostic_sessions"
  ).first<{ last: number | null }>();
  const lastSuccessAt = row?.last ? new Date(row.last * 1000).toISOString() : null;

  // Sem ingest nas últimas 48h é sinal de app parado de enviar dados, não necessariamente erro,
  // mas o painel não deve mostrar "ok" silencioso quando não há evidência de atividade recente.
  const idleThresholdMs = 48 * 60 * 60 * 1000;
  const isIdle = !lastSuccessAt || Date.now() - new Date(lastSuccessAt).getTime() > idleThresholdMs;

  return {
    status: isIdle ? "idle" : "ok",
    keyConfigured: true,
    lastSuccessAt,
    message: isIdle ? "Nenhum ingest de diagnóstico recebido nas últimas 48h." : undefined,
  };
}

async function handleSystemHealth(_req: Request, env: Env): Promise<Response> {
  const d1 = await checkD1Health(env);
  const firebaseCredentials = await checkFirebaseCredentialsHealth(env);
  const bigQuery = await checkBigQueryHealth(env, firebaseCredentials.status === "ok");
  const ingest = await checkIngestHealth(env);

  const lastErrorRow = await env.DB.prepare(
    "SELECT source, message, last_seen FROM system_errors ORDER BY last_seen DESC LIMIT 1"
  ).first<{ source: string; message: string; last_seen: number }>();
  const lastFailure = lastErrorRow
    ? {
        source: lastErrorRow.source,
        message: lastErrorRow.message,
        timestamp: new Date(lastErrorRow.last_seen).toISOString(),
      }
    : null;

  const lastSuccess = ingest.lastSuccessAt
    ? { source: "ingest", timestamp: ingest.lastSuccessAt }
    : null;

  return json(
    {
      source: "worker",
      timestamp: new Date().toISOString(),
      checks: {
        worker: { status: "ok" as const },
        d1,
        firebaseCredentials,
        bigQuery,
        ingest,
      },
      lastFailure,
      lastSuccess,
    },
    200,
    env
  );
}

type IntegrationReadinessState =
  | 'not_configured'
  | 'not_synced'
  | 'stale'
  | 'synced_without_data'
  | 'ready';

type ApiReachability = 'not_configured' | 'not_verified' | 'reachable';

interface IntegrationReadinessItem {
  id: string;
  credentialsConfigured: boolean;
  apiReachability: ApiReachability;
  lastSyncAt: string | null;
  recordsReceived: number | null;
  state: IntegrationReadinessState;
}

function integrationReadiness(
  id: string,
  credentialsConfigured: boolean,
  rawSyncState: unknown,
  recordsReceived: number | null,
): IntegrationReadinessItem {
  const syncedAt = rawSyncState && typeof rawSyncState === 'object' && typeof (rawSyncState as any).syncedAt === 'string'
    ? (rawSyncState as any).syncedAt as string
    : null;
  const apiReachability: ApiReachability = !credentialsConfigured
    ? 'not_configured'
    // Uma sincronização concluída é a única evidência local de que a API foi alcançada.
    // Sem sync, a rota não inventa uma verificação de conectividade nem uma falha remota.
    : syncedAt ? 'reachable' : 'not_verified';

  if (!credentialsConfigured) return { id, credentialsConfigured, apiReachability, lastSyncAt: syncedAt, recordsReceived, state: 'not_configured' };
  if (!syncedAt) return { id, credentialsConfigured, apiReachability, lastSyncAt: null, recordsReceived, state: 'not_synced' };
  const syncedAtMs = Date.parse(syncedAt);
  const nowMs = Date.now();
  if (!Number.isFinite(syncedAtMs) || syncedAtMs > nowMs || nowMs - syncedAtMs > 48 * 60 * 60 * 1000) {
    return { id, credentialsConfigured, apiReachability, lastSyncAt: syncedAt, recordsReceived, state: 'stale' };
  }
  if (recordsReceived === null || recordsReceived <= 0) {
    return { id, credentialsConfigured, apiReachability, lastSyncAt: syncedAt, recordsReceived, state: 'synced_without_data' };
  }
  return { id, credentialsConfigured, apiReachability, lastSyncAt: syncedAt, recordsReceived, state: 'ready' };
}

async function readIntegrationSyncState(env: Env, key: string): Promise<Record<string, unknown> | null> {
  const row = await env.DB.prepare("SELECT value FROM admin_settings WHERE key = ?").bind(key).first<{ value: string }>();
  if (!row?.value) return null;
  try {
    const parsed = JSON.parse(row.value);
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

// P1: conecta configuração, alcance de API, execução, frescor e volume sem transformar uma
// credencial, um sync legado ou um SELECT 1 em alegação de que a fonte possui dados úteis.
export async function handleIntegrationReadiness(_req: Request, env: Env): Promise<Response> {
  const [firebase, vitals, tracks, reviews] = await Promise.all([
    readIntegrationSyncState(env, 'firebase_sync'),
    readIntegrationSyncState(env, 'google_play_vitals_sync'),
    readIntegrationSyncState(env, 'google_play_tracks_sync'),
    readIntegrationSyncState(env, 'google_play_sync'),
  ]);
  const firebaseCredentials = !!(env.FIREBASE_CLIENT_EMAIL && env.FIREBASE_PRIVATE_KEY);
  const playCredentials = !!(env.GOOGLE_PLAY_CLIENT_EMAIL && env.GOOGLE_PLAY_PRIVATE_KEY);
  const numberOf = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
  const sumOf = (...values: Array<number | null>): number | null => values.every((value) => value === null)
    ? null
    : values.reduce<number>((sum, value) => sum + (value ?? 0), 0);
  const integrations = [
    integrationReadiness('firebase_analytics', firebaseCredentials, firebase, sumOf(numberOf(firebase?.eventsImported), numberOf(firebase?.crashesImported))),
    integrationReadiness('google_play_vitals', playCredentials, vitals, numberOf(vitals?.samplesReceived)),
    integrationReadiness('google_play_tracks', playCredentials, tracks, numberOf(tracks?.tracksCount)),
    integrationReadiness('google_play_reviews', playCredentials, reviews, numberOf(reviews?.reviewsSampled)),
  ];
  const byState = Object.fromEntries(
    Array.from(new Set(integrations.map((item) => item.state))).map((state) => [
      state,
      integrations.filter((item) => item.state === state).length,
    ]),
  );

  return json({
    source: 'worker',
    generatedAt: new Date().toISOString(),
    environment: 'worker',
    platform: 'android',
    freshnessThresholdHours: 48,
    coverage: {
      totalIntegrations: integrations.length,
      readyIntegrations: integrations.filter((item) => item.state === 'ready').length,
      nonReadyIntegrations: integrations.filter((item) => item.state !== 'ready').length,
      byState,
    },
    integrations,
  }, 200, env);
}

// #788 — snapshot periódico de latência/uptime, gravado pelo Cron Trigger
// (ver `scheduled` no export default). Reaproveita os MESMOS checks reais já
// usados por /admin/system-health (checkD1Health, checkFirebaseCredentialsHealth,
// checkBigQueryHealth) — nenhum dado novo é fabricado, só passa a ser persistido.
async function runDailyVisitReport(env: Env): Promise<void> {
  try {
    await sendDailyVisitReport(env);
  } catch (e) {
    await logError(env, 'daily-visit-report', String(e), e instanceof Error ? (e.stack ?? '') : '');
  }
}

async function runHealthSnapshot(env: Env): Promise<void> {
  const now = nowSec();
  const d1 = await checkD1Health(env);
  const firebaseCredentials = await checkFirebaseCredentialsHealth(env);
  const bigQuery = await checkBigQueryHealth(env, firebaseCredentials.status === "ok");

  const rows: Array<{ service: string; check: HealthCheckResult }> = [
    { service: "d1", check: d1 },
    { service: "firebase", check: firebaseCredentials },
    { service: "bigquery", check: bigQuery },
  ];

  for (const { service, check } of rows) {
    const id = `${service}_${now}`;
    await env.DB.prepare(
      `INSERT OR IGNORE INTO system_health_snapshots (id, service, status, latency_ms, created_at)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(id, service, check.status, check.latencyMs ?? null, now).run();
  }

  // Housekeeping: mantém só os últimos 35 dias (o painel usa no máximo 30d) —
  // evita crescimento sem limite no D1 free tier (5 GB total da conta).
  const cutoff = now - 35 * 86400;
  await env.DB.prepare(
    "DELETE FROM system_health_snapshots WHERE created_at < ?"
  ).bind(cutoff).run();
}

// POST /admin/system-health/snapshot — dispara runHealthSnapshot sob demanda
// (mesma auth de sessão de /admin/*). Útil pro operador forçar um ponto na série
// sem esperar o próximo Cron Trigger (ex.: logo após o deploy, pra popular o
// gráfico antes dos primeiros 15min), e foi assim que validei o endpoint de
// history em produção antes de abrir a PR.
async function handleTriggerHealthSnapshot(_req: Request, env: Env): Promise<Response> {
  await runHealthSnapshot(env);
  return json({ ok: true, timestamp: new Date().toISOString() }, 200, env);
}

interface DailyHealthPoint {
  date: string;        // YYYY-MM-DD (UTC)
  latencyP95Ms: number | null; // null quando não há amostra "ok" nesse dia
  uptimePercentage: number | null;
}

function percentile95(sorted: number[]): number {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1);
  return sorted[Math.max(0, idx)];
}

// GET /admin/system-health/history?days=14 — #788: série diária de latência
// P95 (proxy: checks de D1, chamado em quase toda rota) e uptime (% de
// snapshots com status "ok" em qualquer serviço monitorado) para o gráfico
// "Latência P95 da API · Nd" da tela Saúde do Sistema.
async function handleSystemHealthHistory(request: Request, env: Env): Promise<Response> {
  const url  = new URL(request.url);
  const days = Math.min(Math.max(parseInt(url.searchParams.get("days") ?? "14", 10) || 14, 1), 30);
  const since = nowSec() - days * 86400;

  const rows = await env.DB.prepare(
    `SELECT service, status, latency_ms, created_at
     FROM system_health_snapshots
     WHERE created_at >= ?
     ORDER BY created_at ASC`
  ).bind(since).all<{ service: string; status: string; latency_ms: number | null; created_at: number }>();

  const results = rows.results ?? [];

  if (results.length === 0) {
    return json({ source: "d1", period: `${days}d`, points: [] as DailyHealthPoint[] }, 200, env);
  }

  // Agrupa por dia UTC (YYYY-MM-DD). "Latência P95 da API" usa só service='d1'
  // (proxy mais estável — é chamado em quase toda rota do worker); uptime
  // considera todos os serviços monitorados (d1/firebase/bigquery).
  const byDay = new Map<string, { d1Latencies: number[]; okCount: number; totalCount: number }>();
  for (const r of results) {
    const date = new Date(r.created_at * 1000).toISOString().slice(0, 10);
    if (!byDay.has(date)) byDay.set(date, { d1Latencies: [], okCount: 0, totalCount: 0 });
    const bucket = byDay.get(date)!;
    if (r.service === "d1" && r.status === "ok" && r.latency_ms != null) {
      bucket.d1Latencies.push(r.latency_ms);
    }
    if (r.status !== "not_configured") {
      bucket.totalCount += 1;
      if (r.status === "ok") bucket.okCount += 1;
    }
  }

  const points: DailyHealthPoint[] = Array.from(byDay.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, bucket]) => ({
      date,
      latencyP95Ms: bucket.d1Latencies.length > 0
        ? percentile95([...bucket.d1Latencies].sort((a, b) => a - b))
        : null,
      uptimePercentage: bucket.totalCount > 0
        ? Math.round((bucket.okCount / bucket.totalCount) * 10000) / 100
        : null,
    }));

  return json({ source: "d1", period: `${days}d`, points }, 200, env);
}

async function handleFirebaseCrashlytics(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials", unresolvedCrashes: 0, crashFreeUsersPercentage: 100 }, 200, env);
  }

  const { rows, error } = await queryBigQuery<{
    total_crashes: string;
    affected_users: string;
    total_users: string;
  }>(env, `
    SELECT
      COUNT(*)                                   AS total_crashes,
      COUNT(DISTINCT installation_uuid)           AS affected_users,
      (SELECT COUNT(DISTINCT installation_uuid)
       FROM \`${env.FIREBASE_PROJECT_ID}.analytics_${env.FIREBASE_GA4_PROPERTY_ID}.events_*\`
       WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
         AND event_name = 'session_start'
      )                                          AS total_users
    FROM \`${env.FIREBASE_PROJECT_ID}.firebase_crashlytics.android_crashes_*\`
    WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY))
  `);

  if (error === "table_not_found" || rows.length === 0) {
    return json({
      source: "no_data_yet",
      message: "BigQuery export ativo; dados disponíveis em até 24h do primeiro crash.",
      unresolvedCrashes: 0,
      crashFreeUsersPercentage: 100,
    }, 200, env);
  }
  if (error) {
    await logError(env, 'bigquery-crashlytics', error, '');
    return json({ source: "error", message: error, unresolvedCrashes: 0, crashFreeUsersPercentage: 100 }, 200, env);
  }

  const row = rows[0];
  const totalCrashes  = parseInt(row.total_crashes  ?? "0", 10);
  const affectedUsers = parseInt(row.affected_users ?? "0", 10);
  const totalUsers    = parseInt(row.total_users    ?? "1", 10);
  const crashFreeUsersPercentage = totalUsers > 0
    ? Math.round(((totalUsers - affectedUsers) / totalUsers) * 10000) / 100
    : 100;

  return json({
    source: "bigquery",
    unresolvedCrashes: totalCrashes,
    affectedUsers,
    crashFreeUsersPercentage,
  }, 200, env);
}

// #879 (achado 2 da auditoria 2026-07-10): esta função ignorava period/environment
// da query string por completo (janela fixa de 30 dias, todos os ambientes),
// enquanto o denominador (productionVersion.sessions, de handleAppVersions) RESPEITA
// os dois filtros — dividir numerador fixo por denominador filtrado distorcia o crash
// rate sempre que o filtro selecionado era diferente de "30d/produção".
//
// `period` agora controla de fato a janela do BigQuery (mesmo mapeamento de
// periodToSeconds usado no resto do worker). `environment`, porém, não pode ser
// respeitado aqui: o export do Crashlytics não carrega o conceito de "environment"
// (production/staging/development) que existe só no nosso schema D1 — não há coluna
// nem dimensão equivalente em `firebase_crashlytics.android_crashes_*`. Em vez de
// fingir que filtra, a resposta agora expõe `environmentScope: "all"` explicitamente
// para o frontend avisar o usuário, conforme a alternativa descrita na issue #879.
async function handleFirebaseVersions(req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials", versions: [] }, 200, env);
  }

  const url = new URL(req.url);
  const period = url.searchParams.get("period") ?? "30d";
  const windowDays = Math.max(1, Math.round(periodToSeconds(period) / 86400));

  const { rows, error } = await queryBigQuery<{
    app_version: string;
    total_crashes: string;
    affected_users: string;
  }>(env, `
    SELECT
      app_version,
      COUNT(*)                          AS total_crashes,
      COUNT(DISTINCT installation_uuid) AS affected_users
    FROM \`${env.FIREBASE_PROJECT_ID}.firebase_crashlytics.android_crashes_*\`
    WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL ${windowDays} DAY))
    GROUP BY app_version
    ORDER BY total_crashes DESC
    LIMIT 10
  `);

  if (error === "table_not_found" || !rows.length) {
    return json({ source: "no_data_yet", versions: [], period, environmentScope: "all" }, 200, env);
  }
  if (error) {
    await logError(env, 'bigquery-versions', error, '');
    return json({ source: "error", versions: [], period, environmentScope: "all" }, 200, env);
  }

  const versions = rows.map((r) => ({
    version:       r.app_version ?? "unknown",
    totalCrashes:  parseInt(r.total_crashes ?? "0", 10),
    affectedUsers: parseInt(r.affected_users ?? "0", 10),
  }));

  return json({ source: "bigquery", versions, period, environmentScope: "all" }, 200, env);
}

// NOTA: app_version/device_model não foram adicionados ao SELECT abaixo via
// ANY_VALUE. ANY_VALUE() em si é seguro (aggregate function, não muda o
// GROUP BY issue_id/issue_title) — mas o nome exato das colunas não pôde ser
// confirmado sem acesso às credenciais/dataset real do BigQuery. Se os nomes
// estiverem errados, a query falha com "Unrecognized name" (BigQuery 400),
// que NÃO cai no branch table_not_found (só reconhece "Not found"/"notFound"),
// então rows.length===0 faz o handler responder "no_data_yet" silenciosamente
// — sem crash, mas mascarando um bug de schema como "sem dados ainda". Dado
// esse risco real e sem forma de validar aqui, a coluna fica de fora até
// alguém confirmar o schema de `firebase_crashlytics.android_crashes_*`
// direto no BigQuery. O frontend (FirebaseCrashIssue) já trata appVersion/
// deviceModel como opcionais e mostra "-" quando ausentes.
async function handleFirebaseCrashIssues(_req: Request, env: Env): Promise<Response> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials", issues: [] }, 200, env);
  }

  const { rows, error } = await queryBigQuery<{
    issue_id: string;
    issue_title: string;
    total_crashes: string;
    affected_users: string;
    last_seen: string;
  }>(env, `
    SELECT
      issue_id,
      issue_title,
      COUNT(*)                              AS total_crashes,
      COUNT(DISTINCT installation_uuid)     AS affected_users,
      MAX(event_timestamp)                  AS last_seen
    FROM \`${env.FIREBASE_PROJECT_ID}.firebase_crashlytics.android_crashes_*\`
    WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY))
    GROUP BY issue_id, issue_title
    ORDER BY total_crashes DESC
    LIMIT 20
  `);

  if (error === "table_not_found" || !rows.length) {
    return json({ source: "no_data_yet", issues: [] }, 200, env);
  }
  if (error) {
    await logError(env, 'bigquery-crash-issues', error, '');
    return json({ source: "error", issues: [] }, 200, env);
  }

  const issues = rows.map((r) => ({
    id:            r.issue_id ?? "",
    title:         r.issue_title ?? "Unknown crash",
    totalCrashes:  parseInt(r.total_crashes ?? "0", 10),
    affectedUsers: parseInt(r.affected_users ?? "0", 10),
    lastSeen:      r.last_seen ? parseInt(r.last_seen, 10) / 1000 : 0,
  }));

  return json({ source: "bigquery", issues }, 200, env);
}

async function handleFirebaseSync(_req: Request, env: Env): Promise<Response> {
  // Decisao 2026-07-11 (GH#877/#878): dataset `analytics_${FIREBASE_GA4_PROPERTY_ID}`
  // nao existe — export GA4->BigQuery nunca foi criado (Sandbox sem billing, decisao do
  // Luiz de nao assumir custo novo). Sem esta guarda, todo sync (cron 06:00 UTC e botao
  // manual) bate em bq_error_403 garantido. Religar via FIREBASE_SYNC_ENABLED=true em
  // wrangler.toml quando o export existir de verdade.
  if (env.FIREBASE_SYNC_ENABLED !== 'true') {
    console.log('[firebase-sync] pulado: FIREBASE_SYNC_ENABLED=false (export GA4->BigQuery ausente)');
    return json({
      ok: false,
      source: 'disabled',
      message: 'Firebase Analytics não configurado (export BigQuery ausente).',
    }, 200, env);
  }

  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return json({ source: "no_credentials", ok: false }, 200, env);
  }

  const { rows, error } = await queryBigQuery<{ sessions: string }>(env, `
    SELECT COUNT(*) AS sessions
    FROM \`${env.FIREBASE_PROJECT_ID}.analytics_${env.FIREBASE_GA4_PROPERTY_ID}.events_*\`
    WHERE _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY))
      AND event_name = 'session_start'
  `);

  if (error) {
    if (error === "table_not_found") {
      // Esperado antes do primeiro export diário do GA4 -> BigQuery: não é falha.
      const syncedAt = new Date().toISOString();
      await writeFirebaseSyncState(env, { syncedAt, eventsImported: 0, crashesImported: 0 });
      return json({ ok: false, source: "no_data_yet", sessionsYesterday: 0, syncedAt }, 200, env);
    }
    // GH#877 — bug anterior: `!rows.length` cai aqui pra QUALQUER erro (queryBigQuery
    // sempre devolve rows:[] em caso de falha), então nenhuma falha real (auth_failed,
    // bq_error_*) nunca chegava a virar source:"error" — era sempre lida como
    // "no_data_yet" e o sync ainda gravava estado de "sucesso com 0 eventos",
    // escondendo a falha real e corrompendo o timestamp da última sincronização
    // válida. Query falhou de verdade: não persiste estado de sync, pois nada foi
    // de fato sincronizado.
    return json({ ok: false, source: "error", message: error }, 200, env);
  }
  if (!rows.length) {
    const syncedAt = new Date().toISOString();
    await writeFirebaseSyncState(env, { syncedAt, eventsImported: 0, crashesImported: 0 });
    return json({ ok: false, source: "no_data_yet", sessionsYesterday: 0, syncedAt }, 200, env);
  }

  const sessions = parseInt(rows[0]?.sessions ?? "0", 10);
  const syncedAt = new Date().toISOString();
  // crashesImported não é coletado nesta query (só conta session_start via BigQuery);
  // não há fonte real para esse número aqui, então preserva o valor anterior em vez de inventar.
  const previous = await readFirebaseSyncState(env);
  await writeFirebaseSyncState(env, {
    syncedAt,
    eventsImported: sessions,
    crashesImported: previous?.crashesImported ?? 0,
  });
  return json({ ok: true, source: "bigquery", sessionsYesterday: sessions, syncedAt }, 200, env);
}

async function handleSettings(request: Request, env: Env): Promise<Response> {
  if (request.method === "GET") {
    const row = await env.DB.prepare(
      "SELECT value FROM admin_settings WHERE key = 'admin'"
    ).first<{ value: string }>();

    const settings = row?.value ? (JSON.parse(row.value) as unknown) : {};
    return json({ source: "d1", settings }, 200, env);
  }

  if (request.method === "POST") {
    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return err("body JSON inválido", 400, env);
    }

    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return err("body deve ser um objeto JSON", 400, env);
    }

    // Validação dos limiares consumidos em GET /admin/metrics/alerts (GH#426).
    const b = body as Record<string, unknown>;
    if ("aiDailyBudgetUsd" in b) {
      const v = b.aiDailyBudgetUsd;
      if (typeof v !== "number" || !Number.isFinite(v) || v < 0) {
        return err("aiDailyBudgetUsd deve ser um número >= 0", 400, env);
      }
    }
    if ("errorSpikeThreshold" in b) {
      const v = b.errorSpikeThreshold;
      if (typeof v !== "number" || !Number.isInteger(v) || v < 1) {
        return err("errorSpikeThreshold deve ser um inteiro >= 1", 400, env);
      }
    }
    if ("criticalScoreThreshold" in b) {
      const v = b.criticalScoreThreshold;
      if (typeof v !== "number" || !Number.isInteger(v) || v < 0 || v > 100) {
        return err("criticalScoreThreshold deve ser um inteiro entre 0 e 100", 400, env);
      }
    }

    await env.DB.prepare(
      "INSERT OR REPLACE INTO admin_settings (key, value, updated_at) VALUES ('admin', ?, ?)"
    ).bind(JSON.stringify(body), nowSec()).run();

    return json({ ok: true, settings: body }, 200, env);
  }

  return err("método não suportado", 405, env);
}

// --- SIG-129 / SIG-135 Fase A: pipeline de erros via D1 ---

// djb2: hash determinístico simples, sem dependência externa, adequado para ids de deduplicação.
function djb2(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) + h) ^ s.charCodeAt(i);
    h = h >>> 0; // mantém uint32
  }
  return h.toString(16).padStart(8, '0');
}

// GH#422: categoriza a origem do erro para diferenciar app / backend / IA /
// integração no painel (antes só existia o campo `source` livre, sem semântica
// de camada). Mapeamento fixo por `source` conhecido do próprio Worker —
// `source` de erros vindos do app real (feature_crash) usa categoria 'app'
// diretamente em handleErrors, não passa por aqui.
const ERROR_CATEGORY_BY_SOURCE: Record<string, 'app' | 'backend' | 'ia' | 'integration'> = {
  'firebase':               'integration',
  'bigquery-crashlytics':   'integration',
  'bigquery-versions':      'integration',
  'bigquery-crash-issues':  'integration',
  'ai-usage':               'ia',
  'ai-quota':               'ia',
  'cloudflare-usage':       'integration',
  'remote-config-admin':    'integration', // #1478 — GET/validate/publish/rollback do backend admin
};

function errorCategoryForSource(source: string): 'app' | 'backend' | 'ia' | 'integration' {
  return ERROR_CATEGORY_BY_SOURCE[source] ?? 'backend';
}

// Fire-and-forget: nunca propaga exceção. Deduplica por (source + message) via hash djb2.
export async function logError(env: Env, source: string, message: string, stack = ''): Promise<void> {
  try {
    const id = djb2(`${source}:${message}`);
    const now = Date.now();
    const category = errorCategoryForSource(source);
    await env.DB.prepare(`
      INSERT INTO system_errors (id, source, category, message, stack_trace, count, first_seen, last_seen)
      VALUES (?, ?, ?, ?, ?, 1, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        count       = count + 1,
        last_seen   = excluded.last_seen,
        stack_trace = excluded.stack_trace
    `).bind(id, source, category, message, stack, now, now).run();
  } catch { /* fire-and-forget — nunca propaga */ }
}

// Envolve um handler de métricas para capturar exceções sem alterar o comportamento.
// O handler original mantém seu throw/fallback; o log é adicional.
function withErrorLogging(source: string, handler: Handler): Handler {
  return async (req: Request, env: Env): Promise<Response> => {
    try {
      return await handler(req, env);
    } catch (e) {
      await logError(env, source, String(e), e instanceof Error ? (e.stack ?? '') : '');
      throw e;
    }
  };
}

// GH#422: por padrão só devolve erros ativos (resolved=0) — é o que faz o
// erro "deixar de aparecer como ativo" quando tratado (critério de aceite).
// ?resolved=all devolve tudo (histórico); ?resolved=true devolve só resolvidos.
async function handleErrors(request: Request, env: Env): Promise<Response> {
  const url        = new URL(request.url);
  const period     = url.searchParams.get("period") ?? "30d";
  const resolvedQ  = url.searchParams.get("resolved"); // null | "all" | "true" | "false"
  const platformFilter = getAnalyticsPlatformFilter(url);
  if (!platformFilter) return err('platform deve ser android ou web', 400, env);
  // Fase A: o parâmetro ?environment= enviado pelo frontend é IGNORADO aqui.
  // A tabela system_errors não possui coluna environment — os erros são do worker,
  // não do app. Filtro por environment entra na Fase B junto com SIG-143.
  // last_seen é em milissegundos (Date.now()), mas periodToSeconds retorna segundos.
  // Multiplicamos por 1000 para ficar na mesma escala.
  const sinceMs = (Date.now()) - periodToSeconds(period) * 1000;

  const resolvedClause =
    resolvedQ === "all"   ? ""                 :
    resolvedQ === "true"  ? " AND resolved = 1" :
    " AND resolved = 0"; // default: só erros ativos

  const rows = await env.DB.prepare(
    `SELECT id, source, category, message, stack_trace, count, first_seen, last_seen,
            resolved, resolved_by, resolved_at, resolution_note
     FROM system_errors
     WHERE last_seen >= ?${resolvedClause}
     ORDER BY count DESC, last_seen DESC
     LIMIT 100`
  ).bind(sinceMs).all();

  const workerErrors = (rows.results ?? []).map((r: any) => ({
    id:               r.id,
    source:           r.source,
    category:         r.category ?? 'backend',
    message:          r.message,
    stackTrace:       r.stack_trace ?? '',
    count:            r.count       ?? 1,
    timestamp:        new Date(r.last_seen).toISOString(),
    // O backend não rastreia usuários únicos por erro — sem PII no D1.
    // Derivar por device_id (já presente em diagnostic_sessions) é Fase B.
    affectedUserCount: 0,
    resolved:         r.resolved === 1,
    resolvedBy:       r.resolved_by ?? '',
    resolvedAt:       r.resolved_at ? new Date(r.resolved_at).toISOString() : null,
    resolutionNote:   r.resolution_note ?? '',
  }));

  // GH#422 gap 5: integra fonte real de erro do app — feature_crash já é
  // aceito por /ingest/analytics (whitelist), mas nunca era lido aqui.
  // Resolução desses eventos usa a mesma tabela system_errors (chave =
  // "app:<id do evento>"), criada sob demanda em handleResolveSystemError.
  const sinceSec = Math.floor(sinceMs / 1000);
  const crashRows = await env.DB.prepare(
    `SELECT id, event_name, error_type, session_id, created_at
     FROM analytics_events
     WHERE event_name = 'feature_crash' AND created_at >= ? AND platform = ?${ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE}
     ORDER BY created_at DESC
     LIMIT 100`
  ).bind(sinceSec, platformFilter).all();

  const appErrorIds = (crashRows.results ?? []).map((r: any) => `app:${r.id}`);
  const resolutionByAppId = new Map<string, { resolved: boolean; resolvedBy: string; resolvedAt: number | null; resolutionNote: string }>();
  if (appErrorIds.length > 0) {
    const placeholders = appErrorIds.map(() => '?').join(',');
    const resRows = await env.DB.prepare(
      `SELECT id, resolved, resolved_by, resolved_at, resolution_note
       FROM system_errors WHERE id IN (${placeholders})`
    ).bind(...appErrorIds).all();
    for (const r of (resRows.results ?? []) as any[]) {
      resolutionByAppId.set(r.id, {
        resolved:       r.resolved === 1,
        resolvedBy:     r.resolved_by ?? '',
        resolvedAt:     r.resolved_at || null,
        resolutionNote: r.resolution_note ?? '',
      });
    }
  }

  const appErrors = (crashRows.results ?? [])
    .map((r: any) => {
      const id  = `app:${r.id}`;
      const res = resolutionByAppId.get(id);
      return {
        id,
        source:            'android_app',
        category:          'app' as const,
        message:           r.error_type || 'Crash reportado pelo app (sem error_type)',
        stackTrace:        '',
        count:             1,
        timestamp:         new Date(r.created_at * 1000).toISOString(),
        affectedUserCount: 0,
        resolved:          res?.resolved ?? false,
        resolvedBy:        res?.resolvedBy ?? '',
        resolvedAt:        res?.resolvedAt ? new Date(res.resolvedAt).toISOString() : null,
        resolutionNote:    res?.resolutionNote ?? '',
      };
    })
    .filter((e: { resolved: boolean }) => {
      if (resolvedQ === "all") return true;
      if (resolvedQ === "true") return e.resolved;
      return !e.resolved; // default e "false": só ativos
    });

  const errors = [...workerErrors, ...appErrors]
    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
    .slice(0, 100);

  return json({ source: "d1", period, platform: platformFilter, errors }, 200, env);
}

// GH#782 — série temporal diária de erros para o gráfico "Taxa de erro · N
// dias" (Problemas & Incidentes). Fonte é exclusivamente
// analytics_events(feature_crash) — a mesma fonte que a página já declara
// (SectionIntro: "FONTE · FIREBASE CRASHLYTICS"). system_errors (erros do
// próprio worker) fica de fora: a tabela só guarda first_seen/last_seen
// agregados por erro único (dedup via djb2 em logError), sem uma linha por
// ocorrência — não dá pra reconstruir contagem diária real sem migration
// nova de log bruto (fora do escopo desta issue).
async function handleErrorsTimeline(request: Request, env: Env): Promise<Response> {
  const url   = new URL(request.url);
  const days  = Math.min(Math.max(parseInt(url.searchParams.get("days") ?? "14"), 1), 90);
  const since = nowSec() - days * 86400;
  const platformFilter = getAnalyticsPlatformFilter(url);
  if (!platformFilter) return err('platform deve ser android ou web', 400, env);

  const rows = await env.DB.prepare(
    `SELECT
       strftime('%Y-%m-%d', datetime(created_at, 'unixepoch')) AS date,
       COUNT(*) AS count
     FROM analytics_events
     WHERE event_name = 'feature_crash' AND created_at >= ? AND platform = ?${ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE}
     GROUP BY date
     ORDER BY date ASC`
  ).bind(since, platformFilter).all();

  const series = (rows.results ?? []).map((r: any) => ({
    date:       r.date,
    errorCount: r.count ?? 0,
  }));

  return json({ source: "d1", days, platform: platformFilter, series }, 200, env);
}

// POST /admin/errors/:id/resolve — GH#422. Marca erro (do worker ou do app)
// como resolvido, gravando responsável (da sessão autenticada), data (server)
// e observação (body). UPSERT: erros "app:<id>" ainda não têm linha em
// system_errors (só existem via analytics_events) — cria a linha sob demanda.
async function handleResolveSystemError(request: Request, env: Env, session: { userId: string; role: string }): Promise<Response> {
  const url   = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/errors\/([^/]+)\/resolve$/);
  if (!match) return err('id inválido', 400, env);
  const id = decodeURIComponent(match[1]);

  let body: any = {};
  try { body = await request.json(); } catch { /* body opcional */ }
  const note = typeof body.note === 'string' ? body.note.slice(0, 2000) : '';

  const userRow = await env.DB.prepare(
    'SELECT email FROM admin_users WHERE id = ?'
  ).bind(session.userId).first<{ email: string }>();
  const resolvedBy = userRow?.email ?? 'admin';
  const now = Date.now();

  // A categoria abaixo só é gravada se a linha ainda não existir (ON CONFLICT
  // não a atualiza) — para erros do worker que já existem em system_errors,
  // a categoria original (gravada por logError) é preservada.
  const isAppError = id.startsWith('app:');

  await env.DB.prepare(`
    INSERT INTO system_errors (id, source, category, message, stack_trace, count, first_seen, last_seen, resolved, resolved_by, resolved_at, resolution_note)
    VALUES (?, ?, ?, '', '', 1, ?, ?, 1, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      resolved        = 1,
      resolved_by     = excluded.resolved_by,
      resolved_at     = excluded.resolved_at,
      resolution_note = excluded.resolution_note
  `).bind(id, isAppError ? 'android_app' : 'unknown', isAppError ? 'app' : 'backend', now, now, resolvedBy, now, note).run();

  return json({ ok: true, id, resolvedBy, resolvedAt: new Date(now).toISOString() }, 200, env);
}

// --- handlers /ingest ---

// GH#786 — as 27 UFs brasileiras válidas. `request.cf.regionCode` (geolocalização
// de borda da própria Cloudflare, calculada a partir do IP da requisição) só é
// aceito quando bate com uma destas — qualquer outro valor (país fora do Brasil,
// colo sem geo confiável, undefined em dev local) vira '' em vez de fabricar UF.
const UF_WHITELIST = new Set([
  'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB',
  'PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO',
]);

// GH#786 — deriva UF aproximada da requisição via geolocalização de borda da
// Cloudflare (`request.cf`), sem NUNCA persistir o IP em si. `cf` não tem
// tipo oficial no projeto (mesmo gap pré-existente de @cloudflare/workers-types
// documentado no topo do arquivo) — acesso via `any` só para este campo.
function deriveUfFromRequest(request: Request): string {
  const regionCode = (request as any).cf?.regionCode;
  if (typeof regionCode !== 'string') return '';
  const uf = regionCode.toUpperCase();
  return UF_WHITELIST.has(uf) ? uf : '';
}

async function handleIngestDiagnostic(request: Request, env: Env): Promise<Response> {
  let p: any;
  try { p = await request.json(); } catch { return err("invalid JSON", 400, env); }
  if (!p.id) return err("id obrigatorio", 400, env);

  // SIG-138: campos de dispositivo e laudo IA opcionais (default '' no schema).
  const deviceModel      = p.device_model      ?? '';
  const osVersion        = p.os_version        ?? '';
  const appVersion       = p.app_version       ?? '';
  const aiSummaryReport  = p.ai_summary_report ?? '';

  // SIG-143: campos de contexto de ambiente e dispositivo.
  const environment = p.environment  ?? 'production';
  const distChannel = p.dist_channel ?? '';
  const buildType   = p.build_type   ?? 'release';
  const versionCode = p.version_code ?? 0;
  const deviceId    = p.device_id    ?? '';

  // GH#442: origem do dado. Default 'android' porque o app Android ainda não
  // envia este campo (todo dado histórico é dele) — o extinto PWA mandava
  // 'web' explicitamente. Mantido para não quebrar leitura de dados históricos.
  const platform = p.platform === 'web' ? 'web' : 'android';

  // Gap 3 (SIG-164): rssi, banda_wifi, padrao_wifi enviados ao AI Worker mas nunca persistidos.
  const rssi       = p.rssi        ?? p.rssiDbm ?? null;
  const bandaWifi  = p.banda_wifi  ?? p.bandaWifi  ?? null;
  const padraoWifi = p.padrao_wifi ?? p.padraoWifi ?? null;

  // GH#786 — UF aproximada, derivada no worker (não enviada pelo app).
  const uf = deriveUfFromRequest(request);

  try {
    await env.DB.prepare(
      `INSERT OR REPLACE INTO diagnostic_sessions
         (id, created_at, network_type, status, score,
          download_mbps, upload_mbps, latency_ms, jitter_ms, packet_loss,
          issues, resolved, operator,
          device_model, os_version, app_version, ai_summary_report,
          environment, dist_channel, build_type, version_code, device_id,
          rssi, banda_wifi, padrao_wifi, platform, uf)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      p.id, p.created_at ?? nowSec(),
      p.network_type ?? "unknown", p.status ?? "unknown", p.score ?? null,
      p.download_mbps ?? null, p.upload_mbps ?? null,
      p.latency_ms ?? null, p.jitter_ms ?? null, p.packet_loss ?? null,
      JSON.stringify(p.issues ?? []),
      p.operator ?? null,
      deviceModel, osVersion, appVersion, aiSummaryReport,
      environment, distChannel, buildType, versionCode, deviceId,
      rssi, bandaWifi, padraoWifi, platform, uf,
    ).run();
  } catch (e) {
    await logError(env, 'ingest', String(e), e instanceof Error ? e.stack ?? '' : '');
    throw e;
  }

  return json({ ok: true, id: p.id }, 201, env);
}

// Preço por token (USD), por modelo. Ver docs/archive/decisoes/ADR-005.
// Modelos do free tier (Gemini, Qwen/Workers AI) custam 0. Ao adotar um modelo
// pago, adicione sua tarifa aqui (ex.: "gemini-2.5-pro": 0.0000003).
const AI_MODEL_RATE_USD: Record<string, number> = {};

function costForModel(model: string, totalTokens: number): number {
  const key = (model ?? "").toLowerCase();
  // Free tier: Gemini (Google AI Studio) e Qwen (Cloudflare Workers AI) não têm custo por token.
  if (key.includes("gemini") || key.includes("qwen") || key.startsWith("@cf/")) return 0;
  for (const [m, rate] of Object.entries(AI_MODEL_RATE_USD)) {
    if (key.includes(m)) return totalTokens * rate;
  }
  // Modelo sem tarifa cadastrada → 0 (arquitetura atual é 100% free tier).
  return 0;
}

async function handleIngestAiUsage(request: Request, env: Env): Promise<Response> {
  let p: any;
  try { p = await request.json(); } catch { return err("invalid JSON", 400, env); }
  if (!p.id || !p.model) return err("id e model obrigatorios", 400, env);

  const prompt     = p.prompt_tokens     ?? 0;
  const completion = p.completion_tokens ?? 0;
  const total      = p.total_tokens      ?? (prompt + completion);

  // Gap 1 (SIG-164): cost_usd null em registros retroativos.
  // Se o Android envia cost_usd=null mas total_tokens > 0, aplica tarifa aproximada
  // do Qwen3 30B (0.30 USD / 1M tokens) para não perder rastreabilidade de custo.
  const cost = p.cost_usd != null
    ? p.cost_usd
    : total > 0
      ? (total / 1_000_000) * 0.30
      : costForModel(p.model, total);

  // SIG-143: campos de contexto de ambiente.
  const environment = p.environment  ?? 'production';
  const versionCode = p.version_code ?? 0;

  // Gap 2 (SIG-164): dist_channel, build_type, device_id antes descartados.
  const distChannel = p.dist_channel ?? '';
  const buildType   = p.build_type   ?? 'release';
  const deviceId    = p.device_id    ?? '';

  // GH#421: status/erro da execução. Default 'success' porque o app hoje só
  // envia ai_usage ao final de uma chamada concluída (sem retry/erro reportado
  // ainda) — quando o Android passar a enviar falhas, o valor real prevalece.
  const status       = p.status === 'error' ? 'error' : 'success';
  const errorMessage = p.error_message ?? p.error ?? '';

  // GH#442: mesmo critério de origem do /ingest/diagnostic.
  const platform = p.platform === 'web' ? 'web' : 'android';

  try {
    await env.DB.prepare(
      `INSERT OR REPLACE INTO ai_usage
         (id, session_id, created_at, model,
          prompt_tokens, completion_tokens, total_tokens, cost_usd,
          environment, version_code, dist_channel, build_type, device_id,
          status, error_message, platform)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      p.id, p.session_id ?? null, p.created_at ?? nowSec(), p.model,
      prompt, completion, total, cost,
      environment, versionCode, distChannel, buildType, deviceId,
      status, errorMessage, platform,
    ).run();
  } catch (e) {
    await logError(env, 'ai-usage', String(e), e instanceof Error ? e.stack ?? '' : '');
    throw e;
  }

  return json({ ok: true, id: p.id }, 201, env);
}

// --- SIG-134: analytics de produto ---

// GH#417: 'session_end' carrega duration_ms — é o único evento com essa métrica,
// necessária para calcular tempo médio de sessão em #418. Sem esse evento, a
// aba Produto & Uso não tem como reportar duração sem heurística inventada.
const VALID_ANALYTICS_EVENTS = new Set([
  'feature_used', 'screen_view', 'session_start', 'session_end', 'feature_crash', 'battery_snapshot',
]);

export async function handleIngestAnalytics(request: Request, env: Env): Promise<Response> {
  let body: any;
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }

  const events: any[] = Array.isArray(body.events) ? body.events : [];
  if (events.length === 0) return json({ ok: true, inserted: 0 }, 200, env);
  if (events.length > 500) return err('máximo 500 eventos por batch', 400, env);

  const now = nowSec();

  // GH#417: id é responsabilidade do cliente (UUID gerado no momento do evento).
  // Sem isso, um retry de rede reenviaria o mesmo batch e o INSERT OR IGNORE geraria
  // linhas duplicadas (o id anterior era sempre aleatório no worker, então nunca
  // colidia). Clientes antigos que não enviam `id` ainda funcionam (fallback abaixo),
  // mas não são protegidos contra duplicação em retry — motivo para o app adotar
  // id determinístico assim que a fila local (retry/backoff) for implementada.
  let normalized = 0;
  let rejected = 0;
  const acceptedIds: string[] = [];
  const stmts = events.flatMap((e) => {
    if (!e || !VALID_ANALYTICS_EVENTS.has(e.name)) {
      rejected += 1;
      return [];
    }
    const normalizedTimestamp = normalizeAnalyticsTimestamp(e.timestamp, now);
    if (normalizedTimestamp.timestamp == null) {
      rejected += 1;
      return [];
    }
    if (normalizedTimestamp.normalized) normalized += 1;
    const id = typeof e.id === 'string' && e.id.length > 0 ? e.id : crypto.randomUUID();
    acceptedIds.push(id);
    return [env.DB.prepare(
        `INSERT OR IGNORE INTO analytics_events
           (id, event_name, session_id, created_at, app_version, feature_id, screen_name, error_type,
            battery_level, battery_charging, environment, device_id, version_code, dist_channel, build_type, duration_ms, platform)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        id,
        e.name,
        e.session_id ?? '',
        normalizedTimestamp.timestamp,
        e.app_version ?? '',
        e.feature_id  ?? '',
        e.screen_name ?? '',
        e.error_type  ?? '',
        typeof e.battery_level    === 'number' ? e.battery_level    : null,
        typeof e.battery_charging === 'boolean' ? (e.battery_charging ? 1 : 0) : null,
        e.environment ?? 'production',
        e.device_id    ?? '',
        typeof e.version_code === 'number' ? e.version_code : 0,
        e.dist_channel ?? '',
        e.build_type   ?? 'release',
        typeof e.duration_ms === 'number' ? e.duration_ms : null,
        // GH#442: mesmo critério de origem dos demais endpoints de ingest.
        e.platform === 'web' ? 'web' : 'android',
      ),
    ];
  });

  const results = stmts.length > 0 ? await env.DB.batch(stmts) : [];
  const inserted = (results as Array<{ meta?: { changes?: number } }>)
    .reduce((total, result) => total + (result.meta?.changes ?? 0), 0);

  return json({ ok: true, inserted, normalized, rejected, acceptedIds }, 201, env);
}

const WAITLIST_PRODUCTS = new Set(['signallq', 'pro']);
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// GH#1155 — lista de espera dos modais "avisar quando lançar" do site
// institucional (EmailCaptureDialog). Reaproveita o rate limit por IP já
// usado em /admin/auth/login (auth_rate_limit) para conter spam do formulário
// — não é sobre credenciais aqui, é sobre não deixar o mesmo IP martelar o
// endpoint. INSERT OR IGNORE + UNIQUE(email, product) tornam o clique
// duplicado idempotente: sempre responde sucesso, nunca vaza se o e-mail já
// estava cadastrado.
async function handleIngestWaitlist(request: Request, env: Env): Promise<Response> {
  const ip = request.headers.get('CF-Connecting-IP') ?? 'unknown';
  if (await checkRateLimit(ip, env.DB)) {
    return err('Muitas tentativas. Tente novamente em 15 minutos.', 429, env);
  }
  await incrementRateLimit(ip, env.DB);

  let body: any;
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }

  const email      = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
  const product    = body.product;
  const sourcePage = typeof body.source_page === 'string' ? body.source_page : '';

  if (!EMAIL_REGEX.test(email)) return err('e-mail inválido', 400, env);
  if (!WAITLIST_PRODUCTS.has(product)) return err('product inválido', 400, env);

  await env.DB.prepare(
    `INSERT OR IGNORE INTO waitlist_signups (id, email, product, platform, source_page, created_at)
     VALUES (?, ?, ?, 'web', ?, ?)`
  ).bind(crypto.randomUUID(), email, product, sourcePage, nowSec()).run();

  return json({ ok: true }, 201, env);
}

// GH#784 — feature_id das 3 etapas do funil de speedtest via feature_used
// (iniciou/completou/compartilhou; "abriu" usa screen_view("speedtest"), já
// existente). Reaproveita o mesmo evento/schema de "wifi"/"historico"/etc —
// sem endpoint novo de ingest — mas precisam ficar de fora do ranking geral
// de "funcionalidade mais usada" (não são features, são estágios de funil).
const SPEEDTEST_FUNNEL_FEATURE_IDS = ['speedtest_iniciado', 'speedtest_completou', 'speedtest_compartilhou'];
const SPEEDTEST_FUNNEL_EXCLUSION_CLAUSE = ` AND feature_id NOT IN (${SPEEDTEST_FUNNEL_FEATURE_IDS.map(() => '?').join(',')})`;

export async function handleProductAnalytics(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get('period') ?? '7d';
  const since     = nowSec() - periodToSeconds(period);
  const coverageSince = nowSec() - periodToSeconds('7d');
  const envFilter = getEnvironmentFilter(url);
  const platformFilter = getAnalyticsPlatformFilter(url);
  if (!platformFilter) return err('platform deve ser android ou web', 400, env);

  const envClause = `${envFilter ? ' AND environment = ?' : ''}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];
  const analyticsClause = ` AND platform = ?${ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE}`;
  const analyticsBinds = [platformFilter];

  const [featureRows, screenRows, crashRows, totalRows] = await Promise.all([
    // Uso por feature: contagem total e sessões únicas
    env.DB.prepare(
      `SELECT feature_id, COUNT(*) AS usage_count, COUNT(DISTINCT session_id) AS unique_sessions
       FROM analytics_events
       WHERE event_name = 'feature_used' AND created_at >= ?${envClause}${analyticsClause}
         AND feature_id != ''${SPEEDTEST_FUNNEL_EXCLUSION_CLAUSE}
       GROUP BY feature_id ORDER BY usage_count DESC`
    ).bind(since, ...envBinds, ...analyticsBinds, ...SPEEDTEST_FUNNEL_FEATURE_IDS).all(),

    // Navegação por tela
    env.DB.prepare(
      `SELECT screen_name, COUNT(*) AS views, COUNT(DISTINCT session_id) AS unique_sessions
       FROM analytics_events
       WHERE event_name = 'screen_view' AND created_at >= ?${envClause}${analyticsClause}
         AND screen_name != ''
       GROUP BY screen_name ORDER BY views DESC`
    ).bind(since, ...envBinds, ...analyticsBinds).all(),

    // Crashes por feature
    env.DB.prepare(
      `SELECT feature_id,
              COUNT(*) AS crashes,
              GROUP_CONCAT(DISTINCT app_version) AS affected_versions
       FROM analytics_events
       WHERE event_name = 'feature_crash' AND created_at >= ?${envClause}${analyticsClause}
         AND feature_id != ''
       GROUP BY feature_id ORDER BY crashes DESC`
    ).bind(since, ...envBinds, ...analyticsBinds).all(),

    // Total de feature_used para calcular crash rate
    env.DB.prepare(
      `SELECT feature_id, COUNT(*) AS total
       FROM analytics_events
       WHERE event_name = 'feature_used' AND created_at >= ?${envClause}${analyticsClause}
         AND feature_id != ''${SPEEDTEST_FUNNEL_EXCLUSION_CLAUSE}
       GROUP BY feature_id`
    ).bind(since, ...envBinds, ...analyticsBinds, ...SPEEDTEST_FUNNEL_FEATURE_IDS).all(),
  ]);

  // GH#418: tempo médio de sessão real (duration_ms só existe em session_end, SIG-295/GH#417).
  // GH#XXX: session_count NÃO pode vir dessa mesma query — nem todo fluxo
  // dispara session_end com duration_ms preenchido (app fica em foreground o
  // teste todo, sessão nunca fecha graciosamente), e isso zera "Sessões no
  // período" mesmo com atividade real (feature_usage/screen_navigation > 0 na
  // mesma janela). session_id é NOT NULL/indexado para TODO evento (migration
  // 006_sig134.sql) — mesmo critério já usado em feature_usage/screen_navigation
  // (COUNT DISTINCT session_id). avg_session_duration_ms continua exigindo
  // session_end de verdade — não inventar duração a partir de outro evento.
  const [sessionDurationRow, sessionCountRow] = await Promise.all([
    env.DB.prepare(
      `SELECT AVG(duration_ms) AS avg_duration_ms
       FROM analytics_events
       WHERE event_name = 'session_end' AND duration_ms IS NOT NULL AND created_at >= ?${envClause}${analyticsClause}`
    ).bind(since, ...envBinds, ...analyticsBinds).first<{ avg_duration_ms: number | null }>(),

    env.DB.prepare(
      `SELECT COUNT(DISTINCT session_id) AS session_count
       FROM analytics_events
       WHERE created_at >= ?${envClause}${analyticsClause}`
    ).bind(since, ...envBinds, ...analyticsBinds).first<{ session_count: number }>(),
  ]);

  const [sessionCoverageRow, excludedHistoricalRow] = await Promise.all([
    env.DB.prepare(
      `SELECT
         COUNT(DISTINCT CASE WHEN event_name = 'session_start' AND session_id != '' THEN session_id END) AS session_start_count,
         COUNT(DISTINCT CASE WHEN event_name = 'session_end' AND session_id != '' THEN session_id END) AS session_end_count
       FROM analytics_events
       WHERE created_at >= ?${envClause}${analyticsClause}`
    ).bind(coverageSince, ...envBinds, ...analyticsBinds).first<{ session_start_count: number | null; session_end_count: number | null }>(),
    env.DB.prepare(
      `SELECT COUNT(*) AS count
       FROM analytics_events
       WHERE platform = ? AND (created_at >= 100000000000 OR (app_version = 'site' AND created_at < ?))`
    ).bind(platformFilter, ANALYTICS_TIMESTAMP_CONTRACT_CUTOFF_SEC).first<{ count: number }>(),
  ]);

  // GH#418: retenção D1/D7/D30 por cohort de device_id (device_id só existe desde GH#417/migration 008).
  // Cohort de retenção usa histórico completo (não limitado ao `period` do request) porque D1/D7/D30
  // exigem que o dispositivo já tenha tempo suficiente decorrido desde o primeiro evento visto.
  const retentionRow = await env.DB.prepare(
    `WITH first_seen AS (
       SELECT device_id, MIN(created_at) AS install_ts
       FROM analytics_events
       WHERE device_id != ''${envFilter ? ' AND environment = ?' : ''}${analyticsClause}
       GROUP BY device_id
     ),
     elapsed AS (
       SELECT device_id, install_ts,
         CAST((strftime('%s','now') - install_ts) / 86400 AS INTEGER) AS days_elapsed
       FROM first_seen
     ),
     last_activity AS (
       SELECT device_id, MAX(created_at) AS last_seen
       FROM analytics_events
       WHERE device_id != ''${envFilter ? ' AND environment = ?' : ''}${analyticsClause}
       GROUP BY device_id
     ),
     returned_d1 AS (
       SELECT DISTINCT a.device_id FROM analytics_events a
       JOIN first_seen f ON a.device_id = f.device_id
       WHERE a.created_at >= f.install_ts + 86400 AND a.created_at < f.install_ts + 172800${analyticsClause.replaceAll('created_at', 'a.created_at').replace("app_version", "a.app_version").replace("platform", "a.platform")}
     ),
     returned_d7 AS (
       SELECT DISTINCT a.device_id FROM analytics_events a
       JOIN first_seen f ON a.device_id = f.device_id
       WHERE a.created_at >= f.install_ts + 86400 AND a.created_at < f.install_ts + 691200${analyticsClause.replaceAll('created_at', 'a.created_at').replace("app_version", "a.app_version").replace("platform", "a.platform")}
     ),
     returned_d30 AS (
       SELECT DISTINCT a.device_id FROM analytics_events a
       JOIN first_seen f ON a.device_id = f.device_id
       WHERE a.created_at >= f.install_ts + 86400 AND a.created_at < f.install_ts + 2678400${analyticsClause.replaceAll('created_at', 'a.created_at').replace("app_version", "a.app_version").replace("platform", "a.platform")}
     )
     SELECT
       (SELECT COUNT(*) FROM elapsed WHERE days_elapsed >= 1)  AS cohort_d1,
       (SELECT COUNT(*) FROM returned_d1)                      AS returned_d1,
       (SELECT COUNT(*) FROM elapsed WHERE days_elapsed >= 7)  AS cohort_d7,
       (SELECT COUNT(*) FROM returned_d7)                      AS returned_d7,
       (SELECT COUNT(*) FROM elapsed WHERE days_elapsed >= 30) AS cohort_d30,
       (SELECT COUNT(*) FROM returned_d30)                     AS returned_d30,
       (SELECT COUNT(*) FROM first_seen)                       AS total_devices,
       (SELECT AVG(days_elapsed) FROM elapsed)                 AS avg_active_span_days,
       (SELECT COUNT(*) FROM last_activity WHERE last_seen < strftime('%s','now') - 1209600) AS inactive_14d,
       (SELECT COUNT(*) FROM last_activity)                    AS total_with_activity
    `
  ).bind(...(envFilter ? [envFilter] : []), ...analyticsBinds, ...(envFilter ? [envFilter] : []), ...analyticsBinds, ...analyticsBinds, ...analyticsBinds, ...analyticsBinds).first<{
    cohort_d1: number; returned_d1: number;
    cohort_d7: number; returned_d7: number;
    cohort_d30: number; returned_d30: number;
    total_devices: number; avg_active_span_days: number | null;
    inactive_14d: number; total_with_activity: number;
  }>();

  const usageByFeature = new Map<string, number>(
    (totalRows.results ?? []).map((r: any) => [r.feature_id, r.total])
  );

  const feature_usage = (featureRows.results ?? []).map((r: any) => ({
    feature:        r.feature_id,
    label:          r.feature_id,
    usageCount:     r.usage_count   ?? 0,
    uniqueUsers:    r.unique_sessions ?? 0,
    completionRate: 0,
    failureRate:    0,
    avgDurationMs:  0,
    trendPercent:   0,
  }));

  const screen_navigation = (screenRows.results ?? []).map((r: any) => ({
    screen:              r.screen_name,
    label:               r.screen_name,
    views:               r.views           ?? 0,
    uniqueUsers:         r.unique_sessions  ?? 0,
    avgTimeOnScreenSec:  0,
    exitRate:            0,
    nextMostCommonScreen: null,
  }));

  const feature_crashes = (crashRows.results ?? []).map((r: any) => {
    const total   = usageByFeature.get(r.feature_id) ?? 0;
    const crashes = r.crashes ?? 0;
    const rate    = total > 0 ? crashes / total : 0;
    const versions = r.affected_versions
      ? String(r.affected_versions).split(',').filter(Boolean)
      : [];
    return {
      feature:         r.feature_id,
      label:           r.feature_id,
      crashes,
      nonFatalErrors:  0,
      anrs:            0,
      crashRate:       Math.round(rate * 1000) / 10,
      affectedVersions: versions,
      severity:        crashes === 0 ? 'ok' : rate > 0.05 ? 'critical' : 'attention',
    };
  });

  const avg_session_duration_ms = sessionDurationRow?.avg_duration_ms != null
    ? Math.round(sessionDurationRow.avg_duration_ms)
    : null;
  const session_count = sessionCountRow?.session_count ?? 0;
  const sessionStartCount = sessionCoverageRow?.session_start_count ?? 0;
  const sessionEndCount = sessionCoverageRow?.session_end_count ?? 0;
  const sessionEndRate = sessionStartCount > 0 ? sessionEndCount / sessionStartCount : null;
  const isSessionMetricReliable = sessionEndRate != null && sessionEndRate >= 0.8;

  const totalDevices = retentionRow?.total_devices ?? 0;
  const retention = totalDevices === 0 ? [] : [{
    cohort:           `Cohort geral (${period})`,
    cohortSize:       totalDevices,
    day1:             retentionRow!.cohort_d1  > 0 ? Math.round((retentionRow!.returned_d1  / retentionRow!.cohort_d1)  * 1000) / 10 : null,
    day7:             retentionRow!.cohort_d7  > 0 ? Math.round((retentionRow!.returned_d7  / retentionRow!.cohort_d7)  * 1000) / 10 : null,
    day30:            retentionRow!.cohort_d30 > 0 ? Math.round((retentionRow!.returned_d30 / retentionRow!.cohort_d30) * 1000) / 10 : null,
    avgInstalledDays: retentionRow!.avg_active_span_days != null ? Math.round(retentionRow!.avg_active_span_days * 10) / 10 : null,
    // Proxy de churn: % de dispositivos sem nenhum evento nos últimos 14 dias. NÃO é confirmação
    // de desinstalação real (isso exigiria Play Console/FCM, não integrado — ver data-architecture.md).
    uninstallRate:    retentionRow!.total_with_activity > 0 ? Math.round((retentionRow!.inactive_14d / retentionRow!.total_with_activity) * 1000) / 10 : null,
  }];

  // Retenção e duração dependem do encerramento confiável das sessões. O Admin
  // também aplica esse estado visualmente, mas o contrato não pode vazar valores
  // que um consumidor futuro possa exibir por engano abaixo do limiar aprovado.
  const reliableRetention = isSessionMetricReliable ? retention : [];
  const reliableAverageSessionDuration = isSessionMetricReliable ? avg_session_duration_ms : null;

  return json({
    source: 'd1',
    period,
    environment: envFilter ?? 'all',
    platform: platformFilter,
    dataQuality: {
      sessionStartCount,
      sessionEndCount,
      sessionEndRate,
      threshold: 0.8,
      isSessionMetricReliable,
      excludedHistoricalTimestampEvents: excludedHistoricalRow?.count ?? 0,
    },
    no_data_yet: feature_usage.length === 0 && screen_navigation.length === 0,
    feature_usage,
    screen_navigation,
    feature_crashes,
    avg_session_duration_ms: reliableAverageSessionDuration,
    session_count,
    retention: reliableRetention,
  }, 200, env);
}

// GH#784 — funil do teste de velocidade (Uso do App): abriu -> iniciou ->
// completou -> compartilhou. "Abriu" usa screen_view("speedtest") (já
// instrumentado desde sempre — a aba Velocidade dispara screen_view ao
// entrar); os demais 3 estágios usam feature_used com feature_id dedicado
// (ver SPEEDTEST_FUNNEL_FEATURE_IDS), tudo via o mesmo pipeline de ingest já
// existente — sem evento novo no whitelist do worker.
async function handleSpeedtestFunnel(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get('period') ?? '7d';
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);
  const platformFilter = getAnalyticsPlatformFilter(url);
  if (!platformFilter) return err('platform deve ser android ou web', 400, env);

  const envClause = `${envFilter ? ' AND environment = ?' : ''}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];
  const analyticsClause = ` AND platform = ?${ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE}`;

  const [abriuRow, estagiosRows] = await Promise.all([
    env.DB.prepare(
      `SELECT COUNT(*) AS count FROM analytics_events
       WHERE event_name = 'screen_view' AND screen_name = 'speedtest' AND created_at >= ?${envClause}${analyticsClause}`
    ).bind(since, ...envBinds, platformFilter).first<{ count: number }>(),

    env.DB.prepare(
      `SELECT feature_id, COUNT(*) AS count FROM analytics_events
       WHERE event_name = 'feature_used' AND created_at >= ?${envClause}${analyticsClause}
         AND feature_id IN (${SPEEDTEST_FUNNEL_FEATURE_IDS.map(() => '?').join(',')})
       GROUP BY feature_id`
    ).bind(since, ...envBinds, platformFilter, ...SPEEDTEST_FUNNEL_FEATURE_IDS).all(),
  ]);

  const countByFeatureId = new Map<string, number>(
    (estagiosRows.results ?? []).map((r: any) => [r.feature_id, r.count ?? 0])
  );

  const stages = [
    { stage: 'abriu', count: abriuRow?.count ?? 0 },
    { stage: 'iniciou', count: countByFeatureId.get('speedtest_iniciado') ?? 0 },
    { stage: 'completou', count: countByFeatureId.get('speedtest_completou') ?? 0 },
    { stage: 'compartilhou', count: countByFeatureId.get('speedtest_compartilhou') ?? 0 },
  ];

  return json({
    source: 'd1',
    period,
    environment: envFilter ?? 'all',
    platform: platformFilter,
    no_data_yet: stages.every((s) => s.count === 0),
    stages,
  }, 200, env);
}

async function handleBatteryAnalytics(request: Request, env: Env): Promise<Response> {
  const url    = new URL(request.url);
  const period = url.searchParams.get('period') ?? '7d';
  const since  = nowSec() - periodToSeconds(period);
  const platformFilter = getAnalyticsPlatformFilter(url);
  if (!platformFilter) return err('platform deve ser android ou web', 400, env);

  const rows = await env.DB.prepare(
    `SELECT
       AVG(CAST(battery_level AS REAL)) AS avg_level,
       SUM(battery_charging) AS charging_count,
       COUNT(*) AS total
     FROM analytics_events
     WHERE event_name = 'battery_snapshot' AND created_at >= ?
       AND battery_level IS NOT NULL AND platform = ?${ANALYTICS_HISTORICAL_CONTAMINATION_CLAUSE}`
  ).bind(since, platformFilter).first<{ avg_level: number | null; charging_count: number; total: number }>();

  const total = rows?.total ?? 0;

  return json({
    source:     'd1',
    period,
    platform: platformFilter,
    no_data_yet: total === 0,
    summary: total === 0 ? null : {
      avg_battery_level:       rows?.avg_level != null ? Math.round(rows.avg_level) : null,
      charging_sessions_pct:   rows && total > 0 ? Math.round((rows.charging_count / total) * 100) : 0,
      total_snapshots:         total,
    },
    items: [],
  }, 200, env);
}

// #785 — breakdown de dispositivos mais ativos (modelo + versão Android + %
// de sessões). Fonte: diagnostic_sessions.device_model/os_version (já
// coletado pelo app via POST /ingest/diagnostic desde SIG-138) — mais direto
// que ir buscar no GA4/BigQuery, que não tem esse breakdown pronto sem uma
// dimensão custom equivalente. Mesma honestidade do resto do painel: sem
// sessão com device_model preenchido no período, no_data_yet: true.
async function handleDeviceBreakdown(request: Request, env: Env): Promise<Response> {
  const url       = new URL(request.url);
  const period    = url.searchParams.get("period") ?? "30d";
  const since     = nowSec() - periodToSeconds(period);
  const envFilter = getEnvironmentFilter(url);

  const envClause = `${envFilter ? " AND environment = ?" : ""}${productionTrackClause(envFilter)}`;
  const envBinds  = envFilter ? [envFilter]            : [];

  const rows = await env.DB.prepare(
    `SELECT device_model, os_version, COUNT(*) AS session_count
     FROM diagnostic_sessions
     WHERE created_at >= ? AND device_model != ''${envClause}
     GROUP BY device_model, os_version
     ORDER BY session_count DESC
     LIMIT 10`
  ).bind(since, ...envBinds).all<{ device_model: string; os_version: string; session_count: number }>();

  const results = rows.results ?? [];
  const totalRow = await env.DB.prepare(
    `SELECT COUNT(*) AS total FROM diagnostic_sessions WHERE created_at >= ? AND device_model != ''${envClause}`
  ).bind(since, ...envBinds).first<{ total: number }>();
  const total = totalRow?.total ?? 0;

  const items = results.map((r: { device_model: string; os_version: string; session_count: number }) => ({
    deviceModel:  r.device_model,
    osVersion:    r.os_version || "—",
    sessionCount: r.session_count,
    percentage:   total > 0 ? Math.round((r.session_count / total) * 10000) / 100 : 0,
  }));

  return json({
    source: "d1",
    period,
    environment: envFilter ?? "all",
    no_data_yet: total === 0,
    items,
  }, 200, env);
}

// --- SIG-13: feature flags ---

interface FeatureFlag {
  key: string;
  enabled: boolean;
  scope: 'public' | 'internal';
  description: string;
}

function getDefaultFlags(): FeatureFlag[] {
  return [
    { key: 'ai_diagnosis_enabled',  enabled: true,  scope: 'public',   description: 'Habilita diagnóstico por IA' },
    { key: 'speedtest_enabled',     enabled: true,  scope: 'public',   description: 'Habilita speedtest' },
    { key: 'fibra_module_enabled',  enabled: true,  scope: 'public',   description: 'Habilita módulo fibra' },
    { key: 'new_ui_diagnostics',    enabled: false, scope: 'internal', description: 'Nova UI de diagnósticos (internal)' },
  ];
}

// GET /feature-flags — público, sem auth. Retorna apenas flags com scope='public'.
// Crítico para o app Android verificar flags sem credenciais de admin.
async function handlePublicFeatureFlags(_request: Request, env: Env): Promise<Response> {
  const row = await env.DB.prepare(
    "SELECT value FROM admin_settings WHERE key = 'feature_flags'"
  ).first<{ value: string }>();
  const allFlags: FeatureFlag[] = row?.value ? JSON.parse(row.value) : getDefaultFlags();
  const publicFlags = allFlags.filter((f) => f.scope === 'public');
  return json({ flags: publicFlags }, 200, env);
}

// --- SIG-13: feature flags via tabela dedicada (substitui JSON blob em admin_settings) ---

// GET /admin/feature-flags — lista todas as flags da tabela feature_flags.
async function handleFeatureFlags(_request: Request, env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    'SELECT key, enabled, description, updated_at, updated_by FROM feature_flags ORDER BY key'
  ).all();

  const flags = (rows.results ?? []).map((r: any) => ({
    key:         r.key,
    enabled:     r.enabled === 1,
    description: r.description ?? '',
    updatedAt:   r.updated_at  ?? 0,
    updatedBy:   r.updated_by  ?? '',
  }));

  return json({ flags }, 200, env);
}

// PUT /admin/feature-flags/:key — atualiza enabled de uma flag e grava audit log.
async function handleUpdateFeatureFlag(request: Request, env: Env, session: { userId: string; role: string }): Promise<Response> {
  const url = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/feature-flags\/([^/]+)$/);
  if (!match) return err('key inválida', 400, env);
  const key = match[1];

  let body: { enabled?: boolean };
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }
  if (typeof body.enabled !== 'boolean') return err('enabled (boolean) obrigatório', 400, env);

  // Busca estado atual para registrar old_enabled no audit.
  const current = await env.DB.prepare(
    'SELECT enabled FROM feature_flags WHERE key = ?'
  ).bind(key).first<{ enabled: number }>();
  if (!current) return err('flag não encontrada', 404, env);

  const now       = Math.floor(Date.now() / 1000);
  const newEnabled = body.enabled ? 1 : 0;

  // Obtém email do usuário da sessão para o audit log.
  const userRow = await env.DB.prepare(
    'SELECT email FROM admin_users WHERE id = ?'
  ).bind(session.userId).first<{ email: string }>();
  const changedBy = userRow?.email ?? 'admin';

  await env.DB.batch([
    env.DB.prepare(
      'UPDATE feature_flags SET enabled = ?, updated_at = ?, updated_by = ? WHERE key = ?'
    ).bind(newEnabled, now, changedBy, key),
    env.DB.prepare(
      'INSERT INTO feature_flag_audit (id, flag_key, old_enabled, new_enabled, changed_at, changed_by) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(crypto.randomUUID(), key, current.enabled, newEnabled, now, changedBy),
  ]);

  return json({ ok: true, key, enabled: body.enabled }, 200, env);
}

// GET /flags — público, sem auth. Retorna key + enabled para consumo do Android.
async function handlePublicFlags(_request: Request, env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    'SELECT key, enabled FROM feature_flags ORDER BY key'
  ).all();

  const flags = (rows.results ?? []).map((r: any) => ({
    key:     r.key,
    enabled: r.enabled === 1,
  }));

  return json({ flags }, 200, env);
}

// --- GH#1402 (#1405/#1406/#1407): catálogo de anúncios locais (house ads) ---
// Exibido pelo AdBanner do Site/PWA quando o AdSense não preenche o slot (no-fill) ou não está
// configurado. Conteúdo administrável via Console (CRUD abaixo), sorteado no client a cada
// exibição — este worker só entrega o catálogo de ativos, não decide qual sortear.

interface LocalAdRow {
  id: string;
  title: string;
  description: string;
  cta_label: string;
  target_url: string;
  active: number;
  created_at: number;
  updated_at: number;
}

function mapLocalAd(r: LocalAdRow) {
  return {
    id:          r.id,
    title:       r.title,
    description: r.description,
    ctaLabel:    r.cta_label,
    targetUrl:   r.target_url,
    active:      r.active === 1,
    createdAt:   r.created_at,
    updatedAt:   r.updated_at,
  };
}

/** Só http/https — evita `javascript:`/esquema não navegável salvo por engano no Console. */
function isValidHttpUrl(value: string): boolean {
  try {
    const u = new URL(value);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

// GET /local-ads — público, sem auth. Só anúncios ativos, para o AdBanner do Site sortear.
async function handlePublicLocalAds(_request: Request, env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    'SELECT id, title, description, cta_label, target_url, active, created_at, updated_at FROM local_ads WHERE active = 1 ORDER BY created_at DESC'
  ).all<LocalAdRow>();

  // Client só precisa do conteúdo exibível — active/createdAt/updatedAt são detalhe
  // administrativo (já filtrado por active = 1 na query acima).
  const ads = (rows.results ?? []).map((r) => ({
    id:          r.id,
    title:       r.title,
    description: r.description,
    ctaLabel:    r.cta_label,
    targetUrl:   r.target_url,
  }));

  return publicJson({ ads });
}

// GET /admin/local-ads — lista completa (ativos e inativos) para a tabela do painel.
async function handleAdminLocalAdsList(_request: Request, env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    'SELECT id, title, description, cta_label, target_url, active, created_at, updated_at FROM local_ads ORDER BY created_at DESC'
  ).all<LocalAdRow>();

  return json({ ads: (rows.results ?? []).map(mapLocalAd) }, 200, env);
}

// POST /admin/local-ads — cria um anúncio local.
async function handleAdminCreateLocalAd(request: Request, env: Env): Promise<Response> {
  let body: { title?: string; description?: string; ctaLabel?: string; targetUrl?: string; active?: boolean };
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }

  const title      = (body.title ?? '').trim();
  const ctaLabel   = (body.ctaLabel ?? '').trim();
  const targetUrl  = (body.targetUrl ?? '').trim();
  const description = (body.description ?? '').trim();

  if (!title) return err('title obrigatório', 400, env);
  if (!ctaLabel) return err('ctaLabel obrigatório', 400, env);
  if (!targetUrl || !isValidHttpUrl(targetUrl)) return err('targetUrl obrigatório e precisa ser http(s) válido', 400, env);

  const id  = crypto.randomUUID();
  const now = Math.floor(Date.now() / 1000);
  const active = body.active === false ? 0 : 1;

  await env.DB.prepare(
    'INSERT INTO local_ads (id, title, description, cta_label, target_url, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, title, description, ctaLabel, targetUrl, active, now, now).run();

  return json({ ad: { id, title, description, ctaLabel, targetUrl, active: active === 1, createdAt: now, updatedAt: now } }, 201, env);
}

// PUT /admin/local-ads/:id — edita campos e/ou alterna active.
async function handleAdminUpdateLocalAd(request: Request, env: Env): Promise<Response> {
  const url   = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/local-ads\/([^/]+)$/);
  if (!match) return err('id inválido', 400, env);
  const id = match[1];

  const current = await env.DB.prepare(
    'SELECT id, title, description, cta_label, target_url, active, created_at, updated_at FROM local_ads WHERE id = ?'
  ).bind(id).first<LocalAdRow>();
  if (!current) return err('anúncio não encontrado', 404, env);

  let body: { title?: string; description?: string; ctaLabel?: string; targetUrl?: string; active?: boolean };
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }

  const title       = body.title       !== undefined ? body.title.trim()       : current.title;
  const ctaLabel    = body.ctaLabel    !== undefined ? body.ctaLabel.trim()    : current.cta_label;
  const targetUrl   = body.targetUrl   !== undefined ? body.targetUrl.trim()   : current.target_url;
  const description = body.description !== undefined ? body.description.trim() : current.description;
  const active      = body.active      !== undefined ? (body.active ? 1 : 0)   : current.active;

  if (!title) return err('title não pode ficar vazio', 400, env);
  if (!ctaLabel) return err('ctaLabel não pode ficar vazio', 400, env);
  if (!targetUrl || !isValidHttpUrl(targetUrl)) return err('targetUrl precisa ser http(s) válido', 400, env);

  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare(
    'UPDATE local_ads SET title = ?, description = ?, cta_label = ?, target_url = ?, active = ?, updated_at = ? WHERE id = ?'
  ).bind(title, description, ctaLabel, targetUrl, active, now, id).run();

  return json({ ad: { id, title, description, ctaLabel, targetUrl, active: active === 1, createdAt: current.created_at, updatedAt: now } }, 200, env);
}

// DELETE /admin/local-ads/:id — remove um anúncio.
async function handleAdminDeleteLocalAd(request: Request, env: Env): Promise<Response> {
  const url   = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/local-ads\/([^/]+)$/);
  if (!match) return err('id inválido', 400, env);
  const id = match[1];

  const result = await env.DB.prepare('DELETE FROM local_ads WHERE id = ?').bind(id).run();
  if (result.meta.changes === 0) return err('anúncio não encontrado', 404, env);

  return json({ ok: true, id }, 200, env);
}

// --- GH#1312: catálogo remoto de releases (produto+canal) ---
// Backend/Worker do épico "Detectar, comunicar e exibir atualizações do SignallQ Android"
// (consolida #1313/#1314/#1315). Recepção/dedup/UI de Ajustes no Android ficam para o Camilo --
// este recorte cobre só o catálogo (D1, migration 020) e o disparo de push (FCM).

const APP_UPDATE_CHANNELS = ['internal', 'alpha', 'beta', 'production'] as const;
type AppUpdateChannel = typeof APP_UPDATE_CHANNELS[number];

const APP_UPDATE_SLUG_RE = /^[a-z0-9_]+$/;

interface AppReleaseRow {
  release_id: string;
  product: string;
  channel: string;
  version_name: string;
  version_code: number;
  release_notes: string;
  store_url: string;
  notification_enabled: number;
  reminder_campaign_id: string | null;
  status: string;
  published_at: number;
  published_by: string;
  push_status: string | null;
  push_sent_at: number | null;
  push_error: string | null;
  created_at: number;
  updated_at: number;
}

function isoOrNull(sec: number | null): string | null {
  return sec === null ? null : new Date(sec * 1000).toISOString();
}

// Contrato exato pedido pela issue #1312 -- é o que o Android consome em GET /app-updates.
function mapReleasePublic(r: AppReleaseRow) {
  return {
    product: r.product,
    channel: r.channel,
    versionName: r.version_name,
    versionCode: r.version_code,
    publishedAt: isoOrNull(r.published_at),
    releaseNotes: r.release_notes,
    storeUrl: r.store_url,
    notificationEnabled: r.notification_enabled === 1,
    releaseId: r.release_id,
    reminderCampaignId: r.reminder_campaign_id,
  };
}

// Visão administrativa (Console) -- inclui status/audit/resultado do último push, que o Android
// não precisa e não deve enxergar.
function mapReleaseAdmin(r: AppReleaseRow) {
  return {
    ...mapReleasePublic(r),
    status: r.status,
    publishedBy: r.published_by,
    pushStatus: r.push_status,
    pushSentAt: isoOrNull(r.push_sent_at),
    pushError: r.push_error,
    createdAt: isoOrNull(r.created_at),
    updatedAt: isoOrNull(r.updated_at),
  };
}

// GET /app-updates?product=X&channel=Y -- público, sem auth (mesmo modelo de /flags e
// /local-ads: o app Android não carrega sessão admin). Só devolve a release com status='active';
// ausência de release ativa é uma resposta válida (release: null), não erro -- comunicação
// desativada ou produto/canal sem publicação ainda não é uma falha de leitura.
async function handlePublicAppUpdates(request: Request, env: Env): Promise<Response> {
  const url     = new URL(request.url);
  const product = (url.searchParams.get('product') ?? '').trim().toLowerCase();
  const channel = (url.searchParams.get('channel') ?? '').trim().toLowerCase();

  if (!product || !APP_UPDATE_SLUG_RE.test(product)) return err('product obrigatório (a-z0-9_)', 400, env);
  if (!APP_UPDATE_CHANNELS.includes(channel as AppUpdateChannel)) {
    return err(`channel obrigatório, um de: ${APP_UPDATE_CHANNELS.join(', ')}`, 400, env);
  }

  const row = await env.DB.prepare(
    "SELECT * FROM app_releases WHERE product = ? AND channel = ? AND status = 'active'"
  ).bind(product, channel).first<AppReleaseRow>();

  return publicJson({ release: row ? mapReleasePublic(row) : null });
}

// GET /admin/app-updates -- histórico (filtros opcionais product/channel/limit) para a tabela do
// Console. Sempre inclui todas as linhas (active/superseded/deactivated) -- é a fonte de "manter
// histórico mínimo" da issue.
async function handleAdminAppUpdatesList(request: Request, env: Env): Promise<Response> {
  const url         = new URL(request.url);
  const product     = url.searchParams.get('product');
  const channel     = url.searchParams.get('channel');
  const limitParam  = parseInt(url.searchParams.get('limit') ?? '50', 10);
  const limit       = Number.isFinite(limitParam) ? Math.min(Math.max(limitParam, 1), 200) : 50;

  const conditions: string[] = [];
  const params: string[] = [];
  if (product) { conditions.push('product = ?'); params.push(product); }
  if (channel) { conditions.push('channel = ?'); params.push(channel); }
  const where = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';

  const rows = await env.DB.prepare(
    `SELECT * FROM app_releases ${where} ORDER BY published_at DESC LIMIT ?`
  ).bind(...params, limit).all<AppReleaseRow>();

  return json({ releases: (rows.results ?? []).map(mapReleaseAdmin) }, 200, env);
}

interface PublishAppReleaseBody {
  product?: string;
  channel?: string;
  versionName?: string;
  versionCode?: number;
  releaseNotes?: string;
  storeUrl?: string;
  notificationEnabled?: boolean;
  reminderCampaignId?: string | null;
  confirmDowngrade?: boolean;
}

// POST /admin/app-updates -- publica uma release. Regras da issue #1312:
// - comparação por versionCode contra a release 'active' atual do mesmo product+channel;
// - versionCode MENOR que o ativo exige confirmDowngrade=true explícito (nunca silencioso);
// - versionCode IGUAL ao ativo é tratado como correção (UPDATE in-place, mesma release_id);
// - versionCode MAIOR promove a nova linha a 'active' e rebaixa a anterior a 'superseded'.
// releaseId é sempre derivado (`${product}-${channel}-${versionCode}`), nunca aceito do body --
// evita duas linhas 'active' concorrentes com IDs divergentes para o mesmo product+channel.
async function handleAdminPublishAppUpdate(request: Request, env: Env, session: { userId: string; role: string }): Promise<Response> {
  let body: PublishAppReleaseBody;
  try { body = await request.json(); } catch { return err('body JSON inválido', 400, env); }

  const product              = (body.product ?? '').trim().toLowerCase();
  const channel              = (body.channel ?? '').trim().toLowerCase();
  const versionName          = (body.versionName ?? '').trim();
  const versionCode          = body.versionCode;
  const storeUrl             = (body.storeUrl ?? '').trim();
  const releaseNotes         = (body.releaseNotes ?? '').trim();
  const notificationEnabled  = body.notificationEnabled !== false; // default true
  const reminderCampaignId   = body.reminderCampaignId ?? null;

  if (!product || !APP_UPDATE_SLUG_RE.test(product)) return err('product obrigatório (a-z0-9_)', 400, env);
  if (!APP_UPDATE_CHANNELS.includes(channel as AppUpdateChannel)) {
    return err(`channel obrigatório, um de: ${APP_UPDATE_CHANNELS.join(', ')}`, 400, env);
  }
  if (!versionName) return err('versionName obrigatório', 400, env);
  if (typeof versionCode !== 'number' || !Number.isInteger(versionCode) || versionCode <= 0) {
    return err('versionCode obrigatório (inteiro positivo)', 400, env);
  }
  if (!storeUrl || !isValidHttpUrl(storeUrl)) return err('storeUrl obrigatório e precisa ser http(s) válido', 400, env);

  const releaseId = `${product}-${channel}-${versionCode}`;
  const now = nowSec();

  const userRow = await env.DB.prepare('SELECT email FROM admin_users WHERE id = ?').bind(session.userId).first<{ email: string }>();
  const publishedBy = userRow?.email ?? 'admin';

  const currentActive = await env.DB.prepare(
    "SELECT * FROM app_releases WHERE product = ? AND channel = ? AND status = 'active'"
  ).bind(product, channel).first<AppReleaseRow>();

  if (currentActive && currentActive.release_id !== releaseId && versionCode < currentActive.version_code && body.confirmDowngrade !== true) {
    return err(
      `versionCode ${versionCode} é menor que o ativo atual (${currentActive.version_code}). Reenvie com confirmDowngrade=true para confirmar o downgrade intencional.`,
      409, env
    );
  }

  if (currentActive && currentActive.release_id === releaseId) {
    // Mesmo versionCode do que já está ativo -- correção in-place (nota/URL/flags), não duplica histórico.
    await env.DB.prepare(
      `UPDATE app_releases SET version_name = ?, release_notes = ?, store_url = ?, notification_enabled = ?,
       reminder_campaign_id = ?, published_by = ?, updated_at = ? WHERE release_id = ?`
    ).bind(versionName, releaseNotes, storeUrl, notificationEnabled ? 1 : 0, reminderCampaignId, publishedBy, now, releaseId).run();
  } else {
    const statements = [
      env.DB.prepare(
        `INSERT INTO app_releases (release_id, product, channel, version_name, version_code, release_notes, store_url,
         notification_enabled, reminder_campaign_id, status, published_at, published_by, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?)`
      ).bind(releaseId, product, channel, versionName, versionCode, releaseNotes, storeUrl,
             notificationEnabled ? 1 : 0, reminderCampaignId, now, publishedBy, now, now),
    ];
    if (currentActive) {
      statements.push(
        env.DB.prepare("UPDATE app_releases SET status = 'superseded', updated_at = ? WHERE release_id = ?")
          .bind(now, currentActive.release_id)
      );
    }
    await env.DB.batch(statements);
  }

  const saved = await env.DB.prepare('SELECT * FROM app_releases WHERE release_id = ?').bind(releaseId).first<AppReleaseRow>();
  const wasUpdateInPlace = currentActive?.release_id === releaseId;
  return json({ release: saved ? mapReleaseAdmin(saved) : null }, wasUpdateInPlace ? 200 : 201, env);
}

// POST /admin/app-updates/:releaseId/deactivate -- desliga a comunicação sem apagar a release
// (regra explícita da issue). Idempotente: já desativada só retorna o estado atual.
async function handleAdminDeactivateAppUpdate(request: Request, env: Env): Promise<Response> {
  const url   = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/app-updates\/([^/]+)\/deactivate$/);
  if (!match) return err('releaseId inválido', 400, env);
  const releaseId = decodeURIComponent(match[1]);

  const current = await env.DB.prepare('SELECT * FROM app_releases WHERE release_id = ?').bind(releaseId).first<AppReleaseRow>();
  if (!current) return err('release não encontrada', 404, env);

  if (current.status !== 'deactivated') {
    await env.DB.prepare("UPDATE app_releases SET status = 'deactivated', updated_at = ? WHERE release_id = ?")
      .bind(nowSec(), releaseId).run();
  }

  const saved = await env.DB.prepare('SELECT * FROM app_releases WHERE release_id = ?').bind(releaseId).first<AppReleaseRow>();
  return json({ release: saved ? mapReleaseAdmin(saved) : null }, 200, env);
}

// Reaproveita a mesma credencial de serviço (FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY, escopo
// cloud-platform) já usada por getFirebaseAccessToken() -- FCM HTTP v1 aceita esse escopo. Requer
// a Firebase Cloud Messaging API habilitada no projeto GCP (mesmo tipo de passo manual do Luiz já
// feito para GA4/Play Console -- ativação de API, sem custo novo).
async function sendFcmTopicMessage(
  env: Env,
  topic: string,
  data: Record<string, string>
): Promise<{ ok: boolean; message?: string }> {
  if (!env.FIREBASE_CLIENT_EMAIL || !env.FIREBASE_PRIVATE_KEY) {
    return { ok: false, message: 'FIREBASE_CLIENT_EMAIL/FIREBASE_PRIVATE_KEY não configurados.' };
  }
  let token: string;
  try {
    token = await getFirebaseAccessToken(env);
  } catch (e) {
    return { ok: false, message: `auth_failed: ${String(e)}` };
  }
  const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: { topic, data } }),
  });
  if (!resp.ok) {
    const errText = await resp.text();
    return { ok: false, message: `fcm_error_${resp.status}: ${errText.slice(0, 300)}` };
  }
  return { ok: true };
}

// POST /admin/app-updates/:releaseId/push -- dispara APP_UPDATE_AVAILABLE via FCM (tópico
// `app_updates_${product}_${channel}`, ao qual o Android se inscreve -- fora do escopo deste
// worker). Exige confirmed=true no body: é a confirmação manual de disponibilidade na Google Play
// que a issue pede -- o worker não tem como verificar isso sozinho, então o contrato obriga o
// admin a declarar explicitamente que já checou. Resultado (sucesso ou falha) é sempre registrado
// na própria release (push_status/push_sent_at/push_error), nunca silencioso.
async function handleAdminPushAppUpdate(request: Request, env: Env): Promise<Response> {
  const url   = new URL(request.url);
  const match = url.pathname.match(/^\/admin\/app-updates\/([^/]+)\/push$/);
  if (!match) return err('releaseId inválido', 400, env);
  const releaseId = decodeURIComponent(match[1]);

  let body: { confirmed?: boolean };
  try { body = await request.json(); } catch { body = {}; }
  if (body.confirmed !== true) {
    return err('confirmed=true obrigatório -- confirme manualmente que a versão está disponível na Google Play antes de disparar.', 400, env);
  }

  const release = await env.DB.prepare('SELECT * FROM app_releases WHERE release_id = ?').bind(releaseId).first<AppReleaseRow>();
  if (!release) return err('release não encontrada', 404, env);
  if (release.status !== 'active') return err(`release não está ativa (status atual: ${release.status})`, 409, env);
  if (release.notification_enabled !== 1) return err('notificação desabilitada nesta release (notificationEnabled=false)', 409, env);

  const topic  = `app_updates_${release.product}_${release.channel}`;
  const result = await sendFcmTopicMessage(env, topic, {
    type:        'APP_UPDATE_AVAILABLE',
    product:     release.product,
    channel:     release.channel,
    versionCode: String(release.version_code),
    releaseId:   release.release_id,
  });

  const now = nowSec();
  await env.DB.prepare(
    'UPDATE app_releases SET push_status = ?, push_sent_at = ?, push_error = ?, updated_at = ? WHERE release_id = ?'
  ).bind(result.ok ? 'sent' : 'error', now, result.ok ? null : (result.message ?? 'falha desconhecida'), now, releaseId).run();

  const saved = await env.DB.prepare('SELECT * FROM app_releases WHERE release_id = ?').bind(releaseId).first<AppReleaseRow>();
  return json({ ok: result.ok, release: saved ? mapReleaseAdmin(saved) : null, message: result.message }, 200, env);
}

// --- router ---

type Handler = (req: Request, env: Env) => Promise<Response>;

const ROUTES: Array<{ method: string; pattern: RegExp; handler: Handler }> = [
  { method: "GET",  pattern: /^\/admin\/metrics\/overview$/,                    handler: withErrorLogging('metrics', handleOverview) },
  { method: "GET",  pattern: /^\/admin\/metrics\/diagnostics\/summary$/,        handler: withErrorLogging('metrics', handleDiagnosticsSummary) },
  { method: "GET",  pattern: /^\/admin\/metrics\/diagnostics$/,                 handler: withErrorLogging('metrics', handleDiagnostics) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-usage$/,                    handler: withErrorLogging('metrics', handleAiCost) },
  { method: "GET",  pattern: /^\/admin\/metrics\/timeline$/,                    handler: withErrorLogging('metrics', handleTimeline) },
  { method: "GET",  pattern: /^\/admin\/metrics\/network$/,                     handler: withErrorLogging('metrics', handleNetworkInsights) },
  { method: "GET",  pattern: /^\/admin\/metrics\/regions$/,                     handler: withErrorLogging('metrics', handleRegionInsights) },
  { method: "GET",  pattern: /^\/admin\/metrics\/top-issues$/,                  handler: withErrorLogging('metrics', handleTopIssues) },
  { method: "GET",  pattern: /^\/admin\/metrics\/alerts$/,                      handler: withErrorLogging('metrics', handleRecentAlerts) },
  { method: "GET",  pattern: /^\/admin\/alerts$/,                               handler: withErrorLogging('alerts', handleAlerts) },
  { method: "POST", pattern: /^\/admin\/alerts\/[^/]+\/resolve$/,               handler: withErrorLogging('alerts', handleResolveAlert) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-costs$/,                    handler: withErrorLogging('metrics', handleAiCostMetrics) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-providers$/,                handler: withErrorLogging('metrics', handleAiProviders) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-quota$/,                    handler: withErrorLogging('ai-quota', handleAiQuota) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-usage\/timeline$/,          handler: withErrorLogging('metrics', handleAiUsageTimeline) },
  { method: "GET",  pattern: /^\/admin\/metrics\/ai-usage\/records$/,          handler: withErrorLogging('metrics', handleAiUsageRecords) },
  { method: "GET",  pattern: /^\/admin\/metrics\/operators$/,                   handler: withErrorLogging('metrics', handleOperators) },
  { method: "GET",  pattern: /^\/admin\/metrics\/app-versions$/,                handler: withErrorLogging('metrics', handleAppVersions) },
  { method: "GET",  pattern: /^\/admin\/metrics\/intelligence$/,                handler: withErrorLogging('metrics', handleDiagnosticsIntelligence) },
  { method: "GET",  pattern: /^\/admin\/diagnostics\/intelligence$/,            handler: withErrorLogging('metrics', handleDiagnosticsIntelligence) },
  { method: "GET",  pattern: /^\/admin\/metrics\/analytics\/product$/,          handler: withErrorLogging('analytics', handleProductAnalytics) },
  { method: "GET",  pattern: /^\/admin\/analytics\/product$/,                   handler: withErrorLogging('analytics', handleProductAnalytics) },
  { method: "GET",  pattern: /^\/admin\/metrics\/analytics\/speedtest-funnel$/, handler: withErrorLogging('analytics', handleSpeedtestFunnel) },
  { method: "GET",  pattern: /^\/admin\/metrics\/analytics\/battery$/,          handler: withErrorLogging('analytics', handleBatteryAnalytics) },
  { method: "GET",  pattern: /^\/admin\/analytics\/battery$/,                   handler: withErrorLogging('analytics', handleBatteryAnalytics) },
  { method: "GET",  pattern: /^\/admin\/metrics\/analytics\/devices$/,          handler: withErrorLogging('analytics', handleDeviceBreakdown) },
  { method: "GET",  pattern: /^\/admin\/analytics\/devices$/,                   handler: withErrorLogging('analytics', handleDeviceBreakdown) },
  { method: "GET",  pattern: /^\/admin\/metrics\/errors$/,                      handler: handleErrors },
  { method: "GET",  pattern: /^\/admin\/metrics\/errors\/timeline$/,             handler: withErrorLogging('metrics', handleErrorsTimeline) },
  { method: "POST", pattern: /^\/admin\/errors\/[^/]+\/resolve$/,               handler: withErrorLogging('errors', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleResolveSystemError(req, env, session);
    }) },
  { method: "GET",  pattern: /^\/admin\/system-health$/,                        handler: withErrorLogging('system-health', handleSystemHealth) },
  { method: "GET",  pattern: /^\/admin\/integrations\/readiness$/,              handler: withErrorLogging('integration-readiness', handleIntegrationReadiness) },
  { method: "GET",  pattern: /^\/admin\/system-health\/history$/,               handler: withErrorLogging('system-health', handleSystemHealthHistory) },
  { method: "POST", pattern: /^\/admin\/system-health\/snapshot$/,              handler: withErrorLogging('system-health', handleTriggerHealthSnapshot) },
  { method: "GET",  pattern: /^\/admin\/cloudflare-usage$/,                     handler: withErrorLogging('cloudflare-usage', handleCloudflareUsage) },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/status$/,       handler: handleFirebaseStatus },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/analytics$/,    handler: handleFirebaseAnalytics },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/acquisition$/,  handler: handleFirebaseAcquisition },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/screens$/,      handler: handleFirebaseScreens },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/retention$/,    handler: handleFirebaseRetention },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/crashlytics$/,  handler: handleFirebaseCrashlytics },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/versions$/,     handler: handleFirebaseVersions },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/crash-issues$/, handler: handleFirebaseCrashIssues },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/sync$/,         handler: handleFirebaseSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/status$/,   handler: handleGooglePlayStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/sync$/,     handler: handleGooglePlaySync },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/reviews$/,  handler: handleGooglePlayReviewsList },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/tracks\/status$/,   handler: handleGooglePlayTracksStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/tracks\/sync$/,     handler: handleGooglePlayTracksSync },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/tracks\/backfill$/, handler: handleGooglePlayTracksBackfill },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/vitals\/status$/,  handler: handlePlayVitalsStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/vitals\/sync$/,    handler: handlePlayVitalsSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/vitals\/crash-rate\/status$/, handler: handlePlayCrashRateStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/vitals\/crash-rate\/sync$/,   handler: handlePlayCrashRateSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/management\/status$/,     handler: handleFirebaseManagementStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/management\/sync$/,       handler: handleFirebaseManagementSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/remote-config\/status$/,  handler: handleRemoteConfigStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/remote-config\/sync$/,    handler: handleRemoteConfigSync },
  // GH#1478 (Épico #1347, F2) — backend admin de Feature Flags / Remote Config. Namespace
  // /admin/firebase/remote-config/* (sem "integrations") é deliberadamente separado do par
  // status/sync acima (GH#1344, leitura read-only pro dashboard de integrações) — este é o CRUD
  // administrativo real (GET/validate/publish/rollback com ETag). Ver src/remoteConfigAdmin.ts —
  // validate/publish/rollback validam contra o catálogo canônico (`src/featureFlagCatalog.ts`,
  // #1477) desde 2026-07-26 (fechamento de #1478); não são mais skeleton bloqueado.
  { method: "GET",  pattern: /^\/admin\/firebase\/remote-config$/,              handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleRemoteConfigAdminGet(req, env, session);
    }) },
  { method: "GET",  pattern: /^\/admin\/firebase\/remote-config\/versions$/,    handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleRemoteConfigAdminVersions(req, env, session);
    }) },
  { method: "POST", pattern: /^\/admin\/firebase\/remote-config\/validate$/,    handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleRemoteConfigAdminValidate(req, env, session);
    }) },
  { method: "POST", pattern: /^\/admin\/firebase\/remote-config\/publish$/,     handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleRemoteConfigAdminPublish(req, env, session);
    }) },
  { method: "POST", pattern: /^\/admin\/firebase\/remote-config\/rollback$/,    handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleRemoteConfigAdminRollback(req, env, session);
    }) },
  // GH#1478 — catálogo canônico (leitura, pro Admin F3 consumir) e sincronização catálogo→Firebase
  // (cria parâmetros ausentes, detecta órfãos/divergências). ?dryRun=true simula sem publicar.
  { method: "GET",  pattern: /^\/admin\/firebase\/feature-flags\/catalog$/,     handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleFeatureFlagsCatalogGet(req, env, session);
    }) },
  { method: "POST", pattern: /^\/admin\/firebase\/feature-flags\/sync$/,        handler: withErrorLogging('remote-config-admin', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleFeatureFlagsSync(req, env, session);
    }) },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/app-check\/status$/,       handler: handleAppCheckStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/app-check\/sync$/,         handler: handleAppCheckSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/app-distribution\/status$/, handler: handleAppDistributionStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/app-distribution\/sync$/,   handler: handleAppDistributionSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/firebase\/fcm-delivery\/status$/,     handler: handleFcmDeliveryDataStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/firebase\/fcm-delivery\/sync$/,       handler: handleFcmDeliveryDataSync },
  { method: "GET",  pattern: /^\/admin\/integrations\/google-play\/store-listing\/status$/, handler: handleStoreListingStatus },
  { method: "POST", pattern: /^\/admin\/integrations\/google-play\/store-listing\/sync$/,   handler: handleStoreListingSync },
  { method: "GET",  pattern: /^\/admin\/analytics\/product$/,                   handler: withErrorLogging('analytics', handleProductAnalytics) },
  { method: "GET",  pattern: /^\/admin\/analytics\/battery$/,                   handler: withErrorLogging('analytics', handleBatteryAnalytics) },
  { method: "GET",  pattern: /^\/admin\/settings$/,                             handler: handleSettings },
  { method: "POST", pattern: /^\/admin\/settings$/,                             handler: handleSettings },
  { method: "GET",  pattern: /^\/admin\/feature-flags$/,                        handler: withErrorLogging('feature-flags', handleFeatureFlags) },
  { method: "PUT",  pattern: /^\/admin\/feature-flags\/[^/]+$/,                 handler: withErrorLogging('feature-flags', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleUpdateFeatureFlag(req, env, session);
    }) },
  // GH#1407 (Feature #1402): CRUD admin do catálogo de anúncios locais — já protegido pela
  // sessão httpOnly checada antes do loop das ROUTES (ver export default.fetch abaixo).
  { method: "GET",    pattern: /^\/admin\/local-ads$/,          handler: withErrorLogging('local-ads', handleAdminLocalAdsList) },
  { method: "POST",   pattern: /^\/admin\/local-ads$/,          handler: withErrorLogging('local-ads', handleAdminCreateLocalAd) },
  { method: "PUT",    pattern: /^\/admin\/local-ads\/[^/]+$/,   handler: withErrorLogging('local-ads', handleAdminUpdateLocalAd) },
  { method: "DELETE", pattern: /^\/admin\/local-ads\/[^/]+$/,   handler: withErrorLogging('local-ads', handleAdminDeleteLocalAd) },
  // GH#1312: catálogo remoto de releases — histórico, publicação e disparo de push.
  { method: "GET",  pattern: /^\/admin\/app-updates$/,                    handler: withErrorLogging('app-updates', handleAdminAppUpdatesList) },
  { method: "POST", pattern: /^\/admin\/app-updates$/,                    handler: withErrorLogging('app-updates', async (req, env) => {
      const session = await authenticateSession(req, env);
      if (!session) return err('Unauthorized', 401, env);
      return handleAdminPublishAppUpdate(req, env, session);
    }) },
  { method: "POST", pattern: /^\/admin\/app-updates\/[^/]+\/deactivate$/,  handler: withErrorLogging('app-updates', handleAdminDeactivateAppUpdate) },
  { method: "POST", pattern: /^\/admin\/app-updates\/[^/]+\/push$/,       handler: withErrorLogging('app-updates', handleAdminPushAppUpdate) },
];

const INGEST_ROUTES: Array<{ method: string; pattern: RegExp; handler: Handler }> = [
  { method: "POST", pattern: /^\/ingest\/diagnostic$/, handler: handleIngestDiagnostic },
  { method: "POST", pattern: /^\/ingest\/ai-usage$/,   handler: handleIngestAiUsage },
  { method: "POST", pattern: /^\/ingest\/analytics$/,  handler: withErrorLogging('analytics', handleIngestAnalytics) },
  { method: "POST", pattern: /^\/ingest\/waitlist$/,   handler: withErrorLogging('waitlist', handleIngestWaitlist) },
];

// GH#877 — automatiza o sync de telemetria (Firebase Analytics + Google Play
// ratings/tracks) que antes só rodava quando alguém clicava em "Sincronizar
// telemetria" no Admin. O botão manual continua existindo (força um sync fora
// do horário do cron); o cron cobre o caso de ninguém lembrar de clicar.
//
// Cada sync roda isolada: uma falhar não pode impedir as outras, por isso
// try/catch individual por job em vez de um Promise.all que aborta tudo no
// primeiro reject. logError é a única forma de alguém notar que o cron parou
// de funcionar, já que não existe nenhuma UI olhando essa execução em tempo real.
//
// GH#878 (decisão 2026-07-11): o job 'firebase' continua na lista de propósito —
// handleFirebaseSync curto-circuita sozinho via FIREBASE_SYNC_ENABLED e devolve
// source:"disabled" (não é 'error'), então o cron não grava mais 403 recorrente
// em system_errors. Tirar o job da lista exigiria reinserir manualmente quando o
// export existir; a guarda por flag é a forma mais fácil de religar.
const SCHEDULED_SYNC_JOBS: Array<{
  name: string;
  run: (env: Env) => Promise<Response>;
  isError: (data: Record<string, unknown>) => boolean;
}> = [
  {
    name: 'firebase',
    run: (env) => handleFirebaseSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.source === 'error',
  },
  {
    name: 'google-play',
    run: (env) => handleGooglePlaySync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'google-play-tracks',
    run: (env) => handleGooglePlayTracksSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'google-play-vitals',
    run: (env) => handlePlayVitalsSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'google-play-crash-rate',
    run: (env) => handlePlayCrashRateSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'firebase-management',
    run: (env) => handleFirebaseManagementSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'firebase-remote-config',
    run: (env) => handleRemoteConfigSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'firebase-app-check',
    run: (env) => handleAppCheckSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'firebase-app-distribution',
    run: (env) => handleAppDistributionSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'firebase-fcm-delivery',
    run: (env) => handleFcmDeliveryDataSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
  {
    name: 'google-play-store-listing',
    run: (env) => handleStoreListingSync(new Request('https://internal/scheduled-sync'), env),
    isError: (data) => data.status === 'error',
  },
];

async function runScheduledSync(env: Env): Promise<void> {
  await Promise.allSettled(
    SCHEDULED_SYNC_JOBS.map(async (job) => {
      try {
        const resp = await job.run(env);
        const data = (await resp.json()) as Record<string, unknown>;
        if (job.isError(data)) {
          await logError(env, `${job.name}-scheduled-sync`, String(data.message ?? 'falha sem mensagem detalhada'));
        }
      } catch (e) {
        await logError(env, `${job.name}-scheduled-sync`, String(e), e instanceof Error ? (e.stack ?? '') : '');
      }
    })
  );
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(env) });
    }
    const url = new URL(request.url);

    // Redireciona browser direto para o painel (evita exibir JSON cru em Edge/Chrome).
    if (url.pathname === "/" || url.pathname === "") {
      return Response.redirect("https://signallq-admin-panel.pages.dev", 302);
    }

    if (url.pathname === "/health") {
      if (!authenticate(request, env)) return err("Unauthorized", 401, env);
      return json({ status: "ok", worker: "signallq-admin-worker", timestamp: new Date().toISOString() }, 200, env);
    }

    // Endpoints públicos sem auth — o app Android consome sem credenciais.
    if (url.pathname === '/feature-flags' && request.method === 'GET') {
      return handlePublicFeatureFlags(request, env);
    }
    // SIG-13: /flags — endpoint público da nova tabela feature_flags.
    if (url.pathname === '/flags' && request.method === 'GET') {
      return withErrorLogging('flags', handlePublicFlags)(request, env);
    }
    // GH#1406 (Feature #1402): catálogo de anúncios locais (house ads) — público, só ativos.
    if (url.pathname === '/local-ads' && request.method === 'GET') {
      return withErrorLogging('local-ads', handlePublicLocalAds)(request, env);
    }
    // GH#1312: catálogo remoto de releases — público, o app Android não carrega sessão admin.
    if (url.pathname === '/app-updates' && request.method === 'GET') {
      return withErrorLogging('app-updates', handlePublicAppUpdates)(request, env);
    }

    // Rotas /ingest/* — autenticam com INGEST_KEY (scope limitado, vai no APK).
    for (const route of INGEST_ROUTES) {
      if (route.method === request.method && route.pattern.test(url.pathname)) {
        if (!authenticateIngest(request, env)) return err("Unauthorized", 401, env);
        return route.handler(request, env);
      }
    }

    // SIG-136: rotas de auth — login é a única /admin/* sem sessão prévia.
    if (url.pathname === '/admin/auth/login' && request.method === 'POST') {
      return handleAuthLogin(request, env)
    }
    if (url.pathname === '/admin/auth/logout' && request.method === 'POST') {
      return handleAuthLogout(request, env)
    }
    if (url.pathname === '/admin/auth/me' && request.method === 'GET') {
      return handleAuthMe(request, env)
    }
    if (url.pathname === '/admin/auth/users' && request.method === 'POST') {
      const session = await authenticateSession(request, env)
      if (!session) return err('Unauthorized', 401, env)
      return handleAuthCreateUser(request, env, session)
    }
    if (url.pathname === '/admin/auth/password' && request.method === 'POST') {
      const session = await authenticateSession(request, env)
      if (!session) return err('Unauthorized', 401, env)
      return handleAuthChangePassword(request, env, session)
    }

    // Rotas /admin/* — autenticam por sessão httpOnly (SIG-136).
    const session = await authenticateSession(request, env)
    if (!session) {
      return err("Unauthorized", 401, env);
    }

    // Refs #1446 — /admin/diagnostic/* é proxy pro signallq-diagnostic-worker (ver
    // proxyDiagnosticAdmin acima). Prefixo genérico, não uma lista fixa de endpoints: cobre tudo
    // que já existe hoje (rulesets, validate, simulate, publish, rollback) e o que for adicionado
    // no diagnostic-worker depois, sem precisar tocar neste arquivo de novo.
    if (url.pathname.startsWith('/admin/diagnostic/')) {
      return proxyDiagnosticAdmin(request, env, session)
    }

    for (const route of ROUTES) {
      if (route.method === request.method && route.pattern.test(url.pathname)) {
        return route.handler(request, env);
      }
    }
    return err("Not found", 404, env);
  },

  // Cloudflare Cron Trigger (ver [triggers] em wrangler.toml) — dois crons
  // compartilham o mesmo handler; diferencia pela expressão recebida em event.cron.
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    if (event.cron === "0 12 * * *") {
      // Informe diário de visitas do site no Discord (09:00 BRT, mesmo horário do Lagcheck).
      ctx.waitUntil(runDailyVisitReport(env));
    } else if (event.cron === "0 6 * * *") {
      // GH#877 — sync diário de telemetria (Firebase Analytics + Google Play).
      ctx.waitUntil(runScheduledSync(env));
    } else {
      // #788 — snapshot de latência/uptime a cada 15min, sem custo (Workers Free
      // inclui cron triggers; 96 invocações/dia, irrelevante nos 100k/dia livres).
      ctx.waitUntil(runHealthSnapshot(env));
    }
  },
};
