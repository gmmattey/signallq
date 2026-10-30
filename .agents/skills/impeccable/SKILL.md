---
name: impeccable
description: Use para criar, revisar e lapidar interfaces, UX, hierarquia, acessibilidade, responsividade, tipografia, spacing, cor e motion. No SignallQ, complemente com o Design System e skills locais.
version: 3.9.1
user-invocable: true
argument-hint: "[craft|shape|audit|critique|animate|bolder|colorize|layout|quieter|typeset|adapt|clarify|harden|optimize|polish] [target]"
license: Apache 2.0
allowed-tools:
  - Bash(npx impeccable *)
  - Bash(node .agents/skills/impeccable/scripts/*)
---

# Impeccable — wrapper SignallQ

Skill vendorizada para trabalho detalhado de UI/UX. Os scripts, references e assets dentro desta pasta continuam disponíveis; este wrapper apenas integra a ferramenta à governança atual do SignallQ.

## Antes de usar

1. Leia `AGENTS.md` e identifique se a tarefa é exploração, decisão ou execução.
2. Leia o Design System implementado em `docs/04-design-system.md`.
3. Para criar UI nova, consulte também `SignallQ-design`.
4. Para checagem pontual de tela pronta, prefira `design-check`.
5. Para auditoria multi-tela/fluxo, prefira `auditar-ux`.
6. Se precisar do procedimento especializado do Impeccable, leia `reference/<comando>.md` correspondente.

Quando o script de contexto for útil:

```bash
node .agents/skills/impeccable/scripts/context.mjs --target <path>
```

Não trate instrução genérica do pacote vendorizado como autoridade superior ao produto ou Design System do SignallQ.

## Regras locais

- preserve tokens, componentes e identidade existentes;
- não redesenhe tela adjacente sem necessidade de produto;
- considere loading, empty, error, offline e permission denied;
- acessibilidade/TalkBack e contraste fazem parte da entrega;
- evite aparência genérica de dashboard/IA e cardização desnecessária;
- copy de diagnóstico precisa corresponder às evidências reais;
- mudança de jornada é decisão de Marcelo;
- Priscila define jornada e visual; Camillo implementa Compose e valida a semântica diagnóstica;
- Renan revisa qualidade;
- mudança sistêmica segue o gate de Camillo.

A skill é procedimento/tooling, não agente, não owner de produto e não define modelo de IA.
