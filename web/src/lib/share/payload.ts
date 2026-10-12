// Resultado compartilhável: parâmetros mínimos, sem dado pessoal, com assinatura HMAC.
// O cliente não tem o segredo: `POST /api/share/` valida as faixas e devolve o link
// assinado; `/r/` só aceita links assinados. A assinatura preserva os valores do link,
// mas não atesta que o cliente os mediu.

export interface SharePayload {
  /** Mbps, 1 casa decimal. */
  download: number
  upload: number
  /** ms, inteiros. */
  ping: number
  jitter: number
  /** Epoch em segundos de quando o link foi gerado. */
  t: number
}

const VERSION = 'v1'

/** Faixas aceitas; fora delas o parâmetro é descartado (link inválido). */
export const SHARE_LIMITS = {
  download: [0.1, 10000],
  upload: [0.1, 10000],
  ping: [0, 2000],
  jitter: [0, 2000],
} as const

const MIN_T = 1_700_000_000 // 2023-11
const MAX_FUTURE_S = 86_400

const inRange = (v: number, [min, max]: readonly [number, number]) => Number.isFinite(v) && v >= min && v <= max

/** Arredonda um resultado medido para o formato canônico do link. */
export function toSharePayload(
  m: { download: number; upload: number; ping: number; jitter: number },
  nowSeconds = Math.floor(Date.now() / 1000),
): SharePayload {
  return {
    download: Math.round(m.download * 10) / 10,
    upload: Math.round(m.upload * 10) / 10,
    ping: Math.round(m.ping),
    jitter: Math.round(m.jitter),
    t: nowSeconds,
  }
}

/** Valida faixas e forma. Devolve o payload canônico ou `null` se algo estiver fora do esperado. */
export function validateSharePayload(
  raw: Partial<Record<keyof SharePayload, unknown>>,
  nowSeconds = Math.floor(Date.now() / 1000),
): SharePayload | null {
  const num = (v: unknown) => (typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN)
  const download = num(raw.download)
  const upload = num(raw.upload)
  const ping = num(raw.ping)
  const jitter = num(raw.jitter)
  const t = num(raw.t)
  if (!inRange(download, SHARE_LIMITS.download) || !inRange(upload, SHARE_LIMITS.upload)) return null
  if (!inRange(ping, SHARE_LIMITS.ping) || !inRange(jitter, SHARE_LIMITS.jitter)) return null
  if (!Number.isInteger(t) || t < MIN_T || t > nowSeconds + MAX_FUTURE_S) return null
  return toSharePayload({ download, upload, ping, jitter }, t)
}

/** Texto assinado: versão + valores canônicos. */
export function signedText(p: SharePayload): string {
  return `${VERSION}:${p.download.toFixed(1)}:${p.upload.toFixed(1)}:${p.ping}:${p.jitter}:${p.t}`
}

/** HMAC-SHA256 truncado em 16 hexadecimais (64 bits). */
export async function hmac(secret: string, text: string): Promise<string> {
  const enc = new TextEncoder()
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(text))
  const hex = Array.from(new Uint8Array(sig))
    .slice(0, 8)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
  return hex
}

export async function signSharePayload(secret: string, p: SharePayload): Promise<string> {
  return hmac(secret, signedText(p))
}

export async function verifyShareSignature(secret: string, p: SharePayload, signature: string): Promise<boolean> {
  if (typeof signature !== 'string' || signature.length !== 16) return false
  const expected = await signSharePayload(secret, p)
  return expected === signature.toLowerCase()
}

/** Converte o payload assinado em query string pronta (`d=...&u=...&s=...`). */
export async function toShareQuery(secret: string, p: SharePayload): Promise<string> {
  const s = await signSharePayload(secret, p)
  const q = new URLSearchParams({
    d: p.download.toFixed(1),
    u: p.upload.toFixed(1),
    p: String(p.ping),
    j: String(p.jitter),
    t: String(p.t),
    s,
  })
  return q.toString()
}

/** Lê os parâmetros da URL, valida valores e confere a assinatura. `null` se algo falhar. */
export async function readSignedShare(secret: string, params: URLSearchParams): Promise<SharePayload | null> {
  const s = params.get('s')
  if (!s) return null
  const candidate = validateSharePayload({
    download: params.get('d') ?? undefined,
    upload: params.get('u') ?? undefined,
    ping: params.get('p') ?? undefined,
    jitter: params.get('j') ?? undefined,
    t: params.get('t') ?? undefined,
  })
  if (!candidate) return null
  const ok = await verifyShareSignature(secret, candidate, s)
  return ok ? candidate : null
}
