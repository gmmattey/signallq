package io.signallq.app.di

import android.content.Context
import android.net.wifi.WifiManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.signallq.app.BuildConfig
import io.signallq.app.ads.AdsRemoteConfigRepository
import io.signallq.app.analytics.FirebaseAnalyticsHelper
import io.signallq.app.analytics.FirebaseAnalyticsTracker
import io.signallq.app.analytics.FirebaseRecommendationAnalyticsTracker
import io.signallq.app.analytics.distributionChannel
import io.signallq.app.core.database.CoreDatabaseModulo
import io.signallq.app.core.database.MedicaoDao
import io.signallq.app.core.database.SignallQDatabase
import io.signallq.app.core.database.chat.ChatSessionDao
import io.signallq.app.core.database.connectivity.ConnectivityDiagnosisHistoryDao
import io.signallq.app.core.database.wificasa.MapeamentoWifiDao
import io.signallq.app.core.datastore.FeatureFlagStore
import io.signallq.app.core.datastore.PreferenciasAppRepository
import io.signallq.app.core.featureflags.FeatureFlagCatalog
import io.signallq.app.core.featureflags.FeatureFlagProvider
import io.signallq.app.core.featureflags.FeatureFlagsModulo
import io.signallq.app.core.network.AnalyticsHelper
import io.signallq.app.core.network.AnalyticsTracker
import io.signallq.app.core.network.CoreNetworkModulo
import io.signallq.app.core.network.DefaultDispatcherProvider
import io.signallq.app.core.network.DispatcherProvider
import io.signallq.app.core.network.MonitorRede
import io.signallq.app.core.network.NetworkCapabilitiesProvider
import io.signallq.app.core.network.connectivity.ConnectivityDiagnosisRunner
import io.signallq.app.core.network.connectivity.ConnectivityDiagnosisSource
import io.signallq.app.core.network.wifi.ScannerRedesWifi
import io.signallq.app.core.permissions.CorePermissionsModulo
import io.signallq.app.core.permissions.GerenciadorPermissoesRede
import io.signallq.app.core.recommendation.analytics.RecommendationAnalyticsTracker
import io.signallq.app.core.telephony.CoreTelephonyModulo
import io.signallq.app.core.telephony.MonitorTelephony
import io.signallq.app.feature.devices.FeatureDevicesModulo
import io.signallq.app.feature.devices.ScannerDispositivos
import io.signallq.app.feature.diagnostico.DiagnosticOrchestrator
import io.signallq.app.feature.dns.BenchmarkDns
import io.signallq.app.feature.dns.FeatureDnsModulo
import io.signallq.app.feature.fibra.ExecutorFibra
import io.signallq.app.feature.fibra.FeatureFibraModulo
import io.signallq.app.feature.speedtest.ExecutorSpeedtest
import io.signallq.app.feature.speedtest.FeatureSpeedtestModulo
import io.signallq.app.feature.speedtest.connectivity.ConnectivityDiagnosisRepository
import io.signallq.app.feature.speedtest.connectivity.ConnectivityDiagnosisRepositoryImpl
import io.signallq.app.feature.wifi.FeatureWifiModulo
import io.signallq.app.featureflags.FeatureFlagManager
import io.signallq.app.featureflags.FeatureFlagRepository
import io.signallq.app.network.IspInfoCache
import io.signallq.app.speedtest.SpeedtestPersistenceCoordinator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import io.signallq.app.core.network.FeatureFlagProvider as LegacyHttpFeatureFlagProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * GH#1684 -- sem handler, uma falha em qualquer coroutine solta em [ApplicationScope] (ex.:
 * `AdminSyncScheduler.agendar`, `migrarCredenciaisSeNecessario`, `consumerFeatureFlagProvider.refresh`
 * em `SignallQApplication.onCreate`) sobe pro `Thread.UncaughtExceptionHandler` padrao da JVM/
 * Android -- em producao isso derruba o processo (SupervisorJob isola os filhos entre si, mas nao
 * engole excecao sem handler); em unit test com Robolectric, `RobolectricTestRunner` recria a
 * `SignallQApplication` (via Hilt) e reroda esse `onCreate()` a CADA metodo de teste do :app, sem
 * cancelar o [CoroutineScope] anterior -- como o [Dispatchers.Default] eh um pool real,
 * compartilhado por toda a JVM do worker de teste, uma falha tardia de uma execucao anterior surge
 * de forma assincrona durante um teste completamente diferente. Sem handler no contexto, essa
 * excecao e roteada pelo `kotlinx.coroutines.test.internal.ExceptionCollectorAsService`
 * (registrado via ServiceLoader como `CoroutineExceptionHandler` global do processo) e reaparece
 * como `UncaughtExceptionsBeforeTest` no proximo `runTest {}` de QUALQUER classe da suite --
 * vitima aleatoria, sem relacao com quem realmente vazou. Logar aqui fecha os dois problemas.
 *
 * ATENCAO (bloqueio 1 da revisao do Caio na PR #1688): `Timber.e` chega ao Crashlytics via
 * `ReleaseTree` e, por ser `priority >= Log.ERROR`, tambem chama
 * `analyticsTracker.registrarFeatureCrash(...)` -- que e o [io.signallq.app.analytics.
 * CompositeAnalyticsTracker], cujo `enviarEvento` despacha OUTRO `applicationScope.launch`. Sem
 * contencao no corpo desse segundo launch, uma falha real (ex.: WorkManager sem guarda em
 * `AdminSyncScheduler`) reentraria neste mesmo handler, girando em ciclo sem limite. A contencao
 * que fecha esse ciclo fica em `CompositeAnalyticsTracker.enviarEvento` (runCatching + Timber.w),
 * nao aqui -- os dois pontos sao complementares, nao intercambiaveis; nao remova um assumindo que
 * o outro basta.
 */
private val applicationScopeExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Excecao nao tratada em coroutine de ApplicationScope")
    }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideBancoDados(
        @ApplicationContext ctx: Context,
    ): SignallQDatabase = CoreDatabaseModulo.criarBanco(ctx)

    @Provides
    @Singleton
    fun providePreferenciasAppRepository(
        @ApplicationContext ctx: Context,
        dispatchers: DispatcherProvider,
    ): PreferenciasAppRepository = PreferenciasAppRepository(ctx, dispatchers.io)

    @Provides
    @Singleton
    fun provideMonitorRede(
        @ApplicationContext ctx: Context,
    ): MonitorRede = CoreNetworkModulo.criarMonitorRede(ctx)

    @Provides
    @Singleton
    fun provideNetworkCapabilitiesProvider(
        @ApplicationContext ctx: Context,
    ): NetworkCapabilitiesProvider = CoreNetworkModulo.criarNetworkCapabilitiesProvider(ctx)

    @Provides
    @Singleton
    fun provideGerenciadorPermissoes(
        @ApplicationContext ctx: Context,
    ): GerenciadorPermissoesRede = CorePermissionsModulo.criarGerenciadorPermissoesRede(ctx)

    /**
     * Cliente HTTP para ScannerDispositivosAndroid (SSDP local).
     *
     * Timeout de 2s adequado para descoberta de dispositivos na LAN — redes locais
     * respondem em <1s. Timeout maior aumentaria o tempo total do scan sem benefício.
     */
    @Provides
    @Singleton
    @Named("upnpClient")
    fun provideUpnpOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()

    /**
     * Cliente HTTP para UpnpIgdDiscovery (IGD/gateway discovery).
     *
     * Timeout de 5s necessário para redes ADSL/4G instáveis onde o roteador pode
     * demorar mais para responder ao fetch do XML de descrição UPnP. Reduzir este
     * valor causa regressão em discovery em redes lentas (banda < 5 Mbps ou alta latência).
     */
    @Provides
    @Singleton
    @Named("upnpIgdClient")
    fun provideUpnpIgdOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideScannerDispositivos(
        @ApplicationContext ctx: Context,
        @Named("upnpClient") okHttpClient: OkHttpClient,
    ): ScannerDispositivos = FeatureDevicesModulo.criarScannerDispositivos(ctx, okHttpClient)

    @Provides
    @Singleton
    fun provideBenchmarkDns(): BenchmarkDns = FeatureDnsModulo.criarBenchmarkDns()

    @Provides
    @Singleton
    fun provideExecutorSpeedtest(networkCapabilitiesProvider: NetworkCapabilitiesProvider): ExecutorSpeedtest =
        FeatureSpeedtestModulo.criarExecutorSpeedtest(
            isMobile = networkCapabilitiesProvider.isMeteredNetwork(),
            // GH#1118: mesmo worker dedicado que a tela Jogos usa (GH#935) — as duas telas
            // passam a medir latência base contra a mesma fonte confiável.
            latencyProbeUrl = BuildConfig.GAME_LATENCY_PROBE_URL,
        )

    @Provides
    @Singleton
    fun provideScannerRedesWifi(
        @ApplicationContext ctx: Context,
    ): ScannerRedesWifi = FeatureWifiModulo.criarScannerRedesWifi(ctx)

    @Provides
    @Singleton
    fun provideExecutorFibra(): ExecutorFibra = FeatureFibraModulo.criarExecutor()

    @Provides
    @Singleton
    fun provideMonitorTelephony(
        @ApplicationContext ctx: Context,
    ): MonitorTelephony = CoreTelephonyModulo.criarMonitorTelephony(ctx)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + applicationScopeExceptionHandler)

    /**
     * URL base do signallq-admin-worker para ingest de telemetria.
     * Vem do BuildConfig — nunca hardcoded aqui.
     */
    @Provides
    @Singleton
    @Named("adminIngestUrl")
    fun provideAdminIngestUrl(): String = BuildConfig.ADMIN_INGEST_URL

    /**
     * Chave de autenticacao para /ingest/ do signallq-admin-worker.
     * Scope limitado: so pode escrever em /ingest/. Nao e o ADMIN_SECRET do painel.
     * Vem do BuildConfig — lido de local.properties em dev, CI inject em release.
     */
    @Provides
    @Singleton
    @Named("adminIngestKey")
    fun provideAdminIngestKey(): String = BuildConfig.ADMIN_INGEST_KEY

    /**
     * Canal de distribuicao desta instalacao ("play_store"/"sideload"/etc.) —
     * GH#1445 (parte de #952). `distributionChannel()` vive em
     * `io.signallq.app.analytics` (`:app`); expor via `@Named` permite que
     * modulos `:feature:*` (que nao podem depender de `:app`) consumam o mesmo
     * calculo sem duplica-lo — usado hoje pela segmentacao de rollout do shadow
     * mode ([io.signallq.app.feature.diagnostico.remote.DiagnosticDivergenceReporter]).
     */
    @Provides
    @Named("appDistributionChannel")
    fun provideAppDistributionChannel(
        @ApplicationContext context: Context,
    ): String = distributionChannel(context)

    /**
     * Repository para busca e persistencia de feature flags remotas.
     * Usa ADMIN_INGEST_URL como base — o Admin Worker expoe /flags (SIG-13) e /feature-flags (legado).
     */
    @Provides
    @Singleton
    fun provideFeatureFlagRepository(
        store: FeatureFlagStore,
        @Named("adminIngestUrl") adminIngestUrl: String,
    ): FeatureFlagRepository =
        FeatureFlagRepository(
            adminWorkerBaseUrl = adminIngestUrl,
            prefs = store,
        )

    /**
     * Expoe PreferenciasAppRepository como FeatureFlagStore.
     * Evita criar uma segunda instancia de PreferenciasAppRepository so para flags.
     */
    @Provides
    @Singleton
    fun provideFeatureFlagStore(prefs: PreferenciasAppRepository): FeatureFlagStore = prefs

    /** Catalogo canonico de feature flags do Consumer (issue #1477, Epico #1347) --
     *  carregado uma vez do classpath de `:core:featureflags`. */
    @Provides
    @Singleton
    fun provideFeatureFlagCatalog(): FeatureFlagCatalog = FeatureFlagsModulo.criarCatalogo()

    /**
     * Unico ponto de acesso a feature flags do Consumer via Firebase Remote Config
     * (issue #1477, Epico #1347). Reusa a MESMA instancia de [FirebaseRemoteConfig] do
     * toggle de anuncios (issue #555) -- um unico template remoto, chaves diferentes
     * (`consumer.*`/`shared.*`/`app.*` aqui, `ads_native_*` la), nao dois Remote Config
     * separados.
     */
    @Provides
    @Singleton
    fun provideConsumerFeatureFlagProvider(
        remoteConfig: Lazy<FirebaseRemoteConfig>,
        catalog: FeatureFlagCatalog,
    ): FeatureFlagProvider = FeatureFlagsModulo.criarProvider(remoteConfigProvider = { remoteConfig.get() }, catalog = catalog)

    /**
     * Expoe [FeatureFlagManager] como o [LegacyHttpFeatureFlagProvider] (HTTP/SIG-13,
     * `io.signallq.app.core.network.FeatureFlagProvider`) para consumidores que ainda dependem
     * desse contrato -- hoje: [io.signallq.app.ui.OperadoraDirectoryResolver] (issue #1464).
     * Binding removido por engano no PR #1560 (migracao do #1497) por nao ter consumidor
     * conhecido naquele momento; o PR #1561, em paralelo, adicionou este novo consumidor sem
     * ver a remocao -- achado real do PR #1560 -- ver issue #1562.
     */
    @Provides
    @Singleton
    fun provideLegacyHttpFeatureFlagProvider(manager: FeatureFlagManager): LegacyHttpFeatureFlagProvider = manager

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(
        @ApplicationContext ctx: Context,
    ): FirebaseAnalytics = FirebaseAnalytics.getInstance(ctx)

    /**
     * Remote Config compartilhado -- toggle de anuncios nativos (issue #555) E feature
     * flags do Consumer (issue #1477, Epico #1347) leem da MESMA instancia, cada um com
     * seu proprio conjunto de chaves. Defaults dos dois catalogos sao mesclados num unico
     * `setDefaultsAsync` -- chamar duas vezes SUBSTITUIRIA o mapa de defaults inteiro em
     * vez de somar, apagando o outro conjunto de chaves.
     *
     * [AdsRemoteConfigRepository]/[io.signallq.app.core.featureflags.RemoteConfigFeatureFlagProvider]
     * injetam isso como `dagger.Lazy<FirebaseRemoteConfig>` (nao `kotlin.Lazy` -- variancia
     * do tipo Kotlin gera wildcard incompativel com o binding do Dagger), entao este metodo
     * so roda no primeiro `.get()` de verdade. `FirebaseRemoteConfig.getInstance()` exige
     * `FirebaseApp` ja inicializado, o que nao e verdade em testes Robolectric -- construir
     * isso eagerly aqui (sem o Lazy do Dagger) derrubava QUALQUER teste que instanciasse a
     * Application real, nao so os testes de ads.
     *
     * Fetch minimo de 0 em debug para iterar rapido testando o painel do Firebase
     * Console; em release o SDK ja aplica seu proprio intervalo minimo padrao
     * (throttling), entao nao precisa de config adicional aqui.
     */
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(catalog: FeatureFlagCatalog): FirebaseRemoteConfig {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings: FirebaseRemoteConfigSettings =
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3_600L
            }
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(AdsRemoteConfigRepository.DEFAULTS_REMOTE_CONFIG + catalog.toRemoteConfigDefaultsMap())
        return remoteConfig
    }

    @Provides
    @Singleton
    fun provideAdsRemoteConfigRepository(remoteConfig: Lazy<FirebaseRemoteConfig>): AdsRemoteConfigRepository =
        AdsRemoteConfigRepository(remoteConfig)

    @Provides
    @Singleton
    fun provideAnalyticsTracker(tracker: FirebaseAnalyticsTracker): AnalyticsTracker = tracker

    /**
     * AnalyticsHelper (SIG-155) — funil principal de engajamento. Distinto do
     * AnalyticsTracker (SIG-134/feature_used) acima; ambos compartilham a mesma
     * instancia de FirebaseAnalytics provida logo abaixo.
     */
    @Provides
    @Singleton
    fun provideAnalyticsHelper(helper: FirebaseAnalyticsHelper): AnalyticsHelper = helper

    /** RecommendationAnalyticsTracker (`coreRecommendation`, issue #790/#813) -- eventos
     *  `recommendation_*` do Recommendation Engine, distintos dos dois contratos acima. */
    @Provides
    @Singleton
    fun provideRecommendationAnalyticsTracker(
        tracker: FirebaseRecommendationAnalyticsTracker,
    ): RecommendationAnalyticsTracker = tracker

    @Provides
    @Singleton
    fun provideMedicaoDao(bancoDados: SignallQDatabase): MedicaoDao = bancoDados.medicaoDao()

    @Provides
    @Singleton
    fun provideChatSessionDao(bancoDados: SignallQDatabase): ChatSessionDao = bancoDados.chatSessionDao()

    @Provides
    @Singleton
    fun provideMapeamentoWifiDao(bancoDados: SignallQDatabase): MapeamentoWifiDao = bancoDados.mapeamentoWifiDao()

    /**
     * WiFi Casa (`.agents/architecture-plan.md`) -- `WifiCasaViewModel` precisa do `WifiManager`
     * injetado (em vez de resolvido via `Context` dentro do ViewModel) para poder receber um
     * mock em teste unitário, mesmo padrão de constructor injection já usado em
     * `SinalWifiViewModelTest` (que instancia `SinalWifiViewModel` direto com um `WifiManager`
     * mockado, sem passar por Hilt).
     */
    @Provides
    @Singleton
    fun provideWifiManager(
        @ApplicationContext ctx: Context,
    ): WifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Provides
    @Singleton
    fun provideAnalyticsOutboxDao(bancoDados: SignallQDatabase): io.signallq.app.core.database.analytics.AnalyticsOutboxDao =
        bancoDados.analyticsOutboxDao()

    @Provides
    @Singleton
    fun provideConnectivityDiagnosisHistoryDao(
        bancoDados: SignallQDatabase,
    ): ConnectivityDiagnosisHistoryDao = bancoDados.connectivityDiagnosisHistoryDao()

    @Provides
    @Singleton
    fun provideConnectivityDiagnosisRunner(
        @ApplicationContext ctx: Context,
    ): ConnectivityDiagnosisRunner = ConnectivityDiagnosisRunner(ctx)

    @Provides
    @Singleton
    fun provideConnectivityDiagnosisRepository(
        runner: ConnectivityDiagnosisRunner,
        historyDao: ConnectivityDiagnosisHistoryDao,
    ): ConnectivityDiagnosisRepository = ConnectivityDiagnosisRepositoryImpl(runner, historyDao)

    // Architecture Plan "Status de conectividade ao vivo na Home" -- StatusConectividadeAoVivoCoordinator
    // injeta a interface diretamente (decisão 4.3: nunca via ConnectivityDiagnosisRepository, que
    // persistiria o polling ambiente no histórico). Nenhum consumidor pedia a interface isolada
    // até agora -- ConnectivityDiagnosisRepositoryImpl recebia o ConnectivityDiagnosisRunner
    // concreto direto (upcast implícito no site de chamada acima), sem exigir este binding.
    @Provides
    @Singleton
    fun provideConnectivityDiagnosisSource(runner: ConnectivityDiagnosisRunner): ConnectivityDiagnosisSource = runner

    @Provides
    @Singleton
    fun provideSpeedtestPersistenceCoordinator(
        executorSpeedtest: ExecutorSpeedtest,
        medicaoDao: MedicaoDao,
        monitorTelephony: MonitorTelephony,
        monitorRede: MonitorRede,
        diagnosticOrchestrator: DiagnosticOrchestrator,
        ispInfoCache: IspInfoCache,
        @ApplicationScope applicationScope: CoroutineScope,
    ): SpeedtestPersistenceCoordinator =
        SpeedtestPersistenceCoordinator(
            executorSpeedtest = executorSpeedtest,
            medicaoDao = medicaoDao,
            monitorTelephony = monitorTelephony,
            monitorRede = monitorRede,
            diagnosticOrchestrator = diagnosticOrchestrator,
            ispInfoCache = ispInfoCache,
            applicationScope = applicationScope,
        )

    @Provides
    @Singleton
    fun provideAssistChatRepository(
        impl: io.signallq.app.assist.data.FirebaseAssistChatRepository,
    ): io.signallq.app.assist.data.AssistChatRepository = impl
}
