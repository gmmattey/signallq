package io.signallq.app.feature.speedtest

data class DiagnosticoFasesSpeedtest(
    val faseInterrompida: String,
    val latenciaAmostrasTotais: Int,
    val latenciaAmostrasValidas: Int,
    val latenciaTimeouts: Int,
    /** p95/max/picos calculados por `AnalisadorAmostragemPing` sobre TODAS as amostras
     *  válidas de latência, antes do filtro de outlier — já existiam no motor (GH#1211
     *  item 3), só não eram propagados até aqui (.agents/architecture-plan.md, seção 2/8). */
    val latenciaP95Ms: Double = 0.0,
    val latenciaMaxMs: Double = 0.0,
    val latenciaPicos: Int = 0,
    /** `true` quando a janela de confirmação de +20 probes (seção 7 do plano) rodou —
     *  informativo, não persiste sozinho como coluna do histórico (Camillo: só
     *  `perdaConfianca` em `ResultadoSpeedtest` precisa sobreviver até `MedicaoEntity`). */
    val latenciaConfirmacaoExecutada: Boolean = false,
    val downloadBytesTotal: Long,
    val downloadAmostrasValidas: Int,
    val downloadRequisicoesSucesso: Int,
    val downloadRequisicoesErro: Int,
    val downloadEncerradaPor: String,
    val downloadThroughputOrigem: String,
    val downloadUltimoErro: String?,
    val uploadBytesTotal: Long,
    val uploadAmostrasValidas: Int,
    val uploadRequisicoesSucesso: Int,
    val uploadRequisicoesErro: Int,
    val uploadEncerradaPor: String,
    val uploadThroughputOrigem: String,
    val uploadUltimoErro: String?,
    val dnsErroMensagem: String?,
)
