package io.signallq.app.ui.screen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import io.signallq.app.core.diagnostico.ObjetivoDiagnostico

enum class EntradaAssist { Padrao, ComDadosRecentes, VideoOuChamada }

enum class TipoMidiaAssist { VIDEO, CHAMADA }

sealed interface EstadoChamadaNds {
    data object EmCurso : EstadoChamadaNds

    data class Sucesso(
        val relatorio: io.signallq.app.core.diagnostico.DiagnosticReport,
    ) : EstadoChamadaNds

    data object Falhou : EstadoChamadaNds
}

// Estado do fluxo guiado 2.0 — issue #1704 (2.0.09b), épico #1647.
//
// Substitui os quatro `remember` independentes que a PR #1708 tentou persistir e foi reprovada por
// isso (bloqueio B3 do parecer de Caio): com quatro slots `rememberSaveable` separados, o
// `SaverObjetivoDiagnostico` podia devolver `null` para nome de enum desconhecido — de propósito,
// para sobreviver à evolução do enum — enquanto passo, respostas e a flag de resultado restauravam
// intactos. O invariante quebrava e produzia dois desfechos reais: `perguntas[passo]` estourando,
// ou o usuário caindo direto no resultado com respostas de OUTRA jornada, sem nada denunciando.
//
// A correção prescrita foi um saver sobre UM objeto, não sobre quatro campos. Aqui o estado é
// indivisível por construção: não existe combinação inválida a restaurar porque não existem slots
// separados, e [DiagnosticoGuiadoEstado.coerente] é a rede que trava o resto.

/**
 * As seis rotas do fluxo guiado — **seis, não sete**.
 *
 * `insufficient` e `recoverable-error`, que a issue #1657 listava como rotas, são **variações
 * dentro das telas**, não destinos: a spec §9 os classifica como estados globais (e sobre "erro
 * transitório" diz "preserva contexto", que é o oposto de navegar), o `COVERAGE.md` do protótipo
 * afirma que não devem virar destinos permanentes, e há um argumento que independe dos dois
 * documentos — se erro é destino, o back a partir do erro volta para a coisa que acabou de falhar
 * e a redispara, virando laço.
 *
 * Estas rotas **não** entram em `AppShellOverlay`. A pilha de overlays é set-like (`open()` ignora
 * duplicata) e este fluxo é cíclico — `Resultado → Orientacao → Reteste → Comparacao → Orientacao`
 * de novo. Além disso overlays acumulam em vez de substituir. Ver
 * `docs/technical/appshell-overlay-registry.md`, seção "Delegação de back".
 */
enum class DiagnosticoGuiadoRota {
    /** Chamada direta ao NDS em curso, ou erro explícito de acesso ao serviço. */
    Processando,

    /** Plano montado e medição em andamento. */
    Analise,

    /** Conclusão compreensível, causa provável, confiança e limite. */
    Resultado,

    /** Uma ação principal com justificativa, mais alternativas secundárias. */
    Orientacao,

    /** Novo teste **vinculado** à análise anterior — não é recomeçar do zero. */
    Reteste,

    /** Veredito melhorou / não mudou / comparação inconclusiva. */
    Comparacao,
}

/**
 * Estado completo do fluxo, restaurado como **unidade atômica**.
 *
 * [pilha] é a back-stack interna: o topo é a rota corrente, e recuar é remover o último. Cíclica
 * por natureza, então admite repetição — diferente da pilha de overlays do shell.
 */
@Immutable
data class DiagnosticoGuiadoEstado(
    val entrada: EntradaAssist = EntradaAssist.Padrao,
    val tipoMidia: TipoMidiaAssist? = null,
    val objetivo: ObjetivoDiagnostico? = null,
    val pilha: List<DiagnosticoGuiadoRota> = emptyList(),
    /**
     * Texto livre digitado quando [objetivo] é [ObjetivoDiagnostico.OUTRO_PROBLEMA].
     */
    val relatoLivre: String? = null,
) {
    /** Rota corrente, ou `null` quando o usuário ainda está escolhendo o objetivo. */
    val rotaAtual: DiagnosticoGuiadoRota? get() = pilha.lastOrNull()

    /**
     * Estado coerente é aquele sem lixo de transições pela metade.
     */
    val coerente: Boolean
        get() =
            if (objetivo == null) {
                pilha.isEmpty() && relatoLivre == null
            } else {
                true
            }

    fun saneado(): DiagnosticoGuiadoEstado = if (coerente) this else DiagnosticoGuiadoEstado()

    fun empilhar(rota: DiagnosticoGuiadoRota): DiagnosticoGuiadoEstado = copy(pilha = pilha + rota)

    fun irPara(rota: DiagnosticoGuiadoRota): DiagnosticoGuiadoEstado =
        copy(pilha = if (pilha.isEmpty()) pilha + rota else pilha.dropLast(1) + rota)

    fun recuar(): DiagnosticoGuiadoEstado? =
        when {
            pilha.isNotEmpty() -> copy(pilha = pilha.dropLast(1))
            objetivo != null -> DiagnosticoGuiadoEstado()
            else -> null
        }

    companion object {
        private const val SEM_OBJETIVO = ""

        val Saver: Saver<DiagnosticoGuiadoEstado, List<String>> =
            Saver(
                save = { estado ->
                    listOf(
                        estado.entrada.name,
                        estado.tipoMidia?.name.orEmpty(),
                        estado.objetivo?.name ?: SEM_OBJETIVO,
                        estado.pilha.joinToString(",") { it.name },
                        estado.relatoLivre.orEmpty(),
                    )
                },
                restore = { valores ->
                    val nomeEntrada = valores.getOrNull(0).orEmpty()
                    val nomeMidia = valores.getOrNull(1).orEmpty()
                    val nomeObjetivo = valores.getOrNull(2).orEmpty()

                    val tokensPilha =
                        valores
                            .getOrNull(3)
                            .orEmpty()
                            .split(',')
                            .filter(String::isNotBlank)

                    val pilhaRestaurada =
                        tokensPilha.map { nome ->
                            DiagnosticoGuiadoRota.entries.firstOrNull { it.name == nome }
                        }

                    if (pilhaRestaurada.any { it == null }) {
                        DiagnosticoGuiadoEstado()
                    } else {
                        DiagnosticoGuiadoEstado(
                            objetivo =
                                if (nomeObjetivo == SEM_OBJETIVO) {
                                    null
                                } else {
                                    ObjetivoDiagnostico.entries.firstOrNull { it.name == nomeObjetivo }
                                },
                            entrada = EntradaAssist.entries.firstOrNull { it.name == nomeEntrada } ?: EntradaAssist.Padrao,
                            tipoMidia = TipoMidiaAssist.entries.firstOrNull { it.name == nomeMidia },
                            pilha = pilhaRestaurada.filterNotNull(),
                            relatoLivre = valores.getOrNull(4)?.takeIf { it.isNotEmpty() },
                        ).saneado()
                    }
                },
            )
    }
}
