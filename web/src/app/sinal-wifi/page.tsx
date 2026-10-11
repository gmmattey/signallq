import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/sinal-wifi"])

export default function SinalWifiPage() {
  const feature = getFeatureBySlug('wifi')!
  return <FeatureBody feature={feature} />
}
