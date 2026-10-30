package io.signallq.app.analytics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.signallq.app.BuildConfig
import io.signallq.app.core.database.analytics.AnalyticsOutboxDao
import io.signallq.app.core.database.analytics.AnalyticsOutboxEntity
import io.signallq.app.core.datastore.PreferenciasAppRepository
import io.signallq.app.core.network.AnalyticsTracker
import io.signallq.app.core.network.AssistAbandonado
import io.signallq.app.core.network.AssistObjetivoSelecionado
import io.signallq.app.core.network.AssistPerguntaRespondida
import io.signallq.app.core.network.DiagnosticoComparacaoConcluida
import io.signallq.app.core.network.DiagnosticoPlanoIniciado
import io.signallq.app.core.network.DiagnosticoRetesteIniciado
import io.signallq.app.di.ApplicationScope
import io.signallq.app.feature.diagnostico.ingest.AdminIngestRepository
import io.signallq.app.feature.diagnostico.ingest.AnalyticsEventIngestPayload
import io.signallq.app.feature.diagnostico.ingest.toOutboxJson
import io.signallq.app.monitoramento.AdminSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AnalyticsTracker] composto (GH#759): registra cada evento no Firebase Analytics
 * (GA4 — como sempre foi) E envia o mesmo evento ao signallq-admin-worker
 * (POST /ingest/analytics), para alimentar as telas "Uso do App" e
 * "Problemas & Incidentes" do painel admin.
 *
 * O envio ao admin-worker e fire-and-forget via [AdminIngestRepository] (mesmo
 * padrao/gate de consentimento LGPD de sendDiagnostic/sendAiUsage) — nunca
 * bloqueia nem falha a chamada do site de chamada original.
 *
 * session_id aqui e independente do session_id interno do FirebaseAnalyticsTracker:
 * sao dois sistemas de analytics distintos, sem necessidade de correlacao cruzada.
 */
@Singleton
class CompositeAnalyticsTracker
    @Inject
    constructor(
        private val firebaseTracker: FirebaseAnalyticsTracker,
        private val adminIngestRepository: AdminIngestRepository,
        private val preferenciasAppRepository: PreferenciasAppRepository,
        private val analyticsOutboxDao: AnalyticsOutboxDao,
        private val outboxFunnelTracker: AnalyticsOutboxFunnelTracker,
        @ApplicationContext private val context: Context,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : AnalyticsTracker {
        private var sessionId: String? = null
        private var sessionStartedAtMs = 0L
        private var sessionStartRecorded = false

        private fun currentSessionId(): String =
            sessionId ?: UUID.randomUUID().toString().also { sessionId = it }

        override fun registrarFeatureUsada(
            featureId: String,
            sessionIdOverride: String?,
        ) {
            firebaseTracker.registrarFeatureUsada(featureId)
            enviarEvento(name = "feature_used", featureId = featureId, sessionIdOverride = sessionIdOverride)
        }

        override fun registrarScreenView(screenName: String) {
            firebaseTracker.registrarScreenView(screenName)
            enviarEvento(name = "screen_view", screenName = screenName)
        }

        override fun registrarSessionStart() {
            if (sessionStartRecorded) return
            currentSessionId()
            sessionStartedAtMs = System.currentTimeMillis()
            sessionStartRecorded = true
            firebaseTracker.registrarSessionStart()
            enviarEvento(name = "session_start")
        }

        override fun registrarSessionEnd() {
            val closingSessionId = sessionId ?: return
            firebaseTracker.registrarSessionEnd()
            // O mesmo UUID de instancia identifica inicio e fim. O Worker aceita
            // retries pelo id do evento, enquanto o par session_id permanece estável.
            enviarEvento(
                name = "session_end",
                sessionIdOverride = closingSessionId,
                durationMs = (System.currentTimeMillis() - sessionStartedAtMs).coerceAtLeast(0),
            )
            sessionId = null
            sessionStartRecorded = false
        }

        override fun registrarFeatureCrash(
            featureId: String,
            errorType: String,
        ) {
            firebaseTracker.registrarFeatureCrash(featureId, errorType)
            enviarEvento(name = "feature_crash", featureId = featureId, errorType = errorType)
        }

        override fun registrarBatterySnapshot(
            level: Int,
            charging: Boolean,
        ) {
            firebaseTracker.registrarBatterySnapshot(level, charging)
            enviarEvento(name = "battery_snapshot", batteryLevel = level, batteryCharging = charging)
        }

        // GH#1480 (Epico #1347, F4) — encaminha so ao Firebase (GA4), sem replicar para o
        // ingest do signallq-admin-worker: `AnalyticsEventIngestPayload`/schema D1 nao tem
        // campo equivalente ainda, e estender esse contrato e escopo separado (nao desta
        // instrumentacao). O evento fica completo no Firebase, que ja e a fonte usada pelo
        // criterio de aceite da issue ("feature_blocked_remote registrado sem dado pessoal").
        override fun registrarFeatureBloqueadaRemota(featureId: String) {
            firebaseTracker.registrarFeatureBloqueadaRemota(featureId)
        }

        // Issue #1656 — mesmo padrão de registrarFeatureBloqueadaRemota acima: só Firebase
        // (GA4), sem replicar pro ingest do signallq-admin-worker. `AnalyticsEventIngestPayload`/
        // schema D1 não tem campo equivalente pro funil do Assist ainda, e estender esse
        // contrato é escopo separado desta fatia — o painel admin não é consumidor desta issue
        // (docs/README.md, perímetro de `docs/`: painel Admin vive em `buildea-admin`).
        override fun registrarAssistObjetivo(evento: AssistObjetivoSelecionado) =
            firebaseTracker.registrarAssistObjetivo(evento)

        override fun registrarAssistResposta(evento: AssistPerguntaRespondida) =
            firebaseTracker.registrarAssistResposta(evento)

        override fun registrarAssistAbandono(evento: AssistAbandonado) =
            firebaseTracker.registrarAssistAbandono(evento)

        // Issue #1706 — mesmo racional do bloco do Assist acima: só Firebase (GA4). O schema D1
        // do `signallq-admin-worker` não tem campo para o funil do diagnóstico guiado, e estender
        // esse contrato é escopo separado.
        override fun registrarDiagnosticoPlanoIniciado(evento: DiagnosticoPlanoIniciado) =
            firebaseTracker.registrarDiagnosticoPlanoIniciado(evento)

        // GH#1707 (Task 2.0.09e) — mesmo racional dos dois blocos acima: só Firebase (GA4). O
        // schema D1 do `signallq-admin-worker` não tem campo para o funil do diagnóstico guiado.
        override fun registrarDiagnosticoRetesteIniciado(evento: DiagnosticoRetesteIniciado) =
            firebaseTracker.registrarDiagnosticoRetesteIniciado(evento)

        override fun registrarDiagnosticoComparacaoConcluida(evento: DiagnosticoComparacaoConcluida) =
            firebaseTracker.registrarDiagnosticoComparacaoConcluida(evento)

        private fun enviarEvento(
            name: String,
            featureId: String? = null,
            screenName: String? = null,
            errorType: String? = null,
            batteryLevel: Int? = null,
            batteryCharging: Boolean? = null,
            durationMs: Long? = null,
            // GH#919 — quando presente, correlaciona o evento a diagnostic_sessions.id
            // (mesmo id gravado em ai_usage.session_id) em vez do UUID de instancia.
            sessionIdOverride: String? = null,
        ) {
            // Captura a sessão no thread chamador. A coroutine pode executar só depois de
            // registrarSessionEnd() limpar o estado, mas o evento já pertence à sessão atual.
            val eventSessionId = sessionIdOverride ?: currentSessionId()
            applicationScope.launch {
                // GH#1684 (bloqueio 1 da revisão do Caio na PR #1688) — este corpo roda dentro do
                // mesmo applicationScope que o CoroutineExceptionHandler dele relata via
                // registrarFeatureCrash -> enviarEvento -> este mesmo launch. Sem contenção
                // aqui, uma falha real (ex.: WorkManager.getInstance(context) em
                // AdminSyncScheduler.agendarEntregaAnalytics, sem guarda) reentraria no próprio
                // handler que a relatou, girando em ciclo sem limite, backoff ou dedup — cada
                // volta gera coroutine, recordException e evento de analytics novos. runCatching
                // fecha o ciclo na origem: telemetria nunca pode derrubar nem realimentar o
                // escopo que ela mesma reporta.
                runCatching {
                    val distChannel = distributionChannel(context)
                    val deviceId =
                        runCatching {
                            preferenciasAppRepository.buscarOuGerarAnonDeviceId()
                        }.getOrDefault("unknown")
                    val payload =
                        AnalyticsEventIngestPayload(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            sessionId = eventSessionId,
                            appVersion = BuildConfig.VERSION_NAME,
                            featureId = featureId,
                            screenName = screenName,
                            errorType = errorType,
                            batteryLevel = batteryLevel,
                            batteryCharging = batteryCharging,
                            environment = environmentFor(distChannel),
                            distChannel = distChannel,
                            buildType = BuildConfig.BUILD_TYPE,
                            versionCode = BuildConfig.VERSION_CODE,
                            deviceId = deviceId,
                            durationMs = durationMs,
                        )
                    if (!adminIngestRepository.canSendTelemetry()) return@runCatching
                    val enqueueResult =
                        analyticsOutboxDao.enqueue(
                            AnalyticsOutboxEntity(
                                id = payload.id,
                                payloadJson = payload.toOutboxJson(),
                                createdAtEpochMs = System.currentTimeMillis(),
                            ),
                        )
                    if (enqueueResult != -1L) {
                        outboxFunnelTracker.registrar(AnalyticsOutboxFunnelTracker.Stage.CREATED)
                    }
                    AdminSyncScheduler.agendarEntregaAnalytics(context)
                }.onFailure { throwable ->
                    // Timber.w (não .e): em release, ReleaseTree.log ainda encaminha pro
                    // Crashlytics via recordException (visível), mas só reentra em
                    // registrarFeatureCrash quando priority >= Log.ERROR — WARN preserva a
                    // visibilidade e quebra o ciclo.
                    Timber.w(throwable, "Falha ao processar evento de analytics ($name) — não propagada")
                }
            }
        }
    }
