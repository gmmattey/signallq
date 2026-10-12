"use client";

import { useEffect, useState } from 'react'
import { BRAND_ICONS } from '@/components/brandIcons'
import { gameShareMessage } from '@/lib/share/gameCard'
import { renderGamePreviewBlob, renderGameStoryBlob } from '@/lib/share/gameStoryCard'
import { gameNameOf, type GameSharePayload } from '@/lib/share/gamePayload'

type Shared = Omit<GameSharePayload, 't'>

export function gameShareLinks(p: Shared, url: string) {
  const e = encodeURIComponent
  const msg = gameShareMessage(p, gameNameOf(p.game) ?? 'meu jogo')
  return [
    { name: 'WhatsApp', color: '#25D366', icon: BRAND_ICONS.whatsapp, href: `https://wa.me/?text=${e(`${msg} ${url}`)}`, copy: null },
    { name: 'Telegram', color: '#26A5E4', icon: BRAND_ICONS.telegram, href: `https://t.me/share/url?url=${e(url)}&text=${e(msg)}`, copy: null },
    { name: 'X', color: 'currentColor', icon: BRAND_ICONS.x, href: `https://twitter.com/intent/tweet?text=${e(msg)}&url=${e(url)}`, copy: null },
    { name: 'TikTok (copia o texto e abre o app)', color: 'currentColor', icon: BRAND_ICONS.tiktok, href: 'https://www.tiktok.com/upload', copy: `${msg} ${url}` },
  ]
}

export function useGameShareLink(p: Shared): string | null {
  const [url, setUrl] = useState<string | null>(null)
  const { game, verdict, ping, jitter, bloat } = p
  useEffect(() => {
    let alive = true
    setUrl(null)
    fetch('/api/share-jogo/', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ game, verdict, ping, jitter, bloat }),
    })
      .then((r) => (r.ok ? (r.json() as Promise<{ ok: boolean; url?: string }>) : null))
      .then((j) => alive && setUrl(j?.ok && j.url ? j.url : null))
      .catch(() => alive && setUrl(null))
    return () => {
      alive = false
    }
  }, [game, verdict, ping, jitter, bloat])
  return url
}

export function GameShareRow({ payload }: { payload: Shared }) {
  const signed = useGameShareLink(payload)
  const isReady = signed !== null
  const url = signed ?? ''
  const links = gameShareLinks(payload, url || 'https://signallq.com/teste-gamer/')
  const [saving, setSaving] = useState(false)
  const [note, setNote] = useState<string | null>(null)


  async function saveImage(format: 'feed' | 'story' = 'feed') {
    setSaving(true)
    setNote(null)
    try {
      const isStory = format === 'story'
      const fullPayload = { ...payload, t: Math.floor(Date.now() / 1000) }
      const blob = isStory ? await renderGameStoryBlob(fullPayload) : await renderGamePreviewBlob(fullPayload)
      const fileName = isStory ? 'signallq-gamer-stories.png' : 'signallq-teste-gamer.png'
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
      const fullPayload = { ...payload, t: Math.floor(Date.now() / 1000) }
      const blob = isStory ? await renderGameStoryBlob(fullPayload) : await renderGamePreviewBlob(fullPayload)
      const fileName = isStory ? 'signallq-gamer-stories.png' : 'signallq-teste-gamer.png'
      const file = new File([blob], fileName, { type: 'image/png' })
      if (typeof navigator !== 'undefined' && navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({
          files: [file],
          title: 'SignallQ — Teste Gamer',
          text: `Testei minha conexão para jogar: ${url}`,
        })
        setNote('Compartilhado com sucesso!')
        return
      }

      if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        setNote('Imagem copiada para a área de transferência! Cole onde desejar.')
        return
      }

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
    if (!isReady) {
      e.preventDefault()
      return
    }

    if (l.copy) {

      await copy(l.copy, 'Texto copiado para a área de transferência.')
      return
    }

    if (typeof navigator !== 'undefined') {
      try {
        const dummyFile = typeof File !== 'undefined' ? new File([], 't.png', { type: 'image/png' }) : null
        if (dummyFile && navigator.canShare && navigator.canShare({ files: [dummyFile] })) {
          e.preventDefault()
          setSaving(true)
          setNote(null)
          try {
            const blob = await renderGamePreviewBlob({ ...payload, t: Math.floor(Date.now() / 1000) })
            const file = new File([blob], 'signallq-teste-gamer.png', { type: 'image/png' })
            await navigator.share({
              files: [file],
              title: 'SignallQ — Teste Gamer',
              text: `Testei minha conexão para jogar: ${url}`,
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

    if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
      try {
        const blob = await renderGamePreviewBlob({ ...payload, t: Math.floor(Date.now() / 1000) })
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        setNote('Imagem copiada! Cole (Ctrl+V / Cmd+V) na conversa.')
      } catch {}
    }
  }

  async function copy(text: string, ok: string) {
    try {
      await navigator.clipboard.writeText(text)
      setNote(ok)
    } catch {
      setNote('Não foi possível copiar.')
    }
  }

  return (
    <div className="mt-6 flex flex-col gap-3.5">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-[11px] font-bold uppercase tracking-wider text-[#CAC4D0]">Compartilhar</span>
        <div className="flex items-center gap-2.5">
          {links.map((l) => (
            <a
              key={l.name}
              href={isReady ? l.href : '#'}
              target={isReady ? '_blank' : undefined}
              rel="noopener noreferrer"
              aria-label={`Compartilhar no ${l.name}`}
              aria-disabled={!isReady}
              title={isReady ? l.name : 'Aguardando link assinado…'}
              onClick={(e) => void handleSocialClick(e, l)}
              className={`flex h-11 w-11 items-center justify-center rounded-full bg-[#16151D] border border-[#4A4458] shadow-sm transition-transform text-white ${
                isReady ? 'hover:-translate-y-0.5' : 'opacity-40 cursor-not-allowed'
              }`}
            >
              <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                <path d={l.icon} fill={l.color === 'currentColor' ? '#F2EDF7' : l.color} />
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
          className="rounded-full bg-[#7B4DF0] px-5 py-2.5 text-xs sm:text-sm font-semibold text-white hover:bg-[#8D64F5] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          {saving ? 'Gerando…' : 'Compartilhar imagem'}
        </button>
        <button
          type="button"
          onClick={() => void shareImage('story')}
          disabled={saving}
          className="rounded-full border border-[#4A4458] bg-[#16151D] px-5 py-2.5 text-xs sm:text-sm font-medium text-[#CAC4D0] hover:text-white hover:border-[#C9B6FF] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          Stories / Status (9:16)
        </button>
        <button
          type="button"
          onClick={() => void saveImage('feed')}
          disabled={saving}
          className="rounded-full border border-[#4A4458] bg-[#16151D] px-5 py-2.5 text-xs sm:text-sm font-medium text-[#CAC4D0] hover:text-white hover:border-[#C9B6FF] transition-colors disabled:opacity-60 cursor-pointer shadow-sm"
        >
          Salvar imagem
        </button>
        <button
          type="button"
          onClick={() => void copy(url, 'Link copiado.')}
          disabled={!isReady}
          className="rounded-full border border-[#4A4458] bg-[#16151D] px-5 py-2.5 text-xs sm:text-sm font-medium text-[#CAC4D0] hover:text-white hover:border-[#C9B6FF] transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer shadow-sm"
        >
          {isReady ? 'Copiar link' : 'Gerando link…'}
        </button>
      </div>


      {note && (
        <p className="m-0 text-xs text-[#CAC4D0]" aria-live="polite">
          {note}
        </p>
      )}
    </div>
  )
}
