"use client";

import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";
import { LAGCHECK_URL, SIGNALLQ_PLAY_STORE_URL } from "@/lib/config";
import { FEATURE_DOWNLOAD_APP_CLICADO, trackFeatureUsed } from "@/lib/telemetry";
import { MODO_GAMER_COMMERCIAL_FAQ } from "./modoGamerData";

export default function ModoGamerContent() {
  const handleDownloadClick = () => {
    trackFeatureUsed(FEATURE_DOWNLOAD_APP_CLICADO);
  };

  return (
    <div className="bg-[#0B0D10] font-sans leading-normal text-[#F5F2F7]">
      {/* Hero Comercial */}
      <section className="mx-auto grid max-w-[1168px] grid-cols-1 items-center gap-12 px-6 pb-16 pt-12 md:grid-cols-2 md:gap-14 md:pb-20 md:pt-16">
        <div>
          <Link
            href="/funcionalidades"
            className="inline-flex items-center gap-1.5 text-[14px] font-medium text-[#CAC4D0] no-underline hover:text-white transition-colors"
          >
            ← Funcionalidades
          </Link>

          <div className="mt-6 flex h-[30px] w-fit items-center gap-2 rounded-full border border-[#3A3446] bg-[#14171C] px-3.5 text-[13px] font-semibold text-[#C9B6FF]">
            <span className="h-2 w-2 rounded-full bg-[#7DDB92]" aria-hidden="true" />
            Modo Jogos · Exclusivo no app SignallQ
          </div>

          <h1 className="m-0 mt-5 text-balance text-[clamp(34px,5vw,56px)] font-bold leading-[1.08] tracking-[-1.5px] text-white">
            Seu jogo trava mesmo com internet rápida?
          </h1>

          <p className="mt-5 max-w-[32em] text-pretty text-[17px] sm:text-[19px] leading-[28px] text-[#CAC4D0]">
            Descubra se sua conexão está adequada para jogar online. O SignallQ analisa indicadores de estabilidade e ajuda você a entender o que pode estar prejudicando suas partidas.
          </p>

          <div className="mt-8 flex flex-col sm:flex-row flex-wrap gap-3.5">
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={handleDownloadClick}
              className="inline-flex items-center justify-center rounded-full bg-[#EAE0FF] px-8 py-4 text-[16px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors shadow-[0_4px_16px_rgba(234,224,255,0.25)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#0B0D10]"
            >
              Baixar SignallQ grátis
            </a>
            <Link
              href="/teste-gamer"
              className="inline-flex items-center justify-center rounded-full border border-[#4A4458] bg-[#14171C] px-7 py-4 text-[16px] font-medium text-[#F5F2F7] no-underline hover:border-[#C9B6FF] hover:bg-[#1D1535] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#0B0D10]"
            >
              Testar conexão no navegador
            </Link>
          </div>

          <div className="mt-5 text-[13px] text-[#A9A3B5] leading-relaxed">
            Grátis · Sem cadastro · Precisa de diagnóstico profundo de rotas de PC?{" "}
            <a
              href={LAGCHECK_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#C9B6FF] underline font-medium hover:text-white transition-colors"
            >
              Conheça o Lagcheck
            </a>
          </div>
        </div>

        {/* Captura real do app Android (Dead by Daylight / PS5) */}
        <div className="flex flex-col items-center justify-center">
          <div className="w-full max-w-[320px]">
            <AppScreenshot
              screenshot={APPROVED_SCREENSHOTS.modoGamer}
              priority
              maxWidth={320}
            />
          </div>
          <p className="mt-4 max-w-[300px] text-center text-[12px] leading-relaxed text-[#A9A3B5]">
            Exemplo de análise do SignallQ Android. O resultado utiliza uma rota de referência e indicadores de conexão, não o servidor oficial de Dead by Daylight.
          </p>
        </div>
      </section>

      {/* 3 Benefícios Reais */}
      <section className="border-t border-[#1F242C] bg-[#14171C] px-6 py-20">
        <div className="mx-auto max-w-[1120px]">
          <div className="max-w-[34em]">
            <div className="text-[13px] font-semibold uppercase tracking-wider text-[#C9B6FF]">
              Diagnóstico com propósito
            </div>
            <h2 className="m-0 mt-2 text-balance text-[clamp(28px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-white">
              O que o Modo Jogos faz pela sua partida
            </h2>
            <p className="mt-3 text-[16px] sm:text-[17px] leading-relaxed text-[#CAC4D0]">
              Em vez de apenas mostrar um número isolado de Mbps, o SignallQ avalia o que realmente afeta jogos em tempo real.
            </p>
          </div>

          <div className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-3">
            <div className="flex flex-col rounded-3xl border border-[#262B33] bg-[#0E1116] p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#1D1535] text-[#C9B6FF] font-bold text-[18px]">
                1
              </div>
              <h3 className="mt-5 text-[20px] font-bold text-white">
                Analise sua conexão para jogar
              </h3>
              <p className="mt-3 text-[15px] leading-relaxed text-[#CAC4D0]">
                O aplicativo considera indicadores relevantes para avaliar a adequação da conexão ao perfil do jogo selecionado.
              </p>
            </div>

            <div className="flex flex-col rounded-3xl border border-[#262B33] bg-[#0E1116] p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#1D1535] text-[#C9B6FF] font-bold text-[18px]">
                2
              </div>
              <h3 className="mt-5 text-[20px] font-bold text-white">
                Entenda o resultado
              </h3>
              <p className="mt-3 text-[15px] leading-relaxed text-[#CAC4D0]">
                Uma conclusão objetiva, como &ldquo;Bom pra jogar&rdquo; ou &ldquo;Pode ter atrasos&rdquo;, acompanhada dos dados que a sustentam.
              </p>
            </div>

            <div className="flex flex-col rounded-3xl border border-[#262B33] bg-[#0E1116] p-7">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#1D1535] text-[#C9B6FF] font-bold text-[18px]">
                3
              </div>
              <h3 className="mt-5 text-[20px] font-bold text-white">
                Saiba o que melhorar
              </h3>
              <p className="mt-3 text-[15px] leading-relaxed text-[#CAC4D0]">
                Orientações práticas quando houver evidências suficientes para recomendar uma ação na rede local ou com a operadora.
              </p>
            </div>
          </div>

          <div className="mt-8 rounded-2xl border border-[#262B33] bg-[#0E1116] px-5 py-4 text-[13px] text-[#A9A3B5] leading-relaxed">
            <strong className="text-[#E7E0EC]">Transparência técnica:</strong> O Modo Jogos do SignallQ é uma ferramenta de diagnóstico e estabilidade de rede. Ele não é um acelerador de internet nem promete redução milagrosa de ping.
          </div>
        </div>
      </section>

      {/* Diferenciais da Análise Android */}
      <section className="px-6 py-20 border-t border-[#1F242C]">
        <div className="mx-auto max-w-[1120px]">
          <div className="max-w-[36em]">
            <div className="text-[13px] font-semibold uppercase tracking-wider text-[#C9B6FF]">
              Diagnóstico profundo
            </div>
            <h2 className="m-0 mt-2 text-balance text-[clamp(28px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-white">
              Por que a análise no app vai além do navegador?
            </h2>
            <p className="mt-3 text-[16px] sm:text-[17px] leading-relaxed text-[#CAC4D0]">
              Navegadores web possuem restrições de segurança que impedem inspeção direta de pacotes e rádio Wi-Fi. No Android nativo, o SignallQ acessa evidências que fazem a diferença:
            </p>
          </div>

          <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-6">
              <div className="text-[16px] font-bold text-white">Sonda UDP nativa</div>
              <p className="mt-2 text-[14px] leading-relaxed text-[#CAC4D0]">
                Jogos online utilizam pacotes UDP. O app envia sondas UDP reais para medir descarte e perda física de pacotes, algo bloqueado em qualquer browser.
              </p>
            </div>

            <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-6">
              <div className="text-[16px] font-bold text-white">Sinal físico do Wi-Fi (dBm)</div>
              <p className="mt-2 text-[14px] leading-relaxed text-[#CAC4D0]">
                O app afere a potência real recebida da antena do roteador e indica se o problema é distância ou barreira física entre o aparelho e o rádio.
              </p>
            </div>

            <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-6">
              <div className="text-[16px] font-bold text-white">Varredura de canais vizinhos</div>
              <p className="mt-2 text-[14px] leading-relaxed text-[#CAC4D0]">
                Identifica redes próximas competindo pelo mesmo canal de 2.4 GHz ou 5 GHz, recomendando o canal menos congestionado.
              </p>
            </div>

            <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-6">
              <div className="text-[16px] font-bold text-white">Separação de rota e rede local</div>
              <p className="mt-2 text-[14px] leading-relaxed text-[#CAC4D0]">
                Diferencia se a instabilidade está dentro de casa (conflito de sinal, outros aparelhos) ou se é latência estrutural da operadora até o servidor regional.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Convite para o Teste Web Secundário */}
      <section className="border-t border-[#1F242C] bg-[#14171C] px-6 py-16">
        <div className="mx-auto flex max-w-[1120px] flex-col md:flex-row items-center justify-between gap-8 rounded-3xl border border-[#262B33] bg-[#0E1116] p-8 md:p-12">
          <div className="max-w-[32em]">
            <div className="text-[13px] font-semibold uppercase tracking-wider text-[#7DDB92]">
              Sem instalação
            </div>
            <h2 className="m-0 mt-2 text-[26px] sm:text-[30px] font-bold leading-tight text-white">
              Quer testar agora pelo navegador?
            </h2>
            <p className="mt-3 text-[15px] leading-relaxed text-[#CAC4D0]">
              Avalie tempo de resposta, jitter e atraso sob carga no navegador antes de entrar na fila. O teste é gratuito e executa em segundos.
            </p>
          </div>
          <Link
            href="/teste-gamer"
            className="shrink-0 rounded-full bg-[#7B4DF0] px-8 py-4 text-[16px] font-semibold text-white no-underline hover:bg-[#8D64F5] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#0E1116]"
          >
            Abrir teste gamer web →
          </Link>
        </div>
      </section>

      {/* FAQ Objetiva */}
      <section className="border-t border-[#1F242C] px-6 py-20">
        <div className="mx-auto max-w-[760px]">
          <div className="text-[13px] font-semibold uppercase tracking-wider text-[#C9B6FF]">
            Tire suas dúvidas
          </div>
          <h2 className="m-0 mt-2 mb-8 text-[clamp(26px,3.4vw,38px)] font-bold leading-tight tracking-tight text-white">
            Perguntas frequentes sobre o Modo Jogos
          </h2>

          <div className="flex flex-col">
            {MODO_GAMER_COMMERCIAL_FAQ.map((item) => (
              <details
                key={item.q}
                className="group border-t border-[#262B33] py-5 transition-colors"
              >
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[17px] font-semibold leading-snug text-[#F5F2F7] hover:text-white [&::-webkit-details-marker]:hidden">
                  <span>{item.q}</span>
                  <span
                    className="text-[22px] font-normal text-[#C9B6FF] transition-transform duration-150 group-open:rotate-45"
                    aria-hidden="true"
                  >
                    +
                  </span>
                </summary>
                <p className="m-0 mt-3 max-w-[42em] text-[15px] leading-relaxed text-[#CAC4D0]">
                  {item.a}
                </p>
              </details>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Final */}
      <section className="border-t border-[#1F242C] bg-[#1D1535] px-6 py-20">
        <div className="mx-auto max-w-[1120px] text-center">
          <h2 className="m-0 mx-auto max-w-[18em] text-balance text-[clamp(30px,4vw,48px)] font-bold leading-tight tracking-tight text-white">
            Descubra a estabilidade da sua internet antes da próxima partida.
          </h2>
          <p className="mx-auto mt-4 max-w-[30em] text-[16px] sm:text-[18px] text-[#E7DEFF] leading-relaxed">
            Instale o SignallQ no seu Android e tenha o diagnóstico completo de Wi-Fi, latência e rotas sempre à mão.
          </p>

          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={handleDownloadClick}
              className="inline-flex items-center justify-center rounded-full bg-[#EAE0FF] px-8 py-4 text-[16px] font-semibold text-[#210A5C] no-underline hover:bg-white transition-colors shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#1D1535]"
            >
              Baixar grátis no Google Play
            </a>
            <Link
              href="/teste-gamer"
              className="inline-flex items-center justify-center rounded-full border border-[#7B4DF0] bg-transparent px-7 py-4 text-[16px] font-medium text-white no-underline hover:bg-[#7B4DF0]/20 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#1D1535]"
            >
              Testar no navegador
            </Link>
          </div>
          <div className="mt-4 text-[13px] text-[#CAC4D0]">
            Sem anúncios invasivos · Sem necessidade de cadastro
          </div>
        </div>
      </section>
    </div>
  );
}
