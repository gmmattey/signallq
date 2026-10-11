"use client";

import React from "react";
import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { AppDownloadCTA } from "@/components/AppDownloadCTA";
import { FaqList } from "@/components/FaqList";
import { SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import { FEATURE_DOWNLOAD_APP_CLICADO, trackFeatureUsed } from "@/lib/telemetry";
import type { ApprovedScreenshot } from "@/lib/approvedScreenshotsManifest";

export interface FeatureMarketingLayoutProps {
  eyebrow: string;
  title: string;
  intro: string;
  screenshot?: ApprovedScreenshot;
  screenshotPendingNotice?: string;
  benefits: {
    title: string;
    description: string;
    icon?: React.ReactNode;
  }[];
  howToUseSteps: {
    step: number;
    title: string;
    description: string;
  }[];
  resultsAndLimitations: {
    title: string;
    description: string;
    items: { label: string; explanation: string }[];
    limitationNote: string;
  };
  faqItems?: [string, string][];
  relatedFeatureLinks?: { title: string; href: string; badge: string }[];
  jsonLd?: Record<string, unknown>;
}

export function FeatureMarketingLayout({
  eyebrow,
  title,
  intro,
  screenshot,
  screenshotPendingNotice,
  benefits,
  howToUseSteps,
  resultsAndLimitations,
  faqItems = [],
  relatedFeatureLinks = [],
  jsonLd,
}: FeatureMarketingLayoutProps) {
  function handleDownloadClick() {
    trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO);
  }

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      {jsonLd && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      )}

      {/* 1. HERO COM COMPOSIÇÃO TEXTO + CAPTURA REAL LADO A LADO */}
      <section className="bg-gradient-to-b from-[#F9F6FC] to-white border-b border-[#F3EEFA]">
        <div className="mx-auto max-w-[1168px] px-6 pt-10 sm:pt-16 pb-16 sm:pb-24">
          <div className="mb-6">
            <Link
              href="/funcionalidades"
              className="inline-flex items-center gap-1.5 rounded-full border border-[#CAC4D0] bg-white px-3.5 py-1 text-[13px] font-semibold text-[#49454F] hover:border-[#5B21D6] hover:text-[#5B21D6] transition-colors no-underline"
            >
              ← Ver todos os recursos
            </Link>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-[1.1fr,0.9fr] gap-12 lg:gap-16 items-center">
            {/* Lado esquerdo: Proposta de valor e chamada */}
            <div>
              <div className="text-[13px] sm:text-[14px] font-bold uppercase tracking-wider text-[#5B21D6]">
                {eyebrow}
              </div>
              <h1 className="mt-3 text-balance text-[clamp(34px,4.8vw,56px)] font-bold leading-[1.06] tracking-[-1.5px] text-[#1C1B1F]">
                {title}
              </h1>
              <p className="mt-5 text-pretty text-[18px] sm:text-[20px] leading-relaxed text-[#49454F] max-w-[32em]">
                {intro}
              </p>

              {/* CTAs */}
              <div className="mt-8 flex flex-wrap items-center gap-4">
                <a
                  href={SIGNALLQ_PLAY_STORE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={handleDownloadClick}
                  className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-8 py-4 text-[16px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-[0_8px_20px_-6px_rgba(91,33,214,0.5)]"
                >
                  Baixar grátis no Google Play
                </a>
                <Link
                  href="/teste-de-velocidade"
                  className="inline-flex items-center justify-center rounded-full bg-[#F3EEFA] px-6 py-4 text-[15px] font-semibold text-[#210A5C] no-underline hover:bg-[#EAE0FF] transition-colors"
                >
                  Testar velocidade na web
                </Link>
              </div>

              {/* Indicadores de confiança */}
              <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-[13px] sm:text-[14px] text-[#79747E]">
                <span>✓ Exclusivo no aplicativo Android</span>
                <span>✓ Grátis e sem cadastro</span>
                <span>✓ Diagnóstico em palavras</span>
              </div>
            </div>

            {/* Lado direito: Captura real ou aviso de pendência */}
            <div className="flex justify-center">
              {screenshot ? (
                <AppScreenshot
                  screenshot={screenshot}
                  priority
                  maxWidth={330}
                  className="w-full"
                />
              ) : screenshotPendingNotice ? (
                <div className="w-full max-w-[360px] rounded-[32px] border border-[#E7E0EC] bg-[#FAF8FC] p-8 text-center shadow-sm">
                  <div className="text-3xl mb-3">📱</div>
                  <div className="text-[16px] font-bold text-[#1C1B1F]">
                    Captura em atualização
                  </div>
                  <p className="mt-2 text-[14px] leading-5 text-[#49454F]">
                    {screenshotPendingNotice}
                  </p>
                  <div className="mt-4 inline-flex items-center gap-1 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
                    Disponível no app Android
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </section>

      {/* 2. O QUE ESTA FUNCIONALIDADE ENTREGA */}
      <section className="mx-auto max-w-[1168px] px-6 py-20">
        <div className="max-w-[36em]">
          <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Benefícios práticos
          </div>
          <h2 className="mt-2 text-balance text-[clamp(28px,3.6vw,40px)] font-bold leading-tight tracking-[-1px] text-[#1C1B1F]">
            O que você descobre com este recurso.
          </h2>
        </div>

        <div className="mt-12 grid grid-cols-1 md:grid-cols-3 gap-6">
          {benefits.map((b, i) => (
            <div
              key={i}
              className="rounded-[28px] border border-[#E7E0EC] bg-[#FAF8FC] p-7 transition-all hover:border-[#D5C2FF] hover:bg-white hover:shadow-md"
            >
              {b.icon && <div className="mb-4">{b.icon}</div>}
              <h3 className="text-[20px] font-bold text-[#1C1B1F]">{b.title}</h3>
              <p className="mt-2.5 text-[15px] leading-6 text-[#49454F]">
                {b.description}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* 3. COMO UTILIZAR NO APLICATIVO ANDROID */}
      <section className="border-t border-[#F3EEFA] bg-[#FDFBFD] py-20">
        <div className="mx-auto max-w-[1168px] px-6">
          <div className="max-w-[36em]">
            <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
              Passo a passo
            </div>
            <h2 className="mt-2 text-balance text-[clamp(28px,3.6vw,40px)] font-bold leading-tight tracking-[-1px] text-[#1C1B1F]">
              Como usar no aplicativo SignallQ.
            </h2>
          </div>

          <div className="mt-12 grid grid-cols-1 md:grid-cols-3 gap-6">
            {howToUseSteps.map((s) => (
              <div
                key={s.step}
                className="rounded-[28px] border border-[#E7E0EC] bg-white p-7 shadow-sm"
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#EAE0FF] text-[16px] font-bold text-[#210A5C]">
                  {s.step}
                </div>
                <h3 className="mt-5 text-[18px] font-bold text-[#1C1B1F]">
                  {s.title}
                </h3>
                <p className="mt-2 text-[14px] leading-relaxed text-[#49454F]">
                  {s.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 4. RESULTADOS E LIMITAÇÕES TÉCNICAS REAIS */}
      <section className="mx-auto max-w-[1168px] px-6 py-20 border-t border-[#F3EEFA]">
        <div className="max-w-[40em]">
          <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Transparência técnica
          </div>
          <h2 className="mt-2 text-balance text-[clamp(28px,3.6vw,40px)] font-bold leading-tight tracking-[-1px] text-[#1C1B1F]">
            {resultsAndLimitations.title}
          </h2>
          <p className="mt-3 text-[16px] leading-relaxed text-[#49454F]">
            {resultsAndLimitations.description}
          </p>
        </div>

        <div className="mt-10 grid grid-cols-1 md:grid-cols-2 gap-4">
          {resultsAndLimitations.items.map((item, idx) => (
            <div
              key={idx}
              className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-5"
            >
              <div className="text-[15px] font-bold text-[#1C1B1F]">
                {item.label}
              </div>
              <p className="mt-1.5 text-[14px] leading-6 text-[#49454F]">
                {item.explanation}
              </p>
            </div>
          ))}
        </div>

        {/* Nota de honestidade sobre limitações */}
        <div className="mt-6 rounded-2xl bg-[#FFF9EB] border border-[#FFE082] p-5 text-[14px] leading-relaxed text-[#664D03]">
          <strong className="font-bold">Aviso de limitação técnica:</strong>{" "}
          {resultsAndLimitations.limitationNote}
        </div>
      </section>

      {/* 5. FAQ E DÚVIDAS ESPECÍFICAS */}
      {faqItems.length > 0 && (
        <section className="border-t border-[#F3EEFA] bg-[#FAF8FC] py-20">
          <div className="mx-auto max-w-[840px] px-6">
            <div className="text-center mb-10">
              <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
                Perguntas Frequentes
              </div>
              <h2 className="mt-2 text-balance text-[clamp(28px,3.6vw,38px)] font-bold text-[#1C1B1F]">
                Dúvidas sobre este recurso
              </h2>
            </div>
            <FaqList items={faqItems} />
          </div>
        </section>
      )}

      {/* 6. RECURSOS RELACIONADOS NO APP */}
      {relatedFeatureLinks.length > 0 && (
        <section className="mx-auto max-w-[1168px] px-6 py-16 border-t border-[#F3EEFA]">
          <div className="text-center mb-8">
            <h3 className="text-[20px] font-bold text-[#1C1B1F]">
              Outras ferramentas do SignallQ Android
            </h3>
          </div>
          <div className="flex flex-wrap justify-center gap-4">
            {relatedFeatureLinks.map((rf) => (
              <Link
                key={rf.href}
                href={rf.href}
                className="inline-flex items-center gap-2 rounded-full border border-[#E7E0EC] bg-white px-5 py-2.5 text-[14px] font-medium text-[#1C1B1F] no-underline hover:border-[#5B21D6] hover:text-[#5B21D6] transition-colors shadow-sm"
              >
                <span>{rf.title}</span>
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-[#EAE0FF] text-[#210A5C]">
                  {rf.badge}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* 7. CTA PRINCIPAL DE DOWNLOAD */}
      <AppDownloadCTA />
    </div>
  );
}
