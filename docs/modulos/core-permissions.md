---
title: "Módulo :corePermissions"
description: "Avaliação do estado das permissões de rede (localização fina e NEARBY_WIFI_DEVICES) sem acoplar a UI ao framework."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:corePermissions`

- **Caminho físico:** `android/core/permissions/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.permissions`
- **Tipo:** biblioteca Android

## Responsabilidade

Responde a uma pergunta só: quais permissões relevantes para operações de rede já estão concedidas e quais faltam. Expõe o contrato `GerenciadorPermissoesRede` (`avaliar()` → `SnapshotPermissoesRede`, `listarPermissoesPendentes()`) e o utilitário `LocationPermissionHelper`, encapsulando as diferenças de API level (`NEARBY_WIFI_DEVICES` só existe a partir do Android 13/TIRAMISU).

Não é dele: **solicitar** permissão ao usuário — quem dispara o launcher e desenha a sheet de justificativa é a UI em `:app`/features. Também não declara nenhuma permissão no próprio manifesto (o `AndroidManifest.xml` do módulo é vazio) e não conhece Wi-Fi, telefonia ou rede em si.

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `androidx.core.ktx` | `ContextCompat.checkSelfPermission` |
| `junit` (test) | declarada, mas sem nenhum teste no módulo |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding padrão, sem teste instrumentado |

É o módulo mais enxuto: nenhuma dependência de outro módulo do monorepo, nenhuma dependência de coroutines.

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |

Observação: nenhum módulo `:feature*` do Consumer depende dele diretamente — o consumo passa por `:app`.

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/permissions/GerenciadorPermissoesRede.kt` | contrato: `avaliar()` e `listarPermissoesPendentes()` |
| `src/main/kotlin/io/signallq/app/core/permissions/GerenciadorPermissoesRedeAndroid.kt` | implementação sobre `ContextCompat`; trata `NEARBY_WIFI_DEVICES` como concedida abaixo da API 33 |
| `src/main/kotlin/io/signallq/app/core/permissions/SnapshotPermissoesRede.kt` | `data class` com `localizacaoFina` + `nearbyWifi` e o predicado `estaAptoParaScanRede()` |
| `src/main/kotlin/io/signallq/app/core/permissions/EstadoPermissao.kt` | enum `concedida` / `negada` |
| `src/main/kotlin/io/signallq/app/core/permissions/LocationPermissionHelper.kt` | `object` utilitário: aceita `ACCESS_FINE_LOCATION` ou fallback `ACCESS_COARSE_LOCATION`; lista as duas em `permissoesAoSolicitar()` |
| `src/main/kotlin/io/signallq/app/core/permissions/CorePermissionsModulo.kt` | fábrica manual `criarGerenciadorPermissoesRede(context)` |

Permissões avaliadas: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (só no helper) e `NEARBY_WIFI_DEVICES`.

## Features

Nenhuma página de feature lista este módulo no frontmatter (`modulos`): o consumo passa por `:app`; ver [`features/INDEX.md`](../features/INDEX.md).

## Riscos e dívidas

- **Zero testes:** nenhum arquivo em `src/test` nem `src/androidTest`, apesar de `junit`/`androidx.junit`/`espresso` declarados.
- **Dois modelos concorrentes de localização:** `GerenciadorPermissoesRedeAndroid` exige `ACCESS_FINE_LOCATION` estrita; `LocationPermissionHelper` aceita `COARSE` como suficiente. Convivem sem uma regra única declarada — quem chamar qual muda o resultado.
- **Estado binário sem "negada permanentemente":** o enum `EstadoPermissao` só tem `concedida`/`negada`, então a UI não distingue "ainda não pediu" de "usuário marcou não perguntar de novo" a partir deste módulo.
