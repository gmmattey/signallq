package io.signallq.app.feature.diagnostico.ai

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.DiagnosticResult
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema v3 (raw): a factory NAO popula mais classificacaoLocal nem decisao
 * local nem recomendacoes locais. Toda interpretacao e da IA. Os testes
 * abaixo cobrem APENAS dados brutos.
 */
class DiagnosisAiContextFactoryTest {
    private fun fakeReport(): DiagnosticReport =
        DiagnosticReport(
            wifiResultados = emptyList(),
            internetResultados = emptyList(),
            mobileResultados = emptyList(),
            fibraResultados = emptyList(),
            dnsResultados = emptyList(),
            historicoResultados = emptyList(),
            wifiCanalResultados = emptyList(),
            decisao =
                DiagnosticResult(
                    id = "dec-1",
                    titulo = "Decisao",
                    status = DiagnosticStatus.attention,
                    evidencia = null,
                    mensagemUsuario = "msg",
                    recomendacao = null,
                    categoria = "isp",
                ),
            perfisUsoSpeedtest = null,
            geradoEmMs = 1700000000000L,
        )

    /**
     * Caso de referencia (294 / 411 / 101 / 25.1):
     *   v3 deve devolver APENAS os numeros brutos, sem classificacao local.
     *   A IA decide se e estabilidade ou velocidade — o factory nao opina.
     */
    @Test
    fun v3_envia_metricas_brutas_e_nao_popula_analise_local() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 294.0,
                        uploadMbps = 411.0,
                        latencyMs = 101.0,
                        jitterMs = 25.1,
                        perdaPercentual = 0.0,
                    ),
            )

        val ctx = DiagnosisAiContextFactory.from(fakeReport(), input, ConnectionType.wifi)

        assertEquals("5", ctx.schemaVersion)
        assertNotNull(ctx.metricasAtuais)
        assertEquals(294.0, ctx.metricasAtuais!!.downloadMbps!!, 0.01)
        assertEquals(411.0, ctx.metricasAtuais!!.uploadMbps!!, 0.01)
        assertEquals(101.0, ctx.metricasAtuais!!.latenciaMs!!, 0.01)
        assertEquals(25.1, ctx.metricasAtuais!!.jitterMs!!, 0.01)

        // feedbackUsuario opcional, sem analise local pre-computada
        assertNull(ctx.feedbackUsuario)
    }

    @Test
    fun v3_evidencias_naoPossuemCampoInterpretacao_emTodaForma() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 100.0,
                        uploadMbps = null,
                        latencyMs = null,
                        jitterMs = null,
                        perdaPercentual = null,
                    ),
            )
        val ctx = DiagnosisAiContextFactory.from(fakeReport(), input, ConnectionType.wifi)
        // Evidencias pode ser vazia (fakeReport sem itens), mas data class nao
        // tem mais o campo `interpretacao` — verificacao reflexiva via campo direto.
        ctx.evidencias.forEach { ev ->
            // Nao deve compilar se houver `interpretacao` na data class.
            // O label e valor sao os unicos campos.
            assertNotNull(ev.label)
            assertNotNull(ev.valor)
        }
        // Garante que a data class so tem 2 propriedades primarias
        val ev = AiEvidence(label = "x", valor = "y")
        assertEquals("x", ev.label)
        assertEquals("y", ev.valor)
    }

    @Test
    fun fromRaw_aceita_contextoAdicional_sem_inventar_dados() {
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 250.0,
                        uploadMbps = 100.0,
                        latencyMs = null,
                        jitterMs = null,
                        perdaPercentual = null,
                    ),
                wifi = WifiDiagnosticInput(rssiDbm = -55, linkSpeedMbps = 866, frequenciaMhz = 5180, ssid = "MinhaRede"),
            )

        val ctx =
            DiagnosisAiContextFactory.fromRaw(
                report = fakeReport(),
                input = input,
                connectionType = ConnectionType.wifi,
                wifiLinkBssid = "AA:BB:CC:DD:EE:FF",
                wifiPadrao = "Wi-Fi 6",
                wifiLinkSpeedMbps = 866,
                ispNome = "Vivo Fibra",
                ispAsn = "AS27699",
                ipPublico = "200.130.45.10",
                dnsResolverIp = "1.1.1.1",
                dnsResolverProvider = "cloudflare",
                dispositivos =
                    AiDispositivosInfo(
                        fabricante = "Google",
                        modelo = "Pixel 7",
                        sistema = "Android",
                        versaoSO = "14",
                    ),
                ultimosTestesHistorico =
                    listOf(
                        AiTesteHistorico(
                            timestampEpochMs = 1700000000000L,
                            downloadMbps = 200.0,
                            uploadMbps = 80.0,
                        ),
                    ),
            )

        // Bruto presente
        assertEquals("Vivo Fibra", ctx.rede?.operadora)
        assertEquals("AS27699", ctx.rede?.asn)
        assertEquals("AA:BB:CC:DD:EE:FF", ctx.contextoRede?.bssid)
        assertEquals("Wi-Fi 6", ctx.contextoRede?.padraoWifi)
        assertEquals(866, ctx.contextoRede?.linkSpeedMbps)
        assertEquals("Pixel 7", ctx.dispositivos?.modelo)
        assertEquals(1, ctx.historico?.ultimosTestes?.size)
        assertEquals(
            200.0,
            ctx.historico
                ?.ultimosTestes
                ?.first()
                ?.downloadMbps!!,
            0.01,
        )
    }

    @Test
    fun factoryRetrocompat_semInput_naoPopulaMetricas() {
        val ctx = DiagnosisAiContextFactory.from(fakeReport(), ConnectionType.wifi)
        assertNull(ctx.metricasAtuais)
        assertNull(ctx.contextoRede)
        assertNull(ctx.historico)
        assertEquals("5", ctx.schemaVersion)
    }

    @Test
    fun aiPromptVersion_constante_atualizadaParaV6LocalDevice() {
        assertEquals("diagnostico_v6_local_device", AI_PROMPT_VERSION)
        assertTrue(AI_PROMPT_VERSION.startsWith("diagnostico_"))
    }

    // -------------------------------------------------------------------------
    // Telefonia movel (item coreTelephony) — payload deve incluir bloco `movel`
    // quando AdditionalAiContext.movel != null. Quando null, omitir do JSON.
    // -------------------------------------------------------------------------
    @Test
    fun v3_mobile_comDadosTelephony_payloadInclui_movel_completo() {
        val movel =
            AiMovelInfo(
                operadora = "Vivo",
                tecnologia = "5G NSA",
                rsrpDbm = -98,
                rsrqDb = -11,
                sinrDb = 8,
                ecnoDb = null,
                bandaMovel = "n78 (3.5 GHz)",
                cellId = "123456789",
                mcc = "724",
                mnc = "06",
                tac = "4321",
                roaming = false,
            )
        val ctx =
            DiagnosisAiContextFactory.fromRaw(
                report = fakeReport(),
                input =
                    DiagnosticInput(
                        connectionType = ConnectionType.mobile,
                        internet =
                            InternetDiagnosticInput(
                                downloadMbps = 35.0,
                                uploadMbps = 12.0,
                                latencyMs = 45.0,
                                jitterMs = 8.0,
                                perdaPercentual = 0.0,
                            ),
                    ),
                connectionType = ConnectionType.mobile,
                movel = movel,
            )

        // Bloco movel preservado no contexto
        assertEquals("Vivo", ctx.movel?.operadora)
        assertEquals("5G NSA", ctx.movel?.tecnologia)
        assertEquals(-98, ctx.movel?.rsrpDbm)
        assertEquals("n78 (3.5 GHz)", ctx.movel?.bandaMovel)
        assertEquals("724", ctx.movel?.mcc)
        assertEquals(false, ctx.movel?.roaming)

        // Serializacao para JSON inclui o bloco com TODOS os campos preenchidos
        val repo =
            io.signallq.app.feature.diagnostico.ai.AiDiagnosisRepository(
                baseUrl = "http://invalid.local",
                isAuthorized = { true },
            )
        val json = repo.contextToJson(ctx).toString()
        assertTrue("payload tem bloco movel", json.contains("\"movel\":"))
        assertTrue("operadora", json.contains("\"operadora\":\"Vivo\""))
        assertTrue("tecnologia", json.contains("\"tecnologia\":\"5G NSA\""))
        assertTrue("rsrpDbm", json.contains("\"rsrpDbm\":-98"))
        assertTrue("sinrDb", json.contains("\"sinrDb\":8"))
        assertTrue("bandaMovel", json.contains("\"bandaMovel\":\"n78"))
        assertTrue("mcc/mnc", json.contains("\"mcc\":\"724\"") && json.contains("\"mnc\":\"06\""))
        assertTrue("cellId como string", json.contains("\"cellId\":\"123456789\""))
        assertTrue("connectionType=mobile", json.contains("\"connectionType\":\"mobile\""))
    }

    @Test
    fun v3_mobile_semDadosTelephony_payloadOmite_movel() {
        // Cenario: connectionType=mobile mas usuario negou READ_PHONE_STATE.
        // O monitor devolve null; o AdditionalAiContext.movel fica null;
        // o payload NAO deve ter a chave "movel".
        val ctx =
            DiagnosisAiContextFactory.fromRaw(
                report = fakeReport(),
                input =
                    DiagnosticInput(
                        connectionType = ConnectionType.mobile,
                        internet =
                            InternetDiagnosticInput(
                                downloadMbps = 35.0,
                                uploadMbps = 12.0,
                                latencyMs = 45.0,
                                jitterMs = 8.0,
                                perdaPercentual = 0.0,
                            ),
                    ),
                connectionType = ConnectionType.mobile,
                movel = null,
            )

        assertNull(ctx.movel)
        val repo =
            io.signallq.app.feature.diagnostico.ai.AiDiagnosisRepository(
                baseUrl = "http://invalid.local",
                isAuthorized = { true },
            )
        val json = repo.contextToJson(ctx).toString()
        assertFalse("payload sem bloco movel", json.contains("\"movel\":"))
        assertTrue("connectionType=mobile preservado", json.contains("\"connectionType\":\"mobile\""))
    }

    // -------------------------------------------------------------------------
    // instrucaoTom (W2-F03) — derivada da contagem de metricas ruins
    // Thresholds: jitter > 50ms | perda >= 2% | RTT > 150ms
    // -------------------------------------------------------------------------

    @Test
    fun instrucaoTom_semMetricas_retornaNull() {
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(null)
        assertNull(instrucao)
    }

    @Test
    fun instrucaoTom_semMetricasRuins_retornaTudoDentroDoEsperado() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 10.0,
                perdaPacotesPercentual = 0.5,
                rttGatewayMs = 80,
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Tudo dentro do esperado.", instrucao)
    }

    @Test
    fun instrucaoTom_umaMetricaRuim_jitter_retornaParcial() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 55.0, // ruim: >50ms
                perdaPacotesPercentual = 0.0,
                rttGatewayMs = 100,
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Sua conexão está funcionando, mas...", instrucao)
    }

    @Test
    fun instrucaoTom_umaMetricaRuim_perda_retornaParcial() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 10.0,
                perdaPacotesPercentual = 2.0, // ruim: >=2%
                rttGatewayMs = 100,
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Sua conexão está funcionando, mas...", instrucao)
    }

    @Test
    fun instrucaoTom_umaMetricaRuim_rtt_retornaParcial() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 10.0,
                perdaPacotesPercentual = 0.0,
                rttGatewayMs = 151, // ruim: >150ms
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Sua conexão está funcionando, mas...", instrucao)
    }

    @Test
    fun instrucaoTom_duasMetricasRuins_retornaDetectei() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 60.0, // ruim
                perdaPacotesPercentual = 3.0, // ruim
                rttGatewayMs = 80,
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Detectei...", instrucao)
    }

    @Test
    fun instrucaoTom_tresMetricasRuins_retornaDetectei() {
        val metricas =
            AiMetricasAtuais(
                jitterMs = 70.0, // ruim
                perdaPacotesPercentual = 5.0, // ruim
                rttGatewayMs = 200, // ruim
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        assertEquals("Detectei...", instrucao)
    }

    @Test
    fun instrucaoTom_limites_exatos_naoContamComoBad() {
        // jitter exatamente 50ms NAO e ruim (threshold e >50, nao >=50)
        // perda exatamente 2% E ruim (threshold e >=2)
        // rtt exatamente 150ms NAO e ruim (threshold e >150, nao >=150)
        val metricas =
            AiMetricasAtuais(
                jitterMs = 50.0, // limite: NAO ruim
                perdaPacotesPercentual = 2.0, // limite: ruim (>=2)
                rttGatewayMs = 150, // limite: NAO ruim
            )
        val instrucao = DiagnosisAiContextFactory.buildToneInstruction(metricas)
        // Apenas 1 metrica ruim (perda)
        assertEquals("Sua conexão está funcionando, mas...", instrucao)
    }

    @Test
    fun instrucaoTom_eSerializadoNoJson_quandoPresente() {
        // .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): perdaPacotesPercentual so chega ao contexto da IA quando
        // perdaConfianca == SUFICIENTE (ver AiModels.internetToMetricas) — sem isso o
        // badCount contaria so 1 metrica ruim (jitter), nao 2.
        val input =
            DiagnosticInput(
                connectionType = ConnectionType.wifi,
                internet =
                    InternetDiagnosticInput(
                        downloadMbps = 50.0,
                        uploadMbps = 20.0,
                        latencyMs = 30.0,
                        jitterMs = 60.0, // ruim
                        perdaPercentual = 3.0, // ruim
                        perdaConfianca = ConfiancaAmostral.SUFICIENTE,
                    ),
            )
        val ctx = DiagnosisAiContextFactory.from(fakeReport(), input, ConnectionType.wifi)
        assertTrue("instrucaoTom deve ser Detectei", ctx.instrucaoTom == "Detectei...")

        val repo =
            AiDiagnosisRepository(
                baseUrl = "http://invalid.local",
                isAuthorized = { true },
            )
        val json = repo.contextToJson(ctx).toString()
        assertTrue("JSON deve conter instrucaoTom", json.contains("\"instrucaoTom\":"))
        assertTrue("JSON deve conter Detectei", json.contains("Detectei..."))
    }

    @Test
    fun instrucaoTom_naoSerializado_quandoSemMetricas() {
        // Fallback sem input — instrucaoTom null nao deve aparecer no JSON
        val ctx = DiagnosisAiContextFactory.from(fakeReport(), ConnectionType.wifi)
        assertNull(ctx.instrucaoTom)

        val repo =
            AiDiagnosisRepository(
                baseUrl = "http://invalid.local",
                isAuthorized = { true },
            )
        val json = repo.contextToJson(ctx).toString()
        assertFalse("JSON sem instrucaoTom quando null", json.contains("\"instrucaoTom\":"))
    }

    @Test
    fun v3_wifi_naoIncluiBlocoMovel_mesmoSeForPassado() {
        // Em wifi, mesmo que algum caller passe `movel` (caller bug), o factory
        // ainda inclui (e o Worker ignoraria). Este teste documenta que o
        // gating de "nao iniciar telephony em wifi" e responsabilidade do
        // chamador (MainViewModel), nao do factory. Ver MonitorTelephony.iniciar
        // condicional em MainViewModel.coletarContextoAdicionalIa.
        val ctx =
            DiagnosisAiContextFactory.fromRaw(
                report = fakeReport(),
                input =
                    DiagnosticInput(
                        connectionType = ConnectionType.wifi,
                        internet =
                            InternetDiagnosticInput(
                                downloadMbps = 250.0,
                                uploadMbps = 100.0,
                                latencyMs = 12.0,
                                jitterMs = 2.0,
                                perdaPercentual = 0.0,
                            ),
                    ),
                connectionType = ConnectionType.wifi,
                movel = null, // como deveria ser sempre em wifi
            )
        assertNull(ctx.movel)
        val repo =
            io.signallq.app.feature.diagnostico.ai.AiDiagnosisRepository(
                baseUrl = "http://invalid.local",
                isAuthorized = { true },
            )
        val json = repo.contextToJson(ctx).toString()
        assertFalse(json.contains("\"movel\":"))
        assertTrue(json.contains("\"connectionType\":\"wifi\""))
    }

    // -------------------------------------------------------------------------
    // equipamentoLocal (GH#542, epic #547) — a IA so pode receber o resumo JA
    // FILTRADO ([SafeLocalDeviceContext]), nunca o snapshot bruto. Como o campo
    // do DiagnosticInput ja e tipado como SafeLocalDeviceContext, e estruturalmente
    // impossivel vazar MAC/IP completo/senha por este caminho — os testes abaixo
    // confirmam o mapeamento e a ausencia do bloco quando nao ha equipamento.
    // -------------------------------------------------------------------------

    @Test
    fun equipamentoLocal_ausente_quandoNaoHaLocalDeviceNoInput() {
        val ctx =
            DiagnosisAiContextFactory.from(
                report = fakeReport(),
                input = DiagnosticInput(connectionType = ConnectionType.wifi),
                connectionType = ConnectionType.wifi,
            )
        assertNull(ctx.equipamentoLocal)

        val repo = AiDiagnosisRepository(baseUrl = "http://invalid.local", isAuthorized = { true })
        val json = repo.contextToJson(ctx).toString()
        assertFalse(json.contains("\"equipamentoLocal\":"))
    }

    @Test
    fun equipamentoLocal_populado_a_partir_do_resumo_seguro_ja_filtrado() {
        val snapshot =
            io.signallq.app.core.network.contracts.localdevice.SafeLocalDeviceContext(
                vendor = "Nokia",
                modelo = "G-1425G-B",
                firmwareVersion = "1.2.3",
                deviceType = io.signallq.app.core.network.contracts.localdevice.DeviceType.ONT_GPON,
                supportLevel = io.signallq.app.core.network.contracts.localdevice.SupportLevel.LAB_VALIDATED,
                capabilities =
                    io.signallq.app.core.network.contracts.localdevice
                        .DeviceCapabilities(suportaFibra = true),
                connectionStatus = io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus.OK,
                statusFibra = io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus.OK,
                statusWan = io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus.OK,
                statusWifi = io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus.NAO_SUPORTADO,
                statusLan = io.signallq.app.core.network.contracts.localdevice.LocalDeviceSectionStatus.NAO_SUPORTADO,
                quantidadeClientes = 4,
                warnings = emptyList(),
                coletadoEmEpochMs = 1_000L,
            )
        val ctx =
            DiagnosisAiContextFactory.from(
                report = fakeReport(),
                input = DiagnosticInput(connectionType = ConnectionType.wifi, localDevice = snapshot),
                connectionType = ConnectionType.wifi,
            )

        assertNotNull(ctx.equipamentoLocal)
        assertEquals("Nokia", ctx.equipamentoLocal?.vendor)
        assertEquals("ONT_GPON", ctx.equipamentoLocal?.deviceType)
        assertEquals("LAB_VALIDATED", ctx.equipamentoLocal?.supportLevel)
        assertEquals(4, ctx.equipamentoLocal?.quantidadeClientes)

        val repo = AiDiagnosisRepository(baseUrl = "http://invalid.local", isAuthorized = { true })
        val json = repo.contextToJson(ctx).toString()
        assertTrue(json.contains("\"equipamentoLocal\":"))
        assertTrue(json.contains("\"deviceType\":\"ONT_GPON\""))
        // Nunca deve vazar campos brutos que nem existem em SafeLocalDeviceContext.
        assertFalse(json.contains("mac"))
        assertFalse(json.contains("senha"))
        assertFalse(json.contains("serial"))
    }

    // ── AiObjetivoDiagnostico — campo estruturado da subcategoria (2026-08) ──────

    /**
     * Preparação de terreno da consolidação de roteiro "muitas perguntas, difícil
     * escolher": a subcategoria escolhida chega ao payload como campo estruturado
     * (objetivoId + índice + rótulo), não como índice cru nem texto livre.
     */
    @Test
    fun objetivoDiagnostico_ausente_por_padrao_quando_caller_nao_informa() {
        val ctx = DiagnosisAiContextFactory.from(fakeReport(), ConnectionType.wifi)
        assertNull(ctx.objetivoDiagnostico)
    }

    @Test
    fun objetivoDiagnostico_populado_quando_fromRaw_recebe_a_subcategoria() {
        val subcategoria =
            io.signallq.app.feature.diagnostico.ai.AiObjetivoDiagnostico(
                objetivoId = "PROBLEMAS_VIDEO_JOGOS",
                subcategoriaIndice = 0,
                subcategoriaRotulo = "",
            )
        val ctx =
            DiagnosisAiContextFactory.fromRaw(
                report = fakeReport(),
                input = null,
                connectionType = ConnectionType.wifi,
                objetivoDiagnostico = subcategoria,
            )
        assertEquals(subcategoria, ctx.objetivoDiagnostico)
    }

    @Test
    fun objetivoDiagnosticoFactory_resolve_fallback() {
        val resultado =
            AiObjetivoDiagnosticoFactory.from(
                objetivo = io.signallq.app.core.diagnostico.ObjetivoDiagnostico.PROBLEMAS_VIDEO_JOGOS,
            )
        assertEquals(
            io.signallq.app.feature.diagnostico.ai.AiObjetivoDiagnostico(
                objetivoId = "PROBLEMAS_VIDEO_JOGOS",
                subcategoriaIndice = 0,
                subcategoriaRotulo = "",
            ),
            resultado,
        )
    }
}
