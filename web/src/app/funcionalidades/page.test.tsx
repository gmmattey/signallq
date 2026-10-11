import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import FuncionalidadesPage from './page'

describe('funcionalidades', () => {
  it('renders web tools, app features and header texts', () => {
    const markup = renderToStaticMarkup(<FuncionalidadesPage />)
    expect(markup).toContain('Tudo o que o SignallQ analisa na sua conexão.')
    expect(markup).toContain('Teste agora, no navegador')
    expect(markup).toContain('Recursos do app')
    expect(markup).toContain('Modo Jogos')
    expect(markup).toContain('Canais do Wi-Fi')
    expect(markup).toContain('Teste de ping')
  })
})
