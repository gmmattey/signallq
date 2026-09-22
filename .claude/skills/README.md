# Skills canônicas do SignallQ

Este diretório é a **fonte canônica** das skills do SignallQ, usada pelo Claude Code.

Skills descrevem procedimentos reutilizáveis e não personas. O roteamento entre Cora, Davi, Ramon, Breno e Camillo é definido em [`AGENTS.md`](../../AGENTS.md) e [`.agents/WORKFLOW.md`](../../.agents/WORKFLOW.md).

Os diretórios `.agents/skills/` (Codex) e `.github/skills/` (GitHub/Copilot) são espelhos de compatibilidade.

Depois de alterar uma skill aqui, execute:

```bash
./scripts/sync-skills-mirrors.sh
./scripts/sync-skills-mirrors.sh --check
```

Não mantenha regra exclusiva nos espelhos.
