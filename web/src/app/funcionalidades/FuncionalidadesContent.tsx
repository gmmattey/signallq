"use client";

import Link from 'next/link'
import { AppScreenshot } from '@/components/AppScreenshot'
import { AppDownloadCTA } from '@/components/AppDownloadCTA'
import { APPROVED_SCREENSHOTS } from '@/lib/approvedScreenshotsManifest'

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
    t: 'Jitter e estabilidade',
    d: 'Oscilação da latência e consistência da resposta.',
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
    t: 'Sinal 4G e 5G',
    d: 'Qualidade e parâmetros técnicos da rede móvel.',
    href: '/sinal-4g-5g',
  },
  {
    t: 'Mapa de calor do Wi-Fi',
    d: 'O sinal do Wi-Fi em cada cômodo da casa.',
    href: '/mapa-de-calor-wifi',
  },
  {
    t: 'Relatório em PDF',
    d: 'Relatório de diagnóstico e histórico para levar à operadora.',
    href: '/relatorio-pdf',
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
          Cinco ferramentas executam medições reais aqui mesmo no navegador. Os diagnósticos avançados de hardware e rádio funcionam dentro do aplicativo Android.
        </p>
      </section>

      {/* Ferramentas Executoras Web */}
      <section className="mx-auto max-w-[1168px] px-6 pb-12 pt-6">
        <div className="inline-flex h-7 items-center rounded-full bg-[#E1F5E6] px-3 text-[13px] font-semibold text-[#0B4A1D]">
          Teste agora, no navegador
        </div>
        <div className="mt-6 grid grid-cols-[repeat(auto-fit,minmax(min(260px,100%),1fr))] gap-4">
          {TOOLS.map((c) => (
            <Link
              key={c.t}
              href={c.href}
              className="flex min-h-[176px] flex-col rounded-[28px] border border-[#E7E0EC] bg-[#F8F5FB] p-7 text-[#1C1B1F] no-underline transition-all hover:-translate-y-1 hover:border-[#5B21D6] hover:bg-white hover:shadow-sm"
            >
              <div className="text-[22px] font-semibold leading-7">{c.t}</div>
              <p className="mt-[10px] text-[15px] leading-6 text-[#49454F]">{c.d}</p>
              <span className="mt-auto pt-5 text-[15px] font-semibold text-[#5B21D6]">Testar agora →</span>
            </Link>
          ))}
        </div>
      </section>

      {/* Recursos Exclusivos do App Android com Captura Real */}
      <section className="border-t border-[#F3EEFA] bg-[#FAF8FC] py-20 px-6">
        <div className="mx-auto max-w-[1168px]">
          <div className="grid grid-cols-1 lg:grid-cols-[1.2fr,0.8fr] gap-12 items-center mb-12">
            <div>
              <div className="inline-flex h-7 items-center rounded-full bg-[#210A5C] px-3.5 text-[13px] font-semibold text-white">
                Recursos do app
              </div>
              <h2 className="mt-4 text-[clamp(28px,3.8vw,42px)] font-bold text-[#1C1B1F] tracking-tight">
                Diagnósticos que exigem acesso ao hardware.
              </h2>
              <p className="mt-3 text-[17px] text-[#49454F] leading-relaxed max-w-[32em]">
                O aplicativo SignallQ para Android possui uma central completa de ferramentas para investigar rádio Wi-Fi, sinal celular, aparelhos na rede e roteadores compatíveis.
              </p>
            </div>

            <div className="flex justify-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.menuFerramentas}
                maxWidth={280}
              />
            </div>
          </div>

          <div className="grid grid-cols-[repeat(auto-fit,minmax(min(280px,100%),1fr))] gap-4">
            {APPS.map((c) => (
              <Link
                key={c.t}
                href={c.href}
                className="flex min-h-[190px] flex-col rounded-[28px] bg-[#210A5C] p-7 text-white no-underline transition-all hover:-translate-y-1 hover:bg-[#2C1075] hover:shadow-md"
              >
                <div className="text-[21px] font-semibold leading-7">{c.t}</div>
                <p className="mt-[10px] text-[15px] leading-6 text-[#E7DEFF]">{c.d}</p>
                <span className="mt-auto pt-5 text-[14px] font-semibold text-[#EAE0FF]">Conhecer recurso →</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Banner de download do app */}
      <AppDownloadCTA />
    </div>
  )
}
