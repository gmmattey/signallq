import Link from "next/link";

interface FeatureCard {
  t: string;
  d: string;
  href: string;
  badge: string;
}

const APP_EXCLUSIVES: FeatureCard[] = [
  {
    t: "Canais do Wi-Fi",
    d: "Varredura das redes vizinhas para descobrir o canal menos congestionado para o seu roteador.",
    href: "/canais-wifi",
    badge: "Wi-Fi e Rádio",
  },
  {
    t: "Relatório em PDF",
    d: "Histórico auditado com dados mascarados pronto para protocolar chamado na operadora.",
    href: "/relatorio-pdf",
    badge: "Operadora",
  },
  {
    t: "Sinal Wi-Fi em dBm",
    d: "Leitura de potência real de rádio cômodo por cômodo, sem depender de barrinhas genéricas.",
    href: "/sinal-wifi",
    badge: "Hardware",
  },
  {
    t: "Dispositivos na rede",
    d: "Identifica todos os aparelhos conectados ao seu roteador e detecta conexões não autorizadas.",
    href: "/quem-esta-na-minha-rede",
    badge: "Rede Local",
  },
  {
    t: "Mapa de calor do Wi-Fi",
    d: "Mapeie visualmente os pontos cegos da sua casa e compare antes e depois de mudar o roteador.",
    href: "/mapa-de-calor-wifi",
    badge: "Cobertura",
  },
  {
    t: "Sinal 4G e 5G",
    d: "Diagnóstico da rede móvel e leitura da intensidade do sinal da torre da operadora celular.",
    href: "/sinal-4g-5g",
    badge: "Rede Móvel",
  },
];

export function HomeFeatures() {
  return (
    <section id="funcionalidades" className="scroll-mt-20 border-t border-[#F3EEFA] bg-white px-6 py-24">
      <div className="mx-auto w-full max-w-[1168px]">
        <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div className="max-w-[36em]">
            <div className="text-[14px] font-semibold uppercase tracking-wider text-[#5B21D6]">
              Exclusivo no Android
            </div>
            <h2 className="m-0 mt-2 text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
              O que só o app faz.
            </h2>
            <p className="mt-3 text-pretty text-[18px] leading-7 text-[#49454F]">
              Por questões de segurança, navegadores web não têm acesso ao hardware do celular ou à interface de rádio. O aplicativo SignallQ realiza diagnósticos profundos que nenhum site consegue fazer.
            </p>
          </div>
          <Link
            href="/funcionalidades"
            className="text-[16px] font-semibold text-[#5B21D6] no-underline hover:text-[#4416A8] transition-colors"
          >
            Ver todos os recursos →
          </Link>
        </div>

        <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-6">
          {APP_EXCLUSIVES.map((c) => (
            <Link
              key={c.t}
              href={c.href}
              className="group flex flex-col justify-between rounded-[28px] border border-[#E7E0EC] bg-[#FAF8FC] p-8 text-[#1C1B1F] no-underline transition-all hover:-translate-y-1 hover:border-[#D5C2FF] hover:bg-white hover:shadow-md"
            >
              <div>
                <span className="rounded-full bg-[#EAE0FF] px-3 py-1 text-[11px] font-bold text-[#210A5C]">
                  {c.badge}
                </span>
                <div className="mt-4 text-[22px] font-bold leading-7 text-[#1C1B1F] group-hover:text-[#5B21D6] transition-colors">
                  {c.t}
                </div>
                <p className="mt-2.5 text-[15px] leading-6 text-[#49454F]">{c.d}</p>
              </div>
              <div className="mt-6 pt-4 border-t border-[#F3EEFA]">
                <span className="text-[14px] font-semibold text-[#5B21D6] group-hover:translate-x-1 inline-flex items-center gap-1 transition-transform">
                  Saiba mais no app →
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}

