package io.signallq.app.feature.speedtest

import io.signallq.app.core.diagnostico.MetricClassifier
import io.signallq.app.core.diagnostico.MetricStatus

/**
 * Ponte entre [SpeedtestQualityClassifier] e o motor local de classificação
 * ([MetricClassifier], `core/diagnostico`) — NDS-02k (issue #1746/#1759, completando o escopo
 * original que #1760/#1764 deixaram de fora: só cobriram `MainViewModel.analisarProblema()`).
 *
 * Espelha o seam equivalente do módulo `:app`
 * (`io.signallq.app.ui.component.ClassificacaoMetricaLocal`, NDS-02b/#1749) — não pode reusar
 * aquele arquivo diretamente porque `feature/speedtest` não pode depender de `:app`
 * (`:feature* -> :core*` é a única direção permitida; `:app` é quem depende das features, nunca
 * o contrário).
 *
 * ## Por que fica pura delegação, sem checar `nds_live_enabled` aqui
 *
 * O bufferbloat classificado aqui é calculado no instante em que o speedtest termina
 * ([ExecutorSpeedtestCloudflare], a partir do delta de latência sob carga medido on-device),
 * não como parte de uma avaliação de diagnóstico já em andamento. Os mesmos três motivos
 * estruturais documentados no seam de `:app` se aplicam:
 *
 * 1. Não existe orquestração viva do NDS neste ponto do código — quem decide chamar o NDS é
 *    `DiagnosticOrchestrator.executarProtegido` (`feature/diagnostico`), disparado por telas de
 *    diagnóstico, não pelo fim de uma medição de velocidade isolada.
 * 2. O `veredicto` do NDS é único por avaliação de diagnóstico, não um status por métrica de
 *    speedtest recém-medida — não há um veredicto do NDS equivalente a "este bufferbloat de
 *    30ms" para ler aqui.
 * 3. Dado histórico não pode ser reclassificado retroativamente (decisão do Luiz em #1746):
 *    mesmo se um veredicto vivo existisse, ele não poderia substituir o cálculo local de uma
 *    medição que acabou de terminar sem inventar uma chamada de rede síncrona no meio da UI de
 *    resultado.
 *
 * Portanto "trocar" aqui também é sintático, não de comportamento: [SpeedtestQualityClassifier]
 * para de importar [MetricClassifier] diretamente e passa a chamar por aqui — hoje delega para a
 * MESMA matemática local (coberta por
 * [io.signallq.app.feature.speedtest.ClassificacaoMetricaLocalTest] e pelo teste de
 * caracterização histórico `BufferbloatDualImplementationCharacterizationTest`, em `:app`), mas
 * fica sendo o único ponto que muda se este contexto específico (classificação de bufferbloat
 * logo após a medição) algum dia ganhar uma fonte viva do NDS.
 *
 * ## Achado comparativo (NDS-02k) — local vs. NDS
 *
 * O contrato do NDS (`network-diagnostics-service`) hoje expõe só `/v1/diagnostics/evaluate`,
 * que devolve um veredicto de diagnóstico por avaliação — não um endpoint de classificação de
 * métrica isolada equivalente a "dado este delta de bufferbloat em ms, qual a severidade".
 * `MetricClassifier.classificarBufferbloat` (3 cortes: 5/30/100ms) é a única implementação que
 * resolve esse problema hoje; não há uma segunda régua do NDS para comparar tecnicamente. Nada a
 * decidir aqui além de manter o motor local.
 */
internal fun classificarBufferbloatLocal(deltaMs: Double): MetricStatus =
    MetricClassifier.classificarBufferbloat(deltaMs)

/**
 * Mesmo seam acima, para [MetricClassifier.classificarJitter] — usado por
 * [ExecutorSpeedtestCloudflare] como um dos 4 gatilhos da janela de confirmação de
 * amostragem (Camillo, "Confiabilidade estatística do diagnóstico de rede",
 * `.agents/architecture-plan.md` seção 7, item 3: jitter classificado como
 * regular/ruim no baseline dispara +20 probes de confirmação).
 */
internal fun classificarJitterLocal(jitterMs: Double): MetricStatus =
    MetricClassifier.classificarJitter(jitterMs)
