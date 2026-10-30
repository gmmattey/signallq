# SignallQ

App Android de **diagnóstico de conectividade** com IA — analisa Wi-Fi, fibra, rede móvel e dispositivos da rede local e gera laudos com explicações em linguagem natural.

- **Site do produto:** https://signallq.pages.dev/
- **Inscrição no teste fechado:** https://groups.google.com/g/testadores-signallq
- **Google Play:** https://play.google.com/store/apps/details?id=io.signallq.app
- **Desenvolvido pela Buildea:** https://github.com/buildea-labs (site institucional temporariamente fora do ar — pendente rename do repo `7ALabs.github.io`, ver decisão de portfólio sobre o rebrand)

> Package/namespace atual é `io.signallq.app` (renomeado de `io.veloo.app` em 2026-06-28). Outros identificadores técnicos preservam nomes anteriores — repo `SignallQ`, banco `linkaKotlin.db`, worker `linka-ai-diagnosis-worker` — são técnicos, não a marca. A marca é **SignallQ**.

## Stack

- **Android** Kotlin + Jetpack Compose (Material 3), MVVM + `StateFlow`
- **DI** Hilt · **Persistência** Room (`SignallQDatabase`) + DataStore · **Background** WorkManager (`MonitoramentoWorker`)
- **IA** Cloudflare Worker (`integrations/cloudflare/ai-diagnosis-worker`), URL via `BuildConfig.AI_WORKER_URL` — provider primário Gemini 2.0 Flash (quando `GEMINI_API_KEY` configurada), fallback Qwen3 30B MoE FP8 (Cloudflare Workers AI)
- **Analytics** Firebase Analytics + Crashlytics
- minSdk/targetSdk/compileSdk e versões em `android/gradle/libs.versions.toml` · JVM 17 (build e CI)

## Arquitetura

Módulos Gradle listados em `android/settings.gradle.kts` (fonte de verdade); visão por módulo em [`docs/02-arquitetura.md`](docs/02-arquitetura.md).

- **app** — shell, navegação por abas (Início, Velocidade, Histórico, Ferramentas; `AppShellNavigation.kt`), DI
- **core** — infraestrutura e regras compartilhadas (rede, banco, datastore, telefonia, permissões, recomendação, diagnóstico, relatório, NDS, sonda de jogo, feature flags)
- **feature** — uma por domínio (home, speedtest, wifi, devices, dns, fibra, router, diagnostico, history, settings)

Features são independentes entre si (sem dependência cruzada `:feature*` → `:feature*`).

## Como rodar localmente

```bash
# Build de debug
cd android && ./gradlew assembleDebug

# Testes unitários
cd android && ./gradlew test

# Lint
cd android && ./gradlew ktlintCheck detekt
```

Requer JDK 17 e o `android/app/google-services.json` (já versionado).

## Release (resumo)

Fluxo completo em [`docs/operations/RELEASE.md`](docs/operations/RELEASE.md) e checklist na skill `checar-release`. Em resumo: bump de `versionCode` em `libs.versions.toml` + `CHANGELOG.md`, depois `git tag vX.Y.Z && git push origin vX.Y.Z` dispara `release.yml` (publica na trilha `beta`). Produção é disparo manual do mesmo workflow. Publicação exige aprovação explícita do Luiz (ver `AGENTS.md`).

Worker Cloudflare: mudança em `integrations/cloudflare/<worker>/src/` é deployada à parte com `npx wrangler deploy` na pasta do worker.

## CI

`.github/workflows/android-ci.yml` roda em PR/push para `main` (quando `android/` muda): **ktlint**, **detekt**, **testes unitários** e **build debug**. `docs-ci.yml` valida a documentação. Detalhes em [`docs/operations/ci-cd.md`](docs/operations/ci-cd.md).

## Subprojetos no repositório

- `integrations/cloudflare/ai-diagnosis-worker/` — worker de diagnóstico IA
- `integrations/cloudflare/game-latency-probe-worker/` — worker de sonda de latência para jogos
- `integrations/cloudflare/signallq-admin-worker/` — worker do painel admin (o painel em si vive em `buildea-admin`)
- `integrations/cloudflare/signallq-diagnostic-worker/` — worker de diagnóstico de rede
- `integrations/cloudflare/signallq-privacy-worker/` — worker da política de privacidade

O painel Admin (React/Vite/TS) e o site/PWA pertencem aos repositórios `buildea-admin` e
`signallq-web`, respectivamente (ver ADR-016) — não vivem neste repositório.

## Documentação

Documentação viva para agentes em [`docs/`](docs/README.md). Personas de agentes legadas
arquivadas em `docs/archive/ai-governance/legacy-agents/` — não participam do roteamento ativo.
