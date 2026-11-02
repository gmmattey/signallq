package io.signallq.app.assist

import io.signallq.app.assist.data.AssistContextoMapper
import io.signallq.app.assist.data.AssistSystemPromptBuilder
import io.signallq.app.core.database.MedicaoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistContextoMedicaoSalvaTest {
    private fun medicao(
        status: String = "completed",
        contaminado: Boolean = false,
        download: Double? = 96.6,
        connectionType: String = "wifi",
    ) = MedicaoEntity(
        id = "m1",
        timestampEpochMs = 1_700_000_000_000L,
        connectionType = connectionType,
        connectionTypeStart = null,
        connectionTypeEnd = null,
        contaminado = contaminado,
        speedtestMode = null,
        specVersion = null,
        downloadMbps = download,
        uploadMbps = 98.7,
        latencyMs = 18.0,
        jitterMs = 2.0,
        perdaPercentual = 0.0,
        bufferbloatMs = null,
        packetLossSource = null,
        vereditoStreaming = "OK",
        vereditoGamer = "ATENCAO",
        vereditoVideoChamada = null,
        gargaloPrimario = null,
        bandaWifi = "ghz5",
        status = status,
    )

    @Test
    fun `medicao salva completa vira contexto com metricas e data da medicao`() {
        val contexto = AssistContextoMapper.mapDeMedicaoSalva(medicao(), ispNome = "Vivo Fibra", operadoraMovel = null)

        assertFalse(contexto.semMedicao)
        assertEquals(96.6, contexto.downloadMbps)
        assertEquals(98.7, contexto.uploadMbps)
        assertEquals(18L, contexto.latenciaMs)
        assertEquals("Wi-Fi (5 GHz)", contexto.tipoRede)
        assertEquals("Vivo Fibra", contexto.operadoraOuIsp)
        assertEquals(1_700_000_000_000L, contexto.medidoEmEpochMs)
    }

    @Test
    fun `medicao salva incompleta contaminada ou sem download vira sem medicao`() {
        listOf(
            medicao(status = "failed"),
            medicao(contaminado = true),
            medicao(download = null),
        ).forEach { m ->
            val contexto = AssistContextoMapper.mapDeMedicaoSalva(m, ispNome = null, operadoraMovel = null)

            assertTrue(contexto.semMedicao)
            assertNull(contexto.downloadMbps)
            assertNull(contexto.medidoEmEpochMs)
        }
    }

    @Test
    fun `prompt informa a data da medicao salva e que ela pode estar desatualizada`() {
        val contexto = AssistContextoMapper.mapDeMedicaoSalva(medicao(), ispNome = null, operadoraMovel = null)

        val prompt = AssistSystemPromptBuilder.build(contexto)

        assertTrue(prompt.contains("Velocidade de Download: 96.6 Mbps"))
        assertTrue(prompt.contains("Medição realizada em:"))
        assertTrue(prompt.contains("pode ter mudado"))
        assertFalse(prompt.contains("Nenhuma medição de velocidade recente"))
        assertNotNull(contexto.medidoEmEpochMs)
    }
}
