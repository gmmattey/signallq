import { useEffect, useRef, useState, type ReactNode } from 'react'
import { metric as formatMetric } from './metrics'

const PATHS = {
  home: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h5v-6h4v6h5V10"/>',
  speed: '<path d="M4 17a8 8 0 1116 0"/><path d="M12 17l4-5"/>',
  wifi: '<path d="M2 9a15 15 0 0120 0"/><path d="M5.5 12.5a10 10 0 0113 0"/><path d="M9 16a5 5 0 016 0"/><circle cx="12" cy="19.5" r="1"/>',
  game: '<rect x="2" y="7" width="20" height="11" rx="5"/><path d="M7 10v5M4.5 12.5h5"/><circle cx="16" cy="11.5" r=".8"/><circle cx="18" cy="14" r=".8"/>',
  history: '<path d="M3 12a9 9 0 109-9 9 9 0 00-7 3.5"/><path d="M3 4v4h4"/><path d="M12 7v5l3 2"/>',
  tools: '<path d="M14.5 6.5a4 4 0 005 5L20 12l-8 8a2.1 2.1 0 01-3-3l8-8z"/><path d="M15 4l5 5"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 00-.1-1.3l2-1.5-2-3.4-2.3 1a7 7 0 00-2.2-1.3L14 3h-4l-.4 2.5a7 7 0 00-2.2 1.3l-2.3-1-2 3.4 2 1.5A7 7 0 005 12c0 .4 0 .9.1 1.3l-2 1.5 2 3.4 2.3-1a7 7 0 002.2 1.3L10 21h4l.4-2.5a7 7 0 002.2-1.3l2.3 1 2-3.4-2-1.5c.1-.4.1-.9.1-1.3z"/>',
  laptop: '<rect x="4" y="5" width="16" height="11" rx="2"/><path d="M2 19h20"/>',
  router: '<rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 16.5h.01M11 16.5h.01"/><path d="M8 10a5.5 5.5 0 018 0M10 7.5a2.5 2.5 0 014 0"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 010 18M12 3a14 14 0 000 18"/>',
  play: '<path d="M7 5l12 7-12 7z"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  down: '<path d="M12 4v14M6 12l6 6 6-6"/>',
  up: '<path d="M12 20V6M6 12l6-6 6 6"/>',
  pulse: '<path d="M3 12h4l3-7 4 14 3-7h4"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  moon: '<path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5"/>',
  chevron: '<path d="M9 6l6 6-6 6"/>',
} as const
export type IconName = keyof typeof PATHS

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return <svg className="i" viewBox="0 0 24 24" width={size} height={size} aria-hidden="true" dangerouslySetInnerHTML={{ __html: PATHS[name] }} />
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) { return <section className={`card ${className}`}>{children}</section> }
export function Badge({ children, tone = '' }: { children: ReactNode; tone?: string }) { return <span className={`badge ${tone}`}>{children}</span> }
export function Notice({ title, children, tone = '' }: { title: string; children: ReactNode; tone?: string }) { return <div className={`notice ${tone}`} role={tone === 'bad' ? 'alert' : undefined}><strong>{title}</strong><p>{children}</p></div> }
export function Row({ title, sub, value, badge }: { title: string; sub?: string; value?: ReactNode; badge?: ReactNode }) {
  return <div className="list-row"><div className="grow">{title}{sub && <small>{sub}</small>}</div>{badge}<span className="val">{value}</span></div>
}
export function Head({ title, sub, children }: { title: string; sub: string; children?: ReactNode }) {
  return <header className="head"><div><h1>{title}</h1><p>{sub}</p></div><div className="spacer">{children}</div></header>
}
export function Sk({ w = '100%', h = 12, round = false }: { w?: string; h?: number; round?: boolean }) { return <div className={`sk ${round ? 'c' : ''}`} style={{ width: w, height: h }} /> }

/** Mostra sempre o valor medido. Só anima a transição entre dois valores reais (nunca parte de zero). */
export function useCountUp(value: number | null): number | null {
  const [shown, setShown] = useState<number | null>(value)
  const from = useRef<number | null>(value)
  useEffect(() => {
    const previous = from.current
    from.current = value
    if (value == null || previous == null || previous === value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setShown(value); return }
    const start = performance.now()
    let raf = 0
    const tick = (now: number) => {
      const k = Math.min(1, (now - start) / 700), eased = 1 - Math.pow(1 - k, 3)
      setShown(previous + (value - previous) * eased)
      if (k < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => { cancelAnimationFrame(raf); setShown(value) }
  }, [value])
  return shown
}

function Spark({ points, color }: { points: number[]; color: string }) {
  const max = Math.max(...points), min = Math.min(...points)
  const d = points.map((v, i) => `${i ? 'L' : 'M'}${(i / (points.length - 1) * 100).toFixed(1)} ${(28 - (v - min) / (max - min || 1) * 24).toFixed(1)}`).join(' ')
  return <div style={{ color }}><svg className="spark" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden="true"><path d={d} vectorEffect="non-scaling-stroke" /></svg></div>
}

/** Valor + unidade + rótulo + estado. Ausência nunca vira número: aparece "—" e "Indisponível". */
export function Metric({ label, value, unit, digits = 1, color = 'var(--on-surface-variant)', note, series }: { label: string; value: number | null; unit: string; digits?: number; color?: string; note?: string; series?: number[] }) {
  const shown = useCountUp(value)
  return <div className="metric">
    <span className="lbl"><i style={{ background: color }} />{label}</span>
    <span className="sr">{formatMetric(value, unit, digits)}</span>
    <span className="num" aria-hidden="true">{shown == null ? '—' : shown.toFixed(digits).replace('.', ',')}{shown != null && <small>{unit}</small>}</span>
    <span className="verdict">{value == null ? 'Indisponível' : note ?? ''}</span>
    {series && series.length > 1 && <Spark points={series} color={color} />}
  </div>
}

export function Toast({ message }: { message: string | null }) { return <div className={`toast ${message ? 'show' : ''}`} role="status">{message}</div> }
