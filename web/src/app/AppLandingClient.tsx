"use client";
import { useEffect } from 'react'
import { useAppLanding } from './useAppLanding'
import { AppLandingHero, AppLandingCTA } from './AppLandingComponents'
import { HomeBenefits } from './HomeBenefits'
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
  publisher: { '@type': 'Organization', name: '7Agents Tecnologia', url: 'https://signallq.com' },
}

export function AppLandingClient() {
  const { baixarNaPlayStore } = useAppLanding()

  useEffect(() => {
    trackScreenView('home')
  }, [])

  return (
    <div className="flex w-full flex-col bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(HOME_JSON_LD) }} />
      {/* 1. Hero comercial do SignallQ */}
      <AppLandingHero onBaixar={baixarNaPlayStore} />

      {/* 2. Benefícios concretos do aplicativo e demonstração do diagnóstico */}
      <HomeBenefits />

      {/* 3. Ferramentas gratuitas que funcionam no navegador */}
      <HomeWebTools />

      {/* 4. Explicação da jornada: problema → medição → recomendação → verificação */}
      <HowItWorksSteps />
      <ResultOrder />

      {/* 5. Diferenciais do aplicativo Android em relação ao navegador */}
      <HomeFeatures />

      {/* 6. Seção "Outras soluções SignallQ" */}
      <OutrasSolucoesSection />

      {/* 7. Guias, dúvidas frequentes e CTA final */}
      <HomeGuidesSection />
      <LandingFaq />
      <TrustPromises />
      <AppLandingCTA onBaixar={baixarNaPlayStore} />
    </div>
  )
}

