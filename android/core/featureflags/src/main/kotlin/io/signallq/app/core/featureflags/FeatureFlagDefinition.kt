package io.signallq.app.core.featureflags

/**
 * Entrada tipada do catalogo canonico -- espelha 1:1 o schema JSON do Epico #1347
 * (`docs/CONTRATOS/schemas/README.md` referencia o arquivo de origem
 * `core/featureflags/src/main/resources/featureflags/consumer-catalog.json`).
 *
 * Este e o contrato que o SignallQ Admin (F3, #1479) e o Worker (F2, #1478) devem
 * consumir para exibir/publicar flags -- qualquer campo novo no schema JSON precisa
 * de um campo correspondente aqui, nunca lido "solto" via JSON dinamico fora deste
 * tipo.
 */
data class FeatureFlagDefinition(
    val key: FeatureFlagKey,
    val module: String,
    val type: FeatureFlagType,
    val defaultValue: FeatureFlagRawValue,
    val criticality: FeatureFlagCriticality,
    val owner: String,
    val description: String,
    val disabledBehavior: FeatureFlagDisabledBehavior,
    val disabledMessage: String?,
    val dependencies: List<FeatureFlagKey>,
    val androidImplemented: Boolean,
    val adminManaged: Boolean,
    val analyticsEvent: String?,
)
