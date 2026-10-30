# ADR-011 — Fase 0 do motor canônico de diagnóstico (issue #1228)

**Data:** 2026-07-26
**Status:** Aceito

## Contexto

A issue #1228 é a iniciativa arquitetural guarda-chuva que consolida os classificadores/motores de
diagnóstico duplicados (Speedtest, Internet, Wi-Fi, DNS, fibra, etc.) numa única fonte de verdade
canônica (`core:measurement` → `core:network-context` → `core:classification` → diagnóstico →
recomendação). A própria issue exige que a preparação ("Fase 0") **não altere o comportamento
observável do motor local** — só decisão, congelamento de comportamento atual em teste e definição
de contrato.

A issue #952 (motor de diagnóstico remoto versionado, D1-only) depende desta preparação: sua "Fase
inicial obrigatória" pede explicitamente para "definir e versionar `DiagnosticSnapshot` e
`DiagnosticResult`" antes de qualquer implementação de regra remota.

Este ADR resolve três decisões da Fase 0 de #1228, escopadas para não migrar nada ainda:

1. Se `InternetDiagnosticEngine` passa a consumir `MetricClassifier` já nesta fase.
2. Congelamento de comportamento atual (`InternetDiagnosticEngine`, `SpeedtestQualityClassifier`)
   como testes de caracterização/dourados.
3. Nomeação e versionamento dos contratos `DiagnosticSnapshot`/`DiagnosticResult` que #952 vai
   reusar — incluindo uma colisão de nome real encontrada durante a investigação, mais grave do que
   uma simples duplicata de nome.

## Decisão 1 — Migração de `InternetDiagnosticEngine` para `MetricClassifier`: adiada para Fase 1

`MetricClassifier` (`core/diagnostico/MetricClassifier.kt`) já centraliza os thresholds de
download/upload/latência/jitter/perda/bufferbloat/RSSI/RSRP/RSRQ/SINR/DNS num vocabulário canônico
de 6 valores (`MetricStatus`: excelente/bom/regular/ruim/crítico/inconclusivo). O próprio kdoc do
objeto já registra que `InternetDiagnosticEngine` **ainda não foi migrado**, pendente de issue
própria.

`InternetDiagnosticEngine.avaliar()` usa hoje thresholds literais embutidos (ex.: `lat > 100.0`,
`jit > 20.0`, `dl < 25.0`) que retornam `DiagnosticStatus` (5 valores: ok/info/attention/critical/
inconclusive) — vocabulário técnico interno diferente de `MetricStatus`, sem correspondência 1:1
trivial (`MetricStatus` tem granularidade `excelente`/`bom` que `DiagnosticStatus` não distingue, e
vice-versa `attention` cobre parte do que seriam `regular` e `ruim`).

Migrar agora exigiria: (a) escrever um adapter `MetricStatus → DiagnosticStatus` sem valor
inventado, (b) confirmar que cada um dos ~10 achados do engine (`IN-NORMAL-03` a `IN-NORMAL-09b`)
preserva exatamente o texto e o status hoje visível ao usuário, (c) validar contra os testes
dourados desta mesma Fase 0. Isso é migração real de comportamento, não preparação — contraria a
instrução explícita da issue #1228 de não alterar comportamento observável nesta fase.

**Decisão:** adapter e migração ficam para Fase 1 (Speedtest e pós-Speedtest), como dispatch
dedicado e testado contra os golden tests desta Fase 0. Nesta fase, `InternetDiagnosticEngine`
permanece com seus thresholds literais atuais, inalterados.

## Decisão 2 — Golden tests (testes de caracterização)

`InternetDiagnosticEngineTest.kt` e `SpeedtestQualityClassifierTest.kt` já existiam antes desta
Fase 0, mas cobriam cenários específicos (upload zero, perda de pacotes por tipo de conexão, Wi-Fi
não confiável) sem testar sistematicamente os **valores de borda** de cada threshold — o ponto mais
sensível a regressão silenciosa numa migração futura.

Adicionamos, nos mesmos arquivos, uma seção de testes de caracterização cobrindo a fronteira exata
de cada regra hoje em produção:

- `InternetDiagnosticEngineTest`: fronteiras de perda de pacotes (1.0/3.0), jitter (20.0),
  latência (100.0), bufferbloat (30.0/100.0), upload (5.0), download (25.0), e a prioridade
  upload-zero sobre upload-baixo.
- `SpeedtestQualityClassifierTest`: fronteiras de cada veredito (streaming/gamer/videochamada) nas
  transições good→acceptable→poor, e a ordem de prioridade do gargalo primário
  (packetLoss > bufferbloat > latency > upload > none).

Nenhuma classe de produção foi alterada — só teste novo. Esses testes são o contrato de não-
regressão que qualquer migração futura (Fase 1 em diante) precisa continuar satisfazendo, byte a
byte, até o dia em que uma migração deliberada e testada mude o valor esperado (nesse caso o próprio
teste muda, com justificativa, na mesma PR da migração).

## Decisão 3 — Nomeação e versionamento dos contratos

### 3.1 `DiagnosticSnapshot` — já existe, já versionado, sem colisão real

`DiagnosticSnapshot` já é um contrato real e versionado: interface TypeScript em
`integrations/cloudflare/signallq-diagnostic-worker/src/contracts.ts:61`, com campo `schemaVersion`
(valor atual: 6, via `DiagnosticSnapshotMapper.REMOTE_DIAGNOSTIC_SNAPSHOT_SCHEMA_VERSION`). O lado
Kotlin não duplica esse nome — `DiagnosticInput` (`core/diagnostico/DiagnosticInput.kt`) é a
estrutura local que `DiagnosticSnapshotMapper.toJson()` serializa para esse contrato, só incluindo
campos presentes (nunca inventa valor pra campo ausente — já é a prática atual).

Comparando campo a campo com o que #952 pede ("tipo e estado da conexão; métricas Wi-Fi; métricas
móveis; download/upload/latência/jitter/perda/bufferbloat; DNS; fibra; contexto sanitizado do
equipamento local; permissões e capacidades relevantes"): `DiagnosticSnapshot` já cobre connection/
wifi/wifiScan/speed/quality/dns/fiber/mobile/historical/gateway. Faltam apenas dois grupos:
contexto sanitizado do equipamento local (`SafeLocalDeviceContext`, já existe em `DiagnosticInput`
mas não é mapeado hoje) e permissões/capacidades.

**Decisão:** não criar um segundo tipo `DiagnosticSnapshot` em Kotlin. #952 estende o contrato
existente (bump de `schemaVersion` para 7+, novos campos opcionais em `contracts.ts` e no mapper),
em vez de inventar um nome ou uma estrutura paralela. Não há colisão a resolver aqui — havia apenas
risco de #952 recriar algo que já existe.

### 3.2 `DiagnosticResult` — colisão real, confirmada, entre dois conceitos diferentes

Esta é a colisão que a Claudete sinalizou, e é mais séria do que “mesmo nome, mesma coisa,
duplicada”: são **dois conceitos diferentes com o mesmo nome em lados opostos do mesmo sistema**.

- **Kotlin, local, já em produção** (`core/diagnostico/DiagnosticResult.kt`): representa **um
  achado individual** (id, título, status de 5 valores, evidência, mensagem ao usuário,
  recomendação, categoria, `podeConcluir`, `categoriaOrigem`). Sem versionamento de schema. **98
  usos** no código Kotlin (`grep -c` em `android/`) — é o tipo de retorno de todo motor de
  diagnóstico local (`InternetDiagnosticEngine`, `DnsDiagnosticEngine`, `FindingEngine` etc.) e é
  consumido por `DiagnosticReport`, telas, PDF, IA.
- **TypeScript, worker, já em produção** (`contracts.ts:158`): representa o **envelope completo de
  uma avaliação** — `resultSchemaVersion`, `engineVersion`, `rulesetVersion`, `evaluationSource`
  (`REMOTE`/`CACHED_LOCAL`/`BUNDLED_LOCAL`), `overallStatus`, `score`, `confidence`,
  `matchedRules`, `findings` (lista, cada um no formato de `DiagnosticFinding`), `recommendations`,
  `primaryFlow`, `humanSummary`, `evaluatedAt`, `traceId`. Esse é exatamente o "Contrato do
  resultado" que a issue #952 pede — já implementado do lado do worker, campo por campo.

O análogo Kotlin mais próximo do achado individual do TS não é `DiagnosticResult` (TS), é
`DiagnosticFinding` (TS) — que também não tem correspondência Kotlin ainda.

Se #952 seguir o caminho óbvio de espelhar o nome do contrato remoto (`DiagnosticResult` do TS) num
novo tipo Kotlin para o avaliador local, ele colide de frente com o `DiagnosticResult` Kotlin já
existente e usado em 98 lugares — duas classes completamente diferentes competindo pelo mesmo nome
no mesmo módulo (`core:diagnostico` é o destino natural de ambas).

**Decisão:**

- O `DiagnosticResult` Kotlin existente **não é renomeado** nesta fase nem em nenhuma fase próxima
  sem tarefa dedicada — 98 usos, mudança de contrato público, fora do escopo de "não alterar
  comportamento observável" e da regra de higiene sobre renomeação em massa (seção 9 da regra de
  higiene do repo).
- O tipo novo que #952 vai introduzir em Kotlin para espelhar o envelope de avaliação do TS
  **não pode se chamar `DiagnosticResult`**. Nome recomendado: **`DiagnosticEvaluation`** —
  consistente com o campo `evaluationSource` que já existe no contrato TS, com o endpoint
  `POST /diagnostic/evaluate`, e sem colidir com nada hoje existente em Kotlin ou TypeScript.
  Alternativas consideradas e descartadas: `DiagnosticVerdict` (colide conceitualmente com a
  propriedade computada `DiagnosticReport.veredito`, que já existe e significa outra coisa —
  texto curto "Excelente/Bom/Regular/Fraco"), `DiagnosticResultEnvelope` (nome genérico demais,
  viola a convenção de nomes vagos da regra de higiene), `DiagnosticOutcome` (vago).
- O lado TypeScript **mantém o nome `DiagnosticResult`** como está — já é consumido por
  `diagnostic-engine.ts`, `diagnostic-report.ts`, `diagnostic-ai.ts` em produção; renomear ali é um
  refactor não relacionado, maior e sem benefício imediato. A assimetria de nome entre os dois lados
  (TS `DiagnosticResult` ↔ Kotlin `DiagnosticEvaluation`) fica documentada aqui exatamente para que
  ninguém, ao implementar #952, tente "corrigir" a assimetria renomeando o lado errado sem revisar
  este ADR primeiro.
- `DiagnosticReport` (Kotlin, `core/diagnostico/DiagnosticReport.kt`) continua sendo a agregação
  local de UI (todos os `DiagnosticResult` por categoria + `decisao` + score + veredito) — não é
  schema-versionado, não é o contrato de fio (wire contract), e não deve ser confundido com
  `DiagnosticEvaluation`. Reconciliar os dois (ou decidir que `DiagnosticEvaluation` é a fonte e
  `DiagnosticReport` vira derivado dele) é decisão de #952/Fase 1, não desta Fase 0.

### 3.3 Regra de versionamento (aplicável a `DiagnosticEvaluation` quando #952 o implementar)

- Versão via **campo** (`schemaVersion: Int`, seguindo o padrão já usado por
  `REMOTE_DIAGNOSTIC_SNAPSHOT_SCHEMA_VERSION` e por `resultSchemaVersion`/`engineVersion`/
  `rulesetVersion` do TS), nunca por sufixo no nome da classe (`V2`, `Final` etc. são proibidos pela
  regra de higiene, seção 6).
- Campo novo nasce opcional (`?`/nullable). Ausência de dado nunca vira valor inventado (`0`,
  string vazia) — já é o padrão em `DiagnosticSnapshotMapper` e deve continuar.
- Versão incompatível é rejeitada de forma controlada pelo consumidor, não silenciosamente
  truncada.

## Consequências

- Nenhum comportamento observável do motor local mudou nesta fase — só teste novo e documentação.
- `InternetDiagnosticEngine`/`SpeedtestQualityClassifier` agora têm cobertura de fronteira que
  qualquer migração futura (Fase 1) precisa respeitar ou justificar explicitamente ao quebrar.
- #952 tem um nome resolvido (`DiagnosticEvaluation`) para o tipo Kotlin que vai introduzir, evitando
  colisão descoberta tarde (ex.: durante implementação, com testes/imports já escritos).
- Dívida registrada, não resolvida aqui: `DiagnosticFinding` (TS) ainda não tem equivalente Kotlin
  nomeado; quando #952 precisar dele, aplicar o mesmo cuidado de nomeação deste ADR antes de
  escolher um nome.
