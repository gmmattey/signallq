"use client";

import { GFiberGauge } from '../teste-de-velocidade/GFiberGauge'
import { SpeedTestConnectionInfo } from '../teste-de-velocidade/SpeedTestConnectionInfo'
import { PingTestMetricsBar } from './PingTestMetricsBar'

export function PingTestIdle({ onStart }: { onStart: () => void }) {
  return (
    <div className="text-center">
      {/* 1. Dashboard Superior de Métricas */}
      <PingTestMetricsBar
        pingMin={null}
        pingAvg={null}
        jitter={null}
        bufferbloat={null}
      />

      {/* 2. Velocímetro Central com Botão GO Integrado */}
      <div className="my-6">
        <GFiberGauge value={0} unit="ms" size={380}>
          <div className="flex flex-col items-center justify-center pointer-events-auto">
            <button
              type="button"
              onClick={onStart}
              aria-label="Medir meu ping"
              className="w-32 h-32 rounded-full bg-[#5B21D6] text-white flex flex-col items-center justify-center font-bold tracking-wider shadow-[0_12px_28px_-6px_rgba(91,33,214,0.45)] hover:scale-105 hover:bg-[#6D28D9] active:scale-95 transition-all duration-200 cursor-pointer group"
            >
              <span className="text-3xl font-black tracking-widest group-hover:scale-105 transition-transform">GO</span>
              <span className="text-[12px] font-medium tracking-normal text-white/90 mt-0.5">Medir meu ping</span>
            </button>
          </div>
        </GFiberGauge>
      </div>

      {/* 3. Rodapé de Infraestrutura */}
      <SpeedTestConnectionInfo />

      <div className="mt-4 text-[13px] text-[#79747E]">
        Mede tempo de resposta, jitter e bufferbloat em tempo real. Nenhum dado pessoal é coletado.
      </div>
    </div>
  )
}
