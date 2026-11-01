package io.signallq.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import io.signallq.app.ads.AdsFlagsManager
import io.signallq.app.core.datastore.PreferenciasAppRepository
import io.signallq.app.core.featureflags.FeatureFlagProvider
import io.signallq.app.core.network.AnalyticsTracker
import io.signallq.app.di.ApplicationScope
import io.signallq.app.featureflags.FeatureFlagManager
import io.signallq.app.logging.ReleaseTree
import io.signallq.app.monitoramento.AdminSyncScheduler
import io.signallq.app.notificacao.SignallQNotificationHelper
import io.signallq.app.speedtest.SpeedtestPersistenceCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SignallQApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var speedtestPersistenceCoordinator: SpeedtestPersistenceCoordinator

    @Inject
    lateinit var featureFlagManager: FeatureFlagManager

    @Inject
    lateinit var adsFlagsManager: AdsFlagsManager

    // GH#1477 (Epico #1347) -- fundacao de feature flags do Consumer via Firebase
    // Remote Config. Distinto do featureFlagManager legado (HTTP/SIG-13) acima.
    @Inject
    lateinit var consumerFeatureFlagProvider: FeatureFlagProvider

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    @Inject
    lateinit var firebaseAnalytics: FirebaseAnalytics

    @Inject
    lateinit var preferenciasAppRepository: PreferenciasAppRepository

    // Reusar o scope singleton provido pelo Hilt — elimina scope duplicado.
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            // Desabilita coleta de crashes em debug para não poluir dados de produção no Firebase.
            // runCatching: Firebase não é inicializado em testes com Robolectric — evita crash nos 37 unit tests.
            runCatching {
                FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
            }
        } else {
            Timber.plant(ReleaseTree(analyticsTracker))
        }

        // ADR-020: Inicialização do Firebase App Check (Play Integrity em release, Debug provider em debug).
        runCatching {
            val appCheck =
                com.google.firebase.appcheck.FirebaseAppCheck
                    .getInstance()
            if (BuildConfig.DEBUG) {
                runCatching {
                    val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val factory = factoryClass.getMethod("getInstance").invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                    appCheck.installAppCheckProviderFactory(factory)
                }.onFailure {
                    Timber.w(it, "DebugAppCheckProviderFactory não encontrado no classpath")
                }
            } else {
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
                        .getInstance(),
                )
            }
        }.onFailure { erro ->
            Timber.w(erro, "Firebase App Check não inicializado (esperado em testes unitários)")
        }

        // Canais de notificacao: criados em IO para nao bloquear o main thread no startup.
        // O MonitoramentoWorker so dispara apos rede disponivel (constraint CONNECTED) e
        // intervalo minimo de 30 min — canais estarao criados muito antes da primeira notificacao.
        applicationScope.launch(Dispatchers.IO) {
            SignallQNotificationHelper.criarCanais(this@SignallQApplication)
        }

        // Inicia o coordinator singleton que persiste resultados do speedtest no Room.
        // Centraliza a lógica que antes estava duplicada entre ViewModels de diagnóstico.
        speedtestPersistenceCoordinator.iniciar()

        // Agenda sync retroativo de historico para o painel admin.
        // Movido para Default: WorkManager.getInstance() pode inicializar banco interno
        // (Room) na primeira execucao, bloqueando o main thread desnecessariamente.
        applicationScope.launch(Dispatchers.Default) {
            AdminSyncScheduler.agendar(this@SignallQApplication)
        }

        // Migra credenciais do modem de plaintext para EncryptedSharedPreferences.
        // Roda uma vez — nas execucoes seguintes o flag "migrado" curto-circuita.
        applicationScope.launch(Dispatchers.IO) {
            preferenciasAppRepository.migrarCredenciaisSeNecessario()
        }

        // Sincroniza feature flags do worker em background.
        // Nao bloqueia o startup — UI usa fallback (todos enabled) ate o fetch completar.
        featureFlagManager.inicializar(applicationScope)

        // Toggle remoto de anuncios nativos (issue #555) via Firebase Remote Config.
        // O bloqueio de build permite publicar uma trilha sem anuncios, independentemente
        // de valores eventualmente ativos no Remote Config.
        if (BuildConfig.ADS_ENABLED) {
            adsFlagsManager.inicializar(applicationScope)
        }

        // Fundacao de feature flags do Consumer (GH#1477, Epico #1347). Nao bloqueia o
        // startup — defaults locais do catalogo ja ficam disponiveis desde a construcao
        // do provider, o fetch so atualiza em background.
        applicationScope.launch(Dispatchers.IO) {
            consumerFeatureFlagProvider.refresh()
        }

        // LGPD: desativa coleta Firebase por padrao. Reativa apenas apos consentimento explícito.
        // setAnalyticsCollectionEnabled deve ser chamado antes do collect para garantir ordem:
        // desabilita primeiro, depois reativa condicionalmente via consentimento.
        firebaseAnalytics.setAnalyticsCollectionEnabled(false)
        applicationScope.launch {
            preferenciasAppRepository.consentimentoLgpdFlow.collect { consentimento ->
                firebaseAnalytics.setAnalyticsCollectionEnabled(consentimento == true)
            }
        }
    }

    // GH#1684 -- onTerminate() nao roda em producao real (a doc da API e explicita: e so pra
    // ambiente de teste/emulador), mas o Robolectric CHAMA isso ao desmontar a Application
    // simulada entre metodos de teste do :app. Cancelar aqui interrompe o quanto antes as
    // coroutines soltas em applicationScope (agendamentos, flags, migracao de credenciais, o
    // collect infinito do consentimento LGPD) em vez de deixa-las rodando no Dispatchers.Default
    // compartilhado por toda a JVM do worker, competindo com o proximo teste.
    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
}
