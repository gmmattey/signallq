---
title: "SignallQ Assist — diagnóstico guiado"
description: "Diagnóstico guiado por objetivo (Assist), detalhes técnicos e diagnóstico offline guiado: regras, estados, flags diagnostico_*, código, eventos e testes."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "assist-diagnostico"
tipo: "jornada"
modulos:
  - "android/feature/diagnostico"
  - "android/core/diagnostico"
  - "android/core/nds"
  - "android/core/recommendation"
  - "android/core/featureflags"
  - "android/app"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DiagnosticoGuiadoScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DiagnosticoGuiadoResultadoSection.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/DetalhesTecnicosScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/component/DiagnosticoResultadoComponents.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/component/DiagnosticoOfflineDialog.kt"
  - "android/app/src/main/kotlin/io/signallq/app/diagnosticooffline/"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/DiagnosticoGuiadoEngine.kt"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/ObjetivoDiagnostico.kt"
  - "android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/DiagnosticOrchestrator.kt"
  - "android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/nds/"
contratos: []
eventos:
  - "diagnostico_objetivo_selecionado"
  - "diagnostico_pergunta_respondida"
  - "diagnostico_guiado_abandonado"
  - "diagnostico_plano_iniciado"
  - "diagnostico_reteste_iniciado"
  - "diagnostico_comparacao_concluida"
  - "diag_iniciado"
  - "diag_concluido"
flags:
  - "consumer_diagnostico_enabled"
  - "consumer_diagnostico_shadow_mode_enabled"
  - "consumer_diagnostico_nds_live_enabled"
  - "consumer_diagnostico_assist_nds_v2_enabled"
  - "consumer_diagnostico_nds_v2_enabled"
testes:
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/DiagnosticoGuiadoEngineTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DiagnosticoGuiadoAnaliseTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DiagnosticoGuiadoProcessandoSectionTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/DiagnosticoGuiadoResultadoNdsTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/diagnosticooffline/DiagnosticoOfflineExecutorRealTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/diagnosticooffline/DiagnosticoOfflineViewModelTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/component/DiagnosticoOfflineDialogTest.kt"
adrs: []
thresholds_em: "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/DiagnosticoGuiadoEngine.kt"
---

# SignallQ Assist — diagnóstico guiado

Migrado de `FUNCIONAL.md` §5.7 (exceto Laudo, em [`historico-laudo.md`](historico-laudo.md)) e §5.5b, e do catálogo/KDoc das flags. Conferido no código em 2026-10-04.

## Negócio

### 1. Problema e promessa

O usuário diz "minha internet está ruim" sem saber onde. O Assist faz a jornada **entender → diagnosticar → resolver → confirmar** sem chat livre: o usuário escolhe um objetivo, responde 2 perguntas fechadas e recebe uma conclusão com evidência, confiança e próximo passo. A IA nunca decide o status; só escreve a prosa sobre dados medidos.

### 2. Quando aparece e para quem

CTA do SignallQ Assist na Início ou no resultado do speedtest. **Diagnóstico offline guiado:** CTA "Diagnosticar problema" dentro do `SignallQOfflineBanner` (`ui/component/SignallQScreenState.kt`), banner não-bloqueante em telas como Sinal e Dispositivos quando não há conexão ativa. **Detalhes técnicos:** caminho paralelo, sem IA e sem recomendação. Qualquer usuário; a rota é gateada por `consumer_diagnostico_enabled`.

### 3. Regras de decisão

- **7 objetivos fechados** + "Quero verificar minha conexão" (`ObjetivoDiagnostico.kt`): internet cai/instável; vídeos travam; jogos atrasam; chamadas de vídeo travam; sites demoram; velocidade abaixo do plano; não sei onde está o problema. Depois, **2 perguntas fechadas** (single-select, barra de progresso).
- Speedtest não válido para conclusão: a tela nem entra no fluxo, pede para refazer o teste na mesma rede.
- **Card "Próximo passo"** aponta **uma** ferramenta (`TipoFerramenta.kt`): sites lentos e velocidade abaixo do plano → DNS; internet instável → Monitoramento; "não sei" → Sinal Wi-Fi. Vídeos, jogos e chamadas **não recebem card** (regra explícita de não empurrar sugestão fraca).
- **Contato da operadora** só quando a causa aponta ISP ou fibra. **Sugestão** (motor de recomendação) rotulada por tipo (DICA, TUTORIAL, AJUSTE RECOMENDADO, PRODUTO SUGERIDO, OFERTA DE PARCEIRO, OFERTA DA OPERADORA, PUBLICIDADE) com feedback Útil / Não útil / Ocultar. Objetivo de jogos: botão "Analisar um jogo específico" → [`modo-gamer.md`](modo-gamer.md).
- **Offline guiado:** stepper de 4 etapas na ordem do motor de conectividade: Gateway → DNS → Rota externa → Hostname/captive portal; para na primeira falha. Se a etapa DNS falha, testa também um resolvedor público (Cloudflare, DoH): se o público resolve e o da rede não, o motivo diz que o problema é o DNS da rede. Com essa evidência recomenda DNS público (provedor + IPs) via `OrientadorConfiguracaoDns` (ver [`dns-ping.md`](dns-ping.md)); não recomenda o que a rede já usa.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Resultado | Duas caixas: "DADOS MEDIDOS PELO SIGNALLQ" (fato medido, colorido por status) e "EXPLICAÇÃO DO RESULTADO" (IA) |
| Rodapé fixo | "A explicação ajuda a entender o resultado. A avaliação é feita com os dados medidos no seu aparelho." |
| Falha da IA (fluxo guiado) | "Não consegui carregar a explicação. O resultado acima continua válido." |
| Speedtest inválido | Pede refazer o teste na mesma rede |
| Entrada sem medição anterior (#1704) | Não exige speedtest prévio: abre na escolha do sintoma e mede sozinho na rota `Analise` antes de concluir. Pela entrada do resultado do speedtest reaproveita os dados recém-medidos |
| Medição parcial, contaminada, inconclusiva ou cancelada (#1705) | Cada um dos 5 valores de `MeasurementStatus` tem explicação própria e ação concreta, em vez de banner único sem saída |
| Confiança (#1707, spec 2.0 §14.4) | Rótulo em texto: "confiança alta/média/baixa", **nunca número** |
| Assist: NDS não responde (origem: FUNCIONAL, seção Início, antes da redução; não reconciliado com a linha de `nds_live` abaixo) | Erro explícito com nova tentativa, sem apresentar fallback local como resultado do Assist; resultado vem exclusivamente do NDS |
| Offline: etapa em curso / ok / falha | "Aguardando" → "Testando…" → "Concluído com sucesso" ou "Falhou", com motivo em linguagem direta |
| Offline: fim | "Diagnóstico concluído com falha" ou sucesso; "Tentar novamente" só se houve falha; "Concluir" |
| Gateway, rota externa e hostname falhando | Só explicam a causa; sem recomendação estruturada (dívida conhecida, issue #1819 fechada) |

### 5. Próximo passo e confirmação

Card de ferramenta, contato da operadora ou sugestão; "Testar novamente" (aparece quando a IA recomenda reteste, `AiAcaoRecomendada.tipo == "reteste"`) vinculado à mesma análise dispara medição nova de verdade e resolve em "Melhorou"/"Não mudou"/"Piorou"/"Comparação inconclusiva" (spec 2.0 §8.8/§14.6), nunca "recomeçar do zero" (outro CTA, em `ResultadoVelocidadeScreen`) e nunca compara redes diferentes sem aviso; gera comparação (`diagnostico_reteste_iniciado` → `diagnostico_comparacao_concluida`, veredito `melhorou|nao_mudou|piorou|inconclusiva`, só contra medição da mesma rede). Detalhes técnicos mostram dados medidos em rótulos comuns, o servidor usado e o equipamento de internet.

### 6. Fora de escopo, status e flag

Sem chat livre; a IA não substitui regra determinística. Status: entregue (Jornada Android Guiada 2.0, v1.0.9). Flag de rota: `consumer_diagnostico_enabled` (cobre diagnóstico local/laudo, guiado e detalhes técnicos).

**Combinação de flags que vale** (todas com default `true` no catálogo local; Remote Config pode desligar):

| Flag | Efeito |
|---|---|
| `consumer_diagnostico_enabled` | Liga/desliga a rota (Assist, Laudo, Detalhes técnicos) |
| `consumer_diagnostico_nds_live_enabled` | Fluxo principal usa o NDS como fonte; falha do NDS cai no motor local. Ligada, o shadow mode fica desligado |
| `consumer_diagnostico_shadow_mode_enabled` | Só atua com `nds_live` **desligada** (comparação local×remoto; não altera a UI) |
| `consumer_diagnostico_nds_v2_enabled` | Rota v2 do NDS no fluxo principal; **só tem efeito com `nds_live` ligada**; desligada volta ao v1 |
| `consumer_diagnostico_assist_nds_v2_enabled` | Rota v2 no caminho dedicado do Assist; independe de `nds_live` no código |

Padrão em produção: gate ligado + `nds_live` + `nds_v2` + `assist_nds_v2`; shadow inativo.

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `app/.../ui/screen/DiagnosticoGuiadoScreen.kt` + `DiagnosticoGuiado*Section.kt`, `DiagnosticoGuiadoAnalise.kt`, `DiagnosticoGuiadoEstado.kt` | Fluxo guiado (lista de objetivos, pergunta binária, processando, análise, resultado) |
| `app/.../ui/screen/AppShellDiagnosticoGuiadoOverlay.kt`, `AppShellDetalhesTecnicosOverlay.kt` | Overlays; gating em `AppShell.kt` |
| `app/.../ui/screen/DetalhesTecnicosScreen.kt` | Detalhes técnicos (sem IA) |
| `app/.../ui/component/DiagnosticoResultadoComponents.kt` | Caixas "Medido" / "Explicação" |
| `core/diagnostico/.../DiagnosticoGuiadoEngine.kt`, `ObjetivoDiagnostico.kt` | Avaliação por objetivo (determinística) |
| `feature/diagnostico/.../DiagnosticOrchestrator.kt` | `avaliarAssist` e `executarProtegido` (NDS live ou shadow) |
| `feature/diagnostico/.../nds/NdsDiagnosticRepository.kt` | `evaluate` e `evaluateForAssist` (v1/v2) |
| `feature/diagnostico/.../remote/DiagnosticDivergenceReporter.kt` | Shadow mode |
| `app/.../diagnosticooffline/` + `ui/component/DiagnosticoOfflineDialog.kt` | Offline guiado: `DiagnosticoOfflineExecutorReal`, ViewModel, diálogo full-screen |

### 8. Dados e contratos

NDS: `POST /v1/diagnostics/evaluate` e `POST /v2/diagnostics/evaluate` (envelope `{raw, explanation}`, contexto opcional). Contrato de repositório no `NDS-02k`/NDS (repositório próprio; contrato não documentado em `CONTRATOS/` — não verificado). Evidência separa fato medido, inferência determinística e interpretação de IA (AGENTS.md §8).

### 9. Eventos e flags

Eventos da jornada via `FirebaseAnalyticsTracker`; `diag_iniciado`/`diag_concluido` via `FirebaseAnalyticsHelper` (`DiagnosticOrchestrator`). `ia_laudo_*` são órfãos (sem call site). Parâmetros: `analytics-events.md`. `feature_blocked_remote` com `feature_id=diagnostico` quando a rota é bloqueada. Flags: tabela da seção 6; chaves em `FeatureFlagKeys.kt` e `consumer-catalog.json`.

### 10. Falhas e fallback

- **Fluxo principal:** NDS falha → motor local assume (`DiagnosticOrchestrator.executarProtegido`). Com `nds_live` ligada, `analisarProblema()` **não chama** `AiDiagnosisRepository.explainDiagnosis()` nem o `NdsClient` de novo: `resolverResultadoAnaliseViaNds` (`MainViewModel.kt`) deriva o resultado do mesmo `relatorio` via `AiFallbackFactory.fromLocal`, sem round-trip adicional. Detalhe do worker de IA, modelos e prompt: [`AI_FLOW.md`](../technical/AI_FLOW.md).
- **Assist:** sucesso exige resposta NDS remota; indisponibilidade é propagada à UI como erro explícito, **sem inventar resultado local** (KDoc de `avaliarAssist`). Isso difere do texto "Não consegui carregar a explicação" de `FUNCIONAL.md`, que descreve a prosa de IA; relação exata entre os dois **não verificada**.
- **Offline:** para na primeira falha; motor do offline (`DiagnosticoOfflineExecutorReal`) roda em paralelo ao motor de conectividade da medição guiada de Wi-Fi e do bloqueio de speedtest (duplicação, issue #1817).

**Diagnóstico guiado: medição própria e supressão de reações do shell** (movido de `technical/appshell-overlay-registry.md`):

- `AppShellDiagnosticoGuiadoEntry` tem um grupo `analise: AnaliseGuiadaContrato` (estado da medição derivado do snapshot do executor + `onIniciar`/`onCancelar`), separado de `dados` por ter outra origem (`ExecutorSpeedtest`, não os snapshots de diagnóstico). O fluxo abre sempre e mede por conta própria quando precisa; não depende de medição anterior.
- O `ExecutorSpeedtest` é global, então uma medição pedida pelo fluxo guiado é indistinguível de uma da tela Velocidade. O estado vive em `AppShellMedicaoGuiada.kt` (`rememberMedicaoGuiada`), fora do `AppShell`, para ser testável.
- O shell tem cinco reações ao executor; três são suprimidas por `suprimeReacoesDoShell`: `VelocidadeScreen` em tela cheia, `BackHandler` que descarta o erro e o empilhamento de `Overlay.ResultadoVelocidade` na conclusão. As outras duas (barra inferior some em `executando` via `shouldShowAppShellBottomBar`; Início reage via `Inicio2UiStateMapper.map`) só não atrapalham porque o overlay guiado as oclui. **Oclusão não é mecanismo:** se alguma ficar visível durante a medição guiada, entra na supressão.
- `rememberMedicaoGuiada` impõe um **limite de início**: `onNovoTeste` não garante medição (`MainViewModel.reiniciarSuite` tem dois `return` silenciosos: execução em andamento e Wi-Fi sem internet). Passado o limite sem ver `executando`, o estado vira `Falhou` ("Tentar de novo").
- `ResultadoIndisponivelScreen` segue em uso por `ResultadoVelocidade` e `DetalhesTecnicos`, que consomem o `ResultadoSpeedtest` inteiro.
- O back do fluxo guiado (interceptador, política que hoje devolve sempre `false`) está em `technical/appshell-overlay-registry.md`.

### 11. Testes

Lista em `testes:`. Cobertura do orquestrador/NDS em `feature/diagnostico/src/test` (ex.: `DiagnosticOrchestratorTest`), não listada no frontmatter.

### 12. Riscos

- Dois motores de sondagem independentes (offline × conectividade) — issue #1817.
- Combinação de flags: `nds_v2` ligada com `nds_live` desligada não tem efeito; operar por Remote Config exige conhecer essa dependência.
- Prosa de IA nunca pode virar decisão de status (regra de produto; sem teste dedicado verificado).
