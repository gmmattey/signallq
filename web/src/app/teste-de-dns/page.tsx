import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { DnsTestContent } from "./DnsTestContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-dns"]);

export default function TesteDnsPage() {
  return <DnsTestContent />;
}
