"use client";

import Link from 'next/link'
import { GFiberGauge } from '../teste-de-velocidade/GFiberGauge'
import { PingTestMetricsBar } from './PingTestMetricsBar'
import {
  getPingVerdict,
  getJitterStatus,
  getBufferbloatStatus,
  getPingUsageLevels,
} from './pingTestVerdict'
import type { PingTestState } from './usePingTestEngine'

export function PingTestResult({
  state,
  onRetry,
}: {
  state: PingTestState
  onRetry: () => void
}) {
  const verdict = getPingVerdict(state.pingMin)
  const jitterStatus = getJitterStatus(state.jitter)
  const bufferbloatStatus = getBufferbloatStatus(state.bufferbloat)
  const usage = getPingUsageLevels(state.pingMin, state.jitter)

  const displayPing = state.pingMin ?? state.pingAvg ?? 0

  return (
    <div className="text-center">
      {/* 1. Dashboard Superior com Métricas Finais Consolidadas */}
      <PingTestMetricsBar
        pingMin={state.pingMin}
        pingAvg={state.pingAvg}
        jitter={state.jitter}
        bufferbloat={state.bufferbloat}
      />

      {/* 2. Velocímetro Central com o resultado consolidado */}
      <div className="my-6">
        <GFiberGauge value={displayPing} unit="ms" size={380}>
          <div className="flex flex-col items-center justify-center text-center">
            <span className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#F3EEFA] text-[#5B21D6] mb-1">
              Ping Mínimo
            </span>
            <div className="font-mono-num text-6xl sm:text-7xl font-black text-[#1C1B1F] tracking-tight leading-none my-1">
              {displayPing}
            </div>
            <span className="text-sm font-bold text-[#49454F] uppercase tracking-wider">
              ms
            </span>

            {/* Ação de repetir o teste */}
            <button
              type="button"
              onClick={onRetry}
              className="pointer-events-auto mt-3 px-5 py-2 rounded-full border border-[#E7E0EC] bg-white hover:bg-[#F3EEFA] text-[#5B21D6] font-semibold text-xs transition-colors flex items-center gap-1.5 cursor-pointer shadow-sm"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
              Testar de novo
            </button>
          </div>
        </GFiberGauge>
      </div>

      {/* 3. Veredito Humano de Diagnóstico (Diferencial SignallQ) */}
      <div className="mt-8 pt-8 border-t border-[#E7E0EC] text-left">
        <div className="text-[12px] font-bold uppercase tracking-wider text-[#5B21D6]">
          Diagnóstico de Latência
        </div>
        <h2 className="mt-1 text-balance text-[clamp(24px,3.2vw,32px)] font-bold leading-[1.15] tracking-[-0.8px] text-[#1C1B1F]">
          {verdict.title}
        </h2>
        <p className="mt-2 text-pretty text-[15px] sm:text-[16px] leading-6 text-[#49454F]">
          {verdict.subtitle}
        </p>

        {/* Detalhes de Latência: Jitter e Bufferbloat */}
        <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold text-[#79747E] uppercase tracking-wider">Estabilidade (Jitter)</div>
              <div className="text-xl font-extrabold text-[#1C1B1F] mt-0.5">
                {state.jitter != null ? `${state.jitter} ms` : '—'}
              </div>
            </div>
            <span
              className="text-xs font-bold px-2.5 py-1 rounded-full bg-white border border-[#E7E0EC]"
              style={{ color: jitterStatus.color }}
            >
              {jitterStatus.label}
            </span>
          </div>

          <div className="rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold text-[#79747E] uppercase tracking-wider">Lentidão sob carga (Bufferbloat)</div>
              <div className="text-xl font-extrabold text-[#1C1B1F] mt-0.5">
                {state.bufferbloat != null ? `+${state.bufferbloat} ms` : '—'}
              </div>
            </div>
            <span
              className="text-xs font-bold px-2.5 py-1 rounded-full bg-white border border-[#E7E0EC]"
              style={{ color: bufferbloatStatus.color }}
            >
              {bufferbloatStatus.label}
            </span>
          </div>
        </div>

        {/* "Como sua internet deve funcionar" (Padrão Android) */}
        <div className="mt-6 rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 sm:p-5">
          <div className="mb-2 text-[12px] font-bold uppercase tracking-wider text-[#49454F]">
            Como sua internet deve funcionar
          </div>
          <div className="divide-y divide-[#E7E0EC]">
            {usage.map((u) => (
              <div key={u.n} className="flex items-center justify-between py-2.5 text-[14px]">
                <span className="text-[#1C1B1F] font-medium">{u.n}</span>
                <span className="font-bold text-[13px]" style={{ color: u.c }}>
                  {u.s}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Nota técnica de transparência sobre latência HTTP */}
        <div className="mt-5 rounded-xl bg-[#F8F9FA] border border-[#E7E0EC] px-4 py-3 text-[13px] leading-5 text-[#79747E]">
          <strong className="text-[#49454F]">Limitação técnica da medição:</strong> A latência exibida é o tempo de ida e volta (RTT) de requisições HTTP até a borda CDN regional mais próxima. Não equivale a um ping ICMP de baixo nível. Para isolar se o atraso se origina no canal Wi-Fi do seu roteador, na rota da operadora ou no servidor DNS local, utilize o aplicativo SignallQ para Android.
        </div>

        {/* Card de CTA para o App SignallQ */}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-3xl bg-[#F3EEFA] border border-[#E7E0EC] p-5 sm:p-6">
          <div className="max-w-[26em]">
            <div className="text-[17px] font-bold text-[#210A5C]">
              O ping está alto no Wi-Fi ou na operadora?
            </div>
            <div className="mt-1 text-[13px] leading-5 text-[#49454F]">
              O app SignallQ testa o sinal do roteador em dBm, identifica interferências de vizinhos e mostra exatamente onde o atraso está acontecendo.
            </div>
          </div>
          <Link
            href="/#baixar"
            className="rounded-full bg-[#5B21D6] hover:bg-[#6D28D9] px-6 py-3 text-[14px] font-semibold text-white no-underline transition-all shadow-[0_4px_14px_-3px_rgba(91,33,214,0.4)]"
          >
            Diagnosticar no app grátis
          </Link>
        </div>
      </div>
    </div>
  )
}

