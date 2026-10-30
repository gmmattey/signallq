---
title: "Módulo :featureDiagnostico"
description: "Orquestração do diagnóstico de conexão, integração com o worker de IA e ingest de telemetria para o worker admin."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# `:featureDiagnostico`

- **Caminho físico:** `android/feature/diagnostico/` (alias flat legado, remapeado por `projectDir` em `android/settings.gradle.kts`)
- **Namespace:** `io.signallq.app.feature.diagnostico`

## Responsabilidade

Orquestra o fluxo de diagnóstico de conexão do app consumer: coleta o contexto bruto (speedtest, rede, topologia LAN, histórico), executa o motor local de `:core:diagnostico`, roda a avaliação remota em shadow mode contra o worker `signallq-diagnostic`, pede o laudo em linguagem natural ao worker de IA e envia telemetria ao worker admin. Também abriga o motor de recomendações práticas (`RecomendacaoPraticaEngine`, regras REC-01..REC-14) e a sondagem de topologia (UPnP/IGD, STUN/NAT, OUI/mesh).

Não é dele: a UI do diagnóstico (as Screens e o `MainViewModel` vivem em `:app` — este módulo não tem nenhum `@Composable`), as regras de causa-raiz canônicas (`FindingEngine`/`ScoreEngine`/`DiagnosticRunner`, em `:core:diagnostico`), a persistência (`:coreDatabase`), a execução do speedtest em si (`:featureSpeedtest`) e a decisão de copy/fallback de UI — os repositories devolvem `null`/estado e quem decide é o chamador.

## Dependências

Extraídas de `android/feature/diagnostico/build.gradle.kts`.

| Dependência | Configuração | Observação |
|---|---|---|
| `:coreDatabase` | `implementation` | Room: `MedicaoDao`, `ProviderDirectoryCacheDao`, `RecommendationHistoryDao` |
| `:coreDatastore` | `implementation` | `PreferenciasAppRepository` (consentimento LGPD, `anon_device_id`) |
| `:coreNetwork` | `implementation` | `AnalyticsHelper`, `MonitorRede`, `GatewayLatencyMeasurer`, contratos de topologia |
| `:coreRecommendation` | `implementation` | `RecommendationEngine` (monetização, issue #790) |
| `:core:featureflags` | `implementation` | Kill switch do shadow mode (issue #1497) + flags `consumer_diagnostico_nds_*` |
| `:core:diagnostico` | `implementation` | `DiagnosticRunner`, `FindingEngine`, `DiagnosticInput/Report/Result` |
| `:core:nds` | `implementation` | `NdsClient`/`NdsClientFactory` + os mappers `DiagnosticInput<->NDS` |
| `libs.hilt.android` + `kapt(libs.hilt.compiler)` | `implementation`/`kapt` | Único módulo `:feature*` com Hilt (junto de `:featureDevices`) |
| `libs.androidx.core.ktx` | `implementation` | |
| `libs.androidx.lifecycle.runtime.ktx` | `implementation` | |
| `libs.kotlinx.coroutines.android` | `implementation` | |
| `libs.timber` | `implementation` | |
| `libs.okhttp` | `implementation` | Cliente HTTP de todos os repositories remotos |
| `libs.androidx.datastore.preferences` | `implementation` | |
| `libs.junit`, `org.json:json:20260719`, `libs.kotlinx.coroutines.test`, `libs.okhttp.mockwebserver` | `testImplementation` | `org.json` é necessário porque `JSONObject` do SDK não existe no unit test JVM |
| `libs.androidx.junit`, `libs.androidx.espresso.core`, `libs.kotlinx.coroutines.test`, `libs.androidx.room.testing` | `androidTestImplementation` | |

`buildConfigField` declarados no módulo: `AI_WORKER_URL` (`https://linka-ai-diagnosis-worker.gmmattey.workers.dev`), `DIAGNOSTIC_WORKER_URL` (`https://signallq-diagnostic.gmmattey.workers.dev`), `APP_VERSION`, `VERSION_CODE`.

## Consumidores

`grep -rn 'project(":featureDiagnostico")' --include=*.kts .`

| Consumidor | Local |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

Nenhum outro módulo depende deste.

## Componentes principais

### Integração de rede — worker de IA (`AiDiagnosisRepository`)

`android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/ai/AiDiagnosisRepository.kt`.

Cliente do worker `ai-diagnosis-worker`, instanciado como `@Singleton` em `DiagnosticoModule.provideAiDiagnosisRepository()` com `baseUrl = BuildConfig.AI_WORKER_URL` e `isAuthorized = { true }`.

| Método | Endpoint | Comportamento |
|---|---|---|
| `checkAvailability()` | `HEAD {AI_WORKER_URL}/api/ai/diagnostico-conexao` | `OkHttpClient` próprio com connect/read de **5 s**. Considera vivo em 2xx **ou HTTP 405**. Qualquer exceção → `false`. |
| `explainDiagnosis(...)` | `POST {AI_WORKER_URL}/api/ai/diagnostico-conexao` | Payload schema v2 (só dados brutos: métricas, contexto de rede, móvel, dispositivos, histórico, evidências sem interpretação, achados locais, equipamento local). Parser tolerante aceita schema v1 e v2 na resposta (`AI_PROMPT_VERSION = "diagnostico_v6_local_device"`). |
| `explainDiagnosisStream(...)` | `POST {AI_WORKER_URL}/api/ai/diagnostico-conexao?stream=true` | SSE (`data: ` linha a linha até `[DONE]`). Fallback silencioso se o `Content-Type` não for `text/event-stream`. `call.cancel()` no `finally`. Captura o bloco `usage` em `lastStreamUsage`. |

**Timeouts do `OkHttpClient` default:** `connectTimeout` 15 s, `readTimeout` 90 s, `writeTimeout` 30 s. Além disso, `explainDiagnosis` envolve toda a chamada em `withTimeoutOrNull(40_000L)` — ou seja, o teto efetivo de espera é **40 s**, menor que o `readTimeout` de 90 s; estourado o teto, o retorno é `AiDiagnosisState.timeout`.

**Cache:** `ConcurrentHashMap<String, Pair<AiDiagnosisResult, Long>>` em memória, TTL de **5 minutos** (`CACHE_TTL_MS = 5 * 60 * 1000L`). A chave é `SHA-256(AI_PROMPT_VERSION + context.toString())` — a versão do prompt entra no hash para que resposta gerada com prompt antigo não seja servida a um cliente que espera o schema novo. Hit dentro do TTL devolve `AiDiagnosisState.success` com `source = "cache"`; expirado, a entrada é removida.

**Degradação:** sem autorização, HTTP não-2xx, corpo vazio, JSON não parseável ou exceção → `AiDiagnosisState.fallback(localFallback())`. `normalizeStatus` cruza o status da IA com o status local e sobrescreve `"inconclusivo"` para `"regular"` quando há dado de speedtest presente.

### Integração de rede — worker admin (`AdminIngestRepository`)

`android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/ingest/AdminIngestRepository.kt`.

Envia telemetria ao `signallq-admin-worker`. `baseUrl` e `ingestKey` vêm de fora via `@Named("adminIngestUrl")`/`@Named("adminIngestKey")` (BuildConfig de `:app`), e o `OkHttpClient` dedicado (`@Named("adminIngestClient")`) usa connect/read/write de **10 s** — telemetria é best-effort, não bloqueia o usuário.

| Método | Endpoint | Payload |
|---|---|---|
| `sendDiagnostic` | `POST {ADMIN_INGEST_URL}/ingest/diagnostic` | `DiagnosticIngestPayload` (id, created_at, network_type, status, score, métricas, issues, operator, device_model, os_version, app_version, ai_summary_report, environment, dist_channel, build_type, version_code, device_id) |
| `sendAiUsage` | `POST {ADMIN_INGEST_URL}/ingest/ai-usage` | `AiUsageIngestPayload` (id, model, session_id, tokens, cost_usd, metadados de build) |
| `sendAnalyticsEvent` | `POST {ADMIN_INGEST_URL}/ingest/analytics` | `AnalyticsEventIngestPayload` embrulhado em `{ "events": [ ... ] }` — o worker aceita batch, o app sempre envia um evento por chamada |

Autenticação: header `Authorization: Bearer {INGEST_KEY}` — chave com escopo limitado a `/ingest/`, distinta do `ADMIN_SECRET` do painel.

Privacidade: `consentimentoProvider` (default `false`) é consultado em **todos** os três métodos; sem consentimento LGPD nada sai do aparelho. Wire real em `DiagnosticoModule`: `{ prefs.buscarConsentimentoLgpd() == true }`.

Confirmação e ordenação (GH#1332): o retorno `Boolean` só é `true` quando o worker confirma o **mesmo identificador** (`{ok: true, id}` ou `acceptedIds` contendo o id) — isso permite que a fila persistente avance o checkpoint com segurança. Como `ai_usage.session_id` tem FK para `diagnostic_sessions(id)` no D1, `sendAiUsage` aguarda (via `CompletableDeferred`, timeout de 10 s) a confirmação do `sendDiagnostic` da mesma sessão antes de enviar; pendências não consumidas são varridas após 30 s.

### Demais componentes

| Arquivo | Responsabilidade |
|---|---|
| `.../ai/AiModels.kt` | Modelos do contrato de IA (`DiagnosisAiContext`, `AiDiagnosisResult`, `ModeloIa`, `PerguntaContextual`…), `DiagnosisAiContextFactory` e `AiFallbackFactory` |
| `.../RecomendacaoPraticaEngine.kt` | Motor de recomendações práticas locais (REC-01..REC-14). Renomeado de `RecommendationEngine` na auditoria #1228 para não colidir com o motor de monetização de `:coreRecommendation` |
| `.../DiagnosticOrchestrator.kt` | Fachada `StateFlow` do diagnóstico; delega para `NdsDiagnosticRepository.evaluate` quando `consumer_diagnostico_nds_live_enabled` está ligada, senão para `RemoteDiagnosticRepository.evaluateShadow` |
| `.../remote/RemoteDiagnosticRepository.kt` | Cliente do worker `signallq-diagnostic` (`POST /api/diagnostic/evaluate`). Timeouts OkHttp 3 s/4 s/3 s com teto de 42 s. Fallback de 3 níveis: `REMOTE` → `CACHED_LOCAL` → `BUNDLED_LOCAL`. Só roda quando a flag do NDS live está desligada (ver linha acima) |
| `.../nds/NdsDiagnosticRepository.kt` | Chama `NdsClient.evaluate()` (`:core:nds`, timeout de 55 s no cliente), mapeia sucesso via `NdsDiagnosticsResponse.toDiagnosticReport()`. Qualquer falha (`KnownError`/`UnknownError`/timeout) cai para `DiagnosticRunner` local — mesmo espírito de `RemoteDiagnosticRepository.evaluate()` (remoto-primeiro, fallback total), não de `evaluateShadow()`. Dispara `AnalyticsHelper.registrarDiagNdsOutcome` (sucesso/erro conhecido/erro desconhecido + fallback usado) |
| `.../remote/DiagnosticDivergenceReporter.kt` | Shadow mode: envia só o resumo já comparado para `POST /ingest/diagnostic-divergence`. Kill switch via `:core:featureflags` + rollout percentual, fail closed |
| `.../remote/DiagnosticRolloutStatusRepository.kt` | `GET /diagnostic/rollout-status` com cache em memória e TTL curto |
| `.../remote/ProviderDirectoryRepository.kt` | Diretório remoto de provedores (`GET /providers/...`) com cache Room (`RoomProviderDirectoryCache`) |
| `.../remote/RulesetCacheStore.kt` | `FileRulesetCacheStore` persiste o último ruleset remoto válido em `filesDir/diagnostic_ruleset` (nunca `cacheDir`) |
| `.../remote/DiagnosticSnapshotMapper.kt` / `RemoteDiagnosticReportMapper.kt` | Serialização do `DiagnosticInput` e desserialização do relatório remoto |
| `.../topology/TopologyDiagnostic.kt` + `topology/lan/*` | Sondagem de topologia: `GatewayResolver`, `UpnpIgdDiscovery`, `UpnpSoapClient`, `UpnpParser`, `MeshDetector`, `OuiVendorLookup`, `StunNatProbe`, `StunMessageCodec` |
| `.../recommendation/RecommendationDecisionCoordinator.kt` e `RecommendationHistoryRepository.kt` | Ponte entre diagnóstico, histórico Room e o `RecommendationEngine` de `:coreRecommendation` |
| `.../di/DiagnosticoModule.kt` | Módulo Hilt `@InstallIn(SingletonComponent)` com todos os providers acima |

## Riscos e dívidas

- **Motor SignallQ Pulse removido (GH#1682)** junto da dependência `:featureDiagnostico` → `:featureSpeedtest` (violação da regra feature→feature, já resolvida). Código recuperável via `git log -- android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/pulse/`.
- **`ai/AiModels.kt` acima de 800 linhas** — agrega ~25 data classes do contrato de IA mais `DiagnosisAiContextFactory` e `AiFallbackFactory`; os dois `object` são candidatos naturais a arquivos próprios.
- **Regra de negócio em Composable:** não aplicável — o módulo não contém nenhum `@Composable` (verificado por `grep -rn "@Composable"`).
- **Divergência entre `readTimeout` (90 s) e o teto de `withTimeoutOrNull` (40 s)** em `AiDiagnosisRepository.explainDiagnosis`: o comentário no construtor justifica os 90 s para dar margem à inferência, mas o teto de 40 s cancela antes. Só o caminho de streaming (`explainDiagnosisStream`, sem `withTimeoutOrNull`) usa de fato os 90 s. Comportamento não documentado no código; se for intencional, o comentário está desatualizado.
- **Falta de teste:** os componentes de rede e mapeamento têm cobertura (incluindo `AiDiagnosisRepositoryTest`, `AdminIngestRepositoryTest`, `RemoteDiagnosticRepositoryTest`). Sem teste próprio: `TopologyDiagnostic`, `GatewayResolver`, `MeshDetector`, `UpnpSoapClient` e o módulo Hilt.
- **Analytics e input órfãos desde `740f558b` (2026-07-13, GH#937), não desde GH#1682 (ver issue de acompanhamento):** `AnalyticsHelper.registrarIaLaudoSolicitado`/`registrarIaLaudoRecebido` e `AnalyticsTracker.registrarFeatureUsada("diagnostico", sessionIdOverride=...)` só eram chamados de dentro do `SignallQOrchestrator`, cujos call sites no `MainViewModel` caíram de oito para um em `740f558b` — desde então nenhum caminho de produção os dispara. GH#1682 apagou código já inalcançável; reverter não restauraria o funil. `DiagnosticInput.deviceGamingSelecionado` (consumido por `RecomendacaoPraticaEngine`) também ficou sem escritor em produção — só a árvore de perguntas do Pulse ("qual_jogo_device") preenchia esse campo. Além disso, `core/database/.../chat/` (`ChatSessionEntity`/`ChatMessageEntity`/`ChatSessionDao`, tabelas `chat_sessions`/`chat_messages`) já estava órfão antes desta remoção — nenhum caminho de produção grava linhas ali (`AdminSyncWorker` só lê, nunca encontra nada) — mudança de schema/migration fica fora do escopo desta remoção (não é decisão a tomar silenciosamente, ver `.claude/rules/higiene-e-padronizacao-repositorio.md` §9).
- **Shadow mode ainda não validado:** o kdoc de `RemoteDiagnosticRepository` registra que a paridade entre motor local e remoto (GH#1442) tem várias regras `PARCIAL`/`PENDENTE`. `evaluate()` (remoto-primeiro) permanece no código, testado, mas fora do caminho de produção — código vivo não exercitado em produção é risco de regressão silenciosa.
