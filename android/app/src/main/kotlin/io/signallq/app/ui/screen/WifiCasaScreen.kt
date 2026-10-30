package io.signallq.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import io.signallq.app.core.database.wificasa.MapeamentoWifiEntity
import io.signallq.app.core.database.wificasa.StatusMapeamentoWifi
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.component.LkLiveIndicator
import io.signallq.app.ui.component.LkSurfaceCard
import io.signallq.app.ui.component.SignalBars
import io.signallq.app.ui.component.SignallQButton
import io.signallq.app.ui.component.SignallQButtonStyle
import io.signallq.app.ui.component.SignallQScreenState
import io.signallq.app.ui.component.SignallQStatefulScreen
import io.signallq.app.ui.component.animacoesDoSistemaDesativadas
import io.signallq.app.ui.component.signalColor
import io.signallq.app.wificasa.ComparacaoMarcadorUi
import io.signallq.app.wificasa.MarcadorUi
import io.signallq.app.wificasa.ResultadoComparacaoMarcador
import io.signallq.app.wificasa.WifiCasaTela
import io.signallq.app.wificasa.WifiCasaUiState
import io.signallq.app.wificasa.WifiCasaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SUGESTOES_ROTULO = listOf("Sala", "Quarto", "Cozinha", "Escritório")

/**
 * Tela "WiFi Casa" (`docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`) -- evolução da ferramenta
 * "Encontrar um bom lugar" para mapeamento espacial de sinal Wi-Fi por cômodo, com comparação
 * Antes×Depois de reposicionamento de roteador/mesh.
 *
 * Mesma assinatura de [SinalWifiScreen] (permissão de localização compartilhada, RF-12) --
 * permite trocar qual composable [AppShellSinalWifiOverlay] renderiza sem tocar no resto do
 * wiring de navegação do hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiCasaScreen(
    temPermissaoLocalizacao: Boolean,
    localizacaoBloqueadaPermanentemente: Boolean,
    onSolicitarPermissaoLocalizacao: () -> Unit,
    onVoltar: () -> Unit,
    viewModel: WifiCasaViewModel = hiltViewModel(),
) {
    val c = LocalLkTokens.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(temPermissaoLocalizacao) {
        viewModel.atualizarPermissaoLocalizacao(temPermissaoLocalizacao)
    }

    var showLocalizacaoSheet by remember { mutableStateOf(false) }
    var localizacaoSheetDismissed by remember { mutableStateOf(false) }
    val locSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(temPermissaoLocalizacao, localizacaoSheetDismissed) {
        if (!temPermissaoLocalizacao && !localizacaoSheetDismissed) showLocalizacaoSheet = true
    }

    val onVoltarInterno: () -> Unit = {
        when (uiState.telaAtual) {
            WifiCasaTela.CAPTURA -> viewModel.cancelarCaptura()
            WifiCasaTela.LISTA_ANTERIORES -> viewModel.voltarParaInicio()
            WifiCasaTela.COMPARACAO -> viewModel.fecharComparacao()
            WifiCasaTela.GRID, WifiCasaTela.INICIAL -> onVoltar()
        }
    }

    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "WiFi Casa",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.W600,
                        color = c.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltarInterno) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !temPermissaoLocalizacao ->
                    SignallQStatefulScreen(
                        state =
                            SignallQScreenState.PermissionRequired(
                                title = if (localizacaoBloqueadaPermanentemente) "Permissão bloqueada" else "Permissão necessária",
                                message =
                                    if (localizacaoBloqueadaPermanentemente) {
                                        "A permissão de localização foi bloqueada nas configurações do Android. " +
                                            "Ela é exigida para medir a intensidade das redes Wi-Fi ao redor."
                                    } else {
                                        "O Android exige permissão de localização para medir a intensidade do sinal Wi-Fi em cada cômodo."
                                    },
                            ),
                        actionLabel = if (localizacaoBloqueadaPermanentemente) "Abrir ajustes do Android" else "Conceder permissão",
                        onAction = onSolicitarPermissaoLocalizacao,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                uiState.carregando ->
                    SignallQStatefulScreen(state = SignallQScreenState.Loading, modifier = Modifier.fillMaxSize()) {}
                else ->
                    when (uiState.telaAtual) {
                        WifiCasaTela.INICIAL -> WifiCasaTelaInicial(uiState, viewModel)
                        WifiCasaTela.GRID -> WifiCasaTelaGrid(uiState, viewModel)
                        WifiCasaTela.CAPTURA -> WifiCasaTelaCaptura(uiState, viewModel)
                        WifiCasaTela.LISTA_ANTERIORES -> WifiCasaTelaListaAnteriores(uiState, viewModel)
                        WifiCasaTela.COMPARACAO -> WifiCasaTelaComparacao(uiState)
                    }
            }
        }
    }

    if (showLocalizacaoSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showLocalizacaoSheet = false
                localizacaoSheetDismissed = true
            },
            sheetState = locSheetState,
        ) {
            PermissaoLocalizacaoContextoSheet(
                bloqueadaPermanentemente = localizacaoBloqueadaPermanentemente,
                onConceder = {
                    showLocalizacaoSheet = false
                    onSolicitarPermissaoLocalizacao()
                },
                onAgoraNao = {
                    showLocalizacaoSheet = false
                    localizacaoSheetDismissed = true
                },
            )
        }
    }
}

@Composable
private fun WifiCasaTelaInicial(
    uiState: WifiCasaUiState,
    viewModel: WifiCasaViewModel,
) {
    val c = LocalLkTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LkSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Mapeie o sinal da sua casa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            "Ande pelos cômodos, marque cada um no grid e compare antes e depois de mudar o roteador.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(LkSpacing.xl))
        SignallQButton(
            label = "Começar mapeamento novo",
            onClick = viewModel::iniciarNovoMapeamento,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.historico.isNotEmpty()) {
            Spacer(Modifier.height(LkSpacing.md))
            SignallQButton(
                label = "Ver mapeamentos anteriores",
                onClick = viewModel::abrirListaAnteriores,
                style = SignallQButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiCasaTelaGrid(
    uiState: WifiCasaUiState,
    viewModel: WifiCasaViewModel,
) {
    val c = LocalLkTokens.current
    var escolhaTipoPendente by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var marcadorEmEdicao by remember { mutableStateOf<MarcadorUi?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LkSpacing.lg),
    ) {
        Text(
            uiState.mapeamentoAtual?.nome ?: "Mapeamento",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.sm))
        Text(
            if (uiState.somenteLeitura) {
                "Mapeamento salvo -- toque em um marcador para ver o sinal medido."
            } else {
                "Toque em um ponto vazio do grid para adicionar um marcador."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(LkSpacing.md))

        WifiCasaGridCanvas(
            marcadores = uiState.marcadores,
            onTapGrid = { x, y -> if (!uiState.somenteLeitura) escolhaTipoPendente = x to y },
            onTapMarcador = { },
        )

        Spacer(Modifier.height(LkSpacing.lg))
        Text("Marcadores", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Spacer(Modifier.height(LkSpacing.sm))
        WifiCasaListaMarcadoresAcessivel(
            marcadores = uiState.marcadores,
            onEditarMarcador = if (uiState.somenteLeitura) null else { m -> marcadorEmEdicao = m },
            onRemoverMarcador = if (uiState.somenteLeitura) null else { m -> viewModel.removerMarcador(m.id) },
        )

        if (!uiState.somenteLeitura) {
            Spacer(Modifier.height(LkSpacing.xl))
            SignallQButton(
                label = "Salvar e concluir",
                onClick = { viewModel.concluirMapeamento(comoBaseline = false) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(LkSpacing.sm))
            SignallQButton(
                label = "Vou reposicionar o roteador",
                onClick = { viewModel.concluirMapeamento(comoBaseline = true) },
                style = SignallQButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(LkSpacing.xl))
    }

    val pendente = escolhaTipoPendente
    if (pendente != null) {
        ModalBottomSheet(onDismissRequest = { escolhaTipoPendente = null }) {
            WifiCasaEscolhaTipoMarcadorSheet(
                onEscolherComodo = {
                    escolhaTipoPendente = null
                    viewModel.iniciarCapturaMarcador(pendente.first, pendente.second, ehRoteador = false)
                },
                onEscolherRoteador = {
                    escolhaTipoPendente = null
                    viewModel.iniciarCapturaMarcador(pendente.first, pendente.second, ehRoteador = true)
                },
            )
        }
    }

    val emEdicao = marcadorEmEdicao
    if (emEdicao != null) {
        WifiCasaEditarRotuloDialog(
            marcador = emEdicao,
            onConfirmar = { novoRotulo ->
                viewModel.editarRotuloMarcador(emEdicao.id, novoRotulo)
                marcadorEmEdicao = null
            },
            onCancelar = { marcadorEmEdicao = null },
        )
    }
}

/** RF-05: edita o rótulo de um marcador sem exigir remedição de RSSI (a leitura é preservada). */
@Composable
private fun WifiCasaEditarRotuloDialog(
    marcador: MarcadorUi,
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var rotulo by remember(marcador.id) { mutableStateOf(marcador.rotulo) }
    AlertDialog(
        onDismissRequest = onCancelar,
        shape = RoundedCornerShape(LkRadius.dialog),
        title = { Text("Editar nome") },
        text = {
            OutlinedTextField(
                value = rotulo,
                onValueChange = { rotulo = it },
                label = { Text("Nome do marcador") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(rotulo) }, enabled = rotulo.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
    )
}

@Composable
private fun WifiCasaEscolhaTipoMarcadorSheet(
    onEscolherComodo: () -> Unit,
    onEscolherRoteador: () -> Unit,
) {
    val c = LocalLkTokens.current
    Column(modifier = Modifier.padding(LkSpacing.lg)) {
        Text("O que você quer marcar aqui?", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(LkSpacing.lg))
        SignallQButton(label = "Um cômodo", onClick = onEscolherComodo, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(LkSpacing.sm))
        SignallQButton(
            label = "O roteador ou nó mesh",
            onClick = onEscolherRoteador,
            style = SignallQButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(LkSpacing.lg))
    }
}

@Composable
private fun WifiCasaTelaCaptura(
    uiState: WifiCasaUiState,
    viewModel: WifiCasaViewModel,
) {
    val captura = uiState.captura ?: return
    val c = LocalLkTokens.current
    val movimentoReduzido = animacoesDoSistemaDesativadas()
    val leitura = captura.leituraAtual

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LkSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (captura.ehRoteador) "Posição do roteador" else "Novo marcador",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.lg))

        if (!captura.ehRoteador || leitura.amostrado) {
            LkLiveIndicator(label = "Medindo agora", estatico = movimentoReduzido)
            Spacer(Modifier.height(LkSpacing.md))
            if (leitura.amostrado && leitura.conectado && leitura.rssiAtual != null) {
                val cor = signalColor(leitura.rssiAtual, leitura.banda, c)
                SignalBars(rssiDbm = leitura.rssiAtual, banda = leitura.banda)
                Spacer(Modifier.height(LkSpacing.sm))
                Text(
                    signalQuality(leitura.rssiAtual, leitura.banda),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W700,
                    color = cor,
                )
                Text("${leitura.rssiAtual} dBm", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                Text("Aguardando leitura de sinal...", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }

        Spacer(Modifier.height(LkSpacing.xl))
        OutlinedTextField(
            value = captura.rotulo,
            onValueChange = viewModel::atualizarRotuloCaptura,
            label = { Text(if (captura.ehRoteador) "Nome (opcional)" else "Nome do cômodo") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!captura.ehRoteador) {
            Spacer(Modifier.height(LkSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(LkSpacing.xs)) {
                SUGESTOES_ROTULO.forEach { sugestao ->
                    Text(
                        sugestao,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.primary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .clickableSemRipple { viewModel.atualizarRotuloCaptura(sugestao) }
                                .padding(horizontal = LkSpacing.sm, vertical = LkSpacing.xs),
                    )
                }
            }
        }

        Spacer(Modifier.height(LkSpacing.xl))
        SignallQButton(
            label = "Salvar marcador",
            onClick = viewModel::confirmarCaptura,
            enabled = captura.podeConfirmar,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(LkSpacing.sm))
        SignallQButton(
            label = "Cancelar",
            onClick = viewModel::cancelarCaptura,
            style = SignallQButtonStyle.Text,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WifiCasaTelaListaAnteriores(
    uiState: WifiCasaUiState,
    viewModel: WifiCasaViewModel,
) {
    val c = LocalLkTokens.current
    if (uiState.historico.isEmpty()) {
        SignallQStatefulScreen(
            state = SignallQScreenState.Empty(title = "Nenhum mapeamento ainda", message = "Comece um mapeamento novo para ver o histórico aqui."),
            modifier = Modifier.fillMaxSize(),
        ) {}
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = LkSpacing.lg)) {
        items(items = uiState.historico, key = { it.id }) { mapeamento ->
            WifiCasaMapeamentoAnteriorItem(mapeamento, onClick = { viewModel.selecionarMapeamentoAnterior(mapeamento.id) })
            Spacer(Modifier.height(LkSpacing.sm))
        }
    }
}

@Composable
private fun WifiCasaMapeamentoAnteriorItem(
    mapeamento: MapeamentoWifiEntity,
    onClick: () -> Unit,
) {
    val c = LocalLkTokens.current
    val formato = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }
    LkSurfaceCard(modifier = Modifier.fillMaxWidth().clickableSemRipple(onClick)) {
        Text(mapeamento.nome, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Text(
            formato.format(Date(mapeamento.criadoEmEpochMs)),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Text(
            statusLabel(mapeamento.status),
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary,
        )
    }
}

private fun statusLabel(status: String): String =
    when (status) {
        StatusMapeamentoWifi.EM_ANDAMENTO -> "Em andamento"
        StatusMapeamentoWifi.CONCLUIDO -> "Concluído"
        StatusMapeamentoWifi.BASELINE_PENDENTE -> "Aguardando comparação"
        StatusMapeamentoWifi.BASELINE_COMPARADO -> "Comparado"
        else -> status
    }

@Composable
private fun WifiCasaTelaComparacao(uiState: WifiCasaUiState) {
    val c = LocalLkTokens.current
    val comparacao = uiState.comparacao
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LkSpacing.lg)) {
        Text(
            "Antes × Depois",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.sm))
        if (comparacao.isNullOrEmpty()) {
            Text(
                "Sem marcadores correspondentes para comparar.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            return@Column
        }
        Text(
            "Comparação por cômodo, marcador a marcador.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(LkSpacing.lg))
        comparacao.forEach { item ->
            WifiCasaComparacaoItem(item)
            Spacer(Modifier.height(LkSpacing.sm))
        }
    }
}

@Composable
private fun WifiCasaComparacaoItem(item: ComparacaoMarcadorUi) {
    val c = LocalLkTokens.current
    val (icone, cor, texto) =
        when (item.resultado) {
            ResultadoComparacaoMarcador.MELHOROU -> Triple(Icons.Outlined.ArrowUpward, c.success, "Melhorou")
            ResultadoComparacaoMarcador.PIOROU -> Triple(Icons.Outlined.ArrowDownward, c.error, "Piorou")
            ResultadoComparacaoMarcador.NAO_MUDOU -> Triple(Icons.Outlined.Remove, c.textSecondary, "Não mudou")
            ResultadoComparacaoMarcador.NOVO -> Triple(Icons.Outlined.Router, c.primary, "Novo")
            ResultadoComparacaoMarcador.REMOVIDO -> Triple(Icons.Outlined.Remove, c.textTertiary, "Removido")
        }
    LkSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, contentDescription = null, tint = cor)
            Spacer(Modifier.width(LkSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(item.rotulo, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    "${item.categoriaAntes ?: "--"} → ${item.categoriaDepois ?: "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            Text(texto, style = MaterialTheme.typography.labelLarge, color = cor)
        }
    }
}

@Composable
private fun Modifier.clickableSemRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
