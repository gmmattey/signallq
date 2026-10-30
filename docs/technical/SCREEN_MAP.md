---
title: "Screen Map — Android SignallQ"
description: "Mapa de navegação do app consumer (4 raízes + pilha de overlays) validado contra AppShell.kt e AppShellNavigation.kt."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.1.0"
---

# Screen Map — Android SignallQ

- **Fonte de verdade:** código — `android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShell.kt`
  e `AppShellNavigation.kt` (enums `AppShellRoot` e `AppShellOverlay`)
- **Escopo:** navegação do app consumer (barra inferior + overlays). Padrões de extensão em
  [`appshell-root-content-registry.md`](appshell-root-content-registry.md) e
  [`appshell-overlay-registry.md`](appshell-overlay-registry.md)

Cada tela pertence a uma feature de produto (coluna **Feature**; índice em [`features/INDEX.md`](../features/INDEX.md)).

Todas as telas residem em `android/app/src/main/kotlin/io/signallq/app/ui/screen/`. A navegação viva
é `AppShellNavigator` (raiz selecionada + pilha de overlays por raiz), não Compose Navigation.

---

## Barra inferior — 4 raízes

`AppShellRoot` em `AppShellNavigation.kt`; rótulos em `AppShellBottomBar.kt`.

| Índice | Raiz | Label | Composable | Arquivo | Feature |
|---|---|---|---|---|---|
| 0 | `Home` | Início | `Inicio2Screen` | `Inicio2Screen.kt` | [`inicio-status`](../features/inicio-status.md) |
| 1 | `Speed` | Velocidade | `SpeedTestScreen` | `SpeedTestScreen.kt` | [`velocidade-resultado`](../features/velocidade-resultado.md) |
| 2 | `History` | Histórico | `HistoricoScreen` | `AppShellHistoricoRoot.kt` → `HistoricoScreen.kt` | [`historico-laudo`](../features/historico-laudo.md) |
| 3 | `Tools` | Ferramentas | `FerramentasScreen` | `AppShellFerramentasRoot.kt` → `FerramentasScreen.kt` | hub: ver cada ferramenta |

Não existe aba "Ajustes" nem "Mais": Ajustes é overlay, aberto pelo acesso de perfil/menu de cada
raiz. Durante a execução do speedtest a barra inferior some (`shouldShowAppShellBottomBar`).

---

## Overlays

`AppShellOverlay` (`AppShellNavigation.kt`), empilhados em `overlayStack`, cada um em
`AnimatedVisibility` com z-index pela posição na pilha (`rememberOverlayZIndex`).

| Overlay | Composable | Arquivo | Origem típica | Feature |
|---|---|---|---|---|
| `ResultadoVelocidade` | `ResultadoVelocidadeScreen` | `AppShellResultadoVelocidadeOverlay.kt` | Teste de velocidade concluído | [`velocidade-resultado`](../features/velocidade-resultado.md) |
| `Laudo` | `LaudoScreen` | `AppShellLaudoOverlay.kt` | "Gerar laudo" (Ferramentas, diagnóstico, atalhos) | [`historico-laudo`](../features/historico-laudo.md) |
| `Dispositivos` | `DispositivosScreen` | `DispositivosScreen.kt` | Ferramentas, atalhos da Início | [`dispositivos-rede`](../features/dispositivos-rede.md) |
| `EquipamentoConectar` | `EquipamentoConectarScreen` | `EquipamentoConectarScreen.kt` | Gateway sem endereço de equipamento salvo (GH#1806) | [`equipamento-internet`](../features/equipamento-internet.md) |
| `EquipamentoInternet` | `EquipamentoInternetScreen` | `EquipamentoInternetScreen.kt` | Ferramentas, nó do gateway na Início | [`equipamento-internet`](../features/equipamento-internet.md) |
| `Fibra` | `EquipamentoInternetScreen` | `EquipamentoInternetScreen.kt` | Conexão bem-sucedida ao equipamento (mesma tela de `EquipamentoInternet`; nome histórico) | [`equipamento-internet`](../features/equipamento-internet.md) |
| `Ferramentas` | `FerramentasScreen` | `FerramentasScreen.kt` | Hub aberto como overlay a partir de card de ferramenta sugerida (Início); fecha limpando `ferramentaRecomendada` | hub: ver cada ferramenta |
| `Ping` | `PingScreen` | `AppShellPingOverlay.kt` | Ferramentas | [`dns-ping`](../features/dns-ping.md) |
| `Dns` | `DnsScreen` | `AppShellDnsOverlay.kt` | Ferramentas | [`dns-ping`](../features/dns-ping.md) |
| `SinalWifi` | `WifiCasaScreen` | `AppShellSinalWifiOverlay.kt` | Ferramentas, trilha da Início (nome técnico preservado; `SinalWifiScreen` segue existindo) | [`wifi-casa`](../features/wifi-casa.md) |
| `SinalCanais` | `SinalScreen` | `SinalScreen.kt` (+ `SinalWifiSection`/`SinalCanalSection`/`SinalMovelSection`) | Ferramentas | [`wifi-canais-sinal`](../features/wifi-canais-sinal.md) |
| `DiagnosticoGuiado` | `DiagnosticoGuiadoScreen` | `AppShellDiagnosticoGuiadoOverlay.kt` | Início / Ferramentas | [`assist-diagnostico`](../features/assist-diagnostico.md) |
| `DetalhesTecnicos` | `DetalhesTecnicosScreen` | `AppShellDetalhesTecnicosOverlay.kt` | Resultado de velocidade | [`assist-diagnostico`](../features/assist-diagnostico.md) |
| `ModoGamer` | `ModoGamerScreen` | `ModoGamerScreen.kt` | Ferramentas, resultado de velocidade | [`modo-gamer`](../features/modo-gamer.md) |
| `Ajustes` | `AjustesScreen` | `AjustesScreen.kt` | Acesso de perfil/menu de qualquer raiz | [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md) |
| `Privacidade` | `PrivacidadeScreen` | `AppShellPrivacidadeOverlay.kt` | Ajustes | [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md) |
| `Novidades` | `NovidadesScreen` | `AppShellNovidadesOverlay.kt` | Ajustes | [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md) |
| `Termos` | `TermosDeUsoScreen` | `AppShellTermosOverlay.kt` | Ajustes | [`perfil-ajustes-legal`](../features/perfil-ajustes-legal.md) |
| `Perfil` | — | — | Valor presente no enum **sem uso**: não há tela intermediária de Perfil (comentário em `AppShell.kt`); candidato a remoção | — |

**Sem rota no app:** telas de chat/IA conversacional (`SignallQScreen`, `SignallQPulseScreen`,
`LLMChatScreen`, `ChatDiagnosticoIaScreen`) e o fluxo "Jogos" (`JogosScreen`, `Overlay.Jogos`)
foram removidos (GH#937, GH#1682, GH#1487 — decisão de produto). O fluxo de IA real é a "Análise
avançada" no `LaudoScreen` (ver `docs/technical/AI_FLOW.md`); o teste de jogo vive no Modo gamer.
Não reintroduzir rota para nenhuma delas.

Sheets sem back-stack (monitoramento — feature [`monitoramento-alertas`](../features/monitoramento-alertas.md), credenciais de equipamento, gerenciar dados, ajuda/suporte,
sobre) não usam `AppShellOverlay` — ver `appshell-overlay-registry.md`.

---

## Onboarding

| Composable | Arquivo | Acesso |
|---|---|---|
| `OnboardingScreen` | `OnboardingScreen.kt`, montado na `MainActivity` | Apenas primeira execução (`onboardingConcluidoFlow` no DataStore); boas-vindas + termos/LGPD, sem pedido de permissão em lote |

---

## Arquivos de suporte à navegação

| Arquivo | Papel |
|---|---|
| `AppShell.kt` | Shell: barra inferior de 4 raízes + pilha de overlays |
| `AppShellNavigation.kt` | `AppShellRoot`, `AppShellOverlay`, `AppShellNavigator`, back |
| `AppShellRootRegistry.kt` / `AppShellOverlayRegistry.kt` | Agregadores de raízes e overlays |
| `AppShellState.kt` | Grupos de estado (`AppShellXxxState`) passados ao shell |
| `MainViewModel.kt` | ViewModel raiz `@HiltViewModel`; dívida registrada em `.claude/rules/higiene-e-padronizacao-repositorio.md` §4.2 |

---

## Diagrama de navegação

```
OnboardingScreen (primeira execução)
    ↓
AppShell  (barra inferior com 4 raízes + overlays)
├── [0] Início ── Dispositivos · Laudo · EquipamentoInternet/Conectar · SinalWifi · DiagnosticoGuiado
├── [1] Velocidade ── ResultadoVelocidade ── DetalhesTecnicos · ModoGamer
├── [2] Histórico
└── [3] Ferramentas (hub)
        ├── Dispositivos · EquipamentoInternet · Ping · Dns
        ├── Laudo · DiagnosticoGuiado · ModoGamer
        └── SinalWifi · SinalCanais

Acesso de perfil/menu (qualquer raiz) → Ajustes
        ├── Privacidade
        ├── Novidades
        └── Termos
```
