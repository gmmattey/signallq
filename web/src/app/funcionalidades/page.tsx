import type { Metadata } from 'next'
import { PAGE_META } from "@/lib/pageMetaCatalog"
import { routeMetadata } from "@/lib/routeMetadata"
import FuncionalidadesContent from "./FuncionalidadesContent"

export const metadata: Metadata = routeMetadata(PAGE_META["/funcionalidades"])

export default function FuncionalidadesPage() {
  return <FuncionalidadesContent />
}
