// Cena dos cards do Teste Gamer (paleta escura fixa, igual ao protótipo).
import { gameNameOf, type GameSharePayload, type GameShareVerdict } from './gamePayload'

export const GAME_COLORS = {
  bg: '#0B0B10',
  text: '#F2EDF7',
  muted: '#C9C3D3',
  accent: '#D0BCFF',
  blue: '#AAC7FF',
  green: '#83DA99',
  amber: '#FFB870',
  red: '#FFB4AB',
} as const

/** Ícones do veredito como traço (caixa 24x24): as fontes do card não têm ✓ nem ✕. */
export const VERDICT_ICON_PATH: Record<GameShareVerdict, string> = {
  ok: 'M5 12.5l4.5 4.5L19 7.5',
  warn: 'M12 6v7M12 17.5v.5',
  bad: 'M6.5 6.5l11 11M17.5 6.5l-11 11',
}

const TAGS: Record<GameShareVerdict, { tag: string; color: string; bg: string }> = {
  ok: { tag: 'ESTÁVEL', color: GAME_COLORS.green, bg: '#143B22' },
  warn: { tag: 'ATENÇÃO', color: GAME_COLORS.amber, bg: '#3B2610' },
  bad: { tag: 'INSTÁVEL', color: GAME_COLORS.red, bg: '#4A1B17' },
}

export interface GameCardMetric {
  label: string
  color: string
  value: string
  unit: string
}

export interface GameCardScene {
  gameName: string
  verdict: GameShareVerdict
  tag: string
  tagColor: string
  tagBg: string
  iconPath: string
  title: string
  metrics: GameCardMetric[]
}

export function gameCardTitle(verdict: GameShareVerdict, gameName: string): string {
  if (verdict === 'ok') return 'Boa para jogar.'
  if (verdict === 'warn') return 'Dá para jogar, com atrasos ocasionais.'
  return `Instável para ${gameName}.`
}

export function gameCardScene(p: GameSharePayload): GameCardScene {
  const gameName = gameNameOf(p.game) ?? 'seu jogo'
  const t = TAGS[p.verdict]
  return {
    gameName,
    verdict: p.verdict,
    tag: t.tag,
    tagColor: t.color,
    tagBg: t.bg,
    iconPath: VERDICT_ICON_PATH[p.verdict],
    title: gameCardTitle(p.verdict, gameName),
    metrics: [
      { label: 'RESPOSTA', color: GAME_COLORS.blue, value: String(p.ping), unit: 'ms' },
      { label: 'JITTER', color: GAME_COLORS.green, value: String(p.jitter), unit: 'ms' },
      { label: 'SOB CARGA', color: GAME_COLORS.amber, value: p.bloat === null ? '—' : `+${p.bloat}`, unit: p.bloat === null ? '' : 'ms' },
    ],
  }
}

/** Quebra o texto em linhas de até `max` caracteres, sem cortar palavras. */
export function wrapLines(text: string, max: number): string[] {
  const lines: string[] = []
  let cur = ''
  for (const w of text.split(' ')) {
    if (cur && (cur + ' ' + w).length > max) {
      lines.push(cur)
      cur = w
    } else {
      cur = cur ? `${cur} ${w}` : w
    }
  }
  if (cur) lines.push(cur)
  return lines
}

export function gameShareMessage(p: Pick<GameSharePayload, 'ping' | 'jitter'>, gameName: string): string {
  return `Testei minha conexão para jogar ${gameName}: resposta ${p.ping} ms, jitter ${p.jitter} ms. Teste a sua no SignallQ:`
}
