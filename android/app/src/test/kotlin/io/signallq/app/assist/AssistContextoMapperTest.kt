package io.signallq.app.assist

import io.signallq.app.assist.data.AssistContextoMapper
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import io.signallq.app.feature.speedtest.DiagnosticoFasesSpeedtest
import io.signallq.app.feature.speedtest.DiagnosticoQualidadeSpeedtest
import io.signallq.app.feature.speedtest.GargaloPrimario
import io.signallq.app.feature.speedtest.MeasurementStatus
import io.signallq.app.feature.speedtest.ModoSpeedtest
import io.signallq.app.feature.speedtest.ResultadoSpeedtest
import io.signallq.app.feature.speedtest.SeveridadeBufferbloat
import io.signallq.app.feature.speedtest.VereditoUso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistContextoMapperTest {
    @Test
    fun `quando resultado for nulo retorna semMedicao verdadeiro com tipo de conexao`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -60, linkSpeedMbps = 300, frequenciaMhz = 5180),
            )

        val contexto = AssistContextoMapper.map(resultado = null, input = input, ispNome = "Vivo Fibra", operadoraMovel = null)

        assertTrue(contexto.semMedicao)
        assertEquals("Wi-Fi", contexto.tipoRede)
        assertEquals("Vivo Fibra", contexto.operadoraOuIsp)
    }

    @Test
    fun `quando resultado for cancelado trata como semMedicao`() {
        val resultado = criarResultado(status = MeasurementStatus.CANCELLED)

        val contexto = AssistContextoMapper.map(resultado = resultado, input = null, ispNome = null, operadoraMovel = null)

        assertTrue(contexto.semMedicao)
    }

    @Test
    fun `quando resultado for completo mapeia metricas com precisao`() {
        val input =
            DiagnosticInput(
                wifi = WifiDiagnosticInput(rssiDbm = -52, linkSpeedMbps = 600, frequenciaMhz = 5200),
            )
        val resultado =
            criarResultado(
                status = MeasurementStatus.COMPLETE,
                downloadMbps = 450.0,
                uploadMbps = 150.0,
                latenciaMs = 14.0,
                jitterMs = 2.0,
                perdaPct = 0.5,
                connectionType = "wifi",
            )

        val contexto =
            AssistContextoMapper.map(
                resultado = resultado,
                input = input,
                ispNome = "Claro",
                operadoraMovel = null,
            )

        assertFalse(contexto.semMedicao)
        assertEquals(450.0, contexto.downloadMbps!!, 0.01)
        assertEquals(150.0, contexto.uploadMbps!!, 0.01)
        assertEquals(14L, contexto.latenciaMs)
        assertEquals(2L, contexto.jitterMs)
        assertEquals(0.5, contexto.perdaPct!!, 0.01)
        assertEquals(-52, contexto.sinalDbm)
        assertEquals("Wi-Fi (5 GHz)", contexto.tipoRede)
        assertEquals("Claro", contexto.operadoraOuIsp)
    }

    private fun criarResultado(
        status: MeasurementStatus,
        downloadMbps: Double = 100.0,
        uploadMbps: Double = 50.0,
        latenciaMs: Double = 10.0,
        jitterMs: Double = 1.0,
        perdaPct: Double = 0.0,
        connectionType: String? = "wifi",
    ): ResultadoSpeedtest =
        ResultadoSpeedtest(
            timestampEpochMs = 0L,
            specVersion = "1",
            modo = ModoSpeedtest.complete,
            connectionTypeStart = connectionType,
            connectionTypeEnd = connectionType,
            contaminado = false,
            latenciaMs = latenciaMs,
            jitterMs = jitterMs,
            perdaPercentual = perdaPct,
            bufferbloatMs = 5.0,
            severidadeBufferbloat = SeveridadeBufferbloat.none,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            latencyDownloadMs = latenciaMs,
            latencyUploadMs = latenciaMs,
            stabilityScore = 1.0,
            peakDownloadMbps = downloadMbps,
            peakUploadMbps = uploadMbps,
            packetLossSource = "download",
            dnsLatencyMs = null,
            dnsResolverIp = null,
            dnsProvider = null,
            diagnosticoQualidade =
                DiagnosticoQualidadeSpeedtest(
                    vereditoStreaming = VereditoUso.good,
                    vereditoGamer = VereditoUso.good,
                    vereditoVideoChamada = VereditoUso.good,
                    gargaloPrimario = GargaloPrimario.none,
                ),
            diagnosticoFases =
                DiagnosticoFasesSpeedtest(
                    faseInterrompida = "",
                    latenciaAmostrasTotais = 0,
                    latenciaAmostrasValidas = 0,
                    latenciaTimeouts = 0,
                    downloadBytesTotal = 0L,
                    downloadAmostrasValidas = 0,
                    downloadRequisicoesSucesso = 0,
                    downloadRequisicoesErro = 0,
                    downloadEncerradaPor = "",
                    downloadThroughputOrigem = "",
                    downloadUltimoErro = null,
                    uploadBytesTotal = 0L,
                    uploadAmostrasValidas = 0,
                    uploadRequisicoesSucesso = 0,
                    uploadRequisicoesErro = 0,
                    uploadEncerradaPor = "",
                    uploadThroughputOrigem = "",
                    uploadUltimoErro = null,
                    dnsErroMensagem = null,
                ),
            connectionType = connectionType,
            status = status,
        )
}
