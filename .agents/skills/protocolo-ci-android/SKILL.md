---
description: Protocolo de CI/CD e dependências Android — validar workflows reais, PRs de bot e incompatibilidades de Kotlin/Compose antes de confiar em mergeability.
---

# Protocolo CI Android

Use ao investigar falha de CI, bump de Kotlin/Compose/Gradle, PR de Dependabot ou status de merge que não prova que o pipeline correto executou.

## Regra de ouro

`mergeable: true` não significa CI validado. Confira os runs/checks associados ao HEAD atual.

```bash
gh run list --repo buildea-labs/signallq --branch <branch> --json status,conclusion,workflowName,headSha
gh pr checks <PR> --repo buildea-labs/signallq
```

## PR de bot / action_required

Se workflow estiver `action_required`, não interprete checks irrelevantes como validação do Android. O run correto precisa ser autorizado por um humano com permissão e executado no HEAD esperado.

A skill não aprova workflow nem merge automaticamente.

## Bump Kotlin/Compose/Gradle

Quando build quebra após atualização:

1. identifique exatamente quais versões mudaram em `android/gradle/libs.versions.toml` e arquivos Gradle;
2. reproduza localmente com o mesmo JDK/toolchain do projeto;
3. verifique incompatibilidade entre Kotlin, Compose plugin, kapt/KSP e `kotlin-metadata-jvm` antes de adicionar workaround;
4. prefira combinação de versões oficialmente compatível a suppression/force global;
5. rode testes, lint/detekt e assemble aplicáveis.

## Branch desatualizada

Se a PR está behind, atualize de forma não destrutiva e revalide o HEAD novo. Não reutilize resultado verde de commit anterior.

## Gate de arquitetura

Bump de dependência local não aciona Camillo por padrão. Aciona quando a atualização exige mudança estrutural, contrato compartilhado, migration ou integração sistêmica.

## Saída

```text
CI-ANDROID: PASS | FAIL | PENDENTE
HEAD validado: <sha>
Workflows relevantes: ...
Runs reais: ...
Dependências alteradas: ...
Validação local: ...
Risco/gate Camillo: ...
Pendências: ...
```

Camillo normalmente resolve CI Android; Renan valida que o pipeline correto passou. A skill não define modelo de IA nem faz merge.
