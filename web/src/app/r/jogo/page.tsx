import { redirect } from 'next/navigation'
import { readSignedGameShare } from '@/lib/share/gamePayload'
import { SharedGameResult } from '@/components/SharedGameResult'

export const dynamic = 'force-dynamic'

export default async function SharedGameResultPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>
}) {
  const params = await searchParams
  const secret = process.env.SHARE_SIGNING_KEY
  if (!secret) redirect('/teste-gamer')

  const urlParams = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (typeof value === 'string') urlParams.set(key, value)
  }

  const payload = await readSignedGameShare(secret, urlParams)
  if (!payload) redirect('/teste-gamer')

  return <SharedGameResult payload={payload} />
}
