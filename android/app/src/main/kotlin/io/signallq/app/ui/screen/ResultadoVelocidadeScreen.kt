package io.signallq.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Troubleshoot
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.signallq.app.ads.AdSlot
import io.signallq.app.ads.AdUnitIds
import io.signallq.app.ads.NativeAdContentSignal
import io.signallq.app.core.diagnostico.MetricStatus
import io.signallq.app.feature.diagnostico.SnapshotDiagnostico
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
import io.signallq.app.ui.component.LkSurfaceCard
import io.signallq.app.ui.component.ads.NativeAdCard
import io.signallq.app.ui.component.ads.NativeAdSource
import io.signallq.app.ui.component.classificarBufferbloatLocal
import io.signallq.app.ui.component.classificarDownloadLocal
import io.signallq.app.ui.component.classificarJitterLocal
import io.signallq.app.ui.component.classificarLatenciaLocal
import io.signallq.app.ui.component.classificarPerdaPacotesLocal
import io.signallq.app.ui.component.classificarUploadLocal
import io.signallq.app.ui.component.copyInstabilidadePerdaPacotes
import io.signallq.app.ui.component.corSemantica
import io.signallq.app.ui.component.labelPt
import kotlinx.coroutines.launch

/**
 * GH#1659a — mapeia o único sinal que esta tela recebe de fora ([adsEnabled]) pro contrato
 * tipado [NativeAdEligibility]. `canRequestAds`/`online` continuam derivados só desse flag: a
 * tela não recebe sinal de consentimento UMP nem de conectividade separados do Remote Config
 * (mesma limitação que `rememberNativeAd()`, o wrapper antigo, já tinha — não é regressão desta
 * migração). Diferenciar os dois sinais de verdade exigiria plumbing novo em AppShell.kt/
 * MainViewModel, fora do escopo desta fatia puramente técnica (decisão de arquitetura de ads,
 * issues #1330/#1694 — ver o mesmo limite documentado em `AppShellRootRegistryTest`).
 */
internal fun eligibilidadeAnuncioResultado(adsGate: io.signallq.app.ads.NativeAdsGate): NativeAdEligibility =
    adsGate.eligibilityFor(AdSlot.RESULTADO)

/** Compatibilidade para testes legados; o fluxo de produção usa [NativeAdsGate]. */
internal fun eligibilidadeAnuncioResultado(adsEnabled: Boolean): NativeAdEligibility =
    NativeAdEligibility(AdSlot.RESULTADO, buildEnabled = true, flagEnabled = adsEnabled, canRequestAds = adsEnabled, online = true)

/**
 * GH#1659a — mensagem exibida quando `ResultadoPdfGenerator.gerarECompartilhar` lança durante o
 * compartilhamento do resultado (mesmo padrão de `LaudoScreen.compartilharLaudo`).
 */
internal fun mensagemErroCompartilhamentoResultado(erro: Throwable): String =
    "Não foi possível compartilhar o resultado: ${erro.message}"

/** Mensagem honesta sobre a confiabilidade da medição exibida no resultado. */
internal fun mensagemIntegridadeResultado(faseInterrompida: String): String =
    if (faseInterrompida.contains("redeMudou", ignoreCase = true)) {
        "O teste foi interrompido porque a conexão caiu ou mudou durante a medição. Tente novamente quando a rede estabilizar."
    } else {
        "Outros aplicativos podem ter afetado o resultado."
    }

/**
 * Tela "Resultado do teste" — GH#536.
 *
 * Escopo reduzido de propósito: mostra só o essencial pra responder "minha internet
 * está boa? o que está ruim? o que eu faço agora?" — diagnóstico geral, badge de rede
 * e os 5 cards principais (Download/Upload/Latência/Oscilação/Perda).
 *
 * Resumo pós-teste enxuto, sem despejo de dado técnico (Feature #550, issue #1475):
 * as 3 CTAs abaixo do card "Como sua internet deve funcionar" abrem cada uma seu próprio destino —
 * [DiagnosticoGuiadoScreen] (7 objetivos fechados, motor local + explicação por IA),
 * modo gamer (fluxo próprio, issue #1476) e [DetalhesTecnicosScreen] (métricas cruas).
 * Antes dessa issue, um único CTA abria a sheet automática "Análise detalhada"
 * (`DiagnosticoDetalhadoSheet`, retirada) com banner de IA e recomendação abertos
 * sozinhos ao entrar aqui — decisão do Luiz (comentário de #1474, 2026-07-26): o
 * banner/recomendação só aparece depois que o usuário escolhe um objetivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultadoVelocidadeScreen(
    resultado: ResultadoSpeedtest,
    snapshotDiagnostico: SnapshotDiagnostico,
    onTestarNovamente: () -> Unit,
    onIrParaHome: () -> Unit,
    onVoltar: () -> Unit = {},
    /** GH#784 — etapa "compartilhou" do funil do teste de velocidade (Uso do App,
     *  admin-worker). Disparado junto do compartilhamento real do PDF, nao antes. */
    onCompartilhar: () -> Unit = {},
    localizacaoServidor: String? = null,
    ispInfo: IspInfo? = null,
    operadoraMovel: String? = null,
    /** Só alimenta o PDF compartilhado (última análise de IA disponível, se houver)
     *  — não dispara mais análise automática nesta tela (issue #1475). */
    analisadorState: AnalisadorState = AnalisadorState.Inativo,
    /** Iniciar diagnóstico guiado por objetivo — issue #1475 ([DiagnosticoGuiadoScreen]). */
    onIniciarDiagnosticoGuiado: () -> Unit = {},
    /** Iniciar modo gamer (jogo → device → resultado) — issue #1476, ainda não
     *  implementado; o CTA já aparece no resumo por paridade com o protótipo #1474. */
    onIniciarModoGamer: () -> Unit = {},
    /** Ver detalhes técnicos (métricas cruas, sem IA/recomendação) — issue #1475
     *  ([DetalhesTecnicosScreen]). */
    onVerDetalhesTecnicos: () -> Unit = {},
    /** Toggle remoto (Firebase Remote Config) + gate de consentimento UMP -- issue #555.
     *  Default `false`: nunca mostra anuncio sem sinal explicito de que pode. */
    adsGate: io.signallq.app.ads.NativeAdsGate =
        io.signallq.app.ads
            .NativeAdsGate(),
) {
    val c = LocalLkTokens.current
    val scrollState = rememberScrollState()
    val decisao = snapshotDiagnostico.relatorio?.decisao
    val decisaoTitulo = decisao?.titulo
    val decisaoMensagem = decisao?.mensagemUsuario
    var compartilhando by remember { mutableStateOf(false) }
    // GH#1659a — visível pra quem tocou compartilhar; nunca deixa o spinner travado sem
    // explicação quando ResultadoPdfGenerator.gerarECompartilhar lança (mesmo padrão de erro
    // de LaudoScreen.compartilharLaudo).
    var erroCompartilhamento by remember { mutableStateOf<String?>(null) }
    var metricasDetalhadasAbertas by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // GH#1221 RF-06 / GH#1225 item C — classificador UNICO (core/diagnostico), a tela nao
    // mantem mais sua propria regua numerica (antes divergia do motor de diagnostico: 3
    // faixas Excelente/Regular/Ruim aqui vs. 6 faixas canonicas em MetricClassifier, com
    // limiares numericos diferentes para a MESMA metrica). "Perda" e rotulada como
    // ESTIMADA — GH#1221 RF-04, o metodo e por timeout de probes HTTP, nao medicao direta
    // de perda de pacotes IP.
    //
    // NDS-02d (#1752, ADR-017) — os 6 cards abaixo pararam de chamar MetricClassifier
    // direto e passaram a delegar pro seam ClassificacaoMetricaLocal.kt (mesmo padrao da
    // NDS-02b), hoje com a MESMA matematica local (comportamento identico, provado por
    // teste de caracterizacao em ClassificacaoMetricaLocalTest). Sem chamada viva ao NDS
    // ainda — a orquestracao real (quando disparar uma avaliacao, tratar loading/erro) e
    // trabalho da fatia final NDS-02k/MainViewModel.
    //
    // GH#1521 (P0-1 da auditoria #1228) tinha introduzido comSeveridadeConciliada() pra
    // impedir que o card de latencia/upload mostrasse veredito melhor do que o achado
    // ativo do InternetDiagnosticEngine (banner desta MESMA tela). A NDS-02d REMOVEU essa
    // mecanica por completo (decisao registrada no inventario de #1746): ela dependia de
    // duas reguas numericas paralelas (MetricClassifier vs. InternetDiagnosticEngine) e de
    // uma taxonomia de ID de achado (prefixo "IN-NORMAL-04"/"IN-NORMAL-05") que o NDS nao
    // reproduz — vira codigo morto assim que a fonte de veredito migra pro NDS, entao sai
    // agora em vez de ser carregada pro seam. Os cards voltam a mostrar so a classificacao
    // isolada; unificar as duas reguas globalmente segue sendo escopo da issue #1466.

    val statusDownload = remember(resultado.downloadMbps) { classificarDownloadLocal(resultado.downloadMbps) }
    val corDownload = statusDownload.corSemantica(c)
    val veredictoDownload = statusDownload.labelPt()

    val statusUpload =
        remember(resultado.uploadMbps, resultado.uploadNaoDetectado) {
            if (resultado.uploadNaoDetectado) {
                MetricStatus.inconclusivo
            } else {
                classificarUploadLocal(resultado.uploadMbps)
            }
        }
    val corUpload = statusUpload.corSemantica(c)
    val veredictoUpload = statusUpload.labelPt()

    val statusPerda = remember(resultado.perdaPercentual) { classificarPerdaPacotesLocal(resultado.perdaPercentual) }
    val corPerda = statusPerda.corSemantica(c)
    // .agents/architecture-plan.md ("Confiabilidade estatística do diagnóstico de rede", passo
    // 6) — 1 timeout isolado (confiança amostral insuficiente) não deve ser apresentado como
    // diagnóstico definitivo; o percentual em si continua visível no card, só o rótulo abaixo
    // dele troca de veredito ("Ruim"/"Ótimo"...) para o aviso de instabilidade pontual.
    val notaInstabilidadePerda =
        remember(resultado.perdaPercentual, resultado.perdaConfianca) {
            copyInstabilidadePerdaPacotes(resultado.perdaPercentual, resultado.perdaConfianca)
        }
    val veredictoPerda = notaInstabilidadePerda ?: statusPerda.labelPt()

    val statusLatencia = remember(resultado.latenciaMs) { classificarLatenciaLocal(resultado.latenciaMs) }
    val corLatencia = statusLatencia.corSemantica(c)
    val veredictoLatencia = statusLatencia.labelPt()

    val statusJitter = remember(resultado.jitterMs) { classificarJitterLocal(resultado.jitterMs) }
    val corJitter = statusJitter.corSemantica(c)
    val veredictoJitter = statusJitter.labelPt()

    val statusBufferbloat = remember(resultado.bufferbloatMs) { classificarBufferbloatLocal(resultado.bufferbloatMs) }
    val corBufferbloat = statusBufferbloat.corSemantica(c)
    val veredictoBufferbloat = statusBufferbloat.labelPt()

    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Resultado do teste", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = c.textPrimary)
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
                            compartilhando = true
                            erroCompartilhamento = null
                            scope.launch {
                                try {
                                    ResultadoPdfGenerator.gerarECompartilhar(
                                        context = context,
                                        resultado = resultado,
                                        snapshotDiagnostico = snapshotDiagnostico,
                                        analisadorState = analisadorState,
                                        ispInfo = ispInfo,
                                        operadoraMovel = operadoraMovel,
                                        localizacaoServidor = localizacaoServidor,
                                    )
                                    onCompartilhar()
                                } catch (e: Exception) {
                                    erroCompartilhamento = mensagemErroCompartilhamentoResultado(e)
                                } finally {
                                    compartilhando = false
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Compartilhar resultado",
                                tint = c.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(c.bgPrimary),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(padding)
                        .padding(horizontal = LkSpacing.xl, vertical = LkSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // GH#1659a — visível assim que o compartilhamento (botão na TopAppBar) falha;
                // some sozinho no próximo toque em compartilhar (erroCompartilhamento = null).
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

                // Título + mensagem diagnóstico
                Box(
                    modifier =
                        Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(c.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(LkSpacing.lg))
                Text(
                    text = decisaoTitulo ?: "Resultado do teste",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                )

                if (decisaoMensagem != null) {
                    Spacer(Modifier.height(LkSpacing.sm))
                    Text(
                        text = decisaoMensagem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                // Badge discreto do tipo de rede
                ChipTipoRede(
                    connectionType = resultado.connectionType,
                    tecnologia = resultado.tecnologia,
                    c = c,
                )

                Spacer(Modifier.height(LkSpacing.xl))

                Text(
                    text = "O que medimos",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(LkSpacing.md))

                // Cards principais: Download + Upload
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    MetricCard(
                        label = "Velocidade de download",
                        value = "%.1f".format(resultado.downloadMbps),
                        unit = "Mbps",
                        cor = corDownload,
                        veredito = veredictoDownload,
                        c = c,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(LkSpacing.md))
                    MetricCard(
                        label = "Velocidade de upload",
                        value = if (resultado.uploadNaoDetectado) "—" else "%.1f".format(resultado.uploadMbps),
                        unit = if (resultado.uploadNaoDetectado) "Não foi possível medir" else "Mbps",
                        cor = corUpload,
                        veredito = veredictoUpload,
                        c = c,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(LkSpacing.md))
                TextButton(onClick = { metricasDetalhadasAbertas = !metricasDetalhadasAbertas }) {
                    Text(
                        text = if (metricasDetalhadasAbertas) "Ocultar detalhes da conexão" else "Ver detalhes da conexão",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.primary,
                    )
                    Spacer(Modifier.width(LkSpacing.xs))
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(18.dp).rotate(if (metricasDetalhadasAbertas) 180f else 0f),
                    )
                }

                AnimatedVisibility(visible = metricasDetalhadasAbertas) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            MetricCard(
                                label = "Tempo de resposta",
                                value = "%.0f".format(resultado.latenciaMs),
                                unit = "ms",
                                cor = corLatencia,
                                veredito = veredictoLatencia,
                                c = c,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(LkSpacing.md))
                            MetricCard(
                                label = "Variação do tempo de resposta",
                                value = "%.0f".format(resultado.jitterMs),
                                unit = "ms",
                                cor = corJitter,
                                veredito = veredictoJitter,
                                c = c,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(LkSpacing.md))
                        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            MetricCard(
                                // GH#1221 RF-04/#1219 — "perda de pacotes" sugere medicao direta;
                                // o metodo real e taxa de falha/timeout de probes HTTP (ver
                                // ResultadoSpeedtest.packetLossSource == "estimated"). Rotulo
                                // honesto sobre a metodologia, sem prometer mais precisao do
                                // que o teste realmente mede.
                                label = "Falhas estimadas na conexão",
                                value = "%.1f".format(resultado.perdaPercentual),
                                unit = "%",
                                cor = corPerda,
                                veredito = veredictoPerda,
                                c = c,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(LkSpacing.md))
                            MetricCard(
                                label = "Lentidão com a rede ocupada",
                                value = "%.0f".format(resultado.bufferbloatMs),
                                unit = "ms",
                                cor = corBufferbloat,
                                veredito = veredictoBufferbloat,
                                c = c,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (resultado.uploadNaoDetectado) {
                    Spacer(Modifier.height(LkSpacing.md))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LkRadius.card))
                                .background(c.warning.copy(alpha = 0.12f))
                                .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LkInfoCallout(
                            icon = Icons.Outlined.Info,
                            text = "Não consegui medir o envio de dados. Vamos tentar novamente.",
                            iconTint = c.warning,
                        )
                    }
                }

                // Integridade do teste — não é "informação secundária", é sobre a
                // confiabilidade dos números que acabaram de ser mostrados.
                if (resultado.contaminado) {
                    val faseInterrompida = resultado.diagnosticoFases.faseInterrompida
                    val mensagemContaminacao = mensagemIntegridadeResultado(faseInterrompida)
                    Spacer(Modifier.height(LkSpacing.md))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LkRadius.card))
                                .background(c.warning.copy(alpha = 0.12f))
                                .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LkInfoCallout(
                            icon = Icons.Outlined.Warning,
                            text = mensagemContaminacao,
                            iconTint = c.warning,
                        )
                    }
                }

                if (!metricasDetalhadasAbertas) {
                    Spacer(Modifier.height(LkSpacing.sm))
                    // GH#1659a — contrato tipado (Ineligible/Loading/Fill/NoFill/
                    // RecoverableError/Offline) no lugar do rememberNativeAd() antigo, que
                    // colapsava tudo isso num único NativeAd? nulo. NativeAdCard continua
                    // só aceitando NativeAd?, então só o Fill vira anúncio de fato.
                    val nativeAdState by rememberNativeAdState(
                        adUnitId = AdUnitIds.para(AdSlot.RESULTADO),
                        contentSignal = NativeAdContentSignal.forSlot(AdSlot.RESULTADO),
                        eligibility = eligibilidadeAnuncioResultado(adsGate),
                    )
                    val nativeAd = (nativeAdState as? NativeAdLoadState.Fill)?.ad
                    NativeAdCard(nativeAd = nativeAd, source = NativeAdSource.ADMOB)
                }

                Spacer(Modifier.height(LkSpacing.xl))
                LkSectionOverline(text = "Como sua internet deve funcionar")
                Spacer(Modifier.height(LkSpacing.sm))
                LkSurfaceCard(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Column {
                        ImpactoPraticoLinha(
                            label = "Vídeos em alta qualidade",
                            veredito = resultado.diagnosticoQualidade.vereditoStreaming,
                            icon = Icons.Outlined.Tv,
                            c = c,
                        )
                        HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                        ImpactoPraticoLinha(
                            label = "Jogos online",
                            veredito = resultado.diagnosticoQualidade.vereditoGamer,
                            icon = Icons.Outlined.SportsEsports,
                            c = c,
                        )
                        HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                        ImpactoPraticoLinha(
                            label = "Chamadas de vídeo",
                            veredito = resultado.diagnosticoQualidade.vereditoVideoChamada,
                            icon = Icons.Outlined.Videocam,
                            c = c,
                        )
                    }
                }

                // 3 CTAs do resumo pós-teste (issue #1475) — nada de dado técnico despejado
                // aqui, cada caminho abre seu próprio destino dedicado.
                Spacer(Modifier.height(LkSpacing.lg))
                Button(
                    onClick = onIniciarDiagnosticoGuiado,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(LkRadius.button),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Troubleshoot,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(LkSpacing.sm))
                    Text(
                        text = "Entender o que está acontecendo",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(LkSpacing.sm))
                OutlinedButton(
                    onClick = onIniciarModoGamer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(LkRadius.button),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(LkSpacing.sm))
                    Text(
                        text = "Analisar jogos online",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                }
                Spacer(Modifier.height(LkSpacing.sm))
                TextButton(
                    onClick = onVerDetalhesTecnicos,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ManageSearch,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(LkSpacing.xs))
                    Text(
                        text = "Ver detalhes técnicos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }

                Spacer(Modifier.height(LkSpacing.sm))
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(LkSpacing.sm))

                OutlinedButton(
                    onClick = onTestarNovamente,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(LkRadius.button),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(LkSpacing.sm))
                    Text(
                        text = "Refazer o teste",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                    )
                }
                TextButton(
                    onClick = onIrParaHome,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Voltar ao início",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }

                Spacer(Modifier.height(LkSpacing.xl))
            }
        }
    }
}

@Composable
private fun ChipTipoRede(
    connectionType: String?,
    tecnologia: String?,
    c: LkTokens,
) {
    val (label, icon) =
        remember(connectionType, tecnologia) {
            when {
                connectionType == null -> null
                connectionType.equals("wifi", ignoreCase = true) ->
                    "Teste feito pelo Wi-Fi" to Icons.Rounded.Wifi
                connectionType.equals("movel", ignoreCase = true) -> {
                    val tecLabel =
                        when {
                            tecnologia == null -> "Teste feito pela rede móvel"
                            tecnologia.contains("5G", ignoreCase = true) -> "Teste feito pelo 5G"
                            tecnologia.contains("4G", ignoreCase = true) ||
                                tecnologia.contains("LTE", ignoreCase = true) -> "Teste feito pelo 4G"
                            else -> "Teste feito pela rede móvel"
                        }
                    tecLabel to Icons.Rounded.CellTower
                }
                else -> null
            }
        } ?: return

    Spacer(Modifier.height(LkSpacing.sm))
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        },
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = c.surfaceContainer,
                labelColor = c.onSurfaceVariant,
                iconContentColor = c.onSurfaceVariant,
            ),
        border = null,
    )
}

@Composable
private fun ImpactoPraticoLinha(
    label: String,
    veredito: VereditoUso,
    icon: ImageVector,
    c: LkTokens,
) {
    val (cor, badgeLabel) =
        when (veredito) {
            VereditoUso.good -> c.success to "Ótimo"
            VereditoUso.acceptable -> c.warning to "Bom"
            VereditoUso.poor -> c.error to "Ruim"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(LkSpacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelLarge,
            color = cor,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(LkRadius.pill))
                    .background(cor.copy(alpha = 0.16f))
                    .padding(horizontal = LkSpacing.sm, vertical = LkSpacing.xs),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    cor: Color,
    veredito: String,
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
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value $unit, $veredito" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            minLines = 2,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = cor,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = c.textTertiary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = veredito,
            style = MaterialTheme.typography.labelSmall,
            color = cor,
        )
    }
}
