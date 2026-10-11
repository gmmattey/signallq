import type { Metadata } from 'next'
import { PAGE_META } from '@/lib/pageMetaCatalog'
import { routeMetadata } from '@/lib/routeMetadata'
import { PingTestContent } from './PingTestContent'

export const metadata: Metadata = routeMetadata(PAGE_META['/teste-de-ping'])

export default function TestePingPage() {
  return <PingTestContent />
}
