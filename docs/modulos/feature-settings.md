---
title: "Módulo :featureSettings"
description: "Modelo e validações puras do perfil de conexão por rede, do tema e dos campos de ajustes do app."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureSettings`

- **Caminho físico:** `android/feature/settings/` (alias flat legado, remapeado por `projectDir` em `android/settings.gradle.kts`)
- **Namespace:** `io.signallq.app.feature.settings`

## Responsabilidade

Biblioteca de regras puras dos Ajustes: modela o `ConnectionProfile` vinculado a uma rede específica (`networkId` resolvido por `ResolvedorNetworkId`, promovido pra `:coreDatabase` na issue #1707), valida os campos que o usuário preenche (velocidade contratada, cidade/UF) e tipa a preferência de tema. Cada regra é um `object` ou `data class` sem dependência de Android, Hilt, Compose ou DataStore.

Não é dele: a tela de Ajustes (`AjustesScreen.kt` vive em `:app`), a persistência (chaves e leitura/escrita ficam em `:coreDatastore`, consumido por `:app`), a resolução real de SSID/BSSID (quem chama já resolveu via `WifiManager`/`ConnectivityManager`) e qualquer decisão de UI sobre divergência de perfil — o módulo só classifica a situação e devolve o tipo.

Comportamento de Ajustes, perfil e privacidade: [`features/perfil-ajustes-legal.md`](../features/perfil-ajustes-legal.md).

## Dependências

Extraídas de `android/feature/settings/build.gradle.kts`.

| Dependência | Configuração | Observação |
|---|---|---|
| `libs.androidx.core.ktx` | `implementation` | |
| `libs.junit` | `testImplementation` | |
| `libs.androidx.junit`, `libs.androidx.espresso.core` | `androidTestImplementation` | |

**Uma dependência de outro módulo do monorepo:** `implementation(project(":coreDatabase"))`, adicionada na issue #1707 (Task 2.0.09e, épico #1647) — só pra alcançar `ResolvedorNetworkId`, promovido pra lá (ver abaixo). Fora isso, tem poucas dependências — sem Hilt, sem coroutines, sem OkHttp, sem Compose.

## Consumidores

`grep -rn 'project(":featureSettings")' --include=*.kts .`

| Consumidor | Local |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

Nenhum outro módulo depende deste.

## Componentes principais

O módulo tem 6 arquivos `main` (`ResolvedorNetworkId.kt` saiu pra `:coreDatabase` na issue #1707).

| Arquivo / classe | Responsabilidade |
|---|---|
| `.../settings/ConnectionProfile.kt` | `data class ConnectionProfile(networkId, providerFixed, contractedDownloadMbps, contractedUploadMbps, city, state, userConfirmed)`. GH#1227 item 3/RF-A — antes provedor e plano eram chaves DataStore globais, então o app aplicava o plano residencial numa rede de trabalho, hotel ou hotspot. `networkId` é o campo que impede isso; `userConfirmed` distingue escolha deliberada do usuário de valor apenas detectado |
| `.../settings/DetectorDivergenciaPerfilConexao.kt` | `sealed interface ResultadoDivergenciaPerfilConexao` com 4 casos (`SemBaseParaComparar`, `PerfilCoincide`, `AtualizavelSilenciosamente`, `DivergenciaConfirmadaPeloUsuario`) e o `object` com `avaliar(perfilSalvo, providerDetectado)`. GH#1227 item 2/RF-B — tipado em vez de `Boolean` porque as três situações reais pedem tratamento diferente do chamador (sobrescrever em silêncio × alertar × não fazer nada) |
| `.../settings/ValidadorVelocidadeContratada.kt` | `ehValida(mbps: Int?)`. `null` é válido (campo vazio explícito); `0` e negativos são inválidos; teto `LIMITE_SUPERIOR_MBPS = 10_000`. GH#1227 item 5/RF-D |
| `.../settings/ValidadorCidadeUf.kt` | `ehCombinacaoValida(cidade, uf)`: ambos vazios é válido, só um preenchido é inválido, e a UF precisa estar em `UFS_VALIDAS` (as 27 siglas). GH#1249 — a validação de correspondência geográfica real exigiria um catálogo IBGE que não existe no repo, e a limitação está registrada no kdoc |
| `.../settings/ThemePreference.kt` | `enum ThemePreference { SYSTEM, LIGHT, DARK }` com `chaveDataStore` preservando as strings já gravadas em produção (`"sistema"`/`"claro"`/`"escuro"`) e `parse(raw)` que nunca lança e sempre cai em `SYSTEM`. GH#1227 item 14/RF-I — antes, valor inesperado deixava a UI sem nenhuma opção selecionada |
| `.../settings/FeatureSettingsModulo.kt` | `object FeatureSettingsModulo` — declaração vazia, sem nenhum membro |

## Riscos e dívidas

- **Dependência entre features:** uma, `:coreDatabase` (issue #1707, só pra `ResolvedorNetworkId`). Continua sem depender de nenhuma outra feature.
- **Regra de negócio em Composable:** não aplicável — 0 `@Composable` no módulo (verificado por grep). Todas as regras são funções puras testáveis.
- **Falta de teste:** apenas `FeatureSettingsModulo.kt` e `ConnectionProfile.kt` não têm teste dedicado — e ambos são declarações sem comportamento. Os quatro componentes com lógica que restaram (`DetectorDivergenciaPerfilConexao`, `ValidadorVelocidadeContratada`, `ValidadorCidadeUf`, `ThemePreference`) têm arquivo de teste correspondente.
- **`FeatureSettingsModulo.kt` é um `object` vazio** (sem membros), ao contrário dos homônimos de `:featureFibra` e `:featureHistory`, que expõem fachada real. Código morto ou fachada nunca implementada — remover ou dar conteúdo.
- **O módulo não é uma feature no sentido da §5 da regra de higiene.** Não tem UI, estado, ViewModel nem casos de uso — é um conjunto de regras puras consumido por `:app`. `ResolvedorNetworkId` já foi promovido pra `core` (issue #1707) por precisar ser alcançado por `:coreDatabase`/`:featureHistory`; se o restante receber a tela de Ajustes hoje em `:app` (`AjustesScreen.kt`, dívida registrada em §4.4), passa a ser feature de fato. Não determinado nesta revisão.
- **`ConnectionProfile` não tem ponto de escrita neste módulo.** A validação existe, mas quem persiste (e se persiste corretamente) está em `:app`/`:coreDatastore`, fora do alcance de qualquer teste deste módulo.
