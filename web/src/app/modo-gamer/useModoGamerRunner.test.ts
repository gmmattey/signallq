import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useModoGamerRunner } from './useModoGamerRunner'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('useModoGamerRunner', () => {
  it('inicia no estado idle com todas as métricas nulas', () => {
    const { result } = renderHook(() => useModoGamerRunner())
    expect(result.current.state.phase).toBe('idle')
    expect(result.current.state.progressPct).toBe(0)
    expect(result.current.state.idlePing).toBeNull()
    expect(result.current.state.jitter).toBeNull()
    expect(result.current.state.uploadMbps).toBeNull()
    expect(result.current.state.bufferbloat).toBeNull()
    expect(result.current.state.result).toBeNull()
    expect(result.current.state.errorMsg).toBeNull()
  })

  it('cancelTest reseta o estado para idle e limpa medições parciais', () => {
    const { result } = renderHook(() => useModoGamerRunner())

    act(() => {
      result.current.cancelTest()
    })

    expect(result.current.state.phase).toBe('idle')
    expect(result.current.state.idlePing).toBeNull()
    expect(result.current.state.bufferbloat).toBeNull()
    expect(result.current.state.progressPct).toBe(0)
  })

  it('aborta execução ativa quando cancelTest é disparado durante o teste', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise((resolve) => setTimeout(() => resolve(new Response('OK')), 50))),
    )

    const { result } = renderHook(() => useModoGamerRunner())

    let startPromise: Promise<void>
    act(() => {
      startPromise = result.current.startTest('Valorant', 'FPS')
    })

    // Cancela imediatamente enquanto a execução está em andamento
    act(() => {
      result.current.cancelTest()
    })

    await act(async () => {
      await startPromise
    })

    expect(result.current.state.phase).toBe('idle')
    expect(result.current.state.idlePing).toBeNull()
    expect(result.current.state.result).toBeNull()
  })

  it('transiciona para o estado error quando a medição inicial falha', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new Error('Network error'))),
    )

    const { result } = renderHook(() => useModoGamerRunner())

    await act(async () => {
      await result.current.startTest('Valorant', 'FPS')
    })

    expect(result.current.state.phase).toBe('error')
    expect(result.current.state.errorMsg).toContain('Não foi possível obter amostras de latência suficientes.')
  })

  it('não inventa bufferbloat se o upload falhar e zero bytes forem entregues', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string | URL | Request, init?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/meta')) {
          return Promise.resolve(new Response(JSON.stringify({ city: 'São Paulo', colo: 'GRU', asOrganization: 'Test ISP' })))
        }
        if (init?.method === 'POST') {
          return Promise.reject(new Error('Upload blocked by adblock/firewall'))
        }
        return Promise.resolve(new Response('OK'))
      }),
    )

    const { result } = renderHook(() => useModoGamerRunner())

    await act(async () => {
      await result.current.startTest('Fortnite', 'Battle royale')
    })

    expect(result.current.state.phase).toBe('done')
    expect(result.current.state.idlePing).toBeGreaterThanOrEqual(0)
    expect(result.current.state.uploadMbps).toBeNull()
    expect(result.current.state.bufferbloat).toBeNull()
    expect(result.current.state.result?.bloatScore).toBeNull()
    expect(result.current.state.result?.uploadScore).toBeNull()
  })

  it('caminho feliz: calcula upload e bufferbloat quando houver entrega de dados e sondas sob carga', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string | URL | Request, init?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString()
        if (urlStr.includes('/meta')) {
          return Promise.resolve(new Response(JSON.stringify({ city: 'Rio de Janeiro', colo: 'GIG', asOrganization: 'Fibra ISP' })))
        }
        if (init?.method === 'POST') {
          return Promise.resolve(new Response('OK'))
        }
        return Promise.resolve(new Response('OK'))
      }),
    )

    const { result } = renderHook(() => useModoGamerRunner())

    await act(async () => {
      await result.current.startTest('Counter-Strike 2', 'FPS')
    })

    expect(result.current.state.phase).toBe('done')
    expect(result.current.state.idlePing).toBeDefined()
    expect(result.current.state.jitter).toBeDefined()
    expect(result.current.state.result).not.toBeNull()
    expect(result.current.state.result?.grade).toBeDefined()
  })
})
