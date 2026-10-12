package io.signallq.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.signallq.app.R
import io.signallq.app.ui.share.CardDesignTokens.COLOR_BG
import io.signallq.app.ui.share.CardDesignTokens.COLOR_DOWNLOAD
import io.signallq.app.ui.share.CardDesignTokens.COLOR_LATENCY
import io.signallq.app.ui.share.CardDesignTokens.COLOR_MUTED
import io.signallq.app.ui.share.CardDesignTokens.COLOR_PANEL
import io.signallq.app.ui.share.CardDesignTokens.COLOR_TEXT
import io.signallq.app.ui.share.CardDesignTokens.COLOR_UPLOAD
import io.signallq.app.ui.share.CardDesignTokens.PREVIEW_H
import io.signallq.app.ui.share.CardDesignTokens.PREVIEW_W
import io.signallq.app.ui.share.CardDesignTokens.STORY_H
import io.signallq.app.ui.share.CardDesignTokens.STORY_W
import io.signallq.app.ui.share.CardDesignTokens.cardHeadline
import io.signallq.app.ui.share.CardDesignTokens.cardNumber
import io.signallq.app.ui.share.CardDesignTokens.cardTicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class CardShareFormat(
    val label: String,
    val subtitulo: String,
) {
    FEED(
        label = "Feed e Redes (1200×630)",
        subtitulo = "Ideal para WhatsApp, Telegram e Twitter/X",
    ),
    STORY(
        label = "Stories e Status (1080×1920)",
        subtitulo = "Formato vertical 9:16 para Instagram e TikTok",
    ),
}

data class SpeedtestShareData(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Int,
)

/**
 * Gerador nativo dos Cards de Compartilhamento do Teste de Velocidade SignallQ.
 * Renderiza bitmaps 1200x630 e 1080x1920 com fidelidade visual idêntica aos
 * protótipos de design oficiais, usando a fonte do Google (Google Sans Flex) e
 * despachando via Intent nativa com image/png.
 */
object ResultadoCardImageGenerator {
    suspend fun gerarECompartilhar(
        context: Context,
        data: SpeedtestShareData,
        format: CardShareFormat,
    ) = withContext(Dispatchers.IO) {
        val bitmap = renderizar(context, data, format)
        val shareDir = context.cacheDir.resolve("share").also { it.mkdirs() }
        val prefix = if (format == CardShareFormat.FEED) "signallq_feed" else "signallq_story"
        val file = File(shareDir, "${prefix}_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val textoCompartilhamento =
            "Meu teste de velocidade no SignallQ: ${cardNumber(data.downloadMbps)} Mbps de download, " +
                "${cardNumber(data.uploadMbps)} Mbps de upload e ${data.pingMs} ms de latência. " +
                "Teste sua rede no app: ${CardDesignTokens.PLAY_STORE_URL}"

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
                Intent.createChooser(intent, "Compartilhar card de resultado").also {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    fun renderizar(
        context: Context,
        data: SpeedtestShareData,
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

    /**
     * Template 1a: Link preview / Feed (1200x630).
     */
    private fun renderizarFeed(
        context: Context,
        data: SpeedtestShareData,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_W, PREVIEW_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (tfSemibold, tfMedium, tfMono) = carregarFontes(context)

        // Fundo escuro fixo
        canvas.drawColor(COLOR_BG)

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

        // Anel à esquerda: escala 470/440 a partir de (50, 80)
        canvas.save()
        canvas.translate(50f, 80f)
        val scaleRing = 470f / 440f
        canvas.scale(scaleRing, scaleRing)

        val tickPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeCap = Paint.Cap.ROUND
            }
        for (k in cardTicks(data.downloadMbps)) {
            tickPaint.color = k.color
            tickPaint.alpha = (k.opacity * 255).roundToInt().coerceIn(0, 255)
            tickPaint.strokeWidth = k.width
            canvas.drawLine(k.x1, k.y1, k.x2, k.y2, tickPaint)
        }
        canvas.restore()

        // Centro do anel (50 + 470/2 = 285)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // DOWNLOAD label
        textPaint.apply {
            color = COLOR_DOWNLOAD
            typeface = tfMono
            textSize = 20f
            textAlign = Paint.Align.CENTER
            letterSpacing = 3f / 20f
        }
        canvas.drawText("DOWNLOAD", 285f, 235f, textPaint)

        // Valor do download
        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 170f
            textAlign = Paint.Align.CENTER
            letterSpacing = -7f / 170f
        }
        canvas.drawText(cardNumber(data.downloadMbps), 285f, 370f, textPaint)

        // Unidade "Mbps"
        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 30f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0f
        }
        canvas.drawText("Mbps", 285f, 415f, textPaint)

        // Lado direito: left 600
        if (lockup != null) {
            val dst = Rect(600, 70, 600 + 158, 70 + 44)
            canvas.drawBitmap(lockup, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // signallq.com à direita
        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMono
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0f
        }
        canvas.drawText("signallq.com", 1136f, 102f, textPaint)

        // Headline
        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 68f
            textAlign = Paint.Align.LEFT
            letterSpacing = -2f / 68f
        }
        canvas.drawText(cardHeadline(data.downloadMbps), 600f, 196f, textPaint)

        // 2 Painéis de métricas: UPLOAD e PING
        val panelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PANEL
                style = Paint.Style.FILL
            }

        // UPLOAD panel
        canvas.drawRoundRect(RectF(600f, 228f, 600f + 260f, 228f + 146f), 24f, 24f, panelPaint)
        textPaint.apply {
            color = COLOR_UPLOAD
            typeface = tfMono
            textSize = 18f
            textAlign = Paint.Align.LEFT
            letterSpacing = 2f / 18f
        }
        canvas.drawText("UPLOAD", 624f, 268f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 60f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        val uploadStr = cardNumber(data.uploadMbps)
        canvas.drawText(uploadStr, 624f, 338f, textPaint)
        val uploadW = textPaint.measureText(uploadStr)

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 24f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("Mbps", 624f + uploadW + 8f, 338f, textPaint)

        // PING panel
        canvas.drawRoundRect(RectF(876f, 228f, 876f + 260f, 228f + 146f), 24f, 24f, panelPaint)
        textPaint.apply {
            color = COLOR_LATENCY
            typeface = tfMono
            textSize = 18f
            textAlign = Paint.Align.LEFT
            letterSpacing = 2f / 18f
        }
        canvas.drawText("PING", 900f, 268f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 60f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        val pingStr = data.pingMs.toString()
        canvas.drawText(pingStr, 900f, 338f, textPaint)
        val pingW = textPaint.measureText(pingStr)

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 24f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("ms", 900f + pingW + 8f, 338f, textPaint)

        // Linha inferior: QR code + Textos + Google Play badge
        desenharQrCode(canvas, 600f, 408f, 124f)

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 25f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O teste mostra a velocidade.", 746f, 440f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 25f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O app mostra o motivo.", 746f, 474f, textPaint)

        if (badge != null) {
            val dst = Rect(746, 486, 746 + 150, 486 + 58)
            canvas.drawBitmap(badge, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        return bitmap
    }

    /**
     * Template 1b: Vertical / Story (1080x1920).
     */
    private fun renderizarStory(
        context: Context,
        data: SpeedtestShareData,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(STORY_W, STORY_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (tfSemibold, tfMedium, tfMono) = carregarFontes(context)

        canvas.drawColor(COLOR_BG)

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

        val safeSide = 64f

        if (lockup != null) {
            val dst = Rect(safeSide.toInt(), 240, safeSide.toInt() + 216, 240 + 60)
            canvas.drawBitmap(lockup, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Anel (viewBox 440 -> 760 px at (160, 360))
        canvas.save()
        canvas.translate(160f, 360f)
        val scaleRing = 760f / 440f
        canvas.scale(scaleRing, scaleRing)

        val tickPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeCap = Paint.Cap.ROUND
            }
        for (k in cardTicks(data.downloadMbps)) {
            tickPaint.color = k.color
            tickPaint.alpha = (k.opacity * 255).roundToInt().coerceIn(0, 255)
            tickPaint.strokeWidth = k.width
            canvas.drawLine(k.x1, k.y1, k.x2, k.y2, tickPaint)
        }
        canvas.restore()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // DOWNLOAD label
        textPaint.apply {
            color = COLOR_DOWNLOAD
            typeface = tfMono
            textSize = 28f
            textAlign = Paint.Align.CENTER
            letterSpacing = 4f / 28f
        }
        canvas.drawText("DOWNLOAD", 540f, 600f, textPaint)

        // Download value
        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 270f
            textAlign = Paint.Align.CENTER
            letterSpacing = -11f / 270f
        }
        canvas.drawText(cardNumber(data.downloadMbps), 540f, 830f, textPaint)

        // Unidade Mbps
        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 44f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0f
        }
        canvas.drawText("Mbps", 540f, 905f, textPaint)

        // 2 Painéis de métricas verticais at y=1140, w=460, h=170, r=32
        val panelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PANEL
                style = Paint.Style.FILL
            }

        // Panel UPLOAD at x=64
        desenharPainelStory(
            canvas = canvas,
            x = safeSide,
            y = 1140f,
            w = 460f,
            h = 170f,
            label = "UPLOAD",
            labelColor = COLOR_UPLOAD,
            value = cardNumber(data.uploadMbps),
            unit = "Mbps",
            tfSemibold = tfSemibold,
            tfMedium = tfMedium,
            tfMono = tfMono,
        )

        // Panel LATÊNCIA at x=556
        desenharPainelStory(
            canvas = canvas,
            x = 556f,
            y = 1140f,
            w = 460f,
            h = 170f,
            label = "LATÊNCIA",
            labelColor = COLOR_LATENCY,
            value = data.pingMs.toString(),
            unit = "ms",
            tfSemibold = tfSemibold,
            tfMedium = tfMedium,
            tfMono = tfMono,
        )

        // Headline at (64, 1400)
        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 80f
            textAlign = Paint.Align.LEFT
            letterSpacing = -3f / 80f
        }
        canvas.drawText(cardHeadline(data.downloadMbps), safeSide, 1400f, textPaint)

        // Badge + Textos no rodapé
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
        canvas.drawText("O teste mostra a velocidade.", 340f, 1478f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 34f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText("O app mostra o motivo.", 340f, 1522f, textPaint)

        return bitmap
    }

    private fun desenharPainelStory(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        labelColor: Int,
        value: String,
        unit: String,
        tfSemibold: Typeface,
        tfMedium: Typeface,
        tfMono: Typeface,
    ) {
        val panelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_PANEL
                style = Paint.Style.FILL
            }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 32f, 32f, panelPaint)

        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor
                typeface = tfMono
                textSize = 26f
                textAlign = Paint.Align.LEFT
                letterSpacing = 3f / 26f
            }
        canvas.drawText(label, x + 36f, y + 52f, textPaint)

        textPaint.apply {
            color = COLOR_TEXT
            typeface = tfSemibold
            textSize = 84f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText(value, x + 36f, y + 140f, textPaint)
        val valW = textPaint.measureText(value)

        textPaint.apply {
            color = COLOR_MUTED
            typeface = tfMedium
            textSize = 32f
            textAlign = Paint.Align.LEFT
            letterSpacing = 0f
        }
        canvas.drawText(unit, x + 36f + valW + 10f, y + 140f, textPaint)
    }

    /**
     * Renderiza o container e os módulos do QR Code Play Store.
     */
    fun desenharQrCode(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
    ) {
        val bgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
        canvas.drawRoundRect(RectF(x, y, x + size, y + size), 16f, 16f, bgPaint)

        val qrPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1C1B1F.toInt()
                style = Paint.Style.FILL
            }

        val rows = CardDesignTokens.PLAY_STORE_QR_ROWS
        val moduleCount = rows.size // 33
        val totalModules = moduleCount + 8 // 4 de margem em cada lado = 41
        val moduleSize = size / totalModules

        for (rowIdx in 0 until moduleCount) {
            val rowStr = rows[rowIdx]
            for (colIdx in 0 until moduleCount) {
                if (rowStr[colIdx] == '1') {
                    val left = x + (colIdx + 4) * moduleSize
                    val top = y + (rowIdx + 4) * moduleSize
                    canvas.drawRect(left, top, left + moduleSize, top + moduleSize, qrPaint)
                }
            }
        }
    }
}
