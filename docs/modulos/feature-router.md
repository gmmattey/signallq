---
title: "Módulo :featureRouter"
description: "Driver somente-leitura do roteador TP-Link Archer C6/A6 (protocolo stok-luci) que normaliza o estado para o contrato compartilhado LocalNetworkDeviceSnapshot."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# `:featureRouter`

- **Caminho físico:** `android/feature/router/` (alias flat legado, `projectDir` remapeado em `android/settings.gradle.kts`)
- **Namespace:** `io.signallq.app.feature.router`
- **Tipo:** biblioteca Android

## Responsabilidade

Driver de equipamento local para roteador TP-Link Archer C6/A6 (família `tplink-stok-luci`): faz o
login por senha, lê as páginas de status (`status/all`, `cloud_account/get_deviceInfo`, topologia
OneMesh quando existir) e normaliza tudo para `LocalNetworkDeviceSnapshot` de `:coreNetwork`
(`DeviceType.ROUTER`, `SupportLevel.LAB_VALIDATED`). Só leitura.

Não é dele: a UI de "Equipamento de Internet" e o fluxo de pedir a senha (em `:app`,
`AppShell.kt`), a escolha do host (gateway detectado pelo `:app`), o armazenamento de credenciais
(`CredenciaisModemStore`, `:coreDatastore`) e o driver de ONT Nokia (`:featureFibra`).

## Dependências

| Dependência | Para quê |
|---|---|
| `project(":coreNetwork")` | Contratos `localdevice` (`LocalNetworkDeviceSnapshot`, `DeviceType`, `SupportLevel`…) |
| `libs.androidx.core.ktx`, `libs.org.json` | Utilitários e parsing JSON |
| `libs.junit`, `libs.okhttp.mockwebserver` (test) | Testes JVM com servidor HTTP em loopback |

Sem Hilt, sem OkHttp em produção: o HTTP usa `HttpURLConnection` (`UrlConnectionTpLinkTransport`).

## Consumidores

| Consumidor | Local |
|---|---|
| `:app` | `android/app/build.gradle.kts`; `AppShell.kt` chama `TpLinkArcherC6Driver.probe(ip)` e `TpLinkArcherC6Driver(ip).loginAndRead(senha)` |

## Componentes principais

Fachada, handshake stok-luci, mapper, `TpLinkFailure` e fluxo de leitura estão em [`features/equipamento-internet.md`](../features/equipamento-internet.md) (§7 Mapa de código). Mapa de campos do levantamento: `docs/technical/TPLINK_ARCHER_ROUTER_FIELD_MAP.md`.

## Riscos e dívidas

Ver [`features/equipamento-internet.md`](../features/equipamento-internet.md) (§12): modelo único sem abstração de driver, protocolo por engenharia reversa, wiring no `AppShell.kt` acima de 800 linhas.
