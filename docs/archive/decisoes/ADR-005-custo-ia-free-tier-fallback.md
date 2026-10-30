# ADR-005: Modelo de custo de IA e fallback (free tier)

**Data:** 2026-06-23
**Status:** Accepted

## Contexto

O diagnóstico por IA do SignallQ usa dois provedores, ambos no **free tier**, com degradação graciosa:

1. **Gemini** (Google AI Studio) — provedor primário.
2. **Qwen** (Cloudflare Workers AI) — fallback quando o Gemini atinge o limite do free tier.
3. **Análise local** (`AiFallbackFactory`, engines stateless do app) — quando ambos os provedores estouram o limite. Não há chamada de IA nem registro de uso.

Enquanto a operação permanece dentro do free tier dos dois provedores, **o custo monetário real é zero**.

O painel admin (`signallq-admin-worker`) registrava custo de IA em `ai_usage.cost_usd` usando uma taxa fixa fabricada (`total_tokens * 0.000000035`) quando o app não enviava `cost_usd`. Isso produzia valores financeiros fictícios no dashboard, sem relação com o custo real — que é zero (bug GitHub #234). O app, por sua vez, nunca calcula nem envia `cost_usd` (correto: o worker é a fonte autoritativa de preço).

## Decisão

O **worker** é a fonte autoritativa do custo. O cálculo passa a usar uma tabela de preço por modelo:

- Modelos do free tier (Gemini, Qwen via Workers AI) → **custo 0 por token**.
- Modelo desconhecido / sem tarifa cadastrada → 0 (a arquitetura atual é 100% free tier).
- Adotar um modelo **pago** é uma mudança deliberada: basta adicionar a tarifa em `AI_MODEL_RATE_USD` no worker — sem necessidade de release do app.

`cost_usd` enviado pelo app (hoje sempre nulo) continua tendo precedência se algum dia for preenchido.

O app continua enviando apenas `model` + tokens em `AiUsageIngestPayload`. O provedor é distinguível pelo `model` (`gemini-*` vs `@cf/qwen/*`). A análise local não gera registro em `ai_usage` — a taxa de fallback local é derivável correlacionando `diagnostic_sessions` sem `ai_usage` correspondente (fora do escopo deste ADR).

## Consequências

- O dashboard de custo reflete a realidade: **R$ 0 / $0** enquanto no free tier, em vez de um valor inventado.
- Métricas úteis hoje são **volume por provedor** (chamadas, tokens) e, futuramente, a **taxa de fallback local** — não dólares.
- Quando um provedor pago entrar, o custo passa a ser real automaticamente ao cadastrar a tarifa no worker.

## Implementação

- `integrations/cloudflare/signallq-admin-worker/src/index.ts`: helper `costForModel(model, totalTokens)` + tabela `AI_MODEL_RATE_USD`, substituindo a taxa fixa em `handleIngestAiUsage`.
- Frontend já trata ausência de métricas financeiras agregadas com estado vazio (PR #241, bugs #237/#238) — nenhum valor de custo é hardcoded no painel.

## Referências

- GitHub #234 — `cost_usd` nunca calculado; custo de IA sempre zero no D1.
- `docs/technical/AI_FLOW.md` — fluxo de IA e fallback.
- `docs/03-especificacao-tecnica.md` — workers e ingest do painel admin.
