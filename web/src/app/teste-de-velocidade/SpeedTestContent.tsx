"use client";

import { trackFeatureUsed } from '@/lib/telemetry'
import { SITE_ORIGIN } from '@/lib/routeMetadata'
import { FaqList } from '@/components/FaqList'
import { AppDownloadCTA } from '@/components/AppDownloadCTA'
import { useSpeedTestEngine } from './useSpeedTestEngine'
import { SpeedTestIdle } from './SpeedTestIdle'
import { SpeedTestRunning } from './SpeedTestRunning'
import { SpeedTestResult } from './SpeedTestResult'
import { SpeedTestError } from './SpeedTestError'

const JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'WebApplication',
  name: 'Teste de velocidade SignallQ',
  url: `${SITE_ORIGIN}/teste-de-velocidade`,
  applicationCategory: 'UtilitiesApplication',
  operatingSystem: 'Any',
  inLanguage: 'pt-BR',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'BRL' },
}

export const FEATURE_TESTE_VELOCIDADE_INICIADO = 'teste_velocidade_iniciado'

export function SpeedTestContent() {
  const { state, start, cancel } = useSpeedTestEngine()

  function handleStart() {
    trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_INICIADO)
    start()
  }

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }} />

      <section className="mx-auto max-w-[840px] px-4 sm:px-6 pb-24 pt-10 md:pt-14 text-center">
        <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Teste de velocidade
        </div>
        <h1 className="m-0 mt-3 text-balance text-[clamp(32px,4.5vw,48px)] font-bold leading-[1.1] tracking-[-1.2px] text-[#1C1B1F]">
          Quão rápida está sua internet agora?
        </h1>
        <p className="mx-auto mt-3.5 max-w-[28em] text-pretty text-[17px] sm:text-[19px] leading-[26px] sm:leading-[28px] text-[#49454F]">
          Mede latência, download e upload em tempo real. Você vê o resultado em palavras, não só em números.
        </p>

        <div className="mt-8 w-full rounded-[32px] border border-[#E7E0EC] bg-white p-5 sm:p-8 md:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.2)]">
          {state.phase === 'idle' && <SpeedTestIdle onStart={handleStart} />}
          {state.phase === 'running' && <SpeedTestRunning state={state} onCancel={cancel} />}
          {state.phase === 'done' && <SpeedTestResult state={state} onRetry={handleStart} />}
          {state.phase === 'error' && <SpeedTestError onRetry={handleStart} />}
        </div>

        {/* Guia Técnico de Referência */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Guia de Velocidade
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F]">
            Quantos Mbps você realmente precisa?
          </h2>
          <p className="mt-2 text-[16px] leading-6 text-[#49454F]">
            A velocidade necessária varia com a quantidade de pessoas e as atividades simultâneas na residência:
          </p>

          <div className="mt-6 grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">Até 50 Mbps</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">Uso básico</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Ideal para 1 a 2 pessoas navegando, assistindo a vídeos em Full HD e enviando mensagens.
              </p>
            </div>
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">100 a 300 Mbps</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">Família conectada</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Suporta streaming em 4K em mais de uma TV, chamadas de vídeo em home office e jogos online estáveis.
              </p>
            </div>
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="text-xs font-semibold uppercase tracking-wider text-[#79747E]">Acima de 500 Mbps</div>
              <div className="mt-1 font-mono-num font-black text-xl text-[#1C1B1F]">Alta demanda</div>
              <p className="mt-2 text-xs text-[#49454F] leading-5">
                Downloads rápidos de jogos pesados (50 GB+), múltiplos criadores de conteúdo e casas inteligentes com dezenas de aparelhos.
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
            Dúvidas sobre o teste de velocidade
          </h2>
          <FaqList
            items={[
              [
                "Por que o teste web pode dar diferente do plano contratado?",
                "O teste no navegador mede a velocidade que chega ao seu dispositivo pelo Wi-Fi no momento. Interferências no ar, paredes, distância do roteador e capacidade da placa de rede do celular limitam a velocidade antes do limite do cabo da operadora.",
              ],
              [
                "Qual a diferença entre velocidade de download e upload?",
                "Download é a velocidade para puxar dados da internet (carregar páginas, vídeos e baixar arquivos). Upload é a velocidade para enviar seus dados (enviar vídeos, fazer backups e transmitir sua câmera em chamadas).",
              ],
              [
                "O que é Bufferbloat no resultado?",
                "Bufferbloat é o aumento da latência quando a conexão está sob carga pesada de download ou upload. Quando o roteador enche seus buffers internos de memória, pacotes essenciais de chamadas e jogos sofrem atrasos.",
              ],
              [
                "Como o aplicativo SignallQ complementa este teste?",
                "Enquanto a ferramenta web mede apenas vazão pontual no navegador, o aplicativo SignallQ para Android analisa canais Wi-Fi, intensidade em dBm, rotas de rede, DNS e equipamentos compatíveis para diagnosticar a causa real dos travamentos.",
              ],
            ]}
          />
        </div>
      </section>

      {/* Banner de download do app */}
      <AppDownloadCTA />
    </div>
  )
}
