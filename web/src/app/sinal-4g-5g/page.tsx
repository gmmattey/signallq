import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/sinal-4g-5g"])

export default function Sinal4g5gPage() {
  const feature = getFeatureBySlug('movel')!
  return <FeatureBody feature={feature} />
}
