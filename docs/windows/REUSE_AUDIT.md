# Auditoria de reaproveitamento — Windows Desktop

Auditada em 2026-10-14 a partir de `main` (`73be0287`). O protótipo efetivamente localizado é
`/Users/giammattey/Downloads/Claude Design /SignallQ-Windows-Prototipo.html`; o diretório citado
na demanda não existe neste checkout.

| Recurso | Origem | Reaproveitamento | Estratégia |
|---|---|---|---|
| Interface e interação visual | protótipo HTML Windows | Total como referência | Preservar tokens, estrutura, telas e estados; substituir somente fontes simuladas por DTOs reais. |
| Marca e fontes | `brand/`, `android/app/src/main/res/font/` | Alto | Usar assets oficiais, sem redesenhar wordmark ou símbolo. |
| Speed test | `android/feature/speedtest/ExecutorSpeedtestCloudflare.kt`, `web/src/app/teste-de-velocidade/useSpeedTestEngine.ts` | Parcial | Implementar executor Windows no host nativo com as mesmas fases e vetores de caracterização. |
| Ping, jitter, perda | `PingExecutor.kt`, `AnalisadorAmostragemPing.kt` | Algoritmo/contrato | Portar após vetores de conformidade; perda por timeout HTTP continua estimativa, não perda ICMP afirmada. |
| Bufferbloat | `MetricClassifier.kt`, `SpeedtestQualityClassifier.kt` | Parcial | Calcular só com carga e probes válidos; sem amostra sob carga, `null` + limitação. |
| Diagnóstico | `android/core/diagnostico/`, ADR-017/018 | Contrato | Não executar Kotlin no Windows nem duplicar regras. Consumir NDS após auth desktop; até lá, indisponibilidade explícita. |
| Modo gamer | `core/probejogo/`, `game-latency-probe-worker` | Parcial | Sonda regional de referência, nunca ping de servidor oficial de jogo. |
| Monitoramento | `android/app/.../monitoramento`, `MedicaoEntity.kt` | Parcial | Recriar scheduler, notificações e persistência Windows; portar semântica de histerese. |
| Histórico | `core/database/MedicaoEntity.kt`, `feature/history/` | Contratual | SQLite local próprio, com status, nulidade, proveniência e migrações. |
| Assist | `ai-diagnosis-worker`, `android/core/nds` | Contrato | Não embarcar token ou chamar modelo direto; aguardar broker/autorização desktop. |

## Limites confirmados

`ConnectivityManager`, Room, DataStore, WorkManager e o código Kotlin/OkHttp são Android-bound.
O site em `web/` é uma landing Next.js, não PWA desktop nem fonte de persistência. O Worker
`signallq-diagnostic-worker` é transitório/shadow; NDS é a direção canônica.

## Gate

**Camillo: atendido.** O plano em `.agents/architecture-plan.md` define a nova fronteira. ADR-019
supersede a exclusão de Windows que constava em ADR-016.
