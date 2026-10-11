import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/quem-esta-na-minha-rede"])

export default function QuemEstaNaMinhaRedePage() {
  const feature = getFeatureBySlug('dispositivos')!
  return <FeatureBody feature={feature} />
}
