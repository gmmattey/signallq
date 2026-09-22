package io.signallq.app.core.diagnostico

/**
 * Classificador dos 5 perfis de uso (aba 9 do documento de produto — Navegacao,
 * Streaming, Jogos, Videochamada, Trabalho) — sucessor do card "Impacto no uso"
 * que hoje e texto livre decidido pela IA ([io.signallq.app.feature.diagnostico.ai.AiImpacto]).
 *
 * ## Por que existe (SIG-289)
 * Ate esta issue, `result.impacto.navegacao/streaming/jogos/videochamada/trabalho`
 * era texto livre gerado pela IA a cada chamada — sem pesos fixos, sem faixas
 * documentadas, inconsistente entre execucoes. Este objeto calcula os 5 perfis
 * localmente, de forma deterministica, a partir das MESMAS evidencias que ja
 * alimentam o [ScoreEngine] (SIG-288) e o [MetricClassifier] (SIG-285).
 *
 * ## Vocabulario — NAO confundir com [MetricStatus]
 * Perfil de uso usa um vocabulario PROPRIO e SEPARADO: [UsageProfileStatus.OK] /
 * [UsageProfileStatus.Instavel] / [UsageProfileStatus.Comprometido] — decisao de
 * arquitetura ja fechada, documentada tambem no kdoc de [MetricClassifier].
 * Mapeamento de referencia (quando preciso comparar em texto, nunca automatize
 * sem contexto de perfil): excelente/bom -> OK | regular -> Instavel | ruim/critico
 * -> Comprometido | inconclusivo -> sem equivalente direto.
 *
 * ## Como funciona
 * 1. Cada perfil calcula um score 0-100 por media ponderada das dimensoes que o
 *    documento de produto define para ele (pesos abaixo), usando as MESMAS
 *    metricas brutas de [DiagnosticInput] (sem reclassificar do zero).
 * 2. Dimensao sem dado disponivel SAI do calculo — peso redistribuido entre as
 *    disponiveis (mesmo principio de reponderacao do [ScoreEngine]). Perfil sem
 *    NENHUMA dimensao disponivel retorna null (status "sem dados").
 * 3. O score determina o status base (OK/Instavel/Comprometido) pelas faixas por
 *    metrica documentadas no produto — nao e um corte generico de score, e sim a
 *    PIOR faixa batida entre as metricas do perfil (pior caso vence, mesmo padrao
 *    do [ScoreEngine.aplicarTetos]).
 * 4. Penalidades contextuais APLICADAS DEPOIS do calculo base (nunca misturadas
 *    nele): Wi-Fi fraco rebaixa 1 nivel; RSSI critico rebaixa streaming/
 *    videochamada/jogos direto a Comprometido.
 * 5. Perda de pacotes ESTIMADA (nao medida real) nunca eleva o status a
 *    Comprometido sozinha — reduz [UsageProfileResult.confianca], nao o status
 *    (mesmo principio de [Provenance.estimada] em todo o motor).
 *
 * ## Reuso — evolucao do embriao, nao duplicacao
 * O antigo [io.signallq.app.feature.speedtest.SpeedtestQualityClassifier]
 * (`:featureSpeedtest`) classificava so 3 perfis (streaming/gamer/videochamada)
 * com um unico limiar cada, sem pesos, sem separar HD/4K, sem Navegacao/Trabalho,
 * sem provenance. Este objeto e a evolucao dele para o vocabulario e a granularidade
 * definitivos do produto. `:featureDiagnostico` nao pode depender de
 * `:featureSpeedtest` (lei de dependencias `:feature* -> :feature*` proibida) —
 * os thresholds de bufferbloat sao os MESMOS ja replicados em [MetricClassifier]
 * (nenhum novo valor inventado aqui).
 */
object UsageProfileClassifier {
    enum class Perfil { NAVEGACAO, STREAMING, JOGOS, VIDEOCHAMADA, TRABALHO }

    enum class UsageProfileStatus { OK, Instavel, Comprometido }

    /**
     * Uma dimensao de entrada, ja em nota 0-100 (maior = melhor) + [Provenance] +
     * o [UsageProfileStatus] individual que a faixa da metrica bateu (usado para
     * decidir o status final por "pior faixa vence", nao so pela media do score).
     */
    private data class Dimensao(
        val nome: String,
        val pesoBase: Double,
        val nota: Int?,
        val statusFaixa: UsageProfileStatus?,
        val provenance: Provenance,
    )

    /**
     * Resultado de um perfil de uso: status ja com penalidades contextuais
     * aplicadas, score 0-100 (media ponderada, antes das penalidades — referencia
     * numerica, nao decide status sozinho), confianca 0-1, motivo em linguagem
     * simples, evidencias usadas e uma acao recomendada quando Instavel/Comprometido.
     */
    data class UsageProfileResult(
        val perfil: Perfil,
        val status: UsageProfileStatus?,
        val score: Int?,
        val confianca: Double,
        val motivo: String,
        val evidencias: List<String>,
        val acaoRecomendada: String?,
        val dadosAusentes: List<String>,
    )

    fun classificarTodos(input: DiagnosticInput): List<UsageProfileResult> =
        Perfil.entries.map { classificar(it, input) }

    fun classificar(
        perfil: Perfil,
        input: DiagnosticInput,
    ): UsageProfileResult {
        val dimensoes = dimensoesPara(perfil, input)
        val disponiveis = dimensoes.filter { it.provenance != Provenance.indisponivel && it.nota != null }
        val dadosAusentes = dimensoes.map { it.nome } - disponiveis.map { it.nome }.toSet()

        if (disponiveis.isEmpty()) {
            return UsageProfileResult(
                perfil = perfil,
                status = null,
                score = null,
                confianca = 0.0,
                motivo = "Sem dados suficientes para avaliar ${perfil.label()}.",
                evidencias = emptyList(),
                acaoRecomendada = null,
                dadosAusentes = dadosAusentes,
            )
        }

        val somaPesos = disponiveis.sumOf { it.pesoBase }
        val score = disponiveis.sumOf { (it.nota ?: 0) * (it.pesoBase / somaPesos) }.toInt().coerceIn(0, 100)

        // Pior faixa entre as dimensoes disponiveis vence — nao e so o corte pela media.
        val piorFaixa =
            disponiveis.mapNotNull { it.statusFaixa }.maxByOrNull { it.severidade() }
                ?: statusPorScore(score)

        val temPerdaEstimada = disponiveis.any { it.nome == "perda" && it.provenance == Provenance.estimada }
        val statusComPenalidades = aplicarPenalidadesContextuais(perfil, piorFaixa, input)

        val confianca = calcularConfianca(disponiveis, dadosAusentes.size, temPerdaEstimada)
        val evidencias = evidenciasPara(perfil, input)
        val motivo = motivoPara(perfil, statusComPenalidades, input)
        val acao = acaoRecomendadaPara(perfil, statusComPenalidades)

        return UsageProfileResult(
            perfil = perfil,
            status = statusComPenalidades,
            score = score,
            confianca = confianca,
            motivo = motivo,
            evidencias = evidencias,
            acaoRecomendada = acao,
            dadosAusentes = dadosAusentes,
        )
    }

    // ── Penalidades transversais (aplicadas DEPOIS do calculo base) ────────────

    /**
     * Wi-Fi fraco rebaixa 1 nivel (OK->Instavel, Instavel->Comprometido). RSSI
     * muito fraco (<=-75dBm) ou link speed muito baixo (<12Mbps — abaixo do minimo
     * util de qualquer perfil) pode comprometer streaming/videochamada/jogos
     * diretamente (rebaixa a Comprometido de uma vez), pois nenhuma metrica de
     * throughput/latencia medida no ar sobrevive a um enlace fisico ruim.
     */
    private fun aplicarPenalidadesContextuais(
        perfil: Perfil,
        statusBase: UsageProfileStatus,
        input: DiagnosticInput,
    ): UsageProfileStatus {
        val wifi = input.wifi ?: return statusBase
        val rssi = wifi.rssiDbm
        val linkSpeed = wifi.linkSpeedMbps

        val rssiMuitoFraco = rssi != null && rssi <= -75
        val linkMuitoBaixo = linkSpeed != null && linkSpeed < 12
        val wifiFraco = rssiMuitoFraco || linkMuitoBaixo
        if (!wifiFraco) return statusBase

        val podeComprometerDireto = perfil in setOf(Perfil.STREAMING, Perfil.VIDEOCHAMADA, Perfil.JOGOS)
        if (podeComprometerDireto && rssiMuitoFraco) return UsageProfileStatus.Comprometido

        return statusBase.rebaixarUmNivel()
    }

    private fun UsageProfileStatus.rebaixarUmNivel(): UsageProfileStatus =
        when (this) {
            UsageProfileStatus.OK -> UsageProfileStatus.Instavel
            UsageProfileStatus.Instavel -> UsageProfileStatus.Comprometido
            UsageProfileStatus.Comprometido -> UsageProfileStatus.Comprometido
        }

    private fun UsageProfileStatus.severidade(): Int =
        when (this) {
            UsageProfileStatus.OK -> 0
            UsageProfileStatus.Instavel -> 1
            UsageProfileStatus.Comprometido -> 2
        }

    private fun statusPorScore(score: Int): UsageProfileStatus =
        when {
            score >= 80 -> UsageProfileStatus.OK
            score >= 50 -> UsageProfileStatus.Instavel
            else -> UsageProfileStatus.Comprometido
        }

    // ── Confianca: cai com dados ausentes e com perda de pacotes so estimada ───

    private fun calcularConfianca(
        disponiveis: List<Dimensao>,
        ausentes: Int,
        temPerdaEstimada: Boolean,
    ): Double {
        val total = disponiveis.size + ausentes
        if (total == 0) return 0.0
        var confianca = disponiveis.size.toDouble() / total
        if (temPerdaEstimada) confianca *= 0.75
        return confianca.coerceIn(0.0, 1.0)
    }

    // ── Dimensoes por perfil (pesos e faixas — aba 9 do documento de produto) ──

    private fun dimensoesPara(
        perfil: Perfil,
        input: DiagnosticInput,
    ): List<Dimensao> =
        when (perfil) {
            Perfil.NAVEGACAO -> dimensoesNavegacao(input)
            Perfil.STREAMING -> dimensoesStreaming(input)
            Perfil.JOGOS -> dimensoesJogos(input)
            Perfil.VIDEOCHAMADA -> dimensoesVideochamada(input)
            Perfil.TRABALHO -> dimensoesTrabalho(input)
        }

    /** Navegacao: DNS 35% + latencia 25% + perda 20% + jitter 10% + download 10%. */
    private fun dimensoesNavegacao(input: DiagnosticInput): List<Dimensao> {
        val internet = input.internet
        val dnsMs = input.dns?.currentDnsLatencyMs
        val latenciaMs = internet?.latencyMs
        val jitterMs = internet?.jitterMs
        val download = internet?.downloadMbps
        val perda = perdaDimensao(internet, peso = 0.20)

        return listOf(
            dimFaixa(
                nome = "dns",
                peso = 0.35,
                valor = dnsMs?.toDouble(),
                provenance = Provenance.medida,
                ok = { it <= 50.0 },
                instavel = { it <= 150.0 },
            ),
            dimFaixa(
                nome = "latencia",
                peso = 0.25,
                valor = latenciaMs,
                provenance = Provenance.medida,
                ok = { it <= 80.0 },
                instavel = { it <= 150.0 },
            ),
            perda,
            dimFaixa(
                nome = "jitter",
                peso = 0.10,
                valor = jitterMs,
                provenance = Provenance.medida,
                ok = { it <= 20.0 },
                instavel = { it <= 40.0 },
            ),
            dimFaixa(
                nome = "download",
                peso = 0.10,
                valor = download,
                provenance = Provenance.medida,
                ok = { it >= 10.0 },
                instavel = { it >= 5.0 },
            ),
        )
    }

    /**
     * Streaming: download 45% + bufferbloat 25% + perda 15% + jitter 10% + historico 5%.
     * Faixas de download SEPARADAS por qualidade (HD/4K) — usa a faixa 4K (mais exigente)
     * como base, pois o card nao distingue qualidade selecionada pelo usuario; ver
     * [evidenciasPara] para o texto mostrar as duas faixas.
     */
    private fun dimensoesStreaming(input: DiagnosticInput): List<Dimensao> {
        val internet = input.internet
        val download = internet?.downloadMbps
        val bufferbloat = internet?.bufferbloatMs
        val jitterMs = internet?.jitterMs
        val historico = historicoDimensao(input, peso = 0.05)
        val perda = perdaDimensao(internet, peso = 0.15)

        return listOf(
            dimFaixa(
                nome = "download",
                peso = 0.45,
                valor = download,
                provenance = Provenance.medida,
                ok = { it >= 25.0 },
                instavel = { it >= 15.0 },
            ),
            dimFaixa(
                nome = "bufferbloat",
                peso = 0.25,
                valor = bufferbloat,
                provenance = Provenance.medida,
                ok = { it <= 30.0 },
                instavel = { it <= 100.0 },
            ),
            perda,
            dimFaixa(
                nome = "jitter",
                peso = 0.10,
                valor = jitterMs,
                provenance = Provenance.medida,
                ok = { it <= 20.0 },
                instavel = { it <= 40.0 },
            ),
            historico,
        )
    }

    /** Jogos: latencia 35% + jitter 25% + perda 25% + bufferbloat 10% + banda 5%. */
    private fun dimensoesJogos(input: DiagnosticInput): List<Dimensao> {
        val internet = input.internet
        val latenciaMs = internet?.latencyMs
        val jitterMs = internet?.jitterMs
        val bufferbloat = internet?.bufferbloatMs
        val perda = perdaDimensao(internet, peso = 0.25)
        val banda =
            minOf(internet?.downloadMbps ?: Double.MAX_VALUE, internet?.uploadMbps ?: Double.MAX_VALUE)
                .takeIf { it != Double.MAX_VALUE }

        return listOf(
            dimFaixa(
                nome = "latencia",
                peso = 0.35,
                valor = latenciaMs,
                provenance = Provenance.medida,
                ok = { it <= 50.0 },
                instavel = { it <= 100.0 },
            ),
            dimFaixa(
                nome = "jitter",
                peso = 0.25,
                valor = jitterMs,
                provenance = Provenance.medida,
                ok = { it <= 15.0 },
                instavel = { it <= 30.0 },
            ),
            perda,
            dimFaixa(
                nome = "bufferbloat",
                peso = 0.10,
                valor = bufferbloat,
                provenance = Provenance.medida,
                ok = { it <= 30.0 },
                instavel = { it <= 100.0 },
            ),
            dimFaixa(
                nome = "banda",
                peso = 0.05,
                valor = banda,
                provenance = Provenance.medida,
                ok = { it >= 10.0 },
                instavel = { it >= 5.0 },
            ),
        )
    }

    /** Videochamada: upload 30% + jitter 25% + perda 25% + latencia 10% + bufferbloat 10%. */
    private fun dimensoesVideochamada(input: DiagnosticInput): List<Dimensao> {
        val internet = input.internet
        val upload = internet?.uploadMbps
        val jitterMs = internet?.jitterMs
        val latenciaMs = internet?.latencyMs
        val bufferbloat = internet?.bufferbloatMs
        val perda = perdaDimensao(internet, peso = 0.25)

        return listOf(
            dimFaixa(
                nome = "upload",
                peso = 0.30,
                valor = upload,
                provenance = Provenance.medida,
                ok = { it >= 5.0 },
                instavel = { it >= 2.0 },
            ),
            dimFaixa(
                nome = "jitter",
                peso = 0.25,
                valor = jitterMs,
                provenance = Provenance.medida,
                ok = { it <= 20.0 },
                instavel = { it <= 40.0 },
            ),
            perda,
            dimFaixa(
                nome = "latencia",
                peso = 0.10,
                valor = latenciaMs,
                provenance = Provenance.medida,
                ok = { it <= 80.0 },
                instavel = { it <= 150.0 },
            ),
            dimFaixa(
                nome = "bufferbloat",
                peso = 0.10,
                valor = bufferbloat,
                provenance = Provenance.medida,
                ok = { it <= 30.0 },
                instavel = { it <= 100.0 },
            ),
        )
    }

    /** Trabalho: estabilidade 35% + DNS 20% + upload 15% + latencia 15% + historico 15%.
     *  "Estabilidade" aqui = perda de pacotes (sem perda real = OK), mesmo sinal usado
     *  pelo [ScoreEngine] para a dimensao "estabilidade". */
    private fun dimensoesTrabalho(input: DiagnosticInput): List<Dimensao> {
        val internet = input.internet
        val dnsMs = input.dns?.currentDnsLatencyMs
        val upload = internet?.uploadMbps
        val latenciaMs = internet?.latencyMs
        val estabilidade = perdaDimensao(internet, peso = 0.35, nomeCustom = "estabilidade")
        val historico = historicoDimensao(input, peso = 0.15)

        return listOf(
            estabilidade,
            dimFaixa(
                nome = "dns",
                peso = 0.20,
                valor = dnsMs?.toDouble(),
                provenance = Provenance.medida,
                ok = { it <= 50.0 },
                instavel = { it <= 150.0 },
            ),
            dimFaixa(
                nome = "upload",
                peso = 0.15,
                valor = upload,
                provenance = Provenance.medida,
                ok = { it >= 5.0 },
                instavel = { it >= 2.0 },
            ),
            dimFaixa(
                nome = "latencia",
                peso = 0.15,
                valor = latenciaMs,
                provenance = Provenance.medida,
                ok = { it <= 100.0 },
                instavel = { it <= 180.0 },
            ),
            historico,
        )
    }

    // ── Dimensoes compartilhadas entre perfis ───────────────────────────────────

    /**
     * Perda de pacotes: OK sem perda real. Instavel quando confianca amostral
     * insuficiente/baixa. Comprometido apenas quando >=1% E
     * [ConfiancaAmostral.SUFICIENTE] (`.agents/architecture-plan.md`, "Confiabilidade
     * estatistica do diagnostico de rede" — substitui o gate antigo
     * `provenance == Provenance.medida`, inatingivel via timeout HTTP). [provenance]
     * continua vindo de [InternetDiagnosticInput.packetLossSource] — eixo diferente,
     * so muda a fonte do STATUS (Instavel/Comprometido), nao a fonte da proveniencia
     * exibida/usada por [confiancaMedia] (`temPerdaEstimada`).
     */
    private fun perdaDimensao(
        internet: InternetDiagnosticInput?,
        peso: Double,
        nomeCustom: String = "perda",
    ): Dimensao {
        val perda =
            internet?.perdaPercentual
                ?: return Dimensao(nomeCustom, peso, null, null, Provenance.indisponivel)
        val fonte = internet.packetLossSource
        val provenance =
            when (fonte) {
                "estimated" -> Provenance.estimada
                "naoMedido", "unknown", null -> Provenance.indisponivel
                else -> Provenance.medida
            }
        if (provenance == Provenance.indisponivel) return Dimensao(nomeCustom, peso, null, null, provenance)

        val confiancaSuficiente = internet.perdaConfianca == ConfiancaAmostral.SUFICIENTE
        val status =
            when {
                confiancaSuficiente && perda >= 1.0 -> UsageProfileStatus.Comprometido
                perda > 0.0 -> UsageProfileStatus.Instavel
                else -> UsageProfileStatus.OK
            }
        val nota =
            when (status) {
                UsageProfileStatus.OK -> 100
                UsageProfileStatus.Instavel -> 60
                UsageProfileStatus.Comprometido -> 15
            }
        return Dimensao(nomeCustom, peso, nota, status, provenance)
    }

    /** Historico: usa a degradacao ja calculada por [HistoricalDegradationEngine]
     *  (percentual), nas mesmas faixas do perfil Trabalho (20%/40%) documentadas no
     *  produto — reaproveitadas tambem por Streaming, unico outro perfil com peso
     *  de historico. */
    private fun historicoDimensao(
        input: DiagnosticInput,
        peso: Double,
    ): Dimensao {
        val degradacao =
            input.historico?.degradationPercent
                ?: return Dimensao("historico", peso, null, null, Provenance.indisponivel)
        val status =
            when {
                degradacao >= 40.0 -> UsageProfileStatus.Comprometido
                degradacao >= 20.0 -> UsageProfileStatus.Instavel
                else -> UsageProfileStatus.OK
            }
        val nota =
            when (status) {
                UsageProfileStatus.OK -> 100
                UsageProfileStatus.Instavel -> 60
                UsageProfileStatus.Comprometido -> 15
            }
        return Dimensao("historico", peso, nota, status, Provenance.medida)
    }

    /** Constroi uma [Dimensao] a partir de duas faixas (ok/instavel) — o que sobra e
     *  Comprometido. [ok] e [instavel] recebem o valor bruto na unidade natural da
     *  metrica (ms, Mbps etc.) — cada call site decide a direcao da comparacao. */
    private fun dimFaixa(
        nome: String,
        peso: Double,
        valor: Double?,
        provenance: Provenance,
        ok: (Double) -> Boolean,
        instavel: (Double) -> Boolean,
    ): Dimensao {
        if (valor == null) return Dimensao(nome, peso, null, null, Provenance.indisponivel)
        val status =
            when {
                ok(valor) -> UsageProfileStatus.OK
                instavel(valor) -> UsageProfileStatus.Instavel
                else -> UsageProfileStatus.Comprometido
            }
        val nota =
            when (status) {
                UsageProfileStatus.OK -> 100
                UsageProfileStatus.Instavel -> 60
                UsageProfileStatus.Comprometido -> 15
            }
        return Dimensao(nome, peso, nota, status, provenance)
    }

    // ── Texto para o usuario (motivo / evidencias / acao) ───────────────────────

    private fun Perfil.label(): String =
        when (this) {
            Perfil.NAVEGACAO -> "navegação"
            Perfil.STREAMING -> "streaming"
            Perfil.JOGOS -> "jogos"
            Perfil.VIDEOCHAMADA -> "videochamada"
            Perfil.TRABALHO -> "trabalho remoto"
        }

    private fun evidenciasPara(
        perfil: Perfil,
        input: DiagnosticInput,
    ): List<String> {
        val internet = input.internet
        val itens = mutableListOf<String>()
        when (perfil) {
            Perfil.NAVEGACAO -> {
                input.dns?.currentDnsLatencyMs?.let { itens += "DNS ${it}ms" }
                internet?.latencyMs?.let { itens += "Latência ${it.toInt()}ms" }
            }
            Perfil.STREAMING -> {
                internet?.downloadMbps?.let { itens += "Download ${it.toInt()}Mbps (HD ≥10 · 4K ≥25)" }
                internet?.bufferbloatMs?.let { itens += "Bufferbloat ${it.toInt()}ms" }
            }
            Perfil.JOGOS -> {
                internet?.latencyMs?.let { itens += "Latência ${it.toInt()}ms" }
                internet?.jitterMs?.let { itens += "Jitter ${it.toInt()}ms" }
            }
            Perfil.VIDEOCHAMADA -> {
                internet?.uploadMbps?.let { itens += "Upload ${it.toInt()}Mbps" }
                internet?.jitterMs?.let { itens += "Jitter ${it.toInt()}ms" }
            }
            Perfil.TRABALHO -> {
                internet?.uploadMbps?.let { itens += "Upload ${it.toInt()}Mbps" }
                input.dns?.currentDnsLatencyMs?.let { itens += "DNS ${it}ms" }
            }
        }
        internet?.perdaPercentual?.let { perda ->
            if (perda > 0.0) {
                val sufixo = if (internet.packetLossSource == "estimated") " (estimada)" else ""
                itens += "Perda de pacotes $perda%$sufixo"
            }
        }
        return itens
    }

    private fun motivoPara(
        perfil: Perfil,
        status: UsageProfileStatus,
        input: DiagnosticInput,
    ): String {
        val wifiFraco =
            (input.wifi?.rssiDbm?.let { it <= -75 } == true) ||
                (input.wifi?.linkSpeedMbps?.let { it < 12 } == true)
        return when (status) {
            UsageProfileStatus.OK -> "Conexão adequada para ${perfil.label()}."
            UsageProfileStatus.Instavel ->
                if (wifiFraco) {
                    "Sinal de Wi-Fi fraco pode causar instabilidade em ${perfil.label()}."
                } else {
                    "Métricas no limite para ${perfil.label()} — pode apresentar instabilidade."
                }
            UsageProfileStatus.Comprometido -> "Conexão comprometida para ${perfil.label()} no momento."
        }
    }

    private fun acaoRecomendadaPara(
        perfil: Perfil,
        status: UsageProfileStatus,
    ): String? {
        if (status == UsageProfileStatus.OK) return null
        return when (perfil) {
            Perfil.NAVEGACAO -> "Considere trocar o DNS ou verificar a estabilidade da conexão."
            Perfil.STREAMING -> "Aproxime-se do roteador ou reduza a qualidade do stream."
            Perfil.JOGOS -> "Prefira conexão cabeada ou aproxime-se do roteador para reduzir latência/jitter."
            Perfil.VIDEOCHAMADA -> "Feche outros uploads simultâneos ou aproxime-se do roteador."
            Perfil.TRABALHO -> "Verifique estabilidade da conexão antes de reuniões ou uploads importantes."
        }
    }
}
