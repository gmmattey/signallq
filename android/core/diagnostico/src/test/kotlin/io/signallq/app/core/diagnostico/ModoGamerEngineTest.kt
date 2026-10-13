package io.signallq.app.core.diagnostico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do [ModoGamerEngine] (Feature #550, issue #1476, fundido com GH#935 pela issue
 * #1487) — cobre as 6 categorias (faixa ok/atenção/crítica por métrica priorizada), dados
 * insuficientes (nunca inventa evidência), a evidência informativa de device, o catálogo
 * fundido de 21 jogos + fallback e o refinamento opcional por ping dedicado.
 */
class ModoGamerEngineTest {
    private fun internet(
        download: Double? = 100.0,
        upload: Double? = 20.0,
        latencia: Double? = 20.0,
        jitter: Double? = 5.0,
        perda: Double? = 0.0,
        bufferbloat: Double? = 10.0,
    ) = InternetDiagnosticInput(
        downloadMbps = download,
        uploadMbps = upload,
        latencyMs = latencia,
        jitterMs = jitter,
        perdaPercentual = perda,
        bufferbloatMs = bufferbloat,
    )

    // ── FPS competitivo (latência + jitter + perda) ──────────────────────────

    @Test
    fun `fps competitivo fica ok dentro da faixa`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 40.0, jitter = 3.0, perda = 0.0)),
            )
        assertEquals(DiagnosticStatus.ok, r.status)
        assertTrue(r.acoes.isEmpty())
    }

    @Test
    fun `fps competitivo fica critica com latencia alta`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 250.0)),
            )
        assertEquals(DiagnosticStatus.critical, r.status)
        assertTrue(r.acoes.isNotEmpty())
    }

    @Test
    fun `fps competitivo inconclusivo sem dados de internet`() {
        val r = ModoGamerEngine.avaliar(CategoriaJogoModoGamer.FPS_COMPETITIVO, DeviceJogo.PC, null)
        assertEquals(DiagnosticStatus.inconclusive, r.status)
        assertTrue(r.dadosInsuficientes)
        // Mesmo sem métricas, a linha de contexto do device continua presente.
        assertEquals(1, r.evidencias.size)
        assertEquals("Aparelho analisado", r.evidencias.first().label)
    }

    // ── Battle royale (latência + jitter + download) ─────────────────────────

    @Test
    fun `battle royale fica atencao com download no limite`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.BATTLE_ROYALE,
                DeviceJogo.ANDROID,
                DiagnosticInput(internet = internet(download = 30.0)),
            )
        assertEquals(DiagnosticStatus.attention, r.status)
    }

    // ── MOBA (jitter + latência + perda) ──────────────────────────────────────

    @Test
    fun `moba fica critica com jitter alto`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.MOBA,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(jitter = 40.0)),
            )
        assertEquals(DiagnosticStatus.critical, r.status)
    }

    // ── Casual (download + latência) ──────────────────────────────────────────

    @Test
    fun `casual fica ok com download e latencia bons`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.CASUAL,
                DeviceJogo.SWITCH,
                DiagnosticInput(internet = internet(download = 80.0, latencia = 50.0)),
            )
        assertEquals(DiagnosticStatus.ok, r.status)
        assertEquals(2, r.evidencias.size - 1) // -1 = linha de device
    }

    // ── Cloud gaming (download + bufferbloat + latência) ──────────────────────

    @Test
    fun `cloud gaming fica critica com bufferbloat severo`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.CLOUD_GAMING,
                DeviceJogo.TV_CLOUD,
                DiagnosticInput(internet = internet(bufferbloat = 150.0)),
            )
        assertEquals(DiagnosticStatus.critical, r.status)
    }

    // ── Outro (fallback, mesma régua de FPS_COMPETITIVO) ──────────────────────

    @Test
    fun `outro fica atencao com latencia no limite`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.OUTRO,
                DeviceJogo.IPHONE,
                DiagnosticInput(internet = internet(latencia = 170.0)),
            )
        assertEquals(DiagnosticStatus.attention, r.status)
    }

    // ── Evidência de device (sempre presente, nunca influencia severidade) ────

    @Test
    fun `evidencia de device usa o label do device selecionado`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PLAYSTATION,
                DiagnosticInput(internet = internet()),
            )
        val evidenciaDevice = r.evidencias.last()
        assertEquals("Aparelho analisado", evidenciaDevice.label)
        assertEquals("PS5 / PS4", evidenciaDevice.valorExibido)
        assertEquals(MetricStatus.inconclusivo, evidenciaDevice.status)
    }

    @Test
    fun `device nunca eleva status mesmo com metricas ruins`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.XBOX,
                DiagnosticInput(internet = internet(latencia = 20.0, jitter = 2.0, perda = 0.0)),
            )
        assertEquals(DiagnosticStatus.ok, r.status)
    }

    // ── Catálogo de jogos expandido (53 jogos com suporte a servidores AWS GameLift) ──

    @Test
    fun `catalogo expandido tem exatamente 53 jogos com ids unicos`() {
        assertEquals(53, CatalogoJogosModoGamer.jogos.size)
        assertEquals(
            53,
            CatalogoJogosModoGamer.jogos
                .map { it.gameId }
                .toSet()
                .size,
        )
    }

    @Test
    fun `jogos do catalogo definem plataformas compativeis`() {
        val cs2 = CatalogoJogosModoGamer.porId("counter_strike_2")
        assertEquals(listOf(DeviceJogo.PC), cs2?.platforms)

        val fortnite = CatalogoJogosModoGamer.porId("fortnite")
        assertTrue(fortnite?.platforms?.contains(DeviceJogo.PC) == true)
        assertTrue(fortnite?.platforms?.contains(DeviceJogo.ANDROID) == true)
        assertTrue(fortnite?.platforms?.contains(DeviceJogo.PLAYSTATION) == true)

        val overwatch2 = CatalogoJogosModoGamer.porId("overwatch_2")
        assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, overwatch2?.categoria)
    }

    @Test
    fun `catalogo resolve jogo catalogado por id`() {
        val jogo = CatalogoJogosModoGamer.porId("valorant")
        assertEquals("Valorant", jogo?.nome)
        assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, jogo?.categoria)
    }

    @Test
    fun `catalogo devolve null para jogo fora do catalogo (nunca erro)`() {
        assertEquals(null, CatalogoJogosModoGamer.porId("jogo-inexistente-xyz"))
    }

    @Test
    fun `jogos deduplicados entre os dois catalogos mantem o id do fluxo novo`() {
        // "EA Sports FC" (legado) e "EA FC" (novo) são o mesmo jogo — só um id sobrevive.
        assertEquals("EA FC", CatalogoJogosModoGamer.porId("ea_fc")?.nome)
        assertEquals(null, CatalogoJogosModoGamer.porId("ea_sports_fc"))
        // "VALORANT" (legado, id "valorant" também) e "Valorant" (novo) — mesmo id, sem duplicata.
        assertEquals(1, CatalogoJogosModoGamer.jogos.count { it.gameId == "valorant" })
        // Fortnite e League of Legends existiam nos dois catálogos — também sem duplicata.
        assertEquals(1, CatalogoJogosModoGamer.jogos.count { it.gameId == "fortnite" })
        assertEquals(1, CatalogoJogosModoGamer.jogos.count { it.gameId == "league_of_legends" })
    }

    @Test
    fun `jogos exclusivos do legado GH935 foram migrados para as categorias existentes`() {
        assertEquals(CategoriaJogoModoGamer.BATTLE_ROYALE, CatalogoJogosModoGamer.porId("warzone")?.categoria)
        assertEquals(CategoriaJogoModoGamer.BATTLE_ROYALE, CatalogoJogosModoGamer.porId("apex_legends")?.categoria)
        assertEquals(CategoriaJogoModoGamer.BATTLE_ROYALE, CatalogoJogosModoGamer.porId("pubg_battlegrounds")?.categoria)
        assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, CatalogoJogosModoGamer.porId("counter_strike_2")?.categoria)
        assertEquals(CategoriaJogoModoGamer.FPS_COMPETITIVO, CatalogoJogosModoGamer.porId("rocket_league")?.categoria)
        assertEquals(CategoriaJogoModoGamer.MOBA, CatalogoJogosModoGamer.porId("dota_2")?.categoria)
        assertEquals(CategoriaJogoModoGamer.CASUAL, CatalogoJogosModoGamer.porId("destiny_2")?.categoria)
        assertEquals(CategoriaJogoModoGamer.CASUAL, CatalogoJogosModoGamer.porId("dead_by_daylight")?.categoria)
    }

    @Test
    fun `nenhum jogo do catalogo fundido mapeia para cloud gaming`() {
        assertTrue(CatalogoJogosModoGamer.jogos.none { it.categoria == CategoriaJogoModoGamer.CLOUD_GAMING })
    }

    @Test
    fun `todas as 6 categorias tem avaliador proprio (nenhuma lanca excecao)`() {
        CategoriaJogoModoGamer.entries.forEach { categoria ->
            val r = ModoGamerEngine.avaliar(categoria, DeviceJogo.PC, DiagnosticInput(internet = internet()))
            assertEquals(categoria, r.categoria)
        }
    }

    // ── Refinamento opcional por ping dedicado (issue #1487, item 2) ─────────

    @Test
    fun `pingEspecificoMs refina a dimensao de latencia quando presente`() {
        // Sem refinamento: latência alta (250ms) deixa o resultado crítico.
        val semRefinar =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 250.0, jitter = 3.0, perda = 0.0)),
            )
        assertEquals(DiagnosticStatus.critical, semRefinar.status)

        // Com refinamento: medição dedicada de 30ms substitui a latência do input já coletado.
        val comRefinar =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 250.0, jitter = 3.0, perda = 0.0)),
                pingEspecificoMs = 30.0,
            )
        assertEquals(DiagnosticStatus.ok, comRefinar.status)
        assertTrue(comRefinar.evidencias.any { it.label == "Tempo de resposta medido agora" && it.valorExibido.contains("30") })
    }

    @Test
    fun `pingEspecificoMs permite resultado mesmo sem DiagnosticInput previo`() {
        // Sem input nenhum (ex.: abriu o Modo gamer direto de Ferramentas, sem ter rodado
        // teste de velocidade ainda) — sem refinamento, cai em dados insuficientes.
        val semInput = ModoGamerEngine.avaliar(CategoriaJogoModoGamer.FPS_COMPETITIVO, DeviceJogo.PC, null)
        assertTrue(semInput.dadosInsuficientes)

        // Com a medição dedicada, deixa de ser "dados insuficientes" mesmo sem input algum.
        val comPing = ModoGamerEngine.avaliar(CategoriaJogoModoGamer.FPS_COMPETITIVO, DeviceJogo.PC, null, pingEspecificoMs = 25.0)
        assertFalse(comPing.dadosInsuficientes)
        assertEquals(DiagnosticStatus.ok, comPing.status)
    }

    @Test
    fun `sem pingEspecificoMs o comportamento permanece identico ao anterior a fusao`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.CASUAL,
                DeviceJogo.SWITCH,
                DiagnosticInput(internet = internet(download = 80.0, latencia = 50.0)),
            )
        assertEquals(DiagnosticStatus.ok, r.status)
        assertTrue(r.evidencias.none { it.label == "Tempo de resposta medido agora" })
    }

    // ── Veredito direto (issue #1667, decisão do Luiz 2026-08-19) ────────────
    // "Linguagem direta e simples em vez de fraseado de probabilidade."

    @Test
    fun `veredito e direto e simples para cada status`() {
        assertEquals("Bom pra jogar", DiagnosticStatus.ok.paraVereditoDiretoModoGamer())
        assertEquals("Pode ter atrasos", DiagnosticStatus.attention.paraVereditoDiretoModoGamer())
        assertEquals("Não recomendado", DiagnosticStatus.critical.paraVereditoDiretoModoGamer())
        assertEquals("Sem dados suficientes", DiagnosticStatus.inconclusive.paraVereditoDiretoModoGamer())
    }

    @Test
    fun `resultado ok carrega o veredito bom pra jogar`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 40.0, jitter = 3.0, perda = 0.0)),
            )
        assertEquals("Bom pra jogar", r.veredito)
    }

    @Test
    fun `resultado critico carrega o veredito nao recomendado`() {
        val r =
            ModoGamerEngine.avaliar(
                CategoriaJogoModoGamer.FPS_COMPETITIVO,
                DeviceJogo.PC,
                DiagnosticInput(internet = internet(latencia = 250.0)),
            )
        assertEquals("Não recomendado", r.veredito)
    }

    @Test
    fun `resultado sem dados carrega o veredito sem dados suficientes`() {
        val r = ModoGamerEngine.avaliar(CategoriaJogoModoGamer.FPS_COMPETITIVO, DeviceJogo.PC, null)
        assertEquals("Sem dados suficientes", r.veredito)
    }
}
