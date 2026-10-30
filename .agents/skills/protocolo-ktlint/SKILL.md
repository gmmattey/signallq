---
description: Protocolo Ktlint — corrigir violações sem transformar suppressions em dívida técnica permanente.
---

# Protocolo Ktlint

Use antes de suprimir regra Ktlint no `.editorconfig` ou quando Ktlint bloqueia CI.

## Ordem de decisão

1. Tente o auto-fix suportado pelo projeto (`ktlintFormat` ou task equivalente).
2. Se não for auto-fixável, corrija o código quando a regra faz sentido.
3. Se houver incompatibilidade real entre regra e código gerado/DSL, prefira suppression local e documentada.
4. Suppression global é último recurso e precisa de justificativa, escopo e plano de remoção quando temporária.

Não use suppression para esconder centenas de violações de um arquivo tocado sem entender a causa.

## Validação

```bash
./android/gradlew ktlintCheck
```

Quando a task/ambiente usar wrapper em diretório diferente, siga o comando real do repositório.

## Regra de mudança ampla

Se corrigir Ktlint exige refactor estrutural em múltiplos módulos, separe a limpeza da feature quando possível. Se a mudança estrutural acionar o gate arquitetural do `AGENTS.md`, Camillo revisa antes da implementação.

## Saída

```text
KTLINT: PASS | FAIL | SUPPRESSION JUSTIFICADA
Regra: ...
Causa: ...
Correção aplicada: ...
Escopo da suppression (se houver): ...
Validação: ...
Pendência de cleanup: ...
```

Camillo normalmente corrige código; Renan verifica o gate. A skill não pertence a uma persona e não define modelo de IA.
