# Agentes e skills do SignallQ

A governança vive em [`AGENTS.md`](../AGENTS.md) e o fluxo em [`WORKFLOW.md`](WORKFLOW.md). Este diretório guarda procedimentos e o plano de arquitetura corrente; os perfis nativos dos agentes ficam em [`.codex/agents/`](../.codex/agents/) (Codex) e [`.claude/agents/`](../.claude/agents/) (Claude Code).

## Squad

- **Marcelo** — Líder de Produto, Negócio e Orquestrador da oficina; dá o aceite final.
- **Priscila** — Diretora de Experiência, UX/UI e Design System.
- **Camillo** — Engenheiro Principal Full-Stack e Arquitetura.
- **Renan** — Qualidade, QA, testes e aparelho real.

O orquestrador principal (Codex ou Claude Code) integra o trabalho. Não simule conversa entre personagens nem declare revisão que não ocorreu.

## Estrutura

```text
AGENTS.md                      governança do produto
.codex/agents/*.toml           perfis dos especialistas (Codex)
.claude/agents/*.md            perfis dos especialistas (Claude Code)
.agents/WORKFLOW.md            ciclo de 3 passos e comandos de validação
.agents/architecture-plan.md   plano de mudança sistêmica, só quando precisa sobreviver à sessão
.claude/skills/                skills canônicas
.agents/skills/                espelho de compatibilidade Codex
.github/skills/                espelho de compatibilidade GitHub
```

## Skills

Skills são procedimentos; o roteamento vem do `AGENTS.md`. A suíte canônica de 7 (`conversar-com-o-luiz`, `matar-cheiro-de-ia`, `marcelo-produto-e-aceite`, `priscila-design-system-ux`, `camillo-engenharia-e-arquitetura`, `renan-qa-e-prova-real`, `loja-e-aso`) e as skills de domínio estão listadas no `AGENTS.md`.

`.claude/skills/` é a fonte canônica. Depois de editar uma skill:

```bash
./scripts/sync-skills-mirrors.sh
./scripts/sync-skills-mirrors.sh --check
```

Os espelhos não ganham regra própria.
