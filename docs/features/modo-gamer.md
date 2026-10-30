---
title: "Jogos online (Modo gamer)"
description: "Avalia se a conexão serve para um jogo e aparelho específicos: catálogo, veredito, sonda UDP de rota, estados, código e testes."
type: "feature"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "1.0.0"
feature: "modo-gamer"
tipo: "jornada"
modulos:
  - "android/core/diagnostico"
  - "android/core/probejogo"
  - "android/feature/speedtest"
  - "android/core/datastore"
  - "android/app"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ModoGamerScreen.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/ModoGamerConfigResultadoSection.kt"
  - "android/app/src/main/kotlin/io/signallq/app/modogamer/"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/ModoGamerEngine.kt"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/ModoGamerMedicaoElegibilidade.kt"
  - "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/GameReadinessClassifier.kt"
  - "android/core/probejogo/src/main/kotlin/io/signallq/app/core/probejogo/SondaGameLiftBeacon.kt"
contratos: []
eventos:
  - "screen_view (screen_name=modo_gamer)"
flags: []
testes:
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/ModoGamerEngineTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/ModoGamerConvergenciaCaracterizacaoTest.kt"
  - "android/core/diagnostico/src/test/kotlin/io/signallq/app/core/diagnostico/ModoGamerMedicaoElegibilidadeTest.kt"
  - "android/core/probejogo/src/test/kotlin/io/signallq/app/core/probejogo/SondaGameLiftBeaconTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/modogamer/ModoGamerViewModelTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/ModoGamerConfigResultadoSectionNativeAdTest.kt"
adrs: []
thresholds_em: "android/core/diagnostico/src/main/kotlin/io/signallq/app/core/diagnostico/ModoGamerEngine.kt"
---

# Jogos online (Modo gamer)

Migrado de `FUNCIONAL.md` §5.11 e `ARQUITETURA/MODULOS/core-probejogo.md`. Conferido no código em 2026-10-04.

## Negócio

### 1. Problema e promessa

O jogador não quer Mbps genérico; quer saber se dá para jogar **aquele jogo naquele aparelho**. Promessa do hub: "Veja se sua conexão pode causar atrasos". É o **único** fluxo de jogos do app: a `JogosScreen` legada foi removida em 2026-07-26 (issue #1487) e fundida aqui — não recriar um segundo fluxo paralelo.

### 2. Quando aparece e para quem

Três entradas para a mesma tela: card "Modo Jogos" em Ferramentas, CTA no resultado do teste de velocidade, botão "Analisar um jogo específico" no resultado do diagnóstico guiado (objetivo "Jogos atrasam ou travam"; ver [`assist-diagnostico.md`](assist-diagnostico.md)). Para o jogador.

### 3. Regras de decisão

- **Etapa 1 — jogo:** busca em catálogo fechado de **21 jogos** (`ModoGamerEngine.kt`; battle royale, FPS competitivo, MOBA, casual). Jogo fora da lista nunca vira erro: "Meu jogo não está na lista" leva a **6 categorias genéricas**.
- **Etapa 2 — aparelho:** sete opções (PS5/PS4, Xbox, PC, Android, iPhone, Switch, TV/Cloud gaming). É contextual — **não altera os limiares do motor**.
- **Etapa 3 — salvar:** "Salvar para os próximos testes" (marcada por padrão) ou "Usar apenas agora". Com padrão salvo, as próximas aberturas pulam direto ao resultado. Medição extra opcional "Medir o tempo de resposta agora", que não bloqueia o fluxo.
- **Medição de rota:** sonda UDP real contra o beacon AWS GameLift (`SondaGameLiftBeacon`, v1.0.9, #1902) para todo jogo do catálogo; falha (UDP bloqueado, DNS ou rede) → fallback HTTPS. Catálogo sem host dedicado por jogo (#1904). É rota regional de referência, **nunca o servidor do jogo**.
- **Convergência com o objetivo guiado (#1667):** `DiagnosticoGuiadoEngine.avaliarJogosComLag` e os perfis do Modo gamer priorizam as mesmas três métricas (tempo de resposta, variação, falhas) pela mesma função `dimsLatenciaJitterPerda` em `DiagnosticoGuiadoEngine.kt`. Limiares só no arquivo apontado em `thresholds_em`.

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Veredito | Headline direta: "Bom pra jogar" / "Pode ter atrasos" / "Não recomendado" / "Sem dados suficientes" (decisão do Luiz, 2026-08-19, #1667) |
| Resultado | Banner de status + bloco "Medido pelo SignallQ / Explicação por IA" + "O que fazer agora" (ações do motor) |
| Jogo veio do fallback | Aviso amarelo declara isso |
| Medição extra pedida | Linha informativa sobre conexão direta com outros jogadores (NAT UDP) — **só informativa, nunca rebaixa o veredito** |
| Fim | Faixa confirma se a escolha virou padrão ou foi usada só desta vez |
| Sonda UDP sem eco | `null` por amostra; timeout nunca vira sucesso nem RTT fabricado; cai no HTTPS |

### 5. Próximo passo e confirmação

"O que fazer agora" com as ações do motor; refazer a medição para confirmar. Padrão salvo (jogo + aparelho) acelera o reteste.

### 6. Fora de escopo, status e flag

Não mede o servidor do jogo nem promete "ping do jogo". Status: entregue. Flag: **nenhuma** — `TipoFerramenta.MODO_JOGOS` é `true` fixo em `AppShellFerramentasRoot.kt`. Há slot de anúncio nativo `JOGOS` no resultado, com contrato central em `FUNCIONAL.md` §7.0 (não migrado aqui).

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `app/.../ui/screen/ModoGamerScreen.kt` | Tela das etapas |
| `app/.../ui/screen/ModoGamerConfigResultadoSection.kt` | Resultado, medição extra, tenta sonda UDP e cai no HTTPS |
| `app/.../modogamer/ModoGamerViewModel.kt`, `ModoGamerCatalogoUi.kt` | Estado do fluxo, padrão salvo, catálogo na UI |
| `core/diagnostico/.../ModoGamerEngine.kt` | Catálogo (21 jogos) e avaliação por perfil |
| `core/diagnostico/.../ModoGamerMedicaoElegibilidade.kt`, `GameReadinessClassifier.kt` | Elegibilidade da medição e classificação |
| `core/probejogo/.../SondaGameLiftBeacon.kt` | Cliente UDP (`sondar(amostras)`, timeout padrão 2000 ms), payload de 33 bytes, só eco íntegro conta |
| `feature/speedtest/.../PingExecutor.kt`, `AnalisadorAmostragemPing` | Fallback HTTPS; estatística (mediana/jitter/perda) |

`:core:probejogo` não tem dependência de projeto (de propósito); consumidor: `:app`. Host/porta do beacon vêm de `BuildConfig.GAMELIFT_BEACON_HOST`/`_PORT` no `:app`.

### 8. Dados e contratos

Padrão salvo: `ModoGamerPadraoPersistido` (`core/datastore`; jogoId ou categoriaFallback + deviceId), gravado via `onSalvarModoGamerPadrao` em `AppShell.kt`. Sem contrato OpenAPI. A medição base vem de `medicaoBaseModoGamer` (MainViewModel).

### 9. Eventos e flags

`screen_view` com `screen_name=modo_gamer` (`TipoFerramenta.kt`). Eventos específicos do Modo gamer: **nenhum encontrado** no código. Sem flag.

### 10. Falhas e fallback

UDP bloqueado, DNS ou rede → fallback HTTPS (`PingExecutor`). Amostra sem eco válido = `null`, nunca zero. Jogo ausente do catálogo → categoria genérica com aviso.

### 11. Testes

Lista em `testes:`. `ModoGamerConvergenciaCaracterizacaoTest` protege a convergência com o diagnóstico guiado.

### 12. Riscos

- **Beacon de terceiro (AWS):** disponibilidade e política fora do controle; UDP bloqueado cai no fallback.
- **Rota de referência ≠ rota do jogo:** limite de promessa — não apresentar como "ping do jogo".
- Convergência com o diagnóstico guiado depende de uma função compartilhada; mudar limiar exige revisão de Marcelo.
- Dois consumidores de estatística de ping (sonda UDP e `PingExecutor`) — manter o mesmo analisador.
