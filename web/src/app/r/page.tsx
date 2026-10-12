import type { Metadata } from 'next'
import { redirect } from 'next/navigation'
import { readSignedShare } from '@/lib/share/payload'
import { SharedResult } from '@/components/SharedResult'

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'Resultado de Velocidade — SignallQ',
  robots: {
    index: false,
    follow: true,
  },
}


export default async function SharedResultPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}) {
  const params = await searchParams
  const secret = process.env.SHARE_SIGNING_KEY
  if (!secret) redirect('/teste-de-velocidade')

  const urlParams = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (typeof value === 'string') urlParams.set(key, value)
  }

  const payload = await readSignedShare(secret, urlParams)
  if (!payload) redirect('/teste-de-velocidade')

  return <SharedResult download={payload.download} upload={payload.upload} ping={payload.ping} jitter={payload.jitter} />
}
