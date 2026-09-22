package io.signallq.app.core.nds

import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DnsDiagnosticInput
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.MobileDiagnosticInput
import io.signallq.app.core.diagnostico.banda
import io.signallq.app.core.network.contracts.localdevice.SafeLocalDeviceContext
import java.util.UUID

/** `app.id` do payload NDS — identificador tecnico preservado do app (`AGENTS.md`,
 *  "Identificadores tecnicos"), confirmado nos testes de NDS-01 (#1745). */
private const val NDS_APP_ID = "io.signallq.app"

/**
 * Ponte `DiagnosticInput -> NdsDiagnosticsRequest` (NDS-02k, issue #1759, item 4 —
 * secao 2a do inventario). Funcao pura, sem I/O. Vive em `core/nds` (nao em
 * `feature/diagnostico`) porque este modulo ja depende de `core/diagnostico`
 * (dependencia temporaria documentada em `core/nds/build.gradle.kts`) e o
 * `NdsClient`/`NdsDiagnosticsRequest` que ela alimenta tambem moram aqui.
 *
 * [appVersion] vem do `BuildConfig` do chamador (`core/nds` nao conhece a versao
 * do app consumer). [requestId] reusa `DiagnosticInput.executionId` (GH#1228 Fase 3)
 * quando propagado; gera um UUID novo quando o chamador nao propagou (default `""`).
 *
 * ## Campos de `DiagnosticInput` sem correspondente no schema do NDS (fora por design)
 * `deviceGamingSelecionado` — o NDS nao pede este campo ainda (`localDevice`
 * ja tem correspondente desde a issue #1839 — ver bloco `localEquipment` abaixo;
 * `natStatus`/`velocidadeContratadaMbps` ganharam correspondente na issue #1841 —
 * ver `connection.natStatus` e o bloco `plan` abaixo). NAO confundir
 * `deviceGamingSelecionado` (device/console especifico, ex.: "playstation") com o sinal
 * "diagnostico roda dentro do Modo Gamer": este ultimo TEM campo correspondente no NDS
 * (`profile="gamer"`, decidido em #1746 secao 3b) e ja e aceito por este mapper via
 * [perfilGamer] — o gap historico (issue #1762) era o chamador em
 * `NdsDiagnosticRepository.evaluate` nunca repassar esse parametro, nao a ausencia do campo.
 *
 * ## `wifiScan` (ADR-018, NDS-Snapshot-02 — issue #1834)
 * O bloco opcional `wifiScan` roda o `ChannelEvaluator` (`:coreNetwork`) sobre as
 * redes vizinhas do scan via [toNdsWifiScanInfo] e carrega tanto o resultado
 * calculado (congestionamento/melhor canal) quanto a evidencia bruta (redes
 * vizinhas com canal/frequencia/RSSI/largura) que o originou — o NDS precisa
 * poder explicar por que um canal foi considerado congestionado, nao so receber
 * a conclusao. Fica `null` apenas quando `DiagnosticInput.wifiScan` e null ou nao
 * carrega nenhuma evidencia util (nem redes vizinhas, nem canal conectado).
 *
 * ## `mobile` (ADR-018 bloco 10 — issue #1837)
 * Bloco opcional montado por [toNdsMobileInfo] a partir de `DiagnosticInput.mobile`.
 * Fica `null` sem conexao movel, sem snapshot de `MonitorTelephony`, sem
 * `READ_PHONE_STATE` (`MobileDiagnosticInput.capturaReduzida = true` — mesma
 * familia de gap de permissao da issue #1735) ou sem nenhuma evidencia de sinal.
 * Nunca carrega Cell ID/TAC/MCC/MNC (proibido pela issue-mae sem revisao de
 * privacidade dedicada).
 *
 * ## `dns` expandido (ADR-018, issue #1840)
 * Bloco opcional montado por [toNdsDnsInfo] a partir de `DiagnosticInput.dns`
 * (`DnsDiagnosticInput`), que ja concentra a ultima comparacao de benchmark
 * (`feature/dns`), a coerencia calculada por `AvaliadorCoerenciaDns` e o estado
 * de Private DNS lido do `SnapshotRede` (`core/network`) — tudo montado pelo
 * chamador antes de chegar aqui; este mapper so transcreve.
 *
 * ## Gap documentado: `dns.hijacked`
 * Ainda nao ha coleta desse dado no app (ADR-017, pendencias em aberto) — fica
 * sempre `null` aqui, mesmo com bloco `dns` presente.
 *
 * ## `historical` (ADR-018 secao 13, NDS-Snapshot-06 — issue #1838)
 * O bloco opcional `historical` carrega medias 7d/30d e degradacao calculada
 * de forma deterministica via [toNdsHistoricalInfo], que roda sobre
 * `DiagnosticInput.historico` (`HistoricalDiagnosticInput`, ja existente e
 * populado por quem consulta `MedicaoDao` em `:app` antes de montar o
 * `DiagnosticInput`). Fica `null` quando nao ha nenhum teste registrado nas
 * duas janelas (usuario novo) — nunca zeros inventados.
 *
 * ## `localEquipment` (ADR-018 bloco 12 — issue #1839)
 * Bloco opcional montado por [toNdsLocalEquipmentInfo] a partir de
 * `DiagnosticInput.localDevice` (`SafeLocalDeviceContext`, ja filtrado por
 * `LocalDeviceSafeFilter` antes de chegar aqui — este mapper nunca le
 * `LocalNetworkDeviceSnapshot` bruto). Fica `null` quando nenhum equipamento
 * local suportado/conectado foi lido nesta sessao. Nunca carrega senha, serial
 * completo, credenciais, payload HTML do equipamento, token de sessao ou MAC
 * completo — a allowlist ja fecha essa superficie antes de chegar em
 * `DiagnosticInput`.
 *
 * ## `connection.natStatus` e bloco `plan` (ADR-018 blocos `connection`/14 —
 * NDS-Snapshot-09, issue #1841)
 * `connection.natStatus` transcreve `DiagnosticInput.natStatus` (enum `NatStatus`
 * calculado por `NatClassifier`/`TopologyDiagnostic`) como string via `.name` —
 * sem tabela de traducao propria. `null` quando nenhuma classificacao rodou
 * nesta sessao; `"UNKNOWN"` quando a classificacao rodou mas foi inconclusiva
 * (`NatClassifier.classify` sem IP de WAN) — os dois casos sao distintos e nenhum
 * dos dois inventa um valor.
 *
 * O bloco `plan` e montado por [toNdsPlanInfo] a partir de
 * `DiagnosticInput.velocidadeContratadaMbps` (fonte
 * `PreferenciasAppRepository.planoInternetFlow`). Fica `null` quando o usuario
 * nunca informou o plano contratado — **nunca inferido a partir da velocidade
 * medida no speedtest** (regra explicita da issue-mae #1832 secao 6).
 *
 * `context.subcategory` e opcional no contrato v2. Quando a tela tiver um recorte
 * canônico, ele é preservado; a ausência dele não impede a avaliação v2.
 *
 * ## `speed.packetLossSource` (ADR-018, "Convenção de proveniência" — NDS-Snapshot-10,
 * issue #1842)
 * Traduz `InternetDiagnosticInput.packetLossSource` (vocabulario interno legado) para
 * o vocabulario fechado [NdsProvenance] via [toNdsPacketLossSource]. `null` quando o
 * usuario nunca mediu perda de pacotes (`"naoMedido"`) ou quando `perdaPercentual`
 * em si e null — omitido do JSON, nunca um valor inventado.
 */
fun DiagnosticInput.toNdsDiagnosticsRequest(
    appVersion: String,
    perfilGamer: Boolean = false,
): NdsDiagnosticsRequest {
    val wifiInfo =
        wifi?.let {
            NdsWifiInfo(
                rssi = it.rssiDbm,
                band = ndsBandaWifi(it.banda()),
                channel = it.canal,
                linkSpeed = it.linkSpeedMbps,
                standard = it.wifiStandard,
            )
        }
    val fiberInfo =
        fibra?.let {
            NdsFiberInfo(
                rxPowerDbm = it.rxPowerDbm,
                txPowerDbm = it.txPowerDbm,
                temperatureC = it.temperatureCelsius,
            )
        }
    val speedInfo =
        internet?.let {
            NdsSpeedInfo(
                pingMs = it.latencyMs,
                jitterMs = it.jitterMs,
                downloadMbps = it.downloadMbps,
                uploadMbps = it.uploadMbps,
                packetLossPercent = it.perdaPercentualConfiavelParaNds(),
                packetLossSource = toNdsPacketLossSource(it.packetLossSource),
            )
        }
    val qualityInfo =
        internet?.let {
            val loadedLatencyMs =
                it.latencyMs?.let { latency -> it.bufferbloatMs?.let { bufferbloat -> latency + bufferbloat } }
            NdsQualityInfo(
                latencyMs = it.latencyMs,
                jitterMs = it.jitterMs,
                packetLossPercent = it.perdaPercentualConfiavelParaNds(),
                loadedLatencyMs = loadedLatencyMs,
                bufferbloatMs = it.bufferbloatMs,
            )
        }
    val dnsInfo = toNdsDnsInfo(dns)
    val gatewayInfo =
        if (internet?.rttGatewayMs != null || wifi?.dispositivosNaRede != null) {
            NdsGatewayInfo(
                rttGatewayMs = internet?.rttGatewayMs,
                connectedDevices = wifi?.dispositivosNaRede,
            )
        } else {
            null
        }
    val wifiScanInfo = wifiScan.toNdsWifiScanInfo(wifi?.banda())
    val mobileInfo = toNdsMobileInfo(mobile)
    val historicalInfo = historico.toNdsHistoricalInfo()
    val localEquipmentInfo = toNdsLocalEquipmentInfo(localDevice)
    val planInfo = toNdsPlanInfo(velocidadeContratadaMbps)

    return NdsDiagnosticsRequest(
        requestId = executionId.ifBlank { UUID.randomUUID().toString() },
        app = NdsAppInfo(id = NDS_APP_ID, version = appVersion),
        profile = ndsProfile(perfilGamer, this.context?.objective),
        capabilities =
            ndsCapabilities(
                wifi = wifiInfo,
                fiber = fiberInfo,
                wifiScan = wifiScanInfo,
                mobile = mobileInfo,
                historical = historicalInfo,
                localEquipment = localEquipmentInfo,
            ) +
                listOfNotNull(
                    "usage_profiles".takeIf {
                        this.context?.objective in setOf("JOGOS_COM_LAG", "VIDEOS_TRAVAM", "CHAMADAS_CONGELAM", "SITES_DEMORAM")
                    },
                ),
        connection =
            NdsConnectionInfo(
                type = ndsConnectionType(connectionType),
                ssid = wifi?.ssid,
                bssid = wifi?.bssidMascarado,
                natStatus = natStatus?.name,
            ),
        wifi = wifiInfo,
        wifiScan = wifiScanInfo,
        speed = speedInfo,
        quality = qualityInfo,
        dns = dnsInfo,
        gateway = gatewayInfo,
        fiber = fiberInfo,
        mobile = mobileInfo,
        historical = historicalInfo,
        localEquipment = localEquipmentInfo,
        plan = planInfo,
        context =
            this.context?.let { context ->
                NdsDiagnosticContext(
                    reportedProblem = context.reportedProblem,
                    objective = context.objective,
                    subcategory = context.subcategory,
                    symptoms = context.symptoms,
                    answers = context.answers,
                )
            },
    )
}

/**
 * Bloco `mobile` (ADR-018 bloco 10, issue #1837). `null` quando:
 * - `mobile` e null (fora de conexao movel, ou `MonitorTelephony` sem snapshot);
 * - `mobile.capturaReduzida` e true — snapshot capturado SEM `READ_PHONE_STATE`
 *   (GH#1662/#1735): so operadora/mcc/mnc estariam disponiveis, sem nenhuma
 *   medicao real de sinal. A regra do #1837 e "ausencia de permissao resulta em
 *   bloco omitido, nao em zeros" — omitir o bloco inteiro em vez de mandar so a
 *   operadora sem nenhum dado de sinal;
 * - nenhum dos seis campos ficaria preenchido (nenhuma evidencia util).
 *
 * Cell ID/TAC/MCC/MNC NUNCA entram aqui — proibido pela issue-mae (#1832 secao 4)
 * sem revisao explicita de privacidade.
 */
private fun toNdsMobileInfo(mobile: MobileDiagnosticInput?): NdsMobileInfo? {
    if (mobile == null || mobile.capturaReduzida) return null
    val info =
        NdsMobileInfo(
            operator = mobile.carrierName,
            technology = mobile.mobileTechnology,
            rsrpDbm = mobile.rsrpDbm,
            rsrqDb = mobile.rsrqDb,
            sinrDb = mobile.sinrDb,
            band = mobile.band,
        )
    val temEvidencia =
        info.operator != null ||
            info.technology != null ||
            info.rsrpDbm != null ||
            info.rsrqDb != null ||
            info.sinrDb != null ||
            info.band != null
    return info.takeIf { temEvidencia }
}

/**
 * Bloco `localEquipment` (ADR-018 bloco 12, issue #1839). `null` quando
 * `localDevice` e null — nenhum equipamento local suportado/conectado foi lido
 * nesta sessao (ausencia de equipamento nunca vira bloco com zeros).
 *
 * Transcreve `SafeLocalDeviceContext` campo a campo, na mesma ordem da tabela
 * da ADR-018 — nao le `LocalNetworkDeviceSnapshot` bruto em nenhum momento
 * (esse tipo nem chega ate `core/nds`; `DiagnosticInput.localDevice` ja e o
 * resultado de `LocalDeviceSafeFilter.filtrar`). `warnings`/`coletadoEmEpochMs`
 * de `SafeLocalDeviceContext` ficam de fora por decisao explicita da ADR-018
 * (secao do bloco 12) — nao pedidos pela issue-mae e sem vocabulario JSON
 * definido para `warnings` ainda.
 */
private fun toNdsLocalEquipmentInfo(localDevice: SafeLocalDeviceContext?): NdsLocalEquipmentInfo? =
    localDevice?.let { ctx ->
        NdsLocalEquipmentInfo(
            vendor = ctx.vendor,
            model = ctx.modelo,
            firmwareVersion = ctx.firmwareVersion,
            deviceType = ctx.deviceType.name,
            supportLevel = ctx.supportLevel.name,
            connectionStatus = ctx.connectionStatus.name,
            fiberStatus = ctx.statusFibra.name,
            wanStatus = ctx.statusWan.name,
            wifiStatus = ctx.statusWifi.name,
            lanStatus = ctx.statusLan.name,
            connectedClients = ctx.quantidadeClientes,
        )
    }

/**
 * Bloco `dns` expandido (ADR-018, issue #1840). Transcricao pura de
 * `DnsDiagnosticInput` — nenhuma inferencia de provedor/IP acontece aqui (a
 * tabela IP/hostname->provedor ja existe em `:app` e nao deve ser duplicada
 * neste mapper, ver divida #1823). `hijacked` sempre `null`: nao ha coleta
 * real desse dado, e a issue-mae #1832 proibe `false` como default para "nao
 * sabemos".
 *
 * `null` quando `dns` e null — nenhum dado de DNS foi coletado nesta sessao.
 */
private fun toNdsDnsInfo(dns: DnsDiagnosticInput?): NdsDnsInfo? =
    dns?.let {
        NdsDnsInfo(
            primary = it.currentDnsIp,
            responseTimeMs = it.currentDnsLatencyMs,
            hijacked = null,
            providerName = it.currentDnsName,
            bestName = it.bestDnsNameFromComparison,
            bestLatencyMs = it.bestDnsLatencyMsFromComparison,
            grade = it.dnsGrade,
            comparisonAvailable = it.dnsComparisonAvailable,
            coherenceAlertLevel = it.coerenciaNivelAlerta,
            coherenceConsecutiveDivergences = it.coerenciaDivergenciasConsecutivas,
            coherenceDivergenceRatePercent = it.coerenciaTaxaDivergenciaPercentual,
            privateDnsActive = it.privateDnsActive,
            privateDnsHostname = it.privateDnsHostname,
        )
    }

/**
 * Bloco `plan` (ADR-018 bloco 14, NDS-Snapshot-09 — issue #1841). `null` quando
 * `velocidadeContratadaMbps` e null — usuario nunca informou o plano contratado
 * (`PreferenciasAppRepository.planoInternetFlow`). **Nunca inferido** a partir de
 * `internet.downloadMbps`/`uploadMbps` medidos — regra explicita da issue-mae
 * #1832 secao 6.
 */
private fun toNdsPlanInfo(velocidadeContratadaMbps: Int?): NdsPlanInfo? =
    velocidadeContratadaMbps?.let { NdsPlanInfo(contractedSpeedMbps = it) }

/**
 * Traduz `InternetDiagnosticInput.packetLossSource` (vocabulário interno em
 * português/legado — `"estimated"`/`"naoMedido"`/`"unknown"`/`"modem"`, ver
 * KDoc do campo) para o vocabulário fechado [NdsProvenance] do payload NDS
 * (ADR-018, "Convenção de proveniência" — issue #1842). Não inventa um
 * terceiro vocabulário: reaproveita exatamente os quatro valores já
 * existentes.
 *
 * - `"modem"` (medição direta do equipamento) -> [NdsProvenance.MEASURED];
 * - `"estimated"` (indício via timeout HTTP) -> [NdsProvenance.ESTIMATED];
 * - `"unknown"` (coleta já retorna incerteza) -> [NdsProvenance.UNKNOWN];
 * - `"naoMedido"` ou `null` -> `null` (omite o campo do payload — nenhuma
 *   medição de perda de pacotes rodou nesta execução, não é o mesmo que
 *   "fonte desconhecida");
 * - qualquer valor não reconhecido -> `null` (nunca propaga string livre
 *   arbitrária para o payload; vocabulário fechado por decisão da ADR-018).
 */
private fun toNdsPacketLossSource(fonte: String?): NdsProvenance? =
    when (fonte) {
        "modem" -> NdsProvenance.MEASURED
        "estimated" -> NdsProvenance.ESTIMATED
        "unknown" -> NdsProvenance.UNKNOWN
        else -> null
    }

/**
 * Filtro de confiança amostral (.agents/architecture-plan.md, "Confiabilidade
 * estatística do diagnóstico de rede", seção 8) — o contrato NDS remoto
 * ([NdsProvenance]) NÃO muda de enum nesta fase; a decisão foi filtrar localmente o
 * que é enviado. Só reporta [InternetDiagnosticInput.perdaPercentual] quando a
 * amostra tem [ConfiancaAmostral.SUFICIENTE] — 1 timeout isolado (confiança
 * insuficiente, ou `null`/desconhecida: histórico legado sem o campo, entrada não
 * originada de speedtest) nunca chega à IA como percentual "confiável" o bastante
 * para basear causa raiz. Omitido do JSON (nunca um valor inventado ou forçado a 0),
 * mesmo padrão já usado para `null`/`"naoMedido"`/`"unknown"`.
 *
 * Fonte única do filtro para o payload remoto: [NdsSpeedInfo.packetLossPercent] e
 * [NdsQualityInfo.packetLossPercent] chamam esta MESMA função — não duplicar a regra.
 *
 * Dois outros consumidores de [InternetDiagnosticInput.perdaPercentual] NÃO passam
 * por este payload NDS — não herdavam o filtro automaticamente, então cada um
 * recebeu o MESMO gate (`perdaConfianca == SUFICIENTE`) no ponto mínimo necessário,
 * sem duplicar o VALOR de nenhum threshold de negócio:
 * - `RecomendacaoPraticaEngine.recomendarPerdaDePacotes` (`:feature:diagnostico`) — lê
 *   [InternetDiagnosticInput.perdaPercentual] direto; ganhou o gate no flag `critico`
 *   que decide entre "Atenção"/"Crítico".
 * - `AiModels.internetToMetricas` (`:feature:diagnostico`, worker de IA legado que
 *   NÃO passa pelo NDS) — ganhou o gate direto no campo
 *   `AiMetricasAtuais.perdaPacotesPercentual`.
 */
private fun InternetDiagnosticInput.perdaPercentualConfiavelParaNds(): Double? =
    if (perdaConfianca == ConfiancaAmostral.SUFICIENTE) perdaPercentual else null

private fun ndsProfile(
    perfilGamer: Boolean,
    objective: String?,
): String? =
    when {
        perfilGamer -> "gamer"
        objective == "JOGOS_COM_LAG" -> "gamer"
        objective == "VIDEOS_TRAVAM" -> "streaming"
        objective == "CHAMADAS_CONGELAM" -> "video_call"
        objective == "SITES_DEMORAM" -> "navigation"
        else -> null
    }

private fun ndsBandaWifi(banda: BandaWifi): String? =
    when (banda) {
        BandaWifi.ghz24 -> "2.4GHz"
        BandaWifi.ghz5 -> "5GHz"
        BandaWifi.desconhecida -> null
    }

/** Vocabulario livre (nao enum) do bloco `connection.type` — mesmo criterio de
 *  [NdsConnectionInfo.type]: string simples, nunca trava o cliente quando o
 *  servidor aceitar um valor novo. */
private fun ndsConnectionType(tipo: ConnectionType): String =
    when (tipo) {
        ConnectionType.wifi -> "WIFI"
        ConnectionType.mobile -> "MOBILE"
        ConnectionType.ethernet -> "ETHERNET"
        ConnectionType.desconectado -> "DISCONNECTED"
        ConnectionType.desconhecido -> "UNKNOWN"
    }
