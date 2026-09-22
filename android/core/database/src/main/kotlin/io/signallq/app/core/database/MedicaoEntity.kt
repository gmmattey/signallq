package io.signallq.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicao")
data class MedicaoEntity(
    @PrimaryKey
    val id: String,
    val timestampEpochMs: Long,
    val connectionType: String,
    val connectionTypeStart: String?,
    val connectionTypeEnd: String?,
    val contaminado: Boolean,
    val speedtestMode: String?,
    val specVersion: String?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val latencyMs: Double?,
    val jitterMs: Double?,
    val perdaPercentual: Double?,
    val bufferbloatMs: Double?,
    val packetLossSource: String?,
    val vereditoStreaming: String?,
    val vereditoGamer: String?,
    val vereditoVideoChamada: String?,
    val gargaloPrimario: String?,
    val fonte: String? = null,
    /** Operadora movel (SIM) OU provedor Wi-Fi (ISP) identificado. Null quando
     *  nenhum dos dois esta disponivel (GH#412). */
    val operadoraMovel: String? = null,
    /** Banda Wi-Fi no momento da medicao: "ghz24" ou "ghz5" (nomes de BandaWifi,
     *  ver feature.diagnostico.DiagnosticInput). Null quando a conexao nao e Wi-Fi
     *  ou a frequencia nao pode ser lida — inclusive em medicoes salvas antes desta
     *  coluna existir (GH#1027). */
    val bandaWifi: String? = null,
    val diagnosticoTexto: String? = null,
    val diagnosticoOrigem: String? = null,
    val diagnosticoProblemas: String? = null,
    /** Score 0–100 calculado pelo engine local após o diagnóstico. Null enquanto diagnóstico não foi executado. */
    val score: Double? = null,
    /** Status da medição: "completed", "failed", "partial", "timeout", "contaminated"
     *  (rede mudou durante o teste, GH#1221/#1225) ou "inconclusive" (amostras de
     *  latência abaixo do mínimo estatístico, GH#1221 RF-08). */
    val status: String = "completed",
    /** GH#1228 (Fase 3, migração 15→16) — identificador único da execução que produziu
     *  esta linha (mesmo `ResultadoSpeedtest.executionId`, GH#1221/#1225), correlacionando
     *  medição/diagnóstico/IA/recomendação/exportação da MESMA execução. Nunca vazio para
     *  escrita nova (o coordinator sempre grava `resultado.executionId`, gerado uma única
     *  vez no início do speedtest). Linhas persistidas antes desta coluna existir recebem
     *  `"legacy-{id}"` na migração — nunca reaproveitado entre linhas, nunca inventado. */
    val executionId: String = "",
    /** GH#1228 (Fase 3, migração 15→16) — versão canônica do conjunto de regras de
     *  classificação/diagnóstico em vigor quando esta linha foi classificada (ver
     *  [io.signallq.app.core.diagnostico.DiagnosticRulesVersion]). Linhas persistidas antes
     *  desta coluna existir recebem `"legacy-unversioned"` — nunca inventamos qual regra
     *  classificou dados antigos. */
    val rulesVersion: String = "legacy-unversioned",
    /** GH#1707 (Task 2.0.09e, épico #1647) — identificador estável da rede em que esta medição
     *  foi feita (ver [io.signallq.app.core.database.rede.ResolvedorNetworkId]). Permite comparar
     *  um reteste com a análise original só quando as condições de rede são equivalentes — a
     *  spec §8.8 exige declarar o limite em vez de comparar redes diferentes. Null quando não há
     *  sinal estável disponível (Ethernet, rede desconhecida) ou em linhas persistidas antes desta
     *  coluna existir — nunca inventado por trás.
     */
    val networkId: String? = null,
    /** Confiança amostral da perda de pacotes desta medição (nome de `ConfiancaAmostral`:
     *  `"SUFICIENTE"`/`"INSUFICIENTE"`, `:core:diagnostico`) — eixo ortogonal a
     *  [packetLossSource] (.agents/architecture-plan.md, "Confiabilidade estatística do
     *  diagnóstico de rede"). Null em linhas persistidas antes desta coluna existir — NUNCA
     *  inferido retroativamente; consumidores tratam null como confiança insuficiente só na
     *  exibição/reclassificação, nunca recalculam o passado. */
    val perdaConfianca: String? = null,
    /** p95 da latência (amostras válidas, antes do filtro de outlier) — já calculado pelo
     *  motor (`AnalisadorAmostragemPing`), só não era propagado até aqui antes desta migração.
     *  Null em linhas antigas (não calculado/não persistido nessa época), nunca 0.0 fingindo
     *  cálculo real. */
    val latenciaP95Ms: Double? = null,
    /** Máximo da latência (amostras válidas, antes do filtro de outlier). Mesmo contrato de
     *  nulidade de [latenciaP95Ms]. */
    val latenciaMaxMs: Double? = null,
    /** Quantidade de picos de latência detectados na amostragem. Mesmo contrato de nulidade
     *  de [latenciaP95Ms]. */
    val latenciaPicos: Int? = null,
)
