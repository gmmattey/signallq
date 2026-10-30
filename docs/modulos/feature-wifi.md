---
title: "Módulo :featureWifi"
description: "Resumo textual do estado da conexão Wi-Fi e vocabulário de topologia (mesh, repetidor, AP) usado pela tela Sinal."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureWifi`

- **Caminho físico:** `android/feature/wifi/` (alias flat legado)
- **Namespace:** `io.signallq.app.feature.wifi`

## Responsabilidade

Traduz o `SnapshotRede` de `:coreNetwork` em um resumo apresentável do estado da conexão (`MontarResumoWifiUseCase` → `ResumoWifi`: título + detalhe por tipo de conexão) e expõe o vocabulário de topologia Wi-Fi consumido pela tela Sinal (`TipoTopologia`, `ConfiancaTopologia`, `RedeClassificada`, `GrupoRedeWifi`). Também reexporta, via `typealias`, os contratos `RedeVizinha`/`SegurancaWifi` que já migraram para `coreNetwork/contracts`.

Não é dele: varrer redes Wi-Fi (isso é `ScannerRedesWifi`, de `:coreNetwork` — o módulo só oferece uma factory que a instancia), classificar topologia de fato (`TopologiaRedeEngine`, também em `:coreNetwork`), renderizar a tela Sinal, nem gerenciar permissões de localização.

Comportamento das telas que usam este vocabulário: [`features/wifi-canais-sinal.md`](../features/wifi-canais-sinal.md).

## Dependências

Extraídas de `android/feature/wifi/build.gradle.kts`.

| Tipo | Dependência | Observação |
|---|---|---|
| Plugin | `com.android.library` | — |
| Plugin | `org.jetbrains.kotlin.android` | — |
| `implementation` | `project(":coreNetwork")` | `SnapshotRede`, `EstadoConexao`, `ScannerRedesWifi`, contratos de `wifi` |
| `implementation` | `libs.androidx.core.ktx` | — |
| `implementation` | `libs.kotlinx.coroutines.android` | — |
| `implementation` | `libs.timber` | log |
| `testImplementation` | `libs.junit` | declarado, mas não há `src/test` no módulo |
| `androidTestImplementation` | `libs.androidx.junit`, `libs.androidx.espresso.core` | não há `src/androidTest` |

Sem Hilt e sem Compose: o wiring é feito por `FeatureWifiModulo` (factories manuais) e consumido pelo `AppModule` do `:app`.

## Consumidores

`grep` por `project(":featureWifi")` em `android/**/build.gradle.kts`:

| Consumidor | Arquivo |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

No código do `:app`, os tipos do módulo aparecem em `di/AppModule.kt`, `ui/screen/AppShellState.kt`, `ui/screen/Inicio2Screen.kt`, `ui/screen/SinalWifiSection.kt`, `ui/screen/SinalCanalSection.kt` e `ui/screen/SinalTopologiaHelpers.kt` (issue #1660 extraiu as superfícies Wi-Fi/Canal do antigo `SinalScreen.kt` monolítico para esses arquivos).

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `android/feature/wifi/src/main/kotlin/io/signallq/app/feature/wifi/MontarResumoWifiUseCase.kt` | Mapeia `EstadoConexao` (wifi/móvel/ethernet/desconectado/desconhecido) em título + detalhe; monta a string técnica `ssid=… bssid=… rssi=… link=… freq=…`. |
| `android/feature/wifi/src/main/kotlin/io/signallq/app/feature/wifi/GrupoRedeWifi.kt` | `TipoTopologia` (roteador, roteador mesh, nó mesh, repetidor, ponto de acesso, desconhecido), `ConfiancaTopologia`, `RedeClassificada`, `GrupoRedeWifi`. |
| `android/feature/wifi/src/main/kotlin/io/signallq/app/feature/wifi/FeatureWifiModulo.kt` | Factories: `criarMontarResumoWifiUseCase()` e `criarScannerRedesWifi(context)` (delega a `:coreNetwork`). |
| `android/feature/wifi/src/main/kotlin/io/signallq/app/feature/wifi/ResumoWifi.kt` | Data class de saída (`titulo`, `detalhe`). |
| `android/feature/wifi/src/main/kotlin/io/signallq/app/feature/wifi/RedeVizinha.kt` | Apenas `typealias` para `io.signallq.app.core.network.contracts.wifi.{RedeVizinha, SegurancaWifi}` — compatibilidade de imports após a migração para `coreNetwork`. |
| `android/feature/wifi/src/main/AndroidManifest.xml` | `<manifest />` vazio. |

## Riscos e dívidas

- **Zero testes.** O módulo declara `testImplementation(libs.junit)` mas **não possui diretório `src/test`**. `MontarResumoWifiUseCase` é lógica pura, 100% testável, e está descoberta.
- **Regra de negócio dentro de Composable, no `:app`.** O agrupamento e a classificação de redes que dão sentido a `GrupoRedeWifi`/`RedeClassificada` são montados em `SinalWifiSection.kt` (inclusive a construção literal de `RedeClassificada(..., TipoTopologia.DESCONHECIDO, ConfiancaTopologia.BAIXA, motivo = "")`) e em `SinalTopologiaHelpers.kt`; o módulo só fornece os tipos. Estado da extração por aba: [`wifi-canais-sinal`](../features/wifi-canais-sinal.md).
- **Desequilíbrio de massa:** módulo mínimo contra milhares de linhas de telas Wi-Fi/Canal/Móvel no `:app`. Mesma inconsistência de `:featureHome`.
- **Regra de dependência entre features: respeitada.** Nenhum `project(":feature…")` no `build.gradle.kts`; a única dependência de projeto é `:coreNetwork`.
