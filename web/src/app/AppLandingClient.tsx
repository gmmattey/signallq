"use client";
import { useEffect } from 'react'
import { useAppLanding } from './useAppLanding'
import { AppLandingHero, AppLandingCTA } from './AppLandingComponents'
import { HomeWebTools } from './HomeWebTools'
import { HowItWorksSteps } from './HowItWorksSteps'
import { ResultOrder } from './ResultOrder'
import { HomeFeatures } from './HomeFeatures'
import { OutrasSolucoesSection } from './OutrasSolucoesSection'
import { HomeGuidesSection } from './HomeGuidesSection'
import { TrustPromises } from './TrustPromises'
import { LandingFaq } from './LandingFaq'
import { trackScreenView } from '@/lib/telemetry'

const HOME_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: 'SignallQ',
  operatingSystem: 'Android',
  applicationCategory: 'UtilitiesApplication',
  description: 'Diagnóstico de conectividade: analisa Wi-Fi, velocidade e DNS, explica o problema e orienta a solução.',
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'BRL' },
  inLanguage: 'pt-BR',
  publisher: { '@type': 'Organization', name: 'SignallQ', url: 'https://signallq.com' },
}

export function AppLandingClient() {
  const { baixarNaPlayStore } = useAppLanding()

  useEffect(() => {
    trackScreenView('home')
  }, [])

  return (
    <div className="flex w-full flex-col bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(HOME_JSON_LD) }} />
      {/* 1. Hero dinâmico do SignallQ */}
      <AppLandingHero onBaixar={baixarNaPlayStore} />

      {/* 2. Ferramentas gratuitas que funcionam no navegador */}
      <HomeWebTools />

      {/* 3. Como funciona o diagnóstico: jornada em 4 etapas e ordem dos resultados */}
      <HowItWorksSteps />
      <ResultOrder />

      {/* 4 e 5. Benefícios exclusivos do Android e monitoramento contínuo/histórico */}
      <HomeFeatures />

      {/* 6. Outros aplicativos de conectividade SignallQ */}
      <OutrasSolucoesSection />

      {/* 7. Guias práticos e perguntas frequentes */}
      <HomeGuidesSection />
      <LandingFaq />

      {/* 8. Compromissos e CTA final */}
      <TrustPromises />
      <AppLandingCTA onBaixar={baixarNaPlayStore} />
    </div>
  )
}

