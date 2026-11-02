package io.signallq.app.assist.model

data class AssistContextoRede(
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latenciaMs: Long? = null,
    val jitterMs: Long? = null,
    val perdaPct: Double? = null,
    val tipoRede: String? = null,
    val sinalDbm: Int? = null,
    val operadoraOuIsp: String? = null,
    val statusRede: String? = null,
    val semMedicao: Boolean = false,
    /** Quando a medição vem do histórico salvo (não do teste da sessão atual), o instante em que foi feita. */
    val medidoEmEpochMs: Long? = null,
)
