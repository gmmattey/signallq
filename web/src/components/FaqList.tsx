// Lista de perguntas do protótipo: <details> nativo, sinal "+" que gira ao abrir.
export type FaqEntry = { q: string; a: string } | [string, string]

export function FaqList({ items }: { items: FaqEntry[] }) {
  const normalized = items.map((item) =>
    Array.isArray(item) ? { q: item[0], a: item[1] } : item
  )

  return (
    <>
      {normalized.map((f, idx) => (
        <details key={`${idx}-${f.q}`} className="group border-t border-[#E7E0EC] py-5">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[18px] font-semibold [&::-webkit-details-marker]:hidden">
            {f.q}
            <span aria-hidden="true" className="text-[26px] font-normal leading-none text-[#5B21D6] transition-transform duration-150 group-open:rotate-45">+</span>
          </summary>
          <p className="mt-3 max-w-[36em] text-pretty text-[16px] leading-6 text-[#49454F]">{f.a}</p>
        </details>
      ))}
    </>
  )
}
