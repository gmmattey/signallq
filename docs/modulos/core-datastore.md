---
title: "Módulo :coreDatastore"
description: "Preferências do app em DataStore Preferences e credenciais do modem em EncryptedSharedPreferences."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:coreDatastore`

- **Caminho físico:** `android/core/datastore/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.datastore`
- **Tipo:** biblioteca Android

## Responsabilidade

Persistência chave-valor do app Consumer: preferências do usuário e estado de app em DataStore Preferences (`PreferenciasAppRepository`), credenciais do modem em `EncryptedSharedPreferences` (`CredenciaisModemStore`) e o cache local de feature flags remotas (`FeatureFlagStore`).

Não é dele: dados estruturados/relacionais (`:coreDatabase`), a origem das feature flags remotas (Firebase Remote Config, em `:core:featureflags`), nem regra de negócio sobre as preferências — só guarda e expõe `Flow`/`suspend`.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `androidx.core.ktx` | utilitários de plataforma |
| `androidx.datastore.preferences` | `preferencesDataStore` do `PreferenciasAppRepository` |
| `androidx.security.crypto` | `MasterKey` + `EncryptedSharedPreferences` do `CredenciaisModemStore` |
| `kotlinx.coroutines.android` | `Flow`, `StateFlow`, `withContext(ioDispatcher)` |
| `junit` (test) | teste JVM de `ConnectionProfilePersistido` |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding padrão, sem teste instrumentado próprio |

Nenhuma dependência de outro módulo do monorepo.

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |
| `:featureDevices`, `:featureDiagnostico`, `:featureSpeedtest` | `implementation` |

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/datastore/PreferenciasAppRepository.kt` | Repository único de preferências sobre DataStore (`name = "linkaPreferencias"`); implementa `FeatureFlagStore` |
| `src/main/kotlin/io/signallq/app/core/datastore/CredenciaisModemStore.kt` | usuário/senha/BSSID vinculado do modem em `EncryptedSharedPreferences` (AES-256 GCM via AndroidKeyStore), arquivo `signallq_modem_credentials` |
| `src/main/kotlin/io/signallq/app/core/datastore/FeatureFlagStore.kt` | contrato mínimo (`salvarFeatureFlags`/`buscarFeatureFlags`), isolado para permitir fake sem `Context` |
| `src/main/kotlin/io/signallq/app/core/datastore/CoreDatastoreModulo.kt` | fábrica manual `criarPreferenciasAppRepository(context)` |
| `src/main/kotlin/io/signallq/app/core/datastore/ConnectionProfilePersistido.kt` | modelo serializado do perfil de conexão persistido |
| `src/main/kotlin/io/signallq/app/core/datastore/ModoGamerPadraoPersistido.kt` | modelo serializado do modo gamer padrão |

O `CredenciaisModemStore` tem fallback explícito para `SharedPreferences` em claro (arquivo `signallq_modem_credentials_fallback`) quando o AndroidKeyStore não está disponível — cenário previsto para testes com Robolectric; em device real o KeyStore sempre existe.

## Features

Features que listam este módulo (comportamento e regras de negócio ficam na página da feature): [`equipamento-internet`](../features/equipamento-internet.md), [`modo-gamer`](../features/modo-gamer.md), [`monitoramento-alertas`](../features/monitoramento-alertas.md), [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md).

## Riscos e dívidas

- **`PreferenciasAppRepository.kt` é um "god repository"** (perto do limite de 800 linhas): concentra dezenas de chaves heterogêneas — monitoramento, modem, tema, perfil do usuário, operadora/região, onboarding, consentimento LGPD, dismisses de sheets de permissão e feature flags. Candidato natural a fatiamento por domínio.
- **Nomes legados em produção:** o DataStore chama-se `linkaPreferencias`. Renomear exige migração de dados.
- **Cobertura de teste baixa:** 1 único arquivo de teste (`ConnectionProfilePersistidoTest`). Nem `PreferenciasAppRepository` nem `CredenciaisModemStore` — o componente que lida com credenciais — têm teste próprio.
- **Chave plaintext legada** `gatewaySessionBssid` ainda declarada no repository, mantida só para migração única (GH#530); precisa de data de remoção.
- **Fallback sem criptografia** no `CredenciaisModemStore` é acionado por `catch (_: Exception)` genérico: uma falha inesperada do KeyStore em device real degradaria silenciosamente para armazenamento em claro.
