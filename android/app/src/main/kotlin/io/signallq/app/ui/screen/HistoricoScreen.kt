package io.signallq.app.ui.screen

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.signallq.app.ads.AdSlot
import io.signallq.app.ads.AdUnitIds
import io.signallq.app.ads.NativeAdContentSignal
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.diagnostico.MetricStatus
import io.signallq.app.feature.history.BlocoUptime
import io.signallq.app.feature.history.ResumoHistorico
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
import io.signallq.app.ui.component.ads.NativeAdCard
import io.signallq.app.ui.component.ads.NativeAdSource
import io.signallq.app.ui.component.classificarBufferbloatLocal
import kotlinx.coroutines.launch
import java.util.Calendar

enum class FiltroPeriodoHistorico(
    val label: String,
) {
    SETE_DIAS("7 dias"),
    TRINTA_DIAS("30 dias"),
    TUDO("Tudo"),
}

enum class FiltroTipo(
    val label: String,
) {
    TODOS("Todas"),
    WIFI("Wi-Fi"),
    MOVEL("Móvel"),
}

enum class OrdenacaoHistorico { MAIS_RECENTES, MAIS_ANTIGAS }

internal fun tipoLabel(medicao: MedicaoEntity): String =
    when (medicao.connectionType.lowercase()) {
        "wifi", "wi-fi" -> "Wi-Fi${bandaWifiSufixo(medicao.bandaWifi)}"
        "cellular", "movel", "móvel" -> "Celular"
        "ethernet", "cabo" -> "Cabo"
        else -> "Wi-Fi"
    }

internal fun bandaWifiSufixo(bandaWifi: String?): String =
    when (bandaWifi) {
        "ghz24" -> " · 2.4GHz"
        "ghz5" -> " · 5GHz"
        else -> ""
    }

internal fun formatDate(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val d = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
    val m = "%02d".format(cal.get(Calendar.MONTH) + 1)
    return "$d/$m"
}

internal fun formatFullDate(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val d = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
    val mo = "%02d".format(cal.get(Calendar.MONTH) + 1)
    val y = cal.get(Calendar.YEAR)
    val h = "%02d".format(cal.get(Calendar.HOUR_OF_DAY))
    val m = "%02d".format(cal.get(Calendar.MINUTE))
    return "$d/$mo/$y às $h:$m"
}

internal fun formatarDataRelativaHistorico(timestampEpochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestampEpochMs }
    val now = Calendar.getInstance()
    val mesmoDia =
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val ontem =
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1

    val horaMin = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    return when {
        mesmoDia -> "Hoje · $horaMin"
        ontem -> "Ontem · $horaMin"
        else -> {
            val meses = arrayOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
            val dia = cal.get(Calendar.DAY_OF_MONTH)
            val mes = meses[cal.get(Calendar.MONTH)]
            "$dia $mes · $horaMin"
        }
    }
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

internal fun eligibilidadeAnuncioHistorico(adsGate: io.signallq.app.ads.NativeAdsGate): NativeAdEligibility =
    adsGate.eligibilityFor(AdSlot.HISTORICO)

internal fun eligibilidadeAnuncioHistorico(adsEnabled: Boolean): NativeAdEligibility =
    NativeAdEligibility(
        AdSlot.HISTORICO,
        buildEnabled = true,
        flagEnabled = adsEnabled,
        canRequestAds = adsEnabled,
        online = true,
    )

@Suppress("LongParameterList")
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
    blocosUptime: List<BlocoUptime> = emptyList(),
    adsGate: io.signallq.app.ads.NativeAdsGate =
        io.signallq.app.ads
            .NativeAdsGate(),
    onConversarAssist: (MedicaoEntity) -> Unit = {},
) {
    val c = LocalLkTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filtroPeriodo by remember { mutableStateOf(FiltroPeriodoHistorico.SETE_DIAS) }
    var filtroConexaoInterno by remember { mutableStateOf(FiltroTipo.TODOS) }
    var medicaoParaDetalhe by remember { mutableStateOf<MedicaoEntity?>(null) }
    var mostrarComparacaoSheet by remember { mutableStateOf(false) }
    var mostrarExport by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 1. Filtragem por Período
    val agora = remember { System.currentTimeMillis() }
    val limitePeriodoMs =
        when (filtroPeriodo) {
            FiltroPeriodoHistorico.SETE_DIAS -> agora - (7L * 24 * 60 * 60 * 1000L)
            FiltroPeriodoHistorico.TRINTA_DIAS -> agora - (30L * 24 * 60 * 60 * 1000L)
            FiltroPeriodoHistorico.TUDO -> 0L
        }

    val listaSemMonitor = remember(historico) { historico.filter { it.fonte != "monitor" } }
    val listaPorPeriodo =
        remember(listaSemMonitor, limitePeriodoMs) {
            listaSemMonitor.filter { it.timestampEpochMs >= limitePeriodoMs }
        }

    // 2. Filtragem por Tipo de Conexão
    val filtroConexaoAtivo =
        when {
            filtroConexao != null ->
                when (filtroConexao) {
                    FiltroConexaoHistorico.TODOS -> FiltroTipo.TODOS
                    FiltroConexaoHistorico.WIFI -> FiltroTipo.WIFI
                    FiltroConexaoHistorico.MOVEL -> FiltroTipo.MOVEL
                }
            else -> filtroConexaoInterno
        }

    val listaFiltrada =
        remember(listaPorPeriodo, filtroConexaoAtivo) {
            listaPorPeriodo
                .filter { m ->
                    when (filtroConexaoAtivo) {
                        FiltroTipo.TODOS -> true
                        FiltroTipo.WIFI -> m.connectionType.lowercase() in listOf("wifi", "wi-fi")
                        FiltroTipo.MOVEL -> m.connectionType.lowercase() in listOf("cellular", "movel", "móvel")
                    }
                }.sortedByDescending { it.timestampEpochMs }
        }

    val totalTestesPeriodo = listaPorPeriodo.size
    val ultimoDownloadPeriodo = listaFiltrada.maxByOrNull { it.timestampEpochMs }?.downloadMbps

    Scaffold(
        containerColor = c.bgPrimary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Histórico",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch { mostrarExport = true }
                        },
                        enabled = listaFiltrada.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Exportar histórico",
                            tint = if (listaFiltrada.isNotEmpty()) c.textPrimary else c.textTertiary,
                        )
                    }
                    IconButton(onClick = onAbrirMenu) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Abrir ajustes",
                            tint = c.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        val filtroAtivo = filtroConexaoAtivo != FiltroTipo.TODOS || filtroPeriodo != FiltroPeriodoHistorico.SETE_DIAS

        if (listaFiltrada.isEmpty() && !filtroAtivo && historico.isEmpty()) {
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
                contentPadding = PaddingValues(horizontal = LkSpacing.lg, vertical = LkSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LkSpacing.md),
            ) {
                // 1. Filtro de período em pills
                item(key = "filtro_periodo") {
                    FiltroPeriodoBar(
                        selecionado = filtroPeriodo,
                        onSelect = { filtroPeriodo = it },
                        c = c,
                    )
                }

                // 2. Resumo do período
                item(key = "resumo_periodo") {
                    HistoricoResumoPeriodoCard(
                        totalTestes = totalTestesPeriodo,
                        ultimoDownloadMbps = ultimoDownloadPeriodo,
                        c = c,
                    )
                }

                // 3. Cabeçalho de medições com filtro de conexão
                item(key = "medicoes_header") {
                    HistoricoMedicoesHeader(
                        filtroSelecionado = filtroConexaoAtivo,
                        onFiltroChange = { novo ->
                            if (filtroConexao != null) {
                                val enumVal =
                                    when (novo) {
                                        FiltroTipo.TODOS -> FiltroConexaoHistorico.TODOS
                                        FiltroTipo.WIFI -> FiltroConexaoHistorico.WIFI
                                        FiltroTipo.MOVEL -> FiltroConexaoHistorico.MOVEL
                                    }
                                onFiltroConexaoChange(enumVal)
                            } else {
                                filtroConexaoInterno = novo
                            }
                        },
                        c = c,
                    )
                }

                // 4. Lista de medições
                if (listaFiltrada.isEmpty()) {
                    item(key = "empty_filtro") {
                        EmptyHistorico(
                            modifier = Modifier.fillMaxWidth().padding(vertical = LkSpacing.xxl),
                            onIniciarTeste = onIniciarTeste,
                            filtroAtivo = true,
                        )
                    }
                } else {
                    itemsIndexed(listaFiltrada, key = { _, m -> m.id }) { index, medicao ->
                        HistoricoCompactCard(
                            medicao = medicao,
                            onClick = { medicaoParaDetalhe = medicao },
                            c = c,
                        )
                        HorizontalDivider(
                            color = c.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
                        )

                        // Anúncio nativo após o 3º item (quando elegível)
                        if (index == 2) {
                            val nativeAdState by rememberNativeAdState(
                                adUnitId = AdUnitIds.para(AdSlot.HISTORICO),
                                contentSignal = NativeAdContentSignal.forSlot(AdSlot.HISTORICO),
                                eligibility = eligibilidadeAnuncioHistorico(adsGate),
                            )
                            val nativeAd = (nativeAdState as? NativeAdLoadState.Fill)?.ad
                            NativeAdCard(nativeAd = nativeAd, source = NativeAdSource.ADMOB)
                        }
                    }

                    // 5. Card Comparar testes
                    item(key = "card_comparar") {
                        CardCompararTestes(
                            onClick = { mostrarComparacaoSheet = true },
                            c = c,
                            modifier = Modifier.padding(top = LkSpacing.sm),
                        )
                    }
                }
            }
        }
    }

    // Modal de Detalhe Completo do Teste
    medicaoParaDetalhe?.let { medicao ->
        ModalBottomSheet(
            onDismissRequest = { medicaoParaDetalhe = null },
            containerColor = c.bgCard,
        ) {
            HistoricoDetailContent(
                medicao = medicao,
                c = c,
                onConversarAssist = {
                    medicaoParaDetalhe = null
                    onConversarAssist(medicao)
                },
                onShare = {
                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, textoCompartilhamento(medicao))
                        }
                    context.startActivity(Intent.createChooser(intent, "Compartilhar resultado"))
                },
                onDelete = {
                    onExcluirMedicao(medicao.id)
                    medicaoParaDetalhe = null
                },
            )
        }
    }

    // Modal de Comparação de Testes
    if (mostrarComparacaoSheet) {
        ModalBottomSheet(
            onDismissRequest = { mostrarComparacaoSheet = false },
            containerColor = c.bgCard,
        ) {
            HistoricoComparacaoModalSheet(
                medicoes = listaPorPeriodo,
                c = c,
                onDismiss = { mostrarComparacaoSheet = false },
            )
        }
    }

    // Modal de Exportação
    if (mostrarExport) {
        val sheetExportState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { mostrarExport = false },
            sheetState = sheetExportState,
            containerColor = c.bgCard,
        ) {
            ExportHistoricoBottomSheet(
                historico = listaFiltrada,
                snackbarHostState = snackbarHostState,
                onDismiss = { mostrarExport = false },
            )
        }
    }
}

@Composable
private fun FiltroPeriodoBar(
    selecionado: FiltroPeriodoHistorico,
    onSelect: (FiltroPeriodoHistorico) -> Unit,
    c: LkTokens,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
    ) {
        FiltroPeriodoHistorico.entries.forEach { periodo ->
            val isSelected = selecionado == periodo
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(LkRadius.pill))
                        .clickable { onSelect(periodo) },
                shape = RoundedCornerShape(LkRadius.pill),
                color = if (isSelected) c.secondaryContainer else Color.Transparent,
                border = if (!isSelected) BorderStroke(1.dp, c.outlineVariant) else null,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = LkSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = periodo.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) c.onSecondaryContainer else c.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoricoResumoPeriodoCard(
    totalTestes: Int,
    ultimoDownloadMbps: Double?,
    c: LkTokens,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.bgSecondary,
        border = BorderStroke(1.dp, c.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(LkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RESUMO DO PERÍODO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary,
                    letterSpacing = 0.5.sp,
                )
                Icon(
                    imageVector = Icons.Outlined.Insights,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs),
            ) {
                Text(
                    text = "$totalTestes",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(
                    text = if (totalTestes == 1) "teste encontrado" else "testes encontrados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Último download registrado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                Text(
                    text = ultimoDownloadMbps?.let { "${formatarMetricaDownloadUpload(it)} Mbps" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun HistoricoMedicoesHeader(
    filtroSelecionado: FiltroTipo,
    onFiltroChange: (FiltroTipo) -> Unit,
    c: LkTokens,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LkSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Medições",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FiltroTipo.entries.forEach { filtro ->
                val isSelected = filtroSelecionado == filtro
                Surface(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(LkRadius.pill))
                            .clickable { onFiltroChange(filtro) },
                    shape = RoundedCornerShape(LkRadius.pill),
                    color = if (isSelected) c.secondaryContainer else Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = LkSpacing.sm, vertical = LkSpacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = filtro.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) c.onSecondaryContainer else c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoricoCompactCard(
    medicao: MedicaoEntity,
    onClick: () -> Unit,
    c: LkTokens,
    modifier: Modifier = Modifier,
) {
    val isWifi = medicao.connectionType.lowercase() in listOf("wifi", "wi-fi")
    val icon = if (isWifi) Icons.Outlined.Wifi else Icons.Outlined.SignalCellularAlt
    val tipo = if (isWifi) "Wi-Fi" else "Rede móvel"
    val dataHora = formatarDataRelativaHistorico(medicao.timestampEpochMs)
    val conclusao = remember(medicao) { conclusaoDaMedicao(medicao) }
    val corConclusao = corDoTom(conclusao.tom, c)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = LkSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(LkSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$tipo · $dataHora",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Ver detalhes",
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = medicao.downloadMbps?.let { formatarMetricaDownloadUpload(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(
                    text = "Mbps download",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = medicao.latencyMs?.let { "%.0f ms".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(
                    text = "ping",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        HistoricoConclusaoTexto(
            texto = conclusao.conclusao,
            cor = corConclusao,
            modifier = Modifier.testTag("historico_conclusao"),
        )
    }
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
private fun CardCompararTestes(
    onClick: () -> Unit,
    c: LkTokens,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = c.bgSecondary,
        border = BorderStroke(1.dp, c.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(LkSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.md),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Comparar testes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(
                    text = "Selecione dois resultados da mesma rede",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HistoricoDetailContent(
    medicao: MedicaoEntity,
    c: LkTokens,
    onConversarAssist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val dl = medicao.downloadMbps
    val ul = medicao.uploadMbps
    val latency = medicao.latencyMs
    val jitter = medicao.jitterMs
    val perda = medicao.perdaPercentual
    val bufferbloat = medicao.bufferbloatMs

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
            formatFullDate(medicao.timestampEpochMs) + " · " + tipoLabel(medicao),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )

        Spacer(Modifier.height(LkSpacing.lg))
        LkSheetDivider()
        Spacer(Modifier.height(LkSpacing.lg))

        // Métricas Primárias
        Row(Modifier.fillMaxWidth()) {
            PrimaryMetric(
                arrow = "↓",
                arrowColor = c.primary,
                value = dl?.let { "%.1f".format(it) } ?: "—",
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
                arrowColor = c.primary,
                value = ul?.let { "%.1f".format(it) } ?: "—",
                label = "Upload",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(LkSpacing.lg))

        // Métricas Secundárias
        Row(Modifier.fillMaxWidth()) {
            SecondaryMetric(
                label = "Latência",
                value = latency?.let { "%.0f ms".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            SecondaryMetric(
                label = "Oscilação",
                value = jitter?.let { "%.0f ms".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            SecondaryMetric(
                label = "Perda",
                value = perda?.let { "%.0f%%".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }

        bufferbloat?.let { b ->
            Spacer(Modifier.height(LkSpacing.md))
            val (veredito, cor) = bufferbloatVeredito(b, c)
            LkSheetInfoRow(
                label = "Bufferbloat",
                value = "+%.0f ms (%s)".format(b, veredito),
                valueColor = cor,
            )
        }

        // Análise de IA (se gravada)
        val diagTexto = medicao.diagnosticoTexto
        if (!diagTexto.isNullOrBlank()) {
            Spacer(Modifier.height(LkSpacing.lg))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = c.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(LkSpacing.md)) {
                    Text(
                        text = if (medicao.diagnosticoOrigem == "ia") "Interpretação da IA" else "Diagnóstico",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (medicao.diagnosticoOrigem == "ia") c.primary else c.textSecondary,
                    )
                    Spacer(Modifier.height(LkSpacing.xs))
                    Text(
                        text = diagTexto,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                    )
                }
            }
        }

        Spacer(Modifier.height(LkSpacing.lg))

        // Ações
        Button(
            onClick = onConversarAssist,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(LkSpacing.sm))
            Text("Conversar com Assist sobre este teste")
        }

        Spacer(Modifier.height(LkSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(LkSpacing.xs))
                Text("Compartilhar")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = c.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(LkSpacing.xs))
                Text("Excluir", color = c.error)
            }
        }
    }
}

@Composable
private fun HistoricoComparacaoModalSheet(
    medicoes: List<MedicaoEntity>,
    c: LkTokens,
    onDismiss: () -> Unit,
) {
    val selecionadas = remember { mutableStateListOf<String>() }

    LkSheetFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Comparação de testes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        Text(
            text = "Selecione duas medições para avaliar a evolução da sua conexão.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )

        Spacer(Modifier.height(LkSpacing.md))
        LkSheetDivider()
        Spacer(Modifier.height(LkSpacing.md))

        if (selecionadas.size == 2) {
            val m1 = medicoes.firstOrNull { it.id == selecionadas[0] }
            val m2 = medicoes.firstOrNull { it.id == selecionadas[1] }

            if (m1 != null && m2 != null) {
                val anterior = if (m1.timestampEpochMs <= m2.timestampEpochMs) m1 else m2
                val atual = if (anterior === m1) m2 else m1
                val mesmaRede = anterior.connectionType == atual.connectionType

                if (!mesmaRede) {
                    Text(
                        text = "Atenção: Estes testes foram feitos em redes diferentes (${tipoLabel(anterior)} vs ${tipoLabel(atual)}). A comparação direta pode ser imprecisa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.warning,
                    )
                    Spacer(Modifier.height(LkSpacing.md))
                }

                // Tabela comparativa
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.bgSecondary)
                            .padding(LkSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Métrica", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text("Anterior", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("Atual", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }

                    HorizontalDivider(color = c.outlineVariant)

                    LinhaComparativaTabela("Download", anterior.downloadMbps, atual.downloadMbps, "Mbps", c)
                    LinhaComparativaTabela("Upload", anterior.uploadMbps, atual.uploadMbps, "Mbps", c)
                    LinhaComparativaTabela("Ping", anterior.latencyMs, atual.latencyMs, "ms", c, inverterMelhor = true)
                }

                Spacer(Modifier.height(LkSpacing.md))

                val dlMelhor = (atual.downloadMbps ?: 0.0) >= (anterior.downloadMbps ?: 0.0)
                val ulMelhor = (atual.uploadMbps ?: 0.0) >= (anterior.uploadMbps ?: 0.0)
                val pingMelhor = (atual.latencyMs ?: 999.0) <= (anterior.latencyMs ?: 999.0)

                val interpretacao =
                    when {
                        dlMelhor && ulMelhor && pingMelhor -> "O resultado atual foi melhor nestas três métricas."
                        !dlMelhor && !ulMelhor && !pingMelhor -> "O resultado atual teve desempenho inferior nas três métricas."
                        else -> "O resultado atual melhorou em alguns pontos e oscilou em outros."
                    }

                Text(
                    text = interpretacao,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Spacer(Modifier.height(LkSpacing.xs))
                Text(
                    text = "Isso não comprova que a conexão melhorou permanentemente. Novos testes em condições semelhantes ajudam a confirmar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )

                Spacer(Modifier.height(LkSpacing.lg))
                Button(onClick = { selecionadas.clear() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Selecionar outros testes")
                }
            }
        } else {
            Text(
                text = "Toque em duas medições da lista abaixo:",
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(LkSpacing.sm))

            medicoes.take(10).forEach { m ->
                val isSelected = m.id in selecionadas
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (isSelected) {
                                    selecionadas.remove(m.id)
                                } else if (selecionadas.size < 2) {
                                    selecionadas.add(m.id)
                                }
                            },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) c.secondaryContainer else c.bgSecondary,
                    border = BorderStroke(1.dp, if (isSelected) c.primary else c.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(LkSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = tipoLabel(m) + " · " + formatFullDate(m.timestampEpochMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                            Text(
                                text = "${m.downloadMbps?.let { "%.0f Mbps".format(it) } ?: "—"} / ${m.latencyMs?.let { "%.0f ms".format(it) } ?: "—"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary,
                            )
                        }
                        Text(
                            text = if (isSelected) "Selecionado" else "Escolher",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) c.primary else c.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaComparativaTabela(
    label: String,
    anterior: Double?,
    atual: Double?,
    unidade: String,
    c: LkTokens,
    inverterMelhor: Boolean = false,
) {
    val antStr = anterior?.let { "%.0f %s".format(it, unidade) } ?: "—"
    val atStr = atual?.let { "%.0f %s".format(it, unidade) } ?: "—"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(antStr, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(atStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

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
                    "Não há medições para o filtro selecionado.\nTente selecionar outro período ou tipo de conexão."
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
