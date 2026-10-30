---
title: "Módulo :featureHome"
description: "Regra pura de escolha da medição exibida na tela Início — sem UI, sem I/O e sem dependência de outras features."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureHome`

- **Caminho físico:** `android/feature/home/` (alias flat legado)
- **Namespace:** `io.signallq.app.feature.home`

## Responsabilidade

Concentra a única regra de negócio da tela Início que foi extraída para fora do `:app`: decidir **qual** medição é exibida — a da execução atual ou a última medição salva no histórico — nunca uma mistura das duas (`ResolvedorMedicaoHome`). Trabalha sobre uma struct genérica (`MetricasMedicaoHome`), deliberadamente desacoplada dos tipos de `:featureSpeedtest` e de `:coreDatabase`.

Não é dele: renderizar a tela Início (a `Inicio2Screen.kt` vive em `:app`), buscar dados, converter entidades do Room, orquestrar speedtest ou navegação. O módulo não tem nenhum Composable, ViewModel, UiState nem Repository.

Comportamento da tela Início e a regra de medição exibida: [`features/inicio-status.md`](../features/inicio-status.md).

## Dependências

Extraídas de `android/feature/home/build.gradle.kts`.

| Tipo | Dependência | Observação |
|---|---|---|
| Plugin | `com.android.library` | módulo de biblioteca Android |
| Plugin | `org.jetbrains.kotlin.android` |
| `implementation` | `libs.androidx.core.ktx` | única dependência de runtime |
| `testImplementation` | `libs.junit` |
| `androidTestImplementation` | `libs.androidx.junit`, `libs.androidx.espresso.core` | herdado do template; não há teste instrumentado no módulo |

Nenhuma dependência de módulo `:core*` e nenhuma de outra `feature` — é o módulo mais isolado do grafo.

## Consumidores

`grep` por `project(":featureHome")` em `android/**/build.gradle.kts`:

| Consumidor | Arquivo |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

No código, o consumo é feito por `android/app/src/main/kotlin/io/signallq/app/ui/screen/HomeMedicaoAdapter.kt` (adapta `ResultadoSpeedtest`/`MedicaoEntity` para `MetricasMedicaoHome`) e por `Inicio2Screen.kt`.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `android/feature/home/src/main/kotlin/io/signallq/app/feature/home/ResolvedorMedicaoHome.kt` → `ResolvedorMedicaoHome` | Escolhe entre medição atual e anterior de forma atômica; nunca combina campos de execuções diferentes. |
| mesmo arquivo → `MetricasMedicaoHome` | Struct genérica de entrada (download, upload, latência, jitter, perda, timestamp, `connectionType`, ssid, veredito gamer, gargalo, flag `utilizavel`). |
| mesmo arquivo → `ResolvedHomeMeasurement` / `OrigemMedicaoHome` | Saída com a origem explícita (`ATUAL` / `ANTERIOR`) para a UI rotular "Resultado anterior · Wi-Fi · há 2h". |
| `android/feature/home/src/main/kotlin/io/signallq/app/feature/home/FeatureHomeModulo.kt` | `object FeatureHomeModulo` vazio — placeholder de factory do módulo, sem membros. |
| `android/feature/home/src/test/kotlin/io/signallq/app/feature/home/ResolvedorMedicaoHomeTest.kt` | Único teste do módulo. |
| `android/feature/home/src/main/AndroidManifest.xml` | `<manifest />` vazio. |

## Riscos e dívidas

- **Módulo quase vazio versus tela no `:app`.** `Inicio2Screen.kt` vive em `:app`, enquanto `:featureHome` inteiro tem um único arquivo de regra. A feature "Início" não mora no módulo `:featureHome` — mora no `:app`; o módulo permanece responsável somente pela regra pura de seleção da medição.
- **`FeatureHomeModulo` é código morto** (`object` sem membros). Ou ganha as factories do módulo, ou é removido.
- **Regra de dependência entre features: respeitada.** O KDoc de `ResolvedorMedicaoHome` documenta explicitamente que a struct genérica existe porque `feature/home → feature/speedtest` é proibido, e a adaptação dos tipos reais foi empurrada para o `:app` (`HomeMedicaoAdapter.kt`). É o exemplo correto do padrão no repositório.
- **Cobertura de teste:** adequada para o que existe (1 arquivo de teste para 1 arquivo de regra).
