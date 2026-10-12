package io.signallq.app.ui.share

import io.signallq.app.core.diagnostico.EvidenciaDiagnostico
import io.signallq.app.core.diagnostico.MetricStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GameCardImageGeneratorTest {

    @Test
    fun `extrairMetricasGamer extrai ping, jitter e bufferbloat corretamente`() {
        val evidencias = listOf(
            EvidenciaDiagnostico("Tempo de resposta medido agora", "18 ms", MetricStatus.bom),
            EvidenciaDiagnostico("Variação do tempo de resposta", "3 ms", MetricStatus.bom),
            EvidenciaDiagnostico("Sobrecarga na rede (bufferbloat)", "+12 ms", MetricStatus.regular),
            EvidenciaDiagnostico("Aparelho analisado", "PlayStation 5", MetricStatus.inconclusivo),
        )

        val (ping, jitter, bloat) = GameCardImageGenerator.extrairMetricasGamer(evidencias)
        assertEquals(18, ping)
        assertEquals(3, jitter)
        assertEquals(12, bloat)
    }

    @Test
    fun `extrairMetricasGamer preserva nulos quando evidencias nao trazem metricas`() {
        val (ping, jitter, bloat) = GameCardImageGenerator.extrairMetricasGamer(emptyList())
        assertEquals(null, ping)
        assertEquals(null, jitter)
        assertEquals(null, bloat)
    }

    @Test
    fun `extrairMetricasGamer lida com jogo casual sem jitter sem inventar valores`() {
        val evidencias = listOf(
            EvidenciaDiagnostico("Tempo de resposta", "22 ms", MetricStatus.bom),
            EvidenciaDiagnostico("Download", "150.0 Mbps", MetricStatus.bom),
        )
        val (ping, jitter, bloat) = GameCardImageGenerator.extrairMetricasGamer(evidencias)
        assertEquals(22, ping)
        assertEquals(null, jitter)
        assertEquals(null, bloat)
    }
}

