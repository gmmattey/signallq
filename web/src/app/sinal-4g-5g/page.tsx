import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { Sinal4g5gContent } from "./Sinal4g5gContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/sinal-4g-5g"]);

export default function Sinal4g5gPage() {
  return <Sinal4g5gContent />;
}
