package io.signallq.app.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.signallq.app.ads.AdSlot
import io.signallq.app.ads.AdUnitIds
import io.signallq.app.ads.NativeAdContentSignal
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.core.diagnostico.MetricStatus
import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.feature.history.BlocoUptime
import io.signallq.app.feature.history.ResumoHistorico
import io.signallq.app.paraConfiancaAmostral
import io.signallq.app.ui.FiltroConexaoHistorico
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.ads.NativeAdEligibility
import io.signallq.app.ui.ads.NativeAdLoadState
import io.signallq.app.ui.ads.rememberNativeAdState
import io.signallq.app.ui.component.LkSheetDivider
import io.signallq.app.ui.component.LkSheetFrame
import io.signallq.app.ui.component.LkSheetInfoRow
import io.signallq.app.ui.component.Overline
import io.signallq.app.ui.component.ads.NativeAdCard
import io.signallq.app.ui.component.ads.NativeAdSource
import io.signallq.app.ui.component.classificarBufferbloatLocal
import io.signallq.app.ui.component.copyInstabilidadePerdaPacotes
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Filtro enum ──────────────────────────────────────────────────────────────

private typealias FiltroTipo = FiltroConexaoHistorico

private val FiltroConexaoHistorico.label: String
    get() =
        when (this) {
            FiltroConexaoHistorico.TODOS -> "Todos"
            FiltroConexaoHistorico.WIFI -> "Wi-Fi"
            FiltroConexaoHistorico.MOVEL -> "Rede móvel"
        }

private enum class OrdenacaoHistorico {
    MAIS_RECENTES,
    MAIS_ANTIGAS,
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

internal fun tipoLabel(m: MedicaoEntity): String =
    when (m.connectionType) {
        "wifi" -> "Wi-Fi" + bandaWifiSufixo(m.bandaWifi)
        EstadoConexao.movel.name -> "Celular"
        "ethernet" -> "Cabo"
        else -> m.connectionType
    }

/** GH#1027: " · 5GHz"/" · 2.4GHz" quando a banda foi capturada na medição, "" quando não (medição antiga). */
internal fun bandaWifiSufixo(bandaWifi: String?): String =
    when (bandaWifi) {
        BandaWifi.ghz5.name -> " · 5GHz"
        BandaWifi.ghz24.name -> " · 2.4GHz"
        else -> ""
    }

private fun formatDate(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val h = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
    val m = "%02d".format(cal.get(Calendar.MINUTE))
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Hoje $h:$m"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Ontem $h:$m"
        else -> {
            val d = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
            val mo = "%02d".format(cal.get(Calendar.MONTH) + 1)
            "$d/$mo $h:$m"
        }
    }
}

private fun formatFullDate(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val d = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
    val mo = "%02d".format(cal.get(Calendar.MONTH) + 1)
    val y = cal.get(Calendar.YEAR)
    val h = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
    val m = "%02d".format(cal.get(Calendar.MINUTE))
    return "$d/$mo/$y às $h:$m"
}

private fun textoCompartilhamento(medicao: MedicaoEntity): String =
    buildString {
        append("Resultado do teste SignallQ\n")
        append("${tipoLabel(medicao)} · ${formatFullDate(medicao.timestampEpochMs)}\n")
        medicao.downloadMbps?.let { append("Download: %.1f Mbps\n".format(it)) }
        medicao.uploadMbps?.let { append("Upload: %.1f Mbps\n".format(it)) }
        medicao.latencyMs?.let { append("Latência: %.0f ms\n".format(it)) }
        medicao.jitterMs?.let { append("Oscilação: %.0f ms\n".format(it)) }
    }

private fun vereditoLabel(v: String?): String? =
    when (v) {
        "good" -> "Bom"
        "acceptable" -> "Aceitável"
        "poor" -> "Ruim"
        null -> null
        else -> v
    }

/**
 * Veredito humano do bufferbloat -- mesma regua canonica que `MetricClassifier.classificarBufferbloat`
 * sempre usou.
 *
 * Issue #1749 (NDS-02b, ADR-017): a chamada passou a ir por [classificarBufferbloatLocal]
 * (`ClassificacaoMetricaLocal.kt`) em vez de `MetricClassifier` direto. Ressalva de dado histórico
 * confirmada por decisão do Luiz em #1746: uma medição já persistida NUNCA é reclassificada
 * retroativamente via NDS — o `MetricStatus` fica congelado no valor calculado a partir do
 * `deltaMs` já salvo, com a MESMA régua de sempre. Não há chamada de rede aqui, de propósito.
 */
internal fun bufferbloatVeredito(
    deltaMs: Double,
    c: LkTokens,
): Pair<String, Color> =
    when (classificarBufferbloatLocal(deltaMs)) {
        MetricStatus.excelente, MetricStatus.bom -> "Baixo" to c.success
        MetricStatus.regular -> "Moderado" to c.warning
        MetricStatus.ruim, MetricStatus.critico -> "Alto" to c.error
        MetricStatus.inconclusivo -> "—" to c.primary
    }

private fun gargaloLabel(g: String?): String? =
    when (g) {
        null, "none" -> null
        "download" -> "Download"
        "upload" -> "Upload"
        "latency" -> "Latência"
        "jitter" -> "Oscilação"
        "packetLoss" -> "Perda de pacotes"
        "bufferbloat" -> "Bufferbloat"
        else -> g
    }

// ─── Filtros de conexão ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltrosConexao(
    filtroSelecionado: FiltroTipo,
    onFiltroChange: (FiltroTipo) -> Unit,
    c: LkTokens,
    compact: Boolean = false,
) {
    Row(
        modifier =
            if (compact) {
                // #1131 (bug 2) — largura fixa (220.dp) dividida em 3 pills de peso igual
                // forcava "Rede movel" a quebrar em duas linhas (nao cabia no espaco
                // reservado). Cada pill agora usa a largura do proprio conteudo em vez de
                // peso igual, e a Row inteira acompanha a soma dos filhos.
                Modifier
                    .clip(RoundedCornerShape(LkRadius.pill))
                    .background(c.surfaceContainer)
                    .padding(LkSpacing.xs)
            } else {
                Modifier
            },
        horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs),
    ) {
        FiltroTipo.entries.forEach { filtro ->
            if (compact) {
                Surface(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(LkRadius.pill))
                            .clickable { onFiltroChange(filtro) },
                    color = if (filtroSelecionado == filtro) c.secondaryContainer else Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = LkSpacing.md, vertical = LkSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            filtro.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (filtroSelecionado == filtro) c.onSecondaryContainer else c.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            } else {
                FilterChip(
                    selected = filtroSelecionado == filtro,
                    onClick = { onFiltroChange(filtro) },
                    label = { Text(filtro.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.secondaryContainer,
                            selectedLabelColor = c.onSecondaryContainer,
                        ),
                    border =
                        FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = filtroSelecionado == filtro,
                            borderColor = c.border,
                            selectedBorderColor = c.secondaryContainer,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FiltroOperadora(
    selecionada: String?,
    operadoras: List<String>,
    onChange: (String?) -> Unit,
    c: LkTokens,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.xs)) {
        Text(
            text = "Operadora",
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs)) {
            item {
                FilterChip(
                    selected = selecionada == null,
                    onClick = { onChange(null) },
                    label = { Text("Todas") },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.secondaryContainer,
                            selectedLabelColor = c.onSecondaryContainer,
                        ),
                )
            }
            items(items = operadoras, key = { it }) { operadora ->
                FilterChip(
                    selected = selecionada == operadora,
                    onClick = { onChange(operadora) },
                    label = { Text(operadora) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = c.secondaryContainer,
                            selectedLabelColor = c.onSecondaryContainer,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FiltroOrdenacao(
    selecionada: OrdenacaoHistorico,
    onChange: (OrdenacaoHistorico) -> Unit,
    c: LkTokens,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("Ordenar", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        OrdenacaoHistorico.entries.forEach { opcao ->
            FilterChip(
                selected = selecionada == opcao,
                onClick = { onChange(opcao) },
                label = { Text(if (opcao == OrdenacaoHistorico.MAIS_RECENTES) "Mais recentes" else "Mais antigas") },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = c.secondaryContainer,
                        selectedLabelColor = c.onSecondaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun HistoricoResumoCard(
    resumo: ResumoHistorico,
    c: LkTokens,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.surfaceContainer,
        shape = RoundedCornerShape(LkRadius.card),
    ) {
        Column(
            modifier = Modifier.padding(LkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            Text(
                text = "Resumo das medições",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
            )
            Text(
                text = "${resumo.totalMedicoes} medições registradas",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
            ) {
                HistoricoResumoMetric(
                    label = "Download médio",
                    value = resumo.mediaDownloadMbps5?.let { "%.0f Mbps".format(it) } ?: "—",
                    c = c,
                    modifier = Modifier.weight(1f),
                )
                HistoricoResumoMetric(
                    label = "Latência média",
                    value = resumo.mediaLatenciaMs5?.let { "%.0f ms".format(it) } ?: "—",
                    c = c,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HistoricoResumoMetric(
    label: String,
    value: String,
    c: LkTokens,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * GH#1785 — mesmo mapeamento de [NativeAdEligibility] usado em `eligibilidadeAnuncioResultado`
 * (ResultadoVelocidadeScreen.kt): a tela só recebe o flag `adsEnabled` de fora, sem sinal de
 * consentimento UMP nem de conectividade separados (mesma limitação que `rememberNativeAd()`,
 * o wrapper antigo, já tinha).
 */
internal fun eligibilidadeAnuncioHistorico(adsGate: io.signallq.app.ads.NativeAdsGate): NativeAdEligibility =
    adsGate.eligibilityFor(AdSlot.HISTORICO)

/** Compatibilidade para testes legados; o fluxo de produção usa [NativeAdsGate]. */
internal fun eligibilidadeAnuncioHistorico(adsEnabled: Boolean): NativeAdEligibility =
    NativeAdEligibility(AdSlot.HISTORICO, buildEnabled = true, flagEnabled = adsEnabled, canRequestAds = adsEnabled, online = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(
    historico: List<MedicaoEntity>,
    resumoHistorico: ResumoHistorico? = null,
    onAbrirMenu: () -> Unit = {},
    onIniciarTeste: () -> Unit = {},
    filtroConexao: FiltroConexaoHistorico? = null,
    onFiltroConexaoChange: (FiltroConexaoHistorico) -> Unit = {},
    filtroOperadora: String? = null,
    onFiltroOperadoraChange: (String?) -> Unit = {},
    operadorasDisponiveis: List<String> = emptyList(),
    onExcluirMedicao: (String) -> Unit = {},
    /** Mantido por compatibilidade com o estado do shell; o bloco de 7 dias foi removido da UI. */
    blocosUptime: List<BlocoUptime> = emptyList(),
    /** Toggle remoto (Firebase Remote Config) + gate de consentimento UMP -- issue #555.
     *  Default `false`: nunca mostra anuncio sem sinal explicito de que pode. */
    adsGate: io.signallq.app.ads.NativeAdsGate =
        io.signallq.app.ads
            .NativeAdsGate(),
) {
    val c = LocalLkTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mostrarExport by remember { mutableStateOf(false) }
    var mostrarFiltros by remember { mutableStateOf(false) }
    // Issue #555 -- dispensar o anuncio e estado de sessao, nunca persistido.
    var modoComparacao by remember { mutableStateOf(false) }
    var mostrarComparacao by remember { mutableStateOf(false) }
    var ordenacao by remember { mutableStateOf(OrdenacaoHistorico.MAIS_RECENTES) }
    val medicoesSelecionadas = remember { mutableStateListOf<String>() }

    // Controlled mode: use external state from AppShell/ViewModel
    // Uncontrolled mode: use internal session state
    var filtroConexaoInterno by remember { mutableStateOf(FiltroTipo.TODOS) }
    var filtroOperadoraInterno by remember { mutableStateOf<String?>(null) }
    val filtroConexaoAtivo = filtroConexao ?: filtroConexaoInterno
    val filtroOperadoraAtivo = if (filtroConexao != null) filtroOperadora else filtroOperadoraInterno

    val sheetExportState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetFiltrosState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Modo controlado: AppShell/ViewModel já pré-filtrou a lista — não re-filtrar aqui,
    // pois isso causaria double-filter e lista sempre vazia ao selecionar MOVEL.
    // Modo não-controlado: aplica filtro local (sessão interna sem ViewModel).
    val historicoFiltrado =
        remember(historico, filtroConexaoAtivo, filtroOperadoraAtivo) {
            if (filtroConexao != null) {
                // Modo controlado: lista já vem filtrada do ViewModel.
                historico
            } else {
                // Modo não-controlado: filtra internamente.
                // #1096 -- exclui medicoes sinteticas do MonitoramentoWorker (fonte="monitor"),
                // que nao tem download/upload e nao devem aparecer na lista do Historico.
                historico
                    .filter { m -> m.fonte != "monitor" }
                    .filter { m ->
                        when (filtroConexaoAtivo) {
                            FiltroTipo.TODOS -> true
                            FiltroTipo.WIFI -> m.connectionType == "wifi"
                            FiltroTipo.MOVEL -> m.connectionType == EstadoConexao.movel.name
                        }
                    }.filter { m -> filtroOperadoraAtivo == null || m.operadoraMovel == filtroOperadoraAtivo }
            }
        }

    Scaffold(
        containerColor = c.bgPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("Histórico", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                },
                actions = {
                    IconButton(onClick = onAbrirMenu) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Abrir ajustes", tint = c.textPrimary)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                mostrarExport = true
                            }
                        },
                        enabled = historicoFiltrado.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Exportar histórico",
                            tint = if (historicoFiltrado.isNotEmpty()) c.textPrimary else c.textTertiary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        val listaParaExibir =
            remember(historicoFiltrado, ordenacao) {
                when (ordenacao) {
                    OrdenacaoHistorico.MAIS_RECENTES -> historicoFiltrado.sortedByDescending { it.timestampEpochMs }
                    OrdenacaoHistorico.MAIS_ANTIGAS -> historicoFiltrado.sortedBy { it.timestampEpochMs }
                }
            }
        val filtroAtivo = filtroConexaoAtivo != FiltroTipo.TODOS || filtroOperadoraAtivo != null

        // #1666/#1520: uma tela sem nenhum teste manual mas com dado de monitoramento
        // (fonte="monitor") ainda tem algo relevante para mostrar -- nao cair no estado
        // totalmente vazio so porque a lista de testes manuais esta vazia.
        if (listaParaExibir.isEmpty() && !filtroAtivo) {
            EmptyHistorico(
                modifier = Modifier.fillMaxSize().padding(padding),
                onIniciarTeste = onIniciarTeste,
                filtroAtivo = false,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag("historico_lista"),
                contentPadding = PaddingValues(horizontal = LkSpacing.lg, vertical = LkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(LkSpacing.md),
            ) {
                item(key = "historico_intro") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Suas análises",
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.textPrimary,
                        )
                    }
                }
                if (historicoFiltrado.size > 1) {
                    item(key = "historico_comparar") {
                        if (!modoComparacao) {
                            OutlinedButton(
                                onClick = {
                                    modoComparacao = true
                                    mostrarComparacao = false
                                    medicoesSelecionadas.clear()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Comparar medições")
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = c.surfaceContainer,
                                shape = RoundedCornerShape(LkRadius.card),
                            ) {
                                Column(
                                    modifier = Modifier.padding(LkSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
                                ) {
                                    Text(
                                        "Compare duas medições",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = c.textPrimary,
                                    )
                                    Text(
                                        when (medicoesSelecionadas.size) {
                                            0 -> "Toque em duas medições para selecioná-las."
                                            1 -> "Selecione mais uma medição para continuar."
                                            else -> "Duas medições selecionadas."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
                                    ) {
                                        TextButton(
                                            onClick = {
                                                modoComparacao = false
                                                mostrarComparacao = false
                                                medicoesSelecionadas.clear()
                                            },
                                        ) {
                                            Text("Cancelar")
                                        }
                                        Button(
                                            onClick = { mostrarComparacao = true },
                                            enabled = medicoesSelecionadas.size == 2,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Comparar selecionadas")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                resumoHistorico?.takeIf { it.totalMedicoes > 0 }?.let { resumo ->
                    item(key = "historico_resumo") {
                        HistoricoResumoCard(resumo = resumo, c = c)
                    }
                }
                item(key = "native_ad_historico") {
                    val nativeAdState by rememberNativeAdState(
                        adUnitId = AdUnitIds.para(AdSlot.HISTORICO),
                        contentSignal = NativeAdContentSignal.forSlot(AdSlot.HISTORICO),
                        eligibility = eligibilidadeAnuncioHistorico(adsGate),
                    )
                    val nativeAd = (nativeAdState as? NativeAdLoadState.Fill)?.ad
                    NativeAdCard(nativeAd = nativeAd, source = NativeAdSource.ADMOB)
                }
                item(key = "medicoes_header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Medições",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                        )
                        Spacer(Modifier.height(LkSpacing.sm))
                        OutlinedButton(
                            onClick = { mostrarFiltros = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FilterList, contentDescription = null)
                            Spacer(Modifier.width(LkSpacing.sm))
                            Text(
                                if (filtroAtivo) "Filtros aplicados" else "Filtrar e ordenar",
                            )
                        }
                    }
                }
                if (listaParaExibir.isEmpty()) {
                    item(key = "empty_filtro") {
                        EmptyHistorico(
                            modifier = Modifier.fillMaxWidth().padding(vertical = LkSpacing.xxl),
                            onIniciarTeste = onIniciarTeste,
                            filtroAtivo = filtroAtivo,
                        )
                    }
                } else {
                    items(listaParaExibir, key = { it.id }) { medicao ->
                        HistoricoCard(
                            medicao = medicao,
                            selectionMode = modoComparacao,
                            selected = medicao.id in medicoesSelecionadas,
                            onClick = {
                                if (modoComparacao) {
                                    if (medicao.id in medicoesSelecionadas) {
                                        medicoesSelecionadas.remove(medicao.id)
                                    } else if (medicoesSelecionadas.size < 2) {
                                        medicoesSelecionadas.add(medicao.id)
                                    }
                                }
                            },
                            onShare = {
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, textoCompartilhamento(medicao))
                                    }
                                context.startActivity(Intent.createChooser(intent, "Compartilhar resultado"))
                            },
                            onDelete = { onExcluirMedicao(medicao.id) },
                        )
                    }
                    if (modoComparacao && mostrarComparacao && medicoesSelecionadas.size == 2) {
                        val selecionadas = listaParaExibir.filter { it.id in medicoesSelecionadas }
                        if (selecionadas.size == 2) {
                            item(key = "historico_comparacao_resultado") {
                                HistoricoComparacaoCard(
                                    primeira = selecionadas[0],
                                    segunda = selecionadas[1],
                                    c = c,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarFiltros) {
        ModalBottomSheet(
            onDismissRequest = { mostrarFiltros = false },
            sheetState = sheetFiltrosState,
            containerColor = c.bgCard,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = LkSpacing.lg, vertical = LkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LkSpacing.lg),
            ) {
                Text("Filtrar histórico", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.sm)) {
                    Text("Tipo de conexão", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    FiltrosConexao(
                        filtroSelecionado = filtroConexaoAtivo,
                        onFiltroChange = { novo ->
                            if (filtroConexao != null) {
                                onFiltroConexaoChange(novo)
                            } else {
                                filtroConexaoInterno = novo
                            }
                        },
                        c = c,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.sm)) {
                    Text("Ordenação", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                    FiltroOrdenacao(selecionada = ordenacao, onChange = { ordenacao = it }, c = c)
                }
                if (operadorasDisponiveis.isNotEmpty()) {
                    FiltroOperadora(
                        selecionada = filtroOperadoraAtivo,
                        operadoras = operadorasDisponiveis,
                        onChange = { nova ->
                            if (filtroConexao != null) {
                                onFiltroOperadoraChange(nova)
                            } else {
                                filtroOperadoraInterno = nova
                            }
                        },
                        c = c,
                    )
                }
                Button(
                    onClick = { mostrarFiltros = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Aplicar")
                }
            }
        }
    }

    if (mostrarExport) {
        ModalBottomSheet(
            onDismissRequest = { mostrarExport = false },
            sheetState = sheetExportState,
            containerColor = c.bgCard,
            dragHandle = {},
        ) {
            ExportHistoricoBottomSheet(
                historico = historicoFiltrado,
                snackbarHostState = snackbarHostState,
                onDismiss = { mostrarExport = false },
                onRetry = {
                    scope.launch {
                        sheetExportState.hide()
                        mostrarExport = false
                        mostrarExport = true
                    }
                },
            )
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistorico(
    modifier: Modifier = Modifier,
    onIniciarTeste: () -> Unit = {},
    filtroAtivo: Boolean = false,
) {
    val c = LocalLkTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.History, null, tint = c.textTertiary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(LkSpacing.lg))
            Text(
                if (filtroAtivo) "Nenhum teste para este filtro" else "Nenhum teste realizado ainda",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W500,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(LkSpacing.sm))
            Text(
                if (filtroAtivo) {
                    "Não há medições para o filtro selecionado.\nTente selecionar outro tipo de conexão."
                } else {
                    "As medições do diagnóstico\naparecerão aqui."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(LkSpacing.lg))
            Button(onClick = onIniciarTeste) {
                Text(if (filtroAtivo) "Medir agora" else "Fazer primeira medição")
            }
        }
    }
}

// ─── List item ────────────────────────────────────────────────────────────────

/** Cor do [TomConclusao] resolvida contra os tokens ativos (claro/escuro). */
private fun corDoTom(
    tom: TomConclusao,
    c: LkTokens,
): Color =
    when (tom) {
        TomConclusao.POSITIVO -> c.success
        TomConclusao.ATENCAO -> c.warning
        TomConclusao.NEGATIVO -> c.error
        TomConclusao.NEUTRO -> c.textSecondary
    }

@Composable
internal fun HistoricoConclusaoTexto(
    texto: String,
    cor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = cor,
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun HistoricoCard(
    medicao: MedicaoEntity,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalLkTokens.current
    var deslocamento by remember { mutableFloatStateOf(0f) }
    var expandido by remember { mutableStateOf(false) }
    // A lista prioriza o padrão reconhecível de histórico: tipo, data/horário e métricas principais.
    // O toque ainda expande os detalhes complementares sem abrir outra tela.
    val conclusao = remember(medicao) { conclusaoDaMedicao(medicao) }
    val corConclusao = corDoTom(conclusao.tom, c)
    val cardDesc =
        "${conclusao.objetivo}, ${formatDate(medicao.timestampEpochMs)}, " +
            "${conclusao.conclusao}, ${tipoLabel(medicao)}"

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(LkRadius.card))) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = LkSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs),
        ) {
            IconButton(
                onClick = {
                    deslocamento = 0f
                    onShare()
                },
                modifier = Modifier.semantics { contentDescription = "Compartilhar resultado" },
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = c.primary)
            }
            IconButton(
                onClick = {
                    deslocamento = 0f
                    onDelete()
                },
                modifier = Modifier.semantics { contentDescription = "Excluir resultado" },
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.error)
            }
        }
        var cardModifier = Modifier.fillMaxWidth()
        cardModifier = cardModifier.offset(x = deslocamento.dp)
        cardModifier = cardModifier.clip(RoundedCornerShape(LkRadius.card))
        cardModifier = cardModifier.background(if (selectionMode && selected) c.secondaryContainer else c.bgPrimary)
        cardModifier =
            cardModifier.pointerInput(medicao.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        deslocamento = (deslocamento + amount).coerceIn(-144f, 0f)
                    },
                    onDragEnd = {
                        deslocamento = if (deslocamento <= -72f) -144f else 0f
                    },
                )
            }
        cardModifier =
            cardModifier.semantics {
                role = Role.Button
                contentDescription = cardDesc
            }
        cardModifier =
            cardModifier.clickable {
                if (deslocamento != 0f) {
                    deslocamento = 0f
                } else if (selectionMode) {
                    onClick()
                } else {
                    expandido = !expandido
                }
            }
        cardModifier = cardModifier.padding(vertical = LkSpacing.md)
        Column(modifier = cardModifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tipoLabel(medicao),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(LkSpacing.md))
                Text(
                    text = formatFullDate(medicao.timestampEpochMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textTertiary,
                )
            }
            Spacer(Modifier.height(LkSpacing.xs))
            HistoricoConclusaoTexto(texto = conclusao.conclusao, cor = corConclusao)
            Spacer(Modifier.height(LkSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HistoricoResumoMetrica("Download", medicao.downloadMbps?.let { "%.1f Mbps".format(it) }, c)
                HistoricoResumoMetrica("Upload", medicao.uploadMbps?.let { "%.1f Mbps".format(it) }, c)
                HistoricoResumoMetrica("Latência", medicao.latencyMs?.let { "%.0f ms".format(it) }, c)
            }
            if (expandido) {
                Spacer(Modifier.height(LkSpacing.md))
                HistoricoDetalhesInline(medicao = medicao, c = c)
            }
            Spacer(Modifier.height(LkSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expandido) "Ocultar detalhes" else "Ver detalhes",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.primary,
                )
                Spacer(Modifier.width(LkSpacing.xs))
                Icon(
                    imageVector = if (expandido) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = c.primary,
                )
            }
            HorizontalDivider(color = c.outlineVariant)
        }
    }
}

@Composable
private fun HistoricoResumoMetrica(
    label: String,
    value: String?,
    c: LkTokens,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Text(value ?: "—", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    }
}

@Composable
private fun HistoricoDetalhesInline(
    medicao: MedicaoEntity,
    c: LkTokens,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LkSpacing.xs)) {
        HistoricoDetalheLinha("Download", medicao.downloadMbps?.let { "%.1f Mbps".format(it) }, c)
        HistoricoDetalheLinha("Upload", medicao.uploadMbps?.let { "%.1f Mbps".format(it) }, c)
        HistoricoDetalheLinha("Latência", medicao.latencyMs?.let { "%.0f ms".format(it) }, c)
        HistoricoDetalheLinha("Oscilação", medicao.jitterMs?.let { "%.0f ms".format(it) }, c)
        HistoricoDetalheLinha("Conexão", tipoLabel(medicao), c)
    }
}

@Composable
private fun HistoricoDetalheLinha(
    label: String,
    value: String?,
    c: LkTokens,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(value ?: "—", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
    }
}

@Composable
private fun HistoricoComparacaoCard(
    primeira: MedicaoEntity,
    segunda: MedicaoEntity,
    c: LkTokens,
) {
    val antiga = if (primeira.timestampEpochMs <= segunda.timestampEpochMs) primeira else segunda
    val recente = if (antiga === primeira) segunda else primeira
    val mesmaRede = antiga.networkId != null && antiga.networkId == recente.networkId
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.surfaceContainer,
        shape = RoundedCornerShape(LkRadius.card),
    ) {
        Column(
            modifier = Modifier.padding(LkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            Text("Comparação", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                "${formatDate(antiga.timestampEpochMs)} → ${formatDate(recente.timestampEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            if (!mesmaRede) {
                Text(
                    "Não dá para comparar estas medições com segurança: elas não têm a mesma rede identificada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.warning,
                )
            } else {
                HistoricoDeltaLinha("Download", antiga.downloadMbps, recente.downloadMbps, "Mbps", c)
                HistoricoDeltaLinha("Upload", antiga.uploadMbps, recente.uploadMbps, "Mbps", c)
                HistoricoDeltaLinha("Latência", antiga.latencyMs, recente.latencyMs, "ms", c)
                HistoricoDeltaLinha("Oscilação", antiga.jitterMs, recente.jitterMs, "ms", c)
            }
        }
    }
}

@Composable
private fun HistoricoDeltaLinha(
    label: String,
    primeiro: Double?,
    segundo: Double?,
    unidade: String,
    c: LkTokens,
) {
    val delta = if (primeiro != null && segundo != null) segundo - primeiro else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Text(
            text = delta?.let { "${if (it >= 0) "+" else ""}${"%.1f".format(it)} $unidade" } ?: "Sem dados",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}

// ─── Detail sheet ─────────────────────────────────────────────────────────────

@Composable
private fun HistoricoDetailSheet(medicao: MedicaoEntity) {
    val c = LocalLkTokens.current
    val dl = medicao.downloadMbps
    val ul = medicao.uploadMbps
    val latency = medicao.latencyMs
    val jitter = medicao.jitterMs
    val perda = medicao.perdaPercentual
    val bufferbloat = medicao.bufferbloatMs
    val streaming = vereditoLabel(medicao.vereditoStreaming)
    val gamer = vereditoLabel(medicao.vereditoGamer)
    val videoChamada = vereditoLabel(medicao.vereditoVideoChamada)
    val gargalo = gargaloLabel(medicao.gargaloPrimario)

    LkSheetFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Detalhes do teste",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Text(
            formatFullDate(medicao.timestampEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(LkSpacing.lg))
        LkSheetDivider()
        Spacer(Modifier.height(LkSpacing.lg))
        Row(Modifier.fillMaxWidth()) {
            PrimaryMetric(
                arrow = "↓",
                arrowColor = c.primary,
                value = dl?.let { "%.1f".format(it) } ?: "--",
                label = "Download",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(72.dp)
                    .background(c.outlineVariant)
                    .align(Alignment.CenterVertically),
            )
            PrimaryMetric(
                arrow = "↑",
                arrowColor = c.success,
                value = ul?.let { "%.1f".format(it) } ?: "--",
                label = "Upload",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(LkSpacing.lg))
        Row(Modifier.fillMaxWidth()) {
            SecondaryMetric("Latência", latency?.let { "%.0f ms".format(it) } ?: "--", Modifier.weight(1f))
            SecondaryMetric("Oscilação", jitter?.let { "%.1f ms".format(it) } ?: "--", Modifier.weight(1f))
            SecondaryMetric("Perda", perda?.let { "%.1f%%".format(it) } ?: "--", Modifier.weight(1f))
        }
        Spacer(Modifier.height(LkSpacing.lg))
        LkSheetDivider()

        if (medicao.fonte == "orbit") {
            // GH#505: accent puro sobre fundo escuro cai a ~3.1:1 (falha WCAG AA) — c.primary
            // já resolve para a variante clara em dark theme (SignallQTheme.kt), sem check manual.
            val origemColor = c.primary
            LkSheetInfoRow("Origem", "Diagnóstico gerado por IA", valueColor = origemColor)
            LkSheetDivider()
        }
        LkSheetInfoRow("Tipo de rede", tipoLabel(medicao))
        LkSheetDivider()
        if (medicao.contaminado) {
            LkSheetInfoRow("Resultado", "Pode não ser confiável", valueColor = c.warning)
            LkSheetDivider()
        }
        if (bufferbloat != null) {
            val (bloatVeredito, bloatColor) = bufferbloatVeredito(bufferbloat, c)
            LkSheetInfoRow("Bufferbloat", "${"%.0f".format(bufferbloat)} ms — $bloatVeredito", valueColor = bloatColor)
            LkSheetDivider()
        }
        // .agents/architecture-plan.md ("Confiabilidade estatística do diagnóstico de rede",
        // passo 6) — aviso de instabilidade pontual só quando a confiança amostral desta
        // medição é insuficiente (1 timeout isolado); nunca aparece pra medição legada (sem
        // essa coluna) nem quando a perda é confirmada/recorrente (comportamento normal).
        val notaInstabilidadePerda =
            copyInstabilidadePerdaPacotes(perda, medicao.perdaConfianca.paraConfiancaAmostral())
        if (notaInstabilidadePerda != null) {
            LkSheetInfoRow("Perda de pacotes", notaInstabilidadePerda, valueColor = c.warning)
            LkSheetDivider()
        }
        // Visão de detalhe técnico: p95/máximo da latência, sempre que existirem (medições
        // gravadas antes desta coluna existir ficam com os dois null — nunca inferido).
        if (medicao.latenciaP95Ms != null) {
            LkSheetInfoRow("Latência (p95)", "%.0f ms".format(medicao.latenciaP95Ms))
            LkSheetDivider()
        }
        if (medicao.latenciaMaxMs != null) {
            LkSheetInfoRow("Latência (máxima)", "%.0f ms".format(medicao.latenciaMaxMs))
            LkSheetDivider()
        }
        if (streaming != null) {
            LkSheetInfoRow("Streaming", streaming, valueColor = historicoVerdictColor(streaming, c))
            LkSheetDivider()
        }
        if (gamer != null) {
            LkSheetInfoRow("Games", gamer, valueColor = historicoVerdictColor(gamer, c))
            LkSheetDivider()
        }
        if (videoChamada != null) {
            LkSheetInfoRow("Vídeo chamada", videoChamada, valueColor = historicoVerdictColor(videoChamada, c))
            LkSheetDivider()
        }
        if (gargalo != null) {
            LkSheetInfoRow("Gargalo identificado", gargalo, valueColor = c.warning)
        }

        val diagTexto = medicao.diagnosticoTexto
        if (!diagTexto.isNullOrBlank()) {
            Spacer(Modifier.height(LkSpacing.lg))
            DiagnosticoHistoricoSection(
                texto = diagTexto,
                origem = medicao.diagnosticoOrigem,
                problemas = medicao.diagnosticoProblemas,
                c = c,
            )
        }
    }
}

@Composable
private fun DiagnosticoHistoricoSection(
    texto: String,
    origem: String?,
    problemas: String?,
    c: LkTokens,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LkRadius.card))
                .background(c.surfaceContainer)
                .padding(LkSpacing.lg),
    ) {
        Overline(texto = "Diagnóstico", color = c.textTertiary)
        Spacer(Modifier.height(LkSpacing.sm))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            lineHeight = 22.sp,
        )
        if (!problemas.isNullOrBlank()) {
            val lista = problemas.split(";").filter { it.isNotBlank() }
            if (lista.isNotEmpty()) {
                Spacer(Modifier.height(LkSpacing.sm))
                lista.forEach { problema ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.warning),
                        )
                        Spacer(Modifier.width(LkSpacing.xs))
                        Text(
                            text = problema,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(LkSpacing.sm))
        // GH#505: accent puro sobre fundo escuro cai a ~3.1:1 (falha WCAG AA) — c.primary
        // já resolve para a variante clara em dark theme (SignallQTheme.kt), sem check manual.
        val origemColor = if (origem == "ia") c.primary else c.textTertiary
        Text(
            text = if (origem == "ia") "Gerado por IA" else "Diagnóstico local",
            style = MaterialTheme.typography.labelMedium,
            color = origemColor,
            fontWeight = FontWeight.W500,
        )
    }
}

private fun historicoVerdictColor(
    label: String,
    c: LkTokens,
): Color =
    when (label) {
        "Bom" -> c.success
        "Aceitável" -> c.warning
        "Ruim" -> c.error
        else -> c.warning
    }

@Composable
private fun PrimaryMetric(
    arrow: String,
    arrowColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalLkTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(arrow, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.W700, color = arrowColor)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.W700,
                color = c.textPrimary,
            )
            Spacer(Modifier.width(4.dp))
            Text("Mbps", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun SecondaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalLkTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.W600, color = c.textPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}
