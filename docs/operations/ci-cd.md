---
title: "CI/CD Pipeline"
description: "Workflows do GitHub Actions do SignallQ: CI Android, docs, release e utilitários."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-10"
version: "2.1.0"
---

# CI/CD Pipeline — SignallQ

- **Fonte de verdade:** `.github/workflows/*.yml`
- **Escopo:** CI (testes/lint/build) e CD (release) via GitHub Actions

## Workflows

| Workflow | Gatilho | Função |
|---|---|---|
| `android-ci.yml` | push/PR em `main` (só roda se `android/` ou o workflow mudou) | Testes, ktlint, detekt e build debug |
| `docs-ci.yml` | PR/push em `main`, segunda 12:00 UTC, manual | `scripts/validar-docs.sh` (ver `.claude/rules/politica-documentacao-viva.md`) |
| `release.yml` | tag `v*` ou manual | Build assinado, GitHub Release e publicação na Play Console — ver `RELEASE.md` |
| `promote-release.yml` | manual | Move AAB entre `internal`/`alpha`; sem uso ativo — ver `RELEASE.md` |
| `firebase-distribution.yml` | manual | Build para Firebase App Distribution — ver `RELEASE.md` |
| `auto-move-board.yml` | issues/PRs | Move cards do GitHub Project |
| `auto-update-branch.yml` | push em `main` | Atualiza PRs abertas atrasadas em relação a `main` |
| `web-ci.yml` | push/PR que mexe em `web/**` | Site (`web/`): lint, typecheck, testes e build; nunca publica |
| `web-deploy-vercel.yml` | manual (`workflow_dispatch`) | Publica o site na Vercel (`preview` ou `production`); exige os secrets `VERCEL_TOKEN`, `VERCEL_ORG_ID` e `VERCEL_PROJECT_ID` — ver `web/docs/deploy-vercel.md` |
| `pages-deploy.yml.disabled`, `site-ci.yml.disabled`, `site-deploy.yml.disabled` | — | Desativados (sufixo `.disabled`); legado do site em Cloudflare Pages, substituído por `web-ci`/`web-deploy-vercel` |

## Android CI — `android-ci.yml`

### Triggers

Ver tabela acima. Jobs:

### Jobs

#### 1. Unit Tests
- Timeout: 30 minutos
- Roda `./gradlew test` em todos os módulos Android
- Outputs: Relatório de testes em `android/**/build/reports/tests/`
- Artefato: `unit-test-reports`

#### 2. Ktlint Check
- Timeout: 15 minutos
- Verifica formatação e estilo Kotlin
- Falha em desvios do padrão de estilo

#### 3. Detekt Analysis
- Timeout: 20 minutos
- Análise de complexidade, bugs potenciais e anti-patterns
- Falha em violações críticas de qualidade

#### 4. Build Debug APK
- Timeout: 30 minutos
- Compila APK debug para validar compilação
- NÃO roda `assembleRelease` pois não há acesso às signing keys em CI
- Outputs: APK em `android/app/build/outputs/apk/debug/`
- Artefato: `debug-apk`

### Gradle Cache

Todos os jobs usam cache gradle para accelerar builds. Cache é automático entre runs na mesma branch.

### JDK

Versão fixa: **JDK 17** (Temurin).

## Interpretando Falhas

### Unit Tests falham

Possíveis causas:

1. **Teste quebrado** — código novo não passou nos testes existentes
   - Solução: revisar o diff e corrigir lógica ou teste

2. **Dependência de teste ausente**
   - Solução: verificar `build.gradle.kts` do módulo

3. **Flakiness** — teste passa/falha aleatoriamente
   - Solução: investigar concorrência, timeouts ou estado compartilhado

### Ktlint falha

Solução automática:

```bash
cd android && ./gradlew ktlintFormat
```

Depois commit.

### Detekt falha

Revisar arquivo flagged, considerar refatoração ou suprimir se falso positivo:

```kotlin
@Suppress("ComplexMethod")
fun complexFunction() { ... }
```

### Build Debug falha

Causas comuns:

- **Erro de compilação Kotlin** — import faltando, tipo incorreto
- **Recurso não encontrado** — arquivo XML ou imagem deletado
- **Dependência duplicada** — conflito de versões

Solução: rodar localmente `./gradlew clean assembleDebug`.

## Performance

Run completo: ~20-35 min (testes 8-12, ktlint 2-3, detekt 3-5, build debug 5-10).

Histórico de runs: https://github.com/buildea-labs/signallq/actions (artefatos por 30 dias).

## Troubleshooting

- **Cache Gradle corrompido:** Settings → Actions → Clear all caches.
- **Artefato ausente:** o upload usa `if-no-files-found: ignore`; reproduza localmente.
- **Notificações Slack/Discord:** o GitHub já notifica o Slack; os antigos `discord_notify.sh`/`slack_notify.sh` foram removidos (`git show 0daa424a:scripts/legacy/discord_notify.sh`).
