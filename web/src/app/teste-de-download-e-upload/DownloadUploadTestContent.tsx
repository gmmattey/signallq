"use client";

import { trackFeatureUsed } from "@/lib/telemetry";
import { SITE_ORIGIN } from "@/lib/routeMetadata";
import { FaqList } from "@/components/FaqList";
import { AppDownloadCTA } from "@/components/AppDownloadCTA";
import { useSpeedTestEngine } from "@/app/teste-de-velocidade/useSpeedTestEngine";
import { SpeedTestIdle } from "@/app/teste-de-velocidade/SpeedTestIdle";
import { SpeedTestRunning } from "@/app/teste-de-velocidade/SpeedTestRunning";
import { SpeedTestResult } from "@/app/teste-de-velocidade/SpeedTestResult";
import { SpeedTestError } from "@/app/teste-de-velocidade/SpeedTestError";

const JSON_LD = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "Teste de download e upload SignallQ",
  url: `${SITE_ORIGIN}/teste-de-download-e-upload`,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "Any",
  inLanguage: "pt-BR",
  offers: { "@type": "Offer", price: "0", priceCurrency: "BRL" },
};

export const FEATURE_TESTE_VELOCIDADE_INICIADO = "teste_velocidade_iniciado";

const FAQ_ITEMS: [string, string][] = [
  [
    "O que é velocidade de download?",
    "Download é a taxa de transferência para receber informações da internet no seu aparelho. É usada ao carregar páginas, assistir a filmes e séries em streaming e baixar jogos ou arquivos.",
  ],
  [
    "O que é velocidade de upload?",
    "Upload é a taxa de transferência para enviar dados do seu dispositivo para a rede. É essencial para enviar vídeos no WhatsApp, fazer transmissões ao vivo, backups na nuvem e abrir a câmera em videoconferências.",
  ],
  [
    "Por que meu download é muito maior que meu upload?",
    "A maioria dos planos residenciais é assimétrica (ex: 300 Mbps de download e 150 Mbps de upload). Conexões de fibra óptica modernas costumam oferecer taxas mais próximas ou simétricas.",
  ],
  [
    "Como o app SignallQ Android ajuda a identificar gargalos?",
    "O SignallQ Android testa sua conexão e cruza o resultado com a qualidade do sinal do roteador, canal do Wi-Fi e a saúde da rota para indicar se o limite está na sua casa ou no provedor.",
  ],
];

export function DownloadUploadTestContent() {
  const { state, start, cancel } = useSpeedTestEngine();

  function handleStart() {
    trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_INICIADO);
    start();
  }

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }}
      />

      <section className="mx-auto max-w-[840px] px-4 sm:px-6 pb-24 pt-10 md:pt-14 text-center">
        <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Download e Upload
        </div>
        <h1 className="m-0 mt-3 text-balance text-[clamp(32px,4.5vw,48px)] font-bold leading-[1.1] tracking-[-1.2px] text-[#1C1B1F]">
          Quantos Mbps sua internet entrega para baixar e enviar?
        </h1>
        <p className="mx-auto mt-3.5 max-w-[28em] text-pretty text-[17px] sm:text-[19px] leading-[26px] sm:leading-[28px] text-[#49454F]">
          Mede a taxa real de transferência de download e upload no navegador e avalia a capacidade para vídeos 4K, reuniões e jogos.
        </p>

        <div className="mt-8 w-full rounded-[32px] border border-[#E7E0EC] bg-white p-5 sm:p-8 md:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.2)]">
          {state.phase === "idle" && <SpeedTestIdle onStart={handleStart} />}
          {state.phase === "running" && <SpeedTestRunning state={state} onCancel={cancel} />}
          {state.phase === "done" && <SpeedTestResult state={state} onRetry={handleStart} />}
          {state.phase === "error" && <SpeedTestError onRetry={handleStart} />}
        </div>

        {/* Guia Técnico */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Referência de consumo
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F]">
            Requisitos recomendados de download e upload
          </h2>

          <div className="mt-6 grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">Streaming 4K</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">25 a 50 Mbps</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Download estável por tela sem interrupções de carregamento ou perda de resolução.
              </p>
            </div>
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">Videochamadas HD</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">10 a 20 Mbps</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Upload consistente para enviar áudio e vídeo de alta definição sem cortes ou congelamentos.
              </p>
            </div>
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">Home Office & Jogos</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">100+ Mbps</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Margem segura para múltiplos membros da casa compartilhando a banda simultaneamente.
              </p>
            </div>
          </div>
        </div>

        {/* FAQ */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Perguntas Frequentes
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F] mb-6">
            Dúvidas sobre download e upload
          </h2>
          <FaqList items={FAQ_ITEMS} />
        </div>
      </section>

      <AppDownloadCTA />
    </div>
  );
}
