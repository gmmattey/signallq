package io.signallq.app.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.signallq.app.core.diagnostico.EstagioRede
import io.signallq.app.core.network.contracts.connectivity.ConnectivityDiagnosis
import io.signallq.app.feature.home.MetricasMedicaoHome
import io.signallq.app.feature.home.OrigemMedicaoHome
import io.signallq.app.feature.home.ResolvedHomeMeasurement
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.SignallQTheme
import io.signallq.app.ui.component.SignallQButton
import io.signallq.app.ui.component.SignallQButtonStyle
import io.signallq.app.ui.component.SignallQFeedbackTone
import io.signallq.app.ui.component.SignallQTopAppBar
import java.util.Calendar

@Suppress("LongParameterList")
@Composable
internal fun Inicio2Screen(
    uiState: Inicio2UiState,
    onAnalisarConexao: () -> Long?,
    onAbrirPerfil: () -> Unit,
    onAlternarTema: () -> Unit = {},
    connectionTrail: Inicio2ConnectionTrailState? = null,
    onAbrirVideos: () -> Unit = {},
    statusAoVivo: Inicio2StatusAoVivo? = null,
    diagnosticoBruto: ConnectivityDiagnosis? = null,
    onIniciarSpeedtest: () -> Unit = {},
    onConversarAssist: () -> Unit = onAbrirVideos,
    onVerResultado: () -> Unit = {},
    onAbrirSinalWifi: () -> Unit = {},
    onAbrirJogos: () -> Unit = {},
) {
    val c = LocalLkTokens.current
    var estagioSelecionado by remember { mutableStateOf<String?>(null) }
    var geracaoSolicitada by remember { mutableStateOf<Long?>(null) }
    val iniciarSpeedtestComLatch = {
        if (geracaoSolicitada == null) {
            geracaoSolicitada = onAnalisarConexao()
            onIniciarSpeedtest()
        }
    }
    LaunchedEffect(uiState.geracaoDiagnostico, uiState.analise) {
        val geracao = geracaoSolicitada
        if (geracao != null && uiState.geracaoDiagnostico == geracao && uiState.analise !is Inicio2Analise.Carregando) {
            geracaoSolicitada = null
        }
    }
    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            SignallQTopAppBar(
                title = "Início",
                actions = {
                    IconButton(onClick = onAbrirPerfil) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Abrir ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = LkSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.xl),
        ) {
            // 1. Hero compacto com status ao vivo da conexão
            Inicio2Hero(
                uiState = uiState,
                statusAoVivo = statusAoVivo,
            )

            // 2. Dupla de ações: Fazer teste de velocidade / Conversar com Assist
            Column(
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(LkSpacing.md),
            ) {
                SignallQButton(
                    label = "Fazer teste de velocidade",
                    onClick = iniciarSpeedtestComLatch,
                    modifier = Modifier.fillMaxWidth(),
                    style = SignallQButtonStyle.Primary,
                    leadingIcon = Icons.Outlined.Speed,
                    loading = uiState.analise is Inicio2Analise.Carregando || geracaoSolicitada != null,
                )
                SignallQButton(
                    label = "Conversar com Assist",
                    onClick = onConversarAssist,
                    modifier = Modifier.fillMaxWidth(),
                    style = SignallQButtonStyle.Secondary,
                    leadingIcon = Icons.Outlined.AutoAwesome,
                )
            }

            // 3. Card do Último Teste
            Column(
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
            ) {
                Inicio2UltimoTesteCard(
                    ultimaMedicao = uiState.ultimaMedicao,
                    onVerResultado = onVerResultado,
                )
            }

            // 4. Sua rede
            connectionTrail?.let { trail ->
                Column(
                    modifier = Modifier.padding(horizontal = LkSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sua rede",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                        )
                        Row(
                            modifier =
                                Modifier.clickable {
                                    estagioSelecionado =
                                        trail.nodes.firstOrNull { it.tom != null }?.id ?: "Wi-Fi"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Ver detalhes",
                                style = MaterialTheme.typography.labelLarge,
                                color = c.primary,
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Inicio2ConnectionTrail(
                        state = trail,
                        onEstagioClick = { nodeId -> estagioSelecionado = nodeId },
                    )
                    Text(
                        text = "Esquema da rede; não representa medição individual de cada etapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
            }

            // 5. Explorar
            Column(
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
            ) {
                Text(
                    text = "Explorar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LkSpacing.md),
                ) {
                    Inicio2ExplorarCard(
                        icon = Icons.Outlined.Wifi,
                        titulo = "Sinal do Wi-Fi",
                        subtitulo = "Verificar qualidade",
                        onClick = onAbrirSinalWifi,
                        modifier = Modifier.weight(1f),
                    )
                    Inicio2ExplorarCard(
                        icon = Icons.Outlined.SportsEsports,
                        titulo = "Conexão para jogos",
                        subtitulo = "Avaliar desempenho",
                        onClick = onAbrirJogos,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    val nodeSelecionado = connectionTrail?.nodes?.firstOrNull { it.id == estagioSelecionado }
    val estagioDoNodeSelecionado =
        when (estagioSelecionado) {
            "Equipamento", "Wi-Fi" -> EstagioRede.WIFI
            "Internet" -> EstagioRede.PROVEDOR
            else -> null
        }
    if (nodeSelecionado != null && nodeSelecionado.tom != null && estagioDoNodeSelecionado != null) {
        Inicio2EstagioDetalheSheet(
            label = nodeSelecionado.label,
            estagio = estagioDoNodeSelecionado,
            tom = nodeSelecionado.tom,
            diagnostico = diagnosticoBruto,
            onDismiss = { estagioSelecionado = null },
        )
    }
}

private data class Inicio2HeroCopy(
    val titulo: String,
    val mensagem: String,
    val tone: SignallQFeedbackTone,
    val glifo: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

@Composable
private fun Inicio2Hero(
    uiState: Inicio2UiState,
    statusAoVivo: Inicio2StatusAoVivo? = null,
) {
    val c = LocalLkTokens.current
    val podeUsarStatusAmbiente =
        uiState.conexao == Inicio2Conexao.Wifi &&
            (uiState.analise is Inicio2Analise.StatusEmTempoReal || uiState.analise is Inicio2Analise.SemAnalise)

    val heroCopy =
        when {
            podeUsarStatusAmbiente && statusAoVivo != null -> copyStatusAmbiente(statusAoVivo)
            podeUsarStatusAmbiente ->
                Inicio2HeroCopy(
                    "Verificando sua rede",
                    "Conferindo Wi-Fi e provedor agora.",
                    SignallQFeedbackTone.Neutral,
                    Icons.Outlined.Info,
                )
            else -> copyLegado(uiState.analise)
        }
    val titulo = heroCopy.titulo
    val mensagem = heroCopy.mensagem
    val tone = heroCopy.tone
    val glifo = heroCopy.glifo ?: Icons.Outlined.CheckCircle
    val (connectionLabel, connectionIcon) =
        when (uiState.conexao) {
            Inicio2Conexao.Wifi -> {
                val label =
                    if (uiState.nomeConexao != null) {
                        "Wi-Fi conectado · ${uiState.nomeConexao}"
                    } else {
                        "Wi-Fi conectado"
                    }
                label to Icons.Outlined.Wifi
            }
            Inicio2Conexao.Movel -> "Dados móveis ativos" to Icons.Outlined.Wifi
            Inicio2Conexao.Ethernet -> "Rede cabeada conectada" to Icons.Outlined.Wifi
            Inicio2Conexao.Offline -> "Sem internet" to Icons.Outlined.WarningAmber
            Inicio2Conexao.Carregando -> "Verificando conexão" to Icons.Outlined.Wifi
        }
    val cor = tone.cor(c)

    Column(
        modifier = Modifier.padding(horizontal = LkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LkSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs),
        ) {
            Icon(
                connectionIcon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = connectionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
            )
        }

        Spacer(Modifier.height(LkSpacing.xs))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            Icon(
                imageVector = glifo,
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = titulo,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
        }

        Text(
            text = mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )

        Spacer(Modifier.height(LkSpacing.xs))

        Text(
            text = "Verificado agora",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
        )
    }
}

@Composable
internal fun Inicio2UltimoTesteCard(
    ultimaMedicao: ResolvedHomeMeasurement?,
    onVerResultado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLkTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.bgSecondary,
        border = BorderStroke(1.dp, c.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(LkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Último teste",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                if (ultimaMedicao != null) {
                    Row(
                        modifier = Modifier.clickable(onClick = onVerResultado),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Ver resultado",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.primary,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (ultimaMedicao != null) {
                val metricas = ultimaMedicao.metricas
                val contexto =
                    formatarDataRelativaContexto(
                        timestampEpochMs = metricas.timestampEpochMs,
                        connectionType = metricas.connectionType,
                        ssid = metricas.ssid,
                    )
                Text(
                    text = contexto,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )

                Spacer(Modifier.height(LkSpacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Inicio2MetricaItem(
                        label = "Download",
                        valor = formatarMetricaDownloadUpload(metricas.downloadMbps),
                        unidade = "Mbps",
                        modifier = Modifier.weight(1f),
                    )
                    Inicio2MetricaItem(
                        label = "Upload",
                        valor = formatarMetricaDownloadUpload(metricas.uploadMbps),
                        unidade = "Mbps",
                        modifier = Modifier.weight(1f),
                    )
                    Inicio2MetricaItem(
                        label = "Ping",
                        valor = formatarMetricaPing(metricas.latenciaMs),
                        unidade = "ms",
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = "Nenhum teste recente",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Text(
                    text = "Faça seu primeiro teste de velocidade para acompanhar o desempenho da sua conexão.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Inicio2MetricaItem(
    label: String,
    valor: String,
    unidade: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalLkTokens.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        Text(
            text = unidade,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
        )
    }
}

@Composable
private fun Inicio2ExplorarCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalLkTokens.current
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = c.bgSecondary,
        border = BorderStroke(1.dp, c.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(LkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.xs),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(LkSpacing.xs))
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

internal fun formatarDataRelativaContexto(
    timestampEpochMs: Long?,
    connectionType: String?,
    ssid: String?,
): String {
    if (timestampEpochMs == null || timestampEpochMs <= 0L) return "Teste recente"
    val cal = Calendar.getInstance().apply { timeInMillis = timestampEpochMs }
    val now = Calendar.getInstance()
    val mesmoDia =
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val ontem =
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1

    val horaMin = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val dataStr =
        when {
            mesmoDia -> "Hoje, $horaMin"
            ontem -> "Ontem, $horaMin"
            else -> "%02d/%02d, %s".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, horaMin)
        }

    val tipoStr =
        when (connectionType?.lowercase()) {
            "wifi", "wi-fi" -> if (!ssid.isNullOrBlank()) "Wi-Fi ($ssid)" else "Wi-Fi"
            "cellular", "movel", "móvel" -> "Dados móveis"
            "ethernet", "cabo" -> "Cabo"
            else -> "Wi-Fi"
        }

    return "$dataStr · $tipoStr"
}

internal fun formatarMetricaDownloadUpload(mbps: Double?): String {
    if (mbps == null) return "—"
    return if (mbps >= 100) {
        "%.0f".format(mbps)
    } else {
        "%.1f".format(mbps)
    }
}

internal fun formatarMetricaPing(latenciaMs: Double?): String {
    if (latenciaMs == null) return "—"
    return "%.0f".format(latenciaMs)
}

private fun SignallQFeedbackTone.cor(c: io.signallq.app.ui.LkTokens): Color =
    when (this) {
        SignallQFeedbackTone.Success -> c.success
        SignallQFeedbackTone.Warning -> c.warning
        SignallQFeedbackTone.Error -> c.error
        SignallQFeedbackTone.Neutral -> c.primary
        SignallQFeedbackTone.Incerto -> c.onSurfaceVariant
    }

private fun copyLegado(analise: Inicio2Analise): Inicio2HeroCopy =
    when (analise) {
        is Inicio2Analise.StatusEmTempoReal ->
            if (analise.veredito in listOf("Excelente", "Bom", "Conectado")) {
                Inicio2HeroCopy(
                    "Sua conexão está disponível",
                    "A verificação rápida não encontrou falhas de acesso à internet.",
                    SignallQFeedbackTone.Success,
                    Icons.Outlined.CheckCircle,
                )
            } else {
                Inicio2HeroCopy(
                    tituloConexao(analise.veredito),
                    analise.motivo,
                    analise.veredito.feedbackTone(),
                    when (analise.veredito.feedbackTone()) {
                        SignallQFeedbackTone.Success -> Icons.Outlined.CheckCircle
                        SignallQFeedbackTone.Warning -> Icons.Outlined.WarningAmber
                        SignallQFeedbackTone.Error -> Icons.Outlined.ErrorOutline
                        else -> Icons.Outlined.Info
                    },
                )
            }
        Inicio2Analise.SemAnalise ->
            Inicio2HeroCopy(
                "Internet lenta",
                "Vídeos em HD e chamadas podem travar agora.",
                SignallQFeedbackTone.Neutral,
                Icons.Outlined.Info,
            )
        Inicio2Analise.Carregando ->
            Inicio2HeroCopy(
                "Analisando sua conexão",
                "Estamos reunindo evidências da rede.",
                SignallQFeedbackTone.Neutral,
                Icons.Outlined.Info,
            )
        is Inicio2Analise.Interrompida ->
            Inicio2HeroCopy(
                "Análise interrompida",
                analise.mensagem,
                SignallQFeedbackTone.Error,
                Icons.Outlined.ErrorOutline,
            )
    }

private fun copyStatusAmbiente(status: Inicio2StatusAoVivo): Inicio2HeroCopy =
    when (status.geral) {
        SignallQFeedbackTone.Success ->
            Inicio2HeroCopy(
                "Sua conexão está disponível",
                "A verificação rápida não encontrou falhas de acesso à internet.",
                SignallQFeedbackTone.Success,
                Icons.Outlined.CheckCircle,
            )
        SignallQFeedbackTone.Warning ->
            when (status.causaPrincipal) {
                EstagioRede.WIFI ->
                    Inicio2HeroCopy(
                        "Wi-Fi pode estar instável",
                        "O sinal do seu Wi-Fi está oscilando; vídeos e chamadas podem engasgar.",
                        SignallQFeedbackTone.Warning,
                        Icons.Outlined.WarningAmber,
                    )
                EstagioRede.PROVEDOR ->
                    Inicio2HeroCopy(
                        "Provedor com lentidão",
                        "Sua internet externa está mais lenta que o normal.",
                        SignallQFeedbackTone.Warning,
                        Icons.Outlined.WarningAmber,
                    )
                null ->
                    Inicio2HeroCopy(
                        "Sua conexão pode estar instável",
                        "Alguma parte da sua rede está com lentidão.",
                        SignallQFeedbackTone.Warning,
                        Icons.Outlined.WarningAmber,
                    )
            }
        SignallQFeedbackTone.Error ->
            when (status.causaPrincipal) {
                EstagioRede.WIFI ->
                    Inicio2HeroCopy(
                        "Problema no seu Wi-Fi",
                        "Não conseguimos falar com seu roteador. Aproxime-se dele ou reinicie o Wi-Fi.",
                        SignallQFeedbackTone.Error,
                        Icons.Outlined.ErrorOutline,
                    )
                EstagioRede.PROVEDOR ->
                    Inicio2HeroCopy(
                        "Problema no provedor",
                        "Seu Wi-Fi está bem, mas a internet externa não está respondendo.",
                        SignallQFeedbackTone.Error,
                        Icons.Outlined.ErrorOutline,
                    )
                null ->
                    Inicio2HeroCopy(
                        "Encontramos um problema na sua conexão",
                        "Alguma parte da sua rede não está respondendo.",
                        SignallQFeedbackTone.Error,
                        Icons.Outlined.ErrorOutline,
                    )
            }
        SignallQFeedbackTone.Incerto ->
            Inicio2HeroCopy(
                "Não conseguimos confirmar sua conexão",
                "Vamos continuar checando; faça um teste para um diagnóstico completo.",
                SignallQFeedbackTone.Incerto,
                Icons.Outlined.HelpOutline,
            )
        SignallQFeedbackTone.Neutral ->
            Inicio2HeroCopy(
                "Verificando sua rede",
                "Conferindo Wi-Fi e provedor agora.",
                SignallQFeedbackTone.Neutral,
                Icons.Outlined.Info,
            )
    }

internal fun String.feedbackTone(): SignallQFeedbackTone =
    when (this) {
        "Excelente", "Bom" -> SignallQFeedbackTone.Success
        "Regular" -> SignallQFeedbackTone.Warning
        "Fraco" -> SignallQFeedbackTone.Error
        else -> SignallQFeedbackTone.Neutral
    }

internal fun tituloConexao(veredito: String): String =
    when (veredito) {
        "Excelente" -> "Conexão excelente"
        "Bom" -> "Conexão boa"
        "Regular" -> "Conexão regular"
        "Fraco" -> "Conexão fraca"
        else -> "Conexão ${veredito.lowercase()}"
    }

@Preview(name = "Início 2 claro", showBackground = true)
@Composable
private fun Inicio2ScreenPreview() {
    SignallQTheme {
        Inicio2Screen(
            uiState =
                Inicio2UiState(
                    conexao = Inicio2Conexao.Wifi,
                    nomeConexao = "Casa",
                    analise = Inicio2Analise.StatusEmTempoReal("Excelente", "Sinal Wi-Fi forte e conexões estáveis."),
                    ultimaMedicao =
                        ResolvedHomeMeasurement(
                            metricas =
                                MetricasMedicaoHome(
                                    downloadMbps = 450.0,
                                    uploadMbps = 120.0,
                                    latenciaMs = 14.0,
                                    jitterMs = 2.0,
                                    perdaPercentual = 0.0,
                                    timestampEpochMs = System.currentTimeMillis() - 3600000L,
                                    connectionType = "Wi-Fi",
                                    ssid = "Casa",
                                    vereditoGamer = null,
                                    gargaloPrimario = null,
                                    utilizavel = true,
                                ),
                            origem = OrigemMedicaoHome.ANTERIOR,
                        ),
                ),
            onAnalisarConexao = { null },
            onAbrirPerfil = {},
            connectionTrail =
                Inicio2ConnectionTrailState(
                    nodes =
                        listOf(
                            Inicio2TrailNode("Internet", "Internet", "Conectada"),
                            Inicio2TrailNode("Equipamento", "Equipamento principal", "Roteador ou modem"),
                            Inicio2TrailNode("Wi-Fi", "Casa", "Casa"),
                            Inicio2TrailNode("Este aparelho", "Este aparelho", "Conectado por Wi-Fi"),
                        ),
                    supportingMessage = null,
                ),
        )
    }
}

@Preview(name = "Início 2 escuro 200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun Inicio2ScreenDarkPreview() {
    SignallQTheme {
        Inicio2Screen(
            uiState =
                Inicio2UiState(
                    conexao = Inicio2Conexao.Offline,
                    nomeConexao = null,
                    analise = Inicio2Analise.Interrompida("Seu contexto foi preservado."),
                ),
            onAnalisarConexao = { null },
            onAbrirPerfil = {},
            connectionTrail =
                Inicio2ConnectionTrailState(
                    nodes =
                        listOf(
                            Inicio2TrailNode("Internet", "Internet", "Sem acesso"),
                            Inicio2TrailNode("Este aparelho", "Este aparelho", "Sem conexão ativa"),
                        ),
                    supportingMessage = "Conecte-se a uma rede para completar a trilha.",
                ),
        )
    }
}
