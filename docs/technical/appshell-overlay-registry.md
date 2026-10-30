---
title: "Ponto de extensão de overlays do AppShell"
description: "Como plugar overlay novo no AppShell.kt sem editar o arquivo central, o que o registro cobre e não cobre, e a delegação de back ao overlay do topo."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.1.0"
---

# Ponto de extensão de overlays do AppShell

- **Fonte de verdade:** o código (`android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellOverlayRegistry.kt`
  e `AppShellNavigation.kt`); se divergirem deste documento, o código vence (higiene §3).
- **Escopo:** módulo `:app`, overlays empilhados pelo `AppShell.kt`. Não cobre a navegação entre as
  4 raízes — ver [`appshell-root-content-registry.md`](appshell-root-content-registry.md).
- **Origem:** issue #1695 (épico #1647). **Substitui** a versão 1.5.0, que carregava o histórico de
  fatias e um bloco de testes (`AppShellOverlayRegistryTest`) que não existe mais; recuperável por
  `git log -- docs/technical/appshell-overlay-registry.md`.

## Objetivo

`AppShell.kt` é o shell de composição e navegação. Cada tela overlay (Perfil, Sinal Wi-Fi, Detalhes
técnicos…) precisava de um bloco `AnimatedVisibility` inline ali para entrar e sair da pilha, o que
concentrava risco e travava fatias paralelas. O registro tira esse bloco do arquivo central.

## O padrão

| Arquivo | Responsabilidade |
|---|---|
| `AppShellNavigation.kt` | **Quando** um overlay existe: enum `AppShellOverlay`, pilha por raiz (`AppShellNavigator`: push/pop/back/restauração). Fonte única da navegação |
| `AppShellOverlayRegistry.kt` | **O quê** desenha cada overlay: agrega os Composables de `AppShellXxxOverlay.kt` num único ponto. Não decide push/pop |

### Plugar um overlay novo

1. Adicione o valor em `AppShellOverlay` (`AppShellNavigation.kt`).
2. Crie `AppShellXxxOverlay.kt` com um `@Composable internal fun` que recebe **só o que precisa**
   (`overlayStack` + dados/callbacks estreitos). O arquivo faz o próprio `AnimatedVisibility` e
   `zIndex` (via `rememberOverlayZIndex`, em `AppShell.kt`) — exceto quando a tela já tem animação
   própria (`PingScreen` é `ModalBottomSheet`; `AppShellPingOverlay.kt` só cuida do `zIndex`).
   `onVoltar` remove **só o próprio overlay** (`overlayStack.remove(AppShellOverlay.Xxx)`).
3. Chame o overlay dentro de `AppShellOverlayRegistry`. A ordem de declaração não decide o z-index
   (isso é `rememberOverlayZIndex`, pela posição real em `overlayStack`).
4. Se o overlay precisar de dado que `AppShell.kt` ainda não expõe, ele circula por `AppShell.kt`
   primeiro (parâmetro novo do registro + o único call site).

**Regra de parâmetros:** campos soltos no registro são legado. Overlay novo ou tocado entra como
**uma entrada agrupada** (`AppShellXxxEntry`, `@Stable data class`) — `AppShellDiagnosticoGuiadoEntry`
é o modelo — em vez de acrescentar campos à assinatura.

### Estado atual

Registrados em `AppShellOverlayRegistry`: `Termos`, `Novidades`, `Privacidade`, `DetalhesTecnicos`,
`SinalWifi`, `DiagnosticoGuiado`, `Ping` e `Dns`.

Têm arquivo próprio mas **fora do registro** (chamados direto de `AppShell.kt`): `ResultadoVelocidade`
(`AppShellResultadoVelocidadeOverlay.kt`) e `Laudo` (`AppShellLaudoOverlay.kt`).

Ainda inline no `AppShell.kt`: `Ajustes`, `Perfil`, `Ferramentas`, `Dispositivos`, `Fibra`,
`EquipamentoInternet`, `EquipamentoConectar`, `SinalCanais` e `ModoGamer`. Migrar é trabalho de quem
tocar cada área, não obrigação retroativa.

Sheets sem back-stack (`showMonitoramentoSheet`, `showEquipamentoCredenciaisSheet`,
`showGerenciarDadosSheet`, `showAjudaSuporteSheet`, `showSobreAppSheet`) não usam `overlayStack` e
ficam fora do registro.

## O que este registro não resolve

Blocos `AnimatedVisibility` de overlay foram só ~15% das ~226 linhas que cinco fatias do épico
devolveram ao `AppShell.kt` (medição da revisão da PR #1697). O resto foi wiring de root content,
estado hoisted e lambdas de regra de negócio, que não passam por aqui — o registro irmão de raízes
cobre parte disso. Estado hoisted e callbacks que empilham overlay seguem no shell.

## Diagnóstico guiado

A medição própria do fluxo e a supressão de reações do shell (`rememberMedicaoGuiada`, `suprimeReacoesDoShell`, limite de início) são comportamento da feature: ver [`features/assist-diagnostico.md`](../features/assist-diagnostico.md) (§10). Aqui fica só o mecanismo de back, abaixo.

## Delegação de back ao overlay do topo

Um overlay com **fluxo interno de vários passos** registra sua política de back antes de sair da
pilha. `AppShellNavigator` mantém um mapa de interceptadores por overlay, consultado por
`AppShellBackHandlers` **antes** do `pop`:

```kotlin
if (navigator.consumirBackDoOverlayTopo()) return@BackHandler
navigator.pop()?.let(onOverlayRemoved)
```

O overlay registra com `RegistrarBackDoOverlay(navigator, overlay) { ... }`, chamado no nível do
`AppShellXxxOverlay`, **fora** do `AnimatedVisibility` (senão o desregistro fica preso à animação de
saída). `onBack` devolve `true` se consumiu o evento, `false` se o overlay deve sair. Não é um
segundo motor de navegação: há um dispatcher, uma pilha e um dono do back.

**Ligação de produção (diagnóstico guiado):** o estado do fluxo (`DiagnosticoGuiadoEstado`) mora
dentro da tela, que só existe dentro do `AnimatedVisibility`; hoistá-lo quebraria a testabilidade da
tela. A ponte é o parâmetro `onBackHandlerReady: (onBack: () -> Boolean) -> Unit` de
`DiagnosticoGuiadoScreen`, chamado por `SideEffect`; o overlay guarda a função num
`var backHandler by remember` fora do `AnimatedVisibility` e registra
`RegistrarBackDoOverlay(navigator, AppShellOverlay.DiagnosticoGuiado) { backHandler() }`. Hoje a
política devolve sempre `false`: respostas anteriores não são uma segunda pilha, então seta e gesto
de voltar fecham a jornada de imediato.

Por isso `AppShellDiagnosticoGuiadoOverlay` recebe `navigator: AppShellNavigator` em vez de
`overlayStack` — único overlay com essa exceção, por ser o único com interceptador. **Não generalize
a mudança de assinatura aos demais "por consistência"**: sem consumidor real é código sem uso.

### Por que não um `AppShellOverlay` por passo do fluxo guiado

A pilha é set-like (`open()` ignora duplicata) e o fluxo é cíclico (`result → guidance → retest →
comparison → guidance`); overlays também acumulam em vez de substituir. **Regra:** `AppShellOverlay`
guarda destinos que o shell pode ser mandado abrir de fora do fluxo que os contém; sub-passo que
nenhum chamador externo endereça não é overlay.

### A guarda `estaNoTopo` não é redundância

`RegistrarBackDoOverlay` só registra quando o overlay é o topo, e `consumirBackDoOverlayTopo`
também consulta só o topo. A primeira é o que **desfaz o registro** quando o overlay deixa o topo
sem sair de composição (o caso normal, pois o registro compõe todos os overlays sempre). Remover a
guarda não muda o comportamento do back, mas o mapa acumularia entradas de overlays fora do topo; a
invariante é asserida por `navigator.overlaysComInterceptador()`.

## Testes

`android/app/src/test/kotlin/io/signallq/app/ui/screen/`: `AppShellNavigationTest`,
`AppShellNavigationComposeTest`, `AppShellBackDelegacaoTest`, `AppShellMedicaoGuiadaTest` e
`AppShellFeatureGatingTest`. **Lacuna:** não existe teste do `AppShellOverlayRegistry` em si
(`AppShellRootRegistryTest.kt` cita um `AppShellOverlayRegistryTest` que não está no repositório);
remover uma chamada do registro deixaria a tela sumir sem teste vermelho. Overlay novo deve ao menos
testar o `AppShellXxxOverlay` direto (visibilidade condicionada à pilha, `onVoltar` removendo só o
próprio overlay).

## Referências

- Regra de higiene §4.3 (`.claude/rules/higiene-e-padronizacao-repositorio.md`)
- `AppShellNavigation.kt` (pilha/push/pop/back) e o KDoc de `AppShellOverlayRegistry.kt`
