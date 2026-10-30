---
title: "Módulo :core:diagnostico"
description: "Motor determinístico de causa-raiz, classificação de métricas e score da conexão, compartilhado entre :app, :featureDiagnostico, :featureSpeedtest e :core:nds."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
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
| `:core:nds` | Contrato/rede do NDS reaproveita tipos do motor (ADR-017) |

Extraído de `:featureDiagnostico` na issue #1157 precisamente para permitir reuso entre múltiplos
consumidores do Consumer — hoje `:app`, `:featureDiagnostico`, `:featureSpeedtest` e `:core:nds`.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `DiagnosticRunner.kt` | Executor puro do diagnóstico — orquestra os engines por `DiagnosticArea` e monta o `DiagnosticReport`. Recebe `gerarRecomendacoes` por injeção (default vazio, seguro para qualquer caller que não gera recomendação) |
| `FindingEngine.kt` | Motor de achados: avalia regras candidatas com confiança declarada, escolhe achado principal/secundários e registra hipóteses descartadas por evidência mais forte |
| `MetricClassifier.kt` | Ponto único de classificação de RSSI/latência/jitter/RSRP/RSRQ/SINR etc., com vocabulário `MetricStatus` de 6 valores |
| `ScoreEngine.kt` + `ScoreEvidenceBuilder.kt` | Score 0–100 por média ponderada de dimensões, com reponderação quando falta dado e teto por métrica crítica; cresceram na fatia "Confiabilidade estatística do diagnóstico de rede" (`.agents/architecture-plan.md`) — o teto de perda crítica passou a checar `EvidenciaPerdaPacotes.confianca == ConfiancaAmostral.SUFICIENTE` em vez de `provenance == Provenance.medida` (inatingível via timeout HTTP) |
| `DiagnosticoGuiadoEngine.kt` | Motor determinístico do diagnóstico guiado por objetivo (Feature #550/#1475) — única fonte do status; a IA só explica |
| `ModoGamerEngine.kt` / `GameReadinessClassifier.kt` | Modo gamer (#1476/#1487) e prontidão para jogos por categoria competitiva (SIG-290); `GameReadinessClassifier.perdaFaixa` migrou para o mesmo gate de confiança amostral na fatia "Confiabilidade estatística" |
| `UsageProfileClassifier.kt` | Os 5 perfis de uso (navegação, streaming, jogos, videochamada, trabalho), substituindo texto livre da IA; `perdaDimensao` migrou para o gate de confiança amostral (fatia "Confiabilidade estatística"). Já perto do limiar de 800 da regra de higiene — reavaliar extração se crescer de novo |
| `ClassificadorConectividadeAoVivo.kt`, `MonitorConexaoLeveUseCase.kt` | Estado de conectividade ao vivo (status da Home) e monitor leve de conexão |
| `WifiChannelDiagnosticEngine.kt` | Congestionamento e recomendação de canal Wi-Fi (GH#1207) |
| `WifiSignalQualityEngine.kt`, `FibraSignalQualityEngine.kt`, `InternetDiagnosticEngine.kt`, `MobileSignalDiagnosticEngine.kt`, `DnsDiagnosticEngine.kt`, `HistoricalDegradationEngine.kt` | Engines por área, cada um devolvendo `List<DiagnosticResult>` |
| `DiagnosticInput.kt` / `DiagnosticReport.kt` / `DiagnosticResult.kt` | Contratos de entrada e saída do motor |
| `DiagnosticEvaluation.kt` | Espelho Kotlin do envelope da avaliação remota (`POST /diagnostic/evaluate` do worker `signallq-diagnostic`) — nome propositalmente distinto de `DiagnosticResult` (ADR-011 §3.2) |
| `DiagnosticDivergenceClassifier.kt` | Shadow mode (GH#1444): compara relatório local com o remoto e classifica a divergência, sem alterar nenhum dos dois |
| `RolloutBucketCalculator.kt` / `DiagnosticRolloutStatus.kt` | Bucketing determinístico 0–99 por `installationId`, com salt por mecanismo de rollout (GH#1445) |
| `DiagnosticRulesVersion.kt` / `DiagnosticExecutionContext.kt` | `rulesVersion` canônica do motor local e identidade da execução (`executionId`), propagadas até histórico e PDF (GH#1228 Fase 3) |
| `EvidenceProvenance.kt` | Proveniência (medida / estimada / indisponível) de cada métrica que entra no score; `EvidenceScore` ganhou campo opcional `confiancaAmostral` (fatia "Confiabilidade estatística") — só preenchido pela dimensão de perda de pacotes, `null` nas demais 10 |
| `EvidenciaPerdaPacotes.kt` | Vocabulário de confiança amostral (`ConfiancaAmostral.SUFICIENTE`/`INSUFICIENTE`) e a evidência rica de perda de pacotes (`EvidenciaPerdaPacotes`), eixo ORTOGONAL a `Provenance` — produzido por `AnalisadorAmostragemPing.avaliarConfianca()` em `:feature:speedtest`, declarado aqui porque `:feature:speedtest` já depende de `:core:diagnostico` (não o inverso). Fatia "Confiabilidade estatística do diagnóstico de rede" (`.agents/architecture-plan.md`) |
| `topology/internet/GeoIpResolver.kt`, `PublicIpResolver.kt` | Resolvem ISP/região e IP público via HTTP (ipinfo com fallback ip-api) |
| `topology/correlation/TopologyTracer.kt`, `NatClassifier.kt` | Traceroute best-effort e classificação RFC1918 de IP privado |
| `topology/model/` | `NetworkTopology`, `SsdpResponse`, `UpnpDeviceInfo` |

## Riscos e dívidas

- **`FindingEngine.kt` e `UsageProfileClassifier.kt` são os maiores arquivos do módulo**, perto do limiar de 800 linhas; o primeiro concentra o desempate entre achados.
- **A premissa de "Kotlin puro, zero `android.*`" declarada no `build.gradle.kts` não se sustenta
  no subpacote `topology/`.** `GeoIpResolver`/`PublicIpResolver` fazem HTTP via OkHttp e
  `TopologyTracer.trace()` executa `Runtime.getRuntime().exec("/system/bin/ping")` — I/O, rede e
  binário do Android dentro de um módulo anunciado como determinístico e sem efeito colateral.
  Isso limita a testabilidade e cria acoplamento implícito ao runtime Android.
- **Thresholds ainda não consolidados.** O próprio KDoc de `MetricClassifier` registra que
  `InternetDiagnosticEngine` foi migrado só parcialmente (jitter, download e bufferbloat) e que
  latência, perda e upload seguem com limiares literais divergentes — achado registrado na issue
  #1466, com decisão de produto pendente. Enquanto isso, duas fontes de verdade numérica convivem.
  `SinalMovelSection.kt`/`SinalMovelClassificacao.kt` (em `:app`) também não usam o classifier (issue #1586).
- **Sem testes instrumentados.** Há `androidTestImplementation` declarado no `build.gradle.kts`,
  mas nenhum diretório `src/androidTest` — as dependências não têm código correspondente. A
  cobertura JVM, por outro lado, é boa (incluindo testes de caracterização para congelar
  comportamento antes de refactors).
