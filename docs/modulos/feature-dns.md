---
title: "Módulo :featureDns"
description: "Benchmark de resolvedores DNS via DoH, recomendação de troca de provedor e avaliação de coerência do DNS ativo."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureDns`

- **Caminho físico:** `android/feature/dns/` (alias flat legado)
- **Namespace:** `io.signallq.app.feature.dns`

## Responsabilidade

Benchmark DoH de resolvedores DNS, recomendação e coerência do DNS ativo. Regras, mapa de código, riscos e testes: [`features/dns-ping.md`](../features/dns-ping.md).

## Dependências

Extraídas de `android/feature/dns/build.gradle.kts`.

| Tipo | Dependência | Observação |
|---|---|---|
| Plugin | `com.android.library` | — |
| Plugin | `org.jetbrains.kotlin.android` | — |
| `implementation` | `libs.androidx.core.ktx` | — |
| `implementation` | `libs.kotlinx.coroutines.android` | — |
| `implementation` | `libs.okhttp` | transporte DoH (`application/dns-message` em GET base64url) |
| `implementation` | `libs.timber` | — |
| `testImplementation` | `libs.junit`, `libs.kotlinx.coroutines.test` | — |
| `androidTestImplementation` | `libs.androidx.junit`, `libs.androidx.espresso.core` | não há `src/androidTest` |

Sem Hilt, sem Compose e **sem nenhuma dependência de projeto** — nem `:core*`, nem `feature`. Junto de `:featureHome`, é um dos dois módulos totalmente independentes do resto do grafo.

## Consumidores

`grep` por `project(":featureDns")` em `android/**/build.gradle.kts`:

| Consumidor | Arquivo |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

No código do `:app`, os tipos aparecem em `di/AppModule.kt`, `MainViewModel.kt`, `ui/screen/AppShell.kt` e `ui/screen/DnsScreen.kt`.
