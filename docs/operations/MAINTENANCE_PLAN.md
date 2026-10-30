---
title: "Plano de atualização — documentos, agentes e skills"
description: "Rotina de manutenção da documentação, dos agentes e das skills a cada mudança."
type: "runbook"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Plano de atualização — documentos, agentes e skills

- **Fonte de verdade:** `AGENTS.md`, `.claude/rules/politica-documentacao-viva.md`, `.claude/rules/higiene-e-padronizacao-repositorio.md`
- **Escopo:** rotina de atualização da documentação; o conteúdo técnico vive nos próprios documentos

## Rotina por mudança

1. Atualize código e testes no módulo Gradle afetado.
2. `docs/01-funcional.md` quando mudar fluxo, tela ou comportamento.
3. `docs/03-especificacao-tecnica.md` e `docs/02-arquitetura.md` quando mudar arquitetura, módulo, serviço, storage ou integração.
4. `docs/04-design-system.md` quando mudar componente visual, token ou navegação.
5. `docs/operations/` quando mudar build, release, ambiente, script ou versionamento.
6. `CHANGELOG.md` quando a mudança for entregável.
7. Gere APK conforme `APK_OUTPUT_POLICY.md`.

## Agentes e skills

- `AGENTS.md` é a governança; perfis em `.claude/agents/*.md` e `.codex/agents/*.toml` devem permanecer coerentes.
- Skills: edite `.claude/skills/`, rode `scripts/sync-skills-mirrors.sh` e valide com `--check`.
- Toda skill nova declara quando usar, o que pode alterar, comando de validação e documento a atualizar.
- Scripts executáveis ficam em `scripts/`.

## Cadência

- A cada PR: revisar os documentos afetados (`bash scripts/validar-docs.sh --base origin/main`).
- Antes de release: `RELEASE.md` e a skill `checar-release`.
- Mensalmente: revisar documentos antigos, duplicados ou legados.
