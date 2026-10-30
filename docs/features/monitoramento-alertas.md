---
title: "Feature — Acompanhar conexão (monitoramento e alertas)"
description: "Monitoramento passivo em segundo plano (WorkManager), histerese, alertas de rede e dispositivo novo, e o gráfico de estabilidade que ele alimenta."
type: "feature"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
feature: "monitoramento-alertas"
tipo: "transversal"
modulos:
  - "android/app"
  - "android/core/datastore"
  - "android/core/database"
  - "android/core/featureflags"
  - "android/feature/history"
arquivos:
  - "android/app/src/main/kotlin/io/signallq/app/monitoramento/MonitoramentoWorker.kt"
  - "android/app/src/main/kotlin/io/signallq/app/monitoramento/MonitoramentoScheduler.kt"
  - "android/app/src/main/kotlin/io/signallq/app/monitoramento/HisteresiHelper.kt"
  - "android/app/src/main/kotlin/io/signallq/app/monitoramento/OemKillInfo.kt"
  - "android/app/src/main/kotlin/io/signallq/app/notificacao/SignallQNotificationHelper.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/MonitoramentoSheet.kt"
  - "android/app/src/main/kotlin/io/signallq/app/ui/screen/UptimeGridChart.kt"
  - "android/feature/history/src/main/kotlin/io/signallq/app/feature/history/UptimeChartUseCase.kt"
  - "android/core/datastore/src/main/kotlin/io/signallq/app/core/datastore/PreferenciasAppRepository.kt"
contratos: []
eventos:
  - "screen_view (screen_name=monitoramento)"
flags:
  - "consumer_settings_enabled"
testes:
  - "android/app/src/test/kotlin/io/signallq/app/monitoramento/MonitoramentoWorkerHistereseTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/monitoramento/MonitoramentoWorkerMedicaoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/monitoramento/DeteccaoDispositivoNovoTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/MonitoramentoSheetFrequenciaRealTest.kt"
  - "android/app/src/test/kotlin/io/signallq/app/ui/screen/HistoricoUptimeWiringCaracterizacaoTest.kt"
adrs: []
thresholds_em: "android/app/src/main/kotlin/io/signallq/app/monitoramento/HisteresiHelper.kt"
---

# Feature — Acompanhar conexão (monitoramento e alertas)

Migrado de `FUNCIONAL.md` (§5.9) e `technical/MONITORAMENTO_PASSIVO.md`. Fatos conferidos no código em 2026-10-04. Valores de limiar, teto e cooldown **não são copiados**: ficam em `HisteresiHelper.kt` (histerese) e `SignallQNotificationHelper.kt` (teto diário e cooldowns). O intervalo vem de `MonitoramentoScheduler.INTERVALO_MINUTOS`.

## Negócio

### 1. Problema e promessa

O usuário só descobre que a internet degradou quando já está sofrendo. A feature acompanha a conexão em segundo plano e avisa quando algo muda, sem prometer vigilância contínua. A promessa é a real: uma checagem periódica, limitada pela bateria e pelo WorkManager do Android (#1666, decisão de produto do Luiz, 2026-08-19).

### 2. Quando aparece e para quem

Entrada: card **Monitoramento** no hub Ferramentas, que abre `MonitoramentoSheet` (título "Diagnóstico avançado"). Para qualquer usuário que ligue o toggle; alertas dependem de permissão de notificação. Não há tela dedicada de monitoramento; toggles e notificações também aparecem em Ajustes (`technical/SCREEN_MAP.md`).

### 3. Regras de decisão

- **Dois toggles**, ambos pedindo confirmação para ligar (não para desligar): **Análise avançada** (sinais extras, avisa sobre bateria) e **Monitoramento passivo**.
- O subtítulo comunica a **frequência real** ("verifica a cada N minutos e pode enviar alertas"), derivada de `MonitoramentoScheduler.INTERVALO_MINUTOS`; há teste de regressão contra mudança do valor.
- **Quatro alertas individuais**, revelados com o monitoramento ativo: Sem internet, Latência alta, DNS lento, Sinal Wi-Fi fraco; cada um tem toggle próprio (`notificacao*AtivaFlow` em `PreferenciasAppRepository`).
- **Histerese:** notifica **só na transição** ok → alerta, nunca repetidamente; entra e sai por limiares distintos (`HisteresiHelper.kt`). "Sem internet" tem prioridade sobre os demais.
- **Contenção de notificação:** teto diário e cooldown por tipo em `SignallQNotificationHelper.disparar` (consolida o que o doc técnico anterior negava, ver Riscos).
- **Dispositivo novo na rede:** notificação disparada pelo app (`MainActivity.kt`, `MainViewModel.kt`), não pelo Worker, com cooldown próprio.
- Em fabricantes que matam processos em background, a sheet mostra aviso para manter o app sem restrição de bateria (`OemKillInfo.kt`).

### 4. Estados e honestidade

| Estado | Texto / ação |
|---|---|
| Métrica `null` (ex.: Doze interrompeu a medição) | Mantém o estado anterior; não força transição nem vira zero |
| RSSI sem valor | Retorna motivo (`SemWifi`/`SemPermissao`/`Invalido`) em vez de número |
| Sem latência e sem DNS | "Sem internet", que suprime os demais alertas |
| Flag `consumer_settings_enabled` desligada | Worker não mede, não persiste, não notifica nesta rodada; histórico salvo é preservado |
| Monitoramento ativo | Subtítulo com frequência real, nunca "acompanhamento contínuo" |
| Sem nenhum bloco medido nos últimos 7 dias | Seção de estabilidade no Histórico não aparece; volta ao estado vazio padrão |

### 5. Próximo passo e confirmação

O alerta leva o usuário de volta ao app para diagnosticar. O gráfico **"Estabilidade da conexão · últimos 7 dias"** no Histórico (`HistoricoScreen.kt`) confirma a evolução: agrupa medições em blocos, classifica cada bloco (OK, LENTO, LATENCIA_ALTA, OFFLINE, SEM_DADO; latência alta nunca é rotulada como offline, GH#1518) e lista eventos por dia com narrativa. A página de Histórico é dona dessa tela; aqui está só o vínculo.

### 6. Fora de escopo, status e flag

Fora: foreground service, medição de throughput, tabela dedicada de alertas, IA (o fluxo de IA é acionado pelo usuário, `technical/AI_FLOW.md`). Status: entregue. Flag: `consumer_settings_enabled` cobre a tela de Ajustes **e** o Worker (`MonitoramentoWorker.doWork`, GH#1480), conforme `consumer-catalog.json`.

## Técnico

### 7. Mapa de código

| Arquivo | Responsabilidade |
|---|---|
| `app/.../monitoramento/MonitoramentoScheduler.kt` | `PeriodicWorkRequest` do WorkManager, policy `KEEP`, tag `linka_monitoramento_passivo`, `INTERVALO_MINUTOS` (também lido pela UI) |
| `app/.../monitoramento/MonitoramentoWorker.kt` | `doWork`: checa a flag; mede latência HTTP (3 amostras paralelas contra `https://speed.cloudflare.com/__down?bytes=0`, usa a mediana), tempo de resolução DNS (`InetAddress.getByName("cloudflare.com")`) e RSSI (`ConnectivityManager`/`WifiInfo` no Android 12+, `WifiManager` no legado); aplica histerese; persiste medição sintética |
| `app/.../monitoramento/HisteresiHelper.kt` | Lógica pura de transição ok → alerta — **fonte dos limiares** |
| `app/.../notificacao/SignallQNotificationHelper.kt` | Canal "Monitoramento de rede", cooldown por tipo, teto diário, `notificarDispositivoNovo` |
| `app/.../ui/screen/MonitoramentoSheet.kt` | Sheet com toggles, alertas individuais e aviso de OEM |
| `app/.../monitoramento/OemKillInfo.kt` | Detecção de fabricantes que matam processos |
| `feature/history/.../UptimeChartUseCase.kt`, `app/.../ui/screen/UptimeGridChart.kt` | Blocos de estabilidade e gráfico no Histórico |
| `core/datastore/.../PreferenciasAppRepository.kt` | `monitoramentoAtivoFlow`, toggles por alerta, estado anterior da histerese, última verificação |
| `app/.../monitoramento/AdminSyncWorker.kt`, `AnalyticsOutboxProcessor.kt` | Outros workers na mesma pasta; **fora do escopo desta feature** |

Detalhe técnico anterior: `technical/MONITORAMENTO_PASSIVO.md` (histórico, ver Riscos).

### 8. Dados e contratos

Sem contrato OpenAPI. Cada execução grava uma `MedicaoEntity` (Room, `MedicaoDao`) com `fonte = "monitor"` e `connectionType = "monitor"`, só com latência; `downloadMbps`/`uploadMbps` ficam `null` (o monitor não mede throughput). Não existe tabela dedicada de alertas. O Worker roda com as condições de rede conectada e bateria não baixa. `networkId` fica `null` nas medições do Worker (não lê SSID/BSSID/operadora); a comparação de reteste trata `null` como "sem par comparável" (GH#1707, `ARQUITETURA/MODULOS/core-database.md`). Estado da histerese e toggles vivem em DataStore (`PreferenciasAppRepository`); cooldowns e contagem diária em `SharedPreferences` (`linka_notif_cooldown`).

### 9. Eventos e flags

- `screen_view` com `screen_name="monitoramento"` (`analytics-events-schema.md`, `TipoFerramenta.kt`). Se o sheet dispara o evento ao abrir: **não verificado**.
- Não há evento de analytics por alerta enviado ou por execução do Worker (nenhum encontrado no código).
- Flag: `consumer_settings_enabled` (`FeatureFlagKeys.kt`, `consumer-catalog.json`). Detalhe: `technical/feature-flags-remote-config.md`.
- Permissões: `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `INTERNET`, `POST_NOTIFICATIONS`.

### 10. Falhas e fallback

- Latência e DNS têm timeout próprio (`CALL_TIMEOUT_MS`, `CONNECT_TIMEOUT_MS`, `READ_TIMEOUT_MS`, `DNS_TIMEOUT_MS` em `MonitoramentoWorker.kt`); estouro vira métrica `null`, que mantém o estado.
- Flag desligada: execução ociosa que retorna sucesso; a próxima rodada reavalia a flag, sem cancelar o WorkManager.
- Medição sintética: só grava `latência`; ausência de download nunca vira zero.
- OEMs agressivos (Samsung em Doze sem exceção, MIUI atrasando por horas) alteram o intervalo real; Moto/AOSP respeitam as constraints padrão do WorkManager; a UI avisa em vez de prometer.

### 11. Testes

Lista em `testes:`: transições e limiares (`MonitoramentoWorkerHistereseTest`), persistência (`MonitoramentoWorkerMedicaoTest`), dispositivo novo (`DeteccaoDispositivoNovoTest`), copy de frequência real (`MonitoramentoSheetFrequenciaRealTest`) e wiring do gráfico (`HistoricoUptimeWiringCaracterizacaoTest`). Sem teste unitário para o teto diário e os cooldowns de `SignallQNotificationHelper`: **não encontrado** na pasta de testes.

### 12. Riscos

- **Doc técnico divergente (código vence):** `technical/MONITORAMENTO_PASSIVO.md` §5 afirma que "não existe cooldown temporal fixo nem teto de alertas/dia". O código tem ambos em `SignallQNotificationHelper.kt` (teto diário e cooldown por tipo), como diz `FUNCIONAL.md` §5.9. O doc técnico precisa ser reduzido na etapa de redução.
- Sem foreground service: o intervalo real pode passar bastante do nominal em Doze/economia de bateria (comportamento da plataforma).
- Latência e DNS usam domínios fixos (Cloudflare): uma degradação isolada deles aparece como falso alerta de rede.
- A pasta `monitoramento/` mistura workers de naturezas diferentes (Admin sync, outbox de analytics); fronteira da feature é só `MonitoramentoWorker`, `Scheduler` e `HisteresiHelper`.
- Dependência feature→feature evitada via `:app`, mas o gráfico de estabilidade mora em `feature/history`; mudança em classificação de blocos exige olhar as duas páginas.
