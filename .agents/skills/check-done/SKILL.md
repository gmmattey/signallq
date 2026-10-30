---
name: check-done
description: Verifica com evidência se uma tarefa/PR do SignallQ está pronta para ser considerada concluída.
argument-hint: "<issue|PR>"
allowed-tools: Bash(gh *), Bash(git *), Bash(./android/gradlew *), Bash(bash scripts/*)
---

# Check Done

Use antes de declarar tarefa concluída, fechar issue ou recomendar merge.

A skill verifica o trabalho; não substitui as regras de merge/publicação do `AGENTS.md`.

## Critérios

1. **Escopo** — comportamento e não-objetivos da issue/plano foram respeitados.
2. **Aceite** — critérios verificáveis foram conferidos.
3. **Testes** — testes aplicáveis foram executados.
4. **Qualidade estática** — lint/detekt/build conforme o escopo.
5. **Documentação/contratos** — atualizados quando a mudança altera comportamento, arquitetura, API, schema ou operação.
6. **Riscos** — limitações e partes não testadas estão declaradas.
7. **Revisão independente** — Renan revisou quando há código ou risco relevante.
8. **Gate arquitetural** — quando o `AGENTS.md` exige Camillo, existe Architecture Plan/revisão e a implementação está aderente.
9. **Rastreabilidade** — diff/commit/PR correspondem ao que está sendo declarado pronto.

## Validação Android

Quando o diff toca Android e os comandos forem aplicáveis:

```bash
./android/gradlew test
./android/gradlew ktlintCheck detekt
./android/gradlew assembleDebug
```

Não invente execução. Se um comando não puder ser rodado, registre `NÃO EXECUTADO` com motivo.

## Workers e contratos

Se houver Worker/API/schema:

- rode testes/comandos definidos no componente específico;
- confira compatibilidade com consumidores;
- confirme que o gate do Camillo foi cumprido quando houver impacto sistêmico;
- não faça deploy de produção como parte desta skill.

## Skills e documentação

Quando aplicável:

```bash
bash scripts/validar-docs.sh --base main
bash scripts/sync-skills-mirrors.sh --check
```

## Saída

```text
CHECK-DONE: PASS | FAIL | PENDENTE

Escopo: PASS/FAIL — evidência
Aceite: PASS/FAIL — evidência
Testes: PASS/FAIL/NÃO EXECUTADO — comandos
Qualidade: PASS/FAIL/NÃO EXECUTADO
Docs/contratos: PASS/FAIL/N/A
Riscos: declarados / faltando
Renan: PASSA/AJUSTA/BLOQUEIA/N/A
Camillo: OK/PENDENTE/N/A
Rastreabilidade: PASS/FAIL

Bloqueios restantes:
- ...
```

`PASS` só quando não houver bloqueio real. Uma PR existente ou um build verde, isoladamente, não significam conclusão.

A skill não faz merge, não fecha issue, não aceita risco crítico e não simula aprovação de Renan, Camillo ou Luiz.
