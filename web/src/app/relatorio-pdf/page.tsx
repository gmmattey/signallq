import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/relatorio-pdf"])

export default function RelatorioPdfPage() {
  const feature = getFeatureBySlug('relatorio')!
  return <FeatureBody feature={feature} />
}
