"use client";

import Link from 'next/link'
import { AppScreenshot } from '@/components/AppScreenshot'
import { APPROVED_SCREENSHOTS } from '@/lib/approvedScreenshotsManifest'
import { GFiberGauge } from './GFiberGauge'
import { SpeedTestMetricsBar } from './SpeedTestMetricsBar'
import {
  formatMetric,
  getBufferbloatStatus,
  getPingStatus,
  getUsageLevels,
  getVerdict,
} from './speedTestVerdict'
import type { SpeedTestState } from './useSpeedTestEngine'
import { ShareRow } from './ShareRow'

export function SpeedTestResult({ state, onRetry }: { state: SpeedTestState; onRetry: () => void }) {
  const verdict = getVerdict(state.download)
  const usage = getUsageLevels(state.download, state.upload, state.ping, state.bufferbloat)
  const dl = state.download ?? 0
  const pingStatus = getPingStatus(state.ping)
  const bufferbloatStatus = getBufferbloatStatus(state.bufferbloat)

  return (
    <div className="text-center">
      {/* 1. Dashboard Superior com 4 Métricas Reais Consolidadas */}
      <SpeedTestMetricsBar
        ping={state.ping}
        download={state.download}
        upload={state.upload}
        bufferbloat={state.bufferbloat}
      />

      {/* 2. Velocímetro Central com o resultado consolidado */}
      <div className="my-6">
        <GFiberGauge value={dl} unit="Mbps" size={380}>
          <div className="flex flex-col items-center justify-center text-center">
            <span className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#E8F0FE] text-[#1A73E8] mb-1">
              Download
            </span>
            <div className="font-mono-num text-6xl sm:text-7xl font-black text-[#1C1B1F] tracking-tight leading-none my-1">
              {formatMetric(state.download)}
            </div>
            <span className="text-sm font-bold text-[#49454F] uppercase tracking-wider">
              Mbps
            </span>

            {/* Ação de repetir o teste com espaçamento adequado */}
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
          Diagnóstico da Conexão
        </div>
        <h2 className="mt-1 text-balance text-[clamp(24px,3.2vw,32px)] font-bold leading-[1.15] tracking-[-0.8px] text-[#1C1B1F]">
          {verdict.title}
        </h2>
        <p className="mt-2 text-pretty text-[15px] sm:text-[16px] leading-6 text-[#49454F]">
          {verdict.subtitle}
        </p>

        {/* Detalhes de Latência: Ping e Bufferbloat */}
        <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div className="rounded-2xl bg-[#F8F9FA] border border-[#E7E0EC] p-4 flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold text-[#79747E] uppercase tracking-wider">Tempo de resposta (Ping)</div>
              <div className="text-xl font-extrabold text-[#1C1B1F] mt-0.5">
                {state.ping != null ? `${state.ping} ms` : '—'}
              </div>
            </div>
            <span
              className="text-xs font-bold px-2.5 py-1 rounded-full bg-white border border-[#E7E0EC]"
              style={{ color: pingStatus.color }}
            >
              {pingStatus.label}
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

        {/* Compartilhamento do resultado */}
        {state.download != null && state.upload != null && state.ping != null && (
          <div className="mt-6 pt-6 border-t border-[#E7E0EC]">
            <ShareRow
              metrics={{
                download: state.download,
                upload: state.upload,
                ping: state.ping,
                jitter: state.jitter ?? 0,
              }}
            />
          </div>
        )}

        {/* Nota técnica sobre as limitações do teste via navegador */}
        <div className="mt-5 rounded-xl bg-[#F8F9FA] border border-[#E7E0EC] px-4 py-3 text-[13px] leading-5 text-[#79747E]">
          <strong className="text-[#49454F]">Limitação da medição web:</strong> Este teste mede a vazão de download e upload e a latência HTTP com servidores de teste no navegador. Velocidade alta não garante ausência de oscilação: interferências de sinal Wi-Fi, saturação de canais de rádio e perdas reais de pacotes só são diagnosticados diretamente no sistema operacional pelo aplicativo Android.
        </div>

        {/* Card de Marketing Secundário para o App SignallQ com Captura Real */}
        <div className="mt-8 rounded-3xl bg-[#FAF8FC] border border-[#E7E0EC] p-6 sm:p-8 text-left">
          <div className="grid grid-cols-1 md:grid-cols-[1.2fr,0.8fr] gap-6 items-center">
            <div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-[#EAE0FF] px-3 py-1 text-[12px] font-bold text-[#210A5C]">
                <span>📱</span> Exclusivo no Android
              </span>
              <h3 className="mt-3 text-[20px] sm:text-[22px] font-bold text-[#1C1B1F]">
                Sua velocidade é só uma parte da história.
              </h3>
              <p className="mt-2 text-[14px] sm:text-[15px] leading-6 text-[#49454F]">
                O SignallQ Android também analisa aspectos do Wi-Fi, DNS e equipamentos compatíveis para ajudar você a entender possíveis causas de problemas.
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-3">
                <Link
                  href="/#baixar"
                  className="rounded-full bg-[#5B21D6] hover:bg-[#6D28D9] px-6 py-3 text-[14px] font-semibold text-white no-underline transition-all shadow-[0_4px_14px_-3px_rgba(91,33,214,0.4)]"
                >
                  Conhecer o diagnóstico completo
                </Link>
                <Link
                  href="/como-funciona"
                  className="text-[13px] font-semibold text-[#5B21D6] hover:underline"
                >
                  Como funciona →
                </Link>
              </div>
            </div>

            <div className="flex justify-center">
              <AppScreenshot
                screenshot={APPROVED_SCREENSHOTS.inicioVelocidade}
                maxWidth={220}
                showCaption={false}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

