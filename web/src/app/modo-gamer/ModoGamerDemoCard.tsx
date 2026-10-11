"use client";

import { useEffect, useState } from "react";
import Image from "next/image";

interface ModoGamerDemoCardProps {
  selectedGame: string
  activeGameImg: string
}

export function ModoGamerDemoCard({ selectedGame, activeGameImg }: ModoGamerDemoCardProps) {
  const [tick, setTick] = useState(0);

  useEffect(() => {
    // Respeita prefers-reduced-motion
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    let timer: ReturnType<typeof setInterval> | null = null;

    const startTimer = () => {
      if (!timer) {
        timer = setInterval(() => setTick((t) => t + 1), 120);
      }
    };

    const stopTimer = () => {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    };

    const handleVisibility = () => {
      if (typeof document !== 'undefined' && document.hidden) {
        stopTimer();
      } else {
        startTimer();
      }
    };

    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibility);
      if (!document.hidden) {
        startTimer();
      }
    }

    return () => {
      stopTimer();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', handleVisibility);
      }
    };
  }, []);

  const sin = Math.sin;
  const demoProfiles = [
    { title: 'Pronto para ranked', color: '#7DDB92', subtitle: 'Rota estável. Pode entrar na fila.', lat: 24, jit: 3, loss: 0, amp: 0.3, game: 'Fortnite' },
    { title: 'Pode ter atrasos', color: '#FFB867', subtitle: 'Jitter no limite. Feche downloads e tente de novo.', lat: 58, jit: 19, loss: 1, amp: 0.7, game: 'Free Fire' },
    { title: 'Evite o ranked agora', color: '#FF8A7A', subtitle: 'Oscilação alta na rota. Tente cabo ou chegue perto do roteador.', lat: 112, jit: 46, loss: 4, amp: 1, game: 'COD Mobile' },
  ];
  const demoIndex = Math.floor(tick / 24) % 3;
  const activeDemo = demoProfiles[demoIndex];
  const demoBars = Array.from({ length: 28 }, (_, i) => ({
    height: Math.round(22 + 50 * (0.5 + 0.5 * sin(i * 0.5 + tick * 0.2)) * (1 - activeDemo.amp * 0.5 + activeDemo.amp * 0.5 * Math.abs(sin(i * 7.3 + tick * 0.4))) + activeDemo.amp * 14),
  }));

  return (
    <div className="flex justify-center">
      <div className="w-full max-w-[380px] rounded-[32px] border border-[#262B33] bg-[#14171C] p-7 shadow-[0_40px_80px_-30px_rgba(91,33,214,0.55)]">
        <div className="flex items-center justify-between text-[14px] text-[#CAC4D0]">
          <span>Exemplo demonstrativo</span>
          <span className="flex items-center gap-2 rounded-full bg-[#1F242C] py-1 pl-1.5 pr-3 font-semibold text-[#F5F2F7]">
            <Image
              src={activeGameImg}
              alt=""
              width={20}
              height={20}
              className="h-5 w-5 rounded-full object-cover shrink-0"
              unoptimized
            />
            <span className="truncate max-w-[130px]">{selectedGame || activeDemo.game}</span>
          </span>
        </div>

        <div style={{ color: activeDemo.color }} className="mt-7 text-[30px] font-bold leading-[34px] tracking-[-0.8px]">
          {activeDemo.title}
        </div>
        <div className="mt-2 text-[15px] leading-[22px] text-[#CAC4D0]">{activeDemo.subtitle}</div>

        <div className="mt-6 flex h-[84px] items-end gap-1">
          {demoBars.map((b, i) => (
            <div key={i} style={{ height: `${b.height}%`, background: activeDemo.color }} className="flex-1 rounded-[3px] opacity-85" />
          ))}
        </div>

        <div className="mt-6 grid grid-cols-3 gap-3 border-t border-[#262B33] pt-5">
          <div>
            <div className="text-[12px] text-[#CAC4D0]">Latência</div>
            <div className="mt-1 font-mono text-[26px] font-bold">
              {activeDemo.lat}
              <span className="text-[13px] font-medium text-[#CAC4D0]"> ms</span>
            </div>
          </div>
          <div>
            <div className="text-[12px] text-[#CAC4D0]">Jitter</div>
            <div className="mt-1 font-mono text-[26px] font-bold">
              {activeDemo.jit}
              <span className="text-[13px] font-medium text-[#CAC4D0]"> ms</span>
            </div>
          </div>
          <div>
            <div className="text-[12px] text-[#CAC4D0]">Perda (App)</div>
            <div className="mt-1 font-mono text-[26px] font-bold">
              {activeDemo.loss}
              <span className="text-[13px] font-medium text-[#CAC4D0]"> %</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
