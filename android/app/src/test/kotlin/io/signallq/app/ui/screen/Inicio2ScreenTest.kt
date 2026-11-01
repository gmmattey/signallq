package io.signallq.app.ui.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.signallq.app.core.diagnostico.EstagioRede
import io.signallq.app.feature.home.MetricasMedicaoHome
import io.signallq.app.feature.home.OrigemMedicaoHome
import io.signallq.app.feature.home.ResolvedHomeMeasurement
import io.signallq.app.ui.SignallQTheme
import io.signallq.app.ui.component.SignallQFeedbackTone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Inicio2ScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `trilha de conexao nao exibe titulo adicional legado`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    connectionTrail =
                        Inicio2ConnectionTrailState(
                            nodes = listOf(Inicio2TrailNode("Internet", "Internet", "Conectada")),
                            supportingMessage = null,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Caminho da rede").assertDoesNotExist()
        composeRule.onNodeWithText("Sua rede").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ver detalhes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `offline em font scale 2 mostra contexto e CTA acessivel disparado uma vez`() {
        var analyses = 0
        composeRule.setContent {
            SignallQTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Inicio2Screen(
                        uiState = Inicio2UiState(Inicio2Conexao.Offline, null, Inicio2Analise.SemAnalise),
                        onAnalisarConexao = {
                            analyses++
                            1L
                        },
                        onAbrirPerfil = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Sem internet").assertIsDisplayed()
        composeRule.onNodeWithText("Internet lenta").assertIsDisplayed()
        composeRule
            .onNodeWithText("Fazer teste de velocidade")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, analyses)
    }

    @Test
    fun `CTA e single flight entre toques recomposicao e ate conclusao canonica`() {
        var analyses = 0
        val state = mutableStateOf(Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise))
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = state.value,
                    onAnalisarConexao = {
                        analyses++
                        1L
                    },
                    onAbrirPerfil = {},
                )
            }
        }

        val cta = composeRule.onNodeWithText("Fazer teste de velocidade")
        val clickAction = cta.fetchSemanticsNode().config[SemanticsActions.OnClick].action
        composeRule.runOnIdle {
            clickAction?.invoke()
            clickAction?.invoke()
        }
        composeRule.waitForIdle()
        assertEquals(1, analyses)
        composeRule.onNode(hasStateDescription("Carregando")).assertIsNotEnabled()

        state.value = state.value.copy(nomeConexao = "Casa 2")
        composeRule.waitForIdle()
        composeRule.onNode(hasStateDescription("Carregando")).assertIsNotEnabled()
        assertEquals(1, analyses)

        state.value =
            state.value.copy(
                analise = Inicio2Analise.StatusEmTempoReal("Bom", "Motivo de teste"),
                geracaoDiagnostico = 1L,
            )
        composeRule.waitForIdle()
        val novoCta = composeRule.onNodeWithText("Fazer teste de velocidade").assertIsEnabled()
        val novaClickAction = novoCta.fetchSemanticsNode().config[SemanticsActions.OnClick].action
        composeRule.runOnIdle { novaClickAction?.invoke() }
        assertEquals(2, analyses)
    }

    @Test
    fun `mesmo veredito falha precoce e cancelamento liberam pela geracao`() {
        var analyses = 0
        var proximaGeracao = 5L
        val state =
            mutableStateOf(
                Inicio2UiState(
                    Inicio2Conexao.Wifi,
                    "Casa",
                    Inicio2Analise.StatusEmTempoReal("Bom", "Motivo de teste"),
                    geracaoDiagnostico = 4L,
                ),
            )
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = state.value,
                    onAnalisarConexao = {
                        analyses++
                        proximaGeracao++
                    },
                    onAbrirPerfil = {},
                )
            }
        }

        fun clicarCta() {
            val action =
                composeRule
                    .onNodeWithText("Fazer teste de velocidade")
                    .fetchSemanticsNode()
                    .config[SemanticsActions.OnClick]
                    .action
            composeRule.runOnIdle { action?.invoke() }
        }

        clicarCta()
        state.value = state.value.copy(geracaoDiagnostico = 5L)
        composeRule.waitForIdle()
        clicarCta()
        assertEquals(2, analyses)

        state.value =
            state.value.copy(
                analise = Inicio2Analise.Interrompida("Falha antes de coletar dados."),
                geracaoDiagnostico = 6L,
            )
        composeRule.waitForIdle()
        clicarCta()
        assertEquals(3, analyses)

        state.value =
            state.value.copy(
                analise = Inicio2Analise.Interrompida("A análise foi cancelada."),
                geracaoDiagnostico = 7L,
            )
        composeRule.waitForIdle()
        clicarCta()
        assertEquals(4, analyses)
    }

    @Test
    fun `recriacao do produtor nao restaura latch efemero da composicao`() {
        var analyses = 0
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = {
                        analyses++
                        1L
                    },
                    onAbrirPerfil = {},
                )
            }
        }

        composeRule.onNodeWithText("Fazer teste de velocidade").performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Fazer teste de velocidade").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(2, analyses)
    }

    @Test
    fun `perfil e estado interrompido permanecem acionaveis`() {
        var profiles = 0
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.Interrompida("Contexto preservado.")),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = { profiles++ },
                )
            }
        }
        composeRule.onNodeWithText("Análise interrompida").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Abrir ajustes").performClick()
        assertEquals(1, profiles)
    }

    @Test
    fun `botao conversar com assist dispara callback`() {
        var assistClicado = 0
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { 1L },
                    onAbrirPerfil = {},
                    onConversarAssist = { assistClicado++ },
                )
            }
        }

        composeRule
            .onNodeWithText("Conversar com Assist")
            .performClick()

        assertEquals(1, assistClicado)
    }

    @Test
    fun `card de ultimo teste exibe metricas e acao quando disponivel`() {
        var resultadoClicado = 0
        val medicao =
            ResolvedHomeMeasurement(
                metricas =
                    MetricasMedicaoHome(
                        downloadMbps = 450.0,
                        uploadMbps = 120.0,
                        latenciaMs = 14.0,
                        jitterMs = 2.0,
                        perdaPercentual = 0.0,
                        timestampEpochMs = 1700000000000L,
                        connectionType = "Wi-Fi",
                        ssid = "Casa",
                        vereditoGamer = null,
                        gargaloPrimario = null,
                        utilizavel = true,
                    ),
                origem = OrigemMedicaoHome.ANTERIOR,
            )

        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState =
                        Inicio2UiState(
                            conexao = Inicio2Conexao.Wifi,
                            nomeConexao = "Casa",
                            analise = Inicio2Analise.StatusEmTempoReal("Excelente", "Conexão ótima"),
                            ultimaMedicao = medicao,
                        ),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    onVerResultado = { resultadoClicado++ },
                )
            }
        }

        composeRule.onNodeWithText("Último teste").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("450").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("120").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("14").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Upload").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ping").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Ver resultado")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, resultadoClicado)
    }

    @Test
    fun `card de ultimo teste exibe convite amigavel quando sem medicoes`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState =
                        Inicio2UiState(
                            conexao = Inicio2Conexao.Wifi,
                            nomeConexao = "Casa",
                            analise = Inicio2Analise.StatusEmTempoReal("Excelente", "Conexão ótima"),
                            ultimaMedicao = null,
                        ),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                )
            }
        }

        composeRule.onNodeWithText("Último teste").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nenhum teste recente").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ver resultado").assertDoesNotExist()
    }

    @Test
    fun `secao explorar exibe atalhos de wifi e jogos e sao clicaveis`() {
        var wifiClicado = 0
        var jogosClicado = 0
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    onAbrirSinalWifi = { wifiClicado++ },
                    onAbrirJogos = { jogosClicado++ },
                )
            }
        }

        composeRule.onNodeWithText("Explorar").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Sinal do Wi-Fi")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, wifiClicado)
        composeRule
            .onNodeWithText("Conexão para jogos")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, jogosClicado)
    }

    private val statusAmbienteErroWifi =
        Inicio2StatusAoVivo(
            porEstagio = mapOf("Equipamento" to SignallQFeedbackTone.Error, "Wi-Fi" to SignallQFeedbackTone.Error),
            geral = SignallQFeedbackTone.Error,
            causaPrincipal = EstagioRede.WIFI,
        )

    @Test
    fun `Hero Carregando preserva copy atual mesmo com statusAoVivo disponivel`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.Carregando),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    statusAoVivo = statusAmbienteErroWifi,
                )
            }
        }

        composeRule.onNodeWithText("Analisando sua conexão").assertIsDisplayed()
        composeRule.onNodeWithText("Problema no seu Wi-Fi").assertDoesNotExist()
    }

    @Test
    fun `Hero Interrompida preserva copy atual mesmo com statusAoVivo disponivel`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.Interrompida("Contexto preservado.")),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    statusAoVivo = statusAmbienteErroWifi,
                )
            }
        }

        composeRule.onNodeWithText("Análise interrompida").assertIsDisplayed()
        composeRule.onNodeWithText("Problema no seu Wi-Fi").assertDoesNotExist()
    }

    @Test
    fun `Hero SemAnalise em Wi-Fi usa pior caso da trilha quando statusAoVivo disponivel`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    statusAoVivo = statusAmbienteErroWifi,
                )
            }
        }

        composeRule.onNodeWithText("Problema no seu Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithText("Internet lenta").assertDoesNotExist()
    }

    @Test
    fun `Hero StatusEmTempoReal em Wi-Fi usa pior caso da trilha quando statusAoVivo disponivel`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.StatusEmTempoReal("Bom", "Motivo de teste")),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    statusAoVivo = statusAmbienteErroWifi,
                )
            }
        }

        composeRule.onNodeWithText("Problema no seu Wi-Fi").assertIsDisplayed()
    }

    @Test
    fun `Hero em Wi-Fi sem statusAoVivo ainda mostra verificando sua rede`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Wifi, "Casa", Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                )
            }
        }

        composeRule.onNodeWithText("Verificando sua rede").assertIsDisplayed()
    }

    @Test
    fun `Hero fora do Wi-Fi ignora statusAoVivo`() {
        composeRule.setContent {
            SignallQTheme {
                Inicio2Screen(
                    uiState = Inicio2UiState(Inicio2Conexao.Movel, null, Inicio2Analise.SemAnalise),
                    onAnalisarConexao = { null },
                    onAbrirPerfil = {},
                    statusAoVivo = statusAmbienteErroWifi,
                )
            }
        }

        composeRule.onNodeWithText("Internet lenta").assertIsDisplayed()
        composeRule.onNodeWithText("Problema no seu Wi-Fi").assertDoesNotExist()
    }
}
