import Link from 'next/link'

const TOOLS = [
  {
    t: 'Speedtest completo',
    d: 'Ping, jitter, download e upload em um só teste, com a estabilidade por tipo de uso.',
    href: '/teste-de-velocidade',
  },
  {
    t: 'Teste de ping',
    d: 'Quanto tempo sua conexão leva para responder.',
    href: '/teste-de-ping',
  },
  {
    t: 'Download e upload',
    d: 'Quantos Mbps chegam para baixar e enviar.',
    href: '/teste-de-download-e-upload',
  },
  {
    t: 'Teste de DNS',
    d: 'Qual DNS responde mais rápido para você.',
    href: '/teste-de-dns',
  },
  {
    t: 'Jitter e perda de pacotes',
    d: 'Oscilação da latência e perda de pacotes.',
    href: '/teste-de-jitter',
  },
]

const APPS = [
  {
    t: 'Modo Jogos',
    d: 'Veja se sua conexão serve para o seu jogo e aparelho.',
    href: '/modo-gamer',
  },
  {
    t: 'Relatório em PDF',
    d: 'Relatório de diagnóstico e histórico para levar à operadora.',
    href: '/relatorio-pdf',
  },
  {
    t: 'Canais do Wi-Fi',
    d: 'Descubra o canal mais livre para o seu roteador.',
    href: '/canais-wifi',
  },
  {
    t: 'Sinal Wi-Fi',
    d: 'O que o dBm do sinal significa de verdade.',
    href: '/sinal-wifi',
  },
  {
    t: 'Dispositivos na rede',
    d: 'Veja os aparelhos conectados ao seu Wi-Fi.',
    href: '/quem-esta-na-minha-rede',
  },
  {
    t: 'Mapa de calor do Wi-Fi',
    d: 'O sinal do Wi-Fi em cada cômodo da casa.',
    href: '/mapa-de-calor-wifi',
  },
  {
    t: 'Sinal 4G e 5G',
    d: 'Qualidade do sinal da rede móvel.',
    href: '/sinal-4g-5g',
  },
]

export default function FuncionalidadesContent() {
  return (
    <div className="bg-white font-sans leading-[normal] text-[#1C1B1F]">
      <section className="mx-auto max-w-[1168px] px-6 pb-12 pt-[72px]">
        <div className="text-[14px] font-medium text-[#5B21D6]">Funcionalidades</div>
        <h1 className="m-0 mt-4 max-w-[14em] text-balance text-[clamp(38px,5.2vw,60px)] font-bold leading-[1.05] tracking-[-1.4px]">
          Tudo o que o SignallQ analisa na sua conexão.
        </h1>
        <p className="mt-6 max-w-[30em] text-pretty text-[20px] leading-[30px] text-[#49454F]">
          Quatro testes rodam aqui mesmo, no navegador. Os outros recursos analisam a sua rede dentro do app.
        </p>
      </section>

      <section className="mx-auto max-w-[1168px] px-6 pb-2 pt-6">
        <div className="inline-flex h-7 items-center rounded-full bg-[#E1F5E6] px-3 text-[13px] font-semibold text-[#0B4A1D]">
          Teste agora, no navegador
        </div>
        <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(min(260px,100%),1fr))] gap-4">
          {TOOLS.map((c) => (
            <Link
              key={c.t}
              href={c.href}
              className="flex min-h-[176px] flex-col rounded-[28px] border border-[#E7E0EC] bg-[#F8F5FB] p-7 text-[#1C1B1F] no-underline transition-transform hover:-translate-y-1"
            >
              <div className="text-[22px] font-semibold leading-7">{c.t}</div>
              <p className="mt-[10px] text-[16px] leading-6 text-[#49454F]">{c.d}</p>
              <span className="mt-auto pt-5 text-[15px] font-semibold text-[#5B21D6]">Testar agora →</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-[1168px] px-6 pb-28 pt-12">
        <div className="inline-flex h-7 items-center rounded-full bg-[#210A5C] px-3 text-[13px] font-semibold text-white">
          Recursos do app
        </div>
        <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] gap-4">
          {APPS.map((c) => (
            <Link
              key={c.t}
              href={c.href}
              className="flex min-h-[200px] flex-col rounded-[28px] bg-[#210A5C] p-7 text-white no-underline transition-transform hover:-translate-y-1"
            >
              <div className="text-[22px] font-semibold leading-7">{c.t}</div>
              <p className="mt-[10px] text-[16px] leading-6 text-[#E7DEFF]">{c.d}</p>
              <span className="mt-auto pt-5 text-[15px] font-semibold text-[#EAE0FF]">Saiba mais →</span>
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}
