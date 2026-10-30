---
title: "Documentação técnica — SignallQ consumer"
description: "Stack, build, persistência, integrações Cloudflare, analytics e segurança, do código real"
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-10"
version: "1.1.1"
---

# Documentação técnica — SignallQ consumer

- **Fonte de verdade:** o código. Este documento é derivado dele. Números vêm do bloco de
  inventário abaixo, **gerado** por `scripts/gerar-inventario-docs.sh` — não editar à mão.
- **Escopo:** app consumer Android (`io.signallq.app`) e backend Cloudflare. Não cobre SignallQ Pro
  (descontinuado permanentemente, ver ADR-016), Admin (`buildea-admin`) nem o site público (`web/`, com documentação própria em `web/docs/` e `web/AGENTS.md`).
- **Perspectiva do usuário:** `FUNCIONAL.md`. **Detalhe por módulo:** `ARQUITETURA/MODULOS/`.

<!-- INVENTARIO:INICIO — gerado por scripts/gerar-inventario-docs.sh, nao editar a mao -->

> **Inventário gerado do código.** Não editar manualmente — rode
> `scripts/gerar-inventario-docs.sh`. Cada número abaixo sai da fonte citada.

| Fato | Valor | Fonte |
|---|---|---|
| versionName / versionCode | **1.0.9** / **89** | `android/gradle/libs.versions.toml` |
| compileSdk / minSdk / targetSdk | 37 / 24 / 36 | `android/gradle/libs.versions.toml` |
| Compose BOM · Room · Hilt | 2026.06.01 · 2.8.4 · 2.60.1 | `android/gradle/libs.versions.toml` |
| Módulos Gradle | **22** | `android/settings.gradle.kts` |
| Workers Cloudflare | 5 | `integrations/cloudflare/*/wrangler.toml` |
| Tabelas D1 | 38 — 20 admin + 18 diagnostic | `*/migrations/*.sql`, `*/schema.sql` |
| Contratos OpenAPI | 5 contratos · **108** endpoints | `docs/CONTRATOS/openapi/` |
| Arquivos `.kt` em caminho legado `io/veloo` | 0 (sendo 0 em `src/main`) | dívida conhecida — higiene §4.1 |

**Módulos (22):** :app :core:diagnostico :core:featureflags :core:nds :core:probejogo :core:relatorio :coreDatabase :coreDatastore :coreNetwork :corePermissions :coreRecommendation :coreTelephony :featureDevices :featureDiagnostico :featureDns :featureFibra :featureHistory :featureHome :featureRouter :featureSettings :featureSpeedtest :featureWifi

**Workers:**

| Diretório | `name` no wrangler |
|---|---|
| `ai-diagnosis-worker` | `linka-ai-diagnosis-worker` |
| `game-latency-probe-worker` | `signallq-game-latency-probe` |
| `signallq-admin-worker` | `signallq-admin` |
| `signallq-diagnostic-worker` | `signallq-diagnostic` |
| `signallq-privacy-worker` | `signallq-privacy` |

**Contratos:**

| Arquivo | Versão | Endpoints |
|---|---|---:|
| `ai-diagnosis-worker.yaml` | 2 | 2 |
| `game-latency-probe-worker.yaml` | 1 | 2 |
| `signallq-admin-api.yaml` | 2.2.0 | 59 |
| `signallq-diagnostic-worker.yaml` | 1 | 43 |
| `signallq-privacy-worker.yaml` | 1 | 2 |

<!-- INVENTARIO:FIM -->

---

## 1. Objetivo técnico

Documentar como o app é construído e integrado, com o código como fonte, para que qualquer pessoa
ou agente entenda a stack sem reler o repositório inteiro — e para servir de checagem factual
contra desatualização.

## 2. Visão geral

### 2.1 Identidade

| Campo | Valor |
|---|---|
| Estrutura | Monorepo — `android/`, `integrations/cloudflare/`, `packages/`, `scripts/`, `docs/` |
| Package / applicationId / namespace | `io.signallq.app` — **identificador técnico, nunca renomear** (quebra Firebase e assinatura) |
| Marca | Linka → Veloo → **SignallQ** |
| Repositório | `buildea-labs/signallq` |

**Identificadores técnicos preservados por compatibilidade de infra** — parecem marca antiga, são
técnicos: banco `linkaKotlin.db`, DataStore `linkaPreferencias`, canais de notificação `linka_*`,
Worker `linka-ai-diagnosis-worker`.

**Path físico ↔ package Kotlin alinhados:** todos os arquivos `.kt` residem em
`.../kotlin/io/signallq/app/...`, coerente com `package io.signallq.app` (migração concluída em
2026-08-15, issue #1645).

### 2.2 Stack

| Tecnologia | Versão | Papel |
|---|---|---|
| Kotlin | 2.4.20 (kapt 2.3.21) | Linguagem |
| AGP | 9.2.1 (application) / 9.3.1 (library) | Build |
| Compose (plugin) | 2.4.20 | Compilador Compose |
| Compose BOM | ver inventário | UI declarativa |
| Material 3 | via BOM (+ `com.google.android.material` 1.14.0) | Design system |
| Room | ver inventário | Persistência local |
| DataStore Preferences | 1.2.1 | Preferências |
| Hilt / Dagger | ver inventário | Injeção de dependência |
| WorkManager | 2.11.2 | Trabalho em segundo plano |
| Firebase BOM | 34.15.0 | Analytics, Crashlytics, Remote Config |
| Timber | logging (ver `ADR-001`) |
| OkHttp | **uso parcial — ver 2.3** | HTTP |

### 2.3 HTTP: duas pilhas convivem

Detalhe que costuma ser documentado errado: **não existe uma única pilha HTTP**.

- **`:coreNetwork` majoritariamente não usa OkHttp.** As sondagens de rede (gateway, DNS do
  sistema, IP externo, hostname) usam `HttpURLConnection`, `Socket` e `InetAddress` puros,
  amarrados à `Network` sob análise — necessário para medir a interface correta em vez da rota
  padrão do sistema. Oito timeouts constantes: passo 2500 ms, global 8000 ms, gateway 1200 ms,
  DNS 1500 ms, IP externo 1500 ms, hostname 2500 ms, RTT de gateway 1000 ms, varredura Wi-Fi
  10 000 ms. **Exceção (issue #1811, Task 1):** `DohFallbackProbe` — o único probe de
  `connectivity/` que faz uma requisição DoH real contra `cloudflare-dns.com` — usa OkHttp
  (timeout configurável por instância, não singleton), porque não precisa amarrar à `Network`
  sob análise da mesma forma que os outros (é sempre uma consulta contra o resolvedor público).
- **OkHttp é usado nas chamadas a Workers**, em `:featureDiagnostico`.
- **`:featureDevices` fixa `okhttp:5.5.0` direto no `build.gradle.kts`**, fora do version catalog —
  hoje igual ao `libs.okhttp`, mas diverge no próximo bump. Dívida registrada.

### 2.4 Diagnóstico de conectividade: dois motores paralelos

Dívida arquitetural conhecida e registrada (issue #1817) — dois orquestradores independentes
sabem rodar a mesma sequência de sondagens (gateway → DNS → rota externa → hostname/captive
portal), por motivos históricos diferentes, até serem unificados.

**`ConnectivityDiagnosisEngine`/`ConnectivityDiagnosisRunner`** (`:coreNetwork`,
`connectivity/`) — motor original, consumido em produção por `AppShellMedicaoGuiada` (medição
guiada de Wi-Fi, chamada direta do Runner) e por `ConnectivityBlockingPolicy` (`:featureSpeedtest`,
extensão sobre `ConnectivityDiagnosis` — a *saída* do engine, não o engine em si — chamada por
`MainViewModel` e `SpeedtestViewModel` para decidir se bloqueia o speedtest). Roda a cadeia inteira
numa única chamada suspend e devolve só o resultado agregado ao final — não expõe progresso etapa
a etapa.

**`DiagnosticoOfflineExecutorReal`** (`io.signallq.app.diagnosticooffline`, em `:app`) — motor
novo (issue #1811, Task 4), chama os mesmos probes (`GatewayReachabilityProbe`,
`DnsReachabilityProbe`/`DohFallbackProbe`, `ExternalIpReachabilityProbe`,
`HostnameReachabilityProbe`) diretamente, na mesma ordem, mas devolve o resultado de UMA etapa
por chamada — necessário para o diagnóstico offline guiado (5.5b em `FUNCIONAL.md`) mostrar
progresso real conforme cada sondagem termina, sem reescrever o Runner original e arriscar
regressão nos dois consumidores de produção.

**`DiagnosticoOfflineViewModel`** (mesmo pacote) é o state holder consumido pela UI —
`StateFlow<DiagnosticoOfflineEstado>` com seis estados (`Idle`, `TestandoEtapa`, `EtapaOk`,
`EtapaFalhou`, `RetryEmAndamento`, `DiagnosticoConcluido`), execução sequencial que para na
primeira falha, e retry (da última etapa que falhou, ou de uma etapa explícita) preservando o
histórico anterior a ela. Guarda de concorrência (`jobEmAndamento`) evita corrotinas paralelas em
tap duplo ou retry disparado durante uma rodada em andamento.

Na etapa DNS, quando `DnsReachabilityProbe` falha mas `DohFallbackProbe` (contra a Cloudflare
pública) resolve, o executor aciona `OrientadorConfiguracaoDns` (`:featureDns` — histórico da
integração na issue #1819, fechada) com o provedor evidenciado — sem rodar o benchmark completo de
`BenchmarkDnsDoh` (7 provedores, 6
rounds, 25 s), pesado demais para esse fluxo de resposta rápida. `provedorAtivo` é derivado
comparando os IPs de `ContextoRedeDiagnosticoOffline.dnsServers` contra uma tabela reversa
IP→provedor (duplicada da tabela privada de `OrientadorConfiguracaoDns.mapearProvedor` — dívida
registrada, issue #1823), para não recomendar trocar para o DNS que a rede já usa.

## 3. Modelo de dados

### 3.1 Local — Room

`SignallQDatabase`, schema **v22**, `exportSchema = true`, 10 entidades, 8 DAOs, migrations
encadeadas até `MIGRATION_21_22`, sem `fallbackToDestructiveMigration`. Arquivo do banco:
`linkaKotlin.db`.

Schemas versionados em `android/core/database/schemas/` (`SignallQDatabase` 9–22). Persistem também
schemas de dois nomes antigos do banco (`LinkaDatabase` 1–10, `VelooDatabase` 10), mantidos por
histórico.

Detalhe em `ARQUITETURA/MODULOS/core-database.md`.

### 3.2 Preferências — DataStore

`linkaPreferencias`. `PreferenciasAppRepository.kt` (741 linhas) concentra dezenas de chaves — é um
repositório-gaveta e está registrado como dívida.

### 3.3 Remoto — D1

Dois bancos, contagem no inventário. `signallq-admin-db` guarda sessões de diagnóstico, uso de IA,
eventos de analytics, flags, usuários e sessões do Admin, saúde do sistema, releases, anúncios
locais e waitlist. `signallq-diagnostic-db` guarda regras de diagnóstico, diretório de provedores
(6 tabelas), catálogo de jogos, divergências de diagnóstico e sua própria tabela de usuários admin.

**Não existe tabela `provider_directory`** — apesar do nome aparecer em migration, módulo e
variável. O diretório de provedores é modelado em `providers`, `provider_identifiers`,
`provider_channels`, `provider_assets`, `provider_detection_stats` e `provider_enrichment_jobs`.

## 4. APIs e endpoints

Contratos formais em `CONTRATOS/openapi/` — contagem no inventário. Não repetir rota aqui: o
contrato é a fonte.

Complemento narrativo do Worker admin: `technical/admin-api-schema.md`.

### 4.1 Diagnóstico com IA

`:featureDiagnostico` → `ai-diagnosis-worker`.

| Aspecto | Valor |
|---|---|
| Modelo padrão | `@cf/qwen/qwen3-30b-a3b-fp8` (Qwen3 30B MoE FP8) |
| Schema de saída | `2` |
| Versão do prompt | app: `diagnostico_v6_local_device` (`AI_PROMPT_VERSION` em `AiModels.kt`); Worker: `diagnostico_v6_explicacao_humana` |
| Timeouts OkHttp | connect 15 s · read 90 s · write 30 s |
| **Timeout efetivo** | **40 s** — `explainDiagnosis` é envolvido em `withTimeoutOrNull(40_000L)`; os 90 s só valem no caminho de streaming |
| Cache | 5 minutos |
| Falha | Fallback local determinístico em qualquer erro — sem auth, timeout, não-2xx ou JSON inválido |

O cliente sempre envia payload v2 e o parser aceita schema `1` e `2`, tolerando campos ausentes.

Desde `v5_local_primary`, quando o motor local reporta confiança ≥ 0,75, **ele é a decisão
primária** e a IA apenas valida e explica.

### 4.2 Ingestão de analytics

`POST /ingest/analytics` no `signallq-admin-worker`, protegido por `INGEST_KEY`.

## 5. Analytics e observabilidade

Cada evento vai **simultaneamente** ao Firebase Analytics e a uma outbox Room local
(`CompositeAnalyticsTracker`). Um processador com backoff e ack idempotente drena a outbox para o
Worker admin, que grava em D1. **Não há Cloudflare Queue** — a ingestão é HTTP direto para D1.

Contagem de eventos no inventário. Quatro grupos:

| Grupo | Onde | Exemplos |
|---|---|---|
| Ciclo de vida e uso | `FirebaseAnalyticsTracker.kt` | `feature_used`, `screen_view`, `app_session_start`, `app_session_end`, `feature_crash`, `battery_snapshot`, `feature_blocked_remote` |
| Funil de produto | `FirebaseAnalyticsHelper.kt` (contrato em `:coreNetwork`) | `app_aberto`, `speedtest_iniciado`, `speedtest_concluido`, `diag_iniciado`, `diag_concluido`, `ia_laudo_solicitado`, `ia_laudo_recebido` |
| Recomendação | `:coreRecommendation` | `recommendation_eligible`, `_shown`, `_clicked`, `_dismissed`, `_feedback`, `_fallback_ad_shown` |
| Outbox | `AnalyticsOutboxFunnelTracker.kt` | `analytics_outbox_delivery` |

Crashlytics ativo. **Não há Firebase Realtime Database.**

## 6. Segurança e privacidade

### 6.1 Falhas conhecidas em aberto

| Falha | Evidência | Issue |
|---|---|---|
| `POST /ingest/provider-detection` e `/ingest/diagnostic-divergence` aceitam requisição **anônima** | `signallq-diagnostic-worker/src/index.ts` (rotas `/ingest/provider-detection` e `/ingest/diagnostic-divergence`) — ficam fora do gate `needsAdminSession`, que só cobre `/admin/`. É intencional e comentado no código | **#1585** |
| Credencial de modem gravada **em claro** no fallback | `CredenciaisModemStore` usa `EncryptedSharedPreferences`; cai para `SharedPreferences` sem cifra em `catch (_: Exception)` genérico quando o AndroidKeyStore falha | — |
| Sessão admin duplicada entre Workers | `auth.ts` de admin e diagnostic são funcionalmente idênticos; `validateSession` é byte-a-byte igual. Duas fontes de verdade sobre quem é admin | **#1587** |

O padrão de proteção **já existe** no repositório: o `signallq-admin-worker` valida `INGEST_KEY`/
`SITE_INGEST_KEY` via `authenticateIngest()`. Ele simplesmente não foi aplicado ao diagnostic.

### 6.2 Autenticação dos Workers

PBKDF2 com 100.000 iterações, formato `pbkdf2$100000$salt$hash`, token opaco SHA-256, sessão em D1
com TTL de 7 dias. Mitigação parcial da duplicação: o admin faz proxy de `/admin/diagnostic/*` por
service binding com `DIAGNOSTIC_PROXY_SECRET` — mas `/admin/providers/*`, `/admin/games/*` e
`/admin/auth/*` seguem exigindo a sessão duplicada.

### 6.3 Privacidade

`:coreTelephony` exige apenas `READ_PHONE_STATE` e **não** usa IMEI, IMSI ou `getDeviceId`. Textos
legais em `legal/`. Worker dedicado: `signallq-privacy-worker`.

## 7. Performance

Sem metas formais de performance ou escalabilidade de backend definidas em código ou documento
ativo. Os limites que existem são os timeouts de rede (§2.3, §4.1) e os limiares da seção 7 da
regra de higiene para tamanho de arquivo.

## 8. Build e release

`compileSdk`, `minSdk` e `targetSdk` no inventário.

Dois canais, ambos por GitHub Actions — nunca comando local:

1. **Firebase App Distribution** — `.github/workflows/firebase-distribution.yml`, disparo manual.
2. **Play Console** — tag `vX.Y.Z` dispara `release.yml`; o default do push de tag é a trilha
   `beta` com anúncios desligados. Disparo manual aceita `playTrack` `beta` ou `production`
   (produção com ads reais exige decisão explícita do Luiz; `adsEnabled=true` fora de `production`
   é barrado por guardrail). `promote-release.yml` promove um AAB já publicado de `internal`/`alpha`
   sem rebuild.

**Regra dura:** nunca subir build sem incrementar `versionCode` antes.

Procedimento completo em `operations/RELEASE.md`.

Validações locais, a partir de `android/` (`gradlew.bat` no Windows):

```
./gradlew ktlintCheck
./gradlew detekt
./gradlew test
./gradlew assembleDebug
```

**Suspeita de poluição entre testes no `:app`** (estado que vaza de um teste pra outro, ex.
`kotlinx.coroutines.test.UncaughtExceptionsBeforeTest`): o Gradle `Test` task deste módulo (JUnit4
puro, sem `useJUnitPlatform()`) não embaralha a ordem das classes nativamente — a ordem é
determinística (varredura do diretório de `.class`). `SuiteEmbaralhadaTest`
(`app/src/test/kotlin/io/signallq/app/SuiteEmbaralhadaTest.kt`) descobre e embaralha as classes de
teste do módulo em runtime, rodando todas na mesma JVM — único jeito de reproduzir poluição de
estado estático entre classes (ver GH#1684). Não roda no CI por padrão:

```
./gradlew :app:testDebugUnitTest --tests "io.signallq.app.SuiteEmbaralhadaTest" --rerun \
    -PsuiteEmbaralhada -Dsuite.embaralhada.seed=<long opcional>
```

## 9. Riscos técnicos

| Risco | Detalhe |
|---|---|
| UI monolítica em `:app` | `MainViewModel.kt`, `AppShell.kt`, `HistoricoScreen.kt`, `LocalDeviceSection.kt`, `SinalWifiSection.kt`, `SinalCanalSection.kt` e outros acima de 800 linhas — contagem atual em `ARQUITETURA/README.md` §4 |
| Dependência feature→feature | nenhuma hoje — ver `ARQUITETURA/README.md` §2 |
| Três mecanismos de feature flag | `:core:featureflags` (14 flags), `FeatureFlagProvider` legado em `:coreNetwork`, e Firebase Remote Config — com colisão de nome entre os dois primeiros |
| Ausência de teste em pontos sensíveis | `:core:relatorio` (0 testes), `:corePermissions` (0), `:featureWifi` (0) |
| `:app` sem `androidTest` | Dependências de teste instrumentado declaradas, diretório `app/src/androidTest` inexistente |
| `:core:diagnostico` não é Kotlin puro | `topology/correlation/TopologyTracer.kt` executa `Runtime.exec("/system/bin/ping")` |
| Equipamentos com poucos drivers | Fibra: só Nokia G-1425G-B (`:featureFibra`); roteador: só TP-Link Archer C6 (`:featureRouter`). Intelbras tem apenas mapa documental em `technical/INTELBRAS_RX1500_FIELD_MAP.md`, sem código |
| `MetricClassifier` não usado em `SinalMovelSection.kt`/`SinalMovelClassificacao.kt` | Limiares duplicados em três lugares — issue **#1586** |

## 10. Referências

Equipamento: `technical/INTELBRAS_RX1500_FIELD_MAP.md`, `NOKIA_GPON_FIELD_MAP.md`,
`TPLINK_ARCHER_ROUTER_FIELD_MAP.md` · Fluxos: `technical/AI_FLOW.md`,
`PING_EXECUTOR_ARCHITECTURE.md`, `MONITORAMENTO_PASSIVO.md` · Flags:
`technical/feature-flags-remote-config.md`, `functional/FEATURE_FLAGS.md` · Worker admin:
`technical/admin-api-schema.md`.
