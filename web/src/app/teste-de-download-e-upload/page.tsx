import type { Metadata } from "next";
import { PAGE_META } from "@/lib/pageMetaCatalog";
import { routeMetadata } from "@/lib/routeMetadata";
import { DownloadUploadTestContent } from "./DownloadUploadTestContent";

export const metadata: Metadata = routeMetadata(PAGE_META["/teste-de-download-e-upload"]);

export default function TesteDownloadUploadPage() {
  return <DownloadUploadTestContent />;
}
