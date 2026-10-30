package io.signallq.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.ObjetivoDiagnostico
import io.signallq.app.core.network.DiagnosticoPlanoIniciado
import io.signallq.app.core.recommendation.RecommendationDecision
import io.signallq.app.core.recommendation.RecommendationFeedbackType
import io.signallq.app.feature.speedtest.ResultadoSpeedtest

// Overlay do diagnóstico guiado — issue #1704 (2.0.09b), épico #1647.
//
// Migra o bloco `AnimatedVisibility` que vivia inline em `AppShell.kt` (linhas 999-1041, 24
// parâmetros de tela). É o PRIMEIRO overlay migrado depois da #1698, e por isso estreia no
// registro o padrão que a ressalva 3 de Caio (PR #1697) tornou obrigatório: **um `@Stable data
// class` de entrada, não N campos soltos**.
//
// Sem isso, `AppShellOverlayRegistry` sairia de 17 para ~35 parâmetros só com esta migração — o
// registro viraria o próximo monolito, que é exatamente o que a decisão da #1698 evita. Ver
// `docs/technical/appshell-root-content-registry.md`, seção "um parâmetro por raiz".

/**
 * Tudo que o overlay do diagnóstico guiado consome do shell, em três grupos coesos.
 *
 * A divisão não é estética: cada grupo tem um dono diferente e muda por motivos diferentes —
 * [dados] vem dos snapshots, [operadora] é a cadeia de resolução injetada da `MainActivity`
 * (GH#970), e [acoes] é navegação/estado do shell. Uma fatia futura que mexa só na resolução de
 * operadora toca um campo, não a assinatura inteira.
 */
@Stable
internal data class AppShellDiagnosticoGuiadoEntry(
    val dados: AppShellDiagnosticoGuiadoDados,
    val operadora: AppShellOperadoraResolvers,
    val acoes: AppShellDiagnosticoGuiadoAcoes,
    /** Rota `Analise` — GH#1704 parte 4/4. Quarto grupo, e não campos dentro de [dados], porque
     *  tem dono diferente dos outros três: sai do `ExecutorSpeedtest`, não dos snapshots. */
    val analise: AnaliseGuiadaContrato,
)

/** Dados de diagnóstico e contexto de rede que a tela exibe. */
@Stable
internal data class AppShellDiagnosticoGuiadoDados(
    val input: DiagnosticInput?,
    val diagnosticReport: DiagnosticReport? = null,
    val resultado: ResultadoSpeedtest?,
    val analisadorState: AnalisadorState,
    val objetivoPreSelecionado: ObjetivoDiagnostico?,
    val respostaPreSelecionadaPasso0: Int?,
    val entradaAssist: EntradaAssist = EntradaAssist.Padrao,
    val tipoMidiaAssist: TipoMidiaAssist? = null,
    val categoria: String?,
    val ispNome: String?,
    val operadoraMovel: String?,
    val recommendationDecision: RecommendationDecision?,
    val recommendationFeedback: RecommendationFeedbackType?,
    /** GH#1706 — contexto que adapta o plano de análise (spec §7 e §8.4). O registry já tinha estes
     *  sinais em mãos para o Sinal Wi-Fi e simplesmente não os repassava ao fluxo guiado. */
    val contextoDoPlano: ContextoDoPlano,
    /** GH#1707 (Task 2.0.09e, parte 2/2) — estado do reteste vinculado em curso (spec §14.6). */
    val comparacaoRetesteState: ComparacaoRetesteUiState = ComparacaoRetesteUiState.Ausente,
)

/** Navegação e efeitos colaterais que só o shell sabe executar. */
@Stable
internal data class AppShellDiagnosticoGuiadoAcoes(
    val onAvaliarAssist: suspend (DiagnosticInput) -> DiagnosticReport,
    val onAnalisarProblema: (String?) -> Unit,
    val onResetarAnalisador: () -> Unit,
    val onVoltar: () -> Unit,
    val onAbrirPerfil: () -> Unit = {},
    val onAlternarTema: () -> Unit = {},
    val onIrParaHome: () -> Unit,
    val onIniciarModoGamer: () -> Unit,
    val onAbrirFerramentaSugerida: (TipoFerramenta) -> Unit,
    /** GH#1706 — funil: o plano foi montado e exibido (spec §12, passo 3). */
    val onPlanoIniciado: (DiagnosticoPlanoIniciado) -> Unit,
    /** GH#1707 (Task 2.0.09e, parte 2/2) — CTA "Testar novamente" vinculado à análise original
     *  (spec §8.8). Ver KDoc do parâmetro homônimo em [DiagnosticoGuiadoScreen]. */
    val onTestarNovamenteVinculado: (analiseId: String, acaoAnteriorId: String) -> Unit = { _, _ -> },
    val onRecommendationShown: () -> Unit,
    val onRecommendationClicked: () -> Unit,
    val onRecommendationFeedback: (RecommendationFeedbackType) -> Unit,
)

/**
 * O overlay em si. Comportamento idêntico ao bloco inline que substitui, incluindo a guarda dupla:
 * o container só compõe quando o overlay está na pilha **e** há resultado de speedtest.
 *
 * O `testTag` no container existe pelo mesmo motivo do `AppShellDetalhesTecnicosOverlay`: sem ele,
 * um teste não distingue "container não compôs" (guarda presente) de "container compôs vazio"
 * (guarda removida) — o `?.let` interno omite o conteúdo sozinho nos dois casos. Achado da
 * revisão de Caio na PR #1697, aplicado aqui de saída em vez de depois.
 */
@Composable
internal fun AppShellDiagnosticoGuiadoOverlay(
    navigator: AppShellNavigator,
    entry: AppShellDiagnosticoGuiadoEntry,
) {
    val overlayStack = navigator.overlayStack
    val dados = entry.dados

    // issue #1720 — ponte entre o `RegistrarBackDoOverlay` (que precisa viver FORA do
    // `AnimatedVisibility`, ver KDoc dele em `AppShellNavigation.kt`) e o comportamento de saída
    // de `DiagnosticoGuiadoScreen` (que só existe DENTRO do conteúdo). `backHandler` é preenchido
    // a cada composição via `onBackHandlerReady` e consultado por
    // `consumirBackDoOverlayTopo()` através da lambda abaixo.
    var backHandler by remember { mutableStateOf<() -> Boolean>({ false }) }
    RegistrarBackDoOverlay(navigator, AppShellOverlay.DiagnosticoGuiado) { backHandler() }

    AnimatedVisibility(
        visible = AppShellOverlay.DiagnosticoGuiado in overlayStack,
        modifier =
            Modifier
                .zIndex(rememberOverlayZIndex(AppShellOverlay.DiagnosticoGuiado, overlayStack))
                .testTag(TAG_OVERLAY_DIAGNOSTICO_GUIADO),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        // GH#1704 parte 4/4 — o estado vazio da #1714 saiu daqui. Ele existia porque o fluxo não
        // conseguia começar sem um resultado anterior; agora começa, e a rota `Analise` produz o
        // resultado que falta. `ResultadoIndisponivelScreen` continua em uso pelos outros dois
        // overlays de resultado, que consomem o `ResultadoSpeedtest` inteiro e não têm como
        // regenerá-lo.
        val resultado = dados.resultado
        DiagnosticoGuiadoScreen(
            input = dados.input,
            diagnosticReport = dados.diagnosticReport,
            contextoDoPlano = dados.contextoDoPlano,
            onPlanoIniciado = entry.acoes.onPlanoIniciado,
            statusMedicao = resultado?.status,
            entradaAssist = dados.entradaAssist,
            tipoMidiaAssist = dados.tipoMidiaAssist,
            onAvaliarAssist = entry.acoes.onAvaliarAssist,
            medidasConfiaveis = medidasConfiaveis(resultado),
            analise = entry.analise,
            onBackHandlerReady = { backHandler = it },
            objetivoPreSelecionado = dados.objetivoPreSelecionado,
            analisadorState = dados.analisadorState,
            onAnalisarProblema = entry.acoes.onAnalisarProblema,
            onResetarAnalisador = entry.acoes.onResetarAnalisador,
            onVoltar = entry.acoes.onVoltar,
            onAbrirPerfil = entry.acoes.onAbrirPerfil,
            onAlternarTema = entry.acoes.onAlternarTema,
            onIrParaHome = entry.acoes.onIrParaHome,
            categoria = dados.categoria,
            ispNome = dados.ispNome,
            connectionType = resultado?.connectionType,
            operadoraMovel = dados.operadoraMovel,
            recommendationDecision = dados.recommendationDecision,
            recommendationFeedback = dados.recommendationFeedback,
            onRecommendationShown = entry.acoes.onRecommendationShown,
            onRecommendationClicked = entry.acoes.onRecommendationClicked,
            onRecommendationFeedback = entry.acoes.onRecommendationFeedback,
            resolveOperadoraIdentidadeLocal = entry.operadora.identidadeLocal,
            resolveOperadoraContatoLocal = entry.operadora.contatoLocal,
            resolveOperadoraIdentidadeRemota = entry.operadora.identidadeRemota,
            resolveOperadoraContatoRemoto = entry.operadora.contatoRemoto,
            onIniciarModoGamer = entry.acoes.onIniciarModoGamer,
            onAbrirFerramentaSugerida = entry.acoes.onAbrirFerramentaSugerida,
            onTestarNovamenteVinculado = entry.acoes.onTestarNovamenteVinculado,
            comparacaoRetesteState = dados.comparacaoRetesteState,
        )
    }
}

/** Marcador que `calcularMeasurementStatus` usa para o 429 — string literal do executor. */
private const val DOWNLOAD_BLOQUEADO_429 = "download_bloqueado_429"

/**
 * As medidas deste resultado podem alimentar conclusão? — GH#1705, bloqueios B3, B7 e B8.
 *
 * As **duas** causas de `MeasurementStatus.PARTIAL`, que o enum achata num valor só:
 *
 * - `downloadEncerradaPor == "download_bloqueado_429"` — o nosso rate limit derrubou a fase de
 *   download e o número ficou artificialmente baixo;
 * - `uploadNaoDetectado` — a nossa medição de upload desistiu depois do retry, e o `MainViewModel`
 *   passa `0.0` cru para o motor.
 *
 * Nos dois casos o número que sobra não descreve a conexão da pessoa, e o motor concluiria defeito
 * dela a partir dele — mandando acionar a operadora, ou afirmando que "o upload está comprometido".
 *
 * Função separada porque a derivação inline não tinha teste: trocá-la por `true` fixo passava na
 * suíte inteira do `:app` (bloqueio B8). O call site também está coberto desde então, por
 * `overlay repassa medidas nao confiaveis e a conclusao parcial nao aparece` — este comentário
 * dizia o contrário e ficou desatualizado no mesmo commit que o cobriu (ressalva RS13).
 */
internal fun medidasConfiaveis(resultado: ResultadoSpeedtest?): Boolean =
    resultado == null ||
        (
            resultado.diagnosticoFases.downloadEncerradaPor != DOWNLOAD_BLOQUEADO_429 &&
                !resultado.uploadNaoDetectado
        )

internal const val TAG_OVERLAY_DIAGNOSTICO_GUIADO = "appshell_overlay_diagnostico_guiado"
