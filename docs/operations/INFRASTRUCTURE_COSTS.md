---
title: "Custos de infraestrutura"
description: "Inventário verificado dos recursos de infraestrutura do SignallQ e estimativas de custo (valores monetários e limites de plano não verificados no repositório)."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-10"
version: "1.3.0"
---

# Custos de Infraestrutura — SignallQ

- **Fonte de verdade do inventário:** `integrations/cloudflare/*/wrangler.toml`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `.github/workflows/`
- **Fonte de verdade de uso e cobrança real:** painéis Cloudflare, Firebase/Google Cloud, Play Console e GitHub (não acessíveis pelo repositório)
- **Escopo:** o que existe de infraestrutura, o que deliberadamente não existe, e estimativa de custo

> **Legenda:** ✅ comprovado no repositório · ⚠️ NÃO VERIFICADO (limite/preço de plano, estimativa de uso ou valor monetário; conferir no painel antes de decidir qualquer coisa). Nenhum plano pago está declarado no repositório; "free tier" aqui é o plano presumido, não comprovado.

## 1. Inventário de recursos

### Cloudflare Workers (5 neste repositório) ✅

| Worker (`name` no wrangler) | Pasta | D1 | Cron | Outros bindings |
|---|---|---|---|---|
| `signallq-admin` | `signallq-admin-worker` | `signallq-admin-db` (`DB`) | `*/15 * * * *` (snapshot de latência/uptime), `0 6 * * *` (sync de telemetria, hoje desligado por `FIREBASE_SYNC_ENABLED="false"`) e `0 12 * * *` (informe diário de visitas do site no Discord, 09:00 BRT; secret `DISCORD_WEBHOOK_URL`) | service binding `DIAGNOSTIC_WORKER` → `signallq-diagnostic` |
| `signallq-diagnostic` | `signallq-diagnostic-worker` | `signallq-diagnostic-db` (`DB`) | `0 * * * *` (de hora em hora) | `observability` habilitada |
| `linka-ai-diagnosis-worker` | `ai-diagnosis-worker` | — | — | `[ai]` binding `AI` (Workers AI); service binding `ADMIN_WORKER` → `signallq-admin` |
| `signallq-game-latency-probe` | `game-latency-probe-worker` | — | — | — |
| `signallq-privacy` | `signallq-privacy-worker` | — | — | — |

Workers consumidos pelo app mas **fora deste repositório**: `linka-assist-relay` (status de serviço) e `network-diagnostics-service` (módulo `:core:nds`), ambos na conta `buildealabs`. Custo deles não é coberto aqui.

### O que NÃO existe ✅

- **KV, R2, Queues, Durable Objects:** nenhum binding em nenhum `wrangler.toml`. R2 foi descartado em 2026-07-14 por decisão de produto (exigiria cartão cadastrado na Cloudflare); logos de operadora ficam em BLOB base64 no D1 (`provider_assets`).
- **BigQuery / export GA4:** nunca criado. O projeto Firebase `signallq-app` está sem billing por decisão do Luiz; a perna de sync está desligada (`FIREBASE_SYNC_ENABLED="false"`).
- **Firebase Cloud Storage, Firestore, Auth, Messaging, Performance:** nenhuma dependência no Gradle.
- **Deploy do site em Cloudflare Pages:** workflows `site-deploy` e `pages-deploy` estão `.disabled` (o de Pages desativado em 2026-07-16). O site (`web/`) é publicado na **Vercel** (projeto `signallq-web`, conta `buildea-projects`, plano gratuito) pelo workflow manual `web-deploy-vercel.yml`; o DNS de `signallq.com` fica na Hostinger. O painel vive em `buildea-admin`.

### Cloudflare D1 (2 bancos) ✅

`signallq-admin-db` e `signallq-diagnostic-db`. Tamanho real, linhas lidas/escritas e plano: ⚠️ ver painel.

### IA ✅ (configuração) / ⚠️ (custo)

- **Provider primário:** Google Gemini via API (`generativelanguage.googleapis.com`), modelo `gemini-flash-latest` (alias móvel), ativo só quando a secret `GEMINI_API_KEY` está configurada (`ai-diagnosis-worker/src/providers.ts`). Se o alias passar a resolver para um modelo pago, o custo muda sem alteração no repositório.
- **Fallback:** `@cf/qwen/qwen3-30b-a3b-fp8` (`AI_MODEL`) via Workers AI. Política do projeto: Llama/Meta não é configurado. Sem os dois, o app usa fallback local sem IA externa.
- Se a secret existe de fato no ambiente de produção e qual tier a chave Gemini usa: ⚠️ ver painel Cloudflare (secrets) e Google AI Studio.

### Firebase (projeto `signallq-app`) ✅

| Serviço | Evidência |
|---|---|
| Crashlytics | `libs.firebase.crashlytics`; mapping enviado no `release.yml` |
| Analytics | `libs.firebase.analytics`; propriedade GA4 `543555227` configurada no admin worker |
| Remote Config | `libs.firebase.config` em `:app` e `:core:featureflags`; chaves de anúncios (`ads_native_enabled` + por tela) |
| App Distribution | workflow `firebase-distribution.yml` (disparo manual, secret `FIREBASE_TOKEN`) |

Plano (Spark/Blaze) do projeto: ⚠️ não declarado no repositório; as notas do `wrangler.toml` indicam "Sandbox, sem billing", conferir no Firebase Console.

### Anúncios (AdMob) ✅

`play-services-ads` + `user-messaging-platform` (UMP/consentimento) em `:app`. Anúncios nativos controlados por Remote Config; `-PadsEnabled=true` só é aceito pelo `release.yml` com `playTrack=production`. Receita e estado da conta AdMob: ⚠️ ver painel AdMob.

### Google Play ✅ / ⚠️

Publicação por `release.yml` (trilha padrão `beta`) e `promote-release.yml`, via secret `PLAY_SERVICE_ACCOUNT_JSON`. Taxa da conta de desenvolvedor (US$ 25, único) e câmbio (R$ ~130): ⚠️ não verificado, estimativa antiga.

### GitHub Actions ✅ / ⚠️

Workflows ativos: `android-ci`, `docs-ci`, `release`, `promote-release`, `firebase-distribution`, `auto-move-board`, `auto-update-branch`. Minutos consumidos por mês e se o repositório é privado no plano gratuito: ⚠️ ver Settings > Billing (a estimativa "~200 min/mês" do doc anterior não tinha fonte).

## 2. Limites de plano e estimativas — ⚠️ NÃO VERIFICADO

Os valores abaixo vieram da versão anterior deste documento (estimativas de 2026-07). Não há medição, fatura nem dado de painel no repositório que os confirme. Limites de planos mudam; confira na página oficial de cada fornecedor.

| Recurso | Limite presumido (plano gratuito) | Uso estimado (1k usuários) | Estado |
|---|---|---|---|
| Workers requests/dia | 100.000 | ~5.000 | ⚠️ |
| Workers CPU por invocação | 10 ms | ~3–5 ms | ⚠️ |
| D1 rows lidas/dia | 5.000.000 | ~50.000 | ⚠️ |
| D1 rows escritas/dia | 100.000 | ~5.000 | ⚠️ |
| D1 armazenamento | 5 GB | ~100 MB | ⚠️ |
| Workers AI neurons/dia | 10.000 | ~300 neurons/request no fallback Qwen | ⚠️ |

Crons somam 96 execuções/dia no admin (15 min) + 2 (sync diário e informe do Discord) + 24 no diagnostic, contados nas requisições do Workers ✅ (conta, não limite).

## 3. Custo total estimado — ⚠️ NÃO VERIFICADO

| Fase | Custo mensal | Notas |
|---|---|---|
| Atual (app em `beta`) | R$ 0 presumido | Nenhum plano pago declarado no repositório; conferir faturas |
| ~5k usuários | R$ 0–50 | Estimativa de 2026-07, sem base de medição |
| 10k+ usuários | R$ 50–200 | Estimativa de 2026-07; cogita Workers Paid (US$ 5/mês, preço não verificado) |

Novo custo recorrente, fornecedor pago ou billing em projeto hoje sem billing exige aprovação explícita do Luiz (AGENTS.md §10).

## 4. Gatilhos de upgrade — ⚠️ limiares presumidos, não verificados

| Gatilho | Ação |
|---|---|
| ~80% das requisições/dia do Workers | Avaliar Workers Paid |
| ~80% dos neurons/dia | Reduzir uso do fallback Qwen ou migrar plano |
| ~80% das leituras D1/dia | Avaliar plano pago do D1 |
| Consumo de minutos do GitHub Actions próximo do limite | Otimizar CI |

## 5. Monitoramento

- **Uso Cloudflare no app admin:** `GET /admin/cloudflare-usage` (exige secret `CLOUDFLARE_API_TOKEN` com escopo Account Analytics: Read; sem ela responde "não disponível" em vez de inventar número).
- **Uso de IA:** `GET /admin/metrics/ai-usage`. **Inteligência de diagnóstico:** `GET /admin/diagnostics/intelligence`.
- **Consoles:** Cloudflare Dashboard, Firebase Console (Crashlytics/Analytics), Play Console, AdMob, GitHub Settings > Billing.

## 6. Decisões registradas

1. **Sem banco pago:** D1 atende; sem Supabase, PlanetScale ou similar.
2. **Sem R2:** descartado em 2026-07-14 (ver acima).
3. **Sem billing no Firebase/GCP:** decisão do Luiz, motivo da perna GA4→BigQuery desligada (reativar exige billing, vínculo GA4→BigQuery e `FIREBASE_SYNC_ENABLED="true"`).
4. **IA:** Gemini primário e Qwen3 30B fallback; ver `docs/03-especificacao-tecnica.md`.

## 7. Pendências de checagem pelo Luiz (painéis)

- **Cloudflare:** plano da conta (Free ou Paid); uso real de requests, D1 (linhas e tamanho) e neurons; se a secret `GEMINI_API_KEY` está de fato configurada no `linka-ai-diagnosis-worker`; se `CLOUDFLARE_API_TOKEN` foi criado; se `linka-assist-relay` e `network-diagnostics-service` estão na mesma conta e plano.
- **Firebase / Google Cloud:** plano do projeto `signallq-app`; tier da chave Gemini; se existe algum custo de Crashlytics/Analytics/Remote Config.
- **Play Console / AdMob:** custo e estado da conta; receita de anúncios.
- **GitHub:** minutos de Actions consumidos e plano do repositório.
