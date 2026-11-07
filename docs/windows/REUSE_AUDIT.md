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
| Diagnóstico | `android/core/diagnostico/`, ADR-017/018 | Fora do escopo Desktop atual | Não executar Kotlin no Windows nem duplicar regras. A interpretação guiada fica principalmente no mobile. |
| Modo gamer | `core/probejogo/`, `game-latency-probe-worker` | Parcial | Sonda regional de referência, nunca ping de servidor oficial de jogo. |
| Monitoramento | `android/app/.../monitoramento`, `MedicaoEntity.kt` | Parcial | Adaptar sondagens leves e persistência Windows; reutilizar histerese se alertas forem aprovados. |
| Histórico | `core/database/MedicaoEntity.kt`, `feature/history/` | Contratual | SQLite local próprio, com status, nulidade, proveniência e migrações. |
| Assist | `android/core/nds`, `ai-diagnosis-worker` | Fora do escopo Desktop atual | O app mobile concentra Assist/IA; não embarcar token ou chamar modelo no Desktop. |

## Limites confirmados

`ConnectivityManager`, Room, DataStore, WorkManager e o código Kotlin/OkHttp são Android-bound.
O site em `web/` é uma landing Next.js, não PWA desktop nem fonte de persistência. O Worker
`signallq-diagnostic-worker` é transitório/shadow; NDS é a direção canônica.

## Gate

**Camillo: atendido.** O plano em `.agents/architecture-plan.md` define a nova fronteira. ADR-019
supersede a exclusão de Windows que constava em ADR-016.

## Revisão após aceite do plano — 2026-10-15

Luiz definiu o Desktop como superfície de dados e exigiu aproveitar tudo que for possível dos
módulos Android já implementados. A interpretação guiada, NDS/Assist e IA ficam fora da correção
Desktop atual. Esta matriz é ponto de partida obrigatório antes de criar componentes Windows:

| Função Desktop | Fonte Android existente | Reuso exigido | Limite da plataforma |
|---|---|---|---|
| Speedtest HTTP | `feature/speedtest/ExecutorSpeedtestCloudflare.kt`, `MeasurementStatus.kt`, `ResultadoSpeedtest.kt` e testes `ExecutorSpeedtestCloudflare*Test.kt` | Mesmas fases e semântica de status; fallback 403/429 para payload de 10 MB; erros por fase, cancelamento, troca de rede, bytes/duração e vetores de integridade | Motor de I/O em Rust/reqwest; não executar OkHttp/Kotlin no Windows |
| Latência, jitter e falhas estimadas | `feature/speedtest/AnalisadorAmostragemPing.kt`, `ValidadorBaselineLatencia.kt`, `PingExecutor.kt` e respectivos testes | Portar política de aquecimento, mediana, outliers, jitter, timeouts, p95 e amostra mínima com os mesmos vetores de entrada/saída | Timeout HTTP deve continuar rotulado como estimativa; medição de Ping Windows usa mecanismo próprio |
| Interface e conectividade | `core/network/SnapshotRede.kt`, `EstadoConexao.kt`, `connectivity/ConnectivityDiagnosisContext.kt`, `ConnectivityProbeStep.kt` | Reusar vocabulário de conexão, ordem/semântica das sondagens e distinção entre ausente, falha e zero | `ConnectivityManager`, `NetworkCapabilities` e Wi-Fi Android são substituídos por APIs Windows tipadas; não copiar disponibilidade de campo |
| Wi-Fi | `core/network/contracts/wifi/channel/FrequencyUtils.kt`, `WifiLinkSnapshot.kt` e testes do módulo | Reusar mapeamento frequência → banda/canal e unidades quando o Windows fornecer frequência válida | A coleta da frequência/sinal depende da API e do driver Windows; sem dado, manter `null` |
| DNS | `feature/dns/BenchmarkDnsDoh.kt`, `ResultadoBenchmarkDns.kt` e `BenchmarkDnsDohTest.kt` | Reusar estratégia de warm-up, múltiplas amostras, timeout global e proveniência se a ferramenta Desktop evoluir para medir tempo de resolução | Cliente DoH/OkHttp é substituído por I/O Rust; não portar recomendação de provedor ou classificação de “melhor DNS” para a tela factual |
| Gamer | `core/probejogo/SondaGameLiftBeacon.kt`, `feature/speedtest/AnalisadorAmostragemPing.kt` e `SondaGameLiftBeaconTest.kt` | Mesmo formato de payload/eco íntegro, timeout como ausência, amostras cruas e rótulo de rota regional de referência | Socket UDP implementado no host Windows; destino precisa ser válido e acessível |
| Monitoramento | `app/monitoramento/MonitoramentoWorker.kt`, `HisteresiHelper.kt` e testes correspondentes | Sondagens leves de HTTP/DNS, registro separado do speedtest e semântica de nulidade; reaproveitar histerese somente se alertas entrarem no escopo aprovado | Agendamento, bandeja e notificações dependem do Windows; não usar WorkManager/Doze/limites OEM |
| Histórico/exportação | `core/database/MedicaoEntity.kt`, `feature/history/ExportadorHistoricoCSV.kt`, testes de CSV e `UptimeChartUseCase.kt` | Mesmas unidades, proveniência, campos vazios para valores não medidos, casos de exportação e agrupamento temporal de amostras; preservar histórico antigo | SQLite Desktop é próprio; não migrar Room nem compartilhar arquivo de banco; não copiar classificações `OK/LENTO` do gráfico Android |
| Marca e textos | `brand/`, `packages/design-system/`, fontes Android e copy factual aprovada | Usar assets e vocabulário existentes sempre que compatíveis com a superfície de dados | Compose/recursos Android não são componentes React executáveis |

**Procedimento de paridade:** antes de cada etapa do
[`CORRECTION_PLAN_2026-10-15.md`](CORRECTION_PLAN_2026-10-15.md), registrar candidato Android,
campos e comportamento que serão reaproveitados, diferenças justificadas pela plataforma e vetor
de teste compartilhado. Cobrir sucesso, 403/429, timeout, parcial, offline, troca de rede e
cancelamento. Comparar Android e Windows sob condições controladas; não exigir Mbps idêntico de
pilhas de rede diferentes, mas exigir estado, proveniência, unidades e tratamento de falhas
equivalentes. Marcelo revisa a semântica e Tiago a validação E2E antes de declarar paridade.

Não copiar thresholds de diagnóstico ou criar classificador de qualidade no Desktop. Critérios de
**validade da amostra** e status por fase pertencem ao contrato da medição. Se um comportamento
Android puder ser usado, adaptado ou caracterizado por teste, justificar qualquer implementação
divergente antes de criá-la.
