---
title: "Monitoramento Passivo — MonitoramentoWorker"
description: "Background monitoring de qualidade de rede (latência, DNS, Wi-Fi) e notificações de alerta."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "2.0.0"
---

# Monitoramento Passivo — MonitoramentoWorker

- **Fonte de verdade:** código — `android/app/src/main/kotlin/io/signallq/app/monitoramento/` (`MonitoramentoWorker.kt`, `MonitoramentoScheduler.kt`, `HisteresiHelper.kt`) e `MonitoramentoSheet.kt`
- **Escopo:** mecânica técnica do worker. Comportamento de produto, alertas, histerese, persistência, permissões, OEMs, testes e riscos estão em [`features/monitoramento-alertas.md`](../features/monitoramento-alertas.md).
- **Substitui:** a versão 1.1.0, que repetia a página de feature e afirmava que não existe cooldown nem teto diário de notificações — o código tem os dois (`SignallQNotificationHelper.kt`). Limiares e contenção não são copiados aqui: ficam no código.

## Fluxo

```
WorkManager (PeriodicWorkRequest, policy KEEP)
  ↓
MonitoramentoWorker.doWork()
  ├─ medirLatenciaHttp()   — amostras paralelas, mediana
  ├─ medirDnsResolveTime() — resolução de nome, com timeout
  ├─ medirRssiWifi()       — RSSI do Wi-Fi conectado
  ↓
aplicarHisterese(latencia, dns, rssi) — HisteresiHelper (lógica pura)
  ↓
persistirMedicaoMonitor() — MedicaoEntity (fonte="monitor") no Room via MedicaoDao
```

Medição direta + histerese + persistência; sem IA nem orquestração de fases. O fluxo de IA ("Análise avançada", `MainViewModel.analisarProblema()`) é acionado pelo usuário no diagnóstico — ver `technical/AI_FLOW.md`.

## Scheduling

Framework: WorkManager (`MonitoramentoScheduler.kt`).

- **Período:** `MonitoramentoScheduler.INTERVALO_MINUTOS`, usado pelo `PeriodicWorkRequestBuilder<MonitoramentoWorker>` **e** pela UI para comunicar a frequência real (`MonitoramentoSheet.kt`, issue #1666). Mudar só um dos lados quebra a honestidade da comunicação.
- **Tag:** `linka_monitoramento_passivo`.
- **Policy:** `ExistingPeriodicWorkPolicy.KEEP` (não duplica o work já agendado).
- **Toggle do usuário:** `PreferenciasAppRepository.monitoramentoAtivoFlow`.

## Medições por execução

| Medição | Método | Timeout | Detalhe |
|---|---|---|---|
| Latência HTTP | `medirLatenciaHttp()` | `CALL_TIMEOUT_MS` por amostra (`CONNECT_TIMEOUT_MS` + `READ_TIMEOUT_MS`) | amostras paralelas (`async`/`awaitAll`) contra `https://speed.cloudflare.com/__down?bytes=0`; usa a mediana |
| DNS | `medirDnsResolveTime()` | `DNS_TIMEOUT_MS` | `InetAddress.getByName("cloudflare.com")`; mede o tempo de resolução |
| RSSI Wi-Fi | `medirRssiWifi()` | — | `ConnectivityManager`/`WifiInfo` (Android 12+) ou `WifiManager` (legado); sem valor, devolve o motivo (`SemWifi`/`SemPermissao`/`Invalido`) |

Métrica `null` (ex.: Doze interrompeu a medição) mantém o estado anterior e não força transição.
