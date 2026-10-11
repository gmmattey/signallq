import { cleanup, render, screen, fireEvent, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TesteGamerPage from './page'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

// Mock de telemetria
vi.mock('@/lib/telemetry', () => ({
  trackFeatureUsed: vi.fn(),
  FEATURE_TESTE_GAMER_JOGO_SELECIONADO: 'teste_gamer_jogo_selecionado',
  FEATURE_TESTE_GAMER_INICIADO: 'teste_gamer_iniciado',
  FEATURE_TESTE_GAMER_CONCLUIDO: 'teste_gamer_concluido',
  FEATURE_TESTE_GAMER_ERRO: 'teste_gamer_erro',
  FEATURE_DOWNLOAD_APP_CLICADO: 'download_app_clicado',
}))

describe('teste-gamer (página executora)', () => {
  it('inicia na etapa 1 sem jogo pré-selecionado (não seleciona Fortnite por padrão)', () => {
    render(<TesteGamerPage />)

    // Título e descrição da etapa 1
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Teste sua conexão para jogar')
    expect(screen.getByText('Escolha seu jogo e descubra como está a estabilidade da sua conexão.')).toBeInTheDocument()

    // Campo de busca e sugestões populares
    expect(screen.getByPlaceholderText(/Buscar jogo/i)).toBeInTheDocument()
    expect(screen.getByText('Valorant')).toBeInTheDocument()
    expect(screen.getByText('Counter-Strike 2')).toBeInTheDocument()

    // Botão de início está desabilitado sem seleção
    const startBtn = screen.getByRole('button', { name: /Escolha um jogo acima para iniciar/i })
    expect(startBtn).toBeDisabled()
    expect(screen.queryByText(/Jogo selecionado/i)).not.toBeInTheDocument()
  })

  it('permite selecionar um jogo popular e habilita o início do teste', () => {
    render(<TesteGamerPage />)

    const valorantBtn = screen.getByRole('button', { name: 'Valorant' })
    fireEvent.click(valorantBtn)

    // Deve exibir banner do jogo selecionado
    expect(screen.getByText('Jogo selecionado')).toBeInTheDocument()
    expect(screen.getByText('Valorant')).toBeInTheDocument()
    expect(screen.getByText(/Régua de sensibilidade ajustada para a categoria/i)).toBeInTheDocument()

    // Botão de iniciar fica habilitado com nome do jogo
    const startBtn = screen.getByRole('button', { name: /Iniciar teste para Valorant/i })
    expect(startBtn).not.toBeDisabled()
  })

  it('permite buscar jogos no catálogo e filtrar sugestões', () => {
    render(<TesteGamerPage />)

    const searchInput = screen.getByPlaceholderText(/Buscar jogo/i)
    fireEvent.change(searchInput, { target: { value: 'League' } })

    expect(screen.getAllByText('League of Legends').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('MOBA')).toBeInTheDocument()

    // Seleciona League of Legends
    fireEvent.click(screen.getAllByText('League of Legends')[0])
    expect(screen.getByRole('button', { name: /Iniciar teste para League of Legends/i })).not.toBeDisabled()
  })

  it('oferece fallback por categoria quando o jogo não está na lista ou não é encontrado na busca', () => {
    render(<TesteGamerPage />)

    // Abre modo customizado
    const customToggleBtn = screen.getByRole('button', { name: /Meu jogo não está na lista/i })
    fireEvent.click(customToggleBtn)

    expect(screen.getByText('FPS / Tiro')).toBeInTheDocument()
    expect(screen.getByText('Battle Royale')).toBeInTheDocument()

    // Seleciona categoria FPS
    fireEvent.click(screen.getByRole('button', { name: /FPS \/ Tiro/i }))

    expect(screen.getByText('Jogo (FPS)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Iniciar teste para Jogo \(FPS\)/i })).not.toBeDisabled()
  })

  it('permite trocar de jogo e resetar a seleção antes de iniciar', () => {
    render(<TesteGamerPage />)

    fireEvent.click(screen.getByRole('button', { name: 'Valorant' }))
    expect(screen.getByText('Valorant')).toBeInTheDocument()

    const trocarBtn = screen.getByRole('button', { name: 'Trocar jogo' })
    fireEvent.click(trocarBtn)

    expect(screen.queryByText(/Jogo selecionado/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Escolha um jogo acima para iniciar/i })).toBeDisabled()
  })

  it('declara de forma honesta que o teste web não mede servidor oficial de jogos', () => {
    render(<TesteGamerPage />)
    expect(
      screen.getByText(/Não mede o servidor oficial do jogo nem perda física UDP/i),
    ).toBeInTheDocument()
  })

  it('exibe a etapa 2 de medição e permite cancelar o teste em andamento', async () => {
    // Fetch pendente que nunca resolve
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    render(<TesteGamerPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Valorant' }))

    const startBtn = screen.getByRole('button', { name: /Iniciar teste para Valorant/i })
    fireEvent.click(startBtn)

    // Deve estar na etapa de execução
    expect(screen.getByText(/Testando para/i)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cancelar teste/i })).toBeInTheDocument()

    // Clica em cancelar teste
    fireEvent.click(screen.getByRole('button', { name: /Cancelar teste/i }))

    // Retorna para a etapa 1 de seleção
    expect(screen.getByText('Teste sua conexão para jogar')).toBeInTheDocument()
  })

  it('exibe tela de erro amigável se a conexão falhar e permite tentar novamente', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('Conexão recusada'))))

    render(<TesteGamerPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Valorant' }))
    fireEvent.click(screen.getByRole('button', { name: /Iniciar teste para Valorant/i }))

    await waitFor(() => {
      expect(screen.getByText('Não foi possível concluir a medição')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Escolher outro jogo' })).toBeInTheDocument()
  })

  it('após medição concluída, exibe veredito em linguagem comum, hipótese fundamentada, detalhes expansíveis e CTA do app', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string | URL | Request, init?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/meta')) {
          return Promise.resolve(new Response(JSON.stringify({ city: 'São Paulo', colo: 'GRU', asOrganization: 'Fibra ISP' })))
        }
        if (init?.method === 'POST') {
          return Promise.resolve(new Response('OK'))
        }
        return Promise.resolve(new Response('OK'))
      }),
    )

    render(<TesteGamerPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Valorant' }))
    fireEvent.click(screen.getByRole('button', { name: /Iniciar teste para Valorant/i }))

    // Aguarda conclusão do teste
    await waitFor(
      () => {
        expect(
          screen.getByRole('heading', { level: 2, name: /Sua conexão parece adequada para jogar/i }),
        ).toBeInTheDocument()
      },
      { timeout: 8000 },
    )

    // Hierarquia visual do resultado:
    // 1. Veredito principal
    expect(
      screen.getByRole('heading', { level: 2, name: /Sua conexão parece adequada para jogar/i }),
    ).toBeInTheDocument()

    // 2. Principal indicador observado
    expect(screen.getByText('Principal indicador observado')).toBeInTheDocument()

    // 3. O que pode estar afetando sua conexão (hipótese)
    expect(screen.getByText('O que pode estar afetando sua conexão')).toBeInTheDocument()

    // 4. Recomendações práticas
    expect(screen.getByText(/Recomendações práticas para sua partida/i)).toBeInTheDocument()

    // 5. Métricas detalhadas expansíveis
    expect(screen.getByText(/Ver medições detalhadas da conexão/i)).toBeInTheDocument()
    expect(screen.getByText('Perda UDP')).toBeInTheDocument()
    expect(screen.getByText(/Exige app nativo/i)).toBeInTheDocument()

    // 6. Botões de refazer e escolher outro jogo
    expect(screen.getByRole('button', { name: 'Testar novamente' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Escolher outro jogo' })).toBeInTheDocument()

    // 7. CTA contextual do SignallQ Android
    expect(screen.getByText('Quer investigar melhor sua conexão?')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Baixar SignallQ grátis' })).toBeInTheDocument()
    expect(screen.getByText(/Conhecer o Modo Gamer completo no app/i)).toBeInTheDocument()
  }, 15000)
})
