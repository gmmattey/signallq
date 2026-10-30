---
title: "Schema de Eventos GA4 — AnalyticsTracker (SIG-134)"
description: "Eventos genéricos do AnalyticsTracker (feature_used, screen_view, sessão, crash, bateria, feature_blocked_remote) e user properties do app Android."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Schema de Eventos GA4 — `AnalyticsTracker` (SIG-134)

**Fonte de verdade:** código — interface `AnalyticsTracker` (`android/core/network/.../AnalyticsTracker.kt`), implementação `FirebaseAnalyticsTracker` e `CompositeAnalyticsTracker` (`android/app/.../analytics/`), `DistributionChannel.kt`.
**Escopo:** eventos genéricos que alimentam a `ProductAnalyticsPage` do painel Admin. Os eventos do funil SIG-155, da jornada guiada, do NDS e de recomendação estão em [`analytics-events.md`](analytics-events.md).
**Substitui:** a versão anterior, que repetia o funil e o endpoint `/flags` (esse vive em [`feature-flags-remote-config.md`](feature-flags-remote-config.md)).

Sem PII. `session_id` é um UUID gerado por instância de processo em `FirebaseAnalyticsTracker`; não persiste entre sessões do app.

## Eventos

| Evento | Parâmetros | Disparo |
|---|---|---|
| `feature_used` | `feature_id`, `session_id`, `app_version`, `timestamp` (ms) | `MainActivity`, ao acionar a feature; ver `feature_id` abaixo |
| `screen_view` | `screen_name`, `session_id`, `app_version` | `AppShell` (`LaunchedEffect` da raiz selecionada) e abertura de ferramenta, via callback `onScreenView` |
| `app_session_start` | `session_id`, `app_version` | `MainActivity.onCreate` |
| `app_session_end` | `session_id`, `app_version` | `registrarSessionEnd()` |
| `feature_crash` | `feature_id` (derivado da tag Timber), `error_type` (`simpleName` ou `"LoggedError"`), `app_version` | `ReleaseTree` (Timber, erros nível ERROR+ em release); o mesmo erro vai ao Crashlytics |
| `battery_snapshot` | `level` (0–100), `charging`, `session_id` | `MainActivity.onCreate`, via `ACTION_BATTERY_CHANGED` |
| `feature_blocked_remote` | `feature_id` (id curto de módulo, ex. `wifi`, `dns`), `session_id`, `app_version` | feature bloqueada por flag remota (GH#1480) |

**`feature_id` em `feature_used`** (conferido no código em 2026-10-04): `speedtest`, `speedtest_iniciado`, `speedtest_completou`, `speedtest_compartilhou`, `wifi`, `dns`, `fibra`, `historico`, `review_prompt_google_play`. `diagnostico` não é mais disparado por `feature_used` (o diagnóstico usa o funil SIG-155).

**`screen_name`** (de `AppShellNavigation.kt` e `TipoFerramenta.kt`): raízes `home`, `speedtest`, `historico`, `ferramentas`; ferramentas `sinal_wifi`, `dispositivos`, `equipamento_internet`, `ping`, `dns`, `laudo`, `monitoramento`, `modo_gamer`.

## User properties (GH#1360)

Definidas uma vez por sessão em `registrarSessionStart()` via `setUserProperty()`:

| Property | Valores | Fonte |
|---|---|---|
| `environment` | `production`, `staging` | `environmentFor(distChannel)`; `production` só quando instalado pela Play Store |
| `dist_channel` | `play_store`, `sideload`, `unknown` (ou nome do pacote instalador) | `distributionChannel(context)` |
| `build_type` | `debug`, `release` | `BuildConfig.BUILD_TYPE` |

As mesmas funções classificam o envio ao `signallq-admin-worker` (`POST /ingest/analytics`) no `CompositeAnalyticsTracker`; Firebase e `/ingest/*` seguem como sistemas paralelos, sem correlação cruzada.
**Pendência manual (console GA4):** registrar as três properties como custom dimensions para consultá-las via API/relatórios.

## Arquitetura

`AnalyticsTracker` (interface em `:coreNetwork`) → `FirebaseAnalyticsTracker` (`@Singleton`, `:app`), composto com o envio ao worker por `CompositeAnalyticsTracker`; binding em `AppModule`. Módulos `:feature*` recebem `AnalyticsTracker` por Hilt e nunca dependem de Firebase diretamente.
