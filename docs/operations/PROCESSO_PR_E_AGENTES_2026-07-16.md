---
title: "Disciplina de PR, branch e dispatch de agentes"
description: "Regras duráveis de batching de PRs, sequenciamento, limpeza de branches e proteção de main, extraídas da revisão de 2026-07-16."
type: "runbook"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Disciplina de PR, branch e dispatch de agentes

- **Fonte de verdade:** `AGENTS.md` §11 e `.agents/WORKFLOW.md`; regras de higiene em `.claude/rules/higiene-e-padronizacao-repositorio.md`
- **Escopo:** abertura de PRs, branches e delegação a subagentes
- **Histórico:** a versão de 2026-07-16 (diagnóstico numérico e avaliação do squad antigo Claudete/Camilo/Juninho, aposentado) foi condensada; o texto integral está em `git show f6f9d437:docs_ai/operations/PROCESSO_PR_E_AGENTES_2026-07-16.md`

## Origem

Em 2026-07-15/16, uma sessão gastou 11 dispatches (>1M tokens) em ~6 frentes e abriu PRs isoladas para achados que deveriam entrar em trabalho já em andamento; havia 74 branches remotas já mergeadas e nunca apagadas. O problema era comportamental (dispatch reativo, um agente/branch/PR por achado), não de elenco.

## Regras

1. **Batching.** Antes de abrir agente ou PR novos, verifique se já existe branch/worktree/PR ativa na mesma área. Se sim, o achado entra ali (mesmo commit ou o seguinte). PR isolada só com urgência de produção distinta, rollback independente ou domínio/revisor diferente.
2. **Sequenciamento.** Se a tarefa B depende de A ainda não mergeada, espere (`gh pr view <N> --json state,mergedAt`).
3. **Limpeza faz parte de fechar a tarefa.** Todo merge usa `--delete-branch`; worktree criada por agente é removida ao fechar. Confirme ancestralidade com `git branch -r --merged origin/main` antes de apagar em lote.
4. **Isolamento.** Subagente que roda checkout, build ou teste local trabalha em worktree dedicada, nunca no diretório principal.
5. **Delegação proporcional.** Trabalho mecânico ou de levantamento não precisa de agente pesado; ajuste esforço ao tipo de tarefa.
6. **`strict` desligado em `required_status_checks` de `main`** (decisão de 2026-07-15, sem Merge Queue disponível em conta pessoal): a PR mergeia quando os checks passam na própria branch. Conferido via `gh api .../required_status_checks` em 2026-10-04 (`strict: false`; contexts Detekt, Ktlint, Unit Tests); `auto-update-branch.yml` mantém PRs atualizadas. Reavalie se houver Merge Queue.
