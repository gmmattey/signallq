import { NextResponse } from 'next/server'
import { toGameShareQuery, validateGameShare } from '@/lib/share/gamePayload'
import { SITE_ORIGIN } from '@/lib/routeMetadata'

function getOrigin(request: Request): string {
  const host = request.headers.get('x-forwarded-host') ?? request.headers.get('host')
  const proto = request.headers.get('x-forwarded-proto') ?? 'https'
  if (host) {
    return `${proto}://${host}`
  }
  try {
    const u = new URL(request.url)
    if (u.origin && u.origin !== 'null') return u.origin
  } catch {}
  return process.env.NEXT_PUBLIC_SITE_URL || SITE_ORIGIN
}

export async function POST(request: Request) {
  const secret = process.env.SHARE_SIGNING_KEY
  if (!secret) {
    return NextResponse.json({ ok: false, error: 'Chave de assinatura não configurada' }, { status: 501 })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return NextResponse.json({ ok: false, error: 'JSON inválido' }, { status: 400 })
  }

  const raw = body && typeof body === 'object' ? { ...(body as Record<string, unknown>) } : {}
  if (raw.t === undefined || raw.t === null) {
    raw.t = Math.floor(Date.now() / 1000)
  }

  const payload = validateGameShare(raw)
  if (!payload) {
    return NextResponse.json({ ok: false, error: 'Valores fora das faixas aceitas' }, { status: 400 })
  }

  const query = await toGameShareQuery(secret, payload)
  const origin = getOrigin(request)
  return NextResponse.json({ ok: true, url: `${origin}/r/jogo/?${query}` })
}

