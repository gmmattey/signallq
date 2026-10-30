---
name: design-check
description: Valida uma tela/arquivo Android contra o Design System do SignallQ, acessibilidade e hierarquia visual.
argument-hint: "<arquivo.kt | tela>"
allowed-tools: Bash(grep *), Bash(find *), Read
---

# Design Check

Use para checagem pontual de UI já existente ou alterada. Para desenho novo, consulte `SignallQ-design`; para auditoria multi-tela, use `auditar-ux`.

A skill não pertence a uma persona. Marcelo pode usá-la para aceite visual, Priscila na especificação, Camillo durante implementação e Renan durante revisão.

## Fonte de verdade

Consulte `docs/04-design-system.md` e a skill `SignallQ-design`. Não copie tokens para este arquivo.

## Verificações

### Tokens

Procure cor, espaçamento, tipografia, shape e elevação hardcoded fora das exceções documentadas.

```bash
grep -noE 'Color\(0x[0-9A-Fa-f]{6,8}\)' "$ALVO"
grep -noE '\.(padding|size|width|height|offset)\([^)]*[0-9]+\.dp' "$ALVO"
grep -noE 'fontSize\s*=\s*[0-9]+\.sp|TextStyle\(' "$ALVO"
```

Classifique cada ocorrência com contexto; não reprovar automaticamente cor de marca de terceiro ou valor realmente específico quando a exceção estiver documentada.

### Hierarquia e densidade

- CTA primário é claro?
- informação técnica compete com a ação principal?
- texto usa roles tipográficas coerentes?
- cardização/containers ajudam a leitura ou só fragmentam a tela?
- loading, empty, error e offline têm representação?

### Acessibilidade

- contraste WCAG AA;
- touch target Android adequado;
- TalkBack/contentDescription/semantics;
- foco e ordem de leitura;
- cor não é único canal de estado.

### Produto

Confirme que a tela comunica diagnóstico em linguagem compreensível e não apresenta conclusão sem evidência. Mudança de jornada/escopo volta para Marcelo; mudança sistêmica que atravesse módulos/contratos segue o gate do Camillo.

## Saída

```text
DESIGN-CHECK: PASS | WARN | FAIL
Tokens: ...
Hierarquia: ...
Acessibilidade: ...
Estados: ...
Produto: ...
Achados acionáveis:
- arquivo:linha — problema — correção sugerida
```

A skill reporta; não altera código e não decide sozinha se um WARN bloqueia merge.
