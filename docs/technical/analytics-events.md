---
title: "Contrato de Eventos — Firebase Analytics (funil e jornada)"
description: "Eventos Firebase do funil principal SIG-155, da jornada guiada, do NDS e do Recommendation Engine, conferidos contra o código; eventos do schema SIG-134 ficam em analytics-events-schema.md."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Contrato de Eventos — Firebase Analytics (funil e jornada)

**Fonte de verdade:** código — `AnalyticsHelper` (`android/core/network/.../AnalyticsHelper.kt`) e `FirebaseAnalyticsHelper` (`:app`) para o funil SIG-155; `FirebaseAnalyticsTracker` (`:app`) para a jornada guiada; `RecommendationAnalytics.kt` (`:core:recommendation`) para recomendação.
**Escopo:** eventos *por nome em português* do funil e da jornada. Os eventos `feature_used`, `screen_view`, `app_session_start`, `feature_crash`, `battery_snapshot` e as user properties estão em [`analytics-events-schema.md`](analytics-events-schema.md).
**Substitui:** a versão anterior (704 linhas), que misturava eventos implementados com ~25 eventos propostos nunca instrumentados (`onboarding_concluido`, `speedtest_erro`, `diag_erro`, `ia_laudo_erro`, `wifi_*`, `historico_*`, `dns_*`, `fibra_*`, `dispositivos_*`, `ajustes_*`). Recuperável via `git log -- docs/technical/analytics-events.md`; um evento novo entra aqui só quando existir no código.

## Convenções

- `snake_case`, sem acento/espaço/hífen, prefixo da feature (`speedtest_`, `diag_`, `ia_`, `diagnostico_`, `app_`, `recommendation_`).
- Tipos: `String`, `Long`, `Double`, `Boolean`. Sem PII (SSID, BSSID, IP, localização, texto livre, identificador de aparelho). Enums como String em minúsculas; unidade no nome (`_ms`, `_mbps`, `_pct`).
- Limites Firebase: 25 parâmetros por evento, nome até 40 caracteres, valor String até 100.
- Todo evento do funil SIG-155 anexa `versao_app` automaticamente (não está na assinatura dos métodos).
- Instrumentação por injeção Hilt de `AnalyticsHelper` (funil) ou `AnalyticsTracker` (SIG-134) em ViewModel/classe de domínio — nunca `FirebaseAnalytics` direto, nem `logEvent` em Composable.
- `tipo_conexao`: `"wifi" | "mobile" | "ethernet" | "desconectado" | "desconhecido"`.

## Funil principal SIG-155 — 5 de 7 eventos disparam

```
app_aberto → speedtest_iniciado → speedtest_concluido → diag_iniciado → diag_concluido
   → ia_laudo_solicitado [órfão] → ia_laudo_recebido [órfão]
```

| Evento | Parâmetros (além de `versao_app`) | Ponto de disparo |
|---|---|---|
| `app_aberto` | `tipo_conexao`; `primeira_abertura` (Boolean, opcional, **não preenchido hoje**) | `MainActivity.onCreate` — `tipo_conexao` pode vir `desconhecido` porque o monitor de rede só inicia em `onStart` |
| `speedtest_iniciado` | `modo` (`fast`\|`complete`), `tipo_conexao` | `SpeedtestViewModel`, só para speedtest explícito do usuário (monitoramento passivo não conta) |
| `speedtest_concluido` | `modo`, `tipo_conexao_inicio`, `tipo_conexao_fim`, `download_mbps`, `upload_mbps`, `latencia_ms`, `jitter_ms`, `perda_pct`, `bufferbloat_ms`, `severidade_bufferbloat`, `stability_score`, `contaminado`, `duracao_ms` (opcional) | `SpeedtestViewModel` após `ExecutorSpeedtest.executar` |
| `diag_iniciado` | `tipo_conexao`, `areas_habilitadas` (CSV de `DiagnosticArea`, opcional), `tem_speedtest` | `DiagnosticOrchestrator.executar` |
| `diag_concluido` | `tipo_conexao`, `status_geral` (`ok\|info\|attention\|critical\|inconclusive`), `decisao_id`, `score_conexao` (0–100), `confianca` (0–1), `n_resultados_criticos`, `n_resultados_attention` (opcionais) | `DiagnosticOrchestrator.executar`, só no caminho de sucesso |
| `ia_laudo_solicitado` | `schema_version`, `prompt_version`, `status_diag_local`, `tem_feedback_usuario` | **órfão** |
| `ia_laudo_recebido` | `schema_version`, `prompt_version`, `status_ia`, `source`, `modelo_ia`, `prompt_tokens`, `completion_tokens`, `total_tokens`, `latencia_ms` | **órfão** |

- `modo` de `speedtest_*` só assume `fast`/`complete` desde GH#1737 (o modo `triplo` foi removido); eventos históricos podem trazer `"triplo"`.
- **Órfãos:** os métodos existem em `AnalyticsHelper`/`FirebaseAnalyticsHelper` (e têm teste), mas não há call site em produção desde `740f558b` (2026-07-13, GH#937). `MainViewModel.analisarProblema()`, o fluxo real de "Análise avançada", não chama `analyticsHelper`. Decisão pendente: reconectar o disparo ali ou remover os dois eventos do contrato. Enquanto isso o drop `ia_laudo_solicitado` → `ia_laudo_recebido` não é observável no Firebase.
- Testes: `app/src/test/.../analytics/FirebaseAnalyticsHelperTest.kt` cobre os 7 métodos.

## Jornada guiada de diagnóstico (`FirebaseAnalyticsTracker`)

Valores são IDs fechados e tipados; SSID, BSSID, IP, localização, texto livre e IDs de aparelho são proibidos. `analise_id` (UUID efêmero da jornada) correlaciona os passos; `diagnostic_id` identifica a decisão de recomendação, não a jornada. Não substituem `diag_iniciado`/`diag_concluido`, que pertencem ao ciclo do motor.

| Evento | Parâmetros | Observação |
|---|---|---|
| `diagnostico_objetivo_selecionado` | `objetivo`, `origem`, `retomada` | Assist contextual (#1656) |
| `diagnostico_pergunta_respondida` | `objetivo`, `pergunta_id`, `resposta_id`, `retomada` | idem |
| `diagnostico_guiado_abandonado` | `etapa`, `objetivo` (opcional), `retomavel` | só no Voltar explícito; restauração/recomposição não gera evento |
| `diagnostico_plano_iniciado` | `analise_id`, `objetivo`, `capacidades`, `qtd_capacidades`, `plano_adaptado` | Task 2.0.09 (#1657) |
| `diagnostico_reteste_iniciado` | `analise_id` (original), `reteste_id`, `acao_anterior_id` (vazio se retestou sem agir), `intervalo_ms`, `mesmo_contexto_rede` | só para "Testar novamente" vinculado a uma análise (`MainViewModel.testarNovamenteVinculado`); análise nova do zero não dispara |
| `diagnostico_comparacao_concluida` | `analise_id`, `reteste_id`, `veredito` (`melhorou\|nao_mudou\|piorou\|inconclusiva`), `comparavel`, `status_anterior`, `status_novo` | compara só com medição da mesma rede (`MedicaoDao.buscarUltimaComparavelNaRede`, `calcularVereditoReteste`) |

## NDS (telemetria operacional de rollout)

Medem o comportamento de `NdsDiagnosticRepository` (`feature/diagnostico`) ao chamar o Network Diagnostics Service, não o resultado para o usuário. Um par de eventos por chamada a `NdsClient.evaluate()` (nunca por bloco; o fallback local sem chamada ao NDS não gera evento).

- **`diag_nds_outcome`**: `outcome` (`success|remote_inconclusive|known_error|unknown_error`), `fallback_local_usado`, `latencia_ms`, `error_code` (opcional, ausente em sucesso), `versao_app`.
- **`nds_snapshot_enviado`** (cobertura do `DiagnosticSnapshot`, ADR-018): `schema_version`, `blocks_present` (CSV de nomes de bloco), `qtd_blocks_present`, `fields_present_count`, `missing_critical_blocks` (CSV; crítico = `connection`/`speed` sempre, `wifi` em Wi-Fi, `mobile` em móvel), `ai_invoked`, `ai_provider` (opcional), `duration_ms`, `result_confidence` (opcional), `outcome`, `versao_app`. Só nomes de bloco, nunca conteúdo. Análise em `core/nds` (`analyzeNdsSnapshotCoverage`).
- Em build de debug, `NdsDiagnosticRepository.logCoverageEmDebug` loga por bloco (`bloco=present` / `bloco=missing:motivo`) via Timber; não é analytics.

## Recommendation Engine (`recommendation_*`)

Seis eventos de `RecommendationAnalyticsEventName` (issue #790): `recommendation_eligible`, `_shown`, `_clicked`, `_dismissed`, `_feedback`, `_fallback_ad_shown`. Parâmetros: `recommendation_id`, `type`, `score`, `diagnostic_id` (opcional), `monetized`, `rule_origin`, `feedback` (opcional). O payload **não carrega `matched_tags`** de propósito (GH#1703/#1717: a conclusão do diagnóstico não é "evento anônimo de uso" segundo a política de privacidade); como `recommendation_id` mapeia 1:1 para a tag no catálogo local, o canal derivado continua aberto — decisão de produto na issue #1730.

## Como manter

Mudou, entrou ou saiu evento ou parâmetro: atualize este arquivo (ou `analytics-events-schema.md`, para SIG-134) no mesmo PR. Evento novo passa por `/analytics-spec` antes de implementar.
