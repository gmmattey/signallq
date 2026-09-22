package io.signallq.app.feature.diagnostico

import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticResult
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.diagnostico.DnsDiagnosticInput
import io.signallq.app.core.diagnostico.FibraDiagnosticInput
import io.signallq.app.core.diagnostico.FindingEngine
import io.signallq.app.core.diagnostico.FindingResult
import io.signallq.app.core.diagnostico.HistoricalDiagnosticInput
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.MobileDiagnosticInput
import io.signallq.app.core.diagnostico.RedeWifiVizinha
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import io.signallq.app.core.diagnostico.WifiQualityResult
import io.signallq.app.core.diagnostico.WifiScanDiagnosticInput
import io.signallq.app.core.network.contracts.topologia.NivelConfianca
import io.signallq.app.core.network.contracts.topologia.PapelTopologia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do [RecomendacaoPraticaEngine] (renomeado de `RecommendationEngine` na
 * Fatia 9a da auditoria #1228, corrige P1-1): as 12 situacoes documentadas na skill
 * `motor-diagnostico` (fase RecommendationEngine, SIG-287). Um teste de "mostrar" e
 * um de "nao mostrar" por situacao, no minimo.
 */
class RecomendacaoPraticaEngineTest {
    private fun achadosOk() =
        FindingEngine.analisar(
            internetResultados = emptyList(),
            wifiQuality = WifiQualityResult(emptyList(), confiavelParaTeste = true),
        )

    private fun achadoCritico(
        id: String,
        categoria: String,
    ) =
        FindingResult(
            principal =
                DiagnosticResult(
                    id = id,
                    titulo = id,
                    status = DiagnosticStatus.critical,
                    evidencia = null,
                    mensagemUsuario = "msg",
                    recomendacao = null,
                    categoria = categoria,
                ),
        )

    // -------------------------------------------------------------------------
    // 1. Trocar para Wi-Fi 5GHz
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 1 - mostra troca para 5GHz quando 2,4GHz com link baixo e 5GHz forte disponivel no mesmo SSID`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi =
                    WifiDiagnosticInput(
                        rssiDbm = -55,
                        linkSpeedMbps = 65,
                        frequenciaMhz = 2437,
                        ssid = "CasaWifi",
                    ),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 15.0,
                        uploadMbps = 10.0,
                        latencyMs = 20.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                    ),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(canal = 36, rssiDbm = -50, frequenciaMhz = 5180, ssid = "CasaWifi", bssid = "AA:BB:CC:11:22:33"),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-01" })
    }

    @Test
    fun `situacao 1 - nao mostra quando 5GHz disponivel esta fraco demais`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(canal = 36, rssiDbm = -80, frequenciaMhz = 5180, ssid = "CasaWifi", bssid = "AA:BB:CC:11:22:33"),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-01" })
    }

    // #980 (Fase 2B, passo 3) — evidencia "ouiConhecido" agora vem do motor unificado
    // (papelTopologia/confiancaTopologia por BSSID), nao mais de MeshOuiDatabase direto.

    @Test
    fun `situacao 1 - evidencia marca ouiConhecido=true quando papel e ROTEADOR com confianca ALTA`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(
                                    canal = 36,
                                    rssiDbm = -50,
                                    frequenciaMhz = 5180,
                                    ssid = "CasaWifi",
                                    bssid = "AA:BB:CC:11:22:33",
                                    papelTopologia = PapelTopologia.ROTEADOR,
                                    confiancaTopologia = NivelConfianca.ALTA,
                                ),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-01" }
        assertTrue(rec.evidencia?.contains("ouiConhecido=true") == true)
    }

    @Test
    fun `situacao 1 - evidencia marca ouiConhecido=false quando confianca e BAIXA mesmo com papel mesh`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(
                                    canal = 36,
                                    rssiDbm = -50,
                                    frequenciaMhz = 5180,
                                    ssid = "CasaWifi",
                                    bssid = "AA:BB:CC:11:22:33",
                                    papelTopologia = PapelTopologia.NO_MESH,
                                    confiancaTopologia = NivelConfianca.BAIXA,
                                ),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-01" }
        assertTrue(rec.evidencia?.contains("ouiConhecido=false") == true)
    }

    @Test
    fun `situacao 1 - evidencia marca ouiConhecido=false quando nao ha classificacao de topologia`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(canal = 36, rssiDbm = -50, frequenciaMhz = 5180, ssid = "CasaWifi", bssid = "AA:BB:CC:11:22:33"),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-01" }
        assertTrue(rec.evidencia?.contains("ouiConhecido=false") == true)
    }

    @Test
    fun `situacao 1 - nao mostra quando problema principal e de operadora`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
                wifiScan =
                    WifiScanDiagnosticInput(
                        redes =
                            listOf(
                                RedeWifiVizinha(canal = 36, rssiDbm = -50, frequenciaMhz = 5180, ssid = "CasaWifi", bssid = "AA:BB:CC:11:22:33"),
                            ),
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadoCritico("DECISAO-GW-01", "decisao"))
        assertFalse(r.any { it.id == "REC-01" })
    }

    // #897: cenario real de producao — sem wifiScan (nao preenchido no fluxo padrao,
    // que so roda o scan de vizinhanca em telas dedicadas de Wi-Fi) e ainda assim
    // deve recomendar.
    @Test
    fun `situacao 1 - mostra troca para 5GHz mesmo sem dado de scan de vizinhanca (bug 897)`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi =
                    WifiDiagnosticInput(
                        rssiDbm = -55,
                        linkSpeedMbps = 65,
                        frequenciaMhz = 2437,
                        ssid = "CasaWifi",
                        is5GhzCapable = true,
                    ),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 15.0,
                        uploadMbps = 10.0,
                        latencyMs = 20.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                    ),
                wifiScan = null,
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-01" })
    }

    @Test
    fun `situacao 1 - nao mostra quando sinal 2,4GHz atual ja esta fraco demais`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 65, frequenciaMhz = 2437, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-01" })
    }

    @Test
    fun `situacao 1 - nao mostra quando aparelho nao suporta 5GHz`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi =
                    WifiDiagnosticInput(
                        rssiDbm = -55,
                        linkSpeedMbps = 65,
                        frequenciaMhz = 2437,
                        ssid = "CasaWifi",
                        is5GhzCapable = false,
                    ),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-01" })
    }

    @Test
    fun `situacao 1 - nao mostra quando conectado em 5GHz`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 5180, ssid = "CasaWifi"),
                internet = InternetDiagnosticInput(downloadMbps = 15.0, uploadMbps = 10.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-01" })
    }

    // -------------------------------------------------------------------------
    // 2. Distancia do roteador / obstaculos
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 2 - mostra distancia do roteador quando rssi fraco e link speed baixo sem problema externo`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 24, frequenciaMhz = 2437),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-02" })
    }

    @Test
    fun `situacao 2 - nao mostra so com download baixo isolado sem rssi fraco`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 150, frequenciaMhz = 5180),
                internet = InternetDiagnosticInput(downloadMbps = 10.0, uploadMbps = 5.0, latencyMs = 20.0, jitterMs = 5.0, perdaPercentual = 0.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-02" })
    }

    // -------------------------------------------------------------------------
    // 3. Canal Wi-Fi congestionado
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 3 - mostra canal congestionado quando scan indica alternativa bem melhor`() {
        val wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 150, frequenciaMhz = 2412)
        val redesCongestionantes =
            (1..8).map {
                RedeWifiVizinha(canal = 1, rssiDbm = -40, frequenciaMhz = 2412, ssid = "vizinha$it", bssid = "00:11:22:33:44:0$it")
            }
        val scan = WifiScanDiagnosticInput(redes = redesCongestionantes, conectadoCanal = 1, conectadoBanda = BandaWifi.ghz24)
        val input = DiagnosticInput(connectionType = ConnectionType.wifi, wifi = wifi, wifiScan = scan)
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-03" })
    }

    @Test
    fun `situacao 3 - sugere Wi-Fi 6E ou 7 quando recorrente, nunca Wi-Fi 6 generico`() {
        val wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 150, frequenciaMhz = 2412)
        val redesCongestionantes =
            (1..8).map {
                RedeWifiVizinha(canal = 1, rssiDbm = -40, frequenciaMhz = 2412, ssid = "vizinha$it", bssid = "00:11:22:33:44:0$it")
            }
        val scan = WifiScanDiagnosticInput(redes = redesCongestionantes, conectadoCanal = 1, conectadoBanda = BandaWifi.ghz24)
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = wifi,
                wifiScan = scan,
                historico = HistoricalDiagnosticInput(degradationDetected = true),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-03" }
        assertTrue(rec.recomendacao!!.contains("Wi-Fi 6E/7"))
        assertFalse(rec.recomendacao!!.contains("Wi-Fi 6 "))
    }

    @Test
    fun `situacao 3 - nao mostra sem scan`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 150, frequenciaMhz = 2412),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-03" })
    }

    // -------------------------------------------------------------------------
    // 4. Roteador limitado
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 4 - mostra roteador limitado quando rssi bom mas link speed baixo e plano maior que enlace`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 65, frequenciaMhz = 5180),
                velocidadeContratadaMbps = 300,
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-04" })
    }

    @Test
    fun `situacao 4 - mostra roteador limitado quando padrao Wi-Fi 4 antigo`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 100, frequenciaMhz = 2412, wifiStandard = "Wi-Fi 4 (n)"),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-04" })
    }

    @Test
    fun `situacao 4 - nao mostra quando rssi bom, link speed bom e sem fatores extras`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 300, frequenciaMhz = 5180),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-04" })
    }

    // -------------------------------------------------------------------------
    // 5. Bufferbloat
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 5 - mostra bufferbloat atencao quando maior que 30ms`() {
        val input = DiagnosticInput(internet = InternetDiagnosticInput(downloadMbps = 100.0, uploadMbps = 20.0, latencyMs = 10.0, jitterMs = 2.0, perdaPercentual = 0.0, bufferbloatMs = 45.0))
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-05" }
        assertEquals(DiagnosticStatus.attention, rec.status)
    }

    @Test
    fun `situacao 5 - mostra bufferbloat critico quando maior que 100ms`() {
        val input = DiagnosticInput(internet = InternetDiagnosticInput(downloadMbps = 100.0, uploadMbps = 20.0, latencyMs = 10.0, jitterMs = 2.0, perdaPercentual = 0.0, bufferbloatMs = 150.0))
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-05" }
        assertEquals(DiagnosticStatus.critical, rec.status)
        assertTrue(rec.recomendacao!!.contains("QoS"))
    }

    @Test
    fun `situacao 5 - nao mostra quando bufferbloat abaixo de 30ms`() {
        val input = DiagnosticInput(internet = InternetDiagnosticInput(downloadMbps = 100.0, uploadMbps = 20.0, latencyMs = 10.0, jitterMs = 2.0, perdaPercentual = 0.0, bufferbloatMs = 10.0))
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-05" })
    }

    // -------------------------------------------------------------------------
    // 6. DNS lento
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 6 - mostra troca de DNS quando ha alternativa melhor com margem segura`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "8.8.8.8",
                        currentDnsName = "ProvedorX",
                        currentDnsLatencyMs = 120,
                        dnsComparisonAvailable = true,
                        bestDnsNameFromComparison = "Cloudflare",
                        bestDnsLatencyMsFromComparison = 20,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-06" }
        assertFalse("nao pode prometer aumento de velocidade contratada", rec.recomendacao!!.contains("velocidade contratada não"))
        assertTrue(rec.recomendacao!!.contains("não aumenta a velocidade contratada"))
    }

    @Test
    fun `situacao 6 - nao mostra quando ja esta no melhor DNS`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsName = "Cloudflare",
                        currentDnsLatencyMs = 60,
                        dnsComparisonAvailable = true,
                        bestDnsNameFromComparison = "Cloudflare",
                        bestDnsLatencyMsFromComparison = 58,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-06" })
    }

    // -------------------------------------------------------------------------
    // 7. Operadora / rota externa
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 7 - mostra suspeita de operadora quando gateway e wifi saudaveis mas latencia externa ruim`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 300, frequenciaMhz = 5180),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 180.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                        rttGatewayMs = 4,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-07" }
        assertTrue(rec.recomendacao!!.contains("pode estar"))
    }

    @Test
    fun `situacao 7 - nao mostra quando rssi wifi esta ruim (nao e confiavel culpar operadora)`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 300, frequenciaMhz = 5180),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 180.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                        rttGatewayMs = 4,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-07" })
    }

    @Test
    fun `situacao 7 - GH521 nao fala de roteador ou wifi quando conexao e movel`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 180.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                        rttGatewayMs = 4,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-07" }
        assertFalse(rec.mensagemUsuario.contains("roteador"))
        assertFalse(rec.mensagemUsuario.contains("Wi-Fi"))
    }

    // -------------------------------------------------------------------------
    // 8. Gateway / roteador lento
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 8 - mostra gateway lento quando rtt maior que 50ms`() {
        val input =
            DiagnosticInput(
                internet = InternetDiagnosticInput(downloadMbps = 80.0, uploadMbps = 20.0, latencyMs = 20.0, jitterMs = 3.0, perdaPercentual = 0.0, rttGatewayMs = 80),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-08" })
    }

    @Test
    fun `situacao 8 - nao mostra quando rtt gateway dentro do normal`() {
        val input =
            DiagnosticInput(
                internet = InternetDiagnosticInput(downloadMbps = 80.0, uploadMbps = 20.0, latencyMs = 20.0, jitterMs = 3.0, perdaPercentual = 0.0, rttGatewayMs = 15),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-08" })
    }

    @Test
    fun `situacao 8 - GH521 nao fala de roteador quando conexao e movel`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                internet = InternetDiagnosticInput(downloadMbps = 80.0, uploadMbps = 20.0, latencyMs = 20.0, jitterMs = 3.0, perdaPercentual = 0.0, rttGatewayMs = 80),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-08" }
        assertFalse(rec.mensagemUsuario.contains("roteador"))
        assertFalse(rec.recomendacao!!.contains("roteador"))
    }

    // -------------------------------------------------------------------------
    // 9. Fibra/ONT com problema
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 9 - mostra problema na fibra quando RX fora da faixa`() {
        val input =
            DiagnosticInput(
                fibra = FibraDiagnosticInput(rxPowerDbm = -30.0, txPowerDbm = 2.0, temperatureCelsius = 50.0, isUp = true),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-09" }
        assertEquals(DiagnosticStatus.critical, rec.status)
    }

    @Test
    fun `situacao 9 - mostra atencao quando temperatura elevada mas nao critica`() {
        val input =
            DiagnosticInput(
                fibra = FibraDiagnosticInput(rxPowerDbm = -20.0, txPowerDbm = 2.0, temperatureCelsius = 70.0, isUp = true),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-09" }
        assertEquals(DiagnosticStatus.attention, rec.status)
    }

    @Test
    fun `situacao 9 - nao mostra quando todos os indicadores da fibra estao bons`() {
        val input =
            DiagnosticInput(
                fibra = FibraDiagnosticInput(rxPowerDbm = -18.0, txPowerDbm = 2.0, temperatureCelsius = 40.0, isUp = true),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-09" })
    }

    // -------------------------------------------------------------------------
    // 10. Rede movel fraca
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 10 - mostra sinal movel fraco quando RSRP 4G ruim (abaixo de -100)`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile = MobileDiagnosticInput(mobileTechnology = "4G", rsrpDbm = -110),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-10" })
    }

    @Test
    fun `situacao 10 - mostra sinal movel fraco quando SINR 5G ruim (abaixo de 0)`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile = MobileDiagnosticInput(mobileTechnology = "5G", sinrDb = -2),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-10" })
    }

    @Test
    fun `situacao 10 - nao mostra quando metricas moveis estao boas`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile = MobileDiagnosticInput(mobileTechnology = "4G", rsrpDbm = -70, rsrqDb = -8, sinrDb = 25),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-10" })
    }

    @Test
    fun `situacao 10 - nao mostra quando conexao nao e movel`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                mobile = MobileDiagnosticInput(mobileTechnology = "4G", rsrpDbm = -110),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-10" })
    }

    // -------------------------------------------------------------------------
    // 11. Perda de pacotes
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 11 - mostra perda de pacotes como conclusao forte quando fonte e medicao confiavel`() {
        // .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): "critico" (usado por podeConcluir) agora exige tambem
        // perdaConfianca == SUFICIENTE, nao so packetLossSource != "estimated" — 1
        // timeout isolado nunca produzia essa combinacao em producao mesmo antes desta
        // mudanca (packetLossSource == "modem" e inatingivel via timeout HTTP).
        val input =
            DiagnosticInput(
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 3.0,
                        perdaPercentual = 4.0,
                        packetLossSource = "modem",
                        perdaConfianca = ConfiancaAmostral.SUFICIENTE,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-11" }
        assertTrue(rec.podeConcluir)
        assertFalse(rec.mensagemUsuario.contains("indício"))
    }

    @Test
    fun `situacao 11 - perda isolada de confianca insuficiente NAO produz conclusao forte mesmo com fonte nao-estimada`() {
        val input =
            DiagnosticInput(
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 3.0,
                        perdaPercentual = 4.0,
                        packetLossSource = "modem",
                        perdaConfianca = ConfiancaAmostral.INSUFICIENTE,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-11" }
        assertFalse(rec.podeConcluir)
    }

    @Test
    fun `situacao 11 - avisa que e indicio quando perda estimada por timeout HTTP`() {
        val input =
            DiagnosticInput(
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 3.0,
                        perdaPercentual = 4.0,
                        packetLossSource = "estimated",
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-11" }
        assertFalse("nao pode cravar certeza quando estimado", rec.podeConcluir)
        assertTrue(rec.mensagemUsuario.contains("indício"))
    }

    @Test
    fun `situacao 11 - GH521 nao fala de roteador ou modem quando conexao e movel`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 3.0,
                        perdaPercentual = 4.0,
                        packetLossSource = "modem",
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-11" }
        assertFalse(rec.recomendacao!!.contains("roteador"))
        assertFalse(rec.recomendacao!!.contains("modem"))
    }

    @Test
    fun `situacao 11 - nao mostra quando fonte e naoMedido`() {
        val input =
            DiagnosticInput(
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 3.0,
                        perdaPercentual = 4.0,
                        packetLossSource = "naoMedido",
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-11" })
    }

    // -------------------------------------------------------------------------
    // 12. Score geral
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 12 - mostra recomendacao-resumo quando multiplos fatores convergem`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 300, frequenciaMhz = 5180),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 80.0,
                        uploadMbps = 20.0,
                        latencyMs = 10.0,
                        jitterMs = 3.0,
                        perdaPercentual = 0.0,
                        bufferbloatMs = 150.0,
                        rttGatewayMs = 80,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadoCritico("DECISAO-02", "decisao"))
        assertTrue(r.any { it.id == "REC-12" })
    }

    @Test
    fun `situacao 12 - nao mostra quando ha apenas um fator isolado`() {
        val input =
            DiagnosticInput(
                internet = InternetDiagnosticInput(downloadMbps = 100.0, uploadMbps = 20.0, latencyMs = 10.0, jitterMs = 2.0, perdaPercentual = 0.0, bufferbloatMs = 45.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-12" })
    }

    // -------------------------------------------------------------------------
    // 13. Preset de device para jogos (SIG-290)
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 13 - mostra recomendacao de device quando fps competitivo esta ruim`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -50, linkSpeedMbps = 300, frequenciaMhz = 5200),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 100.0,
                        uploadMbps = 20.0,
                        latencyMs = 150.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-13" })
    }

    @Test
    fun `situacao 13 - usa dica especifica do xbox quando device selecionado`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -50, linkSpeedMbps = 300, frequenciaMhz = 5200),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 100.0,
                        uploadMbps = 20.0,
                        latencyMs = 150.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                    ),
                deviceGamingSelecionado = "xbox",
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        val rec = r.first { it.id == "REC-13" }
        assertTrue(rec.recomendacao?.contains("NAT", ignoreCase = true) == true)
    }

    @Test
    fun `situacao 13 - nao mostra quando as 3 categorias de jogos estao boas`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -50, linkSpeedMbps = 300, frequenciaMhz = 5200),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 100.0,
                        uploadMbps = 20.0,
                        latencyMs = 20.0,
                        jitterMs = 5.0,
                        perdaPercentual = 0.0,
                        bufferbloatMs = 10.0,
                    ),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-13" })
    }

    // -------------------------------------------------------------------------
    // 14. Upgrade de roteador/mesh — somente com recorrencia
    // -------------------------------------------------------------------------

    @Test
    fun `situacao 14 - mostra upgrade de roteador quando wifi fraco recorrente no historico`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 30, frequenciaMhz = 2437),
                historico = HistoricalDiagnosticInput(degradationDetected = true, degradationPercent = 35.0),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertTrue(r.any { it.id == "REC-14" })
    }

    @Test
    fun `situacao 14 - nao mostra upgrade no primeiro teste isolado sem historico de degradacao`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 30, frequenciaMhz = 2437),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-14" })
    }

    @Test
    fun `situacao 14 - nao mostra upgrade quando historico existe mas sem degradacao detectada`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -78, linkSpeedMbps = 30, frequenciaMhz = 2437),
                historico = HistoricalDiagnosticInput(degradationDetected = false),
            )
        val r = RecomendacaoPraticaEngine.recomendar(input, achadosOk())
        assertFalse(r.any { it.id == "REC-14" })
    }

    // -------------------------------------------------------------------------
    // Sanidade: sem input nenhum, engine nao quebra e nao gera recomendacoes
    // -------------------------------------------------------------------------

    @Test
    fun `sem nenhum dado de input, nao gera nenhuma recomendacao`() {
        val r = RecomendacaoPraticaEngine.recomendar(DiagnosticInput(), achadosOk())
        assertTrue(r.isEmpty())
    }

    @Test
    fun `campos nulos nao quebram o engine`() {
        val r =
            RecomendacaoPraticaEngine.recomendar(
                DiagnosticInput(connectionType = ConnectionType.desconhecido),
                achadosOk(),
            )
        assertNull(r.firstOrNull { it.id == "REC-10" })
    }
}
