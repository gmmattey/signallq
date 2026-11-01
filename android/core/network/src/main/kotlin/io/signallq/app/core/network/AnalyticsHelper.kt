package io.signallq.app.core.network

/**
 * Contrato de instrumentacao do funil principal de engajamento do SignallQ (SIG-155).
 *
 * Implementado por FirebaseAnalyticsHelper (:app) — mesma estrategia de
 * desacoplamento do AnalyticsTracker (SIG-134/feature_used), mas dedicado aos
 * 7 eventos do funil descrito em docs/technical/analytics-events.md:
 *
 * app_aberto -> speedtest_iniciado -> speedtest_concluido -> diag_iniciado
 *   -> diag_concluido -> ia_laudo_solicitado -> ia_laudo_recebido
 *
 * Nao substitui o AnalyticsTracker (feature_used/screen_view/etc — schema SIG-134):
 * sao dois contratos distintos que podem compartilhar a mesma instancia de
 * FirebaseAnalytics internamente sem se misturar na API publica.
 *
 * Sem PII nos parametros — ver docs/technical/analytics-events.md.
 * `versao_app` e anexado automaticamente pela implementacao em todos os eventos
 * (nao faz parte da assinatura dos metodos abaixo).
 */
interface AnalyticsHelper {
    /** Disparado no MainActivity.onCreate. */
    fun registrarAppAberto(
        tipoConexao: String,
        primeiraAbertura: Boolean? = null,
    )

    /** Disparado quando o usuario toca "Iniciar teste" ou o teste silencioso comeca. */
    fun registrarSpeedtestIniciado(
        modo: String,
        tipoConexao: String,
    )

    /** Disparado quando o ResultadoSpeedtest da execucao atual fica disponivel. */
    fun registrarSpeedtestConcluido(
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
        duracaoMs: Long? = null,
    )

    /** Disparado no inicio de DiagnosticOrchestrator.executar(). */
    fun registrarDiagIniciado(
        tipoConexao: String,
        areasHabilitadas: String?,
        temSpeedtest: Boolean,
    )

    /** Disparado quando o DiagnosticOrchestrator conclui com sucesso. */
    fun registrarDiagConcluido(
        tipoConexao: String,
        statusGeral: String,
        decisaoId: String,
        scoreConexao: Long,
        confianca: Double,
        nResultadosCriticos: Long? = null,
        nResultadosAttention: Long? = null,
    )

    /** Disparado quando o app envia o payload ao Worker (AiDiagnosisRepository). */
    fun registrarIaLaudoSolicitado(
        schemaVersion: String,
        promptVersion: String,
        statusDiagLocal: String,
        temFeedbackUsuario: Boolean,
    )

    /** Disparado quando o AiDiagnosisResult (ou fallback local) fica disponivel. */
    fun registrarIaLaudoRecebido(
        schemaVersion: String,
        promptVersion: String,
        statusIa: String,
        source: String,
        modeloIa: String? = null,
        promptTokens: Long? = null,
        completionTokens: Long? = null,
        totalTokens: Long? = null,
        latenciaMs: Long? = null,
    )

    /**
     * NDS-02k (issue #1759, item 10) — disparado uma vez por chamada a
     * `NdsClient.evaluate()` feita por `NdsDiagnosticRepository`, quando a flag
     * `consumer_diagnostico_nds_live_enabled` esta ligada. Mede se o NDS
     * respondeu ou se a rede de seguranca (`DiagnosticRunner` local) precisou
     * assumir — evento operacional de rollout, distinto do funil
     * `diag_iniciado`/`diag_concluido` (SIG-155), que continua disparando
     * normalmente com qualquer fonte (`AnalyticsHelper.registrarDiagConcluido`).
     * Nunca inclui SSID/IP/MAC nem qualquer dado pessoal.
     */
    fun registrarDiagNdsOutcome(
        /** `"success"` | `"remote_inconclusive"` | `"known_error"` | `"unknown_error"`. */
        outcome: String,
        fallbackLocalUsado: Boolean,
        latenciaMs: Long,
        /** Codigo do envelope de erro do NDS quando aplicavel (ex.: `"NDS_TIMEOUT"`,
         *  `"RATE_LIMITED"`) — `null` em sucesso ou quando o shape do erro nao
         *  informa codigo. */
        errorCode: String? = null,
    )

    /**
     * NDS-Snapshot-12 (issue #1844, epico #1832 secao 17 "Observabilidade") — disparado uma vez
     * por chamada a `NdsClient.evaluate()` feita por `NdsDiagnosticRepository`, junto de
     * [registrarDiagNdsOutcome] (mesmo ponto de disparo, evento distinto). Mede a COBERTURA do
     * snapshot enviado — quais blocos do payload NDS (ADR-018) foram montados, quantos campos
     * tem conteudo, e se a IA foi de fato invocada — nao o resultado do diagnostico em si (isso
     * continua sendo `registrarDiagConcluido`, SIG-155).
     *
     * Nunca inclui SSID/BSSID/IP nem qualquer outro valor de campo do snapshot — [blocosPresentes]
     * e [blocosCriticosAusentes] carregam so nomes de bloco (metadado estrutural do payload), na
     * mesma lista fechada de [io.signallq.app.core.nds.NdsSnapshotBlock].
     */
    fun registrarNdsSnapshotEnviado(
        /** Versao do contrato `DiagnosticSnapshot` (ADR-018) —
         *  [io.signallq.app.core.nds.NDS_SNAPSHOT_SCHEMA_VERSION]. */
        schemaVersion: String,
        /** Nomes de bloco (`NdsSnapshotBlock.jsonKey`) presentes no payload, separados por
         *  virgula — mesmo formato de `capacidades`/`qtd_capacidades` ja usado em
         *  `DiagnosticoGuiadoAnalytics`. Vazio quando nenhum bloco opcional foi montado. */
        blocosPresentes: String,
        /** Contagem de blocos presentes — evita o consumidor ter que fazer split() no Firebase
         *  para saber "quantos". */
        qtdBlocosPresentes: Long,
        /** Contagem de campos-folha nao nulos em todo o payload (todos os blocos). */
        camposPresentesCount: Long,
        /** Nomes de bloco criticos ausentes nesta execucao, separados por virgula — string vazia
         *  quando nenhum falta. Criterio de "critico" em
         *  [io.signallq.app.core.nds.NdsSnapshotCoverage.missingCriticalBlocks]. */
        blocosCriticosAusentes: String,
        /** `true` quando o NDS retornou um resultado do modulo `"ai"` (contrato v1) ou uma
         *  explicacao v2 — `false` em qualquer erro antes da resposta, ou quando o NDS decidiu
         *  nao invocar IA. */
        iaInvocada: Boolean,
        /** Modelo/provedor de IA usado, quando informado pelo NDS (`NdsAiResult.aiModelUsed`) —
         *  `null` quando [iaInvocada] e falso ou o contrato nao informa (v2). */
        iaProvider: String? = null,
        duracaoMs: Long,
        /** [io.signallq.app.core.diagnostico.DiagnosticReport.confianca] (0.0-1.0) — `null`
         *  quando nao houve relatorio (erro sem fallback local, caminho do Assist). */
        resultConfidence: Double? = null,
        /** Mesmo vocabulario de `registrarDiagNdsOutcome.outcome` — permite correlacionar os
         *  dois eventos sem duplicar a logica de decisao de outcome. */
        outcome: String,
    )
}

/**
 * Implementacao no-op usada como default em pontos de instanciacao manual
 * (fora do grafo Hilt) — evita quebrar testes/previews que nao precisam
 * verificar analytics. O grafo Hilt sempre injeta FirebaseAnalyticsHelper.
 */
object NoOpAnalyticsHelper : AnalyticsHelper {
    override fun registrarAppAberto(
        tipoConexao: String,
        primeiraAbertura: Boolean?,
    ) = Unit

    override fun registrarSpeedtestIniciado(
        modo: String,
        tipoConexao: String,
    ) = Unit

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
    ) = Unit

    override fun registrarDiagIniciado(
        tipoConexao: String,
        areasHabilitadas: String?,
        temSpeedtest: Boolean,
    ) = Unit

    override fun registrarDiagConcluido(
        tipoConexao: String,
        statusGeral: String,
        decisaoId: String,
        scoreConexao: Long,
        confianca: Double,
        nResultadosCriticos: Long?,
        nResultadosAttention: Long?,
    ) = Unit

    override fun registrarIaLaudoSolicitado(
        schemaVersion: String,
        promptVersion: String,
        statusDiagLocal: String,
        temFeedbackUsuario: Boolean,
    ) = Unit

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
    ) = Unit

    override fun registrarDiagNdsOutcome(
        outcome: String,
        fallbackLocalUsado: Boolean,
        latenciaMs: Long,
        errorCode: String?,
    ) = Unit

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
    ) = Unit
}
