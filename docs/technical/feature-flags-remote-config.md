---
title: "Feature Flags do Consumer — Firebase Remote Config"
description: "Mecanismo técnico do módulo :core:featureflags: catálogo tipado, FeatureFlagProvider, integração com Firebase Remote Config e gate dos módulos no AppShell."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Feature Flags do Consumer — Firebase Remote Config (`:core:featureflags`)

**Fonte de verdade:** código. O **catálogo** (chaves, tipos, defaults) tem fonte única em `android/core/featureflags/src/main/resources/featureflags/consumer-catalog.json` e não é copiado aqui; este arquivo documenta o mecanismo.
**Escopo:** módulo `:core:featureflags` (Épico #1347: F1/#1477, F4/#1480, #1497, #1759, #1790) e o gate dos módulos em `AppShell`. **Não cobre** as flags de compile-time (`FeatureFlags.kt`, em `docs/03-especificacao-tecnica.md` §5.2 e `docs/functional/FEATURE_FLAGS.md`) nem a UI do Admin (`buildea-admin`).
**Backend do Admin:** `integrations/cloudflare/signallq-admin-worker/src/remoteConfigAdmin.ts` e `src/featureFlagCatalog.ts` leem este mesmo catálogo (import JSON embarcado no bundle a cada `wrangler deploy`); rotas `/admin/firebase/remote-config*` e `/admin/firebase/feature-flags/*` em [`CONTRATOS/openapi/signallq-admin-api.yaml`](../CONTRATOS/openapi/signallq-admin-api.yaml).
**Substitui:** a versão 1.x (338 linhas, com histórico de entregas por issue), recuperável via `git log -- docs/technical/feature-flags-remote-config.md`.

## 1. Objetivo

Um catálogo tipado versionado no repositório e um único ponto de acesso (`FeatureFlagProvider`) sobre o Firebase Remote Config, para governar remotamente funcionalidades do Consumer sem publicar nova versão do app.

## 2. Estrutura do módulo

```
android/core/featureflags/
├── src/main/resources/featureflags/consumer-catalog.json   (catálogo canônico)
└── src/main/kotlin/io/signallq/app/core/featureflags/
    FeatureFlagKey / FeatureFlagKeys (constantes — único jeito de referenciar uma flag)
    FeatureFlagType, Criticality, DisabledBehavior, Source (enums do schema)
    FeatureFlagDefinition, FeatureFlagCatalogParser/Loader, FeatureFlagCatalog (lookup + defaults)
    FeatureFlagProvider (interface), RemoteConfigFeatureFlagProvider (impl sobre FirebaseRemoteConfig)
    DisabledFeatureFlagProvider, FeatureFlagRawValue/Value, FeatureFlagRefreshResult, FeatureFlagsModulo
```

- O catálogo fica em `src/main/resources/` (não `assets/`) para ser lido igual em JUnit puro e em runtime, via `Class.getResourceAsStream()`.
- `RemoteConfigFeatureFlagProvider` recebe `remoteConfigProvider: () -> FirebaseRemoteConfig` (lambda), não `dagger.Lazy`: `:core:*` não depende de Hilt. O wiring está em `AppModule` (`:app`).
- A instância de `FirebaseRemoteConfig` é a mesma do toggle de anúncios (`AdsRemoteConfigRepository`, `ads_native_*`); `AppModule.provideFirebaseRemoteConfig()` mescla os dois mapas de defaults num único `setDefaultsAsync`.
- Consumido por `:app`; `SignallQApplication.onCreate` chama `refresh()` uma vez, sem bloquear.

## 3. Modelo de dados

**Chaves:** `consumer_<modulo>_<nome>` (e `shared_*`/`app_*` para núcleos compartilhados), com underscore. O Remote Config rejeita `.` em nome de parâmetro (`400 INVALID_KEY`); por isso as chaves foram renomeadas em #1790 (2026-08-20) — as constantes Kotlin `CONSUMER_*` mantiveram o nome.

**Schema do arquivo:** `{ "schemaVersion": "1.0", "flags": [ ... ] }`. Por entrada:

| Campo | Tipo | Observação |
|---|---|---|
| `key` | string | identificador único |
| `module` | string | alias Gradle do módulo dono (ex.: `:featureSpeedtest`) |
| `type` | `BOOLEAN\|STRING\|LONG\|DOUBLE` | espelha os getters do Remote Config |
| `defaultValue` | conforme `type` | aplicado localmente antes de qualquer fetch |
| `criticality` | `LOW\|MEDIUM\|HIGH\|CRITICAL` | metadado; o Admin usa para exigir confirmação reforçada |
| `owner`, `description` | string | responsável e descrição funcional |
| `disabledBehavior` | `HIDE_ENTRY_AND_BLOCK_ROUTE\|SHOW_DISABLED_MESSAGE\|SILENT_NO_OP\|FALLBACK_MODE` | comportamento ao desativar; enum não exaustivo |
| `disabledMessage` | string? | mensagem ao usuário, quando aplicável |
| `dependencies` | `FeatureFlagKey[]` | flags das quais esta depende (pode ser `[]`) |
| `androidImplemented` | boolean | se algum código real lê a flag |
| `adminManaged` | boolean | se o Admin pode gerenciá-la |
| `analyticsEvent` | string? | evento disparado ao bloquear (`feature_blocked_remote`) |

**Catálogo atual (2026-10-04, 12 flags):** `consumer_{home,speedtest,wifi,devices,dns,fibra,diagnostico,history,settings}_enabled` (gate dos 9 módulos), `consumer_speedtest_cloudflare_engine_enabled`, `consumer_diagnostico_shadow_mode_enabled` e `consumer_diagnostico_nds_live_enabled`. `nds_live` tem `defaultValue: true` (o app tenta o NDS por padrão, com `DiagnosticRunner` local como fallback) e, ligada, desliga o shadow mode do `signallq-diagnostic-worker` no mesmo install. Valores e demais metadados: consultar o JSON.

**Contrato Kotlin:**

```kotlin
interface FeatureFlagProvider {
    fun observe(key: FeatureFlagKey): Flow<FeatureFlagValue>
    fun isEnabled(key: FeatureFlagKey): Boolean
    suspend fun refresh(force: Boolean = false): FeatureFlagRefreshResult
}
```

- `observe`/`isEnabled` nunca bloqueiam; a primeira emissão é o default local do catálogo. `FeatureFlagValue.source` (`DEFAULT|REMOTE|STATIC`) diz de onde veio o valor.
- `refresh(force = false)`: `fetchAndActivate()` (respeita o throttling do SDK). `force = true`: `fetch(0)` + `activate()` (uso de debug). Roda em `Dispatchers.IO`, timeout padrão de 8 s.
- `activate()` devolvendo `false` (nada novo) **não** é erro: vira `Success(activated = false, ...)`. Só exceção/timeout viram `Failure`, e uma falha nunca apaga a última config válida. `refresh()` nunca lança para o chamador.

## 4. Gate dos módulos no AppShell (F4/#1480)

Cada flag `consumer_{modulo}_enabled` (todas `androidImplemented: true`, `HIDE_ENTRY_AND_BLOCK_ROUTE`, default `true`) gateia uma superfície:

| Módulo | Superfície |
|---|---|
| `:featureHome` | Tab Início |
| `:featureSpeedtest` | Tab Velocidade |
| `:featureWifi` | Tab Sinal + `Overlay.SinalWifi` |
| `:featureDevices` | `Overlay.Dispositivos` |
| `:featureDns` | `Overlay.Dns` |
| `:featureFibra` | `Overlay.Fibra` / `Overlay.EquipamentoInternet` |
| `:featureDiagnostico` | `Overlay.Laudo` |
| `:featureHistory` | Tab Histórico |
| `:featureSettings` | `Overlay.Perfil` (Ajustes), `MonitoramentoSheet` e `MonitoramentoWorker.doWork()` |

Fora do gate por decisão: `Overlay.Privacidade`/`Overlay.Termos` (obrigação legal/LGPD, nunca escondidas por flag), o hub Ferramentas e os overlays não nomeados nos 9 módulos.

**Código:** `ui/screen/AppShellFeatureGating.kt` (funções puras `tabHabilitada`, `tabModuleId`, `primeiraTabHabilitada`, `permitirOuBloquear` e o placeholder `FeatureDisabledContent`); `featureflags/ConsumerFeatureGateCoordinator.kt` (`@Singleton` que combina os `observe(...)` num `StateFlow<AppShellFeatureFlagsState>` e centraliza `feature_blocked_remote`); `MainViewModel` só expõe `featureFlagsState`.

**Comportamento ao desativar:**
1. Tab: `NavigationBarItem(enabled = false)`.
2. Overlay: `onAbrir*Overlay` checa a flag antes de empilhar; se desligada, não empilha, registra `feature_blocked_remote` e mostra Snackbar neutro ("Recurso temporariamente indisponível.").
3. Se a tab atual perde a flag em runtime, um `LaunchedEffect` redireciona para a primeira tab habilitada (prioridade 1→0→2→3, Ferramentas por último) e registra o bloqueio.
4. `MonitoramentoWorker.doWork()` consulta `FeatureFlagProvider.isEnabled(consumer_settings_enabled)` direto: desligada, pula a execução sem cancelar o agendamento nem apagar histórico.

O app não tem deep links hoje (o manifest só declara o intent-filter do launcher); todo `onAbrir*Overlay` é o ponto único de entrada.

**Analytics:** `AnalyticsTracker.registrarFeatureBloqueadaRemota(featureId)` → evento GA4 `feature_blocked_remote` (ver [`analytics-events-schema.md`](analytics-events-schema.md)); o `CompositeAnalyticsTracker` encaminha só ao Firebase.

## 5. Sistema legado SIG-13

`FeatureFlagManager`/`FeatureFlagRepository` (`android/app/.../featureflags/`, HTTP `GET /flags` e `GET /feature-flags` no `signallq-admin-worker`) continuam no repositório e inicializam em `SignallQApplication.onCreate`, mas **sem consumidor real do valor lido** desde #1497 (que migrou o último, `DiagnosticDivergenceReporter`, para `CONSUMER_DIAGNOSTICO_SHADOW_MODE_ENABLED`). Não confundir: há dois `FeatureFlagProvider` de mesmo nome simples (`core.network`, legado, e `core.featureflags`, novo). A remoção completa (código Android, endpoints do Worker, tabelas D1 `feature_flags`/`feature_flag_audit`) é candidata a issue dedicada, ainda não decidida.

## 6. Riscos e pendências

- **Paridade catálogo ↔ `FeatureFlagKeys`:** `FeatureFlagKeysParityTest` falha o build se uma constante não tiver entrada no catálogo ou vice-versa. A validação em CI de PR (catálogo ↔ código ↔ Remote Config, F5) não existe.
- **Sem atualização em tempo real:** `addOnConfigUpdateListener` não está implementado; uma publicação no Admin só chega ao app no próximo `refresh()` (hoje, no startup).
- **Pendências do Épico #1347:** gate de capacidades de `:core:*` (`coreNetwork`, `coreTelephony`, `coreRecommendation`) e sub-flags mais finas por módulo (ex.: só a IA do diagnóstico).
- **Segurança/privacidade:** sem credencial nova (reusa `google-services.json`); nenhum dado pessoal trafega, só chaves e valores.
