"use client";

import { useState, useRef } from "react";
import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { AppDownloadCTA } from "@/components/AppDownloadCTA";
import { FaqList } from "@/components/FaqList";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";
import {
  FEATURE_TESTE_DNS_INICIADO,
  FEATURE_TESTE_DNS_CONCLUIDO,
  FEATURE_TESTE_DNS_ERRO,
  trackFeatureUsed,
} from "@/lib/telemetry";

interface DnsResolver {
  name: string;
  provider: string;
  endpoint: string;
  ip: string;
  status: "idle" | "testing" | "done" | "error";
  latencyMs: number | null;
}

const INITIAL_RESOLVERS: DnsResolver[] = [
  {
    name: "Cloudflare",
    provider: "Cloudflare 1.1.1.1",
    endpoint: "https://cloudflare-dns.com/dns-query?name=google.com&type=A",
    ip: "1.1.1.1",
    status: "idle",
    latencyMs: null,
  },
  {
    name: "Google Public DNS",
    provider: "Google 8.8.8.8",
    endpoint: "https://dns.google/resolve?name=google.com&type=A",
    ip: "8.8.8.8",
    status: "idle",
    latencyMs: null,
  },
  {
    name: "Quad9",
    provider: "Quad9 9.9.9.9",
    endpoint: "https://dns.quad9.net/dns-query?name=google.com&type=A",
    ip: "9.9.9.9",
    status: "idle",
    latencyMs: null,
  },
];

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Comparador de servidores DNS SignallQ",
  url: `${SITE_ORIGIN}/teste-de-dns`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Any",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "Como funciona a comparação de DNS no navegador?",
    "A ferramenta envia consultas reais a endpoints DNS-over-HTTPS (DoH) criptografados dos principais provedores globais e cronometra o tempo de resposta em milissegundos. Assim você descobre qual provedor tem a rota mais rápida até a sua conexão.",
  ],
  [
    "Qual a diferença entre DoH e o DNS do meu roteador?",
    "O teste no navegador compara provedores públicos via DoH (porta 443). O seu computador e celular normalmente usam o DNS padrão configurado no roteador ou fornecido pela sua operadora de internet (porta 53 tradicional).",
  ],
  [
    "Mudar de DNS aumenta a velocidade de download?",
    "Não. O DNS afeta apenas o tempo de abertura de novos sites e a resolução de endereços. Ele não altera a largura de banda (Mbps) contratada na sua operadora.",
  ],
  [
    "Como o aplicativo SignallQ para Android testa o DNS?",
    "No Android, o SignallQ avalia o tempo de resposta do resolvedor local configurado na interface de rede, compara com múltiplos serviços DoH e avisa se o servidor da sua operadora estiver sofrendo lentidão ou falhas.",
  ],
];

export function DnsTestContent() {
  const [phase, setPhase] = useState<"idle" | "running" | "done" | "error">("idle");
  const [resolvers, setResolvers] = useState<DnsResolver[]>(INITIAL_RESOLVERS);
  const runRef = useRef(0);

  async function testResolver(r: DnsResolver, runId: number): Promise<number | null> {
    const samples: number[] = [];
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);

    try {
      // 2 amostras por servidor para descartar handshakes e obter mediana
      for (let i = 0; i < 2; i++) {
        if (runRef.current !== runId) return null;
        const start = performance.now();
        const res = await fetch(`${r.endpoint}&_r=${Math.random()}`, {
          headers: { accept: "application/dns-json" },
          cache: "no-store",
          signal: controller.signal,
        });
        if (res.ok) {
          samples.push(performance.now() - start);
        }
      }
      clearTimeout(timeout);
      if (!samples.length) return null;
      return Math.round(Math.min(...samples));
    } catch {
      clearTimeout(timeout);
      return null;
    }
  }

  async function handleStart() {
    const runId = ++runRef.current;
    trackFeatureUsed(FEATURE_TESTE_DNS_INICIADO);
    setPhase("running");

    const current: DnsResolver[] = INITIAL_RESOLVERS.map((r) => ({
      ...r,
      status: "testing",
      latencyMs: null,
    }));
    setResolvers(current);

    const updated = [...current];
    let successCount = 0;

    for (let i = 0; i < updated.length; i++) {
      if (runRef.current !== runId) return;
      const r = updated[i];
      const lat = await testResolver(r, runId);
      if (runRef.current !== runId) return;

      if (lat !== null) {
        updated[i] = { ...r, status: "done", latencyMs: lat };
        successCount++;
      } else {
        updated[i] = { ...r, status: "error", latencyMs: null };
      }
      setResolvers([...updated]);
    }

    if (successCount > 0) {
      setPhase("done");
      trackFeatureUsed(FEATURE_TESTE_DNS_CONCLUIDO);
    } else {
      setPhase("error");
      trackFeatureUsed(FEATURE_TESTE_DNS_ERRO);
    }
  }

  const sortedResolvers = [...resolvers].sort((a, b) => {
    if (a.latencyMs === null) return 1;
    if (b.latencyMs === null) return -1;
    return a.latencyMs - b.latencyMs;
  });

  const bestResolver = sortedResolvers.find((r) => r.latencyMs !== null);

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }}
      />

      <section className="mx-auto max-w-[880px] px-4 sm:px-6 pb-20 pt-10 md:pt-14 text-center">
        {/* Cabeçalho */}
        <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Comparação de DNS
        </div>
        <h1 className="m-0 mt-3 text-balance text-[clamp(30px,4.5vw,46px)] font-bold leading-[1.1] tracking-[-1.2px] text-[#1C1B1F]">
          Qual servidor DNS responde mais rápido para você?
        </h1>
        <p className="mx-auto mt-3.5 max-w-[30em] text-pretty text-[16px] sm:text-[18px] leading-relaxed text-[#49454F]">
          Compara a latência de resolução via DNS-over-HTTPS (DoH) em tempo real entre os principais resolvedores mundiais.
        </p>

        {/* Card Principal da Ferramenta */}
        <div className="mt-8 w-full rounded-[32px] border border-[#E7E0EC] bg-white p-6 sm:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.18)] text-left">
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#E7E0EC] pb-6">
            <div>
              <div className="text-[12px] font-bold uppercase tracking-wider text-[#79747E]">
                Status da comparação
              </div>
              <div className="text-[18px] font-bold text-[#1C1B1F] mt-0.5">
                {phase === "idle" && "Pronto para medir"}
                {phase === "running" && "Consultando servidores DoH…"}
                {phase === "done" && "Comparação concluída"}
                {phase === "error" && "Erro ao consultar servidores"}
              </div>
            </div>

            <button
              type="button"
              onClick={handleStart}
              disabled={phase === "running"}
              className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-7 py-3 text-[14px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] disabled:opacity-50 cursor-pointer shadow-sm"
            >
              {phase === "idle" ? "Iniciar comparação" : "Testar novamente"}
            </button>
          </div>

          {/* Destaque do vencedor no estado concluído */}
          {phase === "done" && bestResolver && (
            <div className="my-6 rounded-2xl bg-[#F3EEFA] border border-[#D5C2FF] p-5">
              <span className="text-[11px] font-bold uppercase tracking-wider text-[#5B21D6] px-2.5 py-0.5 rounded-full bg-white">
                Melhor resultado medido
              </span>
              <div className="mt-2 text-[20px] font-bold text-[#210A5C]">
                {bestResolver.name} foi o mais rápido nesta medição ({bestResolver.latencyMs} ms).
              </div>
              <p className="mt-1 text-[13px] text-[#49454F]">
                Menor tempo de resposta para converter nomes de sites em endereços de rede a partir da sua conexão.
              </p>
            </div>
          )}

          {/* Tabela de Resultados dos Resolvers */}
          <div className="mt-6 divide-y divide-[#E7E0EC]">
            {sortedResolvers.map((r, idx) => (
              <div
                key={r.name}
                className="py-4 flex flex-wrap items-center justify-between gap-4"
              >
                <div className="flex items-center gap-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#FAF8FC] border border-[#E7E0EC] font-mono text-[13px] font-bold text-[#49454F]">
                    {idx + 1}
                  </div>
                  <div>
                    <div className="text-[16px] font-bold text-[#1C1B1F]">
                      {r.name}
                    </div>
                    <div className="text-[12px] text-[#79747E] font-mono">
                      {r.provider} · IP {r.ip}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  {r.status === "idle" && (
                    <span className="text-[13px] text-[#79747E] font-mono">— ms</span>
                  )}
                  {r.status === "testing" && (
                    <span className="text-[13px] text-[#5B21D6] font-medium animate-pulse">
                      Medindo…
                    </span>
                  )}
                  {r.status === "done" && r.latencyMs !== null && (
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[20px] font-black text-[#1C1B1F]">
                        {r.latencyMs}
                      </span>
                      <span className="text-[13px] font-medium text-[#79747E]">ms</span>
                      {idx === 0 && (
                        <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-[#E8F5E9] text-[#146C2E]">
                          Mais rápido
                        </span>
                      )}
                    </div>
                  )}
                  {r.status === "error" && (
                    <span className="text-[12px] font-bold text-[#B3261E]">
                      Sem resposta DoH
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* Limitação da Medição Web */}
          <div className="mt-8 rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 text-[13px] leading-5 text-[#79747E]">
            <strong className="text-[#49454F]">Limitação da medição no navegador:</strong> Esta ferramenta compara endpoints DoH (DNS criptografado via HTTPS) externos. Ela não mede o servidor DNS padrão atualmente configurado no seu roteador (porta 53 UDP), pois os navegadores não têm permissão para inspecionar as configurações de rede do sistema operacional.
          </div>
        </div>

        {/* Marketing Secundário do App Android com Captura Real */}
        <div className="mt-14 rounded-3xl bg-[#FAF8FC] border border-[#E7E0EC] p-6 sm:p-10 text-left">
          <div className="grid grid-cols-1 md:grid-cols-[1.2fr,0.8fr] gap-8 items-center">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
                <span>📱</span> Exclusivo no aplicativo Android
              </span>
              <h2 className="mt-3 text-[22px] sm:text-[26px] font-bold leading-tight text-[#1C1B1F]">
                Veja como o seu DNS atual se comporta no app.
              </h2>
              <p className="mt-3 text-[15px] leading-relaxed text-[#49454F]">
                No Android, o SignallQ lê o servidor DNS real configurado pelo seu roteador, mede a latência interna da sua rede e compara diretamente com OpenDNS, Quad9, Cloudflare e Google DNS.
              </p>
              <div className="mt-6 flex flex-wrap items-center gap-4">
                <Link
                  href="/#baixar"
                  className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-6 py-3.5 text-[15px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-sm"
                >
                  Conhecer o diagnóstico no app
                </Link>
                <Link
                  href="/como-funciona"
                  className="text-[14px] font-semibold text-[#5B21D6] hover:underline"
                >
                  Como funciona →
                </Link>
              </div>
            </div>

            <div className="flex justify-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.comparacaoDns}
                maxWidth={240}
                showCaption={false}
              />
            </div>
          </div>
        </div>

        {/* FAQ Técnico */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Perguntas Frequentes
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F] mb-6">
            Dúvidas sobre o teste de DNS
          </h2>
          <FaqList items={FAQ_ITEMS} />
        </div>
      </section>

      {/* CTA final */}
      <AppDownloadCTA />
    </div>
  );
}
