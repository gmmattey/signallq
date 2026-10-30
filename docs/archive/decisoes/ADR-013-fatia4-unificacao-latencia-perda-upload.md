# ADR-013 — Fatia 4 de #1228: plano técnico para latência/perda de pacotes/upload (`MetricClassifier` vs. `InternetDiagnosticEngine`)

**Data:** 2026-07-31
**Status:** Aceito (arquitetura) — implementação de código bloqueada até decisão de produto (ver "Decisão pendente" abaixo)

## Contexto

A issue [#1466](https://github.com/buildea-labs/signallq/issues/1466) documenta uma divergência
real e já confirmada em produção (achado **P0-1** da auditoria completa de #1228,
`docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`, Parte 8): `MetricClassifier`
(tabela genérica, fonte `/regras-diagnostico-rede`) e `InternetDiagnosticEngine` (limiares de
produto documentados — Anatel RQUAL para latência, piso de videoconferência para upload, piso de
jogos/chamadas para perda de pacotes) têm réguas diferentes para as mesmas três métricas:

| Métrica | `InternetDiagnosticEngine` | `MetricClassifier` | Coincide? |
|---|---|---|---|
| Latência | achado a `>100.0 ms` (IN-NORMAL-05) | tabela `<100/<=150/<=200/>200` | Não |
| Perda de pacotes | achado a `>=1.0%`/`>=3.0%` (IN-NORMAL-07/07b) | tabela `<0.5/<=2.0/>2.0` | Não |
| Upload | achado a `<5.0 Mbps` (IN-NORMAL-04) | tabela `>=20/>=10/>=3/>=1/<1` | Não |

Confirmado por leitura de código nesta sessão (`InternetDiagnosticEngine.kt:51-196`): os três
achados **não foram migrados de propósito** (ADR-011/Fase 1), com comentário inline no próprio
arquivo apontando para #1466. Jitter/download/bufferbloat já migraram e coincidem exatamente com
`MetricClassifier` (fronteiras idênticas, ver #1466).

Confirmado também que **ambas as réguas são consumidas em produção hoje**, não é um caso de uma
delas ser código morto:

- `InternetDiagnosticEngine` alimenta `FindingEngine`/`ScoreEngine` → banner de diagnóstico,
  Diagnóstico Guiado, Score.
- `MetricClassifier.classificarLatencia/classificarPerdaPacotes` são chamados diretamente por
  `ModoGamerEngine.kt` (10 pontos de chamada, dimensões "Tempo de resposta com a rede ocupada" e
  "Falhas estimadas na conexão") e pelos cards de métrica de `ResultadoVelocidadeScreen.kt` — a
  mesma tela cujo banner vem do engine, daí o P0-1 ser visível **na mesma tela, ao mesmo tempo**.

## Por que isto não é só duplicação de código

As duas réguas não são a mesma regra implementada duas vezes (ao contrário de bufferbloat/P1-4,
já resolvido na Fatia 6). São **vocabulários estruturalmente diferentes**:

- `InternetDiagnosticEngine` produz **achados binários/ternários** (`DiagnosticStatus`: ok/attention/
  critical) com um único corte de negócio por métrica — desenhado para responder "isto merece
  virar um card de alerta?".
- `MetricClassifier` produz uma **classificação de qualidade em 6 níveis** (`MetricStatus`:
  excelente/bom/regular/ruim/crítico/inconclusivo) — desenhado para responder "que nota esta
  métrica tira, isoladamente?".

Colapsar os dois em um único conjunto de números, sem antes decidir o que cada consumidor
realmente precisa (achado vs. nota), arrisca perder a semântica de um dos dois lados mesmo que os
números batam.

## Decisão pendente (produto, fora do escopo desta ADR)

Não decidido nesta ADR — depende de validação de produto (Claudete/Luiz) antes de qualquer PR de
código, exatamente como a issue #1466 já registrava:

1. Aceitar a divergência como definitiva e documentá-la como comportamento pretendido (duas réguas,
   dois propósitos).
2. Fazer um lado prevalecer sobre o outro numericamente (ex.: `MetricClassifier` passa a usar os
   cortes do engine para estas 3 métricas) — muda comportamento observável de quem hoje lê
   `MetricClassifier` (`ModoGamerEngine`, cards do Resultado).
3. Manter os dois vocabulários, mas expressos como perfis nomeados do mesmo motor canônico (ver
   "Decisão de arquitetura" abaixo) — não resolve sozinho qual card muda de cor, só remove a
   duplicação de implementação.

Esta ADR **não escolhe entre 1/2/3 no nível de valores numéricos**. O que ela decide é a forma
técnica que qualquer uma dessas opções vai assumir no código, para que a implementação (Fatia 4b)
possa começar assim que a decisão de produto acima for tomada — sem redesenho de arquitetura no
meio do caminho.

## Decisão de arquitetura — perfis de classificação nomeados dentro de `MetricClassifier`

`MetricClassifier` ganha um conceito explícito de **perfil de regra** por métrica, em vez de uma
única tabela implícita. Formato proposto (nomes sujeitos a ajuste na implementação real, não
compromissados aqui):

```kotlin
enum class PerfilRegraLatencia { QUALIDADE_GENERICA, ACHADO_PRODUTO_INTERNET }

fun classificarLatencia(
    latenciaMs: Double,
    perfil: PerfilRegraLatencia = PerfilRegraLatencia.QUALIDADE_GENERICA,
): MetricStatus
```

- `QUALIDADE_GENERICA` preserva a tabela atual (6 níveis, fonte skill) — comportamento de hoje,
  sem mudança, é o default (nenhum chamador existente precisa mudar assinatura).
- `ACHADO_PRODUTO_INTERNET` expõe os cortes hoje hardcoded em `InternetDiagnosticEngine`
  (100ms/1%/3%/5Mbps) através da MESMA função pública, mapeados para o vocabulário de 6 níveis
  mais próximo (ex.: corte único de achado vira fronteira `bom`/`regular` — mapeamento exato é
  decisão de implementação da Fatia 4b, não desta ADR).
- `InternetDiagnosticEngine` passa a chamar `MetricClassifier.classificarLatencia(lat,
  PerfilRegraLatencia.ACHADO_PRODUTO_INTERNET)` em vez de reimplementar o corte inline — elimina a
  duplicação técnica (o motivo original de #1228) sem forçar as duas réguas de produto a virarem
  uma só.
- Repetir o mesmo padrão para perda de pacotes e upload (`PerfilRegraPerdaPacotes`,
  `PerfilRegraUpload`).

Por que perfil nomeado em vez de um segundo classificador separado (ex. `core/classification`
proposto no corpo original de #1228): os valores realmente pertencem ao mesmo domínio (mesma
métrica bruta, mesma unidade), só divergem no **corte**. Um enum de perfil no mesmo arquivo é menor
superfície de manutenção que duplicar toda a estrutura de `MetricClassifier`/`MetricStatus` — e
mantém válido o princípio "uma métrica, uma função de classificação" (ela só passa a aceitar
parâmetro de perfil, não deixa de ser uma função só).

## Rollout proposto (não iniciado)

- **Fatia 4a (baixo risco, não bloqueada por decisão de produto):** implementar o enum de perfil +
  `ACHADO_PRODUTO_INTERNET` espelhando exatamente os cortes atuais do engine, migrar
  `InternetDiagnosticEngine` para chamar `MetricClassifier` com esse perfil. **Zero mudança
  observável** — mesmo padrão já usado em jitter/download/bufferbloat (Fase 1/ADR-011). Testes
  dourados de `InternetDiagnosticEngineTest.kt` continuam verdes sem alteração de asserção.
- **Fatia 4b (bloqueada pela decisão de produto acima):** decidir se `ModoGamerEngine` e os cards
  de `ResultadoVelocidadeScreen` continuam em `QUALIDADE_GENERICA` (comportamento atual mantido,
  opção 1 formalizada) ou migram para `ACHADO_PRODUTO_INTERNET` (opção 2, muda cor/rótulo visível
  nesses dois consumidores). Esta fatia só começa depois de resposta explícita de Claudete/Luiz na
  issue #1466.

## Consequências

- Nenhum threshold muda nesta ADR. Nenhum arquivo de produção é tocado.
- #1466 permanece aberta — esta ADR só documenta o plano técnico e destrava a Fatia 4a (que não
  depende de decisão de produto) assim que for priorizada.
- A Fatia 4b continua explicitamente bloqueada até decisão de produto, conforme já registrado na
  Parte 9 da auditoria (`AUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`) e no item 8 do fechamento
  desta rodada.

## Referências

- Issue [#1466](https://github.com/buildea-labs/signallq/issues/1466)
- Issue [#1228](https://github.com/buildea-labs/signallq/issues/1228)
- `docs/archive/decisoes/ADR-011-fase0-motor-canonico-diagnostico.md`
- `docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`, achado P0-1 e Fatia 4
