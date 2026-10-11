"use client";

import { SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import { FEATURE_DOWNLOAD_APP_CLICADO, trackFeatureUsed } from "@/lib/telemetry";

interface AppDownloadCTAProps {
  title?: string;
  subtitle?: string;
  variant?: "banner" | "card" | "compact";
  className?: string;
}

export function AppDownloadCTA({
  title = "Sua internet continua oscilando? Descubra o motivo exato.",
  subtitle = "O SignallQ para Android analisa a saúde do seu Wi-Fi, interferências de canais, estabilidade de rota e equipamentos compatíveis com inteligência de diagnóstico.",
  variant = "banner",
  className = "",
}: AppDownloadCTAProps) {
  function handleClick() {
    trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO);
  }

  if (variant === "compact") {
    return (
      <div
        className={`flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-[#E7E0EC] bg-[#F3EEFA] p-4 text-left ${className}`}
      >
        <div className="max-w-[26em]">
          <div className="text-[14px] font-bold text-[#210A5C]">{title}</div>
          <div className="mt-0.5 text-[12px] leading-4 text-[#49454F]">{subtitle}</div>
        </div>
        <a
          href={SIGNALLQ_PLAY_STORE_URL}
          target="_blank"
          rel="noopener noreferrer"
          onClick={handleClick}
          className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-5 py-2.5 text-[13px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-sm"
        >
          Baixar no Google Play
        </a>
      </div>
    );
  }

  if (variant === "card") {
    return (
      <div
        className={`rounded-3xl border border-[#E7E0EC] bg-[#F8F5FB] p-6 sm:p-8 text-left ${className}`}
      >
        <div className="inline-flex items-center gap-1.5 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
          <span>📱</span> Exclusivo no aplicativo Android
        </div>
        <h3 className="mt-3 text-[22px] sm:text-[24px] font-bold leading-tight text-[#1C1B1F]">
          {title}
        </h3>
        <p className="mt-2 text-[15px] leading-6 text-[#49454F] max-w-[34em]">
          {subtitle}
        </p>
        <div className="mt-6 flex flex-wrap items-center gap-4">
          <a
            href={SIGNALLQ_PLAY_STORE_URL}
            target="_blank"
            rel="noopener noreferrer"
            onClick={handleClick}
            className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-6 py-3.5 text-[15px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-[0_4px_14px_-3px_rgba(91,33,214,0.4)]"
          >
            Baixar grátis no Google Play
          </a>
          <span className="text-[13px] text-[#79747E]">
            Grátis · Sem cadastro
          </span>
        </div>
      </div>
    );
  }

  // Default: banner largo
  return (
    <section className={`scroll-mt-20 px-6 py-12 ${className}`}>
      <div className="mx-auto max-w-[1120px] rounded-[32px] bg-[#EAE0FF] border border-[#D5C2FF]/60 px-6 sm:px-10 py-12 sm:py-16 text-center">
        <div className="mx-auto inline-flex items-center gap-1.5 rounded-full bg-white/80 px-3.5 py-1 text-[12px] font-bold text-[#210A5C]">
          <span>⚡</span> Diagnóstico Completo de Conectividade
        </div>
        <h2 className="mx-auto mt-4 max-w-[16em] text-balance text-[clamp(26px,3.8vw,42px)] font-bold leading-[1.12] tracking-[-1px] text-[#210A5C]">
          {title}
        </h2>
        <p className="mx-auto mt-3.5 max-w-[32em] text-pretty text-[16px] sm:text-[18px] leading-relaxed text-[#49454F]">
          {subtitle}
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
          <a
            href={SIGNALLQ_PLAY_STORE_URL}
            target="_blank"
            rel="noopener noreferrer"
            onClick={handleClick}
            className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-8 py-4 text-[16px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-[0_8px_20px_-6px_rgba(91,33,214,0.5)]"
          >
            Baixar grátis no Google Play
          </a>
        </div>
        <div className="mt-5 flex flex-wrap items-center justify-center gap-x-5 gap-y-1 text-[13px] text-[#49454F]">
          <span>✓ Grátis</span>
          <span>✓ Sem cadastro para começar</span>
          <span>✓ Funciona em qualquer Wi-Fi ou 4G/5G</span>
        </div>
      </div>
    </section>
  );
}
