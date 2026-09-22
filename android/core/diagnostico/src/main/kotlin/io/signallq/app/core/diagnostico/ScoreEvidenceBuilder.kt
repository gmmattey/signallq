package io.signallq.app.core.diagnostico

/**
 * Converte [DiagnosticInput] + os resultados ja calculados pelos engines (via
 * [DiagnosticReport]) em [EvidenceScore] por dimensao, prontos para o [ScoreEngine].
 *
 * Nao reclassifica nada do zero — reaproveita o [MetricClassifier] (fonte unica de
 * thresholds) e os [DiagnosticResult] que o [DiagnosticRunner] ja produziu, so
 * traduzindo para nota 0–100 + [Provenance]. Mantem coleta de dados, classificacao e
 * pontuacao em camadas separadas (regra do motor-diagnostico).
 */
object ScoreEvidenceBuilder {
    fun tipoConexao(input: DiagnosticInput): ScoreEngine.TipoConexao =
        when {
            input.connectionType == ConnectionType.mobile -> ScoreEngine.TipoConexao.MOVEL
            input.fibra?.isUp == true -> ScoreEngine.TipoConexao.FIBRA
            input.connectionType == ConnectionType.wifi -> ScoreEngine.TipoConexao.WIFI
            else -> ScoreEngine.TipoConexao.DESCONHECIDO
        }

    fun construir(
        input: DiagnosticInput,
        report: DiagnosticReport,
    ): List<EvidenceScore> =
        listOfNotNull(
            estabilidade(input),
            wifiRedeLocal(input),
            velocidade(input),
            dns(input),
            historico(input, report),
            fibra(input),
            sinalMovel(input),
            // Dimensoes auxiliares usadas so pelos tetos do ScoreEngine (nao entram
            // nos pesos de nenhum TipoConexao, ver ScoreEngine.pesosPara).
            perdaPacotesStatus(input),
            bufferbloatStatus(input),
            fibraRxStatus(input),
            rssiStatus(input),
        )

    // ── Estabilidade: latencia + jitter + perda de pacotes + RTT gateway ───────
    private fun estabilidade(input: DiagnosticInput): EvidenceScore {
        val internet = input.internet
        if (internet == null) {
            return EvidenceScore("estabilidade", null, Provenance.indisponivel)
        }
        val notas = mutableListOf<Int>()
        internet.latencyMs?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarLatencia(it)) }
        internet.jitterMs?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarJitter(it)) }
        internet.perdaPercentual?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarPerdaPacotes(it)) }
        internet.bufferbloatMs?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarBufferbloat(it)) }
        if (notas.isEmpty()) return EvidenceScore("estabilidade", null, Provenance.indisponivel)

        val fonteInconfiavel = internet.packetLossSource == "estimated"
        return EvidenceScore("estabilidade", notas.min(), if (fonteInconfiavel) Provenance.estimada else Provenance.medida)
    }

    // ── Wi-Fi / rede local: RSSI + link speed ───────────────────────────────────
    private fun wifiRedeLocal(input: DiagnosticInput): EvidenceScore? {
        val wifi = input.wifi ?: return EvidenceScore("wifiRedeLocal", null, Provenance.indisponivel)
        val rssi = wifi.rssiDbm
        if (rssi == null) return EvidenceScore("wifiRedeLocal", null, Provenance.indisponivel)

        val banda =
            when (wifi.banda()) {
                BandaWifi.ghz24 -> MetricClassifier.WifiBand.GHZ_2_4
                BandaWifi.ghz5 -> MetricClassifier.WifiBand.GHZ_5
                BandaWifi.desconhecida -> MetricClassifier.WifiBand.GHZ_5
            }
        val notaRssi = ScoreEngine.notaParaStatus(MetricClassifier.classificarRssiWifi(rssi, banda))
        val linkSpeed = wifi.linkSpeedMbps
        val notaLink =
            when {
                linkSpeed == null -> null
                linkSpeed < 54 -> 40
                linkSpeed < 144 -> 70
                else -> 100
            }
        val nota = if (notaLink != null) minOf(notaRssi, notaLink) else notaRssi
        return EvidenceScore("wifiRedeLocal", nota, Provenance.medida)
    }

    // ── Velocidade: download + upload ───────────────────────────────────────────
    private fun velocidade(input: DiagnosticInput): EvidenceScore {
        val internet = input.internet
        val dl = internet?.downloadMbps
        if (dl == null) return EvidenceScore("velocidade", null, Provenance.indisponivel)

        val notaDownload =
            when {
                dl >= 100.0 -> 100
                dl >= 50.0 -> 85
                dl >= 25.0 -> 70
                dl >= 10.0 -> 45
                else -> 15
            }
        val ul = internet.uploadMbps
        val notaUpload =
            when {
                ul == null -> null
                ul <= 0.0 -> 15
                ul < 5.0 -> 55
                else -> 100
            }
        val nota = if (notaUpload != null) minOf(notaDownload, notaUpload) else notaDownload
        return EvidenceScore("velocidade", nota, Provenance.medida)
    }

    // ── DNS ──────────────────────────────────────────────────────────────────
    private fun dns(input: DiagnosticInput): EvidenceScore {
        val latencia =
            input.dns?.currentDnsLatencyMs
                ?: return EvidenceScore("dns", null, Provenance.indisponivel)
        val nota = ScoreEngine.notaParaStatus(MetricClassifier.classificarLatenciaDns(latencia))
        return EvidenceScore("dns", nota, Provenance.medida)
    }

    // ── Historico: reaproveita o status ja calculado pelo HistoricalDegradationEngine ──
    private fun historico(
        input: DiagnosticInput,
        report: DiagnosticReport,
    ): EvidenceScore {
        if (input.historico == null) return EvidenceScore("historico", null, Provenance.indisponivel)
        val resultados = report.historicoResultados
        if (resultados.isEmpty() || resultados.any { it.status == DiagnosticStatus.inconclusive }) {
            return EvidenceScore("historico", null, Provenance.indisponivel)
        }
        val pior = resultados.maxByOrNull { severidade(it.status) } ?: return EvidenceScore("historico", null, Provenance.indisponivel)
        return EvidenceScore("historico", ScoreEngine.notaParaDiagnosticStatus(pior.status), Provenance.medida)
    }

    // ── Fibra: pior entre RX/TX/temperatura (ClassificadorSaudeGpon ja aplica o pior-caso) ──
    private fun fibra(input: DiagnosticInput): EvidenceScore {
        val f = input.fibra ?: return EvidenceScore("fibra", null, Provenance.indisponivel)
        if (!f.isUp) return EvidenceScore("fibra", 0, Provenance.medida)
        if (f.rxPowerDbm == null && f.txPowerDbm == null && f.temperatureCelsius == null) {
            return EvidenceScore("fibra", null, Provenance.indisponivel)
        }
        val status =
            io.signallq.app.core.network.contracts.fibra.ClassificadorSaudeGpon.classificar(
                isUp = f.isUp,
                rxPowerDbm = f.rxPowerDbm ?: 0.0,
                txPowerDbm = f.txPowerDbm ?: 0.0,
                temperatureCelsius = f.temperatureCelsius ?: 0.0,
            )
        val nota =
            when (status) {
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.boa -> 100
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.regular -> 60
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.ruim -> 15
            }
        return EvidenceScore("fibra", nota, Provenance.medida)
    }

    // ── Sinal movel: pior entre RSRP/RSRQ/SINR ──────────────────────────────────
    private fun sinalMovel(input: DiagnosticInput): EvidenceScore {
        if (input.connectionType != ConnectionType.mobile) return EvidenceScore("sinalMovel", null, Provenance.indisponivel)
        val mobile = input.mobile ?: return EvidenceScore("sinalMovel", null, Provenance.indisponivel)

        val is5g = mobile.mobileTechnology?.startsWith("5G", ignoreCase = true) == true
        val tech = if (is5g) MetricClassifier.RadioTech.NR_5G else MetricClassifier.RadioTech.LTE_4G

        val notas = mutableListOf<Int>()
        mobile.rsrpDbm?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarRsrp(it, tech)) }
        mobile.rsrqDb?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarRsrq(it, tech)) }
        mobile.sinrDb?.let { notas += ScoreEngine.notaParaStatus(MetricClassifier.classificarSinr(it, tech)) }
        if (notas.isEmpty()) return EvidenceScore("sinalMovel", null, Provenance.indisponivel)

        return EvidenceScore("sinalMovel", notas.min(), Provenance.medida)
    }

    // ── Dimensoes auxiliares (usadas so pelos tetos do ScoreEngine) ────────────

    // MetricClassifier.classificarPerdaPacotes nao tem faixa "critico" dedicada (so
    // vai ate "ruim" — ver kdoc do MetricClassifier). O teto de score, porem, precisa
    // reconhecer perda REALMENTE critica pelo threshold de negocio ja usado em
    // InternetDiagnosticEngine/RecomendacaoPraticaEngine (>=3% = critico), entao a nota
    // aqui e calculada direto (nao via MetricClassifier) para preservar essa faixa.
    private fun perdaPacotesStatus(input: DiagnosticInput): EvidenceScore {
        val internet = input.internet
        val perda = internet?.perdaPercentual ?: return EvidenceScore("perdaPacotesStatus", null, Provenance.indisponivel)
        val fonte = internet.packetLossSource
        val provenance =
            when (fonte) {
                "estimated" -> Provenance.estimada
                "naoMedido", "unknown", null -> Provenance.indisponivel
                else -> Provenance.medida
            }
        if (provenance == Provenance.indisponivel) return EvidenceScore("perdaPacotesStatus", null, provenance)
        val nota =
            when {
                perda >= 3.0 -> ScoreEngine.notaParaStatus(MetricStatus.critico)
                perda >= 1.0 -> ScoreEngine.notaParaStatus(MetricStatus.ruim)
                else -> ScoreEngine.notaParaStatus(MetricClassifier.classificarPerdaPacotes(perda))
            }
        return EvidenceScore("perdaPacotesStatus", nota, provenance, confiancaAmostral = internet.perdaConfianca)
    }

    private fun bufferbloatStatus(input: DiagnosticInput): EvidenceScore {
        val bb = input.internet?.bufferbloatMs ?: return EvidenceScore("bufferbloatStatus", null, Provenance.indisponivel)
        return EvidenceScore("bufferbloatStatus", ScoreEngine.notaParaStatus(MetricClassifier.classificarBufferbloat(bb)), Provenance.medida)
    }

    private fun fibraRxStatus(input: DiagnosticInput): EvidenceScore {
        val rx = input.fibra?.rxPowerDbm ?: return EvidenceScore("fibraRxStatus", null, Provenance.indisponivel)
        val status =
            io.signallq.app.core.network.contracts.fibra.ClassificadorSaudeGpon
                .classificarRx(rx)
        val nota =
            when (status) {
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.boa -> 100
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.regular -> 60
                io.signallq.app.core.network.contracts.fibra.GponSaudeStatus.ruim -> 15
            }
        return EvidenceScore("fibraRxStatus", nota, Provenance.medida)
    }

    private fun rssiStatus(input: DiagnosticInput): EvidenceScore {
        val wifi = input.wifi ?: return EvidenceScore("rssiStatus", null, Provenance.indisponivel)
        val rssi = wifi.rssiDbm ?: return EvidenceScore("rssiStatus", null, Provenance.indisponivel)
        val banda =
            when (wifi.banda()) {
                BandaWifi.ghz24 -> MetricClassifier.WifiBand.GHZ_2_4
                BandaWifi.ghz5 -> MetricClassifier.WifiBand.GHZ_5
                BandaWifi.desconhecida -> MetricClassifier.WifiBand.GHZ_5
            }
        val nota = ScoreEngine.notaParaStatus(MetricClassifier.classificarRssiWifi(rssi, banda))
        return EvidenceScore("rssiStatus", nota, Provenance.medida)
    }

    private fun severidade(status: DiagnosticStatus): Int =
        when (status) {
            DiagnosticStatus.critical -> 4
            DiagnosticStatus.attention -> 2
            DiagnosticStatus.info -> 1
            DiagnosticStatus.inconclusive -> 1
            DiagnosticStatus.ok -> 0
        }
}
