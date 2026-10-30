---
title: "[Nome do runbook]"
description: "Procedimento passo-a-passo para [tarefa operacional]"
type: "runbook"
status: "ativo"
owner: "[Nome - geralmente Gustavo ou ops]"
last_updated: "2026-08-05"
version: "1.0.0"
severity: "P1 / P2 / P3"
---

# Runbook: [Tarefa operacional]

## Metadados

| Campo | Valor |
|---|---|
| **Tipo** | runbook / operação |
| **Status** | ativo / draft |
| **Responsável** | [Nome da pessoa/time] |
| **Última atualização** | 2026-08-05 |
| **Versão** | 1.0.0 |
| **Severidade** | P1 (crítica) / P2 (importante) / P3 (moderada) |
| **Tempo estimado** | [X minutos] |

## Histórico de versões

| Versão | Data | Autor | Mudança |
|---|---|---|---|
| 1.0.0 | 2026-08-05 | [Nome] | Versão inicial |

---

## ⚠️ Visão geral rápida

[**TL;DR:** 1-2 linhas explicando o quê fazer, quando fazer]

**Quando usar este runbook:**
- [Cenário 1]
- [Cenário 2]
- [Cenário 3]

**Não usar se:**
- [Condição onde não aplica]
- [Condição onde não aplica]

---

## Pré-requisitos

[O que você precisa ter antes de começar]

- [ ] Acesso a [sistema/dashboard]
- [ ] Credenciais de [serviço]
- [ ] Permissão de [tipo]
- [ ] [Ferramenta/CLI] instalado
- [ ] Verificou [pré-condição]?

---

## Procedimento passo-a-passo

### Passo 1: [Descrição breve]

[Explicação detalhada]

```bash
# Comando exato a executar
comando --flag valor
```

**Resultado esperado:**
```
output esperado aqui
```

**Se não funcionar:**
- [Problema 1] → [Solução]
- [Problema 2] → [Solução]

---

### Passo 2: [Descrição breve]

[Explicação detalhada]

**Onde:** [Dashboard URL / arquivo / comando]

**Ação:** [O que clicar / fazer]

**Screenshot/referência:**
[Incluir screenshot se relevante]

---

### Passo 3: [Descrição breve]

[Explicação detalhada]

```json
{
  "payload": "exemplo"
}
```

**Validação:**
- [ ] Campo A contém X
- [ ] Campo B é vazio
- [ ] Status é "sucesso"

---

## Verificação de sucesso

[Como saber que funcionou?]

**Indicadores:**
- [ ] [Log/métrica] mostra X
- [ ] [Dashboard] muda para estado Y
- [ ] [Notificação] é recebida
- [ ] [Usuário] consegue fazer Z

**Checklist pós-operação:**
- [ ] [Verificação 1]
- [ ] [Verificação 2]
- [ ] [Verificação 3]

---

## Reversão / Rollback

[O que fazer se deu errado?]

**Se [problema], executar:**

```bash
# Comando de reversão
comando --rollback
```

**Passos de reversão:**
1. [Passo 1]
2. [Passo 2]
3. [Passo 3]

**Tempo estimado:** X minutos

**Validação de rollback:**
- [ ] [Métrica A] volta para estado anterior
- [ ] [Log] mostra sucesso de reversão

---

## Troubleshooting

[Problemas comuns durante o procedimento]

### Problema: [Descrição]

**Sintoma:** [O quê aparece / não funciona]

**Causa provável:** [Por quê]

**Solução:**
1. [Passo 1]
2. [Passo 2]
3. [Passo 3]

**Se ainda não funcionar:**
- Escalate para [pessoa/time]
- Post em [Slack channel]
- Open issue [GitHub]

---

## Impacto / Comunicação

[O que muda para os usuários?]

**Usuários afetados:** [Quem percebe?]

**Janela de impacto:** [Quanto tempo de downtime / degradação?]

**Comunicação:**
- [ ] Postar em #[canal Slack]
- [ ] Informar [pessoas]
- [ ] Atualizar status page

---

## Automação / Alternativas

[Como evitar fazer isto manualmente?]

**Script de automação:**
```bash
# Se existir, incluir aqui
./scripts/automate-this-runbook.sh
```

**Agendamento:**
- Rodas manualmente quando?
- Pode ser automatizado? [Sim/Não]
- CI/CD job correspondente: [Link]

---

## Referências

- **Dashboard relacionado:** [Link]
- **Documentação técnica:** [Link]
- **Issues relacionadas:** #[NNNN], #[NNNN]
- **Slack channel:** [#channel]
- **On-call rotation:** [Link]

---

## Histórico de execução

[Manter log de quando foi executado]

| Data | Executor | Razão | Resultado | Notas |
|---|---|---|---|---|
| 2026-08-05 | [Nome] | [Motivo] | Sucesso / Erro | [Observações] |

---

## Contatos / Escalação

| Nível | Contato | Disponibilidade |
|---|---|---|
| Tier 1 (Operações) | [Nome / Slack] | [Horário] |
| Tier 2 (Engenharia) | [Nome / Slack] | [Horário] |
| Tier 3 (Arquitetura) | [Claudete / Camilo] | [Horário] |

---

## Notas importantes

- **Última execução prática:** [Data]
- **Próxima revisão agendada:** [Data ou cadência]
- **Desvios conhecidos:** [Se o procedimento difere da realidade, listar]
- **Em manutenção?** [Se o serviço que este runbook afeta está em manutenção]

---

**Para manter este runbook vivo:**

1. Executar procedimento a cada [cadência]
2. Se algo mudou, atualizar imediatamente
3. Documentar erros encontrados na seção Troubleshooting
4. Atualizar versão e data
5. Committar com mensagem: `docs(runbook): [descrição da atualização]`

**Red flags:**
- ⚠️ Se este runbook não foi executado há >6 meses, marcar como draft/verificar
- ⚠️ Se o procedimento falha frequentemente, abrir issue P1
- ⚠️ Se o runbook tem >10 passos, considerar automação
