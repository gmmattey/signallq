---
title: "Módulo :core:diagnostico"
description: "Motor determinístico de causa-raiz, classificação de métricas e score da conexão, compartilhado entre :app, :featureDiagnostico e :featureSpeedtest."
type: "técnico"
status: "ativo"
owner: "Camilo"
last_updated: "2026-09-22"
---

# `:core:diagnostico`

- **Caminho físico:** `android/core/diagnostico/`
- **Namespace:** `io.signallq.app.core.diagnostico`
- **Tipo:** biblioteca Android (`com.android.library`) — na prática, quase todo o conteúdo é Kotlin puro

## Responsabilidade

Domínio de diagnóstico de rede extraído de `:featureDiagnostico` na issue #1157 (Fase 1a): recebe
um `DiagnosticInput` (Wi-Fi, internet, móvel, fibra, DNS, histórico) e devolve um
`DiagnosticReport` com achado principal, achados secundários, hipóteses descartadas, score 0–100
com proveniência e perfis de uso. Concentra também os classificadores canônicos de métrica
(`MetricClassifier`), os motores dos fluxos guiado e gamer, o bucketing de rollout e a
classificação de divergência do shadow mode local-vs-remoto.

Não é dele: coletar dado (isso é `:coreNetwork`/`:coreTelephony`/`:featureSpeedtest`), persistir
(`:coreDatabase`), apresentar (`:app`, módulos `feature/*`) nem gerar recomendações práticas em
linguagem de consumidor — o `DiagnosticRunner` recebe o gerador de recomendações por inversão de
dependência justamente para manter `RecomendacaoPraticaEngine` fora deste módulo.

## Dependências

### Módulos do projeto

| Módulo | Para quê |
|---|---|
| `project(":coreNetwork")` | Contratos e modelos compartilhados (`ConnectionType`, `RedeWifiVizinha`, `ClassificadorSaudeGpon` etc.) |

### Bibliotecas externas

| Biblioteca | Para quê |
|---|---|
| `androidx.core.ktx` | Extensões Kotlin do Android |
| `kotlinx.coroutines.android` | `suspend`/`withContext` nos resolvers de topologia |
| `okhttp` | `GeoIpResolver` e `PublicIpResolver` fazem HTTP direto |
| `junit`, `kotlinx.coroutines.test`, `okhttp.mockwebserver`, `org.json:json:20260719` (test) | Suíte de unit tests JVM |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | Declaradas, mas sem código correspondente |

## Consumidores

| Consumidor | Observação |
|---|---|
| `:app` | Usa `DiagnosticReport`/`DiagnosticInput`/`DiagnosticStatus` direto em telas e ViewModels |
| `:featureDiagnostico` | Orquestra o `DiagnosticRunner` e o shadow mode |
| `:featureSpeedtest` | Classificação de qualidade a partir do resultado do teste |

Extraído de `:featureDiagnostico` na issue #1157 precisamente para permitir reuso entre múltiplos
consumidores do Consumer — hoje `:app`, `:featureDiagnostico` e `:featureSpeedtest`.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `DiagnosticRunner.kt` (151 linhas) | Executor puro do diagnóstico — orquestra os engines por `DiagnosticArea` e monta o `DiagnosticReport`. Recebe `gerarRecomendacoes` por injeção (default vazio, seguro para qualquer caller que não gera recomendação) |
| `FindingEngine.kt` (**745 linhas**) | Motor de achados: avalia regras candidatas com confiança declarada, escolhe achado principal/secundários e registra hipóteses descartadas por evidência mais forte |
| `MetricClassifier.kt` (243 linhas) | Ponto único de classificação de RSSI/latência/jitter/RSRP/RSRQ/SINR etc., com vocabulário `MetricStatus` de 6 valores |
| `ScoreEngine.kt` (254 linhas) + `ScoreEvidenceBuilder.kt` (239) | Score 0–100 por média ponderada de dimensões, com reponderação quando falta dado e teto por métrica crítica; cresceram na fatia "Confiabilidade estatística do diagnóstico de rede" (`.agents/architecture-plan.md`) — o teto de perda crítica passou a checar `EvidenciaPerdaPacotes.confianca == ConfiancaAmostral.SUFICIENTE` em vez de `provenance == Provenance.medida` (inatingível via timeout HTTP) |
| `DiagnosticoGuiadoEngine.kt` (509 linhas) | Motor determinístico do diagnóstico guiado por objetivo (Feature #550/#1475) — única fonte do status; a IA só explica |
| `ModoGamerEngine.kt` (376) / `GameReadinessClassifier.kt` (433) | Modo gamer (#1476/#1487) e prontidão para jogos por categoria competitiva (SIG-290); `GameReadinessClassifier.perdaFaixa` migrou para o mesmo gate de confiança amostral na fatia "Confiabilidade estatística" |
| `UsageProfileClassifier.kt` (620 linhas) | Os 5 perfis de uso (navegação, streaming, jogos, videochamada, trabalho), substituindo texto livre da IA; `perdaDimensao` migrou para o gate de confiança amostral (fatia "Confiabilidade estatística"). Já perto do limiar de 800 da regra de higiene — reavaliar extração se crescer de novo |
| `WifiChannelDiagnosticEngine.kt` (384) | Congestionamento e recomendação de canal Wi-Fi (GH#1207) |
| `WifiSignalQualityEngine.kt` (202), `FibraSignalQualityEngine.kt` (175), `InternetDiagnosticEngine.kt` (254), `MobileSignalDiagnosticEngine.kt` (111), `DnsDiagnosticEngine.kt` (125), `HistoricalDegradationEngine.kt` (119) | Engines por área, cada um devolvendo `List<DiagnosticResult>` |
| `DiagnosticInput.kt` (183) / `DiagnosticReport.kt` (121) / `DiagnosticResult.kt` (19) | Contratos de entrada e saída do motor |
| `DiagnosticEvaluation.kt` (202) | Espelho Kotlin do envelope da avaliação remota (`POST /diagnostic/evaluate` do worker `signallq-diagnostic`) — nome propositalmente distinto de `DiagnosticResult` (ADR-011 §3.2) |
| `DiagnosticDivergenceClassifier.kt` (127) | Shadow mode (GH#1444): compara relatório local com o remoto e classifica a divergência, sem alterar nenhum dos dois |
| `RolloutBucketCalculator.kt` (49) / `DiagnosticRolloutStatus.kt` (58) | Bucketing determinístico 0–99 por `installationId`, com salt por mecanismo de rollout (GH#1445) |
| `DiagnosticRulesVersion.kt` (48) / `DiagnosticExecutionContext.kt` (47) | `rulesVersion` canônica do motor local e identidade da execução (`executionId`), propagadas até histórico e PDF (GH#1228 Fase 3) |
| `EvidenceProvenance.kt` (44) | Proveniência (medida / estimada / indisponível) de cada métrica que entra no score; `EvidenceScore` ganhou campo opcional `confiancaAmostral` (fatia "Confiabilidade estatística") — só preenchido pela dimensão de perda de pacotes, `null` nas demais 10 |
| `EvidenciaPerdaPacotes.kt` (52) | Vocabulário de confiança amostral (`ConfiancaAmostral.SUFICIENTE`/`INSUFICIENTE`) e a evidência rica de perda de pacotes (`EvidenciaPerdaPacotes`), eixo ORTOGONAL a `Provenance` — produzido por `AnalisadorAmostragemPing.avaliarConfianca()` em `:feature:speedtest`, declarado aqui porque `:feature:speedtest` já depende de `:core:diagnostico` (não o inverso). Fatia "Confiabilidade estatística do diagnóstico de rede" (`.agents/architecture-plan.md`) |
| `topology/internet/GeoIpResolver.kt` (74), `PublicIpResolver.kt` (42) | Resolvem ISP/região e IP público via HTTP (ipinfo com fallback ip-api) |
| `topology/correlation/TopologyTracer.kt` (29), `NatClassifier.kt` (41) | Traceroute best-effort e classificação RFC1918 de IP privado |
| `topology/model/` | `NetworkTopology`, `SsdpResponse`, `UpnpDeviceInfo` |

Total: 43 arquivos em `src/main` (6729 linhas) e 33 arquivos em `src/test` (5714 linhas).

## Riscos e dívidas

- **`FindingEngine.kt` com 745 linhas** é o maior arquivo do módulo — abaixo do limiar de 800, mas
  no caminho dele, e é justamente onde mora a lógica de desempate entre achados. Nenhum arquivo do
  módulo ultrapassa 800 linhas hoje.
- **A premissa de "Kotlin puro, zero `android.*`" declarada no `build.gradle.kts` não se sustenta
  no subpacote `topology/`.** `GeoIpResolver`/`PublicIpResolver` fazem HTTP via OkHttp e
  `TopologyTracer.trace()` executa `Runtime.getRuntime().exec("/system/bin/ping")` — I/O, rede e
  binário do Android dentro de um módulo anunciado como determinístico e sem efeito colateral.
  Isso limita a testabilidade e cria acoplamento implícito ao runtime Android.
- **Thresholds ainda não consolidados.** O próprio KDoc de `MetricClassifier` registra que
  `InternetDiagnosticEngine` foi migrado só parcialmente (jitter, download e bufferbloat) e que
  latência, perda e upload seguem com limiares literais divergentes — achado registrado na issue
  #1466, com decisão de produto pendente. Enquanto isso, duas fontes de verdade numérica convivem.
  `SinalScreen.kt` (em `:app`) também ainda não foi migrado para o classifier.
- **Sem testes instrumentados.** Há `androidTestImplementation` declarado no `build.gradle.kts`,
  mas nenhum diretório `src/androidTest` — as dependências não têm código correspondente. A
  cobertura JVM, por outro lado, é boa (29 arquivos de teste, incluindo testes de caracterização
  para congelar comportamento antes de refactors).
- Caminho físico correto (`src/main/kotlin/io/signallq/app/core/diagnostico/`) — módulo nasceu
  direto no path novo, nunca passou por `io/veloo/`.
