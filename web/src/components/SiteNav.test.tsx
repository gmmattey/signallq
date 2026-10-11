import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SiteNav } from './SiteNav'

const { usePathnameMock } = vi.hoisted(() => ({ usePathnameMock: vi.fn(() => '/guias') }))
vi.mock('next/navigation', () => ({ usePathname: usePathnameMock }))

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SiteNav', () => {
  it('linka o logo para a home e o botão "Baixar o app" para a seção de download', () => {
    render(<SiteNav />)
    expect(screen.getByRole('link', { name: 'Página inicial SignallQ' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Baixar o app' })).toHaveAttribute('href', '/#baixar')
  })

  it('mostra os cinco links nas páginas internas e marca a rota atual (aria-current)', () => {
    usePathnameMock.mockReturnValue('/guias/por-que-minha-internet-esta-lenta')
    render(<SiteNav />)
    for (const label of ['Teste de velocidade', 'Funcionalidades', 'Guias', 'Como funciona', 'Dúvidas']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(screen.queryByRole('link', { name: 'O resultado' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Guias' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Dúvidas' })).not.toHaveAttribute('aria-current')
  })

  it('na home usa a ordem do protótipo e inclui "O resultado"', () => {
    usePathnameMock.mockReturnValue('/')
    render(<SiteNav />)
    const labels = screen.getAllByRole('link').map((l) => l.textContent)
    expect(labels).toEqual(['SignallQ', 'Teste de velocidade', 'Funcionalidades', 'Como funciona', 'Guias', 'O resultado', 'Dúvidas', 'Baixar o app'])
  })

  it('permite abrir e fechar o menu mobile acessível', async () => {
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    usePathnameMock.mockReturnValue('/')
    render(<SiteNav />)

    const toggleBtn = screen.getByRole('button', { name: 'Abrir menu de navegação' })
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'false')

    await user.click(toggleBtn)
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('navigation', { name: 'Navegação mobile' })).toBeInTheDocument()

    // Fecha com Escape
    await user.keyboard('{Escape}')
    expect(toggleBtn).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('navigation', { name: 'Navegação mobile' })).toBeNull()
  })
})

