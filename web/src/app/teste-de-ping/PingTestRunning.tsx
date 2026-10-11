"use client";

import { GFiberGauge } from '../teste-de-velocidade/GFiberGauge'
import { PingTestMetricsBar } from './PingTestMetricsBar'
import type { PingTestState } from './usePingTestEngine'

export function PingTestRunning({
  state,
  onCancel,
}: {
  state: PingTestState
  onCancel: () => void
}) {
  const stageLabel =
    state.stage === 'bufferbloat'
      ? 'Medindo latência sob carga…'
      : `Medindo pacote ${state.packetIndex} de ${state.totalPackets}…`

  return (
    <div className="text-center">
      {/* 1. Dashboard Superior com métricas atualizadas */}
      <PingTestMetricsBar
        pingMin={state.pingMin}
        pingAvg={state.pingAvg}
        jitter={state.jitter}
        bufferbloat={state.bufferbloat}
        activeStage={state.stage}
      />

      {/* 2. Velocímetro Central com Leitura Dinâmica de Ping */}
      <div className="my-6">
        <GFiberGauge value={state.live} unit="ms" size={380}>
          <div className="flex flex-col items-center justify-center text-center">
            {/* Tag da etapa atual */}
            <div
              className="text-[11px] uppercase tracking-wider font-bold px-3 py-1 rounded-full bg-[#F3EEFA] text-[#5B21D6] mb-1"
              aria-live="polite"
            >
              {stageLabel}
            </div>

            {/* Número gigante ao vivo */}
            <div className="font-mono-num text-6xl sm:text-7xl font-black text-[#1C1B1F] tracking-tight leading-none my-1">
              {state.live}
            </div>

            {/* Unidade */}
            <span className="text-sm font-bold text-[#49454F] uppercase tracking-wider">
              ms
            </span>
          </div>
        </GFiberGauge>
      </div>

      {/* 3. Barra única e sutil de progresso geral */}
      <div className="mx-auto max-w-[360px] px-4">
        <div className="h-[4px] w-full overflow-hidden rounded-full bg-[#F3EEFA]">
          <div
            className="h-full rounded-full bg-[#5B21D6] transition-[width] duration-200"
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
