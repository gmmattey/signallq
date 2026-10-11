// Funções puras do resultado do teste de velocidade — sem React, sem rede.
// Alinhadas com o vocabulário canônico do app Android (DiagnosticoQualidadeSpeedtest / ResultadoVelocidadeScreen).

export type UsageLevel = { n: string; s: 'Ótimo' | 'Bom' | 'Ruim'; c: string }

const GREEN = '#146C2E'
const AMBER = '#8A5000'
const RED = '#BA1A1A'

export function formatMetric(value: number | null): string {
  if (value == null) return '—'
  return value >= 100 ? String(Math.round(value)) : value.toFixed(1)
}

export function getVerdict(downloadMbps: number | null): { title: string; subtitle: string } {
  const d = downloadMbps ?? 0
  if (d >= 100) return { title: 'Sua internet está ótima.', subtitle: 'Rápida o bastante para vários aparelhos ao mesmo tempo.' }
  if (d >= 25) return { title: 'Sua internet está boa.', subtitle: 'Atende bem o dia a dia: vídeo, trabalho e redes sociais.' }
  if (d >= 8) return { title: 'Sua internet dá para o básico.', subtitle: 'Funciona para o essencial, mas pode ficar apertada com vários aparelhos.' }
  return { title: 'Internet lenta.', subtitle: 'Dá para mensagens e páginas simples. Vídeos e chamadas podem travar.' }
}

function level(ok: boolean, mid: boolean): { s: 'Ótimo' | 'Bom' | 'Ruim'; c: string } {
  if (ok) return { s: 'Ótimo', c: GREEN }
  if (mid) return { s: 'Bom', c: AMBER }
  return { s: 'Ruim', c: RED }
}

// Alinhado com SpeedtestQualityClassifier.kt do Android:
export function getBufferbloatStatus(bufferbloatMs: number | null): { label: string; color: string } {
  const b = bufferbloatMs ?? 0
  if (b < 5) return { label: 'Excelente', color: GREEN }
  if (b < 30) return { label: 'Bom', color: GREEN }
  if (b < 100) return { label: 'Regular', color: AMBER }
  return { label: 'Ruim', color: RED }
}

export function getPingStatus(pingMs: number | null): { label: string; color: string } {
  const p = pingMs ?? 0
  if (p <= 20) return { label: 'Excelente', color: GREEN }
  if (p <= 50) return { label: 'Bom', color: GREEN }
  if (p <= 100) return { label: 'Regular', color: AMBER }
  return { label: 'Ruim', color: RED }
}

// Alinhado com SpeedtestQualityClassifier.kt do Android:
// - Vídeos em alta qualidade (streaming)
// - Jogos online (gamer)
// - Chamadas de vídeo (videoChamada)
export function getUsageLevels(
  downloadMbps: number | null,
  uploadMbps: number | null,
  pingMs: number | null,
  bufferbloatMs?: number | null
): UsageLevel[] {
  const d = downloadMbps ?? 0
  const u = uploadMbps ?? 0
  const p = pingMs ?? 0
  const b = bufferbloatMs ?? 0

  return [
    {
      n: 'Vídeos em alta qualidade',
      ...level(d >= 25 && (p === 0 || p <= 200), d >= 15 && (p === 0 || p <= 500)),
    },
    {
      n: 'Jogos online',
      ...level(
        d >= 10 && u >= 3 && (p === 0 || p <= 50) && b <= 30,
        d >= 5 && u >= 1 && (p === 0 || p <= 100) && b <= 100
      ),
    },
    {
      n: 'Chamadas de vídeo',
      ...level(
        d >= 10 && u >= 3 && (p === 0 || p <= 80) && b <= 50,
        d >= 5 && u >= 1 && (p === 0 || p <= 150)
      ),
    },
  ]
}
