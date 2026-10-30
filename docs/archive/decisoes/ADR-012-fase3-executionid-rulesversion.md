# ADR-012 — Fase 3 de #1228: `executionId`/`rulesVersion` em `MedicaoEntity`

**Data:** 2026-07-31
**Status:** Aceito

## Contexto

A auditoria completa da Fase 0 de #1228 (PR #1516, `docs/02-arquitetura.mdAUDITORIA_1228_FASE0_INVENTARIO_COMPLETO.md`)
confirmou o achado **P0-9**: nenhuma linha de `MedicaoEntity` carregava `executionId` nem
`rulesVersion` — o requisito não-negociável da issue #1228 ("mudança futura de regra não
reescreve silenciosamente o significado de resultados antigos") estava violado. A mesma
auditoria confirmou **P0-3**: `LaudoScreen.gerarECompartilharLaudo()` podia combinar métricas de
uma execução de speedtest com o veredito/recomendação de um diagnóstico posterior e não
relacionado — `executionId` hardcoded para `""`.

Esta é a **Fatia 3** proposta na Parte 9 daquele documento — a próxima fatia recomendada, por não
depender de nenhuma decisão de produto pendente (ao contrário da Fatia 4, bloqueada pela decisão
da issue #1466) e por ser a base de dados que qualquer fatia futura de correção precisa para ser
auditável.

## Causa-raiz confirmada

Auditoria de código real (não só documento) revelou dois problemas distintos, ambos corrigidos
nesta fatia:

1. **`executionId` já existia parcialmente (GH#1221/#1225), mas nunca chegava ao banco.**
   `ResultadoSpeedtest.executionId` já é gerado uma única vez no início de
   `ExecutorSpeedtestCloudflare.executar()` (UUID) e já é usado corretamente por
   `ResultadoPdfGenerator`. Porém `SpeedtestPersistenceCoordinator` (responsável único por
   persistir `MedicaoEntity`) nunca mapeava esse campo — a entidade Room simplesmente não tinha
   onde guardá-lo. `DiagnosticInput`/`DiagnosticReport` (o pipeline de diagnóstico) também não
   carregavam identidade de execução nenhuma — o diagnóstico nunca sabia a qual execução
   pertencia.
2. **`LaudoScreen` combinava duas fontes vivas sem checagem.** `gerarECompartilharLaudo()` lia
   métricas de `ultimaMedicao` (última linha do Room) e veredito/recomendação de
   `snapshotDiagnostico.relatorio` (estado em memória) — sem qualquer garantia de que os dois
   pertenciam à mesma execução. Um diagnóstico de Wi-Fi rodado depois de um speedtest antigo (ex.:
   o fluxo de fibra em `MainViewModel.kt`, que dispara diagnóstico ao completar, usando a "última
   medição" do banco) podia contaminar o Laudo com veredito de uma execução diferente das
   métricas exibidas.

## Decisão 1 — `executionId`/`rulesVersion` são aditivos, não substituem `MedicaoEntity.id`

`MedicaoEntity.id` continua sendo o PK único gerado por linha (`UUID.randomUUID()` no momento da
persistência, sem mudança). `executionId` é um campo **novo e distinto**: representa a execução
de origem (mesma identidade usada por resultado/diagnóstico/IA/recomendação/PDF), gerada uma única
vez no **início** da execução do speedtest — não no momento de salvar a linha. Os dois raramente
divergem no caminho feliz, mas são conceitos diferentes: `id` é identidade da LINHA persistida,
`executionId` é identidade da EXECUÇÃO que a originou.

## Decisão 2 — `executionId` nunca é gerado na camada de persistência/diagnóstico

Nenhuma classe tocada nesta fatia (`SpeedtestPersistenceCoordinator`, `DiagnosticRunner`,
`DiagnosticOrchestrator`, `LaudoScreen`) gera um novo `executionId`. Todas propagam o valor já
existente:

- `ExecutorSpeedtestCloudflare.executar()` continua sendo a ÚNICA fonte que gera o UUID (GH#1221,
  inalterado nesta fatia).
- `DiagnosticInput.executionId` (novo campo, default `""`) é preenchido pelo chamador (`app`) a
  partir de `ResultadoSpeedtest.executionId` (fluxo pós-speedtest) ou de `MedicaoEntity.executionId`
  já persistido (fallback "última medição", quando não há resultado em memória).
- `DiagnosticReport.executionId` (novo campo) é preenchido por `DiagnosticRunner.run()` copiando
  `input.executionId` — nunca inventado.
- `MedicaoEntity.executionId` é preenchido por `SpeedtestPersistenceCoordinator` a partir de
  `resultado.executionId` no momento de montar a entidade.
- `RelatorioDiagnosticoSnapshot.executionId` (Laudo) usa `ultimaMedicao.executionId` — a fonte real
  dos números exibidos.

## Decisão 3 — `rulesVersion`: constante canônica única, não um valor por chamador

Criada `DiagnosticRulesVersion` (`core/diagnostico/DiagnosticRulesVersion.kt`), com
`CURRENT = "diagnostic-rules-v1"`. É a única fonte de verdade — nunca recalculada por
tela/mapper/presenter. Critério de incremento documentado no kdoc do próprio objeto (bump quando
qualquer threshold/vocabulário/lógica de `FindingEngine`/catálogo de recomendação REC-01..14
mudar de forma observável; não incrementa para copy/refactor sem mudança de comportamento).

Não reaproveitamos `DiagnosticEvaluation.rulesetVersion` (`Int`, ADR-011 seção 3.2) — é um conceito
do envelope remoto do worker (`signallq-diagnostic-worker`), ainda órfão em produção, versionando
o ruleset REMOTO. `DiagnosticRulesVersion.CURRENT` versiona o motor LOCAL
(`DiagnosticRunner`/`MetricClassifier`/`InternetDiagnosticEngine`/`SpeedtestQualityClassifier`).
Os dois podem evoluir de forma independente; não há necessidade de unificá-los nesta fatia.

## Decisão 4 — Contrato `DiagnosticExecutionContext`

Criado `core/diagnostico/DiagnosticExecutionContext.kt`: agrupa `executionId` + `rulesVersion` +
`startedAtEpochMs` (não `java.time.Instant` — `core/diagnostico` não tem core library desugaring e
o app declara `minSdk 24`). O factory `iniciar(executionId, ...)` nunca gera um id novo — recebe
um já existente e falha explicitamente (`IllegalArgumentException`) se vazio. Kotlin puro, zero
dependência de Android/Compose — reaproveitável pelo SignallQ Pro no futuro (mesmo princípio já
registrado na issue #1228: capacidades técnicas compartilháveis, UI/regra de negócio separadas por
produto).

## Decisão 5 — Migração Room 15→16 (aditiva)

`ALTER TABLE medicao ADD COLUMN executionId TEXT NOT NULL DEFAULT ''` seguido de
`UPDATE medicao SET executionId = 'legacy-' || id` (usa o próprio PK, já único, evitando
qualquer colisão) e `ALTER TABLE medicao ADD COLUMN rulesVersion TEXT NOT NULL DEFAULT
'legacy-unversioned'`. Nenhuma coluna existente é alterada, nenhuma linha é perdida. Mesmo padrão
de migração aditiva já usado 14 vezes no schema (`CoreDatabaseModulo.kt`).

`ConnectivityDiagnosisHistoryEntity` **não foi tocada** nesta fatia — o documento de auditoria
citava as duas tabelas como candidatas, mas o requisito desta fatia (definido por escopo
explícito) restringiu-se a `MedicaoEntity`. Registrado como possível fatia futura, não bloqueante.

## Decisão 6 — Correção do Laudo (P0-3): nunca combinar, sempre avisar

`LaudoScreen.kt` ganha `diagnosticoCorrespondeAMedicao(relatorioExecutionId, medicaoExecutionId)`
(regra pura: ambos os lados precisam ser não-vazios E iguais; "desconhecido" nunca vira "match"
por omissão) e `montarSnapshotLaudo(...)` (função pura, extraída de `gerarECompartilharLaudo`,
testável sem Context/Compose). Quando não há correspondência:

- as métricas continuam vindo de `ultimaMedicao` (fonte real dos números, comportamento mantido);
- veredito/resumo/recomendação da decisão são suprimidos;
- o resumo exibido informa explicitamente "Diagnóstico não disponível para esta medição" — nunca
  fica vazio de forma enganosa, nunca busca automaticamente um diagnóstico de outra execução.

O mesmo gate foi aplicado ao banner on-screen do `LaudoScreen` (não só ao PDF exportado) — é a
mesma causa-raiz, na mesma tela, e um fix parcial deixaria a tela mostrando dado inconsistente
enquanto o PDF exportado estaria correto.

## Decisão 7 — Retry técnico vs. repetição completa (mesmo `executionId` vs. novo)

Documentado explicitamente (kdoc de `ExecutorSpeedtestCloudflare.executar` já preexistente,
GH#1221/#1225, não alterado nesta fatia): o UUID é gerado uma única vez no topo de `executar()` e
reaproveitado por todas as fases internas da MESMA chamada (retry técnico de uma fase = mesmo
`executionId`). Uma nova chamada completa de `executar()` (repetição disparada pelo usuário) gera
um novo UUID. Coberto por teste de contrato em `SpeedtestPersistenceCoordinatorTest.kt`.

## Consequências

- `MedicaoEntity`, `DiagnosticInput`, `DiagnosticReport` agora carregam identidade de execução
  ponta a ponta: início do speedtest → resultado bruto → diagnóstico → persistência → histórico
  (leitura, sem redesenho de tela) → Laudo/PDF.
- Nenhum threshold, severidade, texto de diagnóstico ou recomendação foi alterado.
- Registros legados (anteriores à migração) ficam explicitamente marcados como
  `legacy-{id}`/`legacy-unversioned` — nunca reclassificados silenciosamente como se pertencessem
  à regra atual.
- Dívidas registradas, não resolvidas aqui: (1) `ConnectivityDiagnosisHistoryEntity` sem
  `executionId`/`rulesVersion` próprios (fora do escopo desta fatia); (2)
  `DiagnosticDivergenceReporter`/`RemoteDiagnosticRepository` (shadow-mode) ainda gera um
  `executionId` próprio (UUID por comparação, conceito de trace de telemetria, não de medição) —
  poderia futuramente reaproveitar `input.executionId` para melhor correlação, mas isso é
  telemetria de observabilidade, fora do requisito desta fatia; (3) descoberto de passagem:
  `core/database` não tinha `androidTestImplementation(libs.kotlinx.coroutines.test)`, quebrando a
  compilação do source-set `androidTest` inteiro (`ChatSessionDaoTest.kt`, pré-existente) —
  corrigido nesta fatia por estar na mesma área tocada (novo teste de migração instrumentado).
