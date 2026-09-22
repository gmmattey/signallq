package io.signallq.app.core.diagnostico

/**
 * Motor de pontuacao (0–100) do diagnostico local — sucessor da tabela fixa de 6–7
 * valores que hoje vive em [DiagnosticReport.scoreConexao] (SIG-288).
 *
 * ## Por que existe
 * Hoje o score deriva SO do status categorico da unica [DiagnosticResult] de decisao
 * que sai do [FindingEngine] — nenhuma metrica bruta (Mbps, ms, dBm) entra no calculo.
 * Duas conexoes com o mesmo status "attention" (uma com Wi-Fi levemente fraco, outra
 * com download quase zero) recebem exatamente a mesma nota.
 *
 * ## Como funciona
 * 1. Cada dimensao (estabilidade, wifi, velocidade, dns, historico, fibra, sinal
 *    movel...) e convertida em nota 0–100 a partir do [MetricStatus] mais severo
 *    encontrado nela, com [Provenance] anexada.
 * 2. As notas sao combinadas por media ponderada, com pesos que variam por tipo de
 *    teste ([TipoConexao]).
 * 3. Dimensoes com dado [Provenance.indisponivel] SAEM do calculo — o peso delas e
 *    redistribuido proporcionalmente entre as dimensoes disponiveis (reponderacao),
 *    nunca vira nota artificial (nem 0, nem media, nem "neutro").
 * 4. Depois da media ponderada, aplica-se um teto (nunca um piso) por metrica critica
 *    isolada — mesmo padrao `maxOf` (pior caso vence) usado em
 *    [io.signallq.app.core.network.contracts.fibra.ClassificadorSaudeGpon], aqui como
 *    `minOf` porque a escala e invertida (nota alta = bom, nao ruim).
 *
 * ## Pesos por tipo de teste
 * Wi-Fi usa os pesos ja definidos (documento de produto, nao redefinir aqui):
 * estabilidade 35% / wifi-rede-local 25% / velocidade 25% / dns 10% / historico 5%.
 *
 * Fibra e movel NAO tinham tabela pronta — pesos abaixo sao decisao tecnica desta
 * issue (SIG-288), documentada por dimensao:
 *
 * **Fibra**: a saude optica da ONT (RX/TX/temperatura, ITU-T G.984) e o sinal mais
 * "de raiz" disponivel em fibra — um problema optico explica degradacao futura antes
 * mesmo dela aparecer no speedtest, e e o unico sinal do doc de auditoria classificado
 * como "pronto para recomendacao com confianca alta" (MATRIZ_DIAGNOSTICO_2026-07-03,
 * aba 7). Por isso pesa mais que em Wi-Fi (35% vs os 25% do "wifi-rede-local" em Wi-Fi).
 * Estabilidade continua a maior fatia (30%) pois bufferbloat/latencia/jitter/perda
 * ainda sao o que o usuario sente na pratica. Velocidade cai para 20% (fibra tende a
 * entregar o contratado com mais consistencia que Wi-Fi/movel quando saudavel — menos
 * o fator decisivo). DNS e historico mantem os mesmos 10%/5% de Wi-Fi (a camada DNS e
 * identica em todos os tipos de conexao).
 * fibra 35% / estabilidade 30% / velocidade 20% / dns 10% / historico 5%.
 *
 * **Movel**: RSRP/RSRQ/SINR sao os preditores mais diretos de throughput/latencia real
 * em 4G/5G (mais precisos que "qualidade %", unico sinal ja documentado no motor local
 * — ver kdoc de [MobileSignalDiagnosticEngine]) — por isso pesam mais que Wi-Fi local
 * em Wi-Fi (30%). Estabilidade cai um pouco (25%) porque em rede movel jitter/latencia
 * ja sao naturalmente mais variaveis (handover entre celulas, mobilidade do usuario) —
 * puxar demais esse peso penalizaria o score por comportamento normal da tecnologia,
 * nao por defeito de rede. Velocidade mantem 25% (mesmo peso de Wi-Fi — throughput
 * ainda importa igual). DNS e historico mantem 10%/5%.
 * sinalMovel 30% / estabilidade 25% / velocidade 25% / dns 10% / historico 5%.
 */
object ScoreEngine {
    enum class TipoConexao { WIFI, FIBRA, MOVEL, DESCONHECIDO }

    /** Uma dimensao do score com seu peso-base (antes de reponderacao) para o tipo de conexao. */
    private data class PesoDimensao(
        val nome: String,
        val pesoBase: Double,
    )

    private val pesosWifi =
        listOf(
            PesoDimensao("estabilidade", 0.35),
            PesoDimensao("wifiRedeLocal", 0.25),
            PesoDimensao("velocidade", 0.25),
            PesoDimensao("dns", 0.10),
            PesoDimensao("historico", 0.05),
        )

    private val pesosFibra =
        listOf(
            PesoDimensao("fibra", 0.35),
            PesoDimensao("estabilidade", 0.30),
            PesoDimensao("velocidade", 0.20),
            PesoDimensao("dns", 0.10),
            PesoDimensao("historico", 0.05),
        )

    private val pesosMovel =
        listOf(
            PesoDimensao("sinalMovel", 0.30),
            PesoDimensao("estabilidade", 0.25),
            PesoDimensao("velocidade", 0.25),
            PesoDimensao("dns", 0.10),
            PesoDimensao("historico", 0.05),
        )

    /** Sem tipo de conexao identificado: pesos iguais entre as dimensoes universais
     *  (estabilidade/velocidade/dns/historico) — sem wifi/fibra/movel, que nao se aplicam. */
    private val pesosDesconhecido =
        listOf(
            PesoDimensao("estabilidade", 0.40),
            PesoDimensao("velocidade", 0.40),
            PesoDimensao("dns", 0.15),
            PesoDimensao("historico", 0.05),
        )

    private fun pesosPara(tipo: TipoConexao): List<PesoDimensao> =
        when (tipo) {
            TipoConexao.WIFI -> pesosWifi
            TipoConexao.FIBRA -> pesosFibra
            TipoConexao.MOVEL -> pesosMovel
            TipoConexao.DESCONHECIDO -> pesosDesconhecido
        }

    /**
     * Calcula o score (0–100) a partir das evidencias ja convertidas em nota+proveniencia.
     * [evidencias] deve conter no maximo uma [EvidenceScore] por nome de dimensao usada
     * em [pesosPara] — nomes extras sao ignorados, dimensoes ausentes contam como
     * [Provenance.indisponivel] para fins de reponderacao.
     */
    fun calcular(
        tipo: TipoConexao,
        evidencias: List<EvidenceScore>,
    ): ScoreResult {
        val porNome = evidencias.associateBy { it.dimensao }
        val pesos = pesosPara(tipo)

        val disponiveis =
            pesos.mapNotNull { p ->
                val ev = porNome[p.nome]
                if (ev != null && ev.provenance != Provenance.indisponivel && ev.nota != null) {
                    p to ev
                } else {
                    null
                }
            }
        val dadosAusentes = pesos.map { it.nome } - disponiveis.map { it.first.nome }.toSet()

        if (disponiveis.isEmpty()) {
            return ScoreResult(score = null, dimensoesUsadas = emptyList(), dadosAusentes = dadosAusentes)
        }

        // Reponderacao: peso das dimensoes indisponiveis e redistribuido
        // proporcionalmente entre as disponiveis — nunca vira nota artificial.
        val somaPesosDisponiveis = disponiveis.sumOf { it.first.pesoBase }
        val mediaPonderada =
            disponiveis.sumOf { (peso, ev) ->
                val pesoReponderado = peso.pesoBase / somaPesosDisponiveis
                (ev.nota ?: 0) * pesoReponderado
            }

        val scoreComTeto = aplicarTetos(mediaPonderada.toInt().coerceIn(0, 100), porNome)

        return ScoreResult(
            score = scoreComTeto,
            dimensoesUsadas = disponiveis.map { it.second },
            dadosAusentes = dadosAusentes,
        )
    }

    /**
     * Tetos por metrica critica isolada — mesmo padrao "pior caso vence" do
     * [io.signallq.app.core.network.contracts.fibra.ClassificadorSaudeGpon.classificar],
     * aplicado como limite superior porque aqui a escala e nota (alto = bom):
     * - perda de pacotes critica (>=3%) COM confianca amostral suficiente (2+ timeouts,
     *   consecutivos ou recorrentes confirmados — nao 1 timeout isolado) -> teto 45
     * - bufferbloat critico (>100ms) -> teto 60
     * - fibra RX fora da faixa critica (< -27 dBm) -> teto 35
     * - RSSI muito fraco (critico) + download baixo (nota de velocidade ruim/critico) -> teto 65
     * Tetos NAO se somam com prioridade — aplica-se o MENOR teto entre os que bateram
     * (o mais restritivo vence), depois `minOf` com a media ponderada calculada.
     */
    private fun aplicarTetos(
        scoreBase: Int,
        porNome: Map<String, EvidenceScore>,
    ): Int {
        val tetos = mutableListOf<Int>()

        // Camillo/Luiz (.agents/architecture-plan.md, "Confiabilidade estatística do
        // diagnóstico de rede"): o teto de perda crítica usava provenance == medida,
        // que perda via timeout HTTP nunca atinge de verdade (nunca é captura real de
        // pacote) — o gate correto é confiança amostral: 2+ timeouts, timeouts
        // consecutivos ou perda recorrente confirmada SIM devem poder escalar o score,
        // 1 timeout isolado (mesmo confirmado como isolado) não deve sozinho.
        val perda = porNome["perdaPacotesStatus"]
        if (perda?.confiancaAmostral == ConfiancaAmostral.SUFICIENTE && perda.nota != null && perda.nota <= NOTA_CRITICO) {
            tetos += TETO_PERDA_PACOTES_CRITICA
        }

        val bufferbloat = porNome["bufferbloatStatus"]
        if (bufferbloat?.nota != null && bufferbloat.nota <= NOTA_CRITICO) {
            tetos += TETO_BUFFERBLOAT_CRITICO
        }

        val fibraRx = porNome["fibraRxStatus"]
        if (fibraRx?.nota != null && fibraRx.nota <= NOTA_CRITICO) {
            tetos += TETO_FIBRA_RX_CRITICA
        }

        val rssi = porNome["rssiStatus"]
        val downloadBaixo = porNome["velocidade"]
        if (rssi?.nota != null &&
            rssi.nota <= NOTA_CRITICO &&
            downloadBaixo?.nota != null &&
            downloadBaixo.nota <= NOTA_RUIM
        ) {
            tetos += TETO_RSSI_FRACO_DOWNLOAD_BAIXO
        }

        val tetoMaisRestritivo = tetos.minOrNull() ?: return scoreBase
        return minOf(scoreBase, tetoMaisRestritivo)
    }

    // Notas de corte usadas para reconhecer status "critico"/"ruim" a partir da nota
    // ja convertida (ver [notaParaStatus]) — evita reimportar MetricStatus aqui dentro.
    private const val NOTA_CRITICO = 25
    private const val NOTA_RUIM = 45

    const val TETO_PERDA_PACOTES_CRITICA = 45
    const val TETO_BUFFERBLOAT_CRITICO = 60
    const val TETO_FIBRA_RX_CRITICA = 35
    const val TETO_RSSI_FRACO_DOWNLOAD_BAIXO = 65

    /** Conversao canonica de [MetricStatus] para nota 0–100 — usada por todo call
     *  site que monta [EvidenceScore] a partir de um status ja classificado pelo
     *  [MetricClassifier] ou pelos engines existentes. */
    fun notaParaStatus(status: MetricStatus): Int =
        when (status) {
            MetricStatus.excelente -> 100
            MetricStatus.bom -> 80
            MetricStatus.regular -> 60
            MetricStatus.ruim -> 40
            MetricStatus.critico -> 15
            MetricStatus.inconclusivo -> 50
        }

    /** Conversao canonica de [DiagnosticStatus] (vocabulario dos engines legados) para
     *  nota 0–100 — usada quando a dimensao so tem [DiagnosticResult] disponivel (ex.:
     *  estabilidade agregando varios resultados de categorias diferentes). */
    fun notaParaDiagnosticStatus(status: DiagnosticStatus): Int =
        when (status) {
            DiagnosticStatus.ok -> 100
            DiagnosticStatus.info -> 80
            DiagnosticStatus.attention -> 55
            DiagnosticStatus.critical -> 20
            DiagnosticStatus.inconclusive -> 50
        }
}

/**
 * Resultado do [ScoreEngine.calcular]. [score] e nulo quando NENHUMA dimensao tinha
 * dado disponivel (todas [Provenance.indisponivel]) — nesse caso o chamador deve
 * decidir o fallback (ex.: [DiagnosticReport.scoreConexao] cai para a tabela legada).
 */
data class ScoreResult(
    val score: Int?,
    val dimensoesUsadas: List<EvidenceScore>,
    val dadosAusentes: List<String>,
)
