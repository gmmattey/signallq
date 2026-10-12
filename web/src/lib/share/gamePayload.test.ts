import { describe, expect, it } from 'vitest'
import {
  gameNameOf,
  readSignedGameShare,
  signGameShare,
  toGameShareQuery,
  validateGameShare,
  verifyGameShareSignature,
  type GameSharePayload,
} from './gamePayload'

const SECRET = 'segredo-de-teste-1234567890'
const SAMPLE: GameSharePayload = {
  game: 'fortnite',
  verdict: 'ok',
  ping: 18,
  jitter: 3,
  bloat: 4,
  t: 1_791_000_000,
}

describe('gamePayload', () => {
  it('converte id em nome legivel do catalogo', () => {
    expect(gameNameOf('fortnite')).toBe('Fortnite')
    expect(gameNameOf('cat-br')).toBe('Jogo (Tiro / Battle Royale)')
  })

  it('valida dados corretos e descarta valores corrompidos', () => {
    expect(validateGameShare(SAMPLE, 1_791_000_000)).toEqual(SAMPLE)
    expect(validateGameShare({ ...SAMPLE, verdict: 'invalido' })).toBeNull()
    expect(validateGameShare({ ...SAMPLE, ping: -5 })).toBeNull()
  })

  it('assina e verifica assinatura HMAC', async () => {
    const sig = await signGameShare(SECRET, SAMPLE)
    expect(sig).toHaveLength(16)
    expect(await verifyGameShareSignature(SECRET, SAMPLE, sig)).toBe(true)
    expect(await verifyGameShareSignature(SECRET, { ...SAMPLE, ping: 99 }, sig)).toBe(false)
  })

  it('serializa e desserializa query string assinada', async () => {
    const q = await toGameShareQuery(SECRET, SAMPLE)
    const params = new URLSearchParams(q)
    const back = await readSignedGameShare(SECRET, params)
    expect(back).toEqual(SAMPLE)
  })
})
