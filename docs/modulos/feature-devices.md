---
title: "Módulo :featureDevices"
description: "Descoberta de dispositivos na rede local (ARP, subnet, mDNS, SSDP, TCP probe), identificação, classificação e apelidos."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureDevices`

- **Caminho físico:** `android/feature/devices/` (alias flat legado)
- **Namespace:** `io.signallq.app.feature.devices`

## Responsabilidade

Varre a rede local e produz a lista de dispositivos conectados: descoberta por subnet scan + ARP, mDNS/Bonjour (jmDNS), SSDP/UPnP (com fetch e parse do XML de descrição) e probe TCP de portas. Sobre isso, resolve nome e fabricante por prioridade de fonte (`NamingPrioridade`), classifica o tipo de aparelho (`ClassificadorDispositivoRede`), atribui nível de confiança de identidade (`NivelConfiancaIdentidade`), correlaciona com a topologia Wi-Fi/gateway (`CorrelacaoTopologiaDispositivo`) e gerencia apelidos persistidos em Room via `DevicesViewModel`.

Não é dele: renderizar a lista (`DispositivosScreen.kt` vive no `:app`), exibir notificação de dispositivo novo (o ViewModel só emite o evento; a `MainActivity` notifica, justamente para não depender do `:app`), ler o gateway ativamente (isso vem de `:coreNetwork` como `ClientSnapshot`) nem manter o catálogo OUI (`OuiCatalog`, de `:coreNetwork`).

Comportamento da lista, regras de nome/sem invenção e privacidade: [`features/dispositivos-rede.md`](../features/dispositivos-rede.md).

## Dependências

Extraídas de `android/feature/devices/build.gradle.kts`.

| Tipo | Dependência | Observação |
|---|---|---|
| Plugin | `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.kapt`, `libs.plugins.hilt` | — |
| `implementation` | `libs.hilt.android` + `kapt(libs.hilt.compiler)` | `@HiltViewModel` |
| `implementation` | `libs.androidx.core.ktx`, `libs.kotlinx.coroutines.android`, `libs.androidx.lifecycle.runtime.ktx` | — |
| `implementation` | `com.github.stealthcopter:AndroidNetworkTools:0.4.5.3` | Apache-2.0 — ping sem root + ARP lookup (versão fixa no arquivo, fora do version catalog) |
| `implementation` | `org.jmdns:jmdns:3.6.3` | Apache-2.0 — mDNS/Bonjour com TXT records (fora do catalog) |
| `implementation` | `com.squareup.okhttp3:okhttp:5.5.0` | fetch do XML UPnP (fora do catalog, ao contrário de `libs.okhttp` usado nos outros módulos) |
| `implementation` | `project(":coreDatabase")` | `SignallQDatabase`, `ApelidoDispositivoEntity` |
| `implementation` | `project(":coreDatastore")` | `PreferenciasAppRepository` |
| `implementation` | `project(":coreNetwork")` | contratos `localdevice`/`topologia`/`wifi`, `OuiCatalog`, `DispatcherProvider` |
| `implementation` | `libs.timber` | — |
| `testImplementation` | `libs.junit`, `libs.kotlinx.coroutines.test` | — |
| `androidTestImplementation` | `libs.androidx.junit`, `libs.androidx.espresso.core` | não há `src/androidTest` |

## Consumidores

`grep` por `project(":featureDevices")` em `android/**/build.gradle.kts`:

| Consumidor | Arquivo |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

Nenhum outro módulo consome.

## Componentes principais

| Arquivo / classe | Responsabilidade |
|---|---|
| `.../feature/devices/ScannerDispositivosAndroid.kt` | Implementação do scan: subnet + ARP (AndroidNetworkTools), mDNS (jmDNS), SSDP/M-SEARCH via `DatagramSocket` + fetch do LOCATION (OkHttp), probe TCP, timeout global, progresso e enriquecimento final. |
| `.../feature/devices/NamingPrioridade.kt` | Pipeline puro de nome (`routerActive` > SSDP friendlyName > mDNS TXT > DNS reverso > fallback) e de fabricante (UPnP > mDNS TXT > OUI); lista de nomes genéricos; fontes `routerActive` e variante "provável" por IP. |
| `.../feature/devices/DevicesViewModel.kt` | `@HiltViewModel`. Scan leve/profundo, apelidos via Room, evento `dispositivosNovos` (SharedFlow) — a notificação fica com a `MainActivity` para não inverter a dependência com `:app`. |
| `.../feature/devices/CorrelacaoTopologiaDispositivo.kt` | Correlaciona dispositivo do scan LAN com topologia Wi-Fi/gateway em quatro níveis (`CLIENT_SNAPSHOT_EXATO`, `MAC_EXATO`, `OUI_FRACO`, `SEM_MATCH`); herda papel de topologia só nos níveis fortes. |
| `.../feature/devices/ClassificadorDispositivoRede.kt` | `TipoDispositivo` (roteador, AP, computador, smartphone, smarthome, impressora, console, desconhecido) por listas de nome e OUI. |
| `.../feature/devices/NivelConfiancaIdentidade.kt` | `CONFIRMADA` / `PROVAVEL` / `TEMPORARIA` / `DESCONHECIDA`, com a limitação da chave sintética `ipnome:` documentada. |
| `.../feature/devices/XmlDescricaoUpnpParser.kt` | Parser por regex do device description XML (tolerante a firmware malformado). |
| `.../feature/devices/DispositivosIdentidadeHelper.kt` | Identidade estável entre scans (null quando há MAC; `ipnome:<ip>:<nome>` como fallback). |
| `.../feature/devices/DispositivoRedeExt.kt` | `ehClienteFinal()` — fonte única da contagem de "N dispositivos"; chave de apelido. |
| `.../feature/devices/EstadoScanDispositivos.kt` | Estados tipados: `idle`, `varrendo`, `concluido`, `concluidoParcial`, `semWifi`, `timeout`, `cancelado`, `erro`. |
| `.../feature/devices/MacAddressUtil.kt` | Detecta MAC localmente administrado (randomizado Android 10+) para não consultar OUI indevidamente. |
| `.../feature/devices/DispositivoRede.kt` | Modelo do dispositivo. |
| `.../feature/devices/ScannerDispositivos.kt` | Interface do scanner. |
| `.../feature/devices/FeatureDevicesModulo.kt` | Factory do scanner (recebe `Context` + `OkHttpClient`). |
| `.../feature/devices/SnapshotScanDispositivos.kt` | Snapshot exposto por `StateFlow`. |

## Riscos e dívidas

- **`ScannerDispositivosAndroid.kt` acima de 1200 linhas** — dívida crítica (higiene §7). Concentra cinco protocolos de descoberta (subnet/ARP, mDNS, SSDP, TCP probe, DNS reverso), controle de concorrência (`Mutex`, `Semaphore`, `ConcurrentHashMap`) e enriquecimento; não tem teste direto — os testes cobrem as peças puras extraídas dele.
- **Dependências de terceiros com versão hardcoded** fora do version catalog: `AndroidNetworkTools:0.4.5.3`, `jmdns:3.6.3` e `okhttp:5.4.0`. Esse último cria risco concreto de divergência de versão do OkHttp com os demais módulos, que usam `libs.okhttp`.
- **Tela fora do módulo:** a apresentação vive em `:app` (ver `dispositivos-rede`), incluindo mapeamento de ícones/rótulos.
- **Regra de dependência entre features: respeitada.** Só dependências `:core*`; o KDoc do `DevicesViewModel` registra explicitamente a decisão de não depender do `:app`.
