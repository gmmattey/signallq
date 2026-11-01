package io.signallq.app.ui.screen

import io.signallq.app.core.diagnostico.ObjetivoDiagnostico

/**
 * Identifica cada ferramenta do hub ([FerramentasScreen]) de forma estável — chave
 * compartilhada com o card contextual do diagnóstico guiado (Camada A, issue #1503),
 * sem duplicar decisão de produto em dois lugares.
 */
enum class TipoFerramenta {
    SINAL_CANAIS_MOVEL,
    DISPOSITIVOS,
    EQUIPAMENTO_INTERNET,
    PING,
    DNS,
    LAUDO,
    MONITORAMENTO,
    MODO_JOGOS,
    SINAL_WIFI,
}

internal fun TipoFerramenta.screenName(): String =
    when (this) {
        TipoFerramenta.SINAL_CANAIS_MOVEL, TipoFerramenta.SINAL_WIFI -> "sinal_wifi"
        TipoFerramenta.DISPOSITIVOS -> "dispositivos"
        TipoFerramenta.EQUIPAMENTO_INTERNET -> "equipamento_internet"
        TipoFerramenta.PING -> "ping"
        TipoFerramenta.DNS -> "dns"
        TipoFerramenta.LAUDO -> "laudo"
        TipoFerramenta.MONITORAMENTO -> "monitoramento"
        TipoFerramenta.MODO_JOGOS -> "modo_gamer"
    }

/**
 * Curadoria das seções do hub Ferramentas (Camada B, issue #1503) — puramente sobre
 * quais ferramentas existem e sua ordem, sem ícone/texto (isso continua em
 * [FerramentasScreen], que já tem o catálogo visual completo).
 */
object CatalogoFerramentas {
    // Priorização de produto sem dado de analytics ainda (revisitar quando houver
    // medição real de uso — ver corpo da issue #1503). Ordem importa: DNS, Modo Jogos,
    // Monitoramento, nesta sequência.
    val maisUsadas: List<TipoFerramenta> =
        listOf(TipoFerramenta.DNS, TipoFerramenta.MODO_JOGOS, TipoFerramenta.MONITORAMENTO)

    /** Grupos organizados por finalidade (SUA REDE, TESTES, ACOMPANHAR). */
    val grupos: Map<String, List<TipoFerramenta>> =
        mapOf(
            "SUA REDE" to
                listOf(
                    TipoFerramenta.SINAL_CANAIS_MOVEL,
                    TipoFerramenta.SINAL_WIFI,
                    TipoFerramenta.DISPOSITIVOS,
                    TipoFerramenta.EQUIPAMENTO_INTERNET,
                ),
            "TESTES" to
                listOf(
                    TipoFerramenta.PING,
                    TipoFerramenta.DNS,
                    TipoFerramenta.MODO_JOGOS,
                ),
            "ACOMPANHAR" to
                listOf(
                    TipoFerramenta.MONITORAMENTO,
                    TipoFerramenta.LAUDO,
                ),
        )

    private val ordemPadrao: List<TipoFerramenta> = grupos.values.flatten()

    /** Lista aberta 2.0: fonte canônica única dos nove destinos. */
    val todos: List<TipoFerramenta> = ordemPadrao

    /** Seção "Todas as ferramentas" — o restante, sem duplicar o que já está em [maisUsadas]. */
    val restante: List<TipoFerramenta> = ordemPadrao.filterNot { it in maisUsadas }
}

/**
 * Camada A (issue #1503) — mapeia o objetivo escolhido no diagnóstico guiado para a
 * única ferramenta sugerida como próximo passo. `null` quando não há
 * mapeamento forte o bastante — regra de produto explícita: nunca empurrar sugestão
 * fraca só pra preencher a lista (ver corpo da issue #1503).
 */
fun ObjetivoDiagnostico.ferramentaSugerida(): TipoFerramenta? =
    when (this) {
        ObjetivoDiagnostico.LENTIDAO_GERAL -> TipoFerramenta.DNS
        ObjetivoDiagnostico.INSTABILIDADE_QUEDAS -> TipoFerramenta.MONITORAMENTO
        ObjetivoDiagnostico.PROBLEMAS_VIDEO_JOGOS -> TipoFerramenta.MODO_JOGOS
        // Sem categoria conhecida — nenhuma ferramenta específica é forte o bastante.
        ObjetivoDiagnostico.OUTRO_PROBLEMA,
        -> null
    }
