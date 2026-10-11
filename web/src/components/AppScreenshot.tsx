import Image from "next/image";
import type { ApprovedScreenshot } from "@/lib/approvedScreenshotsManifest";

interface AppScreenshotProps {
  screenshot: ApprovedScreenshot;
  priority?: boolean;
  className?: string;
  showCaption?: boolean;
  showBadge?: boolean;
  maxWidth?: number;
}

export function AppScreenshot({
  screenshot,
  priority = false,
  className = "",
  showCaption = true,
  showBadge = true,
  maxWidth = 340,
}: AppScreenshotProps) {
  return (
    <figure className={`flex flex-col items-center m-0 ${className}`}>
      {/* Moldura de smartphone discreta */}
      <div
        style={{ maxWidth: `${maxWidth}px` }}
        className="relative w-full rounded-[38px] p-[10px] bg-[#121216] shadow-[0_25px_60px_-15px_rgba(33,10,92,0.35),0_0_0_1px_rgba(255,255,255,0.08)] ring-1 ring-black/5"
      >
        {/* Notch / Speaker discreto no topo */}
        <div className="absolute top-[14px] left-1/2 -translate-x-1/2 w-14 h-1 rounded-full bg-[#2A2A32] z-10" />

        {/* Badge discreta de captura real */}
        {showBadge && (
          <div className="absolute -top-3 left-4 z-20 inline-flex items-center gap-1 rounded-full bg-[#EAE0FF] px-2.5 py-0.5 text-[11px] font-bold text-[#210A5C] shadow-sm">
            <span aria-hidden="true">📱</span> Tela real do Android
          </div>
        )}

        {/* Imagem do aplicativo */}
        <div className="relative overflow-hidden rounded-[30px] bg-[#0E0E12]">
          <Image
            src={screenshot.src}
            alt={screenshot.alt}
            width={screenshot.width}
            height={screenshot.height}
            priority={priority}
            sizes={`(max-width: 640px) 100vw, ${maxWidth}px`}
            className="block h-auto w-full object-cover select-none"
          />
        </div>
      </div>

      {showCaption && screenshot.caption && (
        <figcaption className="mt-3.5 max-w-[24em] text-center text-[13px] leading-5 text-[#79747E]">
          {screenshot.caption}
        </figcaption>
      )}
    </figure>
  );
}
