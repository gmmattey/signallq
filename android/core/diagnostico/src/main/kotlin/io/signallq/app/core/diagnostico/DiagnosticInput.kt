package io.signallq.app.core.diagnostico

import io.signallq.app.core.diagnostico.topology.model.NatStatus
import io.signallq.app.core.network.contracts.localdevice.SafeLocalDeviceContext
import io.signallq.app.core.network.contracts.topologia.NivelConfianca
import io.signallq.app.core.network.contracts.topologia.PapelTopologia
import io.signallq.app.core.network.contracts.wifi.SegurancaWifi

data class WifiDiagnosticInput(
    val rssiDbm: Int?,
    val linkSpeedMbps: Int?,
    val frequenciaMhz: Int?,
    val ssid: String? = null,
    val bssidMascarado: String? = null,
    val canal: Int? = null,
    val larguraCanalMhz: Int? = null,
    val wifiStandard: String? = null,
    val linkSpeedDownMbps: Int? = null,
    val linkSpeedUpMbps: Int? = null,
    val gatewayIp: String? = null,
    val localIp: String? = null,
    val routerType: RouterType? = null,
    val dispositivosNaRede: Int? = null,
    /** Suporte do aparelho a 5GHz. Null quando desconhecido (leitura falhou) —
     *  tratado como "desconhecido", nao "sem suporte", pelo RecomendacaoPraticaEngine. */
    val is5GhzCapable: Boolean? = null,
)

data class InternetDiagnosticInput(
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val latencyMs: Double?,
    val jitterMs: Double?,
    val perdaPercentual: Double?,
    val bufferbloatMs: Double? = null,
    val testMode: String? = null,
    val serverName: String? = null,
    val serverRegion: String? = null,
    val serverHost: String? = null,
    val testDurationMs: Long? = null,
    val qualidadeUso: SpeedtestQualityInput? = null,
    /** RTT TCP para o gateway local (porta 80/443/53). Null se não disponível
     *  (emulador, Doze Mode, gateway não responde TCP). */
    val rttGatewayMs: Int? = null,
    /** Proveniência da medição de [perdaPercentual] — "estimated" (timeout HTTP,
     *  indício não confiável), "naoMedido", "unknown" ou "modem" (medição direta).
     *  Fonte: ResultadoSpeedtest.packetLossSource. Usado pelo RecomendacaoPraticaEngine
     *  para não cravar perda de pacotes como certeza quando é apenas estimada. */
    val packetLossSource: String? = null,
    /**
     * Confiança amostral de [perdaPercentual] — eixo ORTOGONAL a [packetLossSource]/
     * [Provenance] (.agents/architecture-plan.md, "Confiabilidade estatística do
     * diagnóstico de rede"). Fonte: `ResultadoSpeedtest.perdaConfianca`
     * (`AnalisadorAmostragemPing.avaliarConfianca`, `:feature:speedtest`).
     *
     * `null` = não calculada (execução antiga sem esse campo, ou histórico legado
     * lido do banco antes desta migração) — tratado como
     * [ConfiancaAmostral.INSUFICIENTE] pelos classificadores que consomem este campo
     * (nunca reclassifica o passado, só não deixa 1 timeout isolado escalar sozinho).
     * Consumido por [GameReadinessClassifier.perdaFaixa],
     * [UsageProfileClassifier.perdaDimensao] e [ScoreEvidenceBuilder.perdaPacotesStatus]
     * (via [ScoreEngine.aplicarTetos]) no lugar do antigo gate
     * `provenance == Provenance.medida`, que era inatingível via timeout HTTP.
     */
    val perdaConfianca: ConfiancaAmostral? = null,
)

data class FibraDiagnosticInput(
    val rxPowerDbm: Double? = null,
    val txPowerDbm: Double? = null,
    val temperatureCelsius: Double? = null,
    val isUp: Boolean,
)

data class MobileDiagnosticInput(
    val carrierName: String? = null,
    val mobileTechnology: String? = null,
    val signalStrengthDbm: Int? = null,
    val signalQualityPercent: Int? = null,
    val band: String? = null,
    val publicIp: String? = null,
    /** Reference Signal Received Power (4G LTE/5G NR), em dBm. Fonte: MovelSnapshot.rsrpDbm. */
    val rsrpDbm: Int? = null,
    /** Reference Signal Received Quality, em dB. Fonte: MovelSnapshot.rsrqDb. */
    val rsrqDb: Int? = null,
    /** Signal-to-Interference-plus-Noise Ratio, em dB. Fonte: MovelSnapshot.sinrDb. */
    val sinrDb: Int? = null,
    /** GH#1662 -- true quando este snapshot foi capturado SEM READ_PHONE_STATE
     *  (MovelSnapshot.capturaReduzida). Nesse modo tecnologia/rsrp/rsrq/sinr sempre
     *  vem null -- so operadora/mcc/mnc, que nao exigem a permissao. Consumidores que
     *  dependem de sinal medido (ex.: bloco `mobile` do NDS, issue #1837) devem tratar
     *  este flag como "sem permissao de telefonia" e omitir a evidencia, nao so os
     *  campos nulos. */
    val capturaReduzida: Boolean = false,
)

data class DnsDiagnosticInput(
    val currentDnsIp: String? = null,
    val currentDnsName: String? = null,
    val currentDnsLatencyMs: Int? = null,
    val bestDnsNameFromComparison: String? = null,
    val bestDnsLatencyMsFromComparison: Int? = null,
    val dnsGrade: String? = null,
    val dnsComparisonAvailable: Boolean = false,
    /** Nivel de alerta calculado por AvaliadorCoerenciaDns.registrarCoerencia() ("none"|"attention"|"critical"). */
    val coerenciaNivelAlerta: String? = null,
    val coerenciaDivergenciasConsecutivas: Int? = null,
    val coerenciaTaxaDivergenciaPercentual: Double? = null,
    /** Estado do Private DNS (Android), lido de `SnapshotRede.privateDnsAtivo`
     *  (ADR-018, bloco `dns` expandido — issue #1840). */
    val privateDnsActive: Boolean? = null,
    /** Hostname do Private DNS configurado, lido de `SnapshotRede.privateDnsHostname`.
     *  So preenchido pelo chamador quando o hostname bate com um provedor publico
     *  conhecido (mesma tabela de deteccao usada em [currentDnsName]) -- hostname
     *  customizado (resolver proprio do usuario) nunca chega aqui, por decisao de
     *  privacidade da ADR-018 (pode ser identificador pessoal). */
    val privateDnsHostname: String? = null,
)

data class HistoricalDiagnosticInput(
    val avgDownload7d: Double? = null,
    val avgUpload7d: Double? = null,
    val avgPing7d: Double? = null,
    val avgDns7d: Double? = null,
    val testsCount7d: Int = 0,
    val avgDownload30d: Double? = null,
    val avgUpload30d: Double? = null,
    val avgPing30d: Double? = null,
    val avgDns30d: Double? = null,
    val testsCount30d: Int = 0,
    val degradationDetected: Boolean? = null,
    val degradationPercent: Double? = null,
    val worstTimeWindow: String? = null,
    val bestTimeWindow: String? = null,
)

data class WifiScanDiagnosticInput(
    val redes: List<RedeWifiVizinha> = emptyList(),
    val conectadoCanal: Int? = null,
    val conectadoBanda: BandaWifi? = null,
)

data class RedeWifiVizinha(
    val canal: Int?,
    val rssiDbm: Int?,
    val frequenciaMhz: Int?,
    val ssid: String? = null,
    val bssid: String? = null,
    val seguranca: SegurancaWifi? = null,
    // #980 (Fase 2B, passo 3) — papel/confianca do motor de topologia unificado
    // (TopologiaRedeEngine/#979) pra este BSSID. Null quando o motor nao classificou essa
    // rede (ex.: fallback de scan vazio). RecomendacaoPraticaEngine usa em vez de MeshOuiDatabase
    // direto pra decidir se vale afirmar "OUI conhecido" nas evidencias.
    val papelTopologia: PapelTopologia? = null,
    val confiancaTopologia: NivelConfianca? = null,
    // GH#1207 item 3 — largura real do canal (20/40/80/160 MHz) quando o scan Android
    // reportou (RedeVizinha.larguraCanalMhz, ja disponivel na origem). Null faz o motor
    // assumir 20 MHz e marcar o DadoCanal correspondente como `larguraEstimada=true`.
    val larguraCanalMhz: Int? = null,
)

data class SpeedtestQualityInput(
    val vereditoStreaming: String? = null,
    val vereditoGamer: String? = null,
    val vereditoVideochamada: String? = null,
    val gargaloPrimario: String? = null,
    val severidadeBufferbloat: String? = null,
)

/** Contexto fechado informado pelo usuário para a avaliação remota. Não contém PII. */
data class DiagnosticContext(
    val reportedProblem: String? = null,
    val objective: String? = null,
    /**
     * Recorte opcional e estruturado do objetivo, usado pelo contrato NDS v2 somente para
     * priorizar achados que já sejam compatíveis. Ausente quando a pergunta do Assist não tem
     * equivalente canônico no contrato remoto; nunca é uma causa declarada pela pessoa.
     */
    val subcategory: String? = null,
    val symptoms: List<String> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
)

enum class ConnectionType { wifi, mobile, ethernet, desconectado, desconhecido }

enum class RouterType { roteador, mesh, extensor, desconhecido }

enum class BandaWifi { ghz24, ghz5, desconhecida }

fun WifiDiagnosticInput.banda(): BandaWifi =
    when {
        frequenciaMhz == null -> BandaWifi.desconhecida
        frequenciaMhz < 3000 -> BandaWifi.ghz24
        else -> BandaWifi.ghz5
    }

data class DiagnosticInput(
    val connectionType: ConnectionType = ConnectionType.desconhecido,
    val internet: InternetDiagnosticInput? = null,
    val wifi: WifiDiagnosticInput? = null,
    val fibra: FibraDiagnosticInput? = null,
    val mobile: MobileDiagnosticInput? = null,
    val dns: DnsDiagnosticInput? = null,
    val historico: HistoricalDiagnosticInput? = null,
    val wifiScan: WifiScanDiagnosticInput? = null,
    /** Velocidade contratada do plano, em Mbps. Fonte: PreferenciasAppRepository.planoInternetFlow. */
    val velocidadeContratadaMbps: Int? = null,
    /** Classificacao de NAT/CGNAT da rede atual. Fonte: TopologyDiagnostic/NatClassifier. */
    val natStatus: NatStatus? = null,
    /** Device/console selecionado manualmente pelo usuario, ex.: "playstation", "xbox",
     *  "pc", "switch". Null quando o usuario nao respondeu essa pergunta (device preset
     *  entao usa o fallback automatico: o proprio Android/iPhone rodando o app).
     *  GH#1682 — a unica fonte que preenchia este campo era a arvore de perguntas do
     *  motor SignallQ Pulse ("qual_jogo_device" — SIG-290), removida por ser codigo
     *  morto sem consumidor de UI. Nenhum caminho de producao atual escreve este campo
     *  (so testes) — o consumidor em RecomendacaoPraticaEngine fica inalcancavel ate
     *  uma nova fonte ser decidida (ex.: selecao de device no Modo Gamer). */
    val deviceGamingSelecionado: String? = null,
    /** Resumo seguro (allowlisted) do equipamento de rede local (ONT/roteador)
     *  detectado, quando disponivel — GH#542, epic #547. Ja passou por
     *  [io.signallq.app.core.network.contracts.localdevice.LocalDeviceSafeFilter],
     *  nunca o snapshot bruto. Null quando nenhum equipamento foi lido nesta
     *  sessao — o diagnostico continua funcionando normalmente sem ele. */
    val localDevice: SafeLocalDeviceContext? = null,
    /** GH#1228 (Fase 3, executionId/rulesVersion) — identificador da execução de origem
     *  desta entrada (mesmo `ResultadoSpeedtest.executionId`, GH#1221/#1225, quando o
     *  diagnóstico segue um speedtest; ou o `MedicaoEntity.executionId` já persistido,
     *  quando a entrada é reconstruída a partir da última medição salva). Nunca gerado
     *  aqui — propagado por quem monta este input. Default `""` preserva os chamadores
     *  que ainda não propagam (nenhum comportamento de classificação muda por causa
     *  deste campo; ele só viaja até [DiagnosticReport.executionId]). */
    val executionId: String = "",
    /** Contexto estruturado da jornada guiada, associado à mesma execução. */
    val context: DiagnosticContext? = null,
)
