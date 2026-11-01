package io.signallq.app.ui.screen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.signallq.app.BuildConfig
import io.signallq.app.DRIVER_ID_TP_LINK_ARCHER_C6
import io.signallq.app.R
import io.signallq.app.bssidElegivelParaAutoconexao
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.datastore.ConnectionProfilePersistido
import io.signallq.app.core.datastore.ModoGamerPadraoPersistido
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.ObjetivoDiagnostico
import io.signallq.app.core.diagnostico.topology.model.NatStatus
import io.signallq.app.core.network.DiagnosticoPlanoIniciado
import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.core.network.SnapshotRede
import io.signallq.app.core.network.contracts.gateway.GatewayConnectionResultado
import io.signallq.app.core.network.contracts.gateway.GatewayConnectionService
import io.signallq.app.core.network.contracts.gateway.GatewayCredentialRequirement
import io.signallq.app.core.network.contracts.localdevice.LocalNetworkDeviceSnapshot
import io.signallq.app.core.telephony.MovelSimSnapshot
import io.signallq.app.core.telephony.MovelSnapshot
import io.signallq.app.feature.devices.ehClienteFinal
import io.signallq.app.feature.dns.SnapshotBenchmarkDns
import io.signallq.app.feature.fibra.SnapshotFibra
import io.signallq.app.feature.router.TpLinkArcherC6Driver
import io.signallq.app.feature.router.TpLinkFailure
import io.signallq.app.feature.router.TpLinkProbeResult
import io.signallq.app.feature.router.TpLinkReadResult
import io.signallq.app.feature.speedtest.EstadoExecucaoSpeedtest
import io.signallq.app.feature.speedtest.modoAutomaticoPara
import io.signallq.app.modogamer.resolverPadraoModoGamer
import io.signallq.app.servicestatus.StatusServicosUiState
import io.signallq.app.ui.GatewayInfo
import io.signallq.app.ui.HistoryPoint
import io.signallq.app.ui.IspInfo
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.resumoBandasWifi
import io.signallq.app.ui.state.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private typealias Overlay = AppShellOverlay

/**
 * GH#1098 — zIndex real do overlay dentro da `Box`, baseado na posição em [overlayStack] (não na
 * ordem de declaração no arquivo). Sem isso, o Z-order de desenho seguia a ordem em que cada
 * bloco `AnimatedVisibility` aparece no código-fonte — um overlay empilhado por cima de outro
 * declarado DEPOIS dele desenhava por baixo (ex.: abrir Perfil e depois Novidades por cima
 * escondia a tela nova, mesmo com `overlayStack` correto).
 *
 * Guarda o último índice válido (>= 0) para não derrubar o zIndex no instante em que o overlay
 * é removido de [overlayStack] — sem isso, a animação de saída (`AnimatedVisibility` com
 * `exit = slideOutVertically`) cairia atrás do conteúdo principal no meio da transição, em vez
 * de continuar visível por cima até terminar.
 */
@Composable
internal fun rememberOverlayZIndex(
    overlay: Overlay,
    overlayStack: List<Overlay>,
): Float {
    val indiceAtual = overlayStack.indexOf(overlay)
    val ultimoIndiceValido = remember { mutableIntStateOf(0) }
    if (indiceAtual >= 0) ultimoIndiceValido.intValue = indiceAtual
    return (ultimoIndiceValido.intValue + 1).toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    snapshotRede: SnapshotRede,
    speedtest: AppShellSpeedtestState,
    wifi: AppShellWifiState,
    diagnostico: AppShellDiagnosticoState,
    signallQ: AppShellSignallQState,
    // Architecture Plan "Status de conectividade ao vivo na Home" -- badge Wi-Fi/Provedor da
    // trilha + Hero (seções 5/6). Default vazio preserva os call sites/testes que ainda não
    // passam esse estado (equivalente a "nunca houve leitura").
    conectividadeAoVivo: AppShellConectividadeAoVivoState = AppShellConectividadeAoVivoState(),
    ads: AppShellAdsState = AppShellAdsState(),
    // GH#1480 (Epico #1347, F4) — gate de navegacao dos 9 modulos feature do Consumer.
    featureFlags: AppShellFeatureFlagsState = AppShellFeatureFlagsState(),
    snapshotDns: SnapshotBenchmarkDns,
    history: List<HistoryPoint>,
    localIp: UiState<String>,
    publicIp: UiState<String>,
    ispInfo: UiState<IspInfo>,
    gateways: List<GatewayInfo>,
    deviceName: String,
    dnsResolverIp: String?,
    // Lista bruta — alimenta `resolverPrimeiraHistoria` (Home/Laudo, GH#1223/#1265). A lista
    // FILTRADA que a tela de Histórico exibe vive em [historicoTela] (GH#1698).
    historico: List<MedicaoEntity>,
    snapshotFibra: SnapshotFibra,
    // GH#865 Fase 1 — snapshot normalizado do equipamento local (ONT Nokia),
    // null ate a primeira leitura de fibra concluir com sucesso.
    localDevice: LocalNetworkDeviceSnapshot? = null,
    // GH#934 — Fase 5: sinal ja existente de NAT/CGNAT (SIG-279, TopologyDiagnostic),
    // reaproveitado pela EquipamentoInternetScreen para alerta de Double NAT.
    natStatus: NatStatus? = null,
    modemHost: String?,
    modemUsername: String,
    modemPassword: String,
    modemPermanecerConectado: Boolean,
    // GH#530 — BSSID em que a sessao "manter conectado" do gateway foi estabelecida.
    gatewaySessionBssid: String?,
    gatewayIpDetectado: String?,
    localizacaoServidor: UiState<String>,
    temaSelecionado: String,
    analiseAvancada: Boolean,
    onDispararBenchmarkDns: () -> Unit,
    onReconectarFibra: (host: String, username: String, password: String) -> Unit,
    // GH#934 — solicita reboot do equipamento (so relevante quando AcessoEquipamento.GERENCIAMENTO_DISPONIVEL).
    onReiniciarEquipamento: () -> Unit = {},
    onSalvarConfiguracaoModem: (host: String, username: String, password: String, permanecer: Boolean) -> Unit,
    // GH#530 — persiste o resultado da GatewayConnectionSheet (fonte unica dos dois entry points).
    onRegistrarConexaoGateway: (
        ip: String,
        usuario: String,
        senha: String,
        lembrarSenha: Boolean,
        manterConectado: Boolean,
        bssidAtual: String?,
        driverIdConfirmado: String?,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDefinirTemaSelecionado: (String) -> Unit,
    onDefinirAnaliseAvancada: (Boolean) -> Unit,
    nomeUsuario: String,
    fotoUriUsuario: String?,
    // GH#1249 (recorte de #1227) — estadoUf/cidadeNome/velocidadeContratada*/ispConfirmado
    // saíram daqui: viraram ConnectionProfilePersistido por rede em vez de chave DataStore
    // global. `operadora` (legado, global) continua existindo só porque LaudoScreen ainda
    // consome esse valor no relatório de diagnóstico — fora do escopo desta issue (Ajustes/
    // "Minha conexão" já usa o perfil por rede via `connectionProfileAtual` abaixo).
    // planoInternet/regiao seguem como fallback de exibição legado (nunca tiveram campo editável
    // na UI, ver KDoc de AjustesProvedorState).
    operadora: String,
    planoInternet: String,
    regiao: String,
    connectionProfileAtual: ConnectionProfilePersistido?,
    onSalvarConnectionProfile: (
        providerFixed: String?,
        downloadMbps: Int?,
        uploadMbps: Int?,
        cidade: String?,
        uf: String?,
        userConfirmed: Boolean,
    ) -> Unit,
    limiteAlertaMbps: Int,
    onLimparHistorico: () -> Unit,
    onApagarDadosLocais: () -> Unit,
    onResetarApp: () -> Unit,
    // Issue #1670 — estado observável (EmAndamento/Sucesso/Falha) da última ação disparada
    // pela DadosLocaisSheet. Único MainViewModel.dadosLocaisAcaoEstado, repassado pros dois
    // call sites do sheet (dentro de AjustesScreen e a partir de Privacidade).
    dadosLocaisAcaoEstado: AcaoDadosLocaisEstado = AcaoDadosLocaisEstado.Ocioso,
    onConsumirDadosLocaisAcaoEstado: () -> Unit = {},
    monitoramentoAtivo: Boolean,
    onAtivarMonitoramento: (Boolean) -> Unit,
    notificacaoLatenciaAtiva: Boolean,
    notificacaoDnsAtiva: Boolean,
    notificacaoRssiAtiva: Boolean,
    notificacaoSemInternetAtiva: Boolean,
    onDefinirNotificacaoLatenciaAtiva: (Boolean) -> Unit,
    onDefinirNotificacaoDnsAtiva: (Boolean) -> Unit,
    onDefinirNotificacaoRssiAtiva: (Boolean) -> Unit,
    onDefinirNotificacaoSemInternetAtiva: (Boolean) -> Unit,
    statusServicos: StatusServicosUiState = StatusServicosUiState(),
    onAtualizarStatusServicos: () -> Unit = {},
    onDefinirSeguimentoServico: (String, Boolean) -> Unit = { _, _ -> },
    onSalvarPerfil: (nome: String, fotoUri: String?) -> Unit,
    onSalvarLimiteAlerta: (Int) -> Unit,
    movelSnapshot: MovelSnapshot?,
    simsAtivos: List<MovelSimSnapshot> = emptyList(),
    temPermissaoTelefonia: Boolean = false,
    onSolicitarPermissaoTelefonia: () -> Unit = {},
    temPermissaoLocalizacao: Boolean = true,
    localizacaoBloqueadaPermanentemente: Boolean = false,
    onSolicitarPermissaoLocalizacao: () -> Unit = {},
    // #95 (filtros) + GH#1698 — os 7 parâmetros soltos da tela de Histórico (lista filtrada,
    // resumo, 2 filtros com seus callbacks e a lista de operadoras) viraram um grupo só,
    // montado pela MainActivity como os demais `AppShellXxxState`.
    historicoTela: AppShellHistoricoState = AppShellHistoricoState(),
    onScreenView: (screenName: String) -> Unit = {},
    // GH#1706 — funil do diagnostico guiado (plano apresentado / bloqueio encontrado).
    onDiagnosticoPlanoIniciado: (DiagnosticoPlanoIniciado) -> Unit = {},
    onAvaliarAssist: suspend (DiagnosticInput) -> DiagnosticReport = { throw IllegalStateException("Assist evaluator not configured") },
    // GH#784 — etapa "compartilhou" do funil do teste de velocidade.
    onCompartilharResultadoVelocidade: () -> Unit = {},
    // GH#970 — resolucao de identidade/contato de operadora: nivel 1 (catalogo local,
    // sincrono, sem I/O) + cadeia completa (local -> diretorio remoto do worker
    // signallq-diagnostic -> fallback generico). Injetado a partir da MainActivity
    // (OperadoraDirectoryResolver via Hilt) — AppShell so repassa, nao resolve nada.
    // GH#1704 — os 4 resolvers de operadora (GH#970) viraram um grupo só; os dois lambdas de
    // fallback default ocupavam 28 linhas desta assinatura e agora vivem em `AppShellState.kt`,
    // ao lado do tipo que descrevem.
    operadoraResolvers: AppShellOperadoraResolvers = AppShellOperadoraResolvers(),
    // Issue #1476 (Feature #550) — combinação jogo+device salva como padrão do Modo gamer,
    // ou `null` quando o usuário nunca salvou nenhuma (sempre abre pela Etapa 1/3).
    modoGamerPadrao: ModoGamerPadraoPersistido? = null,
    onSalvarModoGamerPadrao: suspend (jogoId: String?, categoriaFallback: String?, deviceId: String) -> Unit =
        { _, _, _ -> },
) {
    // Desempacota os grupos de estado para variaveis locais — mantém compatibilidade com
    // o corpo interno sem precisar propagar o prefixo `speedtest.x` por toda a funcao.
    val snapshotSpeedtest = speedtest.snapshotSpeedtest
    val speedtestPendenteModoMovel = speedtest.speedtestPendenteModoMovel
    val speedtestPermiteHeavyMovel = speedtest.speedtestPermiteHeavyMovel
    val speedtestMbConsumidosMes = speedtest.speedtestMbConsumidosMes
    val onNovoTeste = speedtest.onNovoTeste
    val onNovoTesteJaConfirmadoMovel = speedtest.onNovoTesteJaConfirmadoMovel
    val onCancelarTeste = speedtest.onCancelarTeste
    val onConfirmarSpeedtestMovel = speedtest.onConfirmarSpeedtestMovel
    val onCancelarSpeedtestMovel = speedtest.onCancelarSpeedtestMovel
    val onSetSpeedtestPermiteHeavyMovel = speedtest.onSetSpeedtestPermiteHeavyMovel
    val diagnosticoConectividade = speedtest.diagnosticoConectividade
    val onLimparDiagnosticoConectividade = speedtest.onLimparDiagnosticoConectividade

    val snapshotWifi = wifi.snapshotWifi
    val connectedNetwork = wifi.connectedNetwork
    val snapshotDevices = wifi.snapshotDevices
    val apelidos = wifi.apelidos
    val onRefreshDispositivos = wifi.onRefreshDispositivos
    val onRefreshSinal = wifi.onRefreshSinal
    val onSalvarApelido = wifi.onSalvarApelido

    // GH#531 — resumo de bandas Wi-Fi + contagem de clientes do gateway, reusado
    // no subtítulo de Ajustes ("Roteador e rede") e no GatewayItem de Dispositivos.
    val bandasWifiGateway = resumoBandasWifi(snapshotWifi.redes, connectedNetwork?.ssid)
    val clientesNaRedeGateway = snapshotDevices.dispositivos.count { it.ehClienteFinal() }

    val snapshotDiagnostico = diagnostico.snapshotDiagnostico
    val medicaoBaseModoGamer = diagnostico.medicaoBaseModoGamer
    val networkIdAtual = diagnostico.networkIdAtual
    val onIniciarDiagnostico = diagnostico.onIniciarDiagnostico
    val onSolicitarDiagnostico = diagnostico.onSolicitarDiagnostico
    val analisadorState = diagnostico.analisadorState
    val onAnalisarProblema = diagnostico.onAnalisarProblema
    val onResetarAnalisador = diagnostico.onResetarAnalisador
    val onLaudoFechado = diagnostico.onLaudoFechado
    val recommendationDecision = diagnostico.recommendationDecision
    val recommendationFeedback = diagnostico.recommendationFeedback
    val onRecommendationShown = diagnostico.onRecommendationShown
    val onRecommendationClicked = diagnostico.onRecommendationClicked
    val onRecommendationFeedback = diagnostico.onRecommendationFeedback
    val onTestarNovamenteVinculado = diagnostico.onTestarNovamenteVinculado
    val comparacaoRetesteState = diagnostico.comparacaoRetesteState

    val operadoraMovel = signallQ.operadoraMovel
    val onVerificarGemma = signallQ.onVerificarGemma

    val statusConectividadeAoVivo = conectividadeAoVivo.statusAoVivo
    val ultimoDiagnosticoConectividade = conectividadeAoVivo.ultimoDiagnostico
    val onIniciarStatusConectividadeAoVivo = conectividadeAoVivo.onIniciar
    val onPararStatusConectividadeAoVivo = conectividadeAoVivo.onParar

    val resolveOperadoraIdentidadeLocal = operadoraResolvers.identidadeLocal
    val resolveOperadoraIdentidadeRemota = operadoraResolvers.identidadeRemota

    // Monetização nativa: build, UMP e Remote Config permanecem separados até cada slot.
    val adsGate = ads.gate

    val c = LocalLkTokens.current
    val context = LocalContext.current
    // Desempacota UiState<T> → tipos opcionais para as telas filhas que ainda recebem primitivos.
    // Loading e Error resultam em null — as telas exibem fallback textual próprio.
    val localizacaoServidorStr: String? = (localizacaoServidor as? UiState.Success)?.data
    val localIpStr: String? = (localIp as? UiState.Success)?.data
    val publicIpStr: String? = (publicIp as? UiState.Success)?.data
    val ispInfoData: IspInfo? = (ispInfo as? UiState.Success)?.data
    val isIspInfoLoading = publicIp is UiState.Loading
    // A jornada única inicia em Início e restaura raiz/pilhas por processo.
    val navigator = rememberAppShellNavigator()

    // Architecture Plan "Status de conectividade ao vivo na Home", seção 6: inicia quando
    // Home está selecionada E o app está em foreground; para quando qualquer uma das duas
    // deixa de valer. `repeatOnLifecycle(RESUMED)` cobre "foreground" (mesmo proxy que
    // `MonitorRede.iniciar()/encerrar()` usa via `MainActivity.onStart()/onStop()`, mas aqui
    // via Lifecycle direto porque a condição extra "Home selecionada" só existe em Compose,
    // dentro do navigator). Sair da Home (troca de raiz) e ir a background cancelam o mesmo
    // bloco -- `finally` chama `onPararStatusConectividadeAoVivo()` nos dois casos, sem
    // duplicar a lógica de start/stop.
    val lifecycleOwnerConectividade = LocalLifecycleOwner.current
    LaunchedEffect(navigator.selectedRoot, lifecycleOwnerConectividade) {
        if (navigator.selectedRoot != AppShellRoot.Home) return@LaunchedEffect
        lifecycleOwnerConectividade.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            onIniciarStatusConectividadeAoVivo()
            try {
                awaitCancellation()
            } finally {
                onPararStatusConectividadeAoVivo()
            }
        }
    }
    // GH#1737 (épico #1647) — o modo não é mais escolhido pela pessoa (MedicaoTipoSheet/
    // ModeSelector removidos): é decidido automaticamente pelo tipo de rede a cada disparo,
    // recomputado a cada recomposição (sem `remember` — não há mais valor "selecionado" para
    // persistir entre disparos).
    val modoAutomatico = modoAutomaticoPara(snapshotRede.estadoConexao)
    val overlayStack = navigator.overlayStack

    // GH#1704 parte 4/4 — medição pedida pelo fluxo guiado. Estado e regras em
    // `AppShellMedicaoGuiada.kt`; aqui ficam só as leituras.
    //
    // O `ExecutorSpeedtest` é `@Singleton`: uma medição disparada de dentro do diagnóstico guiado é
    // indistinguível, no snapshot, de uma disparada na tela Velocidade.
    //
    // O shell tem **cinco** reações ao executor. Três são suprimidas explicitamente por
    // `suprimeReacoesDoShell` — o `VelocidadeScreen` em tela cheia, o `BackHandler` de erro e o
    // empilhamento de `Overlay.ResultadoVelocidade` na conclusão. As outras duas hoje só não
    // atrapalham porque o overlay guiado as ocluí: a barra inferior some durante `executando`
    // (`shouldShowAppShellBottomBar`) e a Início reage via `Inicio2UiStateMapper.map`. Oclusão não
    // é mecanismo — é a mesma objeção que o comentário do `AnimatedVisibility` abaixo faz ao
    // zIndex. Se alguma delas passar a ser visível durante a medição guiada, entra na supressão.
    // (Achado B4 de Caio na PR #1719; a contagem anterior dizia "três reações" e estava errada.)
    val medicaoGuiada =
        rememberMedicaoGuiada(
            snapshot = snapshotSpeedtest,
            onNovoTeste = onNovoTeste,
            onCancelarTeste = onCancelarTeste,
        )

    // GH#1480 (Epico #1347, F4) — feedback neutro (SHOW_DISABLED_MESSAGE-like) quando uma
    // rota/overlay e bloqueada por flag desligada. `bloquearRota` sempre registra
    // feature_blocked_remote via featureFlags.onFeatureBlocked antes de decidir o retorno
    // (ver AppShellFeatureGating.kt) — nunca chamar onFeatureBlocked direto nos call sites.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val bloquearRota: (Boolean, String) -> Boolean = { habilitada, moduleId ->
        val permitido = featureFlags.permitirOuBloquear(habilitada, moduleId)
        if (!permitido) {
            snackbarScope.launch { snackbarHostState.showSnackbar("Recurso temporariamente indisponível.") }
        }
        permitido
    }

    // BUG#1511 (P0) — ate #547 entregar uma implementacao real (NetHAL/TR-064 por fabricante/
    // firmware), NENHUM caminho de producao pode fingir que autenticou no equipamento. O antigo
    // "gatewayConnectionServiceMock" (delay(900) + Sucesso incondicional para qualquer host/
    // usuario/senha) foi removido — marcava sessao como valida e persistia credencial sem
    // nenhuma autenticacao real ter ocorrido. GatewayConnectionServiceIndisponivelPadrao (core:
    // network) e o unico default aceitavel ate la: nunca retorna Sucesso, so Indisponivel — nao
    // aceita nem tenta validar credencial nenhuma. Continua existindo so para a Sheet/autoconexao
    // funcionarem sem null-check espalhado; a integracao real (fora deste escopo) e a #547.
    var tpLinkSnapshot by remember { mutableStateOf<LocalNetworkDeviceSnapshot?>(null) }
    val bssidAtual = snapshotRede.wifiLinkSnapshot?.bssid
    // Só é preenchido depois da leitura autenticada mínima. A sondagem sem senha
    // escolhe a UI, mas não é evidência suficiente para escolher armazenamento.
    var driverIdGatewayConfirmado by remember { mutableStateOf<String?>(null) }
    var tpLinkHostSessao by remember { mutableStateOf<String?>(null) }
    var tpLinkSenhaSessao by remember { mutableStateOf<String?>(null) }
    var tpLinkBssidSessao by remember { mutableStateOf<String?>(null) }
    var requisitoCredencialGateway by remember { mutableStateOf(GatewayCredentialRequirement.USERNAME_AND_PASSWORD) }
    LaunchedEffect(gatewayIpDetectado) {
        requisitoCredencialGateway =
            if (gatewayIpDetectado != null &&
                withContext(Dispatchers.IO) { TpLinkArcherC6Driver.probe(gatewayIpDetectado) == TpLinkProbeResult.STOK_LUCI_PASSWORD_ONLY }
            ) {
                GatewayCredentialRequirement.PASSWORD_ONLY
            } else {
                GatewayCredentialRequirement.USERNAME_AND_PASSWORD
            }
    }
    val gatewayConnectionService =
        remember(bssidAtual) {
            GatewayConnectionService { ip, _, senha ->
                withContext(Dispatchers.IO) {
                    driverIdGatewayConfirmado = null
                    if (TpLinkArcherC6Driver.probe(ip) != TpLinkProbeResult.STOK_LUCI_PASSWORD_ONLY) {
                        GatewayConnectionResultado.Indisponivel
                    } else {
                        when (val result = TpLinkArcherC6Driver(ip).loginAndRead(senha)) {
                            is TpLinkReadResult.Success -> {
                                tpLinkSnapshot = result.snapshot
                                driverIdGatewayConfirmado = DRIVER_ID_TP_LINK_ARCHER_C6
                                tpLinkHostSessao = ip
                                tpLinkSenhaSessao = senha
                                tpLinkBssidSessao = bssidAtual
                                GatewayConnectionResultado.Sucesso
                            }
                            is TpLinkReadResult.Failure ->
                                GatewayConnectionResultado.Falha(
                                    when (result.reason) {
                                        TpLinkFailure.INVALID_CREDENTIALS -> "A senha do roteador não confere."
                                        TpLinkFailure.UNSUPPORTED_MODEL -> "Este roteador TP-Link ainda não é compatível."
                                        TpLinkFailure.SESSION_EXPIRED -> "A sessão do roteador expirou. Tente novamente."
                                        TpLinkFailure.COMMUNICATION -> "Não consegui falar com o roteador agora."
                                        TpLinkFailure.INVALID_RESPONSE -> "O roteador respondeu de um jeito que não reconheci."
                                    },
                                )
                        }
                    }
                }
            }
        }
    // A leitura autenticada do Archer usa o mesmo contrato normalizado que a
    // Nokia; a UI não precisa conhecer fabricante para mostrar o resultado.
    val equipamentoLocal = tpLinkSnapshot ?: localDevice

    fun invalidarSessaoTpLink() {
        tpLinkSnapshot = null
        driverIdGatewayConfirmado = null
        tpLinkHostSessao = null
        tpLinkSenhaSessao = null
        tpLinkBssidSessao = null
    }

    LaunchedEffect(bssidAtual, snapshotRede.conectado, tpLinkBssidSessao) {
        if (tpLinkSnapshot != null && (!snapshotRede.conectado || bssidAtual != tpLinkBssidSessao)) {
            invalidarSessaoTpLink()
        }
    }

    val atualizarTpLink: () -> Unit = {
        val hostC6 = tpLinkHostSessao
        val senhaC6 = tpLinkSenhaSessao
        if (tpLinkSnapshot != null && !hostC6.isNullOrBlank() && senhaC6 != null) {
            snackbarScope.launch {
                when (val resultado = gatewayConnectionService.conectar(hostC6, "admin", senhaC6)) {
                    GatewayConnectionResultado.Sucesso -> Unit
                    GatewayConnectionResultado.Indisponivel -> {
                        invalidarSessaoTpLink()
                        snackbarHostState.showSnackbar("Não consegui falar com o roteador agora.")
                    }
                    is GatewayConnectionResultado.Falha -> {
                        invalidarSessaoTpLink()
                        snackbarHostState.showSnackbar(resultado.mensagemUsuario)
                    }
                }
            }
        }
    }

    // GH#527 — sessao "manter conectado" do gateway, fonte unica compartilhada pelos dois
    // entry points (Home e Ajustes). Elegivel quando o toggle esta ativo E o BSSID atual bate
    // com o BSSID vinculado a credencial — rede diferente (mesmo com o mesmo SSID) invalida.
    // BUG#1511 — elegibilidade sozinha NUNCA basta pra marcar sessao valida: sem uma
    // implementacao real conectada aqui (gatewayConnectionServiceIndisponivel sempre retorna
    // Indisponivel), gatewaySessaoValida permanece sempre false — o no do gateway volta a abrir
    // a sheet manual em vez de assumir uma sessao que nunca foi autenticada de verdade.
    val elegivelParaAutoconexao =
        bssidElegivelParaAutoconexao(modemPermanecerConectado, gatewaySessionBssid, bssidAtual)
    var gatewaySessaoValida by remember { mutableStateOf(false) }
    LaunchedEffect(elegivelParaAutoconexao, modemHost, modemUsername, modemPassword) {
        gatewaySessaoValida =
            if (elegivelParaAutoconexao && !modemHost.isNullOrBlank()) {
                if (withContext(Dispatchers.IO) {
                        TpLinkArcherC6Driver.probe(modemHost) == TpLinkProbeResult.STOK_LUCI_PASSWORD_ONLY
                    }
                ) {
                    val resultado =
                        runCatching {
                            gatewayConnectionService.conectar(modemHost, modemUsername, modemPassword)
                        }.getOrNull()
                    resultado is GatewayConnectionResultado.Sucesso
                } else {
                    // O caminho legado continua sendo dono da Nokia. Não simulamos
                    // sucesso: o ExecutorFibra atualizará o estado real da leitura.
                    onReconectarFibra(modemHost, modemUsername, modemPassword)
                    false
                }
            } else {
                false
            }
    }

    // GH#930 — Ajustes saiu da tab bar. O avatar abre diretamente Ajustes, que já contém
    // a edição de Perfil no mesmo padrão visual da lista de configurações.
    val onAbrirPerfilOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.settingsEnabled, ConsumerFeatureModuleIds.SETTINGS) &&
            Overlay.Ajustes !in overlayStack
        ) {
            overlayStack.add(Overlay.Ajustes)
        }
    }

    // Todas as raízes usam o mesmo acesso a Ajustes; não existe uma tela intermediária de Perfil.
    val onAbrirMenuDaRaiz: () -> Unit = onAbrirPerfilOverlay

    // GH#936 — Fase 7 MD3 (5f): "Monitoramento" agora é sheet dedicado (MonitoramentoSheet.kt),
    // hoisted aqui pra ser destino único do atalho no hub Ferramentas e da linha equivalente
    // dentro de Perfil/Ajustes — nenhum dos dois reimplementa os toggles.
    var showMonitoramentoSheet by remember { mutableStateOf(false) }
    val onAbrirMonitoramentoOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.settingsEnabled, ConsumerFeatureModuleIds.SETTINGS)) {
            showMonitoramentoSheet = true
        }
    }

    // Issue #1503 (Camada A/B) — ferramenta apontada pelo card contextual do diagnóstico
    // guiado. Só existe enquanto o usuário está vendo o hub via Overlay.Ferramentas aberto
    // a partir desse card; nunca fica "estático" — limpo ao fechar o overlay (voltar
    // explícito ou back físico, ver BackHandler abaixo).
    var ferramentaRecomendada by remember { mutableStateOf<TipoFerramenta?>(null) }
    val onAbrirFerramentaSugeridaOverlay: (TipoFerramenta) -> Unit = { tipo ->
        ferramentaRecomendada = tipo
        if (Overlay.Ferramentas !in overlayStack) overlayStack.add(Overlay.Ferramentas)
    }

    // Destino provisorio da conexao ao gateway — FibraModemScreen ja le sinal do modem, mas
    // NAO e a tela de detalhe definitiva do GPON/Roteador (isso e SIG-357, ainda nao existe).
    // Reusada por ambos entry points (nó do gateway na Home e linha do roteador em Ajustes).
    val onAbrirGatewayDetalhe: () -> Unit = {
        if (bloquearRota(featureFlags.fibraEnabled, ConsumerFeatureModuleIds.FIBRA)) {
            onReconectarFibra(modemHost ?: "", modemUsername, modemPassword)
            if (Overlay.Fibra !in overlayStack) overlayStack.add(Overlay.Fibra)
        }
    }

    // GH#1099 — CTA "Configure o acesso ao equipamento" (EquipamentoInternetScreen, estado
    // AcessoEquipamento.CREDENCIAIS_NECESSARIAS) abria Ajustes genérico via onAbrirPerfilOverlay
    // em vez do formulário real de credenciais — mesma GatewayConnectionSheet já usada pelo nó
    // do gateway na Início (ver Inicio2Screen.kt). Como o usuário já está dentro do overlay de
    // equipamento, aqui não empilha Overlay.Fibra de novo — só persiste a credencial e
    // reconecta com o dado novo, deixando a tela por trás atualizar sozinha.
    var showEquipamentoCredenciaisSheet by remember { mutableStateOf(false) }
    val onAbrirCredenciaisEquipamento: () -> Unit = { showEquipamentoCredenciaisSheet = true }

    // GH#933 — Fase 4: callbacks de overlay compartilhados entre os entry points antigos
    // (Home, SpeedTest, Ajustes) e os novos atalhos do hub Ferramentas — evita duplicar a
    // mesma lógica de push/pop em dois lugares.
    val onAbrirDispositivosOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.devicesEnabled, ConsumerFeatureModuleIds.DEVICES) &&
            Overlay.Dispositivos !in overlayStack
        ) {
            overlayStack.add(Overlay.Dispositivos)
        }
    }
    val onAbrirPingOverlay: () -> Unit = {
        if (Overlay.Ping !in overlayStack) overlayStack.add(Overlay.Ping)
    }
    val onAbrirLaudoOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.diagnosticoEnabled, ConsumerFeatureModuleIds.DIAGNOSTICO) &&
            Overlay.Laudo !in overlayStack
        ) {
            overlayStack.add(Overlay.Laudo)
        }
    }
    // Issue #1487 — o card "Jogos" em Ferramentas passou a abrir o Modo gamer fundido
    // (Overlay.ModoGamer) em vez do fluxo legado (Overlay.Jogos, removida). Mesmo overlay das
    // outras 2 entradas (ResultadoVelocidadeScreen/DiagnosticoGuiadoScreen) — mas aberto sem
    // Overlay.ResultadoVelocidade embaixo, então o resultado usa o `snapshotDiagnostico.input`
    // e `modoGamerPadrao` correntes mesmo sem um teste de velocidade recente (ver
    // `pingEspecificoMs` do ModoGamerEngine para o caso de não haver input nenhum ainda).
    val onAbrirModoGamerOverlay: () -> Unit = {
        if (Overlay.ModoGamer !in overlayStack) overlayStack.add(Overlay.ModoGamer)
    }
    // GH#1201 — nova ferramenta "Sinal WiFi" no hub Ferramentas.
    val onAbrirSinalWifiOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.wifiEnabled, ConsumerFeatureModuleIds.WIFI) &&
            Overlay.SinalWifi !in overlayStack
        ) {
            overlayStack.add(Overlay.SinalWifi)
        }
    }
    val onAbrirSinalCanaisOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.wifiEnabled, ConsumerFeatureModuleIds.WIFI) &&
            Overlay.SinalCanais !in overlayStack
        ) {
            overlayStack.add(Overlay.SinalCanais)
        }
    }
    // GH#1698 — a regra em si vive em `AppShellFerramentasRoot.kt` (função pura, testável sem
    // Compose). Aqui fica só a ligação com o estado corrente do shell.
    val disponibilidadeFerramenta: (TipoFerramenta) -> FerramentaDisponibilidade = { tipo ->
        resolverDisponibilidadeFerramenta(
            tipo = tipo,
            featureFlags = featureFlags,
            conectado = snapshotRede.conectado,
            temPermissaoLocalizacao = temPermissaoLocalizacao,
        )
    }
    val onAbrirDnsOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.dnsEnabled, ConsumerFeatureModuleIds.DNS) &&
            Overlay.Dns !in overlayStack
        ) {
            overlayStack.add(Overlay.Dns)
        }
    }
    // Stub Fase 1 (#930) reaproveitado pela Fase 4 — mesma engine/estado do "Fibra" já usado
    // pelo nó do gateway na Home, so que empilhando Overlay.EquipamentoInternet (nome
    // definitivo, ver TODO da Fase 5/#934 mais abaixo).
    //
    // GH#1806 — sem nenhum endereço salvo ainda, empilha Overlay.EquipamentoConectar em vez de
    // ir direto para a leitura técnica: mostra o que foi detectado na rede, o catálogo de
    // modelos compatíveis e o formulário de conexão antes de qualquer tentativa de leitura.
    // Com endereço já salvo, o comportamento não muda — vai direto para EquipamentoInternet.
    val onAbrirEquipamentoInternetOverlay: () -> Unit = {
        if (bloquearRota(featureFlags.fibraEnabled, ConsumerFeatureModuleIds.FIBRA)) {
            if (modemHost.isNullOrBlank()) {
                if (Overlay.EquipamentoConectar !in overlayStack) overlayStack.add(Overlay.EquipamentoConectar)
            } else {
                onReconectarFibra(modemHost, modemUsername, modemPassword)
                if (Overlay.EquipamentoInternet !in overlayStack) overlayStack.add(Overlay.EquipamentoInternet)
            }
        }
    }

    // GH#1806 — chamado quando GatewayConnectionSheet conecta com sucesso a partir da etapa
    // EquipamentoConectar: persiste a credencial, reconecta e troca o overlay pela leitura
    // técnica de verdade. Distinto de onGatewayConectado (esse vai para Overlay.Fibra, o
    // destino do nó do gateway na Home) porque aqui a origem é o hub Ferramentas.
    val onGatewayConectadoDoEquipamento: (
        ip: String,
        usuario: String,
        senha: String,
        lembrarSenha: Boolean,
        manterConectado: Boolean,
    ) -> Unit = { ip, usuario, senha, lembrarSenha, manterConectado ->
        onRegistrarConexaoGateway(
            ip,
            usuario,
            senha,
            lembrarSenha,
            manterConectado,
            bssidAtual,
            driverIdGatewayConfirmado,
        )
        if (tpLinkSnapshot == null) onReconectarFibra(ip, usuario, senha)
        overlayStack.remove(Overlay.EquipamentoConectar)
        if (Overlay.EquipamentoInternet !in overlayStack) overlayStack.add(Overlay.EquipamentoInternet)
    }

    // GH#1806 — "Pular por enquanto" na etapa EquipamentoConectar: segue para a leitura técnica
    // sem credencial nenhuma, que já sabe lidar com acesso indisponível/somente identificação.
    val onPularConexaoEquipamento: () -> Unit = {
        overlayStack.remove(Overlay.EquipamentoConectar)
        if (Overlay.EquipamentoInternet !in overlayStack) overlayStack.add(Overlay.EquipamentoInternet)
    }
    // GH#1031 — "Ver detalhes do Wi-Fi" na EquipamentoInternetScreen: aba Sinal é o único
    // lugar do app com detalhe real de Wi-Fi (RSSI, banda, varredura) — fecha o(s) overlay(s)
    // de equipamento e troca de aba em vez de empilhar mais um overlay.
    val onVerDetalhesWifiDoEquipamento: () -> Unit = {
        if (bloquearRota(featureFlags.wifiEnabled, ConsumerFeatureModuleIds.WIFI)) {
            overlayStack.remove(Overlay.Fibra)
            overlayStack.remove(Overlay.EquipamentoInternet)
            navigator.open(Overlay.SinalWifi)
        }
    }

    // GH#1698 — os 9 destinos do hub Ferramentas num grupo só. Antes eram 9 atribuições
    // repetidas literalmente duas vezes neste arquivo (a tab 4 e o Overlay.Ferramentas), que
    // precisavam ser editadas em dupla a cada ferramenta nova.
    val acoesFerramentas =
        AppShellFerramentasAcoes(
            onAbrirSinalCanais = onAbrirSinalCanaisOverlay,
            onAbrirDispositivos = onAbrirDispositivosOverlay,
            onAbrirEquipamentoInternet = onAbrirEquipamentoInternetOverlay,
            onAbrirPing = onAbrirPingOverlay,
            onAbrirDns = onAbrirDnsOverlay,
            onAbrirLaudo = onAbrirLaudoOverlay,
            onAbrirMonitoramento = onAbrirMonitoramentoOverlay,
            onAbrirModoGamer = onAbrirModoGamerOverlay,
            onAbrirSinalWifi = onAbrirSinalWifiOverlay,
        )

    // Callback unico chamado quando a GatewayConnectionSheet conecta com sucesso, em qualquer
    // um dos dois entry points — persiste a sessao e navega ao destino provisorio.
    val onGatewayConectado: (
        ip: String,
        usuario: String,
        senha: String,
        lembrarSenha: Boolean,
        manterConectado: Boolean,
    ) -> Unit = { ip, usuario, senha, lembrarSenha, manterConectado ->
        onRegistrarConexaoGateway(
            ip,
            usuario,
            senha,
            lembrarSenha,
            manterConectado,
            bssidAtual,
            driverIdGatewayConfirmado,
        )
        if (tpLinkSnapshot == null) {
            onAbrirGatewayDetalhe()
        } else if (Overlay.EquipamentoInternet !in overlayStack) {
            overlayStack.add(Overlay.EquipamentoInternet)
        }
    }

    var showForaDoWifiDialog by remember { mutableStateOf(false) }
    var showGerenciarDadosSheet by remember { mutableStateOf(false) }
    // GH#1358 — "Ajuda e suporte" e "Sobre o SignallQ" do Perfil: reaproveitam o
    // wrapper genérico SimpleInfoSheet/SobreSheet (já usados dentro de AjustesScreen), sem
    // duplicar conteúdo — só um segundo ponto de entrada hoisted aqui.
    var showAjudaSuporteSheet by remember { mutableStateOf(false) }
    var showSobreAppSheet by remember { mutableStateOf(false) }
    var testeAtivo by remember { mutableStateOf(false) }
    var mostrarConcluido by remember { mutableStateOf(false) }
    var entradaAssistInicial by remember { mutableStateOf(EntradaAssist.Padrao) }
    var tipoMidiaAssistInicial by remember { mutableStateOf<TipoMidiaAssist?>(null) }
    var objetivoAssistInicial by remember { mutableStateOf<ObjetivoDiagnostico?>(null) }
    val onAbrirDiagnosticoGuiado: (EntradaAssist, TipoMidiaAssist?, ObjetivoDiagnostico?) -> Unit = { entrada, midia, objetivo ->
        entradaAssistInicial = entrada
        tipoMidiaAssistInicial = midia
        objetivoAssistInicial = objetivo
        if (Overlay.DiagnosticoGuiado !in overlayStack) {
            overlayStack.add(Overlay.DiagnosticoGuiado)
        }
    }
    // GH#1223 RF-05 — resolução explícita do último resultado por timestamp, não implícita
    // por posição na lista (frágil se a ordenação da query/filtro mudar no futuro). A query
    // real (MedicaoDao.observarUltimas) já é ORDER BY timestampEpochMs DESC, então isso não
    // muda o comportamento hoje — é a rede de segurança que a issue pede.
    // GH#1265 — exclui `fonte == "monitor"` (ping sintético do MonitoramentoWorker, sem
    // download/upload) antes de escolher o "resultado anterior": sem isso, o ping mais recente
    // (mais frequente que um teste real) vencia por timestamp e virava o resultado exibido na
    // Home/Laudo com header "há X min" mas corpo vazio. Ver `resolverPrimeiraHistoria` em
    // HomeMedicaoAdapter.kt.
    val primeiraHistoria = remember(historico) { historico.resolverPrimeiraHistoria() }
    val medicaoHomeResolvida =
        remember(snapshotSpeedtest, primeiraHistoria, snapshotRede.wifiLinkSnapshot?.ssid) {
            resolverMedicaoHome(snapshotSpeedtest, primeiraHistoria, snapshotRede.wifiLinkSnapshot?.ssid)
        }
    // NAV-D: verifica IA ao entrar na tab Velocidade (índice 1)
    LaunchedEffect(navigator.selectedTab) {
        if (navigator.selectedTab == 1) onVerificarGemma()
        onScreenView(navigator.selectedRoot.screenName())
    }

    // GH#1480 (Epico #1347, F4) — a tab atual perdeu a flag em runtime (Admin desligou
    // remotamente enquanto o usuario ja estava nela, ou o cold-start caiu numa tab
    // desabilitada por padrao) -- redireciona pra primeira tab habilitada e registra o
    // bloqueio (unico caminho onde isso acontece sem tap explicito, ja que a tab bar
    // em si fica desabilitada/nao-clicavel para o resto dos casos).
    LaunchedEffect(featureFlags, navigator.selectedTab) {
        if (!featureFlags.tabHabilitada(navigator.selectedTab)) {
            tabModuleId(navigator.selectedTab)?.let { featureFlags.onFeatureBlocked(it) }
            navigator.selectedTab = featureFlags.primeiraTabHabilitada()
        }
    }

    LaunchedEffect(snapshotSpeedtest.estado) {
        when (snapshotSpeedtest.estado) {
            EstadoExecucaoSpeedtest.executando -> testeAtivo = true
            EstadoExecucaoSpeedtest.concluido -> {
                if (testeAtivo) {
                    // `onIniciarDiagnostico()` roda nos dois caminhos: é ele que produz o
                    // `DiagnosticInput` que o `DiagnosticoGuiadoEngine` consome. Sem ele o
                    // resultado guiado sairia com zero dimensões — indistinguível de "sua rede
                    // está ok" (GH#1704, §5 do reconhecimento).
                    onIniciarDiagnostico()
                    if (medicaoGuiada.consumirConclusao()) {
                        // Quem conduz a transição é a própria tela guiada, que observa o snapshot
                        // e avança da rota Analise (§8.5) para a conclusão (§8.6).
                        testeAtivo = false
                    } else {
                        mostrarConcluido = true
                        delay(400)
                        mostrarConcluido = false
                        if (Overlay.ResultadoVelocidade !in overlayStack) {
                            overlayStack.add(Overlay.ResultadoVelocidade)
                        }
                        testeAtivo = false
                    }
                }
            }
            else -> {}
        }
    }

    AppShellBackHandlers(navigator) { removido ->
        // SIG-173/#664 — back fisico tambem conta como "fechou o Laudo" para fins
        // de elegibilidade do prompt de avaliacao, mesmo caminho do botao voltar da tela.
        if (removido == Overlay.Laudo) onLaudoFechado()
        // Issue #1503 — back físico fechando o hub também limpa o badge contextual,
        // mesmo caminho do botão "voltar" explícito da tela (ver onVoltar abaixo).
        if (removido == Overlay.Ferramentas) ferramentaRecomendada = null
    }

    // #374: tela de erro do speedtest (overlay VelocidadeScreen) não tinha BackHandler
    // próprio — o back físico do sistema saía direto do app em vez de descartar o erro.
    // GH#1704: durante a medição do fluxo guiado o `VelocidadeScreen` nem compõe, então este
    // handler descartaria o erro por baixo de uma tela que mostra o próprio estado de falha —
    // e o back do usuário não voltaria ao roteiro de perguntas, como ele espera.
    BackHandler(
        enabled =
            deveTratarBackDeErroDoSpeedtest(
                medicaoGuiada.suprimeReacoesDoShell,
                snapshotSpeedtest.estado,
            ),
    ) {
        onCancelarTeste()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = c.bgPrimary,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (
                    shouldShowAppShellBottomBar(
                        navigator.isAtRoot,
                        snapshotSpeedtest.estado == EstadoExecucaoSpeedtest.executando,
                    )
                ) {
                    AppShellBottomBar(
                        c = c,
                        selectedTab = navigator.selectedTab,
                        testeAtivo = testeAtivo,
                        featureFlags = featureFlags,
                        onRootSelected = navigator::select,
                        onTabBloqueada = { moduleId -> bloquearRota(false, moduleId) },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                // GH#1698 (épico #1647) — ponto de extensão de root content. Histórico e
                // Ferramentas são despachados pelo registro; Início e Velocidade permanecem
                // inline porque concentram o estado operacional do diagnóstico.
                AppShellRootRegistry(
                    root = navigator.selectedRoot,
                    historico =
                        AppShellHistoricoRootEntry(
                            state = historicoTela,
                            adsGate = adsGate,
                            onAbrirMenu = onAbrirMenuDaRaiz,
                            onIniciarTeste = { navigator.select(AppShellRoot.Speed) },
                        ),
                    ferramentas =
                        AppShellFerramentasRootEntry(
                            acoes = acoesFerramentas,
                            disponibilidade = disponibilidadeFerramenta,
                            onAbrirMenu = onAbrirMenuDaRaiz,
                            onRegistrarAbertura = { tipo -> onScreenView(tipo.screenName()) },
                        ),
                ) { inlineRoot ->
                    when (inlineRoot) {
                        // NAV-E: raiz 0 — Início
                        AppShellRoot.Home ->
                            Inicio2Screen(
                                uiState =
                                    Inicio2UiStateMapper.map(
                                        snapshotRede = snapshotRede,
                                        estadoSpeedtest = snapshotSpeedtest.estado,
                                        diagnostico = snapshotDiagnostico,
                                        medicao = medicaoHomeResolvida,
                                    ),
                                onAnalisarConexao = {
                                    onAbrirDiagnosticoGuiado(EntradaAssist.Padrao, null, null)
                                    null
                                },
                                onAbrirPerfil = onAbrirPerfilOverlay,
                                onAlternarTema = {
                                    onDefinirTemaSelecionado(if (temaSelecionado == "escuro") "claro" else "escuro")
                                },
                                connectionTrail =
                                    Inicio2ConnectionTrailMapper.map(
                                        snapshotRede = snapshotRede,
                                        snapshotWifi = snapshotWifi,
                                        temPermissaoLocalizacao = temPermissaoLocalizacao,
                                        ispName = if (snapshotRede.estadoConexao == EstadoConexao.movel) operadoraMovel else ispInfoData?.isp,
                                        equipmentName = equipamentoLocal?.modelo,
                                        deviceName = deviceName,
                                        statusAoVivo = statusConectividadeAoVivo,
                                    ),
                                onAbrirVideos = {
                                    onAbrirDiagnosticoGuiado(EntradaAssist.VideoOuChamada, null, null)
                                },
                                statusAoVivo = statusConectividadeAoVivo,
                                diagnosticoBruto = ultimoDiagnosticoConectividade,
                                onIniciarSpeedtest = {
                                    navigator.select(AppShellRoot.Speed)
                                    onNovoTeste(modoAutomatico)
                                },
                                onConversarAssist = {
                                    onAbrirDiagnosticoGuiado(EntradaAssist.Padrao, null, null)
                                },
                                onVerResultado = {
                                    if (snapshotSpeedtest.resultado != null) {
                                        if (Overlay.ResultadoVelocidade !in overlayStack) {
                                            overlayStack.add(Overlay.ResultadoVelocidade)
                                        }
                                    } else if (primeiraHistoria != null) {
                                        onAbrirLaudoOverlay()
                                    } else {
                                        navigator.select(AppShellRoot.History)
                                    }
                                },
                                onAbrirSinalWifi = acoesFerramentas.onAbrirSinalWifi,
                                onAbrirJogos = acoesFerramentas.onAbrirModoGamer,
                            )
                        // NAV-E: raiz 1 — Velocidade (SpeedTestScreen como raiz fixa)
                        AppShellRoot.Speed ->
                            SpeedTestScreen(
                                snapshotSpeedtest = snapshotSpeedtest,
                                snapshotRede = snapshotRede,
                                ispInfo = ispInfoData,
                                localizacaoServidor = localizacaoServidorStr,
                                onIniciarTeste = { onNovoTeste(modoAutomatico) },
                                onCancelarTeste = onCancelarTeste,
                                onAbrirDnsBenchmark = onAbrirDnsOverlay,
                                onAbrirPing = onAbrirPingOverlay,
                                onVerResultado = {
                                    if (Overlay.ResultadoVelocidade !in
                                        overlayStack
                                    ) {
                                        overlayStack.add(Overlay.ResultadoVelocidade)
                                    }
                                },
                                onAbrirHistorico = { navigator.select(AppShellRoot.History) },
                                onAbrirAjustes = onAbrirPerfilOverlay,
                                onAbrirMenu = onAbrirMenuDaRaiz,
                                planoInternet = planoInternet,
                                speedtestPendenteModoMovel = speedtestPendenteModoMovel,
                                onConfirmarSpeedtestMovel = onConfirmarSpeedtestMovel,
                                onCancelarSpeedtestMovel = onCancelarSpeedtestMovel,
                                movelSnapshot = movelSnapshot,
                                adsGate = adsGate,
                            )
                        else ->
                            error(
                                "Raiz $inlineRoot chegou ao slot inline sem tratamento — " +
                                    "adicione-a ao AppShellRootRegistry quando o estado puder ser isolado.",
                            )
                    }
                }
            }
        }

        // Overlay de execução do speedtest — cobre toda a tela durante o teste.
        // GH#1704: suprimido quando a medição pertence ao fluxo guiado, que desenha a própria
        // rota Analise (§8.5). A supressão é explícita, e não por zIndex, porque zIndex só
        // decide quem fica por cima — o `VelocidadeScreen` continuaria composto por baixo, com
        // seu próprio `BackHandler` de erro concorrendo com o do fluxo guiado.
        AnimatedVisibility(
            visible =
                deveMostrarOverlayVelocidade(
                    medicaoGuiada.suprimeReacoesDoShell,
                    snapshotSpeedtest.estado,
                ),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            VelocidadeScreen(
                snapshot = snapshotSpeedtest,
                localizacaoServidor = localizacaoServidorStr,
                ispInfo = ispInfoData,
                onCancelar = onCancelarTeste,
                onReiniciar = { onNovoTeste(modoAutomatico) },
                onVoltar = onCancelarTeste,
            )
        }

        AnimatedVisibility(
            visible = mostrarConcluido,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val cLocal = LocalLkTokens.current
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(cLocal.bgPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.appshell_cd_concluido),
                        tint = cLocal.success,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.appshell_concluido),
                        style = MaterialTheme.typography.titleLarge,
                        color = cLocal.success,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        }

        // GH#1695 (épico #1647) — ponto de extensão de overlays: uma fatia nova pluga
        // um overlay criando `AppShellXxxOverlay.kt` e registrando em
        // `AppShellOverlayRegistry.kt` (ver KDoc de AppShellOverlayRegistry).
        // Limites conhecidos, medidos na revisão da PR #1697:
        //  - ROTAS não estão cobertas: os call sites de `Screen(` seguem inline aqui.
        //  - Overlay que precise de DADO NOVO ainda exige editar esta chamada, para
        //    passar o parâmetro (passo 4 do KDoc). O `Dns` foi assim: a própria PR
        //    que criou este comentário acrescentou 4 parâmetros logo abaixo.
        //  - Blocos de overlay são ~15% do que faz este arquivo crescer; root content
        //    e estado hoisted são os outros ~85% — ver GH#1698.
        AppShellOverlayRegistry(
            overlayStack = overlayStack,
            navigator = navigator,
            appVersion = BuildConfig.VERSION_NAME,
            onAbrirGerenciarDados = { showGerenciarDadosSheet = true },
            resultadoSpeedtest = snapshotSpeedtest.resultado,
            localizacaoServidor = localizacaoServidorStr,
            localDevice = equipamentoLocal,
            onGerarLaudo = onAbrirLaudoOverlay,
            temPermissaoLocalizacao = temPermissaoLocalizacao,
            localizacaoBloqueadaPermanentemente = localizacaoBloqueadaPermanentemente,
            onSolicitarPermissaoLocalizacao = onSolicitarPermissaoLocalizacao,
            snapshotDns = snapshotDns,
            dnsResolverIp = dnsResolverIp,
            snapshotRede = snapshotRede,
            onIniciarBenchmarkDns = onDispararBenchmarkDns,
            diagnosticoGuiado =
                AppShellDiagnosticoGuiadoEntry(
                    dados =
                        AppShellDiagnosticoGuiadoDados(
                            input = snapshotDiagnostico.input,
                            diagnosticReport = snapshotDiagnostico.relatorio,
                            resultado = snapshotSpeedtest.resultado,
                            analisadorState = analisadorState,
                            objetivoPreSelecionado = objetivoAssistInicial,
                            respostaPreSelecionadaPasso0 = null,
                            entradaAssist = entradaAssistInicial,
                            tipoMidiaAssist = tipoMidiaAssistInicial,
                            categoria = snapshotDiagnostico.relatorio?.decisao?.categoriaOrigem,
                            ispNome = ispInfoData?.isp,
                            operadoraMovel = operadoraMovel,
                            recommendationDecision = recommendationDecision,
                            recommendationFeedback = recommendationFeedback,
                            // GH#1706 — o shell já tinha os sinais; faltava repassá-los.
                            // `estadoConexao` entrou no bloqueio B10 (PR #1732, Rodada 5):
                            // `conectadoPorWifi` sozinho não distingue "estou no móvel" de
                            // "não tenho snapshot de Wi-Fi" (ethernet, desconectado, VPN).
                            contextoDoPlano =
                                ContextoDoPlano(
                                    temPermissaoLocalizacao = temPermissaoLocalizacao,
                                    conectadoPorWifi = snapshotRede.wifiLinkSnapshot != null,
                                    estadoConexao = snapshotRede.estadoConexao,
                                ),
                            comparacaoRetesteState = comparacaoRetesteState,
                        ),
                    operadora = operadoraResolvers,
                    acoes =
                        AppShellDiagnosticoGuiadoAcoes(
                            onAvaliarAssist = onAvaliarAssist,
                            onAnalisarProblema = onAnalisarProblema,
                            onResetarAnalisador = onResetarAnalisador,
                            onVoltar = { overlayStack.remove(Overlay.DiagnosticoGuiado) },
                            onAbrirPerfil = onAbrirPerfilOverlay,
                            onAlternarTema = {
                                onDefinirTemaSelecionado(if (temaSelecionado == "escuro") "claro" else "escuro")
                            },
                            onIrParaHome = {
                                overlayStack.remove(Overlay.DiagnosticoGuiado)
                                overlayStack.remove(Overlay.ResultadoVelocidade)
                                navigator.select(AppShellRoot.Home)
                            },
                            onIniciarModoGamer = {
                                if (Overlay.ModoGamer !in overlayStack) overlayStack.add(Overlay.ModoGamer)
                            },
                            onAbrirFerramentaSugerida = onAbrirFerramentaSugeridaOverlay,
                            onPlanoIniciado = onDiagnosticoPlanoIniciado,
                            onTestarNovamenteVinculado = onTestarNovamenteVinculado,
                            onRecommendationShown = onRecommendationShown,
                            onRecommendationClicked = onRecommendationClicked,
                            onRecommendationFeedback = onRecommendationFeedback,
                        ),
                    analise = medicaoGuiada.contrato,
                ),
        )

        // GH#1714 — ResultadoVelocidade extraído para AppShellResultadoVelocidadeOverlay.kt,
        // aplicando ao terceiro overlay de resultado o padrão que os outros dois já usavam.
        AppShellResultadoVelocidadeOverlay(
            overlayStack = overlayStack,
            entry =
                AppShellResultadoVelocidadeEntry(
                    resultado = snapshotSpeedtest.resultado,
                    snapshotDiagnostico = snapshotDiagnostico,
                    analisadorState = analisadorState,
                    localizacaoServidor = localizacaoServidorStr,
                    ispInfo = ispInfoData,
                    operadoraMovel = operadoraMovel,
                    adsGate = adsGate,
                    onTestarNovamente = {
                        overlayStack.remove(Overlay.ResultadoVelocidade)
                        // Issue #1656 — novo teste invalida a pré-seleção do Assist do anterior.
                    },
                    onIrParaHome = {
                        overlayStack.remove(Overlay.ResultadoVelocidade)
                        navigator.select(AppShellRoot.Home)
                    },
                    onVoltar = { overlayStack.remove(Overlay.ResultadoVelocidade) },
                    onCompartilhar = onCompartilharResultadoVelocidade,
                    onMedirNovamente = {
                        overlayStack.remove(Overlay.ResultadoVelocidade)
                        navigator.select(AppShellRoot.Speed)
                    },
                    onIniciarDiagnosticoGuiado = {
                        onAbrirDiagnosticoGuiado(EntradaAssist.ComDadosRecentes, null, null)
                    },
                    onIniciarModoGamer = {
                        if (Overlay.ModoGamer !in overlayStack) overlayStack.add(Overlay.ModoGamer)
                    },
                    onVerDetalhesTecnicos = {
                        if (Overlay.DetalhesTecnicos !in overlayStack) overlayStack.add(Overlay.DetalhesTecnicos)
                    },
                ),
        )

        // GH#1704 — DiagnosticoGuiado migrou para AppShellOverlayRegistry.

        AnimatedVisibility(
            visible = Overlay.ModoGamer in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.ModoGamer, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            ModoGamerScreen(
                medicaoBaseModoGamer = medicaoBaseModoGamer,
                networkIdAtual = networkIdAtual,
                medicaoGuiada = medicaoGuiada,
                redeMedida = snapshotRede.metered,
                padraoInicial = remember(modoGamerPadrao) { resolverPadraoModoGamer(modoGamerPadrao) },
                onSalvarPadrao = onSalvarModoGamerPadrao,
                onVoltar = { overlayStack.remove(Overlay.ModoGamer) },
                onIrParaHome = {
                    overlayStack.remove(Overlay.ModoGamer)
                    overlayStack.remove(Overlay.DiagnosticoGuiado)
                    overlayStack.remove(Overlay.ResultadoVelocidade)
                    navigator.select(AppShellRoot.Home)
                },
                adsGate = adsGate,
            )
        }

        // GH#1695 — DetalhesTecnicos migrou para AppShellOverlayRegistry.

        // GH#1659 — Laudo extraído para AppShellLaudoOverlay.kt (épico #1647), mesmo padrão
        // já aplicado a AppShellResultadoVelocidadeOverlay.kt (#1714) e
        // AppShellDetalhesTecnicosOverlay.kt (#1695).
        AppShellLaudoOverlay(
            overlayStack = overlayStack,
            entry =
                AppShellLaudoEntry(
                    snapshotDiagnostico = snapshotDiagnostico,
                    ultimaMedicao = primeiraHistoria,
                    nomeUsuario = nomeUsuario,
                    operadora = operadora,
                    ssid = connectedNetwork?.ssid,
                    ipLocal = localIpStr,
                    ipPublico = publicIpStr,
                    velocidadeContratadaMbps = planoInternet.filter { it.isDigit() }.toIntOrNull(),
                    conectado = snapshotRede.conectado,
                    onVoltar = {
                        overlayStack.remove(Overlay.Laudo)
                        onLaudoFechado()
                    },
                ),
        )

        // GH#1695 — Privacidade, Novidades e Ping migraram para AppShellOverlayRegistry.

        AnimatedVisibility(
            visible = Overlay.Fibra in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.Fibra, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            EquipamentoInternetScreen(
                snapshotFibra = snapshotFibra,
                localDevice = equipamentoLocal,
                natStatus = natStatus,
                modemHost = modemHost,
                modemUsername = modemUsername,
                modemPassword = modemPassword,
                onVoltar = { overlayStack.remove(Overlay.Fibra) },
                onRetentar = {
                    if (tpLinkSnapshot != null) atualizarTpLink() else onReconectarFibra(modemHost ?: "", modemUsername, modemPassword)
                },
                onAbrirAjustes = onAbrirCredenciaisEquipamento,
                onReiniciarEquipamento = onReiniciarEquipamento,
                onVerDispositivos = onAbrirDispositivosOverlay,
                onExecutarDiagnostico = onAbrirLaudoOverlay,
                onVerDetalhesWifi = onVerDetalhesWifiDoEquipamento,
                onAbrirMenu = onAbrirMenuDaRaiz,
            )
        }

        AnimatedVisibility(
            visible = Overlay.Dispositivos in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.Dispositivos, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            DispositivosScreen(
                snapshotDevices = snapshotDevices,
                snapshotRede = snapshotRede,
                onRefresh = {
                    onRefreshDispositivos()
                },
                apelidos = apelidos,
                onSalvarApelido = onSalvarApelido,
                onVoltar = { overlayStack.remove(Overlay.Dispositivos) },
                onAbrirMenu = onAbrirMenuDaRaiz,
                onAlternarTema = {
                    onDefinirTemaSelecionado(if (temaSelecionado == "escuro") "claro" else "escuro")
                },
                bandasWifi = bandasWifiGateway,
                adsGate = adsGate,
                correlacoesTopologia = wifi.correlacoesTopologia,
            )
        }

        // GH#1806 — etapa de conexão aberta a partir do hub Ferramentas quando ainda não há
        // endereço salvo (ver onAbrirEquipamentoInternetOverlay). Mesmo GatewayConnectionSheet e
        // GatewayCompatibleModelsSheetContent do resto do app, hospedados dentro da tela.
        AnimatedVisibility(
            visible = Overlay.EquipamentoConectar in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.EquipamentoConectar, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            EquipamentoConectarScreen(
                enderecoDetectado = gatewayIpDetectado,
                conectar = gatewayConnectionService,
                credentialRequirement = requisitoCredencialGateway,
                onVoltar = { overlayStack.remove(Overlay.EquipamentoConectar) },
                onAbrirMenu = onAbrirMenuDaRaiz,
                onConectado = onGatewayConectadoDoEquipamento,
                onPular = onPularConexaoEquipamento,
            )
        }

        // GH#934 — Fase 5 MD3: EquipamentoInternetScreen real, composta por capacidade
        // (engine plugável Nokia, unico provider real hoje — ver decisao #1 do plano).
        AnimatedVisibility(
            visible = Overlay.EquipamentoInternet in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.EquipamentoInternet, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            EquipamentoInternetScreen(
                snapshotFibra = snapshotFibra,
                localDevice = equipamentoLocal,
                natStatus = natStatus,
                modemHost = modemHost,
                modemUsername = modemUsername,
                modemPassword = modemPassword,
                onVoltar = { overlayStack.remove(Overlay.EquipamentoInternet) },
                onRetentar = {
                    if (tpLinkSnapshot != null) atualizarTpLink() else onReconectarFibra(modemHost ?: "", modemUsername, modemPassword)
                },
                onAbrirAjustes = onAbrirCredenciaisEquipamento,
                onReiniciarEquipamento = onReiniciarEquipamento,
                onVerDispositivos = onAbrirDispositivosOverlay,
                onExecutarDiagnostico = onAbrirLaudoOverlay,
                onVerDetalhesWifi = onVerDetalhesWifiDoEquipamento,
                onAbrirMenu = onAbrirMenuDaRaiz,
            )
        }

        // GH#933 — Fase 4: hub real de atalhos (5a-5g). Overlay.Ferramentas fica disponível
        // como ponto de entrada fora da tab bar (ex.: atalho futuro na Home) — hoje só a tab
        // 4 usa FerramentasScreen diretamente, sem passar por este overlay.
        AnimatedVisibility(
            visible = Overlay.Ferramentas in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.Ferramentas, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            FerramentasScreen(
                onAbrirMenu = onAbrirMenuDaRaiz,
                onAbrirSinalCanais = acoesFerramentas.onAbrirSinalCanais,
                onAbrirDispositivos = acoesFerramentas.onAbrirDispositivos,
                onAbrirEquipamentoInternet = acoesFerramentas.onAbrirEquipamentoInternet,
                onAbrirPing = acoesFerramentas.onAbrirPing,
                onAbrirDns = acoesFerramentas.onAbrirDns,
                onAbrirLaudo = acoesFerramentas.onAbrirLaudo,
                onAbrirMonitoramento = acoesFerramentas.onAbrirMonitoramento,
                onAbrirJogos = acoesFerramentas.onAbrirModoGamer,
                onAbrirSinalWifi = acoesFerramentas.onAbrirSinalWifi,
                disponibilidade = disponibilidadeFerramenta,
                onRegistrarAbertura = { tipo -> onScreenView(tipo.screenName()) },
                // Issue #1503 — único consumidor real de Overlay.Ferramentas hoje: o
                // card contextual do diagnóstico guiado. Botão "voltar" explícito
                // limpa o badge, mesmo comportamento do back físico (ver BackHandler).
                ferramentaRecomendada = ferramentaRecomendada,
                onVoltar = {
                    overlayStack.remove(Overlay.Ferramentas)
                    ferramentaRecomendada = null
                },
            )
        }

        // GH#1695 — Dns migrou para AppShellOverlayRegistry.

        // Issue #1487 — fluxo legado "Jogos" (GH#935, 5 etapas) removido: fundido no Modo
        // gamer (Overlay.ModoGamer acima), acessado pelo mesmo card "Jogos" em
        // Ferramentas via onAbrirModoGamerOverlay.

        AnimatedVisibility(
            visible = Overlay.SinalCanais in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.SinalCanais, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            SinalScreen(
                snapshotWifi = snapshotWifi,
                connectedNetwork = connectedNetwork,
                estadoConexao = snapshotRede.estadoConexao,
                conectado = snapshotRede.conectado,
                movelSnapshot = movelSnapshot,
                simsAtivos = simsAtivos,
                localIp = localIpStr,
                temPermissaoTelefonia = temPermissaoTelefonia,
                onSolicitarPermissaoTelefonia = onSolicitarPermissaoTelefonia,
                temPermissaoLocalizacao = temPermissaoLocalizacao,
                localizacaoBloqueadaPermanentemente = localizacaoBloqueadaPermanentemente,
                onSolicitarPermissaoLocalizacao = onSolicitarPermissaoLocalizacao,
                onRefresh = onRefreshSinal,
                onVoltar = { overlayStack.remove(Overlay.SinalCanais) },
                exibirBotaoVoltar = true,
                onAbrirMenu = onAbrirMenuDaRaiz,
                onAlternarTema = {
                    onDefinirTemaSelecionado(if (temaSelecionado == "escuro") "claro" else "escuro")
                },
                wifiLinkSnapshot = snapshotRede.wifiLinkSnapshot,
                dispositivosRede = snapshotDevices.dispositivos,
                apelidos = apelidos,
                onSalvarApelido = onSalvarApelido,
                resolveOperadoraIdentidadeLocal = resolveOperadoraIdentidadeLocal,
                resolveOperadoraIdentidadeRemota = resolveOperadoraIdentidadeRemota,
                onAbrirLaudo = onAbrirLaudoOverlay,
            )
        }

        // GH#1695 — SinalWifi e Termos migraram para AppShellOverlayRegistry.

        AnimatedVisibility(
            visible = Overlay.Ajustes in overlayStack,
            modifier = Modifier.zIndex(rememberOverlayZIndex(Overlay.Ajustes, overlayStack)),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            // GH#1249 -- provedor "detectado" pra comparar com o ConnectionProfilePersistido da
            // rede atual: ISP resolvido por IP em Wi-Fi/Ethernet, operadora do SIM ativo em rede
            // móvel (requisito B -- rede móvel nunca sobrescreve o cadastro da internet fixa,
            // porque cada uma tem seu próprio networkId/perfil, nunca o mesmo registro).
            val providerDetectadoAtual =
                when (snapshotRede.estadoConexao) {
                    EstadoConexao.wifi -> ispInfoData?.isp
                    EstadoConexao.movel -> movelSnapshot?.operadora
                    else -> null
                }
            val minhaConexaoUiState =
                remember(connectionProfileAtual, providerDetectadoAtual) {
                    mapMinhaConexaoUiState(connectionProfileAtual, providerDetectadoAtual)
                }
            AjustesScreen(
                perfil =
                    AjustesPerfilState(
                        nomeUsuario = nomeUsuario,
                        fotoUriUsuario = fotoUriUsuario,
                        deviceName = deviceName,
                        appVersion = BuildConfig.VERSION_NAME,
                        onSalvarPerfil = onSalvarPerfil,
                    ),
                provedor =
                    AjustesProvedorState(
                        planoInternet = planoInternet,
                        regiao = regiao,
                        minhaConexao = minhaConexaoUiState,
                        onSalvarConnectionProfile = onSalvarConnectionProfile,
                    ),
                monitoramento =
                    AjustesMonitoramentoState(
                        monitoramentoAtivo = monitoramentoAtivo,
                        analiseAvancada = analiseAvancada,
                        notificacaoLatenciaAtiva = notificacaoLatenciaAtiva,
                        notificacaoDnsAtiva = notificacaoDnsAtiva,
                        notificacaoRssiAtiva = notificacaoRssiAtiva,
                        notificacaoSemInternetAtiva = notificacaoSemInternetAtiva,
                        onAtivarMonitoramento = onAtivarMonitoramento,
                        onDefinirAnaliseAvancada = onDefinirAnaliseAvancada,
                        onDefinirNotificacaoLatenciaAtiva = onDefinirNotificacaoLatenciaAtiva,
                        onDefinirNotificacaoDnsAtiva = onDefinirNotificacaoDnsAtiva,
                        onDefinirNotificacaoRssiAtiva = onDefinirNotificacaoRssiAtiva,
                        onDefinirNotificacaoSemInternetAtiva = onDefinirNotificacaoSemInternetAtiva,
                    ),
                modem =
                    AjustesModemState(
                        modemHost = modemHost,
                        modemUsername = modemUsername,
                        modemPassword = modemPassword,
                        modemPermanecerConectado = modemPermanecerConectado,
                        gatewayIpDetectado = gatewayIpDetectado,
                        onSalvarConfiguracaoModem = onSalvarConfiguracaoModem,
                        onConectarFibra = { host, user, pass -> onReconectarFibra(host, user, pass) },
                        gatewaySessaoValida = gatewaySessaoValida,
                        conectarGateway = gatewayConnectionService,
                        onGatewayConectado = onGatewayConectado,
                        bandasWifi = bandasWifiGateway,
                        dispositivosNaRede = clientesNaRedeGateway,
                    ),
                temaSelecionado = temaSelecionado,
                onDefinirTemaSelecionado = onDefinirTemaSelecionado,
                limiteAlertaMbps = limiteAlertaMbps,
                onSalvarLimiteAlerta = onSalvarLimiteAlerta,
                onLimparHistorico = onLimparHistorico,
                onApagarDadosLocais = onApagarDadosLocais,
                onResetarApp = onResetarApp,
                dadosLocaisAcaoEstado = dadosLocaisAcaoEstado,
                onConsumirDadosLocaisAcaoEstado = onConsumirDadosLocaisAcaoEstado,
                quantidadeHistorico = historico.size,
                quantidadeApelidos = apelidos.size,
                onAbrirHistorico = {
                    overlayStack.remove(Overlay.Ajustes)
                    navigator.select(AppShellRoot.History)
                },
                onAbrirLaudo = onAbrirLaudoOverlay,
                onAbrirMonitoramento = onAbrirMonitoramentoOverlay,
                onAbrirPrivacidade = { if (Overlay.Privacidade !in overlayStack) overlayStack.add(Overlay.Privacidade) },
                onAbrirTermos = { if (Overlay.Termos !in overlayStack) overlayStack.add(Overlay.Termos) },
                onAbrirNovidades = { if (Overlay.Novidades !in overlayStack) overlayStack.add(Overlay.Novidades) },
                // GH#530 — mesmo destino provisório usado pelo nó do gateway na Home.
                onAbrirFibra = onAbrirGatewayDetalhe,
                dadosMoveis =
                    AjustesDadosMoveisState(
                        speedtestPermiteHeavyMovel = speedtestPermiteHeavyMovel,
                        speedtestMbConsumidosMes = speedtestMbConsumidosMes,
                        onSetSpeedtestPermiteHeavyMovel = onSetSpeedtestPermiteHeavyMovel,
                    ),
                onVoltar = { overlayStack.remove(Overlay.Ajustes) },
            )
        }

        if (showForaDoWifiDialog) {
            ForaDoWifiDialog(
                onContinuar = {
                    showForaDoWifiDialog = false
                    // Usuario ja confirmou o aviso de dados moveis aqui — pula o segundo
                    // gate de confirmacao em rede medida (#516).
                    onNovoTesteJaConfirmadoMovel(modoAutomatico)
                },
                onCancelar = { showForaDoWifiDialog = false },
            )
        }

        // GH#1512 — Speedtest foi interrompido porque o Wi-Fi esta conectado sem
        // internet: mostra a conclusao do diagnostico local em vez de deixar a tela
        // presa em "executando" ou exibir um erro generico.
        diagnosticoConectividade?.let { diagnostico ->
            DiagnosticoConectividadeDialog(
                diagnostico = diagnostico,
                onDismiss = onLimparDiagnosticoConectividade,
            )
        }

        if (showGerenciarDadosSheet) {
            DadosLocaisSheet(
                c = c,
                onDismiss = { showGerenciarDadosSheet = false },
                onLimparHistorico = onLimparHistorico,
                onApagarDadosLocais = onApagarDadosLocais,
                onResetarApp = onResetarApp,
                estado = dadosLocaisAcaoEstado,
                onConsumirEstado = onConsumirDadosLocaisAcaoEstado,
                quantidadeHistorico = historico.size,
                quantidadeApelidos = apelidos.size,
                onAbrirHistorico = {
                    showGerenciarDadosSheet = false
                    navigator.select(AppShellRoot.History)
                },
            )
        }

        if (showMonitoramentoSheet) {
            MonitoramentoSheet(
                c = c,
                analiseAvancada = analiseAvancada,
                monitoramentoAtivo = monitoramentoAtivo,
                notificacaoLatenciaAtiva = notificacaoLatenciaAtiva,
                notificacaoDnsAtiva = notificacaoDnsAtiva,
                notificacaoRssiAtiva = notificacaoRssiAtiva,
                notificacaoSemInternetAtiva = notificacaoSemInternetAtiva,
                onDismiss = { showMonitoramentoSheet = false },
                onDefinirAnaliseAvancada = onDefinirAnaliseAvancada,
                onAtivarMonitoramento = onAtivarMonitoramento,
                onDefinirNotificacaoLatenciaAtiva = onDefinirNotificacaoLatenciaAtiva,
                onDefinirNotificacaoDnsAtiva = onDefinirNotificacaoDnsAtiva,
                onDefinirNotificacaoRssiAtiva = onDefinirNotificacaoRssiAtiva,
                onDefinirNotificacaoSemInternetAtiva = onDefinirNotificacaoSemInternetAtiva,
                statusServicos = statusServicos,
                onAtualizarStatusServicos = onAtualizarStatusServicos,
                onDefinirSeguimentoServico = onDefinirSeguimentoServico,
            )
        }

        // GH#1099 — formulário real de credenciais do equipamento, aberto pelo CTA "Revisar
        // configurações"/"Configure o acesso" dentro do overlay de Fibra/EquipamentoInternet.
        // Mesmo componente e mecânica do nó do gateway na Home (GatewayConnectionSheet).
        if (showEquipamentoCredenciaisSheet) {
            GatewayConnectionSheet(
                ipInicial = modemHost,
                credentialRequirement = requisitoCredencialGateway,
                usuarioInicial = modemUsername,
                senhaInicial = modemPassword,
                lembrarSenhaInicial = modemUsername.isNotBlank() || modemPassword.isNotBlank(),
                manterConectadoInicial = modemPermanecerConectado,
                onDismissRequest = { showEquipamentoCredenciaisSheet = false },
                conectar = gatewayConnectionService,
                onConectado = { ip, usuario, senha, lembrarSenha, manterConectado ->
                    onRegistrarConexaoGateway(
                        ip,
                        usuario,
                        senha,
                        lembrarSenha,
                        manterConectado,
                        bssidAtual,
                        driverIdGatewayConfirmado,
                    )
                    if (tpLinkSnapshot == null) onReconectarFibra(ip, usuario, senha)
                },
            )
        }

        // GH#1358 — "Ajuda e suporte" do Perfil: mesmo wrapper genérico SimpleInfoSheet
        // já usado por SobreSheet dentro de AjustesScreen.kt, sem tela nova.
        if (showAjudaSuporteSheet) {
            SimpleInfoSheet(
                c = c,
                titulo = stringResource(R.string.appshell_menu_ajuda_suporte),
                onDismiss = { showAjudaSuporteSheet = false },
            ) {
                AjudaSuporteContent(
                    onAbrirEmail = { abrirEmailSuporte(context) },
                    onCopiarEmail = { email ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("E-mail de suporte SignallQ", email))
                    },
                    onAbrirLaudo = {
                        showAjudaSuporteSheet = false
                        onAbrirLaudoOverlay()
                    },
                )
            }
        }

        // GH#1358 — "Sobre o SignallQ" do Perfil: mesmo SobreSheet já usado dentro de
        // AjustesScreen.kt (segundo ponto de entrada hoisted aqui, sem duplicar conteúdo).
        if (showSobreAppSheet) {
            SobreSheet(
                c = c,
                appVersion = BuildConfig.VERSION_NAME,
                onDismiss = { showSobreAppSheet = false },
                onAbrirTermos = { if (Overlay.Termos !in overlayStack) overlayStack.add(Overlay.Termos) },
                onAbrirPrivacidade = { if (Overlay.Privacidade !in overlayStack) overlayStack.add(Overlay.Privacidade) },
            )
        }
    }
}

internal fun abrirEmailSuporte(
    context: Context,
    launch: (Intent) -> Unit = context::startActivity,
): Boolean =
    try {
        launch(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:suporte@signallq.com"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

// ─── Dialog: fora do Wi-Fi ────────────────────────────────────────────────────

@Composable
private fun ForaDoWifiDialog(
    onContinuar: () -> Unit,
    onCancelar: () -> Unit,
) {
    val c = LocalLkTokens.current
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.appshell_sem_wifi), fontWeight = FontWeight.W600) },
        text = {
            Text(
                "Você está usando dados móveis. Fazer um teste de velocidade pode consumir uma quantidade significativa do seu plano de dados.\n\nDeseja continuar mesmo assim?",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onContinuar) {
                Text(stringResource(R.string.appshell_continuar_mesmo_assim), color = c.warning)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.global_btn_cancelar)) }
        },
    )
}

// ─── Dialog: diagnostico local de conectividade (GH#1512) ────────────────────
//
// GH#1738 — `DiagnosticoConectividadeDialog` migrou para `VelocidadeScreen.kt` (visual 2.0,
// tokens em vez de `fontSize` cru). Este call site continua igual: mesma função, mesmo nome,
// mesma assinatura, só de arquivo novo — fora da região `medicaoGuiada` compartilhada com #1704.
