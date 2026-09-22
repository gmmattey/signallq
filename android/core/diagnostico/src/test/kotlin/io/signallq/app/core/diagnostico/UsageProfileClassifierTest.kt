package io.signallq.app.core.diagnostico

import io.signallq.app.core.diagnostico.UsageProfileClassifier.Perfil
import io.signallq.app.core.diagnostico.UsageProfileClassifier.UsageProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do [UsageProfileClassifier] (SIG-289): pesos/faixas exatos por perfil de
 * uso (aba 9), penalidades transversais (Wi-Fi fraco, perda estimada) e
 * reponderacao/ausencia de dados.
 */
class UsageProfileClassifierTest {
    private fun internetOk(
        download: Double = 100.0,
        upload: Double = 20.0,
        latencia: Double = 20.0,
        jitter: Double = 5.0,
        perda: Double = 0.0,
        bufferbloat: Double = 10.0,
        packetLossSource: String? = "modem",
        perdaConfianca: ConfiancaAmostral? = null,
    ) = InternetDiagnosticInput(
        downloadMbps = download,
        uploadMbps = upload,
        latencyMs = latencia,
        jitterMs = jitter,
        perdaPercentual = perda,
        bufferbloatMs = bufferbloat,
        packetLossSource = packetLossSource,
        perdaConfianca = perdaConfianca,
    )

    private fun input(
        internet: InternetDiagnosticInput? = internetOk(),
        dnsMs: Int? = 30,
        wifi: WifiDiagnosticInput? = WifiDiagnosticInput(rssiDbm = -50, linkSpeedMbps = 300, frequenciaMhz = 5200),
        degradacao: Double? = null,
    ) = DiagnosticInput(
        connectionType = ConnectionType.wifi,
        internet = internet,
        wifi = wifi,
        dns = dnsMs?.let { DnsDiagnosticInput(currentDnsLatencyMs = it) },
        historico = degradacao?.let { HistoricalDiagnosticInput(degradationDetected = true, degradationPercent = it) },
    )

    // ── Navegacao: DNS 35% + latencia 25% + perda 20% + jitter 10% + download 10% ──

    @Test
    fun `navegacao OK dentro de todas as faixas`() {
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input())
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `navegacao instavel com dns entre 51 e 150ms`() {
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input(dnsMs = 100))
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    @Test
    fun `navegacao comprometida com dns acima de 150ms`() {
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input(dnsMs = 200))
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `navegacao comprometida com perda confirmada maior ou igual a 1 por cento`() {
        // .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): gate migrado de packetLossSource == "modem" (Provenance.medida,
        // inatingivel em producao) para perdaConfianca == SUFICIENTE (2+
        // timeouts/consecutivos/confirmados). Comportamento esperado preservado — perda
        // "de verdade" (agora: confirmada) continua escalando a Comprometido.
        val r =
            UsageProfileClassifier.classificar(
                Perfil.NAVEGACAO,
                input(internet = internetOk(perda = 1.0, perdaConfianca = ConfiancaAmostral.SUFICIENTE)),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `navegacao NAO fica comprometida com perda isolada de confianca insuficiente mesmo maior ou igual a 1 por cento`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.NAVEGACAO,
                input(internet = internetOk(perda = 1.0, perdaConfianca = ConfiancaAmostral.INSUFICIENTE)),
            )
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    // ── Streaming: download 45% + bufferbloat 25% + perda 15% + jitter 10% + historico 5% ──

    @Test
    fun `streaming OK com download alto e bufferbloat baixo`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.STREAMING,
                input(internet = internetOk(download = 30.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `streaming nao fica OK so porque HD esta OK quando download esta abaixo do limiar 4K`() {
        // 20Mbps: acima do limiar HD (>=10) mas abaixo do limiar 4K OK (>=25) -> Instavel (faixa 15-25 do 4K).
        val r =
            UsageProfileClassifier.classificar(
                Perfil.STREAMING,
                input(internet = internetOk(download = 20.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    @Test
    fun `streaming comprometido com bufferbloat acima de 100ms`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.STREAMING,
                input(internet = internetOk(download = 30.0, bufferbloat = 150.0)),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    // ── Jogos: latencia 35% + jitter 25% + perda 25% + bufferbloat 10% + banda 5% ──

    @Test
    fun `jogos OK dentro das faixas`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.JOGOS,
                input(internet = internetOk(latencia = 40.0, jitter = 10.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `jogos nao fica OK so por causa de download alto quando latencia esta ruim`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.JOGOS,
                input(internet = internetOk(download = 500.0, latencia = 150.0, jitter = 10.0, bufferbloat = 10.0)),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `jogos instavel com jitter entre 16 e 30ms`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.JOGOS,
                input(internet = internetOk(latencia = 40.0, jitter = 25.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    // ── Videochamada: upload 30% + jitter 25% + perda 25% + latencia 10% + bufferbloat 10% ──

    @Test
    fun `videochamada OK com upload maior ou igual a 5Mbps`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.VIDEOCHAMADA,
                input(internet = internetOk(upload = 6.0, latencia = 50.0, jitter = 10.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `videochamada comprometida com upload abaixo de 2Mbps`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.VIDEOCHAMADA,
                input(internet = internetOk(upload = 1.0, latencia = 50.0, jitter = 10.0, bufferbloat = 20.0)),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    // ── Trabalho: estabilidade 35% + DNS 20% + upload 15% + latencia 15% + historico 15% ──

    @Test
    fun `trabalho OK sem perda real e metricas dentro da faixa`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.TRABALHO,
                input(internet = internetOk(upload = 10.0, latencia = 50.0), dnsMs = 30),
            )
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `trabalho comprometido com degradacao historica maior ou igual a 40 por cento`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.TRABALHO,
                input(internet = internetOk(upload = 10.0, latencia = 50.0), dnsMs = 30, degradacao = 45.0),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `trabalho instavel com degradacao historica entre 20 e 40 por cento`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.TRABALHO,
                input(internet = internetOk(upload = 10.0, latencia = 50.0), dnsMs = 30, degradacao = 25.0),
            )
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    // ── Penalidades transversais ─────────────────────────────────────────────

    @Test
    fun `wifi fraco rebaixa navegacao OK para instavel`() {
        val wifiFraco = WifiDiagnosticInput(rssiDbm = -76, linkSpeedMbps = 100, frequenciaMhz = 5200)
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input(wifi = wifiFraco))
        assertEquals(UsageProfileStatus.Instavel, r.status)
    }

    @Test
    fun `rssi muito fraco compromete streaming diretamente`() {
        val wifiFraco = WifiDiagnosticInput(rssiDbm = -80, linkSpeedMbps = 100, frequenciaMhz = 5200)
        val r =
            UsageProfileClassifier.classificar(
                Perfil.STREAMING,
                input(internet = internetOk(download = 30.0, bufferbloat = 20.0), wifi = wifiFraco),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `rssi muito fraco compromete videochamada diretamente`() {
        val wifiFraco = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 100, frequenciaMhz = 5200)
        val r =
            UsageProfileClassifier.classificar(
                Perfil.VIDEOCHAMADA,
                input(internet = internetOk(upload = 10.0), wifi = wifiFraco),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `rssi muito fraco compromete jogos diretamente`() {
        val wifiFraco = WifiDiagnosticInput(rssiDbm = -77, linkSpeedMbps = 100, frequenciaMhz = 5200)
        val r =
            UsageProfileClassifier.classificar(
                Perfil.JOGOS,
                input(internet = internetOk(latencia = 30.0, jitter = 5.0), wifi = wifiFraco),
            )
        assertEquals(UsageProfileStatus.Comprometido, r.status)
    }

    @Test
    fun `wifi bom nao aplica nenhuma penalidade`() {
        val wifiBom = WifiDiagnosticInput(rssiDbm = -50, linkSpeedMbps = 300, frequenciaMhz = 5200)
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input(wifi = wifiBom))
        assertEquals(UsageProfileStatus.OK, r.status)
    }

    @Test
    fun `perda estimada nao eleva status a comprometido sozinha`() {
        val r =
            UsageProfileClassifier.classificar(
                Perfil.NAVEGACAO,
                input(internet = internetOk(perda = 2.0, packetLossSource = "estimated")),
            )
        // Perda estimada (nao medida real) nunca vira Comprometido sozinha.
        assertTrue(r.status != UsageProfileStatus.Comprometido)
    }

    @Test
    fun `perda estimada reduz confianca mas nao necessariamente o status`() {
        val comEstimada =
            UsageProfileClassifier.classificar(
                Perfil.NAVEGACAO,
                input(internet = internetOk(perda = 0.5, packetLossSource = "estimated")),
            )
        val semPerda = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input())
        assertTrue(comEstimada.confianca < semPerda.confianca)
    }

    // ── Reponderacao / dados ausentes ────────────────────────────────────────

    @Test
    fun `perfil sem nenhuma dimensao disponivel retorna status nulo`() {
        val vazio = DiagnosticInput(connectionType = ConnectionType.desconhecido)
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, vazio)
        assertNull(r.status)
        assertNull(r.score)
    }

    @Test
    fun `dimensao ausente nao vira nota artificial e aparece em dadosAusentes`() {
        val semDns = input(dnsMs = null)
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, semDns)
        assertTrue("dns" in r.dadosAusentes)
        assertEquals(UsageProfileStatus.OK, r.status) // resto das dimensoes ok
    }

    @Test
    fun `classificarTodos retorna os 5 perfis`() {
        val resultados = UsageProfileClassifier.classificarTodos(input())
        assertEquals(5, resultados.size)
        assertEquals(setOf(Perfil.NAVEGACAO, Perfil.STREAMING, Perfil.JOGOS, Perfil.VIDEOCHAMADA, Perfil.TRABALHO), resultados.map { it.perfil }.toSet())
    }

    @Test
    fun `acao recomendada ausente quando status OK`() {
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input())
        assertNull(r.acaoRecomendada)
    }

    @Test
    fun `acao recomendada presente quando status nao OK`() {
        val r = UsageProfileClassifier.classificar(Perfil.NAVEGACAO, input(dnsMs = 200))
        assertTrue(!r.acaoRecomendada.isNullOrBlank())
    }
}
