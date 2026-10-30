---
title: "Mapeamento de campos — roteador TP-Link Archer (stok-luci)"
description: "Levantamento da interface web do TP-Link Archer C6/A6 v2 (família tplink-stok-luci): campos por menu, achados de segurança e esquema de autenticação. Insumo de reconhecimento, não de produto."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Mapeamento de campos — Interface Web Roteador TP-Link (stok-luci)

**Natureza:** reconhecimento (skill `/reconhecimento-equipamento-rede`), não implementação; mesmo racional de `NOKIA_GPON_FIELD_MAP.md` (a ONT à frente deste roteador). Levantamento de 2026-07-08/09 por HTTP direto, replicando em Node.js o esquema de autenticação RSA duplo + AES da própria interface (`tpEncrypt.js`/`encrypt.js` servidos pelo equipamento). Alimentou o epic SIG-343.
**Fonte de verdade:** o equipamento. **Não existe parser TP-Link no SignallQ**; "TP-Link" no código é só fabricante no catálogo OUI (`OuiCatalog.kt`). Nenhum model Kotlin nasceu deste documento.
**Substitui:** a introdução e as "Oportunidades" da versão anterior (recuperável via `git log`).

## Fingerprint do equipamento

| Campo | Valor |
|---|---|
| Vendor | TP-Link |
| Modelo (via `cloud_account?form=get_deviceInfo`) | **Archer C6** |
| Modelo (via `onemesh_network?form=mesh_topology`, campo `model`) | **Archer A6 v2** |
| Nome interno (`onemesh` `name`) | `ArcherA6v2` |
| Firmware (referência, não confirmado nesta sessão) | provável `1.1.10 Build 20230830 rel.69433(5553)` — o `Last-Modified` dos assets estáticos do próprio equipamento bate exatamente com essa data de build (`Wed, 30 Aug 2023`), mesma versão já catalogada no live-evidence do NetHAL |
| MAC LAN | formato `XX-XX-XX-XX-XX-XX` (endereço específico do equipamento do Luiz — omitido por prudência, ver seção Segurança) |

**Achado curioso:** o próprio firmware relata dois nomes de modelo comercial
diferentes dependendo do endpoint consultado (`Archer C6` via cloud account vs
`Archer A6 v2` via OneMesh). Isso é consistente com a nota do NetHAL de que
"A6"/"C6" compartilham a mesma família de hardware/protocolo (`tplink-stok-luci`)
— vale considerar os dois nomes como sinônimos de fingerprint para essa família.

Topologia confirmada: este roteador está **atrás da ONT Nokia** (WAN do roteador
recebe IP via DHCP da própria ONT — `wan_ipv4_gateway: 192.168.1.254`, que é
exatamente o IP LAN da ONT documentada no `NOKIA_GPON_FIELD_MAP.md`). Ou seja, há
**double NAT** na rede do Luiz: Internet → ONT (rotea `192.168.1.0/24`) → roteador
TP-Link em modo router (rotea `192.168.0.0/24`, a rede "principal" da casa).

---

## Segurança — achados críticos (fora do escopo de schema, mas relevantes)

1. **Credencial padrão ainda ativa.** O login com usuário `admin` / senha `admin`
   (o campo usuário nem aparece na UI: é fixo em `"admin"` no JS de login) **funcionou de primeira**.
   Isso é uma senha padrão de fábrica ainda ativa no roteador principal da rede
   do Luiz. Recomendação: trocar antes de qualquer outra coisa (tela **System
   Tools > Administration**, endpoint `admin/administration?form=account`).
2. **Script de debug remoto hardcoded no firmware.** Tanto a tela de login quanto
   o `index.html` pós-login carregam
   `http://192.168.1.101:1070/target/target-script-min.js#anonymous` — esse é o
   padrão de assinatura do **Weinre** (WEb INspector REmote, ferramenta de debug
   remoto de página web), apontando para um IP de rede interna de
   desenvolvimento da TP-Link (`192.168.1.101:1070`) que nunca foi removido do
   firmware de produção. Isso não afeta a rede do Luiz diretamente (o IP não é
   roteável a partir da LAN dele), mas é uma prática de build ruim do fabricante
   — não tentei me conectar a esse IP nem investiguei mais (fora de escopo/risco
   desnecessário).
3. **Senha Wi-Fi (PSK) trafega em texto plano** nas respostas JSON decriptadas
   dos endpoints `admin/status?form=all` e `admin/wireless?form=wireless_2g/5g`
   (campo `psk_key`). Esperado (a própria tela de configuração precisa mostrar a
   senha para o usuário editar), mas relevante registrar: qualquer parser futuro
   que reaproveitar esses payloads precisa tratar `psk_key` como segredo e nunca
   logar/persistir sem mascarar.

Nenhum desses campos sensíveis (senha Wi-Fi real, hash/criptograma de senha de
administração, MAC completo do equipamento, hostname do PC do Luiz) está
reproduzido com valor real neste documento — apenas nome do campo, tela e
formato. Valores de exemplo fictícios ou `[REDACTED]` substituem os reais.

---

## Esquema de autenticação (referência técnica)

Família `tplink-stok-luci` (já catalogada no NetHAL). Fluxo confirmado:

1. `GET /` redireciona para `/webpages/login.html`. Username é sempre `"admin"`
   fixo no JS (não há campo de usuário na UI).
2. `POST /cgi-bin/luci/;stok=/login?form=keys` body `operation=read` → resposta
   **em texto plano**: `{"success":true,"data":{"password":["<n1_hex>","<e1_hex>"], "mode":"router","username":""}}`.
   `n1` é módulo RSA de **1024 bits** (256 hex chars), `e1` é sempre `010001`
   (65537). Essa chave serve só para cifrar a senha de login.
3. `POST /cgi-bin/luci/;stok=/login?form=auth` body `operation=read` → resposta
   em texto plano: `{"success":true,"data":{"key":["<n2_hex>","<e2_hex>"],"seq":<int>}}`.
   `n2` é módulo RSA de **512 bits** (128 hex chars) — usado só para assinar
   (`sign`), nunca para cifrar payload direto.
4. Client gera AES-128 key + IV como **strings de 16 dígitos decimais ASCII**
   (não bytes aleatórios binários — literalmente `"5945270769887026"` etc.,
   usados como bytes UTF-8 diretos).
5. `h = MD5("admin" + senha)` hex lowercase.
6. Corpo do login (antes de cifrar): `operation=login&password=<RSA1024_PKCS1v1.5(senha)>`
   cifrado em AES-128-CBC/PKCS7 com a key/iv do passo 4 → base64 (`data`).
7. Assinatura (`sign`): string `k=<key>&i=<iv>&h=<hash>&s=<seq+len(data_base64)>`,
   fatiada em pedaços de **53 caracteres**, cada pedaço cifrado com RSA1024...
   não, com a chave RSA **512** bits do passo 3 (PKCS1v1.5 padding — 64 bytes de
   módulo menos 11 bytes de overhead = 53 bytes úteis por bloco), hex
   concatenado sem separador.
8. `POST /cgi-bin/luci/;stok=/login?form=login` body `sign=<hex>&data=<urlencoded base64>`.
9. Resposta **também vem envelopada em AES** (mesma key/iv da requisição):
   `{"data":"<base64>"}` → decripta com AES-CBC (mesma key/iv) →
   `{"success":true,"data":{"stok":"<token>"}}`. Cookie `sysauth=<token2>` também
   é setado via `Set-Cookie`.
10. Leituras autenticadas subsequentes: mesma AES key/iv da sessão (não
    renegocia), `sign` passa a ser só `h=<hash>&s=<seq+len(data)>` (sem `k=`/`i=`,
    já que a chave já foi estabelecida), body `operation=read` (ou outra
    operação), na URL `/cgi-bin/luci/;stok=<token>/admin/<categoria>?form=<subform>`,
    sempre com o cookie `sysauth`.
11. Resposta de leitura: `{"data":"<base64>"}` → decripta AES (mesma key/iv) →
    JSON `{"success":true,"data":{...}}`.

Toda a criptografia é a biblioteca clássica **jsbn** (BigInteger + RSAKey de Tom
Wu, código de 2005 embutido inline no `encrypt.js` do próprio roteador) com
padding PKCS#1 v1.5 padrão — por isso reproduzível 1:1 com `crypto.publicEncrypt`
do Node (via JWK montado a partir de `n`/`e` em hex) sem precisar reimplementar
BigInteger na mão.

---

## Menu: Status

Endpoint único `admin/status?form=all` retorna um payload grande e "achatado"
(sem sub-telas) com quase tudo: WAN (IPv4/IPv6), LAN, Wi-Fi 2.4G/5G + guest,
modem 3G/4G USB, storage USB, impressora, e lista de dispositivos cabeados.
Nenhum campo deste endpoint é usado hoje pelo SignallQ (não existe parser
TP-Link no app — o único uso de "TP-Link" hoje é como fabricante no lookup de
OUI em `core/network/.../topologia/oui/OuiCatalog.kt` (**correção 2026-07-23**:
substituiu o antigo `feature/devices/OuiDatabase.kt` citado originalmente aqui —
ver `docs/archive/2026-07-23_PLANO_UNIFICACAO_TOPOLOGIA_WIFI_2026-07-15.md`,
Fase 1), para identificar o *tipo* de dispositivo pelo prefixo do MAC, não
para ler dados do próprio roteador).

Campos principais (exemplos reais, exceto os marcados como segredo):

| Campo | Tipo | Exemplo real |
|---|---|---|
| `wan_ipv4_ipaddr` / `wan_ipv4_gateway` / `wan_ipv4_pridns` | string (IP) | `192.168.1.64` / `192.168.1.254` / `192.168.1.254` |
| `wan_ipv4_conntype` | enum | `dhcp` |
| `wan_macaddr` | string (MAC) | formato `XX-XX-XX-XX-XX-XX` |
| `lan_ipv4_ipaddr` / `lan_ipv4_netmask` | string | `192.168.0.1` / `255.255.255.0` |
| `lan_ipv4_dhcp_enable` | enum (`On`/`Off`) | `On` |
| `wireless_2g_ssid` / `wireless_5g_ssid` | string | ex. `MinhaRedeWifi_2G` / `MinhaRedeWifi_5G` (nome real do Luiz omitido) |
| `wireless_2g_current_channel` / `wireless_5g_current_channel` | int | `10` / `149` |
| `wireless_2g_channel` / `wireless_5g_channel` | enum (`auto` ou canal fixo) | `auto` |
| `wireless_2g_txpower` / `wireless_5g_txpower` | enum (`high`/`middle`/`low`) | `high` |
| `wireless_2g_encryption` / `_5g_encryption` | enum | `psk` |
| `wireless_2g_psk_key` / `wireless_5g_psk_key` | string | **segredo — senha Wi-Fi real, texto plano** |
| `wireless_2g_hwmode` / `_5g_hwmode` | enum (padrão 802.11) | `bgn` / `anac_5` |
| `guest_2g_ssid` / `guest_5g_ssid` / `guest_*_enable` | string/bool | rede convidado, desabilitada neste equipamento |
| `guest_isolate` | bool | isolamento de clientes da rede convidado |
| `modem_connstatus` / `modem_signal` / `modem_ipaddr` | int/string | modem 3G/4G USB — não há modem conectado (`0%`, `0.0.0.0`) |
| `storage_available` / `storage_capacity` / `printer_count` | int | USB storage/impressora — nenhum conectado |
| `access_devices_wired[]` | array `{wire_type, macaddr, ipaddr, hostname}` | 1 dispositivo cabeado no momento da captura |

---

## Menu: Network

### Internet (`admin/network?form=wan_ipv4_protos` para opções + endpoint de
config real não explorado nesta sessão — a leitura da config atual do WAN vem
pelo `status?form=all` acima)

Protocolos WAN IPv4 suportados pela UI: `Static IP`, `Dynamic IP` (DHCP),
`PPPoE`, `L2TP`, `PPTP` — este equipamento está em `dhcp` (recebendo IP da ONT).

### LAN IPv6 (`admin/network?form=wan_ipv6_protos`)

Protocolos WAN IPv6 suportados: `STATIC_IP`, `DYNAMIC_IP_v6` (DHCPv6),
`PPPoE`, `Tunnel 6to4`, `Pass-Through`. IPv6 está desabilitado
(`wan_ipv6_enable: off`).

### LAN / IPTV / DHCP Server / Dynamic DNS / Advanced Routing

Telas mapeadas na árvore de menu (`admin/network?form=routes_static`,
`admin/network?form=routes_system`, `admin/iptv?form=setting`,
`admin/ddns?form=provider/tplink/noip/dyndns`), mas **não lidas em profundidade
nesta sessão** (menor prioridade para diagnóstico — IPTV e rotas estáticas não
configuradas neste equipamento). DDNS confirmado como `provider: tp-link`
(sem conta vinculada).

### Working Mode (`admin/system?form=change_ip`)

Alterna entre modo Roteador / Access Point / Repetidor Wi-Fi / Extensor. Não
lido em profundidade — o equipamento está em modo Roteador (padrão, dado que
faz NAT e serve DHCP para `192.168.0.0/24`).

---

## Menu: Wireless

### Wireless Settings (`admin/wireless?form=wireless_2g` / `wireless_5g`)

Superset do que aparece em Status, com campos de configuração adicionais:

| Campo | Tipo | Usado hoje? |
|---|---|---|
| `wpa_version` / `wpa_cipher` / `psk_version` / `psk_cipher` | enum | Não |
| `htmode` (largura de canal, `auto`/`20MHz`/`40MHz`) | enum | Não |
| `wps_state` (`configured`/`unconfigured`) | enum | Não |
| `hidden` (SSID oculto) | bool | Não |
| `wds_status` (WDS bridge) | enum | Não |
| `port` (porta RADIUS, `1812` default) | int | Não |
| campos WEP legado (`wep_key1-4`, `wep_type1-4`, `wep_format1-4`, `wep_mode`, `wep_select`) | string/enum | Não (WEP não usado, campos vazios) |

### System Parameters do rádio (`admin/wireless?form=syspara_2g` / `syspara_5g`)

Parâmetros avançados de RF: `frag` (fragmentation threshold, 2346),
`rts` (RTS threshold, 2346), `beacon_int` (100ms), `dtim_period` (1),
`shortgi` (short guard interval, on), `wmm` (QoS 802.11e, on),
`wpa_group_rekey` (0 = desabilitado), `isolate` (client isolation).
**Dado novo** — nenhum usado hoje; útil para diagnóstico avançado de
performance Wi-Fi (fragmentation/RTS baixos demais geram overhead
desnecessário, guard interval curto demais pode gerar erro em ambientes
ruidosos).

### WPS / TxBF & MU-MIMO / Wireless Schedule / Statistics / OneMesh

- **WPS** (`admin/wireless?form=wps_connect` / `wps_pin`): não lido (ação de
  pareamento, não leitura de status).
- **TxBF & MU-MIMO**: campo `mumimo` já capturado via `wireless_5g` (`off`).
  `txbf_enable` idem (`off`).
- **Wireless Statistics** (`admin/wireless?form=statistics`): retornou
  `{"success":true,"data":{}}` — **vazio no momento da captura** (não há
  cliente Wi-Fi ativo gerando estatísticas de sinal/taxa nesse instante, ou o
  endpoint exige parâmetro adicional para popular por cliente). Não
  investigado a fundo — potencial dado valioso (RSSI/taxa por cliente Wi-Fi
  associado) se o formato de resposta populado for descoberto depois.
- **OneMesh** (`admin/onemesh_network?form=mesh_topology`): retorna topologia
  completa mesh (roteador principal + nós satélite, se houver). Campos:
  `mesh_nclient_list[]` (`mac`, `hostname`, `ip`, `wire_type`, `guest`,
  `access_time` epoch), `mesh_sclient_list` (nós satélite mesh — vazio,
  este roteador não tem extensores OneMesh pareados), `model`/`name`/`mac`
  do próprio roteador. **Dado novo, complementar** ao scanner de dispositivos
  do SignallQ (`ScannerDispositivosAndroid`) — de novo, MAC+hostname
  reportado pelo próprio roteador tende a ser mais estável que descoberta
  ativa via ARP/SSDP do celular.

---

## Menu: Guest Network

`admin/wireless?form=guest` retornou `{"isolate":"off","access":"off"}` —
rede convidado desabilitada neste equipamento (`guest_access: off` também
confirmado via Status). Configuração completa (SSID/senha/agendamento de
convidado) fica em `guest_2g`/`guest_5g` dentro do payload de Status, já
documentado acima.

## Menu: USB Settings

Sub-telas (3G/4G modem, Disk Settings, Folder Sharing, Print Server, Offline
Download) mapeadas na árvore de menu, **não lidas nesta sessão** — nenhum
dispositivo USB conectado no momento da captura (confirmado via
`storage_available: 0`, `printer_count: 0`, `modem_available: 0` no Status).
Baixo valor para diagnóstico de conectividade.

## Menu: Parental Control

`admin/smart_network?form=patrol_*` (9 sub-endpoints: lista de apps filtrados,
dispositivos monitorados, filtro, limites de tempo, insights/histórico,
bloqueio por dono, lista de donos, bloqueio de site por dono). Não lido em
profundidade — fora do escopo de diagnóstico de conectividade.

## Menu: QoS

`admin/smart_network?form=qos` (config geral) e `form=device_priority`
(priorização por dispositivo). Não lido em profundidade nesta sessão.

## Menu: Security

| Endpoint | Campo | Usado hoje? |
|---|---|---|
| `admin/security_settings?form=enable` | `enable` (firewall SPI) | Não — `off` neste equipamento |
| `admin/security_settings?form=dos_setting` | thresholds de proteção DoS: `icmp_low/middle/high`, `syn_low/middle/high`, `udp_low/middle/high` | Não — **dado novo**, indica limiares de rate-limit configurados para proteção contra flood ICMP/SYN/UDP |
| `admin/security_settings?form=list` | regras de firewall customizadas | **Não lido** — endpoint retornou `"no such callback"` com payload simples `operation=read` (provavelmente exige parâmetro de paginação não descoberto) |
| `admin/access_control?form=mode` | `access_mode` (`black`/`white` list) | Não — modo `black` (lista negra) configurado, lista vazia |
| `admin/access_control?form=black_list` / `white_list` / `black_devices` / `white_devices` | listas de dispositivos bloqueados/permitidos | **Não lido** — mesmo erro `"no such callback"` |
| `admin/imb?form=arp_list` / `bind_list` | tabela ARP viva / bindings IP-MAC estáticos | **Não lido** — mesmo erro; complementar ao `access_devices_wired` do Status |

## Menu: NAT Forwarding

| Endpoint | Campo | Usado hoje? |
|---|---|---|
| `admin/nat?form=setting` | `enable` (NAT geral), `boost_enable`/`boost_support` (NAT boost / hardware offload), `switch_support` | Não |
| `admin/nat?form=alg` | ALGs habilitados: `h323`, `ipsec`, `pptp`, `sip`, `l2tp`, `tftp`, `rtsp`, `ftp` (todos `on`) | Não |
| `admin/nat?form=vs` (Virtual Servers / port forwarding) | regras de encaminhamento de porta | Não lido em profundidade |
| `admin/nat?form=pt` (Port Triggering) | regras de gatilho de porta | Não lido em profundidade |
| `admin/nat?form=dmz` | host DMZ | Não lido em profundidade |
| `admin/upnp?form=service` | serviços UPnP ativos | Vazio (`{}`) neste equipamento |

## Menu: IPv6

`admin/network?form=wan_ipv6_protos` já documentado acima (lista de protocolos
suportados). Config atual de IPv6 vem do Status (`wan_ipv6_*`, todos
desabilitados/vazios neste equipamento).

## Menu: Smart Life Assistant (Alexa / IFTTT)

Não lido — fora de escopo, feature de integração com assistentes de voz, não
tem relação com diagnóstico de conectividade.

## Menu: System Tools

| Endpoint | Campo | Usado hoje? |
|---|---|---|
| `admin/time?form=settings` | `date`, `time`, `timezone`, `ntp_svr1`/`ntp_svr2`, `type` (`auto`/manual), `ntp_success` (bool) | Não |
| `admin/reboot?form=set` | ação (não é leitura) | — |
| `admin/ledpm?form=setting` (LED control) | agendamento de LED | Não lido em profundidade |
| **`admin/diag?form=diag`** | ferramenta de diagnóstico **nativa do roteador** (ping/traceroute rodando a partir do próprio equipamento): `type` (0=ping), `ipaddr` (alvo), `count` (default 4), `pktsize` (default 64), `timeout` (800ms), `ttl` (20), `result` (texto do último teste) | **Não — dado novo**, mesmo racional já documentado para a ONT Nokia: permite isolar se o problema é entre roteador↔Internet vs celular↔roteador |
| `admin/firmware?form=upgrade` / `config` / `config_multipart` | upgrade de firmware / backup-restore de config | Fora de escopo (ação destrutiva/config, não diagnóstico) |
| `admin/administration?form=account` | troca de senha do admin (campos `new_acc`, `new_pwd`, `old_acc`, `old_pwd` — todos recebem o RSA1024 pubkey pré-carregado para cifrar o valor digitado, não a senha em si) | Não |
| `admin/syslog?form=log` / `filter` / `mail` / `types` | visualizador de log do sistema | **Não lido** — `form=log` retornou `"no such callback"` com payload simples (provavelmente precisa parâmetro de filtro/paginação) |
| `admin/network?form=port_speed_supported` | velocidades de porta suportadas: `auto`, `1000F`, `100F`, `100H`, `10F`, `10H` | Não |

## Menu: VPN / VPN Client

`admin/openvpn`, `admin/pptp_vpn`, `admin/vpn_connections`, `vpn_client` — não
lidos, fora de escopo de diagnóstico de conectividade doméstica básica.

---

## Oportunidades

Nenhum destes campos é usado hoje (não há parser TP-Link). Em ordem de valor para diagnóstico de rede doméstica:

1. **`admin/diag?form=diag`** (diagnóstico nativo): ping/traceroute a partir do roteador isola problema local (Wi-Fi do celular) de downstream (roteador→ONT→internet).
2. **`admin/onemesh_network?form=mesh_topology`**: dispositivos conectados com hostname do próprio roteador (mais estável que o scanner ativo) e detecção automática de extensores OneMesh.
3. **Canal e potência reais por rádio** (`wireless_2g_current_channel`, `wireless_5g_current_channel`, `txpower`): direto do AP, sem depender da leitura do rádio do celular.
4. **Proteção DoS** (`security_settings?form=dos_setting`): rate limit de ICMP/SYN/UDP pode explicar picos de perda em testes agressivos (speedtest multi-thread).
5. **Parâmetros avançados de RF** (`syspara_2g`/`syspara_5g`: fragmentação, RTS, guard interval, WMM): causa raiz de degradação sob interferência.
6. **Double NAT**: o `wan_ipv4_gateway` do roteador coincidir com o IP LAN de uma ONT já indica a condição, se os dois dispositivos puderem ser correlacionados.

**Baixo valor** (configuração ou fora de conectividade): Parental Control, QoS, Guest Network, USB, Smart Life Assistant, VPN/VPN Client, LED, Firmware Upgrade, Backup/Restore, IPTV, Advanced Routing, Dynamic DNS.

**Endpoints que retornaram `"no such callback"`** com o payload simples `operation=read` (provavelmente exigem parâmetros de paginação/filtro não descobertos; não insisti para não arriscar o equipamento): tabela ARP viva, bindings IP-MAC, firewall customizado, Access Control, log do sistema, tráfego por dispositivo. Se algum for priorizado, inspecione o JS de cada tela (`pages/userrpm/*.html`) para o shape exato dos parâmetros.
