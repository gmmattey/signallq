package io.signallq.app.core.diagnostico

import io.signallq.app.core.diagnostico.GameReadinessClassifier.Categoria
import io.signallq.app.core.diagnostico.GameReadinessClassifier.ReadinessStatus
import io.signallq.app.core.diagnostico.topology.model.NatStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do [GameReadinessClassifier] (SIG-290) — thresholds exatos das 3
 * categorias iniciais (aba 10 do documento de produto), penalidades de Wi-Fi e
 * NAT como evidencia adicional (nunca rebaixa status sozinho).
 */
class GameReadinessClassifierTest {
    private fun internet(
        download: Double? = 100.0,
        latencia: Double? = 20.0,
        jitter: Double? = 5.0,
        bufferbloat: Double? = 10.0,
        perda: Double? = 0.0,
        packetLossSource: String? = "modem",
        perdaConfianca: ConfiancaAmostral? = null,
    ) = InternetDiagnosticInput(
        downloadMbps = download,
        uploadMbps = 20.0,
        latencyMs = latencia,
        jitterMs = jitter,
        perdaPercentual = perda,
        bufferbloatMs = bufferbloat,
        packetLossSource = packetLossSource,
        perdaConfianca = perdaConfianca,
    )

    private fun wifiForte(
        banda: Int = 5200,
        rssi: Int = -50,
        linkSpeed: Int = 300,
    ) =
        WifiDiagnosticInput(rssiDbm = rssi, linkSpeedMbps = linkSpeed, frequenciaMhz = banda)

    private fun input(
        internet: InternetDiagnosticInput? = internet(),
        wifi: WifiDiagnosticInput? = wifiForte(),
        nat: NatStatus? = null,
        connectionType: ConnectionType = ConnectionType.wifi,
    ) = DiagnosticInput(connectionType = connectionType, internet = internet, wifi = wifi, natStatus = nat)

    // ── FPS competitivo: latencia<=50 jitter<=15 perda real 0% bufferbloat<=30 ──

    @Test
    fun `fps competitivo bom dentro de todas as faixas`() {
        val r = GameReadinessClassifier.classificar(Categoria.FPS_COMPETITIVO, input())
        assertEquals(ReadinessStatus.Bom, r.status)
    }

    @Test
    fun `fps competitivo atencao com latencia entre 51 e 100ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(latencia = 80.0)),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `fps competitivo ruim com latencia acima de 100ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(latencia = 150.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `fps competitivo ruim com jitter acima de 30ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(jitter = 35.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `fps competitivo ruim com perda confirmada maior ou igual a 1 por cento`() {
        // GH .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): o gate mudou de `packetLossSource == "modem"` (Provenance.medida,
        // inatingivel em producao — nunca ha captura real de pacote via timeout HTTP)
        // para `perdaConfianca == SUFICIENTE` (2+ timeouts/consecutivos/confirmados).
        // O comportamento esperado pelo teste original (perda >=1% "de verdade" deve
        // escalar a Ruim) e preservado — so o mecanismo que sinaliza "de verdade" mudou.
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(perda = 1.0, perdaConfianca = ConfiancaAmostral.SUFICIENTE)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `fps competitivo NAO fica ruim com perda isolada de confianca insuficiente mesmo maior ou igual a 1 por cento`() {
        // Caso novo pedido pelo Luiz: 1 timeout isolado (confianca insuficiente) nunca
        // escala sozinho a Ruim, mesmo que o percentual medido bata o corte de 1%.
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(perda = 1.0, perdaConfianca = ConfiancaAmostral.INSUFICIENTE)),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `fps competitivo atencao com perda estimada nao vira ruim sozinha`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(perda = 0.5, packetLossSource = "estimated")),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `fps competitivo ruim com bufferbloat acima de 100ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(internet = internet(bufferbloat = 150.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `fps competitivo rebaixa 1 nivel quando wifi fraco RSSI menor ou igual a -70`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(wifi = wifiForte(rssi = -75)),
            )
        // base seria Bom -> rebaixado para Atencao pela penalidade de wifi fraco.
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `fps competitivo sem dados retorna status nulo`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                DiagnosticInput(connectionType = ConnectionType.wifi),
            )
        assertNull(r.status)
    }

    // ── Cloud gaming: download>=50 latencia<=50 jitter<=15 perda 0% bufferbloat<=30 ─

    @Test
    fun `cloud gaming bom dentro de todas as faixas com wifi 5GHz forte`() {
        val r = GameReadinessClassifier.classificar(Categoria.CLOUD_GAMING, input())
        assertEquals(ReadinessStatus.Bom, r.status)
    }

    @Test
    fun `cloud gaming atencao com download entre 25 e 50Mbps`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.CLOUD_GAMING,
                input(internet = internet(download = 30.0)),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `cloud gaming ruim com download abaixo de 25Mbps`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.CLOUD_GAMING,
                input(internet = internet(download = 15.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `cloud gaming ruim com latencia acima de 80ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.CLOUD_GAMING,
                input(internet = internet(latencia = 100.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `cloud gaming rebaixa 1 nivel em 2,4GHz mesmo com metricas boas`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.CLOUD_GAMING,
                input(wifi = wifiForte(banda = 2437)),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `cloud gaming nao rebaixa em 5GHz forte`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.CLOUD_GAMING,
                input(wifi = wifiForte(banda = 5200, rssi = -50)),
            )
        assertEquals(ReadinessStatus.Bom, r.status)
    }

    // ── Mobile competitivo: RSSI>=-60 latencia<=60 jitter<=20 perda 0% ──────────

    @Test
    fun `mobile competitivo bom dentro de todas as faixas`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(internet = internet(latencia = 40.0, jitter = 10.0)),
            )
        assertEquals(ReadinessStatus.Bom, r.status)
    }

    @Test
    fun `mobile competitivo atencao com RSSI entre -61 e -72`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(wifi = wifiForte(rssi = -65), internet = internet(latencia = 40.0, jitter = 10.0)),
            )
        assertEquals(ReadinessStatus.Atencao, r.status)
    }

    @Test
    fun `mobile competitivo ruim com RSSI menor ou igual a -72`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(wifi = wifiForte(rssi = -80), internet = internet(latencia = 40.0, jitter = 10.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `mobile competitivo ruim com latencia acima de 120ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(internet = internet(latencia = 150.0, jitter = 10.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    @Test
    fun `mobile competitivo ruim com jitter acima de 35ms`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(internet = internet(latencia = 40.0, jitter = 40.0)),
            )
        assertEquals(ReadinessStatus.Ruim, r.status)
    }

    // ── NAT: evidencia adicional, nunca rebaixa status sozinho ──────────────────

    @Test
    fun `NAT CGNAT nao rebaixa status mas aparece como evidencia`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(nat = NatStatus.CGNAT),
            )
        assertEquals(ReadinessStatus.Bom, r.status)
        assertTrue(r.evidencias.any { it.contains("CGNAT", ignoreCase = true) })
    }

    @Test
    fun `NAT duplo nao rebaixa status mas aparece como evidencia`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.MOBILE_COMPETITIVO,
                input(nat = NatStatus.DOUBLE_NAT_OR_CGNAT, internet = internet(latencia = 40.0, jitter = 10.0)),
            )
        assertEquals(ReadinessStatus.Bom, r.status)
        assertTrue(r.evidencias.any { it.contains("NAT duplo", ignoreCase = true) })
    }

    @Test
    fun `NAT direto publico nao gera evidencia`() {
        val r =
            GameReadinessClassifier.classificar(
                Categoria.FPS_COMPETITIVO,
                input(nat = NatStatus.DIRECT_PUBLIC),
            )
        assertTrue(r.evidencias.none { it.contains("NAT", ignoreCase = true) })
    }

    // ── classificarTodos ──────────────────────────────────────────────────────

    @Test
    fun `classificarTodos retorna as 3 categorias`() {
        val resultados = GameReadinessClassifier.classificarTodos(input())
        assertEquals(3, resultados.size)
        assertEquals(setOf(Categoria.FPS_COMPETITIVO, Categoria.CLOUD_GAMING, Categoria.MOBILE_COMPETITIVO), resultados.map { it.categoria }.toSet())
    }
}
