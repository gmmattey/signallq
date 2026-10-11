"use client";

import { useEffect, useRef, useState } from "react";

interface SpeedometerProps {
  value?: number;
  maxScale?: number;
  unit?: string;
  tone?: "purple" | "green" | "amber" | "orange" | "red" | "neutral";
  size?: number;
  mode?: "forming" | "measuring" | "settled" | "restored" | "quiet";
  showScale?: boolean;
  hideValue?: boolean;
  metricLabel?: string;
}

function polar(deg: number, r: number, cx: number, cy: number) {
  const rad = (deg * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy - r * Math.sin(rad) };
}

function arcPath(t0: number, t1: number, r: number, cx: number, cy: number, startA: number, sweep: number) {
  const a0 = startA - t0 * sweep;
  const a1 = startA - t1 * sweep;
  const p0 = polar(a0, r, cx, cy);
  const p1 = polar(a1, r, cx, cy);
  const large = Math.abs(a0 - a1) > 180 ? 1 : 0;
  return `M ${p0.x.toFixed(2)} ${p0.y.toFixed(2)} A ${r} ${r} 0 ${large} 1 ${p1.x.toFixed(2)} ${p1.y.toFixed(2)}`;
}

function curve(t: number) {
  return Math.pow(Math.max(0, Math.min(1, t)), 0.78);
}

function scaleSet(max: number) {
  const sets: Record<number, number[]> = {
    10: [0, 2.5, 5, 7.5, 10],
    20: [0, 5, 10, 15, 20],
    50: [0, 10, 25, 40, 50],
    100: [0, 25, 50, 75, 100],
    250: [0, 50, 100, 175, 250],
    500: [0, 100, 250, 375, 500],
    1000: [0, 100, 250, 500, 750, 1000],
    2500: [0, 500, 1000, 1750, 2500],
    5000: [0, 1000, 2500, 3750, 5000],
  };
  return sets[max] || sets[100];
}

function autoMax(value: number, unit: string) {
  const v = value || 0;
  if (unit === "ms") {
    if (v <= 20) return 20;
    if (v <= 50) return 50;
    if (v <= 100) return 100;
    return 200;
  }
  if (v <= 10) return 10;
  if (v <= 50) return 50;
  if (v <= 100) return 100;
  if (v <= 250) return 250;
  if (v <= 500) return 500;
  if (v <= 1000) return 1000;
  if (v <= 2500) return 2500;
  return 5000;
}

export function Speedometer({
  value = 0,
  maxScale,
  unit = "Mbps",
  tone = "purple",
  size = 220,
  mode = "settled",
  showScale = true,
  hideValue = false,
  metricLabel = "",
}: SpeedometerProps) {
  const [anim, setAnim] = useState(mode === "quiet" ? 1 : 0);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    if (mode === "quiet") {
      setAnim(1);
      return;
    }
    const prefersReducedMotion =
      typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (prefersReducedMotion) {
      setAnim(1);
      return;
    }

    const dur = mode === "restored" ? 550 : 800;
    const start = performance.now();
    const ease = (t: number) => 1 - Math.pow(1 - t, 3);
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / dur);
      setAnim(ease(t));
      if (t < 1) {
        rafRef.current = requestAnimationFrame(step);
      }
    };
    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [mode]);

  const max = maxScale || autoMax(value, unit);
  const tones = {
    purple: "#5B21D6",
    green: "#146C2E",
    amber: "#8A5000",
    orange: "#C44700",
    red: "#BA1A1A",
    neutral: "#79747E",
  };
  const activeColor = tones[tone] || tones.purple;
  const trackColor = "#E7E0EC";
  const textPrimary = "#1C1B1F";
  const textSecondary = "#49454F";
  const dotRing = "#fff";

  const cx = 150;
  const cy = 170;
  const r = 125;
  const startA = 188;
  const sweep = 196;
  const strokeWidth = size >= 200 ? 14 : size >= 150 ? 16 : 18;
  const valueFontSize = size >= 200 ? 52 : size >= 150 ? 38 : 24;
  const unitFontSize = size >= 200 ? 13 : size >= 150 ? 12 : 10.5;

  const fraction = max > 0 ? value / max : 0;
  const t = curve(fraction) * anim;

  const trackPath = arcPath(0, 1, r, cx, cy, startA, sweep);
  const activePath = arcPath(0, Math.max(t, 0.0006), r, cx, cy, startA, sweep);
  const dotPos = polar(startA - t * sweep, r, cx, cy);
  const needleAngle = startA - t * sweep;
  const needleTip = polar(needleAngle, r - 22, cx, cy);
  const needleBase = polar(needleAngle, 58, cx, cy);

  const shouldShowScale = showScale && size >= 140;
  const majors = shouldShowScale ? scaleSet(max) : [];
  const majorTicks = majors.map((v) => {
    const tt = curve(v / max);
    const ang = startA - tt * sweep;
    const inner = polar(ang, r - strokeWidth / 2 - 6, cx, cy);
    const outer = polar(ang, r - strokeWidth / 2 - 16, cx, cy);
    const labelPos = polar(ang, r - strokeWidth / 2 - 31, cx, cy);
    const label = v >= 1000 ? (v / 1000).toString().replace(/\.0$/, "") + "k" : String(v);
    return { x1: inner.x, y1: inner.y, x2: outer.x, y2: outer.y, lx: labelPos.x, ly: labelPos.y, label };
  });

  const minorTicks: { x1: number; y1: number; x2: number; y2: number }[] = [];
  if (shouldShowScale) {
    const n = 27;
    for (let i = 0; i <= n; i++) {
      const tt = i / n;
      if (majors.some((v) => Math.abs(curve(v / max) - tt) < 0.02)) continue;
      const ang = startA - tt * sweep;
      const inner = polar(ang, r - strokeWidth / 2 - 6, cx, cy);
      const outer = polar(ang, r - strokeWidth / 2 - 11, cx, cy);
      minorTicks.push({ x1: inner.x, y1: inner.y, x2: outer.x, y2: outer.y });
    }
  }

  const scale = size / 300;
  const textTopPx = Math.round(168 * scale);
  const vh = Math.round(size * (200 / 300));
  const dotR = size >= 200 ? 7 : size >= 150 ? 6 : 5;
  const pivotR = size >= 200 ? 5 : size >= 150 ? 4 : 3.5;
  const displayVal = Math.round(value * anim);

  return (
    <div
      style={{ width: `${size}px`, height: `${vh}px` }}
      className="relative flex flex-shrink-0 items-center justify-center"
      role="img"
      aria-label={`Velocímetro: ${displayVal} ${unit}`}
    >
      <svg width={size} height={vh} viewBox="0 0 300 200" className="absolute inset-0 overflow-visible" aria-hidden="true">
        <path d={trackPath} fill="none" stroke={trackColor} strokeWidth={strokeWidth} strokeLinecap="round" />
        <path
          d={activePath}
          fill="none"
          stroke={activeColor}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          style={{ filter: "drop-shadow(0 0 8px rgba(91,33,214,0.4))" }}
        />
        {shouldShowScale && (
          <>
            {minorTicks.map((tick, idx) => (
              <line
                key={idx}
                x1={tick.x1}
                y1={tick.y1}
                x2={tick.x2}
                y2={tick.y2}
                stroke={textSecondary}
                strokeWidth={1.2}
                opacity={0.28}
                strokeLinecap="round"
              />
            ))}
            {majorTicks.map((tick, idx) => (
              <g key={idx}>
                <line
                  x1={tick.x1}
                  y1={tick.y1}
                  x2={tick.x2}
                  y2={tick.y2}
                  stroke={textSecondary}
                  strokeWidth={1.8}
                  opacity={0.55}
                  strokeLinecap="round"
                />
                <text
                  x={tick.lx}
                  y={tick.ly}
                  fill={textSecondary}
                  fontSize={11}
                  fontWeight={600}
                  textAnchor="middle"
                  dominantBaseline="middle"
                >
                  {tick.label}
                </text>
              </g>
            ))}
          </>
        )}
        <circle
          cx={dotPos.x}
          cy={dotPos.y}
          r={dotR}
          fill={activeColor}
          stroke={dotRing}
          strokeWidth={2}
          style={{ filter: "drop-shadow(0 0 8px rgba(91,33,214,0.4))" }}
        />
        <line
          x1={needleBase.x}
          y1={needleBase.y}
          x2={needleTip.x}
          y2={needleTip.y}
          stroke={activeColor}
          strokeWidth={3}
          strokeLinecap="round"
          style={{ filter: "drop-shadow(0 0 8px rgba(91,33,214,0.4))" }}
        />
        <circle cx={needleBase.x} cy={needleBase.y} r={pivotR} fill={activeColor} stroke={dotRing} strokeWidth={2} />
      </svg>

      {!hideValue ? (
        <div
          style={{ top: `${textTopPx}px` }}
          className="absolute left-1/2 flex -translate-x-1/2 -translate-y-[30%] flex-col items-center text-center"
        >
          <div
            style={{ fontSize: `${valueFontSize}px`, color: textPrimary }}
            className="font-mono font-extrabold leading-none tracking-[-1px]"
          >
            {displayVal}
          </div>
          <div style={{ fontSize: `${unitFontSize}px`, color: textSecondary }} className="mt-1 font-semibold">
            {unit}
          </div>
          {metricLabel && (
            <div className="mt-1 text-[11px] font-semibold uppercase tracking-wider text-[#49454F]">
              {metricLabel}
            </div>
          )}
        </div>
      ) : (
        <div
          style={{ background: activeColor }}
          className="absolute left-1/2 top-[56%] h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 animate-ping rounded-full"
        />
      )}
    </div>
  );
}
