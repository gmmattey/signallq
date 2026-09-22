package io.signallq.app

import io.signallq.app.core.diagnostico.ConfiancaAmostral

/**
 * Serialização/desserialização de [ConfiancaAmostral] para a coluna `perdaConfianca` de
 * `MedicaoEntity` (`:core:database`) — `.agents/architecture-plan.md` ("Confiabilidade
 * estatística do diagnóstico de rede", seção 8/10 passo 5).
 *
 * `:core:database` não depende de `:core:diagnostico` (direção de dependência preservada — ver
 * kdoc de [MedicaoEntity.perdaConfianca]), então a coluna é `String?` livre (nome do enum) e a
 * conversão de/para o tipo real vive aqui, no único módulo (`:app`) que enxerga os dois lados.
 */
internal fun ConfiancaAmostral.paraColunaPersistencia(): String = name

/**
 * `null` ou valor desconhecido (nunca deveria acontecer em produção, mas não pode quebrar a
 * leitura de uma linha antiga/corrompida) vira `null` — tratado como
 * [ConfiancaAmostral.INSUFICIENTE] pelos classificadores que consomem
 * [io.signallq.app.core.diagnostico.InternetDiagnosticInput.perdaConfianca], nunca inferido
 * aqui.
 */
internal fun String?.paraConfiancaAmostral(): ConfiancaAmostral? =
    this?.let { valor -> runCatching { ConfiancaAmostral.valueOf(valor) }.getOrNull() }
