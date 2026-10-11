export type GameGenre = 'FPS' | 'Battle royale' | 'MOBA' | 'Esportes' | 'Casual'

export interface StopPoint {
  x: number
  y: number
}

export type ScoreStops = [number, number][]

export interface GameThresholdConfig {
  ping: ScoreStops
  jit: ScoreStops
  bloat: ScoreStops
  upload: ScoreStops
  loss: ScoreStops
}

export const GENRE_CONFIGS: Record<GameGenre, GameThresholdConfig> = {
  FPS: {
    ping: [[0, 100], [15, 95], [25, 90], [45, 80], [70, 70], [110, 60], [250, 20], [800, 0]],
    jit: [[0, 100], [2.5, 95], [5, 90], [10, 80], [18, 70], [30, 60], [70, 20], [200, 0]],
    bloat: [[0, 100], [15, 95], [30, 90], [60, 80], [100, 70], [180, 60], [500, 20], [1800, 0]],
    upload: [[0, 0], [1, 40], [3, 60], [6, 70], [12, 80], [25, 90], [60, 100]],
    loss: [[0, 100], [0.1, 90], [0.5, 80], [1, 70], [2, 60], [6, 20], [100, 0]],
  },
  MOBA: {
    ping: [[0, 100], [20, 95], [30, 90], [50, 80], [80, 70], [120, 60], [300, 20], [900, 0]],
    jit: [[0, 100], [3, 95], [6, 90], [12, 80], [20, 70], [35, 60], [85, 20], [250, 0]],
    bloat: [[0, 100], [20, 95], [40, 90], [75, 80], [120, 70], [200, 60], [550, 20], [2000, 0]],
    upload: [[0, 0], [1, 40], [3, 60], [6, 70], [10, 80], [20, 90], [50, 100]],
    loss: [[0, 100], [0.1, 90], [0.5, 80], [1, 70], [2, 60], [7, 20], [100, 0]],
  },
  'Battle royale': {
    ping: [[0, 100], [20, 95], [35, 90], [60, 80], [90, 70], [130, 60], [320, 20], [950, 0]],
    jit: [[0, 100], [4, 95], [7, 90], [14, 80], [22, 70], [38, 60], [90, 20], [260, 0]],
    bloat: [[0, 100], [25, 95], [45, 90], [80, 80], [130, 70], [220, 60], [600, 20], [2200, 0]],
    upload: [[0, 0], [1, 40], [4, 60], [8, 70], [15, 80], [30, 90], [70, 100]],
    loss: [[0, 100], [0.1, 90], [0.5, 80], [1, 70], [2, 60], [8, 20], [100, 0]],
  },
  Esportes: {
    ping: [[0, 100], [18, 95], [30, 90], [55, 80], [85, 70], [125, 60], [300, 20], [900, 0]],
    jit: [[0, 100], [3, 95], [6, 90], [12, 80], [20, 70], [35, 60], [80, 20], [240, 0]],
    bloat: [[0, 100], [20, 95], [40, 90], [70, 80], [115, 70], [190, 60], [550, 20], [2000, 0]],
    upload: [[0, 0], [1, 40], [3, 60], [6, 70], [12, 80], [25, 90], [60, 100]],
    loss: [[0, 100], [0.1, 90], [0.5, 80], [1, 70], [2, 60], [6, 20], [100, 0]],
  },
  Casual: {
    ping: [[0, 100], [35, 95], [60, 90], [90, 80], [140, 70], [200, 60], [450, 20], [1200, 0]],
    jit: [[0, 100], [6, 95], [12, 90], [22, 80], [35, 70], [55, 60], [130, 20], [350, 0]],
    bloat: [[0, 100], [40, 95], [80, 90], [140, 80], [220, 70], [320, 60], [800, 20], [3000, 0]],
    upload: [[0, 0], [0.8, 40], [2, 60], [5, 70], [10, 80], [20, 90], [45, 100]],
    loss: [[0, 100], [0.2, 90], [1, 80], [2, 70], [4, 60], [12, 20], [100, 0]],
  },
}

export function lerpScore(v: number, stops: ScoreStops): number {
  if (!(v >= 0) || Number.isNaN(v)) return 0
  if (v <= stops[0][0]) return stops[0][1]
  for (let i = 1; i < stops.length; i++) {
    const [x1, y1] = stops[i - 1]
    const [x2, y2] = stops[i]
    if (v <= x2) {
      return y1 + ((y2 - y1) * (v - x1)) / (x2 - x1)
    }
  }
  return stops[stops.length - 1][1]
}

export type ScoreBand = 'A+' | 'A' | 'B' | 'C' | 'D' | 'F'

export function bandOf(score: number): ScoreBand {
  if (score >= 95) return 'A+'
  if (score >= 88) return 'A'
  if (score >= 78) return 'B'
  if (score >= 68) return 'C'
  if (score >= 58) return 'D'
  return 'F'
}

export type DiagnosticCulprit = 'none' | 'home_wifi' | 'line_bufferbloat' | 'line_upload' | 'isp_route' | 'packet_loss'

export interface EvaluatedMetrics {
  pingScore: number
  jitScore: number
  bloatScore: number | null
  uploadScore: number | null
  lossScore: number | null
  overallScore: number
  grade: ScoreBand
  weakestLink: 'ping' | 'jitter' | 'bloat' | 'upload' | 'loss'
  culprit: DiagnosticCulprit
  headline: string
  detail: string
  actions: string[]
  unmeasuredGaps: string[]
  isPartial: boolean
}

export interface RawMetricsInput {
  ping: number
  jit: number
  bloat: number | null
  upload: number | null
  lossPct: number | null
  gameName: string
  genre: GameGenre
}

export function median(arr: number[]): number {
  if (arr.length === 0) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 !== 0 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * Median Absolute Deviation (MAD) — estimador estatístico robusto de dispersão e jitter,
 * que afere a mediana dos desvios absolutos em relação à mediana central, imune a outliers.
 */
export function calculateJitterMAD(samples: number[], med: number): number {
  if (samples.length <= 1) return 0
  const deviations = samples.map((x) => Math.abs(x - med))
  const mad = median(deviations)
  return mad < 0.95 ? Number(mad.toFixed(1)) : Math.round(mad)
}

export function evaluateGameConnection(input: RawMetricsInput): EvaluatedMetrics {
  const config = GENRE_CONFIGS[input.genre] || GENRE_CONFIGS.Casual

  const pingScore = Math.round(lerpScore(input.ping, config.ping))
  const jitScore = Math.round(lerpScore(input.jit, config.jit))

  const bloatScore = input.bloat != null ? Math.round(lerpScore(input.bloat, config.bloat)) : null
  const uploadScore = input.upload != null ? Math.round(lerpScore(input.upload, config.upload)) : null
  const lossScore = input.lossPct != null ? Math.round(lerpScore(input.lossPct, config.loss)) : null

  const scores: { link: 'ping' | 'jitter' | 'bloat' | 'upload' | 'loss'; val: number }[] = [
    { link: 'ping', val: pingScore },
    { link: 'jitter', val: jitScore },
  ]

  if (bloatScore != null) scores.push({ link: 'bloat', val: bloatScore })
  if (uploadScore != null) scores.push({ link: 'upload', val: uploadScore })
  if (lossScore != null) scores.push({ link: 'loss', val: lossScore })

  scores.sort((a, b) => a.val - b.val)

  // Weakest link principle (score = min)
  const weakest = scores[0]
  const overallScore = Math.max(0, Math.min(100, weakest.val))
  const grade = bandOf(overallScore)

  const isPartial = bloatScore == null || uploadScore == null || lossScore == null

  let culprit: DiagnosticCulprit = 'none'
  if (overallScore < 85) {
    switch (weakest.link) {
      case 'jitter':
        culprit = 'home_wifi'
        break
      case 'bloat':
        culprit = 'line_bufferbloat'
        break
      case 'upload':
        culprit = 'line_upload'
        break
      case 'loss':
        culprit = 'packet_loss'
        break
      case 'ping':
      default:
        culprit = 'isp_route'
        break
    }
  }

  const { headline, detail, actions } = buildCulpritCopy(
    culprit,
    grade,
    input.gameName,
    input.genre,
    input.ping,
    input.jit,
    input.bloat,
    input.upload,
    isPartial,
  )

  const unmeasuredGaps: string[] = []
  if (uploadScore == null || bloatScore == null) {
    unmeasuredGaps.push(
      'Upload / Bufferbloat: o teste não conseguiu saturar o canal de subida (possível bloqueio de POST, firewall ou adblock). A nota foi calculada sem carga.',
    )
  }
  if (lossScore == null) {
    unmeasuredGaps.push(
      'Perda de pacotes UDP: navegadores bloqueiam inspeção direta de pacotes UDP sem relay TURN. O teste web não estima valor falso de perda.',
    )
  }
  unmeasuredGaps.push(
    'Fila sob download: o teste web foca na fila de subida (upload), a mais crítica para atrasos de envio em jogos online.',
  )
  unmeasuredGaps.push(
    'Saltos internos da casa: o navegador mede a rota até a borda regional Anycast, sem enxergar canais de rádio do roteador ou sinal Wi-Fi (recursos do app Android).',
  )

  return {
    pingScore,
    jitScore,
    bloatScore,
    uploadScore,
    lossScore,
    overallScore,
    grade,
    weakestLink: weakest.link,
    culprit,
    headline,
    detail,
    actions,
    unmeasuredGaps,
    isPartial,
  }
}

function buildCulpritCopy(
  culprit: DiagnosticCulprit,
  grade: ScoreBand,
  gameName: string,
  genre: GameGenre,
  ping: number,
  jit: number,
  bloat: number | null,
  upload: number | null,
  isPartial: boolean,
): { headline: string; detail: string; actions: string[] } {
  if (culprit === 'none') {
    return {
      headline: isPartial
        ? `Conexão consistente para ${gameName} (estimativa sem perda UDP)`
        : `Conexão consistente para ${gameName}`,
      detail: isPartial
        ? `Tempo de resposta (${ping} ms) e oscilação (${jit} ms) bem calibrados para ${gameName}. Nota calculada sobre os elos medidos (a perda de pacotes UDP não é inspecionável diretamente via navegador).`
        : `Tempo de resposta (${ping} ms) e oscilação (${jit} ms) consistentes. Todos os elos medidos suportam a partida competitiva.`,
      actions: [
        'Mantenha a rede sem downloads massivos durante a partida.',
        'Se possível, jogue em cabo ou Wi-Fi 5 GHz para garantir a constância.',
      ],
    }
  }

  if (culprit === 'home_wifi') {
    return {
      headline: `Oscilação de rota ou rede local (${jit} ms jitter)`,
      detail: `A latência oscila com frequência. Em jogos competitivos como ${gameName}, essa instabilidade provoca atrasos no registro de ações e sensação de travamento. A causa pode ser interferência de sinal Wi-Fi ou variação na rota até a borda da operadora.`,
      actions: [
        'Se você joga no Wi-Fi: conecte com cabo de rede (Ethernet) ou aproxime do roteador na frequência de 5 GHz.',
        'Se você já joga no cabo: a oscilação indica instabilidade no link da operadora ou outros aparelhos consumindo tráfego local.',
        'Verifique se outro aparelho (TV, streaming ou celular) está competindo por banda no mesmo instante.',
      ],
    }
  }

  if (culprit === 'line_bufferbloat') {
    const bloatVal = bloat != null ? `+${bloat} ms` : 'alta'
    return {
      headline: `Bufferbloat na linha sob carga (${bloatVal})`,
      detail: `Sua linha sofre atraso quando há tráfego concorrente. Se alguém enviar um arquivo ou abrir um vídeo enquanto você joga ${gameName}, o ping vai disparar.`,
      actions: [
        'Pause downloads em segundo plano, torrents ou sincronização de nuvem antes de jogar.',
        'Ative SQM (Smart Queue Management) ou QoS no roteador para limitar filas infladas.',
        `Se o problema persistir no cabo, contate o provedor citando: ping ocioso de ${ping} ms saltando para ${
          bloat != null ? ping + bloat : 'o dobro'
        } ms sob carga.`,
      ],
    }
  }

  if (culprit === 'line_upload') {
    const upStr = upload != null ? `${upload} Mbps` : 'baixa'
    return {
      headline: `Vazão de upload no limite para partidas em grupo (${upStr})`,
      detail: `Sua taxa de subida deixa pouca margem. Jogos com chat de voz no Discord, streaming ou múltiplos aparelhos na casa podem sofrer atrasos severos.`,
      actions: [
        'Pause sincronizações de fotos/vídeos em nuvem (Google Fotos, iCloud, OneDrive).',
        'Evite chamadas de vídeo com câmera aberta na mesma rede durante a partida.',
        'Considere contatar sua operadora caso seu plano tenha taxa de upload desproporcionalmente reduzida.',
      ],
    }
  }

  if (culprit === 'packet_loss') {
    return {
      headline: `Queda ou descarte de pacotes detectada`,
      detail: `Pacotes estão se perdendo no trajeto. Em ${gameName}, isso se traduz em rubber-banding (personagem voltando no mapa) e ações canceladas.`,
      actions: [
        'Reinicie o modem e o roteador para restabelecer a sessão com a operadora.',
        'Troque o cabo de rede se estiver usando fiação antiga ou danificada.',
        'Verifique se a operadora está com instabilidade na sua região.',
      ],
    }
  }

  // isp_route
  return {
    headline: `Latência alta na rota do provedor (${ping} ms)`,
    detail: `A distância física ou a rota da sua operadora até a borda regional está longa para a exigência de ${gameName}.`,
    actions: [
      'Confirme se a região do servidor dentro do jogo está configurada para América do Sul / Brasil.',
      'Reinicie a conexão para obter um novo IP e potencialmente uma rota diferente.',
      'Compare testando pelo 4G/5G do celular para validar se é uma rota específica da sua banda larga fixa.',
    ],
  }
}
