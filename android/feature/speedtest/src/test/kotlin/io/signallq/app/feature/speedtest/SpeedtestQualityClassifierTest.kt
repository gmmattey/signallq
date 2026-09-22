package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.ConfiancaAmostral
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedtestQualityClassifierTest {
    @Test
    fun `bufferbloat thresholds are stable`() {
        assertEquals(SeveridadeBufferbloat.none, SpeedtestQualityClassifier.classificarBufferbloat(0.0))
        assertEquals(SeveridadeBufferbloat.none, SpeedtestQualityClassifier.classificarBufferbloat(4.9))
        assertEquals(SeveridadeBufferbloat.mild, SpeedtestQualityClassifier.classificarBufferbloat(5.0))
        assertEquals(SeveridadeBufferbloat.mild, SpeedtestQualityClassifier.classificarBufferbloat(30.0))
        assertEquals(SeveridadeBufferbloat.moderate, SpeedtestQualityClassifier.classificarBufferbloat(30.01))
        assertEquals(SeveridadeBufferbloat.moderate, SpeedtestQualityClassifier.classificarBufferbloat(100.0))
        assertEquals(SeveridadeBufferbloat.severe, SpeedtestQualityClassifier.classificarBufferbloat(100.01))
    }

    @Test
    fun `quality classifier returns expected veredicts and primary bottleneck`() {
        val d =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 30.0,
                ul = 10.0,
                latency = 40.0,
                jitter = 10.0,
                packetLoss = 0.1,
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
            )

        assertEquals(VereditoUso.good, d.vereditoStreaming)
        assertEquals(VereditoUso.good, d.vereditoGamer)
        assertEquals(VereditoUso.good, d.vereditoVideoChamada)
        assertEquals(GargaloPrimario.none, d.gargaloPrimario)

        val comPerda =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 100.0,
                ul = 50.0,
                latency = 20.0,
                jitter = 5.0,
                packetLoss = 3.0,
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
            )
        assertEquals(GargaloPrimario.packetLoss, comPerda.gargaloPrimario)
    }

    @Test
    fun `videoChamada good at 10Mbps not only at 25Mbps`() {
        val good =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 12.0,
                ul = 4.0,
                latency = 60.0,
                jitter = 20.0,
                packetLoss = 0.5,
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
            )
        assertEquals(VereditoUso.good, good.vereditoVideoChamada)

        val acceptable =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 26.0,
                ul = 2.0,
                latency = 60.0,
                jitter = 20.0,
                packetLoss = 0.5,
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
            )
        assertEquals(VereditoUso.acceptable, acceptable.vereditoVideoChamada)
    }

    // ── Testes dourados (caracterizacao) — Fase 0 da issue #1228, ver ADR-011 ──────────────
    // Congelam o comportamento atual EXATO das fronteiras good/acceptable/poor de cada perfil
    // de uso e a ordem de prioridade do gargalo primario. Nao alterar producao pra fazer estes
    // testes passarem — mudanca de valor aqui so por decisao deliberada de migracao (Fase 1+).

    private fun qualidadeBase(
        dl: Double = 100.0,
        ul: Double = 50.0,
        latency: Double = 10.0,
        jitter: Double = 1.0,
        packetLoss: Double = 0.0,
        bufferbloatDeltaMs: Double = 0.0,
        bufferbloat: SeveridadeBufferbloat = SeveridadeBufferbloat.none,
    ) = SpeedtestQualityClassifier.classificarQualidade(dl, ul, latency, jitter, packetLoss, bufferbloatDeltaMs, bufferbloat)

    @Test
    fun `golden - streaming good ate 25Mbps e 200ms, acceptable abaixo disso`() {
        val naFronteira = qualidadeBase(dl = 25.0, latency = 200.0, jitter = 50.0, packetLoss = 2.0)
        assertEquals(VereditoUso.good, naFronteira.vereditoStreaming)

        val abaixoDl = qualidadeBase(dl = 24.99, latency = 200.0, jitter = 50.0, packetLoss = 2.0)
        assertEquals(VereditoUso.acceptable, abaixoDl.vereditoStreaming)
    }

    @Test
    fun `golden - streaming poor abaixo do piso acceptable`() {
        val poor = qualidadeBase(dl = 14.99, latency = 500.0, jitter = 100.0, packetLoss = 5.0)
        assertEquals(VereditoUso.poor, poor.vereditoStreaming)
    }

    @Test
    fun `golden - gamer good exige dl 10 ul 3 latencia 50 jitter 15 perda 0_5`() {
        val naFronteira = qualidadeBase(dl = 10.0, ul = 3.0, latency = 50.0, jitter = 15.0, packetLoss = 0.5)
        assertEquals(VereditoUso.good, naFronteira.vereditoGamer)

        val abaixoUl = qualidadeBase(dl = 10.0, ul = 2.99, latency = 50.0, jitter = 15.0, packetLoss = 0.5)
        assertEquals(VereditoUso.acceptable, abaixoUl.vereditoGamer)
    }

    @Test
    fun `golden - gamer poor abaixo do piso acceptable`() {
        val poor = qualidadeBase(dl = 4.99, ul = 0.99, latency = 100.0, jitter = 30.0, packetLoss = 1.0)
        assertEquals(VereditoUso.poor, poor.vereditoGamer)
    }

    @Test
    fun `golden - videochamada good exige dl 10 ul 3 latencia 80 jitter 30 perda 1_0`() {
        val naFronteira = qualidadeBase(dl = 10.0, ul = 3.0, latency = 80.0, jitter = 30.0, packetLoss = 1.0)
        assertEquals(VereditoUso.good, naFronteira.vereditoVideoChamada)

        val acimaLatencia = qualidadeBase(dl = 10.0, ul = 3.0, latency = 80.01, jitter = 30.0, packetLoss = 1.0)
        assertEquals(VereditoUso.acceptable, acimaLatencia.vereditoVideoChamada)
    }

    @Test
    fun `golden - gargalo primario segue ordem packetLoss maior que bufferbloat maior que latency maior que upload`() {
        // packetLoss > 2.0 vence mesmo com bufferbloat severo e upload baixo simultaneos
        val comTudo =
            qualidadeBase(
                ul = 1.0,
                latency = 200.0,
                packetLoss = 2.01,
                bufferbloatDeltaMs = 150.0,
                bufferbloat = SeveridadeBufferbloat.severe,
            )
        assertEquals(GargaloPrimario.packetLoss, comTudo.gargaloPrimario)

        // sem packetLoss, bufferbloat severo vence sobre latencia e upload baixos
        val semPerda =
            qualidadeBase(
                ul = 1.0,
                latency = 200.0,
                packetLoss = 0.0,
                bufferbloatDeltaMs = 150.0,
                bufferbloat = SeveridadeBufferbloat.severe,
            )
        assertEquals(GargaloPrimario.bufferbloat, semPerda.gargaloPrimario)

        // sem perda nem bufferbloat severo, latencia > 100 vence sobre upload baixo
        val soLatencia = qualidadeBase(ul = 1.0, latency = 100.01, packetLoss = 0.0, bufferbloatDeltaMs = 0.0)
        assertEquals(GargaloPrimario.latency, soLatencia.gargaloPrimario)

        // restando so upload baixo
        val soUpload = qualidadeBase(ul = 4.99, latency = 100.0, packetLoss = 0.0, bufferbloatDeltaMs = 0.0)
        assertEquals(GargaloPrimario.upload, soUpload.gargaloPrimario)

        // nada abaixo do limiar -> nenhum gargalo
        val nenhum = qualidadeBase(ul = 5.0, latency = 100.0, packetLoss = 0.0, bufferbloatDeltaMs = 0.0)
        assertEquals(GargaloPrimario.none, nenhum.gargaloPrimario)
    }

    @Test
    fun `golden - bufferbloatDeltaMs fronteira 100_0 e maior-ou-igual, ja aciona gargalo`() {
        val abaixo = qualidadeBase(ul = 10.0, latency = 10.0, packetLoss = 0.0, bufferbloatDeltaMs = 99.99)
        assertEquals(GargaloPrimario.none, abaixo.gargaloPrimario)

        val naFronteira = qualidadeBase(ul = 10.0, latency = 10.0, packetLoss = 0.0, bufferbloatDeltaMs = 100.0)
        assertEquals(GargaloPrimario.bufferbloat, naFronteira.gargaloPrimario)
    }

    // ── ConfiancaAmostral (.agents/architecture-plan.md, "Confiabilidade estatistica
    //    do diagnostico de rede") — checkpoint 4 do plano de implementacao ──────────

    @Test
    fun `perda com confianca insuficiente NAO derruba o veredito para poor nem vira gargalo primario`() {
        val comPerdaAcimaDoCorteMasIsolada =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 100.0,
                ul = 50.0,
                latency = 20.0,
                jitter = 5.0,
                packetLoss = 5.0, // isoladamente ja seria poor/gargalo=packetLoss
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
                perdaConfianca = ConfiancaAmostral.INSUFICIENTE,
            )

        assertEquals(VereditoUso.good, comPerdaAcimaDoCorteMasIsolada.vereditoStreaming)
        assertEquals(VereditoUso.good, comPerdaAcimaDoCorteMasIsolada.vereditoGamer)
        assertEquals(VereditoUso.good, comPerdaAcimaDoCorteMasIsolada.vereditoVideoChamada)
        assertEquals(GargaloPrimario.none, comPerdaAcimaDoCorteMasIsolada.gargaloPrimario)
    }

    @Test
    fun `perda confirmada com confianca suficiente CONTINUA derrubando o veredito para poor e virando gargalo primario`() {
        val comPerdaConfirmada =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 100.0,
                ul = 50.0,
                latency = 20.0,
                jitter = 5.0,
                packetLoss = 5.0,
                bufferbloatDeltaMs = 0.0,
                bufferbloat = SeveridadeBufferbloat.none,
                perdaConfianca = ConfiancaAmostral.SUFICIENTE,
            )

        assertEquals(VereditoUso.poor, comPerdaConfirmada.vereditoGamer)
        assertEquals(GargaloPrimario.packetLoss, comPerdaConfirmada.gargaloPrimario)
    }

    @Test
    fun `outras metricas ruins continuam valendo mesmo com perda de confianca insuficiente`() {
        // Perda isolada nao "salva" o veredito quando OUTRA metrica ja e ruim sozinha.
        val bufferbloatSeveroComPerdaIsolada =
            SpeedtestQualityClassifier.classificarQualidade(
                dl = 100.0,
                ul = 50.0,
                latency = 20.0,
                jitter = 5.0,
                packetLoss = 5.0,
                bufferbloatDeltaMs = 150.0,
                bufferbloat = SeveridadeBufferbloat.severe,
                perdaConfianca = ConfiancaAmostral.INSUFICIENTE,
            )

        assertEquals(GargaloPrimario.bufferbloat, bufferbloatSeveroComPerdaIsolada.gargaloPrimario)
    }
}
