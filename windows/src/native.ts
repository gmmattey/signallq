import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'

declare global {
  interface Window { __TAURI_INTERNALS__?: unknown }
}

export type MeasurementStatus = 'COMPLETE' | 'PARTIAL' | 'INCONCLUSIVE' | 'CONTAMINATED' | 'CANCELLED'
export type MeasurementPhase = {
  name: string
  method: string
  endpoint: string
  startedAtMs: number
  durationMs: number | null
  attempts: number
  validSamples: number
  bytesTransferred: number
  status: string
  error?: { code: string; httpStatus?: number; message: string } | null
  fallbackUsed?: boolean
  fallbackTrigger?: { code: string; httpStatus?: number; message: string } | null
}
export type Measurement = {
  schemaVersion: number
  executionId: string
  startedAtMs: number
  capturedAtMs: number
  status: MeasurementStatus
  downloadMbps: number | null
  uploadMbps: number | null
  latencyMs: number | null
  jitterMs: number | null
  packetLossPercent: number | null
  packetLossSource: string | null
  bufferbloatMs: number | null
  limitation: string | null
  phases: MeasurementPhase[]
  legacyStatus?: string | null
  networkId?: string | null
  ssid?: string | null
  localIp?: string | null
}
export type SpeedtestProgress = { executionId: string; phase: string; percent: number }
export type SpeedtestFailure = { executionId: string; message: string }

export type NetworkSnapshot = {
  interfaceName: string | null
  connectionType: string
  localIp: string | null
  gateway: string | null
  dnsServers: string[]
  ssid: string | null
  signalPercent: number | null
  channel: string | null
  networkId: string | null
  capabilityNote: string | null
}
export type ToolResult = { target: string; kind: string; output: string; status: string; limitation: string | null; startedAtMs: number; durationMs: number }
export type GamerResult = {
  capturedAtMs: number
  status: 'COMPLETE' | 'PARTIAL' | 'INCONCLUSIVE'
  destination: string
  method: string
  samplesMs: Array<number | null>
  medianMs: number | null
  jitterMs: number | null
  lossPercent: number | null
  maxMs: number | null
  p95Ms: number | null
  limitation: string
}
export type MonitorSample = { latencyMs: number | null; status: string; httpStatus: number | null; errorCode: string | null; bytesReceived: number | null }
export type MonitorResult = {
  id: string
  capturedAtMs: number
  source: string
  status: 'COMPLETE' | 'PARTIAL' | 'INCONCLUSIVE' | 'CONTAMINATED'
  method: string
  endpoint: string
  samples: MonitorSample[]
  latencyMs: number | null
  dnsMs: number | null
  dnsStatus: string
  downloadMbps: null
  uploadMbps: null
  limitation: string
}
export type MonitorStatus = { enabled: boolean; intervalMinutes: number; latest: MonitorResult | null }
export type ExportResult = { saved: boolean; rows: number; path: string | null }

function noDesktop<T>(): Promise<T> {
  return Promise.reject(new Error('Abra o SignallQ pelo aplicativo desktop para usar recursos nativos.'))
}
function desktopInvoke<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  return window.__TAURI_INTERNALS__ ? invoke<T>(command, args) : noDesktop<T>()
}
function desktopListen<T>(event: string, handler: (payload: T) => void): Promise<UnlistenFn> {
  return window.__TAURI_INTERNALS__ ? listen<T>(event, value => handler(value.payload)) : noDesktop<UnlistenFn>()
}

export const native = {
  startSpeedtest: (executionId: string) => desktopInvoke<{ executionId: string }>('start_speedtest', { executionId }),
  cancelSpeedtest: (executionId: string) => desktopInvoke<boolean>('cancel_speedtest', { executionId }),
  onSpeedtestProgress: (handler: (payload: SpeedtestProgress) => void) => desktopListen('speedtest:progress', handler),
  onSpeedtestFinished: (handler: (payload: Measurement) => void) => desktopListen('speedtest:finished', handler),
  onSpeedtestFailed: (handler: (payload: SpeedtestFailure) => void) => desktopListen('speedtest:failed', handler),
  history: () => desktopInvoke<Measurement[]>('list_history'),
  network: () => desktopInvoke<NetworkSnapshot>('network_snapshot'),
  gamerProbe: () => desktopInvoke<GamerResult>('gamer_probe'),
  monitorStatus: () => desktopInvoke<MonitorStatus>('monitor_status'),
  monitorStart: () => desktopInvoke<MonitorStatus>('monitor_start'),
  monitorStop: () => desktopInvoke<MonitorStatus>('monitor_stop'),
  monitorHistory: () => desktopInvoke<MonitorResult[]>('monitor_history'),
  onMonitorSample: (handler: (payload: MonitorResult) => void) => desktopListen('monitor:sample', handler),
  onMonitorError: (handler: (payload: string) => void) => desktopListen('monitor:error', handler),
  exportHistoryCsv: (includeNetworkFields: boolean) => desktopInvoke<ExportResult>('export_history_csv', { includeNetworkFields }),
  deleteHistoryRecord: (recordType: 'speedtest' | 'monitor', id: string) => desktopInvoke<boolean>('delete_history_record', { recordType, id }),
  deleteAllHistory: () => desktopInvoke<void>('delete_all_history'),
  tool: (kind: 'ping' | 'dns' | 'route', target: string) => desktopInvoke<ToolResult>('run_tool', { kind, target }),
  cancelTool: () => desktopInvoke<boolean>('cancel_tool'),
}
