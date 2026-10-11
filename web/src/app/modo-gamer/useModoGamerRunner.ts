import { useCallback, useEffect, useRef, useState } from 'react'
import { SPEEDTEST_DOWNLOAD_URL, SPEEDTEST_META_URL, SPEEDTEST_UPLOAD_URL } from '@/lib/config'
import {
  calculateJitterMAD,
  evaluateGameConnection,
  type EvaluatedMetrics,
  type GameGenre,
  median,
} from './modoGamerEngine'
export type RunnerPhase = 'idle' | 'locating' | 'measuring_ping' | 'measuring_upload' | 'done' | 'error'

export interface EdgeInfo {
  isp?: string
  city?: string
  country?: string
  colo?: string
}

export interface RunnerState {
  phase: RunnerPhase
  progressPct: number
  liveValue: number
  liveUnit: string
  edgeInfo: EdgeInfo | null
  idlePing: number | null
  jitter: number | null
  uploadMbps: number | null
  bufferbloat: number | null
  result: EvaluatedMetrics | null
  errorMsg: string | null
}

async function pingOnce(signal: AbortSignal, timeoutMs = 2000): Promise<number | null> {
  if (signal.aborted) return null
  const start = performance.now()
  const abortCtrl = new AbortController()
  const timer = setTimeout(() => abortCtrl.abort(), timeoutMs)

  const onParentAbort = () => abortCtrl.abort()
  signal.addEventListener('abort', onParentAbort)

  try {
    const separator = SPEEDTEST_DOWNLOAD_URL.includes('?') ? '&' : '?'
    await fetch(`${SPEEDTEST_DOWNLOAD_URL}${separator}bytes=0&r=${Math.random()}`, {
      cache: 'no-store',
      signal: abortCtrl.signal,
    })
    clearTimeout(timer)
    signal.removeEventListener('abort', onParentAbort)
    return performance.now() - start
  } catch {
    clearTimeout(timer)
    signal.removeEventListener('abort', onParentAbort)
    return null
  }
}

export function useModoGamerRunner() {
  const [state, setState] = useState<RunnerState>({
    phase: 'idle',
    progressPct: 0,
    liveValue: 0,
    liveUnit: 'ms',
    edgeInfo: null,
    idlePing: null,
    jitter: null,
    uploadMbps: null,
    bufferbloat: null,
    result: null,
    errorMsg: null,
  })

  const runIdRef = useRef(0)
  const isMountedRef = useRef(true)
  const abortCtrlRef = useRef<AbortController | null>(null)

  const cancelTest = useCallback(() => {
    runIdRef.current++
    if (abortCtrlRef.current) {
      abortCtrlRef.current.abort()
      abortCtrlRef.current = null
    }
    setState({
      phase: 'idle',
      progressPct: 0,
      liveValue: 0,
      liveUnit: 'ms',
      edgeInfo: null,
      idlePing: null,
      jitter: null,
      uploadMbps: null,
      bufferbloat: null,
      result: null,
      errorMsg: null,
    })
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
      if (abortCtrlRef.current) {
        abortCtrlRef.current.abort()
        abortCtrlRef.current = null
      }
    }
  }, [])

  const startTest = useCallback(
    async (gameName: string, genre: GameGenre) => {
      const currentRunId = ++runIdRef.current
      const isCurrent = () => isMountedRef.current && runIdRef.current === currentRunId

      if (abortCtrlRef.current) {
        abortCtrlRef.current.abort()
      }
      const rootAbort = new AbortController()
      abortCtrlRef.current = rootAbort

      setState({
        phase: 'locating',
        progressPct: 5,
        liveValue: 0,
        liveUnit: 'ms',
        edgeInfo: null,
        idlePing: null,
        jitter: null,
        uploadMbps: null,
        bufferbloat: null,
        result: null,
        errorMsg: null,
      })

      try {
        // ── 1. Localização da Borda / ISP ─────────────────────────────────
        let edge: EdgeInfo | null = null
        try {
          const metaRes = await fetch(SPEEDTEST_META_URL, {
            cache: 'no-store',
            signal: rootAbort.signal,
          })
          if (metaRes.ok) {
            const data = await metaRes.json()
            edge = {
              isp: data.asOrganization || data.asn,
              city: data.city,
              country: data.country,
              colo: data.colo,
            }
          }
        } catch {
          // Borda não-bloqueante
        }

        if (!isCurrent()) return
        setState((s) => ({
          ...s,
          phase: 'measuring_ping',
          progressPct: 15,
          edgeInfo: edge,
        }))

        // ── 2. Medição de Latência Ociosa e Jitter (MAD) ───────────────────
        // Descarta as duas primeiras amostras (aquecimento DNS/TLS)
        const TOTAL_PINGS = 12
        const rawSamples: number[] = []

        for (let i = 0; i < TOTAL_PINGS; i++) {
          if (!isCurrent()) return
          const ms = await pingOnce(rootAbort.signal)
          if (!isCurrent()) return
          if (ms != null) {
            rawSamples.push(ms)
            const usable = rawSamples.slice(2)
            const currentLive = Math.round(ms)
            const currentProgress = 15 + Math.round(((i + 1) / TOTAL_PINGS) * 35)

            let curJit = 0
            let curMed = currentLive
            if (usable.length > 0) {
              curMed = Math.round(median(usable))
              curJit = calculateJitterMAD(usable, curMed)
            }

            setState((s) => ({
              ...s,
              progressPct: currentProgress,
              liveValue: currentLive,
              liveUnit: 'ms',
              idlePing: curMed,
              jitter: curJit,
            }))
          }
          await new Promise((r) => setTimeout(r, 60))
        }

        const usablePings = rawSamples.slice(2)
        if (usablePings.length < 3) {
          throw new Error('Não foi possível obter amostras de latência suficientes.')
        }

        const finalIdlePing = Math.round(median(usablePings))
        const finalJitter = calculateJitterMAD(usablePings, finalIdlePing)

        if (!isCurrent()) return
        setState((s) => ({
          ...s,
          phase: 'measuring_upload',
          progressPct: 55,
          liveValue: 0,
          liveUnit: 'Mbps',
          idlePing: finalIdlePing,
          jitter: finalJitter,
        }))

        // ── 3. Medição de Upload e Bufferbloat (Latência sob carga) ────────
        const nav = typeof navigator !== 'undefined' ? (navigator as unknown as { connection?: { saveData?: boolean } }) : null
        const isSaveData = nav?.connection?.saveData === true
        const uploadBudgetMs = isSaveData ? 1500 : 3500
        const maxChunkBytes = isSaveData ? 128 * 1024 : 512 * 1024

        let bytesDelivered = 0
        const loadedPings: number[] = []
        let uploadDone = false

        const uploadStartTime = performance.now()

        // Loop de ping sob carga
        const loadProbeWorker = async () => {
          while (!uploadDone && isCurrent()) {
            const sample = await pingOnce(rootAbort.signal, 1800)
            if (uploadDone || !isCurrent()) break
            if (sample != null) {
              loadedPings.push(sample)
              const curBloat = Math.max(0, Math.round(median(loadedPings) - finalIdlePing))
              setState((s) => ({
                ...s,
                bufferbloat: curBloat,
              }))
            }
            await new Promise((r) => setTimeout(r, 70))
          }
        }

        const probePromise = loadProbeWorker()

        // Multi-stream upload poster
        const postChunk = async (chunkSize: number) => {
          const bodyData = new Uint8Array(chunkSize)
          const resp = await fetch(SPEEDTEST_UPLOAD_URL, {
            method: 'POST',
            body: bodyData,
            cache: 'no-store',
            signal: rootAbort.signal,
          })
          if (resp.ok) {
            bytesDelivered += chunkSize
          }
        }

        const streamCount = isSaveData ? 1 : 2
        const streamWorker = async () => {
          let chunk = 64 * 1024
          while (!uploadDone && isCurrent()) {
            const elapsed = performance.now() - uploadStartTime
            if (elapsed >= uploadBudgetMs) break

            try {
              await postChunk(chunk)
              if (chunk < maxChunkBytes) chunk = Math.min(maxChunkBytes, chunk * 2)

              const timeSecs = (performance.now() - uploadStartTime) / 1000
              const currentMbps = timeSecs > 0 ? (bytesDelivered * 8) / 1e6 / timeSecs : 0
              const upProgress = 55 + Math.min(35, Math.round((elapsed / uploadBudgetMs) * 35))

              setState((s) => ({
                ...s,
                progressPct: upProgress,
                liveValue: Math.round(currentMbps * 10) / 10,
                liveUnit: 'Mbps',
                uploadMbps: Math.round(currentMbps * 10) / 10,
              }))
            } catch {
              break
            }
          }
        }

        await Promise.all(Array.from({ length: streamCount }, () => streamWorker()))
        uploadDone = true
        await probePromise

        if (!isCurrent()) return

        const totalUploadSecs = (performance.now() - uploadStartTime) / 1000
        const finalUploadMbps =
          totalUploadSecs > 0 && bytesDelivered > 0
            ? Math.round(((bytesDelivered * 8) / 1e6 / totalUploadSecs) * 10) / 10
            : null

        // Bufferbloat só é calculado se houve saturação real da linha (bytesDelivered > 0)
        // e se obtivemos sondas válidas sob carga. Se não houve upload ou todas falharam, permanece null.
        let finalBufferbloat: number | null = null
        if (bytesDelivered > 0 && loadedPings.length > 0) {
          const rawMedian = median(loadedPings)
          finalBufferbloat = Math.max(0, Math.round(rawMedian - finalIdlePing))
        }

        // ── 4. Avaliação pelo Motor (Elo Mais Fraco & Culpado) ──────────────
        const evaluation = evaluateGameConnection({
          ping: finalIdlePing,
          jit: finalJitter,
          bloat: finalBufferbloat,
          upload: finalUploadMbps,
          lossPct: null,
          gameName,
          genre,
        })

        setState((s) => ({
          ...s,
          phase: 'done',
          progressPct: 100,
          idlePing: finalIdlePing,
          jitter: finalJitter,
          uploadMbps: finalUploadMbps,
          bufferbloat: finalBufferbloat,
          result: evaluation,
        }))
      } catch (err: unknown) {
        if (!isCurrent()) return
        const msg = err instanceof Error ? err.message : 'Falha ao medir conexão.'
        setState((s) => ({
          ...s,
          phase: 'error',
          errorMsg: msg,
        }))
      }
    },
    [],
  )

  return {
    state,
    startTest,
    cancelTest,
  }
}
