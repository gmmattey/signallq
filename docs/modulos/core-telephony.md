---
title: "Módulo :coreTelephony"
description: "Coleta de sinal e identidade de célula da rede móvel via TelephonyManager, sem PII e degradando para null quando falta permissão."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:coreTelephony`

- **Caminho físico:** `android/core/telephony/` (alias flat legado — `projectDir` remapeado em `settings.gradle.kts`)
- **Namespace:** `io.signallq.app.core.telephony`
- **Tipo:** biblioteca Android

## Responsabilidade

Fornece os dados de rede móvel usados pelo diagnóstico: tecnologia (5G SA/NSA, 4G, 3G, 2G), RSRP/RSRQ/SINR/EcNo, banda, identidade de célula (cellId, mcc, mnc, tac), roaming e rádio desligado — via `MonitorTelephony.snapshotFlow` e `captureSimsAtivos()` por SIM ativo.

Não é dele: solicitar `READ_PHONE_STATE` ao usuário (isso é da UI), classificar a qualidade do sinal (`MetricClassifier` vive em `:core:diagnostico`, GH#1206), persistir os snapshots ou enviá-los à IA. O contrato de erro é explícito: **nunca lança** — sem permissão, sem SIM ativa ou em emulador, `snapshotFlow.value` permanece `null` e o fato é logado uma vez por sessão.

Uso na aba Móvel e estados sem permissão: [`features/wifi-canais-sinal.md`](../features/wifi-canais-sinal.md).

## Dependências

| Módulo/lib | Para quê |
|---|---|
| `androidx.core.ktx` | `ContextCompat.checkSelfPermission` |
| `kotlinx.coroutines.android` | `StateFlow` do snapshot |
| `timber` | log único de permissão negada/revogada |
| `junit` (test) | `MonitorTelephonyTest` + `MonitorTelephonyFake` |
| `androidx.junit`, `androidx.espresso.core` (androidTest) | scaffolding padrão, sem teste instrumentado próprio |

Nenhuma dependência de outro módulo do monorepo — notadamente **não** depende de `:corePermissions`; a checagem é feita direto com `ContextCompat`.

## Permissões exigidas

Declara **uma única** permissão no `src/main/AndroidManifest.xml`:

| Permissão | Uso |
|---|---|
| `android.permission.READ_PHONE_STATE` | `getAllCellInfo()` + `signalStrength` em rede móvel; runtime desde a API 23 |

Regras registradas no próprio manifesto e no KDoc de `MonitorTelephonyImpl`:

- Solicitação **lazy**: só quando o usuário roda um diagnóstico em rede móvel, com justificativa "para análise de sinal 4G/5G".
- **Não** solicita `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` — `getAllCellInfo()` na API 29+ não exige.
- **Não** usa `getDeviceId`, `getImei` nem `getSubscriberId`.
- `cellId`/`mcc`/`mnc`/`tac` são tratados como metadados sensíveis: coletados só para o diagnóstico de IA, descartados após envio, não persistidos localmente e não logados em produção.

## Consumidores

| Módulo | Tipo |
|---|---|
| `:app` | `implementation` |
| `:featureSpeedtest` | `implementation` |

## Componentes principais

| Arquivo/classe | Responsabilidade |
|---|---|
| `src/main/kotlin/io/signallq/app/core/telephony/MonitorTelephony.kt` | contrato: `snapshotFlow`, `iniciar()`/`encerrar()` idempotentes, `captureSimsAtivos(context)` |
| `src/main/kotlin/io/signallq/app/core/telephony/MonitorTelephonyImpl.kt` | implementação Android: `registerTelephonyCallback` na API 31+, fallback `PhoneStateListener` até a API 30; executor single-thread daemon; `SecurityException` capturada em toda chamada |
| `src/main/kotlin/io/signallq/app/core/telephony/MovelSnapshot.kt` | snapshot da célula servidora — todos os campos opcionais, sem PII |
| `src/main/kotlin/io/signallq/app/core/telephony/MovelSimSnapshot.kt` | snapshot por SIM ativo (subId, slot, operadora, tecnologia, RSRP/RSRQ/SINR, roaming, rádio desligado) |
| `src/main/kotlin/io/signallq/app/core/telephony/CoreTelephonyModulo.kt` | fábrica manual `criarMonitorTelephony(context)` |
| `src/test/kotlin/io/signallq/app/core/telephony/MonitorTelephonyFake.kt` | fake do contrato para uso em teste |

## Riscos e dívidas

- **`MonitorTelephonyImpl.kt` concentra a maior parte do módulo** (perto do limite de 800 linhas): dois caminhos de API (31+ e ≤30), extração de LTE e NR, `SubscriptionManager` e toda a tolerância a `SecurityException` em OEMs. Difícil de testar em unidade — a suíte JVM cobre o contrato via `MonitorTelephonyFake`, não a implementação real.
- **Sem teste instrumentado:** `androidTest` vazio; comportamento em device real (MIUI, dual SIM, modo avião) só é validado manualmente.
- **Checagem de permissão duplicada:** o módulo reimplementa a verificação com `ContextCompat` em vez de reutilizar `:corePermissions`; o snapshot de permissões do app e o deste módulo podem divergir.
- Uso de `@SuppressLint` para contornar avisos de permissão exige que o contrato "nunca lança" continue verdadeiro em cada nova chamada de API adicionada.
