---
title: "Sinal — Wi-Fi, canais e rede móvel"
description: "Tela Sinal com três abas (Wi-Fi, Canal, Móvel): redes ao redor e topologia da sua conexão, congestionamento de canais e qualidade do sinal do chip, para quem quer saber se o problema é o sinal."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "wifi-canais-sinal"
tipo: "jornada"
modulos:
  - "android/app"
  - "android/feature/wifi"
  - "android/core/network"
  - "android/core/diagnostico"
  - "android/core/telephony"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalWifiSection.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalCanalSection.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalMovelSection.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalMovelClassificacao.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalSharedComponents.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/SinalTopologiaHelpers.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/BancoOperadoras.kt"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/wifi/"
  - "android/core/network/src/main/kotlin/io/signallq/app/core/network/topologia/"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/WifiChannelDiagnosticEngine.kt"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/WifiSignalQualityEngine.kt"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/MobileSignalDiagnosticEngine.kt"
  - "android/core/telephony/src/main/kotlin/io/signallq/app/core/telephony/MonitorTelephonyImpl.kt"
contratos: []
eventos:
  - "feature_used (feature_id=wifi)"
  - "feature_blocked_remote (feature_id=wifi)"
flags:
  - "consumer_wifi_enabled"
testes:
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalCanalWifiRedesenho2SpecTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalScreenAutoRefreshTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalScreenErroPreservaDadoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalScreenExtracaoAbaCaracterizacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalScreenMeshApSheetRoutingTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalScreenOfflineBannerTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalMovelClassificacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalMovelContatoOperadoraTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalMovelOperadoraBadgeCaracterizacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalMovelPermissaoReduzidaCaracterizacaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/SinalMovelResumoTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/WifiChannelDiagnosticEngineTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/WifiSignalQualityEngineTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/MobileSignalDiagnosticEngineTest.kt"
  - "android/core/network/src/test/kotlin/io/signallq/app/core/network/topologia/engine/TopologiaRedeEngineTest.kt"
  - "android/core/telephony/src/test/kotlin/io/signallq/app/core/telephony/MonitorTelephonyTest.kt"
adrs: []
thresholds_em: "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/WifiChannelDiagnosticEngine.kt"
---

# Sinal — Wi-Fi, canais e rede móvel

Migrado de `FUNCIONAL.md` (§5.2 e §5.3, exceto o parágrafo do WiFi Casa, que vive em [`wifi-casa`](wifi-casa.md)). Fatos conferidos no código em 2026-10-04.

**Nomes:** o slug de produto é `wifi-canais-sinal`. Os nomes técnicos são outros: `Overlay.SinalCanais` abre a tela `SinalScreen` (três abas); `TipoFerramenta.SINAL_CANAIS_MOVEL` é o card do hub; o overlay `SinalWifi`/`TipoFerramenta.SINAL_WIFI` pertence ao [`wifi-casa`](wifi-casa.md). Ambos os tipos de ferramenta têm `screenName()` igual a `sinal_wifi`.

## NEGÓCIO

### 1. Problema e promessa

Responde "o problema é o sinal? o canal está congestionado? o chip está bom?". Lidera com conclusão em linguagem simples, antes da sigla técnica. Não afirma incerteza de estrutura nem causa sem evidência.

### 2. Quando aparece e para quem

Tela `SinalScreen` com abas Wi-Fi (0), Canal (1) e Móvel (2), aberta pelo hub Ferramentas. A aba Móvel é auto-selecionada quando a conexão ativa não é Wi-Fi. Sem Wi-Fi, as abas Wi-Fi e Canal mostram "Você está usando a internet do chip". Auto-refresh de 30 s enquanto visível e em foreground (`SINAL_AUTO_REFRESH_INTERVAL_MS`, `SinalScreen.kt`). Wi-Fi exige permissão de localização; Móvel usa `READ_PHONE_STATE`.

### 3. Regras de decisão

- **Aba Wi-Fi.** Lista redes ao redor com filtro por banda (Todos / 2.4 / 5 / 6 GHz). O bloco "SUA CONEXÃO" desenha a **árvore de topologia** do próprio SSID: o nó conectado mais os BSSIDs que o motor de topologia confirmou como mesma infraestrutura (`GrupoRedeTree`). SSID igual sem evidência de fabricante/banda cai em "outras redes". Quando o motor não tem confiança alta sobre a estrutura, **a tela fica em silêncio** (decisão #1661, 2026-08-19): nunca afirma a incerteza, nem em rodapé. Redes de terceiros agrupadas por SSID, expansíveis. Tocar numa rede abre sheet com sinal, banda, canal, largura, segurança e BSSID; se o nó corresponde a dispositivo real do scan da LAN, abre a sheet de AP mesh.
- **Aba Canal.** Explicação simples (há interferência? vale trocar de canal?) e lista "Ocupação dos canais" ordenada por congestionamento, com status Livre/Moderado/Congestionado (limiares em `thresholds_em`). **Sem gráfico de espectro**: removido, não escondido (#1661, 2026-08-19), divergência deliberada do protótipo do Design System 2.0. Um único bloco de aviso por vez, mutuamente exclusivo: canal congestionado, canal limpo ou canal recomendado. Card de band steering quando está em 2.4 GHz e existe nó do mesmo SSID em 5 GHz. O rótulo da aba ganha ícone de alerta quando o canal conectado está congestionado.
- **Aba Móvel.** Um card por SIM ativo ("Chip 1", "Chip 2"), com logo da operadora e badge "EM USO" no SIM padrão de dados (dual SIM; nunca confunde SIM ativa com SIM de dados, GH#1206). Três cards fixos, **conclusão antes da sigla**: Qualidade do sinal, Tipo de conexão e Experiência esperada, com badge vindo de `SinalMovelClassificacao.kt`. Quarto card opcional "Detalhes técnicos" (RSRP/RSRQ/SINR, tecnologia) só depois da conclusão e só com métrica bruta disponível. CTA de contato ("Falar com a {operadora}" ou "Falar com sua operadora") sempre com destino: site do catálogo local (`BancoOperadoras`) ou busca genérica (#1662).
- Dado medido: scan Wi-Fi, RSSI, métricas do chip. Inferência determinística: topologia, ocupação de canal, classificação de sinal. Sem IA nesta tela.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Sem Wi-Fi (abas Wi-Fi e Canal) | "Você está usando a internet do chip" |
| Mesh incerto | Silêncio; sem aviso nem rodapé |
| Sem permissão de telefonia | Aba Móvel reduzida: só operadora, banner explicando o que falta e atalho para conceder; estado vazio de bloqueio só quando não há nenhum dado (sem SIM, emulador) |
| Dados brutos do chip (ASU, roaming, MCC/MNC) | Seguem na `CellularInfoSheet` da Início |
| AP mesh | Sheet honesta: "Sinal, banda e clientes conectados não estão disponíveis via varredura passiva. Para métricas detalhadas, acesse o painel do seu roteador mesh." |
| Erro de scan | Preserva o dado anterior (`SinalScreenErroPreservaDadoTest`); banner offline coberto por `SinalScreenOfflineBannerTest` |

### 5. Próximo passo e confirmação

Canal recomendado para migração, band steering e CTA da operadora. Confirmação: o auto-refresh reflete a mudança depois que o usuário troca o canal no roteador.

### 6. Fora de escopo, status e flag

Status: entregue. Flag `consumer_wifi_enabled` (módulo `wifi`) gateia o hub e as rotas Wi-Fi/Sinal. Fora de escopo: gráfico de espectro, métricas de nó mesh por varredura passiva.

## TÉCNICO

### 7. Mapa de código

| Responsabilidade | Módulo | Arquivo |
|---|---|---|
| Scaffold, abas, auto-refresh, sheets de permissão | android/app | `ui/screen/SinalScreen.kt` |
| Aba Wi-Fi | android/app | `SinalWifiSection.kt` (~1110 linhas) |
| Aba Canal (`CanalTab`) | android/app | `SinalCanalSection.kt` (~1215 linhas) |
| Aba Móvel (`MovelTab`) | android/app | `SinalMovelSection.kt`, `SinalMovelClassificacao.kt` |
| Componentes Wi-Fi/Canal compartilhados | android/app | `SinalSharedComponents.kt` |
| Helpers visuais de topologia e `signalQuality` | android/app | `SinalTopologiaHelpers.kt` |
| Catálogo de operadoras | android/app | `ui/BancoOperadoras.kt` |
| Resumo textual do estado da conexão (`ResumoWifi`: título + detalhe por tipo) e tipos de topologia (`TipoTopologia`, `ConfiancaTopologia`, `RedeClassificada`, `GrupoRedeWifi`) | android/feature/wifi | `MontarResumoWifiUseCase.kt`, `GrupoRedeWifi.kt`, `FeatureWifiModulo.kt` (factories) |
| Scan Wi-Fi | android/core/network | `wifi/ScannerRedesWifi.kt`, `SnapshotScanWifi.kt` |
| Topologia | android/core/network | `topologia/engine/TopologiaRedeEngine.kt`, `topologia/oui/OuiCatalog.kt` |
| Engines de canal, sinal Wi-Fi e sinal móvel | android/core/diagnostico | `WifiChannelDiagnosticEngine.kt`, `WifiSignalQualityEngine.kt`, `MobileSignalDiagnosticEngine.kt` |
| Telefonia | android/core/telephony | `MonitorTelephonyImpl.kt` |

Regra de manutenção (`higiene` §4.8b): regra de negócio de diagnóstico pertence a `core/diagnostico` ou `core/network`; só função pura de apoio visual vai em `SinalTopologiaHelpers.kt`.

### 8. Dados e contratos

Sem contrato próprio, sem persistência. Privacidade da telemetria móvel (`core/telephony`): `cellId`/`mcc`/`mnc`/`tac` são metadados sensíveis, coletados só para o diagnóstico de IA, descartados após o envio, não persistidos nem logados em produção; `READ_PHONE_STATE` é pedida de forma lazy e `ACCESS_*_LOCATION` não é exigida para `getAllCellInfo()` na API 29+. Entradas: `SnapshotScanWifi`, `SnapshotRede`, snapshot de telefonia, correlações de topologia com dispositivos (ver [`dispositivos-rede`](dispositivos-rede.md)).

### 9. Eventos e flags

`feature_used` com `feature_id=wifi`, disparado no `onRefreshSinal` (`MainActivity.kt`). `feature_blocked_remote` com `feature_id=wifi` quando a flag bloqueia a rota (`AppShell.kt`). `consumer_wifi_enabled` em `FeatureFlagKeys.kt`. Detalhe: `docs/technical/feature-flags-remote-config.md`.

### 10. Falhas e fallback

Sem localização, sem telefonia, sem Wi-Fi, scan com erro: cada caso tem estado próprio (seção 4); erro preserva o dado anterior em vez de zerar.

### 11. Testes

Lista em `testes:`. As seções Wi-Fi/Canal/Móvel têm teste de caracterização da extração por aba (#1660).

### 12. Riscos

- `SinalWifiSection.kt` e `SinalCanalSection.kt` já nascem acima de 800 linhas (`higiene` §7); a extração por componente espera as fatias #1661/#1662/#1668 definirem a forma final.
- O protótipo do Design System 2.0 ainda cita gráficos técnicos de canal como extensão futura, em desacordo com o produto atual.
