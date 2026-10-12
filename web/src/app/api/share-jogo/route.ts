import { NextResponse } from 'next/server'
import { toGameShareQuery, validateGameShare } from '@/lib/share/gamePayload'
import { SITE_ORIGIN } from '@/lib/routeMetadata'

function getShareOrigin(request: Request): string {
  try {
    const host = request.headers.get('x-forwarded-host') ?? request.headers.get('host') ?? ''
    if (host.includes('localhost') || host.includes('127.0.0.1')) {
      const proto = request.headers.get('x-forwarded-proto') ?? 'http'
      return `${proto}://${host}`
    }
  } catch {}
  return SITE_ORIGIN
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
  const origin = getShareOrigin(request)
  return NextResponse.json({ ok: true, url: `${origin}/r/jogo/?${query}` })
}


