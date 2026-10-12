"use client";

import { useEffect, useState } from 'react'
import { BRAND_ICONS } from '@/components/brandIcons'
import { renderPreviewCardBlob, renderStoryCardBlob } from '@/lib/share/storyCard'

export interface ShareMetrics {
  download: number
  upload: number
  ping: number
  jitter: number
}

export function shareMessage(f: Pick<ShareMetrics, 'download' | 'upload' | 'ping'>): string {
  return `Minha internet: ${Math.round(f.download)} Mbps de download, ${Math.round(f.upload)} Mbps de upload, ping ${f.ping} ms. Teste a sua no SignallQ:`
}

export function shareLinks(f: ShareMetrics, url: string) {
  const e = encodeURIComponent
  const msg = shareMessage(f)
  return [
    { name: 'WhatsApp', color: '#25D366', icon: BRAND_ICONS.whatsapp, href: `https://wa.me/?text=${e(`${msg} ${url}`)}`, copy: null },
    { name: 'Telegram', color: '#26A5E4', icon: BRAND_ICONS.telegram, href: `https://t.me/share/url?url=${e(url)}&text=${e(msg)}`, copy: null },
    { name: 'X', color: 'currentColor', icon: BRAND_ICONS.x, href: `https://twitter.com/intent/tweet?text=${e(msg)}&url=${e(url)}`, copy: null },
    { name: 'TikTok (copia o texto e abre o app)', color: 'currentColor', icon: BRAND_ICONS.tiktok, href: 'https://www.tiktok.com/upload', copy: `${msg} ${url}` },
  ]
}

export function useShareLink(f: ShareMetrics): string | null {
  const [url, setUrl] = useState<string | null>(null)
  const { download, upload, ping, jitter } = f
  useEffect(() => {
    let alive = true
    setUrl(null)
    fetch('/api/share/', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ download, upload, ping, jitter }),
    })
      .then((r) => (r.ok ? (r.json() as Promise<{ ok: boolean; url?: string }>) : null))
      .then((j) => alive && setUrl(j?.ok && j.url ? j.url : null))
      .catch(() => alive && setUrl(null))
    return () => {
      alive = false
    }
  }, [download, upload, ping, jitter])
  return url
}

export function ShareRow({ metrics }: { metrics: ShareMetrics }) {
  const signed = useShareLink(metrics)
  const url = signed ?? 'https://signallq.com'
  const links = shareLinks(metrics, url)
  const [saving, setSaving] = useState(false)
  const [note, setNote] = useState<string | null>(null)

  async function copy(text: string, ok: string) {
    try {
      await navigator.clipboard.writeText(text)
      setNote(ok)
    } catch {
      setNote('Não foi possível copiar.')
    }
  }

  async function saveImage(format: 'feed' | 'story' = 'feed') {
    setSaving(true)
    setNote(null)
    try {
      const isStory = format === 'story'
      const blob = isStory ? await renderStoryCardBlob(metrics) : await renderPreviewCardBlob(metrics)
      const fileName = isStory ? 'signallq-stories.png' : 'signallq-resultado.png'
      const href = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = href
      a.download = fileName
      a.click()
      setTimeout(() => URL.revokeObjectURL(href), 10_000)
    } catch {
      setNote('Não foi possível gerar a imagem.')
    } finally {
      setSaving(false)
    }
  }

  async function shareImage(format: 'feed' | 'story' = 'feed') {
    setSaving(true)
    setNote(null)
    try {
      const isStory = format === 'story'
      const blob = isStory ? await renderStoryCardBlob(metrics) : await renderPreviewCardBlob(metrics)
      const fileName = isStory ? 'signallq-stories.png' : 'signallq-resultado.png'
      const file = new File([blob], fileName, { type: 'image/png' })
      if (typeof navigator !== 'undefined' && navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({
          files: [file],
          title: 'SignallQ — Meu teste de velocidade',
          text: `${shareMessage(metrics)} ${url}`,
        })
        setNote('Compartilhado com sucesso!')
        return
      }

      // No desktop: copia a imagem diretamente para o clipboard
      if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        setNote('Imagem copiada para a área de transferência! Cole onde desejar.')
        return
      }

      // Fallback: download
      const href = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = href
      a.download = fileName
      a.click()
      setTimeout(() => URL.revokeObjectURL(href), 10_000)
      setNote('Imagem baixada com sucesso!')
    } catch (err: unknown) {
      if ((err as Error)?.name !== 'AbortError') {
        setNote('Não foi possível compartilhar a imagem.')
      }
    } finally {
      setSaving(false)
    }
  }

  async function handleSocialClick(e: React.MouseEvent<HTMLAnchorElement>, l: { name: string; href: string; copy?: string | null }) {
    if (l.copy) {
      await copy(l.copy, 'Texto copiado para a área de transferência.')
      return
    }

    // No mobile: envia o card 1200x630 (Template 1a: WhatsApp, Telegram, X)
    if (typeof navigator !== 'undefined') {
      try {
        const dummyFile = typeof File !== 'undefined' ? new File([], 't.png', { type: 'image/png' }) : null
        if (dummyFile && navigator.canShare && navigator.canShare({ files: [dummyFile] })) {
          e.preventDefault()
          setSaving(true)
          setNote(null)
          try {
            const blob = await renderPreviewCardBlob(metrics)
            const file = new File([blob], 'signallq-resultado.png', { type: 'image/png' })
            await navigator.share({
              files: [file],
              title: 'SignallQ — Meu teste de velocidade',
              text: `${shareMessage(metrics)} ${url}`,
            })
            setNote('Compartilhado com sucesso!')
          } catch (err: unknown) {
            if ((err as Error)?.name !== 'AbortError') {
              window.open(l.href, '_blank', 'noopener,noreferrer')
            }
          } finally {
            setSaving(false)
          }
          return
        }
      } catch {}
    }

    // No desktop: tenta copiar a imagem 1200x630 para o clipboard antes de abrir o link
    if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
      try {
        const blob = await renderPreviewCardBlob(metrics)
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        setNote('Imagem copiada! Cole (Ctrl+V / Cmd+V) na conversa.')
      } catch {}
    }
  }

  return (
    <div className="mt-6 flex flex-col gap-3.5">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-[11px] font-bold uppercase tracking-wider text-[#79747E]">Compartilhar</span>
        <div className="flex items-center gap-2.5">
          {links.map((l) => (
            <a
              key={l.name}
              href={l.href}
              target="_blank"
              rel="noopener noreferrer"
              aria-label={`Compartilhar no ${l.name}`}
              title={l.name}
              onClick={(e) => void handleSocialClick(e, l)}
              className="flex h-11 w-11 items-center justify-center rounded-full bg-white shadow-sm transition-transform hover:-translate-y-0.5 border border-[#E7E0EC]"
            >
              <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                <path d={l.icon} fill={l.color === 'currentColor' ? '#1C1B1F' : l.color} />
              </svg>
            </a>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2.5">
        <button
          type="button"
          onClick={() => void shareImage('feed')}
          disabled={saving}
          className="rounded-full bg-[#5B21D6] px-5 py-2.5 text-xs sm:text-sm font-semibold text-white hover:bg-[#6D28D9] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          {saving ? 'Gerando…' : 'Compartilhar imagem'}
        </button>
        <button
          type="button"
          onClick={() => void shareImage('story')}
          disabled={saving}
          className="rounded-full border border-[#E7E0EC] bg-white px-5 py-2.5 text-xs sm:text-sm font-medium text-[#1C1B1F] hover:bg-[#F3EEFA] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          Stories / Status (9:16)
        </button>
        <button
          type="button"
          onClick={() => void saveImage('feed')}
          disabled={saving}
          className="rounded-full border border-[#E7E0EC] bg-white px-5 py-2.5 text-xs sm:text-sm font-medium text-[#1C1B1F] hover:bg-[#F3EEFA] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          Salvar imagem
        </button>
        <button
          type="button"
          onClick={() => void copy(url, 'Link copiado.')}
          className="rounded-full border border-[#E7E0EC] bg-white px-5 py-2.5 text-xs sm:text-sm font-medium text-[#1C1B1F] hover:bg-[#F3EEFA] transition-colors cursor-pointer shadow-sm"
        >
          Copiar link
        </button>
      </div>

      {note && (
        <p className="m-0 text-xs text-[#79747E]" aria-live="polite">
          {note}
        </p>
      )}
    </div>
  )
}
