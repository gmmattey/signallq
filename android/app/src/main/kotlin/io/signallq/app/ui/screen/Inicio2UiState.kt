package io.signallq.app.ui.screen

import io.signallq.app.core.diagnostico.MonitorConexaoLeveUseCase
import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.core.network.SnapshotRede
import io.signallq.app.feature.diagnostico.EstadoDiagnostico
import io.signallq.app.feature.diagnostico.SnapshotDiagnostico
import io.signallq.app.feature.home.ResolvedHomeMeasurement
import io.signallq.app.feature.speedtest.EstadoExecucaoSpeedtest

internal enum class Inicio2Conexao { Wifi, Movel, Ethernet, Offline, Carregando }

internal sealed interface Inicio2Analise {
    data object SemAnalise : Inicio2Analise

    data class StatusEmTempoReal(
        val veredito: String,
        val motivo: String,
    ) : Inicio2Analise

    data object Carregando : Inicio2Analise

    data class Interrompida(
        val mensagem: String,
    ) : Inicio2Analise
}

internal data class Inicio2UiState(
    val conexao: Inicio2Conexao,
    val nomeConexao: String?,
    val analise: Inicio2Analise,
    val geracaoDiagnostico: Long = 0L,
    val ultimaMedicao: ResolvedHomeMeasurement? = null,
)

internal object Inicio2UiStateMapper {
    fun map(
        snapshotRede: SnapshotRede,
        estadoSpeedtest: EstadoExecucaoSpeedtest,
        diagnostico: SnapshotDiagnostico,
        medicao: ResolvedHomeMeasurement?,
        monitorConexaoLeve: MonitorConexaoLeveUseCase = MonitorConexaoLeveUseCase(),
    ): Inicio2UiState {
        val relatorio = diagnostico.relatorio
        val conexao =
            when (snapshotRede.estadoConexao) {
                EstadoConexao.wifi -> Inicio2Conexao.Wifi
                EstadoConexao.movel -> Inicio2Conexao.Movel
                EstadoConexao.ethernet -> Inicio2Conexao.Ethernet
                EstadoConexao.desconectado -> Inicio2Conexao.Offline
                EstadoConexao.desconhecido -> Inicio2Conexao.Carregando
            }

        val statusLeve = monitorConexaoLeve.calcularStatus(snapshotRede)

        val analise =
            when {
                estadoSpeedtest == EstadoExecucaoSpeedtest.executando -> Inicio2Analise.Carregando
                diagnostico.estado == EstadoDiagnostico.executando -> Inicio2Analise.Carregando
                estadoSpeedtest == EstadoExecucaoSpeedtest.erro ->
                    Inicio2Analise.Interrompida("A análise foi interrompida. Seu contexto foi preservado.")
                diagnostico.estado == EstadoDiagnostico.cancelado ->
                    Inicio2Analise.Interrompida("A análise foi cancelada. Você pode tentar novamente.")
                diagnostico.estado == EstadoDiagnostico.erro ->
                    Inicio2Analise.Interrompida(diagnostico.erroMensagem ?: "Não foi possível concluir a análise.")
                // Usando telemetria leve como fonte principal em vez de speedtest passado!
                else -> Inicio2Analise.StatusEmTempoReal(statusLeve.veredito, statusLeve.motivo)
            }

        return Inicio2UiState(
            conexao = conexao,
            nomeConexao = snapshotRede.wifiLinkSnapshot?.ssid?.ifBlank { null },
            analise = analise,
            geracaoDiagnostico = diagnostico.geracao,
            ultimaMedicao = medicao,
        )
    }
}
