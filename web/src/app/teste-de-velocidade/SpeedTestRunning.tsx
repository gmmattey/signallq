"use client";

import { GFiberGauge } from './GFiberGauge'
import { SpeedTestMetricsBar } from './SpeedTestMetricsBar'
import { formatMetric } from './speedTestVerdict'
import type { SpeedTestState } from './useSpeedTestEngine'

const STAGE_LABELS: Record<string, string> = {
  ping: 'Medindo latência…',
  down: 'Medindo download…',
  up: 'Medindo upload…',
}

export function SpeedTestRunning({ state, onCancel }: { state: SpeedTestState; onCancel: () => void }) {
  const liveUnit = state.stage === 'ping' ? 'ms' : 'Mbps'
  const displayLiveNumber =
    state.stage === 'ping'
      ? String(Math.round(state.live))
      : formatMetric(state.live)

  return (
    <div className="text-center">
      {/* 1. Dashboard Superior com 4 métricas sincronizadas */}
      <SpeedTestMetricsBar
        ping={state.ping}
        download={state.download}
        upload={state.upload}
        bufferbloat={state.bufferbloat}
        activeStage={state.stage}
        liveValue={state.live}
      />

      {/* 2. Velocímetro Central com Leitura Dinâmica Limpa e Espaçosa */}
      <div className="my-6">
        <GFiberGauge value={state.live} unit={liveUnit} size={380}>
          <div className="flex flex-col items-center justify-center text-center">
            {/* Tag da etapa atual */}
            <div
              className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#F3EEFA] text-[#5B21D6] mb-1"
              aria-live="polite"
            >
              {STAGE_LABELS[state.stage] ?? ''}
            </div>

            {/* Número gigante desobstruído */}
            <div className="font-mono-num text-6xl sm:text-7xl font-black text-[#1C1B1F] tracking-tight leading-none my-1">
              {displayLiveNumber}
            </div>

            {/* Unidade */}
            <span className="text-sm font-bold text-[#49454F] uppercase tracking-wider">
              {liveUnit}
            </span>
          </div>
        </GFiberGauge>
      </div>

      {/* 3. Única barra sutil de progresso geral */}
      <div className="mx-auto max-w-[360px] px-4">
        <div className="h-[4px] w-full overflow-hidden rounded-full bg-[#F3EEFA]">
          <div
            className="h-full rounded-full bg-[#5B21D6] transition-[width] duration-300"
            style={{ width: `${Math.round(state.pct)}%` }}
          />
        </div>
      </div>

      {/* 4. Ação de Cancelamento */}
      <button
        type="button"
        onClick={onCancel}
        className="mt-6 cursor-pointer rounded-full border border-[#E7E0EC] bg-white hover:bg-[#F3EEFA] px-7 py-2 text-[14px] font-semibold text-[#210A5C] transition-colors shadow-sm"
      >
        Cancelar
      </button>
    </div>
  )
}
