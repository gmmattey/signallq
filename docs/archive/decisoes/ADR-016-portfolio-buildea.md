---
title: "ADR-016 — Portfólio Buildea: SignallQ e Linka como produtos comerciais separados"
description: "Consolida e remove os antigos ADR-006, ADR-007, ADR-010, ADR-014, ADR-015. Declara SignallQ (Android+Web, freemium/ads) e Linka (Apple, pago) como os únicos produtos ativos do portfólio Buildea; descontinua Pro, ISP, Nethal e derivados."
type: "adr"
status: "ativo"
owner: "Luiz (CEO)"
last_updated: "2026-08-15"
version: "1.0.0"
---

# ADR-016 — Portfólio Buildea: SignallQ e Linka

- **Status:** Aceito
- **Data:** 2026-08-15
- **Autor:** Luiz (CEO)
- **Consolida:** ADR-010 (monetização consumer grátis/ads), ADR-014 (squad canônico em `ai-governance/agents/`), ADR-015 (plataformas Android+Web). Todos removidos do disco na Fase 3 do épico #1623 em 2026-08-15 — git preserva por SHA.
- **Reforça:** ADR-007 (iOS adiado — também consolidado neste ADR na Fase 3).

## Contexto

O portfólio Buildea acumulou hipóteses e produtos derivados ao longo do tempo:

- **SignallQ Consumer** — Android + PWA em `signallq-web`, plataforma canônica.
- **SignallQ Pro** — 13 módulos Gradle `:pro:*`, on hold desde 2026-08-06.
- **SignallQ ISP** — nome tentativo, nunca chegou a produto.
- **Nethal** — mencionado em decisão de consolidação 7ALabs como "segundo produto possível", nunca definido.
- **iOS** — descontinuado em 2026-07-04 (ex-ADR-007, hoje consolidado aqui), reforçado como permanente.
- **Múltiplos ADRs de squad, monetização e plataforma** — divergentes, com sobreposições.

Sem consolidação, cada retomada de discussão ("e se voltarmos com o Pro?", "e o Nethal?", "e o desktop?") reabre esforço estratégico em cima de algo que já foi implicitamente decidido. Este ADR fecha o portfólio.

## Decisão

### Dois produtos ativos, comerciais, sob o guarda-chuva Buildea

**Buildea** é o guarda-chuva comercial. Não é plataforma técnica nem stack — é a entidade que comercializa e opera os produtos.

**SignallQ**
- **Plataformas:** Android (nativo, Kotlin/Compose) + Web (`signallq.com`, PWA em `signallq-web`).
- **Modelo comercial:** freemium com propaganda. Funcionalidade principal (diagnóstico de rede doméstica, IA de diagnóstico, monitoramento passivo) gratuita e sustentada por ads. Recursos pagos podem entrar no futuro sem quebrar a promessa de gratuidade do núcleo.
- **Público:** usuário doméstico brasileiro.
- **Repositórios:** `signallq` (Android + Workers Cloudflare), `signallq-web` (PWA).

**Linka**
- **Plataformas:** exclusivamente ecossistema Apple (iOS, iPadOS, macOS conforme escopo do produto — não Android, não Web-agnóstico).
- **Modelo comercial:** pago desde o lançamento. Speedtest é o único recurso free (isca de aquisição). Todo o resto é pago.
- **Público:** usuário Apple que quer diagnóstico de rede profissional.
- **Repositório:** `linka` (a ser criado — foundation preparada na Fase 8 do épico #1623).

Os dois produtos **compartilham** a marca-guarda-chuva Buildea e potencialmente o entendimento de domínio (diagnóstico de rede, thresholds, vocabulário), mas **não compartilham código, squad, skills ou governança operacional**. Cada um é fundação própria.

### Squads separadas

Cada produto tem sua própria squad, que vive no seu próprio repositório. Não há squad Buildea unificada. A padronização se dá por **contratos operacionais** compartilhados (ex.: formato de handoff, definition of done), não por pessoas.

Composição do squad SignallQ (definida na Fase 1 do épico #1623):

| Papel | Escopo |
|---|---|
| **Claudete** (PM) | Produto, prioridade, roadmap, absorve direção de design/growth via skills |
| **Camilo** (Dev) | Android + Web + Workers + Admin — dev técnico único |
| **Caio** (Revisor) | Gate único de revisão independente |

Design, growth e dados viram **skills invocáveis** (`/design-check`, `/growth-check`, `/analytics-spec`), não agentes permanentes.

Composição do squad Linka: definida quando o repo nascer (Fase 8), com personas próprias — não é replicação do squad SignallQ.

### Personas dos agentes

Cada agente tem personalidade escrita: tom, palavrão sim/não, matriz de decisão trivial vs escalar, modelo default (Sonnet padrão; Opus para arquitetura/segurança/roadmap; Haiku para trivial/triagem). Objetivo: **melhor custo × benefício em velocidade, custo e qualidade**.

Agentes se dirigem ao Luiz como humanos, sem formalidades. Palavrão, piada, informalidade são permitidos conforme a personalidade — não são obrigatórios nem proibidos.

### Autonomia

**Agentes decidem sozinhos** todas as questões técnicas dentro do domínio deles: arquitetura de feature, refactor até um módulo, dependência nova dentro do orçamento, escolha de padrão, spec de teste.

**Luiz decide** questões de produto (roadmap, priorização macro, monetização, marca) e as aprovações materiais do [contrato operacional Buildea §3](../../../../ai-governance/policies/agent-operating-contract.md): estratégia, marca, escopo material, orçamento, lançamento, custo recorrente novo, dado sensível, alteração irreversível, compromisso externo, aceite de risco crítico.

**Decisões triviais de produto** (ex.: ajustar copy de card, escolher entre dois ícones equivalentes, decidir ordem de exibição de menu) ficam com Claudete sem escalar.

### Governança anti-duplicação

Prevenir código duplicado e reinvenção via **skill executável obrigatória** antes de criar código novo:

- **`/inventario`** — lê `settings.gradle.kts`, catálogo de módulos, exports públicos. Retorna o que já existe.
- **`/verificar-modulo <nome>`** — antes de criar módulo/serviço/componente novo, força justificativa se algo parecido existe.
- **`/check-done`** (piloto #1620 promovido a oficial) — enforcement final antes de merge.

Regra dura mantida: `:feature:*` nunca depende de `:feature:*`. Composição vive em `:core:*` compartilhado ou `:app`. Prioridade **módulos + compartilhamento de função** — nunca monolítico.

### Produtos descontinuados

Ficam **permanentemente fora** do portfólio Buildea:

- **SignallQ Pro** — 13 módulos `:pro:*` a serem removidos (Fase 4a do épico #1623); docs `pro-onhold/` removidos (Fase 4b); skill `signallq-pro-design` removida (Fase 4b).
- **SignallQ ISP** — nunca foi produto; nome descartado.
- **Nethal** — nunca foi definido; descartado.
- **Qualquer plataforma que não seja Android/Web (SignallQ) ou Apple (Linka)** — iOS via SignallQ, macOS/Windows/Linux desktop, wearables, TV, embedded, extensões de navegador. Reforço permanente do que os antigos ADR-007 e ADR-015 já diziam (ambos consolidados aqui).

Se algum dia essas ideias voltarem, nascem em ADR próprio que supersede este.

## Consequências

### Imediatas

- ADR-010, ADR-014, ADR-015 — **consolidados** neste ADR. Removidos do disco na Fase 3 do épico #1623 em 2026-08-15; git preserva.
- ADR-007 (iOS adiado) — **consolidado** neste ADR. Removido do disco na mesma Fase 3.
- ADR-006 (workflow squad de 5) — já era superseded desde 2026-08-15; removido do disco na Fase 3.

### Épico #1623 destranca

- Fase 1 (novo squad de 3 agentes com personalidade) pode começar.
- Fase 3 (consolidar ADRs) pode remover 010, 014, 015 sem re-litigar decisão.
- Fase 4a-b (remover Pro) tem base canônica para justificar remoção.
- Fase 8 (foundation Linka) tem escopo declarado.

### Cross-repo (follow-ups, não bloqueiam este ADR)

- `signallq-web` — AGENTS.md deve citar ADR-016 e declarar-se como o PWA do SignallQ. PR separada lá.
- `ai-governance/` — política `policies/product-platforms.md` opcional, referenciando este ADR se o Luiz quiser centralizar em nível org.
- `linka` (repo novo) — quando nascer, primeiro ADR local referencia este.

### O que este ADR NÃO decide

- Stack de implementação do Web (`signallq-web` continua Next.js/PWA por decisão daquele repo).
- Distribuição do Android (Play Store hoje; F-Droid/sideload são decisões separadas).
- Timing de recursos pagos do SignallQ (produto ainda operando 100% free/ads em 2026-08).
- Timing de lançamento do Linka (Fase 8 só prepara foundation).

## Reabertura

Este ADR só é reaberto por novo ADR do Luiz que o supersede. Sem novo ADR:

- Proposta de retomar Pro/ISP/Nethal/derivado → rejeitada de plano; requer business case direto ao Luiz antes de qualquer trabalho técnico.
- Proposta de nova plataforma (desktop, wearable, TV, embedded) → rejeitada de plano; mesma regra.
- Proposta de mudar modelo comercial (SignallQ vira pago, Linka vira freemium) → escalação obrigatória ao Luiz.
