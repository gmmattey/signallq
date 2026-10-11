import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { JitterTestContent } from "./JitterTestContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-jitter"]);

export default function TesteJitterPage() {
  return <JitterTestContent />;
}
