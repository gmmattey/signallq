---
title: "Módulo :core:nds"
description: "Cliente HTTP e contrato tipado do Network Diagnostics Service (NDS), mappers puros DiagnosticInput↔NDS e decoders de módulo; consumido por :featureDiagnostico atrás de flags remotas, com fallback local."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:core:nds`

- **Caminho físico:** `android/core/nds/`
- **Namespace:** `io.signallq.app.core.nds`
- **Tipo:** biblioteca Android (`com.android.library`) — Kotlin puro além de `BuildConfig`

## Responsabilidade

Camada de rede e contrato tipado do NDS (Network Diagnostics Service, repositório
`network-diagnostics-service`; ADR-017). Cobre: cliente HTTP (`NdsClient.evaluate`), modelos de
request e resposta, decoders tipados dos módulos confirmados no ADR (`asScoring`, `asAi`,
`asWifiDiagnostics`), tratamento de erro (`NdsDiagnosticsOutcome`) e os mappers puros entre
`DiagnosticInput`/`DiagnosticReport` (de `:core:diagnostico`) e o contrato do NDS.

Não é dele: decidir QUANDO usar a chamada viva — isso é orquestração de `:featureDiagnostico`
(`DiagnosticOrchestrator` + `NdsDiagnosticRepository`), atrás das flags remotas `consumer_diagnostico_nds_*` (combinação e defaults em
[`features/assist-diagnostico.md`](../features/assist-diagnostico.md)). Qualquer falha mantém o
fallback local.

Módulo dedicado (não `:coreNetwork`) porque o NDS vai substituir `:core:diagnostico`,
`ai-diagnosis-worker` e `signallq-diagnostic-worker` (ADR-017) e precisa de contrato próprio,
versionável e consumível por múltiplas features; `:coreNetwork` é infraestrutura de conectividade
*on-device*.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `:coreNetwork` | `ChannelScore` para `NdsWifiScanMapper` |
| `:core:diagnostico` | `MetricStatus`/`DiagnosticStatus` e `DiagnosticInput`/`DiagnosticReport` traduzidos pelos mappers. Dependência **intencionalmente temporária** — sai quando `core/diagnostico` sair do repositório; documentada em `core/nds/build.gradle.kts` |
| `androidx.core.ktx`, `kotlinx.coroutines.android`, `timber` | Utilitários, `withContext(Dispatchers.IO)`, log defensivo |
| `okhttp` | Cliente HTTP |
| `junit`, `kotlinx.coroutines.test`, `okhttp.mockwebserver`, `org.json` (test) | Suíte JVM — `MockWebServer` em loopback, nunca bate na rede real |

## Consumidores

- **`:featureDiagnostico`** — `NdsDiagnosticRepository` (`feature/diagnostico/nds/`) chama
  `NdsClient.evaluate()` a partir de `DiagnosticOrchestrator`. Qualquer falha
  (`KnownError`/`UnknownError`/timeout) cai para o `DiagnosticRunner` local, sem exceção propagada.
- Arquivos de UI do `:app` (`SignalBars.kt`, `SinalMovelClassificacao.kt` etc.) consomem só os
  mappers puros (`parseNdsVeredicto`, `classificar*Local`), sem chamada de rede.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `NdsClient.kt` | `suspend fun evaluate(NdsDiagnosticsRequest): NdsDiagnosticsOutcome` — `POST /v1/diagnostics/evaluate` (e `/v2/...` para o envelope estruturado) com `Authorization: Bearer`. Nunca lança ao chamador. Timeout: connect/write 5 s, read/call 55 s (o NDS tem orçamento de 50 s para a inferência) |
| `NdsClientFactory.kt` | Monta `NdsClient` a partir de `BuildConfig.NDS_BASE_URL`/`NDS_API_TOKEN` |
| `NdsDiagnosticsRequest.kt` | Request tipado (blocos `NdsAppInfo`, `NdsConnectionInfo`, `NdsWifiInfo`, `NdsWifiScanInfo`, `NdsSpeedInfo`, `NdsQualityInfo`, `NdsDnsInfo`, `NdsGatewayInfo`, `NdsFiberInfo` etc.); bloco `null` é omitido do payload |
| `NdsDiagnosticsResponse.kt` | Resposta tipada + `NdsResponseParser` tolerante; `NdsModuleResult.result`/`cards` seguem como `Map`/`List` genéricos |
| `NdsModuleResults.kt` | Decoders `asScoring()`, `asAi()`, `asWifiDiagnostics()` — devolvem `null` se o `module` não bater ou faltar campo obrigatório |
| `NdsDiagnosticsOutcome.kt` | `Success`, `KnownError` (shape flat `{error,message}` do ADR-017 ou envelope `{error:{code,message,retryable},request_id}`) e `UnknownError` (5xx/timeout/corpo não-JSON) |
| `NdsJson.kt` | Conversão `JSONObject`/`JSONArray` → `Map`/`List` Kotlin |
| `NdsProfileCapabilitiesMapper.kt` | `ndsCapabilities()`/`ndsProfile()` — regra `profile`/`capabilities` do payload |
| `NdsWifiScanMapper.kt` | `mapWifiScanToNds()` — `ChannelScore` → bloco `wifiScan` |
| `NdsHistoricalMapper.kt`, `NdsSnapshotCoverage.kt` | Bloco `historical` e métrica de cobertura do snapshot enviado |
| `NdsSeverityParser.kt` | `parseNdsVeredicto()` (`veredicto` → `MetricStatus`) e `MetricStatus.toDiagnosticStatus()` |
| `NdsDiagnosticsRequestMapper.kt` | `DiagnosticInput.toNdsDiagnosticsRequest()` — monta `wifiScan`, `mobile` (nunca Cell ID/TAC/MCC/MNC), `historical`, `localEquipment`, `plan`, `connection.natStatus` e `dns`. Gap conhecido: `dns.hijacked` (sem coleta real) |
| `NdsDiagnosticsResponseMapper.kt` | `NdsDiagnosticsResponse.toDiagnosticReport()` — ponte de volta para o `DiagnosticReport` que a UI lê |

## Testes

Todos usam `MockWebServer`; rodam no CI (`android-ci.yml`) sem token real.

| Teste | Cobertura |
|---|---|
| `core/nds/src/test/.../NdsClientTest.kt` | path v1/v2, headers, parsing de sucesso/erro (401/429/504/5xx), envelope canônico vs. shape antigo |
| `feature/diagnostico/src/test/.../nds/NdsDiagnosticRepositoryTest.kt` | fallback local em erro, telemetria de outcome/cobertura |
| `feature/diagnostico/src/test/.../nds/e2e/NdsE2ECenariosTest.kt` | cadeia `DiagnosticInput → NdsDiagnosticsRequest → JSON → NDS (mock) → NdsDiagnosticsResponse → DiagnosticReport` nos cenários Wi-Fi congestionado e móvel com sinal fraco |

```bash
./gradlew :core:nds:testDebugUnitTest
./gradlew :featureDiagnostico:testDebugUnitTest --tests "io.signallq.app.feature.diagnostico.nds.e2e.NdsE2ECenariosTest"
```

As respostas simuladas do E2E foram construídas localmente (o repositório irmão não publica
fixtures JSON versionadas); migrar para elas se passarem a existir.

## Autenticação (ADR-017)

Bearer token estático via `BuildConfig.NDS_API_TOKEN`, lido de `local.properties`
(`NDS_API_TOKEN=...`, gitignorado) em dev ou da variável de ambiente `NDS_API_TOKEN` em CI/release —
nunca em arquivo versionado. Placeholder vazio não quebra o build; requisição real sem token recebe
401, tratado como `KnownError`.

## Riscos e dívidas

- **Formato de erro do servidor não confirmado em produção.** `NdsClient.parseErrorOutcome` tenta o
  envelope canônico primeiro e cai para o shape flat; nenhum lança, `UnknownError` é o fallback
  final. Ver pendências no ADR-017.
- **`profile`/`capabilities` sem regra formal de mapeamento.** O módulo ainda usa o modelo antigo
  (`ndsCapabilities()`/`ndsProfile()`), aceito via alias legado; migrar para
  `capabilities`/`requested_outputs` é pendência do ADR-017.
- **Dependência temporária de `:core:diagnostico`** (ver Dependências).
