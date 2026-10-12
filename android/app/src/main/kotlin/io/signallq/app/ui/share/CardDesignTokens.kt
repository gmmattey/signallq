package io.signallq.app.ui.share

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Constantes e funções de cálculo para renderização dos cards sociais do SignallQ.
 * Mantém fidelidade estrita com o protótipo oficial (Cards de compartilhamento.dc.html e
 * Cards de compartilhamento Gamer.dc.html).
 */
object CardDesignTokens {
    const val COLOR_BG = 0xFF0B0B10.toInt()
    const val COLOR_TEXT = 0xFFF2EDF7.toInt()
    const val COLOR_MUTED = 0xFFA8A2B3.toInt()
    const val COLOR_PANEL = 0xFF16151D.toInt()
    const val COLOR_DOWNLOAD = 0xFF83DA99.toInt()
    const val COLOR_UPLOAD = 0xFFFFB870.toInt()
    const val COLOR_LATENCY = 0xFFAAC7FF.toInt()
    const val COLOR_TICK_MAJOR = 0xFF4A4658.toInt()
    const val COLOR_TICK_MINOR = 0xFF2A2833.toInt()

    const val COLOR_GAME_ACCENT = 0xFFD0BCFF.toInt()
    const val COLOR_GAME_MUTED = 0xFFC9C3D3.toInt()
    const val COLOR_GAME_BLUE = 0xFFAAC7FF.toInt()
    const val COLOR_GAME_GREEN = 0xFF83DA99.toInt()
    const val COLOR_GAME_AMBER = 0xFFFFB870.toInt()
    const val COLOR_GAME_RED = 0xFFFFB4AB.toInt()

    const val TAG_OK_COLOR = 0xFF83DA99.toInt()
    const val TAG_OK_BG = 0xFF143B22.toInt()
    const val TAG_WARN_COLOR = 0xFFFFB870.toInt()
    const val TAG_WARN_BG = 0xFF3B2610.toInt()
    const val TAG_BAD_COLOR = 0xFFFFB4AB.toInt()
    const val TAG_BAD_BG = 0xFF4A1B17.toInt()

    const val PREVIEW_W = 1200
    const val PREVIEW_H = 630
    const val STORY_W = 1080
    const val STORY_H = 1920

    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=io.signallq.app"

    fun cardHeadline(download: Double): String =
        when {
            download >= 100.0 -> "Internet ótima."
            download >= 25.0 -> "Internet boa."
            download >= 8.0 -> "Internet no básico."
            else -> "Internet lenta."
        }

    fun cardNumber(v: Double): String =
        if (v >= 100.0) {
            v.roundToInt().toString()
        } else {
            String.format(Locale("pt", "BR"), "%.1f", v)
        }

    fun niceMax(v: Double): Double {
        val steps = doubleArrayOf(50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0)
        for (s in steps) {
            if (v <= s) return s
        }
        return 5000.0
    }

    fun ringFraction(value: Double, maxVal: Double): Double {
        if (maxVal <= 0 || value <= 0) return 0.0
        return min(1.0, value / maxVal).pow(0.78)
    }

    data class CardTick(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val width: Float,
        val opacity: Float,
        val color: Int,
    )

    fun cardTicks(download: Double): List<CardTick> {
        val ringTicks = 72
        val ringArcDeg = 240.0
        val maxVal = niceMax(download * 1.15)
        val fraction = ringFraction(download, maxVal)
        val head = (fraction * (ringTicks - 1)).roundToInt()
        val ticks = ArrayList<CardTick>(ringTicks)

        for (i in 0 until ringTicks) {
            val angle = ((-ringArcDeg / 2.0 + (ringArcDeg * i) / (ringTicks - 1).toDouble()) * PI) / 180.0
            val major = i % 9 == 0
            val r1 = 205.0
            val r2 = if (i == head) 176.0 else if (major) 183.0 else 191.0
            val width = if (i == head) 3.5f else if (i < head) 2.2f else if (major) 2.0f else 1.5f
            val opacity = if (i <= head) (0.35 + (0.65 * i) / max(1, head)).toFloat() else 1.0f
            val color = if (i <= head) COLOR_DOWNLOAD else if (major) COLOR_TICK_MAJOR else COLOR_TICK_MINOR

            val s = sin(angle)
            val c = cos(angle)
            ticks.add(
                CardTick(
                    x1 = (220.0 + r1 * s).toFloat(),
                    y1 = (220.0 - r1 * c).toFloat(),
                    x2 = (220.0 + r2 * s).toFloat(),
                    y2 = (220.0 - r2 * c).toFloat(),
                    width = width,
                    opacity = opacity,
                    color = color,
                )
            )
        }
        return ticks
    }

    /**
     * Matriz 33x33 do QR code oficial do Google Play Store para io.signallq.app
     * (precomputada para zero overhead, renderização síncrona instantânea e sem dependências externas).
     */
    val PLAY_STORE_QR_ROWS = arrayOf(
        "111111101010000001111101001111111",
        "100000101111001010100011001000001",
        "101110100000110000000011101011101",
        "101110101100100111000001101011101",
        "101110100001011110010010101011101",
        "100000100000101011111100101000001",
        "111111101010101010101010101111111",
        "000000001001000001110011000000000",
        "101101110100001011110101001001011",
        "111110010111110010111001101101111",
        "100001101111110001001011101111011",
        "101100001100000110101000000101010",
        "111011111011001111010011110111000",
        "011100000100101110010100100101010",
        "100100110011010000111100101011100",
        "101011000101101100000111011000100",
        "111110101000011010011110111011100",
        "101100001111110101000111101011001",
        "100011110001001110001100001110110",
        "100100010101000111000011011010001",
        "010001111010001111110100100101110",
        "110011001100101011111001101001001",
        "000000111100101000100111101100111",
        "011100000110000100100001010111000",
        "101010111010100001000010111110011",
        "000000001011011110110101100011010",
        "111111101110110010110101101010000",
        "100000101010000110101111100011110",
        "101110100010000000110101111110101",
        "101110101111010110101010000101001",
        "101110101011010111100011001100100",
        "100000100110001001110010110110001",
        "111111101000111001100101010010100"
    )

    fun wrapLines(text: String, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in text.split(" ")) {
            if (cur.isNotEmpty() && (cur.length + 1 + w.length) > maxChars) {
                lines.add(cur)
                cur = w
            } else {
                cur = if (cur.isEmpty()) w else "$cur $w"
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }
}
