package io.signallq.app.feature.diagnostico

import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticArea
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.DiagnosticReport
import io.signallq.app.core.diagnostico.DiagnosticStatus
import io.signallq.app.core.diagnostico.FibraDiagnosticInput
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.WifiDiagnosticInput
import io.signallq.app.core.featureflags.DisabledFeatureFlagProvider
import io.signallq.app.core.featureflags.FeatureFlagKeys
import io.signallq.app.core.featureflags.FeatureFlagProvider
import io.signallq.app.core.nds.NdsClientFactory
import io.signallq.app.core.network.AnalyticsHelper
import io.signallq.app.core.network.NoOpAnalyticsHelper
import io.signallq.app.feature.diagnostico.nds.NdsDiagnosticRepository
import io.signallq.app.feature.diagnostico.remote.RemoteDiagnosticRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * [remoteDiagnosticRepository] — GH#969 ligou o motor remoto (worker
 * `signallq-diagnostic`) ao fluxo real de diagnostico. Desde GH#1444 (shadow
 * mode, parte de #952) este orquestrador chama
 * [RemoteDiagnosticRepository.evaluateShadow] (motor LOCAL sempre autoritativo
 * — o que a UI mostra — com avaliacao remota em paralelo so para comparacao/
 * telemetria), NAO mais [RemoteDiagnosticRepository.evaluate] (remoto-primeiro).
 * Ver kdoc de [RemoteDiagnosticRepository] para a distincao completa entre os
 * dois metodos e por que a troca aconteceu.
 *
 * [ndsDiagnosticRepository]/[featureFlagProvider] — NDS-02k (issue #1759). Quando
 * `FeatureFlagKeys.CONSUMER_DIAGNOSTICO_NDS_LIVE_ENABLED` esta ligada,
 * [executarProtegido] chama [NdsDiagnosticRepository.evaluate] (chamada viva ao
 * NDS, com `DiagnosticRunner` local como rede de seguranca) EM VEZ de
 * [RemoteDiagnosticRepository.evaluateShadow] — as duas chamadas sao mutuamente
 * exclusivas por construcao: a flag ligada desliga o shadow mode do
 * `signallq-diagnostic-worker` para o mesmo install automaticamente, sem
 * precisar de um segundo kill switch (decisao do Luiz registrada no inventario
 * da issue #1759). Default `false` em todo ambiente preserva o comportamento
 * atual (shadow mode local-autoritativo) sem nenhuma mudanca visivel.
 *
 * `FeatureFlagKeys.USAR_NDS_V2_NO_ASSIST` (feat/nds-client-v2) segue o mesmo padrao:
 * [avaliarAssist] le a flag e repassa pro [ndsDiagnosticRepository] decidir se chama
 * `POST /v2/diagnostics/evaluate` do NDS em vez de `/v1/...` -- default `false`.
 *
 * `FeatureFlagKeys.USAR_NDS_V2_NO_FLUXO_PRINCIPAL` (feat/nds-v2-fluxo-principal) estende o
 * mesmo padrao pro fluxo principal: quando `ndsLiveEnabled` decide chamar o NDS dentro de
 * [executarProtegido], essa segunda flag decide qual contrato usar (v2 por default, v1 quando a
 * flag for desligada como kill-switch). O v2 e estritamente aditivo sobre o v1 (request so ganha
 * campos opcionais, response so troca o envelope de transporte, ja suportado pelo parser usado no
 * Assist) -- sem risco de schema, so uma troca de rota com rollback via Remote Config.
 */
class DiagnosticOrchestrator(
    private val analyticsHelper: AnalyticsHelper = NoOpAnalyticsHelper,
    private val remoteDiagnosticRepository: RemoteDiagnosticRepository =
        RemoteDiagnosticRepository(baseUrl = BuildConfig.DIAGNOSTIC_WORKER_URL),
    private val ndsDiagnosticRepository: NdsDiagnosticRepository =
        NdsDiagnosticRepository(ndsClient = NdsClientFactory.create(), analyticsHelper = analyticsHelper),
    private val featureFlagProvider: FeatureFlagProvider = DisabledFeatureFlagProvider,
) {
    /** Avaliação do SignallQ Assist pelo caminho NDS remoto dedicado.
     *
     * O Assist não participa do rollout global do diagnóstico nem do shadow mode:
     * sucesso precisa ser uma resposta NDS REMOTE; qualquer indisponibilidade é
     * propagada para a UI exibir erro explícito, sem inventar resultado local.
     *
     * `usarNdsV2` (feat/nds-client-v2) segue o mesmo padrão de leitura de flag já usado
     * em [executarProtegido] pra `CONSUMER_DIAGNOSTICO_NDS_LIVE_ENABLED`: flag ligada
     * seleciona o contrato v2, que aceita contexto parcial.
     */
    suspend fun avaliarAssist(input: DiagnosticInput): DiagnosticReport =
        ndsDiagnosticRepository.evaluateForAssist(
            input = input,
            usarNdsV2 = featureFlagProvider.isEnabled(FeatureFlagKeys.USAR_NDS_V2_NO_ASSIST),
        )

    private val mutableSnapshotFlow =
        MutableStateFlow(
            SnapshotDiagnostico(
                estado = EstadoDiagnostico.idle,
                relatorio = null,
                erroMensagem = null,
            ),
        )

    val snapshotFlow: StateFlow<SnapshotDiagnostico> = mutableSnapshotFlow.asStateFlow()

    private val gate = Any()
    private var geracaoAtual = 0L
    private var reservaAtiva: ReservaDiagnostico? = null

    class ReservaDiagnostico internal constructor(
        val geracao: Long,
    )

    sealed interface ResultadoSolicitacao {
        data object Rejeitada : ResultadoSolicitacao

        data class Aceita(
            val snapshot: SnapshotDiagnostico,
        ) : ResultadoSolicitacao
    }

    fun tentarReservar(): ReservaDiagnostico? =
        synchronized(gate) {
            if (reservaAtiva != null) return@synchronized null
            val reserva = ReservaDiagnostico(++geracaoAtual)
            reservaAtiva = reserva
            mutableSnapshotFlow.value =
                SnapshotDiagnostico(EstadoDiagnostico.executando, null, null, geracao = reserva.geracao)
            reserva
        }

    /**
     * Libera uma reserva cujo Job terminou antes ou durante a execução. Idempotente:
     * nunca altera uma geração posterior nem publica um segundo terminal.
     */
    fun cancelarReserva(reserva: ReservaDiagnostico): Boolean =
        synchronized(gate) {
            if (reservaAtiva !== reserva) return@synchronized false
            reservaAtiva = null
            mutableSnapshotFlow.value =
                SnapshotDiagnostico(
                    estado = EstadoDiagnostico.cancelado,
                    relatorio = null,
                    erroMensagem = null,
                    geracao = reserva.geracao,
                )
            true
        }

    suspend fun executar(
        internetInput: InternetDiagnosticInput?,
        wifiInput: WifiDiagnosticInput?,
        fibraInput: FibraDiagnosticInput? = null,
        // GH#1228 (Fase 3) — id da execucao de origem (ver DiagnosticInput.executionId).
        // Default "" preserva os chamadores que ainda nao propagam.
        executionId: String = "",
    ) {
        // Compatibilidade: fluxo legado do app.
        val tipo =
            when {
                wifiInput != null -> ConnectionType.wifi
                else -> ConnectionType.desconhecido
            }
        executar(
            DiagnosticInput(
                connectionType = tipo,
                internet = internetInput,
                wifi = wifiInput,
                fibra = fibraInput,
                executionId = executionId,
            ),
        )
    }

    suspend fun executar(
        input: DiagnosticInput,
        enabledAreas: Set<DiagnosticArea> = DiagnosticArea.entries.toSet(),
    ) {
        executarSolicitacao(enabledAreas) { input }
    }

    suspend fun executarSolicitacao(
        internetInput: InternetDiagnosticInput?,
        wifiInput: WifiDiagnosticInput?,
        fibraInput: FibraDiagnosticInput? = null,
        executionId: String = "",
    ): ResultadoSolicitacao {
        val tipo = if (wifiInput != null) ConnectionType.wifi else ConnectionType.desconhecido
        return executarSolicitacao {
            DiagnosticInput(
                connectionType = tipo,
                internet = internetInput,
                wifi = wifiInput,
                fibra = fibraInput,
                executionId = executionId,
            )
        }
    }

    /**
     * Inclui a preparação do input no ciclo canônico e rejeita solicitações concorrentes.
     * Retorna `false` quando já existe uma execução em andamento.
     */
    suspend fun executarSolicitacao(
        enabledAreas: Set<DiagnosticArea> = DiagnosticArea.entries.toSet(),
        prepararInput: suspend () -> DiagnosticInput,
    ): ResultadoSolicitacao {
        val reserva = tentarReservar() ?: return ResultadoSolicitacao.Rejeitada
        return ResultadoSolicitacao.Aceita(executarReservada(reserva, enabledAreas, prepararInput))
    }

    suspend fun executarReservada(
        reserva: ReservaDiagnostico,
        enabledAreas: Set<DiagnosticArea> = DiagnosticArea.entries.toSet(),
        prepararInput: suspend () -> DiagnosticInput,
    ): SnapshotDiagnostico =
        try {
            val snapshot = executarProtegido(prepararInput(), enabledAreas, reserva.geracao)
            publicarTerminal(reserva, snapshot)
        } catch (cancelamento: CancellationException) {
            publicarTerminal(
                reserva,
                SnapshotDiagnostico(EstadoDiagnostico.cancelado, null, null, geracao = reserva.geracao),
            )
            throw cancelamento
        } catch (t: Throwable) {
            Timber.e(t, "erro no diagnostico: ${t.message}")
            publicarTerminal(
                reserva,
                SnapshotDiagnostico(
                    estado = EstadoDiagnostico.erro,
                    relatorio = null,
                    erroMensagem = t.message ?: "erroDiagnostico",
                    geracao = reserva.geracao,
                ),
            )
        }

    private fun publicarTerminal(
        reserva: ReservaDiagnostico,
        snapshot: SnapshotDiagnostico,
    ): SnapshotDiagnostico =
        synchronized(gate) {
            check(reservaAtiva == reserva) { "Reserva de diagnostico inativa" }
            reservaAtiva = null
            mutableSnapshotFlow.value = snapshot
            snapshot
        }

    private suspend fun executarProtegido(
        input: DiagnosticInput,
        enabledAreas: Set<DiagnosticArea>,
        geracao: Long,
    ): SnapshotDiagnostico {
        analyticsHelper.registrarDiagIniciado(
            tipoConexao = input.connectionType.name,
            areasHabilitadas = enabledAreas.joinToString(",") { it.name.lowercase() },
            temSpeedtest = input.internet != null,
        )
        Timber.i(
            "iniciando diagnostico tipo=${input.connectionType} dl=${input.internet?.downloadMbps} ul=${input.internet?.uploadMbps} lat=${input.internet?.latencyMs} rssi=${input.wifi?.rssiDbm} fibra=${input.fibra?.isUp} dnsMs=${input.dns?.currentDnsLatencyMs}",
        )

        val ndsLiveEnabled = featureFlagProvider.isEnabled(FeatureFlagKeys.CONSUMER_DIAGNOSTICO_NDS_LIVE_ENABLED)
        val relatorio =
            if (ndsLiveEnabled) {
                val usarNdsV2 = featureFlagProvider.isEnabled(FeatureFlagKeys.USAR_NDS_V2_NO_FLUXO_PRINCIPAL)
                ndsDiagnosticRepository.evaluate(input, enabledAreas, usarNdsV2 = usarNdsV2)
            } else {
                remoteDiagnosticRepository.evaluateShadow(input, enabledAreas)
            }

        Timber.i(
            "diagnostico concluido decisao=${relatorio.decisao.id}(${relatorio.decisao.status}) " +
                "wifi=${relatorio.wifiResultados.map { "${it.id}:${it.status}" }} " +
                "internet=${relatorio.internetResultados.map { "${it.id}:${it.status}" }} " +
                "mobile=${relatorio.mobileResultados.map { "${it.id}:${it.status}" }} " +
                "fibra=${relatorio.fibraResultados.map { "${it.id}:${it.status}" }} " +
                "dns=${relatorio.dnsResultados.map { "${it.id}:${it.status}" }} " +
                "hist=${relatorio.historicoResultados.map { "${it.id}:${it.status}" }}",
        )

        val todosResultados =
            relatorio.wifiResultados + relatorio.internetResultados + relatorio.mobileResultados +
                relatorio.fibraResultados + relatorio.dnsResultados + relatorio.historicoResultados +
                relatorio.wifiCanalResultados + relatorio.redeResultados
        analyticsHelper.registrarDiagConcluido(
            tipoConexao = input.connectionType.name,
            statusGeral = relatorio.decisao.status.name,
            decisaoId = relatorio.decisao.id,
            scoreConexao = relatorio.scoreConexao.toLong(),
            confianca = relatorio.confianca,
            nResultadosCriticos = todosResultados.count { it.status == DiagnosticStatus.critical }.toLong(),
            nResultadosAttention = todosResultados.count { it.status == DiagnosticStatus.attention }.toLong(),
        )
        return SnapshotDiagnostico(
            estado = EstadoDiagnostico.concluido,
            relatorio = relatorio,
            erroMensagem = null,
            input = input,
            geracao = geracao,
        )
    }
}
