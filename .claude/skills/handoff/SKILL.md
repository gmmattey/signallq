---
name: handoff
description: Registra passagem formal de contexto entre responsáveis do SignallQ quando uma transição precisa sobreviver à sessão.
argument-hint: "<issue> --de <claudete|rian|marcelo|tiago|camillo|luiz> --para <...> --decisao \"<texto>\""
allowed-tools: Bash(gh *), Bash(git *)
---

# Handoff

Use somente quando há troca real de responsável, decisão que condiciona a próxima etapa, risco/pendência relevante ou Architecture Plan a ser consumido por outro especialista.

Não use para tarefa simples que o Codex principal consegue integrar sem cerimônia.

## Responsáveis válidos

`claudete` · `rian` · `marcelo` · `tiago` · `camillo` · `luiz`

O roteamento e a autoridade de cada um vivem em `AGENTS.md`; esta skill não redefine papéis.

## Pre-flight

Antes de registrar:

```bash
git status --short
git branch --show-current
gh issue view <issue> --repo buildea-labs/signallq --json state,title
gh pr list --repo buildea-labs/signallq --search "head:$(git branch --show-current)" --json number,url,state
```

Se o handoff disser que código está disponível, o estado precisa estar commitado e acessível na branch/PR. Não poste handoff mentiroso sobre alteração que só existe no working tree.

## Conteúdo mínimo

- **De / para**
- **Decisão ou objetivo**
- **Arquivos/módulos relevantes**
- **Validações realmente executadas**
- **Pendências**
- **Riscos/limitações**
- **Branch/PR/issue**
- **Architecture Plan**, se houver gate do Camillo

Exemplo:

```text
De: Marcelo → Para: Camillo
Decisão: a feature deve diagnosticar falha de DNS sem rodar novo speedtest.
Escopo: featureDiagnostico + contrato do diagnostic-worker.
Validações: análise de produto; sem código nesta etapa.
Pendências: definir contrato de evidências e fallback offline.
Riscos: mudança atravessa app↔Worker; gate arquitetural obrigatório.
Referência: issue #NNNN · .agents/architecture-plan.md
```

## Regras

- Não invente aprovação ou validação de outro agente.
- Não use handoff para transferir decisão de produto de Marcelo/Luiz para engenharia.
- Não use handoff para bypassar o gate de Camillo.
- Renan permanece independente de quem implementou.
- Handoff para Luiz ocorre apenas quando existe decisão humana real: produto material, custo, publicação, privacidade sensível, risco crítico ou ação irreversível.

A skill registra contexto; não faz merge, deploy, publicação, mudança de assignee ou aceite de risco.
