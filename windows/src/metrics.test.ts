import { describe, expect, it } from 'vitest'
import { limitationTitle, metric, qualitySummary } from './metrics'
import type { Measurement } from './native'

const complete: Measurement = { executionId: 'x', capturedAtMs: 1, status: 'COMPLETED', downloadMbps: 100, uploadMbps: 50, latencyMs: 10, jitterMs: 3, packetLossPercent: 0, packetLossSource: 'estimated_http_probe', bufferbloatMs: 5, limitation: null }

describe('presentation of measured values', () => {
  it('never turns unavailable values into zero', () => expect(metric(null, 'ms')).toBe('Indisponível'))
  it('does not offer a conclusion for partial data', () => expect(qualitySummary({ ...complete, status: 'PARTIAL' })).toContain('parcial'))
  it('keeps instability as measured evidence, not a cause', () => expect(qualitySummary({ ...complete, jitterMs: 31 })).toContain('instabilidade medida'))
  it('does not call a completed result partial merely because one metric is unavailable', () => {
    expect(limitationTitle('COMPLETED')).toBe('Limitação da medição')
    expect(limitationTitle('PARTIAL')).toBe('Resultado parcial')
  })
})
