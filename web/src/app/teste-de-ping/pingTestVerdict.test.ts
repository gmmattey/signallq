import { describe, expect, it } from 'vitest'
import {
  getPingVerdict,
  getJitterStatus,
  getBufferbloatStatus,
  getPingUsageLevels,
} from './pingTestVerdict'

describe('pingTestVerdict', () => {
  it('classifies ping thresholds correctly', () => {
    expect(getPingVerdict(null).status).toBe('Regular')
    expect(getPingVerdict(15).status).toBe('Ótimo')
    expect(getPingVerdict(45).status).toBe('Bom')
    expect(getPingVerdict(85).status).toBe('Regular')
    expect(getPingVerdict(120).status).toBe('Ruim')
  })

  it('classifies jitter correctly', () => {
    expect(getJitterStatus(null).label).toBe('—')
    expect(getJitterStatus(2).label).toBe('Excelente')
    expect(getJitterStatus(10).label).toBe('Bom')
    expect(getJitterStatus(20).label).toBe('Regular')
    expect(getJitterStatus(40).label).toBe('Instável')
  })

  it('classifies bufferbloat correctly', () => {
    expect(getBufferbloatStatus(null).label).toBe('—')
    expect(getBufferbloatStatus(4).label).toBe('Excelente')
    expect(getBufferbloatStatus(20).label).toBe('Bom')
    expect(getBufferbloatStatus(50).label).toBe('Moderado')
    expect(getBufferbloatStatus(120).label).toBe('Alto')
  })

  it('calculates usage levels accurately', () => {
    const excellent = getPingUsageLevels(15, 3)
    expect(excellent[0].s).toBe('Ótimo')
    expect(excellent[1].s).toBe('Ótimo')
    expect(excellent[2].s).toBe('Ótimo')

    const poor = getPingUsageLevels(180, 50)
    expect(poor[0].s).toBe('Ruim')
    expect(poor[1].s).toBe('Ruim')
    expect(poor[2].s).toBe('Bom')
  })
})
