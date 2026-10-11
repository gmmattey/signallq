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

      <section className="mx-auto grid max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(320px,1fr))] items-center gap-14 px-6 pb-20 pt-[72px]">
        <div>
          <div className="text-[14px] font-medium text-[#5B21D6]">Teste de velocidade</div>
          <h1 className="m-0 mt-4 text-balance text-[clamp(38px,5.2vw,60px)] font-bold leading-[1.05] tracking-[-1.4px]">
            Quão rápida está sua internet agora?
          </h1>
          <p className="mt-6 max-w-[30em] text-pretty text-[20px] leading-[30px] text-[#49454F]">
            Leva cerca de 20 segundos. Você vê o resultado em palavras, não só em números.
          </p>
        </div>

        <div className="w-full rounded-[28px] border border-[#E7E0EC] bg-white p-7 shadow-[0_30px_60px_-28px_rgba(33,10,92,0.35)] sm:p-8">
          {state.phase === 'idle' && <SpeedTestIdle onStart={handleStart} />}
          {state.phase === 'running' && <SpeedTestRunning state={state} onCancel={cancel} />}
          {state.phase === 'done' && <SpeedTestResult state={state} onRetry={handleStart} />}
          {state.phase === 'error' && <SpeedTestError onRetry={handleStart} />}
        </div>
      </section>
    </div>
  )
}
