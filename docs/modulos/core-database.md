---
title: "Módulo :coreDatabase"
description: "Banco Room local do Consumer: 10 entidades, 8 DAOs, schema na versão 22 com 21 migrations encadeadas."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:coreDatabase`

- **Caminho físico:** `android/core/database/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.database`
- **Tipo:** biblioteca Android

## Responsabilidade

Define o banco Room local do app Consumer (`SignallQDatabase`), suas Entities, Daos e toda a cadeia de migrations, além da fábrica `CoreDatabaseModulo.criarBanco(context)`. É a única fonte de persistência estruturada/relacional do Consumer.

Não é dele: preferências chave-valor (`:coreDatastore`), regras de negócio sobre os dados persistidos (ficam nas features e em `:core:diagnostico`). Também não expõe Repository: os Daos são consumidos diretamente pelas camadas acima.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `androidx.core.ktx` | utilitários de plataforma |
| `androidx.room.runtime` (`api`) | runtime Room, reexportado aos consumidores |
| `androidx.room.ktx` (`api`) | suporte a coroutines/`Flow` nos Daos, reexportado |
| `androidx.room.compiler` (`kapt`) | geração de código Room |
| `junit` (test) | teste JVM de `MedicaoEntity` |
| `androidx.room.testing` (androidTest) | `MigrationTestHelper` nos testes de migration |
| `kotlinx.coroutines.test` (androidTest) | `runTest`/`Flow.first()` em `ChatSessionDaoTest` (dependência que faltava, corrigida na GH#1228 Fase 3) |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding instrumentado |

Nenhuma dependência de outro módulo do monorepo. `ResolvedorNetworkId` (`src/main/kotlin/io/signallq/app/core/database/rede/`) foi promovido de `:featureSettings` pra cá na issue #1707 (Task 2.0.09e, épico #1647) — função pura, sem dependência Android, reaproveitada tanto por `ConnectionProfile` (`:featureSettings`, via `implementation(project(":coreDatabase"))`) quanto por `MedicaoEntity.networkId`.

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |
| `:featureDevices`, `:featureDiagnostico`, `:featureHistory`, `:featureSpeedtest` | `implementation` |

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/database/SignallQDatabase.kt` | `@Database` com 10 entities, `version = 22`, `exportSchema = true`; expõe os 8 Daos |
| `src/main/kotlin/io/signallq/app/core/database/CoreDatabaseModulo.kt` | define as 21 migrations e monta o `Room.databaseBuilder` (arquivo `linkaKotlin.db`) |
| `src/main/kotlin/io/signallq/app/core/database/MedicaoEntity.kt` / `MedicaoDao.kt` | histórico de medições de speedtest/monitoramento; `networkId` (GH#1707) identifica a rede da medição pra comparação de reteste; `perdaConfianca`/`latenciaP95Ms`/`latenciaMaxMs`/`latenciaPicos` (migração 20→21, `.agents/architecture-plan.md` "Confiabilidade estatística do diagnóstico de rede") — colunas nullable, `NULL` para todo registro anterior à migração, nunca inferidas retroativamente |
| `src/main/kotlin/io/signallq/app/core/database/rede/ResolvedorNetworkId.kt` | resolve `networkId` estável (BSSID/SSID Wi-Fi ou operadora móvel) — promovido de `:featureSettings` na issue #1707 |
| `src/main/kotlin/io/signallq/app/core/database/ApelidoDispositivoEntity.kt` / `ApelidoDispositivoDao.kt` | apelido por MAC de dispositivo da rede local |
| `src/main/kotlin/io/signallq/app/core/database/chat/ChatSessionEntity.kt`, `ChatMessageEntity.kt`, `ChatSessionDao.kt` | sessões e mensagens do chat de diagnóstico |
| `src/main/kotlin/io/signallq/app/core/database/recommendation/RecommendationHistoryEntity.kt` / `Dao` | histórico de exibições do Recommendation Engine (cooldown, limites, feedback — issues #790/#812) |
| `src/main/kotlin/io/signallq/app/core/database/connectivity/ConnectivityDiagnosisHistoryEntity.kt` / `Dao` | histórico do diagnóstico de conectividade (GH#1512), só campos sanitizados |
| `src/main/kotlin/io/signallq/app/core/database/provider/ProviderDirectoryCacheEntity.kt` / `Dao` | cache local do diretório remoto de provedores (GH#1462) |
| `src/main/kotlin/io/signallq/app/core/database/analytics/AnalyticsOutboxEntity.kt` / `Dao` | outbox de eventos de analytics com retry (`enqueue`/`due`/`acknowledge`/`defer`/`clear`) |
| `src/main/kotlin/io/signallq/app/core/database/wificasa/MapeamentoWifiEntity.kt`, `MarcadorMapeamentoEntity.kt`, `MapeamentoWifiDao.kt` | Wi-Fi Casa: sessão de mapeamento espacial de sinal + marcadores filhos; `comparadoComSessaoId` liga a sessão "depois" à "antes" (`docs/features/wifi-casa.md`) |

### Schema Room

- **Versão atual:** `22`
- **`exportSchema`:** `true`; `room.schemaLocation` = `$projectDir/schemas`, `room.incremental` = `true`
- **Arquivo do banco:** `linkaKotlin.db` (nome legado, mantido para não quebrar bases instaladas)

| Entity | Tabela real |
|---|---|
| `MedicaoEntity` | `medicao` |
| `ApelidoDispositivoEntity` | `apelido_dispositivo` |
| `ChatSessionEntity` | `chat_sessions` |
| `ChatMessageEntity` | `chat_messages` |
| `RecommendationHistoryEntity` | `recommendation_history` |
| `ConnectivityDiagnosisHistoryEntity` | `connectivity_diagnosis_history` |
| `ProviderDirectoryCacheEntity` | `provider_directory_cache` |
| `AnalyticsOutboxEntity` | `analytics_outbox` |
| `MapeamentoWifiEntity` | `mapeamento_wifi` |
| `MarcadorMapeamentoEntity` | `marcador_mapeamento` |

**Migrations:** 21 objetos `Migration`, de 1→2 até 21→22, todos registrados por `addMigrations` em `criarBanco`. Não há `fallbackToDestructiveMigration`. As mais recentes são aditivas: `MIGRATION_19_20` (`CREATE INDEX IF NOT EXISTS` em `analytics_outbox.nextAttemptAtEpochMs`, GH#1787), `MIGRATION_20_21` (4 colunas nullable em `medicao`: `perdaConfianca`, `latenciaP95Ms`, `latenciaMaxMs`, `latenciaPicos` — `NULL` para todo registro anterior, nunca inferidos retroativamente) e `MIGRATION_21_22` (cria `mapeamento_wifi` e `marcador_mapeamento`).

**Testes instrumentados** (`src/androidTest/`): migrations 9→10 e 13→14 até 21→22 (10 das 21 migrations têm teste dedicado), `ChatSessionDaoTest`, `AnalyticsOutboxDaoTest`, `RecommendationHistoryDaoTest`, `MedicaoDaoNetworkIdTest`, `MedicaoDaoPerdaConfiancaTest` e `MapeamentoWifiDaoTest`. O sourceSet `androidTest` aponta `assets.srcDirs` para `schemas/`, requisito do `MigrationTestHelper`.

## Features

Features que listam este módulo (comportamento e regras de negócio ficam na página da feature): [`dispositivos-rede`](../features/dispositivos-rede.md), [`historico-laudo`](../features/historico-laudo.md), [`monitoramento-alertas`](../features/monitoramento-alertas.md), [`wifi-casa`](../features/wifi-casa.md).

## Riscos e dívidas

- **`Migration17Para18Test` permanece vermelho — de propósito.** Roda `runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)` contra o `18.json` imutável, publicado antes de `AnalyticsOutboxEntity` declarar o `@Index` que a 17→18 já criava por SQL bruto; o `MigrationTestHelper` reporta "Migration didn't properly handle: analytics_outbox" para sempre. É o registro histórico da inconsistência (corrigida a partir da v20 por `MIGRATION_19_20`, validada em `Migration19Para20Test`). Não remover nem "consertar".
- **Schemas `9.json` e `15.json` reconstruídos manualmente (GH#1787):** nunca existiram sob o FQN atual (a classe já foi `LinkaDatabase` e `VelooDatabase`); o `identityHash` de ambos é placeholder.
- **Schemas de nomes antigos ainda versionados:** `schemas/io.linka.app.kotlin.core.database.LinkaDatabase/` (`1..10`) e `schemas/io.signallq.app.core.database.VelooDatabase/` (`10.json`) — três nomes de banco na história (Linka → Veloo → SignallQ).
- **Nome legado em produção:** o arquivo continua `linkaKotlin.db`; trocar exige migração de dados.
- **Cobertura parcial de migrations:** nenhuma de 1→2 a 8→9 nem 10→11..12→13 tem teste dedicado.
- **`networkId` (GH#1707) é `null`** para medições do `MonitoramentoWorker` (sintéticas, não leem SSID/BSSID/operadora) e para linhas anteriores à migração 18→19; a comparação de reteste deve tratar `null` como "sem par comparável", nunca inventar rede.
