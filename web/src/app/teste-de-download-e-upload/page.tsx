import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import { getFeatureBySlug } from "@/lib/featuresData"
import { FeatureBody } from "@/components/FeatureBody"

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-download-e-upload"])

export default function TesteDownloadUploadPage() {
  const feature = getFeatureBySlug('velocidade')!
  return <FeatureBody feature={feature} />
}
