import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { GameShareRow, gameShareLinks } from './GameShareRow'

const P = {
  game: 'fortnite',
  verdict: 'ok' as const,
  ping: 18,
  jitter: 3,
  bloat: 4,
}
const SIGNED = 'https://signallq.com/r/jogo/?g=fortnite&v=ok&p=18&j=3&b=4&t=1791800000&s=abc'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('compartilhamento teste gamer', () => {
  it('redes sociais montam os links corretos com TikTok', () => {
    const links = gameShareLinks(P, SIGNED)
    const wa = links.find((l) => l.name === 'WhatsApp')!
    const tg = links.find((l) => l.name === 'Telegram')!
    const x = links.find((l) => l.name === 'X')!
    const tiktok = links.find((l) => l.name.startsWith('TikTok'))!

    expect(wa.href).toContain('https://wa.me/?text=')
    expect(decodeURIComponent(wa.href)).toContain(SIGNED)
    expect(tg.href).toContain(`url=${encodeURIComponent(SIGNED)}`)
    expect(x.href).toContain('https://twitter.com/intent/tweet?text=')
    expect(tiktok.copy).toContain(SIGNED)
  })

  it('copia link do jogo via clipboard', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ ok: true, url: SIGNED }))))
    const writeText = vi.fn(async () => undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    render(<GameShareRow payload={P} />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copiar link' })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Copiar link' }))
    expect(writeText).toHaveBeenCalledWith(SIGNED)
    expect(await screen.findByText('Link copiado.')).toBeInTheDocument()
  })
})
