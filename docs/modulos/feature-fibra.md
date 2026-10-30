---
title: "Módulo :featureFibra"
description: "Driver de leitura autenticada da ONT Nokia GPON G-1425G-B e normalização do snapshot para o contrato de dispositivo local."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# `:featureFibra`

- **Caminho físico:** `android/feature/fibra/` (alias flat legado, remapeado por `projectDir` em `android/settings.gradle.kts`)
- **Namespace:** `io.signallq.app.feature.fibra`

## Responsabilidade

Driver de equipamento local: autentica na interface web da ONT GPON do usuário, faz scraping das páginas `.cgi`, extrai o estado óptico/WAN/PPP/Wi-Fi/LAN e a lista de clientes conectados, e normaliza tudo para o contrato compartilhado `LocalNetworkDeviceSnapshot` de `:coreNetwork`. Também classifica o nível de sinal óptico RX/TX contra o perfil normativo da classe GPON B+.

Não é dele: a UI de "Equipamento de Internet" (vive em `:app` — o módulo não tem nenhum `@Composable`), a decisão de qual host consultar (`MainViewModel` resolve o gateway por scan de rede), o armazenamento de credenciais, o motor genérico de saúde GPON (`ClassificadorSaudeGpon`, em `:coreNetwork`, ainda não wireado a este perfil) e qualquer diagnóstico de causa raiz.

## Dependências

Extraídas de `android/feature/fibra/build.gradle.kts`.

| Dependência | Configuração | Observação |
|---|---|---|
| `:coreNetwork` | `implementation` | Contratos `localdevice`: `LocalNetworkDeviceSnapshot`, `DeviceType`, `SupportLevel`, `TipoConexaoFisica` etc. |
| `libs.androidx.core.ktx` | `implementation` | |
| `libs.kotlinx.coroutines.android` | `implementation` | |
| `libs.timber` | `implementation` | |
| `libs.junit` | `testImplementation` | |
| `libs.androidx.junit`, `libs.androidx.espresso.core` | `androidTestImplementation` | |

Sem Hilt, sem OkHttp, sem Room. O HTTP é feito com `java.net.HttpURLConnection` puro e a criptografia com `javax.crypto`/`java.security` do JDK.

## Consumidores

`grep -rn 'project(":featureFibra")' --include=*.kts .`

| Consumidor | Local |
|---|---|
| `:app` | `android/app/build.gradle.kts` |

Nenhum outro módulo depende deste. Em `:app`, o wiring acontece em `di/AppModule.kt` (`FeatureFibraModulo.criarExecutor()`), `MainViewModel.kt`, `ui/screen/EquipamentoInternetScreen.kt`, `EquipamentoPanelMapper.kt` e `FibraModemUiState.kt`.

## Equipamentos suportados

**Único equipamento com driver de produção: Nokia G-1425G-B** (ONT GPON classe B+, série ALCL / Alcatel-Lucent, OUI `F82229`, chipset MediaTek MTK7528H). Todo o código do módulo (`NokiaModemClient`, `NokiaModemCrypto`, `NokiaModemParser`, `NokiaG1425GBProfile`, `NokiaLocalDeviceMapper`) é específico deste modelo. `NokiaLocalDeviceMapper` publica o snapshot com `supportLevel = SupportLevel.LAB_VALIDATED`.

Os mapas de campo vivem em `docs/technical/` e são documentos de **reconhecimento**, não de produto:

| Documento | Equipamento | Método de levantamento | Existe driver? |
|---|---|---|---|
| `docs/technical/NOKIA_GPON_FIELD_MAP.md` | Nokia G-1425G-B (ONT GPON) | Acesso HTTP ao vivo ao equipamento real (2026-07-08), login RSA+AES replicando o `crypto_page.js` da própria ONT | **Sim** — este módulo |
| `docs/technical/TPLINK_ARCHER_ROUTER_FIELD_MAP.md` | TP-Link Archer C6/A6v2 (família `tplink-stok-luci`) | Acesso HTTP ao vivo (`192.168.0.1`, 2026-07-08/09), handshake `form=keys` → `form=auth` → `form=login` replicado em Node.js | Sim, em outro módulo — `:featureRouter` (`TpLinkArcherC6Driver`, login `stok/luci`) |
| `docs/technical/INTELBRAS_RX1500_FIELD_MAP.md` | Intelbras RX1500/RAX1500 | Análise **estática/offline** do arquivo de firmware `.aes` (2026-07-09), sem acesso ao equipamento; extração do `rootfs` bloqueada | Não — "Intelbras" só aparece no código como fabricante no catálogo OUI |

## Componentes principais

Componentes, protocolo de login, parser e perfil óptico estão em [`features/equipamento-internet.md`](../features/equipamento-internet.md) (§7 Mapa de código). Aqui ficam só o que é específico do módulo: dependências, consumidores, catálogo de equipamentos e páginas lidas.

### Páginas consumidas do equipamento

Lidas por `ExecutorFibra.buscarSnapshot` a cada leitura:

| Path | Conteúdo | Criticidade |
|---|---|---|
| `/login.cgi` | Autenticação (POST) | Crítico |
| `/wan_status.cgi?gpon` | Estado GPON, RX/TX, temperatura, serial, tensão, corrente do laser | Crítico |
| `/show_wan_status.cgi?ipv4` | Conexões WAN (`wan_conns`) | Crítico |
| `/index.cgi?getppp` | Estado PPPoE (JSON) | Crítico |
| `/device_status.cgi` | Modelo, fabricante, firmware, hardware, uptime | Crítico |
| `/lan_status.cgi?lan` | LAN + objeto `wlan_status` (rádios Wi-Fi, canal, segurança, potência) | Best-effort |
| `/lan_ipv4.cgi` | Configuração IPv4 da LAN | Best-effort |
| `/lan_status.cgi?wlan` | `device_cfg` + `alias_cfg` — lista de clientes conectados | Best-effort |
| `/reboot.cgi` | Reinício do equipamento | Desligado por `RebootLabFlags` |

O comentário no código registra a correção de 2026-07-10: `wlan_status` vive em `lan_status.cgi?lan`, não em `?wlan` — revalidado contra equipamento real.

## Riscos e dívidas

Riscos do driver (acoplamento a um equipamento, scraping frágil, falta de teste de `ExecutorFibra`/`NokiaModemCrypto`, `reboot()` não validado, duas noções de saúde óptica) estão em [`features/equipamento-internet.md`](../features/equipamento-internet.md) (§12).
