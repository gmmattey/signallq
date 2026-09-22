package io.signallq.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.signallq.app.core.database.ApelidoDispositivoEntity
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.database.SignallQDatabase
import io.signallq.app.core.datastore.ConnectionProfilePersistido
import io.signallq.app.core.datastore.ModoGamerPadraoPersistido
import io.signallq.app.core.datastore.PreferenciasAppRepository
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.DnsDiagnosticInput
import io.signallq.app.core.diagnostico.ElegibilidadeMedicaoBaseModoGamer
import io.signallq.app.core.diagnostico.FibraDiagnosticInput
import io.signallq.app.core.diagnostico.HistoricalDiagnosticInput
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.MedicaoBaseModoGamer
import io.signallq.app.core.diagnostico.MobileDiagnosticInput
import io.signallq.app.core.diagnostico.RedeWifiVizinha
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import io.signallq.app.core.diagnostico.WifiScanDiagnosticInput
import io.signallq.app.core.diagnostico.avaliarElegibilidadeMedicaoBaseModoGamer
import io.signallq.app.core.diagnostico.banda
import io.signallq.app.core.diagnostico.rotuloConfianca
import io.signallq.app.core.diagnostico.topology.model.NatStatus
import io.signallq.app.core.featureflags.FeatureFlagKeys
import io.signallq.app.core.featureflags.FeatureFlagProvider
import io.signallq.app.core.network.DiagnosticoComparacaoConcluida
import io.signallq.app.core.network.DiagnosticoRetesteIniciado
import io.signallq.app.core.network.DispatcherProvider
import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.core.network.MonitorRede
import io.signallq.app.core.network.NetworkCapabilitiesProvider
import io.signallq.app.core.network.contracts.localdevice.ClientSnapshot
import io.signallq.app.core.network.contracts.localdevice.LocalNetworkDeviceSnapshot
import io.signallq.app.core.network.contracts.topologia.ClassificacaoTopologia
import io.signallq.app.core.network.contracts.topologia.PapelTopologia
import io.signallq.app.core.network.contracts.wifi.RedeVizinha
import io.signallq.app.core.network.contracts.wifi.channel.freqToChannel
import io.signallq.app.core.network.topologia.engine.TopologiaRedeEngine
import io.signallq.app.core.network.wifi.ScannerRedesWifi
import io.signallq.app.core.permissions.GerenciadorPermissoesRede
import io.signallq.app.core.recommendation.RecommendationDecision
import io.signallq.app.core.recommendation.RecommendationFeedbackType
import io.signallq.app.core.recommendation.RecommendationFlags
import io.signallq.app.core.recommendation.analytics.RecommendationAnalyticsEventName
import io.signallq.app.core.recommendation.analytics.RecommendationAnalyticsTracker
import io.signallq.app.core.recommendation.analytics.toAnalyticsPayload
import io.signallq.app.core.telephony.MonitorTelephony
import io.signallq.app.core.telephony.MovelSimSnapshot
import io.signallq.app.core.telephony.MovelSnapshot
import io.signallq.app.feature.devices.ResultadoCorrelacaoTopologia
import io.signallq.app.feature.devices.ScannerDispositivos
import io.signallq.app.feature.devices.SnapshotScanDispositivos
import io.signallq.app.feature.devices.correlacionarDispositivoComTopologia
import io.signallq.app.feature.diagnostico.DiagnosticOrchestrator
import io.signallq.app.feature.diagnostico.EstadoDiagnostico
import io.signallq.app.feature.diagnostico.ai.AdditionalAiContext
import io.signallq.app.feature.diagnostico.ai.AiDiagnosisRepository
import io.signallq.app.feature.diagnostico.ai.AiDiagnosisState
import io.signallq.app.feature.diagnostico.ai.AiDispositivosInfo
import io.signallq.app.feature.diagnostico.ai.AiFallbackFactory
import io.signallq.app.feature.diagnostico.ai.AiMovelInfo
import io.signallq.app.feature.diagnostico.ai.AiRedeVizinha
import io.signallq.app.feature.diagnostico.ai.AiTesteHistorico
import io.signallq.app.feature.diagnostico.ai.DiagnosisAiContextFactory
import io.signallq.app.feature.diagnostico.recommendation.RecommendationDecisionCoordinator
import io.signallq.app.feature.diagnostico.topology.TopologyDiagnostic
import io.signallq.app.feature.dns.AvaliadorCoerenciaDns
import io.signallq.app.feature.dns.BenchmarkDns
import io.signallq.app.feature.dns.DiagnosticoCoerenciaDns
import io.signallq.app.feature.dns.EstadoBenchmarkDns
import io.signallq.app.feature.dns.OrientadorConfiguracaoDns
import io.signallq.app.feature.fibra.EstadoFibra
import io.signallq.app.feature.fibra.ExecutorFibra
import io.signallq.app.feature.fibra.NokiaLocalDeviceMapper
import io.signallq.app.feature.history.BlocoUptime
import io.signallq.app.feature.history.ObservadorHistoricoRoom
import io.signallq.app.feature.history.ResumoHistorico
import io.signallq.app.feature.history.UptimeChartUseCase
import io.signallq.app.feature.history.calcularVereditoReteste
import io.signallq.app.feature.history.paraTelemetriaReteste
import io.signallq.app.feature.history.rotuloComparacaoReteste
import io.signallq.app.feature.speedtest.ExecutorSpeedtest
import io.signallq.app.feature.speedtest.ModoSpeedtest
import io.signallq.app.feature.speedtest.connectivity.ConnectivityDiagnosisMensagem
import io.signallq.app.feature.speedtest.connectivity.ConnectivityDiagnosisPresenter
import io.signallq.app.feature.speedtest.connectivity.ConnectivityDiagnosisRepository
import io.signallq.app.feature.speedtest.connectivity.indicaAusenciaDeInternetParaBloquearSpeedtest
import io.signallq.app.featureflags.ConsumerFeatureGateCoordinator
import io.signallq.app.monitoramento.MonitoramentoScheduler
import io.signallq.app.network.IspInfoCache
import io.signallq.app.notificacao.SignallQNotificationHelper
import io.signallq.app.review.ReviewPromptPolicy
import io.signallq.app.servicestatus.ServiceStatusRepository
import io.signallq.app.servicestatus.StatusServicosUiState
import io.signallq.app.speedtest.SpeedtestPersistenceCoordinator
import io.signallq.app.ui.BancoOperadoras
import io.signallq.app.ui.ConnectionNodeType
import io.signallq.app.ui.FiltroConexaoHistorico
import io.signallq.app.ui.GatewayInfo
import io.signallq.app.ui.HistoryPoint
import io.signallq.app.ui.IspInfo
import io.signallq.app.ui.screen.AcaoDadosLocaisEstado
import io.signallq.app.ui.screen.AnalisadorState
import io.signallq.app.ui.screen.AppShellFeatureFlagsState
import io.signallq.app.ui.screen.ComparacaoRetesteUiState
import io.signallq.app.ui.screen.TipoAcaoDadosLocais
import io.signallq.app.ui.screen.resolverNetworkIdAtual
import io.signallq.app.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt
import io.signallq.app.ui.ConnectionType as UiConnectionType

/** NDS-Snapshot-06 (issue #1838) — janela de 30 dias usada para consultar
 *  `MedicaoDao.buscarDesde` antes de agregar em `agregarHistoricoNds`. */
private const val JANELA_30D_MS = 30L * 24 * 60 * 60 * 1000

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        val preferenciasAppRepository: PreferenciasAppRepository,
        val monitorRede: MonitorRede,
        val networkCapabilitiesProvider: NetworkCapabilitiesProvider,
        val gerenciadorPermissoes: GerenciadorPermissoesRede,
        val scannerDispositivos: ScannerDispositivos,
        val benchmarkDns: BenchmarkDns,
        val executorSpeedtest: ExecutorSpeedtest,
        val scannerRedesWifi: ScannerRedesWifi,
        val executorFibra: ExecutorFibra,
        /** Monitor de telefonia movel — instanciado pelo Hilt. NAO inicia automaticamente:
         *  o start so acontece quando [iniciarMonitorTelefoniaSeMovel] e chamado
         *  (rede movel ativa + permissao concedida). Em Wi-Fi/Ethernet, o monitor
         *  fica idle e nao consome bateria com callbacks de TelephonyManager. */
        val monitorTelephony: MonitorTelephony,
        private val bancoDados: SignallQDatabase,
        private val dispatchers: DispatcherProvider,
        /** AiDiagnosisRepository injetada pelo Hilt como @Singleton (DiagnosticoModule).
         *  Antes era instanciada manualmente via lazy (segunda instancia alem da do Orchestrator). */
        val diagAiRepository: AiDiagnosisRepository,
        /** DiagnosticOrchestrator injetado pelo Hilt como @Singleton (DiagnosticoModule).
         *  Antes era instanciado via lazy: `by lazy { DiagnosticOrchestrator() }`. */
        _diagnosticOrchestrator: DiagnosticOrchestrator,
        private val speedtestPersistenceCoordinator: SpeedtestPersistenceCoordinator,
        /** Cache compartilhado do ultimo ISP resolvido — permite que o
         *  SpeedtestPersistenceCoordinator envie o provedor Wi-Fi ao ingest (GH#412). */
        private val ispInfoCache: IspInfoCache,
        /** TopologyDiagnostic injetado pelo Hilt como @Singleton (DiagnosticoModule).
         *  Usado para classificar NAT/CGNAT (SIG-279) — disparado uma unica vez por
         *  sessao dentro de [iniciarRotinasNaoSpeedtest], mesmo padrao de coletarIspInfo. */
        private val topologyDiagnostic: TopologyDiagnostic,
        /** Unico ponto de integracao com o Recommendation Engine (issue #790/#811/#812) --
         *  monta o request a partir do relatorio/input do diagnostico, le/persiste o
         *  historico local (Room) e devolve a decisao a exibir na experiencia pos-diagnostico
         *  (issue #813). */
        private val recommendationDecisionCoordinator: RecommendationDecisionCoordinator,
        /** Eventos `recommendation_*` (issue #790) do Recommendation Engine -- distinto do
         *  AnalyticsHelper/AnalyticsTracker que cobrem o funil principal (SIG-155) e
         *  feature_used (SIG-134) — instrumentados em MainActivity/DiagnosticOrchestrator,
         *  nao neste ViewModel. */
        private val recommendationAnalyticsTracker: RecommendationAnalyticsTracker,
        /** GH#1480 (Epico #1347, F4) — deriva [featureFlagsState] do FeatureFlagProvider real
         *  (F1/#1477). Toda a logica de flag mora no coordinator, nao aqui (regra de higiene
         *  4.2 -- MainViewModel nao ganha responsabilidade nova). */
        private val featureGateCoordinator: ConsumerFeatureGateCoordinator,
        /** GH#1512 — diagnostico local de conectividade (Wi-Fi conectado sem internet).
         *  Mesmo repositorio/motor usado por SpeedtestViewModel (core:network) -- nao
         *  duplica logica; so intervem no cenario exato do bug (ver
         *  [interromperSpeedtestPorWifiSemInternet]). */
        private val connectivityDiagnosisRepository: ConnectivityDiagnosisRepository,
        /** NDS-02k PR2 (issue #1746) — le `consumer_diagnostico_nds_live_enabled` dentro de
         *  [analisarProblema] para decidir a fonte da narrativa. MESMA instancia @Singleton
         *  (`AppModule.provideConsumerFeatureFlagProvider`) que [DiagnosticOrchestrator] ja usa
         *  para decidir a fonte do proprio relatorio -- nunca pode divergir dentro da mesma
         *  sessao (as duas decisoes leem a mesma flag, do mesmo provider). */
        private val featureFlagProvider: FeatureFlagProvider,
        private val serviceStatusRepository: ServiceStatusRepository,
    ) : AndroidViewModel(application) {
        val statusServicosUiState: StateFlow<StatusServicosUiState> = serviceStatusRepository.uiState

        private companion object {
            const val LOG_TAG = "SignallQSpeedtestSuite"
            const val DNS_CACHE_TTL_MS = 15 * 60 * 1_000L
        }

        private val avaliadorCoerenciaDns by lazy { AvaliadorCoerenciaDns() }

        @Suppress("unused")
        private val orientadorConfiguracaoDns by lazy { OrientadorConfiguracaoDns() }

        private fun getDistributionChannel(context: Context): String =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val info = context.packageManager.getInstallSourceInfo(context.packageName)
                    when (info.initiatingPackageName) {
                        "com.android.vending" -> "play_store"
                        null -> "sideload"
                        else -> info.initiatingPackageName ?: "unknown"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    when (context.packageManager.getInstallerPackageName(context.packageName)) {
                        "com.android.vending" -> "play_store"
                        null -> "sideload"
                        else -> "unknown"
                    }
                }
            } catch (e: Exception) {
                "unknown"
            }

        // DiagnosticOrchestrator injetado via Hilt como @Singleton.
        // O underscore no construtor e convencao para parametros que viram val publico.
        val diagnosticOrchestrator: DiagnosticOrchestrator = _diagnosticOrchestrator
        val movelSnapshot: StateFlow<MovelSnapshot?> get() = monitorTelephony.snapshotFlow

        // GH#1480 (Epico #1347, F4) — gate de navegacao dos 9 modulos feature do Consumer,
        // ja resolvido em booleano (ver ConsumerFeatureGateCoordinator).
        val featureFlagsState: StateFlow<AppShellFeatureFlagsState> get() = featureGateCoordinator.uiState

        private val _analisadorState = MutableStateFlow<AnalisadorState>(AnalisadorState.Inativo)
        val analisadorState: StateFlow<AnalisadorState> = _analisadorState

        // GH#865 Fase 1 — snapshot normalizado do equipamento local (ONT Nokia),
        // consumido por LocalDeviceSection via AppShell. null ate a primeira
        // leitura de fibra concluir com sucesso (ver NokiaLocalDeviceMapper).
        // Eagerly, nao WhileSubscribed: iniciarScan() le .value de fora de
        // qualquer tela que esteja de fato coletando este StateFlow (ex.:
        // Dispositivos na rede nao observa Resultado/Velocidade) — sem
        // coletor ativo o upstream nunca roda e .value fica preso no initial
        // (null) mesmo com a leitura de fibra ja concluida.
        val localDeviceSnapshot: StateFlow<LocalNetworkDeviceSnapshot?> by lazy {
            executorFibra.snapshotFlow
                .map { NokiaLocalDeviceMapper.map(it, System.currentTimeMillis()) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        }

        // GH#934 — expoe reativamente o mesmo natStatusAtual (SIG-279) ja calculado por
        // coletarTopologiaRede() para a EquipamentoInternetScreen sinalizar Double NAT.
        // Espelha a variavel privada em vez de substitui-la — DiagnosticInput continua
        // lendo natStatusAtual diretamente, sem mudar o contrato existente do engine.
        private val _natStatusFlow = MutableStateFlow<NatStatus?>(null)
        val natStatusFlow: StateFlow<NatStatus?> = _natStatusFlow.asStateFlow()

        // ── Recomendacao do Recommendation Engine na experiencia pos-diagnostico (#813) ──
        // Uma unica decisao por diagnostico concluido -- recalculada em iniciarObservadores()
        // quando o DiagnosticOrchestrator emite um relatorio novo. null = nada elegivel
        // (RecommendationEngine.choose retornou null) ou feedback "ocultar" ja dado.
        private val _recommendationDecision = MutableStateFlow<RecommendationDecision?>(null)
        val recommendationDecision: StateFlow<RecommendationDecision?> = _recommendationDecision

        private val _recommendationFeedback = MutableStateFlow<RecommendationFeedbackType?>(null)
        val recommendationFeedback: StateFlow<RecommendationFeedbackType?> = _recommendationFeedback

        // Guarda de idempotencia por trackingId -- evita reenviar o mesmo evento de
        // analytics em recomposicao do Compose (LaunchedEffect/onClick chamam de novo
        // sem side effect se o id ja foi processado).
        private val recommendationShownTrackingIds = mutableSetOf<String>()
        private val recommendationClickedTrackingIds = mutableSetOf<String>()

        // Id do diagnostico que originou _recommendationDecision -- guardado a parte porque
        // RecommendationDecision (coreRecommendation) nao carrega o diagnosticId, so o
        // RecommendationRequest o recebe (fica dentro do RecommendationDecisionCoordinator).
        private var recommendationDiagnosticId: String? = null

        // #179 Task C — Dual SIM: lista de SIMs ativos, atualizada sempre que o monitor de
        // telefonia emite novo snapshot (mudanca de rede/sinal). Inicializado com emptyList()
        // para nao crashar a UI antes da captura. Nao chama startScan() — usa dados cacheados.
        private val _simsAtivos = MutableStateFlow<List<MovelSimSnapshot>>(emptyList())
        val simsAtivos: StateFlow<List<MovelSimSnapshot>> = _simsAtivos

        // #895: default ERA `false` — todo cold start (mesmo de usuario que ja concluiu o
        // onboarding) renderizava OnboardingScreen por um instante ate o DataStore emitir o
        // valor real, causando o "piscar" de tela incorreta reportado. `null` = "ainda nao
        // lido do DataStore", distinto de `false` = "usuario e novo, precisa ver onboarding".
        // MainActivity usa essa distincao pra mostrar so um loading neutro nesse meio-tempo.
        val onboardingConcluido: StateFlow<Boolean?> by lazy {
            preferenciasAppRepository.onboardingConcluidoFlow
                .map { it as Boolean? }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        val consentimentoLgpd: StateFlow<Boolean?> by lazy {
            preferenciasAppRepository.consentimentoLgpdFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        // #82 — Banner Anatel dismissível
        val anatelBannerDismissed: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.anatelBannerDismissedFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

        fun dispensarBannerAnatel() {
            viewModelScope.launch { preferenciasAppRepository.definirAnatelBannerDismissed(true) }
        }

        // Auditoria design To-Be 2026-07-13 — dismiss persistido das sheets contextuais de
        // permissao (localizacao/telefonia), pra nao reabrir sozinha em toda nova sessao.
        val localizacaoSheetDismissed: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.localizacaoSheetDismissedFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

        val telefoniaSheetDismissed: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.telefoniaSheetDismissedFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

        fun dispensarSheetLocalizacao() {
            viewModelScope.launch { preferenciasAppRepository.definirLocalizacaoSheetDismissed(true) }
        }

        fun dispensarSheetTelefonia() {
            viewModelScope.launch { preferenciasAppRepository.definirTelefoniaSheetDismissed(true) }
        }

        // Rastreia se READ_PHONE_STATE ja foi solicitada alguma vez (onboarding ou lazy) --
        // distingue "nunca pedimos" de "negada permanentemente" pra gate de reabertura da sheet.
        val telefoniaPermissaoJaSolicitada: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.telefoniaPermissaoJaSolicitadaFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

        fun marcarTelefoniaPermissaoJaSolicitada() {
            viewModelScope.launch { preferenciasAppRepository.definirTelefoniaPermissaoJaSolicitada() }
        }

        // #1182 -- mesmo padrao acima, para ACCESS_FINE_LOCATION. Eagerly, nao WhileSubscribed:
        // MainActivity.onResume() le .value fora de qualquer coletor Compose ativo -- sem
        // coletor, WhileSubscribed nunca colocaria o upstream pra rodar e .value ficaria preso
        // no initial (false) mesmo com o valor real ja persistido (mesmo raciocinio de
        // localDeviceSnapshot acima).
        val localizacaoPermissaoJaSolicitada: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.localizacaoPermissaoJaSolicitadaFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)
        }

        fun marcarLocalizacaoPermissaoJaSolicitada() {
            viewModelScope.launch { preferenciasAppRepository.definirLocalizacaoPermissaoJaSolicitada() }
        }

        fun marcarOnboardingConcluido() {
            viewModelScope.launch { preferenciasAppRepository.definirOnboardingConcluido(true) }
        }

        fun definirConsentimentoLgpd(aceito: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirConsentimentoLgpd(aceito) }
        }

        // ── Avaliacao nativa Google Play sem atrito (SIG-173/#664) ─────────────────
        // Evento one-shot: a Activity coleta e dispara o InAppReviewManager (precisa
        // de Activity, que o ViewModel nunca deve reter). Decisao de elegibilidade
        // (ReviewPromptPolicy) fica inteiramente aqui, testavel sem Android.
        private val _solicitarAvaliacaoPlayEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val solicitarAvaliacaoPlayEvent: SharedFlow<Unit> = _solicitarAvaliacaoPlayEvent.asSharedFlow()

        /** Chamado quando o usuario fecha o LaudoScreen (botao voltar ou back fisico). */
        fun onLaudoFechado() {
            val veredito =
                diagnosticOrchestrator.snapshotFlow.value.relatorio
                    ?.veredito ?: return
            if (veredito !in ReviewPromptPolicy.VEREDITOS_POSITIVOS) return
            viewModelScope.launch {
                val positivos = preferenciasAppRepository.reviewDiagnosticosPositivosFlow.first()
                val ultimaSolicitacao = preferenciasAppRepository.reviewUltimaSolicitacaoEpochMsFlow.first()
                val agora = System.currentTimeMillis()
                if (ReviewPromptPolicy.deveExibirPrompt(veredito, positivos, ultimaSolicitacao, agora)) {
                    preferenciasAppRepository.registrarReviewSolicitacaoDisparada(agora)
                    _solicitarAvaliacaoPlayEvent.tryEmit(Unit)
                }
            }
        }

        val gemmaAvailable = MutableStateFlow(false)

        // -------------------------------------------------------------------------
        // Flows combinados — agrupam preferencias do mesmo dominio para reduzir o
        // numero de subscricoes na Activity e evitar recomposicoes em cascata.
        // distinctUntilChanged() em flows que podem oscilar para o mesmo valor.
        // -------------------------------------------------------------------------

        /** Preferencias de configuracao do modem: 4 flows para 1 subscricao. */
        val preferenciasModem: StateFlow<PreferenciasModemUiState> by lazy {
            combine(
                preferenciasAppRepository.modemHostFlow,
                preferenciasAppRepository.modemUsernameFlow,
                preferenciasAppRepository.modemPasswordFlow,
                preferenciasAppRepository.modemPermanecerConectadoFlow,
                preferenciasAppRepository.gatewaySessionBssidFlow,
            ) { host, username, password, permanecerConectado, gatewaySessionBssid ->
                PreferenciasModemUiState(
                    host = host,
                    username = username,
                    password = password,
                    permanecerConectado = permanecerConectado,
                    gatewaySessionBssid = gatewaySessionBssid,
                )
            }.distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    PreferenciasModemUiState(),
                )
        }

        /** Controles granulares de notificacao: 4 flows para 1 subscricao. */
        val preferenciasNotificacao: StateFlow<PreferenciasNotificacaoUiState> by lazy {
            combine(
                preferenciasAppRepository.notificacaoLatenciaAtivaFlow,
                preferenciasAppRepository.notificacaoDnsAtivaFlow,
                preferenciasAppRepository.notificacaoRssiAtivaFlow,
                preferenciasAppRepository.notificacaoSemInternetAtivaFlow,
            ) { latencia, dns, rssi, semInternet ->
                PreferenciasNotificacaoUiState(
                    latenciaAtiva = latencia,
                    dnsAtiva = dns,
                    rssiAtiva = rssi,
                    semInternetAtiva = semInternet,
                )
            }.distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    PreferenciasNotificacaoUiState(),
                )
        }

        /** Preferencias de UI (tema e analise avancada): 2 flows para 1 subscricao. */
        val preferenciasUi: StateFlow<PreferenciasUiUiState> by lazy {
            combine(
                preferenciasAppRepository.temaSelecionadoFlow,
                preferenciasAppRepository.analiseAvancadaFlow,
            ) { tema, analise ->
                PreferenciasUiUiState(temaSelecionado = tema, analiseAvancada = analise)
            }.distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    PreferenciasUiUiState(),
                )
        }

        /**
         * Dados de perfil do usuario e provedor: 9 flows para 1 subscricao.
         * Usa combine aninhado para superar o limite de 5 parametros do combine padrao.
         */
        val preferenciasPerfilProvedor: StateFlow<PreferenciasPerfilProvedorUiState> by lazy {
            combine(
                combine(
                    preferenciasAppRepository.nomeUsuarioFlow,
                    preferenciasAppRepository.fotoUriUsuarioFlow,
                    preferenciasAppRepository.operadoraFlow,
                    preferenciasAppRepository.planoInternetFlow,
                ) { nome, foto, op, plano ->
                    listOf<Any?>(nome, foto, op, plano)
                },
                combine(
                    preferenciasAppRepository.regiaoFlow,
                    preferenciasAppRepository.estadoUfFlow,
                    preferenciasAppRepository.cidadeNomeFlow,
                    preferenciasAppRepository.ispConfirmadoFlow,
                    preferenciasAppRepository.limiteAlertaMbpsFlow,
                ) { regiao, uf, cidade, isp, limite ->
                    listOf<Any?>(regiao, uf, cidade, isp, limite)
                },
                combine(
                    preferenciasAppRepository.velocidadeContratadaDownMbpsFlow,
                    preferenciasAppRepository.velocidadeContratadaUpMbpsFlow,
                ) { down, up -> listOf<Any?>(down, up) },
            ) { primeiro, segundo, terceiro ->
                PreferenciasPerfilProvedorUiState(
                    nomeUsuario = primeiro[0] as String,
                    fotoUriUsuario = primeiro[1] as String?,
                    operadora = primeiro[2] as String,
                    planoInternet = primeiro[3] as String,
                    regiao = segundo[0] as String,
                    estadoUf = segundo[1] as String,
                    cidadeNome = segundo[2] as String,
                    ispConfirmado = segundo[3] as Boolean,
                    limiteAlertaMbps = segundo[4] as Int,
                    velocidadeContratadaDownMbps = terceiro[0] as Int,
                    velocidadeContratadaUpMbps = terceiro[1] as Int,
                )
            }.distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    PreferenciasPerfilProvedorUiState(),
                )
        }

        // GH#1249 (recorte de #1227) — "Minha conexao" em Ajustes passa a ler/gravar um
        // ConnectionProfilePersistido por rede (core/datastore) em vez das chaves DataStore
        // globais acima (operadora/velocidadeContratadaDown-UpMbps/estadoUf/cidadeNome). As
        // chaves globais continuam existindo só pra migracao (migrarPerfilGlobalLegado).

        /** Sinal manual pra recarregar [connectionProfileAtual] apos um salvamento (as chamadas
         * de leitura do repositorio sao suspend, nao Flow — nao ha outra forma de "empurrar"
         * uma atualizacao sem re-observar a rede). */
        private val connectionProfileRefreshTrigger = MutableStateFlow(0)

        /**
         * networkId da rede atual (Wi-Fi via BSSID/SSID, movel via operadora do SIM ativo).
         * Ethernet/desconhecido ficam null — ver KDoc de [resolverNetworkIdAtual].
         */
        val networkIdAtual: StateFlow<String?> by lazy {
            combine(monitorRede.snapshotFlow, monitorTelephony.snapshotFlow) { rede, movel ->
                resolverNetworkIdAtual(
                    connectionType = UiConnectionType.parse(rede.estadoConexao.name),
                    ssid = rede.wifiLinkSnapshot?.ssid,
                    bssid = rede.wifiLinkSnapshot?.bssid,
                    operadoraMovelAtiva = movel?.operadora,
                )
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        /**
         * Única fonte de evidência reaproveitável pelo Modo gamer. Histórico só é aceito quando
         * a medição concluída ainda cabe na janela do produto e pertence à rede atual.
         */
        val medicaoBaseModoGamer: StateFlow<MedicaoBaseModoGamer?> by lazy {
            combine(bancoDados.medicaoDao().observarUltimas(10), networkIdAtual) { medicoes, networkId ->
                val agora = System.currentTimeMillis()
                medicoes.firstNotNullOfOrNull { entity ->
                    when (
                        val elegibilidade =
                            avaliarElegibilidadeMedicaoBaseModoGamer(
                                medicao = entity.paraMedicaoBaseModoGamer(),
                                networkIdAtual = networkId,
                                agoraEpochMs = agora,
                            )
                    ) {
                        is ElegibilidadeMedicaoBaseModoGamer.Elegivel -> elegibilidade.medicao
                        is ElegibilidadeMedicaoBaseModoGamer.RequerNovoTeste -> null
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        /**
         * Perfil de conexao persistido pra rede atual — null quando a rede nao tem sinal
         * estavel (Ethernet) ou quando nunca foi salvo nada pra ela. Migra o valor legado
         * global automaticamente só na primeira vez que NENHUM perfil existir ainda (nunca
         * reaplica o plano residencial em redes diferentes depois disso — critério de aceite
         * de #1249: "rede diferente não reutiliza automaticamente o plano residencial").
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val connectionProfileAtual: StateFlow<ConnectionProfilePersistido?> by lazy {
            combine(networkIdAtual, connectionProfileRefreshTrigger) { id, _ -> id }
                .flatMapLatest { id ->
                    flow {
                        if (id == null) {
                            emit(null)
                            return@flow
                        }
                        var perfil = preferenciasAppRepository.buscarConnectionProfile(id)
                        if (perfil == null && preferenciasAppRepository.buscarTodosConnectionProfiles().isEmpty()) {
                            val migrado = preferenciasAppRepository.migrarPerfilGlobalLegado(id)
                            if (migrado != null) {
                                preferenciasAppRepository.salvarConnectionProfile(migrado)
                                perfil = migrado
                            }
                        }
                        emit(perfil)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        /**
         * Salva o [ConnectionProfilePersistido] da rede atual. Sem rede com sinal estavel
         * (Ethernet/desconhecido), nao ha onde persistir — noop silencioso (mesma politica
         * defensiva do resto do arquivo, nunca lanca excecao por estado de rede transitorio).
         */
        fun salvarConnectionProfileAtual(
            providerFixed: String?,
            contractedDownloadMbps: Int?,
            contractedUploadMbps: Int?,
            city: String?,
            state: String?,
            userConfirmed: Boolean,
        ) {
            val id = networkIdAtual.value ?: return
            viewModelScope.launch {
                preferenciasAppRepository.salvarConnectionProfile(
                    ConnectionProfilePersistido(
                        networkId = id,
                        providerFixed = providerFixed,
                        contractedDownloadMbps = contractedDownloadMbps,
                        contractedUploadMbps = contractedUploadMbps,
                        city = city,
                        state = state,
                        userConfirmed = userConfirmed,
                    ),
                )
                connectionProfileRefreshTrigger.value++
            }
        }

        /** Controles de speedtest em rede movel: 2 flows para 1 subscricao. */
        val preferenciasSpeedtestMovel: StateFlow<PreferenciasSpeedtestMovelUiState> by lazy {
            combine(
                preferenciasAppRepository.speedtestPermiteHeavyMovel,
                preferenciasAppRepository.speedtestMbConsumidosMes,
            ) { permite, mb ->
                PreferenciasSpeedtestMovelUiState(permiteHeavy = permite, mbConsumidosMes = mb)
            }.distinctUntilChanged()
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    PreferenciasSpeedtestMovelUiState(),
                )
        }

        /** distinctUntilChanged: toggle boolean que pode oscilar para o mesmo valor. */
        val monitoramentoAtivo: StateFlow<Boolean> by lazy {
            preferenciasAppRepository.monitoramentoAtivoFlow
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

        // Speedtest em rede medida — emite o modo aguardando confirmacao do usuario, null caso contrario
        private val _speedtestPendenteModoMovel = MutableStateFlow<ModoSpeedtest?>(null)
        val speedtestPendenteModoMovel: StateFlow<ModoSpeedtest?> = _speedtestPendenteModoMovel

        /** GH#1512 — conclusao do diagnostico local quando o Speedtest e interrompido por
         *  Wi-Fi sem internet (nao um erro generico de execucao nem loading infinito). null
         *  = sem pendencia; UI limpa apos exibir (ver [limparDiagnosticoConectividade]). */
        private val _diagnosticoConectividade = MutableStateFlow<ConnectivityDiagnosisMensagem?>(null)
        val diagnosticoConectividade: StateFlow<ConnectivityDiagnosisMensagem?> = _diagnosticoConectividade

        fun limparDiagnosticoConectividade() {
            _diagnosticoConectividade.value = null
        }

        fun verificarDisponibilidadeGemma() {
            viewModelScope.launch {
                // GH#1682 — antes delegado ao SignallQOrchestrator.checkAiAvailability()
                // (motor SignallQ Pulse, removido por ser codigo morto sem consumidor de
                // UI). checkAvailability() e o mesmo HEAD request; sem mudanca de contrato.
                gemmaAvailable.value = diagAiRepository.checkAvailability()
            }
        }

        val apelidos by lazy {
            bancoDados
                .apelidoDispositivoDao()
                .observarTodos()
                // Filtra entidades sem apelido (registradas silenciosamente para supressao de
                // notificacao de dispositivo novo). So inclui no mapa dispositivos com apelido definido.
                .map { list -> list.mapNotNull { e -> e.apelido?.let { ap -> e.mac to ap } }.toMap() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }

        val localIp = MutableStateFlow<UiState<String>>(UiState.Loading)
        val publicIp = MutableStateFlow<UiState<String>>(UiState.Loading)
        val ispInfo = MutableStateFlow<UiState<IspInfo>>(UiState.Loading)
        val gateways = MutableStateFlow<List<GatewayInfo>>(emptyList())
        val history = MutableStateFlow<List<HistoryPoint>>(emptyList())
        val historico = MutableStateFlow<List<MedicaoEntity>>(emptyList())

        // ── Filtros do Histórico (#95) ─────────────────────────────────────────
        private val _filtroConexaoHistorico = MutableStateFlow(FiltroConexaoHistorico.TODOS)
        val filtroConexaoHistorico: StateFlow<FiltroConexaoHistorico> = _filtroConexaoHistorico

        private val _filtroOperadoraHistorico = MutableStateFlow<String?>(null)
        val filtroOperadoraHistorico: StateFlow<String?> = _filtroOperadoraHistorico

        val historicoFiltrado: StateFlow<List<MedicaoEntity>> =
            combine(
                historico,
                _filtroConexaoHistorico,
                _filtroOperadoraHistorico,
            ) { lista, filtroConexao, filtroOp ->
                lista
                    // #1096 -- medicoes sinteticas do MonitoramentoWorker (fonte="monitor")
                    // nao tem download/upload e nao devem poluir a lista visivel do Historico,
                    // mas continuam no banco alimentando o grafico de uptime.
                    .filter { m -> m.fonte != "monitor" }
                    .filter { m ->
                        when (filtroConexao) {
                            FiltroConexaoHistorico.TODOS -> true
                            FiltroConexaoHistorico.WIFI -> m.connectionType == "wifi"
                            FiltroConexaoHistorico.MOVEL -> m.connectionType == EstadoConexao.movel.name
                        }
                    }.filter { m -> filtroOp == null || m.operadoraMovel == filtroOp }
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val operadorasDisponiveisHistorico: StateFlow<List<String>> =
            historico
                .map { lista ->
                    lista
                        .filter { it.connectionType == EstadoConexao.movel.name }
                        .mapNotNull { it.operadoraMovel?.trim()?.ifBlank { null } }
                        .distinct()
                        .sorted()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun setFiltroConexaoHistorico(filtro: FiltroConexaoHistorico) {
            _filtroConexaoHistorico.value = filtro
            if (filtro != FiltroConexaoHistorico.MOVEL) _filtroOperadoraHistorico.value = null
        }

        fun setFiltroOperadoraHistorico(operadora: String?) {
            _filtroOperadoraHistorico.value = operadora
        }

        val localizacaoServidor = MutableStateFlow<UiState<String>>(UiState.Loading)

        private val observadorHistorico by lazy { ObservadorHistoricoRoom(bancoDados.medicaoDao(), dispatchers.io) }

        val resumoHistorico: StateFlow<ResumoHistorico?> =
            observadorHistorico.resumoFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        // Issues #1666/#1520 (épico #1647, Task 2.0.18) — religa o wiring do grid de uptime de
        // 7 dias, orfao desde uma refatoracao anterior de HistoricoScreen (GH#495 original).
        // `historico` (observarUltimas(100)) so serve de gatilho de recomputo aqui -- a janela
        // real de 7 dias vem direto do banco via UptimeChartUseCase.gerar7dias(), que nao
        // depende do limite de 100 itens usado pela lista visivel do Historico.
        private val uptimeChartUseCase by lazy { UptimeChartUseCase(bancoDados.medicaoDao(), dispatchers.io) }

        val uptimeBlocos: StateFlow<List<BlocoUptime>> =
            historico
                .map { uptimeChartUseCase.gerar7dias() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private var scannerDispositivosDisparado = false
        private var scanWifiDisparado = false
        private var benchmarkDnsDisparado = false
        private var diagnosticoDisparado = false
        private var fibraDisparada = false
        private var infoLocalRedeColetada = false
        private var ispInfoColetada = false
        private var localizacaoServidorColetada = false
        private var topologiaColetada = false
        private var ultimoBenchmarkDnsEpochMs: Long? = null
        private var ssidAoDispararDns: String? = null
        private var estadoConexaoAoDispararDns: EstadoConexao? = null

        // SIG-279 — cache do NAT status (1 chamada de rede por sessao, reusado nas 3
        // montagens de DiagnosticInput). Populado por coletarTopologiaRede().
        private var natStatusAtual: NatStatus? = null

        // SIG-279 — ultima coerencia de DNS calculada por avaliadorCoerenciaDns, para
        // repassar ao DiagnosticInput.dns nas proximas montagens.
        private var ultimaCoerenciaDns: DiagnosticoCoerenciaDns? = null

        init {
            iniciarObservadores()
            iniciarObservadorSimsAtivos()
        }

        private fun iniciarObservadores() {
            // SIG-173/#664 — acumula diagnosticos com veredito positivo para elegibilidade
            // do prompt de avaliacao nativa do Google Play. So conta; o disparo do fluxo
            // so acontece quando o usuario fecha o Laudo (onLaudoFechado).
            viewModelScope.launch {
                diagnosticOrchestrator.snapshotFlow.collect { snapshot ->
                    if (snapshot.estado != EstadoDiagnostico.concluido) return@collect
                    val veredito = snapshot.relatorio?.veredito ?: return@collect
                    if (veredito in ReviewPromptPolicy.VEREDITOS_POSITIVOS) {
                        preferenciasAppRepository.incrementarReviewDiagnosticosPositivos()
                    }
                }
            }

            // #813 — recalcula a recomendacao do Recommendation Engine a cada diagnostico
            // concluido. Uma unica chamada por relatorio novo: SnapshotDiagnostico e um
            // data class, entao o StateFlow so re-emite quando o conteudo muda de fato
            // (novo relatorio = geradoEmMs diferente); reabrir a tela de resultado depois
            // NAO reexecuta isto, o que evitaria escrever exibicao duplicada no historico
            // (Room, #812) para o mesmo diagnostico.
            viewModelScope.launch {
                diagnosticOrchestrator.snapshotFlow.collect { snapshot ->
                    if (snapshot.estado != EstadoDiagnostico.concluido) return@collect
                    val relatorio = snapshot.relatorio ?: return@collect
                    val input = snapshot.input ?: return@collect
                    avaliarRecomendacao(relatorio = relatorio, input = input)
                }
            }

            // Persistência do speedtest delegada ao SpeedtestPersistenceCoordinator (issues #184/#185).
            viewModelScope.launch {
                executorFibra.snapshotFlow.collect { snapshot ->
                    if (snapshot.estado != EstadoFibra.concluido) return@collect
                    val gpon = snapshot.gpon ?: return@collect
                    val fibraInput =
                        FibraDiagnosticInput(
                            rxPowerDbm = gpon.rxPowerDbm,
                            txPowerDbm = gpon.txPowerDbm,
                            temperatureCelsius = gpon.temperatureCelsius,
                            isUp = gpon.isUp,
                        )
                    val ultimaMedicao =
                        bancoDados
                            .medicaoDao()
                            .observarUltimas(1)
                            .first()
                            .firstOrNull()
                    val wifiSnapshot = monitorRede.snapshotFlow.value.wifiLinkSnapshot
                    val internetInput =
                        ultimaMedicao?.let {
                            InternetDiagnosticInput(
                                downloadMbps = it.downloadMbps,
                                uploadMbps = it.uploadMbps,
                                latencyMs = it.latencyMs,
                                jitterMs = it.jitterMs,
                                perdaPercentual = it.perdaPercentual,
                                bufferbloatMs = it.bufferbloatMs,
                                packetLossSource = it.packetLossSource,
                                perdaConfianca = it.perdaConfianca.paraConfiancaAmostral(),
                            )
                        }
                    val wifiInput =
                        wifiSnapshot?.let { ws ->
                            WifiDiagnosticInput(
                                rssiDbm = ws.rssiDbm,
                                linkSpeedMbps = ws.linkSpeedMbps,
                                frequenciaMhz = ws.frequenciaMhz,
                                wifiStandard = ws.padraoWifi,
                                dispositivosNaRede =
                                    scannerDispositivos.snapshotFlow.value.dispositivos.size
                                        .takeIf { it > 0 },
                            )
                        }
                    diagnosticOrchestrator.executar(
                        DiagnosticInput(
                            connectionType =
                                monitorRede.snapshotFlow.value.estadoConexao
                                    .paraConnectionType(),
                            internet = internetInput,
                            wifi = wifiInput,
                            fibra = fibraInput,
                            mobile = montarMobileInput(),
                            dns = montarDnsInput(),
                            historico = montarHistoricoInput(),
                            wifiScan = montarWifiScanInput(),
                            velocidadeContratadaMbps = montarVelocidadeContratadaMbps(),
                            natStatus = natStatusAtual,
                            // GH#1228 (Fase 3) — mesma medicao persistida usada para montar
                            // internetInput acima (ultimaMedicao); nunca gera id novo aqui.
                            executionId = ultimaMedicao?.executionId ?: "",
                        ),
                    )
                }
            }

            viewModelScope.launch {
                benchmarkDns.snapshotFlow.collect { snapshot ->
                    if (snapshot.estado != EstadoBenchmarkDns.concluido) return@collect
                    val melhor =
                        snapshot.resultados
                            .filter { it.tempoMs != null }
                            .minByOrNull { it.tempoMs ?: Double.MAX_VALUE }
                            ?: return@collect
                    val rede = monitorRede.snapshotFlow.value
                    val provedorAtivo = inferirProvedorAtivoDns(rede.privateDnsHostname, rede.dnsServidores)
                    val coerencia = classificarCoerenciaDns(melhor.nomeProvedor, provedorAtivo)
                    // SIG-279 — resultado repassado ao motor de diagnostico via
                    // DiagnosticInput.dns na proxima montagem (ver montarDnsInput()).
                    ultimaCoerenciaDns = avaliadorCoerenciaDns.registrarCoerencia(coerencia)
                }
            }

            viewModelScope.launch {
                bancoDados.medicaoDao().observarUltimas(20).collect { medicoes ->
                    history.value =
                        medicoes
                            .filter { it.connectionType == EstadoConexao.wifi.name }
                            .map { HistoryPoint(it.timestampEpochMs, it.downloadMbps, it.uploadMbps) }
                }
            }

            viewModelScope.launch {
                bancoDados.medicaoDao().observarUltimas(100).collect { medicoes ->
                    historico.value = medicoes
                }
            }

            viewModelScope.launch {
                var estadoAnterior: EstadoConexao? = null
                monitorRede.snapshotFlow.collect { snapshot ->
                    val estadoAtual = snapshot.estadoConexao
                    val ssidAtual = snapshot.wifiLinkSnapshot?.ssid
                    // Mantem monitor de telefonia sempre ativo para exibir info
                    // de chips na tela de Sinal mesmo quando conectado em Wi-Fi.
                    iniciarMonitorTelefoniaSempre()
                    if (estadoAnterior != null && estadoAtual != estadoAnterior) {
                        infoLocalRedeColetada = false
                        ispInfoColetada = false
                        gateways.value = emptyList()
                        localIp.value = UiState.Loading
                        publicIp.value = UiState.Loading
                        ispInfo.value = UiState.Loading
                        coletarInfoLocalRede()
                        launch { coletarIspInfo() }
                        ultimoBenchmarkDnsEpochMs = null
                    } else if (ssidAtual != ssidAoDispararDns && ultimoBenchmarkDnsEpochMs != null) {
                        ultimoBenchmarkDnsEpochMs = null
                    }
                    estadoAnterior = estadoAtual
                }
            }
        }

        /**
         * Atualiza [simsAtivos] com os SIMs atualmente ativos no dispositivo.
         * Chamado uma vez ao iniciar monitoramento e sempre que o snapshot movel muda.
         * Seguro: captureSimsAtivos e envolto em runCatching internamente.
         */
        private fun atualizarSimsAtivos() {
            _simsAtivos.value = monitorTelephony.captureSimsAtivos(getApplication())
        }

        /**
         * Observa mudancas no snapshot movel para manter [simsAtivos] atualizado.
         * Atualiza sempre que houver coleta — inclusive em Wi-Fi, pois a tela
         * de Sinal agora exibe info de chips independentemente do tipo de conexao.
         */
        private fun iniciarObservadorSimsAtivos() {
            viewModelScope.launch {
                monitorTelephony.snapshotFlow.collect {
                    atualizarSimsAtivos()
                }
            }
        }

        fun iniciarMonitorRede() = monitorRede.iniciar()

        fun encerrarMonitorRede() {
            monitorRede.encerrar()
            monitorTelephony.encerrar()
        }

        /**
         * Inicia o coletor de telefonia movel SOMENTE se o estado atual e movel.
         * Idempotente. Chamado pela MainActivity logo apos o usuario conceder
         * a permissao ou ja ter ela concedida.
         */
        fun iniciarMonitorTelefoniaSeMovel() {
            if (monitorRede.snapshotFlow.value.estadoConexao == EstadoConexao.movel) {
                monitorTelephony.iniciar()
            }
        }

        /**
         * Inicia o monitor de telefonia independentemente do tipo de conexao.
         * Necessario para exibir info de chips na tela de Sinal em Wi-Fi.
         */
        private fun iniciarMonitorTelefoniaSempre() {
            monitorTelephony.iniciar()
        }

        // -------------------------------------------------------------------------
        // LEGADO — Compatibilidade: reiniciarSuite, confirmarSpeedtestEmMovel,
        // cancelarSpeedtestMovel e setSpeedtestPermiteHeavyMovel duplicam logica
        // que agora vive em SpeedtestViewModel (feat/viewmodels-por-funcionalidade).
        // Mantidos aqui pois a AppShell ainda recebe lambdas via MainActivity que
        // passam por este ViewModel. Remover em PR subsequente apos migrar AppShell
        // para consumir SpeedtestViewModel diretamente.
        // Tambem remover: executarSpeedtest(), acumularMbConsumidos() e
        // _speedtestPendenteModoMovel deste ViewModel quando a migracao for concluida.
        // -------------------------------------------------------------------------

        /** GH#1221 RF-09 — trava de concorrencia no dominio (ver kdoc equivalente em
         *  SpeedtestViewModel.execucaoEmAndamento). Sem isso, duplo clique dispara duas
         *  corrotinas de `reiniciarSuite` — o executor ja rejeita a 2a execucao concorrente,
         *  mas esta funcao ainda contaria MB consumidos em dobro e chamaria
         *  [iniciarRotinasNaoSpeedtest] duas vezes para uma unica medicao real. */
        private val execucaoSpeedtestEmAndamento = AtomicBoolean(false)

        /** GH#1225 item G — ao iniciar um novo teste, cancela a analise por IA anterior
         *  (job cancelavel em [analisarProblema]) e reseta a recomendacao/feedback da
         *  execucao anterior. Sem isso, uma resposta tardia da IA ou uma recomendacao de
         *  um diagnostico anterior podia aparecer junto do resultado do teste novo. */
        private fun resetarEstadoPosSpeedtestAnterior() {
            analisarProblemaJob?.cancel()
            _analisadorState.value = AnalisadorState.Inativo
            _recommendationDecision.value = null
            _recommendationFeedback.value = null
            recommendationDiagnosticId = null
        }

        fun reiniciarSuite(
            modo: ModoSpeedtest,
            jaConfirmadoRedeMovel: Boolean = false,
        ) {
            if (!execucaoSpeedtestEmAndamento.compareAndSet(false, true)) {
                Timber.w("reiniciarSuite ignorado — ja ha execucao em andamento")
                return
            }
            scannerDispositivosDisparado = false
            scanWifiDisparado = false
            benchmarkDnsDisparado = false
            diagnosticoDisparado = false
            fibraDisparada = false
            infoLocalRedeColetada = false
            ispInfoColetada = false
            localizacaoServidorColetada = false
            resetarEstadoPosSpeedtestAnterior()
            viewModelScope.launch {
                // Guarda de rede medida: se movel, modo pesado e usuario nao autorizou,
                // suspende e aguarda confirmacao via dialog (Task 4). Sem dialog agora.
                // jaConfirmadoRedeMovel = true quando o usuario ja confirmou o ForaDoWifiDialog
                // (Home) — evita um segundo gate redundante que nao tem UI fora da tab Velocidade (#516).
                //
                // #838 — antes lia networkCapabilitiesProvider.isMeteredNetwork(), uma consulta
                // avulsa ao ConnectivityManager feita exatamente no instante do toque em "Iniciar".
                // Logo apos abrir o app/trocar de tab o NetworkCapabilities pode ainda nao estar
                // assentado (mesma classe de problema ja documentada em MonitorRedeAndroid para
                // NET_CAPABILITY_VALIDATED), fazendo a consulta cair no caminho `?: return false`
                // e o gate nunca disparar. monitorRede.snapshotFlow ja mantem esse dado sempre
                // atualizado via callback continuo do ConnectivityManager — usa o mesmo valor.
                if (deveSolicitarConfirmacaoRedeMovel(
                        metered = monitorRede.snapshotFlow.value.metered,
                        modo = modo,
                        jaConfirmadoRedeMovel = jaConfirmadoRedeMovel,
                    )
                ) {
                    val permiteHeavy = preferenciasAppRepository.speedtestPermiteHeavyMovel.first()
                    if (!permiteHeavy) {
                        _speedtestPendenteModoMovel.value = modo
                        execucaoSpeedtestEmAndamento.set(false)
                        return@launch
                    }
                }
                // GH#1512 — verificado ANTES do try/finally: se o diagnostico interromper,
                // nao ha teste real (nao acumula MB) e nao faz sentido disparar scans/
                // diagnostico canonico numa rede que acabamos de confirmar sem internet
                // (iniciarRotinasNaoSpeedtest fica para quando o teste realmente roda).
                if (interromperSpeedtestPorWifiSemInternet()) {
                    execucaoSpeedtestEmAndamento.set(false)
                    return@launch
                }
                try {
                    executarSpeedtest(modo)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Timber.e(t, "erro ao executar speedtest modo=${modo.name}")
                } finally {
                    acumularMbConsumidos(modo)
                    execucaoSpeedtestEmAndamento.set(false)
                    iniciarRotinasNaoSpeedtest()
                }
            }
        }

        /** Chamada pelo dialog de confirmacao (Task 4 — Lia) quando usuario aceita usar dados moveis. */
        fun confirmarSpeedtestEmMovel() {
            val modo = _speedtestPendenteModoMovel.value ?: return
            if (!execucaoSpeedtestEmAndamento.compareAndSet(false, true)) {
                Timber.w("confirmarSpeedtestEmMovel ignorado — ja ha execucao em andamento")
                return
            }
            _speedtestPendenteModoMovel.value = null
            resetarEstadoPosSpeedtestAnterior()
            viewModelScope.launch {
                // GH#1512 (3a revisao, correcao) -- reintroduzido: o dialog que leva ate
                // aqui e sobre rede MEDIDA (`metered`), nao sobre transporte celular --
                // `deveSolicitarConfirmacaoRedeMovel` dispara para qualquer rede medida,
                // Wi-Fi tarifado incluso (hotspot, rede marcada como limitada). Remover o
                // gate daqui permitia rodar um Speedtest completo sobre um Wi-Fi tarifado
                // e sem internet, sem nenhum aviso. O gate ja e auto-limitado -- devolve
                // false de cara quando nao ha rede Wi-Fi nenhuma (dados moveis puros) --
                // entao mante-lo aqui nao interfere no caso de dados moveis de verdade.
                if (interromperSpeedtestPorWifiSemInternet()) {
                    execucaoSpeedtestEmAndamento.set(false)
                    return@launch
                }
                try {
                    executarSpeedtest(modo)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Timber.e(t, "erro ao confirmar speedtest em rede movel modo=${modo.name}")
                } finally {
                    acumularMbConsumidos(modo)
                    execucaoSpeedtestEmAndamento.set(false)
                    iniciarRotinasNaoSpeedtest()
                }
            }
        }

        /** Chamada pelo dialog quando o usuario cancela. */
        fun cancelarSpeedtestMovel() {
            _speedtestPendenteModoMovel.value = null
        }

        /** Persiste a preferencia de permitir testes pesados em rede medida (Task 5). */
        fun setSpeedtestPermiteHeavyMovel(valor: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.setSpeedtestPermiteHeavyMovel(valor) }
        }

        /**
         * Acumula MB estimados consumidos no mes corrente.
         * Reset automatico quando o mes muda em relacao ao valor salvo em [speedtestMesReferencia].
         * Estimativas: fast=10 MB, complete=25 MB.
         */
        private fun acumularMbConsumidos(modo: ModoSpeedtest) {
            val mbEstimado =
                when (modo) {
                    ModoSpeedtest.fast -> 10L
                    ModoSpeedtest.complete -> 25L
                }
            // Usa Calendar para compatibilidade com minSdk 24 (java.time requer API 26+ ou desugaring)
            val cal = java.util.Calendar.getInstance()
            val mesAtual = "%04d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
            viewModelScope.launch {
                // NonCancellable garante que a escrita no DataStore completa mesmo se o ViewModel
                // for destruido entre o .first() e o set — evita race condition de contagem perdida.
                withContext(kotlinx.coroutines.NonCancellable) {
                    val mesReferencia = preferenciasAppRepository.speedtestMesReferencia.first()
                    val mbAcumulados =
                        if (mesReferencia == mesAtual) {
                            preferenciasAppRepository.speedtestMbConsumidosMes.first()
                        } else {
                            preferenciasAppRepository.setSpeedtestMesReferencia(mesAtual)
                            0L
                        }
                    preferenciasAppRepository.setSpeedtestMbConsumidosMes(mbAcumulados + mbEstimado)
                }
            }
        }

        fun iniciarRotinasNaoSpeedtest() {
            if (!scannerDispositivosDisparado) {
                scannerDispositivosDisparado = true
                viewModelScope.launch {
                    scannerDispositivos.iniciarScan(profundo = false, clientesGateway = localDeviceSnapshot.value?.clientes.orEmpty())
                }
            }
            if (!scanWifiDisparado) {
                scanWifiDisparado = true
                viewModelScope.launch { scannerRedesWifi.escanear() }
            }
            if (!diagnosticoDisparado) {
                diagnosticoDisparado = true
                viewModelScope.launch {
                    // Usa o resultado em memoria do speedtest como fonte primaria.
                    // O snapshotFlow e atualizado imediatamente quando o speedtest termina.
                    // O BD e o fallback para sessoes sem speedtest novo (ex.: app reaberto).
                    // Ler do BD aqui causava race condition: o save acontece em outra
                    // coroutine e pode nao ter terminado quando este bloco executa.
                    val internetInput = speedtestResultToInternetInput()
                    val executionIdAtual = executionIdAtual()
                    val wifiSnapshot = monitorRede.snapshotFlow.value.wifiLinkSnapshot
                    val wifiInput =
                        wifiSnapshot?.let {
                            WifiDiagnosticInput(
                                rssiDbm = it.rssiDbm,
                                linkSpeedMbps = it.linkSpeedMbps,
                                frequenciaMhz = it.frequenciaMhz,
                                wifiStandard = it.padraoWifi,
                                dispositivosNaRede =
                                    scannerDispositivos.snapshotFlow.value.dispositivos.size
                                        .takeIf { size -> size > 0 },
                            )
                        }
                    diagnosticOrchestrator.executar(
                        DiagnosticInput(
                            connectionType =
                                monitorRede.snapshotFlow.value.estadoConexao
                                    .paraConnectionType(),
                            internet = internetInput,
                            wifi = wifiInput,
                            mobile = montarMobileInput(),
                            dns = montarDnsInput(),
                            historico = montarHistoricoInput(),
                            wifiScan = montarWifiScanInput(),
                            velocidadeContratadaMbps = montarVelocidadeContratadaMbps(),
                            natStatus = natStatusAtual,
                            executionId = executionIdAtual,
                        ),
                    )
                }
            }
            if (!fibraDisparada) {
                fibraDisparada = true
                viewModelScope.launch {
                    // #127: guard de rede — fibra só faz sentido em Wi-Fi/Ethernet.
                    // Em rede móvel pura não há modem local para consultar.
                    val estadoAtual = monitorRede.snapshotFlow.value.estadoConexao
                    if (estadoAtual == EstadoConexao.movel) {
                        executorFibra.marcarSemRede()
                        return@launch
                    }
                    if (estadoAtual == EstadoConexao.desconectado) {
                        executorFibra.marcarSemRede()
                        return@launch
                    }
                    val permanecerConectado = preferenciasAppRepository.modemPermanecerConectadoFlow.first()
                    if (!permanecerConectado) return@launch
                    val host =
                        preferenciasAppRepository.modemHostFlow.first()
                            ?: gateways.value.firstOrNull()?.ip
                            ?: return@launch
                    val username = preferenciasAppRepository.modemUsernameFlow.first()
                    val password = preferenciasAppRepository.modemPasswordFlow.first()
                    executorFibra.executar(host, username, password)
                }
            }
            if (!infoLocalRedeColetada) {
                infoLocalRedeColetada = true
                coletarInfoLocalRede()
            }
            if (!ispInfoColetada) {
                ispInfoColetada = true
                viewModelScope.launch { coletarIspInfo() }
            }
            if (!localizacaoServidorColetada) {
                localizacaoServidorColetada = true
                viewModelScope.launch { buscarLocalizacaoServidor() }
            }
            if (!topologiaColetada) {
                topologiaColetada = true
                viewModelScope.launch { coletarTopologiaRede() }
            }
        }

        /** SIG-279 — classifica NAT/CGNAT via TopologyDiagnostic (UPnP + IP publico).
         *  Best-effort: falha de rede/timeout mantem natStatusAtual como null (omitido
         *  do DiagnosticInput, sem gerar resultado de diagnostico). */
        private suspend fun coletarTopologiaRede() {
            try {
                natStatusAtual = topologyDiagnostic.diagnose().nat
                _natStatusFlow.value = natStatusAtual
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w("coletarTopologiaRede falhou: ${e.message}")
            }
        }

        /** SIG-279 — monta o scan de redes vizinhas (feature/wifi) para o motor local
         *  (WifiChannelDiagnosticEngine), incluindo o canal conectado atual calculado
         *  a partir da frequencia do link Wi-Fi. */
        private fun montarWifiScanInput(): WifiScanDiagnosticInput? {
            val redesScan = scannerRedesWifi.snapshotFlow.value.redes
            if (redesScan.isEmpty()) return null
            val wifiSnapshot = monitorRede.snapshotFlow.value.wifiLinkSnapshot
            val conectadoCanal = wifiSnapshot?.frequenciaMhz?.let { freqToChannel(it)?.second }
            // #980 (Fase 2B, passo 3) — motor unificado (TopologiaRedeEngine/#979) alimenta
            // RecommendationEngine com papel/confianca por BSSID, em vez de MeshOuiDatabase
            // consultado direto (ve OUI e banda; nao afirma "roteador central" sem confirmacao).
            val classificacaoPorBssid =
                TopologiaRedeEngine
                    .classificar(redes = redesScan, connectedBssid = wifiSnapshot?.bssid)
                    .associate { it.first.bssid to it.second }
            return WifiScanDiagnosticInput(
                redes =
                    redesScan.map { rv ->
                        val classificacao = classificacaoPorBssid[rv.bssid]
                        RedeWifiVizinha(
                            canal = rv.canal,
                            rssiDbm = rv.rssiDbm,
                            frequenciaMhz = rv.frequenciaMhz,
                            ssid = rv.ssid,
                            bssid = rv.bssid,
                            seguranca = rv.seguranca,
                            papelTopologia = classificacao?.papelProvavel,
                            confiancaTopologia = classificacao?.confianca,
                        )
                    },
                conectadoCanal = conectadoCanal,
                conectadoBanda =
                    wifiSnapshot?.frequenciaMhz?.let { freq ->
                        WifiDiagnosticInput(rssiDbm = null, linkSpeedMbps = null, frequenciaMhz = freq).banda()
                    },
            )
        }

        /** SIG-279 — monta o input de sinal movel (RSRP/RSRQ/SINR) para
         *  MobileSignalDiagnosticEngine, a partir do snapshot bruto do TelephonyManager. */
        private fun montarMobileInput(): MobileDiagnosticInput? {
            if (monitorRede.snapshotFlow.value.estadoConexao != EstadoConexao.movel) return null
            val snap = monitorTelephony.snapshotFlow.value ?: return null
            return MobileDiagnosticInput(
                carrierName = snap.operadora,
                mobileTechnology = snap.tecnologia,
                signalStrengthDbm = snap.rsrpDbm,
                signalQualityPercent = null,
                band = snap.bandaMovel,
                publicIp = (publicIp.value as? UiState.Success)?.data,
                rsrpDbm = snap.rsrpDbm,
                rsrqDb = snap.rsrqDb,
                sinrDb = snap.sinrDb,
                capturaReduzida = snap.capturaReduzida,
            )
        }

        /** SIG-279/#1840 (ADR-018, bloco `dns` expandido) — monta o input de DNS
         *  combinando: o resultado do ultimo benchmark concluido (`benchmarkDns`,
         *  ja existente e exibido em `DnsScreen`), o provedor ativo inferido por
         *  [inferirProvedorAtivoDns] (mesma tabela ja usada para calcular a
         *  coerencia logo abaixo — nao ha uma segunda tabela IP/hostname->provedor
         *  neste metodo, ver divida #1823), a coerencia calculada por
         *  `AvaliadorCoerenciaDns`, e o estado de Private DNS lido de `SnapshotRede`.
         *
         *  `null` quando nao ha absolutamente nenhuma evidencia de DNS coletada
         *  ainda nesta sessao (nem resolvedor, nem benchmark, nem coerencia, nem
         *  Private DNS ativo).
         */
        private fun montarDnsInput(): DnsDiagnosticInput? {
            val coerencia = ultimaCoerenciaDns
            val rede = monitorRede.snapshotFlow.value
            val dnsResolverIp = rede.dnsServidores.firstOrNull()
            val currentDnsName = inferirProvedorAtivoDns(rede.privateDnsHostname, rede.dnsServidores)
            val melhor =
                benchmarkDns.snapshotFlow.value.resultados
                    .filter { it.tempoMs != null && !it.isGatewayLocal }
                    .minByOrNull { it.tempoMs ?: Double.MAX_VALUE }
            // Hostname do Private DNS so sai do aparelho quando bate com um provedor
            // publico conhecido (mesma seguranca de inferirProvedorAtivoDns, agora sem
            // a lista de IPs) -- hostname customizado (resolver proprio do usuario)
            // pode ser identificador pessoal e nunca e enviado (ADR-018).
            val privateDnsHostnameSeguro =
                rede.privateDnsHostname?.takeIf { inferirProvedorAtivoDns(it, emptyList()) != null }

            if (coerencia == null &&
                dnsResolverIp == null &&
                currentDnsName == null &&
                melhor == null &&
                !rede.privateDnsAtivo
            ) {
                return null
            }

            return DnsDiagnosticInput(
                currentDnsIp = dnsResolverIp,
                currentDnsName = currentDnsName,
                currentDnsLatencyMs =
                    melhor
                        ?.takeIf { it.nomeProvedor.equals(currentDnsName, ignoreCase = true) }
                        ?.tempoMs
                        ?.roundToInt(),
                bestDnsNameFromComparison = melhor?.nomeProvedor,
                bestDnsLatencyMsFromComparison = melhor?.tempoMs?.roundToInt(),
                dnsGrade = melhor?.gradeRapidez,
                dnsComparisonAvailable = melhor != null,
                coerenciaNivelAlerta = coerencia?.nivelAlerta?.name,
                coerenciaDivergenciasConsecutivas = coerencia?.divergenciasConsecutivas,
                coerenciaTaxaDivergenciaPercentual = coerencia?.taxaDivergenciaPercentual,
                privateDnsActive = rede.privateDnsAtivo,
                privateDnsHostname = privateDnsHostnameSeguro,
            )
        }

        /** SIG-279 — velocidade contratada, mesma fonte usada por LaudoScreen/AppShell
         *  (PreferenciasAppRepository.planoInternetFlow, string tipo "300" -> 300). */
        private suspend fun montarVelocidadeContratadaMbps(): Int? =
            preferenciasAppRepository.planoInternetFlow
                .first()
                .filter { it.isDigit() }
                .toIntOrNull()

        /** NDS-Snapshot-06 (issue #1838, ADR-018 secao 13) — historico local
         *  (`MedicaoDao`) agregado nas janelas 7d/30d que alimentam tanto o payload
         *  NDS (`historical`) quanto `RecomendacaoPraticaEngine` (REC-14, via
         *  `HistoricalDiagnosticInput.degradationDetected`). A query ja filtra os
         *  ultimos 30 dias; a separacao 7d/30d e a agregacao em si sao puras
         *  (`agregarHistoricoNds`), testadas sem Room. Retorna null (bloco omitido)
         *  quando nao ha nenhuma medicao no periodo -- usuario novo. */
        private suspend fun montarHistoricoInput(): HistoricalDiagnosticInput? {
            val agora = System.currentTimeMillis()
            val corte30d = agora - JANELA_30D_MS
            val medicoes = bancoDados.medicaoDao().buscarDesde(corte30d)
            return agregarHistoricoNds(medicoesUltimos30Dias = medicoes, agoraEpochMs = agora)
        }

        fun dispararBenchmarkDns() {
            val agora = System.currentTimeMillis()
            val expirado =
                ultimoBenchmarkDnsEpochMs == null ||
                    (agora - (ultimoBenchmarkDnsEpochMs ?: 0L)) > DNS_CACHE_TTL_MS
            if (!expirado) return
            val rede = monitorRede.snapshotFlow.value
            ultimoBenchmarkDnsEpochMs = agora
            ssidAoDispararDns = rede.wifiLinkSnapshot?.ssid
            estadoConexaoAoDispararDns = rede.estadoConexao
            viewModelScope.launch {
                benchmarkDns.executar(
                    resolvedoresAtivos = rede.dnsServidores,
                    privateDnsHostname = rede.privateDnsHostname,
                )
            }
        }

        fun iniciarDiagnostico() {
            solicitarDiagnostico()
        }

        suspend fun avaliarAssist(input: DiagnosticInput): DiagnosticReport =
            diagnosticOrchestrator.avaliarAssist(input)

        fun solicitarDiagnostico(): Long? {
            val reserva = diagnosticOrchestrator.tentarReservar() ?: return null
            val job =
                viewModelScope.launch {
                    diagnosticOrchestrator.executarReservada(reserva) {
                        // Acao explicita do usuario ("Analisar problema") — vale coletar a
                        // topologia agora se ainda nao rodou nesta sessao (SIG-279).
                        if (!topologiaColetada) {
                            topologiaColetada = true
                            coletarTopologiaRede()
                        }
                        val internetInput = speedtestResultToInternetInput()
                        val executionIdAtual = executionIdAtual()
                        val wifiSnapshot = monitorRede.snapshotFlow.value.wifiLinkSnapshot
                        val wifiInput =
                            wifiSnapshot?.let { ws ->
                                WifiDiagnosticInput(
                                    rssiDbm = ws.rssiDbm,
                                    linkSpeedMbps = ws.linkSpeedMbps,
                                    frequenciaMhz = ws.frequenciaMhz,
                                    wifiStandard = ws.padraoWifi,
                                    dispositivosNaRede =
                                        scannerDispositivos.snapshotFlow.value.dispositivos.size
                                            .takeIf { it > 0 },
                                )
                            }
                        DiagnosticInput(
                            connectionType =
                                monitorRede.snapshotFlow.value.estadoConexao
                                    .paraConnectionType(),
                            internet = internetInput,
                            wifi = wifiInput,
                            mobile = montarMobileInput(),
                            dns = montarDnsInput(),
                            historico = montarHistoricoInput(),
                            wifiScan = montarWifiScanInput(),
                            velocidadeContratadaMbps = montarVelocidadeContratadaMbps(),
                            natStatus = natStatusAtual,
                            executionId = executionIdAtual,
                        )
                    }
                }
            job.invokeOnCompletion { causa ->
                if (causa is CancellationException) {
                    diagnosticOrchestrator.cancelarReserva(reserva)
                }
            }
            return reserva.geracao
        }

        fun reconectarFibra(
            host: String,
            username: String,
            password: String,
        ) {
            viewModelScope.launch {
                val resolvedHost =
                    host.ifBlank {
                        preferenciasAppRepository.modemHostFlow.first()
                            ?: gateways.value.firstOrNull()?.ip
                            ?: return@launch
                    }
                executorFibra.executar(resolvedHost, username, password)
            }
        }

        // GH#934 — solicita reboot do equipamento conectado (so faz sentido com sessao
        // ativa). Nao reconecta sozinho: a UI mostra o estado "sessao expirada"/loading
        // ate o equipamento voltar e o usuario (ou a autoconexao) tentar de novo.
        fun reiniciarEquipamento() {
            viewModelScope.launch { executorFibra.reiniciar() }
        }

        fun salvarConfiguracaoModem(
            host: String,
            user: String,
            pass: String,
            perm: Boolean,
        ) {
            viewModelScope.launch {
                preferenciasAppRepository.definirModemHost(host.ifBlank { null })
                preferenciasAppRepository.definirModemUsername(user)
                preferenciasAppRepository.definirModemPassword(pass)
                preferenciasAppRepository.definirModemPermanecerConectado(perm)
                // GH#527 — revogar "manter conectado" por aqui tambem limpa o BSSID vinculado,
                // senao fica credencial orfa tentando autoconectar numa sessao que o usuario
                // ja desligou.
                // #894 — junto com o BSSID, encerra a sessao HTTP cacheada no ExecutorFibra:
                // desligar "manter conectado" tem que derrubar a sessao imediatamente, nao so
                // parar de tentar reconectar sozinho.
                if (!perm) {
                    preferenciasAppRepository.definirGatewaySessionBssid(null)
                    executorFibra.desconectar()
                }
            }
        }

        /**
         * Registra o resultado da [GatewayConnectionSheet][io.signallq.app.ui.screen.GatewayConnectionSheet]
         * (GH#526/#530): persiste o host sempre, credenciais so quando [lembrarSenha], e a sessao
         * "manter conectado" atrelada ao [bssidAtual] — e essa sessao (permanecerConectado +
         * BSSID batendo) que permite pular a sheet e ir direto ao destino provisorio na proxima
         * vez que o usuario tocar no gateway na mesma rede.
         */
        fun registrarConexaoGateway(
            ip: String,
            usuario: String,
            senha: String,
            lembrarSenha: Boolean,
            manterConectado: Boolean,
            bssidAtual: String?,
            driverIdConfirmado: String? = null,
        ) {
            viewModelScope.launch {
                // O C6 só chega aqui com driver confirmado depois da leitura
                // autenticada. Não toca no perfil global Nokia, inclusive host:
                // os dois aparelhos podem coexistir na mesma topologia.
                if (usaPerfilGatewayIsolado(driverIdConfirmado)) {
                    if (lembrarSenha) {
                        preferenciasAppRepository.salvarCredenciaisGatewayPerfil(
                            driverId = DRIVER_ID_TP_LINK_ARCHER_C6,
                            host = ip,
                            username = usuario,
                            password = senha,
                            bssidVinculado = if (manterConectado) bssidAtual else null,
                        )
                    }
                    return@launch
                }
                preferenciasAppRepository.definirModemHost(ip.ifBlank { null })
                if (lembrarSenha) {
                    preferenciasAppRepository.definirModemUsername(usuario)
                    preferenciasAppRepository.definirModemPassword(senha)
                }
                preferenciasAppRepository.definirModemPermanecerConectado(manterConectado)
                preferenciasAppRepository.definirGatewaySessionBssid(if (manterConectado) bssidAtual else null)
            }
        }

        fun definirTemaSelecionado(tema: String) {
            viewModelScope.launch { preferenciasAppRepository.definirTemaSelecionado(tema) }
        }

        /** Combinação jogo+device salva como padrão do Modo gamer (Feature #550, issue #1476)
         *  — `null` enquanto o usuário nunca salvou nenhuma. */
        val modoGamerPadrao: StateFlow<ModoGamerPadraoPersistido?> by lazy {
            preferenciasAppRepository.modoGamerPadraoFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

        /** `suspend` direto (sem `viewModelScope.launch`) — quem chama já está numa
         *  coroutine própria ([io.signallq.app.modogamer.ModoGamerViewModel.confirmar]). */
        suspend fun salvarModoGamerPadrao(
            jogoId: String?,
            categoriaFallback: String?,
            deviceId: String,
        ) {
            preferenciasAppRepository.salvarModoGamerPadrao(jogoId, categoriaFallback, deviceId)
        }

        fun definirAnaliseAvancada(ativa: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirAnaliseAvancada(ativa) }
        }

        fun atualizarMonitoramento(ativo: Boolean) {
            viewModelScope.launch {
                preferenciasAppRepository.definirMonitoramentoAtivo(ativo)
                if (ativo) {
                    MonitoramentoScheduler.agendar(getApplication())
                } else {
                    MonitoramentoScheduler.cancelar(getApplication())
                }
            }
        }

        fun atualizarStatusServicos() {
            viewModelScope.launch { serviceStatusRepository.atualizar() }
        }

        fun definirSeguimentoServico(
            serviceId: String,
            ativo: Boolean,
        ) {
            serviceStatusRepository.definirSeguimento(serviceId, ativo)
        }

        fun definirNotificacaoLatenciaAtiva(ativa: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirNotificacaoLatenciaAtiva(ativa) }
        }

        fun definirNotificacaoDnsAtiva(ativa: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirNotificacaoDnsAtiva(ativa) }
        }

        fun definirNotificacaoRssiAtiva(ativa: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirNotificacaoRssiAtiva(ativa) }
        }

        fun definirNotificacaoSemInternetAtiva(ativa: Boolean) {
            viewModelScope.launch { preferenciasAppRepository.definirNotificacaoSemInternetAtiva(ativa) }
        }

        // Issue #1670 — as 3 ações abaixo eram "fire and forget": a DadosLocaisSheet fechava no
        // toque em "Confirmar" sem esperar a corrotina terminar, e uma falha virava sucesso
        // silencioso pra quem usa o app. `dadosLocaisAcaoEstado` torna a execução observável
        // (EmAndamento/Sucesso/Falha) — ver AcaoDadosLocaisEstado.kt.
        private val _dadosLocaisAcaoEstado = MutableStateFlow<AcaoDadosLocaisEstado>(AcaoDadosLocaisEstado.Ocioso)
        val dadosLocaisAcaoEstado: StateFlow<AcaoDadosLocaisEstado> = _dadosLocaisAcaoEstado.asStateFlow()

        /** Volta o estado pra Ocioso depois que a UI já mostrou Sucesso/Falha. */
        fun consumirDadosLocaisAcaoEstado() {
            _dadosLocaisAcaoEstado.value = AcaoDadosLocaisEstado.Ocioso
        }

        private fun executarAcaoDadosLocais(
            acao: TipoAcaoDadosLocais,
            bloco: suspend () -> Unit,
        ) {
            viewModelScope.launch(dispatchers.io) {
                _dadosLocaisAcaoEstado.value = AcaoDadosLocaisEstado.EmAndamento(acao)
                try {
                    bloco()
                    _dadosLocaisAcaoEstado.value = AcaoDadosLocaisEstado.Sucesso(acao)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.w("Ação de dados locais ($acao) falhou: ${e.message}")
                    _dadosLocaisAcaoEstado.value =
                        AcaoDadosLocaisEstado.Falha(acao, "Não foi possível concluir. Tente novamente.")
                }
            }
        }

        fun limparHistorico() {
            executarAcaoDadosLocais(TipoAcaoDadosLocais.LIMPAR_HISTORICO) {
                bancoDados.medicaoDao().deletarTodos()
            }
        }

        fun deletarMedicao(id: String) {
            viewModelScope.launch(dispatchers.io) {
                bancoDados.medicaoDao().deletarPorId(id)
            }
        }

        fun apagarDadosLocais() {
            executarAcaoDadosLocais(TipoAcaoDadosLocais.APAGAR_DADOS_LOCAIS) {
                preferenciasAppRepository.limparTodasPreferencias()
            }
        }

        fun resetarApp() {
            executarAcaoDadosLocais(TipoAcaoDadosLocais.RESETAR_APP) {
                bancoDados.medicaoDao().deletarTodos()
                preferenciasAppRepository.limparTodasPreferencias()
            }
        }

        fun salvarPerfil(
            nome: String,
            fotoUri: String?,
        ) {
            viewModelScope.launch {
                preferenciasAppRepository.definirNomeUsuario(nome)
                preferenciasAppRepository.definirFotoUriUsuario(fotoUri)
            }
        }

        // GH#1249 (recorte de #1227) — salvarDadosProvedor/salvarEstadoCidade/
        // salvarVelocidadeContratada/confirmarIspDetectado/dispensarBannerIsp removidos: eram
        // órfãos de fato (única chamada de cada um vinha do mesmo fluxo de "Minha conexão" em
        // AjustesScreen.kt, que agora usa salvarConnectionProfileAtual, per-rede, em vez de
        // chave DataStore global — ver ConnectionProfilePersistido/DetectorDivergenciaPerfilConexao).
        // `operadora` (chave global) continua existindo só pra alimentar o relatório de
        // diagnóstico (LaudoScreen) e a migração inicial (migrarPerfilGlobalLegado) — fica
        // congelada no último valor salvo antes desta mudança, não recebe escrita nova; migrar
        // LaudoScreen pra também ler o perfil por rede é trabalho futuro, fora do escopo de #1249.

        fun salvarUltimaVersaoVista(versao: String) {
            viewModelScope.launch {
                preferenciasAppRepository.definirUltimaVersaoVista(versao)
            }
        }

        fun salvarLimiteAlerta(limite: Int) {
            viewModelScope.launch { preferenciasAppRepository.definirLimiteAlertaMbps(limite) }
        }

        /**
         * #853 — [mac] pode ser o MAC real do dispositivo ou a chave sintetica de fallback
         * (`DispositivoRede.chaveApelido()`) quando o MAC nao e resolvivel via ARP.
         */
        fun salvarApelido(
            mac: String,
            apelido: String,
        ) {
            viewModelScope.launch {
                bancoDados.apelidoDispositivoDao().salvar(
                    ApelidoDispositivoEntity(mac = mac, apelido = apelido),
                )
            }
        }

        /**
         * Snapshot do scan de dispositivos exposto publicamente.
         * A UI pode observar este flow para exibir contagem no card Wi-Fi da Home
         * e alimentar a DispositivosScreen sem passar pelo orquestrador.
         */
        val snapshotDispositivos: StateFlow<SnapshotScanDispositivos> by lazy {
            scannerDispositivos.snapshotFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), scannerDispositivos.snapshotFlow.value)
        }

        /** #983 (Fase 4) — versao continua (nao reduzida a um mapa por BSSID como em
         *  [montarWifiScanInput]) da classificacao de topologia Wi-Fi, exposta pra alimentar
         *  [correlacoesTopologia]. */
        val redesWifiClassificadas: StateFlow<List<Pair<RedeVizinha, ClassificacaoTopologia>>> by lazy {
            combine(scannerRedesWifi.snapshotFlow, monitorRede.snapshotFlow) { wifiSnapshot, redeSnapshot ->
                TopologiaRedeEngine.classificar(
                    redes = wifiSnapshot.redes,
                    connectedBssid = redeSnapshot.wifiLinkSnapshot?.bssid,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

        /** #983 (Fase 4) — correlaciona best-effort cada dispositivo do scan LAN (feature/devices)
         *  com a topologia Wi-Fi classificada e com a leitura direta do gateway (ClientSnapshot),
         *  quando disponiveis. Sem scan Wi-Fi ou credencial de gateway, todo dispositivo cai em
         *  [io.signallq.app.feature.devices.NivelCorrelacao.SEM_MATCH] — comportamento identico ao
         *  de antes da Fase 4. Correlacao fraca (so OUI) nunca reclassifica sozinha — ver
         *  [correlacionarDispositivoComTopologia]. */
        val correlacoesTopologia: StateFlow<Map<String, ResultadoCorrelacaoTopologia>> by lazy {
            combine(
                snapshotDispositivos,
                redesWifiClassificadas,
                localDeviceSnapshot,
            ) { snapshot, redes, localDevice ->
                construirCorrelacoesTopologia(
                    dispositivos = snapshot.dispositivos,
                    redesWifiClassificadas = redes,
                    clientesGateway = localDevice?.clientes.orEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
        }

        fun refreshDispositivos() {
            viewModelScope.launch {
                scannerDispositivos.iniciarScan(clientesGateway = localDeviceSnapshot.value?.clientes.orEmpty())
            }
        }

        /**
         * Verifica se ha dispositivos novos na rede e notifica o usuario.
         *
         * Executa um scan leve (profundo=false) e compara as identidades estáveis dos dispositivos
         * encontrados com as identidades já conhecidas. Identidade estável:
         *  - Se houver MAC: "mac:<MAC em lowercase>" → persiste na tabela Room (fluxo original).
         *  - Sem MAC: "ipnome:<IP>:<nomeNormalizado>" → persiste no DataStore (sem poluir Room).
         *
         * LIMITAÇÃO DOCUMENTADA: dispositivos sem MAC têm identidade derivada de ip+nome.
         * Se o IP mudar por DHCP ou o nome mudar (ex.: reboot muda hostname), o dispositivo
         * pode ser notificado novamente como "novo". Comportamento aceitável dado que MACs
         * randomizados no Android 10+ tornam a alternativa (só MAC) pior — detectaria nada.
         *
         * Chamado no onResume da MainActivity — scan leve, sem WorkManager.
         */
        fun verificarDispositivosNovos(context: android.content.Context) {
            viewModelScope.launch(dispatchers.io) {
                try {
                    // Scan leve — nao bloqueia UI, resultado rapido via ARP + SubnetDevices
                    scannerDispositivos.iniciarScan(profundo = false, clientesGateway = localDeviceSnapshot.value?.clientes.orEmpty())

                    val dispositivosAtuais = scannerDispositivos.snapshotFlow.value.dispositivos

                    // Identidades conhecidas: MACs do Room + identidades ip+nome do DataStore
                    val macsConhecidosRoom =
                        bancoDados
                            .apelidoDispositivoDao()
                            .buscarTodos()
                            .map { it.mac }
                            .toSet()
                    val identidadesConhecidas =
                        preferenciasAppRepository.buscarDispositivosConhecidos().toMutableSet()

                    val novasIdentidades = mutableSetOf<String>()

                    dispositivosAtuais.forEach { dispositivo ->
                        val identidade = identidadeEstavelDispositivo(dispositivo)
                        val mac = dispositivo.mac // val local para smart cast cross-module
                        when {
                            // Dispositivo com MAC: fluxo original via Room
                            mac != null -> {
                                val macNorm = mac.lowercase()
                                if (macNorm !in macsConhecidosRoom) {
                                    SignallQNotificationHelper.notificarDispositivoNovo(
                                        context,
                                        mac,
                                    )
                                    bancoDados.apelidoDispositivoDao().inserirSilencioso(
                                        ApelidoDispositivoEntity(mac = macNorm, apelido = null),
                                    )
                                }
                            }
                            // Dispositivo sem MAC: identidade ip+nome via DataStore
                            identidade != null && identidade !in identidadesConhecidas -> {
                                SignallQNotificationHelper.notificarDispositivoNovo(
                                    context,
                                    dispositivo.ip ?: dispositivo.nomeExibicao,
                                )
                                novasIdentidades.add(identidade)
                            }
                        }
                    }

                    // Persiste novas identidades sem MAC de uma só vez (batch)
                    if (novasIdentidades.isNotEmpty()) {
                        identidadesConhecidas.addAll(novasIdentidades)
                        preferenciasAppRepository.salvarDispositivosConhecidos(identidadesConhecidas)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.w("verificarDispositivosNovos falhou: ${e.message}")
                }
            }
        }

        /**
         * Retorna uma identidade estável para rastrear dispositivos entre scans.
         *
         * - Com MAC disponível: retorna null (o fluxo via Room já cobre esse caso).
         * - Sem MAC: retorna "ipnome:<IP>:<nome normalizado>" como fallback.
         *   LIMITAÇÃO: pode gerar falso-novo se IP mudar por DHCP ou nome mudar por reboot.
         */
        internal fun identidadeEstavelDispositivo(dispositivo: io.signallq.app.feature.devices.DispositivoRede): String? {
            if (dispositivo.mac != null) return null // MAC presente → fluxo Room, não precisa de identidade DataStore
            val ip = dispositivo.ip ?: return null
            val nome = dispositivo.nomeExibicao.trim().lowercase()
            return "ipnome:$ip:$nome"
        }

        fun refreshSinal() {
            viewModelScope.launch { scannerRedesWifi.escanear() }
        }

        fun coletarInfoLocalRede() {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return
            val props = cm.getLinkProperties(network) ?: return
            val ipLocal =
                props.linkAddresses
                    .mapNotNull { it.address.hostAddress }
                    .firstOrNull { it.contains('.') && !it.startsWith("127.") && !it.startsWith("169.254.") }
            localIp.value = if (ipLocal != null) UiState.Success(ipLocal) else UiState.Loading

            val snapshotRede = monitorRede.snapshotFlow.value
            if (snapshotRede.estadoConexao == EstadoConexao.movel) {
                gateways.value = listOf(GatewayInfo(ip = null, name = "Rede móvel", type = ConnectionNodeType.Mobile))
                return
            }

            val ssid =
                snapshotRede.wifiLinkSnapshot
                    ?.ssid
                    ?.trim('"')
                    .orEmpty()
            val bssidAtual = snapshotRede.wifiLinkSnapshot?.bssid
            val redesVizinhas = scannerRedesWifi.snapshotFlow.value.redes
            // #980 (Fase 2B) — motor unificado (ve OUI e banda, TopologiaRedeEngine/#979)
            // substitui a heuristica que so olhava SSID/contagem de BSSID; nao confunde mais
            // roteador dual-band nem extensor de outro fabricante com mesh real.
            // papelProvavel nunca afirma "roteador central" sozinho — vira
            // SISTEMA_MESH_PROVAVEL quando so ha evidencia de scan Wi-Fi (sem 2a rota IP).
            val classificacaoConectada =
                TopologiaRedeEngine
                    .classificar(redes = redesVizinhas, connectedBssid = bssidAtual)
                    .firstOrNull { it.first.bssid == bssidAtual }
                    ?.second
            val gatewayType = papelParaConnectionNodeType(classificacaoConectada?.papelProvavel ?: PapelTopologia.DESCONHECIDO)
            val confiancaTopologia = classificacaoConectada?.confianca
            val gatewayName =
                ssid.ifBlank {
                    when (gatewayType) {
                        ConnectionNodeType.WifiMesh -> "Rede Mesh"
                        ConnectionNodeType.WifiExtender -> "Repetidor"
                        else -> "Roteador"
                    }
                }
            val gatewayIps =
                props.routes
                    .mapNotNull { it.gateway?.hostAddress }
                    .filter { ip -> !ip.startsWith("0.") && !ip.startsWith("127.") && ip.contains('.') }
                    .distinct()
            gateways.value =
                if (
                    gatewayType == ConnectionNodeType.WifiMesh ||
                    gatewayType == ConnectionNodeType.WifiExtender
                ) {
                    val meshIp = gatewayIps.getOrNull(0)
                    val routerIp = gatewayIps.getOrNull(1)
                    // O Android normalmente expõe apenas uma rota default (o nó mesh ao qual o
                    // dispositivo está conectado). O roteador central por trás do mesh não tem IP
                    // visível, então só criamos o nó "Roteador" quando há de fato um segundo gateway.
                    buildList {
                        add(GatewayInfo(ip = meshIp, name = gatewayName, type = gatewayType, confianca = confiancaTopologia))
                        if (routerIp != null) {
                            add(GatewayInfo(ip = routerIp, name = "Roteador", type = ConnectionNodeType.WifiRouter))
                        }
                    }
                } else {
                    gatewayIps
                        .map { ip ->
                            GatewayInfo(ip = ip, name = gatewayName, type = gatewayType, confianca = confiancaTopologia)
                        }.ifEmpty {
                            listOf(GatewayInfo(ip = null, name = gatewayName, type = gatewayType, confianca = confiancaTopologia))
                        }
                }
        }

        private suspend fun coletarIspInfo() =
            withContext(dispatchers.io) {
                try {
                    val connection =
                        URL("https://ipapi.co/json/")
                            .openConnection() as HttpURLConnection
                    connection.connectTimeout = 6_000
                    connection.readTimeout = 6_000
                    connection.setRequestProperty("User-Agent", "SignallQ/1.0")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val json = JSONObject(body)
                    val ip = json.optString("ip").ifBlank { null }
                    val operadora = json.optString("org").ifBlank { null }
                    val info =
                        IspInfo(
                            ip = ip,
                            isp = operadora,
                            asn = json.optString("asn").ifBlank { null },
                            country = json.optString("country_name").ifBlank { null },
                            region = json.optString("region").ifBlank { null },
                        )
                    publicIp.value = if (ip != null) UiState.Success(ip) else UiState.Error("IP indisponivel")
                    ispInfo.value = UiState.Success(info)
                    ispInfoCache.atualizar(operadora)
                    if (monitorRede.snapshotFlow.value.estadoConexao == EstadoConexao.movel) {
                        gateways.value =
                            listOf(
                                GatewayInfo(ip = null, name = operadora ?: "Operadora", type = ConnectionNodeType.Mobile),
                            )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    publicIp.value = UiState.Error("Falha ao obter IP publico")
                    ispInfo.value = UiState.Error("ISP indisponivel")
                    Timber.w("coletarIspInfo falhou: ${e.message}")
                }
            }

        private suspend fun executarSpeedtest(modo: ModoSpeedtest) {
            val connectionType = monitorRede.snapshotFlow.value.estadoConexao.name
            Timber.i("iniciando modo=${modo.name} connectionType=$connectionType")
            executorSpeedtest.executar(
                modo = modo,
                connectionType = connectionType,
                connectionTypeProvider = { monitorRede.snapshotFlow.value.estadoConexao.name },
                tecnologiaProvider = { monitorTelephony.snapshotFlow.value?.tecnologia },
                // GH#1221 RF-01 — resolve o perfil de pool (metered/movel) no momento do
                // teste, nao no valor congelado na criacao do singleton via AppModule.
                isMobileProvider = { networkCapabilitiesProvider.isMeteredNetwork() },
            )
            Timber.i("finalizado modo=${modo.name}")
        }

        /**
         * GH#1512 — decide localmente rodando o diagnostico ativo de verdade sempre que ha
         * uma rede Wi-Fi presente ([ConnectivityDiagnosisRepository.existeRedeWifiAtiva],
         * checagem direta via `ConnectivityManager`, nunca via [MonitorRede]/rede default —
         * achado de revisao: `SnapshotRede.conectado`/`estadoConexao` podem ficar
         * "otimistas" por ate ~600ms apos instabilidade, ou seguir dados moveis quando o
         * Android promove a rede movel a default, mascarando exatamente o cenario que esta
         * issue pede para detectar). Sem rede Wi-Fi nenhuma, nao intervem aqui — o
         * fluxo/dialogo ja existente (guarda de rede medida acima, `ForaDoWifiDialog` na
         * AppShell) cobre rede movel pura.
         */
        private suspend fun interromperSpeedtestPorWifiSemInternet(): Boolean {
            // GH#1512 (achado de revisao) -- existeRedeWifiAtiva() e diagnosticar() ficam
            // no MESMO try/catch: uma excecao inesperada em qualquer uma delas (binder
            // IPC do ConnectivityManager, I/O de persistencia, etc.) nunca pode propagar
            // como crash nem travar execucaoSpeedtestEmAndamento -- so nao bloqueia o
            // teste (equivalente a "sem evidencia suficiente", igual a INCONCLUSIVE logo
            // abaixo), deixando o Speedtest seguir seu proprio caminho normal de erro.
            val diagnostico =
                try {
                    if (!connectivityDiagnosisRepository.existeRedeWifiAtiva()) return false
                    connectivityDiagnosisRepository.diagnosticar()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    Timber.e(t, "diagnostico de conectividade falhou inesperadamente -- nao bloqueia o speedtest")
                    return false
                }

            // GH#1512 (3a revisao) -- politica extraida para ConnectivityBlockingPolicy.kt
            // (testavel isoladamente, sem duplicar entre MainViewModel/SpeedtestViewModel --
            // achado de revisao R-6). Ver KDoc la para o raciocinio completo.
            if (!diagnostico.indicaAusenciaDeInternetParaBloquearSpeedtest()) return false

            _diagnosticoConectividade.value = ConnectivityDiagnosisPresenter.apresentar(diagnostico)
            Timber.i("speedtest interrompido -- wifi sem internet, status=${diagnostico.status}")
            return true
        }

        /**
         * Coleta TODOS os dados brutos disponiveis no app que possam ajudar a IA a
         * diagnosticar. Chamada por [analisarProblema] antes de cada explainDiagnosis.
         *
         * Politica: dado que nao existe -> null (omitido do payload). Nao inventa.
         * NAO inclui analise local, classificacao ou rotulos. So dados crus.
         */
        private suspend fun coletarContextoAdicionalIa(): AdditionalAiContext {
            val rede = monitorRede.snapshotFlow.value
            val wifi = rede.wifiLinkSnapshot
            val isp = (ispInfo.value as? UiState.Success)?.data

            // Wi-Fi: BSSID, padrao (Wi-Fi 5/6/...) e link speed
            val wifiBssid = wifi?.bssid
            val wifiPadrao = wifi?.padraoWifi
            val wifiLinkSpeedMbps = wifi?.linkSpeedMbps

            // DNS resolver primario (IP + provedor inferido por hostname/IP)
            val dnsResolverIp = rede.dnsServidores.firstOrNull()
            val dnsResolverProvider = inferirProvedorAtivoDns(rede.privateDnsHostname, rede.dnsServidores)

            // Historico cru — ultimas 5 medicoes (sem rotulo, so numeros)
            val ultimosTestes =
                try {
                    bancoDados.medicaoDao().observarUltimas(5).first().map { m ->
                        AiTesteHistorico(
                            timestampEpochMs = m.timestampEpochMs,
                            downloadMbps = m.downloadMbps,
                            uploadMbps = m.uploadMbps,
                            latenciaMs = m.latencyMs,
                            jitterMs = m.jitterMs,
                            perdaPercentual = m.perdaPercentual,
                            connectionType = m.connectionType,
                        )
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }

            // Redes Wi-Fi vizinhas (scan). Pega as 15 mais fortes (RSSI maior).
            val redesProximas =
                try {
                    scannerRedesWifi.snapshotFlow.value.redes
                        .sortedByDescending { it.rssiDbm }
                        .take(15)
                        .map { rv ->
                            AiRedeVizinha(
                                ssid = rv.ssid,
                                bssid = rv.bssid,
                                rssiDbm = rv.rssiDbm,
                                frequenciaMhz = rv.frequenciaMhz,
                                canal = rv.canal,
                                seguranca = rv.seguranca.name,
                            )
                        }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    emptyList()
                }

            // Dispositivo do usuario
            val dispositivos =
                AiDispositivosInfo(
                    fabricante = android.os.Build.MANUFACTURER,
                    modelo = android.os.Build.MODEL,
                    sistema = "Android",
                    versaoSO = android.os.Build.VERSION.RELEASE,
                    quantidadeNaRede =
                        scannerDispositivos.snapshotFlow.value.dispositivos.size
                            .takeIf { it > 0 },
                )

            // Telefonia movel: SO populado quando connectionType=mobile (economia
            // de bateria — em Wi-Fi/Ethernet o monitor sequer e iniciado). Quando
            // a permissao READ_PHONE_STATE foi negada, snapshot vai null e a IA
            // recebe movel: null (gracioso).
            val movel: AiMovelInfo? =
                if (rede.estadoConexao == EstadoConexao.movel) {
                    // Garante que o monitor esta rodando — idempotente.
                    monitorTelephony.iniciar()
                    monitorTelephony.snapshotFlow.value?.let { snap -> mapMovelSnapshotToAi(snap) }
                } else {
                    null
                }

            return AdditionalAiContext(
                ispNome = isp?.isp,
                ispOperadoraDetectada = isp?.isp?.let { raw -> BancoOperadoras.resolver(raw)?.nome ?: raw },
                ispAsn = isp?.asn,
                ipPublico = isp?.ip ?: (publicIp.value as? UiState.Success)?.data,
                ipLocal = (localIp.value as? UiState.Success)?.data,
                pais = isp?.country,
                regiao = isp?.region,
                gatewayIp = gateways.value.firstOrNull()?.ip,
                dnsResolverIp = dnsResolverIp,
                dnsResolverProvider = dnsResolverProvider,
                dnsLatenciaMs = null,
                servidorTesteCidade = (localizacaoServidor.value as? UiState.Success)?.data,
                ultimosTestesHistorico = ultimosTestes,
                redesProximas = redesProximas,
                movel = movel,
                dispositivos = dispositivos,
                privateDnsAtivo = rede.privateDnsAtivo,
                privateDnsHostname = rede.privateDnsHostname,
                wifiBssid = wifiBssid,
                wifiPadrao = wifiPadrao,
                wifiLinkSpeedMbps = wifiLinkSpeedMbps,
                speedtestExtras = null,
            )
        }

        /** GH#1225 item 7/G — job em andamento de [analisarProblema], cancelado antes de
         *  iniciar uma nova analise. Sem isso, uma resposta tardia (ate 45s de timeout
         *  interno) de uma chamada anterior podia sobrescrever `_analisadorState` DEPOIS
         *  de uma analise mais nova ja ter concluido, mostrando texto de uma execucao
         *  velha por cima do resultado atual. */
        private var analisarProblemaJob: kotlinx.coroutines.Job? = null

        /**
         * Chamada de IA de diagnostico -- mecanismo UNICO (decisao do Luiz, 2026-07-14)
         * reaproveitado tanto pela tela 1a "Analise detalhada" (spec To-Be -- acionada
         * automaticamente ao abrir o sheet a partir do Resultado, `problema = null`)
         * quanto pelo fluxo com objetivo/sintoma escolhido pelo usuario (`problema`
         * preenchido -- hoje `DiagnosticoGuiadoScreen` e `ModoGamerScreen`; o sheet
         * legado `AnaliseDetalhadaBottomSheet` foi removido em #1485). Os cards da 1a
         * (banner de veredito, Recomendacoes, Configuracoes) sao montados a partir do
         * MESMO [AnalisadorState.Resultado] que essa funcao produz.
         *
         * Timeout de UI proprio (~5s, spec da 1a) por cima do timeout interno do
         * repository (40s): se a IA nao respondeu em 5s, cai pro fallback local
         * (`AiFallbackFactory.fromLocal`, sincrono/zero rede) em vez de deixar o usuario
         * esperando o teto de 40s do repository -- vale pros dois gatilhos.
         */
        fun analisarProblema(problema: String? = null) {
            val snap = diagnosticOrchestrator.snapshotFlow.value
            val relatorio =
                snap.relatorio ?: run {
                    _analisadorState.value = AnalisadorState.Erro("Faça um diagnóstico de rede antes de analisar.")
                    return
                }
            _analisadorState.value = AnalisadorState.Analisando
            // GH#1225 — cancela qualquer analise anterior ainda em voo antes de iniciar
            // esta, para a resposta tardia dela nunca sobrescrever o estado atual.
            analisarProblemaJob?.cancel()
            analisarProblemaJob =
                viewModelScope.launch {
                    try {
                        // NDS-02k PR2 (issue #1746) -- com a flag ligada, `relatorio` ja veio do
                        // NDS (DiagnosticOrchestrator.executarProtegido troca a fonte na MESMA
                        // execucao que produziu este relatorio) e ja carrega a narrativa do
                        // modulo `ai` do NDS embutida em decisao.titulo/mensagemUsuario -- ver
                        // kdoc de `resolverResultadoAnaliseViaNds` para o achado completo sobre
                        // por que NdsClient nao substitui AiDiagnosisRepository.explainDiagnosis
                        // ponto a ponto. Com a flag desligada (default, todo ambiente hoje) esta
                        // funcao devolve null e o fluxo abaixo segue IDENTICO ao anterior.
                        val ndsLiveEnabled =
                            featureFlagProvider.isEnabled(FeatureFlagKeys.CONSUMER_DIAGNOSTICO_NDS_LIVE_ENABLED)
                        val resultadoViaNds = resolverResultadoAnaliseViaNds(ndsLiveEnabled, relatorio, problema)
                        if (resultadoViaNds != null) {
                            _analisadorState.value = resultadoViaNds
                            speedtestPersistenceCoordinator.atualizarDiagnosticoIa(resultadoViaNds.texto, problema)
                            return@launch
                        }
                        val connectionType =
                            snap.input?.connectionType
                                ?: io.signallq.app.core.diagnostico.ConnectionType.desconhecido
                        val extra = coletarContextoAdicionalIa()
                        val ctx =
                            DiagnosisAiContextFactory.fromRaw(
                                report = relatorio,
                                input = snap.input,
                                connectionType = connectionType,
                                feedbackUsuario = problema,
                                ispNome = extra.ispNome,
                                ispAsn = extra.ispAsn,
                                ipPublico = extra.ipPublico,
                                ipLocal = extra.ipLocal,
                                pais = extra.pais,
                                regiao = extra.regiao,
                                gatewayIp = extra.gatewayIp,
                                dnsResolverIp = extra.dnsResolverIp,
                                dnsResolverProvider = extra.dnsResolverProvider,
                                servidorTesteCidade = extra.servidorTesteCidade,
                                ultimosTestesHistorico = extra.ultimosTestesHistorico,
                                redesProximas = extra.redesProximas,
                                movel = extra.movel,
                                dispositivos = extra.dispositivos,
                                privateDnsAtivo = extra.privateDnsAtivo,
                                privateDnsHostname = extra.privateDnsHostname,
                                wifiLinkBssid = extra.wifiBssid,
                                wifiPadrao = extra.wifiPadrao,
                                wifiLinkSpeedMbps = extra.wifiLinkSpeedMbps,
                            )
                        val resultado =
                            withTimeoutOrNull(45_000L) {
                                diagAiRepository.explainDiagnosis(
                                    context = ctx,
                                    decisaoLocalStatus = relatorio.decisao.status.name,
                                ) { AiFallbackFactory.fromLocal(relatorio) }
                            }
                        when (resultado) {
                            is AiDiagnosisState.Success -> {
                                val texto = resultado.result.textoLaudo.ifBlank { resultado.result.resumo }
                                _analisadorState.value =
                                    AnalisadorState.Resultado(
                                        texto = texto,
                                        origem = "ia",
                                        acoes = resultado.result.acoesRecomendadas,
                                        titulo = resultado.result.titulo,
                                        resumo = resultado.result.resumo,
                                        problemaRelatado = problema,
                                        confianca = relatorio.rotuloConfianca,
                                    )
                                speedtestPersistenceCoordinator.atualizarDiagnosticoIa(texto, problema)
                            }
                            is AiDiagnosisState.Fallback -> {
                                val texto = resultado.result.textoLaudo.ifBlank { resultado.result.resumo }
                                _analisadorState.value =
                                    AnalisadorState.Resultado(
                                        texto = texto,
                                        origem = "local",
                                        acoes = resultado.result.acoesRecomendadas,
                                        titulo = resultado.result.titulo,
                                        resumo = resultado.result.resumo,
                                        problemaRelatado = problema,
                                        confianca = relatorio.rotuloConfianca,
                                    )
                                speedtestPersistenceCoordinator.atualizarDiagnosticoIa(texto, problema)
                            }
                            // Timeout de UI (5s) estourado, ou repository devolveu timeout/error/idle/loading:
                            // cai pro fallback local sincrono em vez de erro -- o app consegue responder
                            // sozinho a partir do relatorio ja calculado, sem depender da rede.
                            else -> {
                                val local = AiFallbackFactory.fromLocal(relatorio)
                                val texto = local.textoLaudo.ifBlank { local.resumo }
                                _analisadorState.value =
                                    AnalisadorState.Resultado(
                                        texto = texto,
                                        origem = "local",
                                        acoes = local.acoesRecomendadas,
                                        titulo = local.titulo,
                                        resumo = local.resumo,
                                        problemaRelatado = problema,
                                        confianca = relatorio.rotuloConfianca,
                                    )
                                speedtestPersistenceCoordinator.atualizarDiagnosticoIa(texto, problema)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Timber.e(e, "analisarProblema falhou")
                        _analisadorState.value = AnalisadorState.Erro("Erro ao analisar. Tente novamente.")
                    }
                }
        }

        fun resetarAnalisador() {
            analisarProblemaJob?.cancel()
            _analisadorState.value = AnalisadorState.Inativo
        }

        // ── Reteste vinculado (GH#1707, Task 2.0.09e, parte 2/2, épico #1647) ────────

        private val _comparacaoRetesteState = MutableStateFlow<ComparacaoRetesteUiState>(ComparacaoRetesteUiState.Ausente)
        val comparacaoRetesteState: StateFlow<ComparacaoRetesteUiState> = _comparacaoRetesteState.asStateFlow()

        /**
         * Telemetria do funil (spec #1657, passos 8/9) — emitida aqui e não injetada como
         * `AnalyticsTracker` neste ViewModel de propósito: o funil principal e o `feature_used`
         * já são "instrumentados em MainActivity/DiagnosticOrchestrator, não neste ViewModel"
         * (ver comentário de [recommendationAnalyticsTracker] acima). O cálculo do payload
         * (rede/DB/orchestrator) só pode acontecer aqui; o disparo pro Firebase continua na
         * Activity, que já tem o `AnalyticsTracker` de verdade.
         */
        private val _retesteIniciadoEvent = MutableSharedFlow<DiagnosticoRetesteIniciado>(extraBufferCapacity = 1)
        val retesteIniciadoEvent: SharedFlow<DiagnosticoRetesteIniciado> = _retesteIniciadoEvent.asSharedFlow()

        private val _comparacaoConcluidaEvent = MutableSharedFlow<DiagnosticoComparacaoConcluida>(extraBufferCapacity = 1)
        val comparacaoConcluidaEvent: SharedFlow<DiagnosticoComparacaoConcluida> = _comparacaoConcluidaEvent.asSharedFlow()

        private var retesteVinculadoJob: kotlinx.coroutines.Job? = null

        /**
         * CTA "Testar novamente" **vinculado** à análise original (spec §8.8) — nunca "recomeçar
         * do zero" (isso é [solicitarDiagnostico] chamado direto de `ResultadoVelocidadeScreen`,
         * outro fluxo). Dispara uma medição nova de verdade pelo MESMO pipeline
         * ([solicitarDiagnostico]), aguarda a conclusão e calcula o veredito de comparação
         * (§14.6) contra a medição anterior NA MESMA REDE (`MedicaoDao.buscarUltimaComparavelNaRede`,
         * GH#1707 parte 1/2) — nunca compara redes diferentes com aviso, declara o limite
         * ("inconclusiva").
         *
         * [acaoAnteriorId] vazio quando o usuário retestou sem executar nenhuma ação antes.
         */
        fun testarNovamenteVinculado(
            analiseIdOriginal: String,
            acaoAnteriorId: String = "",
        ) {
            if (retesteVinculadoJob?.isActive == true) return
            retesteVinculadoJob =
                viewModelScope.launch {
                    val medicaoOriginal =
                        bancoDados
                            .medicaoDao()
                            .observarUltimas(1)
                            .first()
                            .firstOrNull()
                    val medicaoOriginalId = medicaoOriginal?.id
                    val retesteId = UUID.randomUUID().toString()
                    val intervaloMs = medicaoOriginal?.let { System.currentTimeMillis() - it.timestampEpochMs } ?: 0L
                    val mesmoContextoRede =
                        medicaoOriginal?.networkId != null && medicaoOriginal.networkId == networkIdAtual.value
                    val relatorioAnterior = diagnosticOrchestrator.snapshotFlow.value.relatorio
                    val statusAnterior = relatorioAnterior?.decisao?.status?.name ?: "inconclusive"

                    _retesteIniciadoEvent.emit(
                        DiagnosticoRetesteIniciado(
                            analiseId = analiseIdOriginal,
                            retesteId = retesteId,
                            acaoAnteriorId = acaoAnteriorId,
                            intervaloMs = intervaloMs,
                            mesmoContextoRede = mesmoContextoRede,
                        ),
                    )
                    _comparacaoRetesteState.value = ComparacaoRetesteUiState.EmAndamento

                    val novaGeracao = solicitarDiagnostico()
                    if (novaGeracao == null) {
                        _comparacaoRetesteState.value = ComparacaoRetesteUiState.Ausente
                        return@launch
                    }

                    // Observa a MESMA fonte que persiste a medição (Room), não um contador em
                    // memória — sobrevive a qualquer diferença de timing entre
                    // `SpeedtestPersistenceCoordinator` (persiste) e `DiagnosticOrchestrator`
                    // (avalia), que rodam em coletores independentes.
                    val medicaoNova =
                        withTimeoutOrNull(60_000L) {
                            bancoDados
                                .medicaoDao()
                                .observarUltimas(1)
                                .first { lista -> lista.firstOrNull()?.id?.let { it != medicaoOriginalId } == true }
                        }?.firstOrNull()

                    val relatorioNovo =
                        withTimeoutOrNull(15_000L) {
                            diagnosticOrchestrator.snapshotFlow.first { it.geracao == novaGeracao && it.relatorio != null }
                        }?.relatorio

                    if (medicaoNova == null) {
                        _comparacaoRetesteState.value = ComparacaoRetesteUiState.Ausente
                        return@launch
                    }

                    val comparavelEntity =
                        medicaoNova.networkId?.let { networkId ->
                            bancoDados.medicaoDao().buscarUltimaComparavelNaRede(
                                networkId = networkId,
                                excluirId = medicaoNova.id,
                                antesDoTimestamp = medicaoNova.timestampEpochMs,
                            )
                        }
                    val comparavel = comparavelEntity != null
                    val veredito = calcularVereditoReteste(comparavelEntity, medicaoNova)

                    _comparacaoConcluidaEvent.emit(
                        DiagnosticoComparacaoConcluida(
                            analiseId = analiseIdOriginal,
                            retesteId = retesteId,
                            veredito = veredito.paraTelemetriaReteste(),
                            comparavel = comparavel,
                            statusAnterior = statusAnterior,
                            statusNovo = relatorioNovo?.decisao?.status?.name ?: "inconclusive",
                        ),
                    )

                    _comparacaoRetesteState.value =
                        ComparacaoRetesteUiState.Concluido(
                            veredito = veredito.rotuloComparacaoReteste(),
                            comparavel = comparavel,
                        )
                }
        }

        // ── Recomendacao do Recommendation Engine (#813) ─────────────────────────────

        /**
         * Chamada uma vez por diagnostico concluido (ver [iniciarObservadores]).
         * Nenhum tipo monetizado entra nesta entrega -- flags correspondentes desligadas
         * (criterio de aceite da #813); apenas free_tip/tutorial/configuration podem
         * aparecer aqui ate a monetizacao real ser implementada.
         */
        private suspend fun avaliarRecomendacao(
            relatorio: DiagnosticReport,
            input: DiagnosticInput,
        ) {
            val diagnosticId = relatorio.geradoEmMs.toString()
            val decisao =
                recommendationDecisionCoordinator.escolherRecomendacao(
                    report = relatorio,
                    input = input,
                    isp = ispInfoCache.ultimoIspNome,
                    flags =
                        RecommendationFlags(
                            affiliateEnabled = false,
                            partnerOffersEnabled = false,
                            operatorOffersEnabled = false,
                            nativeAdFallbackEnabled = false,
                        ),
                    diagnosticId = diagnosticId,
                )
            _recommendationDecision.value = decisao
            _recommendationFeedback.value = null
            recommendationDiagnosticId = diagnosticId
            if (decisao != null) {
                recommendationAnalyticsTracker.track(
                    decisao.toAnalyticsPayload(RecommendationAnalyticsEventName.ELIGIBLE, diagnosticId = diagnosticId),
                )
            }
        }

        /** Chamada pela UI quando o card de recomendacao e efetivamente renderizado na tela
         *  (LaunchedEffect por trackingId) -- distinto de "eligible", que so significa que o
         *  engine encontrou uma recomendacao, nao que o usuario chegou a ve-la. */
        fun registrarRecomendacaoMostrada() {
            val decisao = _recommendationDecision.value ?: return
            if (!recommendationShownTrackingIds.add(decisao.trackingId)) return
            recommendationAnalyticsTracker.track(
                decisao.toAnalyticsPayload(RecommendationAnalyticsEventName.SHOWN, diagnosticId = recommendationDiagnosticId),
            )
        }

        /** Chamada quando o usuario interage com o card (expande o motivo). */
        fun registrarRecomendacaoClicada() {
            val decisao = _recommendationDecision.value ?: return
            if (!recommendationClickedTrackingIds.add(decisao.trackingId)) return
            recommendationAnalyticsTracker.track(
                decisao.toAnalyticsPayload(RecommendationAnalyticsEventName.CLICKED, diagnosticId = recommendationDiagnosticId),
            )
        }

        /** Feedback explicito do usuario (util / nao util / ocultar). Persiste no historico
         *  (Room, #812) -- influencia a proxima recomendacao via cooldown/penalizacao de
         *  score do RecommendationEngine. "Ocultar" remove o card da tela imediatamente;
         *  util/nao util mantem o card visivel com o feedback registrado. */
        fun registrarFeedbackRecomendacao(feedback: RecommendationFeedbackType) {
            val decisao = _recommendationDecision.value ?: return
            _recommendationFeedback.value = feedback
            if (feedback == RecommendationFeedbackType.HIDE) {
                _recommendationDecision.value = null
            }
            viewModelScope.launch {
                recommendationDecisionCoordinator.registrarFeedback(decisao.trackingId, feedback)
            }
            recommendationAnalyticsTracker.track(
                decisao.toAnalyticsPayload(
                    RecommendationAnalyticsEventName.FEEDBACK,
                    diagnosticId = recommendationDiagnosticId,
                    feedback = feedback,
                ),
            )
        }

        /**
         * Converte MovelSnapshot (coreTelephony) em AiMovelInfo (featureDiagnostico).
         * Mapeamento direto; cellId/tac viram String (JSON-safe; Long pode estourar
         * Number.MAX_SAFE_INTEGER em runtimes JS — Cloudflare Worker e JS).
         */
        private fun mapMovelSnapshotToAi(snap: MovelSnapshot): AiMovelInfo =
            AiMovelInfo(
                operadora = snap.operadora,
                tecnologia = snap.tecnologia,
                rsrpDbm = snap.rsrpDbm,
                rsrqDb = snap.rsrqDb,
                sinrDb = snap.sinrDb,
                ecnoDb = snap.ecnoDb,
                bandaMovel = snap.bandaMovel,
                cellId = snap.cellId?.toString(),
                mcc = snap.mcc,
                mnc = snap.mnc,
                tac = snap.tac?.toString(),
                roaming = snap.roaming,
            )

        private fun classificarCoerenciaDns(
            melhorProvedor: String?,
            provedorAtivo: String?,
        ): String {
            if (melhorProvedor.isNullOrBlank()) return "indeterminado"
            if (provedorAtivo.isNullOrBlank()) return "semReferencia"
            return if (melhorProvedor.equals(provedorAtivo, ignoreCase = true)) "coerente" else "divergente"
        }

        private fun inferirProvedorAtivoDns(
            privateDnsHostname: String?,
            dnsServidores: List<String>,
        ): String? {
            val hostname = privateDnsHostname?.lowercase().orEmpty()
            if (hostname.contains("cloudflare")) return "cloudflare"
            if (hostname.contains("dns.google") || hostname.contains("google")) return "google"
            if (hostname.contains("quad9")) return "quad9"
            if (hostname.contains("opendns")) return "opendns"
            if (hostname.contains("adguard")) return "adguard"
            val ips = dnsServidores.map { it.trim() }.filter { it.isNotBlank() }
            if (ips.any { it == "1.1.1.1" || it == "1.0.0.1" }) return "cloudflare"
            if (ips.any { it == "8.8.8.8" || it == "8.8.4.4" }) return "google"
            if (ips.any { it == "9.9.9.9" || it == "149.112.112.112" }) return "quad9"
            if (ips.any { it == "208.67.222.222" || it == "208.67.220.220" }) return "opendns"
            if (ips.any { it == "94.140.14.14" || it == "94.140.15.15" }) return "adguard"
            return null
        }

        private suspend fun buscarLocalizacaoServidor() =
            withContext(dispatchers.io) {
                localizacaoServidor.value = UiState.Loading
                try {
                    val connection =
                        URL("https://speed.cloudflare.com/meta")
                            .openConnection() as HttpURLConnection
                    connection.connectTimeout = 6_000
                    connection.readTimeout = 6_000
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val json = JSONObject(body)
                    val cidade = json.optString("city").ifBlank { null }
                    val codigoPais = json.optString("country").ifBlank { null }
                    val local = cidade ?: codigoPais?.let { nomePaisPtBr(it) }
                    // Se local e nulo (JSON sem city/country), exibe "Cloudflare" sem cidade
                    localizacaoServidor.value = UiState.Success(if (local != null) "Cloudflare · $local" else "Cloudflare")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Falha de rede ou parse — expoe o estado de erro para a UI
                    localizacaoServidor.value = UiState.Error("Servidor indisponivel")
                }
            }

        private fun nomePaisPtBr(codigo: String): String =
            when (codigo.uppercase()) {
                "BR" -> "Brasil"
                "US" -> "Estados Unidos"
                "AR" -> "Argentina"
                "CL" -> "Chile"
                "CO" -> "Colombia"
                "PE" -> "Peru"
                "UY" -> "Uruguai"
                "PT" -> "Portugal"
                "ES" -> "Espanha"
                "GB" -> "Reino Unido"
                "DE" -> "Alemanha"
                "FR" -> "Franca"
                "JP" -> "Japao"
                "CN" -> "China"
                else -> codigo
            }

        override fun onCleared() {
            super.onCleared()
            observadorHistorico.cancel()
            monitorRede.encerrar()
            // bancoDados e injetado como @Singleton (Hilt) — compartilhado com
            // SpeedtestPersistenceCoordinator e outros ViewModels, e vive por todo
            // o processo do app. Fecha-lo aqui derrubava o Room pra sempre assim que
            // este ViewModel era destruido (ex.: recriacao de Activity), causando
            // falha silenciosa de persistencia em qualquer insert/query subsequente
            // (issues #388/#389/#390 — Historico vazio, grafico da Home preso no
            // placeholder e diagnostico "Inconclusivo" mesmo com teste completo).
        }

        // -------------------------------------------------------------------------
        // Helper: InternetDiagnosticInput a partir da fonte mais fresca disponivel.
        //
        // Ordem de preferencia:
        //  1. executorSpeedtest.snapshotFlow.value.resultado — atualizado imediatamente
        //     quando o speedtest termina, sem depender do commit no banco de dados.
        //  2. bancoDados.medicaoDao().observarUltimas(1) — fallback para sessoes onde
        //     nenhum speedtest foi rodado (ex.: app reaberto com historico gravado).
        //
        // Ler apenas do BD causava race condition: o save ao BD acontece numa coroutine
        // separada (observer do snapshotFlow) e pode nao ter terminado antes de
        // iniciarRotinasNaoSpeedtest() / iniciarDiagnostico() rodarem.
        // -------------------------------------------------------------------------
        private suspend fun speedtestResultToInternetInput(): InternetDiagnosticInput? {
            val resultado = executorSpeedtest.snapshotFlow.value.resultado
            if (resultado != null) {
                return InternetDiagnosticInput(
                    downloadMbps = resultado.downloadMbps,
                    uploadMbps = resultado.uploadMbps,
                    latencyMs = resultado.latenciaMs,
                    jitterMs = resultado.jitterMs,
                    perdaPercentual = resultado.perdaPercentual,
                    bufferbloatMs = resultado.bufferbloatMs,
                    packetLossSource = resultado.packetLossSource,
                    perdaConfianca = resultado.perdaConfianca,
                )
            }
            return bancoDados.medicaoDao().observarUltimas(1).first().firstOrNull()?.let {
                InternetDiagnosticInput(
                    downloadMbps = it.downloadMbps,
                    uploadMbps = it.uploadMbps,
                    latencyMs = it.latencyMs,
                    jitterMs = it.jitterMs,
                    perdaPercentual = it.perdaPercentual,
                    bufferbloatMs = it.bufferbloatMs,
                    packetLossSource = it.packetLossSource,
                    perdaConfianca = it.perdaConfianca.paraConfiancaAmostral(),
                )
            }
        }

        /**
         * GH#1228 (Fase 3, executionId/rulesVersion) — mesma resolucao de fonte de
         * [speedtestResultToInternetInput] (resultado em memoria do speedtest atual, senao a
         * ultima medicao persistida), devolvendo o `executionId` correspondente em vez das
         * metricas. Garante que o [DiagnosticInput] construido a partir de qualquer uma das
         * duas fontes carregue a MESMA identidade de execucao que alimentou o `internetInput`
         * — nunca gera um id novo aqui, nunca deixa vazio quando a fonte tem um valor real.
         */
        private suspend fun executionIdAtual(): String {
            executorSpeedtest.snapshotFlow.value.resultado
                ?.let { return it.executionId }
            return bancoDados
                .medicaoDao()
                .observarUltimas(1)
                .first()
                .firstOrNull()
                ?.executionId ?: ""
        }

        // -------------------------------------------------------------------------
        // Data classes de UiState agrupado — usadas pelos flows combinados acima.
        // Imutaveis e comparaveis por valor (data class), permitindo que
        // distinctUntilChanged() filtre emissoes redundantes corretamente.
        // -------------------------------------------------------------------------

        data class PreferenciasModemUiState(
            val host: String? = null,
            val username: String = "userAdmin",
            val password: String = "",
            val permanecerConectado: Boolean = false,
            // GH#530 — BSSID em que a sessao "manter conectado" foi estabelecida.
            val gatewaySessionBssid: String? = null,
        )

        data class PreferenciasNotificacaoUiState(
            val latenciaAtiva: Boolean = true,
            val dnsAtiva: Boolean = true,
            val rssiAtiva: Boolean = true,
            val semInternetAtiva: Boolean = true,
        )

        data class PreferenciasUiUiState(
            val temaSelecionado: String = "sistema",
            val analiseAvancada: Boolean = false,
        )

        data class PreferenciasPerfilProvedorUiState(
            val nomeUsuario: String = "",
            val fotoUriUsuario: String? = null,
            val operadora: String = "",
            val planoInternet: String = "",
            val regiao: String = "",
            val estadoUf: String = "",
            val cidadeNome: String = "",
            val ispConfirmado: Boolean = false,
            val limiteAlertaMbps: Int = 0,
            val velocidadeContratadaDownMbps: Int = 0,
            val velocidadeContratadaUpMbps: Int = 0,
        )

        data class PreferenciasSpeedtestMovelUiState(
            val permiteHeavy: Boolean = false,
            val mbConsumidosMes: Long = 0L,
        )
    }

// #838 — extraida de reiniciarSuite() para ser testavel sem Robolectric/Hilt (MainViewModel
// tem dependencias demais para instanciar em teste unitario puro). NAO decide sozinha se o
// speedtest deve rodar — so se o gate de confirmacao de rede movel deve interceptar o inicio.
internal fun deveSolicitarConfirmacaoRedeMovel(
    metered: Boolean,
    modo: ModoSpeedtest,
    jaConfirmadoRedeMovel: Boolean,
): Boolean = !jaConfirmadoRedeMovel && modo != ModoSpeedtest.fast && metered

// #980 (Fase 2B) — traduz o papel canonico do motor de topologia unificado
// (TopologiaRedeEngine, Fase 2A/#979) pro enum que a Home ja usa. SISTEMA_MESH_PROVAVEL vira
// WifiMesh (nunca WifiRouter): so o papel ROTEADOR aciona o fluxo de login do modem
// (GatewayConnectionSheet, ver Inicio2Screen.onGatewayTap) — um no so "provavelmente" mesh nao
// pode acionar esse fluxo como se fosse um roteador confirmado.
internal fun papelParaConnectionNodeType(papel: PapelTopologia): ConnectionNodeType =
    when (papel) {
        PapelTopologia.ROTEADOR -> ConnectionNodeType.WifiRouter
        PapelTopologia.NO_MESH -> ConnectionNodeType.WifiMesh
        PapelTopologia.SISTEMA_MESH_PROVAVEL -> ConnectionNodeType.WifiMesh
        PapelTopologia.REPETIDOR -> ConnectionNodeType.WifiExtender
        PapelTopologia.PONTO_DE_ACESSO -> ConnectionNodeType.Unknown
        PapelTopologia.DESCONHECIDO -> ConnectionNodeType.Unknown
    }

// #983 (Fase 4) — extraida para ser testavel sem Hilt/Android; correlaciona cada dispositivo
// do scan LAN com a topologia Wi-Fi classificada e com a leitura direta do gateway. Sem scan
// Wi-Fi ou credencial de gateway (ambos vazios), cai no comportamento anterior a Fase 4 —
// todo dispositivo mapeado pra SEM_MATCH (ver correlacionarDispositivoComTopologia).
internal fun construirCorrelacoesTopologia(
    dispositivos: List<io.signallq.app.feature.devices.DispositivoRede>,
    redesWifiClassificadas: List<Pair<RedeVizinha, ClassificacaoTopologia>>,
    clientesGateway: List<ClientSnapshot>,
): Map<String, ResultadoCorrelacaoTopologia> =
    dispositivos.associate { dispositivo ->
        dispositivo.id to
            correlacionarDispositivoComTopologia(
                dispositivo = dispositivo,
                clientesGateway = clientesGateway,
                redesWifiClassificadas = redesWifiClassificadas,
            )
    }

// SIG-279 — enums identicos por nome (wifi/movel/ethernet/desconectado/desconhecido),
// mapeamento explicito para nao acoplar core/network a feature/diagnostico.
private fun EstadoConexao.paraConnectionType(): ConnectionType =
    when (this) {
        EstadoConexao.wifi -> ConnectionType.wifi
        EstadoConexao.movel -> ConnectionType.mobile
        EstadoConexao.ethernet -> ConnectionType.ethernet
        EstadoConexao.desconectado -> ConnectionType.desconectado
        EstadoConexao.desconhecido -> ConnectionType.desconhecido
    }

// -------------------------------------------------------------------------
// NDS-02k PR2 (issue #1746, ADR-017) -- parte pura de [MainViewModel.analisarProblema],
// extraida como funcao de nivel de arquivo para ser testavel sem instanciar o ViewModel
// inteiro (25+ dependencias Hilt/Android incompativeis com JVM test puro -- mesmo motivo
// documentado em MainViewModelHistoricoTest/MainViewModelLocalDeviceTest).
//
// Quando `nds_live_enabled` esta ligada, [relatorio] ja veio do NDS --
// `DiagnosticOrchestrator.executarProtegido` troca `RemoteDiagnosticRepository.evaluateShadow`
// por `NdsDiagnosticRepository.evaluate` na MESMA execucao que produziu este relatorio, ANTES
// de `analisarProblema` ser chamado (snap.relatorio ja e o resultado). A narrativa do modulo
// `ai` do proprio NDS (`tituloAmigavel`/`resumoTecnicoTraduzido`) ja fica embutida em
// `relatorio.decisao.titulo`/`mensagemUsuario` por
// `io.signallq.app.core.nds.NdsDiagnosticsResponseMapper.toDiagnosticReport`.
//
// ACHADO explicito (item 3 do escopo da PR) -- `io.signallq.app.core.nds.NdsClient` so expoe
// `POST /v1/diagnostics/evaluate`. Nao existe endpoint equivalente a
// `AiDiagnosisRepository.explainDiagnosis`: o worker `ai-diagnosis-worker` devolve um schema
// v2/v3 completo (perguntasContextuais, hipotesesDescartadas, classificacaoTecnica por
// dimensao, metadados detalhados de modeloIa) que o NDS nao produz -- o modulo `ai` do NDS
// devolve so `tituloAmigavel`/`resumoTecnicoTraduzido` (ver `NdsAiResult`/`NdsAiExplanation`
// em `core/nds/NdsModuleResults.kt`). Por isso, com a flag ligada, esta funcao NAO chama o
// `NdsClient` de novo (seria um round-trip redundante -- o relatorio ja foi avaliado pelo NDS
// na chamada que originou [relatorio]) e NAO chama o worker legado `ai-diagnosis-worker` --
// deriva o resultado direto de [relatorio] via `AiFallbackFactory.fromLocal`, o mesmo mapeador
// que ja existe para o fallback local do caminho antigo. `origem="local"` no
// `AnalisadorState.Resultado` resultante segue tecnicamente correto (nenhuma chamada ao
// `ai-diagnosis-worker` aconteceu), mas quando [relatorio] e REMOTE (NDS) o texto exibido ja
// carrega a narrativa que o proprio NDS gerou -- nao e um fallback generico "sem IA". Essa
// divergencia de granularidade entre os dois motores (explicacao rica vs explicacao enxuta)
// fica registrada aqui; unificar os dois contratos (se um dia fizer sentido) nao e desta fatia.
//
// @return `null` quando a flag esta desligada -- o chamador segue o caminho antigo
//   (`AiDiagnosisRepository.explainDiagnosis`), comportamento IDENTICO ao anterior a esta PR.
// -------------------------------------------------------------------------
internal fun resolverResultadoAnaliseViaNds(
    ndsLiveEnabled: Boolean,
    relatorio: DiagnosticReport,
    problema: String?,
): AnalisadorState.Resultado? {
    if (!ndsLiveEnabled) return null
    val local = AiFallbackFactory.fromLocal(relatorio)
    return AnalisadorState.Resultado(
        texto = local.textoLaudo.ifBlank { local.resumo },
        origem = "local",
        acoes = local.acoesRecomendadas,
        titulo = local.titulo,
        resumo = local.resumo,
        problemaRelatado = problema,
        confianca = relatorio.rotuloConfianca,
    )
}
