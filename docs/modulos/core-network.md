---
title: "Módulo :coreNetwork"
description: "Infraestrutura de rede compartilhada: monitoramento de conexão, sondagens de conectividade, scan Wi-Fi, topologia/OUI e contratos de analytics."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# `:coreNetwork`

- **Caminho físico:** `android/core/network/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.network`
- **Tipo:** biblioteca Android

## Responsabilidade

Concentra a infraestrutura de rede compartilhada do app Consumer: observação do estado de conexão (`MonitorRede`), diagnóstico local de conectividade por sondagens ativas (gateway → DNS → rota externa → hostname/captive portal, mais fallback DoH), scan de redes Wi-Fi vizinhas, classificação de topologia/OUI e os contratos de dados de dispositivo local, gateway e fibra. Também hospeda os dois contratos de instrumentação (`AnalyticsTracker` e `AnalyticsHelper`) usados pelas features sem acoplá-las ao Firebase.

Não é dele: persistir nada (isso é `:coreDatabase`/`:coreDatastore`), pedir permissões ao usuário (`:corePermissions`), ler telefonia móvel (`:coreTelephony`), falar com o Worker de IA ou implementar analytics — as implementações Firebase vivem em `:app`. Também não expõe cliente HTTP genérico: só faz as chamadas HTTP mínimas das próprias sondagens.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `androidx.core.ktx` | utilitários de plataforma (`ContextCompat`, extensões Android) |
| `androidx.lifecycle.runtime.ktx` | integração de ciclo de vida do monitoramento |
| `kotlinx.coroutines.android` | `StateFlow`, `runInterruptible`, `withTimeoutOrNull` nas sondagens |
| `timber` | log do `ScannerRedesWifi` (movido de `:featureWifi`, issue #1157 Fase 1c) |
| `junit`, `kotlinx.coroutines.test` (test) | testes JVM das sondagens e do engine |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding padrão, sem teste instrumentado próprio |

Nenhuma dependência de outro módulo do monorepo. As sondagens usam `java.net.HttpURLConnection` + `java.net.Socket` + `java.net.InetAddress` puros, amarrados à rede sob análise via `ConnectivityProbeBinding`; a exceção é `DohFallbackProbe`, que usa OkHttp (consulta DoH ao resolvedor público, sem amarrar à `Network`).

**Timeouts declarados no código:**

| Constante | Valor | Arquivo |
|---|---|---|
| `ConnectivityDiagnosisEngine.STEP_TIMEOUT_MS_DEFAULT` | 2500 ms | `connectivity/ConnectivityDiagnosisEngine.kt` |
| `ConnectivityDiagnosisEngine.GLOBAL_TIMEOUT_MS_DEFAULT` | 8000 ms | idem |
| `GatewayReachabilityProbe.TIMEOUT_MS_DEFAULT` | 1200 ms | `connectivity/GatewayReachabilityProbe.kt` |
| `DnsReachabilityProbe.TIMEOUT_MS_DEFAULT` | 1500 ms | `connectivity/DnsReachabilityProbe.kt` |
| `ExternalIpReachabilityProbe.TIMEOUT_MS_DEFAULT` | 1500 ms | `connectivity/ExternalIpReachabilityProbe.kt` |
| `HostnameReachabilityProbe.TIMEOUT_MS_DEFAULT` | 2500 ms | `connectivity/HostnameReachabilityProbe.kt` |
| `GatewayLatencyMeasurer.TIMEOUT_MS_DEFAULT` | 1000 ms (3 amostras, portas 80/443/53) | `.../network/GatewayLatencyMeasurer.kt` |
| `DohFallbackProbe.TIMEOUT_MS_DEFAULT` | 2000 ms | `connectivity/DohFallbackProbe.kt` |
| `TIMEOUT_SCAN_MS` | 10 000 ms | `wifi/ScannerRedesWifi.kt` |

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |
| `:core:diagnostico` | `implementation` |
| `:core:nds` | `implementation` |
| `:featureDevices`, `:featureDiagnostico`, `:featureFibra`, `:featureRouter`, `:featureSpeedtest`, `:featureWifi` | `implementation` |

É o módulo core mais consumido do repositório.

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/network/MonitorRede.kt` | contrato do monitor de conexão (`snapshotFlow`, `iniciar`, `encerrar`) |
| `src/main/kotlin/io/signallq/app/core/network/MonitorRedeAndroid.kt` | implementação sobre `ConnectivityManager`/`WifiManager`/`LocationManager` |
| `src/main/kotlin/io/signallq/app/core/network/CoreNetworkModulo.kt` | fábrica manual (`criarMonitorRede`, `criarNetworkCapabilitiesProvider`) |
| `src/main/kotlin/io/signallq/app/core/network/AnalyticsHelper.kt` | contrato do funil de 7 eventos (SIG-155) + `NoOpAnalyticsHelper` |
| `src/main/kotlin/io/signallq/app/core/network/AnalyticsTracker.kt` | contrato de eventos GA4 genéricos (SIG-134): `feature_used`, `screen_view`, `app_session_start/end`, `feature_crash`, `battery_snapshot`, `feature_blocked_remote` |
| `src/main/kotlin/io/signallq/app/core/network/GatewayLatencyMeasurer.kt` | RTT do gateway por TCP connect (sem ICMP/root), mediana de 3 amostras |
| `src/main/kotlin/io/signallq/app/core/network/connectivity/ConnectivityDiagnosisEngine.kt` | motor puro que encadeia as sondagens e produz `ConnectivityDiagnosis` |
| `src/main/kotlin/io/signallq/app/core/network/connectivity/ConnectivityDiagnosisRunner.kt` | ponto de entrada Android; `ConnectivityDiagnosisSource` permite fake em teste JVM |
| `src/main/kotlin/io/signallq/app/core/network/connectivity/{Gateway,Dns,ExternalIp,Hostname}ReachabilityProbe.kt` | as quatro sondagens concretas; `DohFallbackProbe.kt` faz a consulta DoH de fallback |
| `src/main/kotlin/io/signallq/app/core/network/connectivity/ConnectivityProbeBinding.kt` + `AndroidNetworkProbeBinding.kt` | amarram socket/resolução/HTTP à `Network` sob análise (testável por fake) |
| `src/main/kotlin/io/signallq/app/core/network/wifi/ScannerRedesWifi.kt` | scan de redes vizinhas via `WifiManager` + `BroadcastReceiver` |
| `src/main/kotlin/io/signallq/app/core/network/topologia/engine/TopologiaRedeEngine.kt` | motor único de classificação de topologia (issues #975/#979) |
| `src/main/kotlin/io/signallq/app/core/network/topologia/oui/OuiCatalog.kt` | catálogo OUI único, unificação de `OuiDatabase` + `MeshOuiDatabase` |
| `src/main/kotlin/io/signallq/app/core/network/contracts/gateway/GatewayConnectionService.kt` | contrato de conexão ao gateway — implementação real ainda pendente (#547); BUG#1511 proíbe mock que devolva sucesso |
| `src/main/kotlin/io/signallq/app/core/network/contracts/localdevice/LocalDeviceSafeFilter.kt` | allowlist de campos seguros do dispositivo local (GH#541) |

### Contrato `AnalyticsHelper`

Interface do funil principal de engajamento (SIG-155), implementada por `FirebaseAnalyticsHelper` em `:app`. Cobre sete eventos encadeados: `app_aberto` → `speedtest_iniciado` → `speedtest_concluido` → `diag_iniciado` → `diag_concluido` → `ia_laudo_solicitado` → `ia_laudo_recebido`. Convive com `AnalyticsTracker` (schema SIG-134) sem se misturar: são dois contratos distintos que podem compartilhar a mesma instância de `FirebaseAnalytics` internamente. Sem PII nos parâmetros; `versao_app` é anexado pela implementação, fora da assinatura. `NoOpAnalyticsHelper` serve como default em instanciação manual fora do grafo Hilt.

## Riscos e dívidas

- **Módulo grande** — o maior dos módulos `core` flat legados (sondagens, scan Wi-Fi, topologia/OUI, contratos). Maior arquivo: `OuiCatalog.kt`.
- **`GatewayConnectionService` sem implementação real** (issue #547 aberta); risco de reintrodução do BUG#1511 se alguém injetar um mock de sucesso em produção.
- Sem testes instrumentados próprios (`androidTest` vazio) apesar das dependências declaradas; a cobertura é toda JVM.
