import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import ModoGamerContent from "./ModoGamerContent"

export const metadata: Metadata = routeMetadata(PAGE_META["/modo-gamer"])

export default function ModoGamerPage() {
  return <ModoGamerContent />
}
