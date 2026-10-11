import { Speedometer } from '@/components/Speedometer'

export function SpeedTestIdle({ onStart }: { onStart: () => void }) {
  return (
    <div className="text-center">
      <div className="flex justify-center pb-2">
        <Speedometer value={0} maxScale={100} unit="Mbps" mode="measuring" size={260} tone="purple" />
      </div>
      <button
        type="button"
        onClick={onStart}
        className="mt-2 cursor-pointer rounded-full border-0 bg-[#5B21D6] px-12 py-5 text-[18px] font-medium text-white shadow-[0_8px_20px_-8px_rgba(91,33,214,0.6)] hover:-translate-y-0.5"
      >
        Iniciar teste
      </button>
      <div className="mt-4 text-[14px] text-[#49454F]">
        Mede latência, download e upload. Nenhum dado pessoal é coletado.
      </div>
    </div>
  )
}
