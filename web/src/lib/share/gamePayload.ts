// Resultado do Teste Gamer compartilhável: jogo, veredito e três números, com assinatura HMAC
import { GAMES_DATA } from '@/app/modo-gamer/modoGamerData'
import { hmac } from './payload'

export type GameShareVerdict = 'ok' | 'warn' | 'bad'

export interface GameSharePayload {
  /** Id do jogo ou categoria */
  game: string
  verdict: GameShareVerdict
  /** ms, inteiros. */
  ping: number
  jitter: number
  /** Aumento sob carga em ms; `null` quando a carga não foi medida. */
  bloat: number | null
  t: number
}

const VERSION = 'g1'
const MIN_T = 1_700_000_000
const MAX_FUTURE_S = 86_400
const MAX_MS = 2000

export const CATEGORY_REFS: Record<string, { label: string }> = {
  'cat-br': { label: 'Tiro / Battle Royale' },
  'cat-mob': { label: 'MOBA / Mobile' },
  'cat-cas': { label: 'Casual / Esportes' },
  'cat-fps': { label: 'FPS / Tiro' },
}

/** Nome para exibir a partir da referência; `null` se a referência não existe. */
export function gameNameOf(ref: string): string | null {
  const g = GAMES_DATA.find((x) => x.id === ref)
  if (g) return g.name
  const c = CATEGORY_REFS[ref]
  return c ? `Jogo (${c.label})` : ref || 'Jogo'
}

const VERDICTS: readonly string[] = ['ok', 'warn', 'bad']

function int(v: unknown, max = MAX_MS): number | null {
  const n = typeof v === 'number' ? v : typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN
  return Number.isFinite(n) && n >= 0 && n <= max ? Math.round(n) : null
}

export function validateGameShare(
  raw: { game?: unknown; verdict?: unknown; ping?: unknown; jitter?: unknown; bloat?: unknown; t?: unknown },
  nowSeconds = Math.floor(Date.now() / 1000),
): GameSharePayload | null {
  const game = typeof raw.game === 'string' ? raw.game.trim() : ''
  if (!game) return null

  const verdict = typeof raw.verdict === 'string' && VERDICTS.includes(raw.verdict) ? (raw.verdict as GameShareVerdict) : null
  if (!verdict) return null

  const ping = int(raw.ping)
  const jitter = int(raw.jitter)
  if (ping === null || jitter === null) return null

  let bloat: number | null = null
  if (raw.bloat !== undefined && raw.bloat !== null && raw.bloat !== '') {
    bloat = int(raw.bloat)
    if (bloat === null) return null
  }

  const t = typeof raw.t === 'number' ? raw.t : typeof raw.t === 'string' && raw.t.trim() !== '' ? Number(raw.t) : NaN
  if (!Number.isInteger(t) || t < MIN_T || t > nowSeconds + MAX_FUTURE_S) return null

  return { game, verdict, ping, jitter, bloat, t }
}

export function gameSignedText(p: GameSharePayload): string {
  const b = p.bloat === null ? 'n' : String(p.bloat)
  return `${VERSION}:${p.game}:${p.verdict}:${p.ping}:${p.jitter}:${b}:${p.t}`
}

export async function signGameShare(secret: string, p: GameSharePayload): Promise<string> {
  return hmac(secret, gameSignedText(p))
}

export async function verifyGameShareSignature(secret: string, p: GameSharePayload, signature: string): Promise<boolean> {
  if (typeof signature !== 'string' || signature.length !== 16) return false
  const expected = await signGameShare(secret, p)
  return expected === signature.toLowerCase()
}

export async function toGameShareQuery(secret: string, p: GameSharePayload): Promise<string> {
  const s = await signGameShare(secret, p)
  const q = new URLSearchParams({
    g: p.game,
    v: p.verdict,
    p: String(p.ping),
    j: String(p.jitter),
    t: String(p.t),
    s,
  })
  if (p.bloat !== null) q.set('b', String(p.bloat))
  return q.toString()
}

export async function readSignedGameShare(secret: string, params: URLSearchParams): Promise<GameSharePayload | null> {
  const s = params.get('s')
  if (!s) return null
  const candidate = validateGameShare({
    game: params.get('g') ?? undefined,
    verdict: params.get('v') ?? undefined,
    ping: params.get('p') ?? undefined,
    jitter: params.get('j') ?? undefined,
    bloat: params.get('b') ?? undefined,
    t: params.get('t') ?? undefined,
  })
  if (!candidate) return null
  const ok = await verifyGameShareSignature(secret, candidate, s)
  return ok ? candidate : null
}
