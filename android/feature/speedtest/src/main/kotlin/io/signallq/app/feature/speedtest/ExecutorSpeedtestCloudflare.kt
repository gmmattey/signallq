package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.EvidenciaPerdaPacotes
import io.signallq.app.core.diagnostico.MetricStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * @param latencyProbeUrl Endpoint usado para medir a fase de latência BASE (a que vira
 * `latenciaMs`/jitter/perda do resultado). GH#1118: até esta correção, a latência base
 * era medida contra [HOST_PUBLICO_LATENCIA] (CDN pública, sujeita a throttling/anti-abuso
 * em rajada de requisições idênticas — evidência real: 408ms medidos vs. 11ms no Ookla e
 * no `game-latency-probe-worker` dedicado, no mesmo device/rede). O default preserva o
 * host público (comportamento antigo) para quem não injeta explicitamente a URL do worker;
 * o `:app` passa `BuildConfig.GAME_LATENCY_PROBE_URL` (mesmo worker que a tela Jogos usa,
 * GH#935) via [FeatureSpeedtestModulo]. A latência SOB CARGA (medida durante download/
 * upload, usada no cálculo de bufferbloat) continua contra o host público de propósito —
 * ela precisa competir por banda no mesmo pool HTTP usado pela transferência real.
 */
class ExecutorSpeedtestCloudflare(
    isMobile: Boolean = false,
    private val latencyProbeUrl: String = HOST_PUBLICO_LATENCIA,
) : ExecutorSpeedtest {
    private companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 14; SM-A256E) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        // Host público usado para: (a) fallback quando o worker dedicado de latência não
        // responde, (b) latência SOB CARGA durante download/upload (medida de propósito
        // contra o mesmo host da transferência), (c) default de [latencyProbeUrl].
        const val HOST_PUBLICO_LATENCIA = "https://speed.cloudflare.com/__down?bytes=0"

        // Camillo/Luiz (.agents/architecture-plan.md, seção 7): +20 probes brutos quando
        // o baseline de latência já deu QUALQUER sinal de problema (deveConfirmarAmostragem).
        // Disparo único — sem loop, para não estourar o orçamento de tempo do teste.
        const val PROBES_JANELA_CONFIRMACAO = 20

        // Pool adaptativo: móvel usa menos conexões e keep-alive curto para poupar bateria/dados.
        // Wi-Fi/fixo usa pool maior para throughput máximo no speedtest.
        fun criarConnectionPool(isMobile: Boolean): okhttp3.ConnectionPool =
            if (isMobile) {
                okhttp3.ConnectionPool(2, 1, TimeUnit.MINUTES)
            } else {
                okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES)
            }
    }

    // GH#1221 RF-01: os 3 clients eram `val` fixados na construcao do singleton — o
    // perfil de pool (isMobile) ficava "congelado" no tipo de rede ativo quando o app
    // abriu, nunca no tipo de rede do teste em si. Agora sao `var` reconstruidos por
    // [garantirPerfilRede] sempre que o perfil resolvido no INICIO de [executar] diverge
    // do perfil usado para construir o pool atual.
    @Volatile private var perfilMovelAtual: Boolean = isMobile

    // Client HTTP/2 para upload e ping — múltiplos streams em uma conexão TCP.
    private var client: OkHttpClient = criarClientTransferencia(perfilMovelAtual)

    // Client HTTP/1.1 para download — cada worker usa conexão TCP própria,
    // com headers de contexto de browser para evitar rate-limit 429/403 do Cloudflare
    // no endpoint /__down (que bloqueia clientes HTTP/2 sem contexto de origem).
    // Pool adaptado ao tipo de rede: móvel=2 conexões, Wi-Fi=8 conexões.
    private var downloadClient: OkHttpClient = criarClientDownload(perfilMovelAtual)

    // Client separado para pings — timeout menor, reusa o mesmo pool H2.
    private var pingClient: OkHttpClient = criarClientPing(client)

    private fun criarClientTransferencia(isMobileAtual: Boolean): OkHttpClient =
        OkHttpClient
            .Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(criarConnectionPool(isMobileAtual))
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", UA)
                        .header("Cache-Control", "no-store")
                        .build(),
                )
            }.build()

    private fun criarClientDownload(isMobileAtual: Boolean): OkHttpClient =
        OkHttpClient
            .Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(criarConnectionPool(isMobileAtual))
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", UA)
                        .header("Accept", "*/*")
                        .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Cache-Control", "no-store")
                        .header("Origin", "https://speed.cloudflare.com")
                        .header("Referer", "https://speed.cloudflare.com/")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .build(),
                )
            }.build()

    private fun criarClientPing(base: OkHttpClient): OkHttpClient =
        base
            .newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()

    /**
     * GH#1221 RF-01 — resolve o perfil de pool (metered/movel vs Wi-Fi) no INICIO de cada
     * execucao, nunca so na construcao do singleton. So reconstroi os clients quando o
     * perfil realmente mudou desde a ultima execucao — evita descartar conexoes
     * keep-alive vivas em testes consecutivos na mesma rede. Chamado antes de qualquer
     * fase iniciar, com [emExecucao] ja travado (sem concorrencia possivel aqui).
     */
    private fun garantirPerfilRede(isMobileAtual: Boolean) {
        if (isMobileAtual == perfilMovelAtual) return
        Timber.i("perfilRede: mudou isMobile=$perfilMovelAtual -> $isMobileAtual, recriando pool HTTP")
        perfilMovelAtual = isMobileAtual
        client = criarClientTransferencia(isMobileAtual)
        downloadClient = criarClientDownload(isMobileAtual)
        pingClient = criarClientPing(client)
    }

    private val emExecucao = AtomicBoolean(false)
    private val cancelFlag = AtomicBoolean(false)
    private val bytesConsumidosTotal = AtomicLong(0L)

    @Volatile private var faseAtualInterna: FaseSpeedtest = FaseSpeedtest.idle

    @Volatile private var velocidadeAtualInterna: Double = 0.0
    private val uploadPayloadCache = ConcurrentHashMap<Int, ByteArray>()
    private val pontosAoVivoInternos = Collections.synchronizedList(mutableListOf<PontoAoVivo>())
    private val mutableSnapshotFlow =
        MutableStateFlow(
            SnapshotExecucaoSpeedtest(
                estado = EstadoExecucaoSpeedtest.idle,
                progressoPercentual = 0,
                resultado = null,
                erroMensagem = null,
            ),
        )

    override val snapshotFlow: StateFlow<SnapshotExecucaoSpeedtest> = mutableSnapshotFlow.asStateFlow()

    override fun cancelar() {
        cancelFlag.set(true)
        // #374: se não há teste em execução (ex.: já caiu em erro), sinalizar a flag não
        // muda o estado publicado — a tela de erro ficava presa. Nesse caso, resetar direto.
        if (!emExecucao.get() && mutableSnapshotFlow.value.estado == EstadoExecucaoSpeedtest.erro) {
            mutableSnapshotFlow.value =
                SnapshotExecucaoSpeedtest(
                    estado = EstadoExecucaoSpeedtest.idle,
                    progressoPercentual = 0,
                    resultado = mutableSnapshotFlow.value.resultado,
                    erroMensagem = null,
                )
        }
    }

    override suspend fun executar(
        modo: ModoSpeedtest,
        connectionType: String?,
        connectionTypeProvider: (() -> String?)?,
        tecnologiaProvider: (() -> String?)?,
        isMobileProvider: (() -> Boolean)?,
    ) {
        if (!emExecucao.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            try {
                // GH#1221 RF-01 — resolve o perfil de rede ATUAL antes de qualquer fase,
                // nunca reaproveita so o valor congelado na construcao do singleton.
                garantirPerfilRede(isMobileProvider?.invoke() ?: perfilMovelAtual)
                // GH#1221/#1225 RF-02 — identificador unico desta execucao, usado por
                // Resultado/Diagnostico/IA/Recomendacao/PDF para confirmar que pertencem
                // a mesma execucao (descartar respostas assincronas de execucoes antigas).
                val executionId =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                cancelFlag.set(false)
                bytesConsumidosTotal.set(0L)
                faseAtualInterna = FaseSpeedtest.idle
                velocidadeAtualInterna = 0.0

                val redeInicial = connectionType
                var faseInterrompida = "none"
                faseAtualInterna = FaseSpeedtest.ping
                pontosAoVivoInternos.clear()
                publicar(EstadoExecucaoSpeedtest.executando, 5, null, null)
                val config = SpeedtestConfig.fromModo(modo)
                val latencyPhase =
                    executarFaseLatencia(
                        config = config,
                        redeInicial = redeInicial,
                        connectionTypeProvider = connectionTypeProvider,
                        onPingProgress = { idx, total ->
                            publicar(EstadoExecucaoSpeedtest.executando, (idx.toDouble() / total * 28).toInt(), null, null)
                        },
                    )
                if (cancelFlag.get()) {
                    faseAtualInterna = FaseSpeedtest.idle
                    velocidadeAtualInterna = 0.0
                    publicar(EstadoExecucaoSpeedtest.idle, 0, null, null)
                    return@withContext
                }
                if (mudouRede(redeInicial, connectionTypeProvider)) {
                    faseInterrompida = "redeMudouAposLatencia"
                    val redeFinal = connectionTypeProvider?.invoke() ?: connectionType
                    val resultadoContaminado =
                        construirResultado(
                            modo = modo,
                            redeInicial = redeInicial,
                            redeFinal = redeFinal,
                            latencyPhase = latencyPhase,
                            downloadPhase = throughputVazio("naoExecutado"),
                            uploadPhase = throughputVazio("naoExecutado"),
                            pingDownload = emptyList(),
                            pingUpload = emptyList(),
                            dns = DnsProbeResult(null, null, null, null),
                            contaminado = true,
                            faseInterrompida = faseInterrompida,
                            tecnologia = tecnologiaProvider?.invoke(),
                            executionId = executionId,
                        )
                    registrarDiagnostico(resultadoContaminado)
                    faseAtualInterna = FaseSpeedtest.concluido
                    publicar(EstadoExecucaoSpeedtest.concluido, 100, resultadoContaminado, null)
                    return@withContext
                }
                faseAtualInterna = FaseSpeedtest.download
                pontosAoVivoInternos.clear()
                velocidadeAtualInterna = 0.0
                publicar(EstadoExecucaoSpeedtest.executando, 28, null, null)

                val pingDownload = Collections.synchronizedList(mutableListOf<Double>())
                val downloadPhase =
                    try {
                        executarFaseTransferencia(
                            isDownload = true,
                            config = config,
                            onFaseProgress = { local -> publicar(EstadoExecucaoSpeedtest.executando, 28 + (local * 44).toInt(), null, null) },
                            pingsSobCarga = pingDownload,
                            redeInicial = redeInicial,
                            connectionTypeProvider = connectionTypeProvider,
                        )
                    } catch (t: Throwable) {
                        val mensagem = t.message.orEmpty()
                        val ehRateLimit =
                            mensagem.startsWith("download_failed:IllegalStateException:HttpStatus:429") ||
                                mensagem.startsWith("download_failed:IllegalStateException:HttpStatus:403")
                        if (!ehRateLimit) {
                            throw t
                        }
                        val configFallback429 =
                            config.copy(
                                downloadPayloadBytes = 10_000_000,
                                downloadInitialStreams = 1,
                                downloadMaxStreams = 2,
                            )
                        Timber.w(
                            "fallback429 modo=${modo.name} downloadPayload=${configFallback429.downloadPayloadBytes} streams=${configFallback429.downloadInitialStreams}..${configFallback429.downloadMaxStreams}",
                        )
                        try {
                            executarFaseTransferencia(
                                isDownload = true,
                                config = configFallback429,
                                onFaseProgress = { local -> publicar(EstadoExecucaoSpeedtest.executando, 28 + (local * 44).toInt(), null, null) },
                                pingsSobCarga = pingDownload,
                                redeInicial = redeInicial,
                                connectionTypeProvider = connectionTypeProvider,
                            )
                        } catch (t2: Throwable) {
                            val msg2 = t2.message.orEmpty()
                            val ehRateLimit2 =
                                msg2.startsWith("download_failed:IllegalStateException:HttpStatus:429") ||
                                    msg2.startsWith("download_failed:IllegalStateException:HttpStatus:403")
                            if (!ehRateLimit2) throw t2
                            Timber.w("fallback429 também bloqueado, continuando sem download modo=${modo.name}")
                            throughputVazio("download_bloqueado_429")
                        }
                    }
                if (cancelFlag.get()) {
                    faseAtualInterna = FaseSpeedtest.idle
                    velocidadeAtualInterna = 0.0
                    publicar(EstadoExecucaoSpeedtest.idle, 0, null, null)
                    return@withContext
                }
                if (mudouRede(redeInicial, connectionTypeProvider)) {
                    faseInterrompida = "redeMudouAposDownload"
                    val redeFinal = connectionTypeProvider?.invoke() ?: connectionType
                    val resultadoContaminado =
                        construirResultado(
                            modo = modo,
                            redeInicial = redeInicial,
                            redeFinal = redeFinal,
                            latencyPhase = latencyPhase,
                            downloadPhase = downloadPhase,
                            uploadPhase = throughputVazio("naoExecutado"),
                            pingDownload = pingDownload,
                            pingUpload = emptyList(),
                            dns = DnsProbeResult(null, null, null, null),
                            contaminado = true,
                            faseInterrompida = faseInterrompida,
                            tecnologia = tecnologiaProvider?.invoke(),
                            executionId = executionId,
                        )
                    registrarDiagnostico(resultadoContaminado)
                    faseAtualInterna = FaseSpeedtest.concluido
                    publicar(EstadoExecucaoSpeedtest.concluido, 100, resultadoContaminado, null)
                    return@withContext
                }

                faseAtualInterna = FaseSpeedtest.upload
                pontosAoVivoInternos.clear()
                velocidadeAtualInterna = 0.0
                publicar(EstadoExecucaoSpeedtest.executando, 74, null, null)

                val pingUpload = Collections.synchronizedList(mutableListOf<Double>())
                val dnsProbe = async { executarDnsProbe() }
                var uploadPhase =
                    if (connectionType == "movel") {
                        executarFaseUploadAdaptativa(
                            config = config,
                            onFaseProgress = { local -> publicar(EstadoExecucaoSpeedtest.executando, 74 + (local * 24).toInt(), null, null) },
                            pingsSobCarga = pingUpload,
                            redeInicial = redeInicial,
                            connectionTypeProvider = connectionTypeProvider,
                        )
                    } else {
                        executarFaseTransferencia(
                            isDownload = false,
                            config = config,
                            onFaseProgress = { local -> publicar(EstadoExecucaoSpeedtest.executando, 74 + (local * 24).toInt(), null, null) },
                            pingsSobCarga = pingUpload,
                            redeInicial = redeInicial,
                            connectionTypeProvider = connectionTypeProvider,
                        )
                    }
                var uploadNaoDetectado = false
                if (uploadPhase.throughputMbps == 0.0 && !cancelFlag.get()) {
                    val backoffMs = listOf(1_000L, 2_000L, 4_000L)
                    for ((idx, backoff) in backoffMs.withIndex()) {
                        delay(backoff)
                        if (cancelFlag.get()) break
                        Timber.w("upload=0 retry #${idx + 1} apos ${backoff}ms")
                        val mbpsRetry = executarProbeUpload()
                        if (mbpsRetry > 0.0) {
                            uploadPhase =
                                uploadPhase.copy(
                                    throughputMbps = mbpsRetry,
                                    peakMbps = mbpsRetry,
                                    faseEncerradaPor = "retryBemSucedido",
                                    throughputOrigem = "retryProbe",
                                )
                            break
                        }
                        if (idx == backoffMs.lastIndex) uploadNaoDetectado = true
                    }
                }
                val dns = dnsProbe.await()
                val redeFinal = connectionTypeProvider?.invoke() ?: connectionType
                val contaminado = redeInicial != null && redeFinal != null && redeInicial != redeFinal

                val latencyPhaseValidada =
                    validarBaselineLatencia(
                        latencyPhase = latencyPhase,
                        pingDownload = pingDownload,
                        pingUpload = pingUpload,
                        config = config,
                        redeInicial = redeInicial,
                        connectionTypeProvider = connectionTypeProvider,
                    )

                val resultado =
                    construirResultado(
                        modo = modo,
                        redeInicial = redeInicial,
                        redeFinal = redeFinal,
                        latencyPhase = latencyPhaseValidada,
                        downloadPhase = downloadPhase,
                        uploadPhase = uploadPhase,
                        pingDownload = pingDownload,
                        pingUpload = pingUpload,
                        dns = dns,
                        contaminado = contaminado,
                        faseInterrompida = faseInterrompida,
                        uploadNaoDetectado = uploadNaoDetectado,
                        tecnologia = tecnologiaProvider?.invoke(),
                        executionId = executionId,
                    )

                registrarDiagnostico(resultado)
                faseAtualInterna = FaseSpeedtest.concluido
                publicar(EstadoExecucaoSpeedtest.concluido, 100, resultado, null)
            } catch (t: Throwable) {
                Timber.e(t, "modo=${modo.name} erro=${t.message ?: "desconhecido"}")
                faseAtualInterna = FaseSpeedtest.idle
                velocidadeAtualInterna = 0.0
                publicar(
                    EstadoExecucaoSpeedtest.erro,
                    100,
                    null,
                    erroMensagem = null,
                    causaFalha =
                        mapearCausaFalha(
                            erro = t,
                            possuiTransporte = possuiTransporte(connectionTypeProvider?.invoke() ?: connectionType),
                        ),
                )
            } finally {
                emExecucao.set(false)
            }
        }
    }

    private suspend fun executarFaseUploadAdaptativa(
        config: SpeedtestConfig,
        onFaseProgress: (Double) -> Unit,
        pingsSobCarga: MutableList<Double>,
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
    ): ThroughputPhase =
        supervisorScope {
            val inicioNs = System.nanoTime()
            val budgetMs = 25_000L
            val stopNs = inicioNs + (budgetMs * 1_000_000L)
            val amostras = mutableListOf<Sample>()
            val bytesTotal = AtomicLong(0)
            val requisicoesSucesso = AtomicInteger(0)
            val requisicoesErro = AtomicInteger(0)
            var chunkBytes = 64 * 1024
            var paralelo = 1
            var roundsLentos = 0
            var rodada = 0

            fun elapsedMs(): Long = (System.nanoTime() - inicioNs) / 1_000_000L

            // Intervalo aumentado para 1000ms (era 300ms) durante o throughput adaptativo:
            // pings frequentes competem por banda com os workers de upload no mesmo pool HTTP/2,
            // distorcendo ambas as medições. Amostras a cada 1s ainda são suficientes para
            // calcular bufferbloat (latência sob carga vs. latência em repouso).
            val pingJob =
                launch {
                    while (System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                        val t0 = System.nanoTime()
                        val rtt = medirPing()
                        if (rtt != null) pingsSobCarga.add(rtt)
                        val elapsed = (System.nanoTime() - t0) / 1_000_000L
                        delay(max(0L, 1_000L - elapsed))
                    }
                }

            while (rodada < 4 && System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                rodada++
                val tRoundNs = System.nanoTime()
                val jobs = mutableListOf<Job>()
                val bytesRodada = AtomicLong(0)

                repeat(paralelo) {
                    jobs +=
                        launch {
                            try {
                                val sent = executarRequestUpload(chunkBytes)
                                bytesRodada.addAndGet(sent.toLong())
                                bytesTotal.addAndGet(sent.toLong())
                                bytesConsumidosTotal.addAndGet(sent.toLong())
                                requisicoesSucesso.incrementAndGet()
                            } catch (_: Throwable) {
                                requisicoesErro.incrementAndGet()
                            }
                        }
                }
                jobs.forEach { it.join() }
                val tRoundMs = ((System.nanoTime() - tRoundNs) / 1_000_000L).coerceAtLeast(1L)
                val mbps = (bytesRodada.get() * 8.0) / (tRoundMs.toDouble() / 1000.0) / 1_000_000.0
                amostras.add(Sample(elapsedMs().toInt(), mbps))
                velocidadeAtualInterna = mbps
                onFaseProgress(min(1.0, elapsedMs().toDouble() / config.uploadDurationMs.toDouble()))

                val roundRapido = tRoundMs < 2_000L
                val podeEscalar = paralelo < 4 && chunkBytes < (2 * 1024 * 1024)
                if (roundRapido && podeEscalar) {
                    paralelo = min(4, paralelo + 1)
                    chunkBytes = min(2 * 1024 * 1024, chunkBytes * 4)
                    roundsLentos = 0
                } else {
                    roundsLentos++
                    if (roundsLentos >= 2) break
                }
            }

            pingJob.join()
            val validas = amostras.filter { it.mbps > 0.0 }
            val throughput = if (validas.isEmpty()) 0.0 else validas.map { it.mbps }.average()
            val pico = validas.maxOfOrNull { it.mbps } ?: 0.0
            val encerradaPor =
                when {
                    mudouRede(redeInicial, connectionTypeProvider) -> "redeMudou"
                    rodada >= 4 -> "rodadasMax"
                    roundsLentos >= 2 -> "estagnou"
                    else -> "tempoEsgotado"
                }
            ThroughputPhase(
                throughputMbps = throughput,
                peakMbps = pico,
                amostrasInstantaneas = validas.map { it.mbps },
                bytesTotal = bytesTotal.get(),
                requisicoesSucesso = requisicoesSucesso.get(),
                requisicoesErro = requisicoesErro.get(),
                faseEncerradaPor = encerradaPor,
                throughputOrigem = if (validas.isEmpty()) "semDados" else "validasSemCorte",
                ultimoErro = null,
            )
        }

    internal suspend fun executarFaseLatencia(
        config: SpeedtestConfig,
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
        onPingProgress: ((Int, Int) -> Unit)? = null,
    ): LatencyPhase {
        val coletaProbe = coletarAmostrasLatencia(latencyProbeUrl, config, redeInicial, connectionTypeProvider, onPingProgress)

        // GH#1118: worker dedicado sem resposta (perda total) — cai pro host público em
        // vez de devolver latência sem dado. Se o probe já É o host público (default sem
        // override), não há para onde cair — usa o resultado como veio.
        val coleta =
            if (latencyProbeUrl != HOST_PUBLICO_LATENCIA && ValidadorBaselineLatencia.probeIndisponivel(coletaProbe.resultado)) {
                Timber.w("latenciaBase: probe dedicado sem resposta ($latencyProbeUrl), fallback para host publico")
                coletarAmostrasLatencia(HOST_PUBLICO_LATENCIA, config, redeInicial, connectionTypeProvider, onPingProgress)
            } else {
                coletaProbe
            }

        val resultado = coleta.resultado
        val evidenciaPerda = AnalisadorAmostragemPing.avaliarConfianca(resultado, coleta.confirmacaoExecutada)

        return LatencyPhase(
            latenciaMs = resultado.latenciaMs,
            jitterMs = resultado.jitterMs,
            perdaPercentual = resultado.perdaPercentual,
            totalAmostras = resultado.totalAmostras,
            amostrasValidas = resultado.amostrasValidas,
            timeouts = resultado.timeouts,
            p95Ms = resultado.p95Ms,
            maxMs = resultado.maxMs,
            picos = resultado.picos,
            evidenciaPerda = evidenciaPerda,
        )
    }

    /** Resultado interno de [coletarAmostrasLatencia]: o [resultado] já inclui as
     *  amostras extras da janela de confirmação quando ela rodou ([confirmacaoExecutada]). */
    internal data class ResultadoColetaLatencia(
        val resultado: ResultadoAmostragemPing,
        val confirmacaoExecutada: Boolean,
    )

    internal suspend fun coletarAmostrasLatencia(
        url: String,
        config: SpeedtestConfig,
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
        onPingProgress: ((Int, Int) -> Unit)?,
    ): ResultadoColetaLatencia {
        val inicioNs = System.nanoTime()

        fun orcamentoEstourado(): Boolean {
            val orcamentoMs = config.latenciaOrcamentoMs ?: return false
            return (System.nanoTime() - inicioNs) / 1_000_000L >= orcamentoMs
        }

        val bruto = mutableListOf<Double?>()
        repeat(config.pingCount) { i ->
            if (mudouRede(redeInicial, connectionTypeProvider) || orcamentoEstourado()) return@repeat
            bruto.add(medirPing(url))
            onPingProgress?.invoke(i + 1, config.pingCount)
        }

        // Algoritmo de mediana/outlier/jitter/perda extraído para AnalisadorAmostragemPing
        // (GH#1019) — reusado também por PingExecutor. Aqui só permanece o que é
        // específico do speedtest: laço de coleta com corte por mudança de rede.
        val baseline = AnalisadorAmostragemPing.analisar(bruto)

        // Camillo/Luiz (.agents/architecture-plan.md, "Confiabilidade estatística do
        // diagnóstico de rede" seção 7): janela de confirmação de +20 probes brutos,
        // disparo único (sem loop, para não estourar o orçamento de tempo do teste),
        // quando o baseline já deu QUALQUER sinal de problema. Continuação da MESMA
        // coleta (a lista `bruto` já tem a 1ª amostra "de aquecimento" descartada só
        // uma vez por AnalisadorAmostragemPing — não há novo warm-up aqui).
        if (!deveConfirmarAmostragem(baseline) || orcamentoEstourado()) {
            return ResultadoColetaLatencia(baseline, confirmacaoExecutada = false)
        }

        repeat(PROBES_JANELA_CONFIRMACAO) { i ->
            if (mudouRede(redeInicial, connectionTypeProvider) || orcamentoEstourado()) return@repeat
            bruto.add(medirPing(url))
            // Mantém o progresso reportado no teto do baseline durante a confirmação —
            // não redesenha a barra de progresso (fora de escopo desta mudança); evita
            // que o percentual ande pra trás ao trocar o "total" no meio da coleta.
            onPingProgress?.invoke(config.pingCount, config.pingCount)
        }

        // `confirmacaoExecutada = true` mesmo se o orçamento cortou a janela no meio —
        // a confirmação REALMENTE rodou (não é o mesmo caso de "nem chegou a disparar"
        // acima); o resultado é analisado sobre as amostras que deu tempo de coletar,
        // igual ao corte por mudança de rede (nunca é tratado como erro).
        val confirmado = AnalisadorAmostragemPing.analisar(bruto)
        return ResultadoColetaLatencia(confirmado, confirmacaoExecutada = true)
    }

    /**
     * Os 4 gatilhos da janela de confirmação (seção 7 do plano): qualquer um dispara.
     * Sinal de rede já saudável (o caso comum) não passa por nenhum — só paga o custo
     * extra quando já há indício de problema no baseline.
     */
    internal fun deveConfirmarAmostragem(baseline: ResultadoAmostragemPing): Boolean {
        if (baseline.timeouts >= 1) return true
        if (baseline.picos > 0) return true
        val jitterStatus = classificarJitterLocal(baseline.jitterMs)
        if (jitterStatus == MetricStatus.regular || jitterStatus == MetricStatus.ruim) return true
        return perdaProximaDeCorteDeNegocio(baseline.perdaPercentual)
    }

    // Cortes de negócio de perda de pacotes (fonte única: `regras-diagnostico-rede`,
    // já usados por MetricClassifier/GameReadinessClassifier/UsageProfileClassifier/
    // ScoreEvidenceBuilder — não duplicar o VALOR do threshold, só a checagem de
    // proximidade que decide se vale a pena confirmar a amostra).
    private val cortesPerdaNegocioPercentual = listOf(0.5, 1.0, 2.0, 3.0)

    private fun perdaProximaDeCorteDeNegocio(perdaPercentual: Double): Boolean =
        cortesPerdaNegocioPercentual.any { corte ->
            val margem = corte * 0.30
            perdaPercentual in (corte - margem)..(corte + margem)
        }

    /**
     * GH#1118: se a latência base medida ficar maior que a latência sob carga, a medição
     * está fisicamente invertida (a rede não fica mais rápida sob carga) — sinal de que o
     * baseline foi contaminado (ex.: throttling pontual do host de latência). Remede a fase
     * de latência uma única vez, com a conexão já ociosa (download/upload terminados), em
     * vez de aceitar/exibir o número bruto absurdo. Se a remedição continuar implausível,
     * aceita o resultado mesmo assim — o cálculo de bufferbloat já grampeia em 0 (não há
     * como inflar negativamente o resultado final) e insistir indefinidamente arriscaria
     * atrasar a conclusão do teste sem garantia de convergência.
     */
    private suspend fun validarBaselineLatencia(
        latencyPhase: LatencyPhase,
        pingDownload: List<Double>,
        pingUpload: List<Double>,
        config: SpeedtestConfig,
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
    ): LatencyPhase {
        val latenciaSobCarga = max(median(pingDownload), median(pingUpload))
        if (!ValidadorBaselineLatencia.baselineImplausivel(latencyPhase.latenciaMs, latenciaSobCarga)) {
            return latencyPhase
        }
        Timber.w(
            "latenciaBase implausivel: base=${latencyPhase.latenciaMs}ms > sobCarga=${latenciaSobCarga}ms — remedindo uma vez",
        )
        return executarFaseLatencia(
            config = config,
            redeInicial = redeInicial,
            connectionTypeProvider = connectionTypeProvider,
        )
    }

    private suspend fun executarFaseTransferencia(
        isDownload: Boolean,
        config: SpeedtestConfig,
        onFaseProgress: (Double) -> Unit,
        pingsSobCarga: MutableList<Double>,
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
    ): ThroughputPhase =
        supervisorScope {
            val duracaoMs = if (isDownload) config.downloadDurationMs else config.uploadDurationMs
            val warmupMs = if (isDownload) config.downloadWarmupMs else config.uploadWarmupMs
            val payloadBytes = if (isDownload) config.downloadPayloadBytes else config.uploadPayloadBytes
            val streamInicial = if (isDownload) config.downloadInitialStreams else config.uploadInitialStreams
            val maxStreams = if (isDownload) config.downloadMaxStreams else config.uploadMaxStreams

            val inicioNs = System.nanoTime()
            val stopNs = inicioNs + (duracaoMs * 1_000_000L)
            val stopFlag = AtomicBoolean(false)
            val bytesTick = AtomicLong(0)
            val bytesTotal = AtomicLong(0)
            val requisicoesSucesso = AtomicInteger(0)
            val requisicoesErro = AtomicInteger(0)
            val ultimoErro = AtomicReference<String?>(null)
            val targetStreams = AtomicInteger(streamInicial)
            val amostras = Collections.synchronizedList(mutableListOf<Sample>())
            val workers = mutableListOf<Job>()
            val ultimoSampleNs = AtomicLong(inicioNs)

            fun elapsedMs(): Long = (System.nanoTime() - inicioNs) / 1_000_000L

            fun spawnWorker(indice: Int) {
                workers +=
                    launch {
                        if (indice > 0) delay(indice * 200L)
                        var fallbackTriedDownload = false
                        var payloadDownloadAtual = payloadBytes
                        var tentativasRateLimit = 0
                        while (!stopFlag.get() && System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                            if (indice >= targetStreams.get()) {
                                delay(120)
                                continue
                            }
                            val restanteMs = ((stopNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                            if (restanteMs <= 0L) break
                            try {
                                if (isDownload) {
                                    executarRequestDownload(payloadDownloadAtual) { lidos ->
                                        bytesTick.addAndGet(lidos.toLong())
                                        bytesTotal.addAndGet(lidos.toLong())
                                        bytesConsumidosTotal.addAndGet(lidos.toLong())
                                    }
                                } else {
                                    val sent = executarRequestUpload(payloadBytes)
                                    bytesTick.addAndGet(sent.toLong())
                                    bytesTotal.addAndGet(sent.toLong())
                                    bytesConsumidosTotal.addAndGet(sent.toLong())
                                }
                                tentativasRateLimit = 0
                                requisicoesSucesso.incrementAndGet()
                            } catch (t: Throwable) {
                                val erroResumo = resumirErroTransferencia(t)
                                val ehRateLimit = erroResumo.contains("HttpStatus:429") || erroResumo.contains("HttpStatus:403")
                                if (isDownload && ehRateLimit) {
                                    if (ultimoErro.get() == null) ultimoErro.set(erroResumo)
                                    tentativasRateLimit++
                                    val backoffMs = min(2000L, 500L * tentativasRateLimit)
                                    delay(backoffMs)
                                    if (tentativasRateLimit >= 3) {
                                        delay(4000L)
                                        tentativasRateLimit = 0
                                    }
                                    continue
                                }
                                requisicoesErro.incrementAndGet()
                                if (ultimoErro.get() == null) ultimoErro.set(erroResumo)
                                if (isDownload && !fallbackTriedDownload) {
                                    val fallback = reduzirPayloadDownload(payloadDownloadAtual)
                                    if (fallback < payloadDownloadAtual) {
                                        payloadDownloadAtual = fallback
                                        fallbackTriedDownload = true
                                        continue
                                    }
                                }
                                break
                            }
                        }
                    }
            }

            repeat(maxStreams) { idx -> spawnWorker(idx) }

            // Intervalo aumentado para 1000ms (era 300ms) durante o throughput:
            // pings a cada 300ms competem por banda com os workers de transferência no mesmo pool
            // HTTP/2, distorcendo o throughput e os próprios valores de latência sob carga.
            // 1 amostra/s ainda é suficiente para calcular bufferbloat com precisão adequada.
            val pingJob =
                launch {
                    while (!stopFlag.get() && System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                        val t0 = System.nanoTime()
                        val rtt = medirPing()
                        if (rtt != null) pingsSobCarga.add(rtt)
                        val elapsed = (System.nanoTime() - t0) / 1_000_000L
                        val waitMs = max(0L, 1_000L - elapsed)
                        delay(waitMs)
                    }
                }

            val sampler =
                launch {
                    var ultimaEscalaMs = 0L
                    while (!stopFlag.get() && System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                        delay(1_000)
                        val agoraNs = System.nanoTime()
                        val elapsedSec = (agoraNs - ultimoSampleNs.get()).toDouble() / 1_000_000_000.0
                        ultimoSampleNs.set(agoraNs)
                        val bytes = bytesTick.getAndSet(0)
                        val tMs = elapsedMs()
                        val progressoLocal = min(1.0, tMs.toDouble() / duracaoMs.toDouble())
                        onFaseProgress(progressoLocal)

                        if (elapsedSec > 0.0 && bytes > 0L) {
                            val instant = (bytes * 8.0) / elapsedSec / 1_000_000.0
                            amostras.add(Sample(tMs.toInt(), instant))
                            velocidadeAtualInterna = instant
                        }

                        val prontoParaEscalar = tMs - ultimaEscalaMs >= 4000L && targetStreams.get() < maxStreams
                        if (prontoParaEscalar) {
                            ultimaEscalaMs = tMs
                            val ganho = calcularGanhoJanela(amostras.toList(), tMs.toInt())
                            if (ganho >= 0.10) {
                                val novoAlvo = min(maxStreams, targetStreams.get() + 2)
                                targetStreams.set(novoAlvo)
                            }
                        }
                    }
                }

            while (System.nanoTime() < stopNs && !mudouRede(redeInicial, connectionTypeProvider) && !cancelFlag.get()) {
                delay(80)
            }
            stopFlag.set(true)
            pingJob.join()
            sampler.join()
            workers.forEach { it.join() }
            val bytesRestantes = bytesTick.getAndSet(0)
            val agoraFlushNs = System.nanoTime()
            val elapsedFlushSec = (agoraFlushNs - ultimoSampleNs.get()).toDouble() / 1_000_000_000.0
            if (bytesRestantes > 0L && elapsedFlushSec > 0.0) {
                val instant = (bytesRestantes * 8.0) / elapsedFlushSec / 1_000_000.0
                amostras.add(Sample(elapsedMs().toInt(), instant))
            }
            val duracaoMedidaMs = ((System.nanoTime() - inicioNs) / 1_000_000L).coerceAtLeast(1L)

            val amostrasValidas =
                amostras
                    .filter { it.tMs >= warmupMs && it.mbps > 0.0 }
                    .sortedBy { it.tMs }
            val corte = min(amostrasValidas.size, kotlin.math.ceil(amostrasValidas.size * 0.35).toInt())
            val estaveis = amostrasValidas.drop(corte)
            val throughputCalculado =
                calcularThroughputFase(
                    estaveis = estaveis,
                    amostrasValidas = amostrasValidas,
                    bytesTotal = bytesTotal.get(),
                    duracaoFaseMs = duracaoMedidaMs,
                    warmupMs = warmupMs,
                )
            if (isDownload && bytesTotal.get() <= 0L && requisicoesSucesso.get() <= 0) {
                throw IllegalStateException("download_failed:${ultimoErro.get() ?: "semDetalhe"}")
            }
            val pico = amostrasValidas.maxOfOrNull { it.mbps } ?: 0.0
            val encerradaPor =
                when {
                    mudouRede(redeInicial, connectionTypeProvider) -> "redeMudou"
                    else -> "tempoEsgotado"
                }
            ThroughputPhase(
                throughputMbps = throughputCalculado.mbps,
                peakMbps = pico,
                amostrasInstantaneas = amostrasValidas.map { it.mbps },
                bytesTotal = bytesTotal.get(),
                requisicoesSucesso = requisicoesSucesso.get(),
                requisicoesErro = requisicoesErro.get(),
                faseEncerradaPor = encerradaPor,
                throughputOrigem = throughputCalculado.origem,
                ultimoErro = ultimoErro.get(),
            )
        }

    // ── Camada HTTP (OkHttp) ─────────────────────────────────────────────────

    private fun executarRequestDownload(
        payloadBytes: Int,
        onBytesChunk: ((Int) -> Unit)? = null,
    ): Int {
        val url = "https://speed.cloudflare.com/__down?bytes=$payloadBytes&_cb=${cacheBust()}"
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        val response = downloadClient.newCall(request).execute()
        return try {
            if (!response.isSuccessful) throw IllegalStateException("HttpStatus:${response.code}")
            val body = response.body ?: throw IllegalStateException("emptyBody")
            val buffer = ByteArray(16 * 1024)
            var total = 0
            body.byteStream().use { stream ->
                while (true) {
                    val lidos = stream.read(buffer)
                    if (lidos < 0) break
                    total += lidos
                    onBytesChunk?.invoke(lidos)
                }
            }
            total
        } finally {
            response.close()
        }
    }

    private fun executarRequestUpload(payloadBytes: Int): Int {
        val url = "https://speed.cloudflare.com/__up?_cb=${cacheBust()}"
        val payload = obterPayloadUpload(payloadBytes)
        val body = payload.toRequestBody("application/octet-stream".toMediaType())
        val request =
            Request
                .Builder()
                .url(url)
                .post(body)
                .build()
        val response = client.newCall(request).execute()
        return try {
            if (!response.isSuccessful) throw IllegalStateException("HttpStatus:${response.code}")
            payloadBytes
        } finally {
            response.close()
        }
    }

    private suspend fun executarProbeUpload(): Double {
        val payloadBytes = 256 * 1024
        var totalBytes = 0L
        val inicioNs = System.nanoTime()
        var sucesso = 0
        repeat(3) {
            try {
                val sent = executarRequestUpload(payloadBytes)
                totalBytes += sent.toLong()
                sucesso++
            } catch (_: Throwable) {
            }
        }
        val elapsedMs = ((System.nanoTime() - inicioNs) / 1_000_000L).coerceAtLeast(1L)
        if (sucesso == 0) return 0.0
        return (totalBytes * 8.0) / (elapsedMs.toDouble() / 1000.0) / 1_000_000.0
    }

    private fun medirPing(baseUrl: String = HOST_PUBLICO_LATENCIA): Double? {
        val separador = if (baseUrl.contains("?")) "&" else "?"
        val url = "$baseUrl${separador}_cb=${cacheBust()}"
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        val inicio = System.nanoTime()
        return try {
            val response = pingClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()
                (System.nanoTime() - inicio) / 1_000_000.0
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun obterPayloadUpload(payloadBytes: Int): ByteArray =
        uploadPayloadCache.getOrPut(payloadBytes) {
            ByteArray(payloadBytes) { indice -> (indice and 0xFF).toByte() }
        }

    private fun executarDnsProbe(): DnsProbeResult {
        val url = "https://cloudflare-dns.com/dns-query?name=whoami.cloudflare.com&type=TXT"
        val request =
            Request
                .Builder()
                .url(url)
                .header("accept", "application/dns-json")
                .get()
                .build()
        val inicio = System.nanoTime()
        return try {
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return DnsProbeResult(null, null, null, null)
                val corpo = resp.body?.string() ?: return DnsProbeResult(null, null, null, null)
                val duracaoMs = ((System.nanoTime() - inicio) / 1_000_000L).toInt()
                val resolverIp = extrairPrimeiroIpv4(corpo)
                val provider =
                    when {
                        corpo.contains("cloudflare", ignoreCase = true) -> "cloudflare"
                        resolverIp != null -> "desconhecido"
                        else -> null
                    }
                DnsProbeResult(duracaoMs, resolverIp, provider, null)
            }
        } catch (_: Throwable) {
            DnsProbeResult(null, null, null, null)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    // Classificacao extraida para SpeedtestQualityClassifier para reuso no diagnostico
    // sem duplicar thresholds. Nao alterar comportamento do speedtest aqui.

    private fun calcularEstabilidade(amostrasMbps: List<Double>): Double {
        val positivas = amostrasMbps.filter { it > 0.0 }
        if (positivas.size < 2) return 50.0
        val media = positivas.average()
        if (media <= 0.0) return 0.0
        val variancia = positivas.map { (it - media) * (it - media) }.average()
        val std = kotlin.math.sqrt(variancia)
        val cv = std / media
        return max(0.0, min(100.0, 100.0 - cv * 150.0))
    }

    private fun calcularGanhoJanela(
        amostras: List<Sample>,
        nowMs: Int,
    ): Double {
        val recente = amostras.filter { it.tMs > nowMs - 4000 }
        val anterior = amostras.filter { it.tMs <= nowMs - 4000 && it.tMs > nowMs - 8000 }
        if (recente.size < 3 || anterior.size < 3) return 0.0
        val mediaRecente = recente.map { it.mbps }.average()
        val mediaAnterior = anterior.map { it.mbps }.average()
        if (mediaAnterior <= 0.0) return 0.0
        return (mediaRecente - mediaAnterior) / mediaAnterior
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val m = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[m - 1] + sorted[m]) / 2.0 else sorted[m]
    }

    private fun mudouRede(
        redeInicial: String?,
        connectionTypeProvider: (() -> String?)?,
    ): Boolean {
        if (redeInicial == null || connectionTypeProvider == null) return false
        val atual = connectionTypeProvider.invoke() ?: return false
        return atual != redeInicial
    }

    private fun cacheBust(): String = "${System.currentTimeMillis()}_${Random.nextInt(10_000, 99_999)}"

    private fun extrairPrimeiroIpv4(texto: String): String? {
        val regex = Regex("""\b((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(?!$)|$)){4}\b""")
        return regex.find(texto)?.value
    }

    private fun calcularThroughputFase(
        estaveis: List<Sample>,
        amostrasValidas: List<Sample>,
        bytesTotal: Long,
        duracaoFaseMs: Long,
        warmupMs: Int,
    ): ThroughputCalculado {
        if (estaveis.isNotEmpty()) {
            return ThroughputCalculado(mbps = estaveis.map { it.mbps }.average(), origem = "estavel")
        }
        if (amostrasValidas.isNotEmpty()) {
            return ThroughputCalculado(mbps = amostrasValidas.map { it.mbps }.average(), origem = "validasSemCorte")
        }
        if (bytesTotal > 0L) {
            val janelaUtilMs = max(1L, duracaoFaseMs - warmupMs.toLong())
            return ThroughputCalculado(
                mbps = (bytesTotal * 8.0) / (janelaUtilMs.toDouble() / 1000.0) / 1_000_000.0,
                origem = "bytesTempoUtil",
            )
        }
        return ThroughputCalculado(mbps = 0.0, origem = "semDados")
    }

    private fun resumirErroTransferencia(t: Throwable): String {
        val tipo = t::class.java.simpleName.ifBlank { "Throwable" }
        val mensagem = (t.message ?: "").replace('\n', ' ').trim()
        val trecho = if (mensagem.length > 120) mensagem.substring(0, 120) else mensagem
        return if (trecho.isBlank()) tipo else "$tipo:$trecho"
    }

    private fun reduzirPayloadDownload(atual: Int): Int {
        val tamanhos = listOf(100_000, 1_000_000, 10_000_000, 25_000_000, 100_000_000)
        val idx = tamanhos.indexOf(atual)
        if (idx <= 0) return atual
        return tamanhos[idx - 1]
    }

    private fun construirResultado(
        modo: ModoSpeedtest,
        redeInicial: String?,
        redeFinal: String?,
        latencyPhase: LatencyPhase,
        downloadPhase: ThroughputPhase,
        uploadPhase: ThroughputPhase,
        pingDownload: List<Double>,
        pingUpload: List<Double>,
        dns: DnsProbeResult,
        contaminado: Boolean,
        faseInterrompida: String,
        uploadNaoDetectado: Boolean = false,
        tecnologia: String? = null,
        executionId: String = "",
    ): ResultadoSpeedtest {
        val latencyDownload = median(pingDownload)
        val latencyUpload = median(pingUpload)
        val bufferbloatMs = max(max(latencyDownload, latencyUpload) - latencyPhase.latenciaMs, 0.0)
        val severidadeBufferbloat = SpeedtestQualityClassifier.classificarBufferbloat(bufferbloatMs)
        // GH#1221/#1225 — status canonico de integridade (ver MeasurementStatus.kt).
        val status =
            calcularMeasurementStatus(
                contaminado = contaminado,
                amostrasValidasLatencia = latencyPhase.amostrasValidas,
                uploadNaoDetectado = uploadNaoDetectado,
                downloadEncerradaPor = downloadPhase.faseEncerradaPor,
                uploadEncerradaPor = uploadPhase.faseEncerradaPor,
            )
        val estabilidade = calcularEstabilidade(downloadPhase.amostrasInstantaneas + uploadPhase.amostrasInstantaneas)
        val diagnostico =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = downloadPhase.throughputMbps,
                ul = uploadPhase.throughputMbps,
                latency = latencyPhase.latenciaMs,
                jitter = latencyPhase.jitterMs,
                packetLoss = latencyPhase.perdaPercentual,
                bufferbloatDeltaMs = bufferbloatMs,
                bufferbloat = severidadeBufferbloat,
                perdaConfianca = latencyPhase.evidenciaPerda?.confianca,
            )
        return ResultadoSpeedtest(
            timestampEpochMs = System.currentTimeMillis(),
            specVersion = "1.0.0",
            modo = modo,
            connectionTypeStart = redeInicial,
            connectionTypeEnd = redeFinal,
            contaminado = contaminado,
            latenciaMs = latencyPhase.latenciaMs,
            jitterMs = latencyPhase.jitterMs,
            perdaPercentual = latencyPhase.perdaPercentual,
            bufferbloatMs = bufferbloatMs,
            severidadeBufferbloat = severidadeBufferbloat,
            downloadMbps = downloadPhase.throughputMbps,
            uploadMbps = uploadPhase.throughputMbps,
            latencyDownloadMs = latencyDownload,
            latencyUploadMs = latencyUpload,
            stabilityScore = estabilidade,
            peakDownloadMbps = downloadPhase.peakMbps,
            peakUploadMbps = uploadPhase.peakMbps,
            packetLossSource = "estimated",
            perdaConfianca = latencyPhase.evidenciaPerda?.confianca,
            dnsLatencyMs = dns.latencyMs,
            dnsResolverIp = dns.resolverIp,
            dnsProvider = dns.provider,
            diagnosticoQualidade = diagnostico,
            diagnosticoFases =
                DiagnosticoFasesSpeedtest(
                    faseInterrompida = faseInterrompida,
                    latenciaAmostrasTotais = latencyPhase.totalAmostras,
                    latenciaAmostrasValidas = latencyPhase.amostrasValidas,
                    latenciaTimeouts = latencyPhase.timeouts,
                    latenciaP95Ms = latencyPhase.p95Ms,
                    latenciaMaxMs = latencyPhase.maxMs,
                    latenciaPicos = latencyPhase.picos,
                    latenciaConfirmacaoExecutada = latencyPhase.evidenciaPerda?.confirmacaoExecutada ?: false,
                    downloadBytesTotal = downloadPhase.bytesTotal,
                    downloadAmostrasValidas = downloadPhase.amostrasInstantaneas.size,
                    downloadRequisicoesSucesso = downloadPhase.requisicoesSucesso,
                    downloadRequisicoesErro = downloadPhase.requisicoesErro,
                    downloadEncerradaPor = downloadPhase.faseEncerradaPor,
                    downloadThroughputOrigem = downloadPhase.throughputOrigem,
                    downloadUltimoErro = downloadPhase.ultimoErro,
                    uploadBytesTotal = uploadPhase.bytesTotal,
                    uploadAmostrasValidas = uploadPhase.amostrasInstantaneas.size,
                    uploadRequisicoesSucesso = uploadPhase.requisicoesSucesso,
                    uploadRequisicoesErro = uploadPhase.requisicoesErro,
                    uploadEncerradaPor = uploadPhase.faseEncerradaPor,
                    uploadThroughputOrigem = uploadPhase.throughputOrigem,
                    uploadUltimoErro = uploadPhase.ultimoErro,
                    dnsErroMensagem = dns.erroMensagem,
                ),
            uploadNaoDetectado = uploadNaoDetectado,
            // #862: usar a rede FINAL (nao a inicial) — se a rede mudou durante o
            // teste (ex.: Wi-Fi reativou sozinho e caiu pra movel no meio da
            // medicao), o badge/PDF devem refletir o estado real ao final, igual
            // ao que o diagnostico local/IA ja usam (connectionTypeEnd).
            connectionType = redeFinal ?: redeInicial,
            tecnologia = tecnologia,
            executionId = executionId,
            status = status,
        )
    }

    private fun registrarDiagnostico(resultado: ResultadoSpeedtest) {
        val d = resultado.diagnosticoFases
        Timber.i(
            "modo=${resultado.modo.name} d=${resultado.downloadMbps} u=${resultado.uploadMbps} " +
                "lat=${resultado.latenciaMs} jit=${resultado.jitterMs} " +
                "faseInterrompida=${d.faseInterrompida} " +
                "dlReqOk=${d.downloadRequisicoesSucesso} dlReqErr=${d.downloadRequisicoesErro} dlBytes=${d.downloadBytesTotal} dlOrigem=${d.downloadThroughputOrigem} dlErr=${d.downloadUltimoErro ?: "none"} " +
                "ulReqOk=${d.uploadRequisicoesSucesso} ulReqErr=${d.uploadRequisicoesErro} ulBytes=${d.uploadBytesTotal} ulOrigem=${d.uploadThroughputOrigem} ulErr=${d.uploadUltimoErro ?: "none"} " +
                "dnsErro=${d.dnsErroMensagem ?: "none"}",
        )
    }

    private fun publicar(
        estado: EstadoExecucaoSpeedtest,
        progressoPercentual: Int,
        resultado: ResultadoSpeedtest?,
        erroMensagem: String?,
        causaFalha: CausaFalhaSpeedtest? = null,
    ) {
        val progresso = min(100, max(0, progressoPercentual))
        val vel = velocidadeAtualInterna
        when (faseAtualInterna) {
            FaseSpeedtest.download ->
                if (vel > 0) {
                    pontosAoVivoInternos.add(PontoAoVivo(t = System.currentTimeMillis(), dl = vel))
                    if (pontosAoVivoInternos.size > 60) pontosAoVivoInternos.removeFirst()
                }
            FaseSpeedtest.upload ->
                if (vel > 0) {
                    pontosAoVivoInternos.add(PontoAoVivo(t = System.currentTimeMillis(), ul = vel))
                    if (pontosAoVivoInternos.size > 60) pontosAoVivoInternos.removeFirst()
                }
            else -> {}
        }
        mutableSnapshotFlow.value =
            SnapshotExecucaoSpeedtest(
                estado = estado,
                progressoPercentual = progresso,
                resultado = resultado,
                erroMensagem = erroMensagem,
                faseAtual = faseAtualInterna,
                velocidadeAtualMbps = vel,
                bytesConsumidos = bytesConsumidosTotal.get(),
                progressoGlobal = progresso / 100f,
                pontosAoVivo = pontosAoVivoInternos.toList(),
                causaFalha = causaFalha,
            )
    }

    private fun possuiTransporte(connectionType: String?): Boolean =
        connectionType != null && connectionType != "desconectado"

    /**
     * Converte a cadeia de exceções apenas no limite que publica o snapshot. O detalhe bruto
     * não atravessa o contrato de UI; ele já foi registrado por [Timber.e] no chamador.
     */
    internal fun mapearCausaFalha(
        erro: Throwable,
        possuiTransporte: Boolean,
    ): CausaFalhaSpeedtest =
        when {
            !possuiTransporte -> CausaFalhaSpeedtest.SEM_CONEXAO
            erro.temCausa<UnknownHostException>() ||
                erro.temDescricaoDeCausa("UnknownHostException") -> CausaFalhaSpeedtest.DNS_OU_HOSTNAME_INACESSIVEL
            erro.temCausa<SocketTimeoutException>() ||
                erro.temCausa<InterruptedIOException>() ||
                erro.temCausa<TimeoutCancellationException>() ||
                erro.temDescricaoDeCausa("SocketTimeoutException") ||
                erro.temDescricaoDeCausa("TimeoutCancellationException") -> CausaFalhaSpeedtest.TIMEOUT
            else -> CausaFalhaSpeedtest.FALHA_GENERICA
        }

    private inline fun <reified T : Throwable> Throwable.temCausa(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    /** Prefixos legados de [executarFaseTransferencia] serializam o tipo da causa no texto. */
    private fun Throwable.temDescricaoDeCausa(tipo: String): Boolean =
        generateSequence(this) { it.cause }.any { it.message?.contains(tipo) == true }

    private fun throughputVazio(encerradaPor: String): ThroughputPhase =
        ThroughputPhase(
            throughputMbps = 0.0,
            peakMbps = 0.0,
            amostrasInstantaneas = emptyList(),
            bytesTotal = 0L,
            requisicoesSucesso = 0,
            requisicoesErro = 0,
            faseEncerradaPor = encerradaPor,
            throughputOrigem = "naoExecutado",
            ultimoErro = null,
        )

    // ── Data classes internos ─────────────────────────────────────────────────

    internal data class LatencyPhase(
        val latenciaMs: Double,
        val jitterMs: Double,
        val perdaPercentual: Double,
        val totalAmostras: Int,
        val amostrasValidas: Int,
        val timeouts: Int,
        // Antes descartados aqui (GH#1211 item 3 já calculava, ExecutorSpeedtestCloudflare
        // só não propagava) — ver .agents/architecture-plan.md seção 2/8.
        val p95Ms: Double = 0.0,
        val maxMs: Double = 0.0,
        val picos: Int = 0,
        val evidenciaPerda: EvidenciaPerdaPacotes? = null,
    )

    private data class ThroughputPhase(
        val throughputMbps: Double,
        val peakMbps: Double,
        val amostrasInstantaneas: List<Double>,
        val bytesTotal: Long,
        val requisicoesSucesso: Int,
        val requisicoesErro: Int,
        val faseEncerradaPor: String,
        val throughputOrigem: String,
        val ultimoErro: String?,
    )

    private data class ThroughputCalculado(
        val mbps: Double,
        val origem: String,
    )

    private data class Sample(
        val tMs: Int,
        val mbps: Double,
    )

    internal data class SpeedtestConfig(
        val pingCount: Int,
        val downloadDurationMs: Long,
        val uploadDurationMs: Long,
        val downloadPayloadBytes: Int,
        val uploadPayloadBytes: Int,
        val downloadInitialStreams: Int,
        val downloadMaxStreams: Int,
        val uploadInitialStreams: Int,
        val uploadMaxStreams: Int,
        val downloadWarmupMs: Int,
        val uploadWarmupMs: Int,
        // Camillo (achado médio de Breno no QA desta mudança, .agents/architecture-plan.md
        // seção 9/10 item 8): teto de duração de PROTEÇÃO para a fase de latência inteira
        // (baseline + janela de confirmação), só no modo fast. NÃO é um threshold de
        // negócio (não muda pingCount=20, não muda os 4 gatilhos de confirmação, não muda
        // nenhum corte de perda) — é uma rede de segurança de resiliência/UX: sem ela, o
        // pior caso teórico do modo fast em rede muito degradada passou de ~60s (antes
        // desta fatia) para até ~160s (20 probes baseline + 20 de confirmação, 4s de
        // callTimeout cada). `null` = sem teto (modo complete preserva o comportamento
        // já decidido na seção 7 do plano — mais completude, não é sensível a bateria/dados
        // do jeito que o fast é). Comportamento ao estourar o teto: para de coletar novas
        // amostras e analisa o que já foi coletado até ali — mesmo padrão já usado para
        // `mudouRede()` (early-exit tolerante a resultado parcial, não um erro).
        val latenciaOrcamentoMs: Long? = null,
    ) {
        companion object {
            fun fromModo(modo: ModoSpeedtest): SpeedtestConfig =
                when (modo) {
                    // Espelho do TypeScript DOWNLOAD_CONFIG_FAST / DOWNLOAD_CONFIG_COMPLETE
                    ModoSpeedtest.fast ->
                        SpeedtestConfig(
                            // Camillo/Luiz (.agents/architecture-plan.md, "Confiabilidade
                            // estatistica..."): 15->20 probes brutos (19 efetivos pos
                            // warm-up) para reduzir a resolucao minima de 1 timeout
                            // isolado de 7,14% para 5,26% -- ainda insuficiente sozinho,
                            // por isso a janela de confirmacao em coletarAmostrasLatencia.
                            pingCount = 20,
                            downloadDurationMs = 7_000L,
                            uploadDurationMs = 7_000L,
                            downloadPayloadBytes = 10_000_000, // 10 MB
                            uploadPayloadBytes = 5_000_000,
                            downloadInitialStreams = 2,
                            downloadMaxStreams = 4,
                            uploadInitialStreams = 4,
                            uploadMaxStreams = 4,
                            downloadWarmupMs = 1_000,
                            uploadWarmupMs = 1_000,
                            latenciaOrcamentoMs = 20_000L,
                        )
                    ModoSpeedtest.complete ->
                        SpeedtestConfig(
                            pingCount = 25,
                            downloadDurationMs = 18_000L,
                            uploadDurationMs = 18_000L,
                            downloadPayloadBytes = 25_000_000, // 25 MB
                            uploadPayloadBytes = 10_000_000,
                            downloadInitialStreams = 2,
                            downloadMaxStreams = 8,
                            uploadInitialStreams = 8,
                            uploadMaxStreams = 8,
                            downloadWarmupMs = 2_000,
                            uploadWarmupMs = 2_000,
                        )
                }
        }
    }

    private data class DnsProbeResult(
        val latencyMs: Int?,
        val resolverIp: String?,
        val provider: String?,
        val erroMensagem: String?,
    )
}
