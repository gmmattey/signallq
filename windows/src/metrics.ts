import type { Measurement, MeasurementStatus } from './native'

export function metric(value: number | null, unit: string, digits = 1): string {
  return value == null ? 'Indisponível' : `${value.toFixed(digits).replace('.', ',')} ${unit}`
}

export function measurementStatus(status: MeasurementStatus): string {
  return ({ COMPLETE: 'Concluída', PARTIAL: 'Parcial', INCONCLUSIVE: 'Inconclusiva', CONTAMINATED: 'Contaminada', CANCELLED: 'Cancelada' })[status]
}

export function measurementSummary(measurement: Measurement | null): string {
  if (!measurement) return 'Nenhuma medição registrada.'
  if (measurement.status === 'COMPLETE') return 'Medição concluída. Confira os valores coletados.'
  if (measurement.status === 'PARTIAL') return 'Medição parcial. Alguns valores não puderam ser coletados.'
  if (measurement.status === 'CANCELLED') return 'Medição cancelada. Os valores coletados até aqui podem estar incompletos.'
  if (measurement.status === 'CONTAMINATED') return 'A rede mudou durante a medição. Faça uma nova coleta para comparar os valores.'
  return 'Medição inconclusiva. Confira os detalhes e tente novamente.'
}
