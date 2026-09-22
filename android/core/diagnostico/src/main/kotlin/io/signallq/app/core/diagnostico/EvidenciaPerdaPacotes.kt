package io.signallq.app.core.diagnostico

/**
 * Confianca amostral da medicao de perda de pacotes — eixo ORTOGONAL a [Provenance].
 *
 * Nao substitui [Provenance] nem o enum generico compartilhado por outras dimensoes
 * (RSSI, fibra, velocidade) — perda de pacotes via timeout HTTP nunca alcanca
 * [Provenance.medida] de verdade (nunca e captura real de pacote, ver kdoc de
 * [Provenance.estimada]). O problema que este eixo resolve e outro: com a resolucao
 * amostral de um ping/speedtest (dezenas de amostras, nao milhares), 1 timeout isolado
 * ja ultrapassa os cortes de negocio (0,5%/1%/2%/3%) sem que isso signifique perda
 * "critica" de verdade — e o percentual medido nao pode ser tratado como confiavel
 * so porque e um numero real.
 *
 * Fonte de verdade da decisao: `AnalisadorAmostragemPing.avaliarConfianca()`
 * (`:feature:speedtest`) — este arquivo so declara o vocabulario, para ser consumido
 * pelos classificadores deste modulo ([GameReadinessClassifier], [UsageProfileClassifier],
 * [ScoreEngine], [ScoreEvidenceBuilder]) sem que `:core:diagnostico` precise depender
 * de `:feature:speedtest` (a dependencia hoje e a inversa).
 */
enum class ConfiancaAmostral {
    /**
     * Amostra suficiente para tratar o percentual como confiavel: 0 timeouts, 2+
     * timeouts (consecutivos ou nao), ou 100% de timeout (falha total nao e "evento
     * isolado" — e o oposto disso).
     */
    SUFICIENTE,

    /**
     * Percentual real, mas calculado sobre exatamente 1 timeout isolado — mesmo que a
     * janela de confirmacao tenha rodado e nao tenha encontrado mais nenhum. NUNCA vira
     * 0 — o valor medido e reportado como esta, so a confianca declarada muda. Nao deve
     * sozinha elevar classificacao a Ruim/Comprometido nem acionar teto critico de score.
     */
    INSUFICIENTE,
}

/**
 * Evidencia rica de perda de pacotes: fato medido ([perdaPercentual], nunca forcado a
 * zero) + a inferencia deterministica de confianca amostral ([confianca]) sobre esse
 * fato. Produzida por `AnalisadorAmostragemPing.avaliarConfianca()` a partir do
 * resultado bruto de amostragem (sem duplicar mediana/jitter/p95, que ja vivem em
 * `ResultadoAmostragemPing`).
 */
data class EvidenciaPerdaPacotes(
    val perdaPercentual: Double,
    val timeoutsTotais: Int,
    val timeoutsConsecutivosMax: Int,
    val amostrasEfetivas: Int,
    val confirmacaoExecutada: Boolean,
    val confianca: ConfiancaAmostral,
)
