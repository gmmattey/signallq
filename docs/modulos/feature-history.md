---
title: "Módulo :featureHistory"
description: "Leitura observável do histórico de medições, gráfico de uptime com narrativa e exportação para CSV e PDF."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureHistory`

- **Caminho físico:** `android/feature/history/` (alias flat legado, remapeado por `projectDir` em `android/settings.gradle.kts`)
- **Namespace:** `io.signallq.app.feature.history`

## Responsabilidade

Camada de leitura e derivação sobre o histórico de medições persistido em Room: expõe `Flow`/`StateFlow` filtrados por período, modo e contaminação; agrega o resumo das últimas medições; agrupa amostras em blocos de 30 minutos para o gráfico de uptime; gera a narrativa textual dos últimos 7 dias; e exporta o histórico em CSV e PDF.

Não é dele: a escrita das medições (quem grava é `:featureSpeedtest`/monitor via `:coreDatabase`), o schema Room (`MedicaoEntity`/`MedicaoDao` vivem em `:coreDatabase`), a UI do histórico (Screens em `:app` — o módulo não tem nenhum `@Composable`) e o motor genérico de paginação HTML→PDF (`:core:relatorio`, que só recebe HTML pronto e não conhece `MedicaoEntity`).

Comportamento do histórico e do laudo: [`features/historico-laudo.md`](../features/historico-laudo.md).

## Dependências

Extraídas de `android/feature/history/build.gradle.kts`.

| Dependência | Configuração | Observação |
|---|---|---|
| `:coreDatabase` | `implementation` | `MedicaoDao`, `MedicaoEntity` |
| `:core:relatorio` | `implementation` | Motor de paginação HTML→PDF via WebView (issue #1157 Fase 1b) |
| `libs.androidx.core.ktx` | `implementation` | |
| `libs.kotlinx.coroutines.android` | `implementation` | |
| `libs.junit` | `testImplementation` | |
| `libs.androidx.junit`, `libs.androidx.espresso.core` | `androidTestImplementation` | |

`testOptions { unitTests { isReturnDefaultValues = true } }` — necessário porque o módulo toca APIs Android (`android.graphics`, `PdfDocument`) em testes JVM.

Sem Hilt, sem OkHttp, sem Room direto (só o DAO de `:coreDatabase`). Depende de um alias hierárquico novo (`:core:relatorio`).

## Consumidores

`grep -rn 'project(":featureHistory")' --include=*.kts .`

| Consumidor | Local |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

Nenhum outro módulo depende deste.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `.../history/FeatureHistoryModulo.kt` | Fachada `object` do módulo. Sete funções de observação sobre `MedicaoDao` (`observarUltimas`, `observarPorModo`, `observarDesde`, `observarContaminadasDesde`, `observarPorModoDesde`, `observarFiltrado`) mapeando `MedicaoEntity` → `ItemHistoricoRecente`, mais `criarObservadorHistorico(dao)` |
| `.../history/ObservadorHistorico.kt` | Interface: expõe `resumoFlow: StateFlow<ResumoHistorico>` |
| `.../history/ObservadorHistoricoRoom.kt` | Implementação Room. `stateIn` com `SharingStarted.Eagerly` em `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Calcula últimas métricas, médias da janela de 5 medições, contagem de contaminadas e as 3 últimas amostras. `cancel()` encerra o scope |
| `.../history/ResumoHistorico.kt` | Data classes `ResumoHistorico` e `ItemHistoricoRecente` |
| `.../history/TendenciaCalculador.kt` | Função pura `calcularTendencia(resumo)`: compara o último download com a média de 5; limiar de ±10% define `MELHOROU`/`PIOROU`/`ESTAVEL`. Devolve `null` com menos de 2 medições ou média zero |
| `.../history/UptimeChartUseCase.kt` | Agrupa medições em blocos de 30 min. Enum `StatusUptime { OK, LENTO, LATENCIA_ALTA, OFFLINE, SEM_DADO }` e `BlocoUptime` (data/hora, status, latência representativa, latência média). GH#1518 separou `LATENCIA_ALTA` de `OFFLINE` — antes de 2026-07-31, latência > 800 ms era rotulada erroneamente como offline |
| `.../history/UptimeNarrativaEngine.kt` | `object` que produz a narrativa PT-BR dos últimos 7 dias a partir de `List<BlocoUptime>`: detecta padrões horários recorrentes, sequências longas de `OFFLINE` (≥2 blocos = >30 min) e tendência de qualidade. v2.1 nunca descreve `LATENCIA_ALTA` como "offline"/"sem conexão" |
| `.../history/ExportadorHistoricoCSV.kt` | Exporta para CSV UTF-8: Data, Hora, Download, Upload, Latência, Jitter, Perda, Bufferbloat, Fonte. `null` vira campo vazio; números com 2 casas em `Locale.US`. Devolve `Boolean`, engole exceção |
| `.../history/ExportadorHistoricoPDF.kt` | Duas rotas: `exportar()` sem `Context` monta PDF A4 multi-página com `PdfDocument`/`Paint` (compatível com testes JVM); `exportarComWebView()` gera o HTML e delega a paginação a `io.signallq.app.core.relatorio.exportarHtmlComoPdf`. `gerarHtml()` é função pura e testável — o template é do consumer e deliberadamente não migra para o core |

## Riscos e dívidas

- **Dependência entre features:** nenhuma. Depende só de `:coreDatabase` e `:core:relatorio` — em conformidade com a regra.
- **Regra de negócio em Composable:** não aplicável — 0 `@Composable` no módulo (verificado por grep). Toda a lógica de agregação, narrativa e tendência está em funções puras ou `object`.
- **`ExportadorHistoricoPDF` mantém dois motores de geração em paralelo** (`PdfDocument` desenhando linha a linha e `WebView`/HTML via `:core:relatorio`), o primeiro preservado explicitamente "para compatibilidade com testes JVM existentes". Duas implementações ativas da mesma responsabilidade produzem layouts diferentes conforme o call site — dívida real, ainda que documentada no código.
- **Exportadores engolem exceção e devolvem apenas `Boolean`.** `ExportadorHistoricoCSV.exportar` faz `catch (e: Exception) { false }` sem log (não usa Timber — o módulo nem declara a dependência). Uma falha de escrita chega ao usuário sem nenhuma causa diagnosticável.
- **Falta de teste.** Com teste: `UptimeNarrativaEngine`, `UptimeChartUseCase`, a lógica de grid do uptime, `TendenciaCalculador`, os dois exportadores e o fluxo de exportação . **Sem teste:** `ObservadorHistoricoRoom` (o `StateFlow` que alimenta o resumo da Home) e `FeatureHistoryModulo` (as sete queries observáveis e o mapeamento `MedicaoEntity` → `ItemHistoricoRecente`) — ou seja, toda a superfície de leitura do módulo está descoberta.
- **`ObservadorHistoricoRoom` cria o próprio `CoroutineScope`** com `SharingStarted.Eagerly` e depende de o chamador invocar `cancel()` manualmente. Não há `AutoCloseable` nem integração com ciclo de vida — vazamento se `:app` esquecer de cancelar.
