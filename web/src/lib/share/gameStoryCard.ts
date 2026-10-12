// Cards de compartilhamento do Teste Gamer (desenhados em canvas).
// Formato 1a: Preview horizontal 1200x630 (WhatsApp, Telegram, X).
// Formato 1b: Vertical 1080x1920 (Status, Stories, capa de TikTok).
// Zonas seguras do 1080x1920: topo 220 px, base 380 px, laterais 64 px.
import { coverOf } from './gameCovers'
import { GAME_COLORS as C, gameCardScene, wrapLines } from './gameCard'
import type { GameSharePayload } from './gamePayload'
import { qrData } from './card'
import { drawText, loadStoryImages, roundRect, SAFE, STORY_H, STORY_W, type Ctx, type StoryImages } from './storyCard'

export const GAME_PREVIEW_W = 1200
export const GAME_PREVIEW_H = 630

export interface GameStoryImages extends StoryImages {
  cover: CanvasImageSource | null
  /** Largura e altura de `cover`, para o recorte proporcional. */
  coverSize: { w: number; h: number } | null
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`Falha ao carregar ${src}`))
    img.src = src
  })
}

export async function loadGameStoryImages(game: string): Promise<GameStoryImages> {
  const base = await loadStoryImages()
  const url = coverOf(game)
  const cover = url ? await loadImage(url).catch(() => null) : null
  return { ...base, cover, coverSize: cover ? { w: cover.naturalWidth, h: cover.naturalHeight } : null }
}

function drawCover(ctx: Ctx, images: GameStoryImages, targetW: number = STORY_W, targetH: number = STORY_H) {
  if (!images.cover || !images.coverSize) {
    ctx.fillStyle = '#1A0B2E'
    ctx.fillRect(0, 0, targetW, targetH)
    return
  }
  const { w, h } = images.coverSize
  const k = Math.max(targetW / w, targetH / h)
  const dw = w * k
  const dh = h * k
  ctx.drawImage(images.cover, (targetW - dw) / 2, (targetH - dh) / 2, dw, dh)
}

/**
 * Template 1a: Link preview · WhatsApp, Telegram, X (1200x630).
 * Exatamente igual ao protótipo Cards de compartilhamento Games.dc.html.
 */
export function drawGamePreviewCard(ctx: Ctx, p: GameSharePayload, images: GameStoryImages): void {
  const s = gameCardScene(p)
  ctx.save()
  ctx.fillStyle = C.bg
  ctx.fillRect(0, 0, GAME_PREVIEW_W, GAME_PREVIEW_H)

  drawCover(ctx, images, GAME_PREVIEW_W, GAME_PREVIEW_H)

  // Degradê lateral para manter o texto legível à direita
  const g = ctx.createLinearGradient(0, 0, GAME_PREVIEW_W, 0)
  g.addColorStop(0, 'rgba(11,11,16,.15)')
  g.addColorStop(0.38, 'rgba(11,11,16,.35)')
  g.addColorStop(0.58, 'rgba(11,11,16,.9)')
  g.addColorStop(1, 'rgba(11,11,16,.96)')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, GAME_PREVIEW_W, GAME_PREVIEW_H)

  // Selo do veredito no canto inferior esquerdo (56, 514)
  ctx.fillStyle = s.tagBg
  roundRect(ctx, 56, 514, 60, 60, 18)
  ctx.fill()
  ctx.save()
  ctx.translate(56 + 10, 514 + 10)
  ctx.scale(1.7, 1.7)
  ctx.strokeStyle = s.tagColor
  ctx.lineWidth = 2.4
  ctx.lineCap = 'round'
  if (typeof Path2D !== 'undefined') {
    ctx.stroke(new Path2D(s.iconPath))
  }
  ctx.restore()

  drawText(ctx, s.tag, 130, 542, { size: 14, weight: 600, color: s.tagColor, spacing: 2, mono: true })
  drawText(ctx, s.gameName, 130, 564, { size: 20, weight: 600, color: C.text })

  // Lado direito: 620px
  ctx.drawImage(images.lockup, 620, 64, 158, 44)
  drawText(ctx, 'signallq.com', 1144, 96, { size: 22, weight: 500, color: C.muted, align: 'right', mono: true })

  drawText(ctx, 'TESTE GAMER · RESULTADO', 620, 154, { size: 14, weight: 500, color: C.accent, spacing: 2, mono: true })
  drawText(ctx, s.title, 620, 204, { size: 44, weight: 600, color: C.text, spacing: -1.2 })

  // 3 cards de métricas
  const cards = [
    { label: 'RESPOSTA', color: C.blue, value: String(p.ping), unit: 'ms' },
    { label: 'JITTER', color: C.green, value: String(p.jitter), unit: 'ms' },
    { label: 'SOB CARGA', color: C.amber, value: p.bloat === null ? '—' : `+${p.bloat}`, unit: p.bloat === null ? '' : 'ms' },
  ]
  cards.forEach((c, i) => {
    const x = 620 + i * (162 + 14)
    ctx.fillStyle = 'rgba(22,21,29,.85)'
    roundRect(ctx, x, 246, 162, 126, 20)
    ctx.fill()
    drawText(ctx, c.label, x + 16, 276, { size: 13, weight: 500, color: c.color, spacing: 1.5, mono: true })
    drawText(ctx, c.value, x + 16, 332, { size: 40, weight: 600, color: C.text })
    if (c.unit) {
      drawText(ctx, c.unit, x + 16, 354, { size: 16, weight: 500, color: C.muted })
    }
  })

  // Linha inferior: QR code + Textos + Google Play badge
  const qr = qrData()
  const qrSize = 112
  ctx.fillStyle = '#fff'
  roundRect(ctx, 620, 420, qrSize, qrSize, 16)
  ctx.fill()
  ctx.save()
  ctx.translate(620, 420)
  ctx.scale(qrSize / qr.size, qrSize / qr.size)
  ctx.translate(4, 4)
  ctx.fillStyle = '#1C1B1F'
  if (typeof Path2D !== 'undefined') {
    ctx.fill(new Path2D(qr.d))
  }
  ctx.restore()

  drawText(ctx, 'O teste mostra o tempo.', 754, 452, { size: 24, weight: 500, color: C.muted })
  drawText(ctx, 'O app mostra a rota.', 754, 484, { size: 24, weight: 600, color: C.text })
  ctx.drawImage(images.badge, 754, 496, 144, 56)

  ctx.restore()
}

/**
 * Template 1b: Vertical · Status, Stories, capa TikTok (1080x1920).
 * Exatamente igual ao protótipo Cards de compartilhamento Games.dc.html.
 */
export function drawGameStoryCard(ctx: Ctx, p: GameSharePayload, images: GameStoryImages): void {
  const s = gameCardScene(p)
  ctx.save()
  ctx.fillStyle = C.bg
  ctx.fillRect(0, 0, STORY_W, STORY_H)

  drawCover(ctx, images, STORY_W, STORY_H)

  // Vinheta escurecendo o fundo para contraste dos textos
  const g = ctx.createLinearGradient(0, 0, 0, STORY_H)
  g.addColorStop(0, 'rgba(11,11,16,.5)')
  g.addColorStop(0.35, 'rgba(11,11,16,.75)')
  g.addColorStop(0.75, 'rgba(11,11,16,.96)')
  g.addColorStop(1, '#0B0B10')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, STORY_W, STORY_H)

  ctx.drawImage(images.lockup, SAFE.side, 240, 216, 60)
  drawText(ctx, 'TESTE GAMER', STORY_W - SAFE.side, 280, { size: 26, weight: 500, color: C.muted, spacing: 3, align: 'right', mono: true })

  // Pill do Veredito (64, 440)
  ctx.fillStyle = s.tagBg
  roundRect(ctx, SAFE.side, 440, 320, 68, 34)
  ctx.fill()
  ctx.save()
  ctx.translate(SAFE.side + 18, 440 + 18)
  ctx.scale(1.3, 1.3)
  ctx.strokeStyle = s.tagColor
  ctx.lineWidth = 2.4
  ctx.lineCap = 'round'
  if (typeof Path2D !== 'undefined') {
    ctx.stroke(new Path2D(s.iconPath))
  }
  ctx.restore()
  drawText(ctx, s.tag, SAFE.side + 62, 484, { size: 24, weight: 600, color: s.tagColor, spacing: 2, mono: true })

  // Título e Jogo
  drawText(ctx, s.gameName, SAFE.side, 560, { size: 48, weight: 500, color: C.muted })
  const titleLines = wrapLines(s.title, 22)
  titleLines.forEach((line, i) => {
    drawText(ctx, line, SAFE.side, 640 + i * 86, { size: 76, weight: 600, color: C.text, spacing: -2 })
  })

  // 3 métricas em cards verticais
  const metrics = [
    { label: 'RESPOSTA', color: C.blue, value: String(p.ping), unit: 'ms' },
    { label: 'JITTER', color: C.green, value: String(p.jitter), unit: 'ms' },
    { label: 'SOB CARGA', color: C.amber, value: p.bloat === null ? '—' : `+${p.bloat}`, unit: p.bloat === null ? '' : 'ms' },
  ]
  const cardW = (STORY_W - SAFE.side * 2 - 2 * 20) / 3
  metrics.forEach((m, i) => {
    const x = SAFE.side + i * (cardW + 20)
    ctx.fillStyle = 'rgba(22,21,29,.92)'
    roundRect(ctx, x, 960, cardW, 200, 28)
    ctx.fill()
    drawText(ctx, m.label, x + 24, 1008, { size: 20, weight: 500, color: m.color, spacing: 2, mono: true })
    drawText(ctx, m.value, x + 24, 1090, { size: 72, weight: 600, color: C.text })
    if (m.unit) {
      drawText(ctx, m.unit, x + 24, 1134, { size: 26, weight: 500, color: C.muted })
    }
  })

  // Bloco inferior com o selo do Google Play
  ctx.drawImage(images.badge, SAFE.side, 1440, 247, 96)
  drawText(ctx, 'O teste mostra o tempo.', 340, 1478, { size: 34, weight: 500, color: C.muted })
  drawText(ctx, 'O app mostra a rota.', 340, 1522, { size: 34, weight: 600, color: C.text })

  ctx.restore()
}

/** Gera o PNG do card horizontal do Teste Gamer (1200x630). */
export async function renderGamePreviewBlob(p: GameSharePayload): Promise<Blob> {
  const images = await loadGameStoryImages(p.game)
  const canvas = document.createElement('canvas')
  canvas.width = GAME_PREVIEW_W
  canvas.height = GAME_PREVIEW_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas indisponível')
  drawGamePreviewCard(ctx, p, images)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Falha ao gerar a imagem'))), 'image/png'))
}

/** Gera o PNG do card vertical do Teste Gamer (1080x1920). */
export async function renderGameStoryBlob(p: GameSharePayload): Promise<Blob> {
  const images = await loadGameStoryImages(p.game)
  const canvas = document.createElement('canvas')
  canvas.width = STORY_W
  canvas.height = STORY_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas indisponível')
  drawGameStoryCard(ctx, p, images)
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Falha ao gerar a imagem'))), 'image/png'))
}
