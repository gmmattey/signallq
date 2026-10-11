import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import ModoGamerPage from './page'

describe('modo-gamer (página comercial)', () => {
  it('renders commercial Modo Gamer page with headline, benefits, android screenshot and link to web test', () => {
    const markup = renderToStaticMarkup(<ModoGamerPage />)
    expect(markup).toContain('Seu jogo trava mesmo com internet rápida?')
    expect(markup).toContain('Modo Jogos · Exclusivo no app SignallQ')
    expect(markup).toContain('Analise sua conexão para jogar')
    expect(markup).toContain('Entenda o resultado')
    expect(markup).toContain('Saiba o que melhorar')
    expect(markup).toContain('Baixar SignallQ grátis')
    expect(markup).toContain('Testar conexão no navegador')
    expect(markup).toContain('/teste-gamer')
    expect(markup).toContain('Exemplo de análise do SignallQ Android')
    expect(markup).toContain('Perguntas frequentes sobre o Modo Jogos')

    // Garantir que a ferramenta executora e seletores fictícios foram retirados
    expect(markup).not.toContain('Iniciar teste de conexão')
  })
})
