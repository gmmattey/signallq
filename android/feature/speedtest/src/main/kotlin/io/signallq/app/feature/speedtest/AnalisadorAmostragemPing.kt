package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import io.signallq.app.core.diagnostico.EvidenciaPerdaPacotes
import kotlin.math.abs

/**
 * Resultado do algoritmo puro de amostragem de ping: mediana de latência, jitter,
 * percentual de perda de pacote e contagem de amostras/timeouts.
 *
 * [maxMs] e [p95Ms] são calculados sobre TODAS as amostras válidas, antes do filtro de
 * outlier que produz [latenciaMs] — GH#1211 item 3: o filtro de outlier é adequado para a
 * latência-base, mas não pode fazer os picos desaparecerem da análise de estabilidade.
 * [picos] é quantas amostras válidas foram descartadas pelo filtro (`> 3x` a mediana bruta).
 * [timeoutsConsecutivosMax] é a maior sequência de timeouts consecutivos na lista bruta
 * pós warm-up (0 se não houve nenhum) — usado por [AnalisadorAmostragemPing.avaliarConfianca]
 * para distinguir timeout isolado de perda recorrente.
 */
data class ResultadoAmostragemPing(
    val latenciaMs: Double,
    val jitterMs: Double,
    val perdaPercentual: Double,
    val totalAmostras: Int,
    val amostrasValidas: Int,
    val timeouts: Int,
    val maxMs: Double = 0.0,
    val p95Ms: Double = 0.0,
    val picos: Int = 0,
    val timeoutsConsecutivosMax: Int = 0,
)

/**
 * Algoritmo puro de amostragem de ping, extraído em GH#1019 por estar duplicado
 * literalmente entre [ExecutorSpeedtestCloudflare] (Tela 1 · Velocidade) e [PingExecutor]
 * (tela Ping + refinamento opcional de latência do Modo gamer,
 * `io.signallq.app.ui.screen.ModoGamerConfigConteudo` — reaproveitado do antigo
 * `JogoConexaoEngine`/GH#935 pela fusão #1487).
 *
 * Regras (preservadas exatamente como estavam nos dois consumidores originais):
 * - a 1ª amostra é sempre descartada (aquecimento de conexão);
 * - a latência final é a mediana das amostras válidas restantes;
 * - amostras acima de 3x a mediana são tratadas como outlier e descartadas — a menos
 *   que isso zere a lista, caso em que o filtro é ignorado;
 * - jitter é a média das deltas absolutas entre amostras consecutivas (após o filtro);
 * - perda é o percentual de timeouts (amostra nula) sobre o total pós-descarte da 1ª,
 *   com precisão total de [Double] (sem arredondamento — ver decisão de consolidação
 *   na issue #1019: o `PingExecutor` antigo arredondava para `Int` antes de devolver,
 *   divergência sem efeito observável dado o número de amostras usado hoje, mas
 *   removida para não haver duas fontes de verdade com precisão diferente).
 */
object AnalisadorAmostragemPing {
    fun analisar(amostrasBrutas: List<Double?>): ResultadoAmostragemPing {
        val semPrimeiro = amostrasBrutas.drop(1)
        val timeouts = semPrimeiro.count { it == null }
        val validos = semPrimeiro.filterNotNull()
        val mediana = mediana(validos)
        val filtrados = if (mediana > 0.0) validos.filter { it <= mediana * 3.0 } else validos
        val usados = if (filtrados.isNotEmpty()) filtrados else validos

        return ResultadoAmostragemPing(
            latenciaMs = mediana(usados),
            jitterMs = jitter(usados),
            perdaPercentual =
                if (semPrimeiro.isNotEmpty()) {
                    (timeouts.toDouble() / semPrimeiro.size.toDouble()) * 100.0
                } else {
                    0.0
                },
            totalAmostras = semPrimeiro.size,
            amostrasValidas = usados.size,
            timeouts = timeouts,
            maxMs = validos.maxOrNull() ?: 0.0,
            p95Ms = percentil95(validos),
            picos = validos.size - usados.size,
            timeoutsConsecutivosMax = timeoutsConsecutivosMax(semPrimeiro),
        )
    }

    /**
     * Confianca amostral da perda de pacotes deste [resultado] — compõe em cima do que
     * [analisar] já calculou, sem duplicar mediana/jitter/p95 (Camillo, arquitetura
     * "Confiabilidade estatística do diagnóstico de rede", seção 6/7 de
     * `.agents/architecture-plan.md`).
     *
     * Regra (decidida com o Luiz — não reabrir):
     * - 0 timeouts → [ConfiancaAmostral.SUFICIENTE];
     * - 100% de timeout (nenhuma amostra válida) → [ConfiancaAmostral.SUFICIENTE] — falha
     *   total é o oposto de um evento isolado, não exige confirmação para ser confiável;
     * - 2+ timeouts (consecutivos ou não) → [ConfiancaAmostral.SUFICIENTE];
     * - exatamente 1 timeout isolado → [ConfiancaAmostral.INSUFICIENTE], MESMO que
     *   [confirmacaoExecutada] seja `true` e a confirmação não tenha encontrado mais
     *   nenhum timeout (evento confirmado como isolado continua isolado, não vira
     *   crítico sozinho).
     *
     * [perdaPercentual] nunca é forçado a 0 — é sempre o valor real medido por
     * [analisar]; só a interpretação ([ConfiancaAmostral]) muda.
     */
    fun avaliarConfianca(
        resultado: ResultadoAmostragemPing,
        confirmacaoExecutada: Boolean,
    ): EvidenciaPerdaPacotes {
        val timeouts = resultado.timeouts
        val perdaTotal = resultado.totalAmostras > 0 && timeouts == resultado.totalAmostras

        val confianca =
            when {
                timeouts == 0 -> ConfiancaAmostral.SUFICIENTE
                perdaTotal -> ConfiancaAmostral.SUFICIENTE
                timeouts >= 2 -> ConfiancaAmostral.SUFICIENTE
                else -> ConfiancaAmostral.INSUFICIENTE
            }

        return EvidenciaPerdaPacotes(
            perdaPercentual = resultado.perdaPercentual,
            timeoutsTotais = timeouts,
            timeoutsConsecutivosMax = resultado.timeoutsConsecutivosMax,
            amostrasEfetivas = resultado.totalAmostras,
            confirmacaoExecutada = confirmacaoExecutada,
            confianca = confianca,
        )
    }

    // Maior sequência de timeouts (amostra nula) consecutivos na lista bruta pós
    // warm-up — usado por [avaliarConfianca] para diferenciar timeout isolado de
    // perda recorrente/consecutiva, ainda que ambos possam somar o mesmo `timeouts`.
    private fun timeoutsConsecutivosMax(amostras: List<Double?>): Int {
        var atual = 0
        var maximo = 0
        for (amostra in amostras) {
            if (amostra == null) {
                atual += 1
                maximo = maxOf(maximo, atual)
            } else {
                atual = 0
            }
        }
        return maximo
    }

    private fun mediana(valores: List<Double>): Double {
        if (valores.isEmpty()) return 0.0
        val ordenadas = valores.sorted()
        val m = ordenadas.size / 2
        return if (ordenadas.size % 2 == 0) {
            (ordenadas[m - 1] + ordenadas[m]) / 2.0
        } else {
            ordenadas[m]
        }
    }

    private fun jitter(valores: List<Double>): Double {
        if (valores.size < 2) return 0.0
        val deltas = valores.zipWithNext { a, b -> abs(b - a) }
        return if (deltas.isEmpty()) 0.0 else deltas.average()
    }

    // Método do posto mais próximo (nearest-rank) — suficiente para o volume de amostras
    // de um ping/speedtest (dezenas, não milhares), sem depender de biblioteca estatística.
    private fun percentil95(valores: List<Double>): Double {
        if (valores.isEmpty()) return 0.0
        val ordenadas = valores.sorted()
        val posto =
            kotlin.math
                .ceil(0.95 * ordenadas.size)
                .toInt()
                .coerceIn(1, ordenadas.size)
        return ordenadas[posto - 1]
    }
}
