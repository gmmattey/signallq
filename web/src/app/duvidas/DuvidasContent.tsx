const GROUPS = [
  {
    t: 'Sobre o app',
    items: [
      { q: 'O SignallQ é só um teste de velocidade?', a: 'Não. Ele mede quando necessário, mas o foco é explicar a causa do problema e orientar a solução.' },
      { q: 'Preciso entender de redes?', a: 'Não. O resultado usa frases curtas e palavras comuns. O detalhe técnico é opcional.' },
      { q: 'Funciona em iPhone?', a: 'Por enquanto o app de diagnóstico é só para Android.' },
      { q: 'Quanto custa?', a: 'O app é gratuito, sem assinatura e sem compras dentro dele. Ele exibe anúncios do Google AdMob.' },
    ],
  },
  {
    t: 'Diagnóstico',
    items: [
      { q: 'O diagnóstico por IA resolve meu problema?', a: 'Ele é um apoio: analisa dados técnicos e recomenda ações com base em padrões conhecidos. Não é parecer profissional nem garantia. Se o problema persistir, fale com sua operadora.' },
      { q: 'Por que o app pede permissão de localização?', a: 'O Android exige essa permissão para liberar o nome e o canal do Wi-Fi. O app não rastreia sua localização por GPS.' },
      { q: 'As medições são sempre exatas?', a: 'Não. Velocidade varia com o momento e a rede, por isso o app indica o nível de confiança do resultado.' },
    ],
  },
  {
    t: 'Privacidade',
    items: [
      { q: 'O SignallQ coleta meus dados pessoais?', a: 'Não coletamos nome, e-mail, endereço nem localização GPS. O histórico fica no seu aparelho. Detalhes na Política de Privacidade.' },
      { q: 'Posso apagar meus dados?', a: 'Sim. Limpe o histórico em Ajustes, revogue o consentimento em Ajustes → Privacidade ou desinstale o app.' },
    ],
  },
]

const FAQ_JSON_LD = {
  '@context': 'https://schema.org',
  '@type': 'FAQPage',
  mainEntity: GROUPS.flatMap((g) =>
    g.items.map((item) => ({ '@type': 'Question', name: item.q, acceptedAnswer: { '@type': 'Answer', text: item.a } }))
  ),
}

export default function DuvidasContent() {
  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(FAQ_JSON_LD) }} />

      <section className="mx-auto grid max-w-[1168px] grid-cols-[repeat(auto-fit,minmax(320px,1fr))] items-center gap-14 px-6 pb-20 pt-[72px]">
        <div>
          <div className="text-[14px] font-medium text-[#5B21D6]">Dúvidas</div>
          <h1 className="m-0 mt-4 max-w-[14em] text-balance text-[clamp(38px,5.2vw,60px)] font-bold leading-[1.05] tracking-[-1.4px]">
            Perguntas comuns sobre o SignallQ
          </h1>
          <div className="mt-7 flex flex-wrap gap-2">
            {GROUPS.map((g, idx) => (
              <a
                key={g.t}
                href={`#g${idx}`}
                className="inline-flex h-8 items-center rounded-lg border border-[#CAC4D0] px-3 text-[14px] font-medium text-[#49454F] no-underline transition-colors hover:border-[#5B21D6] hover:text-[#5B21D6]"
              >
                {g.t}
              </a>
            ))}
          </div>
        </div>

        <div className="rounded-[28px] bg-[#F8F5FB] p-8">
          <div className="text-[22px] font-semibold leading-7">Não achou sua dúvida?</div>
          <div className="mt-[6px] text-[16px] text-[#49454F]">Escreva para a gente.</div>
          <a
            href="mailto:suporte@signallq.com"
            className="mt-5 inline-block rounded-full bg-[#5B21D6] px-6 py-[14px] text-[15px] font-medium text-white no-underline hover:bg-[#4416A8]"
          >
            suporte@signallq.com
          </a>
        </div>
      </section>

      <section className="px-6 pb-28">
        {GROUPS.map((g, idx) => (
          <div
            key={g.t}
            id={`g${idx}`}
            className="mx-auto grid max-w-[1120px] scroll-mt-20 grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-x-16 gap-y-4 border-t-2 border-[#1C1B1F] py-8 pb-12"
          >
            <h2 className="m-0 text-[28px] font-bold leading-[34px] tracking-[-0.5px]">{g.t}</h2>
            <div className="min-w-0 sm:col-span-2">
              {g.items.map((f) => (
                <details key={f.q} className="group border-b border-[#E7E0EC] py-5">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[18px] font-semibold leading-6 [&::-webkit-details-marker]:hidden">
                    <span>{f.q}</span>
                    <span className="text-[26px] font-normal leading-none text-[#5B21D6] transition-transform duration-150 group-open:rotate-45">
                      +
                    </span>
                  </summary>
                  <p className="m-0 mt-3 max-w-[36em] text-pretty text-[16px] leading-6 text-[#49454F]">
                    {f.a}
                  </p>
                </details>
              ))}
            </div>
          </div>
        ))}
      </section>
    </div>
  )
}
