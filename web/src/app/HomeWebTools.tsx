import Link from "next/link";

interface WebToolItem {
  titulo: string;
  descricao: string;
  href: string;
  metrica: string;
  badge: string;
  diferencialApp: string;
}

export function HomeWebTools() {
  const tools: WebToolItem[] = [
    {
      titulo: "Teste de velocidade",
      descricao: "Mede taxa real de download, upload e latência com veredito em palavras simples.",
      href: "/teste-de-velocidade",
      metrica: "Download · Upload · Ping",
      badge: "Completo",
      diferencialApp: "No app: analisa se o gargalo é o canal do Wi-Fi ou a operadora.",
    },
    {
      titulo: "Teste de ping e latência",
      descricao: "Tempo de resposta HTTP, jitter e atraso sob saturação de banda (bufferbloat).",
      href: "/teste-de-ping",
      metrica: "Tempo de resposta (ms)",
      badge: "Tempo real",
      diferencialApp: "No app: isola a latência do roteador e o ping por salto de rede.",
    },
    {
      titulo: "Teste gamer de estabilidade",
      descricao: "Check de conectividade calibrado por perfil de jogo (FPS, MOBA, Battle Royale).",
      href: "/teste-gamer",
      metrica: "Ping · Jitter · Fila",
      badge: "Gamer",
      diferencialApp: "No app: inclui sonda UDP nativa e varredura de rádio Wi-Fi.",
    },
    {
      titulo: "Teste de DNS",
      descricao: "Compara o tempo de resolução via DoH entre Cloudflare, Google e Quad9.",
      href: "/teste-de-dns",
      metrica: "Cloudflare · Google · Quad9",
      badge: "Resolução",
      diferencialApp: "No app: afere o DNS real configurado no seu roteador local.",
    },
    {
      titulo: "Jitter e estabilidade",
      descricao: "Identifica oscilação no tempo de resposta para videochamadas e streaming.",
      href: "/teste-de-jitter",
      metrica: "Variação e timeouts",
      badge: "Chamadas",
      diferencialApp: "No app: mede perda física e interferência de rádio no ambiente.",
    },
  ];

  return (
    <section id="ferramentas-web" className="scroll-mt-20 border-t border-[#F3EEFA] bg-[#FAF8FC] px-6 py-24">
      <div className="mx-auto max-w-[1168px]">
        <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
          <div className="max-w-[36em]">
            <div className="text-[14px] font-semibold uppercase tracking-wider text-[#5B21D6]">
              Testes no navegador
            </div>
            <h2 className="m-0 mt-2 text-balance text-[clamp(30px,3.8vw,42px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
              Ferramentas gratuitas que funcionam no navegador.
            </h2>
            <p className="mt-3 text-pretty text-[18px] leading-7 text-[#49454F]">
              Medições reais e imediatas no seu navegador para entender o estado da sua conexão agora.
            </p>
          </div>
          <Link
            href="/funcionalidades"
            className="text-[16px] font-semibold text-[#5B21D6] no-underline hover:text-[#4416A8] transition-colors"
          >
            Ver catálogo completo →
          </Link>
        </div>

        <div className="mt-12 grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-6">
          {tools.map((t) => (
            <Link
              key={t.titulo}
              href={t.href}
              className="group flex flex-col justify-between rounded-3xl border border-[#E7E0EC] bg-white p-7 text-[#1C1B1F] no-underline shadow-sm transition-all hover:-translate-y-1 hover:border-[#D5C2FF] hover:shadow-md"
            >
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-[12px] font-bold uppercase tracking-wider text-[#79747E]">
                    {t.metrica}
                  </span>
                  <span className="rounded-full bg-[#F3EEFA] px-2.5 py-0.5 text-[11px] font-bold text-[#5B21D6]">
                    {t.badge}
                  </span>
                </div>
                <div className="mt-4 text-[22px] font-bold leading-7 text-[#1C1B1F] group-hover:text-[#5B21D6] transition-colors">
                  {t.titulo}
                </div>
                <p className="mt-2.5 text-[15px] leading-6 text-[#49454F]">{t.descricao}</p>
              </div>

              <div className="mt-6 pt-4 border-t border-[#F3EEFA]">
                <div className="text-[12px] text-[#79747E] leading-4 mb-3">{t.diferencialApp}</div>
                <span className="text-[14px] font-semibold text-[#5B21D6] group-hover:translate-x-1 inline-flex items-center gap-1 transition-transform">
                  Testar agora →
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
