"use client";

import Link from 'next/link'
import { AppDownloadCTA } from '@/components/AppDownloadCTA'
import { trackFeatureUsed } from '@/lib/telemetry'
import { SITE_ORIGIN } from '@/lib/routeMetadata'
import { usePingTestEngine } from './usePingTestEngine'
import { PingTestIdle } from './PingTestIdle'
import { PingTestRunning } from './PingTestRunning'
import { PingTestResult } from './PingTestResult'
import { PingTestError } from './PingTestError'

const JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'WebApplication',
  name: 'Teste de ping e latência SignallQ',
  url: `${SITE_ORIGIN}/teste-de-ping`,
  applicationCategory: 'UtilitiesApplication',
  operatingSystem: 'Any',
  inLanguage: 'pt-BR',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'BRL' },
}

export const FEATURE_TESTE_PING_INICIADO = 'teste_ping_iniciado'

const FAQ_ITEMS: [string, string][] = [
  [
    'Qual ping é considerado bom para jogar online?',
    'Até 30 ms é considerado excelente para qualquer jogo competitivo (FPS, MOBA). Entre 30 e 60 ms ainda é muito bom; de 60 a 100 ms você pode sentir pequenos atrasos; acima de 100 ms ocorrem desvantagens visíveis de delay e hit-registration.',
  ],
  [
    'Ping e velocidade de download são a mesma coisa?',
    'Não. A velocidade (Mbps) mede o volume de dados transferidos por segundo. O ping (ms) mede o tempo que uma mensagem leva para ir ao servidor e voltar. Uma conexão de 500 Mbps pode ter ping alto se o Wi-Fi estiver instável ou a rota congestionada.',
  ],
  [
    'O que é o Jitter no teste de ping?',
    'Jitter é a variação do ping ao longo do tempo. Se um pacote demora 20 ms e o próximo demora 70 ms, há jitter de 50 ms. O jitter alto é a principal causa de travadas e engasgos em chamadas de voz, vídeo e jogos.',
  ],
  [
    'O que é Bufferbloat?',
    'Bufferbloat é o aumento súbito da latência quando alguém na casa começa a baixar ou enviar arquivos pesados, enchendo a fila de memória do roteador.',
  ],
]

export function PingTestContent() {
  const { state, start, cancel } = usePingTestEngine()

  function handleStart() {
    trackFeatureUsed(FEATURE_TESTE_PING_INICIADO)
    start()
  }

  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }} />

      <section className="mx-auto max-w-[840px] px-4 sm:px-6 pb-20 pt-10 md:pt-14 text-center">
        {/* Eyebrow & Headline alinhados ao padrão do Teste de Velocidade */}
        <div className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Teste de ping e latência
        </div>
        <h1 className="m-0 mt-3 text-balance text-[clamp(32px,4.5vw,48px)] font-bold leading-[1.1] tracking-[-1.2px] text-[#1C1B1F]">
          Qual é o ping da sua internet?
        </h1>
        <p className="mx-auto mt-3.5 max-w-[28em] text-pretty text-[17px] sm:text-[19px] leading-[26px] sm:leading-[28px] text-[#49454F]">
          Mede tempo de resposta, estabilidade (jitter) e latência sob carga em tempo real. Você vê o resultado em palavras, não só em números.
        </p>

        {/* Card Principal Elevado com o Teste Interativo */}
        <div className="mt-8 w-full rounded-[32px] border border-[#E7E0EC] bg-white p-5 sm:p-8 md:p-10 shadow-[0_20px_50px_-20px_rgba(33,10,92,0.2)]">
          {state.phase === 'idle' && <PingTestIdle onStart={handleStart} />}
          {state.phase === 'running' && <PingTestRunning state={state} onCancel={cancel} />}
          {state.phase === 'done' && <PingTestResult state={state} onRetry={handleStart} />}
          {state.phase === 'error' && <PingTestError onRetry={handleStart} />}
        </div>

        {/* Seção Educativa e Guia de Referência */}
        <div className="mt-16 text-left border-t border-[#E7E0EC] pt-12">
          <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
            Guia de Latência
          </div>
          <h2 className="mt-2 text-2xl sm:text-3xl font-bold tracking-tight text-[#1C1B1F]">
            Como interpretar o resultado do ping
          </h2>
          <p className="mt-2 text-[16px] leading-6 text-[#49454F]">
            Quanto menor o ping em milissegundos (ms), mais ágil e imediata é a resposta da sua conexão:
          </p>

          <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono-num font-extrabold text-lg text-[#1C1B1F]">Até 30 ms</span>
                <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#E8F5E9] text-[#146C2E]">Excelente</span>
              </div>
              <p className="mt-1 text-xs text-[#49454F] leading-5">
                Sem atraso perceptível. Resposta instantânea em jogos e chamadas de voz.
              </p>
            </div>

            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono-num font-extrabold text-lg text-[#1C1B1F]">30 a 60 ms</span>
                <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#F3EEFA] text-[#5B21D6]">Bom</span>
              </div>
              <p className="mt-1 text-xs text-[#49454F] leading-5">
                Atende bem qualquer atividade, incluindo partidas online e videoconferências em HD.
              </p>
            </div>

            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono-num font-extrabold text-lg text-[#1C1B1F]">60 a 100 ms</span>
                <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#FFF3E0] text-[#B06000]">Regular</span>
              </div>
              <p className="mt-1 text-xs text-[#49454F] leading-5">
                Razoável para navegação, mas jogos de tiro e reflexo rápido já começam a sentir atraso.
              </p>
            </div>

            <div className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono-num font-extrabold text-lg text-[#1C1B1F]">Acima de 100 ms</span>
                <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-[#FFEBEE] text-[#BA1A1A]">Alto</span>
              </div>
              <p className="mt-1 text-xs text-[#49454F] leading-5">
                Atraso perceptível em jogos e chamadas. Voz pode picotar ou ficar dessincronizada.
              </p>
            </div>
          </div>

          {/* Dicas práticas */}
          <div className="mt-10 rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-6">
            <h3 className="text-lg font-bold text-[#1C1B1F]">O que fazer se o ping estiver alto?</h3>
            <ul className="mt-3 space-y-2.5 text-sm text-[#49454F] pl-4 list-disc">
              <li><strong>Aproxime-se do roteador Wi-Fi</strong> ou conecte via cabo de rede para eliminar interferências de frequência.</li>
              <li><strong>Pause downloads pesados e torrents</strong> na rede enquanto estiver jogando ou em chamadas importantes.</li>
              <li><strong>Verifique se não há VPN ativada</strong>, pois túneis criptografados aumentam o caminho dos pacotes.</li>
              <li><strong>Reinicie o roteador</strong> para limpar a memória temporária de pacotes e tabelas NAT acumuladas.</li>
            </ul>
          </div>

          {/* Perguntas Frequentes (FAQ) */}
          <div className="mt-12">
            <h3 className="text-xl font-bold text-[#1C1B1F] mb-4">Perguntas frequentes sobre ping</h3>
            <div className="divide-y divide-[#E7E0EC]">
              {FAQ_ITEMS.map(([q, a]) => (
                <div key={q} className="py-4">
                  <h4 className="text-base font-semibold text-[#1C1B1F]">{q}</h4>
                  <p className="mt-1.5 text-sm text-[#49454F] leading-6">{a}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Links para ferramentas irmãs */}
          <div className="mt-12 pt-8 border-t border-[#E7E0EC] flex flex-wrap items-center justify-between gap-4">
            <div>
              <div className="text-xs uppercase font-bold text-[#79747E] tracking-wider">Outras ferramentas</div>
              <div className="text-sm font-semibold text-[#1C1B1F] mt-0.5">Explore testes complementares de conectividade</div>
            </div>
            <div className="flex flex-wrap gap-2.5">
              <Link
                href="/teste-de-velocidade"
                className="px-4 py-2 rounded-full border border-[#E7E0EC] bg-white text-xs font-semibold text-[#5B21D6] hover:bg-[#F3EEFA] transition-colors"
              >
                Teste de velocidade
              </Link>
              <Link
                href="/teste-de-jitter"
                className="px-4 py-2 rounded-full border border-[#E7E0EC] bg-white text-xs font-semibold text-[#5B21D6] hover:bg-[#F3EEFA] transition-colors"
              >
                Teste de jitter
              </Link>
              <Link
                href="/teste-de-dns"
                className="px-4 py-2 rounded-full border border-[#E7E0EC] bg-white text-xs font-semibold text-[#5B21D6] hover:bg-[#F3EEFA] transition-colors"
              >
                Teste de DNS
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Banner de download do app */}
      <AppDownloadCTA />
    </div>
  )
}
