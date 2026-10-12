import { describe, expect, it } from 'vitest'
import {
  readSignedShare,
  signSharePayload,
  toSharePayload,
  toShareQuery,
  validateSharePayload,
  verifyShareSignature,
} from './payload'

const VALID = { download: 287.4, upload: 94.2, ping: 12, jitter: 3 }
const SECRET = 'segredo-de-teste-1234567890'

describe('validacao do payload', () => {
  it('arredonda para 1 casa em vazao e inteiros em latencia', () => {
    const p = toSharePayload({ download: 287.44, upload: 94.26, ping: 12.3, jitter: 3.8 }, 1_790_000_000)
    expect(p.download).toBe(287.4)
    expect(p.upload).toBe(94.3)
    expect(p.ping).toBe(12)
    expect(p.jitter).toBe(4)
  })

  it('rejeita valores fora das faixas aceitas', () => {
    expect(validateSharePayload({ ...VALID, download: 0 })).toBeNull()
    expect(validateSharePayload({ ...VALID, download: 20000 })).toBeNull()
    expect(validateSharePayload({ ...VALID, ping: -1 })).toBeNull()
    expect(validateSharePayload({ ...VALID, jitter: 3000 })).toBeNull()
    expect(validateSharePayload({ ...VALID, t: 100 })).toBeNull()
  })
})

describe('assinatura HMAC', () => {
  it('gera assinatura deterministica de 16 caracteres hex', async () => {
    const p = toSharePayload(VALID, 1_790_000_000)
    const sig1 = await signSharePayload(SECRET, p)
    const sig2 = await signSharePayload(SECRET, p)
    expect(sig1).toHaveLength(16)
    expect(sig1).toBe(sig2)
    expect(/^[0-9a-f]{16}$/.test(sig1)).toBe(true)
  })

  it('detecta alteracao de valores no link assinado', async () => {
    const p = toSharePayload(VALID, 1_790_000_000)
    const sig = await signSharePayload(SECRET, p)
    expect(await verifyShareSignature(SECRET, p, sig)).toBe(true)
    expect(await verifyShareSignature(SECRET, { ...p, download: 999 }, sig)).toBe(false)
  })

  it('le e valida a query string assinada', async () => {
    const p = toSharePayload(VALID, 1_790_000_000)
    const query = await toShareQuery(SECRET, p)
    const params = new URLSearchParams(query)
    const read = await readSignedShare(SECRET, params)
    expect(read).toEqual(p)
  })
})
