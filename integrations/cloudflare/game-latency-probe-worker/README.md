# SignallQ Game Latency Probe Worker

Worker de eco/latencia dedicado a tela **Jogos** (GH#935), estrategia
`REGIONAL_ESTIMATE` da `docs/functional/JOGOS_TESTE_CONEXAO_SPEC.md`.

## Escopo — de proposito minimo

- `GET`/`HEAD` em `/probe` (ou `/`) devolve `204` sem corpo o mais rapido
  possivel. Sem autenticacao, sem estado, sem logica de jogo.
- O cliente Android mede o round-trip contra este endpoint (varias amostras)
  para estimar latencia/jitter/perda — nunca chama isso de "ping real da
  partida" na UI (regra do spec).

## Por que este Worker existe

O `target` conceitual do spec (`udp-game-sp.signallq.com`, UDP puro) exigiria
Cloudflare Spectrum — produto pago novo, fora da infra atual (decisao tecnica
registrada em `JOGOS_TESTE_CONEXAO_SPEC.md`, secao "Decisao tecnica de
implementacao"). Este Worker roda em TCP/HTTPS sobre a infra Cloudflare ja
usada no projeto (mesmo padrao de `ai-diagnosis-worker`/`signallq-admin-worker`).
A Cloudflare roteia por anycast ao PoP mais proximo do usuario (GRU para a
maior parte do Brasil), dando a semantica de "sonda regional" sem custo novo.

## Deploy

```bash
cd integrations/cloudflare/game-latency-probe-worker
npx wrangler deploy
```

URL productiva referenciada pelo app via `BuildConfig.GAME_LATENCY_PROBE_URL`
(`android/app/build.gradle.kts`).
