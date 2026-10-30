---
title: "ADR-017 — Motor de diagnóstico e IA migram para o NDS (Network Diagnostics Service)"
description: "O motor local (core/diagnostico), o ai-diagnosis-worker e o signallq-diagnostic-worker (shadow) são substituídos por um serviço externo único, o NDS, que unifica scoring e explicação em IA."
type: "adr"
status: "ativo"
owner: "Luiz"
last_updated: "2026-08-19"
version: "1.1.0"
---

# ADR-017 — Motor de diagnóstico e IA migram para o NDS

- **Status:** Aceito
- **Data:** 2026-08-19
- **Autor:** Luiz
- **Substitui:** o papel de `core/diagnostico` (motor local), `ai-diagnosis-worker` (explicação em IA)
  e `signallq-diagnostic-worker` (shadow mode, motor remoto sem IA, nunca promovido).
- **Investigação de origem:** issue [#1742](https://github.com/buildea-labs/signallq/issues/1742)
  (inventário completo de consumidores, gaps de coleta e perguntas em aberto).

## Contexto

O SignallQ acumulou três caminhos paralelos de diagnóstico, nenhum deles unificado:

1. **Motor local** (`core/diagnostico`: `InternetDiagnosticEngine`, `MetricClassifier`,
   `DiagnosticReport`) — roda on-device, síncrono, alimenta praticamente toda a superfície de
   diagnóstico do app (Início, Resultado de Velocidade, Laudo, Histórico, Sinal, Dispositivos).
   Tem dois vocabulários de severidade paralelos (`MetricStatus` de 6 faixas e `DiagnosticStatus`
   de 5 valores) reconciliados via `comSeveridadeConciliada()`.
2. **`ai-diagnosis-worker`** — Worker Cloudflare separado, único call site de produção em
   `MainViewModel.analisarProblema()` via `AiDiagnosisRepository.explainDiagnosis`, só cobre a
   parte de explicação em linguagem natural.
3. **`signallq-diagnostic-worker`** — endpoint `/diagnostic/evaluate`, rodando em shadow mode em
   produção: cópia remota sem IA do motor local, com fallback de 3 níveis
   (`REMOTE`→`CACHED_LOCAL`→`BUNDLED_LOCAL`), nunca promovido a autoritativo, aguardando validação
   de paridade que nunca terminou.

O NDS (Network Diagnostics Service, `network-diagnostics-service.buildealabs.workers.dev`,
`POST /v1/diagnostics/evaluate`) é um serviço externo novo que unifica scoring e explicação em IA
num único contrato modular, recebendo o mesmo tipo de snapshot de rede que o app já coleta
on-device (wifi, wifiScan, speed, dns, fiber).

## Decisão

### O NDS substitui os três caminhos existentes

- **Motor local** (`core/diagnostico`) é removido depois que todos os consumidores migrarem para
  o NDS. `MetricClassifier` (função pura, sem I/O) migra sua lógica de classificação para o NDS
  também — sem duplicar régua de severidade entre cliente e servidor.
- **`ai-diagnosis-worker`** é descontinuado — a explicação em IA passa a vir do módulo `ai` da
  resposta do NDS.
- **`signallq-diagnostic-worker`** (shadow mode) é descontinuado — o investimento anterior em
  motor remoto sem IA fica absorvido pelo NDS, não mantido em paralelo.

### Exceção: zero conectividade

O único cenário em que o motor local faria sentido sobreviver é quando o dispositivo está sem
nenhuma rede (não "rede ruim" — sem Wi-Fi e sem dados móveis), porque nesse caso o app não
consegue nem chamar o NDS. A investigação em #1742 confirmou que **esse caminho não existe hoje**
no motor local — o único tratamento especial encontrado (`ExecutorFibra.marcarSemRede()`) evita
consultar o modem local sem rede, mas não é uma classificação de diagnóstico. Decisão: **não
construir isso como feature nova** nesta migração. Se o app ficar sem conseguir diagnosticar em
zero conectividade, isso é um gap conhecido e aceito, não um requisito desta migração.

### Autenticação

O NDS usa Bearer token estático. Decisão consciente do Luiz: o token fica embutido no cliente
Android, com o risco de extração por engenharia reversa (descompilação do APK) explicitamente
aceito. Isso **não dispensa** a regra de higiene do repositório de nunca versionar segredos em
texto puro — o token entra via `local.properties`/`BuildConfig` (gitignorado), nunca hardcoded em
arquivo `.kt` commitado. Quem implementar a camada de rede (fatia NDS-01) não deve escrever o
valor real do token em nenhum arquivo do repositório; a inserção do valor é manual, feita pelo
Luiz (ou por quem tiver o token) no ambiente local/CI.

### Sequenciamento com o épico #1647

A migração para o NDS entra **antes** de continuar o épico #1647 (Android 2.0 por fatias
verticais). Da issue #1647, 12 de 28 fatias de topo já fecharam (última: #1659, 2.0.11); nenhuma
fatia nova (#1660 em diante) abre até o NDS estar pronto — evita migrar telas visualmente e ter
que retocar a leitura de dados de novo quando o formato do relatório mudar.

## Contrato conhecido do NDS

Documentado a partir de testes reais (`curl`) e leitura do código-fonte do NDS pelo Luiz — sujeito
a completar conforme a implementação avançar.

**Request** (`POST /v1/diagnostics/evaluate`, `Authorization: Bearer <token>`):

```json
{
  "request_id": "req-12345",
  "app": { "id": "com.buildea.signallq", "version": "2.4.0" },
  "locale": "pt-BR",
  "profile": "gamer",
  "capabilities": ["scoring", "ai", "wifi", "fiber"],
  "connection": { "type": "WIFI", "ssid": "MinhaRede_5G", "bssid": "00:14:22:01:23:45" },
  "wifi": { "rssi": -65, "band": "5GHz", "channel": 36, "linkSpeed": 433, "standard": "802.11ac" },
  "wifiScan": { "channelCongestion": 15, "bestChannel": 149 },
  "speed": { "ping_ms": 151, "jitter_ms": 40, "download_mbps": 300, "upload_mbps": 150, "packet_loss_percent": 2 },
  "dns": { "primary": "8.8.8.8", "responseTime_ms": 35, "hijacked": false },
  "fiber": { "rxPower_dbm": -22, "txPower_dbm": 2.5, "temperature_c": 45, "voltage_v": 3.3 }
}
```

**Resposta de sucesso (200 OK):** modular, um item por módulo avaliado em `results[]`, mais um
`recommendation` de topo (ação única sugerida, ou `null`):

```json
{
  "recommendation": null,
  "results": [
    { "module": "diagnostics.wifi", "module_version": "1.0.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "matched_rules": [] }, "cards": [] },
    { "module": "scoring", "module_version": "1.1.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "score": 50, "veredicto": "regular", "tipo_conexao": "WIFI", "observed_dimensions": 1, "dimensoes": [] } },
    { "module": "ai", "module_version": "1.5.0", "request_id": "req-12345", "warnings": [], "missing_inputs": [], "result": { "tokens_used": 0, "ai_model_used": "copy-catalog", "fallback_used": false, "explanation_source": "copy_catalog", "explanation_status": "catalog_hit", "explanation": { "titulo_amigavel": "...", "resumo_tecnico_traduzido": "..." }, "source_finding_ids": [] } }
  ],
  "traces": [
    { "module": "wifi", "duration_ms": 2, "status": "ok" },
    { "module": "ai", "duration_ms": 15, "status": "ok", "source": "copy_catalog" }
  ]
}
```

Observação importante para o design do cliente: os módulos retornados **não mapeiam 1:1** com a
lista `capabilities` pedida — no teste real, `capabilities: ["scoring", "ai"]` ainda devolveu um
resultado do módulo `diagnostics.wifi`. O cliente deve buscar por `module` na lista `results[]`,
nunca assumir posição fixa ou presença garantida só porque foi pedida em `capabilities`.

### Contrato canônico (PR [buildea-labs/network-diagnostics-service#12](https://github.com/buildea-labs/network-diagnostics-service/pull/12))

Fecha as lacunas que este ADR listava como pendentes. **Status: PR aberto, CI verde
(`npm run typecheck && npm test`), ainda em draft no repositório do NDS — não confirmado como
implantado em produção.** Tratar como o desenho canônico a seguir; reconfirmar contra o endpoint
real antes de depender dele em produção.

- **Envelope de erro único**, para toda falha pública (`400`, `401`, `429`, `500`, `503`, `504`):
  ```json
  { "error": { "code": "NDS_TIMEOUT", "message": "...", "retryable": true }, "request_id": "..." }
  ```
  Códigos estáveis: `INVALID_INPUT`, `UNAUTHORIZED`, `RATE_LIMITED`, `NDS_TIMEOUT`,
  `AI_UNAVAILABLE`, `SERVICE_UNAVAILABLE`, `NOT_FOUND`, `INTERNAL_ERROR`. `request_id` é gerado
  antes de autenticação/validação, presente mesmo em 4xx/5xx. Substitui o formato flat
  `{error, message}` dos exemplos de `401`/`429` acima — esses exemplos ficam como registro
  histórico do que foi observado antes do contrato formal existir.
- **Timeout de avaliação** configurável via `EVALUATE_TIMEOUT_MS` (padrão 10000ms, mínimo 1000,
  máximo 30000) — estouro devolve `504 NDS_TIMEOUT`, `retryable: true`.
- **`profile` fechado em 5 valores**: `general`, `gamer`, `streaming`, `wfh`, `smarthome`. Valor
  fora do conjunto é rejeitado com `400 INVALID_INPUT`.
- **`capabilities` redefinido como evidência**, não mistura mais com output desejado: `wifi`,
  `wifi_scan`, `fiber`, `mobile`, `dns`, `historical`, `gateway`, `local_equipment`. O que o
  cliente quer de volta (`scoring`, `ai`) agora é `requested_outputs`, campo novo — separado de
  `capabilities`. `capabilities: ["ai", "scoring"]` (o formato usado nos exemplos deste ADR e já
  implementado na fatia NDS-01/02a) continua aceito como **alias legado durante a transição**, não
  quebra o cliente atual, mas integrações novas devem usar `requested_outputs`.
- **Matriz de cobertura por domínio** publicada em
  [`docs/signallq-2-coverage.md`](https://github.com/buildea-labs/network-diagnostics-service/blob/e15a4ad64c86ee18119e3e711a112538fc6d90a3/docs/signallq-2-coverage.md)
  do repositório do NDS — usar como fonte para decidir se uma fatia NDS-02x pode migrar (domínio
  "Coberto") ou deve esperar (domínio "Parcial"/"Lacuna", rastreado nas issues P1
  [buildea-labs/network-diagnostics-service#13](https://github.com/buildea-labs/network-diagnostics-service/issues/13)
  ou P2 [#14](https://github.com/buildea-labs/network-diagnostics-service/issues/14) do repo do
  NDS). Regra do NDS, idêntica em espírito à deste ADR: "o cliente coleta/mede evidências; o NDS
  interpreta, cruza dados, gera findings, score e Next Best Action" — execução local de speedtest,
  scan Wi-Fi, leitura de rede móvel e descoberta de dispositivos são responsabilidade do cliente,
  nunca lacuna do NDS.

## Consequências

- **Perde:** diagnóstico funcionando sem chamada de rede. O motor local garantia classificação
  mesmo com dados parciais porque rodava on-device; o NDS exige que o dispositivo consiga alcançar
  a rede para ser diagnosticado — paradoxal apenas no caso de zero conectividade total, que fica
  como gap aceito (ver seção acima).
- **Ganha:** fonte única de verdade para scoring e explicação, elimina a reconciliação entre
  `MetricStatus` e `DiagnosticStatus` (`comSeveridadeConciliada()` deixa de ser necessária depois
  da migração completa).
- **Muda a superfície de custo:** cada diagnóstico vira uma chamada a um serviço externo pago/
  limitado (rate limit confirmado), diferente do motor local, que era grátis e instantâneo.
- **Remove dívida:** `signallq-diagnostic-worker` (shadow, nunca promovido) e sua validação de
  paridade pendente saem de cena.

## Alternativas consideradas

- **Manter o motor local como fallback do NDS** — rejeitada. O Luiz optou por fonte única de
  verdade em vez de manter dois motores de classificação sincronizados.
- **Manter `signallq-diagnostic-worker` em paralelo ao NDS** — rejeitada. Dois motores remotos
  mirando o mesmo objetivo é duplicação sem propósito claro.

## Pendências

### Resolvidas pelo contrato canônico (PR #12 do NDS, ver seção acima)

- ~~Formato de resposta em erro genérico/timeout~~ — envelope único definido, `NDS_TIMEOUT` com
  timeout configurável.
- ~~Regra de mapeamento `profile`/`capabilities`~~ — vocabulário fechado do lado do NDS.
  Follow-up de implementação registrado (não bloqueante, nada em produção depende disso ainda):
  o mapper que a NDS-02a (#1747, já mergeada) construiu usa o modelo antigo (`capabilities`
  misturando evidência com output, `profile` omitido para "general" em vez de enviado
  explicitamente) — funciona via alias legado, mas precisa de uma fatia de ajuste para o modelo
  `capabilities`/`requested_outputs` quando o PR #12 do NDS sair de draft e for confirmado em
  produção.

### Seguem em aberto

- Se vale priorizar a coleta de `dns.hijacked` (gap de coleta confirmado — não existe hoje;
  domínio DNS está listado como "Parcial" na matriz de cobertura do NDS, P1).
- Quebra completa em fatias de migração (NDS-01 em diante) — proposta inicial no inventário de
  #1742, refinada em #1746 (NDS-02, 11 sub-fatias + remoção final), cruzada com a matriz de
  cobertura do NDS para confirmar que cada fatia migra só domínio já "Coberto" do lado do NDS.
