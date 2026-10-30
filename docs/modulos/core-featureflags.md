---
title: "Módulo :core:featureflags"
description: "Fundação de feature flags do Consumer — catálogo tipado e provider único sobre Firebase Remote Config."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:core:featureflags`

- **Caminho físico:** `android/core/featureflags/`
- **Namespace:** `io.signallq.app.core.featureflags`
- **Tipo:** biblioteca Android (`com.android.library`)

## Responsabilidade

Fundação de feature flags do app Consumer (issue #1477, Épico #1347): define o catálogo canônico
de flags em JSON, expõe constantes tipadas para referenciá-las em código e oferece um único
contrato de leitura (`FeatureFlagProvider`) implementado sobre o Firebase Remote Config. Garante
que exista sempre um valor utilizável — os defaults locais do catálogo semeiam o estado em memória
antes de qualquer fetch, e uma falha de rede nunca apaga a última configuração válida.

Não é dele: decidir o que fazer quando uma flag está desligada (isso é do consumidor — em `:app`,
`ConsumerFeatureGateCoordinator` + `AppShellFeatureGating`), fazer wiring de DI (o módulo expõe um
`object` fábrica; os `@Provides` Hilt ficam em `AppModule`, em `:app`). O catálogo é
explicitamente `consumer-catalog.json` — específico deste produto, não genérico.

## Dependências

### Módulos do projeto

Nenhuma. É um módulo folha — não depende de nenhum outro módulo do monorepo. Deliberado: o KDoc de
`RemoteConfigFeatureFlagProvider` explica que a ponte `Task` → `suspend` foi duplicada de
`AdsRemoteConfigRepository` em vez de reusada porque "este módulo `:core:featureflags` não pode
depender de `:app`", e que a instância de `FirebaseRemoteConfig` chega como lambda
`() -> FirebaseRemoteConfig` em vez de `dagger.Lazy` para não trazer Dagger para dentro de um
`:core:*`.

### Bibliotecas externas

| Biblioteca | Para quê |
|---|---|
| `androidx.core.ktx` | Extensões Kotlin do Android |
| `kotlinx.coroutines.android` | `StateFlow`, `Flow`, `withContext`, `withTimeoutOrNull` |
| `platform(libs.firebase.bom)` + `firebase.config` | Backend real das flags (Firebase Remote Config) |
| `timber` | Log de falha de fetch e de leitura de valores ativos |
| `junit`, `mockk`, `kotlinx.coroutines.test`, `libs.org.json` (test) | Suíte JVM pura — `org.json` real é necessário porque o parser roda sem Robolectric |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | Declaradas, sem código correspondente |

## Consumidores

| Consumidor | Uso |
|---|---|
| `:app` | `AppModule` monta catálogo + provider via `FeatureFlagsModulo`; `SignallQApplication` injeta e chama `refresh()`; `ConsumerFeatureGateCoordinator` observa as 9 chaves de módulo; `MonitoramentoWorker` consulta flags no background |
| `:featureDiagnostico` | `DiagnosticDivergenceReporter` usa `CONSUMER_DIAGNOSTICO_SHADOW_MODE_ENABLED` como kill switch do shadow mode (migrado do sistema legado SIG-13 na issue #1497); `di/DiagnosticoModule` recebe o provider por injeção |

Restrição de consumo registrada em `settings.gradle.kts:45-46`: "Consumido apenas por `:app` e
módulos core/feature do Consumer" — diferente de `:core:diagnostico` e `:core:relatorio`, que
são consumidos por mais de uma árvore de features.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `src/main/resources/featureflags/consumer-catalog.json` (**14 flags**) | Catálogo canônico — fonte de verdade também para o SignallQ Admin (F3/#1479) e o Worker (F2/#1478). Fica em `resources/`, não `assets/`, para funcionar igual em teste JVM puro, Robolectric e device |
| `FeatureFlagKeys.kt` | Constantes tipadas das 14 chaves; `ALL` e `CONSUMER_MODULE_KEYS` (as 9 chaves `enabled` por módulo feature, instrumentadas em F4/#1480) |
| `FeatureFlagDefinition.kt` | Entrada tipada do catálogo, espelhando 1:1 o schema JSON: `module`, `type`, `defaultValue`, `criticality`, `owner`, `disabledBehavior`, `dependencies`, `androidImplemented`, `adminManaged`, `analyticsEvent` |
| `FeatureFlagCatalogParser.kt` | Parser JSON → `List<FeatureFlagDefinition>`, usando `org.json.JSONObject` |
| `FeatureFlagCatalogLoader.kt` | Lê `/featureflags/consumer-catalog.json` do classpath; ausência é erro de build, lança `FeatureFlagCatalogParseException` |
| `FeatureFlagCatalog.kt` | Catálogo em memória — indexação por chave, `defaultsAsValues()` e `toRemoteConfigDefaultsMap()` para alimentar `setDefaultsAsync` |
| `FeatureFlagProvider.kt` | Contrato único: `observe(key): Flow<FeatureFlagValue>`, `isEnabled(key): Boolean` (nunca lança, fallback `false`) e `suspend refresh(force): FeatureFlagRefreshResult` |
| `RemoteConfigFeatureFlagProvider.kt` | Implementação sobre `FirebaseRemoteConfig`. `MutableStateFlow` semeado com os defaults; timeout de fetch de 8s; lê os valores ativos independentemente do resultado do fetch, preservando a última config válida |
| `FeatureFlagRefreshResult.kt` | `Success(activated, fetchTimeMillis)` vs `Failure(reason, cause)` — `fetchAndActivate()` retornando `false` é `Success(activated = false)`, nunca falha. Razões: `TIMEOUT`, `NETWORK_ERROR`, `THROTTLED`, `UNKNOWN` |
| `FeatureFlagSource.kt` | `DEFAULT` / `REMOTE` / `STATIC`, mapeado 1:1 de `FirebaseRemoteConfigValue.getSource()` |
| `FeatureFlagsModulo.kt` | Fábrica (`criarCatalogo`, `criarProvider`) — mesmo padrão de `CoreNetworkModulo`, sem anotações Dagger |
| `FeatureFlagRawValue.kt`, `FeatureFlagValue.kt`, `FeatureFlagType.kt`, `FeatureFlagCriticality.kt`, `FeatureFlagDisabledBehavior.kt`, `FeatureFlagKey.kt` | Tipos de apoio do domínio |
| `src/test/.../FeatureFlagKeysParityTest.kt` | Impede divergência entre `FeatureFlagKeys` e o JSON — toda chave em um precisa existir no outro |

## Features

Features que listam este módulo (comportamento e regras de negócio ficam na página da feature): [`assist-diagnostico`](../features/assist-diagnostico.md), [`dns-ping`](../features/dns-ping.md), [`equipamento-internet`](../features/equipamento-internet.md), [`monitoramento-alertas`](../features/monitoramento-alertas.md), [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md), [`velocidade-resultado`](../features/velocidade-resultado.md).

## Riscos e dívidas

- **Colisão de nome com o mecanismo legado.** Existem duas interfaces chamadas
  `FeatureFlagProvider`: esta e `io.signallq.app.core.network.FeatureFlagProvider` (HTTP
  `GET /flags`, SIG-13). O próprio KDoc alerta, mas o risco de import errado é real e o compilador
  não ajuda. Somando `FeatureFlags.kt` (sobre `BuildConfig`) em `:app`, são três mecanismos de
  flag no Consumer ao mesmo tempo. O único consumidor real do legado já migrou (#1497), mas o
  código legado permanece.
- **Uma flag do catálogo não é implementada no Android.**
  `consumer_speedtest_cloudflare_engine_enabled` tem `androidImplemented = false` — segue como
  smoke-test da fundação, não gateia nada. Das 14 chaves, 9 são flags de módulo (`CONSUMER_MODULE_KEYS`), 4 do domínio de diagnóstico (shadow mode e NDS) e 1 é esta smoke-test.
- **`FeatureFlagCatalogParser` usa `org.json`**, o que obriga cada consumidor de teste JVM a
  declarar a dependência real de `org.json` para não cair no stub do `android.jar` — pegadinha já
  documentada em comentário no `build.gradle.kts`, mas que se repete em `:app` e
  `:core:diagnostico`.
- **Sem testes instrumentados.** `androidTestImplementation` declarado, mas não existe diretório
  `src/androidTest`. A cobertura JVM inclui o teste de paridade catálogo↔código.
- **Classificação de falha por nome de classe.** `classificarFalha` decide `THROTTLED` via
  `erro.javaClass.simpleName.contains("Throttled")` — heurística frágil que quebra silenciosamente
  se o SDK do Firebase renomear a exceção ou se o build for ofuscado.
- **`observe()` não reage a mudanças remotas sozinho.** O `StateFlow` só muda quando alguém chama
  `refresh()` (hoje: `SignallQApplication.onCreate`). Não há listener de update em tempo real do
  Remote Config nem refresh periódico — o "reativo" do `ConsumerFeatureGateCoordinator` só se
  concretiza no próximo `refresh()`.
