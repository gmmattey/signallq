"use client";

import { useEffect, useRef } from "react";
import { type GameGenre } from "./modoGamerEngine";
import { type RunnerState } from "./useModoGamerRunner";
import { ModoGamerIdleView } from "./ModoGamerIdleView";
import { ModoGamerRunningView } from "./ModoGamerRunningView";
import { ModoGamerResultView } from "./ModoGamerResultView";
import { ModoGamerErrorView } from "./ModoGamerErrorView";

interface ModoGamerTestPanelProps {
  runner: RunnerState
  selectedGame: string
  selectedGenre: GameGenre
  onStart: () => void
  onCancel: () => void
}

export function ModoGamerTestPanel({
  runner,
  selectedGame,
  selectedGenre,
  onStart,
  onCancel,
}: ModoGamerTestPanelProps) {
  const resultHeadingRef = useRef<HTMLHeadingElement>(null);
  const errorHeadingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (runner.phase === 'done') {
      resultHeadingRef.current?.focus();
    } else if (runner.phase === 'error') {
      errorHeadingRef.current?.focus();
    }
  }, [runner.phase]);

  // Mensagem persistente para leitores de tela na live region (apenas transições de fase)
  let liveAnnouncement = "";
  if (runner.phase === 'locating') {
    liveAnnouncement = "Localizando borda regional mais próxima.";
  } else if (runner.phase === 'measuring_ping') {
    liveAnnouncement = "Medindo latência ociosa e oscilação de rota.";
  } else if (runner.phase === 'measuring_upload') {
    liveAnnouncement = "Medindo vazão de upload e bufferbloat sob carga.";
  } else if (runner.phase === 'done' && runner.result) {
    liveAnnouncement = `Teste concluído. Nota ${runner.result.overallScore} de 100, Grau ${runner.result.grade}. ${runner.result.headline}`;
  } else if (runner.phase === 'error') {
    liveAnnouncement = `Erro: ${runner.errorMsg || 'Não foi possível medir a conexão.'}`;
  }

  return (
    <div className="mt-9 rounded-[32px] border border-[#262B33] bg-[#14171C] p-6 text-left shadow-2xl sm:p-10">
      {/* Live region permanente para leitores de tela */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">
        {liveAnnouncement}
      </div>

      {runner.phase === 'idle' && (
        <ModoGamerIdleView
          selectedGame={selectedGame}
          selectedGenre={selectedGenre}
          onStart={onStart}
        />
      )}

      {(runner.phase === 'locating' ||
        runner.phase === 'measuring_ping' ||
        runner.phase === 'measuring_upload') && (
        <ModoGamerRunningView
          runner={runner}
          onCancel={onCancel}
        />
      )}

      {runner.phase === 'done' && runner.result && (
        <ModoGamerResultView
          runner={runner}
          selectedGame={selectedGame}
          selectedGenre={selectedGenre}
          onRetest={onStart}
          resultHeadingRef={resultHeadingRef}
        />
      )}

      {runner.phase === 'error' && (
        <ModoGamerErrorView
          errorMsg={runner.errorMsg}
          onRetry={onStart}
          errorHeadingRef={errorHeadingRef}
        />
      )}
    </div>
  );
}
