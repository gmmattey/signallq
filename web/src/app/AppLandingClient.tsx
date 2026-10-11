"use client";
import { useAppLanding } from './useAppLanding'
import { AppLandingHero, AppLandingCTA } from './AppLandingComponents'
import { HomeFeatures } from './HomeFeatures'
import { HowItWorksSteps } from './HowItWorksSteps'
import { ResultOrder } from './ResultOrder'
import { TrustPromises } from './TrustPromises'
import { LandingFaq } from './LandingFaq'

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

  return (
    <div className="flex w-full flex-col bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(HOME_JSON_LD) }} />
      <AppLandingHero onBaixar={baixarNaPlayStore} />
      <HomeFeatures />
      <HowItWorksSteps />
      <ResultOrder />
      <TrustPromises />
      <LandingFaq />
      <AppLandingCTA onBaixar={baixarNaPlayStore} />
    </div>
  )
}
