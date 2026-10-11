"use client";

import React from "react";
import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { AppDownloadCTA } from "@/components/AppDownloadCTA";
import { FaqList } from "@/components/FaqList";
import type { ApprovedScreenshot } from "@/lib/approvedScreenshotsManifest";

export interface ToolExecutionLayoutProps {
  toolName: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  toolComponent: React.ReactNode;
  secondaryAppScreenshot?: ApprovedScreenshot;
  appBenefitTitle?: string;
  appBenefitDescription?: string;
  limitationNotice?: string;
  technicalGuide?: {
    title: string;
    description: string;
    cards: { label: string; value: string; detail: string }[];
  };
  faqItems?: [string, string][];
  jsonLd?: Record<string, unknown>;
}

export function ToolExecutionLayout({
  toolName,
  eyebrow,
  title,
  subtitle,
  toolComponent,
  secondaryAppScreenshot,
  appBenefitTitle = "Sua medição é só uma parte da história.",
  appBenefitDescription = "O SignallQ Android também analisa aspectos do Wi-Fi, DNS, perdas e equipamentos compatíveis para ajudar você a entender as possíveis causas dos problemas.",
  limitationNotice,
  technicalGuide,
  faqItems = [],
  jsonLd,
}: ToolExecutionLayoutProps) {
  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      {jsonLd && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      )}

      {/* ÁREA DA FERRAMENTA (PROTAGONISTA ABSOLUTA) */}
      <section className="mx-auto max-w-[1040px] px-4 sm:px-6 pt-6 sm:pt-10 pb-16">
        {/* Cabeçalho compacto */}
        <div className="text-center mb-6 sm:mb-8">
          <div className="inline-flex items-center gap-2">
            <Link
              href="/funcionalidades"
              className="text-[12px] font-semibold uppercase tracking-wider text-[#79747E] hover:text-[#5B21D6] transition-colors no-underline"
            >
              Ferramentas Web
            </Link>
            <span className="text-[#CAC4D0]">•</span>
            <span className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
              {eyebrow || toolName}
            </span>
          </div>

          <h1 className="mt-2 text-balance text-[clamp(28px,4vw,44px)] font-bold leading-[1.12] tracking-[-1.2px] text-[#1C1B1F]">
            {title}
          </h1>
          <p className="mx-auto mt-2.5 max-w-[32em] text-pretty text-[15px] sm:text-[17px] leading-6 sm:leading-7 text-[#49454F]">
            {subtitle}
          </p>
        </div>

        {/* CONTAINER DA FERRAMENTA DE MEDIÇÃO */}
        <div className="w-full rounded-[32px] border border-[#E7E0EC] bg-white p-4 sm:p-8 md:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.18)]">
          {toolComponent}
        </div>

        {/* NOTA DE LIMITAÇÃO TÉCNICA WEB (HONESTIDADE) */}
        {limitationNotice && (
          <div className="mt-6 rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 text-[13px] leading-5 text-[#79747E]">
            <strong className="text-[#49454F]">Limitação da medição no navegador:</strong>{" "}
            {limitationNotice}
          </div>
        )}

        {/* MARKETING SECUNDÁRIO DO APP ANDROID (NÃO CONCORRE VISUALMENTE) */}
        <div className="mt-14 pt-12 border-t border-[#E7E0EC]">
          <div className="rounded-[32px] bg-[#FAF8FC] border border-[#E7E0EC] p-6 sm:p-10">
            <div className="grid grid-cols-1 lg:grid-cols-[1.2fr,0.8fr] gap-8 items-center">
              <div>
                <span className="inline-flex items-center gap-1.5 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
                  <span>📱</span> Diagnóstico Completo no Android
                </span>
                <h2 className="mt-3 text-[24px] sm:text-[28px] font-bold leading-tight text-[#1C1B1F]">
                  {appBenefitTitle}
                </h2>
                <p className="mt-3 text-[15px] sm:text-[16px] leading-relaxed text-[#49454F]">
                  {appBenefitDescription}
                </p>
                <div className="mt-6 flex flex-wrap items-center gap-4">
                  <Link
                    href="/#baixar"
                    className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-6 py-3.5 text-[15px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-[0_4px_14px_-3px_rgba(91,33,214,0.4)]"
                  >
                    Conhecer o diagnóstico completo
                  </Link>
                  <Link
                    href="/como-funciona"
                    className="text-[14px] font-semibold text-[#5B21D6] hover:underline"
                  >
                    Como funciona →
                  </Link>
                </div>
              </div>

              {secondaryAppScreenshot && (
                <div className="flex justify-center">
                  <AppScreenshot
                    screenshot={secondaryAppScreenshot}
                    maxWidth={260}
                    showCaption={false}
                  />
                </div>
              )}
            </div>
          </div>
        </div>

        {/* GUIA TÉCNICO DE REFERÊNCIA */}
        {technicalGuide && (
          <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
            <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
              Referência e Interpretação
            </div>
            <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F]">
              {technicalGuide.title}
            </h2>
            <p className="mt-2 text-[15px] sm:text-[16px] leading-6 text-[#49454F]">
              {technicalGuide.description}
            </p>

            <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
              {technicalGuide.cards.map((c, i) => (
                <div
                  key={i}
                  className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4 flex flex-col justify-between"
                >
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">
                      {c.label}
                    </div>
                    <div className="mt-1 font-mono-num text-xl font-black text-[#1C1B1F]">
                      {c.value}
                    </div>
                  </div>
                  <p className="mt-2 text-xs text-[#49454F] leading-5">{c.detail}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* FAQ TÉCNICO */}
        {faqItems.length > 0 && (
          <div className="mt-16 border-t border-[#E7E0EC] pt-12 text-left">
            <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
              Perguntas Frequentes
            </div>
            <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F] mb-6">
              Dúvidas sobre o teste
            </h2>
            <FaqList items={faqItems} />
          </div>
        )}
      </section>

      {/* BANNER FINAL DE CONVERSÃO */}
      <AppDownloadCTA />
    </div>
  );
}
