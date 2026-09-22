package io.signallq.app.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.signallq.app.BuildConfig
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.feature.diagnostico.SnapshotDiagnostico
import io.signallq.app.paraConfiancaAmostral
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.component.DecisaoDiagnosticoLocal
import io.signallq.app.ui.component.LkSectionOverline
import io.signallq.app.ui.component.LkStatusDot
import io.signallq.app.ui.component.LkSurfaceCard
import io.signallq.app.ui.component.SignallQButton
import io.signallq.app.ui.component.copyInstabilidadePerdaPacotes
import io.signallq.app.ui.component.corContainer
import io.signallq.app.ui.component.corConteudo
import io.signallq.app.ui.component.labelPt
import io.signallq.app.ui.component.paraDecisaoDiagnosticoLocal
import io.signallq.app.ui.relatorio.RelatorioDiagnosticoExporter
import io.signallq.app.ui.relatorio.RelatorioDiagnosticoSnapshot
import io.signallq.app.ui.relatorio.RelatorioPrivacidade
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaudoScreen(
    snapshotDiagnostico: SnapshotDiagnostico,
    ultimaMedicao: MedicaoEntity?,
    nomeUsuario: String,
    operadora: String,
    ssid: String?,
    ipLocal: String?,
    ipPublico: String?,
    onVoltar: () -> Unit,
    velocidadeContratadaMbps: Int? = null,
    conectado: Boolean = true,
) {
    val c = LocalLkTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var gerando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    // NDS-02e (#1754, ADR-017) — le o relatorio local so atraves do seam DecisaoDiagnosticoLocal
    // (`ui/component/DecisaoDiagnosticoLocal.kt`), nunca DiagnosticReport/DiagnosticResult direto.
    val decisaoLocal = snapshotDiagnostico.relatorio?.paraDecisaoDiagnosticoLocal()
    // GH#1228 (Fase 3, corrige P0-3) — so usa a decisao (veredito/resumo/recomendacao) do
    // diagnostico em memoria quando ela pertence a MESMA execucao da medicao persistida
    // sendo exibida (ultimaMedicao); nunca combina metricas de uma execucao com o veredito
    // de outra ("Frankenstein" ja documentado na auditoria da #1228). Sem medicao persistida
    // ainda (ultimaMedicao == null), nao ha metrica pra conflitar — decisao segue disponivel.
    val diagnosticoCorresponde =
        ultimaMedicao == null || diagnosticoCorrespondeAMedicao(decisaoLocal?.executionId, ultimaMedicao.executionId)
    val decisao = decisaoLocal.takeIf { diagnosticoCorresponde }
    val diagnosticoIndisponivelPorDivergencia = decisaoLocal != null && !diagnosticoCorresponde

    val compartilharLaudo: () -> Unit = {
        scope.launch {
            gerando = true
            erro = null
            try {
                gerarECompartilharLaudo(
                    context = context,
                    snapshotDiagnostico = snapshotDiagnostico,
                    ultimaMedicao = ultimaMedicao,
                    operadora = operadora,
                    ssid = ssid,
                    ipLocal = ipLocal,
                    ipPublico = ipPublico,
                    velocidadeContratadaMbps = velocidadeContratadaMbps,
                    conectado = conectado,
                )
            } catch (e: Exception) {
                erro = "Não foi possível gerar o PDF: ${e.message}"
            } finally {
                gerando = false
            }
        }
    }

    // #375: offline reaproveita a ultima medicao salva — exibir o timestamp da
    // medicao original, nunca o momento da consulta, para nao sugerir uma analise nova.
    val dataHoraEpochMs =
        if (!conectado) ultimaMedicao?.timestampEpochMs else null
    val dataHora =
        remember(dataHoraEpochMs) {
            val data = dataHoraEpochMs?.let { Date(it) } ?: Date()
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.of("pt", "BR")).format(data)
        }
    val redeResumo =
        listOfNotNull(
            ssid?.takeIf { it.isNotBlank() },
            ultimaMedicao?.connectionType?.let(::tipoRedeLabelLaudo),
        ).joinToString(" · ").ifBlank { "Não identificada" }
    val resultadoResumo = decisao?.veredito ?: "Não disponível"

    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // GH#1219 item 2 — "Laudo Técnico"/"Laudo de diagnóstico" sugere documento
                    // pericial (responsável técnico, metodologia completa, cadeia de custódia),
                    // que este relatório B2C não tem. Não renomear para "Laudo".
                    Text(
                        "Relatório de diagnóstico",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.W600,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = compartilharLaudo,
                        enabled = !gerando,
                    ) {
                        if (gerando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(LkSpacing.lg),
                                color = c.textPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Compartilhar",
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = c.bgPrimary,
                        titleContentColor = c.textPrimary,
                        navigationIconContentColor = c.textPrimary,
                        actionIconContentColor = c.textPrimary,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding =
                PaddingValues(
                    start = LkSpacing.lg,
                    end = LkSpacing.lg,
                    top = 0.dp,
                    bottom = LkSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.lg),
        ) {
            item {
                Column {
                    Text(
                        "Relatório de diagnóstico",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(LkSpacing.xs))
                    Text(
                        "Um resumo compartilhável das evidências encontradas nesta conexão.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            }

            // Banner de status — colorido por severidade da decisão
            if (decisao != null) {
                item {
                    val containerColor = decisao.status.corContainer(c)
                    val textColor = decisao.status.corConteudo(c)
                    val labelStatus = decisao.status.labelPt()
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LkRadius.card))
                                .background(containerColor)
                                .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
                            modifier = Modifier.weight(1f),
                        ) {
                            LkStatusDot(color = textColor)
                            Column {
                                Text(
                                    labelStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.W600,
                                    color = textColor,
                                )
                                Text(
                                    decisao.titulo,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.W600,
                                    color = textColor,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${decisao.scoreConexao}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.W700,
                                color = textColor,
                            )
                            Text(
                                decisao.veredito,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.W600,
                                color = textColor,
                            )
                        }
                    }
                }
            } else if (diagnosticoIndisponivelPorDivergencia) {
                // GH#1228 (Fase 3, corrige P0-3) — ha um diagnostico em memoria, mas ele nao
                // corresponde a mesma execucao da medicao exibida abaixo (ex.: um diagnostico
                // de Wi-Fi rodou depois, sem um novo speedtest) — nunca combinar, avisar.
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LkRadius.card))
                                .background(c.warningContainer)
                                .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Diagnóstico não disponível para esta medição (resultado de uma execução diferente).",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.W600,
                            color = c.onWarningContainer,
                        )
                    }
                }
            }

            // Contexto do relatório: sem valores inferidos; cada linha vem do diagnóstico ou da medição.
            item {
                Column {
                    LaudoResumoRow(label = "Rede", value = redeResumo, c = c)
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    LaudoResumoRow(label = "Resultado", value = resultadoResumo, c = c)
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    LaudoResumoRow(label = "Gerado", value = dataHora, c = c)
                    // #375: sem conexao no momento da consulta — deixa explicito que os
                    // dados exibidos sao de uma medicao anterior, nao uma analise nova.
                    if (!conectado) {
                        Spacer(Modifier.height(LkSpacing.xs))
                        Text(
                            "Sem conexão no momento · exibindo última medição salva",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.W600,
                            color = c.error,
                        )
                    }
                }
            }

            // RESUMO
            if (decisao != null) {
                item {
                    LaudoSection(titulo = "RESUMO", c = c) {
                        Text(
                            decisao.mensagemUsuario,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                }
            }

            // MÉTRICAS — grid 3×2
            if (ultimaMedicao != null) {
                item {
                    LaudoSection(titulo = "MÉTRICAS", c = c) {
                        Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.md)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(LkSpacing.md)) {
                                LaudoMetrica(
                                    label = "Download",
                                    valor = ultimaMedicao.downloadMbps?.let { "%.1f".format(it) } ?: "—",
                                    unidade = "Mbps",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                )
                                LaudoMetrica(
                                    label = "Upload",
                                    valor = ultimaMedicao.uploadMbps?.let { "%.1f".format(it) } ?: "—",
                                    unidade = "Mbps",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HorizontalDivider(color = c.border, thickness = 0.5.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(LkSpacing.md)) {
                                LaudoMetrica(
                                    label = "Latência",
                                    valor = ultimaMedicao.latencyMs?.let { "%.0f".format(it) } ?: "—",
                                    unidade = "ms",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                )
                                LaudoMetrica(
                                    label = "Jitter",
                                    valor = ultimaMedicao.jitterMs?.let { "%.0f".format(it) } ?: "—",
                                    unidade = "ms",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HorizontalDivider(color = c.border, thickness = 0.5.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(LkSpacing.md)) {
                                LaudoMetrica(
                                    label = "Perda",
                                    valor = ultimaMedicao.perdaPercentual?.let { "%.1f".format(it) } ?: "—",
                                    unidade = "%",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                    // .agents/architecture-plan.md ("Confiabilidade estatística
                                    // do diagnóstico de rede", passo 6) — quando a confiança
                                    // amostral é insuficiente (1 timeout isolado), o aviso de
                                    // instabilidade tem prioridade sobre o rótulo de metodologia
                                    // "estimado" (eixo diferente, ver copyInstabilidadePerdaPacotes).
                                    nota =
                                        copyInstabilidadePerdaPacotes(
                                            ultimaMedicao.perdaPercentual,
                                            ultimaMedicao.perdaConfianca.paraConfiancaAmostral(),
                                        ) ?: "estimado".takeIf { ultimaMedicao.packetLossSource == "estimated" },
                                )
                                LaudoMetrica(
                                    label = "Bufferbloat",
                                    valor = ultimaMedicao.bufferbloatMs?.let { "%.0f".format(it) } ?: "—",
                                    unidade = "ms",
                                    c = c,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // RECOMENDAÇÃO
            val recomendacao = decisao?.recomendacao
            if (!recomendacao.isNullOrBlank()) {
                item {
                    LaudoSection(titulo = "RECOMENDAÇÃO", c = c) {
                        Text(
                            recomendacao,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                }
            }

            // Error message if PDF generation failed
            if (erro != null) {
                item {
                    Text(
                        erro!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.error,
                    )
                }
            }

            item {
                SignallQButton(
                    onClick = compartilharLaudo,
                    label = "Compartilhar PDF",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !gerando,
                    loading = gerando,
                    leadingIcon = Icons.Outlined.Share,
                )
            }
        }
    }
}

@Composable
private fun LaudoSection(
    titulo: String,
    c: LkTokens,
    content: @Composable () -> Unit,
) {
    LkSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        LkSectionOverline(titulo)
        Spacer(Modifier.height(LkSpacing.sm))
        content()
    }
}

@Composable
private fun LaudoResumoRow(
    label: String,
    value: String,
    c: LkTokens,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LkSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun LaudoMetrica(
    label: String,
    valor: String,
    unidade: String,
    c: LkTokens,
    modifier: Modifier = Modifier,
    nota: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textTertiary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                valor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W700,
                color = c.textPrimary,
            )
            Spacer(Modifier.width(LkSpacing.xs))
            Text(
                unidade,
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(bottom = LkSpacing.xs),
            )
        }
        if (nota != null) {
            Text(
                nota,
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
            )
        }
    }
}

/**
 * GH#1228 (Fase 3, corrige P0-3) — decide se o diagnóstico em memória ([relatorioExecutionId])
 * pertence à MESMA execução que a medição persistida sendo exibida ([medicaoExecutionId]).
 *
 * Ambos os lados precisam ter `executionId` não vazio E iguais. Se qualquer lado for
 * desconhecido (linha legada persistida antes desta coluna existir, ou fluxo que ainda não
 * propaga o id — ver `DiagnosticInput.executionId`), a correspondência NUNCA é assumida como
 * verdadeira — "não sei" nunca vira "sim" por omissão. Função pura, sem Compose/Context,
 * testável diretamente.
 */
internal fun diagnosticoCorrespondeAMedicao(
    relatorioExecutionId: String?,
    medicaoExecutionId: String?,
): Boolean {
    if (relatorioExecutionId.isNullOrBlank() || medicaoExecutionId.isNullOrBlank()) return false
    return relatorioExecutionId == medicaoExecutionId
}

/**
 * GH#1228 (Fase 3, corrige P0-3) — monta o [RelatorioDiagnosticoSnapshot] exportável usando
 * SOMENTE a decisão (veredito/resumo/recomendação) do [decisaoLocal] quando ela pertence à MESMA
 * execução da [ultimaMedicao] persistida — nunca combina métricas de uma execução com o
 * veredito de outra ("Frankenstein" já documentado na auditoria da #1228, ex.: um diagnóstico
 * de Wi-Fi sem internet rodado depois de um speedtest antigo). Quando não há correspondência
 * (mas há diagnóstico em memória), o resumo exibido é explícito sobre a indisponibilidade —
 * nunca busca automaticamente um diagnóstico de outra execução para preencher a lacuna.
 *
 * NDS-02e (#1754, ADR-017) — recebe [DecisaoDiagnosticoLocal] em vez de `DiagnosticReport` (o
 * call site, [gerarECompartilharLaudo], converte pelo seam `ui/component/
 * DecisaoDiagnosticoLocal.kt` antes de chamar esta função). Função pura (sem Context/IO/Compose)
 * — testável diretamente, sem instrumentação Android.
 */
internal fun montarSnapshotLaudo(
    decisaoLocal: DecisaoDiagnosticoLocal?,
    ultimaMedicao: MedicaoEntity?,
    operadora: String,
    ssid: String?,
    ipLocal: String?,
    ipPublico: String?,
    velocidadeContratadaMbps: Int?,
    conectado: Boolean,
    versaoApp: String,
    agoraEpochMs: Long = System.currentTimeMillis(),
): RelatorioDiagnosticoSnapshot {
    val diagnosticoCorresponde =
        ultimaMedicao == null || diagnosticoCorrespondeAMedicao(decisaoLocal?.executionId, ultimaMedicao.executionId)
    val decisao = decisaoLocal.takeIf { diagnosticoCorresponde }

    // #375: offline reaproveita a ultima medicao salva — o horario exibido precisa ser o
    // da MEDICAO real, nunca o momento da geracao do PDF (GH#1219 item 5).
    val medidoEmEpochMs = if (!conectado) ultimaMedicao?.timestampEpochMs ?: agoraEpochMs else agoraEpochMs

    val operadoraOuIsp = (operadora.ifBlank { null } ?: ultimaMedicao?.operadoraMovel)
    val planoInfo =
        if (velocidadeContratadaMbps != null && velocidadeContratadaMbps > 0) {
            "Plano contratado informado: $velocidadeContratadaMbps Mbps (comparação apenas informativa, não é aferição oficial)."
        } else {
            null
        }

    val resumo =
        when {
            decisao != null -> decisao.mensagemUsuario.ifBlank { null }
            decisaoLocal != null && !diagnosticoCorresponde ->
                "Diagnóstico não disponível para esta medição — o último diagnóstico calculado " +
                    "pertence a uma execução diferente da medição exibida acima."
            else -> null
        }

    return RelatorioDiagnosticoSnapshot(
        // GH#1228 (Fase 3) — id da medicao que originou os numeros abaixo (fonte real das
        // metricas exibidas), nunca o id de um diagnostico que nao corresponde a ela.
        executionId = ultimaMedicao?.executionId ?: "",
        nomeDocumento = "Relatório de diagnóstico da conexão",
        medidoEmEpochMs = medidoEmEpochMs,
        tipoRede = ultimaMedicao?.connectionType?.let { tipoRedeLabelLaudo(it) } ?: "Não identificado",
        downloadMbps = ultimaMedicao?.downloadMbps,
        uploadMbps = ultimaMedicao?.uploadMbps,
        latenciaMs = ultimaMedicao?.latencyMs,
        jitterMs = ultimaMedicao?.jitterMs,
        perdaPercentual = ultimaMedicao?.perdaPercentual,
        perdaEstimada = ultimaMedicao?.packetLossSource == "estimated",
        bufferbloatMs = ultimaMedicao?.bufferbloatMs,
        veredito = decisao?.titulo,
        resumo = resumo,
        recomendacao = listOfNotNull(decisao?.recomendacao, planoInfo).joinToString(" ").ifBlank { null },
        diagnosticoOrigem = ultimaMedicao?.diagnosticoOrigem,
        ssidMascarado = RelatorioPrivacidade.mascararSsid(ssid),
        ipLocalMascarado = RelatorioPrivacidade.mascararIpLocal(ipLocal),
        ipPublicoMascarado = RelatorioPrivacidade.mascararIpPublico(ipPublico),
        operadora = operadoraOuIsp,
        versaoApp = versaoApp,
        versaoMotor = ultimaMedicao?.specVersion ?: "n/d",
        offline = !conectado,
    )
}

/**
 * GH#1219 — antes gerava o PDF direto via `PdfDocument`/`Canvas` manual, com texto
 * truncado por limite de caracteres, sem paginação e com a seção "CONFORMIDADE ANATEL"
 * (mínimo garantido 40%/meta 80%/Resolução 574/2011 — afirmações regulatórias
 * desatualizadas, removidas nesta correção; ver critérios de aceite da issue). Agora monta
 * um [RelatorioDiagnosticoSnapshot] com SSID/IPs já mascarados (via [montarSnapshotLaudo]) e
 * delega pro renderer único ([RelatorioDiagnosticoExporter], mesmo motor HTML→PDF do
 * `ResultadoPdfGenerator`).
 */
private suspend fun gerarECompartilharLaudo(
    context: Context,
    snapshotDiagnostico: SnapshotDiagnostico,
    ultimaMedicao: MedicaoEntity?,
    // GH#1219 item 11 — nome do usuario (PII) deliberadamente NAO entra no snapshot
    // compartilhado; o parametro so existe no cabecalho da tela (LaudoScreen), nunca no
    // arquivo PDF gerado.
    operadora: String,
    ssid: String?,
    ipLocal: String?,
    ipPublico: String?,
    velocidadeContratadaMbps: Int? = null,
    conectado: Boolean = true,
) {
    val snapshot =
        montarSnapshotLaudo(
            // NDS-02e (#1754, ADR-017) — converte pelo seam antes de montar o snapshot; nenhuma
            // função abaixo deste ponto depende de DiagnosticReport/core.diagnostico direto.
            decisaoLocal = snapshotDiagnostico.relatorio?.paraDecisaoDiagnosticoLocal(),
            ultimaMedicao = ultimaMedicao,
            operadora = operadora,
            ssid = ssid,
            ipLocal = ipLocal,
            ipPublico = ipPublico,
            velocidadeContratadaMbps = velocidadeContratadaMbps,
            conectado = conectado,
            versaoApp = BuildConfig.VERSION_NAME,
        )

    RelatorioDiagnosticoExporter.gerarECompartilhar(
        context = context,
        snapshot = snapshot,
        nomeArquivoPrefixo = "laudo_signallq",
    )
}

private fun tipoRedeLabelLaudo(connectionType: String): String =
    when {
        connectionType.equals("wifi", ignoreCase = true) -> "Wi-Fi"
        connectionType.equals("movel", ignoreCase = true) -> "Rede móvel"
        else -> "Não identificado"
    }
