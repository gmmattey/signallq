package io.signallq.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Ponto de extensão de **root content** do [AppShell] (issue #1698, épico #1647).
 *
 * Irmão de [AppShellOverlayRegistry], para o vetor que aquele registro não cobria. A revisão da
 * PR #1697 mediu, linha a linha, de onde vieram as ~226 linhas que as 5 fatias 2.0.04–2.0.08
 * devolveram a `AppShell.kt`: blocos `AnimatedVisibility` de overlay respondem por ~15%; **root
 * content, estado hoisted e lambdas de regra de negócio respondem pelos outros ~85%**. As fatias
 * 2.0.06 (Início) e 2.0.07 (Trilha) não adicionaram overlay nenhum — foi 100% wiring de raiz.
 *
 * ## Como plugar / migrar uma raiz
 *
 * 1. crie `AppShellXxxRoot.kt` com **um** `@Composable internal fun` que recebe só o que aquela
 *    raiz precisa, e — quando forem mais que uns 3 campos — um `@Stable data class` de grupo no
 *    mesmo arquivo (ex.: [AppShellHistoricoState], [AppShellFerramentasAcoes]);
 * 2. mova a construção do grupo para quem já constrói os outros: a `MainActivity` monta
 *    [AppShellSpeedtestState], [AppShellWifiState] etc. desde antes deste épico — grupo novo
 *    nasce lá, não em `AppShell.kt`;
 * 3. mantenha no slot inline apenas as raízes que precisam do estado operacional do shell.
 *
 * **Um parâmetro por raiz, nunca N campos soltos.** Esta é a decisão que a issue #1698 pedia
 * explicitamente antes da primeira migração, herdada da ressalva 3 de Caio na PR #1697:
 * `AppShellOverlayRegistry` chegou a 17 parâmetros e ganhava ~4 por overlay migrado, com 10
 * overlays ainda por migrar — na trajetória atual o registro viraria ele mesmo o próximo ponto de
 * concentração, trocando um monolito por outro. Aqui a assinatura cresce em exatamente +1 por
 * raiz. Agravante que torna a disciplina obrigatória: `detekt` **não** avisa — a regra
 * `LongParameterList` tem threshold 8, mas `build.maxIssues: 2000` no config torna o gate mudo
 * (dívida pré-existente do repositório, não desta issue).
 *
 * ## O que este registro deliberadamente NÃO faz
 *
 * Não decide **qual** raiz está selecionada nem como o back se comporta — isso é
 * `AppShellNavigation.kt` ([AppShellNavigator], [AppShellRoot], pilha por raiz), fonte de verdade
 * única e inalterada por esta issue. Aqui só se decide "qual Composable desenha a raiz X",
 * exatamente como [AppShellOverlayRegistry] faz para overlay. Não existe segundo motor de
 * navegação.
 *
 * Ver `docs/technical/appshell-root-content-registry.md`.
 *
 * @param root raiz selecionada — vem de `navigator.selectedRoot`, que é
 *   [AppShellRoot.fromIndex] sobre `selectedTab`. Índice desconhecido cai em
 *   [AppShellRoot.Tools], mesmo comportamento do `else ->` que o `when` inline tinha em
 *   `AppShell.kt` antes desta extração.
 * @param inlineRootContent slot explícito para as raízes que precisam permanecer inline em
 *   `AppShell.kt` (Início e Velocidade). Uma raiz nova deve ser adicionada ao registro com uma
 *   entrada própria, quando seu estado puder ser isolado.
 */
@Composable
internal fun AppShellRootRegistry(
    root: AppShellRoot,
    historico: AppShellHistoricoRootEntry,
    ferramentas: AppShellFerramentasRootEntry,
    inlineRootContent: @Composable (AppShellRoot) -> Unit,
) {
    when (root) {
        AppShellRoot.History ->
            AppShellHistoricoRoot(
                state = historico.state,
                adsGate = historico.adsGate,
                onAbrirMenu = historico.onAbrirMenu,
                onIniciarTeste = historico.onIniciarTeste,
            )
        AppShellRoot.Tools ->
            AppShellFerramentasRoot(
                acoes = ferramentas.acoes,
                disponibilidade = ferramentas.disponibilidade,
                onAbrirMenu = ferramentas.onAbrirMenu,
                onRegistrarAbertura = ferramentas.onRegistrarAbertura,
            )
        AppShellRoot.Home, AppShellRoot.Speed -> inlineRootContent(root)
    }
}

/**
 * Entrada do registro para a raiz Histórico — o que `AppShell.kt` precisa fornecer para ela.
 *
 * Existe para manter a regra de "+1 parâmetro por raiz": sem isto, [AppShellRootRegistry] receberia
 * `historicoState`, `historicoAdsEnabled`, `historicoOnAbrirMenu` e `historicoOnIniciarTeste`
 * soltos — 4 campos, o padrão que a ressalva 3 de Caio manda evitar.
 */
@Stable
internal data class AppShellHistoricoRootEntry(
    val state: AppShellHistoricoState,
    val adsGate: io.signallq.app.ads.NativeAdsGate,
    val onAbrirMenu: () -> Unit,
    val onIniciarTeste: () -> Unit,
)

/** Entrada do registro para a raiz Ferramentas. Mesmo racional de [AppShellHistoricoRootEntry]. */
@Stable
internal data class AppShellFerramentasRootEntry(
    val acoes: AppShellFerramentasAcoes,
    val disponibilidade: (TipoFerramenta) -> FerramentaDisponibilidade,
    val onAbrirMenu: () -> Unit,
    val onRegistrarAbertura: (TipoFerramenta) -> Unit,
)
