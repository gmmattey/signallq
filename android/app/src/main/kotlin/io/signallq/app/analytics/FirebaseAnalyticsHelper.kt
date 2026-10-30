package io.signallq.app.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import io.signallq.app.BuildConfig
import io.signallq.app.core.network.AnalyticsHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacao de [AnalyticsHelper] usando Firebase Analytics — funil
 * principal de engajamento (SIG-155). Ver docs/technical/analytics-events.md.
 *
 * Compartilha a mesma instancia de [FirebaseAnalytics] do [FirebaseAnalyticsTracker]
 * (SIG-134) — ambos sao providos como @Singleton a partir do mesmo
 * `FirebaseAnalytics.getInstance(ctx)` em AppModule. Nao ha duplicacao de
 * instancia, apenas duas APIs publicas distintas para dois schemas distintos.
 *
 * `versao_app` e anexado automaticamente em todos os eventos.
 */
@Singleton
class FirebaseAnalyticsHelper
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsHelper {
        private val appVersion: String = BuildConfig.VERSION_NAME

        override fun registrarAppAberto(
            tipoConexao: String,
            primeiraAbertura: Boolean?,
        ) {
            firebaseAnalytics.logEvent(
                "app_aberto",
                Bundle().apply {
                    putString("versao_app", appVersion)
                    putLong("version_code", BuildConfig.VERSION_CODE.toLong())
                    putString("tipo_conexao", tipoConexao)
                    primeiraAbertura?.let { putBoolean("primeira_abertura", it) }
                },
            )
        }

        override fun registrarSpeedtestIniciado(
            modo: String,
            tipoConexao: String,
        ) {
            firebaseAnalytics.logEvent(
                "speedtest_iniciado",
                Bundle().apply {
                    putString("modo", modo)
                    putString("tipo_conexao", tipoConexao)
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarSpeedtestConcluido(
            modo: String,
            tipoConexaoInicio: String,
            tipoConexaoFim: String?,
            downloadMbps: Double,
            uploadMbps: Double,
            latenciaMs: Double,
            jitterMs: Double,
            perdaPct: Double,
            bufferbloatMs: Double,
            severidadeBufferbloat: String,
            stabilityScore: Double,
            contaminado: Boolean,
            duracaoMs: Long?,
        ) {
            firebaseAnalytics.logEvent(
                "speedtest_concluido",
                Bundle().apply {
                    putString("modo", modo)
                    putString("tipo_conexao_inicio", tipoConexaoInicio)
                    tipoConexaoFim?.let { putString("tipo_conexao_fim", it) }
                    putDouble("download_mbps", downloadMbps)
                    putDouble("upload_mbps", uploadMbps)
                    putDouble("latencia_ms", latenciaMs)
                    putDouble("jitter_ms", jitterMs)
                    putDouble("perda_pct", perdaPct)
                    putDouble("bufferbloat_ms", bufferbloatMs)
                    putString("severidade_bufferbloat", severidadeBufferbloat)
                    putDouble("stability_score", stabilityScore)
                    putBoolean("contaminado", contaminado)
                    duracaoMs?.let { putLong("duracao_ms", it) }
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarDiagIniciado(
            tipoConexao: String,
            areasHabilitadas: String?,
            temSpeedtest: Boolean,
        ) {
            firebaseAnalytics.logEvent(
                "diag_iniciado",
                Bundle().apply {
                    putString("tipo_conexao", tipoConexao)
                    areasHabilitadas?.let { putString("areas_habilitadas", it) }
                    putBoolean("tem_speedtest", temSpeedtest)
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarDiagConcluido(
            tipoConexao: String,
            statusGeral: String,
            decisaoId: String,
            scoreConexao: Long,
            confianca: Double,
            nResultadosCriticos: Long?,
            nResultadosAttention: Long?,
        ) {
            firebaseAnalytics.logEvent(
                "diag_concluido",
                Bundle().apply {
                    putString("tipo_conexao", tipoConexao)
                    putString("status_geral", statusGeral)
                    putString("decisao_id", decisaoId)
                    putLong("score_conexao", scoreConexao)
                    putDouble("confianca", confianca)
                    nResultadosCriticos?.let { putLong("n_resultados_criticos", it) }
                    nResultadosAttention?.let { putLong("n_resultados_attention", it) }
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarIaLaudoSolicitado(
            schemaVersion: String,
            promptVersion: String,
            statusDiagLocal: String,
            temFeedbackUsuario: Boolean,
        ) {
            firebaseAnalytics.logEvent(
                "ia_laudo_solicitado",
                Bundle().apply {
                    putString("schema_version", schemaVersion)
                    putString("prompt_version", promptVersion)
                    putString("status_diag_local", statusDiagLocal)
                    putBoolean("tem_feedback_usuario", temFeedbackUsuario)
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarIaLaudoRecebido(
            schemaVersion: String,
            promptVersion: String,
            statusIa: String,
            source: String,
            modeloIa: String?,
            promptTokens: Long?,
            completionTokens: Long?,
            totalTokens: Long?,
            latenciaMs: Long?,
        ) {
            firebaseAnalytics.logEvent(
                "ia_laudo_recebido",
                Bundle().apply {
                    putString("schema_version", schemaVersion)
                    putString("prompt_version", promptVersion)
                    putString("status_ia", statusIa)
                    putString("source", source)
                    modeloIa?.let { putString("modelo_ia", it) }
                    promptTokens?.let { putLong("prompt_tokens", it) }
                    completionTokens?.let { putLong("completion_tokens", it) }
                    totalTokens?.let { putLong("total_tokens", it) }
                    latenciaMs?.let { putLong("latencia_ms", it) }
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarDiagNdsOutcome(
            outcome: String,
            fallbackLocalUsado: Boolean,
            latenciaMs: Long,
            errorCode: String?,
        ) {
            firebaseAnalytics.logEvent(
                "diag_nds_outcome",
                Bundle().apply {
                    putString("outcome", outcome)
                    putBoolean("fallback_local_usado", fallbackLocalUsado)
                    putLong("latencia_ms", latenciaMs)
                    errorCode?.let { putString("error_code", it) }
                    putString("versao_app", appVersion)
                },
            )
        }

        override fun registrarNdsSnapshotEnviado(
            schemaVersion: String,
            blocosPresentes: String,
            qtdBlocosPresentes: Long,
            camposPresentesCount: Long,
            blocosCriticosAusentes: String,
            iaInvocada: Boolean,
            iaProvider: String?,
            duracaoMs: Long,
            resultConfidence: Double?,
            outcome: String,
        ) {
            firebaseAnalytics.logEvent(
                "nds_snapshot_enviado",
                Bundle().apply {
                    putString("schema_version", schemaVersion)
                    putString("blocks_present", blocosPresentes)
                    putLong("qtd_blocks_present", qtdBlocosPresentes)
                    putLong("fields_present_count", camposPresentesCount)
                    putString("missing_critical_blocks", blocosCriticosAusentes)
                    putBoolean("ai_invoked", iaInvocada)
                    iaProvider?.let { putString("ai_provider", it) }
                    putLong("duration_ms", duracaoMs)
                    resultConfidence?.let { putDouble("result_confidence", it) }
                    putString("outcome", outcome)
                    putString("versao_app", appVersion)
                },
            )
        }
    }
