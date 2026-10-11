import { Speedometer } from '@/components/Speedometer'
import { formatMetric } from './speedTestVerdict'
import type { SpeedTestState } from './useSpeedTestEngine'

const STAGE_LABELS: Record<string, string> = {
  ping: 'Medindo latência…',
  down: 'Medindo download…',
  up: 'Medindo upload…',
}

export function SpeedTestRunning({ state, onCancel }: { state: SpeedTestState; onCancel: () => void }) {
  const liveUnit = state.stage === 'ping' ? 'ms' : 'Mbps'

  return (
    <div className="text-center" aria-live="polite">
      <div className="text-[16px] font-medium text-[#49454F]">{STAGE_LABELS[state.stage] ?? ''}</div>
      <div className="mt-3 flex justify-center">
        <Speedometer
          value={state.live}
          unit={liveUnit}
          mode="measuring"
          size={260}
          tone="purple"
        />
      </div>
      <div className="mx-auto mt-8 h-[6px] max-w-[420px] overflow-hidden rounded-full bg-[#F3EEFA]">
        <div
          className="h-full rounded-full bg-[#5B21D6] transition-[width] duration-300"
          style={{ width: `${Math.round(state.pct)}%` }}
        />
      </div>
      <div className="mx-auto mt-6 flex justify-center gap-6 text-[14px] text-[#49454F]">
        <span>Latência <b className="text-[#1C1B1F]">{state.ping == null ? '—' : `${state.ping} ms`}</b></span>
        <span>Download <b className="text-[#1C1B1F]">{state.download == null ? '—' : `${formatMetric(state.download)} Mbps`}</b></span>
        <span>Upload <b className="text-[#1C1B1F]">{state.upload == null ? '—' : `${formatMetric(state.upload)} Mbps`}</b></span>
      </div>
      <button
        type="button"
        onClick={onCancel}
        className="mt-7 cursor-pointer rounded-full border-0 bg-[#F3EEFA] px-7 py-3 text-[15px] font-medium text-[#210A5C]"
      >
        Cancelar
      </button>
    </div>
  )
}
