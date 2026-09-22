package io.signallq.app.speedtest

import io.signallq.app.core.database.MedicaoDao
import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.database.rede.ResolvedorNetworkId
import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.DiagnosticRulesVersion
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.core.network.MonitorRede
import io.signallq.app.core.telephony.MonitorTelephony
import io.signallq.app.feature.diagnostico.DiagnosticOrchestrator
import io.signallq.app.feature.diagnostico.EstadoDiagnostico
import io.signallq.app.feature.speedtest.EstadoExecucaoSpeedtest
import io.signallq.app.feature.speedtest.ExecutorSpeedtest
import io.signallq.app.feature.speedtest.MeasurementStatus
import io.signallq.app.network.IspInfoCache
import io.signallq.app.paraColunaPersistencia
import io.signallq.app.ui.BancoOperadoras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve o valor a persistir no campo `operadoraMovel` da [MedicaoEntity].
 *
 * Rede movel: usa a operadora do SIM ([MonitorTelephony]). Wi-Fi: usa o ISP
 * publico ja resolvido ([IspInfoCache]), normalizado pelo catalogo
 * [BancoOperadoras] quando reconhecido — caso contrario mantem o nome cru do
 * ISP (GH#412: antes o campo saia sempre null em testes via Wi-Fi).
 */
internal fun resolverOperadorPersistencia(
    estadoConexao: EstadoConexao,
    operadoraMovelDetectada: String?,
    ispWifiDetectado: String?,
): String? =
    when (estadoConexao) {
        EstadoConexao.movel -> operadoraMovelDetectada
        EstadoConexao.wifi -> ispWifiDetectado?.let { raw -> BancoOperadoras.resolver(raw)?.nome ?: raw }
        else -> null
    }

/**
 * Resolve o valor a persistir no campo `bandaWifi` da [MedicaoEntity] (GH#1027).
 *
 * Só captura banda quando a conexão é Wi-Fi — célular e cabo não têm banda 2.4/5GHz.
 * Reaproveita o mesmo limiar de [BandaWifi] usado no diagnóstico (`< 3000 MHz` = 2.4GHz).
 */
internal fun resolverBandaWifiPersistencia(
    estadoConexao: EstadoConexao,
    frequenciaMhz: Int?,
): String? {
    if (estadoConexao != EstadoConexao.wifi) return null
    return when {
        frequenciaMhz == null -> null
        frequenciaMhz < 3000 -> BandaWifi.ghz24.name
        else -> BandaWifi.ghz5.name
    }
}

/**
 * Resolve o valor a persistir no campo `networkId` da [MedicaoEntity] (GH#1707, Task 2.0.09e).
 *
 * Wi-Fi usa SSID/BSSID ([ResolvedorNetworkId.paraWifi]); rede móvel usa a operadora do SIM ativo
 * ([ResolvedorNetworkId.paraRedeMovel]). Ethernet e conexão desconhecida não têm sinal estável
 * disponível — retornam `null`, mesmo contrato já documentado em [ResolvedorNetworkId] (a
 * comparação de reteste trata `null` como "sem par comparável", nunca inventa rede).
 */
internal fun resolverNetworkIdPersistencia(
    estadoConexao: EstadoConexao,
    ssidWifi: String?,
    bssidWifi: String?,
    operadoraMovelDetectada: String?,
): String? =
    when (estadoConexao) {
        EstadoConexao.wifi -> ResolvedorNetworkId.paraWifi(ssid = ssidWifi, bssid = bssidWifi)
        EstadoConexao.movel -> ResolvedorNetworkId.paraRedeMovel(operadoraMovelDetectada)
        else -> null
    }

/**
 * Resolve o valor a persistir no campo `status` da [MedicaoEntity] a partir do
 * [MeasurementStatus] canonico (GH#1221/#1225) — antes o campo era sempre hardcoded
 * "completed", mesmo para resultado contaminado. Valores conhecidos previamente
 * documentados no KDoc de [MedicaoEntity.status]: "completed"/"failed"/"partial"/
 * "timeout". Acrescenta "contaminated"/"inconclusive" como novos valores validos —
 * campo e String livre (sem enum no Room), sem necessidade de migration.
 */
internal fun statusPersistenciaParaMeasurementStatus(status: MeasurementStatus): String =
    when (status) {
        MeasurementStatus.COMPLETE -> "completed"
        MeasurementStatus.PARTIAL -> "partial"
        MeasurementStatus.INCONCLUSIVE -> "inconclusive"
        MeasurementStatus.CONTAMINATED -> "contaminated"
        MeasurementStatus.CANCELLED -> "failed"
    }

/**
 * Responsável único por persistir resultados do speedtest no Room.
 *
 * Observa [ExecutorSpeedtest.snapshotFlow], detecta estado concluído, monta a
 * [MedicaoEntity] completa (com operadoraMovel via [MonitorTelephony] em rede
 * móvel, ou via [IspInfoCache] + [BancoOperadoras] em Wi-Fi — GH#412; e bandaWifi
 * via [MonitorRede.snapshotFlow] quando a conexão é Wi-Fi — GH#1027) e salva.
 *
 * Também observa [DiagnosticOrchestrator.snapshotFlow]: quando o diagnóstico local
 * é concluído após um speedtest, atualiza o registro com o texto do diagnóstico e
 * a origem ("local"). O diagnóstico por IA (SIG-113) atualiza via [atualizarDiagnosticoIa].
 *
 * Guard interno [ultimoResultadoPersistidoEpochMs] garante que o mesmo resultado
 * não seja salvo mais de uma vez, mesmo que o snapshotFlow emita múltiplas vezes
 * com o mesmo timestamp.
 */
@Singleton
class SpeedtestPersistenceCoordinator
    @Inject
    constructor(
        private val executorSpeedtest: ExecutorSpeedtest,
        private val medicaoDao: MedicaoDao,
        private val monitorTelephony: MonitorTelephony,
        private val monitorRede: MonitorRede,
        private val diagnosticOrchestrator: DiagnosticOrchestrator,
        private val ispInfoCache: IspInfoCache,
        private val applicationScope: CoroutineScope,
    ) {
        private var ultimoResultadoPersistidoEpochMs: Long? = null

        @Volatile
        var ultimaMedicaoId: String? = null
            private set

        private var aguardandoDiagnostico = false

        /**
         * Inicia a observação do snapshotFlow do speedtest e do orquestrador de diagnóstico.
         * Deve ser chamado uma única vez na inicialização do app.
         */
        fun iniciar() {
            applicationScope.launch {
                executorSpeedtest.snapshotFlow.collect { snapshot ->
                    if (snapshot.estado != EstadoExecucaoSpeedtest.concluido) return@collect
                    val resultado = snapshot.resultado ?: return@collect
                    if (ultimoResultadoPersistidoEpochMs == resultado.timestampEpochMs) return@collect

                    ultimoResultadoPersistidoEpochMs = resultado.timestampEpochMs

                    val novoId = UUID.randomUUID().toString()
                    try {
                        medicaoDao.salvar(
                            MedicaoEntity(
                                id = novoId,
                                timestampEpochMs = resultado.timestampEpochMs,
                                connectionType = monitorRede.snapshotFlow.value.estadoConexao.name,
                                connectionTypeStart = resultado.connectionTypeStart,
                                connectionTypeEnd = resultado.connectionTypeEnd,
                                contaminado = resultado.contaminado,
                                speedtestMode = resultado.modo.name,
                                specVersion = resultado.specVersion,
                                downloadMbps = resultado.downloadMbps,
                                uploadMbps = resultado.uploadMbps,
                                latencyMs = resultado.latenciaMs,
                                jitterMs = resultado.jitterMs,
                                perdaPercentual = resultado.perdaPercentual,
                                bufferbloatMs = resultado.bufferbloatMs,
                                packetLossSource = resultado.packetLossSource,
                                vereditoStreaming = resultado.diagnosticoQualidade.vereditoStreaming.name,
                                vereditoGamer = resultado.diagnosticoQualidade.vereditoGamer.name,
                                vereditoVideoChamada = resultado.diagnosticoQualidade.vereditoVideoChamada.name,
                                gargaloPrimario = resultado.diagnosticoQualidade.gargaloPrimario.name,
                                operadoraMovel =
                                    resolverOperadorPersistencia(
                                        estadoConexao = monitorRede.snapshotFlow.value.estadoConexao,
                                        operadoraMovelDetectada = monitorTelephony.snapshotFlow.value?.operadora,
                                        ispWifiDetectado = ispInfoCache.ultimoIspNome,
                                    ),
                                bandaWifi =
                                    resolverBandaWifiPersistencia(
                                        estadoConexao = monitorRede.snapshotFlow.value.estadoConexao,
                                        frequenciaMhz =
                                            monitorRede.snapshotFlow.value.wifiLinkSnapshot
                                                ?.frequenciaMhz,
                                    ),
                                status = statusPersistenciaParaMeasurementStatus(resultado.status),
                                networkId =
                                    resolverNetworkIdPersistencia(
                                        estadoConexao = monitorRede.snapshotFlow.value.estadoConexao,
                                        ssidWifi =
                                            monitorRede.snapshotFlow.value.wifiLinkSnapshot
                                                ?.ssid,
                                        bssidWifi =
                                            monitorRede.snapshotFlow.value.wifiLinkSnapshot
                                                ?.bssid,
                                        operadoraMovelDetectada = monitorTelephony.snapshotFlow.value?.operadora,
                                    ),
                                // GH#1228 (Fase 3) — mesmo executionId gerado uma unica vez no
                                // inicio do speedtest (GH#1221/#1225, nunca regenerado aqui) e a
                                // versao canonica das regras vigentes no momento da persistencia.
                                executionId = resultado.executionId,
                                rulesVersion = DiagnosticRulesVersion.CURRENT,
                                // .agents/architecture-plan.md ("Confiabilidade estatística do
                                // diagnóstico de rede", passo 5) — perdaConfianca e os campos de
                                // latência (p95/máximo/picos) que o motor já calculava mas não
                                // persistia. resultado sempre vem de uma execução real concluída
                                // do ExecutorSpeedtestCloudflare, então os valores aqui são o
                                // cálculo de verdade desta execução (nunca um default 0.0/null
                                // fingindo dado real) — a nulidade só existe para linhas
                                // legadas, gravadas antes desta coluna existir.
                                perdaConfianca = resultado.perdaConfianca?.paraColunaPersistencia(),
                                latenciaP95Ms = resultado.diagnosticoFases.latenciaP95Ms,
                                latenciaMaxMs = resultado.diagnosticoFases.latenciaMaxMs,
                                latenciaPicos = resultado.diagnosticoFases.latenciaPicos,
                            ),
                        )
                        ultimaMedicaoId = novoId
                        // GH#1225 criterio D — resultado CONTAMINATED/INCONCLUSIVE nao pode
                        // alimentar diagnostico conclusivo (rede mudou no meio do teste, ou
                        // amostras insuficientes p/ confianca estatistica). PARTIAL ainda
                        // libera diagnostico (so nao permite conclusao causal forte na UI).
                        aguardandoDiagnostico =
                            resultado.status != MeasurementStatus.CONTAMINATED &&
                            resultado.status != MeasurementStatus.INCONCLUSIVE
                        Timber.d(
                            "SpeedtestPersistenceCoordinator: salvo ts=${resultado.timestampEpochMs} " +
                                "id=$novoId status=${resultado.status}",
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "SpeedtestPersistenceCoordinator: falha ao salvar medicao")
                    }
                }
            }

            applicationScope.launch {
                diagnosticOrchestrator.snapshotFlow.collect { snap ->
                    if (snap.estado != EstadoDiagnostico.concluido) return@collect
                    if (!aguardandoDiagnostico) return@collect
                    val relatorio = snap.relatorio ?: return@collect
                    val id = ultimaMedicaoId ?: return@collect

                    aguardandoDiagnostico = false

                    try {
                        val texto = relatorio.decisao.mensagemUsuario.ifBlank { null }
                        val problemas = extrairProblemasRelatorio(relatorio)
                        medicaoDao.atualizarDiagnostico(id, texto, "local", problemas)
                        medicaoDao.atualizarScore(id, relatorio.scoreConexao.toDouble())
                        Timber.d("SpeedtestPersistenceCoordinator: diagnostico local salvo id=$id score=${relatorio.scoreConexao}")
                    } catch (e: Exception) {
                        Timber.e(e, "SpeedtestPersistenceCoordinator: falha ao salvar diagnostico local")
                    }
                }
            }
        }

        /**
         * Atualiza o registro mais recente com o diagnóstico gerado por IA (SIG-113).
         * Sobrescreve o diagnóstico local, se existir.
         */
        suspend fun atualizarDiagnosticoIa(
            texto: String?,
            problemas: String?,
        ) {
            val id = ultimaMedicaoId ?: return
            try {
                medicaoDao.atualizarDiagnostico(id, texto, "ia", problemas)
                Timber.d("SpeedtestPersistenceCoordinator: diagnostico IA salvo id=$id")
            } catch (e: Exception) {
                Timber.e(e, "SpeedtestPersistenceCoordinator: falha ao salvar diagnostico IA")
            }
        }

        private fun extrairProblemasRelatorio(relatorio: DiagnosticReport): String? {
            val problemas =
                (
                    relatorio.wifiResultados +
                        relatorio.internetResultados +
                        relatorio.mobileResultados +
                        relatorio.fibraResultados +
                        relatorio.dnsResultados
                ).filter { it.status == DiagnosticStatus.critical || it.status == DiagnosticStatus.attention }
                    .map { it.titulo }
                    .distinct()
                    .take(5)
            return if (problemas.isEmpty()) null else problemas.joinToString(";")
        }
    }
