import Link from "next/link";
import { AppScreenshot } from "@/components/AppScreenshot";
import { APPROVED_SCREENSHOTS } from "@/lib/approvedScreenshotsManifest";

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
    t: "Sinal Wi-Fi em dBm",
    d: "Leitura de potência real de rádio cômodo por cômodo, sem depender de barrinhas genéricas.",
    href: "/sinal-wifi",
    badge: "Hardware",
  },
  {
    t: "Dispositivos na rede",
    d: "Identifica aparelhos visíveis conectados ao roteador e detecta potenciais intrusos.",
    href: "/quem-esta-na-minha-rede",
    badge: "Rede Local",
  },
  {
    t: "Sinal 4G e 5G",
    d: "Diagnóstico da rede móvel e leitura da intensidade do sinal da torre da operadora celular.",
    href: "/sinal-4g-5g",
    badge: "Rede Móvel",
  },
  {
    t: "Mapa de calor do Wi-Fi",
    d: "Mapeie visualmente os pontos cegos da sua casa e compare antes e depois de mudar o roteador.",
    href: "/mapa-de-calor-wifi",
    badge: "WiFi Casa",
  },
  {
    t: "Relatório em PDF",
    d: "Histórico auditado com dados mascarados pronto para protocolar chamado na operadora.",
    href: "/relatorio-pdf",
    badge: "Operadora",
  },
];

export function HomeFeatures() {
  return (
    <section id="funcionalidades" className="scroll-mt-20 border-t border-[#F3EEFA] bg-white px-6 py-24">
      <div className="mx-auto w-full max-w-[1168px]">
        {/* Cabeçalho da seção */}
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

        {/* Grade de funcionalidades exclusivas */}
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

        {/* Demonstração visual com capturas reais do app */}
        <div className="mt-20 pt-16 border-t border-[#F3EEFA]">
          <div className="text-center max-w-[34em] mx-auto mb-14">
            <span className="text-[13px] font-bold uppercase tracking-wider text-[#5B21D6]">
              Evidências do Aplicativo
            </span>
            <h3 className="mt-2 text-[28px] sm:text-[34px] font-bold text-[#1C1B1F] tracking-tight">
              Acompanhamento contínuo da sua rede.
            </h3>
            <p className="mt-2.5 text-[16px] text-[#49454F] leading-relaxed">
              O SignallQ acompanha o desempenho da sua conexão com histórico detalhado, monitoramento passivo e leitura de equipamentos.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 items-start">
            {/* 1. Histórico de medições */}
            <div className="flex flex-col items-center text-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.historicoMedicoes}
                maxWidth={280}
                showBadge={false}
                showCaption={false}
              />
              <div className="mt-5 font-bold text-[18px] text-[#1C1B1F]">
                Histórico e Médias
              </div>
              <p className="mt-1 text-[14px] text-[#49454F] max-w-[20em] leading-relaxed">
                Acompanhe o histórico de velocidade e latência ao longo das semanas para comprovar instabilidades.
              </p>
            </div>

            {/* 2. Monitoramento passivo */}
            <div className="flex flex-col items-center text-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.monitoramentoPassivo}
                maxWidth={280}
                showBadge={false}
                showCaption={false}
              />
              <div className="mt-5 font-bold text-[18px] text-[#1C1B1F]">
                Alertas em Segundo Plano
              </div>
              <p className="mt-1 text-[14px] text-[#49454F] max-w-[20em] leading-relaxed">
                Verificações automáticas que avisam quando a rede começa a degradar antes da partida cair.
              </p>
            </div>

            {/* 3. Equipamento da operadora */}
            <div className="flex flex-col items-center text-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.equipamentoOnt}
                maxWidth={280}
                showBadge={false}
                showCaption={false}
              />
              <div className="mt-5 font-bold text-[18px] text-[#1C1B1F]">
                Diagnóstico de Modem e ONT
              </div>
              <p className="mt-1 text-[14px] text-[#49454F] max-w-[20em] leading-relaxed">
                Leitura do estado do sinal óptico e conexão PON em equipamentos compatíveis da operadora.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
