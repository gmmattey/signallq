package io.signallq.app.feature.history

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.relatorio.exportarHtmlComoPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// v2.0 — Layout rico: HTML/CSS embutido + paginação automática via WebView ou PdfDocument multi-página.
// Assinatura original mantida para compatibilidade com testes JVM existentes.
// Overload com Context usa WebView.createPrintDocumentAdapter() para layout rico completo.

private const val PAGINA_LARGURA_PX = 595 // A4 em pontos (72dpi)
private const val PAGINA_ALTURA_PX = 842 // A4 em pontos (72dpi)
private const val MARGEM = 40f
private const val ALTURA_LINHA = 16f
private const val LINHAS_CABECALHO = 5 // título + subtítulo + data + header tabela + separador
private const val LINHAS_UTEIS_POR_PAGINA = 44 // ~(842 - 2*40) / 16

/**
 * Exporta histórico de medições para PDF.
 *
 * Versão 2.0:
 *  - [exportar] (sem Context): PdfDocument multi-página com layout melhorado — compatível com JVM tests.
 *  - [exportarComWebView] (com Context): gera o HTML do relatório e delega a paginação ao motor
 *    generico [io.signallq.app.core.relatorio.exportarHtmlComoPdf] (:core:relatorio, extraído na
 *    issue #1157 Fase 1b — o motor não conhece MedicaoEntity, só recebe HTML pronto).
 *  - [gerarHtml]: função interna pura e testável — gera o template HTML/CSS especifico do
 *    consumidor. NAO migra para o core — o Pro gera o proprio HTML de laudo tecnico.
 */
class ExportadorHistoricoPDF {
    private val formatadorDataHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val formatadorDataRelatorio = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())

    // ─── Painters ─────────────────────────────────────────────────────────────

    private val paintTitulo =
        Paint().apply {
            textSize = 20f
            isFakeBoldText = true
            color = Color.parseColor("#1565C0")
        }
    private val paintSubtitulo =
        Paint().apply {
            textSize = 10f
            color = Color.parseColor("#757575")
        }
    private val paintCabecalhoTabela =
        Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.WHITE
        }
    private val paintCabecalhoFundo =
        Paint().apply {
            color = Color.parseColor("#1565C0")
        }
    private val paintTextoDado =
        Paint().apply {
            textSize = 9f
            color = Color.parseColor("#212121")
        }
    private val paintFundoAlternado =
        Paint().apply {
            color = Color.parseColor("#F5F5F5")
        }
    private val paintLinhaSeparador =
        Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.5f
        }
    private val paintRodape =
        Paint().apply {
            textSize = 8f
            color = Color.parseColor("#9E9E9E")
        }
    private val paintPaginacao =
        Paint().apply {
            textSize = 8f
            color = Color.parseColor("#757575")
            textAlign = Paint.Align.RIGHT
        }

    // ─── Colunas da tabela ────────────────────────────────────────────────────

    private val colDataX = MARGEM
    private val colDlX = MARGEM + 140f
    private val colUlX = MARGEM + 215f
    private val colLatX = MARGEM + 290f
    private val colJitterX = MARGEM + 355f
    private val colFonteX = MARGEM + 415f
    private val larguraColunas = PAGINA_LARGURA_PX - MARGEM * 2

    // ─── API pública sem Context (compatível com testes JVM) ──────────────────

    /**
     * Exporta usando [PdfDocument] multi-página com layout tabular melhorado.
     * Compatível com testes JVM (não usa WebView nem Context Android de UI).
     */
    suspend fun exportar(
        medicoes: List<MedicaoEntity>,
        arquivo: File,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val pdf = PdfDocument()
            try {
                val chunks = medicoes.chunked(LINHAS_UTEIS_POR_PAGINA)
                val totalPaginas = maxOf(1, chunks.size)

                if (chunks.isEmpty()) {
                    // PDF vazio mas válido — ao menos uma página
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGINA_LARGURA_PX, PAGINA_ALTURA_PX, 1).create()
                    val page = pdf.startPage(pageInfo)
                    desenharCabecalho(page.canvas, 1, totalPaginas, medicoes.size)
                    desenharRodape(page.canvas, medicoes.size)
                    pdf.finishPage(page)
                } else {
                    chunks.forEachIndexed { idx, chunk ->
                        val numeroPagina = idx + 1
                        val pageInfo = PdfDocument.PageInfo.Builder(PAGINA_LARGURA_PX, PAGINA_ALTURA_PX, numeroPagina).create()
                        val page = pdf.startPage(pageInfo)
                        val canvas = page.canvas

                        var y = desenharCabecalho(canvas, numeroPagina, totalPaginas, medicoes.size)
                        y = desenharHeaderTabela(canvas, y)
                        chunk.forEachIndexed { linhaIdx, medicao ->
                            y = desenharLinhaTabela(canvas, medicao, linhaIdx, y)
                        }
                        desenharRodape(canvas, medicoes.size)
                        pdf.finishPage(page)
                    }
                }

                FileOutputStream(arquivo).use { stream ->
                    pdf.writeTo(stream)
                }
                true
            } catch (e: Exception) {
                false
            } finally {
                pdf.close()
            }
        }

    // ─── API com Context — WebView rich HTML (overload v2.0) ──────────────────

    /**
     * Exporta usando WebView.createPrintDocumentAdapter() para layout HTML/CSS rico.
     * Requer Context Android (UI thread para criar WebView).
     * Paginação automática gerenciada pelo motor generico [exportarHtmlComoPdf].
     */
    suspend fun exportarComWebView(
        medicoes: List<MedicaoEntity>,
        arquivo: File,
        context: Context,
    ): Boolean = exportarHtmlComoPdf(html = gerarHtml(medicoes), arquivo = arquivo, context = context)

    // ─── Geração de HTML (pura — testável em JVM) ─────────────────────────────

    /**
     * Gera o HTML completo com CSS embutido para o relatório de medições.
     * Função pura — sem dependências Android, testável em JVM.
     */
    internal fun gerarHtml(medicoes: List<MedicaoEntity>): String {
        val dataGeracao = formatadorDataRelatorio.format(Date())
        val linhasHtml =
            medicoes.joinToString("\n") { medicao ->
                val dataHora = formatadorDataHora.format(Date(medicao.timestampEpochMs))
                val dl = medicao.downloadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
                val ul = medicao.uploadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
                val lat = medicao.latencyMs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                val fonte = medicao.fonte?.let { escapeHtml(it) } ?: "—"
                """        <tr>
          <td>$dataHora</td>
          <td class="num">$dl Mbps</td>
          <td class="num">$ul Mbps</td>
          <td class="num">$lat ms</td>
          <td>$fonte</td>
        </tr>"""
            }

        val totalMedicoes = medicoes.size
        val dlMedia =
            medicoes.mapNotNull { it.downloadMbps }.let { vals ->
                if (vals.isEmpty()) "—" else String.format(Locale.US, "%.1f Mbps", vals.average())
            }
        val ulMedia =
            medicoes.mapNotNull { it.uploadMbps }.let { vals ->
                if (vals.isEmpty()) "—" else String.format(Locale.US, "%.1f Mbps", vals.average())
            }
        val latMedia =
            medicoes.mapNotNull { it.latencyMs }.let { vals ->
                if (vals.isEmpty()) "—" else String.format(Locale.US, "%.0f ms", vals.average())
            }

        return """<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Relatório SignallQ</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      font-size: 12px;
      line-height: 1.5;
      color: #1C1B1F;
      margin: 24px;
      background: #FFFFFF;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      border-bottom: 2px solid #F3EEFA;
      padding-bottom: 14px;
      margin-bottom: 16px;
    }
    .header-brand {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .brand-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .brand-logo-svg {
      width: 28px;
      height: 28px;
    }
    .brand-name {
      font-size: 20px;
      font-weight: 800;
      color: #1C1B1F;
      letter-spacing: -0.5px;
    }
    .badge-relatorio {
      display: inline-block;
      background: #EAE0FF;
      color: #210A5C;
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 3px 10px;
      border-radius: 9999px;
      width: fit-content;
    }
    .header-meta {
      text-align: right;
      font-size: 11px;
      color: #49454F;
      line-height: 1.6;
    }
    .header-meta strong {
      color: #1C1B1F;
    }

    .resumo {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-bottom: 18px;
    }
    .resumo-card {
      background: #FAF8FC;
      border: 1px solid #E7E0EC;
      border-radius: 8px;
      padding: 10px 14px;
    }
    .resumo-card .label {
      font-size: 10px;
      color: #5B21D6;
      text-transform: uppercase;
      font-weight: 700;
      letter-spacing: 0.3px;
    }
    .resumo-card .valor {
      font-size: 16px;
      font-weight: 800;
      color: #1C1B1F;
      margin-top: 2px;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 11px;
      border: 1px solid #E7E0EC;
      border-radius: 8px;
      overflow: hidden;
    }
    thead tr {
      background: #5B21D6;
      color: #FFFFFF;
    }
    thead th {
      padding: 8px 10px;
      text-align: left;
      font-weight: 700;
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 0.4px;
    }
    thead th.num { text-align: right; }
    tbody tr:nth-child(even) { background: #FAF8FC; }
    tbody td {
      padding: 7px 10px;
      border-bottom: 1px solid #E7E0EC;
      color: #1C1B1F;
    }
    tbody td.num {
      text-align: right;
      font-weight: 600;
    }

    .footer {
      margin-top: 20px;
      font-size: 9px;
      color: #79747E;
      text-align: center;
      border-top: 1px solid #E7E0EC;
      padding-top: 8px;
    }
    .empty {
      text-align: center;
      padding: 32px;
      color: #79747E;
      font-size: 13px;
    }

    @media print {
      body { margin: 0; }
      thead { display: table-header-group; }
      tbody tr { page-break-inside: avoid; }
    }
  </style>
</head>
<body>
  <div class="header">
    <div class="header-brand">
      <div class="brand-row">
        <svg class="brand-logo-svg" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="32" height="32" rx="8" fill="#5B21D6"/>
          <path d="M16 6C10.477 6 6 10.477 6 16M16 10C12.686 10 10 12.686 10 16M16 14C14.895 14 14 14.895 14 16" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
          <circle cx="16" cy="22" r="2.5" fill="white"/>
        </svg>
        <span class="brand-name">SignallQ</span>
      </div>
      <span class="badge-relatorio">Histórico de Medições</span>
    </div>
    <div class="header-meta">
      <p>Gerado em: <strong>$dataGeracao</strong></p>
      <p>Total de registros: <strong>$totalMedicoes</strong></p>
    </div>
  </div>

  <div class="resumo">
    <div class="resumo-card">
      <div class="label">Download médio</div>
      <div class="valor">$dlMedia</div>
    </div>
    <div class="resumo-card">
      <div class="label">Upload médio</div>
      <div class="valor">$ulMedia</div>
    </div>
    <div class="resumo-card">
      <div class="label">Latência média</div>
      <div class="valor">$latMedia</div>
    </div>
  </div>

  <table>
    <thead>
      <tr>
        <th>Data/Hora</th>
        <th class="num">Download</th>
        <th class="num">Upload</th>
        <th class="num">Latência</th>
        <th>Fonte</th>
      </tr>
    </thead>
    <tbody>
${if (medicoes.isEmpty()) "      <tr><td colspan=\"5\" class=\"empty\">Nenhuma medição registrada.</td></tr>" else linhasHtml}
    </tbody>
  </table>

  <div class="footer">
    SignallQ — monitoramento inteligente de rede &nbsp;|&nbsp; Relatório gerado automaticamente &nbsp;|&nbsp; © 2026 SignallQ
  </div>
</body>
</html>"""
    }

    // ─── Desenho interno (PdfDocument multi-página) ───────────────────────────

    private fun desenharCabecalho(
        canvas: android.graphics.Canvas,
        paginaAtual: Int,
        totalPaginas: Int,
        totalMedicoes: Int,
    ): Float {
        var y = MARGEM + 24f

        canvas.drawText("Relatório de Medições — SignallQ", MARGEM, y, paintTitulo)
        y += 6f
        canvas.drawLine(MARGEM, y, PAGINA_LARGURA_PX - MARGEM, y, paintCabecalhoFundo.apply { strokeWidth = 1.5f })
        y += 10f

        if (paginaAtual == 1) {
            val dataAtual = formatadorDataRelatorio.format(Date())
            canvas.drawText("Gerado em: $dataAtual  |  Total de registros: $totalMedicoes", MARGEM, y, paintSubtitulo)
            y += 18f
        } else {
            // páginas seguintes: só indicador de continuação
            canvas.drawText("(continuação)", MARGEM, y, paintSubtitulo)
            y += 14f
        }

        // Número da página no canto superior direito
        canvas.drawText(
            "Pág. $paginaAtual / $totalPaginas",
            PAGINA_LARGURA_PX - MARGEM,
            MARGEM + 10f,
            paintPaginacao,
        )

        return y
    }

    private fun desenharHeaderTabela(
        canvas: android.graphics.Canvas,
        yInicial: Float,
    ): Float {
        val alturaHeader = ALTURA_LINHA + 8f
        canvas.drawRect(MARGEM, yInicial, PAGINA_LARGURA_PX - MARGEM, yInicial + alturaHeader, paintCabecalhoFundo)
        val yTexto = yInicial + alturaHeader - 5f
        canvas.drawText("Data/Hora", colDataX + 4f, yTexto, paintCabecalhoTabela)
        canvas.drawText("DL (Mbps)", colDlX, yTexto, paintCabecalhoTabela)
        canvas.drawText("UL (Mbps)", colUlX, yTexto, paintCabecalhoTabela)
        canvas.drawText("Lat (ms)", colLatX, yTexto, paintCabecalhoTabela)
        canvas.drawText("Jitter", colJitterX, yTexto, paintCabecalhoTabela)
        canvas.drawText("Fonte", colFonteX, yTexto, paintCabecalhoTabela)
        return yInicial + alturaHeader
    }

    private fun desenharLinhaTabela(
        canvas: android.graphics.Canvas,
        medicao: MedicaoEntity,
        linhaIdx: Int,
        yInicial: Float,
    ): Float {
        val alturaLinha = ALTURA_LINHA + 4f

        if (linhaIdx % 2 == 1) {
            canvas.drawRect(MARGEM, yInicial, PAGINA_LARGURA_PX - MARGEM, yInicial + alturaLinha, paintFundoAlternado)
        }

        val yTexto = yInicial + alturaLinha - 5f
        val dataHora = formatadorDataHora.format(Date(medicao.timestampEpochMs))
        val dl = medicao.downloadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val ul = medicao.uploadMbps?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val lat = medicao.latencyMs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
        val jitter = medicao.jitterMs?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val fonte = (medicao.fonte ?: "—").take(12)

        canvas.drawText(dataHora, colDataX + 4f, yTexto, paintTextoDado)
        canvas.drawText(dl, colDlX, yTexto, paintTextoDado)
        canvas.drawText(ul, colUlX, yTexto, paintTextoDado)
        canvas.drawText(lat, colLatX, yTexto, paintTextoDado)
        canvas.drawText(jitter, colJitterX, yTexto, paintTextoDado)
        canvas.drawText(fonte, colFonteX, yTexto, paintTextoDado)

        val yLinhaSep = yInicial + alturaLinha
        canvas.drawLine(MARGEM, yLinhaSep, PAGINA_LARGURA_PX - MARGEM, yLinhaSep, paintLinhaSeparador)

        return yLinhaSep
    }

    private fun desenharRodape(
        canvas: android.graphics.Canvas,
        totalMedicoes: Int,
    ) {
        val yRodape = PAGINA_ALTURA_PX - MARGEM + 10f
        canvas.drawLine(MARGEM, yRodape - 10f, PAGINA_LARGURA_PX - MARGEM, yRodape - 10f, paintLinhaSeparador)
        canvas.drawText(
            "SignallQ — monitoramento inteligente de rede  |  $totalMedicoes medições exportadas",
            MARGEM,
            yRodape,
            paintRodape,
        )
    }

    // ─── Utilitários ──────────────────────────────────────────────────────────

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
