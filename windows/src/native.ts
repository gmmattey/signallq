import { invoke } from '@tauri-apps/api/core'

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown
  }
}

export type Measurement = {
  executionId: string
  capturedAtMs: number
  status: 'COMPLETED' | 'PARTIAL'
  downloadMbps: number | null
  uploadMbps: number | null
  latencyMs: number | null
  jitterMs: number | null
  packetLossPercent: number | null
  packetLossSource: string
  bufferbloatMs: number | null
  limitation: string | null
}

export type NetworkSnapshot = {
  interfaceName: string | null
  connectionType: string
  localIp: string | null
  gateway: string | null
  dnsServers: string[]
  ssid: string | null
  signalPercent: number | null
  channel: string | null
  capabilityNote: string | null
}

export type ToolResult = { target: string; kind: string; output: string; status: string; limitation: string | null }

function noDesktop<T>(): Promise<T> {
  return Promise.reject(new Error('Abra o SignallQ pelo aplicativo desktop para usar recursos nativos.'))
}

export const native = {
  speedtest: () => window.__TAURI_INTERNALS__ ? invoke<Measurement>('run_speedtest') : noDesktop<Measurement>(),
  history: () => window.__TAURI_INTERNALS__ ? invoke<Measurement[]>('list_history') : noDesktop<Measurement[]>(),
  network: () => window.__TAURI_INTERNALS__ ? invoke<NetworkSnapshot>('network_snapshot') : noDesktop<NetworkSnapshot>(),
  tool: (kind: 'ping' | 'dns' | 'route', target: string) => window.__TAURI_INTERNALS__
    ? invoke<ToolResult>('run_tool', { kind, target }) : noDesktop<ToolResult>(),
}
