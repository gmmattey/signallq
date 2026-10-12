import { NextResponse } from 'next/server'
import { toGameShareQuery, validateGameShare } from '@/lib/share/gamePayload'

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

  const payload = validateGameShare(body as Record<string, unknown>)
  if (!payload) {
    return NextResponse.json({ ok: false, error: 'Valores fora das faixas aceitas' }, { status: 400 })
  }

  const query = await toGameShareQuery(secret, payload)
  return NextResponse.json({ ok: true, url: `/r/jogo/?${query}` })
}
