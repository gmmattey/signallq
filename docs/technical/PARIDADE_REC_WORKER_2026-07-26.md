---
title: "Paridade REC-01..REC-14 — motor local vs worker"
description: "Rastreabilidade entre as 14 regras REC-01..REC-14 do motor local (Kotlin, featureDiagnostico) e o ruleset do signallq-diagnostic-worker."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# Paridade REC-01..REC-14 (Kotlin) × worker (`signallq-diagnostic-worker`)

**Fonte de verdade:** `RecomendacaoPraticaEngine.kt` (`android/feature/diagnostico/src/main/kotlin/io/signallq/app/feature/diagnostico/`, casos dourados em `RecomendacaoPraticaEngineTest.kt`) e, no worker, `src/bundled-ruleset.ts` + `evaluateDerivedFindings` em `src/diagnostic-engine.ts`.
**Origem:** issue #1442 (parte de #952). Nenhuma regra nova foi criada no worker para fechar lacunas; elas são base para decisão de produto (estender `DiagnosticSnapshot` ou aceitar a divergência).
**Ressalva de validação:** a tabela abaixo foi montada em 2026-07-26. Em 2026-10-04 foram reconferidos só os caminhos, os ids de regra do worker e a existência da suíte `rec-parity.test.ts`; thresholds e condições por linha **não foram revalidados** contra o código atual — recruze antes de agir sobre uma linha.

## Como o worker avalia

1. **Regras declarativas** de `bundled-ruleset.ts` (`getBundledRuleset()`), avaliadas por `evaluateRule`/`evaluateGroup`.
2. **Findings derivados** (`evaluateDerivedFindings`): lógica em código para canal Wi-Fi congestionado, degradação histórica e `DECISAO-GW-01/02`. Contam para a paridade.

O `id` devolvido em `/diagnostic/evaluate` é o `matchedRuleId` (o `ruleId` da regra declarativa, ou um id sintético como `derived_decisao_gw_02`). Regras do worker sem REC correspondente (`internet_download_unavailable`, `packet_loss_critical`, `upload_zero`) são legítimas e estão fora desta paridade.

## Tabela de rastreabilidade

| REC | Situação (Kotlin) | Status | Equivalente no worker | Motivo / diferença |
|---|---|---|---|---|
| REC-01 | Troca para Wi-Fi 5GHz | **PARCIAL** | `wifi_24ghz_slow_with_5ghz_available` (`bundled-ruleset.ts`) | Worker: banda 2.4GHz + `has5GhzAvailable=true` + download<50Mbps. Kotlin: banda 2.4GHz + (linkSpeed<144 OU download<25) + `is5GhzCapable != false` + RSSI atual não muito fraco (>-70dBm) + achado principal não é problema externo. Threshold de download diferente (50 vs 25), sem gate de RSSI mínimo nem exclusão de problema externo no worker. |
| REC-02 | Distância do roteador / obstáculos | **PARCIAL** | `derived_decisao_04_wifi` (`WIFI_NEEDS_ATTENTION`, `evaluateDerivedFindings`) | Worker dispara com Wi-Fi fraco isolado (RSSI ruim por banda), sem exigir link speed baixo nem excluir problema externo explicitamente (a exclusão vem indiretamente de não haver finding de internet crítico/warning). Kotlin exige RSSI fraco **e** link speed<54 juntos, com exclusão explícita de achado externo. Semântica próxima, condição não idêntica. |
| REC-03 | Canal Wi-Fi congestionado | **PARCIAL** | `derived_wifi_channel_congested` (`WIFI_CHANNEL_CONGESTED`, `evaluateDerivedFindings`) | Worker: agregação própria (≥6 redes no scan, ≥4 sobrepondo o canal conectado com RSSI≥-70, janela ±4 canais em 2.4GHz). Kotlin usa `WifiChannelDiagnosticEngine.avaliar` (regra `WIFI-CANAL-01`), lógica e limiares próprios do motor local, não auditados linha a linha nesta task. Texto condicional de recorrência (Wi-Fi 6E/7) do Kotlin não tem equivalente no worker. |
| REC-04 | Roteador limitado (RSSI bom, link speed baixo) | **PENDENTE** | nenhum | `DiagnosticSnapshot` do worker não tem campos para `wifiStandard` (padrão Wi-Fi antigo) nem `velocidadeContratadaMbps` (plano contratado) — a condição do Kotlin depende desses dois. `wifi.devicesOnNetwork` existe no schema mas nenhuma regra do worker o usa. Não dá para portar sem antes estender `DiagnosticSnapshot`/`contracts.ts`. |
| REC-05 | Bufferbloat (atenção >30ms, crítico >100ms) | **COBERTA** | `bufferbloat_elevated` (WARNING, >30ms) + `bufferbloat_critical` (ERROR, >100ms) | Thresholds idênticos (GH#955 já alinhou com `MetricClassifier.classificarBufferbloat`). Melhor caso de paridade real da tabela. |
| REC-06 | DNS lento com alternativa melhor | **PARCIAL** | `dns_latency_elevated` (>150ms) / `dns_latency_high` (>300ms) | Worker usa limiar absoluto sobre `dns.latencyMs`. Kotlin compara DNS atual contra o melhor da comparação, com margem de segurança de 5ms, e não recomenda se já estiver no melhor DNS — lógica comparativa, não limiar absoluto. Sem campo `bestDnsLatencyMsFromComparison`/`bestDnsNameFromComparison` no `DiagnosticSnapshot` do worker. |
| REC-07 | Operadora / rota externa | **PARCIAL** | `derived_decisao_gw_01` (`ISP_PROBLEM_DETECTED`, `evaluateDerivedFindings`) | Worker: `gatewayRtt<10 && latencyMs>200 && !weakWifi` (uma condição fixa). Kotlin: `rttGateway<10` + Wi-Fi saudável (se houver) + **qualquer** de latência>100/jitter>20/perda≥1 (OR, não só latência). Worker não considera jitter nem perda nesta regra, e usa threshold de latência mais alto (200 vs 100). |
| REC-08 | Gateway / roteador lento | **COBERTA** (com ressalva) | `derived_decisao_gw_02` (`ROUTER_SLOW_RESPONSE`, `evaluateDerivedFindings`) | Mesmo threshold (`rttGateway>50`). Kotlin adiciona texto reforçado quando RSSI também está bom e distingue mensagem para rede móvel — worker não faz essa distinção de copy, mas a condição de disparo é equivalente. |
| REC-09 | Fibra/ONT com problema | **PARCIAL** | `fiber_rx_power_critical` (RX<-27dBm, ERROR) | Kotlin usa `FibraSignalQualityEngine.avaliar` cobrindo RX **e** TX fora de faixa **e** temperatura elevada, com dois níveis (atenção/crítico). Worker só cobre RX crítico — sem TX, sem temperatura, sem nível de atenção. |
| REC-10 | Rede móvel fraca | **PARCIAL** | `mobile_signal_poor_5g` (RSRP≤-110 OU SINR<0) | Kotlin usa `MetricClassifier` com tabela própria por tecnologia (4G/5G) e considera RSRP, RSRQ **e** SINR. Worker só verifica RSRP/SINR, sem RSRQ, e usa um único limiar (nomeado "5g" mas sem checar `mobile.technology` na condição) em vez de tabela por tecnologia. |
| REC-11 | Perda de pacotes | **PARCIAL** | `packet_loss_moderate` (≥1%, WARNING) + `packet_loss_critical` (≥3%, ERROR) | Thresholds numéricos idênticos aos do Kotlin (`perda<1.0` não mostra, `>=1.0` atenção, `>=3.0` crítico). Falta a distinção de `packetLossSource` (Kotlin não mostra nada se `naoMedido`/`unknown`, e marca como "indício" se `estimated`) — `DiagnosticSnapshot` do worker não tem esse campo. |
| REC-12 | Score geral (múltiplos fatores) | **PENDENTE** | nenhum | Meta-recomendação: conta quantas outras recomendações/achados relevantes já foram gerados (≥2 fora de REC-13/14) e resume. O motor de regras declarativas do worker não tem noção de "contar findings já gerados" como condição — arquitetura diferente (regra por regra, não meta-regra sobre o resultado agregado). |
| REC-13 | Preset de device para jogos | **PENDENTE** (fora de escopo por design) | nenhum | Depende de `GameReadinessClassifier` (3 categorias: FPS competitivo/cloud gaming/mobile competitivo) e do preset selecionado pelo usuário (`deviceGamingSelecionado`) — subsistema próprio, sem relação com `DiagnosticSnapshot`/`bundled-ruleset.ts`. O worker tem um catálogo de jogos (`game-catalog.ts`), mas é uma feature separada (perfis de jogo/latência por jogo), não parte do ruleset de diagnóstico. |
| REC-14 | Upgrade de roteador/mesh (recorrência) | **PARCIAL** | `derived_history_degradation_warning`/`derived_history_degradation_critical` (`evaluateDerivedFindings`) | Worker calcula degradação por médias 7d/30d (download/upload/ping/dns) com `testsCount7d>=5 && testsCount30d>=10`, sem olhar para Wi-Fi. Kotlin usa um booleano explícito (`historico.degradationDetected`) combinado com sinal Wi-Fi fraco/link baixo/banda 2.4GHz atual — condição e fonte de dado diferentes, mesmo conceito de "recorrência" por trás. |

## Resumo

- **Coberta (thresholds idênticos):** REC-05, REC-08, REC-11 (2 de 3 são "coberta com ressalva de
  campo ausente" — REC-08 tem diferença só de copy, REC-11 falta o campo `packetLossSource`).
- **Parcial (mesma intenção, condição/threshold diferente ou campo faltante):** REC-01, REC-02,
  REC-03, REC-06, REC-07, REC-09, REC-10, REC-14.
- **Pendente (sem equivalente algum):** REC-04 (campos ausentes no snapshot), REC-12 (arquitetura —
  meta-regra sobre findings agregados), REC-13 (subsistema separado, fora de escopo por design).

## Suíte cruzada

`integrations/cloudflare/signallq-diagnostic-worker/test/rec-parity.test.ts` (`node:test`; o worker não usa Vitest) reaproveita os cenários dos casos dourados do Kotlin contra `evaluateSnapshot`. Cobre positivo e negativo para as linhas **COBERTA** e as **PARCIAL** com condição testável (REC-05, 08, 11, 07, 14, 02, 03) e só os cenários de convergência para REC-01, 06, 09, 10; **PENDENTE** (REC-04, 12, 13) não tem teste.
