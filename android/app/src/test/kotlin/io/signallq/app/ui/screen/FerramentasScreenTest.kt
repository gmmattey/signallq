package io.signallq.app.ui.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.signallq.app.ui.SignallQTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FerramentasScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `open list routes all nine canonical destinations at font scale 2`() {
        val opened = mutableListOf<String>()
        val tracked = mutableListOf<String>()
        composeRule.setContent {
            SignallQTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    FerramentasScreen(
                        onAbrirMenu = {},
                        onAbrirSinalCanais = { opened += "signal" },
                        onAbrirSinalWifi = { opened += "wifi-live" },
                        onAbrirDispositivos = { opened += "devices" },
                        onAbrirEquipamentoInternet = { opened += "equipment" },
                        onAbrirPing = { opened += "ping" },
                        onAbrirDns = { opened += "dns" },
                        onAbrirLaudo = { opened += "report" },
                        onAbrirMonitoramento = { opened += "monitoring" },
                        onAbrirJogos = { opened += "gamer" },
                        onRegistrarAbertura = { tracked += it.screenName() },
                    )
                }
            }
        }
        listOf(
            "Sinal e canais",
            "Mapa do Wi-Fi",
            "Aparelhos da rede",
            "Modem e roteador",
            "Ping",
            "Teste de DNS",
            "Relatório",
            "Monitoramento",
            "Conexão para jogos",
        ).forEach { label ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithText(label).performClick()
        }

        assertEquals(listOf("signal", "wifi-live", "devices", "equipment", "ping", "dns", "report", "monitoring", "gamer"), opened)
        assertEquals(
            listOf("sinal_wifi", "sinal_wifi", "dispositivos", "equipamento_internet", "ping", "dns", "laudo", "monitoramento", "modo_gamer"),
            tracked,
        )
    }

    @Test
    fun `remote offline and permission states have action or next step`() {
        val opened = mutableListOf<String>()
        val tracked = mutableListOf<String>()
        composeRule.setContent {
            SignallQTheme {
                FerramentasScreen(
                    onAbrirMenu = {},
                    onAbrirDns = { opened += "dns-gate" },
                    onAbrirPing = { opened += "ping" },
                    onAbrirDispositivos = { opened += "permission" },
                    onAbrirJogos = { opened += "gamer" },
                    onRegistrarAbertura = { tracked += it.screenName() },
                    disponibilidade = { tipo ->
                        when (tipo) {
                            TipoFerramenta.DNS -> FerramentaDisponibilidade.IndisponivelRemotamente("Tente depois.")
                            TipoFerramenta.PING -> FerramentaDisponibilidade.Offline("Reconecte-se.")
                            TipoFerramenta.DISPOSITIVOS -> FerramentaDisponibilidade.PermissaoNecessaria("Permita redes próximas.")
                            TipoFerramenta.MODO_JOGOS -> FerramentaDisponibilidade.Oculta("Não disponível nesta versão.")
                            else -> FerramentaDisponibilidade.Disponivel
                        }
                    },
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Teste de DNS"))
        composeRule.onNodeWithText("Teste de DNS").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Tente depois."))
        composeRule.onNodeWithText("Tente depois.").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Ping"))
        composeRule.onNodeWithText("Ping").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Reconecte-se."))
        composeRule.onNodeWithText("Reconecte-se.").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Aparelhos da rede"))
        composeRule.onNodeWithText("Aparelhos da rede").performClick()
        composeRule.onNodeWithText("Conexão para jogos").assertDoesNotExist()
        composeRule.onNode(hasText("Não disponível nesta versão.", substring = true)).assertDoesNotExist()

        assertEquals(listOf("dns-gate", "permission"), opened)
        assertEquals(listOf("dispositivos"), tracked)
    }
}
