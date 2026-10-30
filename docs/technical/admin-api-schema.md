---
title: "Admin API — guia do worker signallq-admin"
description: "Mapa de rotas, autenticação, regras de negócio sem equivalente no OpenAPI e schema D1 do signallq-admin-worker. Request/response de cada endpoint vivem no OpenAPI."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-10"
version: "2.1.0"
---

# Admin API — guia do worker `signallq-admin`

**Fonte de verdade:** código (`integrations/cloudflare/signallq-admin-worker/src/index.ts`: `ROUTES`, `INGEST_ROUTES`, `fetch`) e migrations em `integrations/cloudflare/signallq-admin-worker/migrations/`.
**Contrato formal de request/response:** [`CONTRATOS/openapi/signallq-admin-api.yaml`](../CONTRATOS/openapi/signallq-admin-api.yaml). Este arquivo **não** repete schemas de payload; documenta o que o OpenAPI não cobre (rotas ausentes dele, autenticação, regras de negócio, D1).
**Substitui:** a versão 1.x deste arquivo (1470 linhas, com request/response por endpoint duplicando o OpenAPI), recuperável via `git log -- docs/technical/admin-api-schema.md`.
**Consumidores:** painel Admin (`buildea-admin`) e app Android (`/ingest/*` e rotas públicas).
**Banco:** Cloudflare D1 `signallq-admin-db`. Firebase e Google Play são consultados por API e cacheados em `admin_settings` / `integration_metric_snapshots`.

## Superfícies e autenticação

| Superfície | Prefixo | Autenticação |
|---|---|---|
| Painel admin | `/admin/*` | Cookie de sessão `session` (httpOnly) |
| Proxy do diagnóstico | `/admin/diagnostic/*` | Sessão; repassado ao `signallq-diagnostic-worker` (contrato em `CONTRATOS/openapi/signallq-diagnostic-worker.yaml`) |
| Ingest do app | `/ingest/*` | `Authorization: Bearer <INGEST_KEY>` |
| Públicas | `/feature-flags`, `/flags`, `/local-ads`, `/app-updates` | Sem auth |
| Health | `/health` | `Bearer <ADMIN_SECRET>` (legado, só monitoramento externo) |

- **Sessão:** `POST /admin/auth/login` devolve cookie `HttpOnly; Secure; SameSite=None` com validade de 7 dias; o frontend usa `credentials: "include"`. Mais de 5 tentativas de login em 15 min por IP → `429`. `POST /admin/auth/users` exige role `admin` (único role ativo; `409` se o e-mail já existe). Troca de senha: `POST /admin/auth/password` com `currentPassword`/`newPassword`. Demais: `POST /admin/auth/logout`, `GET /admin/auth/me`.
- **`INGEST_KEY`:** vai no APK via `BuildConfig`; escopo limitado a `/ingest/*`, sem acesso aos dados do painel.
- **Convenção de campos:** respostas em `camelCase`; o D1 usa `snake_case`. Exceção: `/admin/metrics/diagnostics` devolve colunas do D1 em `snake_case`.
- **Erros:** corpo `{ "error": "mensagem" }`. Status usados: `400` body/parâmetro inválido, `401` sem sessão/credencial, `403` sem permissão, `404` rota inexistente, `409` conflito, `429` rate limit, `500` erro interno.

## Mapa de rotas

Conferido contra o código em 2026-10-04. Rotas marcadas **sem OpenAPI** não têm schema formal; a regra está neste arquivo ou só no código.

| Grupo | Rotas (`/admin` salvo indicação) |
|---|---|
| Métricas | `GET metrics/{overview, diagnostics, diagnostics/summary, ai-usage, timeline, network, regions, top-issues, alerts, ai-costs, ai-providers, ai-quota, ai-usage/timeline, ai-usage/records, operators, app-versions, intelligence, errors, errors/timeline}`, `GET diagnostics/intelligence` |
| Analytics do app | `GET {metrics/,}analytics/{product, battery, devices}`, `GET metrics/analytics/speedtest-funnel` |
| Alertas e erros | `GET alerts`, `POST alerts/{id}/resolve`, `POST errors/{id}/resolve` (**sem OpenAPI**) |
| Saúde | `GET system-health`, `GET system-health/history`, `POST system-health/snapshot`, `GET cloudflare-usage`, `GET integrations/readiness` |
| Firebase | `GET integrations/firebase/{status, analytics, acquisition, screens, retention, crashlytics, versions, crash-issues, management/status, remote-config/status, app-check/status, app-distribution/status, fcm-delivery/status}` e `POST` de `sync` correspondentes |
| Google Play | `GET integrations/google-play/{status, reviews, tracks/status, vitals/status, vitals/crash-rate/status, store-listing/status}`; `POST integrations/google-play/{sync, tracks/sync, tracks/backfill, vitals/sync, vitals/crash-rate/sync, store-listing/sync}` |
| Remote Config e flags | `GET firebase/remote-config`, `GET firebase/remote-config/versions`, `POST firebase/remote-config/{validate, publish, rollback}`, `GET firebase/feature-flags/catalog`, `POST firebase/feature-flags/sync`, `GET feature-flags`, `PUT feature-flags/{flag}` (ver [`feature-flags-remote-config.md`](feature-flags-remote-config.md)) |
| Configuração | `GET/POST settings` |
| Anúncios locais | `GET/POST local-ads`, `PUT/DELETE local-ads/{id}` (**sem OpenAPI**) |
| Releases | `GET/POST app-updates`, `POST app-updates/{releaseId}/{deactivate, push}` (**sem OpenAPI**; regras abaixo) |
| Ingest | `POST /ingest/{diagnostic, ai-usage, analytics, waitlist}` (`waitlist` **sem OpenAPI**) |
| Públicas | `GET /feature-flags` (só `scope: public`), `GET /flags` (`key` + `enabled` da tabela `feature_flags`), `GET /local-ads`, `GET /app-updates?product=&channel=` (**sem OpenAPI**) |

Rotas do código sem schema no OpenAPI: `system-health*`, `cloudflare-usage`, `metrics/ai-quota`, `metrics/regions`, `metrics/network`, `metrics/errors/timeline`, `analytics/devices`, `analytics/speedtest-funnel`, `errors/{id}/resolve`, `local-ads*`, `app-updates*`, rotas públicas e `ingest/waitlist`, além de várias de `integrations/firebase/*` e `integrations/google-play/*` (sub-integrações `management`, `app-check`, `app-distribution`, `fcm-delivery`, `vitals`, `store-listing`, `tracks`).

Divergências de nome entre OpenAPI e código: o OpenAPI documenta `/auth/*` e `/admin/metrics/networks`; o código serve `/admin/auth/*` e `/admin/metrics/network`. O código vence.

## Regras de negócio sem equivalente no OpenAPI

### `GET /admin/integrations/readiness`

Snapshot somente leitura da prontidão das integrações Android. Não chama Firebase, BigQuery nem Google Play: consolida credenciais e o estado de sync já persistido em `admin_settings`.

| Campo | Semântica |
|---|---|
| `generatedAt` | Instante ISO 8601 do snapshot. |
| `environment` / `platform` | Contexto do contrato (`worker` / `android`); não é alegação de deploy em produção. |
| `apiReachability` | `not_configured`, `not_verified` ou `reachable`. `reachable` é evidência local de sync concluído; a rota não faz probe externo e nunca declara API inacessível. |
| `state` | `not_configured`, `not_synced`, `stale`, `synced_without_data` ou `ready`. `ready` só com credenciais, sync em até 48h, timestamp não futuro e `recordsReceived > 0`. |
| `coverage` | Total, prontas, não prontas e contagem por estado, para o consumidor não tratar a presença da rota como cobertura completa. |

### Catálogo de releases (`app_releases`, GH#1471/#1312)

Catálogo remoto por produto+canal, lido pelo app Android (comparação sempre por `versionCode`, nunca por `versionName`) e administrado via pipeline autenticado, sem painel dedicado. Canais: `internal | alpha | beta | production`. Histórico append-only; `status` (`active | superseded | deactivated`) define a linha comunicada. O índice único parcial `idx_app_releases_active_unique` garante no máximo uma `active` por `product`+`channel`.

- **`GET /app-updates`** (público): `product` e `channel` obrigatórios (`400` se ausentes ou canal fora do enum). Devolve a release `active` ou `release: null` (resposta válida, não erro). Campos: `product, channel, versionName, versionCode, publishedAt, releaseNotes, storeUrl, notificationEnabled, releaseId, reminderCampaignId`.
- **`GET /admin/app-updates`**: histórico de todos os `status`, mais recente primeiro; filtros `product`, `channel`, `limit` (padrão 50, máx. 200); acrescenta `status, publishedBy, pushStatus, pushSentAt, pushError, createdAt, updatedAt`.
- **`POST /admin/app-updates`**: `releaseId` é sempre derivado (`${product}-${channel}-${versionCode}`), nunca aceito do body. Contra a `active` atual: `versionCode` maior → nova `active` e anterior `superseded` (`201`); igual → correção in-place (`200`); menor → `409`, salvo `confirmDowngrade: true`. `400` para campo ausente/inválido ou `storeUrl` que não seja http(s).
- **`POST /admin/app-updates/{releaseId}/deactivate`**: desliga a comunicação sem apagar o registro; idempotente.
- **`POST /admin/app-updates/{releaseId}/push`**: exige `confirmed: true` (confirmação manual de que a versão já está na Google Play; publicar não dispara push sozinho). Envia `APP_UPDATE_AVAILABLE` por FCM ao tópico `app_updates_<product>_<channel>` com `type, product, channel, versionCode, releaseId`. O resultado é sempre gravado na release (`pushStatus`/`pushSentAt`/`pushError`) sem alterar o catálogo. `409` se a release não está `active` ou `notificationEnabled` é falso; `404` se inexistente. Requer `FIREBASE_CLIENT_EMAIL`/`FIREBASE_PRIVATE_KEY` e a FCM API habilitada no projeto GCP; sem isso retorna `pushError: "fcm_error_*"`.

### Ingest

- **`POST /ingest/diagnostic`**: único campo obrigatório é `id`; os demais têm default no D1 (`INSERT OR REPLACE`). A UF vem de `request.cf.regionCode` (nunca o IP) e só aceita as 27 UFs de `UF_WHITELIST`.
- **`POST /ingest/ai-usage`**: obrigatórios `id` e `model`; `cost_usd` é calculado pelo worker (`costForModel()`) quando ausente; `status` (`success` | `error`, default `success`) e `error_message` permitem auditar falhas de inferência (migration `009_gh421.sql`).
- Eventos de analytics: ver [`analytics-events-schema.md`](analytics-events-schema.md).

### Informe diário de visitas do site (Discord)

Cron `0 12 * * *` (09:00 BRT) chama `sendDailyVisitReport` (`src/dailyVisitReport.ts`): envia um embed ao webhook `DISCORD_WEBHOOK_URL` (secret; sem ela o informe é ignorado) com visitas, cliques em Baixar e testes de velocidade do `signallq.com`, de D-1 (dia em America/Sao_Paulo) e acumulado.

- **Fonte:** `analytics_events` com `platform = 'web'`. Visita = sessões distintas em `session_start`; cliques = `feature_used` com `feature_id = 'download_app_clicado'`; testes = `feature_id = 'teste_velocidade_iniciado'`.
- **Escalas de tempo:** o site grava `created_at` em milissegundos e o app em segundos; a janela do dia cobre as duas.
- **O acumulado** inclui sessões do site anterior à reformulação de 2026-08-27.
- O Lagcheck envia o próprio informe, no mesmo canal, por Worker independente.

## Schema do Cloudflare D1

Migrations `001`–`022`. `diagnostic_sessions` e `ai_usage` têm tabela-base criada fora das migrations; elas só acrescentam colunas. Tabelas existentes: `diagnostic_sessions`, `ai_usage`, `analytics_events`, `alerts`, `system_errors`, `system_health_snapshots`, `admin_users`, `admin_sessions`, `auth_rate_limit`, `feature_flags`, `feature_flag_audit`, `app_releases`, `local_ads`, `play_console_tracks`, `google_play_reviews`, `integration_metric_snapshots`, `waitlist_signups`, `remote_config_audit_log`, `remote_config_write_rate_limit` e `admin_settings` (cache de sync e configurações; `key='admin'` guarda os settings). As colunas estão no `CREATE`/`ALTER TABLE` da migration; abaixo, só o que não é óbvio.

- **`diagnostic_sessions`**
  - `status`: veredito do motor local (`excelente | bom | regular | critico | inconclusivo | failed | unknown`, GH#764), não o ciclo de vida da sessão.
  - `issues`: JSON array serializado. `resolved`: 0 aberto, 1 resolvido. `device_id`: hash anônimo.
  - `rssi`, `banda_wifi`, `padrao_wifi` (migration 007): nulos quando o Android não expõe o dado; ausência não é zero.
  - `platform`: `android` (default) ou `web` (dado histórico do extinto PWA).
  - `play_track` (migration 012): trilha resolvida por `play_console_tracks.version_code`; `NULL` = ainda não mapeada, **nunca assumir `production`**.
  - `uf` (migration 014): UF aproximada; `''` fora da whitelist.
- **`ai_usage`**: `session_id` referencia `diagnostic_sessions.id`; `status` e `error_message` (migration 009); `platform`, `play_track`, `dist_channel`, `build_type`, `device_id` (migrations 007, 011, 012).
- **`play_console_tracks`**: mapa `version_code → track` sincronizado pela Android Publisher API (`POST .../google-play/tracks/sync`). Alimenta o backfill de `play_track` em `diagnostic_sessions`, `ai_usage` e `analytics_events`, que é **manual** (`POST .../tracks/backfill`). `track` aceita trilhas custom, não só as quatro padrão.
- **`system_health_snapshots`**: série histórica por dependência (`service`: `d1 | firebase | bigquery`; `status`: `ok | error | not_configured`), gravada pelo cron de 15 min (`scheduled` em `src/index.ts`). O cron `0 6 * * *` faz o sync diário de telemetria Firebase/Google Play.
- **`system_errors`**: `id` é hash determinístico de `source:message` (deduplicação); `first_seen`/`last_seen` em milissegundos; resolução em `resolved, resolved_by, resolved_at, resolution_note`; `category` (migration 010).
- **`app_releases`**: `release_id` determinístico; `product` = `signallq` (`signallq_pro` é legado, Pro descontinuado pelo ADR-016); `reminder_campaign_id` distingue lembrete novo de duplicata (dedup Android); `push_status` = `NULL | sent | error | not_configured`.

## Como manter

Mudou rota, campo ou autenticação do worker: atualize o OpenAPI (schema) e, se for rota nova ou regra de negócio, a tabela de rotas e as regras acima, no mesmo PR. O frontend do painel é documentado em `buildea-admin`.
