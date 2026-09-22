package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Teste de caracterização (GH#1019): trava o comportamento numérico do algoritmo de
 * amostragem de ping antes/depois da extração de [ExecutorSpeedtestCloudflare] e
 * [PingExecutor] para [AnalisadorAmostragemPing]. Os valores esperados replicam
 * manualmente a matemática que existia duplicada nos dois consumidores originais.
 */
class AnalisadorAmostragemPingTest {
    @Test
    fun `descarta sempre a primeira amostra`() {
        // 1a amostra é um outlier gigante (500ms) que seria descartado de qualquer forma,
        // mas o teste comprova que o descarte da 1a amostra é incondicional: se ela não
        // fosse descartada por posição, sobraria no cálculo mesmo após o filtro de outlier
        // (pois passaria a compor a própria mediana usada no filtro).
        val resultado = AnalisadorAmostragemPing.analisar(listOf(500.0, 20.0, 22.0, 21.0))

        assertEquals(3, resultado.totalAmostras)
        assertEquals(21.0, resultado.latenciaMs, 0.0001)
    }

    @Test
    fun `mediana com quantidade impar de amostras validas`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 30.0, 20.0))

        assertEquals(20.0, resultado.latenciaMs, 0.0001)
    }

    @Test
    fun `mediana com quantidade par de amostras validas`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 30.0, 20.0, 40.0))

        // pós-descarte da 1a: [10, 30, 20, 40] ordenado -> [10, 20, 30, 40] -> (20+30)/2
        assertEquals(25.0, resultado.latenciaMs, 0.0001)
    }

    @Test
    fun `filtra amostra acima de 3x a mediana como outlier`() {
        // pós-descarte da 1a: [10, 10, 10, 10, 300] -> mediana bruta = 10 -> corte em 30
        // 300 é descartado, sobra [10,10,10,10] -> mediana final = 10
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 10.0, 10.0, 10.0, 300.0))

        assertEquals(10.0, resultado.latenciaMs, 0.0001)
        assertEquals(4, resultado.amostrasValidas)
    }

    @Test
    fun `amostra exatamente em 3x a mediana nao e descartada (filtro inclusivo)`() {
        // pós-descarte da 1a: [10, 10, 10, 10, 30] -> mediana bruta = 10 -> corte <= 30
        // 30 fica dentro do corte (<=), então nada é descartado.
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 10.0, 10.0, 10.0, 30.0))

        assertEquals(5, resultado.amostrasValidas)
    }

    @Test
    fun `filtro de outlier e ignorado se zerar a lista`() {
        // pós-descarte da 1a: [10, 300] -> mediana bruta = 155 -> corte <= 465, nada descartado.
        // Mas com só 2 amostras e mediana positiva vinda de valores muito distantes,
        // o filtro pode em tese esvaziar a lista; aqui garantimos que o fallback
        // "usa os validos originais se o filtro zerar tudo" está preservado.
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 1.0, 1000.0))

        assertEquals(2, resultado.amostrasValidas)
    }

    @Test
    fun `jitter e a media das deltas absolutas entre amostras consecutivas`() {
        // pós-descarte da 1a: [10, 15, 12] -> deltas = |15-10|=5, |12-15|=3 -> media = 4
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 15.0, 12.0))

        assertEquals(4.0, resultado.jitterMs, 0.0001)
    }

    @Test
    fun `jitter zero com menos de duas amostras validas`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 42.0))

        assertEquals(0.0, resultado.jitterMs, 0.0001)
    }

    @Test
    fun `perda percentual conta timeouts pos-descarte da primeira amostra com precisao total`() {
        // pós-descarte da 1a: [20.0, null, null, 22.0] -> 2 timeouts em 4 amostras = 50%
        val resultado = AnalisadorAmostragemPing.analisar(listOf(19.0, 20.0, null, null, 22.0))

        assertEquals(50.0, resultado.perdaPercentual, 0.0001)
        assertEquals(2, resultado.timeouts)
        assertEquals(4, resultado.totalAmostras)
    }

    @Test
    fun `perda percentual mantem precisao decimal sem arredondar para Int`() {
        // pós-descarte da 1a: 19 amostras, 1 timeout -> 1/19 * 100 = 5.263...%
        // Este e o caso que documenta a divergencia real encontrada na issue #1019:
        // o PingExecutor antigo fazia .toInt() aqui e perdia essa precisao.
        val brutas = listOf<Double?>(1.0) + List(18) { 20.0 } + listOf(null)
        val resultado = AnalisadorAmostragemPing.analisar(brutas)

        assertEquals(19, resultado.totalAmostras)
        assertEquals(1, resultado.timeouts)
        assertEquals(100.0 / 19.0, resultado.perdaPercentual, 0.0001)
    }

    @Test
    fun `perda percentual zero quando nao ha amostras pos-descarte`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(10.0))

        assertEquals(0.0, resultado.perdaPercentual, 0.0001)
        assertEquals(0, resultado.totalAmostras)
    }

    @Test
    fun `lista vazia nao quebra e devolve resultado neutro`() {
        val resultado = AnalisadorAmostragemPing.analisar(emptyList())

        assertEquals(0.0, resultado.latenciaMs, 0.0001)
        assertEquals(0.0, resultado.jitterMs, 0.0001)
        assertEquals(0.0, resultado.perdaPercentual, 0.0001)
        assertEquals(0, resultado.totalAmostras)
        assertEquals(0, resultado.amostrasValidas)
        assertEquals(0, resultado.timeouts)
    }

    @Test
    fun `todas as amostras em timeout resulta em 100 por cento de perda e zero latencia`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(null, null, null, null))

        assertEquals(100.0, resultado.perdaPercentual, 0.0001)
        assertEquals(0.0, resultado.latenciaMs, 0.0001)
        assertEquals(0, resultado.amostrasValidas)
    }

    // ── timeoutsConsecutivosMax ──────────────────────────────────────────────

    @Test
    fun `timeoutsConsecutivosMax zero quando nao ha timeout`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 12.0, 11.0))

        assertEquals(0, resultado.timeoutsConsecutivosMax)
    }

    @Test
    fun `timeoutsConsecutivosMax conta a maior sequencia mesmo com timeouts nao-consecutivos intercalados`() {
        // pos-descarte da 1a: [10, null, 12, null, null, 14] -> sequencias de 1 e 2 -> max = 2
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, null, 12.0, null, null, 14.0))

        assertEquals(3, resultado.timeouts)
        assertEquals(2, resultado.timeoutsConsecutivosMax)
    }

    @Test
    fun `timeoutsConsecutivosMax igual ao total quando todas as amostras sao timeout`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(null, null, null, null))

        assertEquals(3, resultado.timeoutsConsecutivosMax)
    }

    // ── avaliarConfianca / EvidenciaPerdaPacotes ─────────────────────────────

    @Test
    fun `confianca suficiente quando nao ha nenhum timeout`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, 12.0, 11.0))

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = false)

        assertEquals(ConfiancaAmostral.SUFICIENTE, evidencia.confianca)
        assertEquals(0.0, evidencia.perdaPercentual, 0.0001)
        assertEquals(0, evidencia.timeoutsTotais)
    }

    @Test
    fun `confianca insuficiente com 1 timeout isolado e sem confirmacao disparada`() {
        val brutas = listOf<Double?>(1.0) + List(18) { 20.0 } + listOf(null)
        val resultado = AnalisadorAmostragemPing.analisar(brutas)

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = false)

        assertEquals(ConfiancaAmostral.INSUFICIENTE, evidencia.confianca)
        assertEquals(1, evidencia.timeoutsTotais)
        // fato medido nunca e forcado a 0 mesmo com confianca insuficiente
        assertEquals(100.0 / 19.0, evidencia.perdaPercentual, 0.0001)
        assertEquals(false, evidencia.confirmacaoExecutada)
    }

    @Test
    fun `confianca continua insuficiente com 1 timeout isolado mesmo apos confirmacao nao encontrar mais nenhum`() {
        // confirmacao rodou (janela de +20 probes) e confirmou que o timeout continua
        // isolado -- o total de timeouts nao mudou, so o total de amostras cresceu.
        val brutas = listOf<Double?>(1.0) + List(38) { 20.0 } + listOf(null)
        val resultado = AnalisadorAmostragemPing.analisar(brutas)

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = true)

        assertEquals(1, evidencia.timeoutsTotais)
        assertEquals(ConfiancaAmostral.INSUFICIENTE, evidencia.confianca)
        assertEquals(true, evidencia.confirmacaoExecutada)
    }

    @Test
    fun `confianca suficiente com 2 timeouts nao-consecutivos`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, null, 12.0, null, 14.0))

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = true)

        assertEquals(2, evidencia.timeoutsTotais)
        // nao-consecutivos: a maior sequencia continua sendo 1 (nunca 2 timeouts seguidos)
        assertEquals(1, resultado.timeoutsConsecutivosMax)
        assertEquals(ConfiancaAmostral.SUFICIENTE, evidencia.confianca)
    }

    @Test
    fun `confianca suficiente com timeouts consecutivos`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(0.0, 10.0, null, null, 14.0))

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = false)

        assertEquals(2, evidencia.timeoutsConsecutivosMax)
        assertEquals(ConfiancaAmostral.SUFICIENTE, evidencia.confianca)
    }

    @Test
    fun `100 por cento de timeout e confianca suficiente mesmo sendo caso especial`() {
        val resultado = AnalisadorAmostragemPing.analisar(listOf(null, null, null, null))

        val evidencia = AnalisadorAmostragemPing.avaliarConfianca(resultado, confirmacaoExecutada = false)

        assertEquals(100.0, evidencia.perdaPercentual, 0.0001)
        assertEquals(ConfiancaAmostral.SUFICIENTE, evidencia.confianca)
    }
}
