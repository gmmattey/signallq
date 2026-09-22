package io.signallq.app.core.diagnostico

import io.signallq.app.core.diagnostico.topology.model.NatStatus

/**
 * Classificador de prontidao para jogos online (aba 10 do documento de produto) —
 * SIG-290. Consome o mesmo [DiagnosticInput] do [UsageProfileClassifier] (perfil
 * "Jogos"), mas com faixas MUITO mais estritas e especificas por categoria de jogo —
 * o perfil "Jogos" da aba 9 e generico (qualquer jogo online), a aba 10 e voltada a
 * cenarios competitivos onde poucos ms fazem diferenca.
 *
 * ## Escopo desta issue
 * Apenas as 3 categorias iniciais do documento: [Categoria.FPS_COMPETITIVO],
 * [Categoria.CLOUD_GAMING] e [Categoria.MOBILE_COMPETITIVO]. As outras 7 categorias
 * (MOBA, luta, corrida, RPG online, battle royale nao-FPS, etc.) ficam para issues
 * futuras — NAO adicionar aqui sem nova issue.
 *
 * ## Vocabulario — PROPRIO desta aba, nao reutilizar em outro contexto
 * [ReadinessStatus.Bom] / [ReadinessStatus.Atencao] / [ReadinessStatus.Ruim] — 3
 * niveis (nao 6 como [MetricStatus], nem os 3 de [UsageProfileClassifier.UsageProfileStatus]
 * que usam nomes diferentes: OK/Instavel/Comprometido). Pior faixa entre as metricas
 * da categoria vence, mesmo principio do [UsageProfileClassifier].
 *
 * ## NAT como evidencia adicional (regra transversal do documento)
 * NAT restrito/duplo NAT/CGNAT ([NatStatus.DOUBLE_NAT_OR_CGNAT]/[NatStatus.CGNAT])
 * piora matchmaking e chat de voz em jogos com P2P — mas NUNCA rebaixa o status
 * sozinho (a conexao pode estar tecnicamente perfeita e o NAT so afetar quem
 * consegue "hostear"/parear). Entra como evidencia adicional no motivo, igual a
 * regra ja aplicada em [DiagnosticRunner.avaliarNat] (status "info", nao eleva
 * veredito). Fonte do dado: [NatClassifier] (topology/correlation), ja plugado em
 * [DiagnosticInput.natStatus].
 */
object GameReadinessClassifier {
    enum class Categoria { FPS_COMPETITIVO, CLOUD_GAMING, MOBILE_COMPETITIVO }

    enum class ReadinessStatus { Bom, Atencao, Ruim }

    data class GameReadinessResult(
        val categoria: Categoria,
        val status: ReadinessStatus?,
        val motivo: String,
        val evidencias: List<String>,
        val recomendacao: String?,
        val dadosAusentes: List<String>,
    )

    fun classificarTodos(input: DiagnosticInput): List<GameReadinessResult> =
        Categoria.entries.map { classificar(it, input) }

    fun classificar(
        categoria: Categoria,
        input: DiagnosticInput,
    ): GameReadinessResult {
        val avaliador =
            when (categoria) {
                Categoria.FPS_COMPETITIVO -> ::avaliarFpsCompetitivo
                Categoria.CLOUD_GAMING -> ::avaliarCloudGaming
                Categoria.MOBILE_COMPETITIVO -> ::avaliarMobileCompetitivo
            }
        return avaliador(input)
    }

    // ── FPS competitivo (Call of Duty/Warzone, Valorant, CS2, Apex, Rainbow Six) ─
    // Bom: latencia<=50 jitter<=15 perda real 0% bufferbloat<=30
    // Atencao: latencia 51-100 jitter 16-30 perda estimada bufferbloat 31-100
    // Ruim: latencia>100 jitter>30 perda real>=1% bufferbloat>100
    private fun avaliarFpsCompetitivo(input: DiagnosticInput): GameReadinessResult {
        val internet = input.internet
        val latencia = internet?.latencyMs
        val jitter = internet?.jitterMs
        val bufferbloat = internet?.bufferbloatMs
        val perda = perdaFaixa(internet)

        val ausentes = mutableListOf<String>()
        val faixas = mutableListOf<ReadinessStatus>()

        if (latencia == null) {
            ausentes += "latencia"
        } else {
            faixas +=
                when {
                    latencia <= 50.0 -> ReadinessStatus.Bom
                    latencia <= 100.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (jitter == null) {
            ausentes += "jitter"
        } else {
            faixas +=
                when {
                    jitter <= 15.0 -> ReadinessStatus.Bom
                    jitter <= 30.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (bufferbloat == null) {
            ausentes += "bufferbloat"
        } else {
            faixas +=
                when {
                    bufferbloat <= 30.0 -> ReadinessStatus.Bom
                    bufferbloat <= 100.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (perda == null) ausentes += "perda" else faixas += perda

        if (faixas.isEmpty()) return semDados(Categoria.FPS_COMPETITIVO, ausentes)

        val statusBase = piorFaixa(faixas)
        val status = aplicarPenalidadeWifi(statusBase, input)

        val evidencias = mutableListOf<String>()
        latencia?.let { evidencias += "Latência ${it.toInt()}ms" }
        jitter?.let { evidencias += "Jitter ${it.toInt()}ms" }
        bufferbloat?.let { evidencias += "Bufferbloat ${it.toInt()}ms" }
        internet?.perdaPercentual?.let { p -> if (p > 0.0) evidencias += "Perda $p%${sufixoEstimada(internet)}" }

        return GameReadinessResult(
            categoria = Categoria.FPS_COMPETITIVO,
            status = status,
            motivo = motivoFps(status, input),
            evidencias = evidencias + evidenciaNat(input),
            recomendacao = acaoFps(status),
            dadosAusentes = ausentes,
        )
    }

    private fun motivoFps(
        status: ReadinessStatus,
        input: DiagnosticInput,
    ): String =
        when (status) {
            ReadinessStatus.Bom -> "Conexão pronta para FPS competitivo (COD/Warzone, Valorant, CS2, Apex, Rainbow Six)."
            ReadinessStatus.Atencao -> "Métricas no limite para FPS competitivo — pode haver atraso perceptível na mira/registro de tiros."
            ReadinessStatus.Ruim -> "Conexão não recomendada para FPS competitivo no momento — latência/jitter/perda vão prejudicar a precisão."
        }

    private fun acaoFps(status: ReadinessStatus): String? {
        if (status == ReadinessStatus.Bom) return null
        return "Priorize a rede 5GHz perto do roteador, reduza downloads em segundo plano e evite Wi-Fi fraco."
    }

    // ── Cloud gaming (Xbox Cloud Gaming, GeForce NOW, PS Remote Play, Steam Link) ─
    // Bom: download>=50 latencia<=50 jitter<=15 perda 0% bufferbloat<=30 Wi-Fi 5/6GHz forte
    // Atencao: download 25-50 latencia 51-80 jitter 16-30 bufferbloat 31-80
    // Ruim: download<25 latencia>80 jitter>30 perda real>=1% bufferbloat>80
    private fun avaliarCloudGaming(input: DiagnosticInput): GameReadinessResult {
        val internet = input.internet
        val download = internet?.downloadMbps
        val latencia = internet?.latencyMs
        val jitter = internet?.jitterMs
        val bufferbloat = internet?.bufferbloatMs
        val perda = perdaFaixa(internet)

        val ausentes = mutableListOf<String>()
        val faixas = mutableListOf<ReadinessStatus>()

        if (download == null) {
            ausentes += "download"
        } else {
            faixas +=
                when {
                    download >= 50.0 -> ReadinessStatus.Bom
                    download >= 25.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (latencia == null) {
            ausentes += "latencia"
        } else {
            faixas +=
                when {
                    latencia <= 50.0 -> ReadinessStatus.Bom
                    latencia <= 80.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (jitter == null) {
            ausentes += "jitter"
        } else {
            faixas +=
                when {
                    jitter <= 15.0 -> ReadinessStatus.Bom
                    jitter <= 30.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (bufferbloat == null) {
            ausentes += "bufferbloat"
        } else {
            faixas +=
                when {
                    bufferbloat <= 30.0 -> ReadinessStatus.Bom
                    bufferbloat <= 80.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (perda == null) ausentes += "perda" else faixas += perda

        if (faixas.isEmpty()) return semDados(Categoria.CLOUD_GAMING, ausentes)

        val statusBase = piorFaixa(faixas)
        // Cloud gaming exige Wi-Fi 5/6GHz forte explicitamente — 2.4GHz rebaixa
        // direto (nao so quando fraco), pois o video streaming em tempo real nao
        // tolera a instabilidade tipica da banda 2.4GHz mesmo com RSSI bom.
        val status = aplicarPenalidadeWifiCloudGaming(statusBase, input)

        val evidencias = mutableListOf<String>()
        download?.let { evidencias += "Download ${it.toInt()}Mbps" }
        latencia?.let { evidencias += "Latência ${it.toInt()}ms" }
        jitter?.let { evidencias += "Jitter ${it.toInt()}ms" }
        bufferbloat?.let { evidencias += "Bufferbloat ${it.toInt()}ms" }
        internet?.perdaPercentual?.let { p -> if (p > 0.0) evidencias += "Perda $p%${sufixoEstimada(internet)}" }

        return GameReadinessResult(
            categoria = Categoria.CLOUD_GAMING,
            status = status,
            motivo = motivoCloudGaming(status),
            evidencias = evidencias + evidenciaNat(input),
            recomendacao = acaoCloudGaming(status),
            dadosAusentes = ausentes,
        )
    }

    private fun motivoCloudGaming(status: ReadinessStatus): String =
        when (status) {
            ReadinessStatus.Bom -> "Conexão pronta para cloud gaming (Xbox Cloud Gaming, GeForce NOW, PS Remote Play, Steam Link)."
            ReadinessStatus.Atencao -> "Métricas no limite para cloud gaming — pode haver perda de qualidade de imagem ou engasgos."
            ReadinessStatus.Ruim -> "Conexão não recomendada para cloud gaming no momento — o stream de vídeo em tempo real vai sofrer."
        }

    private fun acaoCloudGaming(status: ReadinessStatus): String? {
        if (status == ReadinessStatus.Bom) return null
        return "Cloud gaming exige Wi-Fi muito estável: use a rede 5GHz/6GHz perto do roteador e evite a faixa 2.4GHz."
    }

    // ── Mobile competitivo (COD Mobile, Free Fire, PUBG Mobile, Wild Rift) ──────
    // Bom: RSSI>=-60 latencia<=60 jitter<=20 perda 0% 5GHz perto
    // Atencao: RSSI -61 a -72 latencia 61-120 jitter 21-35
    // Ruim: RSSI<=-72 latencia>120 jitter>35 perda real>=1%
    private fun avaliarMobileCompetitivo(input: DiagnosticInput): GameReadinessResult {
        val internet = input.internet
        val rssi = input.wifi?.rssiDbm
        val latencia = internet?.latencyMs
        val jitter = internet?.jitterMs
        val perda = perdaFaixa(internet)

        val ausentes = mutableListOf<String>()
        val faixas = mutableListOf<ReadinessStatus>()

        if (rssi == null) {
            ausentes += "rssi"
        } else {
            faixas +=
                when {
                    rssi >= -60 -> ReadinessStatus.Bom
                    rssi >= -72 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (latencia == null) {
            ausentes += "latencia"
        } else {
            faixas +=
                when {
                    latencia <= 60.0 -> ReadinessStatus.Bom
                    latencia <= 120.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (jitter == null) {
            ausentes += "jitter"
        } else {
            faixas +=
                when {
                    jitter <= 20.0 -> ReadinessStatus.Bom
                    jitter <= 35.0 -> ReadinessStatus.Atencao
                    else -> ReadinessStatus.Ruim
                }
        }
        if (perda == null) ausentes += "perda" else faixas += perda

        if (faixas.isEmpty()) return semDados(Categoria.MOBILE_COMPETITIVO, ausentes)

        val status = piorFaixa(faixas)

        val evidencias = mutableListOf<String>()
        rssi?.let { evidencias += "RSSI ${it}dBm" }
        latencia?.let { evidencias += "Latência ${it.toInt()}ms" }
        jitter?.let { evidencias += "Jitter ${it.toInt()}ms" }
        internet?.perdaPercentual?.let { p -> if (p > 0.0) evidencias += "Perda $p%${sufixoEstimada(internet)}" }

        return GameReadinessResult(
            categoria = Categoria.MOBILE_COMPETITIVO,
            status = status,
            motivo = motivoMobileCompetitivo(status, input),
            evidencias = evidencias + evidenciaNat(input),
            recomendacao = acaoMobileCompetitivo(status),
            dadosAusentes = ausentes,
        )
    }

    private fun motivoMobileCompetitivo(
        status: ReadinessStatus,
        input: DiagnosticInput,
    ): String {
        val alternanciaRede = input.connectionType == ConnectionType.mobile
        return when (status) {
            ReadinessStatus.Bom -> "Conexão pronta para mobile competitivo (COD Mobile, Free Fire, PUBG Mobile, Wild Rift)."
            ReadinessStatus.Atencao ->
                if (alternanciaRede) {
                    "Métricas no limite para mobile competitivo — alternância entre Wi-Fi e rede móvel pode causar picos de latência."
                } else {
                    "Métricas no limite para mobile competitivo — pode haver instabilidade em momentos decisivos da partida."
                }
            ReadinessStatus.Ruim -> "Conexão não recomendada para mobile competitivo no momento."
        }
    }

    private fun acaoMobileCompetitivo(status: ReadinessStatus): String? {
        if (status == ReadinessStatus.Bom) return null
        return "Jogue perto do roteador em 5GHz e evite a alternância automática entre Wi-Fi e rede móvel durante a partida."
    }

    // ── Compartilhado ────────────────────────────────────────────────────────

    private fun semDados(
        categoria: Categoria,
        ausentes: List<String>,
    ) =
        GameReadinessResult(
            categoria = categoria,
            status = null,
            motivo = "Sem dados suficientes para avaliar prontidão para ${categoria.labelSemDados()}.",
            evidencias = emptyList(),
            recomendacao = null,
            dadosAusentes = ausentes,
        )

    private fun Categoria.labelSemDados(): String =
        when (this) {
            Categoria.FPS_COMPETITIVO -> "FPS competitivo"
            Categoria.CLOUD_GAMING -> "cloud gaming"
            Categoria.MOBILE_COMPETITIVO -> "mobile competitivo"
        }

    /** Pior faixa entre as metricas disponiveis vence — mesmo principio do [UsageProfileClassifier]. */
    private fun piorFaixa(faixas: List<ReadinessStatus>): ReadinessStatus =
        faixas.maxByOrNull { it.severidade() } ?: ReadinessStatus.Bom

    private fun ReadinessStatus.severidade(): Int =
        when (this) {
            ReadinessStatus.Bom -> 0
            ReadinessStatus.Atencao -> 1
            ReadinessStatus.Ruim -> 2
        }

    /** Perda de pacotes: Bom sem perda real. Atencao quando qualquer perda parcial
     *  sem confianca amostral suficiente. Ruim apenas quando >=1% E a amostra tem
     *  [ConfiancaAmostral.SUFICIENTE] (`.agents/architecture-plan.md`, "Confiabilidade
     *  estatistica do diagnostico de rede" — substitui o gate antigo
     *  `provenance == Provenance.medida`, inatingivel via timeout HTTP: perda so
     *  estimada por timeout nunca vira captura real de pacote, mas 2+ timeouts ou
     *  timeouts consecutivos/confirmados SIM devem poder escalar). Retorna null
     *  quando o dado nao esta disponivel. */
    private fun perdaFaixa(internet: InternetDiagnosticInput?): ReadinessStatus? {
        val perda = internet?.perdaPercentual ?: return null
        val fonte = internet.packetLossSource
        if (fonte == "naoMedido" || fonte == "unknown" || fonte == null) return null
        val confiancaSuficiente = internet.perdaConfianca == ConfiancaAmostral.SUFICIENTE
        return when {
            confiancaSuficiente && perda >= 1.0 -> ReadinessStatus.Ruim
            perda > 0.0 -> ReadinessStatus.Atencao
            else -> ReadinessStatus.Bom
        }
    }

    private fun sufixoEstimada(internet: InternetDiagnosticInput): String =
        if (internet.packetLossSource == "estimated") " (estimada)" else ""

    /** Wi-Fi fraco rebaixa 1 nivel — sinais que rebaixam por categoria (aba 10):
     *  RSSI<=-70dBm, linkSpeed baixo, 2.4GHz congestionado/canal sobreposto.
     *  Mesmo principio de [UsageProfileClassifier.aplicarPenalidadesContextuais]. */
    private fun aplicarPenalidadeWifi(
        statusBase: ReadinessStatus,
        input: DiagnosticInput,
    ): ReadinessStatus {
        val wifi = input.wifi ?: return statusBase
        val rssi = wifi.rssiDbm
        val linkSpeed = wifi.linkSpeedMbps
        val rssiFraco = rssi != null && rssi <= -70
        val linkBaixo = linkSpeed != null && linkSpeed < 54
        val wifiFraco = rssiFraco || linkBaixo
        if (!wifiFraco) return statusBase
        return statusBase.rebaixarUmNivel()
    }

    /** Cloud gaming exige Wi-Fi 5/6GHz forte (aba 10) — 2.4GHz sempre rebaixa 1
     *  nivel, mesmo com RSSI bom, alem da penalidade generica de sinal fraco. */
    private fun aplicarPenalidadeWifiCloudGaming(
        statusBase: ReadinessStatus,
        input: DiagnosticInput,
    ): ReadinessStatus {
        val wifi = input.wifi ?: return statusBase
        val rssi = wifi.rssiDbm
        val linkSpeed = wifi.linkSpeedMbps
        val em24Ghz = wifi.banda() == BandaWifi.ghz24
        val rssiFraco = rssi != null && rssi <= -67
        val linkBaixo = linkSpeed != null && linkSpeed < 54
        if (!em24Ghz && !rssiFraco && !linkBaixo) return statusBase
        return statusBase.rebaixarUmNivel()
    }

    private fun ReadinessStatus.rebaixarUmNivel(): ReadinessStatus =
        when (this) {
            ReadinessStatus.Bom -> ReadinessStatus.Atencao
            ReadinessStatus.Atencao -> ReadinessStatus.Ruim
            ReadinessStatus.Ruim -> ReadinessStatus.Ruim
        }

    /** NAT restrito/duplo NAT/CGNAT piora matchmaking e chat — evidencia adicional,
     *  NUNCA rebaixa o status sozinho (regra transversal de NAT do documento). */
    private fun evidenciaNat(input: DiagnosticInput): List<String> {
        val nat = input.natStatus ?: return emptyList()
        return when (nat) {
            NatStatus.CGNAT -> listOf("NAT: CGNAT detectado — pode piorar matchmaking e chat por voz")
            NatStatus.DOUBLE_NAT_OR_CGNAT -> listOf("NAT: NAT duplo detectado — pode piorar matchmaking e chat por voz")
            NatStatus.DIRECT_PUBLIC, NatStatus.UNKNOWN -> emptyList()
        }
    }
}
