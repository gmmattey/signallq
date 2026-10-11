import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import ModoGamerPage from './page'

describe('modo-gamer', () => {
  it('renders Modo Gamer page with title, catalog, devices and FAQ', () => {
    const markup = renderToStaticMarkup(<ModoGamerPage />)
    expect(markup).toContain('Sua internet está pronta para o ranked?')
    expect(markup).toContain('Modo Jogos · no app SignallQ')
    expect(markup).toContain('Comece pelo jogo que você vai abrir.')
    expect(markup).toContain('Fortnite')
    expect(markup).toContain('PlayStation')
    expect(markup).toContain('Rota de referência em servidores da AWS')
    expect(markup).toContain('Veja a estabilidade da sua conexão agora.')
  })
})
