import { describe, expect, it } from 'vitest'
import { measurementStatus, measurementSummary, metric } from './metrics'
import type { Measurement } from './native'

const complete: Measurement = { schemaVersion: 2, executionId: 'x', startedAtMs: 1, capturedAtMs: 2, status: 'COMPLETE', downloadMbps: 100, uploadMbps: 50, latencyMs: 10, jitterMs: 3, packetLossPercent: null, packetLossSource: null, bufferbloatMs: null, limitation: null, phases: [] }

describe('presentation of measured values', () => {
  it('never turns unavailable values into zero', () => expect(metric(null, 'ms')).toBe('Indisponível'))
  it('distinguishes partial, inconclusive and cancelled measurements', () => {
    expect(measurementSummary({ ...complete, status: 'PARTIAL' })).toContain('parcial')
    expect(measurementSummary({ ...complete, status: 'INCONCLUSIVE' })).toContain('inconclusiva')
    expect(measurementSummary({ ...complete, status: 'CANCELLED' })).toContain('cancelada')
  })
  it('does not infer quality or root cause from measurements', () => {
    expect(measurementSummary({ ...complete, jitterMs: 40 })).toBe('Medição concluída. Confira os valores coletados.')
  })
  it('labels all canonical states', () => {
    expect(measurementStatus('COMPLETE')).toBe('Concluída')
    expect(measurementStatus('CONTAMINATED')).toBe('Contaminada')
  })
})
