---
title: "Ponto de extensão de root content do AppShell"
description: "Como migrar/plugar uma raiz (tab) do AppShell.kt sem inchar o arquivo central: um parâmetro por raiz, três camadas de teste. Irmão do registro de overlays."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Ponto de extensão de root content do AppShell

- **Fonte de verdade:** o código (`android/app/src/main/kotlin/io/signallq/app/ui/screen/AppShellRootRegistry.kt`);
  se divergir deste documento, o código vence (higiene §3).
- **Escopo:** módulo `:app`, as 4 raízes do `AppShell.kt` (`AppShellRoot`: `Home`, `Speed`, `History`,
  `Tools`). Não cobre overlay ([`appshell-overlay-registry.md`](appshell-overlay-registry.md)) nem a
  navegação em si (`AppShellNavigation.kt`).
- **Origem:** issue #1698 (épico #1647). **Substitui** a versão 1.0.0, que carregava a contabilidade
  de linhas da PR e uma tabela de mutação; recuperável por
  `git log -- docs/technical/appshell-root-content-registry.md`.

## Por que existe

O registro de overlays cobre só ~15% do que fez o `AppShell.kt` crescer. Os outros ~85% (medição da
revisão da PR #1697) eram wiring de root content, estado hoisted e lambdas de regra de negócio, que
não passavam por ele.

## O padrão

| Arquivo | Responsabilidade |
|---|---|
| `AppShellNavigation.kt` | **Quando/qual**: enum `AppShellRoot`, `AppShellNavigator`, pilha por raiz, back |
| `AppShellRootRegistry.kt` | **O quê desenha cada raiz**: agrega os Composables de `AppShellXxxRoot.kt`; não decide seleção de raiz nem back |
| `AppShellOverlayRegistry.kt` | O mesmo, para overlay empilhado |

Não há segundo motor de navegação: o registro recebe a raiz já resolvida (`navigator.selectedRoot`;
índice desconhecido cai em `AppShellRoot.Tools`) e só decide qual Composable desenhar.

### Regra central — um parâmetro por raiz, nunca N campos soltos

Cada raiz contribui com **exatamente um** parâmetro (um `@Stable data class AppShellXxxRootEntry`),
para o registro não virar o próximo ponto de concentração (o de overlays chegou a 17 parâmetros
soltos). **O linter não protege isso:** `detekt` tem `LongParameterList` (threshold 8), mas
`build.maxIssues: 2000` torna o gate mudo — a disciplina é humana.

### Passo a passo para migrar uma raiz

1. **Criar `AppShellXxxRoot.kt`** com um `@Composable internal fun` que recebe só o que a raiz
   precisa. Acima de ~3 campos, declare um `@Stable data class` de grupo no mesmo arquivo
   (`AppShellHistoricoState`, `AppShellFerramentasAcoes` são as referências).
2. **Mover a construção do grupo para a `MainActivity`**, que já monta `AppShellSpeedtestState`,
   `AppShellWifiState` etc. É este passo que **de fato encolhe** `AppShell.kt`; só vale quando o
   grupo é estado de ViewModel. Grupo de callbacks que empilham overlay (hub Ferramentas) é
   inerentemente do shell e fica nele — re-embrulhar campos soltos num grupo construído dentro do
   shell só troca a forma do wiring.
3. **Adicionar uma entrada própria** ao `when` de `AppShellRootRegistry` (com o
   `AppShellXxxRootEntry`).
4. **Escrever as três camadas de teste** (abaixo) antes de considerar pronto.

### Estado atual

| Raiz | Onde vive |
|---|---|
| `History` | `AppShellHistoricoRoot.kt` — estado em `AppShellHistoricoState`, construído na `MainActivity`; entrada `AppShellHistoricoRootEntry` (`state`, `adsGate`, `onAbrirMenu`, `onIniciarTeste`) |
| `Tools` | `AppShellFerramentasRoot.kt` — regra `resolverDisponibilidadeFerramenta` (função pura) e os callbacks em `AppShellFerramentasAcoes`; entrada `AppShellFerramentasRootEntry` |
| `Home`, `Speed` | ainda inline em `AppShell.kt`, via slot `inlineRootContent` (`Inicio2Screen` e a tela de velocidade). Sinal e Wi-Fi são overlays do hub Ferramentas/trilha do Início, não raízes |

O ganho do padrão é o custo marginal da próxima fatia: mexer no Histórico edita
`AppShellHistoricoRoot.kt`; mexer na regra de disponibilidade do hub edita
`AppShellFerramentasRoot.kt`, com teste unitário barato e sem compor o shell.

## Testes

`AppShellRootRegistryTest.kt` e `ResolverDisponibilidadeFerramentaTest.kt`
(`android/app/src/test/kotlin/io/signallq/app/ui/screen/`), em três camadas:

1. **por raiz** — `AppShellXxxRoot` chamado direto: repasse de estado, callbacks e regra;
2. **por registro** — `AppShellRootRegistry` inteiro: confirma que a entrada daquela raiz existe no
   agregador (sem esta camada, apagar a chamada do registro deixa a suíte verde e a tela some);
3. **pelo slot** — `inlineRootContent` é chamado só para Início e Velocidade e **nunca** para
   Histórico e Ferramentas (pega a reversão silenciosa de uma migração).

Raiz nova migrada deve repetir as três camadas **e** exercitar cada campo da sua `RootEntry` com
valor distinguível (helpers de teste que fixam `onAbrirMenu = {}` deixam mutantes sobreviverem).

**Limite conhecido:** a suíte usa `NativeAdsGate()` padrão sem variar o campo `adsGate` da entrada do Histórico; o comentário de `AppShellRootRegistryTest.kt` registra por que ele não é distinguível sob Robolectric (anúncio nativo só renderiza com resposta real do AdMob). Até lá depende de revisão humana do diff.

## Referências

- [`appshell-overlay-registry.md`](appshell-overlay-registry.md) — registro irmão, para overlay
- Regra de higiene §4.3 (`.claude/rules/higiene-e-padronizacao-repositorio.md`)
- `AppShellNavigation.kt` (raiz/pilha/back)
