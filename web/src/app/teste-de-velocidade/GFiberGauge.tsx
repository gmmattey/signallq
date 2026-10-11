"use client";

import React from 'react'

interface GFiberGaugeProps {
  value?: number
  unit?: string
  children?: React.ReactNode
  size?: number
}

// Mapeamento progressivo suave para 1 Gbps
function speedToFraction(speed: number, unit: string): number {
  if (speed <= 0) return 0
  if (unit === 'ms') {
    if (speed >= 150) return 1
    return Math.min(1, Math.pow(speed / 150, 0.75))
  }

  // Mbps: escala progressiva suave até 1000 Mbps
  if (speed >= 1000) return 1
  if (speed <= 10) return (speed / 10) * 0.12
  if (speed <= 50) return 0.12 + ((speed - 10) / 40) * 0.18
  if (speed <= 100) return 0.30 + ((speed - 50) / 50) * 0.20
  if (speed <= 250) return 0.50 + ((speed - 100) / 150) * 0.20
  if (speed <= 500) return 0.70 + ((speed - 250) / 250) * 0.15
  return 0.85 + ((speed - 500) / 500) * 0.15
}

export function GFiberGauge({ value = 0, unit = 'Mbps', children, size = 380 }: GFiberGaugeProps) {
  const fraction = Math.max(0, Math.min(1, speedToFraction(value, unit)))

  // Parâmetros geométricos do arco (horseshoe aberto embaixo)
  // Centro: (200, 160), Raio: 135px
  const cx = 200
  const cy = 160
  const r = 135

  // Ângulos em graus (0 = 12h / topo). Varredura de -120° a +120° (240°)
  const sweepDeg = 240
  const startDeg = -120
  const currentDeg = startDeg + fraction * sweepDeg

  // Comprimento do arco: 2 * PI * 135 * (240 / 360) = 565.48px
  const arcLength = 565.48
  const strokeOffset = arcLength * (1 - fraction)

  // Coordenadas das extremidades do arco
  // start (-120°): x = 200 + 135 * sin(-120°) = 200 - 116.91 = 83.09, y = 160 - 135 * cos(-120°) = 160 + 67.5 = 227.5
  // end (+120°): x = 200 + 116.91 = 316.91, y = 227.5
  const startX = 83.1
  const startY = 227.5
  const endX = 316.9
  const endY = 227.5

  // Ponto atual da ponta iluminada (cursor)
  const currentRad = currentDeg * (Math.PI / 180)
  const cursorX = cx + r * Math.sin(currentRad)
  const cursorY = cy - r * Math.cos(currentRad)

  // Ticks sutis de referência ao longo do anel (apenas marcas limpas, sem texto interno)
  const ticks = [0.12, 0.30, 0.50, 0.70, 0.85]

  return (
    <div
      style={{ width: `${size}px`, maxWidth: '100%' }}
      className="relative mx-auto flex flex-col items-center select-none"
      role="img"
      aria-label={`Velocímetro SignallQ: ${Math.round(value)} ${unit}`}
    >
      <div className="relative w-full aspect-[400/280] flex items-center justify-center">
        <svg viewBox="0 0 400 280" className="absolute inset-0 w-full h-full overflow-visible">
          <defs>
            <linearGradient id="gfiberGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#8B5CF6" />
              <stop offset="50%" stopColor="#7C3AED" />
              <stop offset="100%" stopColor="#5B21D6" />
            </linearGradient>
            <filter id="cursorGlow" x="-50%" y="-50%" width="200%" height="200%">
              <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#5B21D6" floodOpacity="0.6" />
            </filter>
            <filter id="arcGlow" x="-20%" y="-20%" width="140%" height="140%">
              <feDropShadow dx="0" dy="0" stdDeviation="4" floodColor="#7C3AED" floodOpacity="0.3" />
            </filter>
          </defs>

          {/* 1. Trilha de fundo (cinza neutro suave, espessura limpa de 12px) */}
          <path
            d={`M ${startX} ${startY} A ${r} ${r} 0 1 1 ${endX} ${endY}`}
            fill="none"
            stroke="#F1F3F4"
            strokeWidth="12"
            strokeLinecap="round"
          />

          {/* 2. Traços de referência sutis (ticks) no anel */}
          {ticks.map((t, idx) => {
            const deg = (startDeg + t * sweepDeg) * (Math.PI / 180)
            const x1 = cx + (r - 6) * Math.sin(deg)
            const y1 = cy - (r - 6) * Math.cos(deg)
            const x2 = cx + (r + 6) * Math.sin(deg)
            const y2 = cy - (r + 6) * Math.cos(deg)
            return (
              <line
                key={idx}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke="#D1D5DB"
                strokeWidth="1.5"
                strokeLinecap="round"
              />
            )
          })}

          {/* 3. Arco ativo iluminado */}
          <path
            d={`M ${startX} ${startY} A ${r} ${r} 0 1 1 ${endX} ${endY}`}
            fill="none"
            stroke="url(#gfiberGradient)"
            strokeWidth="12"
            strokeLinecap="round"
            strokeDasharray={arcLength}
            strokeDashoffset={strokeOffset}
            filter="url(#arcGlow)"
            className="transition-[stroke-dashoffset] duration-150 ease-out"
          />

          {/* 4. Cursor / ponteiro iluminado deslizante na ponta do arco */}
          {fraction > 0.005 && (
            <g className="transition-[transform] duration-150 ease-out">
              <circle
                cx={cursorX}
                cy={cursorY}
                r="9"
                fill="#5B21D6"
                stroke="#FFFFFF"
                strokeWidth="2.5"
                filter="url(#cursorGlow)"
              />
            </g>
          )}

          {/* 5. Rótulos das extremidades posicionados nos pés do arco com espaço limpo */}
          <text
            x={startX - 6}
            y={startY + 26}
            fill="#79747E"
            fontSize="12"
            fontWeight="600"
            textAnchor="middle"
            className="font-mono-num"
          >
            0
          </text>
          <text
            x={endX + 6}
            y={endY + 26}
            fill="#79747E"
            fontSize="12"
            fontWeight="600"
            textAnchor="middle"
            className="font-mono-num"
          >
            {unit === 'ms' ? '150 ms' : '1 Gbps'}
          </text>
        </svg>

        {/* 6. Espaço central livre e desobstruído para o conteúdo */}
        <div className="absolute inset-0 flex flex-col items-center justify-center pt-2 pointer-events-none">
          {children}
        </div>
      </div>
    </div>
  )
}
