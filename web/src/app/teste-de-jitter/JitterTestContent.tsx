"use client";

import { useState, useRef } from "react";
import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { AppDownloadCTA } from "@/components/AppDownloadCTA";
import { FaqList } from "@/components/FaqList";
import { GFiberGauge } from "@/app/teste-de-velocidade/GFiberGauge";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { SITE_ORIGIN } from "@/lib/routeMetadata";
import {
  FEATURE_TESTE_JITTER_INICIADO,
  FEATURE_TESTE_JITTER_CONCLUIDO,
  FEATURE_TESTE_JITTER_ERRO,
  trackFeatureUsed,
} from "@/lib/telemetry";

const BASE = "https://speed.cloudflare.com";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Teste de jitter e oscilação SignallQ",
  url: `${SITE_ORIGIN}/teste-de-jitter`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Any",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

const FAQ_ITEMS: [string, string][] = [
  [
    "O que é Jitter e por que ele afeta a conexão?",
    "Jitter é a variação no tempo de resposta da sua internet. Se um pacote chega em 20 ms e o próximo demora 80 ms, o jitter é de 60 ms. Essa oscilação desestabiliza chamadas de voz (áudio cortando), videoconferências e jogos competitivos.",
  ],
  [
    "Qual é um valor bom de jitter?",
    "Abaixo de 5 ms é excelente. De 5 a 15 ms é considerado bom para a maioria dos usos. Acima de 20 ms você começará a notar engasgos em jogos e reuniões de vídeo.",
  ],
  [
    "O que causa jitter alto na minha casa?",
    "A causa mais comum é a interferência no canal do Wi-Fi, distância excessiva do roteador ou saturação de outros aparelhos na residência fazendo downloads pesados simultâneos.",
  ],
  [
    "Qual a limitação do teste de jitter no navegador?",
    "O teste web mede a variação de requisições HTTP sucessivas até a borda CDN. Falhas pontuais indicam timeouts na camada web, mas não equivalem a perda física de pacotes UDP/ICMP. A perda real de pacotes e o diagnóstico de canais do roteador são medidos pelo app Android.",
  ],
];

export function JitterTestContent() {
  const [phase, setPhase] = useState<"idle" | "running" | "done" | "error">("idle");
  const [progress, setProgress] = useState(0);
  const [currentLatency, setCurrentLatency] = useState<number | null>(null);
  const [avgLatency, setAvgLatency] = useState<number | null>(null);
  const [jitterValue, setJitterValue] = useState<number | null>(null);
  const [timeoutRate, setTimeoutRate] = useState<number>(0);
  const [verdictTitle, setVerdictTitle] = useState("");
  const [verdictDesc, setVerdictDesc] = useState("");

  const runRef = useRef(0);

  async function pingOnce(timeoutMs = 2000): Promise<number | null> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const start = performance.now();
    try {
      await fetch(`${BASE}/__down?bytes=0&r=${Math.random()}`, {
        cache: "no-store",
        signal: controller.signal,
      });
      clearTimeout(timer);
      return performance.now() - start;
    } catch {
      clearTimeout(timer);
      return null;
    }
  }

  async function handleStart() {
    const runId = ++runRef.current;
    trackFeatureUsed(FEATURE_TESTE_JITTER_INICIADO);
    setPhase("running");
    setProgress(0);
    setCurrentLatency(null);
    setAvgLatency(null);
    setJitterValue(null);
    setTimeoutRate(0);

    const totalSamples = 20;
    const samples: number[] = [];
    let lost = 0;

    for (let i = 0; i < totalSamples; i++) {
      if (runRef.current !== runId) return;
      const v = await pingOnce(2000);
      if (runRef.current !== runId) return;

      if (v === null) {
        lost++;
      } else {
        samples.push(v);
        setCurrentLatency(Math.round(v));
      }
      setProgress(Math.round(((i + 1) / totalSamples) * 100));
    }

    if (!samples.length) {
      setPhase("error");
      trackFeatureUsed(FEATURE_TESTE_JITTER_ERRO);
      return;
    }

    const avg = samples.reduce((a, b) => a + b, 0) / samples.length;
    let totalDelta = 0;
    for (let i = 1; i < samples.length; i++) {
      totalDelta += Math.abs(samples[i] - samples[i - 1]);
    }
    const calculatedJitter = samples.length > 1 ? totalDelta / (samples.length - 1) : 0;
    const timeouts = Math.round((lost / totalSamples) * 100);

    setAvgLatency(Math.round(avg));
    setJitterValue(Number(calculatedJitter.toFixed(1)));
    setTimeoutRate(timeouts);

    if (calculatedJitter < 8 && timeouts === 0) {
      setVerdictTitle("Conexão altamente estável");
      setVerdictDesc("Mínima oscilação de tempo de resposta e sem timeouts HTTP. Excelente para jogos e reuniões.");
    } else if (calculatedJitter < 20 && timeouts <= 5) {
      setVerdictTitle("Estabilidade aceitável");
      setVerdictDesc("Há alguma oscilação perceptível no tempo de resposta. Reuniões longas podem ter pequenas travadas.");
    } else {
      setVerdictTitle("Conexão com oscilação relevante");
      setVerdictDesc("Variação considerável de latência detectada. Pode indicar congestionamento no Wi-Fi ou rota instável.");
    }

    setPhase("done");
    trackFeatureUsed(FEATURE_TESTE_JITTER_CONCLUIDO);
  }

  function handleCancel() {
    runRef.current++;
    setPhase("idle");
  }

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }}
      />

      <section className="mx-auto max-w-[880px] px-4 sm:px-6 pb-20 pt-10 md:pt-14 text-center">
        {/* Cabeçalho */}
        <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Estabilidade e Jitter
        </div>
        <h1 className="m-0 mt-3 text-balance text-[clamp(30px,4.5vw,46px)] font-bold leading-[1.1] tracking-[-1.2px] text-[#1C1B1F]">
          Sua conexão oscila enquanto você usa?
        </h1>
        <p className="mx-auto mt-3.5 max-w-[30em] text-pretty text-[16px] sm:text-[18px] leading-relaxed text-[#49454F]">
          Mede a estabilidade da latência consecutiva (jitter) e a frequência de oscilações na sua conexão em tempo real.
        </p>

        {/* Card Principal da Ferramenta */}
        <div className="mt-8 w-full rounded-[32px] border border-[#E7E0EC] bg-white p-6 sm:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.18)]">
          {phase === "idle" && (
            <div className="flex flex-col items-center py-8">
              <div className="text-[12px] font-bold uppercase tracking-wider text-[#79747E]">
                Aguardando início
              </div>
              <div className="mt-3 font-mono text-[64px] font-bold leading-none text-[#CAC4D0]">
                —
              </div>
              <div className="mt-2 text-[15px] font-medium text-[#79747E]">
                ms de Jitter
              </div>
              <p className="mt-4 max-w-[24em] text-[14px] text-[#49454F]">
                Envia 20 solicitações consecutivas para calcular a regularidade milissegundo a milissegundo.
              </p>
              <button
                type="button"
                onClick={handleStart}
                className="mt-8 inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-8 py-4 text-[16px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-[0_8px_20px_-6px_rgba(91,33,214,0.5)] cursor-pointer"
              >
                Iniciar teste de jitter
              </button>
            </div>
          )}

          {phase === "running" && (
            <div className="flex flex-col items-center py-6">
              <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6] animate-pulse">
                Medindo estabilidade… {progress}%
              </div>
              <div className="my-6">
                <GFiberGauge value={currentLatency ?? 0} unit="ms" size={320}>
                  <div className="flex flex-col items-center justify-center text-center">
                    <span className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#F3EEFA] text-[#5B21D6] mb-1">
                      Amostra
                    </span>
                    <div className="font-mono-num text-5xl font-black text-[#1C1B1F]">
                      {currentLatency ?? "—"}
                    </div>
                    <span className="text-xs font-bold text-[#49454F] uppercase tracking-wider mt-1">
                      ms RTT
                    </span>
                  </div>
                </GFiberGauge>
              </div>
              <button
                type="button"
                onClick={handleCancel}
                className="text-[13px] font-semibold text-[#79747E] hover:text-[#B3261E] transition-colors cursor-pointer"
              >
                Cancelar teste
              </button>
            </div>
          )}

          {phase === "done" && (
            <div className="text-center">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
                <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4 text-center">
                  <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">
                    Jitter Calculado
                  </div>
                  <div className="mt-1 font-mono text-2xl font-black text-[#5B21D6]">
                    {jitterValue} ms
                  </div>
                </div>
                <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4 text-center">
                  <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">
                    Latência Média
                  </div>
                  <div className="mt-1 font-mono text-2xl font-black text-[#1C1B1F]">
                    {avgLatency} ms
                  </div>
                </div>
                <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4 text-center">
                  <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">
                    Timeouts HTTP
                  </div>
                  <div className="mt-1 font-mono text-2xl font-black text-[#1C1B1F]">
                    {timeoutRate}%
                  </div>
                </div>
              </div>

              <div className="my-6">
                <GFiberGauge value={jitterValue ?? 0} unit="ms" size={340}>
                  <div className="flex flex-col items-center justify-center text-center">
                    <span className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#E8F5E9] text-[#146C2E] mb-1">
                      Jitter
                    </span>
                    <div className="font-mono-num text-5xl sm:text-6xl font-black text-[#1C1B1F]">
                      {jitterValue}
                    </div>
                    <span className="text-xs font-bold text-[#49454F] uppercase tracking-wider">
                      ms
                    </span>
                    <button
                      type="button"
                      onClick={handleStart}
                      className="pointer-events-auto mt-3 px-4 py-1.5 rounded-full border border-[#E7E0EC] bg-white hover:bg-[#F3EEFA] text-[#5B21D6] font-semibold text-xs transition-colors flex items-center gap-1.5 cursor-pointer shadow-sm"
                    >
                      Testar de novo
                    </button>
                  </div>
                </GFiberGauge>
              </div>

              <div className="mt-6 pt-6 border-t border-[#E7E0EC] text-left">
                <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
                  Diagnóstico de Estabilidade
                </div>
                <h2 className="mt-1 text-2xl font-bold text-[#1C1B1F]">
                  {verdictTitle}
                </h2>
                <p className="mt-1.5 text-[15px] leading-6 text-[#49454F]">
                  {verdictDesc}
                </p>
              </div>
            </div>
          )}

          {phase === "error" && (
            <div className="py-8 text-center">
              <div className="text-3xl mb-2">⚠️</div>
              <h3 className="text-lg font-bold text-[#B3261E]">Falha na medição</h3>
              <p className="mt-1 text-sm text-[#49454F]">
                Não foi possível obter respostas suficientes para calcular a estabilidade.
              </p>
              <button
                type="button"
                onClick={handleStart}
                className="mt-5 inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-6 py-2.5 text-sm font-semibold text-white cursor-pointer"
              >
                Tentar novamente
              </button>
            </div>
          )}

          {/* Limitação da Medição Web */}
          <div className="mt-8 rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 text-[13px] leading-5 text-[#79747E] text-left">
            <strong className="text-[#49454F]">Limitação da medição no navegador:</strong> Falhas de requisições HTTP indicam timeouts ou instabilidade temporária na camada web, mas não equivalem a perda física de pacotes UDP ou ICMP. A perda real de pacotes e o diagnóstico de canais do roteador são medidos pelo app SignallQ para Android.
          </div>
        </div>

        {/* Marketing Secundário com Captura Real */}
        <div className="mt-14 rounded-3xl bg-[#FAF8FC] border border-[#E7E0EC] p-6 sm:p-10 text-left">
          <div className="grid grid-cols-1 md:grid-cols-[1.2fr,0.8fr] gap-8 items-center">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
                <span>📱</span> Exclusivo no Android
              </span>
              <h2 className="mt-3 text-[22px] sm:text-[26px] font-bold leading-tight text-[#1C1B1F]">
                Entenda o que causa a instabilidade da sua rede.
              </h2>
              <p className="mt-3 text-[15px] leading-relaxed text-[#49454F]">
                No Android, o SignallQ analisa a estabilidade minuto a minuto, cruzando a oscilação com a força do sinal Wi-Fi em dBm e a saturação de canais para isolar a causa.
              </p>
              <div className="mt-6 flex flex-wrap items-center gap-4">
                <Link
                  href="/#baixar"
                  className="inline-flex items-center justify-center rounded-full bg-[#5B21D6] px-6 py-3.5 text-[15px] font-semibold text-white no-underline transition-all hover:bg-[#4A18B0] shadow-sm"
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

            <div className="flex justify-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.tempoRespostaPing}
                maxWidth={240}
                showCaption={false}
              />
            </div>
          </div>
        </div>

        {/* FAQ */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Perguntas Frequentes
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F] mb-6">
            Dúvidas sobre o teste de jitter
          </h2>
          <FaqList items={FAQ_ITEMS} />
        </div>
      </section>

      {/* CTA final */}
      <AppDownloadCTA />
    </div>
  );
}
