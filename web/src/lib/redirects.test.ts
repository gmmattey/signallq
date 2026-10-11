import { describe, expect, it } from 'vitest'
import nextConfig from '../../next.config'
import { PAGE_META } from './pageMetaCatalog'

describe('redirects no next.config.ts', () => {
  it('define redirecionamentos permanentes para URLs historicas', async () => {
    expect(nextConfig.redirects).toBeDefined()
    const redirects = await nextConfig.redirects!()

    const legacyRedirects = redirects.filter((r) => !('has' in r && r.has))

    expect(legacyRedirects).toEqual([
      {
        source: '/ping',
        destination: '/teste-de-ping',
        permanent: true,
      },
      {
        source: '/lag-em-jogos-online',
        destination: '/modo-gamer',
        permanent: true,
      },
      {
        source: '/internet-para-jogos',
        destination: '/modo-gamer',
        permanent: true,
      },
    ])

    // Garante que todo destino existe no catálogo de rotas canônicas e não gera 404
    for (const r of legacyRedirects) {
      expect(PAGE_META[r.destination]).toBeDefined()
      expect(r.destination.startsWith('/')).toBe(true)
    }
  })
})
