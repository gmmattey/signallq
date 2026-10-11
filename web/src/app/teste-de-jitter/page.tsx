import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-jitter"])

export default function TesteJitterPage() {
  const feature = getFeatureBySlug('jitter')!
  return <FeatureBody feature={feature} />
}
