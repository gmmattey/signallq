---
title: "Quem está usando sua rede"
description: "Lista os aparelhos conectados à rede Wi-Fi local, com nome honesto, apelido e papel na topologia, para quem quer saber o que consome a sua internet."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.0.0"
feature: "dispositivos-rede"
tipo: "jornada"
modulos:
  - "android/feature/devices"
  - "android/app"
  - "android/core/database"
  - "android/core/network"
arquivos:
  - "android/feature/devices/src/main/kotlin/io/signallq/app/feature/devices/"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DispositivosScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DispositivosLista.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DispositivoDetalheSheet.kt"
  - "android/core/database/src/main/kotlin/io/signallq/app/core/database/ApelidoDispositivoDao.kt"
  - "android/core/database/src/main/kotlin/io/signallq/app/core/database/ApelidoDispositivoEntity.kt"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/topologia/oui/OuiCatalog.kt"
contratos: []
eventos:
  - "feature_blocked_remote (feature_id=devices)"
flags:
  - "consumer_devices_enabled"
testes:
  - "android/feature/devices/src/test/kotlin/io/signallq/app/feature/devices/ClassificadorDispositivoCaracterizacaoTest.kt"
  - "android/feature/devices/src/test/kotlin/io/signallq/app/feature/devices/ClassificadorDispositivoTest.kt"
  - "android/feature/devices/src/test/kotlin/io/signallq/app/feature/devices/CorrelacionarDispositivoComTopologiaTest.kt"
  - "android/feature/devices/src/test/kotlin/io/signallq/app/feature/devices/DevicesViewModelTest.kt"
  - "android/feature/devices/src/test/kotlin/io/signallq/app/feature/devices/ScannerDispositivosClienteTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DispositivosScreenExtracaoCaracterizacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DispositivosResumoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DispositivosTopologiaLabelsTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DispositivosListaNativeAdTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/ResolverDispositivoParaNoTopologiaTest.kt"
adrs: []
thresholds_em: ""
---

# Quem está usando sua rede

Migrado de `FUNCIONAL.md` (§5.4) e `ARQUITETURA/MODULOS/feature-devices.md`. Fatos conferidos no código em 2026-10-04. Esta feature não tem limiar numérico de decisão (`thresholds_em` vazio de propósito).

## NEGÓCIO

### 1. Problema e promessa

Responde "quais aparelhos estão na minha rede?". Card do hub Ferramentas: **"Quem está usando sua rede"** / "Veja os aparelhos conectados" (`FerramentasScreen.kt`). Promete a lista descoberta e identificada com honestidade; **nunca inventa marca ou tipo**.

### 2. Quando aparece e para quem

`DispositivosScreen` (overlay via Ferramentas, também aberto a partir de outros pontos como a Início). **Só funciona em Wi-Fi**: em rede móvel ou offline mostra fallback explicativo (`SemWifiFallback` em `DispositivosLista.kt`) em vez da lista. A varredura roda com pull-to-refresh.

### 3. Regras de decisão

- **Seções da lista:** **Infraestrutura** (o gateway, subtítulo "IP · bandas Wi-Fi · N clientes"), **Pontos de acesso** (nós mesh, badge "AP Mesh") e **Dispositivos**. O próprio celular aparece como "Este aparelho".
- **Nome e fabricante** (dado medido, pipeline determinístico em `NamingPrioridade.kt`): nome por `routerActive` > SSDP friendlyName > mDNS TXT > DNS reverso > fallback; fabricante por UPnP > mDNS TXT > OUI.
- **Sem invenção:** dispositivo sem nome resolvido e sem fabricante confirmado mostra "Dispositivo desconhecido" (ou "Dispositivo <Fabricante>" quando o fabricante é confirmado via OUI/UPnP/mDNS) com ícone genérico (decisão do Luiz, #1663, 2026-08-19).
- **Detalhe:** tocar abre sheet com IP, MAC mascarado, fabricante, tipo e, só com correlação confirmada de topologia, conexão física e papel na rede. A correlação scan LAN × topologia Wi-Fi tem quatro níveis (`CLIENT_SNAPSHOT_EXATO`, `MAC_EXATO`, `OUI_FRACO`, `SEM_MATCH`).
- **Apelido:** o usuário pode apelidar qualquer dispositivo; persistido por MAC, com fallback para IP+nome quando o Android não resolve o MAC via ARP.
- **Contagem:** "N dispositivos" usa `ehClienteFinal()` como fonte única.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Rede móvel ou offline | Fallback explicativo; sem lista |
| Resultado parcial (`concluidoParcial`) | Avisa "uma etapa da varredura não respondeu" |
| Estados do scan | `idle`, `varrendo`, `concluido`, `concluidoParcial`, `semWifi`, `timeout`, `cancelado`, `erro` |
| Sem nome nem fabricante | "Dispositivo desconhecido", ícone genérico |
| MAC randomizado (Android 10+) | Não consulta OUI indevidamente (`MacAddressUtil.kt`) |
| Sheet de AP mesh | "Sinal, banda e clientes conectados não estão disponíveis via varredura passiva. Para métricas detalhadas, acesse o painel do seu roteador mesh." |

Timeout nunca vira sucesso; ausência de dado não vira zero.

### 5. Próximo passo e confirmação

Apelidar aparelhos e reconhecer o que é desconhecido; puxar para atualizar confirma a lista. Um dispositivo novo na rede gera notificação (emitida pelo `DevicesViewModel` como evento `dispositivosNovos`, disparada pela `MainActivity`).

### 6. Fora de escopo, status e flag

Status: entregue. Flag `consumer_devices_enabled` (módulo `devices`) gateia a rota. Não lê tráfego nem bloqueia aparelho. Privacidade: IP/MAC/SSID **nunca** vão para analytics; a telemetria do scan só emite contagem de dispositivos.

## TÉCNICO

### 7. Mapa de código

| Responsabilidade | Módulo | Arquivo |
|---|---|---|
| Scan (subnet+ARP, mDNS, SSDP/UPnP, probe TCP) | android/feature/devices | `ScannerDispositivosAndroid.kt` (interface `ScannerDispositivos.kt`, factory `FeatureDevicesModulo.kt`) |
| Estado, scan leve/profundo, apelidos, evento de dispositivo novo | android/feature/devices | `DevicesViewModel.kt` |
| Nome/fabricante | android/feature/devices | `NamingPrioridade.kt`, `XmlDescricaoUpnpParser.kt` |
| Tipo e confiança | android/feature/devices | `ClassificadorDispositivoRede.kt`, `NivelConfiancaIdentidade.kt` (`CONFIRMADA`/`PROVAVEL`/`TEMPORARIA`/`DESCONHECIDA`) |
| Correlação com topologia | android/feature/devices | `CorrelacaoTopologiaDispositivo.kt` |
| Identidade entre scans | android/feature/devices | `DispositivosIdentidadeHelper.kt` (chave `ipnome:<ip>:<nome>` quando não há MAC), `DispositivoRedeExt.kt` |
| Telas | android/app | `DispositivosScreen.kt` (scaffold), `DispositivosLista.kt`, `DispositivoDetalheSheet.kt` (`DeviceDetailSheet`, `MeshApSheet`) |
| Apelidos | android/core/database | `ApelidoDispositivoDao.kt`, `ApelidoDispositivoEntity.kt` |
| OUI | android/core/network | `topologia/oui/OuiCatalog.kt` |

Dependências do módulo: `:coreDatabase`, `:coreDatastore`, `:coreNetwork`, mais `AndroidNetworkTools` e `jmdns` (fora do version catalog). Único consumidor Gradle: `:app`. Detalhe: [`feature-devices.md`](../modulos/feature-devices.md).

### 8. Dados e contratos

Sem contrato OpenAPI. O apelido é a única persistência (Room, por MAC). O snapshot do scan é exposto por `StateFlow` (`SnapshotScanDispositivos`). MAC mascarado na UI.

### 9. Eventos e flags

`consumer_devices_enabled` (`FeatureFlagKeys.kt`); o bloqueio emite `feature_blocked_remote` com `feature_id=devices`. `feature_used` para esta tela: **não verificado**. A telemetria do scan emite só a contagem (sem IP/MAC).

### 10. Falhas e fallback

Timeout global do scan (estado `timeout`), cancelamento, scan parcial com aviso, sem Wi-Fi. Falha de uma etapa não derruba as demais.

### 11. Testes

Lista em `testes:`. A extração em três arquivos (#1663) tem teste de caracterização.

### 12. Riscos

- `ScannerDispositivosAndroid.kt` acima de 1200 linhas (dívida crítica, `higiene` §7): concentra cinco protocolos de descoberta.
- Versões hardcoded fora do catálogo (`AndroidNetworkTools`, `jmdns`, `okhttp`) com risco de divergência do OkHttp dos outros módulos.
- `DispositivoDetalheSheet.kt`/`DispositivosLista.kt` devem ficar abaixo de 800 linhas; cada sheet nova ganha arquivo próprio.
