---
title: "ADR-[NNN]: [Título da decisão]"
description: "O que foi decidido, quem decidiu, por quê"
type: "adr"
status: "aceito / proposto / rejeitado / depreciado"
owner: "[Nome do tomador de decisão]"
last_updated: "2026-08-05"
version: "1.0.0"
decision_date: "2026-08-05"
---

# ADR-[NNN]: [Título da decisão arquitetural]

## Metadados

| Campo | Valor |
|---|---|
| **Status** | Aceito / Proposto / Rejeitado / Depreciado |
| **Tomador de decisão** | [Claudete / Camilo / Renan / outro] |
| **Data da decisão** | 2026-08-05 |
| **Data de revisão** | [quando será revista] |
| **Afetados** | [Quem precisa saber / implementar] |

---

## Contexto

[**Por que** esta decisão foi necessária?]

- **Problema:** [O quê não está funcionando ou é ineficiente]
- **Pressão:** [Deadline? Volume? Segurança? Performance?]
- **Restrições:** [Limites técnicos, orçamentários, de escopo]

---

## Decisão

[**O quê** foi decidido, em uma frase clara]

**Vamos:** [descrição breve da decisão tomada]

---

## Opções consideradas

[**Como** chegamos aqui? Quais eram as alternativas?]

### Opção A: [Nome]

**Vantagens:**
- [+]
- [+]

**Desvantagens:**
- [-]
- [-]

**Custo estimado:** [em story points / horas / money]

**Risco:** [Low / Medium / High] — [razão]

### Opção B: [Nome]

**Vantagens:**
- [+]
- [+]

**Desvantagens:**
- [-]
- [-]

**Custo estimado:** [em story points / horas / money]

**Risco:** [Low / Medium / High] — [razão]

### Opção C: [Nome]

**Vantagens:**
- [+]
- [+]

**Desvantagens:**
- [-]
- [-]

**Custo estimado:** [em story points / horas / money]

**Risco:** [Low / Medium / High] — [razão]

---

## Justificativa da decisão

[**Por que** escolhemos a Opção A?]

- [Razão 1]
- [Razão 2]
- [Razão 3]

**Trade-offs aceitos:**
- [Aceitamos X em troca de Y]
- [Aceitamos X em troca de Y]

---

## Consequências

[**O que muda** com esta decisão?]

### Positivas

- [Benefício 1]
- [Benefício 2]

### Negativas

- [Custo / débito técnico 1]
- [Custo / débito técnico 2]

### Riscos

- [Risco 1 e como mitigar]
- [Risco 2 e como mitigar]

---

## Implementação

[**Como** vamos executar?]

**Responsável:** [Nome do agente]

**Timeline:**
- Semana 1: [Fase 1]
- Semana 2: [Fase 2]

**Dependências:**
- [Dependência 1]
- [Dependência 2]

**Critérios de sucesso:**
- [ ] [Critério 1]
- [ ] [Critério 2]

---

## Alternativas futuras

[Se esta decisão não der certo, qual é o plano B?]

- **Rollback:** [Como desfazer?]
- **Revisão:** [Quando revisitar?]
- **Condição de reversão:** [Se X acontecer, reconsiderar]

---

## Referências

- **Issue de negócio:** #[NNNN]
- **Documento de requisitos:** [Link]
- **ADRs relacionados:** ADR-[NNN], ADR-[NNN]
- **Implementação:** [Link para branch / PR / código]

---

## Histórico de revisões

| Data | Autor | Status anterior | Status novo | Razão |
|---|---|---|---|---|
| 2026-08-05 | [Nome] | — | Aceito | Decisão inicial |

---

## Notas

- **Revisada em:** [data]
- **Próxima revisão agendada para:** [data ou condição]
- **Desvios conhecidos:** [se a implementação diferiu da decisão, explicar por quê]

---

**Para criar um novo ADR:**

1. Copiar este template e renomear para `ADR-[NNN]-[slug].md`
2. Preencher todas as seções
3. Apresentar para aprovação de [Claudete ou líder técnico]
4. Committar com mensagem: `docs(adr): ADR-[NNN] — [título]`
5. Adicionar link no índice principal: `docs/archive/decisoes/README.md`

**Para depreciar um ADR:**

1. Alterar `status` para "depreciado"
2. Adicionar seção "Razão da deprecação"
3. Linkar para ADR que o substitui
4. Committar com mensagem: `docs(adr): ADR-[NNN] deprecated — ADR-[NNN] is the new decision`
