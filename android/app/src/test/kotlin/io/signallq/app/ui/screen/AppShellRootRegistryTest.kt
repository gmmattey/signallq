package io.signallq.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.signallq.app.ads.NativeAdsGate
import io.signallq.app.ui.FiltroConexaoHistorico
import io.signallq.app.ui.SignallQTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes de caracterização do ponto de extensão de **root content** criado pela issue #1698
 * (épico #1647), irmão de `AppShellOverlayRegistryTest`.
 *
 * Mesma estrutura de duas camadas deliberadamente redundantes que a revisão de Caio exigiu na
 * PR #1697:
 * - **por raiz** (`AppShellXxxRoot` chamado direto) — cobre que a raiz repassa corretamente o que
 *   recebe (estado, callbacks, regra de disponibilidade);
 * - **por registro** (`AppShellRootRegistry` chamado inteiro) — cobre que a entrada daquela raiz
 *   realmente existe dentro do agregador. Sem esta camada, apagar a linha
 *   `AppShellRoot.History -> AppShellHistoricoRoot(...)` de dentro do registro deixaria a suíte
 *   verde e a tela sumiria do app — foi exatamente o achado que reprovou a primeira rodada da
 *   PR #1697 (4 de 7 entradas do registro de overlay sem cobertura efetiva).
 *
 * A terceira camada é específica desta issue e não existia no registro de overlays: o slot
 * [AppShellRootRegistry] `inlineRootContent`. Ele precisa ser chamado para as 2 raízes ainda
 * inline em `AppShell.kt` e **nunca** para as 2 registradas — um `when` que caísse no slot por engano
 * renderizaria a tela antiga silenciosamente.
 *
 * ## Campo sabidamente NÃO coberto: `AppShellHistoricoRootEntry.adsEnabled`
 *
 * Declarado aqui em vez de deixar a suíte parecer completa (achado R2 da revisão de Caio na
 * PR #1702). Trocar `adsEnabled` por `false` fixo no registro **sobrevive** a esta suíte, e não é
 * descuido: é limite do ambiente. `rememberNativeAd` (`NativeAdLoader.kt`) só devolve anúncio não
 * nulo em `NativeAdLoadState.Fill`, que exige resposta real do AdMob; e `NativeAdCard` faz
 * `if (nativeAd == null) return` no primeiro statement. Sob Robolectric, sem SDK nem rede, não há
 * fill — então `eligible = true` e `eligible = false` renderizam exatamente a mesma árvore
 * (nenhum nó). Não existe asserção de UI capaz de distinguir os dois.
 *
 * Cobrir isso de verdade exigiria injetar um `NativeAdRequester` fake até a raiz — mudança no
 * contrato de `AppShellHistoricoRoot` só para viabilizar teste, o que é decisão de arquitetura de
 * ads (issues #1330/#1694), não desta fatia. Enquanto não houver, o campo depende de revisão
 * humana do diff.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellRootRegistryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun historicoEntry(
        state: AppShellHistoricoState = AppShellHistoricoState(),
        onIniciarTeste: () -> Unit = {},
    ) = AppShellHistoricoRootEntry(
        state = state,
        adsGate = NativeAdsGate(),
        onAbrirMenu = {},
        onIniciarTeste = onIniciarTeste,
    )

    private fun ferramentasEntry(
        acoes: AppShellFerramentasAcoes = acoesVazias(),
        disponibilidade: (TipoFerramenta) -> FerramentaDisponibilidade = { FerramentaDisponibilidade.Disponivel },
        onRegistrarAbertura: (TipoFerramenta) -> Unit = {},
    ) = AppShellFerramentasRootEntry(
        acoes = acoes,
        disponibilidade = disponibilidade,
        onAbrirMenu = {},
        onRegistrarAbertura = onRegistrarAbertura,
    )

    private fun acoesVazias(registro: MutableList<String>? = null) =
        AppShellFerramentasAcoes(
            onAbrirSinalCanais = { registro?.add("sinal_canais") },
            onAbrirDispositivos = { registro?.add("dispositivos") },
            onAbrirEquipamentoInternet = { registro?.add("equipamento") },
            onAbrirPing = { registro?.add("ping") },
            onAbrirDns = { registro?.add("dns") },
            onAbrirLaudo = { registro?.add("laudo") },
            onAbrirMonitoramento = { registro?.add("monitoramento") },
            onAbrirModoGamer = { registro?.add("modo_gamer") },
            onAbrirSinalWifi = { registro?.add("sinal_wifi") },
        )

    /** Wiring padrão do registro; o teste customiza só o que está exercitando. */
    @Composable
    private fun RegistroDeTeste(
        root: AppShellRoot,
        historico: AppShellHistoricoRootEntry = historicoEntry(),
        ferramentas: AppShellFerramentasRootEntry = ferramentasEntry(),
        inlineRootContent: @Composable (AppShellRoot) -> Unit = {},
    ) {
        SignallQTheme {
            AppShellRootRegistry(
                root = root,
                historico = historico,
                ferramentas = ferramentas,
                inlineRootContent = inlineRootContent,
            )
        }
    }

    // ─── Por raiz (a raiz repassa o que recebe) ─────────────────────────────────

    @Test
    fun `historico root compoe a tela e repassa onIniciarTeste`() {
        var iniciou = false
        composeRule.setContent {
            SignallQTheme {
                AppShellHistoricoRoot(
                    state = AppShellHistoricoState(),
                    adsGate = NativeAdsGate(),
                    onAbrirMenu = {},
                    onIniciarTeste = { iniciou = true },
                )
            }
        }
        composeRule.onNodeWithText("Histórico").assertExists()
        // Lista vazia + filtro TODOS = estado vazio inicial.
        composeRule.onNodeWithText("Fazer primeira medição").performClick()
        composeRule.runOnIdle { assertTrue(iniciou) }
    }

    @Test
    fun `historico root repassa o filtro de conexao e nao cai no modo nao-controlado`() {
        // Mutante que este teste mata: remover `filtroConexao = state.filtroConexao` do repasse.
        // Sem ele, `HistoricoScreen` entra em modo NÃO-controlado (filtro nulo) e volta a filtrar
        // a lista internamente — double-filter que a própria tela documenta como bug. A diferença
        // é observável: com filtro ativo e lista vazia, a copy do estado vazio é "Medir agora";
        // sem filtro, é "Fazer primeiro teste".
        composeRule.setContent {
            SignallQTheme {
                AppShellHistoricoRoot(
                    state = AppShellHistoricoState(filtroConexao = FiltroConexaoHistorico.MOVEL),
                    adsGate = NativeAdsGate(),
                    onAbrirMenu = {},
                    onIniciarTeste = {},
                )
            }
        }
        composeRule.onNodeWithText("Medir agora").assertExists()
        composeRule.onNodeWithText("Fazer primeiro teste").assertDoesNotExist()
    }

    @Test
    fun `ferramentas root repassa as nove acoes para os cards certos`() {
        // Mutante que este teste mata: trocar dois campos de lugar em AppShellFerramentasAcoes
        // (ex.: onAbrirPing recebendo onAbrirDns). Compila, e o usuário abre a tela errada.
        val abertas = mutableListOf<String>()
        composeRule.setContent {
            SignallQTheme {
                AppShellFerramentasRoot(
                    acoes = acoesVazias(abertas),
                    disponibilidade = { FerramentaDisponibilidade.Disponivel },
                    onAbrirMenu = {},
                    onRegistrarAbertura = {},
                )
            }
        }
        listOf(
            "Sinal e canais" to "sinal_canais",
            "Mapa do Wi-Fi" to "sinal_wifi",
            "Aparelhos da rede" to "dispositivos",
            "Modem e roteador" to "equipamento",
            "Ping" to "ping",
            "Teste de DNS" to "dns",
            "Relatório" to "laudo",
            "Monitoramento" to "monitoramento",
            "Conexão para jogos" to "modo_gamer",
        ).forEach { (rotulo, _) ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(rotulo))
            composeRule.onNodeWithText(rotulo).performClick()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "sinal_canais",
                    "sinal_wifi",
                    "dispositivos",
                    "equipamento",
                    "ping",
                    "dns",
                    "laudo",
                    "monitoramento",
                    "modo_gamer",
                ),
                abertas,
            )
        }
    }

    @Test
    fun `ferramentas root repassa a regra de disponibilidade em vez de assumir tudo disponivel`() {
        // Mutante: trocar `disponibilidade = disponibilidade` por um `{ Disponivel }` fixo.
        // Sem esta asserção, a ferramenta bloqueada abriria normalmente.
        //
        // `Offline` é o estado escolhido de propósito: é o único dos três que BLOQUEIA a
        // navegação. `IndisponivelRemotamente` e `PermissaoNecessaria` mostram o próximo passo e
        // ainda assim navegam (comportamento já travado por `FerramentasScreenTest`) — usá-los
        // aqui não distinguiria "regra repassada" de "regra ignorada".
        val abertas = mutableListOf<String>()
        composeRule.setContent {
            SignallQTheme {
                AppShellFerramentasRoot(
                    acoes = acoesVazias(abertas),
                    disponibilidade = { tipo ->
                        if (tipo == TipoFerramenta.PING) {
                            FerramentaDisponibilidade.Offline("Reconecte-se.")
                        } else {
                            FerramentaDisponibilidade.Disponivel
                        }
                    },
                    onAbrirMenu = {},
                    onRegistrarAbertura = {},
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Ping"))
        composeRule.onNodeWithText("Ping").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Reconecte-se."))
        composeRule.onNodeWithText("Reconecte-se.").assertExists()
        composeRule.runOnIdle { assertTrue("Ping offline nao pode ter navegado", abertas.isEmpty()) }
    }

    // ─── Por registro (a entrada existe dentro do agregador) ────────────────────

    @Test
    fun `registro compoe historico quando a raiz e History`() {
        composeRule.setContent { RegistroDeTeste(root = AppShellRoot.History) }
        composeRule.onNodeWithText("Histórico").assertExists()
    }

    @Test
    fun `registro compoe ferramentas quando a raiz e Tools`() {
        composeRule.setContent { RegistroDeTeste(root = AppShellRoot.Tools) }
        composeRule.onNodeWithText("Sinal e canais").assertExists()
    }

    @Test
    fun `registro repassa onIniciarTeste do historico ate a tela`() {
        // Cobre a cadeia inteira registro -> raiz -> tela, não só a raiz isolada.
        var iniciou = false
        composeRule.setContent {
            RegistroDeTeste(
                root = AppShellRoot.History,
                historico = historicoEntry(onIniciarTeste = { iniciou = true }),
            )
        }
        composeRule.onNodeWithText("Fazer primeira medição").performClick()
        composeRule.runOnIdle { assertTrue(iniciou) }
    }

    @Test
    fun `registro repassa a regra de disponibilidade do hub ate a tela`() {
        composeRule.setContent {
            RegistroDeTeste(
                root = AppShellRoot.Tools,
                ferramentas =
                    ferramentasEntry(
                        disponibilidade = { tipo ->
                            if (tipo == TipoFerramenta.PING) {
                                FerramentaDisponibilidade.Offline("Reconecte-se.")
                            } else {
                                FerramentaDisponibilidade.Disponivel
                            }
                        },
                    ),
            )
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Ping"))
        composeRule.onNodeWithText("Ping").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Reconecte-se."))
        composeRule.onNodeWithText("Reconecte-se.").assertExists()
    }

    // ─── Campos das RootEntry que a mutação alcançou (achado R1 da revisão de Caio) ──

    @Test
    fun `registro repassa onAbrirMenu do historico ate a barra superior`() {
        // Mutante que este teste mata: `onAbrirMenu = historico.onAbrirMenu` -> `{}` no registro.
        // Sobrevivia antes porque os helpers desta classe fixavam `onAbrirMenu = {}` em todos os
        // casos, sem valor distinguível. É o campo cuja ORIGEM esta fatia mudou (o hoisting do
        // `onAbrirMenuDaRaiz` em AppShell.kt) — a interseção entre "o que mais mexeu" e "o que
        // ninguém vigiava".
        var abriuMenu = false
        composeRule.setContent {
            RegistroDeTeste(
                root = AppShellRoot.History,
                historico =
                    AppShellHistoricoRootEntry(
                        state = AppShellHistoricoState(),
                        adsGate = NativeAdsGate(),
                        onAbrirMenu = { abriuMenu = true },
                        onIniciarTeste = {},
                    ),
            )
        }
        // A jornada única usa Perfil como ação da app bar.
        composeRule.onNodeWithContentDescription("Abrir ajustes").performClick()
        composeRule.runOnIdle { assertTrue(abriuMenu) }
    }

    @Test
    fun `registro repassa onAbrirMenu das ferramentas ate a barra superior`() {
        // Mesmo mutante, na outra entrada: `onAbrirMenu = ferramentas.onAbrirMenu` -> `{}`.
        var abriuMenu = false
        composeRule.setContent {
            RegistroDeTeste(
                root = AppShellRoot.Tools,
                ferramentas =
                    AppShellFerramentasRootEntry(
                        acoes = acoesVazias(),
                        disponibilidade = { FerramentaDisponibilidade.Disponivel },
                        onAbrirMenu = { abriuMenu = true },
                        onRegistrarAbertura = {},
                    ),
            )
        }
        composeRule.onNodeWithContentDescription("Abrir ajustes").performClick()
        composeRule.runOnIdle { assertTrue(abriuMenu) }
    }

    @Test
    fun `registro repassa onRegistrarAbertura e a telemetria do hub nao emudece`() {
        // Mutante: `onRegistrarAbertura = ferramentas.onRegistrarAbertura` -> `{}`. Sem este
        // teste, o app pararia de registrar screen_view das ferramentas em silêncio — falha
        // invisível na UI e visível só no funil, semanas depois.
        val registradas = mutableListOf<String>()
        composeRule.setContent {
            RegistroDeTeste(
                root = AppShellRoot.Tools,
                ferramentas = ferramentasEntry(onRegistrarAbertura = { registradas += it.screenName() }),
            )
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Teste de DNS"))
        composeRule.onNodeWithText("Teste de DNS").performClick()
        composeRule.runOnIdle { assertEquals(listOf("dns"), registradas) }
    }

    // ─── Slot das raízes mantidas inline ───────────────────────────────────────

    @Test
    fun `registro delega ao slot exatamente as duas raizes inline no AppShell`() {
        // Trava os dois lados da fronteira de migração: quem deve cair no slot cai, e a raiz
        // recebida é a mesma que entrou (um `inlineRootContent(AppShellRoot.Home)` fixo passaria
        // despercebido sem a comparação por valor).
        //
        // A raiz é dirigida por estado em vez de um `setContent` por iteração: `ComposeTestRule`
        // aceita `setContent` uma única vez por teste. Começa em `History` (migrada) para que a
        // primeira transição já seja uma troca real de valor.
        var recebida: AppShellRoot? = null
        val raizAtual = mutableStateOf(AppShellRoot.History)
        composeRule.setContent {
            RegistroDeTeste(root = raizAtual.value, inlineRootContent = { recebida = it })
        }
        listOf(AppShellRoot.Home, AppShellRoot.Speed).forEach { raiz ->
            composeRule.runOnIdle {
                recebida = null
                raizAtual.value = raiz
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertEquals("slot deveria receber $raiz", raiz, recebida) }
        }
    }

    @Test
    fun `registro nunca cai no slot para as raizes ja migradas`() {
        // Mutante que este teste mata: mover History ou Tools de volta para o ramo
        // `inlineRootContent` do `when`. O app continuaria funcionando (o slot ainda desenha a tela
        // antiga em `AppShell.kt`), mas a migração teria sido revertida em silêncio.
        var recebida: AppShellRoot? = null
        val raizAtual = mutableStateOf(AppShellRoot.Home)
        composeRule.setContent {
            RegistroDeTeste(root = raizAtual.value, inlineRootContent = { recebida = it })
        }
        listOf(AppShellRoot.History, AppShellRoot.Tools).forEach { raiz ->
            composeRule.runOnIdle {
                recebida = null
                raizAtual.value = raiz
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertNull("$raiz nao pode cair no slot de nao migradas", recebida) }
        }
    }
}
