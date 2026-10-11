"use client";

import { trackFeatureUsed } from '@/lib/telemetry'
import { SITE_ORIGIN } from '@/lib/routeMetadata'
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
      </section>
    </div>
  )
}
