# Personas do SignallQ — subagentes Claude Code

Este diretório contém os subagentes invocáveis do Claude Code, espelhando a squad definida em `../../AGENTS.md`:

- `cora.md` — Product Lead
- `davi.md` — Android Engineer
- `ramon.md` — Diagnostic Systems Engineer
- `breno.md` — QA & Reliability (somente leitura)
- `camillo.md` — Principal Engineer / System Architect transversal

A fonte de verdade do papel de cada agente é `../../AGENTS.md`. Os perfis equivalentes para sessões Codex vivem em `../../.codex/agents/*.toml` — Codex e Claude Code coexistem como orquestradores válidos neste repositório; mantenha os dois pares de arquivos coerentes ao alterar responsabilidade, gate ou fluxo de uma persona.

Não crie uma sexta persona aqui sem primeiro atualizar `AGENTS.md` §4.
