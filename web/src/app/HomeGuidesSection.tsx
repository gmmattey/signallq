import Link from "next/link";

interface GuideCard {
  titulo: string;
  descricao: string;
  href: string;
  leitura: string;
}

export function HomeGuidesSection() {
  const guias: GuideCard[] = [
    {
      titulo: "Por que minha internet está lenta?",
      descricao: "Wi-Fi, roteador, operadora ou DNS: aprenda a separar a causa e resolver sem achismo.",
      href: "/guias/por-que-minha-internet-esta-lenta",
      leitura: "4 min de leitura",
    },
    {
      titulo: "Como melhorar o sinal do Wi-Fi",
      descricao: "Posicionamento do roteador, diferença entre 2,4 GHz e 5 GHz e escolha de canais livres.",
      href: "/guias/como-melhorar-sinal-wifi",
      leitura: "5 min de leitura",
    },
    {
      titulo: "O que são latência, jitter e DNS",
      descricao: "Entenda por que a velocidade não é a única coisa que importa para chamadas e jogos.",
      href: "/guias/latencia-jitter-dns",
      leitura: "4 min de leitura",
    },
    {
      titulo: "Qual velocidade de internet eu preciso?",
      descricao: "Referência prática de Mbps por pessoa e aparelho para streaming, trabalho e jogos.",
      href: "/guias/qual-velocidade-de-internet-preciso",
      leitura: "3 min de leitura",
    },
  ];

  return (
    <section className="border-t border-[#F3EEFA] bg-white px-6 py-24">
      <div className="mx-auto max-w-[1168px]">
        <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div className="max-w-[36em]">
            <div className="text-[14px] font-semibold uppercase tracking-wider text-[#5B21D6]">
              Conteúdo educativo
            </div>
            <h2 className="m-0 mt-2 text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
              Guias práticos para melhorar sua conexão.
            </h2>
            <p className="mt-3 text-pretty text-[18px] leading-7 text-[#49454F]">
              Artigos sem jargão técnico para entender e solucionar problemas de internet.
            </p>
          </div>
          <Link
            href="/guias"
            className="text-[16px] font-semibold text-[#5B21D6] no-underline hover:text-[#4416A8] transition-colors"
          >
            Ver todos os guias →
          </Link>
        </div>

        <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-6">
          {guias.map((g) => (
            <Link
              key={g.titulo}
              href={g.href}
              className="group flex flex-col justify-between rounded-3xl border border-[#E7E0EC] bg-[#FAF8FC] p-7 text-[#1C1B1F] no-underline transition-all hover:-translate-y-1 hover:border-[#D5C2FF] hover:bg-white hover:shadow-md"
            >
              <div>
                <span className="text-[12px] font-semibold text-[#79747E]">{g.leitura}</span>
                <div className="mt-3 text-[19px] font-bold leading-6 text-[#1C1B1F] group-hover:text-[#5B21D6] transition-colors">
                  {g.titulo}
                </div>
                <p className="mt-2.5 text-[15px] leading-6 text-[#49454F]">{g.descricao}</p>
              </div>
              <div className="mt-6 pt-4 border-t border-[#E7E0EC]">
                <span className="text-[14px] font-semibold text-[#5B21D6] group-hover:translate-x-1 inline-flex items-center gap-1 transition-transform">
                  Ler guia →
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
