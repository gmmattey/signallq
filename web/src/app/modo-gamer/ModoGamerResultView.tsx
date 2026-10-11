import Link from "next/link";
import { type GameGenre, type ScoreBand } from "./modoGamerEngine";
import { type RunnerState } from "./useModoGamerRunner";

interface ModoGamerResultViewProps {
  runner: RunnerState
  selectedGame: string
  selectedGenre: GameGenre
  onRetest: () => void
  resultHeadingRef: React.RefObject<HTMLHeadingElement | null>
}

function getGradeColor(grade: ScoreBand) {
  switch (grade) {
    case 'A+':
    case 'A':
      return { text: '#7DDB92', bg: 'rgba(125,219,146,0.12)', border: 'rgba(125,219,146,0.3)' }
    case 'B':
      return { text: '#B5E77E', bg: 'rgba(181,231,126,0.12)', border: 'rgba(181,231,126,0.3)' }
    case 'C':
      return { text: '#FFB867', bg: 'rgba(255,184,103,0.12)', border: 'rgba(255,184,103,0.3)' }
    case 'D':
    case 'F':
    default:
      return { text: '#FF8A7A', bg: 'rgba(255,138,122,0.12)', border: 'rgba(255,138,122,0.3)' }
  }
}

function getMetricScoreColor(score: number | null) {
  if (score == null) return '#CAC4D0'
  if (score >= 80) return '#7DDB92'
  if (score >= 68) return '#FFB867'
  return '#FF8A7A'
}

export function ModoGamerResultView({
  runner,
  selectedGame,
  selectedGenre,
  onRetest,
  resultHeadingRef,
}: ModoGamerResultViewProps) {
  const result = runner.result
  if (!result) return null

  const gradeStyle = getGradeColor(result.grade)

  return (
    <div>
      {/* Result Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#262B33] pb-6">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <span
              style={{
                color: gradeStyle.text,
                backgroundColor: gradeStyle.bg,
                borderColor: gradeStyle.border,
              }}
              className="inline-flex items-center rounded-xl border px-3 py-1 font-mono text-[20px] font-bold"
            >
              {result.overallScore}/100 · Grau {result.grade}
            </span>
            {result.isPartial && (
              <span className="rounded-full bg-[#1F242C] px-3 py-1 text-[12px] font-medium text-[#CAC4D0] border border-[#3A3446]">
                {runner.uploadMbps == null || runner.bufferbloat == null
                  ? 'Estimado (sem bufferbloat/upload)'
                  : 'Estimado (sem perda UDP)'}
              </span>
            )}
            <span className="text-[14px] text-[#CAC4D0]">
              Calibrado para <strong className="text-white">{selectedGame}</strong> ({selectedGenre})
            </span>
          </div>

          <h3
            ref={resultHeadingRef}
            tabIndex={-1}
            className="m-0 mt-3 text-[26px] font-bold text-white leading-tight outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C]"
          >
            {result.headline}
          </h3>
          <p className="mt-1.5 max-w-[36em] text-[15px] leading-6 text-[#CAC4D0]">
            {result.detail}
          </p>
        </div>
      </div>

      {/* Culprit Diagnostic Badge */}
      <div className="my-6 rounded-2xl border border-[#262B33] bg-[#0E1116] p-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-xl" aria-hidden="true">
            {result.culprit === 'none' && '✅'}
            {result.culprit === 'home_wifi' && '📶'}
            {result.culprit === 'line_bufferbloat' && '⏳'}
            {result.culprit === 'line_upload' && '📤'}
            {result.culprit === 'isp_route' && '🌐'}
            {result.culprit === 'packet_loss' && '📦'}
          </span>
          <div>
            <div className="text-[12px] font-semibold uppercase tracking-wider text-[#CAC4D0]">
              Origem do gargalo
            </div>
            <div className="text-[14px] font-semibold text-white">
              {result.culprit === 'none' && 'Todos os elos medidos suportam a partida'}
              {result.culprit === 'home_wifi' && 'Oscilação de rota ou rede local (pode ser Wi-Fi ou rota)'}
              {result.culprit === 'line_bufferbloat' && 'Fila do roteador/operadora sob carga (Bufferbloat)'}
              {result.culprit === 'line_upload' && 'Vazão de upload no limite para partidas em grupo'}
              {result.culprit === 'isp_route' && 'Rota da operadora ou distância do servidor regional'}
              {result.culprit === 'packet_loss' && 'Descarte de pacotes durante a transmissão'}
            </div>
          </div>
        </div>
        <div className="text-[12px] text-[#CAC4D0]">
          {runner.edgeInfo?.city && runner.edgeInfo?.isp ? (
            <span>
              Borda Anycast: <strong>{runner.edgeInfo.city}</strong> · {runner.edgeInfo.isp}
            </span>
          ) : (
            'Borda Anycast regional'
          )}
        </div>
      </div>

      {/* The 5 Connection Chain Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {/* 1. Idle Latency */}
        <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-4">
          <div className="text-[12px] text-[#CAC4D0]">Latência ociosa</div>
          <div className="mt-1 font-mono text-[22px] font-bold text-white">
            {runner.idlePing}
            <span className="text-[12px] font-normal text-[#CAC4D0]"> ms</span>
          </div>
          <div
            style={{ color: getMetricScoreColor(result.pingScore) }}
            className="mt-2 text-[11px] font-semibold"
          >
            Nota {result.pingScore}/100
          </div>
        </div>

        {/* 2. Jitter */}
        <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-4">
          <div className="text-[12px] text-[#CAC4D0]">Jitter (MAD)</div>
          <div className="mt-1 font-mono text-[22px] font-bold text-white">
            {runner.jitter}
            <span className="text-[12px] font-normal text-[#CAC4D0]"> ms</span>
          </div>
          <div
            style={{ color: getMetricScoreColor(result.jitScore) }}
            className="mt-2 text-[11px] font-semibold"
          >
            Nota {result.jitScore}/100
          </div>
        </div>

        {/* 3. Bufferbloat */}
        <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-4">
          <div className="text-[12px] text-[#CAC4D0]">Bufferbloat</div>
          <div className="mt-1 font-mono text-[22px] font-bold text-white">
            {runner.bufferbloat != null ? `+${runner.bufferbloat}` : '—'}
            <span className="text-[12px] font-normal text-[#CAC4D0]"> ms</span>
          </div>
          <div
            style={{ color: getMetricScoreColor(result.bloatScore) }}
            className="mt-2 text-[11px] font-semibold"
          >
            {result.bloatScore != null ? `Nota ${result.bloatScore}/100` : 'Sem saturação'}
          </div>
        </div>

        {/* 4. Upload */}
        <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-4">
          <div className="text-[12px] text-[#CAC4D0]">Vazão de upload</div>
          <div className="mt-1 font-mono text-[22px] font-bold text-white">
            {runner.uploadMbps != null ? runner.uploadMbps : '—'}
            <span className="text-[12px] font-normal text-[#CAC4D0]"> Mbps</span>
          </div>
          <div
            style={{ color: getMetricScoreColor(result.uploadScore) }}
            className="mt-2 text-[11px] font-semibold"
          >
            {result.uploadScore != null ? `Nota ${result.uploadScore}/100` : 'Sem upload'}
          </div>
        </div>

        {/* 5. Packet Loss (Honest Ceiling) */}
        <div className="rounded-2xl border border-[#262B33] bg-[#14171C] p-4">
          <div className="text-[12px] text-[#CAC4D0]">Perda UDP</div>
          <div className="mt-1 text-[16px] font-bold text-[#E7E0EC]">Não medido</div>
          <div className="mt-2 text-[11px] font-medium text-[#CAC4D0]">
            Requer app nativo
          </div>
        </div>
      </div>

      {/* Actionable Guidance */}
      <div className="mt-7 border-t border-[#262B33] pt-6">
        <div className="text-[14px] font-bold text-white">O que tentar para resolver:</div>
        <ul className="mt-2.5 flex flex-col gap-2 pl-4 text-[14px] text-[#CAC4D0]">
          {result.actions.map((act, idx) => (
            <li key={idx} className="leading-6">
              {act}
            </li>
          ))}
        </ul>
      </div>

      {/* Radical Honesty: Gaps */}
      <div className="mt-5 rounded-2xl bg-[#0E1116] p-4 text-[13px] text-[#CAC4D0] leading-5">
        <div className="font-semibold text-white mb-1">Limitações do teste no navegador:</div>
        {result.unmeasuredGaps.map((gap, idx) => (
          <div key={idx}>· {gap}</div>
        ))}
      </div>

      {/* App CTA Card */}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-[#1D1535] border border-[#7B4DF0]/40 p-5">
        <div>
          <div className="text-[16px] font-bold text-white">
            Quer o teste profundo com sonda UDP real para {selectedGame}?
          </div>
          <div className="mt-1 text-[14px] text-[#E7DEFF]">
            O app SignallQ testa pacotes UDP reais para o beacon da AWS e mapeia o sinal Wi-Fi da sua casa.
          </div>
        </div>
        <Link
          href="/#baixar"
          className="rounded-full bg-[#7B4DF0] px-6 py-3 text-[14px] font-semibold text-white no-underline hover:bg-[#8D64F5] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#1D1535] transition-colors"
        >
          Baixar no Google Play
        </Link>
      </div>

      {/* Retest button */}
      <div className="mt-6 flex justify-center">
        <button
          type="button"
          onClick={onRetest}
          className="cursor-pointer border-0 bg-transparent text-[15px] font-semibold text-[#C9B6FF] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C9B6FF] focus-visible:ring-offset-2 focus-visible:ring-offset-[#14171C] transition-colors"
        >
          ↺ Refazer teste para {selectedGame}
        </button>
      </div>
    </div>
  )
}
