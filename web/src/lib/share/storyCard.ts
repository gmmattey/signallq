// Cards de compartilhamento do SignallQ no navegador (desenhados em canvas).
// Formato 1a: Preview horizontal 1200x630 (WhatsApp, Telegram, X).
// Formato 1b: Vertical 1080x1920 (Status, Stories, capa de TikTok).
// Zonas seguras do 1080x1920: topo 220 px, base 380 px, laterais 64 px.
import { CARD_COLORS as C, cardHeadline, cardNumber, cardTicks, qrData } from './card'
import type { SharePayload } from './payload'

export const PREVIEW_W = 1200
export const PREVIEW_H = 630
export const STORY_W = 1080
export const STORY_H = 1920
export const SAFE = { top: 220, bottom: 380, side: 64 } as const

export interface StoryImages {
  lockup: CanvasImageSource
  badge: CanvasImageSource
}

const FAMILY = '"Google Sans Flex", Roboto, system-ui, sans-serif'
const MONO = '"Roboto Mono", monospace, sans-serif'

export type Ctx = CanvasRenderingContext2D | OffscreenCanvasRenderingContext2D

export function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

export function drawText(
  ctx: Ctx,
  s: string,
  x: number,
  y: number,
  o: { size: number; weight?: number; color: string; spacing?: number; align?: CanvasTextAlign; mono?: boolean },
) {
  const fam = o.mono ? MONO : FAMILY
  ctx.font = `${o.weight ?? 500} ${o.size}px ${fam}`
  ctx.fillStyle = o.color
  ctx.textAlign = o.align ?? 'left'
  ctx.textBaseline = 'alphabetic'
  ;(ctx as unknown as { letterSpacing: string }).letterSpacing = `${o.spacing ?? 0}px`
  ctx.fillText(s, x, y)
}

function metricPanel(ctx: Ctx, x: number, label: string, color: string, value: string, unit: string) {
  ctx.fillStyle = C.panel
  roundRect(ctx, x, 1140, 460, 170, 32)
  ctx.fill()
  drawText(ctx, label, x + 36, 1140 + 26 + 26, { size: 26, weight: 500, color, spacing: 3, mono: true })
  drawText(ctx, value, x + 36, 1140 + 26 + 26 + 4 + 84, { size: 84, weight: 600, color: C.text })
  ctx.font = `600 84px ${FAMILY}`
  const w = ctx.measureText(value).width
  drawText(ctx, unit, x + 36 + w + 10, 1140 + 26 + 26 + 4 + 84, { size: 32, weight: 500, color: C.muted })
}

/**
 * Template 1a: Link preview · WhatsApp, Telegram, X (1200x630).
 * Exatamente igual ao protótipo Cards de compartilhamento.dc.html.
 */
export function drawPreviewCard(ctx: Ctx, p: Pick<SharePayload, 'download' | 'upload' | 'ping'>, images: StoryImages): void {
  ctx.save()
  ctx.fillStyle = C.bg
  ctx.fillRect(0, 0, PREVIEW_W, PREVIEW_H)

  // Anel à esquerda: escala 470/440 a partir de (50, 80)
  ctx.save()
  ctx.translate(50, 80)
  ctx.scale(470 / 440, 470 / 440)
  ctx.lineCap = 'round'
  for (const k of cardTicks(p.download)) {
    ctx.globalAlpha = k.opacity
    ctx.strokeStyle = k.color
    ctx.lineWidth = k.width
    ctx.beginPath()
    ctx.moveTo(k.x1, k.y1)
    ctx.lineTo(k.x2, k.y2)
    ctx.stroke()
  }
  ctx.restore()

  // Centro do anel (50 + 470/2 = 285)
  drawText(ctx, 'DOWNLOAD', 285, 235, { size: 20, weight: 500, color: C.download, spacing: 3, align: 'center', mono: true })
  drawText(ctx, cardNumber(p.download), 285, 370, { size: 170, weight: 600, color: C.text, spacing: -7, align: 'center' })
  drawText(ctx, 'Mbps', 285, 415, { size: 30, weight: 500, color: C.muted, align: 'center' })

  // Lado direito (left: 600px, top: 70px)
  ctx.drawImage(images.lockup, 600, 70, 158, 44)
  drawText(ctx, 'signallq.com', 1136, 102, { size: 22, weight: 500, color: C.muted, align: 'right', mono: true })

  drawText(ctx, cardHeadline(p.download), 600, 196, { size: 68, weight: 600, color: C.text, spacing: -2 })

  // 2 cards de métricas (UPLOAD e PING)
  // UPLOAD
  ctx.fillStyle = C.panel
  roundRect(ctx, 600, 228, 260, 146, 24)
  ctx.fill()
  drawText(ctx, 'UPLOAD', 624, 268, { size: 18, weight: 500, color: C.upload, spacing: 2, mono: true })
  drawText(ctx, cardNumber(p.upload), 624, 338, { size: 60, weight: 600, color: C.text })
  ctx.font = `600 60px ${FAMILY}`
  const upW = ctx.measureText(cardNumber(p.upload)).width
  drawText(ctx, 'Mbps', 624 + upW + 8, 338, { size: 24, weight: 500, color: C.muted })

  // PING
  ctx.fillStyle = C.panel
  roundRect(ctx, 876, 228, 260, 146, 24)
  ctx.fill()
  drawText(ctx, 'PING', 900, 268, { size: 18, weight: 500, color: C.latency, spacing: 2, mono: true })
  drawText(ctx, String(p.ping), 900, 338, { size: 60, weight: 600, color: C.text })
  ctx.font = `600 60px ${FAMILY}`
  const pingW = ctx.measureText(String(p.ping)).width
  drawText(ctx, 'ms', 900 + pingW + 8, 338, { size: 24, weight: 500, color: C.muted })

  // Linha inferior: QR code + Textos + Google Play badge
  const qr = qrData()
  const qrSize = 124
  ctx.fillStyle = '#fff'
  roundRect(ctx, 600, 408, qrSize, qrSize, 16)
  ctx.fill()
  ctx.save()
  ctx.translate(600, 408)
  ctx.scale(qrSize / qr.size, qrSize / qr.size)
  ctx.translate(4, 4)
  ctx.fillStyle = '#1C1B1F'
  if (typeof Path2D !== 'undefined') {
    ctx.fill(new Path2D(qr.d))
  }
  ctx.restore()

  drawText(ctx, 'O teste mostra a velocidade.', 746, 440, { size: 25, weight: 500, color: C.muted })
  drawText(ctx, 'O app mostra o motivo.', 746, 474, { size: 25, weight: 600, color: C.text })
  ctx.drawImage(images.badge, 746, 486, 150, 58)

  ctx.restore()
}

/**
 * Template 1b: Vertical · Status, Stories, capa TikTok (1080x1920).
 * Exatamente igual ao protótipo Cards de compartilhamento.dc.html.
 */
export function drawStoryCard(ctx: Ctx, p: Pick<SharePayload, 'download' | 'upload' | 'ping'>, images: StoryImages): void {
  ctx.save()
  ctx.fillStyle = C.bg
  ctx.fillRect(0, 0, STORY_W, STORY_H)

  ctx.drawImage(images.lockup, SAFE.side, 240, 216, 60)

  // Anel (viewBox 440 -> 760 px)
  ctx.save()
  ctx.translate(160, 360)
  ctx.scale(760 / 440, 760 / 440)
  ctx.lineCap = 'round'
  for (const k of cardTicks(p.download)) {
    ctx.globalAlpha = k.opacity
    ctx.strokeStyle = k.color
    ctx.lineWidth = k.width
    ctx.beginPath()
    ctx.moveTo(k.x1, k.y1)
    ctx.lineTo(k.x2, k.y2)
    ctx.stroke()
  }
  ctx.restore()

  drawText(ctx, 'DOWNLOAD', 540, 600, { size: 28, weight: 500, color: C.download, spacing: 4, align: 'center', mono: true })
  drawText(ctx, cardNumber(p.download), 540, 830, { size: 270, weight: 600, color: C.text, spacing: -11, align: 'center' })
  drawText(ctx, 'Mbps', 540, 905, { size: 44, weight: 500, color: C.muted, align: 'center' })

  metricPanel(ctx, SAFE.side, 'UPLOAD', C.upload, cardNumber(p.upload), 'Mbps')
  metricPanel(ctx, 556, 'LATÊNCIA', C.latency, String(p.ping), 'ms')

  drawText(ctx, cardHeadline(p.download), SAFE.side, 1400, { size: 80, weight: 600, color: C.text, spacing: -3 })

  ctx.drawImage(images.badge, SAFE.side, 1440, 247, 96)
  drawText(ctx, 'O teste mostra a velocidade.', 340, 1478, { size: 34, weight: 500, color: C.muted })
  drawText(ctx, 'O app mostra o motivo.', 340, 1522, { size: 34, weight: 600, color: C.text })

  ctx.restore()
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`Falha ao carregar ${src}`))
    img.src = src
  })
}

let cachedImages: Promise<StoryImages> | null = null

/** Carrega logos e fontes antes de desenhar, para o canvas não sair com fonte reserva. */
export function loadStoryImages(): Promise<StoryImages> {
  if (cachedImages) return cachedImages
  cachedImages = (async () => {
    if (typeof document !== 'undefined' && document.fonts) {
      await Promise.all([
        document.fonts.load('600 40px "Google Sans Flex"'),
        document.fonts.load('500 40px "Google Sans Flex"'),
      ]).catch(() => null)
    }
    const [lockup, badge] = await Promise.all([
      loadImage('/assets/signallq-lockup-dark-bg-v5.png'),
      loadImage('/assets/google-play-badge-transparent.png'),
    ])
    return { lockup, badge }
  })()
  return cachedImages
}

/** Gera o PNG do card horizontal 1200x630 (WhatsApp, Telegram, X). */
export async function renderPreviewCardBlob(p: Pick<SharePayload, 'download' | 'upload' | 'ping'>): Promise<Blob> {
  const images = await loadStoryImages()
  const canvas = document.createElement('canvas')
  canvas.width = PREVIEW_W
  canvas.height = PREVIEW_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas indisponível')
  drawPreviewCard(ctx, p, images)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Falha ao gerar a imagem'))), 'image/png'))
}

/** Gera o PNG do card vertical 1080x1920 (Status, Stories, TikTok). */
export async function renderStoryCardBlob(p: Pick<SharePayload, 'download' | 'upload' | 'ping'>): Promise<Blob> {
  const images = await loadStoryImages()
  const canvas = document.createElement('canvas')
  canvas.width = STORY_W
  canvas.height = STORY_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas indisponível')
  drawStoryCard(ctx, p, images)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Falha ao gerar a imagem'))), 'image/png'))
}
