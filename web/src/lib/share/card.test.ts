import { beforeAll, describe, expect, it, vi } from 'vitest'
import { cardHeadline, cardNumber, cardTicks, qrData, ringLines } from './card'
import { drawStoryCard, SAFE, STORY_H, STORY_W } from './storyCard'
import { toSharePayload } from './payload'

const P = toSharePayload({ download: 287.4, upload: 94.2, ping: 12, jitter: 3 }, 1_791_800_000)

function recorder() {
  const calls: { op: string; args: unknown[] }[] = []
  const state: Record<string, unknown> = {}
  const ctx = new Proxy(state, {
    get(t, key: string) {
      if (key in t) return t[key]
      if (key === 'measureText') return (s: string) => ({ width: s.length * 40 })
      return (...args: unknown[]) => void calls.push({ op: key, args })
    },
    set(t, key: string, v) {
      t[key] = v
      return true
    },
  })
  return { ctx: ctx as unknown as CanvasRenderingContext2D, calls }
}
const img = {} as CanvasImageSource

describe('textos do card', () => {
  it('título por faixa e formatação dos números', () => {
    expect(cardHeadline(287)).toBe('Internet ótima.')
    expect(cardHeadline(30)).toBe('Internet boa.')
    expect(cardHeadline(10)).toBe('Internet no básico.')
    expect(cardHeadline(2)).toBe('Internet lenta.')
    expect(cardNumber(287.4)).toBe('287')
    expect(cardNumber(94.2)).toBe('94,2')
  })

  it('o anel tem 72 marcas e acende em proporção ao download', () => {
    const lit = (d: number) => cardTicks(d).filter((k) => k.color === '#83DA99').length
    expect(cardTicks(287)).toHaveLength(72)
    expect(lit(500)).toBeGreaterThan(lit(100))
    expect(ringLines(287).match(/<line /g)).toHaveLength(72)
  })

  it('o QR existe e começa com um módulo', () => {
    const qr = qrData()
    expect(qr.size).toBeGreaterThan(20)
    expect(qr.d.startsWith('M')).toBe(true)
  })
})

describe('card vertical 1080x1920', () => {
  beforeAll(() => {
    vi.stubGlobal('Path2D', class { constructor(public d: string) {} })
  })

  it('é determinístico: mesmos dados, mesmos comandos', () => {
    const a = recorder()
    const b = recorder()
    drawStoryCard(a.ctx, P, { lockup: img, badge: img })
    drawStoryCard(b.ctx, P, { lockup: img, badge: img })
    expect(JSON.stringify(a.calls)).toBe(JSON.stringify(b.calls))
  })

  it('desenha os números do teste', () => {
    const { ctx, calls } = recorder()
    drawStoryCard(ctx, P, { lockup: img, badge: img })
    const texts = calls.filter((c) => c.op === 'fillText').map((c) => c.args[0])
    expect(texts).toEqual(expect.arrayContaining(['287', '94,2', '12', 'Internet ótima.', 'DOWNLOAD', 'Mbps']))
  })

  it('mantém textos e selo dentro das zonas seguras', () => {
    const { ctx, calls } = recorder()
    drawStoryCard(ctx, P, { lockup: img, badge: img })
    for (const c of calls.filter((c) => c.op === 'fillText')) {
      const y = c.args[2] as number
      expect(y).toBeGreaterThan(SAFE.top)
      expect(y).toBeLessThan(STORY_H - SAFE.bottom)
    }
    for (const c of calls.filter((c) => c.op === 'drawImage')) {
      const [, x, y, w, h] = c.args as number[]
      expect(x).toBeGreaterThanOrEqual(SAFE.side)
      expect(x + w).toBeLessThanOrEqual(STORY_W - SAFE.side)
      expect(y).toBeGreaterThanOrEqual(SAFE.top)
      expect(y + h).toBeLessThanOrEqual(STORY_H - SAFE.bottom)
    }
  })
})
