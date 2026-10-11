import Link from 'next/link'
import { GUIDES } from './guides-data'

export default function GuiasIndexContent() {
  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <section className="mx-auto max-w-[1168px] px-6 pb-12 pt-[72px]">
        <div className="text-[14px] font-medium text-[#5B21D6]">Guias</div>
        <h1 className="m-0 mt-4 max-w-[14em] text-balance text-[clamp(38px,5.2vw,60px)] font-bold leading-[1.05] tracking-[-1.4px]">
          Entenda sua internet, sem jargão.
        </h1>
      </section>

      <section className="mx-auto grid max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-4 px-6 pb-28 pt-6">
        {GUIDES.map((guide) => (
          <Link
            key={guide.slug}
            href={`/guias/${guide.slug}`}
            className="flex flex-col rounded-[28px] bg-[#F8F5FB] p-8 text-[#1C1B1F] no-underline transition-transform hover:-translate-y-1"
          >
            <div className="text-[26px] font-bold leading-8 tracking-[-0.4px]">{guide.title}</div>
            <p className="mt-3 max-w-[30em] text-[16px] leading-6 text-[#49454F]">{guide.summary}</p>
            <span className="mt-auto pt-5 text-[15px] font-semibold text-[#5B21D6]">Ler guia →</span>
          </Link>
        ))}
      </section>
    </div>
  )
}
