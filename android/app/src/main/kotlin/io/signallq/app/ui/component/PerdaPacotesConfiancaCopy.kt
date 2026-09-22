package io.signallq.app.ui.component

import io.signallq.app.core.diagnostico.ConfiancaAmostral

/**
 * Copy da confiança amostral de perda de pacotes exibida na UI — Cora,
 * `.agents/architecture-plan.md` ("Confiabilidade estatística do diagnóstico de rede", passo 6).
 *
 * Reaproveita o mesmo padrão visual já existente do sufixo "(estimada)" (metodologia — timeout
 * HTTP em vez de captura real de pacote): este badge é um eixo ORTOGONAL, sobre CONFIANÇA da
 * amostra, não sobre a metodologia de medição.
 *
 * - [ConfiancaAmostral.INSUFICIENTE] com perda > 0 -> retorna o texto do badge; o valor numérico
 *   em si nunca é escondido pelo chamador, só não é destacado como diagnóstico definitivo.
 * - [ConfiancaAmostral.SUFICIENTE] -> `null` (perda confirmada/recorrente, comportamento normal:
 *   mostra percentual e classificação como sempre).
 * - `null` (dado legado, sem essa informação) -> `null` — comportamento idêntico ao anterior a
 *   esta mudança, nunca infere confiança de dado antigo.
 */
internal fun copyInstabilidadePerdaPacotes(
    perdaPercentual: Double?,
    perdaConfianca: ConfiancaAmostral?,
): String? {
    if (perdaConfianca != ConfiancaAmostral.INSUFICIENTE) return null
    if (perdaPercentual == null || perdaPercentual <= 0.0) return null
    return "Pequena instabilidade detectada durante o teste"
}
