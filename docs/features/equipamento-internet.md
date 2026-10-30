---
title: "Feature — Seu equipamento (modem/ONT/roteador)"
description: "Leitura do equipamento de internet do usuário (ONT GPON, roteador TP-Link): conexão, painel técnico, ações com confirmação, drivers, flags e testes."
type: "feature"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.2.0"
feature: "equipamento-internet"
tipo: "jornada"
modulos:
  - "android/feature/fibra"
  - "android/feature/router"
  - "android/core/network"
  - "android/core/datastore"
  - "android/core/featureflags"
  - "android/app"
arquivos:
  - "android/feature/fibra/src/main/kotlin/io/signallq/app/feature/fibra/"
  - "android/feature/router/src/main/kotlin/io/signallq/app/feature/router/"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/contracts/gateway/"
  - "android/core/datastore/src/main/kotlin/io/signallq/app/core/datastore/CredenciaisModemStore.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/EquipamentoInternetScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/EquipamentoConectarScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/EquipamentoPanelMapper.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/EquipamentoAcoesCard.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/GatewayConnectionSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShell.kt"
contratos: []
eventos:
  - "feature_used (feature_id=fibra)"
  - "screen_view (screen_name=equipamento_internet)"
  - "feature_blocked_remote (feature_id=fibra)"
flags:
  - "consumer_fibra_enabled"
  - "feature_fibra"
testes:
  - "android/feature/fibra/src/test/kotlin/io/signallq/app/feature/fibra/NokiaModemParserTest.kt"
  - "android/feature/fibra/src/test/kotlin/io/signallq/app/feature/fibra/ClassificadorOpticoNokiaG1425GBTest.kt"
  - "android/feature/fibra/src/test/kotlin/io/signallq/app/feature/fibra/NokiaLocalDeviceMapperTest.kt"
  - "android/feature/fibra/src/test/kotlin/io/signallq/app/feature/fibra/ValidadorHostEquipamentoTest.kt"
  - "android/feature/fibra/src/test/kotlin/io/signallq/app/feature/fibra/NokiaModemClientTest.kt"
  - "android/feature/router/src/test/kotlin/io/signallq/app/feature/router/TpLinkArcherMapperTest.kt"
  - "android/feature/router/src/test/kotlin/io/signallq/app/feature/router/TpLinkStokLuciClientTest.kt"
  - "android/feature/router/src/test/kotlin/io/signallq/app/feature/router/TpLinkStokLuciCryptoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/EquipamentoInternetScreenEstadosTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/EquipamentoConectarScreenTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/GatewayConnectionSheetTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/GatewayAutoconexaoTest.kt"
adrs: []
thresholds_em: "android/feature/fibra/src/main/kotlin/io/signallq/app/feature/fibra/NokiaG1425GBProfile.kt"
---

# Feature — Seu equipamento (modem/ONT/roteador)

Migrado de `FUNCIONAL.md` (§5.6), `ARQUITETURA/MODULOS/feature-fibra.md` e `feature-router.md`. Fatos conferidos no código em 2026-10-04. Unifica três nomes técnicos de um só domínio: **Fibra** (driver ONT Nokia, `:featureFibra`), **EquipamentoInternet** (tela de leitura) e **EquipamentoConectar** (etapa de conexão), mais o driver de roteador TP-Link (`:featureRouter`).

## Negócio

### 1. Problema e promessa

O usuário não sabe o que o equipamento da operadora está dizendo: sinal óptico, WAN, Double NAT, quem está conectado. A feature lê o equipamento (com a senha dele) e mostra o estado por capacidade, em cards separados, com significado e próximo passo. Só leitura, exceto a ação "Reiniciar equipamento", que é confirmada.

### 2. Quando aparece e para quem

Tela cheia aberta pelo card do hub **Ferramentas** ou pelo nó do gateway na Início. Sem endereço de equipamento salvo, o destino é primeiro `EquipamentoConectar` (formulário de conexão); com endereço salvo, vai direto a `EquipamentoInternet` (GH#1806, `AppShell.kt`). Para quem tem a credencial administrativa do modem/roteador na rede local.

### 3. Regras de decisão

- **Destinos (overlays):** `Overlay.EquipamentoConectar` → persiste credencial, reconecta e troca o overlay por `Overlay.EquipamentoInternet`. "Pular por enquanto" também segue para a leitura técnica (`AppShell.kt`).
- **Cards por capacidade** (`EquipamentoInternetScreen.kt` é só chrome e ordem): status/disponibilidade, módulos técnicos (fibra/WAN/LAN/Wi-Fi/dispositivos), topologia, seletor de dispositivo, informação técnica, ações. Cada capacidade nova ganha seu próprio arquivo `Equipamento*Card.kt`.
- **Com acesso:** estado GPON com potência óptica Rx/Tx classificada por perfil versionado (faixas em `thresholds_em`, não copiadas), WAN/PPP e alerta de Double NAT quando a topologia detecta CGNAT.
- **Ações dependem do driver:** "Reiniciar equipamento" só aparece com `AcessoEquipamento.GERENCIAMENTO_DISPONIVEL`.
- **Três CTAs de navegação:** "Ver dispositivos", "Executar diagnóstico" (Laudo) e "Ver detalhes do Wi-Fi" (fecha o overlay e leva à aba Sinal em vez de empilhar tela).
- **Credenciais:** `GatewayConnectionSheet` (a mesma do nó do gateway na Início), com "lembrar senha" e "manter conectado" (vincula a sessão ao BSSID atual).
- **Roteador TP-Link:** só senha (`GatewayCredentialRequirement.PASSWORD_ONLY` quando `TpLinkArcherC6Driver.probe` acusa `STOK_LUCI_PASSWORD_ONLY`); caso contrário usuário e senha.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| `AcessoEquipamento.SOMENTE_IDENTIFICACAO` (modelo identificado, não lido) | Dois próximos passos: "Abrir configurações no navegador" (só com host; `http://<host>` via `ExternalActionLauncher`) e "Falar com o suporte" (`abrirEmailSuporte`, `suporte@signallq.com`) — decisão de produto, Luiz, 2026-08-19, #1664 |
| `AcessoEquipamento.CREDENCIAIS_NECESSARIAS` | CTA "Configure o acesso ao equipamento" abre `GatewayConnectionSheet` |
| Carregando / indisponível | Estados próprios na tela (`EquipamentoInternetScreenEstadosTest`) |
| Reiniciar | Diálogo de confirmação antes de agir ("fica sem internet por 1-3 minutos", `ReiniciarEquipamentoDialog`). As demais ações são só navegação e não pedem confirmação (#1664) |
| Roteador TP-Link com falha | Mensagem traduzida uma vez a partir de `TpLinkFailure` (senha não confere, modelo não compatível, sessão expirada…); nunca expõe detalhe HTTP/cripto |
| Host fora de rede local | Recusado antes de qualquer credencial sair do aparelho (`ValidadorHostEquipamento`) |
| Dado ausente do equipamento | Não vira zero nem "ok": `NokiaLocalDeviceMapper` devolve `null` sem leitura `concluido` com dado óptico |

### 5. Próximo passo e confirmação

Os CTAs levam a dispositivos, ao Laudo ou ao Wi-Fi. Não há confirmação automática de efeito: depois de reiniciar, o usuário reabre a leitura. Reinício nunca ocorre sem o diálogo.

### 6. Fora de escopo, status e flag

Fora: alterar configuração do equipamento além do reinício, ler equipamento sem credencial, Intelbras RX1500 (só mapa estático em `technical/`, sem driver). Status: entregue, driver de produção só para **Nokia G-1425G-B** (ONT) e **TP-Link Archer C6/A6** (roteador, `LAB_VALIDATED`). Flag: `consumer_fibra_enabled` bloqueia a rota (`bloquearRota(featureFlags.fibraEnabled, ConsumerFeatureModuleIds.FIBRA)` em `AppShell.kt`); `feature_fibra` é legado (default `true` em `FeatureFlagRepository.kt`).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `feature/fibra/.../ExecutorFibra.kt` | Entrada do driver ONT: `snapshotFlow: StateFlow<SnapshotFibra>`; `executar(host, username, password)` reaproveita a sessão HTTP em cache antes de login novo (#894: o firmware rejeita login concorrente com `err_t=0`), até 3 tentativas com backoff de 1 s × tentativa; erro permanente (host inválido, CSRF) interrompe o retry; `reiniciar()` e `marcarSemRede()` completam a API |
| `feature/fibra/.../NokiaModemClient.kt` | Sessão HTTP (`HttpURLConnection`, `internal`, valida o host no `init`): GET da página de login → material cripto → `POST /login.cgi` → `sid`/`X-SID` nas demais páginas; `fetchPage(path)`, `reboot()` (`/reboot.cgi`) |
| `feature/fibra/.../NokiaModemCrypto.kt` | Replica o `crypto_page.js` do equipamento: AES-CBC com padding ISO/IEC 7816-4 + RSA PKCS#1 v1.5; extrai `pubkey`, `nonce` e `csrf_token` do HTML por regex. Qualquer alteração pode quebrar o login em silêncio (`err_t=[0]`) |
| `feature/fibra/.../NokiaModemParser.kt` | `parseGpon`, `parseWan`, `parsePpp`, `parseDeviceInfo`, `parseWifi`, `parseLan`, `parseClientes`, `normalizarTipoConexaoFisica`. Particularidades do firmware tratadas: typo `SupplyVottage`, `TransceiverTemperature` em Q8.8 (`raw/256`), `LaserCurrent` em 0,5 µA (`raw/500`), tensão em `raw/10000` |
| `feature/fibra/.../ValidadorHostEquipamento.kt` | Só IP literal privado/local; nunca resolve hostname (evita DNS rebinding) |
| `feature/fibra/.../NokiaG1425GBProfile.kt` | Perfil óptico versionado (ITU-T G.984.2 Amd.1 classe B+), `NivelSinalOpticoRx` (4 estados), `NivelSinalOpticoTx` (3 estados) e `object ClassificadorOpticoNokiaG1425GB` (funções puras) — **fonte dos limiares**. As margens de atenção são heurística de produto, **não** normativa |
| `feature/fibra/.../NokiaLocalDeviceMapper.kt` | `SnapshotFibra` → `LocalNetworkDeviceSnapshot` (`DeviceType.ONT_GPON`, `SupportLevel.LAB_VALIDATED`); só mapeia leituras `EstadoFibra.concluido` com dado óptico, senão `null` |
| `feature/fibra/.../SnapshotFibra.kt`, `GponStatus.kt`, `WanStatus.kt`, `WifiStatus.kt`, `LanStatus.kt`, `PppStatus.kt`, `DeviceInfoFibra.kt`, `ClienteFibra.kt`, `EstadoFibra.kt`, `GponSaudeStatus.kt`, `FeatureFibraModulo.kt` | Data classes do snapshot bruto; `FeatureFibraModulo.criarExecutor()` é a fachada |
| `feature/fibra/.../RebootLabFlags.kt` | `HABILITADO_SEM_VALIDACAO_HARDWARE = false`: reinício sem validação em hardware real fica desligado |
| `feature/router/.../TpLinkArcherC6Driver.kt` | Fachada. `probe(host)` sonda sem credencial os dois endpoints de bootstrap (`TpLinkProbeResult.STOK_LUCI_PASSWORD_ONLY`/`NOT_SUPPORTED`); `loginAndRead(password)` → `TpLinkReadResult.Success`/`Failure(TpLinkFailure)`; o modelo C6/A6 só é confirmado após o login. Lê `status/all`, `cloud_account/get_deviceInfo` e topologia OneMesh quando existir. Contém `TpLinkStokLuciClient` e o transporte injetável `TpLinkHttpTransport` (`UrlConnectionTpLinkTransport`, `HttpURLConnection`) |
| `feature/router/.../TpLinkStokLuciCrypto.kt`, `TpLinkArcherMapper.kt` | Handshake stok-luci (chaves RSA, cifra de senha e corpo); mapper para `LocalNetworkDeviceSnapshot` (`DeviceType.ROUTER`, `LAB_VALIDATED`) com rádios 2.4/5 GHz e clientes cabeados e da malha; `null` se o modelo não for suportado |
| `core/network/.../contracts/gateway/` | `AcessoEquipamento`, `GatewayConnectionService`, `GatewayCredentialRequirement`, `DeviceDriverCatalog`, `EquipmentClassifier` |
| `core/datastore/.../CredenciaisModemStore.kt` | Armazenamento de credenciais do modem |
| `app/.../ui/screen/EquipamentoInternetScreen.kt` + `EquipamentoStatusPanel`, `EquipamentoModuloTecnicoCard`, `EquipamentoTopologiaCard`, `EquipamentoDeviceSelectorCard`, `EquipamentoInfoTecnicaCard`, `EquipamentoAcoesCard`, `EquipamentoPanelMapper`, `EquipamentoInternetUiState` | Tela de leitura e cards por capacidade |
| `app/.../ui/screen/EquipamentoConectarScreen.kt` | Etapa de conexão (GH#1806); reusa `GatewayConnectionSheet` e `GatewayCompatibleModelsSheet` |
| `app/.../ui/screen/GatewayConnectionSheet.kt`, `GatewayCredentialsGuideSheet.kt`, `GatewayCompatibleModelsSheet.kt` | Formulário de credencial, guia e catálogo de modelos |
| `app/.../ui/screen/AppShell.kt` | Roteamento dos overlays, chamada aos drivers, fluxo de senha, gating |

Detalhe de módulo: [`feature-fibra.md`](../modulos/feature-fibra.md), [`feature-router.md`](../modulos/feature-router.md). Mapas de campo (reconhecimento, não produto): `technical/NOKIA_GPON_FIELD_MAP.md`, `technical/TPLINK_ARCHER_ROUTER_FIELD_MAP.md`, `technical/INTELBRAS_RX1500_FIELD_MAP.md`.

### 8. Dados e contratos

Sem contrato OpenAPI. Contrato interno normalizado: `LocalNetworkDeviceSnapshot` (em `:coreNetwork`), com `DeviceType`, `SupportLevel` e `AcessoEquipamento`. Páginas lidas do ONT e criticidade (crítico × best-effort) estão no KDoc de `ExecutorFibra.buscarSnapshot`. Credenciais em `CredenciaisModemStore` (`EncryptedSharedPreferences`, AES-256 GCM via AndroidKeyStore, arquivo `signallq_modem_credentials`; com fallback explícito em claro quando o KeyStore não existe, cenário de teste Robolectric), não em DataStore. Nada do equipamento vai para analytics.

### 9. Eventos e flags

- `feature_used` com `feature_id="fibra"` em `onReconectarFibra`, `onReiniciarEquipamento` e `onRegistrarConexaoGateway` (`MainActivity.kt`).
- `screen_view` com `screen_name="equipamento_internet"` (`analytics-events-schema.md`).
- `feature_blocked_remote` quando `consumer_fibra_enabled` bloqueia a rota (`AppShell.kt`).
- Flags: `consumer_fibra_enabled` (catálogo `consumer-catalog.json`, módulo `:featureFibra`), `feature_fibra` (legado). Detalhe: `technical/feature-flags-remote-config.md`.

### 10. Falhas e fallback

- Login do ONT: até 3 tentativas com backoff; erro permanente (host inválido, CSRF) interrompe o retry; o firmware rejeita login concorrente, por isso a sessão é reaproveitada (#894).
- Páginas best-effort do ONT não derrubam a leitura quando faltam.
- Host não consultado ou conexão impossível → `GatewayConnectionResultado.Indisponivel`/`Falha`, nunca sucesso fingido. Origem histórica: o mock anterior fingia autenticar e persistia credencial sem validar (BUG#1511).
- Reinício: desligado por `RebootLabFlags` enquanto não validado em hardware.
- `CredenciaisModemStore` cai para armazenamento em claro por `catch (_: Exception)` genérico: uma falha inesperada do KeyStore em device real degradaria em silêncio (risco registrado em `core-datastore.md`; `CredenciaisModemStore` não tem teste próprio).

### 11. Testes

Lista em `testes:`. Sem teste: `ExecutorFibra` (retry, cache de sessão, best-effort) e `NokiaModemCrypto` (handshake que quebra em silêncio com `err_t=[0]`) — os dois pontos de maior risco operacional (`feature-fibra.md`). TP-Link é coberto por fixtures locais, não por hardware em CI.

### 12. Riscos

- **Divergência com `FUNCIONAL.md` §5.6 (código vence):** o texto diz que o serviço de conexão é só `GatewayConnectionServiceIndisponivelPadrao` e que a leitura real passa só pelo Nokia. Em `AppShell.kt` o serviço agora faz `TpLinkArcherC6Driver.probe` + `loginAndRead` e devolve `Indisponivel` para o restante. Se `gatewaySessaoValida` ainda é sempre `false`: o comentário em `AppShell.kt` diz que sim; **não reverificado**.
- Cada driver cobre um modelo; não há interface comum nem registry de família (`fibra` e `router` repetem login → leitura → mapper).
- Scraping por regex sobre HTML/JS do firmware: atualização do fabricante quebra a leitura sem sinal de compilação.
- Duas noções de saúde óptica no repo: `NokiaG1425GBProfile` em `feature/fibra` e `ClassificadorSaudeGpon` genérico em `:coreNetwork`, ainda não unificados.
- `AppShell.kt` (wiring de driver e senha) está acima do limiar de 800 linhas; candidato a extração.
- `TpLinkFailure` é conjunto fechado (`INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `UNSUPPORTED_MODEL`, `COMMUNICATION`, `INVALID_RESPONSE`); a UI traduz uma vez.
- `:featureFibra` não depende de nenhuma outra feature (só de `:coreNetwork`) e não tem `@Composable`; o maior arquivo é `NokiaModemParser.kt` (acima do limiar de revisão de coesão). O motor genérico `ClassificadorSaudeGpon` (`:coreNetwork`) ainda não está ligado ao perfil Nokia.
- `reboot()` está implementado mas nunca foi validado em hardware físico (critério de aceite #1213 item 12): código vivo não exercitado.
