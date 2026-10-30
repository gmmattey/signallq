---
title: "Arquivo histórico — ponteiro para o git"
description: "O que ainda vive em archive/ (decisões originais e governança de squads anteriores) e como recuperar o que foi removido"
type: "índice"
status: "ativo"
owner: "Squad"
last_updated: "2026-10-30"
---

# Arquivo histórico

**Esta pasta não recebe documentos substituídos.** Documento substituído é removido; o git é o arquivo.
Só permanecem aqui:

- `decisoes/` — os ADRs originais, preservados para auditoria. A fonte viva é [`../DECISOES.md`](../DECISOES.md).
- `ai-governance/` — definições de squads anteriores, histórico de governança.

Os 100 documentos que viviam em `_archive/` foram removidos da árvore em 2026-08-06. Nada foi perdido.

## Por que foram removidos

Documentação arquivada no sistema de arquivos duplica o que o controle de versão já faz, e cobra um
preço alto: toda busca em `docs/` devolvia versões substituídas junto com as vigentes. Um agente
que procurasse `versionCode` recebia `2026-07-16_ANDROID_TECNICO.md` dizendo `0.23.0/56` ao lado do
documento vivo. A regra "nunca use `_archive` como verdade atual" existia, mas depender de
disciplina para evitar um erro é pior do que eliminar a possibilidade dele.

## Como recuperar qualquer arquivo

Todos existiam no commit **`10b2f05d`**. Para ler um arquivo sem restaurá-lo:

```bash
git show 10b2f05d:docs_ai/_archive/2026-07-16_ANDROID_TECNICO.md
```

Para listar tudo que havia:

```bash
git ls-tree -r --name-only 10b2f05d -- docs/archive/
```

Para restaurar um arquivo específico:

```bash
git checkout 10b2f05d -- docs/archive/<nome-do-arquivo>.md
```

## O que havia aqui

| Conjunto | Qtd | Conteúdo |
|---|---:|---|
| `2026-07-16_*.md` | 48 | Versões anteriores de `ANDROID_FUNCIONAL`, `ANDROID_TECNICO`, `ARCHITECTURE`, `BUILD_SYSTEM`, `MODULES`, `STORAGE`, `DATA_FLOW`, `API_MAP` e dos docs de design system — todas explicitamente substituídas por `FUNCIONAL.md`, `TECNICO.md`, `ARQUITETURA/` e `DESIGN_SYSTEM.md` |
| `2026-07-23_*.md` | 12 | Specs de Admin, OpenAPI, autenticação e consolidação de paths |
| `issues-backlog-2026-06/` | 24 | Backlog de issues em markdown, anterior à adoção do GitHub Issues como fonte de verdade |
| `impeccable-critique-2026-07-05/` | 5 | Críticas pontuais de código de julho/2026 |
| `rebranding-veloo/` | — | Material do rebrand Veloo → SignallQ |
| Diversos | 11 | Relatórios de limpeza, migração e paridade de plataformas |

## Regra a partir de agora

Documento substituído é **removido**, não movido para cá. A substituição fica registrada no
documento que o substituiu (campo "Documentos substituídos" no frontmatter) e no histórico do git.
Esta pasta não volta a receber arquivos.
