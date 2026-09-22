package io.signallq.app.core.nds

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticContext
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DnsDiagnosticInput
import io.signallq.app.core.diagnostico.FibraDiagnosticInput
import io.signallq.app.core.diagnostico.HistoricalDiagnosticInput
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.MobileDiagnosticInput
import io.signallq.app.core.diagnostico.RedeWifiVizinha
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import io.signallq.app.core.diagnostico.WifiScanDiagnosticInput
import io.signallq.app.core.diagnostico.topology.model.NatStatus
import io.signallq.app.core.network.contracts.localdevice.DeviceCapabilities
import io.signallq.app.core.network.contracts.localdevice.DeviceType
import io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus
import io.signallq.app.core.network.contracts.localdevice.SafeLocalDeviceContext
import io.signallq.app.core.network.contracts.localdevice.SupportLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura da ponte `DiagnosticInput -> NdsDiagnosticsRequest` (NDS-02k, issue
 * #1759, item 4).
 */
class NdsDiagnosticsRequestMapperTest {
    @Test
    fun `input vazio gera request minimo, sem blocos opcionais`() {
        val input = DiagnosticInput(connectionType = ConnectionType.desconhecido, executionId = "exec-1")

        val request = input.toNdsDiagnosticsRequest(appVersion = "2.4.0")

        assertEquals("exec-1", request.requestId)
        assertEquals("io.signallq.app", request.app.id)
        assertEquals("2.4.0", request.app.version)
        assertNull(request.profile)
        assertEquals(listOf("scoring", "ai"), request.capabilities)
        assertEquals("UNKNOWN", request.connection?.type)
        assertNull(request.wifi)
        assertNull(request.wifiScan)
        assertNull(request.speed)
        assertNull(request.quality)
        assertNull(request.dns)
        assertNull(request.gateway)
        assertNull(request.fiber)
        assertNull(request.mobile)
        assertNull(request.historical)
        assertNull(request.localEquipment)
        assertNull(request.plan)
        assertNull(request.connection?.natStatus)
    }

    @Test
    fun `executionId em branco gera um requestId novo (UUID), nunca vazio`() {
        val input = DiagnosticInput(executionId = "")

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertTrue(request.requestId.isNotBlank())
        assertTrue("deveria parecer um UUID (36 chars, 4 hifens)", request.requestId.count { it == '-' } == 4)
    }

    @Test
    fun `wifi presente preenche bloco wifi e capabilities inclui wifi`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                executionId = "exec-2",
                wifi =
                    WifiDiagnosticInput(
                        rssiDbm = -65,
                        linkSpeedMbps = 433,
                        frequenciaMhz = 5180,
                        ssid = "MinhaRede_5G",
                        bssidMascarado = "00:14:22:01:23:45",
                        canal = 36,
                        wifiStandard = "802.11ac",
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("WIFI", request.connection?.type)
        assertEquals("MinhaRede_5G", request.connection?.ssid)
        assertEquals("00:14:22:01:23:45", request.connection?.bssid)
        assertEquals(-65, request.wifi?.rssi)
        assertEquals("5GHz", request.wifi?.band)
        assertEquals(36, request.wifi?.channel)
        assertEquals(433, request.wifi?.linkSpeed)
        assertEquals("802.11ac", request.wifi?.standard)
        assertTrue(request.capabilities.contains("wifi"))
    }

    @Test
    fun `fibra presente preenche bloco fiber e capabilities inclui fiber`() {
        val input =
            DiagnosticInput(
                fibra = FibraDiagnosticInput(rxPowerDbm = -22.0, txPowerDbm = 2.5, temperatureCelsius = 45.0, isUp = true),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(-22.0, request.fiber?.rxPowerDbm)
        assertEquals(2.5, request.fiber?.txPowerDbm)
        assertEquals(45.0, request.fiber?.temperatureC)
        assertNull("voltage nao existe em FibraDiagnosticInput -- gap conhecido", request.fiber?.voltageV)
        assertTrue(request.capabilities.contains("fiber"))
    }

    @Test
    fun `internet presente preenche bloco speed`() {
        val input =
            DiagnosticInput(
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 300.0,
                        uploadMbps = 150.0,
                        latencyMs = 12.0,
                        jitterMs = 2.0,
                        perdaPercentual = 0.5,
                        bufferbloatMs = 65.0,
                        rttGatewayMs = 4,
                        // .agents/architecture-plan.md ("Confiabilidade estatistica do
                        // diagnostico de rede", secao 8): sem confianca == SUFICIENTE,
                        // perdaPercentual fica omitido do payload NDS (ver testes
                        // dedicados de ConfiancaAmostral mais abaixo).
                        perdaConfianca = ConfiancaAmostral.SUFICIENTE,
                    ),
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 433, frequenciaMhz = 5180, dispositivosNaRede = 6),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(12.0, request.speed?.pingMs)
        assertEquals(2.0, request.speed?.jitterMs)
        assertEquals(300.0, request.speed?.downloadMbps)
        assertEquals(150.0, request.speed?.uploadMbps)
        assertEquals(0.5, request.speed?.packetLossPercent)
        assertEquals(12.0, request.quality?.latencyMs)
        assertEquals(2.0, request.quality?.jitterMs)
        assertEquals(0.5, request.quality?.packetLossPercent)
        assertEquals(77.0, request.quality?.loadedLatencyMs)
        assertEquals(65.0, request.quality?.bufferbloatMs)
        assertEquals(4, request.gateway?.rttGatewayMs)
        assertEquals(6, request.gateway?.connectedDevices)
        assertNull("sem packetLossSource informado na origem, campo fica omitido", request.speed?.packetLossSource)
    }

    // -------------------------------------------------------------------------
    // speed.packetLossSource (ADR-018 "Convenção de proveniência" — issue #1842)
    // -------------------------------------------------------------------------

    private fun internetComPerda(packetLossSource: String?): InternetDiagnosticInput =
        InternetDiagnosticInput(
            downloadMbps = 300.0,
            uploadMbps = 150.0,
            latencyMs = 12.0,
            jitterMs = 2.0,
            perdaPercentual = 2.1,
            packetLossSource = packetLossSource,
        )

    @Test
    fun `packetLossSource modem traduz para NdsProvenance MEASURED (medicao direta do equipamento)`() {
        val input = DiagnosticInput(internet = internetComPerda("modem"))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(NdsProvenance.MEASURED, request.speed?.packetLossSource)
    }

    @Test
    fun `packetLossSource estimated traduz para NdsProvenance ESTIMATED (indicio via timeout HTTP)`() {
        val input = DiagnosticInput(internet = internetComPerda("estimated"))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(NdsProvenance.ESTIMATED, request.speed?.packetLossSource)
    }

    @Test
    fun `packetLossSource unknown traduz para NdsProvenance UNKNOWN (coleta ja retorna incerteza)`() {
        val input = DiagnosticInput(internet = internetComPerda("unknown"))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(NdsProvenance.UNKNOWN, request.speed?.packetLossSource)
    }

    @Test
    fun `packetLossSource naoMedido omite o campo (nenhuma medicao rodou, nao e fonte desconhecida)`() {
        val input = DiagnosticInput(internet = internetComPerda("naoMedido"))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.speed?.packetLossSource)
    }

    @Test
    fun `packetLossSource null omite o campo`() {
        val input = DiagnosticInput(internet = internetComPerda(null))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.speed?.packetLossSource)
    }

    @Test
    fun `packetLossSource com valor nao reconhecido omite o campo (vocabulario fechado, nunca propaga string livre)`() {
        val input = DiagnosticInput(internet = internetComPerda("valor-desconhecido-futuro"))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.speed?.packetLossSource)
    }

    @Test
    fun `dns presente preenche bloco dns com hijacked sempre nulo (gap de coleta)`() {
        val input =
            DiagnosticInput(
                dns = DnsDiagnosticInput(currentDnsIp = "8.8.8.8", currentDnsLatencyMs = 35),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("8.8.8.8", request.dns?.primary)
        assertEquals(35, request.dns?.responseTimeMs)
        assertNull(request.dns?.hijacked)
    }

    // ── #1840 (ADR-018, bloco `dns` expandido) ──────────────────────────────────

    @Test
    fun `dns coerente preenche provedor, melhor dns, grade e nivel de coerencia`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "1.1.1.1",
                        currentDnsName = "Cloudflare",
                        currentDnsLatencyMs = 12,
                        bestDnsNameFromComparison = "Cloudflare",
                        bestDnsLatencyMsFromComparison = 12,
                        dnsGrade = "A",
                        dnsComparisonAvailable = true,
                        coerenciaNivelAlerta = "none",
                        coerenciaDivergenciasConsecutivas = 0,
                        coerenciaTaxaDivergenciaPercentual = 0.0,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("Cloudflare", request.dns?.providerName)
        assertEquals("Cloudflare", request.dns?.bestName)
        assertEquals(12, request.dns?.bestLatencyMs)
        assertEquals("A", request.dns?.grade)
        assertEquals(true, request.dns?.comparisonAvailable)
        assertEquals("none", request.dns?.coherenceAlertLevel)
        assertEquals(0, request.dns?.coherenceConsecutiveDivergences)
        assertEquals(0.0, request.dns?.coherenceDivergenceRatePercent)
        assertNull("hijacked continua sem coleta real", request.dns?.hijacked)
    }

    @Test
    fun `dns divergente reporta nivel critical e taxa de divergencia`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "200.200.200.200",
                        currentDnsName = "DNS do Provedor",
                        currentDnsLatencyMs = 90,
                        bestDnsNameFromComparison = "Cloudflare",
                        bestDnsLatencyMsFromComparison = 15,
                        dnsGrade = "C",
                        dnsComparisonAvailable = true,
                        coerenciaNivelAlerta = "critical",
                        coerenciaDivergenciasConsecutivas = 3,
                        coerenciaTaxaDivergenciaPercentual = 80.0,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("DNS do Provedor", request.dns?.providerName)
        assertEquals("Cloudflare", request.dns?.bestName)
        assertEquals(15, request.dns?.bestLatencyMs)
        assertEquals("critical", request.dns?.coherenceAlertLevel)
        assertEquals(3, request.dns?.coherenceConsecutiveDivergences)
        assertEquals(80.0, request.dns?.coherenceDivergenceRatePercent)
        assertNull("hijacked continua sem coleta real mesmo com divergencia critica", request.dns?.hijacked)
    }

    @Test
    fun `dns sem melhor dns calculado mantem bestName bestLatencyMs e grade nulos, comparisonAvailable false`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "8.8.8.8",
                        currentDnsName = "Google DNS",
                        currentDnsLatencyMs = 40,
                        dnsComparisonAvailable = false,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("Google DNS", request.dns?.providerName)
        assertNull(request.dns?.bestName)
        assertNull(request.dns?.bestLatencyMs)
        assertNull(request.dns?.grade)
        assertEquals(false, request.dns?.comparisonAvailable)

        val dnsJson = input.toNdsDiagnosticsRequest(appVersion = "1.0.0").toJson().getJSONObject("dns")
        assertFalse("bestName ausente na origem nunca vira chave", dnsJson.has("bestName"))
        assertFalse("bestLatencyMs ausente na origem nunca vira chave", dnsJson.has("bestLatencyMs"))
        assertFalse("grade ausente na origem nunca vira chave", dnsJson.has("grade"))
        assertEquals(
            "comparisonAvailable=false e um valor legitimo (comparacao nao rodou), nao ausencia -- sempre serializado",
            false,
            dnsJson.getBoolean("comparisonAvailable"),
        )
    }

    @Test
    fun `private dns ativo preenche privateDnsActive e hostname`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        privateDnsActive = true,
                        privateDnsHostname = "dns.google",
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(true, request.dns?.privateDnsActive)
        assertEquals("dns.google", request.dns?.privateDnsHostname)
    }

    @Test
    fun `private dns inativo preenche privateDnsActive false e hostname nulo`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "8.8.8.8",
                        privateDnsActive = false,
                        privateDnsHostname = null,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(false, request.dns?.privateDnsActive)
        assertNull(request.dns?.privateDnsHostname)
    }

    @Test
    fun `hijacked permanece nulo mesmo com todos os demais campos de dns preenchidos`() {
        val input =
            DiagnosticInput(
                dns =
                    DnsDiagnosticInput(
                        currentDnsIp = "1.1.1.1",
                        currentDnsName = "Cloudflare",
                        currentDnsLatencyMs = 12,
                        bestDnsNameFromComparison = "Cloudflare",
                        bestDnsLatencyMsFromComparison = 12,
                        dnsGrade = "A",
                        dnsComparisonAvailable = true,
                        coerenciaNivelAlerta = "attention",
                        coerenciaDivergenciasConsecutivas = 2,
                        coerenciaTaxaDivergenciaPercentual = 40.0,
                        privateDnsActive = true,
                        privateDnsHostname = "cloudflare-dns.com",
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(
            "nunca inventar hijacked=false so porque o resto do bloco esta completo -- so null representa 'nao sabemos'",
            request.dns?.hijacked,
        )
        val dnsJson = request.toJson().getJSONObject("dns")
        assertFalse("hijacked sem coleta real nunca vira chave no JSON", dnsJson.has("hijacked"))
    }

    @Test
    fun `perfilGamer true preenche profile gamer, false mantem omitido`() {
        val input = DiagnosticInput()

        assertEquals("gamer", input.toNdsDiagnosticsRequest(appVersion = "1.0.0", perfilGamer = true).profile)
        assertNull(input.toNdsDiagnosticsRequest(appVersion = "1.0.0", perfilGamer = false).profile)
    }

    @Test
    fun `wifiScan ausente quando DiagnosticInput nao carrega scan`() {
        val input = DiagnosticInput()

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.wifiScan)
    }

    @Test
    fun `wifiScan ausente quando scan nao tem nenhuma evidencia util`() {
        val input = DiagnosticInput(wifiScan = WifiScanDiagnosticInput())

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.wifiScan)
    }

    @Test
    fun `wifiScan com redes vizinhas chega ao payload com evidencia e entra em capabilities`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                wifi = WifiDiagnosticInput(rssiDbm = -60, linkSpeedMbps = 433, frequenciaMhz = 5180, canal = 36),
                wifiScan =
                    WifiScanDiagnosticInput(
                        conectadoCanal = 36,
                        conectadoBanda = io.signallq.app.core.diagnostico.BandaWifi.ghz5,
                        redes =
                            listOf(
                                RedeWifiVizinha(canal = 40, rssiDbm = -55, frequenciaMhz = 5200, bssid = "AA:BB:CC:00:00:01"),
                                RedeWifiVizinha(canal = 36, rssiDbm = -70, frequenciaMhz = 5180, bssid = "AA:BB:CC:00:00:02"),
                            ),
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(36, request.wifiScan?.connectedChannel)
        assertEquals(2, request.wifiScan?.neighborCount)
        assertEquals(2, request.wifiScan?.neighbors?.size)
        assertTrue(request.capabilities.contains("wifi_scan"))
    }

    @Test
    fun `tipos de conexao mapeiam para o vocabulario esperado`() {
        assertEquals("WIFI", DiagnosticInput(connectionType = ConnectionType.wifi).toNdsDiagnosticsRequest("1.0.0").connection?.type)
        assertEquals("MOBILE", DiagnosticInput(connectionType = ConnectionType.mobile).toNdsDiagnosticsRequest("1.0.0").connection?.type)
        assertEquals("ETHERNET", DiagnosticInput(connectionType = ConnectionType.ethernet).toNdsDiagnosticsRequest("1.0.0").connection?.type)
        assertEquals("DISCONNECTED", DiagnosticInput(connectionType = ConnectionType.desconectado).toNdsDiagnosticsRequest("1.0.0").connection?.type)
        assertEquals("UNKNOWN", DiagnosticInput(connectionType = ConnectionType.desconhecido).toNdsDiagnosticsRequest("1.0.0").connection?.type)
    }

    @Test
    fun `caracterizacao -- wifiScan preenchido nao muda serializacao dos outros blocos`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                executionId = "exec-caracterizacao",
                wifi =
                    WifiDiagnosticInput(
                        rssiDbm = -65,
                        linkSpeedMbps = 433,
                        frequenciaMhz = 5180,
                        ssid = "MinhaRede_5G",
                        bssidMascarado = "00:14:22:01:23:45",
                        canal = 36,
                        wifiStandard = "802.11ac",
                        dispositivosNaRede = 6,
                    ),
                wifiScan =
                    WifiScanDiagnosticInput(
                        conectadoCanal = 36,
                        redes = listOf(RedeWifiVizinha(canal = 40, rssiDbm = -55, frequenciaMhz = 5200, bssid = "AA:BB:CC:00:00:01")),
                    ),
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 300.0,
                        uploadMbps = 150.0,
                        latencyMs = 12.0,
                        jitterMs = 2.0,
                        perdaPercentual = 0.5,
                        bufferbloatMs = 65.0,
                        rttGatewayMs = 4,
                        perdaConfianca = ConfiancaAmostral.SUFICIENTE,
                    ),
                dns = DnsDiagnosticInput(currentDnsIp = "8.8.8.8", currentDnsLatencyMs = 35),
                fibra = FibraDiagnosticInput(rxPowerDbm = -22.0, txPowerDbm = 2.5, temperatureCelsius = 45.0, isUp = true),
            )

        val json = input.toNdsDiagnosticsRequest(appVersion = "1.0.0").toJson()

        // Blocos que nao pertencem a esta fatia (#1834) — mesmos valores que os
        // testes dedicados de cada bloco (acima) ja verificam, aqui conferidos via
        // JSON serializado para provar que a introducao do wifiScan real nao
        // alterou o shape ou o valor de nenhum outro bloco.
        val wifiJson = json.getJSONObject("wifi")
        assertEquals(-65, wifiJson.getInt("rssi"))
        assertEquals("5GHz", wifiJson.getString("band"))
        assertEquals(36, wifiJson.getInt("channel"))
        assertEquals(433, wifiJson.getInt("linkSpeed"))
        assertEquals("802.11ac", wifiJson.getString("standard"))

        val speedJson = json.getJSONObject("speed")
        assertEquals(12.0, speedJson.getDouble("ping_ms"), 0.0)
        assertEquals(300.0, speedJson.getDouble("download_mbps"), 0.0)
        assertEquals(150.0, speedJson.getDouble("upload_mbps"), 0.0)
        assertEquals(0.5, speedJson.getDouble("packet_loss_percent"), 0.0)

        val qualityJson = json.getJSONObject("quality")
        assertEquals(77.0, qualityJson.getDouble("loadedLatencyMs"), 0.0)
        assertEquals(65.0, qualityJson.getDouble("bufferbloatMs"), 0.0)

        val dnsJson = json.getJSONObject("dns")
        assertEquals("8.8.8.8", dnsJson.getString("primary"))
        assertEquals(35, dnsJson.getInt("latencyMs"))
        assertTrue("hijacked segue sem coleta -- nunca deve virar chave", !dnsJson.has("hijacked"))

        val gatewayJson = json.getJSONObject("gateway")
        assertEquals(4, gatewayJson.getInt("rttGatewayMs"))
        assertEquals(6, gatewayJson.getInt("connectedDevices"))

        val fiberJson = json.getJSONObject("fiber")
        assertEquals(-22.0, fiberJson.getDouble("rxPower_dbm"), 0.0)
        assertEquals(2.5, fiberJson.getDouble("txPower_dbm"), 0.0)
        assertEquals(45.0, fiberJson.getDouble("temperature_c"), 0.0)
        assertTrue("voltage segue sem coleta -- gap conhecido", !fiberJson.has("voltage_v"))

        // Bloco desta fatia: presente, com evidencia bruta e resultado calculado.
        val wifiScanJson = json.getJSONObject("wifiScan")
        assertEquals(36, wifiScanJson.getInt("connectedChannel"))
        assertEquals(1, wifiScanJson.getInt("neighborCount"))
        assertEquals(1, wifiScanJson.getJSONArray("neighbors").length())
    }

    @Test
    fun `mobile presente preenche bloco mobile e capabilities inclui mobile`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile =
                    MobileDiagnosticInput(
                        carrierName = "TIM",
                        mobileTechnology = "5G",
                        rsrpDbm = -101,
                        rsrqDb = -14,
                        sinrDb = 7,
                        band = "n78",
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("TIM", request.mobile?.operator)
        assertEquals("5G", request.mobile?.technology)
        assertEquals(-101, request.mobile?.rsrpDbm)
        assertEquals(-14, request.mobile?.rsrqDb)
        assertEquals(7, request.mobile?.sinrDb)
        assertEquals("n78", request.mobile?.band)
        assertTrue(request.capabilities.contains("mobile"))
    }

    @Test
    fun `mobile ausente quando DiagnosticInput nao carrega dados de rede movel`() {
        val input = DiagnosticInput(connectionType = ConnectionType.wifi)

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.mobile)
        assertTrue("sem bloco mobile, capability mobile nao deve aparecer", !request.capabilities.contains("mobile"))
    }

    @Test
    fun `mobile omitido quando snapshot foi capturado sem permissao de telefonia`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile =
                    MobileDiagnosticInput(
                        carrierName = "TIM",
                        // capturaReduzida=true (GH#1662): sem READ_PHONE_STATE so operadora/mcc/mnc
                        // sao lidos -- tecnologia/rsrp/rsrq/sinr sempre null nesse modo.
                        capturaReduzida = true,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull("ausencia de permissao deve omitir o bloco inteiro, nao so zerar campos", request.mobile)
        assertTrue(!request.capabilities.contains("mobile"))
    }

    @Test
    fun `mobile com tecnologia desconhecida ainda preenche os demais campos de sinal`() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.mobile,
                mobile =
                    MobileDiagnosticInput(
                        carrierName = "Vivo",
                        mobileTechnology = null,
                        rsrpDbm = -95,
                        rsrqDb = -12,
                        sinrDb = 3,
                        band = null,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("Vivo", request.mobile?.operator)
        assertNull(request.mobile?.technology)
        assertEquals(-95, request.mobile?.rsrpDbm)
        assertEquals(-12, request.mobile?.rsrqDb)
        assertEquals(3, request.mobile?.sinrDb)
        assertNull(request.mobile?.band)
        assertTrue(request.capabilities.contains("mobile"))
    }

    @Test
    fun `historico ausente quando DiagnosticInput nao carrega nenhum teste`() {
        val input = DiagnosticInput(historico = HistoricalDiagnosticInput(testsCount7d = 0, testsCount30d = 0))

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(request.historical)
    }

    @Test
    fun `historico presente preenche bloco historical e entra em capabilities`() {
        val input =
            DiagnosticInput(
                historico =
                    HistoricalDiagnosticInput(
                        avgDownload7d = 287.4,
                        avgUpload7d = 141.2,
                        avgPing7d = 19.2,
                        testsCount7d = 8,
                        avgDownload30d = 301.8,
                        avgUpload30d = 149.1,
                        avgPing30d = 17.5,
                        testsCount30d = 31,
                        degradationDetected = true,
                        degradationPercent = 18.3,
                    ),
            )

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(8, request.historical?.testsCount7d)
        assertEquals(287.4, request.historical?.avgDownload7d)
        assertEquals(31, request.historical?.testsCount30d)
        assertEquals(301.8, request.historical?.avgDownload30d)
        assertEquals(true, request.historical?.degradationDetected)
        assertEquals(18.3, request.historical?.degradationPercent)
        assertTrue(request.capabilities.contains("historical"))
    }

    @Test
    fun `contexto guiado preserva relato objetivo subcategoria e respostas estruturadas`() {
        val request =
            DiagnosticInput(
                executionId = "exec-context",
                context =
                    DiagnosticContext(
                        reportedProblem = "A conexão cai à noite.",
                        objective = "JOGOS_COM_LAG",
                        subcategory = "lag_horario_pico",
                        answers = mapOf("pergunta_0" to "resposta_1"),
                    ),
            ).toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("gamer", request.profile)
        assertTrue(request.capabilities.contains("usage_profiles"))
        assertEquals("A conexão cai à noite.", request.context?.reportedProblem)
        assertEquals("JOGOS_COM_LAG", request.context?.objective)
        assertEquals("lag_horario_pico", request.context?.subcategory)
        assertEquals(mapOf("pergunta_0" to "resposta_1"), request.context?.answers)
    }

    /** Contexto seguro completo (allowlist da issue-mãe #1832 seção 8) para os testes
     *  de `localEquipment` abaixo — mesmos valores do exemplo Nokia G-1425G-B/ONT. */
    private fun ontContextoCompleto(): SafeLocalDeviceContext =
        SafeLocalDeviceContext(
            vendor = "Nokia",
            modelo = "G-1425G-B",
            firmwareVersion = "3FE12345AA",
            deviceType = DeviceType.ONT_GPON,
            supportLevel = SupportLevel.LAB_VALIDATED,
            capabilities =
                DeviceCapabilities(
                    suportaFibra = true,
                    suportaWan = true,
                    suportaWifi = true,
                    suportaLan = true,
                    suportaClientes = true,
                ),
            connectionStatus = LocalDeviceSectionStatus.OK,
            statusFibra = LocalDeviceSectionStatus.OK,
            statusWan = LocalDeviceSectionStatus.OK,
            statusWifi = LocalDeviceSectionStatus.OK,
            statusLan = LocalDeviceSectionStatus.OK,
            quantidadeClientes = 7,
            warnings = emptyList(),
            coletadoEmEpochMs = 1_725_000_000_000L,
        )

    @Test
    fun `localEquipment presente preenche todos os campos e entra em capabilities`() {
        val input = DiagnosticInput(localDevice = ontContextoCompleto())

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("Nokia", request.localEquipment?.vendor)
        assertEquals("G-1425G-B", request.localEquipment?.model)
        assertEquals("3FE12345AA", request.localEquipment?.firmwareVersion)
        assertEquals("ONT_GPON", request.localEquipment?.deviceType)
        assertEquals("LAB_VALIDATED", request.localEquipment?.supportLevel)
        assertEquals("OK", request.localEquipment?.connectionStatus)
        assertEquals("OK", request.localEquipment?.fiberStatus)
        assertEquals("OK", request.localEquipment?.wanStatus)
        assertEquals("OK", request.localEquipment?.wifiStatus)
        assertEquals("OK", request.localEquipment?.lanStatus)
        assertEquals(7, request.localEquipment?.connectedClients)
        assertTrue(request.capabilities.contains("local_equipment"))
    }

    @Test
    fun `localEquipment ausente quando nenhum equipamento local suportado foi lido`() {
        val input = DiagnosticInput(localDevice = null)

        val request = input.toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull("ausencia de equipamento deve omitir o bloco inteiro, nunca zerar campos", request.localEquipment)
        assertFalse(request.capabilities.contains("local_equipment"))
    }

    @Test
    fun `localEquipment com campos opcionais ausentes ainda preenche os campos obrigatorios`() {
        val contextoParcial =
            SafeLocalDeviceContext(
                vendor = null,
                modelo = null,
                firmwareVersion = null,
                deviceType = DeviceType.UNKNOWN_SUPPORTED,
                supportLevel = SupportLevel.INFERRED_FAMILY,
                capabilities = DeviceCapabilities(),
                connectionStatus = LocalDeviceSectionStatus.ATENCAO,
                statusFibra = LocalDeviceSectionStatus.NAO_SUPORTADO,
                statusWan = LocalDeviceSectionStatus.INDISPONIVEL,
                statusWifi = LocalDeviceSectionStatus.NAO_SUPORTADO,
                statusLan = LocalDeviceSectionStatus.NAO_SUPORTADO,
                quantidadeClientes = 0,
                warnings = emptyList(),
                coletadoEmEpochMs = 1_725_000_000_000L,
            )
        val input = DiagnosticInput(localDevice = contextoParcial)

        val json = input.toNdsDiagnosticsRequest(appVersion = "1.0.0").toJson()
        val localEquipmentJson = json.getJSONObject("localEquipment")

        assertFalse("vendor ausente na fonte nunca vira chave", localEquipmentJson.has("vendor"))
        assertFalse("model ausente na fonte nunca vira chave", localEquipmentJson.has("model"))
        assertFalse("firmwareVersion ausente na fonte nunca vira chave", localEquipmentJson.has("firmwareVersion"))
        assertEquals("UNKNOWN_SUPPORTED", localEquipmentJson.getString("deviceType"))
        assertEquals("INFERRED_FAMILY", localEquipmentJson.getString("supportLevel"))
        assertEquals("ATENCAO", localEquipmentJson.getString("connectionStatus"))
        assertEquals("NAO_SUPORTADO", localEquipmentJson.getString("fiberStatus"))
        assertEquals("INDISPONIVEL", localEquipmentJson.getString("wanStatus"))
        assertEquals(0, localEquipmentJson.getInt("connectedClients"))
    }

    @Test
    fun `localEquipment serializado nunca carrega campo fora da allowlist`() {
        val json =
            DiagnosticInput(localDevice = ontContextoCompleto())
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")
                .toJson()
        val localEquipmentJson = json.getJSONObject("localEquipment")

        // Allowlist fechada: exatamente estas chaves, nunca senha/serial completo/
        // credenciais/payload HTML/token de sessao/MAC completo/lista crua de
        // clientes -- nenhuma dessas existe em NdsLocalEquipmentInfo, entao esta
        // asserção também documenta a garantia estrutural (o tipo Kotlin nem tem
        // campo para carregar esses dados), não só o valor do JSON.
        val chavesEsperadas =
            setOf(
                "vendor",
                "model",
                "firmwareVersion",
                "deviceType",
                "supportLevel",
                "connectionStatus",
                "fiberStatus",
                "wanStatus",
                "wifiStatus",
                "lanStatus",
                "connectedClients",
            )
        assertEquals(chavesEsperadas, localEquipmentJson.keys().asSequence().toSet())

        val chavesProibidas =
            listOf(
                "password",
                "senha",
                "serial",
                "serialNumber",
                "credentials",
                "credenciais",
                "html",
                "sessionToken",
                "token",
                "macaddress",
                "cookie",
                "clientlist",
                "warnings",
                "coletadoEmEpochMs",
            )
        val jsonTexto = localEquipmentJson.toString().lowercase()
        chavesProibidas.forEach { chave ->
            assertFalse(
                "campo proibido '$chave' nao pode aparecer no JSON de localEquipment",
                jsonTexto.contains(chave.lowercase()),
            )
        }
    }

    // --- connection.natStatus (ADR-018, NDS-Snapshot-09, issue #1841) ---

    @Test
    fun `natStatus DIRECT_PUBLIC (NAT simples) transcreve o nome do enum`() {
        val request =
            DiagnosticInput(natStatus = NatStatus.DIRECT_PUBLIC)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("DIRECT_PUBLIC", request.connection?.natStatus)
    }

    @Test
    fun `natStatus CGNAT transcreve o nome do enum`() {
        val request =
            DiagnosticInput(natStatus = NatStatus.CGNAT)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("CGNAT", request.connection?.natStatus)
    }

    @Test
    fun `natStatus DOUBLE_NAT_OR_CGNAT (NAT duplo) transcreve o nome do enum`() {
        val request =
            DiagnosticInput(natStatus = NatStatus.DOUBLE_NAT_OR_CGNAT)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("DOUBLE_NAT_OR_CGNAT", request.connection?.natStatus)
    }

    @Test
    fun `natStatus UNKNOWN (classificacao rodou mas foi inconclusiva) transcreve UNKNOWN`() {
        val request =
            DiagnosticInput(natStatus = NatStatus.UNKNOWN)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals("UNKNOWN", request.connection?.natStatus)
    }

    @Test
    fun `natStatus null (sem classificacao) omite o campo do JSON`() {
        val json =
            DiagnosticInput(natStatus = null)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")
                .toJson()
        val connectionJson = json.getJSONObject("connection")

        assertFalse(
            "natStatus null deve ser omitido, nunca virar chave com valor nulo/vazio",
            connectionJson.has("natStatus"),
        )
    }

    // --- bloco plan (ADR-018 bloco 14, NDS-Snapshot-09, issue #1841) ---

    @Test
    fun `plan presente quando usuario informou velocidade contratada`() {
        val request =
            DiagnosticInput(velocidadeContratadaMbps = 300)
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(300, request.plan?.contractedSpeedMbps)

        val json = request.toJson()
        assertEquals(300, json.getJSONObject("plan").getInt("contractedSpeedMbps"))
    }

    @Test
    fun `plan ausente quando usuario nunca informou velocidade contratada (nunca inferido)`() {
        val request =
            DiagnosticInput(
                velocidadeContratadaMbps = null,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 287.5,
                        uploadMbps = 45.0,
                        latencyMs = null,
                        jitterMs = null,
                        perdaPercentual = null,
                    ),
            ).toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(
            "ausencia de plano informado deve omitir o bloco inteiro, nunca inferir da velocidade medida",
            request.plan,
        )

        val json = request.toJson()
        assertFalse("bloco plan nao deve virar chave quando null", json.has("plan"))
    }

    // -------------------------------------------------------------------------
    // perdaConfianca (.agents/architecture-plan.md, "Confiabilidade estatistica do
    // diagnostico de rede", secao 8) — checkpoint 7 do plano de implementacao.
    // -------------------------------------------------------------------------

    private fun internetComPerdaEConfianca(perdaConfianca: ConfiancaAmostral?) =
        InternetDiagnosticInput(
            downloadMbps = 100.0,
            uploadMbps = 20.0,
            latencyMs = 15.0,
            jitterMs = 3.0,
            perdaPercentual = 5.0,
            bufferbloatMs = 10.0,
            packetLossSource = "estimated",
            perdaConfianca = perdaConfianca,
        )

    @Test
    fun `perdaPercentual omitido do payload NDS quando confianca amostral e insuficiente`() {
        val request =
            DiagnosticInput(internet = internetComPerdaEConfianca(ConfiancaAmostral.INSUFICIENTE))
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull("1 timeout isolado nao deve chegar a IA como percentual confiavel", request.speed?.packetLossPercent)
        assertNull(request.quality?.packetLossPercent)

        val json = request.toJson()
        assertFalse("speed.packet_loss_percent nao deve virar chave quando confianca insuficiente", json.getJSONObject("speed").has("packet_loss_percent"))
        assertFalse("quality.packetLossPercent nao deve virar chave quando confianca insuficiente", json.getJSONObject("quality").has("packetLossPercent"))
    }

    @Test
    fun `perdaPercentual omitido do payload NDS quando confianca amostral e desconhecida (historico legado)`() {
        val request =
            DiagnosticInput(internet = internetComPerdaEConfianca(perdaConfianca = null))
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertNull(
            "ausencia de perdaConfianca (historico legado sem o campo) e tratada como insuficiente, nunca inventada",
            request.speed?.packetLossPercent,
        )
        assertNull(request.quality?.packetLossPercent)
    }

    @Test
    fun `perdaPercentual reportado normalmente no payload NDS quando confianca amostral e suficiente`() {
        val request =
            DiagnosticInput(internet = internetComPerdaEConfianca(ConfiancaAmostral.SUFICIENTE))
                .toNdsDiagnosticsRequest(appVersion = "1.0.0")

        assertEquals(5.0, request.speed?.packetLossPercent)
        assertEquals(5.0, request.quality?.packetLossPercent)
    }
}
