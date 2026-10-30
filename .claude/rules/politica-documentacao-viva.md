---
title: "Política — Documentação Viva"
description: "Regras obrigatórias para manter documentação sincronizada com código, confiável e útil"
version: "1.0.0"
effective_date: "2026-08-05"
---

# Política — Documentação Viva

**Objetivo:** Garantir que documentação é (1) sincronizada com código, (2) confiável o suficiente para qualquer desenvolvedor agir baseado nela, (3) útil (encontrável, indexada, viva).

**Escopo:** Todos os documentos em `docs/`, `AGENTS.md`, `CLAUDE.md`, contratos OpenAPI, e runbooks.

---

## 0. Enforcement (2026-08-06)

Esta política deixou de ser só texto. `.github/workflows/docs-ci.yml` roda
`scripts/validar-docs.sh` em toda PR e reprova quando:

| Verificação | O que pega |
|---|---|
| Inventário gerado vs. código | `TECNICO.md`/`ARQUITETURA/README.md` com versão, contagem de módulos, tabelas D1 ou endpoints diferentes do código |
| Espelhos de skill | `.agents/skills/` ou `.github/skills/` divergindo de `.claude/skills/` |
| Frontmatter | `.md` em `docs/` alterado pela PR sem `title`, `description`, `type`, `status`, `owner`, `last_updated` |
| Localização | `.md` criado fora da árvore permitida |
| Contagem do índice | `INDICE.md` declarando "`technical/` (14)" quando o disco tem 15 |
| Pasta órfã | pasta nova em `docs/` não citada em `INDICE.md` nem `README.md` |
| Documento solto | `.md` na raiz de `docs/` sem citação no índice |

**A PR valida apenas os arquivos que ela toca.** Exigir frontmatter da árvore
inteira reprovaria 79 documentos herdados e travaria todo mundo. Dívida nova é
barrada; a antiga aparece no relatório semanal (segunda, 09:00 BRT) e encolhe
conforme os arquivos são tocados — quem mexe num documento o traz ao padrão.

Rodar localmente o mesmo que o CI roda:

```bash
bash scripts/validar-docs.sh --base origin/main   # como na PR
bash scripts/validar-docs.sh --relatorio          # cobertura, nunca falha
```

Isenções: `docs/templates/` e `docs/archive/decisoes/` (vocabulário próprio de status), e tudo
fora de `docs/` — `SKILL.md` usa o cabeçalho exigido pelo carregador de skills.

---

## 1. Princípios

### 1.1 Documentação é código

- **Documentação obsoleta é pior que ausência.** Desenvolvedores agindo em docs antigas geram bugs.
- **Manutenção é responsabilidade de quem mexe no código,** não de um "proprietário de docs".
- **Docs vivem perto de código.** `docs/` fica no repo, commits incluem atualizações relacionadas.

### 1.2 Fonte única de verdade por tipo

| Tipo | Fonte de verdade |
|---|---|
| Funcionalidade | Figma (design) + FUNCIONAL.md (spec) |
| Arquitetura técnica | Código-fonte + TECNICO.md + ADRs |
| Decisões | ADRs (versionados, imutáveis) |
| Operações | Runbooks (executáveis, testados) |
| Interfaces públicas | OpenAPI .yaml sincronizado com código |

**Consequência:** Não manter a mesma informação em 2 lugares. Se encontrar duplicação, consolidar.

### 1.3 Confiança = precisão + data

- Todo documento deve ter `last_updated`
- Documentação >6 meses sem atualização é suspeita (até prova contrária)
- Red flag: "atualizado em 2026-02 mas o código que descreve mudou em 2026-07"

---

## 2. Regras obrigatórias

### 2.1 TODO documento deve ter metadados

Frontmatter YAML obrigatório:

```yaml
---
title: "..."
description: "..."
type: "técnico | funcional | adr | runbook"
status: "ativo | draft | archived | deprecated"
owner: "[Nome do agente/pessoa]"
last_updated: "YYYY-MM-DD"
version: "X.Y.Z"
---
```

**Violação:** Documentação sem metadados é inválida e deve ser marcada como draft ou deletada.

### 2.2 Status correto é obrigatório

- **ativo:** Confiável, mantido, pode ser usado
- **draft:** Ainda em construção, pode mudar, não use como verdade
- **archived:** Substitua por doc mais nova (linkar qual)
- **deprecated:** Não usar; ADR/código foi descontinuado

**Violação:** Documentação com status incompatível com `last_updated` (ex: "ativo" mas não tocado há 8 meses → investigar e fixar).

### 2.3 Atualizar documentação quando código muda

**Obrigação de quem faz a PR:**

- [ ] Mudança técnica → atualizar doc técnica relevante
- [ ] Mudança de fluxo/interface → atualizar FUNCIONAL.md
- [ ] Decisão arquitetural → criar/atualizar ADR
- [ ] Runbook inválido → atualizar ou marcar como draft

**Consequência:** PR que muda código mas não atualiza doc relacionada pode ser rejeitada em review.

**Exceção:** Mudanças triviais (typos, refactor sem semântica) → atualizar doc em commit separado ou futuro.

### 2.4 Índices devem estar sincronizados

Manter atualizados:

- `docs/INDICE.md` — mapa central com todos os documentos
- `docs/README.md` — entrada rápida
- `docs/01-funcional.md` — índice de features
- `docs/03-especificacao-tecnica.md` — índice técnico
- `docs/archive/decisoes/README.md` — lista de ADRs
- `docs/operations/README.md` — lista de runbooks

**Violação:** Criar doc novo sem adicionar ao índice → considerar perdido.

### 2.5 Runbooks devem ser testados

- Executar cada runbook a cada 3 meses (rotação de oncall)
- Se falhar, atualizar imediatamente ou marcar como draft
- Documentar na tabela "Histórico de execução" quando foi rodado

**Violação:** Runbook não testado há >6 meses é suspeito.

### 2.6 OpenAPI deve estar sincronizado com código

- Executar `npm run validate:openapi` (ou equivalente) antes de committar
- Se schema não valida contra código real, rejeitar PR
- Falso positivo? Atualizar swagger + código de forma síncrona

**Violação:** OpenAPI desynced = desenvolvedor confunde o que API realmente faz.

### 2.7 Contratos de múltiplas plataformas devem ser explícitos

Quando Android + Web + Admin consomem o mesmo endpoint:

- Documentar os campos obrigatórios vs opcionais por plataforma
- Documentar diferenças de comportamento (ex: "web não envia banda")
- Testar multi-plataforma em suite de testes (não só um cliente)

**Violação:** "Isso funciona no Android mas não na web" = contrato incompleto.

---

## 3. Cadência de manutenção

### 3.1 Semanal

- [ ] Revisar documentação que será tocada pela sprint
- [ ] Se atualizando código que tem doc, atualizar doc também

### 3.2 Mensal

- [ ] Executar um runbook (rotação)
- [ ] Revisar que nenhum doc ficou órfão (linkado de lugar nenhum)

### 3.3 Trimestral (este trimestre: 2026-11-05)

- [ ] Auditoria completa: revisar INDICE.md
- [ ] Deletar/arquivar docs não tocados há 9+ meses
- [ ] Validar que `last_updated` é coerente
- [ ] Revisar red flags (paths legados, agentes extintos, etc.)

---

## 4. Governança

### 4.1 Responsáveis

Squad ativo (ver `AGENTS.md`): Marcelo (produto e aceite), Priscila (UX/UI e Design System), Camillo (engenharia e arquitetura) e Renan (QA). Design, growth e telemetria seguem como skills invocáveis.

| Tipo | Cria | Mantém | Revisa |
|---|---|---|---|
| Técnico | Camillo | Camillo | Renan |
| Funcional | Marcelo | Marcelo | Renan |
| Design System | Priscila | Priscila | Renan |
| Decisão (`docs/DECISOES.md`) | Marcelo/Camillo | Marcelo/Camillo | Renan |
| Runbook | Camillo | Camillo | Renan |

Notas por tipo:

- **Funcional** — checagem de design invocando `/design-check` (arquivo/tela) ou `/auditar-ux` (auditoria profunda multi-tela); Priscila especifica a experiência e Marcelo decide direção.
- **Runbook** — checagem de dados/telemetria invocando `/analytics-spec` quando aplicável (evento novo, mudança de propriedade, propriedade sensível).

### 4.2 Aprovação

- **Técnico / decisão / runbook:** revisão independente do Renan se afeta arquitetura, segurança ou privacidade.
- **Funcional:** revisão do Renan se afeta jornada/UX; usar `/design-check` para validação pontual e `/auditar-ux` para revisão profunda multi-tela.

### 4.3 Escalação

**Se documentação está desatualizada:**
1. Abrir issue marcando como `doc-debt`
2. Assigñar ao `owner` do documento
3. Se não atualizar em 2 semanas, marcar como `draft` (avisando usuários)

---

## 5. Quando deletar documentação

Documentação pode ser deletada se:

- [ ] Último código relacionado foi deletado
- [ ] Não há link de entrada (órfão há >3 meses)
- [ ] Owner concorda que é obsoleto
- [ ] Alternativa nova foi linkada

**Procedimento (atualizado 2026-08-06 — não há mais pasta de arquivo):**
1. Remover o arquivo — o git é o arquivo
2. Remover de todos os índices (`INDICE.md`, `README.md`, links de outros documentos)
3. Registrar a substituição no documento que o substituiu
4. Citar na mensagem de commit o SHA em que o arquivo ainda existia

Ver `.claude/rules/higiene-e-padronizacao-repositorio.md`, seção "Remoção", para o racional — em
resumo: documentação arquivada na árvore duplica o histórico do git e é lida como verdade atual por
quem busca, o que causa erro real.

---

## 6. Versionamento de documentos

### 6.1 Esquema semântico

- **1.0.0** → Versão inicial estável
- **1.1.0** → Novo conteúdo / seção (não-breaking)
- **1.1.1** → Correção / typo / clarificação
- **2.0.0** → Mudança arquitetural / breaking (ex: novo fluxo inteiro)

### 6.2 Quando incrementar

- **Patch** (1.1.1): Typo, clarificação, exemplo novo
- **Minor** (1.1.0): Nova seção, diagrama novo, mudança não-breaking
- **Major** (2.0.0): Arquitetura muda, fluxo completamente novo, incompatível com v1

### 6.3 ADRs e imutabilidade

ADRs nunca são "versionados" de forma contínua. Se uma decisão muda:
- [ ] Criar novo ADR (ADR-N+1 substitui ADR-N)
- [ ] Marcar ADR-N como `deprecated`
- [ ] Linkar: "Ver ADR-N+1 para versão atual"

---

## 7. Qualidade de documentação

### 7.1 Checklist de qualidade

- [ ] **Claro:** Qualquer desenvolvedor consegue entender sem perguntar
- [ ] **Preciso:** Exemplos funcionam, código não foi removido
- [ ] **Completo:** Não deixa "ficará pronto depois"
- [ ] **Linkado:** Encontrável de 2+ lugares
- [ ] **Indicado:** Tem dono e data
- [ ] **Vivo:** Atualizado quando código relacionado muda

### 7.2 Sinais de qualidade baixa 🚩

- [ ] Linguagem vaga ("pode ser que...", "talvez...")
- [ ] Comando de exemplo gera erro
- [ ] Estrutura descrita não existe no código
- [ ] Falta contexto (escreve "use X" mas não explica por quê)
- [ ] Português ruim (dificulta compreensão)

### 7.3 Revisor checklist

Ao revisar documentação, validar:

- [ ] Metadados corretos (status, versão, dono, data)
- [ ] Conteúdo alinhado com código/design
- [ ] Exemplos são executáveis
- [ ] Estrutura segue template
- [ ] Índices foram atualizados
- [ ] Não há duplicação com outro documento

---

## 8. Documentação em repositórios relacionados

### 8.1 Sincronização entre repos

Quando `signallq` + `signallq-web` + `buildea-admin` compartilham:

- **Contratos:** Sincronizar OpenAPI entre repos
- **Decisões:** Se ADR afeta múltiplos repos, documentar em `ai-governance/`
- **Runbooks:** Share em `.claude/skills/` se operação é cross-repo

### 8.2 Referências cruzadas

- Linkable em markdown: `../signallq-web/docs/01-funcional.md`
- Documentar em `AGENTS.md` que existe overlap

---

## 9. Exceções e desvios

**Pode desviar desta política se:**

1. Aprovação explícita de Marcelo ou do Luiz (exceção de negócio)
2. Situação de emergência/incidente (fixar depois)
3. Documentação em transição (máximo 2 sprints em `draft`)

**Não é exceção:**
- "Não temos tempo" → reorganizar prioridades
- "Vai ficar pronto depois" → marcar como `draft` agora

---

## 10. Métricas

### 10.1 Audit de documentação (trimestral)

| Métrica | Target | Ação se miss |
|---|---|---|
| % docs com `last_updated` <6 meses | >80% | Criar issues de manutenção |
| % runbooks testados | 100% | Bloquear release |
| % ADRs implementados | 90% | Review: agendar revisão |
| Docs órfãs (sem link de entrada) | 0 | Deletar ou linkar |
| Docs divergentes código | 0 | Marcar como draft |

### 10.2 Indicadores de saúde

- **Verde:** >80% docs atualizados, runbooks testados, índices sincronizados
- **Amarelo:** 60-80% docs atualizados, alguns runbooks não testados
- **Vermelho:** <60% docs atualizados, runbooks quebrados, índices divergem de código

---

## 11. Histórico desta política

| Versão | Data | Mudança |
|---|---|---|
| 1.0.0 | 2026-08-05 | Política inicial após auditoria |

---

**Questões?** Abra issue em `buildea-labs/signallq` com label `doc-policy`.

**Próxima revisão:** 2026-11-05

---

## Apêndice: Checklist de PR com mudanças de código

Toda PR que muda código deve validar:

- [ ] Código está correto (testes passam)
- [ ] Documentação técnica relacionada foi atualizada (se aplicável)
- [ ] Funcionalidade é descrita em FUNCIONAL.md (se nova feature)
- [ ] Contrato OpenAPI foi atualizado (se muda API)
- [ ] ADR existe para decisão arquitetural (se relevante)
- [ ] Runbook foi testado (se muda operação)
- [ ] Índices foram atualizados (INDICE.md, README.md, etc.)
- [ ] Não há documentação órfã ou conflitante

**Sem isso, PR pode ser rejeitada.**
