---
title: "[Título do documento]"
description: "O que o usuário faz, fluxo de ação, critérios de sucesso"
type: "funcional"
status: "ativo"
owner: "[Nome - geralmente Marcelo ou dono do feature]"
last_updated: "2026-08-05"
version: "1.0.0"
---

# [Feature / Funcionalidade]

## Metadados

| Campo | Valor |
|---|---|
| **Tipo** | funcional |
| **Status** | ativo / beta / deprecated |
| **Responsável** | [Claudete ou dono] |
| **Última atualização** | 2026-08-05 |
| **Versão** | 1.0.0 |
| **Issue relacionada** | #[NNNN] |

## Histórico de versões

| Versão | Data | Autor | Mudança |
|---|---|---|---|
| 1.0.0 | 2026-08-05 | [Nome] | Versão inicial |

---

## 1. Descrição da funcionalidade

[O que é, para quem, por que existe]

**Objetivo do usuário:** [Qual problema resolve? Qual é o job to be done?]

---

## 2. Personas / Públicos

[Quem usa? Qual é o perfil?]

| Persona | Necessidade | Sucesso |
|---|---|---|
| [Persona] | [O quê] | [Resultado esperado] |

---

## 3. Jornada do usuário

[Passo a passo do fluxo]

1. Usuário [ação]
2. Sistema [reação]
3. Usuário [próxima ação]
4. Sistema [resultado]

### 3.1 Tela [Nome]

[O que aparece, componentes principais, opções]

- **Campo 1:** [descrição]
- **Campo 2:** [descrição]
- **Botão principal:** [label e ação]

---

## 4. Critérios de aceitação

[Definição clara de "pronto"]

- [ ] O usuário consegue [ação] em [contexto]
- [ ] O sistema retorna [resultado] em [tempo]
- [ ] Mensagem de [tipo] aparece quando [condição]
- [ ] [Caso extremo] é tratado com [comportamento]

---

## 5. Casos de uso

### Caso de uso: [Nome]

**Contexto:** [Situação inicial]

**Fluxo principal:**
1. [Passo]
2. [Passo]
3. [Passo]

**Resultado:** [Produto final]

**Variações:**
- Variação A: quando [condição], então [fluxo alternativo]
- Variação B: quando [condição], então [fluxo alternativo]

**Erros:**
- Se [condição], retornar [mensagem de erro]
- Se [condição], retornar [mensagem de erro]

---

## 6. Métricas de sucesso

[Como sabemos que está funcionando?]

| Métrica | Baseline | Alvo | Frequência |
|---|---|---|---|
| [Métrica 1] | [Baseline] | [Alvo] | [Diário/Semanal] |
| [Métrica 2] | [Baseline] | [Alvo] | [Diário/Semanal] |

---

## 7. Constraints / Limitações

[O que NÃO fazemos, por que]

- [Limitação 1 e motivo]
- [Limitação 2 e motivo]
- [Restrição de design ou técnica]

---

## 8. Design / Interface

[Wireframes, screenshots, componentes usados]

- **Componentes visuais:** [DS component 1, DS component 2]
- **Tipografia:** [fonte, tamanho]
- **Cores:** [uso de cor]
- **Ícones:** [ícone usado para quê]

[Incluir screenshot quando aplicável]

---

## 9. Próximos passos / Roadmap

[Fases futuras, melhorias, expansões]

- v1.1: [Feature]
- v2.0: [Feature maior]

---

## 10. Referências

- **Especificação detalhada:** [Link para Figma/Design]
- **Implementação:** [Link para código / branch]
- **Issue do tracker:** #[NNNN]
- **Documento técnico relacionado:** [Link]

---

## Notas importantes

- **Fonte de verdade:** design é definido em [Figma file]; implementação em [branch]
- **Divergência conhecida:** [listar se houver diferença entre spec e código]
- **Mantém-se vivo?** Sim — atualizar quando a jornada muda ou novos dados aparecem

---

**Para atualizar este documento:**

1. Editar o arquivo `.md`
2. Atualizar `version` se a jornada muda (minor) ou se foi deprecated/sunseted (major)
3. Adicionar linha ao "Histórico de versões"
4. Atualizar `last_updated`
5. Committar com mensagem: `docs(funcional): [descrição da mudança]`
