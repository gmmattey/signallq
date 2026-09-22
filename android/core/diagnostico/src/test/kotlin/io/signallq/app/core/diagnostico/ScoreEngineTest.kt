package io.signallq.app.core.diagnostico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do [ScoreEngine] (SIG-288): pesos por tipo de conexao, reponderacao quando
 * falta dado confiavel e tetos por metrica critica isolada.
 */
class ScoreEngineTest {
    private fun ev(
        nome: String,
        nota: Int?,
        provenance: Provenance = Provenance.medida,
        confiancaAmostral: ConfiancaAmostral? = null,
    ) = EvidenceScore(nome, nota, provenance, confiancaAmostral)

    // ── Media ponderada basica (Wi-Fi) ──────────────────────────────────────────

    @Test
    fun `wifi com todas as dimensoes excelentes da score proximo de 100`() {
        val evidencias =
            listOf(
                ev("estabilidade", 100),
                ev("wifiRedeLocal", 100),
                ev("velocidade", 100),
                ev("dns", 100),
                ev("historico", 100),
            )
        val resultado = ScoreEngine.calcular(ScoreEngine.TipoConexao.WIFI, evidencias)
        assertEquals(100, resultado.score)
    }

    @Test
    fun `wifi pondera estabilidade mais que dns`() {
        // Estabilidade ruim (nota 20) pesa 35%, dns ruim (nota 20) pesaria so 10% —
        // o impacto no score deve ser bem maior quando e estabilidade que cai.
        val comEstabilidadeRuim =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 20),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                ),
            )
        val comDnsRuim =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100),
                    ev("dns", 20),
                    ev("historico", 100),
                ),
            )
        assertTrue(comEstabilidadeRuim.score!! < comDnsRuim.score!!)
    }

    // ── Reponderacao automatica ──────────────────────────────────────────────

    @Test
    fun `dimensao indisponivel nao vira nota artificial e e removida do calculo`() {
        // So estabilidade disponivel (nota 100) — resto indisponivel. Reponderando,
        // o peso todo cai em estabilidade e o score deve ser 100, nao uma media com
        // zeros implicitos nas dimensoes ausentes.
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(ev("estabilidade", 100)),
            )
        assertEquals(100, resultado.score)
        assertEquals(setOf("wifiRedeLocal", "velocidade", "dns", "historico"), resultado.dadosAusentes.toSet())
    }

    @Test
    fun `todas as dimensoes indisponiveis retorna score nulo`() {
        val resultado = ScoreEngine.calcular(ScoreEngine.TipoConexao.WIFI, emptyList())
        assertNull(resultado.score)
        assertEquals(5, resultado.dadosAusentes.size)
    }

    @Test
    fun `reponderacao mantem proporcao relativa entre dimensoes disponiveis`() {
        // Só velocidade (25%) e dns (10%) disponiveis. Reponderando entre elas
        // (proporcao 25:10 = 5:2), o resultado deve ficar mais perto da nota de
        // velocidade (peso maior) que da nota de dns.
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(ev("velocidade", 100), ev("dns", 0)),
            )
        // 100 * (25/35) + 0 * (10/35) = ~71
        assertEquals(71, resultado.score)
    }

    // ── Pesos por tipo de conexao ────────────────────────────────────────────

    @Test
    fun `fibra pondera dimensao fibra mais que wifi pondera wifiRedeLocal`() {
        val fibraRuim =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.FIBRA,
                listOf(
                    ev("fibra", 15),
                    ev("estabilidade", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                ),
            )
        val wifiRedeLocalRuim =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("wifiRedeLocal", 15),
                    ev("estabilidade", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                ),
            )
        assertTrue(fibraRuim.score!! < wifiRedeLocalRuim.score!!)
    }

    @Test
    fun `movel pondera sinalMovel como dimensao dedicada`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.MOVEL,
                listOf(
                    ev("sinalMovel", 15),
                    ev("estabilidade", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                ),
            )
        // sinalMovel pesa 30% -> score deve cair sensivelmente abaixo de 100.
        assertTrue(resultado.score!! < 90)
    }

    // ── Tetos por metrica critica isolada ───────────────────────────────────

    @Test
    fun `perda de pacotes critica com confianca amostral suficiente limita o score a 45`() {
        // .agents/architecture-plan.md ("Confiabilidade estatistica do diagnostico de
        // rede"): o gate de ScoreEngine.aplicarTetos migrou de
        // `provenance == Provenance.medida` (inatingivel em producao via timeout HTTP)
        // para `confiancaAmostral == SUFICIENTE` (2+ timeouts/consecutivos/confirmados).
        // Comportamento esperado preservado: perda critica "de verdade" continua
        // limitando o score a 45 — so o mecanismo que sinaliza "de verdade" mudou.
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("perdaPacotesStatus", 15, Provenance.estimada, ConfiancaAmostral.SUFICIENTE), // critico
                ),
            )
        assertTrue(resultado.score!! <= ScoreEngine.TETO_PERDA_PACOTES_CRITICA)
    }

    @Test
    fun `perda de pacotes critica mas com confianca amostral insuficiente nao aplica teto`() {
        // Caso novo pedido pelo Luiz: 1 timeout isolado (confianca insuficiente) nunca
        // aplica o teto critico sozinho, mesmo com nota critica calculada sobre o
        // percentual medido.
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("perdaPacotesStatus", 15, Provenance.estimada, ConfiancaAmostral.INSUFICIENTE),
                ),
            )
        assertEquals(100, resultado.score)
    }

    @Test
    fun `bufferbloat critico limita o score a 60`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("bufferbloatStatus", 15),
                ),
            )
        assertTrue(resultado.score!! <= ScoreEngine.TETO_BUFFERBLOAT_CRITICO)
    }

    @Test
    fun `fibra rx fora da faixa critica limita o score a 35`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.FIBRA,
                listOf(
                    ev("fibra", 100),
                    ev("estabilidade", 100),
                    ev("velocidade", 100),
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("fibraRxStatus", 15),
                ),
            )
        assertTrue(resultado.score!! <= ScoreEngine.TETO_FIBRA_RX_CRITICA)
    }

    @Test
    fun `rssi muito fraco com download baixo limita o score a 65`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 40), // ruim
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("rssiStatus", 15), // critico
                ),
            )
        assertTrue(resultado.score!! <= ScoreEngine.TETO_RSSI_FRACO_DOWNLOAD_BAIXO)
    }

    @Test
    fun `rssi fraco sem download baixo nao aplica teto de rssi`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.WIFI,
                listOf(
                    ev("estabilidade", 100),
                    ev("wifiRedeLocal", 100),
                    ev("velocidade", 100), // bom, nao aplica o teto combinado
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("rssiStatus", 15),
                ),
            )
        assertEquals(100, resultado.score)
    }

    @Test
    fun `menor teto entre os aplicaveis vence quando multiplos batem`() {
        val resultado =
            ScoreEngine.calcular(
                ScoreEngine.TipoConexao.FIBRA,
                listOf(
                    ev("fibra", 100),
                    ev("estabilidade", 100),
                    ev("velocidade", 40),
                    ev("dns", 100),
                    ev("historico", 100),
                    ev("bufferbloatStatus", 15), // teto 60
                    ev("fibraRxStatus", 15), // teto 35 — mais restritivo
                ),
            )
        assertTrue(resultado.score!! <= ScoreEngine.TETO_FIBRA_RX_CRITICA)
    }
}
