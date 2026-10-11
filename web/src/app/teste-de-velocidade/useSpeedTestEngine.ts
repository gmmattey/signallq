"use client";

import { useCallback, useRef, useState } from 'react'
import { SPEEDTEST_DOWNLOAD_URL, SPEEDTEST_UPLOAD_URL } from '@/lib/config'
import {
   FEATURE_TESTE_VELOCIDADE_CONCLUIDO,
   FEATURE_TESTE_VELOCIDADE_ERRO,
   trackFeatureUsed,
 } from '@/lib/telemetry'

export type SpeedTestPhase = 'idle' | 'running' | 'done' | 'error'
export type SpeedTestStage = 'ping' | 'down' | 'up' | ''

export interface SpeedTestState {
  phase: SpeedTestPhase
  stage: SpeedTestStage
  live: number
  pct: number
  ping: number | null
  jitter: number | null
  download: number | null
  upload: number | null
  bufferbloat: number | null
}

const INITIAL_STATE: SpeedTestState = {
  phase: 'idle',
  stage: '',
  live: 0,
  pct: 0,
  ping: null,
  jitter: null,
  download: null,
  upload: null,
  bufferbloat: null,
}

function median(values: number[]): number {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const mid = Math.floor(sorted.length / 2)
  return sorted.length % 2 !== 0 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

// Máquina de estados da medição com coleta real de Ping, Jitter, Download, Upload e Bufferbloat
export function useSpeedTestEngine() {
  const [state, setState] = useState<SpeedTestState>(INITIAL_STATE)
  const runId = useRef(0)

  const start = useCallback(async () => {
    const id = ++runId.current
    const current = () => runId.current === id
    setState({
      phase: 'running',
      stage: 'ping',
      live: 0,
      pct: 3,
      ping: null,
      jitter: null,
      download: null,
      upload: null,
      bufferbloat: null,
    })

    try {
      // 1. Fase de Latência em Repouso (Ping + Jitter) — 10 amostras reais
      const pingSamples: number[] = []
      for (let i = 0; i < 10; i++) {
        const started = performance.now()
        await fetch(`${SPEEDTEST_DOWNLOAD_URL}?bytes=0&r=${Math.random()}`, { cache: 'no-store' })
        const elapsed = performance.now() - started
        pingSamples.push(elapsed)
        if (!current()) return

        const currentBest = Math.round(Math.min(...pingSamples))
        setState((s) => ({ ...s, live: currentBest, pct: 3 + i * 1.2 }))
        await new Promise((r) => setTimeout(r, 100))
        if (!current()) return
      }

      const ping = Math.round(Math.min(...pingSamples))
      let jitterSum = 0
      for (let i = 1; i < pingSamples.length; i++) {
        jitterSum += Math.abs(pingSamples[i] - pingSamples[i - 1])
      }
      const jitter = pingSamples.length > 1 ? Math.round(jitterSum / (pingSamples.length - 1)) : 0

      setState((s) => ({ ...s, ping, jitter, stage: 'down', live: 0, pct: 15 }))

      // Amostras de latência sob carga (para cálculo real de Bufferbloat)
      const loadedPings: number[] = []
      let lastLoadedProbeTime = 0

      const probeLoadedLatency = async () => {
        try {
          const probeStart = performance.now()
          await fetch(`${SPEEDTEST_DOWNLOAD_URL}?bytes=0&r=${Math.random()}`, { cache: 'no-store' })
          const probeElapsed = performance.now() - probeStart
          if (probeElapsed > 0) {
            loadedPings.push(probeElapsed)
            const currentMed = median(loadedPings)
            const currentBbloat = Math.max(0, Math.round(currentMed - ping))
            if (current()) {
              setState((s) => ({ ...s, bufferbloat: currentBbloat }))
            }
          }
        } catch {
          // ignora falha isolada de probe sob saturação
        }
      }

      // 2. Fase de Download — saturação contínua e probes de latência sob carga
      const dlTargetDurationSec = 8
      const dlStartTime = performance.now()
      let totalDlBytes = 0
      let warmUpBytes = 0
      let warmUpTime = 0
      let hasWarmedUp = false
      let liveDlSmoothed = 0

      const dlChunkSizes = [5e6, 10e6, 25e6, 25e6]
      let dlChunkIdx = 0

      while ((performance.now() - dlStartTime) / 1000 < dlTargetDurationSec && current()) {
        const size = dlChunkSizes[Math.min(dlChunkIdx++, dlChunkSizes.length - 1)]
        const res = await fetch(`${SPEEDTEST_DOWNLOAD_URL}?bytes=${size}&r=${Math.random()}`, { cache: 'no-store' })
        const reader = res.body?.getReader()
        if (!reader) break

        let streamFinished = false
        while (!streamFinished && current()) {
          const { done, value } = await reader.read()
          if (done) {
            streamFinished = true
            break
          }
          const chunkLen = value.length
          totalDlBytes += chunkLen

          const now = performance.now()
          const totalElapsedSec = (now - dlStartTime) / 1000

          // Descarte de warm-up (primeiros 1.5s de TCP slow-start)
          if (!hasWarmedUp && totalElapsedSec >= 1.5) {
            hasWarmedUp = true
            warmUpBytes = totalDlBytes
            warmUpTime = totalElapsedSec
          }

          // Dispara probe de latência sob carga (bufferbloat) a cada 1.5s após o warmup
          if (hasWarmedUp && now - lastLoadedProbeTime >= 1500) {
            lastLoadedProbeTime = now
            probeLoadedLatency()
          }

          let currentRateMbps = 0
          if (hasWarmedUp && totalElapsedSec > warmUpTime) {
            const measuredBytes = totalDlBytes - warmUpBytes
            const measuredSec = totalElapsedSec - warmUpTime
            currentRateMbps = (measuredBytes * 8) / measuredSec / 1e6
          } else {
            currentRateMbps = (totalDlBytes * 8) / Math.max(0.1, totalElapsedSec) / 1e6
          }

          liveDlSmoothed = liveDlSmoothed === 0 ? currentRateMbps : liveDlSmoothed * 0.7 + currentRateMbps * 0.3
          const progress = 15 + Math.min(45, (totalElapsedSec / dlTargetDurationSec) * 45)
          setState((s) => ({ ...s, live: liveDlSmoothed, pct: progress }))

          if (totalElapsedSec >= dlTargetDurationSec) {
            reader.cancel()
            break
          }
        }
      }
      if (!current()) return

      const finalDlTotalSec = (performance.now() - dlStartTime) / 1000
      let download = liveDlSmoothed
      if (hasWarmedUp && finalDlTotalSec > warmUpTime) {
        download = ((totalDlBytes - warmUpBytes) * 8) / (finalDlTotalSec - warmUpTime) / 1e6
      }
      download = Math.max(0.1, download)
      setState((s) => ({ ...s, download, stage: 'up', live: 0, pct: 60 }))

      // 3. Fase de Upload — envio contínuo com probes de bufferbloat
      const upTargetDurationSec = 6.5
      const upStartTime = performance.now()
      let totalUpBytes = 0
      let upWarmUpBytes = 0
      let upWarmUpTime = 0
      let upHasWarmedUp = false
      let liveUpSmoothed = 0

      const uploadBuffer = new Uint8Array(2e6)
      for (let b = 0; b < uploadBuffer.length; b += 1024) {
        uploadBuffer[b] = (b + Math.floor(Math.random() * 255)) & 0xff
      }

      while ((performance.now() - upStartTime) / 1000 < upTargetDurationSec && current()) {
        const postStart = performance.now()
        await fetch(SPEEDTEST_UPLOAD_URL, {
          method: 'POST',
          body: uploadBuffer,
          cache: 'no-store',
        })
        const postEnd = performance.now()
        if (!current()) return

        totalUpBytes += uploadBuffer.length
        const totalElapsedSec = (postEnd - upStartTime) / 1000

        if (!upHasWarmedUp && totalElapsedSec >= 1.2) {
          upHasWarmedUp = true
          upWarmUpBytes = totalUpBytes
          upWarmUpTime = totalElapsedSec
        }

        if (upHasWarmedUp && postEnd - lastLoadedProbeTime >= 1500) {
          lastLoadedProbeTime = postEnd
          probeLoadedLatency()
        }

        let currentRateMbps = 0
        if (upHasWarmedUp && totalElapsedSec > upWarmUpTime) {
          const measuredBytes = totalUpBytes - upWarmUpBytes
          const measuredSec = totalElapsedSec - upWarmUpTime
          currentRateMbps = (measuredBytes * 8) / measuredSec / 1e6
        } else {
          const blockElapsedSec = Math.max(0.01, (postEnd - postStart) / 1000)
          currentRateMbps = (uploadBuffer.length * 8) / blockElapsedSec / 1e6
        }

        liveUpSmoothed = liveUpSmoothed === 0 ? currentRateMbps : liveUpSmoothed * 0.7 + currentRateMbps * 0.3
        const progress = 60 + Math.min(38, (totalElapsedSec / upTargetDurationSec) * 38)
        setState((s) => ({ ...s, live: liveUpSmoothed, pct: progress }))
      }
      if (!current()) return

      const finalUpTotalSec = (performance.now() - upStartTime) / 1000
      let upload = liveUpSmoothed
      if (upHasWarmedUp && finalUpTotalSec > upWarmUpTime) {
        upload = ((totalUpBytes - upWarmUpBytes) * 8) / (finalUpTotalSec - upWarmUpTime) / 1e6
      }
      upload = Math.max(0.1, upload)

      // Cálculo final consolidado de Bufferbloat (Delta ms em relação ao Ping em repouso)
      const finalBufferbloat =
        loadedPings.length > 0
          ? Math.max(0, Math.round(median(loadedPings) - ping))
          : 0

      if (current()) {
        trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_CONCLUIDO)
        setState((s) => ({
          ...s,
          upload,
          bufferbloat: finalBufferbloat,
          phase: 'done',
          pct: 100,
        }))
      }
    } catch {
      if (current()) {
        trackFeatureUsed(FEATURE_TESTE_VELOCIDADE_ERRO)
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
