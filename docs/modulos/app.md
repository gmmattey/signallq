---
title: "Módulo :app"
description: "Aplicação Android do SignallQ Consumer — composição de features, navegação, DI raiz e telas Compose."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:app`

- **Caminho físico:** `android/app/`
- **Namespace:** `io.signallq.app` (mesmo valor em `applicationId`)
- **Tipo:** aplicação (`com.android.application`)

## Responsabilidade

Módulo de composição do app Consumer: hospeda `SignallQApplication`, `MainActivity`, o
`MainViewModel` raiz, o grafo Hilt de nível de aplicação (`di/AppModule.kt`), a navegação
(`ui/screen/AppShell.kt`) e as telas Compose. O comportamento de cada feature (Início, Sinal,
Dispositivos, WiFi Casa, Modo gamer, Monitoramento, Ajustes, anúncios etc.) está em
[`features/`](../features/README.md), não aqui.

Não é dele: coleta de dados de rede (`:coreNetwork`, `:coreTelephony`), persistência
(`:coreDatabase`, `:coreDatastore`), regras de classificação/causa-raiz (`:core:diagnostico`),
paginação HTML→PDF (`:core:relatorio`) nem o contrato de feature flags remoto
(`:core:featureflags`). Na prática essa fronteira ainda vaza — ver Riscos.

## Dependências

### Módulos do projeto

| Módulo | Para quê |
|---|---|
| `project(":coreNetwork")` | Monitor de conectividade, contratos de gateway/dispositivo local, scan Wi-Fi, `AnalyticsTracker`, `FeatureFlagProvider` legado (SIG-13) |
| `project(":corePermissions")` | Fluxos de permissão (localização, telefonia, notificações) |
| `project(":coreDatabase")` | Room — histórico de medições, outbox de analytics |
| `project(":coreDatastore")` | `PreferenciasAppRepository` (tema, consentimento, onboarding) |
| `project(":coreTelephony")` | RSRP/RSRQ/SINR/banda quando a conexão é móvel |
| `project(":coreRecommendation")` | Motor de recomendações práticas do consumidor |
| `project(":featureHome")` | Feature Home |
| `project(":featureWifi")` | Feature Wi-Fi |
| `project(":featureDevices")` | Feature Dispositivos |
| `project(":featureDns")` | Feature DNS |
| `project(":featureSpeedtest")` | Feature SpeedTest |
| `project(":featureDiagnostico")` | Feature Diagnóstico (orquestração local + remota) |
| `project(":featureFibra")` | Feature Fibra/GPON |
| `project(":featureRouter")` | Driver de roteador TP-Link Archer C6 (login/leitura no `AppShell`) |
| `project(":featureHistory")` | Feature Histórico |
| `project(":featureSettings")` | Feature Ajustes |
| `project(":core:diagnostico")` | `DiagnosticReport`/`DiagnosticInput`/`DiagnosticStatus` consumidos direto por telas e ViewModels (issue #1157 Fase 1a) |
| `project(":core:relatorio")` | `exportarHtmlComoPdf` — renderer único de PDF do consumidor (GH#1219) |
| `project(":core:featureflags")` | `FeatureFlagProvider` + catálogo tipado sobre Firebase Remote Config (issue #1477) |
| `project(":core:probejogo")` | `SondaGameLiftBeacon` — sonda UDP de rota do Modo gamer |

### Bibliotecas externas (do catálogo `libs.versions.toml`)

| Biblioteca | Para quê |
|---|---|
| Compose BOM, `compose.ui`, `material3`, `material.icons.extended`, `activity.compose`, `navigation.compose` | Toda a camada de UI |
| `androidx.core.ktx`, `activity.ktx`, `lifecycle.runtime.ktx`, `lifecycle.runtime.compose` | Ciclo de vida e extensões Android |
| Hilt (`hilt.android` + `hilt.compiler` via kapt) e `hilt.work` (+ `hilt.work.compiler` via KSP) | Injeção de dependência, inclusive nos Workers |
| `androidx.work.runtime.ktx` | `MonitoramentoWorker`, `AdminSyncWorker` |
| Firebase BOM + `crashlytics`, `analytics`, `config` | Crash reporting, analytics e Remote Config |
| `okhttp` | Chamadas HTTP diretas (ingest do admin worker, sonda de latência de jogos) |
| `coil.compose` | Logo remota de operadora de cauda longa (GH#970) |
| `play.services.ads` + `user.messaging.platform` | AdMob nativo e gate de consentimento UMP (issue #555) |
| `play.review` | Avaliação in-app (SIG-173/#664) |
| `timber`, `androidx.profileinstaller`, `desugar.jdk.libs` | Log, baseline profile, desugaring |
| Testes: `junit`, `robolectric`, `mockk`, `kotlinx.coroutines.test`, `org.json:json:20260719`, `compose.ui.test.junit4` | Suíte de unit tests JVM/Robolectric |

Plugins aplicados: AGP application, Kotlin Android, Compose compiler, kapt, KSP, Hilt,
`google-services`, Firebase App Distribution, Firebase Crashlytics, detekt, ktlint e
`gradle-play-publisher`.

## Consumidores

Nenhum. `:app` é o topo do grafo do Consumer — a busca por `project(":app")` nos
`build.gradle.kts` do repositório não retorna nenhum consumidor.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `app/src/main/kotlin/io/signallq/app/SignallQApplication.kt` | `@HiltAndroidApp`, `Configuration.Provider` do WorkManager; inicializa Timber/Crashlytics, feature flags legadas e do novo `FeatureFlagProvider`, coordenador de persistência de speedtest, `AdsFlagsManager` e agendamento de sync com o admin worker |
| `app/src/main/kotlin/io/signallq/app/MainActivity.kt` | Activity única (`@AndroidEntryPoint`); monta `SignallQTheme { AppShell(...) }` e trata permissões contextuais |
| `app/src/main/kotlin/io/signallq/app/MainViewModel.kt` | ViewModel raiz que orquestra os serviços e expõe os `StateFlow` das telas |
| `app/src/main/kotlin/io/signallq/app/ui/screen/AppShell.kt` | Navegação, bottom bar e composição das telas |
| `app/src/main/kotlin/io/signallq/app/ui/screen/AppShellOverlayRegistry.kt` | Ponto de extensão de overlays (issue #1695) — agrega os `AppShellXxxOverlay.kt` sem exigir editar `AppShell.kt`; `AppShellRootRegistry.kt` faz o mesmo para o conteúdo de raiz (ver `technical/appshell-*-registry.md`) |
| `app/src/main/kotlin/io/signallq/app/ui/screen/AppShellFeatureGating.kt` | Aplica o gate de navegação por flag remota (F4/#1480) |
| `app/src/main/kotlin/io/signallq/app/di/AppModule.kt` | Módulo Hilt único — provê tudo, inclusive a lambda `() -> FirebaseRemoteConfig` e o `FeatureFlagProvider` de `:core:featureflags` |
| `app/src/main/kotlin/io/signallq/app/FeatureFlags.kt` | Flags de compilação (`BuildConfig.FEATURE_*`) — mecanismo por build type, distinto das flags remotas |
| `app/src/main/kotlin/io/signallq/app/featureflags/ConsumerFeatureGateCoordinator.kt` | Deriva `AppShellFeatureFlagsState` reativo a partir do `FeatureFlagProvider` remoto |
| `app/src/main/kotlin/io/signallq/app/featureflags/FeatureFlagManager.kt` / `FeatureFlagRepository.kt` | Mecanismo legado de flags via HTTP `GET /flags` (SIG-13) |
| `app/src/main/kotlin/io/signallq/app/{ads,monitoramento,analytics,ui/relatorio,ui/screen}/` | Código de feature hospedado no `:app`: descrito nas páginas de [`features/`](../features/README.md) (ex.: `perfil-ajustes-legal` para anúncios/UMP, `monitoramento-alertas`, `historico-laudo` para o relatório PDF, `wifi-canais-sinal`, `dispositivos-rede`, `wifi-casa`, `inicio-status`); eventos e tracker em `technical/analytics-events-schema.md` |
| `app/src/main/AndroidManifest.xml` | 9 `uses-permission`, `FileProvider`, App ID do AdMob, remoção do `WorkManagerInitializer` automático |

Versão e SDKs: `android/gradle/libs.versions.toml` e inventário em `../README.md`.

## Riscos e dívidas

- **Arquivos acima de 800 linhas em `src/main`** — `MainViewModel.kt` e `AppShell.kt` acima de 1200
  (dívida crítica), mais `HistoricoScreen.kt`, `LocalDeviceSection.kt`, `SinalWifiSection.kt`,
  `SinalCanalSection.kt`, `DiagnosticoGuiadoScreen.kt`, `ResultadoVelocidadeScreen.kt`,
  `DnsScreen.kt` e `MainActivity.kt`. Contagem atual:
  `find android/app/src/main -name '*.kt' | xargs wc -l | sort -rn`. Registro por arquivo e regra de
  extração em `.claude/rules/higiene-e-padronizacao-repositorio.md` §4.
- **`AppShell.kt`:** os registries de overlay e de root content (#1695) não cobrem o wiring de
  estado hoisted, que segue no arquivo — ver "O que este registro não resolve" em
  `technical/appshell-overlay-registry.md`.
- **Dois sistemas de feature flag remotos convivendo.** `featureflags/FeatureFlagManager` (HTTP,
  SIG-13, `io.signallq.app.core.network.FeatureFlagProvider`) e
  `io.signallq.app.core.featureflags.FeatureFlagProvider` (Firebase Remote Config), com nomes de
  interface idênticos em pacotes diferentes — risco de import errado. Soma-se um terceiro
  mecanismo, `FeatureFlags.kt` sobre `BuildConfig`.
- **Sem testes instrumentados.** Não há `src/androidTest`; as dependências
  `androidTestImplementation` declaradas não têm código correspondente.
- **Segredo de ingest em `BuildConfig`.** `ADMIN_INGEST_KEY` entra via `local.properties`/env e
  acaba como string no APK; escopo limitado a `POST /ingest/*`, mas extraível do binário.
- **`di/AppModule.kt` como módulo Hilt único** para todo o grafo — ponto de acoplamento central.
