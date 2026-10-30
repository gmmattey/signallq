---
title: "Governança e fluxo de trabalho"
description: "Papéis da squad, ciclo de 3 passos, gate arquitetural, regras de decisão e critérios de conclusão do SignallQ."
type: "técnico"
status: "ativo"
owner: "Marcelo"
last_updated: "2026-10-30"
version: "1.0.0"
---

# Governança e fluxo de trabalho

- **Fonte de verdade:** [`AGENTS.md`](../AGENTS.md) (regras) e [`.agents/WORKFLOW.md`](../.agents/WORKFLOW.md) (ciclo operacional). Este documento é o mapa normativo e não repete o texto deles.
- **Escopo:** como o trabalho é enquadrado, construído, provado e aceito no repositório `buildea-labs/signallq`, por Codex ou Claude Code.
- **Substitui:** a squad `Claudete / Rian / Marcelo / Tiago / Camillo`, a "Squad 2.0" (Giam, Marco, Bia) e o Cartão de Missão em disco.

## 1. Squad Ultra-Enxuta

| Agente | Papel | Escreve código? |
| --- | --- | --- |
| **Marcelo** | Líder de produto, negócio e orquestração; dá o aceite final | Não |
| **Priscila** | UX/UI, Design System, acessibilidade, auditoria visual | Não |
| **Camillo** | Engenharia full-stack e arquitetura (Android, Windows, Workers, motor de diagnóstico) | Sim |
| **Renan** | QA independente, segurança, prova em aparelho real | Não |

Perfis nativos em `.codex/agents/*.toml` e `.claude/agents/*.md`; precisam ficar coerentes entre si. O orquestrador principal integra o resultado e fala com o Luiz. Nenhum agente simula conversa com outro nem declara revisão que não aconteceu.

## 2. Ciclo de 3 passos e WIP = 1

1. **Enquadramento (Marcelo):** um parágrafo com o que fazer, o que não fazer e o critério de sucesso.
2. **Construção e auto-teste (Camillo, com Priscila quando há tela):** implementa, roda build e testes.
3. **Prova de fogo (Renan):** `✓ APROVADO` com evidência ou `✗ BLOQUEADO` com arquivo, linha e cenário. Depois, Marcelo confere as 7 perguntas de aceite.

Exploração (o Luiz pensando em voz alta) não altera código. Hot lane (produção quebrada) faz o conserto cirúrgico mínimo, valida o caminho crítico e registra depois.

## 3. Gate arquitetural

Camillo fecha a abordagem por escrito **antes** do código quando a mudança tem impacto arquitetural: contrato entre módulos, API, integração app ↔ Worker, migração de persistência, motor central de diagnóstico ou speedtest, nova dependência estrutural, autenticação, segurança ou privacidade sistêmica. O gatilho é o impacto, não o número de arquivos. Plano curto, na conversa ou em `.agents/architecture-plan.md` quando precisa sobreviver à sessão.

## 4. Decisões

Toda decisão aprovada pelo Luiz entra ao final de [`DECISOES.md`](DECISOES.md), numerada e indexada. Não há arquivo por decisão. Decisão de ID maior prevalece sobre a anterior. Código não substitui decisão de produto.

Sobem para o Luiz: escopo, marca, monetização, custo recorrente, privacidade sensível, publicação e trade-off relevante de comportamento. Desempate entre frentes, nesta ordem: verdade do resultado para quem usa, clareza da experiência, segurança e privacidade, custo, velocidade.

## 5. Skills

`.claude/skills/` é a fonte canônica; `.agents/skills/` e `.github/skills/` são espelhos sincronizados por `scripts/sync-skills-mirrors.sh`. A suíte canônica de 7 procedimentos e a lista de skills de domínio estão no `AGENTS.md`.

## 6. Critérios de conclusão

Uma entrega só é concluída quando:

1. o comportamento combinado por Marcelo foi atendido;
2. o diagnóstico não tem dado inventado, ausência virando zero nem causa sem evidência;
3. Priscila confirmou a fidelidade visual, quando há interface;
4. Renan rodou os testes aplicáveis e não achou regressão;
5. o gate do Camillo foi cumprido, quando aplicável;
6. a documentação diretamente ligada foi atualizada na mesma PR (`docs/features/<slug>.md`, contratos, decisão);
7. o estado real está declarado: documentado, implementado, validado em emulador, validado em aparelho físico, enviado ao teste fechado ou publicado são coisas diferentes, e o que **não** foi executado também aparece.

## 7. Validação e git

Comandos de validação por área estão em `.agents/WORKFLOW.md`. Trabalhe em branch, preserve mudança alheia, não use force push sem autorização e não publique, faça release ou deploy em produção sem o Luiz. Regras de higiene do repositório: [`.claude/rules/higiene-e-padronizacao-repositorio.md`](../.claude/rules/higiene-e-padronizacao-repositorio.md). Documentação viva: [`.claude/rules/politica-documentacao-viva.md`](../.claude/rules/politica-documentacao-viva.md).
