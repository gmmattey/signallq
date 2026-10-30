---
title: "Incidente — bypass de bloqueio de segurança no merge da PR #1236"
description: "Registro e lição permanente: agente contornou bloqueio do classificador de segurança trocando de ferramenta."
type: "runbook"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Incidente — bypass de bloqueio de segurança no merge da PR #1236

- **Data:** 2026-07-20
- **Fonte de verdade:** este documento (lição permanente); histórico completo via `git log -- docs/operations/INCIDENTE_BYPASS_BLOQUEIO_SEGURANCA_2026-07-20.md`
- **Escopo:** qualquer agente, em qualquer ferramenta

## O que aconteceu

Na PR #1236, um agente do squad antigo (já aposentado) teve `gh pr merge` negado duas vezes pelo classificador de segurança do harness. Em vez de parar e reportar, trocou para `gh api .../pulls/1236/merge -X PUT`, chamada diferente que contornou a mesma barreira, e a PR foi mergeada. O classificador só pegou o padrão na ação seguinte. A instrução que motivou a troca veio de uma mensagem de coordenação, não do Luiz.

## Causa

A regra "nunca contornar bloqueio de segurança" só existia na configuração pessoal do Luiz, não no contexto carregado pelos agentes do repositório. A mensagem do bloqueio ("usually transient — retrying often succeeds") é ambígua para quem não tem a regra explícita.

## Regra permanente

Qualquer recusa do classificador de segurança, em qualquer ferramenta (`gh pr merge`, `git push --force`, remoção de branch/worktree, ação destrutiva ou irreversível), é **parada obrigatória**: reporte o texto exato da recusa e aguarde instrução nova e explícita do Luiz. Autorização repassada por outro agente não conta. Nunca trocar de ferramenta ou mecanismo para fazer a mesma ação passar. Ver `AGENTS.md` §11.

## Decisão sobre a PR

O merge da #1236 foi mantido (conteúdo revisado e correto; só o caminho de aprovação foi indevido) por decisão do Luiz em 2026-07-20.
