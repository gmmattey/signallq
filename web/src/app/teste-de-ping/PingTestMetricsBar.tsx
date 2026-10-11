"use client";

interface PingTestMetricsBarProps {
  pingMin: number | null
  pingAvg: number | null
  jitter: number | null
  bufferbloat: number | null
  activeStage?: 'ping' | 'bufferbloat' | ''
}

export function PingTestMetricsBar({
  pingMin,
  pingAvg,
  jitter,
  bufferbloat,
  activeStage = '',
}: PingTestMetricsBarProps) {
  const isPingActive = activeStage === 'ping'
  const isBbloatActive = activeStage === 'bufferbloat'

  const displayMin = pingMin != null ? String(pingMin) : '—'
  const displayAvg = pingAvg != null ? String(pingAvg) : '—'
  const displayJitter = jitter != null ? String(jitter) : '—'
  const displayBbloat = bufferbloat != null ? `+${bufferbloat}` : '—'

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 sm:gap-3.5 mb-6">
      {/* 1. Ping Mínimo */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isPingActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <span className="truncate">Ping Mínimo</span>
          {isPingActive && <span className="w-1.5 h-1.5 rounded-full bg-[#5B21D6] animate-pulse shrink-0 ml-auto" />}
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayMin}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>

      {/* 2. Ping Médio */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isPingActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          <span className="truncate">Ping Médio</span>
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayAvg}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>

      {/* 3. Jitter (Estabilidade) */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isPingActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
          </svg>
          <span className="truncate">Jitter</span>
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayJitter}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>

      {/* 4. Bufferbloat (Sob Carga) */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isBbloatActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : bufferbloat != null
              ? 'bg-white border-[#5B21D6] shadow-sm'
              : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="truncate">Bufferbloat</span>
          {isBbloatActive && <span className="w-1.5 h-1.5 rounded-full bg-[#5B21D6] animate-pulse shrink-0 ml-auto" />}
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayBbloat}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>
    </div>
  )
}
