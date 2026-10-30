---
title: "AI Flow"
description: "Fluxo de diagnóstico assistido por IA no app Android, o ai-diagnosis-worker que o atende, o desvio via NDS e o fallback local."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.1.0"
---

# AI Flow — Android SignallQ

**Fonte de verdade:** código — `android/feature/diagnostico` (`ai/AiDiagnosisRepository.kt`, `ai/AiModels.kt`), `MainViewModel.analisarProblema()` e `integrations/cloudflare/ai-diagnosis-worker`. Documento derivado, não normativo.
**Contrato HTTP do worker:** [`CONTRATOS/openapi/ai-diagnosis-worker.yaml`](../CONTRATOS/openapi/ai-diagnosis-worker.yaml).
**Substitui:** a versão 1.x, que carregava o histórico da remoção do SignallQ Pulse (GH#1682) e uma lista de modelos descartados (recuperável via `git log -- docs/technical/AI_FLOW.md`).

## 1. Visão geral

O app envia os dados de diagnóstico a um Worker Cloudflare, que gera o laudo com LLM. Não há inferência local; o fallback local não usa LLM.

```
MainViewModel.analisarProblema()
  → coletarContextoAdicionalIa() + DiagnosisAiContextFactory.fromRaw()   [monta payload]
  → AiDiagnosisRepository.explainDiagnosis()   [POST via OkHttp]
      → linka-ai-diagnosis-worker              [Cloudflare Worker]
          → Gemini (primário) / Qwen3 30B MoE FP8 no Workers AI (fallback cloud)
      → AiDiagnosisResult
  → AiFallbackFactory.fromLocal                [timeout, erro HTTP/rede ou sem internet]
```

O relatório local que alimenta o payload vem de `DiagnosticOrchestrator.executar()` → `DiagnosticRunner.run()` (engines stateless de `:core:diagnostico`, tabela abaixo). O resultado aparece em `LaudoScreen`.

## 2. Worker e modelos

- **Endpoint:** `POST https://linka-ai-diagnosis-worker.gmmattey.workers.dev/api/ai/diagnostico-conexao` (`Content-Type: application/json`). Nome do worker em `wrangler.toml`: `linka-ai-diagnosis-worker`. Também expõe `GET /health`.
- **Provider primário:** Gemini, model id `gemini-flash-latest` (alias da Google; `providers.ts`), ativo quando a secret `GEMINI_API_KEY` está configurada.
- **Fallback cloud:** Cloudflare Workers AI `@cf/qwen/qwen3-30b-a3b-fp8` (`AI_MODEL` em `wrangler.toml`, `DEFAULT_MODEL` em `src/index.ts`). Sem a secret do Gemini, é o único provider.
- Llama/Meta não é padrão nem fallback (política do projeto). Persona da IA: "SignallQ".
- **Prompt:** `AI_PROMPT_VERSION = "diagnostico_v6_explicacao_humana"` (`src/index.ts`). Os achados do motor local entram como entrada e a IA refina e expande; o worker aceita schemas anteriores por retrocompatibilidade. Montagem do payload em `DiagnosisAiContextFactory.fromRaw()`: tipo de conexão, snapshot Wi-Fi, latência, jitter, perda, download/upload, DNS, histórico 7d/30d, ISP e configuração do usuário (plano, operadora, UF/cidade).
- **Telemetria:** o evento `ia_laudo_solicitado` está órfão (ver [`analytics-events.md`](analytics-events.md)).

## 3. Engines locais (`:core:diagnostico`, stateless)

| Engine | Entrada |
|---|---|
| `WifiSignalQualityEngine` | RSSI, frequência, link speed |
| `InternetDiagnosticEngine` | snapshot de internet, flag de Wi-Fi confiável |
| `WifiChannelDiagnosticEngine` | redes vizinhas, canal conectado |
| `DnsDiagnosticEngine` | IP do DNS, latência |
| `HistoricalDegradationEngine` | médias 7d/30d, tendência |
| `FibraSignalQualityEngine` | RX/TX, temperatura |
| `MobileSignalDiagnosticEngine` | RSRP, RSRQ, SINR, tecnologia |
| `FindingEngine` | achados de todos os engines → decisão final (herdou as regras do antigo `DiagnosticDecisionEngine`) |

## 4. Desvio via NDS (NDS-02k, ADR-017, issue #1746)

Com `consumer_diagnostico_nds_live_enabled` ligada (default e combinação com as demais flags: [`features/assist-diagnostico.md`](../features/assist-diagnostico.md)), o `relatorio` já vem do NDS (`DiagnosticOrchestrator.executarProtegido`) com a narrativa do módulo `ai` (`tituloAmigavel`/`resumoTecnicoTraduzido`) embutida em `relatorio.decisao` (`NdsDiagnosticsResponseMapper.toDiagnosticReport`, `:core:nds`). Nesse caso `analisarProblema()` **não chama** `AiDiagnosisRepository.explainDiagnosis()` nem o `NdsClient` de novo: `resolverResultadoAnaliseViaNds` (`MainViewModel.kt`) deriva o resultado do mesmo `relatorio` via `AiFallbackFactory.fromLocal`, sem round-trip adicional. Se o NDS falha, o `DiagnosticRunner` local assume (fallback).

Limite conhecido: `NdsClient` só expõe `POST /v1/diagnostics/evaluate` (e v2); não há endpoint NDS equivalente a `explainDiagnosis` (schema completo com `perguntasContextuais`, `hipotesesDescartadas`, `classificacaoTecnica` por dimensão). O texto autorrelatado do usuário (`problema`) nunca vai ao NDS.

## 5. Fallback local

`AiFallbackFactory` (`ai/AiModels.kt`) monta um `AiDiagnosisResult` a partir dos engines locais, sem texto de LLM, em timeout, erro HTTP/rede ou ausência de internet.

## 6. Persistência e custo

- O diagnóstico vive em `MainViewModel.snapshotDiagnostico` (StateFlow, sem Room).
- Não há chat conversacional (decisão de produto #564/SIG-282). As tabelas Room `chat_sessions`/`chat_messages` (`core/database/.../chat/`) seguem no schema mas estão órfãs; removê-las exige migration (dívida, higiene §9).
- Não há cota client-side de IA. O controle é server-side: `aiDailyBudgetUsd` em `admin_settings` dispara o alerta `AI_BUDGET` quando o custo das últimas 24h passa do limite (ver [`admin-api-schema.md`](admin-api-schema.md)); só alerta o painel, não bloqueia chamadas.

## 7. Riscos técnicos

- Sem cota client-side, um dispositivo anômalo pode gerar custo de IA; a salvaguarda é reativa (alerta `AI_BUDGET`).
- No fallback local o usuário recebe dados dos engines sem explicação em linguagem natural.
