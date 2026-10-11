import { type RunnerState } from "./useModoGamerRunner";

interface ModoGamerRunningViewProps {
  runner: RunnerState
  onCancel: () => void
}

export function ModoGamerRunningView({
  runner,
  onCancel,
}: ModoGamerRunningViewProps) {
  return (
    <div className="py-2 text-center">
      {/* Phase Steps Indicator */}
      <div className="mb-8 flex items-center justify-center gap-2 text-[12px] font-medium text-[#CAC4D0]">
        <span
          className={`rounded-full px-3 py-1 ${
            runner.phase === 'locating'
              ? 'bg-[#7B4DF0] text-white font-semibold'
              : 'bg-[#1F242C] text-[#CAC4D0]'
          }`}
        >
          1. Borda
        </span>
        <span>→</span>
        <span
          className={`rounded-full px-3 py-1 ${
            runner.phase === 'measuring_ping'
              ? 'bg-[#7B4DF0] text-white font-semibold'
              : 'bg-[#1F242C] text-[#CAC4D0]'
          }`}
        >
          2. Ping & Jitter
        </span>
        <span>→</span>
        <span
          className={`rounded-full px-3 py-1 ${
            runner.phase === 'measuring_upload'
              ? 'bg-[#7B4DF0] text-white font-semibold'
              : 'bg-[#1F242C] text-[#CAC4D0]'
          }`}
        >
          3. Upload & Bufferbloat
        </span>
      </div>

      <div className="text-[15px] font-medium text-[#C9B6FF]">
        {runner.phase === 'locating' && 'Localizando borda regional mais próxima…'}
        {runner.phase === 'measuring_ping' &&
          'Medindo latência ociosa e oscilação (descartando amostras iniciais)…'}
        {runner.phase === 'measuring_upload' &&
          'Saturando upload e medindo inchaço da fila (bufferbloat)…'}
      </div>

      <div className="mt-4 flex items-baseline justify-center gap-2 font-mono text-[72px] font-bold leading-none tracking-[-3px] text-white">
        {runner.liveValue}
        <span className="text-[22px] font-medium text-[#CAC4D0]"> {runner.liveUnit}</span>
      </div>

      {/* Progress bar */}
      <div
        role="progressbar"
        aria-valuenow={runner.progressPct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Progresso do teste de conexão gamer"
        className="mx-auto mt-6 h-2 max-w-[420px] overflow-hidden rounded-full bg-[#1F242C]"
      >
        <div
          style={{ width: `${runner.progressPct}%` }}
          className="h-full rounded-full bg-gradient-to-r from-[#5B21D6] to-[#7B4DF0] transition-all duration-200"
        />
      </div>

      {/* Live partial chips */}
      <div className="mt-8 flex flex-wrap items-center justify-center gap-4 text-[13px] text-[#CAC4D0]">
        <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-1.5">
          Latência ociosa:{' '}
          <span className="font-mono font-semibold text-white">
            {runner.idlePing != null ? `${runner.idlePing} ms` : '—'}
          </span>
        </div>
        <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-1.5">
          Jitter (MAD):{' '}
          <span className="font-mono font-semibold text-white">
            {runner.jitter != null ? `${runner.jitter} ms` : '—'}
          </span>
        </div>
        <div className="rounded-xl border border-[#262B33] bg-[#0E1116] px-3.5 py-1.5">
          Bufferbloat:{' '}
          <span className="font-mono font-semibold text-white">
            {runner.bufferbloat != null ? `+${runner.bufferbloat} ms` : '—'}
          </span>
        </div>
      </div>

      <button
        type="button"
        onClick={onCancel}
        className="mt-8 cursor-pointer rounded-full border border-[#3A3446] bg-transparent px-6 py-2.5 text-[14px] font-medium text-[#CAC4D0] hover:border-red-500/50 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C] transition-colors"
      >
        Cancelar teste
      </button>
    </div>
  )
}
