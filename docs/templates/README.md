---
title: "Templates de Documentação — SignallQ"
description: "Modelos oficiais para manter documentação viva, consistente e confiável"
type: "referência"
status: "ativo"
version: "1.0.0"
last_updated: "2026-08-06"
owner: "Squad"
---

# Templates de Documentação — SignallQ

**Objetivo:** padronizar como a documentação é criada, versionada, mantida e revisada no repositório `signallq`.

---

## 📋 Tipos de documentação

### 1. **Documentação Técnica** (`TEMPLATE_TECNICO.md`)

**Quando usar:** Explicar como algo funciona internamente (arquitetura, engine, worker, módulo, API).

**Responsável típico:** Camilo (engenharia técnica)

**Estrutura:**
- Visão geral + conceitos-chave
- Arquitetura / estrutura de componentes
- Implementação e padrões
- Contrato / interface pública
- Observabilidade (logs, métricas, alertas)
- Troubleshooting
- Roadmap

**Exemplos:**
- `TECNICO.md` (índice técnico principal)
- `ARQUITETURA/MODULOS/core-diagnostico.md`
- `technical/INTELBRAS_RX1500_FIELD_MAP.md`

**Mantém-se vivo?** Sim — toda mudança em código que afete a arquitetura deve atualizar a doc.

---

### 2. **Documentação Funcional** (`TEMPLATE_FUNCIONAL.md`)

**Quando usar:** Explicar o que o usuário faz, qual problema resolve, critérios de sucesso.

**Responsável típico:** Claudete (produto) + Juliana (design)

**Estrutura:**
- Descrição da funcionalidade
- Personas / públicos
- Jornada do usuário (passo a passo)
- Critérios de aceitação
- Casos de uso
- Métricas de sucesso
- Design / interface

**Exemplos:**
- `FUNCIONAL.md` (índice funcional principal)
- `functional/FEATURE_FLAGS.md`
- `functional/JORNADA_ANDROID_GUIADA_2_SPEC.md`

**Mantém-se vivo?** Sim — mudanças no fluxo, design ou requisitos devem atualizar a doc.

---

### 3. **Decisões Arquiteturais (ADRs)** (`TEMPLATE_ADR.md`)

**Quando usar:** Registrar uma decisão importante que afeta a arquitetura ou direção técnica.

**Responsável típico:** Claudete (decisão) + Camilo (implementação)

**Estrutura:**
- Contexto (por quê?)
- Decisão (o quê?)
- Opções consideradas (como chegamos aqui?)
- Justificativa (por que esta opção?)
- Consequências (o que muda?)
- Implementação (como executar?)
- Alternativas futuras (plano B?)

**Exemplos:**
- `decisions/ADR-001-shadow-mode-remote-diagnostics.md`
- `decisions/ADR-013-consolidate-feature-flags.md`

**Locação:** `docs/archive/decisoes/ADR-[NNN]-[slug].md`

**Mantém-se vivo?** Parcialmente — ADRs são ponto-no-tempo. Se depreciar, marcar status como "depreciado" e linkar para novo ADR.

---

### 4. **Runbooks / Operações** (`TEMPLATE_RUNBOOK.md`)

**Quando usar:** Procedimento passo-a-passo para operação, release, deploy, ou resolução de incidente.

**Responsável típico:** Gustavo (ops) + Camilo (eng)

**Estrutura:**
- Visão geral rápida (TL;DR)
- Pré-requisitos
- Procedimento passo-a-passo
- Verificação de sucesso
- Reversão / rollback
- Troubleshooting
- Automação / alternativas

**Exemplos:**
- `operations/RELEASE_ANDROID.md`
- `operations/DEPLOY_WORKERS.md`
- `operations/INCIDENT_RESPONSE.md`

**Mantém-se vivo?** Sim — executar cada vez que usado, atualizar se algo mudou.

---

## 📝 Metadados obrigatórios

Todo documento deve ter frontmatter YAML com:

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

**Versionamento:**
- `major.minor.patch`
- Increment `major` para mudanças arquiteturais / breaking changes
- Increment `minor` para mudanças não-breaking (novo conteúdo)
- Increment `patch` para correções / typos

---

## 🔄 Histórico de versões

**Todo documento deve ter uma tabela de histórico:**

| Versão | Data | Autor | Mudança |
|---|---|---|---|
| 1.0.0 | 2026-08-05 | [Nome] | Versão inicial |
| 1.1.0 | 2026-08-12 | [Nome] | Adicionado seção X |

---

## 📍 Estrutura de localização

```
docs/
├── README.md                          (índice principal)
├── FUNCIONAL.md                       (índice funcional)
├── TECNICO.md                         (índice técnico)
├── ARQUITETURA/
│   ├── README.md
│   └── MODULOS/
│       ├── core-diagnostico.md
│       ├── feature-diagnostico.md
│       └── [...]
├── decisions/
│   ├── README.md
│   ├── ADR-001-[slug].md
│   ├── ADR-002-[slug].md
│   └── [...]
├── operations/
│   ├── README.md
│   ├── RELEASE_ANDROID.md
│   ├── DEPLOY_WORKERS.md
│   └── [...]
├── templates/
│   ├── README.md (este arquivo)
│   ├── TEMPLATE_TECNICO.md
│   ├── TEMPLATE_FUNCIONAL.md
│   ├── TEMPLATE_ADR.md
│   └── TEMPLATE_RUNBOOK.md
└── _archive/
    └── [documentação legada / desatualizada]
```

---

## ✅ Checklist: Criar nova documentação

- [ ] Escolher tipo correto (`técnico`, `funcional`, `adr`, `runbook`)
- [ ] Copiar template correspondente
- [ ] Preencher metadados (title, type, owner, status, version)
- [ ] Preencher conteúdo (seguir estrutura do template)
- [ ] Adicionar histórico de versão inicial
- [ ] Linkar de índice principal (README.md ou seção relevante)
- [ ] Revisar: linguagem clara, sem jargão, exemplos concretos
- [ ] Committar com mensagem: `docs([tipo]): [descrição]`

---

## ♻️ Checklist: Manter documentação viva

### Cadência

**Ao menos a cada 3 meses:**
- [ ] Revisar `last_updated`
- [ ] Verificar se conteúdo ainda está correto
- [ ] Se mudança simples (typo), fazer patch
- [ ] Se mudança significativa, fazer minor bump
- [ ] Adicionar linha ao histórico de versões

**Imediatamente quando:**
- [ ] Código muda e doc fica divergente → `ATUALIZAR DOC`
- [ ] Design/interface muda → atualizar doc funcional
- [ ] Arquitetura muda → atualizar doc técnica + possivelmente ADR
- [ ] Runbook não funciona mais → atualizar ou marcar como draft

### Red flags 🚩

Documento pode estar desatualizado se:
- [ ] `last_updated` é >6 meses atrás
- [ ] Referencia código/paths que não existem mais
- [ ] Menciona agentes/personas arquivadas
- [ ] Workflow descrito não funciona (erro ao executar runbook)
- [ ] Ninguém sabe quem é o `owner`
- [ ] Documento está em `_archive/` mas ainda é linkado do código

---

## 🔗 Índices relacionados

Manter atualizados:

- **`docs/README.md`** — índice principal, links para FUNCIONAL/TECNICO/ARQUITETURA
- **`docs/01-funcional.md`** — índice funcional, features agrupadas por jornada
- **`docs/03-especificacao-tecnica.md`** — índice técnico, módulos e engines
- **`docs/02-arquitetura.md`** — árvore de módulos
- **`docs/archive/decisoes/README.md`** — lista de ADRs com status
- **`docs/operations/README.md`** — lista de runbooks por severidade

---

## 📊 Responsabilidades

| Tipo | Cria | Mantém | Revisa |
|---|---|---|---|
| Técnico | Camilo | Camilo | Caio |
| Funcional | Claudete | Claudete | Caio |
| ADR | Claudete/Camilo | Claudete/Camilo | Caio |
| Runbook | Gustavo/Camilo | Gustavo | Caio |

---

## 🚀 Como usar os templates

1. **Copiar arquivo:** `cp docs/templates/TEMPLATE_[TIPO].md docs/[pasta]/[NOME].md`
2. **Preencher:** Seguir estrutura, substituir placeholders `[...]`
3. **Revisar:** Ler uma vez inteira, verificar clareza
4. **Linkar:** Adicionar referência no índice
5. **Committar:** `git add . && git commit -m "docs([tipo]): [descrição]"`

---

## 📌 Versão dos templates

| Template | Versão | Data |
|---|---|---|
| TEMPLATE_TECNICO | 1.0.0 | 2026-08-05 |
| TEMPLATE_FUNCIONAL | 1.0.0 | 2026-08-05 |
| TEMPLATE_ADR | 1.0.0 | 2026-08-05 |
| TEMPLATE_RUNBOOK | 1.0.0 | 2026-08-05 |

---

## ❓ FAQ

**P: Posso misturar tipos de documento?**  
A: Não recomendado. Se você escrever algo que mistura técnico + funcional, considerar dois docs (um de cada tipo) com crosslinks.

**P: E se meu documento não se encaixa em nenhum template?**  
A: Abrir issue para criar novo template de tipo. Até lá, usar o template mais próximo.

**P: Com que frequência revisar documentação?**  
A: Ao menos quando tocar no código relacionado. Revisão completa = quarterly (3 em 3 meses).

**P: Pode manter doc em `_archive/`?**  
A: Sim, mas com data e motivo claro (`_archive/2026-07-16_FEATURE_REMOVIDA.md`). Se ninguém consultar por 12 meses, deletar.

**P: Qual é a diferença entre `draft` e `deprecated`?**  
A: `draft` = ainda em construção, pode mudar muito. `deprecated` = foi ativo, mas agora substituído por outro (sempre linkar para o novo).

---

**Mantém-se vivo?** Sim — atualizar este README quando novos tipos de documento forem criados ou quando processo mudar.

**Última atualização:** 2026-08-05 por [Nome]  
**Próxima revisão:** 2026-11-05
