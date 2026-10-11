import Link from 'next/link'
import { SITE_ORIGIN } from '@/lib/routeMetadata'
import { getRelatedGuides, type Guide } from '../guides-data'

export default function GuideContent({ guide }: { guide: Guide }) {
  const related = getRelatedGuides(guide.slug)
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: guide.title,
    description: guide.summary,
    inLanguage: 'pt-BR',
    mainEntityOfPage: `${SITE_ORIGIN}/guias/${guide.slug}`,
    datePublished: '2026-10-10',
    dateModified: '2026-10-10',
    author: { '@type': 'Organization', name: 'SignallQ' },
    publisher: { '@type': 'Organization', name: '7Agents Tecnologia', url: SITE_ORIGIN },
  }

  return (
    <article className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />

      <section className="mx-auto grid max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(320px,1fr))] items-center gap-14 px-6 pb-20 pt-[72px]">
        <div>
          <Link
            href="/guias"
            className="inline-flex h-8 items-center rounded-lg border border-[#CAC4D0] px-3 text-[14px] font-medium text-[#49454F] no-underline transition-colors hover:border-[#5B21D6] hover:text-[#5B21D6]"
          >
            ← Todos os guias
          </Link>
          <h1 className="m-0 mt-4 text-balance text-[clamp(38px,5.2vw,60px)] font-bold leading-[1.05] tracking-[-1.4px]">
            {guide.title}
          </h1>
          <p className="mt-6 max-w-[30em] text-pretty text-[20px] leading-[30px] text-[#49454F]">
            {guide.intro}
          </p>
        </div>

        <div className="rounded-[28px] bg-[#F8F5FB] p-7">
          <div className="text-[14px] font-semibold text-[#49454F]">Neste guia</div>
          <div className="mt-3">
            {guide.sections.map((section, idx) => (
              <a
                key={section.h}
                href={`#s${idx}`}
                className="block border-t border-[#E7E0EC] py-3 text-[17px] font-medium text-[#1C1B1F] no-underline hover:text-[#5B21D6]"
              >
                {section.h}
              </a>
            ))}
          </div>
        </div>
      </section>

      <section className="px-6 py-6">
        {guide.sections.map((section, idx) => (
          <div
            key={section.h}
            id={`s${idx}`}
            className="mx-auto grid max-w-[1120px] scroll-mt-20 grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-x-16 gap-y-6 border-t-2 border-[#1C1B1F] py-9 pb-14"
          >
            <h2 className="m-0 text-[28px] font-bold leading-[34px] tracking-[-0.5px]">{section.h}</h2>
            <div>
              {section.ps.map((p) => (
                <p key={p} className="m-0 mb-[14px] max-w-[36em] text-pretty text-[18px] leading-7 text-[#49454F]">
                  {p}
                </p>
              ))}
            </div>
          </div>
        ))}
      </section>

      <section className="px-6 py-12">
        <div className="mx-auto max-w-[1120px] rounded-[28px] bg-[#EAE0FF] px-8 py-14 text-center">
          <h2 className="mx-auto max-w-[14em] text-balance text-[clamp(26px,3.6vw,38px)] font-bold leading-[1.1] tracking-[-1px] text-[#210A5C]">
            {guide.ctaTitle}
          </h2>
          <p className="mt-3 text-[17px] text-[#210A5C]">{guide.ctaText}</p>
          <Link
            href={guide.ctaHref}
            className="mt-6 inline-block rounded-full bg-[#5B21D6] px-8 py-4 text-[16px] font-medium text-white no-underline hover:bg-[#4416A8]"
          >
            {guide.ctaLabel}
          </Link>
        </div>
      </section>

      <section className="mx-auto max-w-[1168px] px-6 pb-28 pt-14">
        <div className="mb-4 text-[14px] font-semibold text-[#49454F]">Leia também</div>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-4">
          {related.map((r) => (
            <Link
              key={r.slug}
              href={`/guias/${r.slug}`}
              className="block rounded-3xl bg-[#F8F5FB] p-6 text-[19px] font-semibold leading-[25px] text-[#1C1B1F] no-underline transition-colors hover:bg-[#EAE0FF]"
            >
              {r.title}
            </Link>
          ))}
        </div>
      </section>
    </article>
  )
}
