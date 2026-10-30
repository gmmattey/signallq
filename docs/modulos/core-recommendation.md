---
title: "Módulo :coreRecommendation"
description: "Motor stateless de decisão de recomendações pós-diagnóstico, com catálogo local de fallback e contratos de analytics."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# `:coreRecommendation`

- **Caminho físico:** `android/core/recommendation/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.recommendation`
- **Tipo:** biblioteca Android (plugin `com.android.library`), porém sem nenhuma API Android no código — é lógica Kotlin pura

## Responsabilidade

Decide **qual** recomendação exibir após um diagnóstico. Recebe um `RecommendationRequest` já estruturado (tags, métricas, contexto de rede, flags e histórico), filtra candidatos do catálogo por rede, flags, relevância de tag, limiar de afiliado e histórico (cooldown/limite diário/semanal), calcula score e devolve a lista ranqueada (`rank`) ou a única decisão a exibir (`choose`).

Não é dele: rodar o diagnóstico, carregar o histórico (quem chama busca em `:coreDatabase` e passa em `RecommendationRequest.history`), desenhar o card, enviar eventos ao Firebase ou integrar SDK de anúncio. O motor é **stateless** e não conhece Compose, Room nem AdMob — só define os contratos (`RecommendationCatalog`, `RecommendationAnalyticsTracker`) que o integrador implementa.

Estratégia de decisão (issue #790): 1) recomendação gratuita quando resolve; 2) produto afiliado só com forte relação com o diagnóstico; 3) serviço/parceiro/operadora quando mais adequado; 4) AdMob nativo apenas como fallback. Como só existe um card por diagnóstico, a regra "nunca afiliado + AdMob simultâneos" é garantida estruturalmente.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `junit` (test) | `RecommendationEngineTest` |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding padrão, sem teste instrumentado |

**Nenhuma dependência de `implementation`** — nem `androidx.core.ktx`, nem coroutines, nem módulo do monorepo. É o único módulo `core` legado com o bloco de dependências de produção vazio.

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |
| `:featureDiagnostico` | `implementation` |

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/recommendation/RecommendationEngine.kt` | motor: `rank()` (filtros encadeados + ordenação por `priorityTier` e `-score`) e `choose()` |
| `src/main/kotlin/io/signallq/app/core/recommendation/Recommendation.kt` | item de catálogo (tags, redes aplicáveis, `basePriority`, `cooldownHours`, `maxPerDay`/`maxPerWeek`) |
| `src/main/kotlin/io/signallq/app/core/recommendation/RecommendationDecision.kt` | saída do motor: recomendação + `matchedTags`, `score`, `priorityTier`, `reason`, `trackingId` |
| `src/main/kotlin/io/signallq/app/core/recommendation/RecommendationRequest.kt` | entrada estruturada + `RecommendationFlags` (inclui `minAffiliateMatchRatio`, default 0.5) |
| `src/main/kotlin/io/signallq/app/core/recommendation/RecommendationType.kt` | `FREE_TIP`, `TUTORIAL`, `CONFIGURATION`, `AFFILIATE_PRODUCT`, `PARTNER_OFFER`, `OPERATOR_OFFER`, `NATIVE_AD_FALLBACK` + propriedade `monetized` |
| `src/main/kotlin/io/signallq/app/core/recommendation/DiagnosticTag.kt` | `value class` sobre `String` (não enum, para o catálogo remoto poder introduzir tags sem release) |
| `src/main/kotlin/io/signallq/app/core/recommendation/DiagnosticMetrics.kt` | métricas cruas do teste + `DeviceContext`, todas opcionais |
| `src/main/kotlin/io/signallq/app/core/recommendation/RecommendationFeedback.kt` | `RecommendationFeedbackType` e `RecommendationHistoryEntry` |
| `src/main/kotlin/io/signallq/app/core/recommendation/NetworkContextType.kt` | `WIFI`, `MOVEL`, `ETHERNET` |
| `src/main/kotlin/io/signallq/app/core/recommendation/catalog/RecommendationCatalog.kt` | `fun interface` da fonte de candidatos |
| `src/main/kotlin/io/signallq/app/core/recommendation/catalog/LocalRecommendationCatalog.kt` | catálogo mínimo embarcado, usado enquanto o catálogo remoto não existe |
| `src/main/kotlin/io/signallq/app/core/recommendation/analytics/RecommendationAnalytics.kt` | 6 eventos (`recommendation_eligible/shown/clicked/dismissed/feedback/fallback_ad_shown`), payload e `RecommendationAnalyticsTracker` |

## Riscos e dívidas

- **Catálogo remoto inexistente:** só há `LocalRecommendationCatalog`, com um exemplo por categoria. Toda a monetização depende hoje de uma lista hardcoded no app — mudar recomendação exige release.
- **Plugin Android sem uso de Android:** aplica `com.android.library` e declara `compileSdk`/`minSdk`/`testInstrumentationRunner` para código Kotlin puro. Poderia ser um módulo `java-library`/JVM, o que aceleraria build e testes.
- **Acoplamento indireto com `:coreDatabase`:** a tabela `recommendation_history` e `RecommendationHistoryEntity`/`RecommendationHistoryDao` vivem em `:coreDatabase`, enquanto o modelo `RecommendationHistoryEntry` vive aqui. Os dois precisam evoluir juntos sem que o Gradle imponha a relação — desalinhamento não quebra a compilação.
- **Cobertura concentrada:** 1 arquivo de teste (`RecommendationEngineTest`); cobre o motor, não o catálogo nem o mapeamento de analytics.
