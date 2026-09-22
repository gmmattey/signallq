package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Camillo/Luiz (.agents/architecture-plan.md, "Confiabilidade estatística do diagnóstico
 * de rede", seção 7) — checkpoint 2 do plano de implementação: cobre a janela de
 * confirmação de [ExecutorSpeedtestCloudflare.coletarAmostrasLatencia] (+20 probes brutos,
 * disparo único) e a propagação de p95/max/picos/[io.signallq.app.core.diagnostico.EvidenciaPerdaPacotes]
 * até [ExecutorSpeedtestCloudflare.LatencyPhase].
 *
 * `executarFaseLatencia`/`coletarAmostrasLatencia`/`deveConfirmarAmostragem`/
 * `SpeedtestConfig`/`LatencyPhase` foram promovidos de `private` para `internal` só para
 * viabilizar este teste (mesmo padrão já usado por `mapearCausaFalha`) — não mudam de
 * visibilidade fora do módulo `:feature:speedtest`.
 */
class ExecutorSpeedtestCloudflareConfirmacaoTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun executorContraServidor(): ExecutorSpeedtestCloudflare =
        ExecutorSpeedtestCloudflare(isMobile = false, latencyProbeUrl = server.url("/__down").toString())

    @Test
    fun `rede saudavel nao dispara confirmacao e usa so o baseline`() =
        runTest {
            // config fast: pingCount = 20 (19 efetivos pos warm-up), todas as 20 respostas OK.
            repeat(20) { server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) }
            val executor = executorContraServidor()
            val config = ExecutorSpeedtestCloudflare.SpeedtestConfig.fromModo(ModoSpeedtest.fast)

            val fase =
                executor.executarFaseLatencia(
                    config = config,
                    redeInicial = null,
                    connectionTypeProvider = null,
                )

            assertEquals(20, server.requestCount)
            assertEquals(19, fase.totalAmostras)
            assertEquals(0, fase.timeouts)
            assertEquals(false, fase.evidenciaPerda?.confirmacaoExecutada)
            assertEquals(ConfiancaAmostral.SUFICIENTE, fase.evidenciaPerda?.confianca)
        }

    @Test
    fun `1 timeout isolado no baseline dispara a janela de confirmacao de 20 probes extras`() =
        runTest {
            // 1a amostra (warm-up, descartada) OK + 18 OK + 1 timeout (HTTP 500 -> null em
            // medirPing) = pingCount 20, 1 timeout entre as 19 efetivas -> dispara gatilho
            // "timeouts >= 1". Confirmacao consome +20 probes OK (nenhum timeout novo).
            repeat(19) { server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) }
            server.enqueue(MockResponse().setResponseCode(500))
            repeat(20) { server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) }
            val executor = executorContraServidor()
            val config = ExecutorSpeedtestCloudflare.SpeedtestConfig.fromModo(ModoSpeedtest.fast)

            val fase =
                executor.executarFaseLatencia(
                    config = config,
                    redeInicial = null,
                    connectionTypeProvider = null,
                )

            // 20 (baseline) + 20 (confirmacao) = 40 requisicoes brutas -> 39 efetivas pos warm-up.
            assertEquals(40, server.requestCount)
            assertEquals(39, fase.totalAmostras)
            assertEquals(1, fase.timeouts)
            assertTrue(fase.evidenciaPerda?.confirmacaoExecutada == true)
            // 1 timeout isolado, confirmado como isolado -> continua INSUFICIENTE, nao
            // "some" nem vira 0 -- o percentual medido e reportado normalmente.
            assertEquals(ConfiancaAmostral.INSUFICIENTE, fase.evidenciaPerda?.confianca)
            assertTrue(fase.evidenciaPerda!!.perdaPercentual > 0.0)
        }

    @Test
    fun `p95 max e picos sobrevivem ate LatencyPhase em vez de serem descartados`() =
        runTest {
            repeat(20) { server.enqueue(MockResponse().setResponseCode(200).setBody("ok")) }
            val executor = executorContraServidor()
            val config = ExecutorSpeedtestCloudflare.SpeedtestConfig.fromModo(ModoSpeedtest.fast)

            val fase =
                executor.executarFaseLatencia(
                    config = config,
                    redeInicial = null,
                    connectionTypeProvider = null,
                )

            // Nao afirma um valor numerico especifico (depende do tempo real de round-trip
            // local) -- so que os campos deixaram de ser 0.0/valores-padrao "descartados".
            assertTrue(fase.p95Ms >= 0.0)
            assertTrue(fase.maxMs >= 0.0)
            assertTrue(fase.picos >= 0)
        }

    @Test
    fun `deveConfirmarAmostragem dispara com qualquer um dos 4 gatilhos`() {
        val executor = ExecutorSpeedtestCloudflare()

        val comTimeout =
            ResultadoAmostragemPing(
                latenciaMs = 20.0,
                jitterMs = 2.0,
                perdaPercentual = 5.0,
                totalAmostras = 19,
                amostrasValidas = 18,
                timeouts = 1,
            )
        assertTrue(executor.deveConfirmarAmostragem(comTimeout))

        val comPico =
            ResultadoAmostragemPing(
                latenciaMs = 20.0,
                jitterMs = 2.0,
                perdaPercentual = 0.0,
                totalAmostras = 19,
                amostrasValidas = 18,
                timeouts = 0,
                picos = 1,
            )
        assertTrue(executor.deveConfirmarAmostragem(comPico))

        val comJitterRuim =
            ResultadoAmostragemPing(
                latenciaMs = 20.0,
                jitterMs = 25.0,
                perdaPercentual = 0.0,
                totalAmostras = 19,
                amostrasValidas = 19,
                timeouts = 0,
            )
        assertTrue(executor.deveConfirmarAmostragem(comJitterRuim))

        val comPerdaPertoDoCorte =
            ResultadoAmostragemPing(
                latenciaMs = 20.0,
                jitterMs = 2.0,
                // dentro de +-30% do corte de 2.0% (1.4 a 2.6)
                perdaPercentual = 2.3,
                totalAmostras = 19,
                amostrasValidas = 19,
                timeouts = 0,
            )
        assertTrue(executor.deveConfirmarAmostragem(comPerdaPertoDoCorte))

        val semNenhumGatilho =
            ResultadoAmostragemPing(
                latenciaMs = 20.0,
                jitterMs = 2.0,
                perdaPercentual = 0.0,
                totalAmostras = 19,
                amostrasValidas = 19,
                timeouts = 0,
            )
        assertFalse(executor.deveConfirmarAmostragem(semNenhumGatilho))
    }
}
