import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { CanaisWifiContent } from "./CanaisWifiContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/canais-wifi"]);

export default function CanaisWifiPage() {
  return <CanaisWifiContent />;
}
