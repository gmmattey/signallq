import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-ping"])

export default function TestePingPage() {
  const feature = getFeatureBySlug('ping')!
  return <FeatureBody feature={feature} />
}
