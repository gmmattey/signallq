"use client";

import { useModoGamerRunner } from "./useModoGamerRunner";
import { ModoGamerTestPanel } from "./ModoGamerTestPanel";
import { type GameGenre } from "./modoGamerEngine";

interface ModoGamerTestSectionProps {
  selectedGame: string
  selectedGenre: GameGenre
}

export function ModoGamerTestSection({
  selectedGame,
  selectedGenre,
}: ModoGamerTestSectionProps) {
  const { state: runner, startTest, cancelTest } = useModoGamerRunner();

  const handleRun = () => {
    startTest(selectedGame, selectedGenre);
  };

  return (
    <section id="teste" className="border-t border-[#1F242C] px-6 py-20 text-center">
      <div className="mx-auto max-w-[940px]">
        <div className="flex h-[30px] w-fit items-center gap-2 rounded-full border border-[#3A3446] px-3.5 text-[13px] font-semibold text-[#C9B6FF] mx-auto">
          <span className="h-2 w-2 rounded-full bg-[#7DDB92]" />
          Teste de estabilidade gamer
        </div>

        <h2 className="m-0 mt-5 text-balance text-[clamp(32px,4.5vw,52px)] font-bold tracking-[-1.5px] text-white">
          Veja a estabilidade da sua conexão agora
        </h2>

        <p className="mx-auto mt-4 max-w-[34em] text-pretty text-[17px] leading-7 text-[#CAC4D0]">
          Medição multicamada no navegador: latência ociosa, oscilação (jitter MAD) e inchaço de fila sob upload (bufferbloat), calibrados para as exigências de <strong className="text-white">{selectedGame}</strong> ({selectedGenre}).
        </p>

        <ModoGamerTestPanel
          runner={runner}
          selectedGame={selectedGame}
          selectedGenre={selectedGenre}
          onStart={handleRun}
          onCancel={cancelTest}
        />
      </div>
    </section>
  );
}
