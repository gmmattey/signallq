---
title: "Módulo :core:probejogo"
description: "Cliente UDP determinístico do beacon regional AWS GameLift, usado pelo Modo gamer para medir RTT de rota real em vez de estimativa HTTPS."
type: "técnico"
status: "ativo"
owner: "Camillo"
last_updated: "2026-10-04"
version: "1.1.0"
---

# `:core:probejogo`

- **Caminho físico:** `android/core/probejogo/` (módulo hierárquico novo, sem override de `projectDir`)
- **Namespace:** `io.signallq.app.core.probejogo`
- **Tipo:** biblioteca Android — Kotlin puro (só `java.net.DatagramSocket`)

## Responsabilidade

`SondaGameLiftBeacon(host, port, timeoutMs = 2000).sondar(amostras)` envia datagramas UDP ao beacon
público de referência da AWS GameLift (que ecoa qualquer corpo não vazio < 1024 bytes) e devolve
`List<Double?>` — RTT em ms, `null` quando não houve eco válido dentro do timeout. O payload
(`SQPB` + versão + sessão + sequência + nonce, 33 bytes) segue o formato do LagCheck; só um eco
íntegro do payload exato conta como amostra. Uma amostra por vez (bloqueante dentro do timeout).

É medição de **rota regional de referência**, nunca o servidor de um jogo específico — o chamador
não deve apresentá-la como "ping do jogo". Nunca trata timeout como sucesso nem fabrica RTT.

Não é dele: a análise estatística (mediana/jitter/perda — `AnalisadorAmostragemPing` em
`:featureSpeedtest`), a escolha de host/porta (`BuildConfig.GAMELIFT_BEACON_HOST`/`_PORT`, em
`:app`) e o fallback HTTPS (`PingExecutor`).

## Dependências

`libs.kotlinx.coroutines.android` (produção); `libs.junit`, `libs.kotlinx.coroutines.test` (test).
Nenhuma dependência de projeto — de propósito, para não puxar `:core:diagnostico` nem
`:featureSpeedtest`.

## Consumidores

`:app` — `ModoGamerConfigResultadoSection.kt` tenta primeiro a sonda UDP e, sem resposta válida
nenhuma, cai na estimativa HTTPS antiga, preservando o contrato de `ModoGamerScreen`.

## Componentes principais

| Arquivo | Responsabilidade |
|---|---|
| `SondaGameLiftBeacon.kt` | Client UDP; `sondar(amostras)` em `Dispatchers.IO` |
| `src/test/.../SondaGameLiftBeaconTest.kt` | Teste JVM do cliente |

## Riscos e dívidas

Beacon de terceiro (AWS) e "rota de referência ≠ rota do jogo": ver [`features/modo-gamer.md`](../features/modo-gamer.md), seção 12.
