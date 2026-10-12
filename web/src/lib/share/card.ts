// Cena dos cards de compartilhamento (paleta escura fixa, igual ao protótipo).
// Compartilhada pelo cliente e renderização em canvas.
import qrcode from 'qrcode-generator'
import { SIGNALLQ_PLAY_STORE_URL } from '@/lib/config'
import type { SharePayload } from './payload'

export const CARD_COLORS = {
  bg: '#0B0B10',
  text: '#F2EDF7',
  muted: '#A8A2B3',
  panel: '#16151D',
  download: '#83DA99',
  upload: '#FFB870',
  latency: '#AAC7FF',
  tickMajor: '#4a4658',
  tickMinor: '#2a2833',
} as const

/** Título curto do card, por faixa de download. */
export function cardHeadline(download: number): string {
  if (download >= 100) return 'Internet ótima.'
  if (download >= 25) return 'Internet boa.'
  if (download >= 8) return 'Internet no básico.'
  return 'Internet lenta.'
}

export function cardNumber(v: number): string {
  return v >= 100 ? String(Math.round(v)) : v.toFixed(1).replace('.', ',')
}

export interface CardTick {
  x1: number
  y1: number
  x2: number
  y2: number
  width: number
  opacity: number
  color: string
}

const RING_TICKS = 72
const RING_ARC_DEG = 240
const RING_CURVE = 0.78
const RING_STEPS = [50, 100, 250, 500, 1000, 2500, 5000]

export function niceMax(v: number): number {
  for (const s of RING_STEPS) {
    if (v <= s) return s
  }
  return 5000
}

export function ringFraction(value: number, max: number): number {
  if (!(max > 0) || !(value > 0)) return 0
  return Math.pow(Math.min(1, value / max), RING_CURVE)
}

/** Marcas do anel já coloridas para o fundo escuro. */
export function cardTicks(download: number): CardTick[] {
  const max = niceMax(download * 1.15)
  const fraction = ringFraction(download, max)
  const head = Math.round(fraction * (RING_TICKS - 1))
  const ticks: CardTick[] = []

  for (let i = 0; i < RING_TICKS; i++) {
    const angle = ((-RING_ARC_DEG / 2 + (RING_ARC_DEG * i) / (RING_TICKS - 1)) * Math.PI) / 180
    const major = i % 9 === 0
    const r1 = 205
    let r2 = major ? 183 : 191
    let width = major ? 2 : 1.5
    let opacity = 1
    let color: string = major ? CARD_COLORS.tickMajor : CARD_COLORS.tickMinor

    if (i <= head) {
      color = CARD_COLORS.download
      opacity = 0.35 + (0.65 * i) / Math.max(1, head)
      width = 2.2
      if (i === head) {
        opacity = 1
        width = 3.5
        r2 = 176
      }
    }

    const s = Math.sin(angle)
    const c = Math.cos(angle)
    ticks.push({
      x1: 220 + r1 * s,
      y1: 220 - r1 * c,
      x2: 220 + r2 * s,
      y2: 220 - r2 * c,
      width,
      opacity: Math.round(opacity * 100) / 100,
      color,
    })
  }

  return ticks
}

/** Linhas SVG do anel (coordenadas da viewBox 440). */
export function ringLines(download: number): string {
  return cardTicks(download)
    .map(
      (k) =>
        `<line x1="${k.x1.toFixed(2)}" y1="${k.y1.toFixed(2)}" x2="${k.x2.toFixed(2)}" y2="${k.y2.toFixed(2)}" stroke="${k.color}" stroke-width="${k.width}" stroke-linecap="round" opacity="${k.opacity}"/>`,
    )
    .join('')
}

export interface QrData {
  d: string
  size: number
}

/** QR Code síncrono (usado no servidor e no canvas). */
export function qrData(text: string = SIGNALLQ_PLAY_STORE_URL): QrData {
  const q = qrcode(0, 'M')
  q.addData(text, 'Byte')
  q.make()
  const n = q.getModuleCount()
  let d = ''
  for (let y = 0; y < n; y++) {
    let x = 0
    while (x < n) {
      if (q.isDark(y, x)) {
        const s = x
        while (x < n && q.isDark(y, x)) x++
        d += `M${s} ${y}h${x - s}v1h-${x - s}z`
      } else {
        x++
      }
    }
  }
  return { d, size: n + 8 }
}

export function qrSvg(text?: string): string {
  const { d, size } = qrData(text)
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}"><rect width="100%" height="100%" fill="#fff"/><path d="${d}" transform="translate(4 4)" fill="#1C1B1F"/></svg>`
}

export function cardPayloadText(p: Pick<SharePayload, 'download' | 'upload' | 'ping'>): string {
  return `${cardNumber(p.download)} Mbps · ${cardNumber(p.upload)} Mbps · ${p.ping} ms`
}
