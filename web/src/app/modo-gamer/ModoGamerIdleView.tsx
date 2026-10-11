import { type GameGenre } from "./modoGamerEngine";

interface ModoGamerIdleViewProps {
  selectedGame: string
  selectedGenre: GameGenre
  onStart: () => void
}

export function ModoGamerIdleView({
  selectedGame,
  selectedGenre,
  onStart,
}: ModoGamerIdleViewProps) {
  return (
    <div className="py-4 text-center">
      <div
        aria-hidden="true"
        className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-[#1D1535] text-[#C9B6FF] border border-[#7B4DF0]/30 text-2xl font-bold"
      >
        ⚡
      </div>
      <h3 className="m-0 text-[24px] font-bold text-white">
        Pronto para testar {selectedGame}
      </h3>
      <p className="mx-auto mt-2 max-w-[28em] text-[15px] leading-6 text-[#CAC4D0]">
        O teste avaliará latência, jitter e fila de bufferbloat com a régua de sensibilidade de {selectedGenre}.
      </p>

      <div className="my-8 grid grid-cols-2 gap-3 text-left sm:grid-cols-4">
        <div className="rounded-2xl border border-[#262B33] bg-[#0E1116] p-3.5">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">1. Latência</div>
          <div className="mt-1 text-[13px] text-[#E7E0EC]">Ping ocioso na rota Anycast</div>
        </div>
        <div className="rounded-2xl border border-[#262B33] bg-[#0E1116] p-3.5">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">2. Jitter</div>
          <div className="mt-1 text-[13px] text-[#E7E0EC]">Desvio absoluto mediano (MAD)</div>
        </div>
        <div className="rounded-2xl border border-[#262B33] bg-[#0E1116] p-3.5">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">3. Bufferbloat</div>
          <div className="mt-1 text-[13px] text-[#E7E0EC]">Atraso da fila sob upload</div>
        </div>
        <div className="rounded-2xl border border-[#262B33] bg-[#0E1116] p-3.5">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">4. Diagnóstico</div>
          <div className="mt-1 text-[13px] text-[#E7E0EC]">Casa, linha ou provedor</div>
        </div>
      </div>

      <button
        type="button"
        onClick={onStart}
        className="cursor-pointer rounded-full border-0 bg-[#7B4DF0] px-10 py-4 text-[17px] font-semibold text-white shadow-[0_8px_24px_-4px_rgba(123,77,240,0.6)] hover:bg-[#8D64F5] hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C] transition-all"
      >
        Iniciar teste de conexão
      </button>
      <div className="mt-4 text-[13px] text-[#CAC4D0]">
        As medições vão direto à borda regional Anycast · Nada é salvo ou compartilhado
      </div>
    </div>
  )
}
