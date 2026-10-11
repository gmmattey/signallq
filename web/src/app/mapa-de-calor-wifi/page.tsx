import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/mapa-de-calor-wifi"])

export default function MapaDeCalorWifiPage() {
  const feature = getFeatureBySlug('mapa')!
  return <FeatureBody feature={feature} />
}
