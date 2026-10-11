import { NextRequest } from 'next/server'
import { describe, expect, it } from 'vitest'
import { middleware } from './middleware'

describe('middleware', () => {
  it('rewrites speedtest.signallq.com/ to /teste-de-velocidade, keeping the host', () => {
    const req = new NextRequest('https://speedtest.signallq.com/')
    const res = middleware(req)
    expect(res.headers.get('x-middleware-rewrite')).toBe('https://speedtest.signallq.com/teste-de-velocidade')
  })

  it('redirects unknown paths on signallq.pages.dev to /', () => {
    const req = new NextRequest('https://signallq.pages.dev/rota-que-nao-existe')
    const res = middleware(req)
    expect(res.status).toBe(308)
    expect(res.headers.get('location')).toBe('https://signallq.com/')
  })

  it('preserves the new public routes on signallq.pages.dev, including guia subpaths', () => {
    for (const path of ['/como-funciona', '/duvidas', '/teste-de-velocidade', '/guias', '/guias/por-que-minha-internet-esta-lenta']) {
      const req = new NextRequest(`https://signallq.pages.dev${path}`)
      const res = middleware(req)
      expect(res.headers.get('location')).toBe(`https://signallq.com${path}`)
    }
  })

  it('redirects historical routes on signallq.pages.dev directly to their canonical destinations', () => {
    const cases = [
      { from: '/ping', to: '/teste-de-ping' },
      { from: '/lag-em-jogos-online', to: '/modo-gamer' },
      { from: '/internet-para-jogos', to: '/modo-gamer' },
    ]
    for (const { from, to } of cases) {
      const req = new NextRequest(`https://signallq.pages.dev${from}`)
      const res = middleware(req)
      expect(res.status).toBe(308)
      expect(res.headers.get('location')).toBe(`https://signallq.com${to}`)
    }
  })

  it('does not touch requests on the canonical host', () => {
    const req = new NextRequest('https://signallq.com/guias')
    const res = middleware(req)
    expect(res.headers.get('location')).toBeNull()
    expect(res.headers.get('x-middleware-rewrite')).toBeNull()
  })
})
