import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { SinalWifiContent } from "./SinalWifiContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/sinal-wifi"]);

export default function SinalWifiPage() {
  return <SinalWifiContent />;
}
