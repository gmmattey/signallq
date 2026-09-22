package io.signallq.app.core.diagnostico

/**
 * Proveniencia de uma metrica usada pelo motor de diagnostico — generaliza para
 * TODAS as dimensoes do [ScoreEngine] o modelo que a Fase 1 introduziu apenas para
 * perda de pacotes ([InternetDiagnosticInput.packetLossSource]: "medida"/"estimated"/
 * "naoMedido"/"unknown", ja consumido pelo `RecomendacaoPraticaEngine.recomendarPerdaDePacotes`,
 * renomeado de `RecommendationEngine` na Fatia 9a da auditoria #1228).
 *
 * Cada metrica bruta que entra no calculo de score carrega uma proveniencia junto do
 * valor:
 * - [medida] — leitura direta de hardware/API (RSSI, RSRP, potencia optica GPON, etc.)
 *   ou calculo estatistico direto sobre uma medicao real (latencia via ping, jitter).
 * - [estimada] — inferida indiretamente, sem medicao direta do fenomeno (ex.: perda de
 *   pacotes por timeout HTTP em vez de sequência real de pacotes — unico caso hoje).
 * - [indisponivel] — dado nao coletado nesta rodada (sem hardware, sem permissao, sem
 *   teste executado). NUNCA vira nota artificial — ver [ScoreEngine.Peso.reponderar].
 */
enum class Provenance {
    medida,
    estimada,
    indisponivel,
}

/**
 * Um valor de metrica (ja convertido para nota 0–100 pelo [ScoreEngine]) junto da sua
 * [Provenance]. [nota] so e significativa quando [provenance] != [Provenance.indisponivel]
 * — dimensoes indisponiveis nunca entram no denominador da media ponderada
 * (ver [ScoreEngine.Peso.reponderar]).
 */
data class EvidenceScore(
    val dimensao: String,
    val nota: Int?,
    val provenance: Provenance,
    /**
     * Confiança amostral — SÓ preenchida pela dimensão `perdaPacotesStatus`
     * (`.agents/architecture-plan.md`, "Confiabilidade estatística do diagnóstico de
     * rede"). `null` para as demais 10 dimensões (RSSI, fibra, velocidade, etc.) — eixo
     * ortogonal a [Provenance], não um substituto genérico. Consumido só por
     * [ScoreEngine.aplicarTetos] para decidir o teto de perda crítica, no lugar do
     * antigo gate `provenance == Provenance.medida` (inatingível via timeout HTTP).
     */
    val confiancaAmostral: ConfiancaAmostral? = null,
)
