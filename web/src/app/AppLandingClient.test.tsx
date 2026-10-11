import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppLandingClient } from './AppLandingClient'
import * as telemetry from '@/lib/telemetry'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('página / — AppLandingClient (home com hierarquia comercial e ecossistema)', () => {
  beforeEach(() => {
    vi.spyOn(window, 'open').mockImplementation(() => null)
  })

  it('mostra o título do hero, o texto de apoio e a captura real do app', () => {
    render(<AppLandingClient />)
    expect(screen.getByRole('heading', { level: 1, name: 'Descubra por que sua internet está ruim.' })).toBeInTheDocument()
    expect(screen.getByText('Diagnóstico de internet para Android')).toBeInTheDocument()
    expect(screen.getByText('Grátis · Sem cadastro para começar')).toBeInTheDocument()
    expect(screen.getByText(/O SignallQ analisa sua rede, mostra onde está o problema e diz o que fazer\. Depois, ajuda você a verificar se resolveu\./)).toBeInTheDocument()
    expect(screen.getByAltText(/Tela Início do aplicativo SignallQ para Android exibindo o diagnóstico Conexão estável/)).toBeInTheDocument()
  })

  it('preserva apenas um H1 na página inteira e usa níveis semânticos apropriados nos slides', () => {
    render(<AppLandingClient />)
    const h1Headings = screen.getAllByRole('heading', { level: 1 })
    expect(h1Headings).toHaveLength(1)
    expect(h1Headings[0]).toHaveTextContent('Descubra por que sua internet está ruim.')
  })

  it('segue a nova hierarquia comercial definida de 8 blocos', () => {
    render(<AppLandingClient />)
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual([
      'Ferramentas gratuitas que funcionam no navegador.',
      'Quatro perguntas. Uma resposta de cada vez.',
      'Primeiro a conclusão. Os números vêm depois.',
      'O que só o app faz.',
      'Acompanhamento contínuo da sua rede.',
      'Outras soluções SignallQ',
      'Guias práticos para melhorar sua conexão.',
      'Dúvidas comuns',
      'Sem achismo. Sem jargão.',
      'Sua internet está ruim agora? Descubra em minutos.',
    ])
  })

  it('destaca SignallQ Gamer tanto no carrossel do hero quanto na grade de recursos', () => {
    render(<AppLandingClient />)
    const gamerElements = screen.getAllByText('SignallQ Gamer')
    // 1 na vitrine do carrossel e 1 no catálogo de recursos exclusivos
    expect(gamerElements).toHaveLength(2)
    expect(screen.getByRole('link', { name: /SignallQ Gamer/i })).toHaveAttribute('href', '/modo-gamer')
  })

  it('lista as 4 perguntas do FAQ e o link para testar minha internet', () => {
    render(<AppLandingClient />)
    expect(document.querySelectorAll('details')).toHaveLength(4)
    expect(screen.getByRole('link', { name: 'Testar minha internet' })).toHaveAttribute('href', '/teste-de-velocidade')
  })

  it('tem o CTA semântico de download no hero e na faixa final', () => {
    render(<AppLandingClient />)
    const downloadLinks = screen.getAllByRole('link', { name: 'Baixar grátis no Google Play' })
    expect(downloadLinks).toHaveLength(2)
    for (const link of downloadLinks) {
      expect(link).toHaveAttribute('href', expect.stringContaining('play.google.com/store/apps/details?id=io.signallq.app'))
      expect(link).toHaveAttribute('target', '_blank')
    }
  })

  it('dispara telemetria de visualização da home e de clique no download e no teste', async () => {
    const trackSpy = vi.spyOn(telemetry, 'trackFeatureUsed')
    const screenSpy = vi.spyOn(telemetry, 'trackScreenView')
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(<AppLandingClient />)

    expect(screenSpy).toHaveBeenCalledWith('home')

    const [hero] = screen.getAllByRole('link', { name: 'Baixar grátis no Google Play' })
    await user.click(hero)
    expect(trackSpy).toHaveBeenCalledWith('download_app_clicado')

    const testLink = screen.getByRole('link', { name: 'Testar minha internet' })
    await user.click(testLink)
    expect(trackSpy).toHaveBeenCalledWith('hero_testar_internet_clicado')
  })

  it('exibe a seção de outras soluções com Lagcheck, Linka e WiFi Casa', () => {
    render(<AppLandingClient />)
    expect(screen.getByRole('heading', { level: 2, name: 'Outras soluções SignallQ' })).toBeInTheDocument()
    expect(screen.getByText('Lagcheck')).toBeInTheDocument()
    expect(screen.getByText('Linka')).toBeInTheDocument()
    expect(screen.getAllByText('WiFi Casa').length).toBeGreaterThanOrEqual(1)
  })
})

