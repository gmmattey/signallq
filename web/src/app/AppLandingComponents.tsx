import Link from 'next/link'
import { HeroCarousel } from './HeroCarousel'
import { SIGNALLQ_PLAY_STORE_URL } from '@/lib/config'
import { FEATURE_HERO_TESTAR_INTERNET_CLICADO, trackFeatureUsed } from '@/lib/telemetry'

const PRIMARY = 'rounded-full bg-[#5B21D6] font-medium text-white no-underline hover:bg-[#4A18B0] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]'

export function AppLandingHero({ onBaixar }: { onBaixar: () => void }) {
  return (
    <section
      id="topo"
      className="mx-auto w-full max-w-[1168px] px-6 pb-16 pt-12 lg:pb-[88px] lg:pt-20"
    >
      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] items-center gap-y-10 lg:gap-x-12 xl:gap-x-16">
        {/* Bloco A: Título principal, descrição e botões de ação */}
        <div className="order-1 lg:order-none lg:col-start-1 lg:row-start-1">
          <div className="mb-4 lg:mb-5 text-[14px] font-medium text-[#5B21D6]">
            Diagnóstico de internet para Android
          </div>
          <h1 className="m-0 text-balance text-[clamp(36px,5.2vw,64px)] font-bold leading-[1.04] tracking-[-1.5px] text-[#1C1B1F]">
            Descubra por que sua internet está ruim.
          </h1>
          <p className="mt-5 lg:mt-6 max-w-[30em] text-pretty text-[18px] lg:text-[20px] leading-[28px] lg:leading-[30px] text-[#49454F]">
            O SignallQ analisa sua rede, mostra onde está o problema e diz o que fazer. Depois, ajuda você a verificar se resolveu.
          </p>
          <div className="mt-8 lg:mt-9 flex flex-wrap gap-3">
            <a
              href={SIGNALLQ_PLAY_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              onClick={onBaixar}
              className={`${PRIMARY} inline-flex items-center justify-center px-7 py-4 text-[16px] font-semibold shadow-[0_8px_20px_-6px_rgba(91,33,214,0.5)]`}
            >
              Baixar grátis no Google Play
            </a>
            <Link
              href="/teste-de-velocidade"
              onClick={() => trackFeatureUsed(FEATURE_HERO_TESTAR_INTERNET_CLICADO)}
              className="rounded-full bg-[#F3EEFA] hover:bg-[#EAE0FF] px-7 py-4 text-[16px] font-medium text-[#210A5C] no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
            >
              Testar minha internet
            </Link>
          </div>
        </div>

        {/* Bloco B: Vitrine dinâmica de funcionalidades reais (HeroCarousel) */}
        <div className="order-2 lg:order-none lg:col-start-2 lg:row-start-1 lg:row-span-2 flex justify-center">
          <HeroCarousel />
        </div>

        {/* Bloco C: Informações complementares */}
        <div className="order-3 lg:order-none lg:col-start-1 lg:row-start-2 pt-1 lg:pt-0">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[14px] text-[#49454F]">
            <span>Grátis · Sem cadastro para começar</span>
            <span aria-hidden="true">·</span>
            <span>
              Usa iPhone?{' '}
              <a href="#outras-solucoes" className="text-[#5B21D6] font-medium underline">
                Conheça o Linka
              </a>
            </span>
          </div>
        </div>
      </div>
    </section>
  )
}

export function AppLandingCTA({ onBaixar }: { onBaixar: () => void }) {
  return (
    <section id="baixar" className="scroll-mt-20 px-6 pb-28 pt-6">
      <div className="mx-auto max-w-[1120px] rounded-[28px] bg-[#EAE0FF] px-8 py-[72px] text-center">
        <h2 className="mx-auto max-w-[14em] text-balance text-[clamp(30px,4vw,46px)] font-bold leading-[1.08] tracking-[-1px] text-[#210A5C]">
          Sua internet está ruim agora? Descubra em minutos.
        </h2>
        <a
          href={SIGNALLQ_PLAY_STORE_URL}
          target="_blank"
          rel="noopener noreferrer"
          onClick={onBaixar}
          className={`${PRIMARY} mt-8 inline-block px-8 py-4 text-[16px] font-semibold shadow-[0_8px_20px_-6px_rgba(91,33,214,0.5)]`}
        >
          Baixar grátis no Google Play
        </a>
      </div>
    </section>
  )
}

