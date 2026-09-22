package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.ConfiancaAmostral

data class ResultadoSpeedtest(
    val timestampEpochMs: Long,
    val specVersion: String,
    val modo: ModoSpeedtest,
    val connectionTypeStart: String?,
    val connectionTypeEnd: String?,
    val contaminado: Boolean,
    val latenciaMs: Double,
    val jitterMs: Double,
    val perdaPercentual: Double,
    val bufferbloatMs: Double,
    val severidadeBufferbloat: SeveridadeBufferbloat,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latencyDownloadMs: Double,
    val latencyUploadMs: Double,
    val stabilityScore: Double,
    val peakDownloadMbps: Double,
    val peakUploadMbps: Double,
    val packetLossSource: String,
    val dnsLatencyMs: Int?,
    val dnsResolverIp: String?,
    val dnsProvider: String?,
    val diagnosticoQualidade: DiagnosticoQualidadeSpeedtest,
    val diagnosticoFases: DiagnosticoFasesSpeedtest,
    val uploadNaoDetectado: Boolean = false,
    val connectionType: String? = null,
    val tecnologia: String? = null,
    /** GH#1221/#1225 — identificador unico desta execucao (UUID), gerado no inicio de
     *  [io.signallq.app.feature.speedtest.ExecutorSpeedtestCloudflare.executar]. E o elo
     *  que Resultado/Diagnostico/IA/Recomendacao/PDF usam para confirmar que pertencem
     *  a MESMA execucao — respostas assincronas com [executionId] diferente do resultado
     *  atualmente exibido devem ser descartadas pelo consumidor. */
    val executionId: String = "",
    /** GH#1221/#1225 — integridade desta execucao (ver [MeasurementStatus]). Unica fonte
     *  de verdade sobre "posso tratar isto como medicao valida" — substitui a checagem
     *  isolada de [contaminado]/[uploadNaoDetectado] pelos consumidores. */
    val status: MeasurementStatus = MeasurementStatus.COMPLETE,
    /**
     * Confiança amostral de [perdaPercentual] — eixo ortogonal a [packetLossSource]
     * (.agents/architecture-plan.md, "Confiabilidade estatística do diagnóstico de
     * rede"). `null` = não calculada (execução antiga/histórico legado antes desta
     * mudança) — consumidores devem tratar `null` como
     * [ConfiancaAmostral.INSUFICIENTE] só na exibição/reclassificação, nunca
     * recalcular o passado. [perdaPercentual] em si NUNCA é forçado a 0 por causa
     * disto — é sempre o valor real medido.
     */
    val perdaConfianca: ConfiancaAmostral? = null,
)
