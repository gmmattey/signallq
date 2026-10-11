"use client";

import { formatMetric } from './speedTestVerdict'

interface SpeedTestMetricsBarProps {
  ping: number | null
  download: number | null
  upload: number | null
  bufferbloat?: number | null
  activeStage?: 'ping' | 'down' | 'up' | ''
  liveValue?: number
}

export function SpeedTestMetricsBar({
  ping,
  download,
  upload,
  bufferbloat = null,
  activeStage = '',
  liveValue = 0,
}: SpeedTestMetricsBarProps) {
  const isPingActive = activeStage === 'ping'
  const isDownActive = activeStage === 'down'
  const isUpActive = activeStage === 'up'

  const displayPing = isPingActive
    ? String(Math.round(liveValue || 0))
    : ping != null
      ? String(ping)
      : '—'

  const displayDown = isDownActive
    ? liveValue > 0
      ? formatMetric(liveValue)
      : '0.0'
    : download != null
      ? formatMetric(download)
      : '—'

  const displayUp = isUpActive
    ? liveValue > 0
      ? formatMetric(liveValue)
      : '0.0'
    : upload != null
      ? formatMetric(upload)
      : '—'

  const displayBufferbloat =
    bufferbloat != null
      ? `+${bufferbloat}`
      : '—'

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 sm:gap-3.5 mb-6">
      {/* 1. Ping */}
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
          <span className="truncate">Ping</span>
          {isPingActive && <span className="w-1.5 h-1.5 rounded-full bg-[#5B21D6] animate-pulse shrink-0 ml-auto" />}
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayPing}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>

      {/* 2. Download */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isDownActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M19 14l-7 7m0 0l-7-7m7 7V3" />
          </svg>
          <span className="truncate">Download</span>
          {isDownActive && <span className="w-1.5 h-1.5 rounded-full bg-[#5B21D6] animate-pulse shrink-0 ml-auto" />}
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayDown}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">Mbps</span>
        </div>
      </div>

      {/* 3. Upload */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          isUpActive
            ? 'bg-white border-[#5B21D6] shadow-[0_4px_16px_-4px_rgba(91,33,214,0.25)] ring-1 ring-[#5B21D6]'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" d="M5 10l7-7m0 0l7 7m-7-7v18" />
          </svg>
          <span className="truncate">Upload</span>
          {isUpActive && <span className="w-1.5 h-1.5 rounded-full bg-[#5B21D6] animate-pulse shrink-0 ml-auto" />}
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayUp}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">Mbps</span>
        </div>
      </div>

      {/* 4. Bufferbloat */}
      <div
        className={`p-3 sm:p-3.5 rounded-2xl border transition-all duration-200 text-left min-w-0 overflow-hidden ${
          bufferbloat != null
            ? 'bg-white border-[#5B21D6] shadow-sm'
            : 'bg-[#F8F9FA] border-[#E7E0EC]'
        }`}
      >
        <div className="flex items-center gap-1.5 text-[11px] sm:text-xs font-semibold text-[#49454F] uppercase tracking-wider mb-1 truncate">
          <svg className="w-3.5 h-3.5 text-[#5B21D6] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span className="truncate">Bufferbloat</span>
        </div>
        <div className="flex items-baseline gap-1 truncate">
          <span className="font-mono-num text-xl sm:text-2xl md:text-3xl font-extrabold text-[#1C1B1F] truncate">
            {displayBufferbloat}
          </span>
          <span className="text-xs font-medium text-[#49454F] shrink-0">ms</span>
        </div>
      </div>
    </div>
  )
}
