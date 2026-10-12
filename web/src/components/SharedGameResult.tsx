import Image from 'next/image'
import Link from 'next/link'
import { coverOf } from '@/lib/share/gameCovers'
import { SIGNALLQ_PLAY_STORE_URL } from '@/lib/config'
import { gameCardScene } from '@/lib/share/gameCard'
import type { GameSharePayload } from '@/lib/share/gamePayload'

/** Página de um resultado do Teste Gamer compartilhado: só os números do link assinado e o convite para testar. */
export function SharedGameResult({ payload }: { payload: GameSharePayload }) {
  const s = gameCardScene(payload)
  const cover = coverOf(payload.game)
  const color = { ok: '#7DDB92', warn: '#FFB867', bad: '#FF8A7A' }[payload.verdict]
  const cells = [
    { label: 'Resposta', value: String(payload.ping), unit: 'ms', color: '#AAC7FF' },
    { label: 'Jitter', value: String(payload.jitter), unit: 'ms', color: '#7DDB92' },
    { label: 'Sob carga', value: payload.bloat === null ? '—' : `+${payload.bloat}`, unit: payload.bloat === null ? '' : 'ms', color: '#FFB867' },
  ]
  return (
    <div className="w-full bg-[#0B0B10] py-4">
      <section className="mx-auto w-full max-w-[880px] px-4 sm:px-6 py-12 text-[#F2EDF7]">
        <div className="flex items-center gap-4">
          {cover && <Image src={cover} alt="" width={64} height={64} className="block h-16 w-16 rounded-2xl object-cover" />}
          <div>
            <div className="text-[11px] font-bold uppercase tracking-wider text-[#D0BCFF]">Teste gamer compartilhado</div>
            <div className="mt-1 text-lg font-semibold text-white">{s.gameName}</div>
          </div>
        </div>
        <h1 className="m-0 mt-5 text-balance text-[clamp(30px,5vw,48px)] font-bold leading-[1.05] tracking-tight" style={{ color }}>{s.title}</h1>
        <p className="m-0 mt-3 max-w-[30em] text-lg text-[#CAC4D0]">
          Estes valores foram enviados pelo navegador de quem compartilhou o link. O SignallQ não verifica a origem da medição.
        </p>
        <dl className="m-0 mt-8 grid grid-cols-1 sm:grid-cols-3 gap-3">
          {cells.map((c) => (
            <div key={c.label} className="rounded-2xl border border-[#4A4458] bg-[#16151D] p-4">
              <dt className="text-xs font-semibold uppercase tracking-wider" style={{ color: c.color }}>{c.label}</dt>
              <dd className="m-0 mt-2 text-[34px] font-extrabold tracking-tight text-white">
                {c.value}
                <span className="ml-1 text-sm font-medium text-[#CAC4D0]">{c.unit}</span>
              </dd>
            </div>
          ))}
        </dl>
        <div className="mt-8 flex flex-wrap items-center gap-4">
          <Link href="/teste-gamer" className="rounded-full bg-[#7B4DF0] px-6 py-3 font-semibold text-white no-underline hover:bg-[#8D64F5] transition-colors">
            Teste a sua
          </Link>
          <a href={SIGNALLQ_PLAY_STORE_URL} target="_blank" rel="noopener noreferrer" className="rounded-lg">
            <Image src="/assets/google-play-badge-transparent.png" alt="Disponível no Google Play" width={149} height={44} className="block h-11 w-auto" />
          </a>
        </div>
        <p className="mt-6 text-xs text-[#CAC4D0]">
          A assinatura do link impede alterar os valores após o compartilhamento, mas não atesta a medição. <Link href="/como-funciona" className="underline underline-offset-2">Como medimos</Link>
        </p>
      </section>
    </div>
  )
}

