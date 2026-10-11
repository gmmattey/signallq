import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import TesteGamerContent from "./TesteGamerContent"

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-gamer"])

export default function TesteGamerPage() {
  return <TesteGamerContent />
}
