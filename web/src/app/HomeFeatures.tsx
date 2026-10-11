import Link from "next/link";

interface FeatureCard {
  t: string;
  d: string;
  href: string;
}

const STARS: FeatureCard[] = [
  {
    t: "Modo Jogos",
    d: "Veja se sua conexão serve para o seu jogo e aparelho.",
    href: "/modo-gamer",
  },
  {
    t: "Relatório em PDF",
    d: "Relatório de diagnóstico e histórico para levar à operadora.",
    href: "/relatorio-pdf",
  },
  {
    t: "Canais do Wi-Fi",
    d: "Descubra o canal mais livre para o seu roteador.",
    href: "/canais-wifi",
  },
];

const FEATS: FeatureCard[] = [
  {
    t: "Teste de ping",
    d: "Quanto tempo sua conexão leva para responder.",
    href: "/teste-de-ping",
  },
  {
    t: "Download e upload",
    d: "Quantos Mbps chegam para baixar e enviar.",
    href: "/teste-de-download-e-upload",
  },
  {
    t: "Teste de DNS",
    d: "Qual DNS responde mais rápido para você.",
    href: "/teste-de-dns",
  },
  {
    t: "Sinal Wi-Fi",
    d: "O que o dBm do sinal significa de verdade.",
    href: "/sinal-wifi",
  },
  {
    t: "Dispositivos na rede",
    d: "Veja os aparelhos conectados ao seu Wi-Fi.",
    href: "/quem-esta-na-minha-rede",
  },
  {
    t: "Mapa de calor do Wi-Fi",
    d: "O sinal do Wi-Fi em cada cômodo da casa.",
    href: "/mapa-de-calor-wifi",
  },
  {
    t: "Sinal 4G e 5G",
    d: "Qualidade do sinal da rede móvel.",
    href: "/sinal-4g-5g",
  },
  {
    t: "Jitter e perda de pacotes",
    d: "Oscilação da latência e perda de pacotes.",
    href: "/teste-de-jitter",
  },
];

export function HomeFeatures() {
  return (
    <section id="funcionalidades" className="mx-auto w-full max-w-[1168px] px-6 pb-24 pt-4">
      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <h2 className="m-0 max-w-[16em] text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px]">
          O que só o app faz.
        </h2>
      </div>

      <div className="mb-24 mt-10 grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-4">
        {STARS.map((c) => (
          <Link
            key={c.t}
            href={c.href}
            className="flex min-h-[220px] flex-col rounded-[28px] bg-[#210A5C] p-8 text-white no-underline transition-transform hover:-translate-y-1"
          >
            <div className="text-[26px] font-bold leading-8 tracking-[-0.4px]">{c.t}</div>
            <p className="mt-3 text-[16px] leading-6 text-[#E7DEFF]">{c.d}</p>
            <span className="mt-auto pt-6 text-[15px] font-semibold text-[#EAE0FF]">Saiba mais →</span>
          </Link>
        ))}
      </div>

      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <h2 className="m-0 max-w-[16em] text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px]">
          Procurando algo específico? Teste agora.
        </h2>
        <Link href="/funcionalidades" className="text-[16px] font-semibold text-[#5B21D6] no-underline hover:text-[#4416A8]">
          Ver todas →
        </Link>
      </div>

      <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(240px,1fr))] gap-x-10 gap-y-0">
        {FEATS.map((c) => (
          <Link
            key={c.t}
            href={c.href}
            className="block border-t-2 border-[#1C1B1F] py-5 pb-8 text-[#1C1B1F] no-underline transition-colors hover:text-[#5B21D6]"
          >
            <div className="text-[20px] font-semibold leading-[26px]">{c.t}</div>
            <p className="mt-2 text-[15px] leading-[22px] text-[#49454F]">{c.d}</p>
          </Link>
        ))}
      </div>
    </section>
  );
}
