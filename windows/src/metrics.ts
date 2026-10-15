import type { Measurement } from './native'

export function metric(value: number | null, unit: string, digits = 1): string {
  return value == null ? 'Indisponível' : `${value.toFixed(digits).replace('.', ',')} ${unit}`
}

export function limitationTitle(status: Measurement['status']): string {
  return status === 'COMPLETED' ? 'Limitação da medição' : 'Resultado parcial'
}

export function qualitySummary(measurement: Measurement | null): string {
  if (!measurement) return 'Faça uma medição para ver a qualidade da conexão.'
  if (measurement.status !== 'COMPLETED') return 'A medição foi parcial. Não é seguro concluir a qualidade da conexão.'
  if ((measurement.packetLossPercent ?? 0) > 2 || (measurement.jitterMs ?? 0) > 30) {
    return 'Há instabilidade medida. Abra os detalhes antes de atribuir uma causa.'
  }
  return 'A medição foi concluída. Compare mais tarde para confirmar estabilidade ao longo do tempo.'
}
