// GH#1442 (parte de #952) — suite de paridade entre as 14 regras REC-01..REC-14 do motor
// local Kotlin (`RecommendationEngine.kt`, casos dourados em `RecommendationEngineTest.kt`,
// android/feature/diagnostico) e o motor do worker (`bundled-ruleset.ts` + findings derivados
// de `evaluateDerivedFindings`, diagnostic-engine.ts).
//
// Tabela de rastreabilidade completa (status COBERTA/PARCIAL/PENDENTE por REC, com motivo de
// cada divergencia de threshold/logica): docs/technical/PARIDADE_REC_WORKER_2026-07-26.md
//
// Regra desta suite: reaproveitar os MESMOS valores numericos dos casos dourados do Kotlin
// sempre que o worker converge no mesmo threshold; quando os thresholds divergem (documentado
// na tabela), usar um valor ajustado que faca AMBOS os motores concordarem (convergencia real),
// nunca fingir equivalencia que nao existe. Nenhuma regra nova foi criada no worker para fechar
// gap — REC-04/REC-12/REC-13 (PENDENTE, sem equivalente) nao tem teste aqui.
//
// node:test (nao Vitest — o corpo original da issue #1442 citava Vitest, mas o worker usa
// `"test": "node --test"` no package.json, mesmo runner de test/index.test.ts).

import assert from "node:assert/strict";
import test from "node:test";
import worker from "../src/index.ts";

function jsonRequest(url: string, body: unknown): Request {
  return new Request(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

interface ReportPayload {
  wifiResultados: Array<{ id: string }>;
  internetResultados: Array<{ id: string }>;
  mobileResultados: Array<{ id: string }>;
  fibraResultados: Array<{ id: string }>;
  dnsResultados: Array<{ id: string }>;
  historicoResultados: Array<{ id: string }>;
  wifiCanalResultados: Array<{ id: string }>;
  redeResultados: Array<{ id: string }>;
}

async function evaluate(snapshot: Record<string, unknown>): Promise<ReportPayload> {
  const response = await worker.fetch(
    jsonRequest("https://example.com/diagnostic/evaluate", { schemaVersion: 6, ...snapshot }),
    {},
  );
  assert.equal(response.status, 200);
  return (await response.json()) as ReportPayload;
}

function hasRule(cards: Array<{ id: string }>, ruleId: string): boolean {
  return cards.some((card) => card.id === ruleId);
}

// ---------------------------------------------------------------------------
// REC-05 — Bufferbloat (COBERTA, thresholds identicos ao Kotlin/MetricClassifier)
// ---------------------------------------------------------------------------

test("REC-05 paridade: bufferbloat 10ms nao dispara em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 5 - nao mostra quando bufferbloat abaixo de 30ms` (bufferbloatMs=10.0)
  const payload = await evaluate({
    quality: { latencyMs: 10.0, jitterMs: 2.0, packetLossPercent: 0.0, loadedLatencyMs: 10.0 },
  });
  assert.ok(!hasRule(payload.internetResultados, "bufferbloat_elevated"));
  assert.ok(!hasRule(payload.internetResultados, "bufferbloat_critical"));
});

test("REC-05 paridade: bufferbloat 45ms dispara atencao (elevated) nos dois motores", async () => {
  // Kotlin: `situacao 5 - mostra bufferbloat atencao quando maior que 30ms` (bufferbloatMs=45.0)
  const payload = await evaluate({
    quality: { latencyMs: 10.0, jitterMs: 2.0, packetLossPercent: 0.0, loadedLatencyMs: 45.0 },
  });
  assert.ok(hasRule(payload.internetResultados, "bufferbloat_elevated"));
  assert.ok(!hasRule(payload.internetResultados, "bufferbloat_critical"));
});

test("REC-05 paridade: bufferbloat 150ms dispara critico nos dois motores", async () => {
  // Kotlin: `situacao 5 - mostra bufferbloat critico quando maior que 100ms` (bufferbloatMs=150.0)
  const payload = await evaluate({
    quality: { latencyMs: 10.0, jitterMs: 2.0, packetLossPercent: 0.0, loadedLatencyMs: 150.0 },
  });
  assert.ok(hasRule(payload.internetResultados, "bufferbloat_critical"));
});

// ---------------------------------------------------------------------------
// REC-08 — Gateway / roteador lento (COBERTA, mesmo threshold rttGateway>50ms)
// ---------------------------------------------------------------------------

test("REC-08 paridade: rttGateway 15ms nao dispara em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 8 - nao mostra quando rtt gateway dentro do normal` (rttGatewayMs=15)
  const payload = await evaluate({
    quality: { latencyMs: 20.0, jitterMs: 3.0, packetLossPercent: 0.0 },
    gateway: { rttMs: 15 },
  });
  assert.ok(!hasRule(payload.redeResultados, "derived_decisao_gw_02"));
});

test("REC-08 paridade: rttGateway 80ms dispara gateway lento nos dois motores", async () => {
  // Kotlin: `situacao 8 - mostra gateway lento quando rtt maior que 50ms` (rttGatewayMs=80)
  const payload = await evaluate({
    quality: { latencyMs: 20.0, jitterMs: 3.0, packetLossPercent: 0.0 },
    gateway: { rttMs: 80 },
  });
  assert.ok(hasRule(payload.redeResultados, "derived_decisao_gw_02"));
});

// ---------------------------------------------------------------------------
// REC-11 — Perda de pacotes (COBERTA nos thresholds numericos; PARCIAL porque o
// worker nao tem campo `packetLossSource`, ver tabela de paridade)
// ---------------------------------------------------------------------------

test("REC-11 paridade: perda de 4% dispara critico nos dois motores", async () => {
  // Kotlin: `situacao 11 - mostra perda de pacotes como conclusao forte quando fonte e medicao confiavel` (perdaPercentual=4.0)
  const payload = await evaluate({
    quality: { latencyMs: 20.0, jitterMs: 3.0, packetLossPercent: 4.0 },
  });
  assert.ok(hasRule(payload.internetResultados, "packet_loss_critical"));
});

// ---------------------------------------------------------------------------
// REC-02 — Distancia do roteador (PARCIAL — worker dispara com Wi-Fi fraco
// isolado via `derived_decisao_04_wifi`, sem exigir link speed baixo junto;
// convergencia testada apenas no cenario onde ambos concordam)
// ---------------------------------------------------------------------------

test("REC-02 paridade: RSSI muito fraco em 2.4GHz sem problema de internet dispara aviso de Wi-Fi nos dois motores", async () => {
  // Kotlin: `situacao 2 - mostra distancia do roteador quando rssi fraco e link speed baixo sem problema externo` (rssiDbm=-78, linkSpeedMbps=24)
  const payload = await evaluate({
    wifi: { band: "2_4_GHZ", rssiDbm: -78, linkSpeedMbps: 24 },
  });
  assert.ok(hasRule(payload.redeResultados, "derived_decisao_04_wifi"));
});

test("REC-02 paridade: RSSI bom nao dispara aviso de Wi-Fi em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 2 - nao mostra so com download baixo isolado sem rssi fraco` (rssiDbm=-55)
  const payload = await evaluate({
    wifi: { band: "5_GHZ", rssiDbm: -55, linkSpeedMbps: 150 },
    speed: { downloadMbps: 10.0, uploadMbps: 5.0 },
    quality: { latencyMs: 20.0, jitterMs: 5.0, packetLossPercent: 0.0 },
  });
  assert.ok(!hasRule(payload.redeResultados, "derived_decisao_04_wifi"));
});

// ---------------------------------------------------------------------------
// REC-03 — Canal Wi-Fi congestionado (PARCIAL — agregacao propria do worker,
// `derived_wifi_channel_congested`; convergencia testada com o mesmo cenario
// de 8 redes no mesmo canal do fixture Kotlin)
// ---------------------------------------------------------------------------

test("REC-03 paridade: 8 redes vizinhas no mesmo canal disparam canal congestionado nos dois motores", async () => {
  // Kotlin: `situacao 3 - mostra canal congestionado quando scan indica alternativa bem melhor`
  // (8 redes canal=1, rssiDbm=-40, conectadoCanal=1, banda 2.4GHz)
  const payload = await evaluate({
    wifi: { band: "2_4_GHZ", rssiDbm: -55, linkSpeedMbps: 150 },
    wifiScan: {
      connectedChannel: 1,
      networks: Array.from({ length: 8 }, (_, i) => ({ channel: 1, rssiDbm: -40, ssid: `vizinha${i}` })),
    },
  });
  assert.ok(hasRule(payload.wifiCanalResultados, "derived_wifi_channel_congested"));
});

test("REC-03 paridade: sem scan de vizinhanca nao dispara canal congestionado em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 3 - nao mostra sem scan`
  const payload = await evaluate({
    wifi: { band: "2_4_GHZ", rssiDbm: -55, linkSpeedMbps: 150 },
  });
  assert.ok(!hasRule(payload.wifiCanalResultados, "derived_wifi_channel_congested"));
});

// ---------------------------------------------------------------------------
// REC-01 — Troca para Wi-Fi 5GHz (PARCIAL — threshold de download e gate de
// RSSI/exclusao de problema externo diferem; convergencia testada no cenario
// de disparo simples e no de "ja esta em 5GHz")
// ---------------------------------------------------------------------------

test("REC-01 paridade: 2.4GHz com 5GHz disponivel e download baixo dispara troca de banda nos dois motores", async () => {
  // Kotlin: `situacao 1 - mostra troca para 5GHz quando 2,4GHz com link baixo e 5GHz forte disponivel no mesmo SSID`
  // (downloadMbps=15.0 — abaixo tanto do limiar Kotlin (<25) quanto do limiar do worker (<50))
  const payload = await evaluate({
    wifi: { band: "2_4_GHZ", rssiDbm: -55, linkSpeedMbps: 65, has5GhzAvailable: true },
    speed: { downloadMbps: 15.0, uploadMbps: 10.0 },
    quality: { latencyMs: 20.0, jitterMs: 5.0, packetLossPercent: 0.0 },
  });
  assert.ok(hasRule(payload.wifiResultados, "wifi_24ghz_slow_with_5ghz_available"));
});

test("REC-01 paridade: ja conectado em 5GHz nao dispara troca de banda em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 1 - nao mostra quando conectado em 5GHz`
  const payload = await evaluate({
    wifi: { band: "5_GHZ", rssiDbm: -55, linkSpeedMbps: 65 },
    speed: { downloadMbps: 15.0, uploadMbps: 10.0 },
    quality: { latencyMs: 20.0, jitterMs: 5.0, packetLossPercent: 0.0 },
  });
  assert.ok(!hasRule(payload.wifiResultados, "wifi_24ghz_slow_with_5ghz_available"));
});

// ---------------------------------------------------------------------------
// REC-06 — DNS lento (PARCIAL — worker usa limiar absoluto de latencia,
// Kotlin compara contra o melhor DNS com margem; convergencia testada com
// latencia acima do limiar mais alto dos dois — 150ms do worker)
// ---------------------------------------------------------------------------

test("REC-06 paridade: DNS a 200ms dispara recomendacao de troca nos dois motores", async () => {
  // Adaptado de `situacao 6 - mostra troca de DNS quando ha alternativa melhor com margem segura`
  // (Kotlin fixture original usava 120ms/melhor 20ms — dispara no Kotlin mas NAO no worker,
  // que so passa a considerar DNS lento acima de 150ms; usamos 200ms para convergir nos dois).
  const payload = await evaluate({
    dns: { latencyMs: 200 },
    quality: { latencyMs: 20.0, jitterMs: 5.0, packetLossPercent: 0.0 },
  });
  assert.ok(hasRule(payload.dnsResultados, "dns_latency_elevated"));
});

test("REC-06 paridade: DNS ja no melhor provedor (60ms) nao dispara recomendacao em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 6 - nao mostra quando ja esta no melhor DNS` (currentDnsLatencyMs=60)
  const payload = await evaluate({
    dns: { latencyMs: 60 },
    quality: { latencyMs: 20.0, jitterMs: 5.0, packetLossPercent: 0.0 },
  });
  assert.ok(!hasRule(payload.dnsResultados, "dns_latency_elevated"));
  assert.ok(!hasRule(payload.dnsResultados, "dns_latency_high"));
});

// ---------------------------------------------------------------------------
// REC-07 — Operadora / rota externa (PARCIAL — worker so olha latencia
// (>200ms), sem jitter/perda, e limiar mais alto que o Kotlin (>100ms);
// convergencia testada acima dos dois limiares)
// ---------------------------------------------------------------------------

test("REC-07 paridade: rttGateway baixo com latencia bem acima do normal sugere problema externo nos dois motores", async () => {
  // Adaptado de `situacao 7 - mostra suspeita de operadora ...` (Kotlin fixture original usava
  // latencyMs=180 — dispara no Kotlin (>100) mas NAO no worker (exige >200); usamos 220ms).
  const payload = await evaluate({
    wifi: { band: "5_GHZ", rssiDbm: -55, linkSpeedMbps: 300 },
    quality: { latencyMs: 220.0, jitterMs: 5.0, packetLossPercent: 0.0 },
    gateway: { rttMs: 4 },
  });
  assert.ok(hasRule(payload.redeResultados, "derived_decisao_gw_01"));
});

// ---------------------------------------------------------------------------
// REC-09 — Fibra/ONT com problema (PARCIAL — worker so cobre RX critico, sem
// TX nem temperatura, sem nivel de atencao)
// ---------------------------------------------------------------------------

test("REC-09 paridade: RX fora da faixa dispara problema critico de fibra nos dois motores", async () => {
  // Kotlin: `situacao 9 - mostra problema na fibra quando RX fora da faixa` (rxPowerDbm=-30.0)
  const payload = await evaluate({ fiber: { rxPowerDbm: -30.0 } });
  assert.ok(hasRule(payload.fibraResultados, "fiber_rx_power_critical"));
});

test("REC-09 paridade: todos os indicadores de fibra bons nao disparam nada em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 9 - nao mostra quando todos os indicadores da fibra estao bons` (rxPowerDbm=-18.0)
  const payload = await evaluate({ fiber: { rxPowerDbm: -18.0 } });
  assert.ok(!hasRule(payload.fibraResultados, "fiber_rx_power_critical"));
});

// ---------------------------------------------------------------------------
// REC-10 — Rede movel fraca (PARCIAL — worker so olha RSRP/SINR, sem RSRQ,
// sem tabela por tecnologia)
// ---------------------------------------------------------------------------

test("REC-10 paridade: RSRP 4G muito ruim dispara sinal movel fraco nos dois motores", async () => {
  // Kotlin: `situacao 10 - mostra sinal movel fraco quando RSRP 4G ruim (abaixo de -100)` (rsrpDbm=-110)
  const payload = await evaluate({ mobile: { technology: "4G", rsrpDbm: -110 } });
  assert.ok(hasRule(payload.mobileResultados, "mobile_signal_poor_5g"));
});

test("REC-10 paridade: SINR 5G negativo dispara sinal movel fraco nos dois motores", async () => {
  // Kotlin: `situacao 10 - mostra sinal movel fraco quando SINR 5G ruim (abaixo de 0)` (sinrDb=-2)
  const payload = await evaluate({ mobile: { technology: "5G", sinrDb: -2 } });
  assert.ok(hasRule(payload.mobileResultados, "mobile_signal_poor_5g"));
});

test("REC-10 paridade: metricas moveis boas nao disparam nada em nenhum dos dois motores", async () => {
  // Kotlin: `situacao 10 - nao mostra quando metricas moveis estao boas` (rsrp=-70, rsrq=-8, sinr=25)
  const payload = await evaluate({ mobile: { technology: "4G", rsrpDbm: -70, sinrDb: 25 } });
  assert.ok(!hasRule(payload.mobileResultados, "mobile_signal_poor_5g"));
});

// ---------------------------------------------------------------------------
// REC-14 — Upgrade de roteador recorrente (PARCIAL — worker usa medias 7d/30d
// de `historical`, Kotlin usa booleano `degradationDetected`; nao ha forma de
// traduzir o mesmo fixture Kotlin 1:1 para o formato do worker, entao aqui so
// demonstramos que a condicao propria do worker dispara com dados realistas
// de degradacao, sem comparar valor a valor com o Kotlin — ver tabela)
// ---------------------------------------------------------------------------

test("REC-14 paridade (condicao propria do worker): degradacao forte nas medias 7d/30d dispara aviso de historico", async () => {
  const payload = await evaluate({
    historical: {
      testsCount7d: 6,
      testsCount30d: 12,
      avgDownload7d: 40,
      avgDownload30d: 100, // queda de 60% no download
      avgUpload7d: 15,
      avgUpload30d: 18,
      avgPing7d: 25,
      avgPing30d: 20,
      avgDns7d: 30,
      avgDns30d: 28,
    },
  });
  assert.ok(hasRule(payload.historicoResultados, "derived_history_degradation_critical"));
});

test("REC-14 paridade (condicao propria do worker): sem historico suficiente nao dispara aviso", async () => {
  const payload = await evaluate({
    historical: { testsCount7d: 2, testsCount30d: 3 },
  });
  assert.ok(!hasRule(payload.historicoResultados, "derived_history_degradation_critical"));
  assert.ok(!hasRule(payload.historicoResultados, "derived_history_degradation_warning"));
});
