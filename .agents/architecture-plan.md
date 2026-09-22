# Architecture Plan — trabalho corrente

## Status de sites e aplicativos — consumidor da API Linka

O SignallQ consome somente o contrato público de leitura `v1/service-status` já operado pelo Linka (catálogo e incidentes). A escolha de cada serviço e a revisão já notificada ficam locais; `WorkManager` consulta o feed em rede disponível e emite uma notificação Android apenas para incidentes novos ou revisados. O estado externo não alimenta score, finding ou recomendação do diagnóstico da conexão. Falha da API mantém escolhas e mostra atualização indisponível, nunca “operando normalmente”. Não há escrita na API Linka, token push, segredo ou mudança de infraestrutura nesta fatia.

> Use somente quando o gate arquitetural do `AGENTS.md` for acionado. Camillo mantém este artefato curto e proporcional à mudança.

(demais entradas históricas deste arquivo preservadas — ver git log; esta revisão substitui o topo do arquivo pela fatia corrente)

---

# Confiabilidade estatística do diagnóstico de rede — amostragem, perda de pacotes e proveniência

## 1. Inventário do motor atual

Algoritmo de amostragem (fonte única, correta, não duplicar):
- `android/feature/speedtest/src/main/kotlin/io/signallq/app/feature/speedtest/AnalisadorAmostragemPing.kt` — `AnalisadorAmostragemPing.analisar()`. Já calcula corretamente: descarte da 1ª amostra (warm-up), mediana, jitter por deltas sucessivos, filtro de outlier (`> 3x` mediana) só para a latência-base, **e já preserva `p95Ms`, `maxMs` e `picos` calculados sobre todas as amostras válidas, antes do filtro** (comentário do próprio arquivo cita GH#1211 item 3 como o problema que isso resolveu). `ResultadoAmostragemPing` (linhas 14–24) já tem os 8 campos que a Fase de investigação pediu para confirmar.
- Reusado por dois consumidores, sem duplicação: `ExecutorSpeedtestCloudflare.coletarAmostrasLatencia()` (linha 594) e `PingExecutor.executar()` (linha 128, usado pela tela Ping e pelo refinamento do Modo Gamer).
- `PingExecutor.PingResultado` (linhas 38–51) **já propaga `maxMs`/`p95Ms`/`picos` para fora** — não há perda de dado nesse caminho.

Config de amostragem: `ExecutorSpeedtestCloudflare.SpeedtestConfig` (linha 1257) — `pingCount = 15` (fast) / `25` (complete), definido em `fromModo()` (linhas 1271–1302). Com o descarte da 1ª amostra em `AnalisadorAmostragemPing`, a janela efetiva é **14** (fast) / **24** (complete) — confirmado.

Perda de pacotes existe hoje em **duas camadas distintas e não sincronizadas** (achado central desta investigação):
1. **`Provenance`** (`EvidenceProvenance.kt`) — enum genérico `medida`/`estimada`/`indisponivel`, desenhado para todas as dimensões do `ScoreEngine`.
2. **`packetLossSource: String?`** (`InternetDiagnosticInput.kt` linha 49, `ResultadoSpeedtest.kt` linha 22, `MedicaoEntity.kt`) — vocabulário legado de string livre: `"estimated"`, `"naoMedido"`, `"unknown"`, `"modem"` (medição direta, nunca produzida hoje).

Classificadores/engines de perda de pacotes, cada um com sua própria regra:
- `MetricClassifier.classificarPerdaPacotes()` (`MetricClassifier.kt:124-130`) — genérico, thresholds 0% / 0,5% / 2% / >2%, **sem qualquer noção de proveniência ou tamanho de amostra**. Consumido por `ScoreEvidenceBuilder.estabilidade()` (linha 50) e por `ModoGamerEngine` (linha 168, dimensão "Falhas estimadas na conexão").
- `GameReadinessClassifier.perdaFaixa()` (`GameReadinessClassifier.kt:364-374`) — Ruim só se `fonte != "estimated"` (`medida`) **e** `perda >= 1.0`; senão qualquer `perda > 0.0` já é Atenção.
- `UsageProfileClassifier.perdaDimensao()` (`UsageProfileClassifier.kt:448-478`) — mesmo padrão: Comprometido só com `provenance == medida && perda >= 1.0`.
- `ScoreEvidenceBuilder.perdaPacotesStatus()` (`ScoreEvidenceBuilder.kt:179-197`) — thresholds 1%/3%, provenance `estimada` quando `fonte == "estimated"`, `medida` para qualquer outro valor não nulo/não-`naoMedido`/não-`unknown`.
- `ScoreEngine.aplicarTetos()` (`ScoreEngine.kt:167-200`) — teto 45 quando `perda.provenance == Provenance.medida && nota <= NOTA_CRITICO` (perda real ≥3%).
- `SpeedtestQualityClassifier.classificarQualidade()` (`SpeedtestQualityClassifier.kt:39-68`) — **quarto** conjunto de thresholds (0,5%/1%/2%/3%/5%, por perfil de uso), **sem checar `packetLossSource`/`Provenance` em nenhum ponto** — produz `vereditoStreaming`/`vereditoGamer`/`vereditoVideoChamada`/`gargaloPrimario`, que são persistidos em `MedicaoEntity` e exibidos em `ResultadoVelocidadeScreen`, `HistoricoScreen`, `HomeMedicaoAdapter` e enviados ao `signallq-admin-worker` via `AdminIngestPayloads`.
- `RecomendacaoPraticaEngine.recomendarPerdaDePacotes()` (linhas 495-521) — thresholds 1%/3%, já rotula corretamente "(estimada)" quando `fonte == "estimated"`.

Contrato NDS já tem vocabulário fechado mais rico que o local: `NdsProvenance` (`NdsDiagnosticsRequest.kt:105-113`) = `MEASURED`/`ESTIMATED`/`DERIVED`/`CACHED`/`UNKNOWN` (ADR-018, issue #1842). `toNdsPacketLossSource()` (`NdsDiagnosticsRequestMapper.kt:339-345`) mapeia `"modem"→MEASURED`, `"estimated"→ESTIMATED`, `"unknown"→UNKNOWN`, qualquer outro valor (inclusive `null`/`"naoMedido"`) → `null` (omitido do JSON).

Persistência: `MedicaoEntity` (`android/core/database/.../MedicaoEntity.kt`) guarda `perdaPercentual`, `packetLossSource`, `jitterMs`, `latencyMs`, `bufferbloatMs`, vereditos — **não guarda `p95Ms`/`maxMs`/`picos`/`timeouts`/`amostrasValidas`**. `ResultadoSpeedtest` também não tem esses campos no nível raiz (só dentro de `DiagnosticoFasesSpeedtest.latenciaAmostrasTotais/latenciaAmostrasValidas/latenciaTimeouts`, que sobrevivem só até a UI de detalhe de fases, não até o histórico).

## 2. Problemas estatísticos encontrados

1. **Resolução amostral inconsistente com os thresholds usados.** Fast: 14 amostras efetivas → 1 timeout = 7,14%. Complete: 24 → 4,17%. Ambos já estouram os cortes de 0,5%/1%/2%/3% usados por 4 dos 5 classificadores no primeiro timeout, sem qualquer noção de confiança.
2. **`p95Ms`/`maxMs`/`picos` são calculados corretamente por `AnalisadorAmostragemPing` mas descartados no caminho do speedtest.** Confirmado em `ExecutorSpeedtestCloudflare.executarFaseLatencia()` (linhas 567-574): `LatencyPhase` (linha 1226) só recebe `latenciaMs`/`jitterMs`/`perdaPercentual`/`totalAmostras`/`amostrasValidas`/`timeouts` — `p95Ms`, `maxMs` e `picos` morrem ali. **No caminho do `PingExecutor` (tela Ping / Modo Gamer), esses 3 campos SOBREVIVEM** até `PingResultado` — não é um problema geral do motor, é um problema específico do mapeamento em `ExecutorSpeedtestCloudflare`.
3. **`Provenance.medida` para perda de pacotes é hoje inatingível em produção.** Nenhum local do código produz `packetLossSource = "modem"` ou qualquer valor que caia no `else -> Provenance.medida` dos 3 classificadores que dependem disso. `ExecutorSpeedtestCloudflare` hard-codeia `packetLossSource = "estimated"` sempre (linha 1093). Resultado: os tetos/Comprometido/Ruim "só com medição real" descritos no kdoc de `GameReadinessClassifier`, `UsageProfileClassifier` e `ScoreEngine.aplicarTetos` **nunca disparam com dado real de speedtest hoje** — é código morto disfarçado de proteção. Isso é bom (não superreage) mas também ruim (nunca escala mesmo diante de perda recorrente/consistente), e não é isso que os kdocs dizem que acontece.
4. **`SpeedtestQualityClassifier` é o único dos 5 consumidores sem nenhuma proteção de confiança.** É também o que persiste dado (vereditos) e alimenta a tela de resultado e o histórico — ou seja, é o de MAIOR exposição ao usuário e o que hoje reage pior a 1 timeout isolado.
5. **`ScoreEvidenceBuilder.estabilidade()` degrada a proveniência de latência/jitter (que SÃO medidos de verdade) para `estimada` só porque `packetLossSource == "estimated"`** (linha 54) — mistura confiança de uma dimensão (perda) com a confiança de outras (latência, jitter, bufferbloat) que não têm o mesmo problema de resolução.
6. **Quatro vocabulários paralelos para a mesma proveniência**: `Provenance` (enum Kotlin local), `packetLossSource: String?` (string livre legada), `NdsProvenance` (enum remoto), e o texto solto `"(estimada)"` usado em 3 lugares de UI/copy. Não há hoje uma fonte única — o comentário do próprio `EvidenceProvenance.kt` já reconhece isso como "generaliza para TODAS as dimensões (...) o modelo que a Fase 1 introduziu apenas para perda de pacotes".

## 3. Consumers impactados

- `:feature:speedtest` — `AnalisadorAmostragemPing`, `ExecutorSpeedtestCloudflare` (config, `LatencyPhase`, `construirResultado`), `ResultadoSpeedtest`, `DiagnosticoFasesSpeedtest`, `SpeedtestQualityClassifier`, `PingExecutor` (não muda, já correto).
- `:core:diagnostico` — `DiagnosticInput`/`InternetDiagnosticInput`, `EvidenceProvenance`, `MetricClassifier`, `UsageProfileClassifier`, `GameReadinessClassifier`, `ModoGamerEngine`, `ScoreEvidenceBuilder`, `ScoreEngine`.
- `:core:database` — `MedicaoEntity`, migration nova (aditiva) se decidirmos persistir p95/max/picos/confiança.
- `:core:nds` — `NdsDiagnosticsRequest`/`NdsProvenance`, `NdsDiagnosticsRequestMapper.toNdsPacketLossSource`.
- `:feature:diagnostico` — `RecomendacaoPraticaEngine.recomendarPerdaDePacotes`, `AiModels`/`AiDiagnosisRepository` (packetLossSource repassado à IA), `RemoteDiagnosticReportMapper`.
- `:app` — `MainViewModel` (linhas 853/2635/2646), `SpeedtestPersistenceCoordinator`, `ResultadoVelocidadeScreen`, `LaudoScreen`, `HistoricoScreen`, `ResultadoPdfGenerator`, `ModoGamerMedicaoAdapter`, `HomeMedicaoAdapter`, `MonitoramentoWorker`.
- `docs_ai/CONTRATOS/openapi/` — nenhum arquivo documenta hoje `packetLossSource`/`NdsProvenance` apesar de existir desde a ADR-018 (dívida de documentação pré-existente, não criada por esta mudança, mas que deve ser fechada junto se o contrato mudar).

## 4. Thresholds que permanecem iguais

- Tabela ANATEL/genérica de latência (0/100/150/200ms), jitter (0/5/10/20ms), RSSI, RSRP/RSRQ/SINR, bufferbloat (0/5/30/100ms) — nenhum motivo técnico ou estatístico para mudar; a skill `regras-diagnostico-rede` continua fonte canônica.
- Os PERCENTUAIS de corte por si só (0,5%/1%/2%/3%/5%) não precisam mudar — o problema não é "o valor do threshold está errado", é "o percentual medido com poucas amostras não pode ser comparado ao threshold como se fosse confiável". Trocar os números não resolve nada sozinho.
- `AnalisadorAmostragemPing`: mediana como latência principal, warm-up discard, filtro de outlier 3x para a latência-base — preservados integralmente (restrição do Luiz).
- `MetricClassifier.classificarBufferbloat()` como única fonte de bufferbloat — preservado.

## 5. Thresholds que realmente precisam mudar

Nenhum valor numérico de threshold de negócio muda. O que muda é:
- `SpeedtestConfig.pingCount` (fast): `15` → `20` (19 efetivos) para dar janela inicial mais próxima de ~20 amostras úteis, reduzindo a resolução mínima de 7,14% para 5,26% por timeout isolado — ainda insuficiente sozinho, por isso item 7 abaixo.
- `SpeedtestConfig.pingCount` (complete): mantém `25` (24 efetivos) — já está acima de ~20, não precisa de baseline maior, só ganha a mesma janela de confirmação quando os gatilhos disparam.
- Os "tetos"/gates hoje escritos como `provenance == Provenance.medida` em `GameReadinessClassifier.perdaFaixa`, `UsageProfileClassifier.perdaDimensao` e `ScoreEngine.aplicarTetos` passam a checar uma nova condição de **confiança amostral** (seção 6), não mais proveniência — porque proveniência `medida` para perda via HTTP timeout é, por natureza, inatingível (nunca vai virar captura real de pacote) e não deveria ter sido a condição de gate.

## 6. Modelo proposto de `EvidenciaPerdaPacotes`

Não introduz um novo enum de proveniência nem mexe no `Provenance` genérico (usado por RSSI, fibra, velocidade etc. — mudar o enum compartilhado teria raio de impacto maior que o necessário). Em vez disso, acrescenta um eixo ortogonal de **confiança amostral**, específico de perda de pacotes (onde o problema de resolução realmente existe — latência/jitter não sofrem do mesmo jeito porque não são binários por amostra).

```kotlin
// io.signallq.app.feature.speedtest (ou io.signallq.app.core.diagnostico, ver decisão abaixo)

enum class ConfiancaAmostral {
    /** Amostra suficiente para tratar o percentual como confiável: 0 timeouts,
     *  ou timeouts recorrentes/consecutivos confirmados após a janela de confirmação. */
    SUFICIENTE,
    /** Percentual real, mas calculado sobre poucos timeouts (tipicamente 1, isolado,
     *  não confirmado) — não deve ser tratado como perda "crítica" nem elevar
     *  classificação a Ruim/Comprometido sozinho. NUNCA vira 0 — o valor medido
     *  é reportado como está, só com a confiança declarada. */
    INSUFICIENTE,
}

data class EvidenciaPerdaPacotes(
    val perdaPercentual: Double,        // fato — nunca forçado a 0
    val timeoutsTotais: Int,
    val timeoutsConsecutivosMax: Int,
    val amostrasEfetivas: Int,          // pós warm-up, soma baseline + confirmação se houve
    val confirmacaoExecutada: Boolean,  // se a janela de confirmação rodou
    val confianca: ConfiancaAmostral,
)
```

`AnalisadorAmostragemPing.analisar()` ganha uma função irmã (ou um segundo método) que recebe o resultado bruto + `timeoutsConsecutivosMax` (novo cálculo simples sobre a lista bruta) e devolve `EvidenciaPerdaPacotes` — sem duplicar mediana/jitter/p95, só compondo em cima do que já existe.

`p95Ms`/`maxMs`/`picos` **não precisam de um modelo novo** — já existem em `ResultadoAmostragemPing`. O trabalho aqui é só parar de descartá-los em `LatencyPhase` (item 8).

## 7. Estratégia de amostragem adaptativa

Janela inicial (baseline), pós warm-up:
- **Fast**: 20 probes brutos → 19 efetivos (era 15/14).
- **Complete**: 25 probes brutos → 24 efetivos (sem mudança — já é ≥20).

Janela de confirmação (+20 probes brutos, mesma regra warm-up não se aplica de novo — é uma continuação da mesma coleta, não uma nova rodada com novo descarte de 1ª amostra), disparada **uma única vez** (sem loop iterativo, para não estourar o orçamento de tempo do teste) quando QUALQUER um destes sinais aparece no resultado do baseline:
1. `timeouts >= 1` (gatilho dominante — com N=19/24, 1 timeout isolado já é >5%/>4%, sempre acima de pelo menos um threshold de negócio);
2. `picos > 0` (spike detectado — `p95Ms` ou `maxMs` distante da mediana, já calculado por `AnalisadorAmostragemPing`);
3. `MetricClassifier.classificarJitter(jitterMs) in [regular, ruim]`;
4. resultado da perda dentro de ±30% relativo de qualquer corte de negócio (0,5%/1%/2%/3%) mesmo sem timeout novo entrando na fórmula — cobre o caso de janelas maiores no futuro onde a % não é dominada por 1 timeout isolado.

Por que 20 e não outro número: é o valor pedido explicitamente pelo Luiz como "amostra estatisticamente mais séria" sem virar um segundo speedtest; dobrar o baseline (19→39 efetivos fast, 24→44 complete) leva a resolução mínima de 1 timeout para ~2,6%/~2,3% — ainda "real", mas já compatível com a ideia de "não é mais um evento isolado dominando o resultado".

Diferença fast vs complete: fast preserva orçamento de tempo curto como prioridade de produto — só paga o custo da confirmação quando a rede já deu sinal de problema (o caso comum, rede saudável, não passa pelos 4 gatilhos e não paga o custo extra). Complete já tem baseline maior por natureza (medição mais completa é a proposta de produto do próprio modo), então a confirmação é mais sobre consistência que sobre tamanho mínimo.

Custo em pior caso: confirmação só dispara com rede já degradada (por definição dos gatilhos) — 20 probes extras a até 4s de timeout cada é um pior caso teórico de +80s que não é realista (mesma rede que já timou uma vez tende a timar rápido nas seguintes, não no timeout máximo de 4s cada), mas deve ser declarado como risco de UX (seção 9) e considerado no critério de aceite de Breno (teste em rede real degradada).

## 8. Impacto em persistência/contratos

**Mudança 100% aditiva — nenhuma coluna removida, nenhum contrato quebrado.**

- `MedicaoEntity`: nova migration `Nx → Nx+1` adicionando colunas nullable `latenciaP95Ms REAL`, `latenciaMaxMs REAL`, `latenciaPicos INTEGER`, `perdaConfianca TEXT` (valores `"suficiente"`/`"insuficiente"`, nullable — registros antigos ficam `null`, nunca inferidos retroativamente). Seguir o padrão já usado nas migrations 13→14/15→16 (`Migration13Para14Test`/`Migration15Para16Test` como modelo de teste).
- `ResultadoSpeedtest`/`DiagnosticoFasesSpeedtest`: adicionar os mesmos campos (default `null`/`0` para não quebrar quem já constrói o data class por posição — checar todos os call sites listados na seção 3, todos são código do próprio monorepo, não há consumidor externo desse tipo).
- `LatencyPhase` (`ExecutorSpeedtestCloudflare.kt:1226`): passa a propagar `p95Ms`/`maxMs`/`picos`/`EvidenciaPerdaPacotes` (ou os campos soltos) em vez de descartá-los — mudança confinada a um `private data class` do próprio arquivo, raio de impacto mínimo.
- **Contrato NDS**: `NdsProvenance` já tem vocabulário suficiente (`MEASURED`/`ESTIMATED`/`DERIVED`/`CACHED`/`UNKNOWN`) — **não precisa mudar o enum**. Decisão: `toNdsPacketLossSource()` ganha uma regra nova — quando `confianca == SUFICIENTE` e há timeouts recorrentes/consecutivos confirmados, ainda mapeia para `ESTIMATED` (continua sendo estimativa por timeout HTTP, nunca captura real de pacote — não é honesto chamar de `MEASURED`); a informação de confiança em si **não** precisa ir para o payload remoto nesta fase — o valor filtrado (`perdaPercentual` só reportado quando `confianca == SUFICIENTE`, omitido/tratado como indisponível quando `INSUFICIENTE`) já basta para a IA nunca receber confiança maior que a real. Isso responde a restrição do brief: **decisão explícita = enriquecer localmente primeiro, contrato remoto não precisa evoluir nesta fase.** Se o produto quiser no futuro mostrar "confiança" explicitamente na resposta da IA, aí sim vira uma ADR nova.
- `packetLossSource` (string legada) não é removida nem renomeada — continua existindo para compatibilidade com `RecomendacaoPraticaEngine`, PDF, `LaudoScreen`, telas atuais. A nova `ConfiancaAmostral` é um campo adicional, não um substituto.
- Registros legados (sem `perdaConfianca`, `latenciaP95Ms` etc.): tratados como `confianca = INSUFICIENTE` por default apenas na exibição/reclassificação de Histórico antigo — não se recalcula silenciosamente o passado, não se finge que havia janela de confirmação que não rodou.

## 9. Gate Camillo: decisão

**Aprovado, com condições.** A mudança é sistêmica (cruza `:feature:speedtest`, `:core:diagnostico`, `:core:database`, `:core:nds`, `:app`) e o gate se aplica — mas o escopo real é menor do que o pedido inicial sugeria, porque:
- `p95Ms`/`maxMs`/`picos` **já existem** no motor (`AnalisadorAmostragemPing`) — o trabalho é parar de descartá-los em `ExecutorSpeedtestCloudflare`, não criar cálculo novo.
- O vocabulário de proveniência remota (`NdsProvenance`) **já é suficiente** — não precisa de mudança de contrato NDS nesta fase.
- Não se cria um segundo motor de diagnóstico nem um segundo bufferbloat — `MetricClassifier.classificarBufferbloat()` intocado.

Condições:
1. Nenhum dos 5 consumidores de perda de pacotes muda de arquivo/dono sem necessidade — `MetricClassifier` continua genérico; a checagem de confiança entra nos 3 pontos que já tinham a intenção de checar proveniência (`GameReadinessClassifier`, `UsageProfileClassifier`, `ScoreEngine.aplicarTetos`) e no único que não tinha nenhuma proteção (`SpeedtestQualityClassifier`), que precisa ganhar acesso à `EvidenciaPerdaPacotes`/`confianca` — hoje só recebe `Double` cru, isso é uma mudança de assinatura de função pública dentro do módulo, não de um contrato externo.
2. Testes de caracterização (golden tests já existentes: `ScoreEngineTest`, `ScoreEvidenceBuilderThresholdCharacterizationTest`, `GameReadinessClassifierTest`, `UsageProfileClassifierTest`, `AnalisadorAmostragemPingTest`) precisam ser revisados ANTES da implementação para confirmar quais casos hoje dependem do comportamento "1 timeout já é Ruim" — alguns podem estar codificando o bug como comportamento esperado.
3. `pingCount` fast passar de 15→20 é uma mudança observável de duração de teste — Cora/produto deve validar que o aumento (poucas centenas de ms em rede saudável) é aceitável antes de Davi/Ramon implementar; não é decisão só técnica.

Riscos:
- Mudar o gate de `provenance == medida` (inatingível) para `confianca == SUFICIENTE` (atingível) é o único ponto onde o comportamento REALMENTE muda de forma observável para o usuário — perda confirmada/recorrente agora pode, pela primeira vez, chegar a Ruim/Comprometido/teto 45. Isso é a correção pedida ("perda confirmada deve continuar pesando forte"), mas é uma mudança de comportamento que hoje nunca acontecia — precisa de teste de regressão explícito e não pode ser silenciosa.
- `SpeedtestQualityClassifier` ganhar checagem de confiança muda os vereditos persistidos (`vereditoGamer` etc.) — janela de compatibilidade com histórico antigo (item 8) evita reclassificar dados velhos.

Fora de escopo (reafirmando os não-objetivos do brief): redesign de telas, novo provedor de speedtest, segundo motor de diagnóstico, mudança de RSSI/RSRP/RSRQ/SINR, reescrita do Modo Gamer, LLM decidindo threshold, mudança de contrato NDS além do já necessário (nenhuma nesta fase).

## 10. Plano de implementação

Ordem sugerida, com checkpoint de teste após cada bloco:

1. **Ramon** — `AnalisadorAmostragemPing`: adicionar cálculo de `timeoutsConsecutivosMax` e a função que produz `EvidenciaPerdaPacotes`/`ConfiancaAmostral`. Checkpoint: estender `AnalisadorAmostragemPingTest` com casos (0 timeouts, 1 isolado, 2 consecutivos, 2 não-consecutivos, confirmação não disparada vs disparada).
2. **Ramon** — `ExecutorSpeedtestCloudflare`: `SpeedtestConfig.fromModo()` (pingCount fast 15→20); implementar a janela de confirmação em `coletarAmostrasLatencia`/`executarFaseLatencia` (os 4 gatilhos da seção 7); parar de descartar `p95Ms`/`maxMs`/`picos` em `LatencyPhase`; propagar `EvidenciaPerdaPacotes` até `construirResultado`. Checkpoint: testes de `ExecutorSpeedtestCloudflare` (duração da fase de latência, confirmação disparando/não disparando, campos propagados).
3. **Ramon** — `:core:diagnostico`: `InternetDiagnosticInput` ganha os campos novos; `GameReadinessClassifier.perdaFaixa`, `UsageProfileClassifier.perdaDimensao`, `ScoreEngine.aplicarTetos`/`ScoreEvidenceBuilder.perdaPacotesStatus` trocam o gate `provenance == medida` por `confianca == SUFICIENTE`. Checkpoint: rodar os golden tests existentes primeiro (sem alterar), listar quais quebram, atualizar só os que codificavam o bug (documentar a mudança de expectativa no próprio teste).
4. **Ramon** — `SpeedtestQualityClassifier.classificarQualidade()`: ganha parâmetro de confiança; perda com `confianca == INSUFICIENTE` não deve sozinha empurrar veredito para `poor`/`gargaloPrimario = packetLoss` — cai para o pior caso entre as outras métricas, igual ao padrão já usado por `GameReadinessClassifier.piorFaixa`. Checkpoint: `ClassificacaoMetricaLocalTest` + novo teste dedicado de confirmação.
5. **Davi** — `:core:database`: migration aditiva (novas colunas), `MedicaoEntity`, DAO, mapeamentos em `SpeedtestPersistenceCoordinator`/`MainViewModel`. Checkpoint: teste de migration seguindo o padrão `Migration15Para16Test`, teste de round-trip do DAO.
6. **Davi** — UI que já lê `p95Ms`/`maxMs`/`picos`/confiança quando fizer sentido mostrar (ex.: badge "perda ainda não confirmada" em `ResultadoVelocidadeScreen`/`LaudoScreen`, reaproveitando o padrão já existente do sufixo "(estimada)") — escopo de copy/UX definido por Cora antes da implementação visual.
7. **Ramon** — `NdsDiagnosticsRequestMapper.toNdsPacketLossSource`: aplicar o filtro "só reporta `perdaPercentual` quando `confianca == SUFICIENTE`" antes de montar o payload; `RecomendacaoPraticaEngine.recomendarPerdaDePacotes` e `AiModels`/`AiDiagnosisRepository` herdam o mesmo filtro (não precisam de novo código se o filtro acontecer na origem do dado). Checkpoint: teste de `NdsDiagnosticsRequestMapper` confirmando omissão quando `INSUFICIENTE`.
8. **Breno** — regressão completa: `./android/gradlew test ktlintCheck detekt assembleDebug`; teste em rede real degradada (Wi-Fi ruim/perda intermitente) validando que 1 timeout isolado não vira "Ruim" em nenhuma tela, e que perda recorrente/consecutiva simulada (proxy/throttling) efetivamente chega a Ruim/Comprometido/teto pela primeira vez; medir o custo real de duração da janela de confirmação em rede degradada.
9. **Camillo** — revisão de aderência ao plano após a implementação, focada em: nenhum segundo motor criado, `MetricClassifier`/`Provenance` genéricos intocados, contrato NDS não mudou além do combinado, migration é aditiva.

## 11. Fechamento (Camillo, 2026-09-22) — achados médios de Breno

**Revisão de aderência:** confirmada. `git diff` mostra `MetricClassifier.kt` sem alteração (só um novo seam `classificarJitterLocal` em `ClassificacaoMetricaLocal.kt`, mesmo padrão do seam de bufferbloat já existente); `enum class Provenance` intocado (só `EvidenceScore` ganhou campo opcional `confiancaAmostral: ConfiancaAmostral? = null`, default null, não quebra os outros 10 consumidores); `enum class NdsProvenance` intocado (4 valores, sem alteração) — o filtro entrou como função local `perdaPercentualConfiavelParaNds()` no mapper, não no contrato. Migration 20→21 confirmada aditiva (schema JSON gerado, coluna nova nullable). `EvidenciaPerdaPacotes`/`ConfiancaAmostral` ficaram em `:core:diagnostico` — correto: `:feature:speedtest/build.gradle.kts` depende de `:core:diagnostico` (não o inverso), então colocar o vocabulário lá evita inversão de dependência; o kdoc do próprio arquivo já documenta essa decisão.

**Achado 1 (janela de confirmação sem teto de duração) — decisão (b): adicionar teto de tempo, só no modo fast.**
Contexto: com pingCount fast 20 + confirmação +20, o pior caso teórico saltou de ~60s (antes desta fatia, 15 probes) para ~160s (40 probes × 4s de `callTimeout`), sem proteção alguma. Isso não é "forçar um número cego de threshold de negócio" (nenhum corte de perda/latência/jitter muda) — é uma rede de segurança de resiliência/duração, categoria diferente da restrição do Luiz. Dado que o modo fast é usado majoritariamente em rede móvel e é o modo consciente de bateria/dados por design de produto, decidi (autoridade técnica/proporcional, sem precisar voltar ao Luiz):
- Implementado `SpeedtestConfig.latenciaOrcamentoMs` (`ExecutorSpeedtestCloudflare.kt`), `null` por padrão (modo complete não muda — decisão da seção 7 preservada, completude > sensibilidade de duração).
- Fast mode: `latenciaOrcamentoMs = 20_000L` (20s) cobrindo baseline + confirmação juntos. Reaproveita o MESMO idioma já usado no arquivo para o teto de 25s do throughput adaptativo (`executarFaseUploadAdaptativa`, `budgetMs`/`stopNs`/`System.nanoTime()`), não um mecanismo novo.
- Ao estourar o orçamento, o loop para de coletar nova amostra e analisa o que já foi coletado — mesmo tratamento tolerante já usado para `mudouRede()` (troca de rede no meio da coleta); nunca é tratado como erro, nunca força perda a 0.
- Não reabre pingCount=20, não reabre os 4 gatilhos, não reabre a regra de escalonamento de perda confirmada — só limita a duração total de wall-clock da fase de latência no fast.
- Testes existentes de `ExecutorSpeedtestCloudflare`/`AnalisadorAmostragemPing` seguem verdes (`:featureSpeedtest:testDebugUnitTest`), `ktlintCheck`/`detekt` (`:featureSpeedtest`) sem violação nova.
- **Follow-up ainda recomendado, não bloqueante:** validação empírica em device real/rede degradada real (throttling) para confirmar que 20s é um valor confortável (nem curto demais a ponto de cortar toda confirmação legítima, nem longo demais para UX) — abrir issue de validação pré-release, já que nem Ramon/Davi (MockWebServer) nem Breno (sem ferramenta de throttling) conseguiram validar isso com rede real até aqui.

**Achado 2 (documentação de arquitetura desatualizada):** corrigido por Camillo — ver `docs_ai/ARQUITETURA/MODULOS/core-database.md`, `feature-speedtest.md`, `core-diagnostico.md` (contagens de linha reais, versão do banco, `EvidenciaPerdaPacotes.kt` adicionado à tabela de componentes, `last_updated` atualizado).

**Veredito do gate arquitetural: FECHADO/APROVADO.** Nenhuma pendência arquitetural bloqueante restante. Único item aberto é o follow-up de validação empírica do teto de 20s (não bloqueia merge, mesmo veredito de proporcionalidade que Breno já havia dado ao risco original).
