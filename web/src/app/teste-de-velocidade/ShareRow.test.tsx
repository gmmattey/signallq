import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ShareRow, shareLinks, shareMessage } from './ShareRow'

const F = { download: 287.4, upload: 94.2, ping: 12, jitter: 3 }
const SIGNED = 'https://signallq.com/r/?d=287.4&u=94.2&p=12&j=3&t=1791800000&s=abc'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('compartilhamento speedtest', () => {
  it('a mensagem traz os números medidos', () => {
    expect(shareMessage(F)).toBe('Minha internet: 287 Mbps de download, 94 Mbps de upload, ping 12 ms. Teste a sua no SignallQ:')
  })

  it('redes sociais montam os links corretos', () => {
    const links = shareLinks(F, SIGNED)
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

  it('copia link via clipboard', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ ok: true, url: SIGNED }))))
    const writeText = vi.fn(async () => undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    render(<ShareRow metrics={F} />)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copiar link' })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Copiar link' }))
    expect(writeText).toHaveBeenCalledWith(SIGNED)
    expect(await screen.findByText('Link copiado.')).toBeInTheDocument()
  })
})
