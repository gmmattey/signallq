import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { QuemEstaNaMinhaRedeContent } from "./QuemEstaNaMinhaRedeContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/quem-esta-na-minha-rede"]);

export default function QuemEstaNaMinhaRedePage() {
  return <QuemEstaNaMinhaRedeContent />;
}
