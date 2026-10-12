package io.signallq.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.signallq.app.R
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.diagnostico.EvidenciaDiagnostico
import io.signallq.app.ui.share.CardDesignTokens.COLOR_BG
import io.signallq.app.ui.share.CardDesignTokens.COLOR_GAME_ACCENT
import io.signallq.app.ui.share.CardDesignTokens.COLOR_GAME_AMBER
import io.signallq.app.ui.share.CardDesignTokens.COLOR_GAME_BLUE
import io.signallq.app.ui.share.CardDesignTokens.COLOR_GAME_GREEN
import io.signallq.app.ui.share.CardDesignTokens.COLOR_GAME_MUTED
import io.signallq.app.ui.share.CardDesignTokens.COLOR_MUTED
import io.signallq.app.ui.share.CardDesignTokens.COLOR_TEXT
import io.signallq.app.ui.share.CardDesignTokens.PREVIEW_H
import io.signallq.app.ui.share.CardDesignTokens.PREVIEW_W
import io.signallq.app.ui.share.CardDesignTokens.STORY_H
import io.signallq.app.ui.share.CardDesignTokens.STORY_W
import io.signallq.app.ui.share.CardDesignTokens.TAG_BAD_BG
import io.signallq.app.ui.share.CardDesignTokens.TAG_BAD_COLOR
import io.signallq.app.ui.share.CardDesignTokens.TAG_NEUTRAL_BG
import io.signallq.app.ui.share.CardDesignTokens.TAG_NEUTRAL_COLOR
import io.signallq.app.ui.share.CardDesignTokens.TAG_OK_BG
import io.signallq.app.ui.share.CardDesignTokens.TAG_OK_COLOR
import io.signallq.app.ui.share.CardDesignTokens.TAG_WARN_BG
import io.signallq.app.ui.share.CardDesignTokens.TAG_WARN_COLOR
import io.signallq.app.ui.share.CardDesignTokens.wrapLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class GameShareData(
    val gameId: String,
    val gameName: String,
    val pingMs: Int?,
    val jitterMs: Int?,
    val bufferbloatMs: Int?,
    val status: DiagnosticStatus,
    val customTitle: String? = null,
)

/**
 * Gerador nativo dos Cards de Compartilhamento do Teste Gamer SignallQ.
 * Renderiza bitmaps 1200x630 e 1080x1920 com capas reais de jogos, veredito estilizado,
 * métricas de latência/jitter/bufferbloat e fonte oficial Google Sans Flex.
 */
object GameCardImageGenerator {
    fun extrairMetricasGamer(evidencias: List<EvidenciaDiagnostico>): Triple<Int?, Int?, Int?> {
        var ping: Int? = null
        var jitter: Int? = null
        var bloat: Int? = null

        for (e in evidencias) {
            val digits = "\\d+".toRegex().find(e.valorExibido)?.value?.toIntOrNull() ?: continue
            val label = e.label.lowercase()
            when {
                label.contains("variação") || label.contains("jitter") -> {
                    jitter = digits
                }
                label.contains("sobrecarga") || label.contains("bufferbloat") || label.contains("lentidão com a rede ocupada") -> {
                    bloat = digits
                }
                label.contains("tempo de resposta") || label.contains("latência") || label.contains("ping") -> {
                    if (ping == null || label.contains("medido agora")) {
                        ping = digits
                    }
                }
            }
        }
        return Triple(ping, jitter, bloat)
    }

    suspend fun gerarECompartilhar(
        context: Context,
        data: GameShareData,
        format: CardShareFormat,
    ) = withContext(Dispatchers.IO) {
        val bitmap = renderizar(context, data, format)
        val shareDir = context.cacheDir.resolve("share").also { it.mkdirs() }
        val prefix = if (format == CardShareFormat.FEED) "signallq_gamer_feed" else "signallq_gamer_story"
        val file = File(shareDir, "${prefix}_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val partesMetricas = mutableListOf<String>()
        if (data.pingMs != null) partesMetricas.add("resposta ${data.pingMs} ms")
        if (data.jitterMs != null) partesMetricas.add("jitter ${data.jitterMs} ms")
        if (data.bufferbloatMs != null) partesMetricas.add("sob carga +${data.bufferbloatMs} ms")
        val sufixoMetricas = if (partesMetricas.isNotEmpty()) ": " + partesMetricas.joinToString(", ") else ""

        val textoCompartilhamento =
            "Testei minha conexão para jogar ${data.gameName} no SignallQ$sufixoMetricas" +
                ". Teste a sua também: ${CardDesignTokens.PLAY_STORE_URL}"

        withContext(Dispatchers.Main) {
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, textoCompartilhamento)

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(
                Intent.createChooser(intent, "Compartilhar resultado do jogo").also {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun renderizar(
        context: Context,
        data: GameShareData,
        format: CardShareFormat,
    ): Bitmap =
        when (format) {
            CardShareFormat.FEED -> renderizarFeed(context, data)
            CardShareFormat.STORY -> renderizarStory(context, data)
        }

    private fun carregarFontes(context: Context): Triple<Typeface, Typeface, Typeface> {
        val tfSemibold = ResourcesCompat.getFont(context, R.font.google_sans_flex_semibold) ?: Typeface.DEFAULT_BOLD
        val tfMedium = ResourcesCompat.getFont(context, R.font.google_sans_flex_medium) ?: Typeface.DEFAULT
        val tfMono = Typeface.MONOSPACE
        return Triple(tfSemibold, tfMedium, tfMono)
    }

    fun resolverVeredito(status: DiagnosticStatus): Triple<String, Int, Int> =
        when (status) {
            DiagnosticStatus.ok, DiagnosticStatus.info ->
                Triple("ESTÁVEL", TAG_OK_COLOR, TAG_OK_BG)
            DiagnosticStatus.attention ->
                Triple("ATENÇÃO", TAG_WARN_COLOR, TAG_WARN_BG)
            DiagnosticStatus.inconclusive ->
                Triple("INCONCLUSIVO", TAG_NEUTRAL_COLOR, TAG_NEUTRAL_BG)
            DiagnosticStatus.critical ->
                Triple("INSTÁVEL", TAG_BAD_COLOR, TAG_BAD_BG)
        }

    fun resolverTitulo(
        status: DiagnosticStatus,
        gameName: String,
        custom: String?,
    ): String {
        if (!custom.isNullOrBlank()) return custom
        return when (status) {
            DiagnosticStatus.ok, DiagnosticStatus.info -> "Boa para jogar."
            DiagnosticStatus.attention -> "Dá para jogar, com atrasos ocasionais."
            DiagnosticStatus.inconclusive -> "Sem dados suficientes para avaliar $gameName."
            DiagnosticStatus.critical -> "Instável para $gameName."
        }
    }

    fun resolverCapaJogo(
        context: Context,
        gameId: String,
    ): Bitmap? {
        val resId =
            when (gameId.lowercase().replace("-", "_").trim()) {
                "fortnite" -> R.drawable.game_art_fortnite
                "warzone", "cod_warzone" -> R.drawable.game_art_warzone
                "codm", "call_of_duty_mobile" -> R.drawable.game_art_codm
                "apex", "apex_legends" -> R.drawable.game_art_apex_legends
                "rocket", "rocket_league" -> R.drawable.game_art_rocket_league
                "overwatch", "overwatch_2" -> R.drawable.game_art_overwatch
                "rainbowsix", "rainbow_six_siege" -> R.drawable.game_art_rainbow_six_siege
                "ea_fc", "fc", "eafc" -> R.drawable.game_art_ea_sports_fc
                "marvel_rivals" -> R.drawable.game_art_marvel_rivals
                "the_finals" -> R.drawable.game_art_the_finals
                "destiny_2" -> R.drawable.game_art_destiny_2
                "valorant" -> R.drawable.game_art_valorant
                "league_of_legends", "lol" -> R.drawable.game_art_league_of_legends
                "counter_strike_2", "cs2" -> R.drawable.game_art_counter_strike_2
                "dota_2", "dota2" -> R.drawable.game_art_dota_2
                "freefire", "free_fire" -> R.drawable.game_art_freefire
                "roblox" -> R.drawable.game_art_roblox
                "minecraft" -> R.drawable.game_art_minecraft
                "dead_by_daylight" -> R.drawable.game_art_dead_by_daylight
                "pubg", "pubg_battlegrounds", "pubg_mobile" -> R.drawable.game_art_pubg_battlegrounds
                else -> null
            }
        return if (resId != null) {
            try {
                BitmapFactory.decodeResource(context.resources, resId)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun desenharCapa(
        canvas: Canvas,
        cover: Bitmap?,
        targetW: Float,
        targetH: Float,
    ) {
        if (cover == null) {
            canvas.drawColor(0xFF1A0B2E.toInt())
            return
        }
        val scale = max(targetW / cover.width.toFloat(), targetH / cover.height.toFloat())
        val dw = cover.width * scale
        val dh = cover.height * scale
        val dx = (targetW - dw) / 2f
        val dy = (targetH - dh) / 2f

        val dst = RectF(dx, dy, dx + dw, dy + dh)
        canvas.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * Template 1a: Link preview Gamer (1200x630).
     */
    private fun renderizarFeed(
        context: Context,
        data: GameShareData,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_W, PREVIEW_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (tfSemibold, tfMedium, tfMono) = carregarFontes(context)

        canvas.drawColor(COLOR_BG)

        val cover = resolverCapaJogo(context, data.gameId)
        desenharCapa(canvas, cover, PREVIEW_W.toFloat(), PREVIEW_H.toFloat())

        // Gradiente horizontal
        val gradPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        PREVIEW_W.toFloat(),
                        0f,
                        intArrayOf(
                            0x260B0B10.toInt(),
                            0x590B0B10.toInt(),
                            0xE60B0B10.toInt(),
                            0xF50B0B10.toInt(),
                        ),
                        floatArrayOf(0f, 0.38f, 0.58f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, PREVIEW_W.toFloat(), PREVIEW_H.toFloat(), gradPaint)

        val (tagLabel, tagColor, tagBg) = resolverVeredito(data.status)
        val title = resolverTitulo(data.status, data.gameName, data.customTitle)

        // Selo do veredito no canto inferior esquerdo (56, 514)
        val tagBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tagBg
                style = Paint.Style.FILL
            }
        canvas.drawRoundRect(RectF(56f, 514f, 56f + 60f, 514f + 60f), 18f, 18f, tagBgPaint)
        desenharIconeVeredito(canvas, 56f + 10f, 514f + 10f, 1.7f, data.status, tagColor)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.apply {
            color = tagColor
            typeface = tfMono
            textSize = 14f
            textAlign = Paint.Align.LEFT
            letterSpacing = 2f / 14f
        }
        canvas.drawText(tagLabel, 130f, 542f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 20f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText(data.gameName, 130f, 564f, textPaint)

        // Lado direito: 620px
        val lockup =
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.signallq_lockup_dark)
            } catch (_: Exception) {
                null
            }
        val badge =
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.google_play_badge)
            } catch (_: Exception) {
                null
            }

        if (lockup != null) {
            val dst = Rect(620, 64, 620 + 158, 64 + 44)
            canvas.drawBitmap(lockup, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMono
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0f
        }
        canvas.drawText("signallq.com", 1144f, 96f, textPaint)

        textPaint.apply {
            color = COLOR_GAME_ACCENT
            typeface = tfMono
            textSize = 14f
            textAlign = Paint.Align.LEFT
            letterSpacing = 2f / 14f
        }
        canvas.drawText("TESTE GAMER · RESULTADO", 620f, 154f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 44f
            textAlign = Paint.Align.LEFT
            letterSpacing = -1.2f / 44f
        }
        canvas.drawText(title, 620f, 204f, textPaint)

        // 3 cards de métricas: RESPOSTA, JITTER, SOB CARGA
        val metricCards =
            listOf(
                Triple(
                    "RESPOSTA",
                    COLOR_GAME_BLUE,
                    Pair(
                        if (data.pingMs != null) "${data.pingMs}" else "—",
                        if (data.pingMs != null) "ms" else "",
                    ),
                ),
                Triple(
                    "JITTER",
                    COLOR_GAME_GREEN,
                    Pair(
                        if (data.jitterMs != null) "${data.jitterMs}" else "—",
                        if (data.jitterMs != null) "ms" else "",
                    ),
                ),
                Triple(
                    "SOB CARGA",
                    COLOR_GAME_AMBER,
                    Pair(
                        if (data.bufferbloatMs != null) "+${data.bufferbloatMs}" else "—",
                        if (data.bufferbloatMs != null) "ms" else "",
                    ),
                ),
            )

        val cardBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD916151D.toInt()
                style = Paint.Style.FILL
            }

        metricCards.forEachIndexed { i, m ->
            val x = 620f + i * (162f + 14f)
            canvas.drawRoundRect(RectF(x, 246f, x + 162f, 246f + 126f), 20f, 20f, cardBgPaint)

            textPaint.apply {
                color = m.second
                typeface = tfMono
                textSize = 13f
                textAlign = Paint.Align.LEFT
                letterSpacing = 1.5f / 13f
            }
            canvas.drawText(m.first, x + 16f, 276f, textPaint)

            textPaint.apply {
                color = COLOR_TEXT
                typeface = tfSemibold
                textSize = 40f
                textAlign = Paint.Align.LEFT
                letterSpacing = 0f
            }
            val valStr = m.third.first
            canvas.drawText(valStr, x + 16f, 332f, textPaint)

            if (m.third.second.isNotEmpty()) {
                textPaint.apply {
                    color = COLOR_MUTED
                    typeface = tfMedium
                    textSize = 16f
                    textAlign = Paint.Align.LEFT
                    letterSpacing = 0f
                }
                canvas.drawText(m.third.second, x + 16f, 354f, textPaint)
            }
        }

        // Rodapé com QR code e badge
        ResultadoCardImageGenerator.desenharQrCode(canvas, 620f, 420f, 112f)

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 24f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O teste mostra o tempo.", 754f, 452f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 24f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O app mostra a rota.", 754f, 484f, textPaint)

        if (badge != null) {
            val dst = Rect(754, 496, 754 + 144, 496 + 56)
            canvas.drawBitmap(badge, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        return bitmap
    }

    /**
     * Template 1b: Vertical Gamer Story (1080x1920).
     */
    private fun renderizarStory(
        context: Context,
        data: GameShareData,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(STORY_W, STORY_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (tfSemibold, tfMedium, tfMono) = carregarFontes(context)

        canvas.drawColor(COLOR_BG)

        val cover = resolverCapaJogo(context, data.gameId)
        desenharCapa(canvas, cover, STORY_W.toFloat(), STORY_H.toFloat())

        // Vinheta vertical escurecendo o fundo
        val gradPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        STORY_H.toFloat(),
                        intArrayOf(
                            0x800B0B10.toInt(),
                            0xBF0B0B10.toInt(),
                            0xF50B0B10.toInt(),
                            0xFF0B0B10.toInt(),
                        ),
                        floatArrayOf(0f, 0.35f, 0.75f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, STORY_W.toFloat(), STORY_H.toFloat(), gradPaint)

        val safeSide = 64f
        val lockup =
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.signallq_lockup_dark)
            } catch (_: Exception) {
                null
            }
        val badge =
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.google_play_badge)
            } catch (_: Exception) {
                null
            }

        if (lockup != null) {
            val dst = Rect(safeSide.toInt(), 240, safeSide.toInt() + 216, 240 + 60)
            canvas.drawBitmap(lockup, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMono
            textSize = 26f
            textAlign = Paint.Align.RIGHT
            letterSpacing = 3f / 26f
        }
        canvas.drawText("TESTE GAMER", STORY_W - safeSide, 280f, textPaint)

        // Pill do Veredito at (64, 440)
        val (tagLabel, tagColor, tagBg) = resolverVeredito(data.status)
        val pillBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tagBg
                style = Paint.Style.FILL
            }
        canvas.drawRoundRect(RectF(safeSide, 440f, safeSide + 320f, 440f + 68f), 34f, 34f, pillBgPaint)
        desenharIconeVeredito(canvas, safeSide + 18f, 440f + 18f, 1.3f, data.status, tagColor)

        textPaint.apply {
            color = tagColor
            typeface = tfMono
            textSize = 24f
            textAlign = Paint.Align.LEFT
            letterSpacing = 2f / 24f
        }
        canvas.drawText(tagLabel, safeSide + 62f, 484f, textPaint)

        // Jogo e Título
        textPaint.apply {
            color = COLOR_GAME_MUTED
            typeface = tfMedium
            textSize = 48f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText(data.gameName, safeSide, 560f, textPaint)

        val title = resolverTitulo(data.status, data.gameName, data.customTitle)
        val titleLines = wrapLines(title, 22)
        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 76f
            textAlign = Paint.Align.LEFT
            letterSpacing = -2f / 76f
        }
        titleLines.forEachIndexed { i, line ->
            canvas.drawText(line, safeSide, 640f + i * 86f, textPaint)
        }

        // 3 métricas em cards verticais at y=960, h=200
        val cardW = (STORY_W - safeSide * 2 - 2 * 20f) / 3f
        val metricCards =
            listOf(
                Triple(
                    "RESPOSTA",
                    COLOR_GAME_BLUE,
                    Pair(
                        if (data.pingMs != null) "${data.pingMs}" else "—",
                        if (data.pingMs != null) "ms" else "",
                    ),
                ),
                Triple(
                    "JITTER",
                    COLOR_GAME_GREEN,
                    Pair(
                        if (data.jitterMs != null) "${data.jitterMs}" else "—",
                        if (data.jitterMs != null) "ms" else "",
                    ),
                ),
                Triple(
                    "SOB CARGA",
                    COLOR_GAME_AMBER,
                    Pair(
                        if (data.bufferbloatMs != null) "+${data.bufferbloatMs}" else "—",
                        if (data.bufferbloatMs != null) "ms" else "",
                    ),
                ),
            )

        val cardBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xEB16151D.toInt()
                style = Paint.Style.FILL
            }

        metricCards.forEachIndexed { i, m ->
            val x = safeSide + i * (cardW + 20f)
            canvas.drawRoundRect(RectF(x, 960f, x + cardW, 960f + 200f), 28f, 28f, cardBgPaint)

            textPaint.apply {
                color = m.second
                typeface = tfMono
                textSize = 20f
                textAlign = Paint.Align.LEFT
                letterSpacing = 2f / 20f
            }
            canvas.drawText(m.first, x + 24f, 1008f, textPaint)

            textPaint.apply {
                color = COLOR_TEXT
                typeface = tfSemibold
                textSize = 72f
                textAlign = Paint.Align.LEFT
                letterSpacing = 0f
            }
            val valStr = m.third.first
            canvas.drawText(valStr, x + 24f, 1090f, textPaint)
            val valW = textPaint.measureText(valStr)

            if (m.third.second.isNotEmpty()) {
                textPaint.apply {
                    color = COLOR_MUTED
                    typeface = tfMedium
                    textSize = 26f
                    textAlign = Paint.Align.LEFT
                    letterSpacing = 0f
                }
                canvas.drawText(m.third.second, x + 24f + valW + 8f, 1090f, textPaint)
            }
        }

        // Bloco inferior com o selo do Google Play
        if (badge != null) {
            val dst = Rect(safeSide.toInt(), 1440, safeSide.toInt() + 247, 1440 + 96)
            canvas.drawBitmap(badge, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 34f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O teste mostra o tempo.", 340f, 1478f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 34f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O app mostra a rota.", 340f, 1522f, textPaint)

        return bitmap
    }

    private fun desenharIconeVeredito(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float,
        status: DiagnosticStatus,
        color: Int,
    ) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = 2.4f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)

        when (status) {
            DiagnosticStatus.ok, DiagnosticStatus.info -> {
                val path =
                    Path().apply {
                        moveTo(5f, 12.5f)
                        lineTo(9.5f, 17f)
                        lineTo(19f, 7.5f)
                    }
                canvas.drawPath(path, paint)
            }
            DiagnosticStatus.attention -> {
                canvas.drawLine(12f, 6f, 12f, 13f, paint)
                val dotPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        style = Paint.Style.FILL
                    }
                canvas.drawCircle(12f, 17.5f, 1.2f, dotPaint)
            }
            DiagnosticStatus.inconclusive -> {
                canvas.drawLine(7f, 12f, 17f, 12f, paint)
            }
            DiagnosticStatus.critical -> {
                canvas.drawLine(6.5f, 6.5f, 17.5f, 17.5f, paint)
                canvas.drawLine(17.5f, 6.5f, 6.5f, 17.5f, paint)
            }
        }
        canvas.restore()
    }
}
