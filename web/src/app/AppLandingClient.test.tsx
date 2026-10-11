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
    expect(screen.getByAltText(/Tela Início do SignallQ mostrando o diagnóstico Conexão excelente/)).toBeInTheDocument()
  })

  it('segue a nova hierarquia comercial definida', () => {
    render(<AppLandingClient />)
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    expect(headings).toEqual([
      'Diagnóstico compreensível para problemas do dia a dia.',
      'Ferramentas gratuitas que funcionam no navegador.',
      'Quatro perguntas. Uma resposta de cada vez.',
      'Primeiro a conclusão. Os números vêm depois.',
      'O que só o app faz.',
      'Outras soluções SignallQ',
      'Guias práticos para melhorar sua conexão.',
      'Dúvidas comuns',
      'Sem achismo. Sem jargão.',
      'Sua internet está ruim agora? Descubra em minutos.',
    ])
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

  it('dispara telemetria de visualização da home e de clique no download', async () => {
    const trackSpy = vi.spyOn(telemetry, 'trackFeatureUsed')
    const screenSpy = vi.spyOn(telemetry, 'trackScreenView')
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    render(<AppLandingClient />)

    expect(screenSpy).toHaveBeenCalledWith('home')

    const [hero] = screen.getAllByRole('link', { name: 'Baixar grátis no Google Play' })
    await user.click(hero)
    expect(trackSpy).toHaveBeenCalledWith('download_app_clicado')
  })

  it('exibe a seção de outras soluções com Lagcheck, Linka e WiFi Casa', () => {
    render(<AppLandingClient />)
    expect(screen.getByRole('heading', { level: 2, name: 'Outras soluções SignallQ' })).toBeInTheDocument()
    expect(screen.getByText('Lagcheck')).toBeInTheDocument()
    expect(screen.getByText('Linka')).toBeInTheDocument()
    expect(screen.getByText('WiFi Casa')).toBeInTheDocument()
  })
})

