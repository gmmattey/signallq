package io.signallq.app.assist

import io.signallq.app.assist.data.AssistSystemPromptBuilder
import io.signallq.app.assist.model.AssistContextoRede
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistSystemPromptBuilderTest {
    @Test
    fun `quando contexto for nulo orienta que nao ha medicao recente`() {
        val prompt = AssistSystemPromptBuilder.build(null)

        assertTrue(prompt.contains("Nenhuma medição de velocidade recente"))
        assertTrue(prompt.contains("convide o usuário a rodar o teste de velocidade"))
        assertFalse(prompt.contains("Velocidade de Download"))
    }

    @Test
    fun `quando contexto indicar semMedicao orienta que nao ha medicao recente`() {
        val contexto = AssistContextoRede(semMedicao = true)
        val prompt = AssistSystemPromptBuilder.build(contexto)

        assertTrue(prompt.contains("Nenhuma medição de velocidade recente"))
    }

    @Test
    fun `quando houver metricas medidas inclui todas no prompt`() {
        val contexto =
            AssistContextoRede(
                downloadMbps = 350.5,
                uploadMbps = 120.0,
                latenciaMs = 12,
                jitterMs = 3,
                perdaPct = 0.0,
                tipoRede = "Wi-Fi (5 GHz)",
                sinalDbm = -55,
                operadoraOuIsp = "Claro Fibra",
                statusRede = "Streaming: good, Jogos: good",
                semMedicao = false,
            )

        val prompt = AssistSystemPromptBuilder.build(contexto)

        assertTrue(prompt.contains("350.5 Mbps"))
        assertTrue(prompt.contains("120.0 Mbps"))
        assertTrue(prompt.contains("12 ms"))
        assertTrue(prompt.contains("3 ms"))
        assertTrue(prompt.contains("0.0%"))
        assertTrue(prompt.contains("Wi-Fi (5 GHz)"))
        assertTrue(prompt.contains("-55 dBm"))
        assertTrue(prompt.contains("Claro Fibra"))
    }
}
