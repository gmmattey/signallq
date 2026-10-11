import Link from 'next/link'
import { Speedometer } from '@/components/Speedometer'
import { formatMetric, getUsageLevels, getVerdict } from './speedTestVerdict'
import type { SpeedTestState } from './useSpeedTestEngine'

function Metric({ label, value, unit, text }: { label: string; value: string; unit: string; text: string }) {
  return (
    <div>
      <div className="text-[13px] font-medium text-[#49454F]">{label}</div>
      <div className="mt-[6px] text-[32px] font-bold tracking-[-1px]">
        {value}
        <span className="text-[14px] font-medium tracking-normal text-[#49454F]"> {unit}</span>
      </div>
      <div className="mt-[6px] text-[13px] leading-5 text-[#49454F]">{text}</div>
    </div>
  )
}

export function SpeedTestResult({ state, onRetry }: { state: SpeedTestState; onRetry: () => void }) {
  const verdict = getVerdict(state.download)
  const usage = getUsageLevels(state.download, state.upload, state.ping)
  const dl = state.download ?? 0
  const tone = dl >= 100 ? 'green' : dl >= 25 ? 'purple' : dl >= 8 ? 'amber' : 'red'

  return (
    <div>
      <div className="flex justify-center pb-2">
        <Speedometer
          value={dl}
          unit="Mbps"
          mode="settled"
          size={240}
          tone={tone}
          metricLabel="Download"
        />
      </div>
      <div className="text-[13px] font-medium text-[#49454F]">Resultado</div>
      <div className="mt-2 text-balance text-[clamp(28px,3.8vw,38px)] font-bold leading-[1.1] tracking-[-1px] text-[#1C1B1F]">
        {verdict.title}
      </div>
      <p className="mt-3 text-pretty text-[16px] leading-6 text-[#49454F]">{verdict.subtitle}</p>

      <div className="mt-8 grid grid-cols-3 gap-4 border-t border-[#E7E0EC] pt-6">
        <Metric label="Download" value={formatMetric(state.download)} unit="Mbps" text="Velocidade para baixar arquivos." />
        <Metric label="Upload" value={formatMetric(state.upload)} unit="Mbps" text="Velocidade para chamadas e envio." />
        <Metric label="Latência" value={state.ping == null ? '—' : String(state.ping)} unit="ms" text="Tempo de resposta." />
      </div>

      <div className="mt-8">
        <div className="mb-1 text-[13px] font-semibold text-[#49454F]">Com essa conexão</div>
        {usage.map((u) => (
          <div key={u.n} className="flex justify-between gap-4 border-b border-[#F3EEFA] py-3 text-[15px]">
            <span>{u.n}</span>
            <b style={{ color: u.c }}>{u.s}</b>
          </div>
        ))}
      </div>

      <div className="mt-8 flex flex-wrap items-center justify-between gap-x-6 gap-y-4 rounded-3xl bg-[#EAE0FF] p-6">
        <div className="max-w-[20em]">
          <div className="text-[18px] font-semibold text-[#210A5C]">Algo não parece certo?</div>
          <div className="mt-1 text-[14px] leading-5 text-[#210A5C]">O app descobre a causa e diz o que fazer.</div>
        </div>
        <Link href="/#baixar" className="rounded-full bg-[#5B21D6] px-6 py-3 text-[14px] font-medium text-white no-underline">
          Diagnosticar no app
        </Link>
      </div>

      <button type="button" onClick={onRetry} className="mt-6 cursor-pointer border-0 bg-transparent px-0 py-2 text-[15px] font-semibold text-[#5B21D6]">
        Testar de novo
      </button>
    </div>
  )
}
