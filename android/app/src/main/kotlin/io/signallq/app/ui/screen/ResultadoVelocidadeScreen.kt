package io.signallq.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.signallq.app.ads.AdSlot
import io.signallq.app.ads.AdUnitIds
import io.signallq.app.ads.NativeAdContentSignal
import io.signallq.app.ads.NativeAdsGate
import io.signallq.app.feature.diagnostico.SnapshotDiagnostico
import io.signallq.app.feature.speedtest.DiagnosticoQualidadeSpeedtest
import io.signallq.app.feature.speedtest.ResultadoSpeedtest
import io.signallq.app.feature.speedtest.VereditoUso
import io.signallq.app.ui.IspInfo
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.ResultadoPdfGenerator
import io.signallq.app.ui.ads.NativeAdEligibility
import io.signallq.app.ui.ads.NativeAdLoadState
import io.signallq.app.ui.ads.rememberNativeAdState
import io.signallq.app.ui.component.LkInfoCallout
import io.signallq.app.ui.component.LkSectionOverline
import io.signallq.app.ui.component.ads.NativeAdCard
import io.signallq.app.ui.component.ads.NativeAdSource
import io.signallq.app.ui.component.classificarBufferbloatLocal
import io.signallq.app.ui.component.classificarJitterLocal
import io.signallq.app.ui.component.classificarPerdaPacotesLocal
import io.signallq.app.ui.component.copyInstabilidadePerdaPacotes
import io.signallq.app.ui.component.corSemantica
import io.signallq.app.ui.component.labelPt
import io.signallq.app.ui.share.CardShareFormat
import io.signallq.app.ui.share.CompartilharSheet
import io.signallq.app.ui.share.OpcaoCompartilhamento
import io.signallq.app.ui.share.ResultadoCardImageGenerator
import io.signallq.app.ui.share.SpeedtestShareData
import kotlinx.coroutines.launch

/** Mapeamento de elegibilidade de anúncio nativo no resultado. */
internal fun eligibilidadeAnuncioResultado(adsGate: NativeAdsGate): NativeAdEligibility =
    adsGate.eligibilityFor(AdSlot.RESULTADO)

/** Compatibilidade para testes legados; o fluxo de produção usa [NativeAdsGate]. */
internal fun eligibilidadeAnuncioResultado(adsEnabled: Boolean): NativeAdEligibility =
    NativeAdEligibility(AdSlot.RESULTADO, buildEnabled = true, flagEnabled = adsEnabled, canRequestAds = adsEnabled, online = true)

/** Mensagem de erro ao compartilhar PDF ou imagem do resultado. */
internal fun mensagemErroCompartilhamentoResultado(erro: Throwable): String =
    "Não foi possível compartilhar o resultado: ${erro.message}"

/** Mensagem informativa sobre a confiabilidade da medição. */
internal fun mensagemIntegridadeResultado(faseInterrompida: String): String =
    if (faseInterrompida.contains("redeMudou", ignoreCase = true)) {
        "O teste foi interrompido porque a conexão caiu ou mudou durante a medição. Tente novamente quando a rede estabilizar."
    } else {
        "Outros aplicativos podem ter afetado o resultado."
    }

/**
 * Tela de Resultado simplificada — resultado direto, explicação curta e ação principal para o Assist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultadoVelocidadeScreen(
    resultado: ResultadoSpeedtest,
    snapshotDiagnostico: SnapshotDiagnostico,
    onTestarNovamente: () -> Unit,
    onIrParaHome: () -> Unit,
    onVoltar: () -> Unit = {},
    onCompartilhar: () -> Unit = {},
    localizacaoServidor: String? = null,
    ispInfo: IspInfo? = null,
    operadoraMovel: String? = null,
    analisadorState: AnalisadorState = AnalisadorState.Inativo,
    onIniciarDiagnosticoGuiado: () -> Unit = {},
    onIniciarModoGamer: () -> Unit = {},
    onVerDetalhesTecnicos: () -> Unit = {},
    adsGate: NativeAdsGate = NativeAdsGate(),
) {
    val c = LocalLkTokens.current
    val scrollState = rememberScrollState()
    val decisao = snapshotDiagnostico.relatorio?.decisao
    val decisaoTitulo = decisao?.titulo
    val decisaoMensagem = decisao?.mensagemUsuario
    var compartilhando by remember { mutableStateOf(false) }
    var erroCompartilhamento by remember { mutableStateOf<String?>(null) }
    var mostrarSheetCompartilhamento by remember { mutableStateOf(false) }
    var detalhesExpandidos by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Resultado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = c.textPrimary,
                        )
                    }
                },
                actions = {
                    if (compartilhando) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .padding(end = LkSpacing.sm),
                            strokeWidth = 2.dp,
                            color = c.primary,
                        )
                    } else {
                        IconButton(onClick = {
                            erroCompartilhamento = null
                            mostrarSheetCompartilhamento = true
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Compartilhar resultado",
                                tint = c.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(c.bgPrimary)
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = LkSpacing.xl, vertical = LkSpacing.lg),
        ) {
            if (erroCompartilhamento != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LkRadius.card))
                            .background(c.error.copy(alpha = 0.12f))
                            .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LkInfoCallout(
                        icon = Icons.Outlined.Warning,
                        text = erroCompartilhamento!!,
                        iconTint = c.error,
                    )
                }
                Spacer(Modifier.height(LkSpacing.md))
            }

            CabecalhoResultado(
                connectionType = resultado.connectionType,
                tecnologia = resultado.tecnologia,
                decisaoTitulo = decisaoTitulo,
                decisaoMensagem = decisaoMensagem,
                contaminado = resultado.contaminado,
                faseInterrompida = resultado.diagnosticoFases.faseInterrompida,
                c = c,
            )

            Spacer(Modifier.height(LkSpacing.xl))

            SecaoSeuResultado(resultado = resultado, c = c)

            Spacer(Modifier.height(LkSpacing.xl))

            SecaoComoDeveFuncionar(qualidade = resultado.diagnosticoQualidade, c = c)

            Spacer(Modifier.height(LkSpacing.xl))
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(LkSpacing.xl))

            SecaoAssistCta(
                contaminado = resultado.contaminado,
                uploadNaoDetectado = resultado.uploadNaoDetectado,
                onConversarAssist = onIniciarDiagnosticoGuiado,
                onTestarNovamente = onTestarNovamente,
                c = c,
            )

            Spacer(Modifier.height(LkSpacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onTestarNovamente) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = c.textPrimary,
                    )
                    Spacer(Modifier.width(LkSpacing.xs))
                    Text(
                        text = "Refazer teste",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                }

                TextButton(onClick = { detalhesExpandidos = !detalhesExpandidos }) {
                    Text(
                        text = "Mais detalhes",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.width(LkSpacing.xs))
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp).rotate(if (detalhesExpandidos) 180f else 0f),
                    )
                }
            }

            AnimatedVisibility(visible = detalhesExpandidos) {
                SecaoMaisDetalhes(
                    resultado = resultado,
                    localizacaoServidor = localizacaoServidor,
                    ispInfo = ispInfo,
                    operadoraMovel = operadoraMovel,
                    adsGate = adsGate,
                    onVerDetalhesTecnicos = onVerDetalhesTecnicos,
                    onIniciarModoGamer = onIniciarModoGamer,
                    c = c,
                )
            }

            Spacer(Modifier.height(LkSpacing.xxl))
        }
    }

    if (mostrarSheetCompartilhamento) {
        CompartilharSheet(
            c = c,
            mostrarOpcaoPdf = true,
            onSelecionarOpcao = { opcao ->
                compartilhando = true
                erroCompartilhamento = null
                scope.launch {
                    try {
                        when (opcao) {
                            OpcaoCompartilhamento.FEED -> {
                                ResultadoCardImageGenerator.gerarECompartilhar(
                                    context = context,
                                    data =
                                        SpeedtestShareData(
                                            downloadMbps = resultado.downloadMbps,
                                            uploadMbps = resultado.uploadMbps,
                                            pingMs = kotlin.math.round(resultado.latenciaMs).toInt(),
                                        ),
                                    format = CardShareFormat.FEED,
                                )
                            }
                            OpcaoCompartilhamento.STORY -> {
                                ResultadoCardImageGenerator.gerarECompartilhar(
                                    context = context,
                                    data =
                                        SpeedtestShareData(
                                            downloadMbps = resultado.downloadMbps,
                                            uploadMbps = resultado.uploadMbps,
                                            pingMs = kotlin.math.round(resultado.latenciaMs).toInt(),
                                        ),
                                    format = CardShareFormat.STORY,
                                )
                            }
                            OpcaoCompartilhamento.PDF -> {
                                ResultadoPdfGenerator.gerarECompartilhar(
                                    context = context,
                                    resultado = resultado,
                                    snapshotDiagnostico = snapshotDiagnostico,
                                    analisadorState = analisadorState,
                                    ispInfo = ispInfo,
                                    operadoraMovel = operadoraMovel,
                                    localizacaoServidor = localizacaoServidor,
                                )
                            }
                        }
                        onCompartilhar()
                    } catch (e: Exception) {
                        erroCompartilhamento = mensagemErroCompartilhamentoResultado(e)
                    } finally {
                        compartilhando = false
                    }
                }
            },
            onDismiss = { mostrarSheetCompartilhamento = false },
        )
    }
}

@Composable
private fun CabecalhoResultado(
    connectionType: String?,
    tecnologia: String?,
    decisaoTitulo: String?,
    decisaoMensagem: String?,
    contaminado: Boolean,
    faseInterrompida: String,
    c: LkTokens,
) {
    val ehMovel = connectionType.equals("movel", ignoreCase = true)
    val tipoRedeRotulo =
        when {
            ehMovel -> {
                when {
                    tecnologia?.contains("5G", ignoreCase = true) == true -> "5G · Teste concluído"
                    tecnologia?.contains("4G", ignoreCase = true) == true -> "4G · Teste concluído"
                    else -> "Rede móvel · Teste concluído"
                }
            }
            else -> "Wi-Fi · Teste concluído"
        }

    val titulo =
        when {
            contaminado -> "Teste interrompido"
            decisaoTitulo != null -> decisaoTitulo
            else -> "Sua conexão está estável"
        }

    val mensagem =
        when {
            contaminado -> mensagemIntegridadeResultado(faseInterrompida)
            decisaoMensagem != null -> decisaoMensagem
            else -> "Não encontramos sinais importantes de instabilidade neste teste."
        }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ehMovel) Icons.Rounded.CellTower else Icons.Rounded.Wifi,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(LkSpacing.xs))
            Text(
                text = tipoRedeRotulo,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(LkSpacing.sm))
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun SecaoSeuResultado(
    resultado: ResultadoSpeedtest,
    c: LkTokens,
) {
    Column {
        LkSectionOverline(text = "SEU RESULTADO")
        Spacer(Modifier.height(LkSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.md),
        ) {
            CardVelocidadeCompacto(
                label = "Download",
                valor = "%.0f".format(resultado.downloadMbps),
                unidade = "Mbps",
                c = c,
                modifier = Modifier.weight(1f),
            )
            CardVelocidadeCompacto(
                label = "Upload",
                valor = if (resultado.uploadNaoDetectado || resultado.uploadMbps <= 0.0) "—" else "%.0f".format(resultado.uploadMbps),
                unidade = if (resultado.uploadNaoDetectado) "Não detectado" else "Mbps",
                c = c,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(LkSpacing.md))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = LkSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(LkSpacing.sm))
                Text(
                    text = "Latência (ping)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                )
            }
            Text(
                text = if (resultado.latenciaMs > 0) "%.0f ms".format(resultado.latenciaMs) else "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
        }
    }
}

@Composable
private fun CardVelocidadeCompacto(
    label: String,
    valor: String,
    unidade: String,
    c: LkTokens,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(LkRadius.card))
                .background(c.surfaceContainer)
                .padding(LkSpacing.lg)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $valor $unidade" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = valor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
            )
            Spacer(Modifier.width(LkSpacing.xs))
            Text(
                text = unidade,
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun SecaoComoDeveFuncionar(
    qualidade: DiagnosticoQualidadeSpeedtest,
    c: LkTokens,
) {
    Column {
        Text(
            text = "Como deve funcionar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.sm))

        ItemUsoLinha(
            label = "Streaming",
            veredito = qualidade.vereditoStreaming,
            icon = Icons.Outlined.Tv,
            c = c,
        )
        ItemUsoLinha(
            label = "Jogos online",
            veredito = qualidade.vereditoGamer,
            icon = Icons.Outlined.SportsEsports,
            c = c,
        )
        ItemUsoLinha(
            label = "Videochamadas",
            veredito = qualidade.vereditoVideoChamada,
            icon = Icons.Outlined.Videocam,
            c = c,
        )

        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = "Estimativas baseadas nas medições disponíveis.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
        )
    }
}

@Composable
private fun ItemUsoLinha(
    label: String,
    veredito: VereditoUso,
    icon: ImageVector,
    c: LkTokens,
) {
    val rotulo =
        when (veredito) {
            VereditoUso.good -> "Bom"
            VereditoUso.acceptable -> "Bom"
            VereditoUso.poor -> "Ruim"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(LkSpacing.md))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
            )
        }
        Text(
            text = rotulo,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
        )
    }
}

@Composable
private fun SecaoAssistCta(
    contaminado: Boolean,
    uploadNaoDetectado: Boolean,
    onConversarAssist: () -> Unit,
    onTestarNovamente: () -> Unit,
    c: LkTokens,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(LkSpacing.sm))
            Text(
                text = "Quer entender seu resultado?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
            )
        }
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = "Converse com o Assist usando os dados deste teste.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(LkSpacing.md))

        if (contaminado || uploadNaoDetectado) {
            Button(
                onClick = onTestarNovamente,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(LkRadius.pill),
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
            ) {
                Text(
                    text = "Refazer teste",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(LkSpacing.xs))
            TextButton(
                onClick = onConversarAssist,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Conversar com Assist mesmo assim",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
            }
        } else {
            Button(
                onClick = onConversarAssist,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(LkRadius.pill),
                colors = ButtonDefaults.buttonColors(containerColor = c.surfaceContainerHighest, contentColor = c.textPrimary),
            ) {
                Text(
                    text = "Conversar com Assist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SecaoMaisDetalhes(
    resultado: ResultadoSpeedtest,
    localizacaoServidor: String?,
    ispInfo: IspInfo?,
    operadoraMovel: String?,
    adsGate: NativeAdsGate,
    onVerDetalhesTecnicos: () -> Unit,
    onIniciarModoGamer: () -> Unit,
    c: LkTokens,
) {
    val statusJitter = remember(resultado.jitterMs) { classificarJitterLocal(resultado.jitterMs) }
    val corJitter = statusJitter.corSemantica(c)
    val veredictoJitter = statusJitter.labelPt()

    val statusPerda = remember(resultado.perdaPercentual) { classificarPerdaPacotesLocal(resultado.perdaPercentual) }
    val corPerda = statusPerda.corSemantica(c)
    val notaInstabilidadePerda =
        remember(resultado.perdaPercentual, resultado.perdaConfianca) {
            copyInstabilidadePerdaPacotes(resultado.perdaPercentual, resultado.perdaConfianca)
        }
    val veredictoPerda = notaInstabilidadePerda ?: statusPerda.labelPt()

    val statusBufferbloat = remember(resultado.bufferbloatMs) { classificarBufferbloatLocal(resultado.bufferbloatMs) }
    val corBufferbloat = statusBufferbloat.corSemantica(c)
    val veredictoBufferbloat = statusBufferbloat.labelPt()

    Column(modifier = Modifier.fillMaxWidth().padding(top = LkSpacing.md)) {
        HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
        Spacer(Modifier.height(LkSpacing.lg))

        Text(
            text = "Métricas secundárias",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.md))

        LinhaDetalheMetrica(
            label = "Variação do tempo de resposta (jitter)",
            valor = "%.0f ms".format(resultado.jitterMs),
            veredito = veredictoJitter,
            cor = corJitter,
            c = c,
        )
        LinhaDetalheMetrica(
            label = "Falhas estimadas na conexão",
            valor = "%.1f%%".format(resultado.perdaPercentual),
            veredito = veredictoPerda,
            cor = corPerda,
            c = c,
        )
        LinhaDetalheMetrica(
            label = "Lentidão com rede ocupada (bufferbloat)",
            valor = "%.0f ms".format(resultado.bufferbloatMs),
            veredito = veredictoBufferbloat,
            cor = corBufferbloat,
            c = c,
        )

        if (localizacaoServidor != null || ispInfo != null || operadoraMovel != null) {
            Spacer(Modifier.height(LkSpacing.md))
            Text(
                text = "Informações da conexão",
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(LkSpacing.xs))
            if (ispInfo != null && !ispInfo.isp.isNullOrBlank()) {
                Text(
                    text = "Provedor: ${ispInfo.isp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
            }
            if (operadoraMovel != null) {
                Text(
                    text = "Operadora: $operadoraMovel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
            }
            if (localizacaoServidor != null) {
                Text(
                    text = "Servidor: $localizacaoServidor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
            }
        }

        Spacer(Modifier.height(LkSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LkSpacing.sm),
        ) {
            OutlinedButton(
                onClick = onVerDetalhesTecnicos,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(LkRadius.button),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ManageSearch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(LkSpacing.xs))
                Text(text = "Detalhes técnicos", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onIniciarModoGamer,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(LkRadius.button),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(LkSpacing.xs))
                Text(text = "Modo gamer", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(LkSpacing.lg))
        val nativeAdState by rememberNativeAdState(
            adUnitId = AdUnitIds.para(AdSlot.RESULTADO),
            contentSignal = NativeAdContentSignal.forSlot(AdSlot.RESULTADO),
            eligibility = eligibilidadeAnuncioResultado(adsGate),
        )
        val nativeAd = (nativeAdState as? NativeAdLoadState.Fill)?.ad
        NativeAdCard(nativeAd = nativeAd, source = NativeAdSource.ADMOB)
    }
}

@Composable
private fun LinhaDetalheMetrica(
    label: String,
    valor: String,
    veredito: String,
    cor: Color,
    c: LkTokens,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LkSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Text(text = veredito, style = MaterialTheme.typography.labelSmall, color = cor)
        }
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary,
        )
    }
}
