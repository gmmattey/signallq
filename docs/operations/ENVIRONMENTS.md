---
title: "Ambientes"
description: "Ambientes locais, de CI e de backend do app Android e dos 5 Workers Cloudflare."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-10"
version: "2.1.0"
---

# Ambientes

- **Fonte de verdade:** `android/gradle/libs.versions.toml` (build), `integrations/cloudflare/*/wrangler.toml` (Workers)
- **Escopo:** ambientes locais/CI do app Android e dos 5 Workers Cloudflare

## Não há staging

O app **não** tem backend de staging: todos os builds, de qualquer trilha da Play Console (`beta`, `production`) ou do Firebase App Distribution, usam os **mesmos** Workers de produção. Não há product flavor nem endpoint alternativo por ambiente; `build.gradle.kts` só distingue `debug`/`release` (assinatura e feature flags). A validação pré-release é feita com build local e teste manual.

## Local

Android Studio, Gradle Wrapper, Android SDK (`local.properties`), emulador ou device. Assinatura local: `android/key.properties` + `android/segredos/signallq.jks` (ver `SIGNING.md`). Scripts: `SCRIPTS.md`.

## CI

GitHub Actions, JDK 17 (Temurin); ver `ci-cd.md`. Release e trilhas: `RELEASE.md`.

## Cloudflare Workers (`integrations/cloudflare/`)

| Pasta | `name` no `wrangler.toml` | Função |
|---|---|---|
| `ai-diagnosis-worker` | `linka-ai-diagnosis-worker` | Diagnóstico por IA |
| `signallq-admin-worker` | `signallq-admin` | Backend do Console/Admin (D1 `signallq-admin-db`) |
| `signallq-diagnostic-worker` | `signallq-diagnostic` | Diagnóstico/telemetria (D1 `signallq-diagnostic-db`) |
| `signallq-privacy-worker` | `signallq-privacy` | Política de privacidade e termos (público) |
| `game-latency-probe-worker` | `signallq-game-latency-probe` | Probe de latência para jogos |

Cada Worker tem seu `wrangler.toml`, bindings e secrets; não há arquivo único. Modelos e fallbacks de IA: `docs/03-especificacao-tecnica.md` e `docs/CONTRATOS/`.

## Hosts públicos dos Workers

Todos os Workers respondem em `*.gmmattey.workers.dev`. O subdomínio antigo `*.giammattey-luiz.workers.dev` **não resolve mais** (conferido em 2026-10-10). Os hosts ficam fixos em `BuildConfig` (`:app` e `:featureDiagnostico`), então uma versão do app publicada com o host errado só se corrige com nova versão; `WorkerHostsBuildConfigTest` (nos dois módulos) falha se o host antigo voltar. Workers de outra conta: `linka-assist-relay` e `network-diagnostics-service`, em `*.buildealabs.workers.dev`.

## Site público (`web/`)

- **Hospedagem:** Vercel, projeto `signallq-web` (conta `buildea-projects`); sem integração Git, publicação só pelo workflow manual `web-deploy-vercel.yml` (ou pela Vercel CLI de dentro de `web/`).
- **Domínios:** `signallq.com` (redireciona `www`) e `speedtest.signallq.com` (rewrite para `/teste-de-velocidade`). O DNS está na **Hostinger**; o subdomínio usa um registro `A speedtest → 76.76.21.21`.
- **Telemetria:** o site envia eventos para `/api/track`, que repassa ao `signallq-admin` com a secret `SITE_INGEST_KEY`. O **mesmo valor** precisa existir como secret do Worker (`wrangler secret put SITE_INGEST_KEY`) e como variável `SITE_INGEST_KEY` de produção na Vercel.
- **Secrets e variáveis (só os nomes):** Worker — `DISCORD_WEBHOOK_URL`, `SITE_INGEST_KEY`; Vercel — `SITE_INGEST_KEY`; GitHub Actions — `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` (necessários para o workflow de deploy).
- Detalhes: `web/AGENTS.md`, `web/docs/deploy-vercel.md`.

## Riscos

- Sem staging, mudança em Worker afeta todos os usuários imediatamente; valide antes de `npx wrangler deploy`.
- Keystore e `key.properties` são geridos manualmente fora do git (`SIGNING.md`).
