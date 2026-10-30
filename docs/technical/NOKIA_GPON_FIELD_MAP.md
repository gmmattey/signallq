---
title: "Mapeamento de campos — ONT Nokia G-1425G-B (GPON)"
description: "Levantamento da interface web da ONT Nokia G-1425G-B (série ALCL): campos por tela, segredos e esquema de autenticação. Insumo de reconhecimento, não de produto."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Mapeamento de campos — Interface Web ONT Nokia GPON

**Natureza:** reconhecimento (skill `/reconhecimento-equipamento-rede`), não implementação. Levantamento de 2026-07-08 por acesso HTTP direto à interface web da ONT (login RSA+AES conforme o JS da própria ONT), navegando todas as telas do menu. Alimentou o epic SIG-343.
**Fonte de verdade:** o equipamento e o código que o consome: `NokiaModemClient.kt`, `NokiaModemParser.kt`, `NokiaModemCrypto.kt`, `ExecutorFibra.kt` em `android/feature/fibra/` (único scraper real de gateway hoje). Na divergência, vale o código.
**Substitui:** a introdução e as "Oportunidades" da versão anterior, reescritas para refletir o que o parser já consome (recuperável via `git log`).

## Fingerprint do equipamento

| Campo | Valor |
|---|---|
| Vendor (rótulo na UI) | Nokia |
| Manufacturer (TR-069) | ALCL (Alcatel-Lucent, legado — Nokia adquiriu a linha GPON da ALU) |
| Manufacturer OUI | F82229 |
| Model / ProductClass | **G-1425G-B** |
| Hardware Version | 3FE49937ADAA (exemplo real, não é segredo) |
| Software Version (firmware) | 3FE49568IJJJ09 |
| Boot/Bootbase Version | Bootbase1.1-Apr-03-2022--08:23:12 |
| Chipset | MTK7528H (MediaTek) |
| Operator ID (campo interno `operatorid`) | ALCL |
| Board type | ONT |
| Serial Number (Optics/ONT) | formato `ALCLxxxxxxxx` (12 chars, prefixo `ALCL` = OUI Alcatel-Lucent). **Valor real redigido — ver seção Segurança.** |

A UI é servida como app single-page com frame principal (`menu.cgi` monta a árvore
de navegação, cada item carrega um `.cgi` dentro de um `<iframe name="mainFrame">`).
Toda escrita (POST) é criptografada no cliente com RSA (chave pública estática por
device) + AES-CBC, incluindo CSRF token por página. Login também usa esse esquema
(retorna HTTP 299 + header `X-SID` em vez do 200 padrão).

---

## Segurança — nota importante

Durante o levantamento, os seguintes campos **contêm segredo real do equipamento do
Luiz** e foram varridos, mas **NÃO estão reproduzidos com valor real neste
documento** (apenas nome do campo, tela, formato, exemplo fictício):

- Usuário e senha PPPoE do WAN (tela **Network > WAN**, campos `Username`/`Password`
  dentro de `pppConns[]`) — senha vinha **em texto plano** na resposta HTML da tela.
- Chave WPA/PSK das redes Wi-Fi (tela **Network > Wireless 2.4GHz/5GHz**, campo
  `PreSharedKey` dentro de `psks{}`) — vem em formato ofuscado/cifrado (base64-like,
  não texto plano), mas ainda assim tratado como segredo.
- Segredos de servidor RADIUS (`radiusPassword`, `Secret`/`SecondarySecret` em
  `Accounting_2Ghz`), mesma tela.
- Chave WEP (`wepKeys{}`), mesma tela (WEP legado, desabilitado por padrão).
- Serial Number do ONT / do módulo óptico e do dispositivo mesh (`SerialNumber`,
  usado como fingerprint único — tratado como identificador de assinante).
- Usuário/senha de acesso à própria interface administrativa (não exibidos na UI
  após login, mas é o próprio credential usado para autenticar).

Todos os demais campos documentados abaixo (potência óptica, temperatura, contadores
de erro, versão de firmware, uptime, IP externo, VLAN, canais Wi-Fi, tabela de
portas LAN, etc.) **são valores reais capturados do equipamento**, pois não são
segredo.

---

## Menu: Status

### Overview (`overview.cgi`)

Tela-resumo. Fonte: objeto JS `DeviceStatus` + `WanStatus` + `device_info`.

| Campo | Tipo | Usado hoje no SignallQ? |
|---|---|---|
| WAN IP (`WanStatus.ExternalIPAddress`) | string (IPv4) | Sim — `WanStatus.externalIp` (`NokiaModemParser.parseWan`) |
| Internet Status (`WanStatus.gwwanup`) | bool (0/1) | Parcial — não há campo dedicado de "internet on/off" na model atual |
| WiFi Status (agregado 2.4G/5G Enabled) | enum (On/Off) | Não |
| Voice Status (`Voice_status.lines_config[].status`) | enum | Não (não há linha de voz configurada neste equipamento) |
| Network Topology (contagem de nós mesh + `device_app_status`) | int | Não |
| Devices (`DeviceStatus.Device_list[]` — lista de leases DHCP: `HostName`, `IPAddress`, `MACAddress`, `InterfaceType`) | array de objetos | **Não** — o SignallQ hoje descobre dispositivos via varredura de rede nativa do Android (`ScannerDispositivosAndroid`, ARP/SSDP), não via leitura do DHCP server do modem |
| `beacon_detail[]` (nós mesh detectados, com `SerialNumber`/`MACAddress`/`FriendlyName`) | array | Não |

### Device Information (`device_status.cgi`)

Fonte: `dev_info`, `mem_info`, `cpu_usageinfo`, `cpu_temperatureinfo`, `gpon_status`.

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| Device Name (`ProductClass`) | string | — | Sim — `DeviceInfoFibra.model` |
| Vendor (hardcoded "Nokia" na UI) | string | — | Não (model atual guarda `manufacturer` = `dev_info.Manufacturer`, que é "ALCL", não "Nokia") |
| Serial Number | string | — | Sim — `DeviceInfoFibra.serialNumber` |
| Hardware Version | string | — | Sim — `DeviceInfoFibra.hardwareVersion` |
| Boot Version (`AdditionalSoftwareVersion`) | string | — | Não |
| Software Version | string | — | Sim — `DeviceInfoFibra.firmwareVersion` |
| Chipset (`X_ASB_COM_Chipset`) | string | — | Não |
| Device Running Time (`UpTime`) | int | segundos | Sim — `DeviceInfoFibra.uptimeSeconds` |
| `lot_number` (data de fabricação, formato YYMMDD) | string | — | Não |
| `mem_info.Total` / `mem_info.Free` | int | KB | **Não — dado novo** (memória livre do ONT, indicador de saúde do equipamento) |
| `cpu_temperatureinfo.CPUTemp` | string numérico | °C | **Não — dado novo** (temperatura da CPU do ONT, distinto da temperatura do módulo óptico) |
| `FirstUseDate` | string (ISO date, geralmente epoch=1970 se nunca ativado via TR-069) | — | Não |

### LAN Status (`lan_status.cgi?lan`)

Fonte: `lan_ether` (4 portas Ethernet), `lan_ifip`, `is_superuser`.

Por porta Ethernet (1 a 4, ou mais se houver porta 10G — `g_has_10g_port`):

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| `Enable` | bool | — | Não |
| `Status` (`Up` / `NoLink`) | enum | — | **Não — dado novo**, valioso para diagnosticar se o problema é físico no cabo LAN |
| `MACAddress` | string (MAC) | — | Não |
| `MaxBitRate` / `X_ALU_COM_CurMaxBitRate` | string ("Auto", "1000") | Mbps | **Não — dado novo** (velocidade negociada da porta LAN) |
| `DuplexMode` / `X_ALU_COM_CurDuplexMode` | string ("Auto"/"Full") | — | Não |
| `X_ASB_COM_PhyType` | int (3=1G, 4=10G) | — | Não |
| `stat.BytesSent` / `BytesReceived` | int | bytes | Não |
| `stat.PacketsSent` / `PacketsReceived` | int | pacotes | Não |
| `stat.ErrorsSent` / `ErrorsReceived` | int | pacotes | **Não — dado novo** (erros de camada física por porta) |
| `stat.DiscardPacketsSent` / `DiscardPacketsReceived` | int | pacotes | Não |
| `stat.Multicast/BroadcastPacketsSent/Received` | int | pacotes | Não |

`lan_ifip`: IP/máscara da interface LAN do próprio ONT (gateway local, ex.
`192.168.1.254` / `255.255.255.0`) — não usado hoje (o app descobre o gateway via
Android, não via leitura direta do modem).

### WAN Status / WAN Status IPv6 (`show_wan_status.cgi?ipv4` / `?ipv6`)

Tela de "WAN Connection List" (uma linha por VLAN/serviço provisionado pela
operadora — neste equipamento há 3: INTERNET via PPPoE, INTERNET via DHCP em outra
VLAN, e OTHER via DHCP).

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| Access Type | string | — | Não |
| Connection Mode / Enable/Disable | enum/bool | — | Parcial (`connectionType` existe na model) |
| VLAN | int | — | Sim — `WanStatus.vlanId` |
| WAN Link Status / PON Link Status | enum (Up/Down) | — | Não |
| WAN MAC Address | string (MAC) | — | Não |
| IPv4/IPv6 Address, Netmask/Prefix, Gateway | string | — | Sim (IPv4 externo/gateway/DNS) — IPv6 **não** |
| Primary/Second DNS, Manual DNS | string (IP) | — | Sim (primaryDns/secondaryDns) |
| PPPoE Concentrator | string | — | Sim — `pppoeConcentrator` |
| PPPoE Connection Failure / ISP Failure / BRAS Connection Status / Authentication Failure | enum/string | — | **Não — dado novo**, valioso para diagnosticar causa de queda de PPPoE sem depender só de "conectado/desconectado" |
| Tx/Rx Packets, Tx/Rx Dropped, Err Packets | int | pacotes | **Não — dado novo** (estatísticas de tráfego por conexão WAN) |

### Home Networking — `wlan_status` (CORRIGIDO 2026-07-10: vive em `lan_status.cgi?lan`, não `?wlan`)

**Revalidação contra equipamento real em 2026-07-10 corrigiu dois erros da versão original deste documento:**

1. **URL errada.** `wlan_status` (radios Wi-Fi com canal/segurança/potência) é servido por
   `lan_status.cgi?lan` — a mesma página já buscada para os dados de LAN — e **não**
   `lan_status.cgi?wlan` como esta seção descrevia originalmente. A página `?wlan`
   serve outros objetos (ver subseção abaixo).
2. **Formato errado.** `wlan_status` é um **objeto indexado por chave numérica**
   (`{1:{...}, 2:{...}, ..., 8:{...}}`), não um array (`[{...}, {...}]`). 8 slots reais
   observados: chaves 1–4 = rádio 2.4GHz (1 principal + 3 guest), chaves 5–8 = rádio
   5GHz (1 principal + 3 guest) — todos os 8 sempre presentes na resposta, ativos ou não.

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| `RadioEnabled` | bool (0/1) | — | Sim, mas com ressalva: reflete o rádio físico do band inteiro, fica `1` mesmo com o SSID guest daquele slot desligado — não usar sozinho para decidir se aquele SSID específico está ativo |
| `Enable` | bool (0/1) | — | **Sim** — flag correta para "este SSID está ativo"; tem prioridade sobre `RadioEnabled` no parser (`GH#865`) |
| `SSID` | string | — | Sim |
| `Channel` / `ChannelsInUse` | int | — | Sim (`Channel`) |
| `BeaconType` (ex. `WPAand11i`, `11i`) | enum | — | Sim, traduzido para rótulo humano (Sem senha/WEP/WPA/WPA2/WPA3/não identificada) |
| `Standard` (ex. `b,g,n` / `a,n,ac`) | string | — | Sim — usado só para inferir banda (`ac`/`ax` → 5GHz) |
| `WPAAuthenticationMode` / `WPAEncryptionModes` | string | — | Não — `BeaconType` já cobre a tradução de segurança hoje; esses dois dariam uma tradução mais precisa (ex. distinguir WPA2 de WPA3) se o `BeaconType` sozinho ficar ambíguo |
| `TransmitPower` | int | % | Sim |
| `TotalAssociations` | int | qtd de clientes conectados naquele SSID | **Não — dado novo** |
| `TotalBytesSent/Received`, `TotalPacketsSent/Received` | int | bytes/pacotes | Não |
| `X_ASB_COM_RxErrors` / `RxDrops` / `TxErrors` / `TxDrops` | int | pacotes | **Não — dado novo** (taxa de erro/descarte Wi-Fi por rádio, direto do chipset do AP embutido no ONT) |

**Nota de UX:** o app filtra e exibe só os SSIDs com `Enable=1` (decisão de produto de
2026-07-10) — mostrar as 8 entradas (a maioria guest/desligada) polui a tela sem
valor de diagnóstico.

### `lan_status.cgi?wlan` — objetos reais encontrados (diferente do que a URL sugere)

Contrário à suposição original deste documento, esta página **não** contém
`wlan_status`. Os objetos reais encontrados na revalidação de 2026-07-10:

- **`wlan_ssid`** — objeto indexado por chave numérica (mesmas 8 chaves de
  `wlan_status`), só com `SSID`, `BSSID`, `X_ASB_COM_routeMode`. Redundante com
  `wlan_status.SSID` — sem valor adicional hoje.
- **`device_cfg`** — array com a lista real de dispositivos conhecidos pelo
  equipamento: `HostName`, `IPAddress`, `MACAddress`, `InterfaceType` (`Ethernet`/
  outros), `Active` (0/1), `AddressSource` (`DHCP`/`Static`), `LeaseTimeRemaining`.
  **Não usado hoje — dado novo relevante**: é a fonte que poderia alimentar a lista
  de clientes conectados (`ClientSnapshot`) e o selo "confirmado pelo roteador"
  (issue #839), hoje sempre vazia por falta desse wiring.
- **`alias_cfg`** — array com apelido atribuído pelo usuário por MAC (`MACAddress`,
  `HostName`, `HostAlias` — ex. `HostAlias:"Notebook da TIM"`). **Não usado hoje —
  dado novo**, potencialmente melhor que `device_cfg.HostName` para nomear
  dispositivo na UI (é o nome que o próprio usuário deu no roteador, não um hostname
  técnico de rede).
- `lan_ifip`, `is_superuser` — duplicam objetos já descritos em outras seções
  (mesmo template compartilhado entre as abas de "Home Networking").

### Optics Module Status (`wan_status.cgi?gpon`) — tela mais relevante para fibra

Fonte: `gpon_status`. **Esta é a tela que já alimenta o `NokiaModemParser` hoje.**

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| Serial Number | string | — | Sim |
| `Status` (Up/Down) | enum | — | Sim (`GponStatus.isUp`) |
| `ConnectionMode` (`VlanMuxMode`) | string | — | Sim (`GponStatus.mode`) |
| Rx Optics Signal Level — `RXPower` | raw int → conversão dBm | dBm | Sim (`rxPowerDbm`) |
| Tx Optics Signal Level — `TXPower` | raw int → conversão dBm | dBm | Sim (`txPowerDbm`) |
| Laser Bias Current — `BiasCurrent` | raw int → mA | mA | Sim (`laserCurrentMa`) |
| Optics Module Voltage — `SupplyVottage` (sic, typo de fábrica) | raw int → V | V | Sim (`voltageV`) |
| Optics Module Temperature — `TransceiverTemperature` | raw int (Q8.8) → °C | °C | Sim (`temperatureCelsius`) |
| **Rx Threshold Lower — `RXPowerLower` + `RXPowerLowerDec`** | raw int + decimal fracionário → dBm | dBm | **Não — dado novo**, muito valioso: permite dizer "sinal está a X dB da falha" em vez de só o valor absoluto |
| **Rx Threshold Upper — `RXPowerUpper` + `RXPowerUpperDec`** | idem | dBm | **Não — dado novo**, mesmo racional (excesso de sinal também é problema, satura o receptor) |
| `stats.FECError` | int (contador) | erros corrigidos | **Não — dado novo**, indicador de qualidade de linha óptica antes de virar perda de pacote visível |
| `stats.HECError` | int (contador) | erros de cabeçalho GPON | **Não — dado novo** |
| `stats.DropPackets` | int | pacotes descartados na camada GPON | **Não — dado novo** |
| `stats.BytesSent/Received`, `PacketsSent/Received`, `S/RUnicast`, `S/RMulticast`, `S/RBroadcast`, `S/ROmciPackets` | int | bytes/pacotes | Não |
| `fec_state` / `aes_state` (flags de FEC e criptografia AES da linha GPON) | bool | — | Não |
| `FECAbility` / `FECMode` / `LightSingnalMode` | int/flag | — | Não |

Exemplo real capturado (não é segredo): RX ≈ **-23,19 dBm**, TX ≈ **+2,63 dBm**,
temperatura do módulo ≈ **48,5 °C**, bias current ≈ **13,99 mA**, threshold
Rx Lower ≈ **-27,95 dBm**, threshold Rx Upper ≈ **-7,00 dBm** (ou seja, o sinal
real está ~4,8 dB acima do piso mínimo aceitável — margem apertada, útil pra
ilustrar por que esse threshold importa).

> **Gambiarra encontrada no firmware (não é do SignallQ, é da própria Nokia):** o
> JS da tela calcula a tensão com uma função `to_V()` (raw × 0.0001, resultado
> correto em Volts) mas **não usa essa função** — o `<div>` exibido na tela usa
> `SupplyVottage*100 + " uV"`, que gera um número absurdo (ex. "3250000 uV" em vez
> de "3.25 V"). O parser do SignallQ já faz a conta certa (`/10000.0`), então isso
> não afeta o app — só registra que a UI oficial do fabricante está com uma conta
> furada há quem sabe quantas versões de firmware.

### Statistics (`statistics.cgi`)

Tela agregada com 3 abas (tabs) que apenas reapresentam os mesmos objetos já
descritos acima (`lan_ether`, `wlan_status`, `wan_conns`) em formato de tabela
comparativa. Não introduz campo novo além dos já listados.

### Voice Information (`voice_info.cgi`)

Retornou **corpo vazio (0 bytes)** neste equipamento — não há linha de voz/VoIP
provisionada. A tela existe no menu mas não há dado a mapear neste device
específico.

---

## Menu: Network

### LAN (`lan_ipv4.cgi`)

Fonte: `ipv4_config` (servidor DHCP), `ipv4_intf`, `ipv4_route`, `device_cfg`
(leases), `bind_mac`, `static_nat`.

| Campo | Tipo | Unidade | Usado hoje? |
|---|---|---|---|
| `DHCPServerEnable` | bool | — | Não |
| `MinAddress` / `MaxAddress` (range do pool DHCP) | string (IP) | — | Não |
| `SubnetMask` | string | — | Não |
| `DHCPLeaseTime` | int | segundos | Não |
| Pools dedicados por categoria (`X_CT_COM_STB_*`, `_Phone_*`, `_Camera_*`, `_Computer_*`) | bool + range IP | — | Não (feature de QoS por tipo de dispositivo, não usada) |
| `device_cfg[]` (leases: `HostName`, `IPAddress`, MAC implícito) | array | — | **Não — dado novo/complementar** ao scanner Android atual |
| `ReservedAddresses` / `bind_mac[]` (DHCP reservado por MAC) | array | — | Não |
| `static_nat[]` | array | — | Não |

### LAN_IPv6 (`lan_ipv6.cgi`)

Config de delegação de prefixo IPv6 (`ipv6_prefix`, `LanPrefix`, `LanPriDns`,
`LanSecDns`, range de RA `ra_min`/`ra_max`, `pool_Min`/`pool_Max`). Nenhum campo
usado hoje pelo SignallQ (app não trata IPv6 na fibra ainda).

### WAN (`wan_config_glb.cgi`)

Config das 3 conexões WAN provisionadas (`wan_conns[]`), uma por VLAN:

| Campo | Tipo | Usado hoje? |
|---|---|---|
| `Name` (ex. `1_INTERNET_R_VID_1002`) | string | Não |
| `ConnectionType` (`IP_Routed`, `PPPoE_Bridged`) | enum | Sim (mapeado como `connectionType`) |
| `NATEnabled` | bool | Não |
| `ConnectionTrigger` (`AlwaysOn`) | enum | Não |
| `Username` / `Password` (credenciais PPPoE) | string | **Segredo — não redigido aqui, ver seção Segurança** |
| `X_CT_COM_ServiceList` (`INTERNET`, `OTHER`) | string | Não |
| `InterfaceMtu` | int | Não (mas relevante — MTU real da WAN, ex. 1492 no PPPoE vs 1500 no IP_Routed) |
| `xponLinkCfg.VLANIDMark` | int | Sim (vlanId) |
| `X_CT_COM_Dslite_Enable` / `Aftr` (DS-Lite/IPv6 transition) | bool/string | Não |
| `dial_status.LastConnectionError` | enum | Não |

### Wireless (2.4GHz) / Wireless (5GHz) (`wlan_config.cgi` / `?v=11ac`)

Config completa por SSID (`wlan_config`), superset do que aparece em "Home
Networking" (status), incluindo campos de **escrita**:

| Campo | Tipo | Usado hoje? |
|---|---|---|
| `PreSharedKey` (dentro de `psks{}`) | string | **Segredo** |
| `wepKeys{}` | string | **Segredo** (WEP legado) |
| `radiusServerIp`/`radiusPort`/`radiusPassword` (+ secundário) | string/int | **Segredo** (senha) |
| `Accounting_2Ghz.Secret`/`SecondarySecret` | string | **Segredo** |
| `WPAAuthenticationMode` / `WPAEncryptionModes` | enum | Não |
| `AutoChannelEnable` / `Channel` / `PossibleChannels` | bool/int/string | Não |
| `WPSEnable` / `WPSMode` / `X_CT_COM_WPSKeyWord` | bool/enum/int | Não |
| `X_ASB_COM_RSSIThreshold` | int (dBm) | Não — **dado novo**, threshold de desconexão por RSSI configurado no AP |
| `X_ASB_COM_BaseCfg_GlobalMaxAssoc` / `VirtualIfCfg_MaxAssoc` | int | Não (limite de clientes simultâneos) |
| `X_ASB_COM_OperatingChannelBandwidth` (`20MHz`) | enum | Não |
| `SSIDIsolate` / `IsolationEnable` (isolamento de cliente) | bool | Não |

### Wireless Schedule (`wifi_schedule.cgi`)

Fonte: `wifi_schedule_config`, `schedule_list`, dias/horários de liga-desliga
programado do Wi-Fi. Nenhum campo usado hoje; não parece relevante para
diagnóstico (é feature de controle parental/economia, não de qualidade de sinal).

### MESH (`mesh.cgi`)

Fonte: `beacon_detail[]` (vazio neste device — sem satélites mesh pareados),
`meshStatus` (enum: `NotDetected`/`Detected`/`Configured`/`ConfigurationFailed`),
`meshBackhaulStatus` (enum: `NotConnected`/`Connected_Bad`/`Connected_Average`/
`Connected_Good`/`Disconnected`).

Nenhum campo usado hoje. **Dado novo potencialmente valioso** se o usuário tiver
extensores/mesh Nokia: qualidade do backhaul entre unidades já vem pronta em enum
qualitativo (Bad/Average/Good), sem precisar o app inferir isso sozinho.

---

## Menu: Security

Nenhum campo desta seção é usado hoje pelo SignallQ (fora de escopo de
diagnóstico de conectividade/fibra — é configuração de segurança de rede).

- **Firewall** (`firewall.cgi?fire`): `firewallLevel` (nível 1-3 + `Config` on/off),
  `dosProtectionEnable`, regras avançadas por chain (`firewall_chain_advance`:
  `Target` Accept/Drop, `Protocol`, `IPVersion`, `ExpiryDate`, `Log`).
- **MAC Filter** (`macfilter.cgi`): modo (allow/deny list), lista de MACs.
- **IP Filter** (`ipfilter.cgi`): regras por IP/porta/protocolo, direção (US/DS).
- **URL Filter** (`urlfilter.cgi`): lista de domínios bloqueados.
- **Parental Control** (`parental_control.cgi`): políticas por dispositivo
  (IP/MAC), dias da semana, janela de horário.
- **DMZ and ALG** (`nat_glb.cgi?v=alg`): `dmz_enabled`, `dmzaddr`, lista de ALGs.

---

## Menu: Application

- **Port Forwarding** (`nat_glb.cgi?v=vhost`): `port_mappings[]` (protocolo,
  porta WAN/LAN, IP interno, nome da regra).
- **Port Triggering** (`nat_glb.cgi?v=thost`): `port_triggerings[]`.
- **DDNS** (`ddns.cgi`): `ddnsIsp` (provedor), `domainName`, `userName`/`userPswd`
  (**segredo** se preenchido — neste equipamento o serviço estava sem
  provedor configurado).
- **NTP** (`sntp.cgi`): `TimeZones_data`, até 3 servidores NTP configuráveis
  (`select_sever1/2/3`), hora atual do sistema (`time`).
- **UPNP and DLNA** (`upnp.cgi`): `upnp_config` (enable/disable UPnP IGD e DLNA).

Nenhum campo usado hoje; nenhum parece relevante para diagnóstico de
conectividade/fibra (são features de configuração de rede avançada).

---

## Menu: Maintenance

- **Password** (`user_glb.cgi`): troca de senha do usuário logado (`oldpassword`,
  `password`, `password2`), medidor de força (`score`, regras de composição —
  `capitalNum`/`lowercaseNum`/`numberNum`). Não expõe lista de usuários nem a
  senha atual.
- **Device Management** (`device_name.cgi`): apelido customizado do próprio ONT
  (`alias`, `alias_cfg`).
- **Reboot Device** (`reboot.cgi`): apenas ação (confirmação + botão), sem campos
  de leitura adicionais.
- **Factory Default** (`restore.cgi`): idem — apenas ação destrutiva com
  confirmação, sem campos de leitura.
- **Diagnostics** (`diag.cgi?ping`): ferramenta de ping/traceroute **rodada a
  partir do próprio ONT** (não do celular). Campos: IP Version (v4/v6),
  Interface (LAN/WAN), IP ou domínio alvo, contagem de pings (default 4),
  tamanho de pacote (default 64 bytes), TTL/hops de traceroute (default 30).
  **Dado novo interessante**: permite isolar se a perda de pacote é entre
  ONT↔Internet (roda no próprio equipamento, sem passar pelo Wi-Fi do celular)
  vs. celular↔ONT — hoje o SignallQ só mede a partir do celular.
- **Log** (`log.cgi`): configuração de nível de log (`Writing Level` /
  `Reading Level`, enum Emergency..Debug) + visualizador de syslog local
  (endpoint `?vlog_glb`, carrega texto plano do log do kernel/sistema do ONT).
  Não explorado em profundidade (log ao vivo não foi extraído neste
  levantamento — fora do escopo de "campos de tela", e potencialmente contém
  dados operacionais extensos).

## Menu: RG Troubleshooting / SmartHome

Ambos os itens de menu existem na árvore de navegação (`menu.cgi`) mas retornaram
**sem sub-itens** (`nodes: []`) neste equipamento/firmware — nenhuma tela
associada a explorar.

---

## Oportunidades

Conferido contra `android/feature/fibra` em 2026-10-04. **Já consumidos pelo `NokiaModemParser`:** `lan_ether` (status e erros por porta LAN), `X_ASB_COM_*` (erros/drops por rádio Wi-Fi) e `device_cfg[]` (leases DHCP). **Ainda sem consumo no app**, em ordem de valor para diagnóstico de fibra:

1. **`RXPowerLower`/`RXPowerUpper`**: margem até o limite de falha do transceptor, em vez de só RX/TX absolutos contra faixa fixa.
2. **`stats.FECError`/`HECError`/`DropPackets`** (camada GPON): erro corrigido/descartado que aparece antes da perda de pacote perceptível; bom para alerta preditivo.
3. **Diagnóstico nativo** (`diag.cgi?ping`): ping/traceroute a partir da ONT isola celular↔roteador de roteador↔internet.
4. **`mem_info`/`cpu_temperatureinfo`**: saúde do próprio equipamento (memória baixa ou CPU quente explicam instabilidade intermitente).
5. **`meshStatus`/`meshBackhaulStatus`**: só para quem tem extensores Nokia pareados.

Varridos e de **baixo valor** (configuração pura): Firewall, MAC/IP/URL Filter, Parental Control, DMZ/ALG, Port Forwarding/Triggering, DDNS, NTP, UPnP, Wireless Schedule.

---

## Referência técnica — esquema de autenticação da UI

Documentado aqui porque é reutilizável para qualquer scraping futuro desta
mesma família de firmware Nokia (`G-14xxG-*`, série ALCL):

1. `GET /` retorna form de login com RSA pubkey estática (PEM, embutida no HTML)
   e, a cada carregamento, um `nonce` (base64, 32 bytes) e `token` CSRF novos.
2. Client gera AES-128 key + IV aleatórios, monta plaintext
   `&username=...&password=...&csrf_token=...&nonce=...&enckey=...&enciv=...`,
   cifra em AES-CBC, e cifra o par (key,iv) com RSA PKCS1v1.5 usando a pubkey da
   página.
3. `POST /login.cgi` com corpo `encrypted=1&ct=<base64url(AES-CBC ciphertext)>&ck=<base64_custom_escape(RSA(key+iv))>` (`base64_custom_escape`: `+`→`-`, `/`→`_`, `=`→`.`).
4. Sucesso: HTTP **299** (não 200!) + header `X-SID` + cookies `sid`/`lsid`.
5. Navegação normal por GET nas páginas `.cgi` do menu, cookies `lang`/`sid`/`lsid`
   bastam (não precisa repetir handshake RSA por página — só para POSTs de
   escrita, que usam a mesma pubkey + um CSRF token embutido na própria página).
6. Logout: `GET /login.cgi?out`.
