---
name: checar-release
description: Checklist técnico e operacional de prontidão para release do SignallQ sem executar publicação automaticamente.
argument-hint: "[PR|versão|contexto]"
allowed-tools: Bash(gh *), Bash(git *), Bash(./android/gradlew *), Bash(bash scripts/*)
---

# Checar Release

Use quando uma entrega está candidata a Play Store, beta, release interna ou deploy coordenado de componentes do SignallQ.

Esta skill verifica prontidão. Não publica, não faz deploy de produção e não substitui autorização do Luiz.

## Android

Quando aplicável, confirme:

```bash
./android/gradlew test
./android/gradlew ktlintCheck detekt
./android/gradlew assembleDebug
```

Para build de release, assinatura e variante, use apenas os comandos/configurações reais do projeto. Não improvise keystore ou segredo.

Verifique também:

- versionCode/versionName coerentes;
- changelog/release notes user-facing;
- permissões novas documentadas;
- crash/erro crítico conhecido;
- comportamento em Android real quando a mudança depende de rede, lifecycle, background, OEM ou permissão;
- política de privacidade e Data Safety quando coleta/finalidade mudou.

## Workers/API

Se a release depende de Worker/API:

- confirme testes do componente específico;
- confirme compatibilidade do contrato;
- confirme que o gate de Camillo foi cumprido quando sistêmico;
- confirme estratégia de rollout/fallback quando aplicável;
- não faça deploy de produção nesta skill.

## Produto e loja

Use `growth-check` quando screenshots, descrição, ASO, marca ou superfície pública forem afetados.

Marcelo confirma que o comportamento entregue corresponde ao produto; Renan confirma qualidade e regressão. Camillo revisa apenas quando a release contém mudança sistêmica que acionou o gate arquitetural.

## Saída

```text
RELEASE-CHECK: PRONTO | PENDENTE | BLOQUEADO
Android: ...
Testes/CI: ...
Device real: ...
Workers/API: ...
Docs/contratos: ...
Privacidade: ...
Store/growth: ...
Renan: PASSA/AJUSTA/BLOQUEIA/N/A
Camillo: OK/PENDENTE/N/A
Autorização de publicação: PENDENTE|CONCEDIDA (com evidência)

Pendências:
- ...
```

Nunca marque autorização como concedida sem uma ação explícita do Luiz ou mecanismo autorizado. Release-ready não significa publicado.
