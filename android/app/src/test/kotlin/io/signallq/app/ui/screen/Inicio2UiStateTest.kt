package io.signallq.app.ui.screen

import io.signallq.app.core.network.EstadoConexao
import io.signallq.app.core.network.SnapshotRede
import io.signallq.app.feature.diagnostico.EstadoDiagnostico
import io.signallq.app.feature.diagnostico.SnapshotDiagnostico
import io.signallq.app.feature.home.MetricasMedicaoHome
import io.signallq.app.feature.home.OrigemMedicaoHome
import io.signallq.app.feature.home.ResolvedHomeMeasurement
import io.signallq.app.feature.speedtest.EstadoExecucaoSpeedtest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Inicio2UiStateTest {
    private val idle = SnapshotDiagnostico(EstadoDiagnostico.idle, null, null)

    @Test
    fun `mapeia wifi movel offline e carregando sem fonte paralela`() {
        assertEquals(Inicio2Conexao.Wifi, map(rede(EstadoConexao.wifi), idle).conexao)
        assertEquals(Inicio2Conexao.Movel, map(rede(EstadoConexao.movel), idle).conexao)
        assertEquals(Inicio2Conexao.Offline, map(rede(EstadoConexao.desconectado), idle).conexao)
        assertEquals(Inicio2Conexao.Carregando, map(rede(EstadoConexao.desconhecido), idle).conexao)
    }

    @Test
    fun `agora usa o status em tempo real do monitor de conexao leve`() {
        // Redes offline devem gerar StatusEmTempoReal Offline, não SemAnalise/ResultadoAnterior
        val offline = map(rede(EstadoConexao.desconectado), idle)
        assertTrue(offline.analise is Inicio2Analise.StatusEmTempoReal)
        assertEquals("Offline", (offline.analise as Inicio2Analise.StatusEmTempoReal).veredito)

        // Redes conectadas com wifi e sinal bom/desconhecido devem ter StatusEmTempoReal Conectado/Bom/etc
        val wifi = map(rede(EstadoConexao.wifi), idle)
        assertTrue(wifi.analise is Inicio2Analise.StatusEmTempoReal)
        assertEquals("Conectado", (wifi.analise as Inicio2Analise.StatusEmTempoReal).veredito)
    }

    @Test
    fun `execucao e erro preservam loading e interrompido`() {
        assertTrue(map(rede(EstadoConexao.wifi), idle, estado = EstadoExecucaoSpeedtest.executando).analise is Inicio2Analise.Carregando)
        assertTrue(map(rede(EstadoConexao.wifi), idle, estado = EstadoExecucaoSpeedtest.erro).analise is Inicio2Analise.Interrompida)
        val executando = SnapshotDiagnostico(EstadoDiagnostico.executando, null, null, geracao = 8L)
        assertTrue(map(rede(EstadoConexao.wifi), executando).analise is Inicio2Analise.Carregando)
        assertEquals(8L, map(rede(EstadoConexao.wifi), executando).geracaoDiagnostico)
        val cancelado = SnapshotDiagnostico(EstadoDiagnostico.cancelado, null, null, geracao = 9L)
        assertTrue(map(rede(EstadoConexao.wifi), cancelado).analise is Inicio2Analise.Interrompida)
    }

    @Test
    fun `jornada unica usa Inicio2 como entrada oficial`() {
        assertEquals(
            listOf(AppShellRoot.Home, AppShellRoot.Speed, AppShellRoot.History, AppShellRoot.Tools),
            AppShellRoot.entries.toList(),
        )
    }

    @Test
    fun `propaga medicao resolvida para ultimaMedicao no estado`() {
        val medicao = medicaoAnterior()
        val state = map(rede(EstadoConexao.wifi), idle, medicao = medicao)
        assertEquals(medicao, state.ultimaMedicao)
    }

    private fun map(
        rede: SnapshotRede,
        diagnostico: SnapshotDiagnostico,
        medicao: ResolvedHomeMeasurement? = null,
        estado: EstadoExecucaoSpeedtest = EstadoExecucaoSpeedtest.idle,
    ) = Inicio2UiStateMapper.map(rede, estado, diagnostico, medicao)

    private fun rede(estado: EstadoConexao) =
        SnapshotRede.desconectado(0L).copy(estadoConexao = estado, conectado = estado != EstadoConexao.desconectado)

    private fun medicaoAnterior() =
        ResolvedHomeMeasurement(
            MetricasMedicaoHome(null, null, null, null, null, 123L, "wifi", null, null, null, true),
            OrigemMedicaoHome.ANTERIOR,
        )
}
