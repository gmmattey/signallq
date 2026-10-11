"use client";

import { useState, useRef, useCallback } from 'react'

export interface PingTestState {
  phase: 'idle' | 'running' | 'done' | 'error'
  stage: 'ping' | 'bufferbloat' | ''
  live: number
  pct: number
  packetIndex: number
  totalPackets: number
  pingMin: number | null
  pingAvg: number | null
  jitter: number | null
  bufferbloat: number | null
}

const INITIAL_STATE: PingTestState = {
  phase: 'idle',
  stage: '',
  live: 0,
  pct: 0,
  packetIndex: 0,
  totalPackets: 12,
  pingMin: null,
  pingAvg: null,
  jitter: null,
  bufferbloat: null,
}

const SPEEDTEST_BASE = 'https://speed.cloudflare.com'

function median(values: number[]): number {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 !== 0 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

export function usePingTestEngine() {
  const [state, setState] = useState<PingTestState>(INITIAL_STATE)
  const runId = useRef(0)

  const start = useCallback(async () => {
    const id = ++runId.current
    const current = () => runId.current === id

    const TOTAL_PACKETS = 12
    setState({
      phase: 'running',
      stage: 'ping',
      live: 0,
      pct: 5,
      packetIndex: 0,
      totalPackets: TOTAL_PACKETS,
      pingMin: null,
      pingAvg: null,
      jitter: null,
      bufferbloat: null,
    })

    try {
      // 1. Coleta cadenciada de Ping e Jitter em repouso
      const samples: number[] = []

      for (let i = 0; i < TOTAL_PACKETS; i++) {
        if (!current()) return

        const started = performance.now()
        await fetch(`${SPEEDTEST_BASE}/__down?bytes=0&r=${Math.random()}`, {
          cache: 'no-store',
        })
        const elapsed = Math.round(performance.now() - started)

        if (!current()) return
        samples.push(elapsed)

        const currentMin = Math.min(...samples)
        const currentAvg = Math.round(samples.reduce((a, b) => a + b, 0) / samples.length)

        let currentJitter = 0
        if (samples.length > 1) {
          let sumDiff = 0
          for (let j = 1; j < samples.length; j++) {
            sumDiff += Math.abs(samples[j] - samples[j - 1])
          }
          currentJitter = Math.round(sumDiff / (samples.length - 1))
        }

        const progressPct = 5 + Math.round(((i + 1) / TOTAL_PACKETS) * 60)

        setState((s) => ({
          ...s,
          live: elapsed,
          packetIndex: i + 1,
          pingMin: currentMin,
          pingAvg: currentAvg,
          jitter: currentJitter,
          pct: progressPct,
        }))

        // Pausa entre pacotes para capturar variabilidade real
        await new Promise((r) => setTimeout(r, 100))
      }

      if (!current()) return
      const finalMin = Math.min(...samples)
      const finalAvg = Math.round(samples.reduce((a, b) => a + b, 0) / samples.length)

      let jitterSum = 0
      for (let j = 1; j < samples.length; j++) {
        jitterSum += Math.abs(samples[j] - samples[j - 1])
      }
      const finalJitter = samples.length > 1 ? Math.round(jitterSum / (samples.length - 1)) : 0

      // 2. Medição de Bufferbloat (Latência sob carga)
      setState((s) => ({
        ...s,
        stage: 'bufferbloat',
        pct: 70,
        pingMin: finalMin,
        pingAvg: finalAvg,
        jitter: finalJitter,
      }))

      const loadedPings: number[] = []
      const controller = new AbortController()

      // Inicia stream de saturação leve (4MB) em background
      const saturationPromise = fetch(`${SPEEDTEST_BASE}/__down?bytes=4000000&r=${Math.random()}`, {
        cache: 'no-store',
        signal: controller.signal,
      }).catch(() => null)

      // Aguarda 400ms para saturação inicial
      await new Promise((r) => setTimeout(r, 400))

      // Dispara 3 sondas sob saturação
      for (let p = 0; p < 3; p++) {
        if (!current()) {
          controller.abort()
          return
        }
        const pStart = performance.now()
        await fetch(`${SPEEDTEST_BASE}/__down?bytes=0&r=${Math.random()}`, {
          cache: 'no-store',
        }).catch(() => null)
        const pElapsed = Math.round(performance.now() - pStart)
        if (pElapsed > 0) {
          loadedPings.push(pElapsed)
          const currentBbloat = Math.max(0, Math.round(median(loadedPings) - finalMin))
          setState((s) => ({
            ...s,
            live: pElapsed,
            bufferbloat: currentBbloat,
            pct: 70 + (p + 1) * 9,
          }))
        }
        await new Promise((r) => setTimeout(r, 120))
      }

      controller.abort()
      await saturationPromise

      if (!current()) return

      const finalBufferbloat =
        loadedPings.length > 0 ? Math.max(0, Math.round(median(loadedPings) - finalMin)) : 0

      setState((s) => ({
        ...s,
        phase: 'done',
        stage: '',
        pct: 100,
        live: finalAvg,
        pingMin: finalMin,
        pingAvg: finalAvg,
        jitter: finalJitter,
        bufferbloat: finalBufferbloat,
      }))
    } catch {
      if (current()) {
        setState((s) => ({ ...s, phase: 'error' }))
      }
    }
  }, [])

  const cancel = useCallback(() => {
    runId.current++
    setState(INITIAL_STATE)
  }, [])

  return { state, start, cancel }
}
