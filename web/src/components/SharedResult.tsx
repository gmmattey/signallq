import Image from 'next/image'
import Link from 'next/link'
import { cardHeadline, cardNumber } from '@/lib/share/card'
import { SIGNALLQ_PLAY_STORE_URL } from '@/lib/config'

interface Props {
  download: number
  upload: number
  ping: number
  jitter: number
}

/** Página de um resultado compartilhado: só os números do link assinado e o convite para medir a sua. */
export function SharedResult({ download, upload, ping, jitter }: Props) {
  const cells = [
    { label: 'Download', value: cardNumber(download), unit: 'Mbps', color: '#146C2E' },
    { label: 'Upload', value: cardNumber(upload), unit: 'Mbps', color: '#8A5000' },
    { label: 'Ping', value: String(ping), unit: 'ms', color: '#2563EB' },
    { label: 'Jitter', value: String(jitter), unit: 'ms', color: '#49454F' },
  ]
  return (
    <section className="mx-auto w-full max-w-[880px] px-4 sm:px-6 py-12 text-[#1C1B1F]">
      <div className="text-[11px] font-bold uppercase tracking-wider text-[#5B21D6]">Resultado compartilhado</div>
      <h1 className="m-0 mt-3 text-[clamp(34px,6vw,56px)] font-bold leading-[1.05] tracking-tight">{cardHeadline(download)}</h1>
      <p className="m-0 mt-3 max-w-[30em] text-lg text-[#49454F]">
        Estes valores foram enviados pelo navegador de quem compartilhou o link. O SignallQ não verifica a origem da medição.
      </p>
      <dl className="m-0 mt-8 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
        {cells.map((c) => (
          <div key={c.label} className="rounded-2xl border border-[#E7E0EC] bg-[#F8F9FA] p-4">
            <dt className="text-xs font-semibold uppercase tracking-wider" style={{ color: c.color }}>{c.label}</dt>
            <dd className="m-0 mt-2 text-[34px] font-extrabold tracking-tight">
              {c.value}
              <span className="ml-1 text-sm font-medium text-[#79747E]">{c.unit}</span>
            </dd>
          </div>
        ))}
      </dl>
      <div className="mt-8 flex flex-wrap items-center gap-4">
        <Link href="/teste-de-velocidade" className="rounded-full bg-[#5B21D6] px-6 py-3 font-semibold text-white no-underline hover:bg-[#6D28D9] transition-colors">
          Teste a sua
        </Link>
        <a href={SIGNALLQ_PLAY_STORE_URL} target="_blank" rel="noopener noreferrer" className="rounded-lg">
          <Image src="/assets/google-play-badge-transparent.png" alt="Disponível no Google Play" width={149} height={44} className="block h-11 w-auto" />
        </a>
      </div>
      <p className="mt-6 text-xs text-[#79747E]">
        A assinatura do link impede alterar os valores após o compartilhamento, mas não atesta a medição. <Link href="/como-funciona" className="underline underline-offset-2">Como medimos</Link>
      </p>
    </section>
  )
}
