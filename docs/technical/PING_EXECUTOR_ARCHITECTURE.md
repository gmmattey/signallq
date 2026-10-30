---
title: "PingExecutor — arquitetura e implementação"
description: "Medição HTTP de latência/jitter/perda (PingExecutor + AnalisadorAmostragemPing) usada pela tela Ping, pelo speedtest e pelo Modo gamer."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# PingExecutor — arquitetura e implementação

- **Fonte de verdade:** código (`android/feature/speedtest/src/main/kotlin/io/signallq/app/feature/speedtest/PingExecutor.kt` e `AnalisadorAmostragemPing.kt`)
- **Escopo:** medição de latência/jitter/perda usada pela tela Ping, pelo speedtest de velocidade e pelo Modo gamer

---

## 1. Objetivo técnico

Medir latência, jitter e perda de pacote de forma consistente entre três consumidores (tela
Ping, `ExecutorSpeedtestCloudflare`, Modo gamer) sem ICMP real — Android não concede
`CAP_NET_RAW` a apps, então a medição é HTTP/HTTPS. A UI nunca deve chamar isso de "ping ICMP"
(decisão registrada em GH#1211).

## 2. Visão geral da solução

### `PingExecutor` (class, não singleton)

```kotlin
class PingExecutor(
    private val targetUrl: String = "https://speed.cloudflare.com/__down?bytes=0",
) {
    suspend fun executar(
        count: Int = 20,
        onProgresso: (Int) -> Unit = {},
    ): PingResultado
}
```

`targetUrl` é parametrizável — a tela Ping aceita destino informado pelo usuário e o Modo gamer
apontando para o `game-latency-probe-worker` (sonda regional dedicada) reaproveitam a mesma classe
em vez de duplicar a lógica de amostragem.

O algoritmo estatístico (mediana, filtro de outlier, jitter, perda) foi extraído para
**`AnalisadorAmostragemPing`** (GH#1019) — classe pura, sem I/O, reusada também por
`ExecutorSpeedtestCloudflare`. `PingExecutor` cuida só da parte de rede (disparar requisições,
timeouts, abort antecipado); `AnalisadorAmostragemPing` cuida só da estatística.

### `PingResultado` (data class)

```kotlin
data class PingResultado(
    val latenciaMs: Double,
    val jitterMs: Double,
    val perdaPercentual: Double,
    val amostras: Int,
    val amostrasValidas: Int = 0,
    val timeouts: Int = 0,
    val maxMs: Double = 0.0,
    val p95Ms: Double = 0.0,
    val picos: Int = 0,
    val destino: String = "",
    val abortadoPorRede: Boolean = false,
    val execucaoParcial: Boolean = false,
)
```

`maxMs`/`p95Ms`/`picos` (GH#1211) preservam os valores extremos que o filtro de outlier
descarta do cálculo de `latenciaMs` — evita que picos reais "desapareçam" da análise de
estabilidade. `abortadoPorRede`/`execucaoParcial` sinalizam quando a execução não completou as
`count` amostras solicitadas.

## 3. Fluxo de execução

```
executar(count=20)
  │
  ├─ timeout global de 30s (withTimeoutOrNull) cobrindo a execução inteira
  │
  ├─ loop de até count amostras:
  │  ├─ medirPingComMotivo() → RTT (Double) ou (null, motivo)
  │  ├─ reportar progresso via onProgresso(i)
  │  └─ 3 falhas consecutivas de rede/DNS (UnknownHostException) → aborta cedo
  │     (abortadoPorRede = true) em vez de esgotar as count tentativas
  │
  └─ AnalisadorAmostragemPing.analisar(amostras) →
       ├─ descarta a 1ª amostra (aquecimento de conexão)
       ├─ mediana das amostras válidas restantes → latenciaMs
       ├─ filtra outliers > 3× mediana (ignora o filtro se ele zerar a lista)
       ├─ jitter = média das deltas absolutas entre amostras consecutivas (não é desvio-padrão)
       └─ perda = % de timeouts sobre o total pós-descarte da 1ª amostra
```

### Timeouts

| Nível | Valor |
|---|---|
| Timeout global da execução inteira | 30s (`TIMEOUT_GLOBAL_MS`) |
| Timeout por amostra (`OkHttpClient`) | 4s (`connectTimeout`/`readTimeout`/`callTimeout`) |
| Falhas de rede consecutivas para abortar | 3 (`FALHAS_REDE_CONSECUTIVAS_PARA_ABORTAR`) |

## 4. Tratamento de erros

| Erro | Tratamento |
|---|---|
| `UnknownHostException` (sem rede/DNS) | Amostra nula, motivo `SEM_REDE_OU_DNS` — 3 consecutivas abortam a execução |
| `SocketTimeoutException` | Amostra nula, motivo `TIMEOUT` |
| `IOException` genérica | Amostra nula, motivo `DESTINO_INDISPONIVEL` |
| `CancellationException` | Sempre relançada — nunca tratada como amostra falhada (cancelamento de tela/coroutine precisa propagar de verdade) |

## 5. Cliente HTTP

```kotlin
OkHttpClient.Builder()
    .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .callTimeout(4, TimeUnit.SECONDS)
    .addInterceptor { /* User-Agent + Cache-Control: no-store */ }
    .build()
```

Cada requisição inclui um cache-buster (`_cb=<timestamp>_<random>`) na URL para evitar qualquer
cache intermediário.

## 6. Integrações e dependências

| Consumidor | Uso |
|---|---|
| `PingScreen` (`:app`) | Tela Ping standalone (overlay); `PingExecutor()` ou `PingExecutor(targetUrl = "https://$destino/")` |
| `ExecutorSpeedtestCloudflare` | Reusa `AnalisadorAmostragemPing` para a fase de latência do speedtest completo |
| Modo gamer (`ModoGamerConfigResultadoSection.kt`, `:app`) | Reusa `PingExecutor` com `targetUrl` do `game-latency-probe-worker` como fallback; antes tenta a sonda UDP `SondaGameLiftBeacon` (`:core:probejogo`), cujas amostras passam pelo mesmo `AnalisadorAmostragemPing` |

## 7. Limitações conhecidas

| Limitação | Motivo | Mitigação |
|---|---|---|
| Não é ICMP real | Android não permite socket ICMP bruto sem privilégio elevado | HTTP é a abordagem padrão em apps; UI nunca chama de "ping ICMP" |
| Primeira amostra sempre descartada | Aquecimento de conexão (DNS/TLS) | Comportamento intencional, preservado desde a versão original |
| `perdaPercentual` mede falha HTTP, não perda de pacote ICMP comprovada | Limitação da abordagem | Responsabilidade da UI rotular corretamente |

## 8. Testes

`android/feature/speedtest/src/test/kotlin/.../feature/speedtest/PingExecutorTest.kt`. O
algoritmo estatístico tem teste próprio em `AnalisadorAmostragemPingTest.kt`.
