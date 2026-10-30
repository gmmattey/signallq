---
title: "Índice de schemas — SignallQ"
description: "Ponteiro para os schemas reais (Room, D1, analytics, feature flags) na origem; não copia conteúdo."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Schemas do monorepo SignallQ — índice

**Fonte de verdade:** os caminhos de origem abaixo. Este arquivo só aponta para eles; copiar o conteúdo divergiria na primeira migration (o JSON do Room, por exemplo, é regerado a cada build por `exportSchema`).
**Escopo:** Room (Android), D1 (Cloudflare), eventos de analytics e catálogo de feature flags.
**Substitui:** a versão 1.x, que listava migration por migration e a história de renomeações do banco Room (recuperável via `git log -- docs/CONTRATOS/schemas/README.md`).

| Schema | Estado (conferido em 2026-10-04) | Origem | Consumidores |
|---|---|---|---|
| Room — `SignallQDatabase` | **v22** (`version = 22`; JSONs `9`–`22`) | `android/core/database/schemas/io.signallq.app.core.database.SignallQDatabase/` e `SignallQDatabase.kt` | App Android (`:app`, DAOs usados por `:featureHistory`, `:featureDiagnostico`, `:featureDevices`) |
| Room — `VelooDatabase`, `LinkaDatabase` | Só histórico de schema gerado pelo Room, sem classe `.kt` correspondente | `android/core/database/schemas/io.signallq.app.core.database.VelooDatabase/` (v10) e `.../io.linka.app.kotlin.core.database.LinkaDatabase/` (v1–v10) | Nenhum |
| D1 `signallq-admin-db` | 22 migrations (`001_sig143.sql` … `022_gh1478_remote_config_write_rate_limit.sql`) | `integrations/cloudflare/signallq-admin-worker/migrations/` | `signallq-admin-worker` e painel `buildea-admin`; tabelas descritas em [`technical/admin-api-schema.md`](../../technical/admin-api-schema.md) |
| D1 `signallq-diagnostic-db` | 9 migrations (`001_gh952_diagnostic_rules.sql` … `009_gh1461_provider_audit_log.sql`) | `integrations/cloudflare/signallq-diagnostic-worker/migrations/` | `signallq-diagnostic-worker` e seus endpoints `/admin/*` |
| Eventos GA4 (Firebase Analytics) | Documento vivo, sem versão formal | [`technical/analytics-events-schema.md`](../../technical/analytics-events-schema.md) (eventos instrumentados) e [`technical/analytics-events.md`](../../technical/analytics-events.md) (funil e contrato-alvo) | `FirebaseAnalyticsTracker` → GA4; espelho em `analytics_events` (D1 admin, migration `006_sig134.sql`) |
| Catálogo de feature flags do Consumer | `schemaVersion` `"1.0"`, 12 flags | `android/core/featureflags/src/main/resources/featureflags/consumer-catalog.json`; schema em [`technical/feature-flags-remote-config.md`](../../technical/feature-flags-remote-config.md) | `:core:featureflags` (app) e `signallq-admin-worker` (`/admin/firebase/feature-flags/*`) |

Contratos HTTP (OpenAPI) vivem em [`../openapi/`](../openapi/): um arquivo por worker (`signallq-admin-api`, `signallq-diagnostic-worker`, `ai-diagnosis-worker`, `game-latency-probe-worker`, `signallq-privacy-worker`).
