import { describe, expect, it } from 'vitest'
import {
  bandOf,
  calculateJitterMAD,
  evaluateGameConnection,
  lerpScore,
  median,
} from './modoGamerEngine'

describe('modoGamerEngine', () => {
  describe('lerpScore', () => {
    const sampleStops: [number, number][] = [
      [0, 100],
      [20, 90],
      [100, 50],
      [200, 0],
    ]

    it('retorna a nota exata nos limites dos pontos de parada', () => {
      expect(lerpScore(0, sampleStops)).toBe(100)
      expect(lerpScore(20, sampleStops)).toBe(90)
      expect(lerpScore(200, sampleStops)).toBe(0)
    })

    it('interpola linearmente entre pontos intermediários', () => {
      expect(lerpScore(10, sampleStops)).toBe(95)
      expect(lerpScore(60, sampleStops)).toBe(70)
    })

    it('limita valores abaixo do mínimo e acima do máximo', () => {
      expect(lerpScore(-5, sampleStops)).toBe(0)
      expect(lerpScore(300, sampleStops)).toBe(0)
    })
  })

  describe('median & calculateJitterMAD', () => {
    it('calcula mediana para listas ímpares e pares', () => {
      expect(median([])).toBe(0)
      expect(median([10, 20, 30])).toBe(20)
      expect(median([10, 20, 30, 40])).toBe(25)
      expect(median([50, 10, 30])).toBe(30)
    })

    it('calcula o desvio absoluto mediano (MAD) com precisão', () => {
      const pings = [20, 21, 20, 22, 20, 19]
      const med = median(pings)
      const mad = calculateJitterMAD(pings, med)
      expect(mad).toBe(0.5)
    })

    it('retorna 0 para listas com 1 ou 0 elementos', () => {
      expect(calculateJitterMAD([], 0)).toBe(0)
      expect(calculateJitterMAD([25], 25)).toBe(0)
    })
  })

  describe('bandOf', () => {
    it('mapeia faixas exatas de pontuação para letras de grau', () => {
      expect(bandOf(98)).toBe('A+')
      expect(bandOf(95)).toBe('A+')
      expect(bandOf(94)).toBe('A')
      expect(bandOf(88)).toBe('A')
      expect(bandOf(87)).toBe('B')
      expect(bandOf(78)).toBe('B')
      expect(bandOf(77)).toBe('C')
      expect(bandOf(68)).toBe('C')
      expect(bandOf(67)).toBe('D')
      expect(bandOf(58)).toBe('D')
      expect(bandOf(57)).toBe('F')
      expect(bandOf(30)).toBe('F')
    })
  })

  describe('evaluateGameConnection', () => {
    it('aplica o princípio do elo mais fraco (a nota da cadeia é o mínimo, nunca a média)', () => {
      // Latência ociosa boa (15 ms), jitter bom (2 ms), mas bufferbloat desaba (+350 ms)
      const result = evaluateGameConnection({
        ping: 15,
        jit: 2,
        bloat: 350,
        upload: 15,
        lossPct: null,
        gameName: 'Valorant',
        genre: 'FPS',
      })

      expect(result.pingScore).toBe(95)
      expect(result.jitScore).toBe(96)
      expect(result.bloatScore).toBe(39)
      expect(result.overallScore).toBe(39)
      expect(result.grade).toBe('F')
      expect(result.weakestLink).toBe('bloat')
      expect(result.culprit).toBe('line_bufferbloat')
      expect(result.headline).toBe('Bufferbloat na linha sob carga (+350 ms)')
    })

    it('identifica oscilação de rota ou rede local quando o jitter é o gargalo', () => {
      const result = evaluateGameConnection({
        ping: 22,
        jit: 28,
        bloat: 20,
        upload: 25,
        lossPct: null,
        gameName: 'Counter-Strike 2',
        genre: 'FPS',
      })

      expect(result.culprit).toBe('home_wifi')
      expect(result.weakestLink).toBe('jitter')
      expect(result.headline).toBe('Oscilação de rota ou rede local (28 ms jitter)')
      expect(result.actions.some((a) => a.includes('cabo de rede'))).toBe(true)
    })

    it('identifica upload como gargalo quando a taxa de subida é insuficiente', () => {
      const result = evaluateGameConnection({
        ping: 20,
        jit: 3,
        bloat: 25,
        upload: 0.8,
        lossPct: null,
        gameName: 'League of Legends',
        genre: 'MOBA',
      })

      expect(result.culprit).toBe('line_upload')
      expect(result.weakestLink).toBe('upload')
      expect(result.headline).toBe('Vazão de upload no limite para partidas em grupo (0.8 Mbps)')
    })

    it('identifica rota do provedor quando o ping base é muito alto', () => {
      const result = evaluateGameConnection({
        ping: 180,
        jit: 4,
        bloat: 30,
        upload: 25,
        lossPct: null,
        gameName: 'Free Fire',
        genre: 'Battle royale',
      })

      expect(result.culprit).toBe('isp_route')
      expect(result.weakestLink).toBe('ping')
      expect(result.headline).toBe('Latência alta na rota do provedor (180 ms)')
    })

    it('reconhece conexão íntegra e declara estimativa sem perda UDP com dados parciais', () => {
      const result = evaluateGameConnection({
        ping: 18,
        jit: 2,
        bloat: 15,
        upload: 30,
        lossPct: null,
        gameName: 'EA Sports FC',
        genre: 'Esportes',
      })

      expect(result.culprit).toBe('none')
      expect(result.overallScore).toBe(91)
      expect(result.grade).toBe('A')
      expect(result.isPartial).toBe(true)
      expect(result.headline).toBe('Conexão consistente para EA Sports FC (estimativa sem perda UDP)')
      expect(result.detail).toContain('a perda de pacotes UDP não é inspecionável diretamente via navegador')
    })

    it('calibra os limiares conforme o gênero (FPS é mais rigoroso que Casual para o mesmo ping)', () => {
      const fpsRes = evaluateGameConnection({
        ping: 60,
        jit: 12,
        bloat: 40,
        upload: 20,
        lossPct: null,
        gameName: 'Valorant',
        genre: 'FPS',
      })

      const casualRes = evaluateGameConnection({
        ping: 60,
        jit: 12,
        bloat: 40,
        upload: 20,
        lossPct: null,
        gameName: 'Minecraft',
        genre: 'Casual',
      })

      expect(fpsRes.pingScore).toBe(74)
      expect(casualRes.pingScore).toBe(90)
      expect(fpsRes.pingScore).toBeLessThan(casualRes.pingScore)
      expect(fpsRes.jitScore).toBeLessThan(casualRes.jitScore)
    })

    it('declara de forma transparente as limitações de teste de UDP no navegador', () => {
      const result = evaluateGameConnection({
        ping: 18,
        jit: 3,
        bloat: 15,
        upload: 30,
        lossPct: null,
        gameName: 'Fortnite',
        genre: 'Battle royale',
      })

      expect(result.isPartial).toBe(true)
      expect(result.unmeasuredGaps.some((g) => g.includes('UDP'))).toBe(true)
    })
  })
})
