import Link from 'next/link'
import { AppScreenshot } from '@/components/AppScreenshot'
import { APPROVED_SCREENSHOTS } from '@/lib/approvedScreenshotsManifest'
import { SIGNALLQ_PLAY_STORE_URL } from '@/lib/config'

const PRIMARY = 'rounded-full bg-[#5B21D6] font-medium text-white no-underline hover:bg-[#4A18B0] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]'

export function AppLandingHero({ onBaixar }: { onBaixar: () => void }) {
  return (
    <section
      id="topo"
      className="mx-auto grid w-full max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(320px,1fr))] items-center gap-16 px-6 pb-[88px] pt-20"
    >
      <div>
        <div className="mb-5 text-[14px] font-medium text-[#5B21D6]">Diagnóstico de internet para Android</div>
        <h1 className="m-0 text-balance text-[clamp(40px,5.6vw,64px)] font-bold leading-[1.04] tracking-[-1.5px]">
          Descubra por que sua internet está ruim.
        </h1>
        <p className="mt-6 max-w-[30em] text-pretty text-[20px] leading-[30px] text-[#49454F]">
          O SignallQ analisa sua rede, mostra onde está o problema e diz o que fazer. Depois, confirma se resolveu.
        </p>
        <div className="mt-9 flex flex-wrap gap-3">
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
            className="rounded-full bg-[#F3EEFA] hover:bg-[#EAE0FF] px-7 py-4 text-[16px] font-medium text-[#210A5C] no-underline transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#5B21D6]"
          >
            Testar minha internet
          </Link>
        </div>
        <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-1 text-[14px] text-[#49454F]">
          <span>Grátis · Sem cadastro para começar</span>
          <span>·</span>
          <span>Usa iPhone? <a href="#outras-solucoes" className="text-[#5B21D6] font-medium underline">Conheça o Linka</a></span>
        </div>
      </div>
      <div className="flex justify-center">
        <AppScreenshot
          screenshot={APPROVED_SCREENSHOTS.homeDiagnostico}
          priority
          maxWidth={310}
        />
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

