---
title: "Auditoria de segurança do AndroidManifest"
description: "Inventário de permissões, componentes exportados, backup e network security config do app Android."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
---

# Auditoria de Seguranca — AndroidManifest.xml

- **Fonte de verdade:** `AndroidManifest.xml` de `:app` e dos módulos em `android/` (`settings.gradle.kts` lista os módulos)
- **Escopo:** manifesto principal + manifestos dos módulos de biblioteca; achados originais de 2026-06-28, revalidados em 2026-10-04 contra permissões, `allowBackup`, `exported` e `network_security_config.xml`

## Resumo

Auditoria do manifesto principal e dos manifestos dos módulos de biblioteca (10 feature, `core/*` com manifesto: database, datastore, network, permissions, probejogo, recommendation, telephony). Os módulos `core:relatorio`, `core:diagnostico`, `core:featureflags` e `core:nds` não têm manifesto próprio. Nenhuma vulnerabilidade encontrada. O manifesto segue as boas práticas de segurança Android.

---

## 1. Inventario de Permissoes

Todas declaradas em `android/app/src/main/AndroidManifest.xml`, exceto `READ_PHONE_STATE` que tambem aparece em `core/telephony`.

| Permissao | Justificativa (Data Safety) | Risco |
|---|---|---|
| `POST_NOTIFICATIONS` | Notificacoes de progresso de diagnostico e monitoramento em background (WorkManager) | Baixo — runtime desde API 33 |
| `INTERNET` | Comunicacao com Worker Cloudflare (IA), speedtest, requisicoes de rede | Essencial |
| `ACCESS_NETWORK_STATE` | Detectar tipo de conexao (Wi-Fi/movel/sem rede) para diagnostico | Baixo — sem dados pessoais |
| `ACCESS_WIFI_STATE` | Coletar SSID, BSSID, canal, frequencia, RSSI para analise Wi-Fi | Medio — dados de rede local |
| `ACCESS_FINE_LOCATION` | Necessaria para SSID/BSSID em Android 8+ (requisito do sistema) | Alto — solicitada em runtime |
| `ACCESS_COARSE_LOCATION` | Fallback de localizacao para contexto de rede | Medio — solicitada em runtime |
| `NEARBY_WIFI_DEVICES` | Scan de dispositivos na rede local (Android 13+). Flag `neverForLocation` corretamente aplicada | Baixo |
| `READ_PHONE_STATE` | RSRP, SINR, banda, cellId para diagnostico de rede movel. Solicitada lazy (so na primeira vez em rede movel). Sem IMEI/IMSI | Alto — runtime, justificativa documentada no manifest |
| `CHANGE_WIFI_MULTICAST_STATE` | Descoberta de dispositivos via mDNS/multicast na rede local | Baixo |

**Nenhuma permissao desnecessaria identificada.** Todas sao justificadas pelo escopo do app (diagnostico de conectividade).

---

## 2. Componentes — Status de Exportacao

### Manifesto Principal (`app`)

| Componente | Tipo | exported | Justificativa |
|---|---|---|---|
| `.MainActivity` | Activity | `true` | Launcher activity — obrigatorio para `MAIN/LAUNCHER` intent-filter |
| `androidx.core.content.FileProvider` | Provider | `false` | Compartilhamento de laudos via URI. Authorities: `${applicationId}.fileprovider` |
| `androidx.startup.InitializationProvider` | Provider | `false` | Desabilita auto-init do WorkManager. Authorities: `${applicationId}.androidx-startup` |

### Modulos Feature (10)

Todos os modulos feature (`devices`, `diagnostico`, `dns`, `fibra`, `history`, `home`, `router`, `settings`, `speedtest`, `wifi`) possuem manifestos vazios (`<manifest />`). Nenhum componente exportado, nenhuma permissao adicional.

### Modulos Core com manifesto (7)

| Modulo | Conteudo |
|---|---|
| `core/database` | Manifesto vazio |
| `core/datastore` | Manifesto vazio |
| `core/network` | Manifesto vazio |
| `core/permissions` | Manifesto vazio |
| `core/probejogo` | Manifesto vazio |
| `core/recommendation` | Manifesto vazio |
| `core/telephony` | Declara `READ_PHONE_STATE` com documentacao detalhada de privacidade |

---

## 3. Seguranca de Providers

| Provider | exported | Authorities | Avaliacao |
|---|---|---|---|
| FileProvider | `false` | `${applicationId}.fileprovider` | OK — usa placeholder, nao hardcoded |
| InitializationProvider | `false` | `${applicationId}.androidx-startup` | OK — usa placeholder |

Nenhum authority hardcoded encontrado. Ambos usam `${applicationId}` corretamente.

---

## 4. Backup

**`android:allowBackup="false"`** — correto para app de diagnostico que nao persiste dados sensiveis entre dispositivos.

### backup_rules.xml (Android < 12)

Exclui corretamente:
- `datastore/` (preferencias e dados de sessao)
- `linkaKotlin.db` + WAL/SHM (banco Room local)

### data_extraction_rules.xml (Android 12+)

- `disableIfNoEncryptionCapabilities="true"` — correto, impede backup em dispositivos sem criptografia
- Cloud backup: exclui datastore + banco completo
- Device transfer: exclui datastore + banco (sem WAL/SHM, aceitavel pois transferencia e atomica)

---

## 5. Network Security Config

Arquivo: `android/app/src/main/res/xml/network_security_config.xml`

**`<base-config cleartextTrafficPermitted="false" />`** — HTTPS obrigatorio por padrao.

Cleartext permitido apenas para IPs de gateway LAN (acesso HTTP ao modem):

| IP | Uso tipico |
|---|---|
| 192.168.0.1, 192.168.0.254 | Gateways padrao (Vivo, Claro, NET) |
| 192.168.1.1, 192.168.1.254 | Gateways padrao (TP-Link, D-Link) |
| 192.168.2.1 | Gateway alternativo |
| 10.0.0.1, 10.0.0.138 | Gateways de operadora (NET/Claro) |
| 172.16.0.1 | Gateway de rede corporativa |

**Avaliacao:** correto. Somente IPs privados RFC 1918 de gateway. Nenhum dominio publico ou IP externo. `includeSubdomains="false"` em todos.

---

## 6. FileProvider Paths

Arquivo: `android/app/src/main/res/xml/file_paths.xml`

| Path | Tipo | Uso |
|---|---|---|
| `external-files-path: Documents/` | Laudos salvos | Compartilhamento de relatorio PDF |
| `cache-path: laudos/` | Cache de laudos | Temporarios |
| `cache-path: .` | Export historico | Exportacao de dados |
| `cache-path: share/` | Imagens de compartilhamento | Screenshots de resultado |

**Avaliacao:** aceitavel. `cache-path: .` e amplo mas refere-se ao cache do app (nao expoe arquivos do sistema). Nenhum `root-path` ou `external-path` usado.

---

## 7. Issues Encontradas

**Nenhuma vulnerabilidade de seguranca identificada.**

Pontos positivos:
- Unico componente `exported=true` e a MainActivity (obrigatorio)
- Todos os providers com `exported="false"` e authorities dinamicos
- Backup desabilitado com regras de exclusao adequadas
- Network security config restritivo (HTTPS padrao, cleartext so para LAN)
- Permissoes justificadas e documentadas no proprio manifest
- Modulos feature/core nao adicionam componentes ou permissoes extras (exceto telephony com READ_PHONE_STATE documentado)

---

## 8. Recomendacoes (nao bloqueantes)

1. **cache-path generico**: `cache-path name="export_historico" path="."` expoe todo o diretorio de cache ao FileProvider. Considerar restringir a um subdiretorio dedicado (`cache-path path="export/"`). Risco baixo pois FileProvider so concede acesso via URI explicita.

2. **Gateway dinamico**: o NSC lista IPs fixos de gateway. Gateways fora da lista (ex: 192.168.15.1, comum em roteadores brasileiros) falharao. Issue ja mencionada no comentario do XML — detectar gateway em runtime e nao depender de lista fixa resolveriam.
