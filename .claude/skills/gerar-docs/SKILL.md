---
name: gerar-docs
description: Gera ou atualiza documentação do SignallQ a partir do código e das fontes canônicas, evitando duplicação e docs obsoletos.
---

# Gerar documentação

Use para documentação funcional, técnica, contratos, testes, fluxo, design ou operação.

## Antes de criar arquivo novo

1. identifique o público e a finalidade do documento;
2. procure documento existente que já seja dono do assunto;
3. confirme comportamento no código/testes quando a documentação afirmar algo implementado;
4. atualize a fonte existente em vez de criar documento concorrente quando possível;
5. se substituir documento, deixe relação clara e preserve histórico no Git — não mantenha duas fontes ativas contraditórias;
6. para comportamento de uma feature, a fonte é `docs/features/<slug>.md` (índice em `docs/features/INDEX.md`): atualize a página, não `FUNCIONAL.md` nem o doc de módulo, e rode `scripts/validar-docs.sh` (confere caminhos, eventos e flags do frontmatter).

Não é necessário perguntar “humano ou IA?” quando o pedido e o destino já deixam o público claro. Pergunte somente quando isso realmente muda o artefato.

## Tipos

### Produto/funcional

Descreva comportamento do usuário, estados, limites e o que está efetivamente disponível. Draft deve estar marcado como draft.

### Arquitetura/técnica

Documente responsabilidade, dependências, contratos, fluxo de dados, falhas, segurança e decisões relevantes. Mudança sistêmica deve refletir o Architecture Plan aprovado pelo Camillo.

### Contratos/API/schema

Use formato executável/machine-readable quando o projeto já tiver padrão (OpenAPI, JSON Schema, SQL migration). Texto explicativo não substitui contrato canônico.

### Release/operação

Separe nota user-facing de instrução técnica. Nunca documente segredo, credencial ou chave real.

## Validação

Quando aplicável:

```bash
bash scripts/validar-docs.sh --base main
bash scripts/sync-skills-mirrors.sh --check
```

Verifique links relativos, referências a arquivo/seção e termos aposentados.

## Responsabilidades

Marcelo normalmente valida documento de produto; Priscila, design system e UX; Camillo documenta Android, diagnóstico, Workers, contratos e arquitetura sistêmica; Renan revisa documentação de risco/release quando necessário.

A skill é procedimento, não persona, e não define modelo de IA.
