---
title: "Módulo :featureSpeedtest"
description: "Motor de medição de velocidade (Cloudflare), amostragem de ping, classificação de qualidade e diagnóstico local de conectividade."
type: "técnico"
status: "ativo"
owner: "Camilo"
last_updated: "2026-09-22"
---

# `:featureSpeedtest`

- **Caminho físico:** `android/feature/speedtest/` (alias flat legado)
- **Namespace:** `io.signallq.app.feature.speedtest`

## Responsabilidade

É o motor de medição do produto: executa o speedtest contra endpoints Cloudflare (fases de latência-base, download, upload e latência sob carga), calcula jitter, perda de pacote, bufferbloat, estabilidade e picos, e produz um `ResultadoSpeedtest` já classificado (`MeasurementStatus`, `DiagnosticoQualidadeSpeedtest`, `DiagnosticoFasesSpeedtest`). Expõe também o `SpeedtestViewModel` (Hilt) com a guarda de rede medida, a acumulação mensal de MB em rede móvel e a interrupção por "Wi-Fi sem internet" (`connectivity/`).

Não é dele: renderizar as telas de execução e resultado (`ResultadoVelocidadeScreen.kt` e `Inicio2Screen.kt`, ambas no `:app`), persistir histórico de medições (`:coreDatabase`), gerar recomendação/laudo, nem decidir navegação. Também não define os thresholds de bufferbloat — delega a `:core:diagnostico` (`MetricClassifier`), hoje por trás do seam local `ClassificacaoMetricaLocal.kt` (NDS-02k, #1746/#1759) documentado como o único ponto que muda se a classificação de bufferbloat pós-medição algum dia ganhar uma fonte viva do NDS.

## Dependências

Extraídas de `android/feature/speedtest/build.gradle.kts`.

| Tipo | Dependência | Observação |
|---|---|---|
| Plugin | `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.kapt`, `libs.plugins.hilt` | único dos cinco, junto de `:featureDevices`, com DI própria |
| `implementation` | `libs.hilt.android` + `kapt(libs.hilt.compiler)` | `@HiltViewModel` |
| `implementation` | `libs.androidx.core.ktx` | — |
| `implementation` | `libs.kotlinx.coroutines.android` | — |
| `implementation` | `libs.androidx.lifecycle.runtime.ktx` | `ViewModel`/`viewModelScope` |
| `implementation` | `libs.okhttp` | transporte das fases de medição |
| `implementation` | `project(":coreNetwork")` | `MonitorRede`, `AnalyticsHelper/Tracker`, contratos de conectividade |
| `implementation` | `project(":coreDatabase")` | `ConnectivityDiagnosisHistoryDao/Entity` |
| `implementation` | `project(":coreDatastore")` | `PreferenciasAppRepository` (MB acumulados, preferências) |
| `implementation` | `project(":coreTelephony")` | `MonitorTelephony` (tecnologia da rede móvel) |
| `implementation` | `project(":core:diagnostico")` | GH#1228 fatia 6 — fonte única dos cortes de bufferbloat |
| `implementation` | `libs.timber` | — |
| `testImplementation` | `libs.junit`, `libs.kotlinx.coroutines.test`, `libs.okhttp.mockwebserver` | — |
| `androidTestImplementation` | `libs.androidx.junit`, `libs.androidx.espresso.core` | não há `src/androidTest` |

## Consumidores

`grep` por `project(":featureSpeedtest")` em `android/**/build.gradle.kts` — é o módulo mais consumido dos cinco:

| Consumidor | Arquivo | Situação |
|---|---|---|
| `:app` | `android/app/build.gradle.kts:315` | legítimo (composição no app) |
| `:featureDiagnostico` | `android/feature/diagnostico/build.gradle.kts:62` | **violação da regra feature → feature** |

## Componentes principais

| Arquivo / classe | Linhas | Responsabilidade |
|---|---|---|
| `.../feature/speedtest/ExecutorSpeedtestCloudflare.kt` | 1422 | Implementação real do motor: pool HTTP adaptativo (móvel × Wi-Fi), fases ping/download/upload, latência sob carga, cálculo de bufferbloat/estabilidade/picos, `construirResultado` (onde `MeasurementStatus` é computado uma única vez). Cresceu na fatia "Confiabilidade estatística do diagnóstico de rede" (`.agents/architecture-plan.md`): janela de confirmação de amostragem (`coletarAmostrasLatencia`/`deveConfirmarAmostragem`), propagação de `p95Ms`/`maxMs`/`picos`/`EvidenciaPerdaPacotes`, e o teto de duração `latenciaOrcamentoMs` (só modo fast, achado de QA do Breno). |
| `.../feature/speedtest/SpeedtestViewModel.kt` | 312 | `@HiltViewModel`. Orquestra execução (fast/complete — GH#1737 removeu o modo triplo e a escolha manual; `modoAutomaticoPara` decide o modo por tipo de rede), guarda de rede medida, acúmulo de MB, analytics (Firebase + admin-worker), interrupção por Wi-Fi sem internet, callback `onSpeedtestConcluido` para o orquestrador. |
| `.../feature/speedtest/PingExecutor.kt` | 183 | Ping via HTTP (Android não permite ICMP bruto sem `CAP_NET_RAW`); classifica motivo de falha por amostra. |
| `.../feature/speedtest/connectivity/ConnectivityDiagnosisPresenter.kt` | 185 | Converte `ConnectivityDiagnosis` em título/mensagem + `ConnectivityAction` ordenadas. |
| `.../feature/speedtest/connectivity/ConnectivityDiagnosisRepository.kt` | 111 | Interface + implementação: executa o motor de `:coreNetwork`, persiste sanitizado em `:coreDatabase`, expõe `ultimoDiagnostico` e `existeRedeWifiAtiva`. |
| `.../feature/speedtest/AnalisadorAmostragemPing.kt` | 169 | Algoritmo puro: mediana, jitter, perda, filtro de outlier (`> 3x` mediana), `maxMs`/`p95Ms`/`picos`, e (fatia "Confiabilidade estatística") `avaliarConfianca()` — produz `EvidenciaPerdaPacotes`/`ConfiancaAmostral` (declarados em `:core:diagnostico`) a partir do resultado bruto + `timeoutsConsecutivosMax`. |
| `.../feature/speedtest/MeasurementStatus.kt` | 85 | Fonte única de integridade da execução: `COMPLETE`/`PARTIAL`/`INCONCLUSIVE`/`CONTAMINATED`/`CANCELLED`, com as regras de consumo documentadas. |
| `.../feature/speedtest/SpeedtestQualityClassifier.kt` | 102 | Traduz `MetricStatus` de `:core:diagnostico` para `SeveridadeBufferbloat`; ganhou parâmetro de confiança amostral (fatia "Confiabilidade estatística") — perda com `ConfiancaAmostral.INSUFICIENTE` não empurra sozinha o veredito pra `poor`/`gargaloPrimario = packetLoss`. |
| `.../feature/speedtest/ClassificacaoMetricaLocal.kt` | 63 | Seam NDS-02k (#1746/#1759): isola a chamada a `MetricClassifier.classificarBufferbloat` e (fatia "Confiabilidade estatística") `MetricClassifier.classificarJitter` — usado como um dos 4 gatilhos da janela de confirmação; espelha `io.signallq.app.ui.component.ClassificacaoMetricaLocal` do `:app` (não reusável direto por causa da direção `:feature* -> :core*`). |
| `.../feature/speedtest/connectivity/ConnectivityBlockingPolicy.kt` | 53 | Decide se um diagnóstico é evidência forte o bastante para interromper o teste (extraída da duplicação entre `MainViewModel` e `SpeedtestViewModel`). |
| `.../feature/speedtest/ResultadoSpeedtest.kt` | 53 | Contrato de saída (28+ campos, incluindo métricas DNS e diagnósticos; ganhou `p95Ms`/`maxMs`/`picos`/`evidenciaPerda` na fatia "Confiabilidade estatística", todos aditivos/nullable). |
| `.../feature/speedtest/ValidadorBaselineLatencia.kt` | 28 | Guardas puras: probe indisponível e baseline fisicamente implausível. |
| `.../feature/speedtest/ExecutorSpeedtest.kt` | 22 | Interface do motor (`snapshotFlow`, `executar`, `cancelar`). |
| `.../feature/speedtest/FeatureSpeedtestModulo.kt` | 18 | Factory que injeta a URL do worker dedicado de latência (`GAME_LATENCY_PROBE_URL`). |
| Modelos de apoio | 3–33 cada | `SnapshotExecucaoSpeedtest`, `DiagnosticoFasesSpeedtest`, `DiagnosticoQualidadeSpeedtest`, `EstadoExecucaoSpeedtest`, `FaseSpeedtest`, `ModoSpeedtest`, `PontoAoVivo`, `SeveridadeBufferbloat`. |

Total: 2940 linhas em `src/main` e 1473 em `src/test` (12 arquivos de teste).

## Riscos e dívidas

- **Violação da regra "feature nunca depende de feature" — nenhuma ocorrência conhecida hoje.**
  As duas anteriores já foram resolvidas: `android/feature/diagnostico/build.gradle.kts:62`
  (`implementation(project(":featureSpeedtest"))`, usada só por
  `feature/diagnostico/.../pulse/SignallQOrchestrator.kt`) foi removida em GH#1682 junto com o
  motor SignallQ Pulse, órfão sem consumidor de UI; `android/pro/feature/medicao-diagnostico/
  build.gradle.kts` não existe mais — o módulo `:pro:*` inteiro foi removido nas Fases 4a-b do
  épico #1623 (SignallQ Pro descontinuado permanentemente, ADR-016). Reavaliar esta seção se
  `grep -rn 'project(":feature' feature/*/build.gradle.kts` voltar a encontrar algo.
- **`ExecutorSpeedtestCloudflare.kt` com 1422 linhas** (GH#1737 removeu `executarModoTriplo` e o modo
  `triplo`, encolhendo de 1495 para 1227; a fatia "Confiabilidade estatística do diagnóstico de
  rede", `.agents/architecture-plan.md`, voltou a crescer o arquivo para 1422 — janela de
  confirmação de amostragem, propagação de p95/max/picos e teto de duração do modo fast) — muito
  acima do limite de 800. É o maior arquivo dos cinco módulos, concentra rede, concorrência,
  estatística e construção do resultado, e **não tem teste direto**: os testes cobrem as peças
  puras extraídas dele (`AnalisadorAmostragemPing`, `ValidadorBaselineLatencia`,
  `SpeedtestQualityClassifier`, `PingExecutor`) e o pacote `connectivity`, além de
  `ExecutorSpeedtestCloudflareConfirmacaoTest` (fatia "Confiabilidade estatística").
- **Path físico alinhado ao package `io.signallq.app.*`** — migração de `io/signallq/app/kotlin/` concluída em 2026-08-15 (#1645).
- **Pacote de teste divergente:** `src/test/kotlin/io/signallq/app/kotlin/feature/speedtest/SpeedtestMbEstimativaTest.kt` declara pacote `io.signallq.app.kotlin.feature.speedtest` (com `.kotlin.`), diferente dos demais — visível no nome do relatório JUnit gerado.
- **Telas fora do módulo:** `ResultadoVelocidadeScreen.kt` e o fluxo de execução em `Inicio2Screen.kt` estão em `android/app/src/main/kotlin/io/signallq/app/ui/screen/`. O módulo não contém nenhum Composable — a separação UI/motor é real aqui, mas assimétrica: a UI inteira ficou no `:app`.
- **`MainViewModel.kt` do `:app` também consome `ConnectivityDiagnosisRepository`** (linha 190), o que mantém acoplamento do app ao pacote `connectivity` de uma feature de medição — candidato natural a `:coreNetwork`.
