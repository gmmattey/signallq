package io.signallq.app.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardDesignTokensTest {
    @Test
    fun `cardHeadline retorna faixas corretas de velocidade`() {
        assertEquals("Internet ótima.", CardDesignTokens.cardHeadline(150.0))
        assertEquals("Internet ótima.", CardDesignTokens.cardHeadline(100.0))
        assertEquals("Internet boa.", CardDesignTokens.cardHeadline(99.9))
        assertEquals("Internet boa.", CardDesignTokens.cardHeadline(25.0))
        assertEquals("Internet no básico.", CardDesignTokens.cardHeadline(24.9))
        assertEquals("Internet no básico.", CardDesignTokens.cardHeadline(8.0))
        assertEquals("Internet lenta.", CardDesignTokens.cardHeadline(7.9))
        assertEquals("Internet lenta.", CardDesignTokens.cardHeadline(1.0))
    }

    @Test
    fun `cardNumber formata inteiros e decimais com virgula`() {
        assertEquals("150", CardDesignTokens.cardNumber(150.0))
        assertEquals("100", CardDesignTokens.cardNumber(100.2))
        assertEquals("85,4", CardDesignTokens.cardNumber(85.4))
        assertEquals("7,0", CardDesignTokens.cardNumber(7.0))
    }

    @Test
    fun `niceMax calcula escala correta de anel`() {
        assertEquals(50.0, CardDesignTokens.niceMax(45.0), 0.001)
        assertEquals(100.0, CardDesignTokens.niceMax(80.0), 0.001)
        assertEquals(250.0, CardDesignTokens.niceMax(200.0), 0.001)
        assertEquals(500.0, CardDesignTokens.niceMax(400.0), 0.001)
        assertEquals(1000.0, CardDesignTokens.niceMax(900.0), 0.001)
        assertEquals(2500.0, CardDesignTokens.niceMax(1500.0), 0.001)
        assertEquals(5000.0, CardDesignTokens.niceMax(3500.0), 0.001)
    }

    @Test
    fun `cardTicks gera exatamente 72 ticks e calcula limites`() {
        val ticks = CardDesignTokens.cardTicks(120.0)
        assertEquals(72, ticks.size)

        // Primeiro tick
        val t0 = ticks[0]
        assertNotNull(t0)
        assertTrue(t0.width > 0f)
        assertTrue(t0.opacity in 0f..1f)

        // Último tick
        val t71 = ticks[71]
        assertNotNull(t71)
        assertTrue(t71.width > 0f)
    }

    @Test
    fun `PLAY_STORE_QR_ROWS possui exatamente 33 linhas com 33 colunas`() {
        val rows = CardDesignTokens.PLAY_STORE_QR_ROWS
        assertEquals(33, rows.size)
        rows.forEach { row ->
            assertEquals(33, row.length)
            assertTrue(row.all { it == '0' || it == '1' })
        }
    }

    @Test
    fun `wrapLines quebra texto sem cortar palavras`() {
        val lines = CardDesignTokens.wrapLines("Dá para jogar, com atrasos ocasionais.", 20)
        assertTrue(lines.size >= 2)
        assertEquals("Dá para jogar, com", lines[0])
    }
}
