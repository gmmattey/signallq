import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { RelatorioPdfContent } from "./RelatorioPdfContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/relatorio-pdf"]);

export default function RelatorioPdfPage() {
  return <RelatorioPdfContent />;
}
