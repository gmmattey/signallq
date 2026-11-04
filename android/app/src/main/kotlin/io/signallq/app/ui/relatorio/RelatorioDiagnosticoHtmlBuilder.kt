package io.signallq.app.ui.relatorio

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gera o HTML/CSS do relatório de diagnóstico/velocidade — GH#1219.
 *
 * Fonte ÚNICA para os dois fluxos do app consumidor (pós-speedtest e laudo de diagnóstico),
 * substituindo os dois geradores `PdfDocument`/`Canvas` divergentes (`ResultadoPdfGenerator`,
 * gerador privado de `LaudoScreen`). Segue o mesmo padrão já estabelecido por
 * `ExportadorHistoricoPDF.gerarHtml` (`:featureHistory`) — função pura, sem dependência de
 * Android, testável em JVM — e delega a paginação real ao motor genérico
 * `io.signallq.app.core.relatorio.exportarHtmlComoPdf` (`:core:relatorio`, WebView).
 *
 * Não-negociáveis (GH#1219):
 * - SEM afirmação de mínimo garantido/obrigação regulatória (Resolução 574/2011 e
 *   variantes) — a medição é sempre apresentada como informativa, nunca oficial.
 * - Métrica ausente vira "Não medido", nunca `0`/`NaN`.
 * - "Perda" sempre rotulada como estimada quando [RelatorioDiagnosticoSnapshot.perdaEstimada].
 * - Horário de medição e de geração sempre exibidos separadamente.
 * - Identificação do documento (id único, execução, versão do app/motor) sempre no rodapé.
 */
object RelatorioDiagnosticoHtmlBuilder {
    private val formatadorDataHora = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))

    fun gerarHtml(snapshot: RelatorioDiagnosticoSnapshot): String {
        val relatorioId = UUID.randomUUID().toString().take(8)
        val medidoEm = formatadorDataHora.format(Date(snapshot.medidoEmEpochMs))
        val geradoEm = formatadorDataHora.format(Date(snapshot.geradoEmEpochMs))

        val download = formatarMetrica(snapshot.downloadMbps, "Mbps", 1)
        val upload = if (snapshot.uploadNaoDetectado) "Não detectado" else formatarMetrica(snapshot.uploadMbps, "Mbps", 1)
        val latencia = formatarMetrica(snapshot.latenciaMs, "ms", 0)
        val jitter = formatarMetrica(snapshot.jitterMs, "ms", 0)
        val perdaValor = formatarMetrica(snapshot.perdaPercentual, "%", 1)
        val perda = if (snapshot.perdaEstimada && snapshot.perdaPercentual != null) "$perdaValor (estimada)" else perdaValor
        val bufferbloat = formatarMetrica(snapshot.bufferbloatMs, "ms", 0)

        val linhasRede =
            buildList {
                snapshot.ssidMascarado?.let { add("Wi-Fi (SSID)" to escapeHtml(it)) }
                snapshot.ipLocalMascarado?.let { add("IP local" to escapeHtml(it)) }
                snapshot.ipPublicoMascarado?.let { add("IP público" to escapeHtml(it)) }
                snapshot.operadora?.takeIf { it.isNotBlank() }?.let { add("Operadora" to escapeHtml(it)) }
                snapshot.servidorTeste?.takeIf { it.isNotBlank() }?.let { add("Servidor de teste" to escapeHtml(it)) }
            }

        val secaoDiagnostico =
            if (!snapshot.veredito.isNullOrBlank() || !snapshot.resumo.isNullOrBlank()) {
                """
                <section class="card-diagnostico">
                  <div class="eyebrow">Diagnóstico da Conexão</div>
                  ${if (!snapshot.veredito.isNullOrBlank()) "<h2 class=\"veredito-title\">${escapeHtml(snapshot.veredito)}</h2>" else ""}
                  ${if (!snapshot.resumo.isNullOrBlank()) "<p class=\"diagnostico-resumo\">${escapeHtml(snapshot.resumo)}</p>" else ""}
                  ${if (!snapshot.recomendacao.isNullOrBlank()) "<div class=\"recomendacao-box\"><strong>Recomendação:</strong> ${escapeHtml(snapshot.recomendacao)}</div>" else ""}
                  ${diagnosticoOrigemLabel(snapshot.diagnosticoOrigem)}
                </section>
                """.trimIndent()
            } else {
                ""
            }

        val avisoIntegridade =
            snapshot.statusIntegridade?.takeIf { it != "Completo" }?.let {
                """<div class="aviso-integridade">Integridade da medição: <strong>${escapeHtml(it)}</strong> — os
                    números abaixo podem representar só parte do teste, não trate como conclusão definitiva.</div>"""
            } ?: ""

        val avisoOffline =
            if (snapshot.offline) {
                """<div class="aviso-integridade">Gerado sem conexão no momento — exibindo a última medição salva.</div>"""
            } else {
                ""
            }

        return """<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <title>${escapeHtml(snapshot.nomeDocumento)}</title>
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

    /* Cabeçalho no estilo SignallQ Web */
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
      font-size: 10px;
      color: #79747E;
      line-height: 1.5;
    }
    .header-meta strong {
      color: #1C1B1F;
    }

    /* Avisos */
    .aviso-integridade {
      background: #FFF3CD;
      border: 1px solid #FFE69C;
      color: #8A5000;
      border-radius: 10px;
      padding: 8px 12px;
      font-size: 11px;
      margin-bottom: 14px;
      line-height: 1.4;
    }

    /* Card Diagnóstico (SignallQ Web) */
    .card-diagnostico {
      background: #FAF8FC;
      border: 1px solid #E7E0EC;
      border-radius: 14px;
      padding: 14px 16px;
      margin-bottom: 14px;
    }
    .eyebrow {
      font-size: 10px;
      font-weight: 800;
      color: #5B21D6;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      margin-bottom: 3px;
    }
    .veredito-title {
      font-size: 16px;
      font-weight: 800;
      color: #1C1B1F;
      margin-bottom: 4px;
    }
    .diagnostico-resumo {
      font-size: 12px;
      color: #49454F;
      line-height: 1.5;
    }
    .recomendacao-box {
      background: #FFFFFF;
      border: 1px solid #E7E0EC;
      border-radius: 8px;
      padding: 8px 12px;
      font-size: 11px;
      color: #1C1B1F;
      margin-top: 8px;
    }
    .recomendacao-box strong {
      color: #5B21D6;
    }
    .origem-label {
      font-size: 9px;
      color: #79747E;
      margin-top: 6px;
    }

    /* Seção de Métricas (Dashboard) */
    .secao-titulo {
      font-size: 11px;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      color: #5B21D6;
      margin-bottom: 8px;
    }
    .metricas-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-bottom: 14px;
    }
    .metrica-card {
      background: #F8F9FA;
      border: 1px solid #E7E0EC;
      border-radius: 10px;
      padding: 10px 12px;
    }
    .metrica-card .label {
      font-size: 9px;
      font-weight: 700;
      color: #79747E;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 2px;
    }
    .metrica-card .valor {
      font-size: 16px;
      font-weight: 800;
      color: #1C1B1F;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
    }

    /* Tabela de Informações de Rede */
    .card-rede {
      background: #FFFFFF;
      border: 1px solid #E7E0EC;
      border-radius: 14px;
      padding: 12px 16px;
      margin-bottom: 14px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 11px;
    }
    table td {
      padding: 6px 0;
      border-bottom: 1px solid #F3EEFA;
    }
    table tr:last-child td {
      border-bottom: none;
    }
    table td.label {
      color: #79747E;
      font-weight: 500;
      width: 38%;
    }
    table td.valor {
      color: #1C1B1F;
      font-weight: 600;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace;
    }

    /* Disclaimer e Rodapé */
    .disclaimer {
      background: #FAF8FC;
      border: 1px solid #E7E0EC;
      border-radius: 10px;
      padding: 10px 12px;
      font-size: 9px;
      color: #49454F;
      line-height: 1.5;
      margin-top: 14px;
    }
    .footer {
      margin-top: 10px;
      font-size: 8px;
      color: #79747E;
      border-top: 1px solid #E7E0EC;
      padding-top: 8px;
      display: flex;
      justify-content: space-between;
    }
    @media print {
      body { margin: 0; }
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
      <span class="badge-relatorio">${escapeHtml(snapshot.nomeDocumento)}</span>
    </div>
    <div class="header-meta">
      <p>Medição realizada em: <strong>$medidoEm</strong></p>
      <p>Relatório gerado em: <strong>$geradoEm</strong></p>
      <p>Tipo de rede: <strong>${escapeHtml(snapshot.tipoRede)}</strong></p>
    </div>
  </div>

  $avisoOffline
  $avisoIntegridade

  $secaoDiagnostico

  <div class="secao-titulo">Métricas de Conexão</div>
  <div class="metricas-grid">
    <div class="metrica-card"><div class="label">Download</div><div class="valor">$download</div></div>
    <div class="metrica-card"><div class="label">Upload</div><div class="valor">$upload</div></div>
    <div class="metrica-card"><div class="label">Latência</div><div class="valor">$latencia</div></div>
    <div class="metrica-card"><div class="label">Oscilação (jitter)</div><div class="valor">$jitter</div></div>
    <div class="metrica-card"><div class="label">Perda</div><div class="valor">$perda</div></div>
    <div class="metrica-card"><div class="label">Atraso sob carga</div><div class="valor">$bufferbloat</div></div>
  </div>

  ${if (linhasRede.isNotEmpty()) {
            """
            <section class="card-rede">
              <div class="secao-titulo" style="margin-bottom: 6px;">Informações de Rede</div>
              <table>
                ${linhasRede.joinToString("\n      ") { (label, valor) -> "<tr><td class=\"label\">$label</td><td class=\"valor\">$valor</td></tr>" }}
              </table>
            </section>
            """.trimIndent()
        } else {
            ""
        }}

  <div class="disclaimer">
    Esta medição é informativa: reflete o desempenho deste aparelho, nesta rede e neste
    momento — pode ser influenciada por Wi-Fi interno, aparelho, servidor de teste, rota e
    uso simultâneo da rede. Não é uma medição oficial da Anatel nem comprova, isoladamente,
    descumprimento contratual ou regulatório. Para reclamação formal, consulte os canais
    oficiais da sua operadora e da Anatel.
  </div>

  <div class="footer">
    <span>SignallQ &nbsp;|&nbsp; Relatório $relatorioId &nbsp;|&nbsp; Execução ${snapshot.executionId.ifBlank { "n/d" }}</span>
    <span>App ${escapeHtml(snapshot.versaoApp)} &nbsp;|&nbsp; Motor ${escapeHtml(snapshot.versaoMotor)} &nbsp;|&nbsp; © 2026 SignallQ</span>
  </div>
</body>
</html>"""
    }

    private fun diagnosticoOrigemLabel(origem: String?): String =
        when (origem) {
            "ia" -> "<p class=\"origem-label\">Diagnóstico gerado por IA — orientação automatizada, não substitui avaliação de um técnico.</p>"
            "local" -> "<p class=\"origem-label\">Diagnóstico gerado pelo motor local do app.</p>"
            else -> ""
        }

    private fun formatarMetrica(
        valor: Double?,
        unidade: String,
        casasDecimais: Int,
    ): String {
        // Nao concatenar `unidade` dentro do padrao de "%.${casasDecimais}f" antes do format —
        // a unidade "%" (perda) e interpretada como inicio de conversao pelo String.format,
        // lancando UnknownFormatConversionException. Formata so o numero, concatena depois.
        if (valor == null) return "Não medido"
        val numero = "%.${casasDecimais}f".format(Locale.US, valor)
        return "$numero $unidade"
    }

    private fun escapeHtml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
