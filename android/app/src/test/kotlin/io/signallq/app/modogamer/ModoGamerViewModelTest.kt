package io.signallq.app.modogamer

import io.signallq.app.core.datastore.ModoGamerPadraoPersistido
import io.signallq.app.core.diagnostico.CatalogoJogosModoGamer
import io.signallq.app.core.diagnostico.CategoriaJogoModoGamer
import io.signallq.app.core.diagnostico.DeviceJogo
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.feature.diagnostico.topology.lan.NatUdpResultado
import io.signallq.app.feature.diagnostico.topology.lan.NatUdpTipo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de navegação do [ModoGamerViewModel] (Feature #550, issue #1476, fundido com GH#935
 * pela issue #1487) — fluxo jogo → device → medição → resultado, fallback pra jogo fora do
 * catálogo, "voltar" em cada etapa, persistência do padrão e refinamento por ping/NAT dedicado.
 */
class ModoGamerViewModelTest {
    private val valorant = CatalogoJogosModoGamer.porId("valorant")!!

    private fun viewModel(
        padraoInicial: Pair<SelecaoJogoModoGamer, DeviceJogo>? = null,
        evidenciaBaseDisponivel: () -> Boolean = { true },
        onSalvarPadrao: suspend (String?, String?, String) -> Unit = { _, _, _ -> },
    ) = ModoGamerViewModel(
        padraoInicial = padraoInicial,
        inputAtual = { DiagnosticInput() },
        evidenciaBaseDisponivel = evidenciaBaseDisponivel,
        onSalvarPadrao = onSalvarPadrao,
    )

    @Test
    fun `comeca na etapa de selecao de jogo quando nao ha padrao salvo`() {
        val vm = viewModel()
        assertTrue(vm.etapa.value is ModoGamerEtapa.SelecaoJogo)
    }

    @Test
    fun `busca atualiza o texto da etapa de selecao de jogo`() {
        val vm = viewModel()
        vm.buscar("valo")
        val etapa = vm.etapa.value as ModoGamerEtapa.SelecaoJogo
        assertEquals("valo", etapa.busca)
    }

    @Test
    fun `selecionar jogo catalogado avanca para selecao de device`() {
        val vm = viewModel()
        vm.selecionarJogo(valorant)
        val etapa = vm.etapa.value as ModoGamerEtapa.SelecaoDevice
        val selecao = etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado
        assertEquals("valorant", selecao.jogo.gameId)
        assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, selecao.categoria)
    }

    @Test
    fun `selecionar categoria fallback avanca para selecao de device sem erro`() {
        val vm = viewModel()
        vm.selecionarCategoriaFallback(CategoriaJogoModoGamer.MOBA)
        val etapa = vm.etapa.value as ModoGamerEtapa.SelecaoDevice
        val selecao = etapa.selecaoJogo as SelecaoJogoModoGamer.ForaDoCatalogo
        assertEquals(CategoriaJogoModoGamer.MOBA, selecao.categoria)
        assertEquals(CategoriaJogoModoGamer.MOBA.label, selecao.nomeExibido)
    }

    @Test
    fun `selecionar device avanca para medicao`() {
        val vm = viewModel()
        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.PC)
        val etapa = vm.etapa.value as ModoGamerEtapa.Medindo
        assertEquals(DeviceJogo.PC, etapa.device)
    }

    @Test
    fun `sem evidencia base elegivel pede teste rapido preservando jogo e aparelho`() {
        val vm = viewModel(evidenciaBaseDisponivel = { false })

        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.PC)

        val etapa = vm.etapa.value as ModoGamerEtapa.AguardandoTesteRapido
        assertEquals("valorant", (etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado).jogo.gameId)
        assertEquals(DeviceJogo.PC, etapa.device)
    }

    @Test
    fun `evidencia base nova retoma medicao especifica sem perder selecao`() {
        var evidenciaDisponivel = false
        val vm = viewModel(evidenciaBaseDisponivel = { evidenciaDisponivel })
        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.XBOX)

        evidenciaDisponivel = true
        vm.onEvidenciaBaseDisponivel()

        val etapa = vm.etapa.value as ModoGamerEtapa.Medindo
        assertEquals("valorant", (etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado).jogo.gameId)
        assertEquals(DeviceJogo.XBOX, etapa.device)
    }

    @Test
    fun `perder evidencia base durante medicao volta a pedir teste rapido`() {
        val vm = viewModel()
        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.PC)

        vm.onEvidenciaBaseIndisponivel()

        val etapa = vm.etapa.value as ModoGamerEtapa.AguardandoTesteRapido
        assertEquals("valorant", (etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado).jogo.gameId)
        assertEquals(DeviceJogo.PC, etapa.device)
    }

    @Test
    fun `nao confirma veredito se evidencia expira durante medicao`() =
        runTest {
            var evidenciaDisponivel = true
            val vm = viewModel(evidenciaBaseDisponivel = { evidenciaDisponivel })
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PLAYSTATION)

            evidenciaDisponivel = false
            vm.confirmarMedicao(pingEspecificoMs = 18.0, jitterMs = 2.0, perdaPercentual = 0.0, natUdp = null)

            assertTrue(vm.etapa.value is ModoGamerEtapa.AguardandoTesteRapido)
        }

    @Test
    fun `confirmar medicao salva como padrao automaticamente`() =
        runTest {
            var jogoIdPersistido: String? = null
            var devicePersistido: String? = null
            val vm =
                viewModel(
                    onSalvarPadrao = { jogoId, _, device ->
                        jogoIdPersistido = jogoId
                        devicePersistido = device
                    },
                )
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PC)
            vm.confirmarMedicao(null, null, null, null)

            assertEquals("valorant", jogoIdPersistido)
            assertEquals("PC", devicePersistido)
            val etapa = vm.etapa.value as ModoGamerEtapa.Resultado
            assertTrue(etapa.salvoComoPadrao)
            assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, etapa.resultado.categoria)
        }

    @Test
    fun `confirmar salvando como padrao persiste jogoId e device`() =
        runTest {
            var jogoIdPersistido: String? = null
            var categoriaPersistida: String? = null
            var devicePersistido: String? = null
            val vm =
                viewModel(
                    onSalvarPadrao = { jogoId, categoria, device ->
                        jogoIdPersistido = jogoId
                        categoriaPersistida = categoria
                        devicePersistido = device
                    },
                )
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.XBOX)
            vm.confirmarMedicao(null, null, null, null)

            assertEquals("valorant", jogoIdPersistido)
            assertNull(categoriaPersistida)
            assertEquals("XBOX", devicePersistido)
            assertTrue((vm.etapa.value as ModoGamerEtapa.Resultado).salvoComoPadrao)
        }

    @Test
    fun `confirmar com jogo fora do catalogo persiste categoriaFallback e jogoId nulo`() =
        runTest {
            var jogoIdPersistido: String? = "nao deveria mudar"
            var categoriaPersistida: String? = null
            val vm =
                viewModel(
                    onSalvarPadrao = { jogoId, categoria, _ ->
                        jogoIdPersistido = jogoId
                        categoriaPersistida = categoria
                    },
                )
            vm.selecionarCategoriaFallback(CategoriaJogoModoGamer.CASUAL)
            vm.selecionarDevice(DeviceJogo.SWITCH)
            vm.confirmarMedicao(null, null, null, null)

            assertNull(jogoIdPersistido)
            assertEquals("CASUAL", categoriaPersistida)
        }

    @Test
    fun `voltar para selecao de jogo reseta a busca`() {
        val vm = viewModel()
        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.PC)
        vm.voltarParaSelecaoJogo()
        assertEquals(ModoGamerEtapa.SelecaoJogo(), vm.etapa.value)
    }

    @Test
    fun `voltar para selecao de device a partir da medicao preserva o jogo escolhido`() {
        val vm = viewModel()
        vm.selecionarJogo(valorant)
        vm.selecionarDevice(DeviceJogo.PC)
        vm.voltarParaSelecaoDevice()
        val etapa = vm.etapa.value as ModoGamerEtapa.SelecaoDevice
        assertEquals("valorant", (etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado).jogo.gameId)
    }

    @Test
    fun `trocar jogo ou aparelho a partir do resultado volta para selecao de jogo`() =
        runTest {
            val vm = viewModel()
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PC)
            vm.confirmarMedicao(null, null, null, null)
            vm.trocarJogoOuDevice()
            assertTrue(vm.etapa.value is ModoGamerEtapa.SelecaoJogo)
        }

    // ── Padrão salvo (abre direto no resultado) ───────────────────────────────

    @Test
    fun `com padrao salvo comeca direto na medicao`() {
        val vm =
            viewModel(
                padraoInicial = SelecaoJogoModoGamer.Catalogado(valorant) to DeviceJogo.PLAYSTATION,
            )
        val etapa = vm.etapa.value as ModoGamerEtapa.Medindo
        assertEquals(DeviceJogo.PLAYSTATION, etapa.device)
        assertEquals("valorant", (etapa.selecaoJogo as SelecaoJogoModoGamer.Catalogado).jogo.gameId)
    }

    @Test
    fun `padrao salvo tambem permite trocar jogo ou aparelho`() {
        val vm =
            viewModel(
                padraoInicial = SelecaoJogoModoGamer.Catalogado(valorant) to DeviceJogo.PLAYSTATION,
            )
        vm.trocarJogoOuDevice()
        assertTrue(vm.etapa.value is ModoGamerEtapa.SelecaoJogo)
    }

    // ── Refinamento opcional por ping/NAT dedicado (issue #1487) ──────────────

    @Test
    fun `confirmar sem pingEspecificoMs preserva o comportamento anterior a fusao`() =
        runTest {
            val vm = viewModel()
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PC)
            vm.confirmarMedicao(null, null, null, null)
            val etapa = vm.etapa.value as ModoGamerEtapa.Resultado
            assertNull(etapa.natUdp)
        }

    @Test
    fun `confirmar com pingEspecificoMs e natUdp propaga os dois para o resultado`() =
        runTest {
            val vm = viewModel()
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PC)
            val natUdp = NatUdpResultado(NatUdpTipo.MODERADO)
            vm.confirmarMedicao(pingEspecificoMs = 22.0, jitterMs = 3.0, perdaPercentual = 0.0, natUdp = natUdp)

            val etapa = vm.etapa.value as ModoGamerEtapa.Resultado
            assertEquals(natUdp, etapa.natUdp)
            assertTrue(etapa.resultado.evidencias.any { it.label == "Tempo de resposta medido agora" })
        }

    @Test
    fun `alternar salvar padrao no resultado limpa o padrao salvo`() =
        runTest {
            var jogoIdPersistido: String? = "valorant"
            var categoriaPersistida: String? = "FPS_COMPETITIVO"
            var devicePersistido: String? = "PC"
            val vm =
                viewModel(
                    onSalvarPadrao = { jogoId, categoria, device ->
                        jogoIdPersistido = jogoId
                        categoriaPersistida = categoria
                        devicePersistido = device
                    },
                )
            vm.selecionarJogo(valorant)
            vm.selecionarDevice(DeviceJogo.PC)
            vm.confirmarMedicao(null, null, null, null)

            vm.alternarSalvarPadrao(false)

            assertNull(jogoIdPersistido)
            assertNull(categoriaPersistida)
            assertEquals("PC", devicePersistido)
            assertFalse((vm.etapa.value as ModoGamerEtapa.Resultado).salvoComoPadrao)
        }

    // ── Resolução de padrão salvo com validação de plataforma (review Codex) ──

    @Test
    fun `resolverPadraoModoGamer rejeita combinacao incompativel de jogo e aparelho`() {
        // CS2 é exclusivo para PC no catálogo. Se persistido com ANDROID, deve ser rejeitado.
        val persistidoIncompativel = ModoGamerPadraoPersistido(jogoId = "counter_strike_2", categoriaFallback = null, deviceId = "ANDROID")
        val resolvido = resolverPadraoModoGamer(persistidoIncompativel)
        assertNull(resolvido)
    }

    @Test
    fun `resolverPadraoModoGamer aceita combinacao compativel de jogo e aparelho`() {
        val persistidoCompativel = ModoGamerPadraoPersistido(jogoId = "counter_strike_2", categoriaFallback = null, deviceId = "PC")
        val resolvido = resolverPadraoModoGamer(persistidoCompativel)
        assertEquals(DeviceJogo.PC, resolvido?.second)
        assertEquals("counter_strike_2", (resolvido?.first as? SelecaoJogoModoGamer.Catalogado)?.jogo?.gameId)
    }

    @Test
    fun `resolverPadraoModoGamer aceita TV_CLOUD mesmo para jogo com plataformas restritas`() {
        // Jogos PC-only podem ser jogados via TV/Cloud streaming
        val persistidoTvCloud = ModoGamerPadraoPersistido(jogoId = "counter_strike_2", categoriaFallback = null, deviceId = "TV_CLOUD")
        val resolvido = resolverPadraoModoGamer(persistidoTvCloud)
        assertEquals(DeviceJogo.TV_CLOUD, resolvido?.second)
        assertEquals("counter_strike_2", (resolvido?.first as? SelecaoJogoModoGamer.Catalogado)?.jogo?.gameId)
    }
}
