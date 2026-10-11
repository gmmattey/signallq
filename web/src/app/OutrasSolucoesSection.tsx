"use client";

import Link from "next/link";
import { LAGCHECK_URL, LINKA_URL, WIFICASA_URL } from "@/lib/config";
import {
  FEATURE_ECOSSISTEMA_LAGCHECK_CLICADO,
  FEATURE_ECOSSISTEMA_LINKA_CLICADO,
  FEATURE_ECOSSISTEMA_WIFICASA_CLICADO,
  trackFeatureUsed,
} from "@/lib/telemetry";

interface SolucaoItem {
  id: string;
  nome: string;
  tagline: string;
  descricao: string;
  plataformas: string;
  badgePlataforma: string;
  href: string;
  isExternal: boolean;
  featureId: string;
  icone: React.ReactNode;
}

export function OutrasSolucoesSection() {
  const solucoes: SolucaoItem[] = [
    {
      id: "lagcheck",
      nome: "Lagcheck",
      tagline: "Conectividade para jogos",
      descricao:
        "Análise de conectividade e rotas para jogos online. Descubra se sua conexão tem estabilidade para jogar no modo competitivo sem travamentos e lag.",
      plataformas: "Web · Desktop",
      badgePlataforma: "Multiplataforma",
      href: LAGCHECK_URL,
      isExternal: true,
      featureId: FEATURE_ECOSSISTEMA_LAGCHECK_CLICADO,
      icone: (
        <svg className="w-6 h-6 text-[#7B4DF0]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z"
          />
        </svg>
      ),
    },
    {
      id: "linka",
      nome: "Linka",
      tagline: "Conectividade para iOS",
      descricao:
        "Ferramentas de conectividade e diagnóstico de rede desenvolvidas para o ecossistema Apple. Ideal para quem utiliza iPhone e iPad.",
      plataformas: "iOS · iPadOS",
      badgePlataforma: "Apple iOS",
      href: LINKA_URL,
      isExternal: true,
      featureId: FEATURE_ECOSSISTEMA_LINKA_CLICADO,
      icone: (
        <svg className="w-6 h-6 text-[#5B21D6]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"
          />
        </svg>
      ),
    },
    {
      id: "wificasa",
      nome: "WiFi Casa",
      tagline: "Cobertura residencial",
      descricao:
        "Diagnóstico de cobertura cômodo por cômodo. Mapeie a intensidade do sinal Wi-Fi em cada ambiente da sua residência e compare o antes e o depois de reposicionar o roteador.",
      plataformas: "Android",
      badgePlataforma: "Android",
      href: WIFICASA_URL,
      isExternal: false,
      featureId: FEATURE_ECOSSISTEMA_WIFICASA_CLICADO,
      icone: (
        <svg className="w-6 h-6 text-[#146C2E]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
          />
        </svg>
      ),
    },
  ];

  return (
    <section id="outras-solucoes" className="scroll-mt-20 border-t border-[#F3EEFA] bg-[#FAF8FC] px-6 py-24">
      <div className="mx-auto max-w-[1168px]">
        <div className="max-w-[36em]">
          <div className="text-[14px] font-semibold uppercase tracking-wider text-[#5B21D6]">
            Ecossistema de Conectividade
          </div>
          <h2 className="m-0 mt-2 text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
            Outras soluções SignallQ
          </h2>
          <p className="mt-3 text-pretty text-[18px] leading-7 text-[#49454F]">
            Ferramentas e aplicativos complementares criados para cenários específicos de diagnóstico e medição de rede.
          </p>
        </div>

        <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-6">
          {solucoes.map((s) => {
            const handleClick = () => {
              trackFeatureUsed(s.featureId);
            };

            return (
              <div
                key={s.id}
                className="flex flex-col justify-between rounded-[28px] border border-[#E7E0EC] bg-white p-7 shadow-sm transition-all hover:-translate-y-1 hover:shadow-md"
              >
                <div>
                  <div className="flex items-center justify-between">
                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#F3EEFA]">
                      {s.icone}
                    </div>
                    <span className="rounded-full bg-[#F3EEFA] px-3 py-1 text-[12px] font-semibold text-[#5B21D6]">
                      {s.badgePlataforma}
                    </span>
                  </div>

                  <div className="mt-5">
                    <div className="text-[22px] font-bold text-[#1C1B1F]">{s.nome}</div>
                    <div className="text-[13px] font-medium text-[#5B21D6] mt-0.5">{s.tagline}</div>
                    <p className="mt-3 text-[15px] leading-6 text-[#49454F]">{s.descricao}</p>
                  </div>
                </div>

                <div className="mt-8 pt-5 border-t border-[#F3EEFA] flex items-center justify-between">
                  <span className="text-[13px] text-[#79747E]">{s.plataformas}</span>
                  {s.isExternal ? (
                    <a
                      href={s.href}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={handleClick}
                      className="rounded-full bg-[#F3EEFA] hover:bg-[#EAE0FF] px-4 py-2 text-[14px] font-semibold text-[#210A5C] no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
                    >
                      Conhecer o app →
                    </a>
                  ) : (
                    <Link
                      href={s.href}
                      onClick={handleClick}
                      className="rounded-full bg-[#F3EEFA] hover:bg-[#EAE0FF] px-4 py-2 text-[14px] font-semibold text-[#210A5C] no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
                    >
                      Conhecer o app →
                    </Link>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Chamada contextual sutil para usuários de iPhone */}
        <div className="mt-10 rounded-2xl border border-[#E7E0EC] bg-white p-5 sm:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#EAE0FF] text-[#210A5C] font-bold text-sm">
              iOS
            </div>
            <div>
              <div className="text-[15px] font-bold text-[#1C1B1F]">
                Utiliza iPhone ou iPad?
              </div>
              <p className="text-[13px] text-[#49454F] mt-0.5">
                O aplicativo SignallQ é focado em Android. No ecossistema Apple, conheça o Linka para diagnóstico de rede no iOS.
              </p>
            </div>
          </div>
          <a
            href={LINKA_URL}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => trackFeatureUsed(FEATURE_ECOSSISTEMA_LINKA_CLICADO)}
            className="shrink-0 rounded-full border border-[#D5C2FF] bg-[#F8F5FB] hover:bg-[#EAE0FF] px-5 py-2.5 text-[14px] font-semibold text-[#210A5C] no-underline text-center transition-colors"
          >
            Conhecer o Linka
          </a>
        </div>
      </div>
    </section>
  );
}
