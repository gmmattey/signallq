package io.signallq.app.feature.diagnostico

import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticResult
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.diagnostico.FibraSignalQualityEngine
import io.signallq.app.core.diagnostico.FindingEngine
import io.signallq.app.core.diagnostico.FindingResult
import io.signallq.app.core.diagnostico.GameReadinessClassifier
import io.signallq.app.core.diagnostico.MetricClassifier
import io.signallq.app.core.diagnostico.MetricStatus
import io.signallq.app.core.diagnostico.WifiChannelDiagnosticEngine
import io.signallq.app.core.diagnostico.banda
import io.signallq.app.core.network.contracts.topologia.NivelConfianca
import io.signallq.app.core.network.contracts.topologia.PapelTopologia

private const val CAT = "recomendacao"

/**
 * Motor de recomendacoes praticas do diagnostico local — consome os achados do
 * [FindingEngine] (via [FindingResult]) e os dados brutos ([DiagnosticInput]) para
 * gerar as 14 regras de recomendacao (REC-01..REC-14) documentadas na skill
 * `motor-diagnostico` (fase RecommendationEngine, SIG-287).
 *
 * Renomeado de `RecommendationEngine` para `RecomendacaoPraticaEngine` na Fatia 9a
 * da auditoria #1228 (corrige P1-1): o nome antigo colidia literalmente com
 * `core.recommendation.RecommendationEngine` (motor de monetizacao, issue #790),
 * sem nenhuma relacao de chamada entre os dois — ver
 * `docs_ai/ARQUITETURA/AUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`.
 *
 * ## Por que separa de [FindingEngine]
 * O [FindingEngine] decide QUAL e o problema principal (desempate por score entre
 * causas concorrentes). O [RecomendacaoPraticaEngine] decide O QUE FAZER a respeito —
 * puramente aditivo, roda depois do achado principal estar definido e pode gerar
 * zero ou varias recomendacoes simultaneas (ex.: "troque para 5GHz" e "canal
 * congestionado" podem coexistir).
 *
 * Cada regra abaixo implementa exatamente uma das 12 situacoes do documento, com a
 * condicao de "quando mostrar" e "quando NAO mostrar" comentada no bloco.
 */
object RecomendacaoPraticaEngine {
    fun recomendar(
        input: DiagnosticInput,
        achados: FindingResult,
    ): List<DiagnosticResult> {
        val recomendacoes = mutableListOf<DiagnosticResult>()

        val principalId = achados.principal.id
        val problemaExternoProvavel =
            principalId in
                setOf(
                    "DECISAO-GW-01", // operadora
                    "DECISAO-DNS-01",
                    "DECISAO-DNS-01b", // DNS
                    "DECISAO-00",
                    "DECISAO-00b", // fibra
                )

        recomendarWifi5Ghz(input, problemaExternoProvavel)?.let { recomendacoes += it }
        recomendarDistanciaRoteador(input, achados)?.let { recomendacoes += it }
        recomendarCanalCongestionado(input)?.let { recomendacoes += it }
        recomendarRoteadorLimitado(input)?.let { recomendacoes += it }
        recomendarBufferbloat(input)?.let { recomendacoes += it }
        recomendarDnsLento(input)?.let { recomendacoes += it }
        recomendarOperadoraRotaExterna(input)?.let { recomendacoes += it }
        recomendarGatewayLento(input)?.let { recomendacoes += it }
        recomendarFibraComProblema(input)?.let { recomendacoes += it }
        recomendarRedeMovelFraca(input)?.let { recomendacoes += it }
        recomendarPerdaDePacotes(input)?.let { recomendacoes += it }
        recomendarDevicePresetGaming(input)?.let { recomendacoes += it }
        recomendarUpgradeRoteadorRecorrente(input)?.let { recomendacoes += it }
        recomendarScoreGeral(achados, recomendacoes)?.let { recomendacoes += it }

        return recomendacoes
    }

    // -------------------------------------------------------------------------
    // 1. Trocar para Wi-Fi 5GHz
    // -------------------------------------------------------------------------
    // Mostrar: conectado em 2.4GHz + (link speed baixo OU download baixo) +
    // aparelho compativel com 5GHz + sinal atual nao fraco demais (5GHz tem
    // alcance menor, entao trocar so ajuda perto do roteador) + achado principal
    // nao aponta problema externo (operadora/DNS/fibra).
    //
    // #897: a versao anterior EXIGIA rede 5GHz do mesmo SSID no scan de
    // vizinhanca como pre-requisito obrigatorio — mas o wifiScan nao e
    // preenchido no fluxo de producao (o scan de vizinhanca so roda em telas
    // dedicadas de Wi-Fi), entao a recomendacao nunca
    // aparecia de verdade. Alem disso, muitos roteadores usam SSID diferente
    // para a banda de 5GHz (ex.: "Casa" e "Casa_5G"), entao exigir SSID igual
    // tambem excluia esse cenario comum mesmo com scan disponivel. Scan
    // confirmando o mesmo SSID em 5GHz agora e evidencia extra (reforca a
    // mensagem), nao mais obrigatorio.
    private fun recomendarWifi5Ghz(
        input: DiagnosticInput,
        problemaExternoProvavel: Boolean,
    ): DiagnosticResult? {
        val wifi = input.wifi ?: return null
        if (wifi.banda() != BandaWifi.ghz24) return null
        if (problemaExternoProvavel) return null
        if (wifi.is5GhzCapable == false) return null // aparelho sem suporte confirmado a 5GHz

        val linkBaixo = (wifi.linkSpeedMbps ?: Int.MAX_VALUE) < 144
        val downloadBaixo = (input.internet?.downloadMbps ?: Double.MAX_VALUE) < 25.0
        if (!linkBaixo && !downloadBaixo) return null

        // Sinal 2.4GHz ja fraco demais: 5GHz tem alcance menor, trocar tende a
        // piorar o cenario em vez de ajudar — nao recomenda.
        val rssiAtual = wifi.rssiDbm
        if (rssiAtual != null && rssiAtual < -70) return null

        val ssid = wifi.ssid
        val redes5Ghz =
            if (ssid != null) {
                input.wifiScan?.redes.orEmpty().filter {
                    val freq = it.frequenciaMhz
                    it.ssid == ssid && freq != null && freq >= 5000
                }
            } else {
                emptyList()
            }

        // Se o scan confirma o mesmo SSID em 5GHz mas com sinal fraco demais,
        // nao recomenda — evidencia direta de que a troca nao ajudaria.
        val melhorSinal5Ghz = redes5Ghz.mapNotNull { it.rssiDbm }.maxOrNull()
        if (melhorSinal5Ghz != null && melhorSinal5Ghz <= -75) return null

        // #980 (Fase 2B, passo 3) — papel/confianca do motor unificado (TopologiaRedeEngine/#979,
        // ja calculado em MainViewModel.montarWifiScanInput) em vez de MeshOuiDatabase direto por
        // OUI cru. So conta como "conhecido" com confianca != BAIXA — um sinal de baixa confianca
        // nao deveria virar afirmacao na evidencia mostrada ao usuario.
        val temOuiConhecido =
            redes5Ghz.any { rede ->
                val papel = rede.papelTopologia
                rede.confiancaTopologia != NivelConfianca.BAIXA &&
                    (papel == PapelTopologia.ROTEADOR || papel == PapelTopologia.NO_MESH || papel == PapelTopologia.SISTEMA_MESH_PROVAVEL)
            }
        val confirmadoPorScan = redes5Ghz.isNotEmpty()

        val evidencia =
            "banda=2.4GHz linkSpeed=${wifi.linkSpeedMbps ?: "—"}Mbps rssiAtual=${rssiAtual ?: "—"}dBm " +
                if (confirmadoPorScan) {
                    "rede5GhzRssi=${melhorSinal5Ghz}dBm ouiConhecido=$temOuiConhecido"
                } else {
                    "confirmadoPorScan=false"
                }

        val mensagem =
            if (confirmadoPorScan) {
                "Seu roteador tem uma rede 5GHz disponível com sinal bom, mas você está conectado na faixa 2.4GHz."
            } else {
                "Você está na faixa 2,4GHz e a velocidade está baixa. Se o seu roteador tiver rede 5GHz (às vezes com nome parecido, tipo \"_5G\"), perto dele ela costuma ser mais rápida."
            }

        return DiagnosticResult(
            id = "REC-01",
            titulo = "Troque para o Wi-Fi 5GHz",
            status = DiagnosticStatus.info,
            evidencia = evidencia,
            mensagemUsuario = mensagem,
            recomendacao = "Troque para a rede 5GHz do mesmo roteador nas configurações de Wi-Fi do aparelho — costuma ser mais rápida e com menos interferência.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 2. Distancia do roteador / obstaculos
    // -------------------------------------------------------------------------
    // Mostrar: RSSI fraco + link speed baixo + sem evidencia de problema externo.
    // NAO mostrar: apenas com download baixo isolado (sem RSSI fraco confirmado).
    private fun recomendarDistanciaRoteador(
        input: DiagnosticInput,
        achados: FindingResult,
    ): DiagnosticResult? {
        val wifi = input.wifi ?: return null
        val rssi = wifi.rssiDbm ?: return null
        val banda = wifi.banda()
        val rssiFraco =
            when (banda) {
                BandaWifi.ghz24 -> rssi < -70
                BandaWifi.ghz5 -> rssi < -75
                BandaWifi.desconhecida -> rssi < -70
            }
        if (!rssiFraco) return null

        val linkBaixo = (wifi.linkSpeedMbps ?: Int.MAX_VALUE) < 54
        if (!linkBaixo) return null

        // Sem evidencia de problema externo: nao ha achado critico de fibra/DNS/operadora.
        val temProblemaExterno = achados.principal.id in setOf("DECISAO-GW-01", "DECISAO-00", "DECISAO-DNS-01")
        if (temProblemaExterno) return null

        return DiagnosticResult(
            id = "REC-02",
            titulo = "Aproxime-se do roteador",
            status = DiagnosticStatus.attention,
            evidencia = "rssi=${rssi}dBm banda=$banda linkSpeed=${wifi.linkSpeedMbps}Mbps",
            mensagemUsuario = "O sinal Wi-Fi está fraco e a velocidade de enlace com o roteador também está baixa — a distância ou obstáculos (paredes, móveis) podem ser a causa.",
            recomendacao = "Aproxime-se do roteador ou remova obstáculos entre ele e o dispositivo. Se não for possível, considere um repetidor ou sistema mesh.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 3. Canal Wi-Fi congestionado
    // -------------------------------------------------------------------------
    // Mostrar: scan recente existe + canal atual com muitas redes sobrepostas +
    // ha alternativa melhor (reaproveita WifiChannelDiagnosticEngine, ja rodado no
    // DiagnosticRunner e presente em internetResultados via categoria "wifi-canal").
    // Se recorrente (historico com degradacao), sugere upgrade Wi-Fi 6E/7 ou mesh —
    // nunca "Wi-Fi 6" generico para 6GHz.
    private fun recomendarCanalCongestionado(input: DiagnosticInput): DiagnosticResult? {
        val scan = input.wifiScan ?: return null
        val wifi = input.wifi ?: return null
        val canalResultados = WifiChannelDiagnosticEngine.avaliar(wifi, scan)
        val congestionado = canalResultados.firstOrNull { it.id == "WIFI-CANAL-01" } ?: return null

        val recorrente = input.historico?.degradationDetected == true
        val recomendacaoTexto =
            if (recorrente) {
                "Troque o canal Wi-Fi para um menos ocupado. Como o problema é recorrente, considere um roteador Wi-Fi 6E/7 ou um sistema mesh — eles lidam melhor com ambientes congestionados."
            } else {
                "Troque o canal Wi-Fi para um menos ocupado nas configurações do roteador e refaça o teste."
            }

        return DiagnosticResult(
            id = "REC-03",
            titulo = "Canal Wi-Fi congestionado",
            status = DiagnosticStatus.attention,
            evidencia = congestionado.evidencia,
            mensagemUsuario = "O canal Wi-Fi que você está usando tem muitas redes vizinhas competindo pelo mesmo espaço, o que pode causar lentidão e instabilidade.",
            recomendacao = recomendacaoTexto,
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 4. Roteador limitado
    // -------------------------------------------------------------------------
    // Mostrar: RSSI bom + link speed baixo + (padrao Wi-Fi antigo OU plano
    // contratado maior que o enlace suporta OU muitos dispositivos na rede).
    private fun recomendarRoteadorLimitado(input: DiagnosticInput): DiagnosticResult? {
        val wifi = input.wifi ?: return null
        val rssi = wifi.rssiDbm ?: return null
        val banda = wifi.banda()
        val rssiBom =
            when (banda) {
                BandaWifi.ghz24 -> rssi >= -60
                BandaWifi.ghz5 -> rssi >= -65
                BandaWifi.desconhecida -> rssi >= -60
            }
        if (!rssiBom) return null

        val linkSpeed = wifi.linkSpeedMbps ?: return null
        if (linkSpeed >= 144) return null

        val padraoAntigo = wifi.wifiStandard?.contains("Wi-Fi 4") == true
        val planoMaiorQueEnlace = input.velocidadeContratadaMbps?.let { it > linkSpeed } == true
        val muitosDispositivos = (wifi.dispositivosNaRede ?: 0) > 10

        if (!padraoAntigo && !planoMaiorQueEnlace && !muitosDispositivos) return null

        val motivos =
            listOfNotNull(
                "padrão Wi-Fi antigo (${wifi.wifiStandard})".takeIf { padraoAntigo },
                "plano contratado (${input.velocidadeContratadaMbps} Mbps) acima do enlace atual ($linkSpeed Mbps)".takeIf { planoMaiorQueEnlace },
                "muitos dispositivos na rede (${wifi.dispositivosNaRede})".takeIf { muitosDispositivos },
            )

        return DiagnosticResult(
            id = "REC-04",
            titulo = "O roteador pode estar limitando sua velocidade",
            status = DiagnosticStatus.info,
            evidencia = "rssi=${rssi}dBm linkSpeed=${linkSpeed}Mbps motivos=${motivos.joinToString("; ")}",
            mensagemUsuario = "O sinal Wi-Fi está bom, mas a velocidade de enlace com o roteador está limitada: ${motivos.joinToString(", ")}.",
            recomendacao = "Considere atualizar o roteador para um modelo mais recente, compatível com a velocidade do seu plano.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 5. Bufferbloat
    // -------------------------------------------------------------------------
    // Mostrar: bufferbloat > 30ms (atencao) ou > 100ms (critico). Mensagem
    // pratica sobre QoS/SQM.
    private fun recomendarBufferbloat(input: DiagnosticInput): DiagnosticResult? {
        val bb = input.internet?.bufferbloatMs ?: return null
        if (bb <= 30.0) return null

        val critico = bb > 100.0
        return DiagnosticResult(
            id = "REC-05",
            titulo = if (critico) "Bufferbloat crítico — ative QoS/SQM" else "Bufferbloat elevado — considere QoS/SQM",
            status = if (critico) DiagnosticStatus.critical else DiagnosticStatus.attention,
            evidencia = "bufferbloat=${"%.0f".format(bb)}ms",
            mensagemUsuario =
                if (critico) {
                    "O bufferbloat está muito alto (${"%.0f".format(bb)} ms) — mesmo com boa velocidade, jogos, chamadas e streaming podem travar sob carga."
                } else {
                    "O bufferbloat está elevado (${"%.0f".format(bb)} ms) — sob carga (downloads, uploads simultâneos), jogos e chamadas podem engasgar."
                },
            recomendacao = "Se o roteador suportar QoS (Quality of Service) ou SQM (Smart Queue Management), ative essa opção nas configurações avançadas — isso prioriza tráfego sensível a latência mesmo com o link ocupado.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 6. DNS lento
    // -------------------------------------------------------------------------
    // Mostrar: DNS atual com latencia alta e ha alternativa melhor com margem
    // segura (reaproveita a mesma margem de 5ms do DnsDiagnosticEngine).
    // NAO dizer que aumenta a velocidade contratada.
    private fun recomendarDnsLento(input: DiagnosticInput): DiagnosticResult? {
        val dns = input.dns ?: return null
        val atual = dns.currentDnsLatencyMs ?: return null
        if (atual <= 50) return null

        val bestMs = dns.bestDnsLatencyMsFromComparison
        val bestName = dns.bestDnsNameFromComparison
        if (bestMs == null || bestName == null) return null
        if (bestMs + 5 >= atual) return null // sem margem segura de melhora

        val nomeAtual = (dns.currentDnsName ?: "").trim().lowercase()
        if (nomeAtual.isNotBlank() && nomeAtual == bestName.trim().lowercase()) return null

        return DiagnosticResult(
            id = "REC-06",
            titulo = "Troque o DNS para reduzir a demora ao abrir sites",
            status = DiagnosticStatus.info,
            evidencia = "dnsAtual=${dns.currentDnsName ?: "—"}=${atual}ms melhor=$bestName=${bestMs}ms",
            mensagemUsuario = "O DNS atual (${dns.currentDnsName ?: "desconhecido"}) está mais lento ($atual ms) que $bestName ($bestMs ms) no comparativo.",
            recomendacao = "Trocar o DNS não aumenta a velocidade contratada, mas reduz a demora para abrir sites e apps. Configure $bestName como DNS no roteador ou no Android (DNS privado).",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 7. Operadora / rota externa
    // -------------------------------------------------------------------------
    // Mostrar: RTT gateway bom + RSSI/link local saudaveis + latencia/jitter/perda
    // externos ruins. Usar "pode estar", nao cravar certeza.
    private fun recomendarOperadoraRotaExterna(input: DiagnosticInput): DiagnosticResult? {
        val internet = input.internet ?: return null
        val rttGateway = internet.rttGatewayMs ?: return null
        if (rttGateway >= 10) return null

        val wifi = input.wifi
        if (wifi != null) {
            val rssi = wifi.rssiDbm
            val linkSpeed = wifi.linkSpeedMbps
            val rssiRuim = rssi != null && rssi < -70
            val linkRuim = linkSpeed != null && linkSpeed < 54
            if (rssiRuim || linkRuim) return null
        }

        val latenciaRuim = (internet.latencyMs ?: 0.0) > 100.0
        val jitterRuim = (internet.jitterMs ?: 0.0) > 20.0
        val perdaRuim = (internet.perdaPercentual ?: 0.0) >= 1.0
        if (!latenciaRuim && !jitterRuim && !perdaRuim) return null

        val sintomas =
            listOfNotNull(
                "latência ${"%.0f".format(internet.latencyMs)} ms".takeIf { latenciaRuim },
                "jitter ${"%.0f".format(internet.jitterMs)} ms".takeIf { jitterRuim },
                "perda ${"%.1f".format(internet.perdaPercentual)}%".takeIf { perdaRuim },
            )

        // Mensagem varia por connectionType: RTT de gateway baixo tambem ocorre em
        // rede movel (GH#521, mesmo padrao de FindingEngine DECISAO-02/02b).
        val emRedeMovel = input.connectionType == ConnectionType.mobile
        val mensagem =
            if (emRedeMovel) {
                "Sua rede móvel está respondendo rápido, mas ${sintomas.joinToString(", ")} sugerem instabilidade fora do seu aparelho."
            } else {
                "Seu roteador está respondendo rápido e o Wi-Fi está saudável, mas ${sintomas.joinToString(", ")} sugerem instabilidade fora da sua rede local."
            }

        return DiagnosticResult(
            id = "REC-07",
            titulo = "O problema pode estar fora da sua rede",
            status = DiagnosticStatus.attention,
            evidencia = "rttGateway=${rttGateway}ms sintomasExternos=${sintomas.joinToString("; ")}",
            mensagemUsuario = mensagem,
            recomendacao = "O problema pode estar na operadora ou na rota até a internet. Se persistir, contate o suporte do provedor com esses dados.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 8. Gateway / roteador lento
    // -------------------------------------------------------------------------
    // Mostrar: RTT gateway > 50ms, especialmente com RSSI bom (reforca que nao e
    // culpa do Wi-Fi, e sim do proprio roteador/gateway).
    private fun recomendarGatewayLento(input: DiagnosticInput): DiagnosticResult? {
        val rttGateway = input.internet?.rttGatewayMs ?: return null
        if (rttGateway <= 50) return null

        val rssi = input.wifi?.rssiDbm
        val rssiBom = rssi != null && rssi >= -67
        val reforco = if (rssiBom) " O sinal Wi-Fi está bom, então o atraso não é do ar — é do próprio equipamento." else ""

        // RTT de gateway alto tambem ocorre em rede movel — nao falar de "roteador"
        // nesse caso (GH#521, mesmo padrao de FindingEngine DECISAO-02/02b).
        val emRedeMovel = input.connectionType == ConnectionType.mobile
        val titulo = if (emRedeMovel) "Rede respondendo lentamente" else "Roteador respondendo lentamente"
        val mensagem =
            if (emRedeMovel) {
                "A rede está demorando $rttGateway ms para responder."
            } else {
                "O roteador está demorando $rttGateway ms para responder na rede local.$reforco"
            }
        val recomendacao =
            if (emRedeMovel) {
                "Tente desativar e reativar o modo avião, ou testar em outro local. Se o problema persistir, contate sua operadora."
            } else {
                "Reinicie o roteador: desligue da tomada, aguarde 30 segundos e ligue novamente. Se o problema persistir, o roteador pode estar sobrecarregado ou precisando de troca."
            }

        return DiagnosticResult(
            id = "REC-08",
            titulo = titulo,
            status = DiagnosticStatus.attention,
            evidencia = "rttGateway=${rttGateway}ms rssiBom=$rssiBom",
            mensagemUsuario = mensagem,
            recomendacao = recomendacao,
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 9. Fibra/ONT com problema
    // -------------------------------------------------------------------------
    // Mostrar: RX/TX fora da faixa ou temperatura elevada (usa os resultados ja
    // classificados pelo FibraSignalQualityEngine, que ja usa ClassificadorSaudeGpon).
    private fun recomendarFibraComProblema(input: DiagnosticInput): DiagnosticResult? {
        val fibra = input.fibra ?: return null
        val resultados = FibraSignalQualityEngine.avaliar(fibra)
        val problemas = resultados.filter { it.status == DiagnosticStatus.critical || it.status == DiagnosticStatus.attention }
        if (problemas.isEmpty()) return null

        val critico = problemas.any { it.status == DiagnosticStatus.critical }
        val itens = problemas.joinToString("; ") { it.titulo }

        return DiagnosticResult(
            id = "REC-09",
            titulo = if (critico) "A ONT precisa de atenção" else "Fique de olho na ONT",
            status = if (critico) DiagnosticStatus.critical else DiagnosticStatus.attention,
            evidencia = "problemasFibra=$itens",
            mensagemUsuario = "Foram encontrados indicadores fora do ideal na fibra/ONT: $itens.",
            recomendacao = "Verifique a ventilação da ONT e o estado do cabo de fibra (sem dobras apertadas). Se os valores não melhorarem, contate o provedor para verificação da OLT ou troca do equipamento.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 10. Rede movel fraca
    // -------------------------------------------------------------------------
    // Mostrar: RSRP/RSRQ/SINR ruins (tabela unica MetricClassifier, fonte
    // /regras-diagnostico-rede), roaming ativo, ou tecnologia caiu do esperado.
    private fun recomendarRedeMovelFraca(input: DiagnosticInput): DiagnosticResult? {
        if (input.connectionType != ConnectionType.mobile) return null
        val mobile = input.mobile ?: return null

        val is5g = mobile.mobileTechnology?.startsWith("5G", ignoreCase = true) == true
        val tech = if (is5g) MetricClassifier.RadioTech.NR_5G else MetricClassifier.RadioTech.LTE_4G

        val statusRuins =
            listOfNotNull(
                mobile.rsrpDbm?.let { MetricClassifier.classificarRsrp(it, tech) },
                mobile.rsrqDb?.let { MetricClassifier.classificarRsrq(it, tech) },
                mobile.sinrDb?.let { MetricClassifier.classificarSinr(it, tech) },
            )
        val algumRuim = statusRuins.any { it == MetricStatus.ruim || it == MetricStatus.critico }
        if (!algumRuim) return null

        return DiagnosticResult(
            id = "REC-10",
            titulo = "Sinal de rede móvel fraco",
            status = DiagnosticStatus.attention,
            evidencia = "rsrp=${mobile.rsrpDbm ?: "—"}dBm rsrq=${mobile.rsrqDb ?: "—"}dB sinr=${mobile.sinrDb ?: "—"}dB tecnologia=${mobile.mobileTechnology ?: "—"}",
            mensagemUsuario = "As métricas técnicas do sinal móvel (RSRP/RSRQ/SINR) indicam cobertura fraca ou muita interferência na sua localização.",
            recomendacao = "Tente se mover para perto de uma janela ou área aberta. Se estiver em roaming ou a tecnologia caiu para uma rede mais lenta que o esperado, aguarde a normalização ou tente reiniciar o modo avião.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 11. Perda de pacotes
    // -------------------------------------------------------------------------
    // So mostrar como conclusao forte quando medicao confiavel; se estimada por
    // timeout HTTP (packetLossSource == "estimated"), avisar que e indicio, nao
    // certeza. "naoMedido"/"unknown" sem medicao real -> nao mostra recomendacao.
    private fun recomendarPerdaDePacotes(input: DiagnosticInput): DiagnosticResult? {
        val internet = input.internet ?: return null
        val perda = internet.perdaPercentual ?: return null
        if (perda < 1.0) return null

        val fonte = internet.packetLossSource
        if (fonte == "naoMedido" || fonte == "unknown") return null

        val estimada = fonte == "estimated"
        // .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): mesmo gate de confianca amostral usado por
        // GameReadinessClassifier/UsageProfileClassifier/ScoreEngine — 1 timeout
        // isolado (confianca insuficiente, ou null/desconhecida) nao deve sozinho
        // escalar esta recomendacao para "critico" (texto mais forte, "contate a
        // operadora"). Nao suprime a recomendacao inteira — perda >=1% ainda aparece
        // como "Atencao", so o nivel "critico" exige confianca suficiente.
        val critico = perda >= 3.0 && internet.perdaConfianca == ConfiancaAmostral.SUFICIENTE

        val avisoConfiabilidade =
            if (estimada) {
                " Esse valor foi estimado por timeout de rede, não por medição direta — é um indício, não uma certeza."
            } else {
                ""
            }

        // GH#521: nao falar de "roteador/modem" quando a conexao e rede movel.
        val emRedeMovel = input.connectionType == ConnectionType.mobile

        return DiagnosticResult(
            id = "REC-11",
            titulo = if (critico) "Perda de pacotes alta" else "Alguma perda de pacotes detectada",
            status = if (critico) DiagnosticStatus.critical else DiagnosticStatus.attention,
            evidencia = "perda=${"%.1f".format(perda)}% fonte=${fonte ?: "—"}",
            mensagemUsuario = "Foi detectada perda de pacotes de ${"%.1f".format(perda)}%.$avisoConfiabilidade",
            recomendacao =
                if (critico) {
                    if (emRedeMovel) {
                        "Teste em outro local ou horário. Se a perda persistir mesmo assim, contate a operadora — chamadas e jogos serão afetados."
                    } else {
                        "Reinicie o roteador e o modem. Se a perda persistir mesmo assim, contate o provedor — chamadas e jogos serão afetados."
                    }
                } else {
                    "Fique de olho: perda de pacotes moderada pode afetar chamadas e jogos em momentos de pico."
                },
            categoria = CAT,
            podeConcluir = critico && !estimada,
        )
    }

    // -------------------------------------------------------------------------
    // 13. Preset de device para jogos (SIG-290, GameReadinessClassifier)
    // -------------------------------------------------------------------------
    // Mostrar: quando alguma das 3 categorias de prontidao para jogos (aba 10) NAO
    // esta "Bom" — texto condicional por device. Device automatico (o proprio
    // Android/iPhone rodando o app) usa os dados JA coletados (Wi-Fi/RSSI). Os
    // demais presets (PS5/PS4, Xbox, PC, Switch) sao texto condicional, acionados
    // apenas quando o usuario respondeu a pergunta "qual jogo/console voce mais
    // usa" (arvore "qual_jogo_device", DynamicQuestionEngine) — sem esse dado, nao
    // ha base para citar um device que o app nao mediu.
    // NAO mostrar: todas as 3 categorias "Bom" (nada a recomendar).
    private fun recomendarDevicePresetGaming(input: DiagnosticInput): DiagnosticResult? {
        val readiness = GameReadinessClassifier.classificarTodos(input)
        val comProblema =
            readiness.filter {
                it.status == GameReadinessClassifier.ReadinessStatus.Atencao ||
                    it.status == GameReadinessClassifier.ReadinessStatus.Ruim
            }
        if (comProblema.isEmpty()) return null

        val critico = comProblema.any { it.status == GameReadinessClassifier.ReadinessStatus.Ruim }
        val dicaDevice = dicaPresetDevice(input.deviceGamingSelecionado)
        val categorias = comProblema.joinToString(", ") { it.categoria.labelCurto() }

        return DiagnosticResult(
            id = "REC-13",
            titulo = "Ajustes recomendados para jogar melhor",
            status = if (critico) DiagnosticStatus.critical else DiagnosticStatus.attention,
            evidencia = "categoriasComProblema=$categorias device=${input.deviceGamingSelecionado ?: "auto(android/iphone)"}",
            mensagemUsuario = "Sua conexão não está no ponto ideal para $categorias no momento.",
            recomendacao = dicaDevice,
            categoria = CAT,
        )
    }

    private fun GameReadinessClassifier.Categoria.labelCurto(): String =
        when (this) {
            GameReadinessClassifier.Categoria.FPS_COMPETITIVO -> "FPS competitivo"
            GameReadinessClassifier.Categoria.CLOUD_GAMING -> "cloud gaming"
            GameReadinessClassifier.Categoria.MOBILE_COMPETITIVO -> "mobile competitivo"
        }

    // Device presets (aba 10): dado/checklist plugado aqui, sem engine nova. Cada
    // preset cita o sinal proprio que o documento associa aquele device. "mobile"
    // e o unico automatico de verdade (dados ja vem do proprio Android rodando o
    // app); os demais sao texto condicional so quando o usuario seleciona o device
    // manualmente na arvore "qual_jogo_device".
    private fun dicaPresetDevice(device: String?): String =
        when (device) {
            "playstation" -> "No PS5/PS4: prefira cabo Ethernet quando possível; se usar Wi-Fi, fique perto do roteador em 5GHz. Verifique o tipo de NAT em Configurações > Rede — NAT tipo 3 prejudica partidas com outros jogadores."
            "xbox" -> "No Xbox: verifique o tipo de NAT em Configurações > Rede — NAT Restrito ou Estrito prejudica matchmaking e chat. Ative UPnP no roteador se disponível, ou configure port forwarding manual."
            "pc" -> "No PC: atualize o driver da placa de Wi-Fi, desative o modo de economia de energia do adaptador de rede e feche downloads/updates em segundo plano (Steam, Windows Update) antes de jogar."
            "switch" -> "No Nintendo Switch: o Wi-Fi do console é mais limitado — fique o mais próximo possível do roteador, de preferência com linha de visada direta."
            "mobile", null -> "No celular: jogue perto do roteador em 5GHz, evite a alternância automática entre Wi-Fi e rede móvel e ative o modo de economia de bateria só depois de jogar."
            else -> "Priorize a rede 5GHz perto do roteador, reduza downloads em segundo plano e evite Wi-Fi fraco."
        }

    // -------------------------------------------------------------------------
    // 14. Upgrade de roteador/mesh/Wi-Fi 6E — SOMENTE com recorrencia
    // -------------------------------------------------------------------------
    // Mostrar: achado de Wi-Fi/canal congestionado/roteador limitado JA recorrente
    // no historico (mesma infraestrutura 7d/30d do HistoricalDegradationEngine —
    // input.historico.degradationDetected). NUNCA no primeiro teste isolado, mesmo
    // que o teste atual esteja ruim — recomendacao de compra exige padrao repetido.
    private fun recomendarUpgradeRoteadorRecorrente(input: DiagnosticInput): DiagnosticResult? {
        val historico = input.historico ?: return null
        if (historico.degradationDetected != true) return null

        val wifi = input.wifi ?: return null
        val banda = wifi.banda()
        val rssiFraco =
            when (banda) {
                BandaWifi.ghz24 -> (wifi.rssiDbm ?: 0) < -60
                BandaWifi.ghz5 -> (wifi.rssiDbm ?: 0) < -65
                BandaWifi.desconhecida -> (wifi.rssiDbm ?: 0) < -60
            }
        val linkBaixo = (wifi.linkSpeedMbps ?: Int.MAX_VALUE) < 144
        val problemaWifiRecorrente = rssiFraco || linkBaixo || banda == BandaWifi.ghz24
        if (!problemaWifiRecorrente) return null

        return DiagnosticResult(
            id = "REC-14",
            titulo = "Considere trocar o roteador",
            status = DiagnosticStatus.info,
            evidencia = "degradacaoRecorrente=true banda=$banda rssi=${wifi.rssiDbm ?: "—"}dBm linkSpeed=${wifi.linkSpeedMbps ?: "—"}Mbps degradacaoPercent=${historico.degradationPercent ?: "—"}",
            mensagemUsuario = "O problema de Wi-Fi vem se repetindo nos últimos testes, não é algo pontual de hoje.",
            recomendacao = "Considere um roteador Wi-Fi 6E ou um sistema mesh — equipamentos mais recentes lidam melhor com múltiplos dispositivos e ambientes congestionados que o roteador atual parece não estar suportando bem.",
            categoria = CAT,
        )
    }

    // -------------------------------------------------------------------------
    // 12. Score geral
    // -------------------------------------------------------------------------
    // Recomendacao-resumo quando multiplos fatores convergem: principal + pelo
    // menos 2 outras recomendacoes/achados relevantes (nao-ok) no mesmo diagnostico.
    // REC-13/REC-14 (gaming, SIG-290) ficam FORA da contagem: sao derivados dos
    // mesmos numeros que ja geraram outras recomendacoes (ex.: bufferbloat alto ja
    // conta via REC-05) — contar de novo inflaria "fatores" artificialmente.
    private fun recomendarScoreGeral(
        achados: FindingResult,
        recomendacoesGeradas: List<DiagnosticResult>,
    ): DiagnosticResult? {
        val fatoresRelevantes =
            recomendacoesGeradas.count {
                it.id !in setOf("REC-13", "REC-14") &&
                    (it.status == DiagnosticStatus.attention || it.status == DiagnosticStatus.critical)
            } +
                achados.secundarios.count {
                    it.status == DiagnosticStatus.attention || it.status == DiagnosticStatus.critical
                }
        if (fatoresRelevantes < 2) return null

        val critico =
            achados.principal.status == DiagnosticStatus.critical ||
                recomendacoesGeradas.any { it.status == DiagnosticStatus.critical }

        return DiagnosticResult(
            id = "REC-12",
            titulo = "Vários fatores estão afetando sua conexão",
            status = if (critico) DiagnosticStatus.critical else DiagnosticStatus.attention,
            evidencia = "principal=${achados.principal.id} fatoresRelevantes=$fatoresRelevantes",
            mensagemUsuario = "Identificamos ${fatoresRelevantes + 1} fatores que juntos explicam a qualidade da sua conexão hoje, não apenas um problema isolado.",
            recomendacao = "Resolva primeiro o item de maior severidade (${achados.principal.titulo}) e refaça o diagnóstico — os demais pontos podem melhorar junto ou ficar mais fáceis de identificar.",
            categoria = CAT,
        )
    }
}
