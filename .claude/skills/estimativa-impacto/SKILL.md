---
name: estimativa-impacto
description: Avalia tamanho, risco, dependências e necessidade de gate arquitetural antes da execução de uma mudança do SignallQ.
argument-hint: "<issue-ou-descrição>"
---

# Estimativa de impacto

Use antes de decompor feature média/grande ou quando não estiver claro se uma mudança pode seguir no fluxo comum.

Não use datas ou milestones hardcoded como fonte de verdade; consulte o estado atual do projeto/GitHub quando prazo for relevante.

## 1. Tamanho

Classifique pelo impacto real:

- **Pequena** — uma responsabilidade local, reversível, sem contrato externo.
- **Média** — mais de uma área local ou comportamento relevante, mas dentro da arquitetura existente.
- **Grande** — mudança ampla, migração, fluxo crítico ou alto custo de regressão.
- **Sistêmica** — aciona pelo menos um gate arquitetural do `AGENTS.md`.

Contagem de arquivos/módulos é sinal, não regra automática.

## 2. Risco

Avalie:

- critério de aceite está claro?
- comportamento atual foi confirmado no código?
- toca diagnóstico central/speedtest?
- toca Room, persistência ou migration?
- toca Worker/API/contrato?
- muda permissão/API Android/background?
- altera threshold, métrica ou cálculo?
- mexe em dado sensível/autenticação?
- cria custo recorrente?
- depende de outro repositório/issue?
- existe teste que caracteriza o comportamento?

Classifique `Baixo`, `Médio`, `Alto` ou `Crítico` e cite evidências.

## 3. Gate Camillo

Marque `SIM` quando houver qualquer gatilho arquitetural do `AGENTS.md`, incluindo API, integração, contrato compartilhado, mudança estrutural entre módulos, migração sistêmica, novo serviço ou segurança sistêmica.

Se `SIM`, a implementação espera Architecture Plan/revisão do Camillo.

## 4. Especialistas prováveis

- produto/jornada: Marcelo;
- UX/UI e Design System: Priscila;
- Android, diagnóstico/Workers e arquitetura sistêmica: Camillo;
- qualidade: Renan.

Liste apenas quem realmente agrega valor.

## Saída

```text
IMPACTO: Pequena|Média|Grande|Sistêmica
RISCO: Baixo|Médio|Alto|Crítico
GATE CAMILLO: SIM|NÃO — motivo
RESPONSÁVEIS: ...
RECOMENDAÇÃO: explorar | refinar | executar | arquitetar antes | adiar

Riscos ativos:
- ...
Dependências:
- ...
```

Esta skill estima e roteia; não decide prioridade estratégica, custo novo ou publicação em nome do Luiz.
