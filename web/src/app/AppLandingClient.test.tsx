import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLandingClient } from './AppLandingClient'
import * as telemetry from '@/lib/telemetry'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('página / — AppLandingClient (home, 1:1 com o protótipo)', () => {
  beforeEach(() => {
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  it('mostra o título do hero, o texto de apoio e a captura real do app', () => {
    render(<AppLandingClient />)
    expect(screen.getByRole('heading', { level: 1, name: 'Descubra por que sua internet está ruim.' })).toBeInTheDocument()
    expect(screen.getByText('Diagnóstico de internet para Android')).toBeInTheDocument()
    expect(screen.getByText('Grátis · Sem cadastro para começar')).toBeInTheDocument()
    expect(screen.getByAltText(/Tela Início do SignallQ mostrando o diagnóstico Conexão excelente/)).toBeInTheDocument()
  })

  it('segue a ordem das seções do protótipo', () => {
    render(<AppLandingClient />)
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual([
      'O que só o app faz.',
      'Procurando algo específico? Teste agora.',
      'Quatro perguntas. Uma resposta de cada vez.',
      'Primeiro a conclusão. Os números vêm depois.',
      'Sem achismo. Sem jargão.',
      'Dúvidas comuns',
      'Sua internet está ruim agora? Descubra em minutos.',
    ])
    // não há mais "Diferenciais" nem galeria de capturas
    expect(screen.queryByText('Diferenciais')).toBeNull()
    expect(document.querySelectorAll('figure')).toHaveLength(0)
  })

  it('lista as 4 perguntas do FAQ e o link para o teste de velocidade', () => {
    render(<AppLandingClient />)
    expect(document.querySelectorAll('details')).toHaveLength(4)
    expect(screen.getByRole('link', { name: 'Fazer teste de velocidade' })).toHaveAttribute('href', '/teste-de-velocidade')
  })

  it('tem o CTA de download no hero e na faixa final', () => {
    render(<AppLandingClient />)
    expect(screen.getAllByRole('button', { name: 'Baixar no Google Play' })).toHaveLength(2)
  })

  it('dispara telemetria de download ao clicar no CTA e abre a Play Store', async () => {
    const trackSpy = vi.spyOn(telemetry, 'trackFeatureUsed')
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(<AppLandingClient />)

    const [hero] = screen.getAllByRole('button', { name: 'Baixar no Google Play' })
    await user.click(hero)
    expect(trackSpy).toHaveBeenCalledWith('download_app_clicado')
    expect(window.open).toHaveBeenCalledWith(
      expect.stringContaining('play.google.com/store/apps/details?id=io.signallq.app'),
      '_blank',
      'noopener,noreferrer',
    )
  })
})
