import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { MapaDeCalorWifiContent } from "./MapaDeCalorWifiContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/mapa-de-calor-wifi"]);

export default function MapaDeCalorWifiPage() {
  return <MapaDeCalorWifiContent />;
}
