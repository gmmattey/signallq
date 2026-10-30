package io.signallq.app.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.signallq.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes unitarios de [FirebaseAnalyticsHelper] — funil principal de
 * engajamento (SIG-155). Mocka [FirebaseAnalytics] com MockK e captura o
 * [Bundle] passado a `logEvent` para verificar nomes de evento e parametros
 * exatamente como definidos em docs/technical/analytics-events.md.
 *
 * Robolectric e necessario aqui: android.os.Bundle e um stub que lanca
 * excecao em unit tests JVM puros sem um runtime Android simulado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAnalyticsHelperTest {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var helper: FirebaseAnalyticsHelper

    @Before
    fun setUp() {
        firebaseAnalytics = mockk(relaxed = true)
        helper = FirebaseAnalyticsHelper(firebaseAnalytics)
    }

    private fun capturarBundle(nomeEvento: String): Bundle {
        val slot = slot<Bundle>()
        verify { firebaseAnalytics.logEvent(nomeEvento, capture(slot)) }
        return slot.captured
    }

    @Test
    fun `registrarAppAberto envia tipo_conexao e versao_app`() {
        helper.registrarAppAberto(tipoConexao = "wifi", primeiraAbertura = true)

        val bundle = capturarBundle("app_aberto")
        assertEquals("wifi", bundle.getString("tipo_conexao"))
        assertTrue(bundle.getBoolean("primeira_abertura"))
        assertEquals(BuildConfig.VERSION_NAME, bundle.getString("versao_app"))
        assertEquals(BuildConfig.VERSION_CODE.toLong(), bundle.getLong("version_code"))
    }

    @Test
    fun `registrarAppAberto omite primeira_abertura quando nulo`() {
        helper.registrarAppAberto(tipoConexao = "desconhecido")

        val bundle = capturarBundle("app_aberto")
        assertFalse(bundle.containsKey("primeira_abertura"))
    }

    @Test
    fun `registrarSpeedtestIniciado envia modo e tipo_conexao`() {
        helper.registrarSpeedtestIniciado(modo = "complete", tipoConexao = "mobile")

        val bundle = capturarBundle("speedtest_iniciado")
        assertEquals("complete", bundle.getString("modo"))
        assertEquals("mobile", bundle.getString("tipo_conexao"))
        assertEquals(BuildConfig.VERSION_NAME, bundle.getString("versao_app"))
    }

    @Test
    fun `registrarSpeedtestConcluido envia todas as metricas`() {
        helper.registrarSpeedtestConcluido(
            modo = "complete",
            tipoConexaoInicio = "wifi",
            tipoConexaoFim = "wifi",
            downloadMbps = 294.0,
            uploadMbps = 411.0,
            latenciaMs = 12.5,
            jitterMs = 3.2,
            perdaPct = 0.0,
            bufferbloatMs = 15.0,
            severidadeBufferbloat = "nenhum",
            stabilityScore = 92.0,
            contaminado = false,
            duracaoMs = 8_500L,
        )

        val bundle = capturarBundle("speedtest_concluido")
        assertEquals("complete", bundle.getString("modo"))
        assertEquals("wifi", bundle.getString("tipo_conexao_inicio"))
        assertEquals("wifi", bundle.getString("tipo_conexao_fim"))
        assertEquals(294.0, bundle.getDouble("download_mbps"), 0.0001)
        assertEquals(411.0, bundle.getDouble("upload_mbps"), 0.0001)
        assertEquals("nenhum", bundle.getString("severidade_bufferbloat"))
        assertFalse(bundle.getBoolean("contaminado"))
        assertEquals(8_500L, bundle.getLong("duracao_ms"))
    }

    @Test
    fun `registrarSpeedtestConcluido omite tipo_conexao_fim e duracao_ms quando nulos`() {
        helper.registrarSpeedtestConcluido(
            modo = "fast",
            tipoConexaoInicio = "mobile",
            tipoConexaoFim = null,
            downloadMbps = 50.0,
            uploadMbps = 20.0,
            latenciaMs = 30.0,
            jitterMs = 5.0,
            perdaPct = 1.0,
            bufferbloatMs = 40.0,
            severidadeBufferbloat = "leve",
            stabilityScore = 80.0,
            contaminado = true,
            duracaoMs = null,
        )

        val bundle = capturarBundle("speedtest_concluido")
        assertFalse(bundle.containsKey("tipo_conexao_fim"))
        assertFalse(bundle.containsKey("duracao_ms"))
        assertTrue(bundle.getBoolean("contaminado"))
    }

    @Test
    fun `registrarDiagIniciado envia areas_habilitadas e tem_speedtest`() {
        helper.registrarDiagIniciado(
            tipoConexao = "wifi",
            areasHabilitadas = "velocidade,wifi_sinal,dns",
            temSpeedtest = true,
        )

        val bundle = capturarBundle("diag_iniciado")
        assertEquals("wifi", bundle.getString("tipo_conexao"))
        assertEquals("velocidade,wifi_sinal,dns", bundle.getString("areas_habilitadas"))
        assertTrue(bundle.getBoolean("tem_speedtest"))
    }

    @Test
    fun `registrarDiagConcluido envia decisao_id score e confianca`() {
        helper.registrarDiagConcluido(
            tipoConexao = "wifi",
            statusGeral = "attention",
            decisaoId = "DECISAO-04",
            scoreConexao = 65L,
            confianca = 0.88,
            nResultadosCriticos = 0L,
            nResultadosAttention = 2L,
        )

        val bundle = capturarBundle("diag_concluido")
        assertEquals("attention", bundle.getString("status_geral"))
        assertEquals("DECISAO-04", bundle.getString("decisao_id"))
        assertEquals(65L, bundle.getLong("score_conexao"))
        assertEquals(0.88, bundle.getDouble("confianca"), 0.0001)
        assertEquals(2L, bundle.getLong("n_resultados_attention"))
    }

    @Test
    fun `registrarIaLaudoSolicitado envia schema e prompt version`() {
        helper.registrarIaLaudoSolicitado(
            schemaVersion = "5",
            promptVersion = "diagnostico_v5_local_primary",
            statusDiagLocal = "attention",
            temFeedbackUsuario = false,
        )

        val bundle = capturarBundle("ia_laudo_solicitado")
        assertEquals("5", bundle.getString("schema_version"))
        assertEquals("diagnostico_v5_local_primary", bundle.getString("prompt_version"))
        assertEquals("attention", bundle.getString("status_diag_local"))
        assertFalse(bundle.getBoolean("tem_feedback_usuario"))
    }

    @Test
    fun `registrarIaLaudoRecebido envia tokens e latencia quando presentes`() {
        helper.registrarIaLaudoRecebido(
            schemaVersion = "5",
            promptVersion = "diagnostico_v5_local_primary",
            statusIa = "regular",
            source = "cloud",
            modeloIa = "gemma",
            promptTokens = 1200L,
            completionTokens = 300L,
            totalTokens = 1500L,
            latenciaMs = 4200L,
        )

        val bundle = capturarBundle("ia_laudo_recebido")
        assertEquals("regular", bundle.getString("status_ia"))
        assertEquals("cloud", bundle.getString("source"))
        assertEquals("gemma", bundle.getString("modelo_ia"))
        assertEquals(1500L, bundle.getLong("total_tokens"))
        assertEquals(4200L, bundle.getLong("latencia_ms"))
    }

    @Test
    fun `registrarIaLaudoRecebido omite campos opcionais no fallback local`() {
        helper.registrarIaLaudoRecebido(
            schemaVersion = "3",
            promptVersion = "diagnostico_v5_local_primary",
            statusIa = "inconclusivo",
            source = "local",
        )

        val bundle = capturarBundle("ia_laudo_recebido")
        assertEquals("local", bundle.getString("source"))
        assertFalse(bundle.containsKey("modelo_ia"))
        assertFalse(bundle.containsKey("prompt_tokens"))
        assertFalse(bundle.containsKey("latencia_ms"))
        assertNull(bundle.getString("modelo_ia", null))
    }

    // NDS-Snapshot-12 (issue #1844) — telemetria de cobertura do snapshot enviado ao NDS.

    @Test
    fun `registrarNdsSnapshotEnviado envia todas as propriedades quando ia foi invocada`() {
        helper.registrarNdsSnapshotEnviado(
            schemaVersion = "1",
            blocosPresentes = "connection,wifi,speed,quality",
            qtdBlocosPresentes = 4,
            camposPresentesCount = 12,
            blocosCriticosAusentes = "",
            iaInvocada = true,
            iaProvider = "copy-catalog",
            duracaoMs = 850,
            resultConfidence = 0.9,
            outcome = "success",
        )

        val bundle = capturarBundle("nds_snapshot_enviado")
        assertEquals("1", bundle.getString("schema_version"))
        assertEquals("connection,wifi,speed,quality", bundle.getString("blocks_present"))
        assertEquals(4L, bundle.getLong("qtd_blocks_present"))
        assertEquals(12L, bundle.getLong("fields_present_count"))
        assertEquals("", bundle.getString("missing_critical_blocks"))
        assertTrue(bundle.getBoolean("ai_invoked"))
        assertEquals("copy-catalog", bundle.getString("ai_provider"))
        assertEquals(850L, bundle.getLong("duration_ms"))
        assertEquals(0.9, bundle.getDouble("result_confidence"), 0.0001)
        assertEquals("success", bundle.getString("outcome"))
        assertEquals(BuildConfig.VERSION_NAME, bundle.getString("versao_app"))
    }

    @Test
    fun `registrarNdsSnapshotEnviado omite ai_provider e result_confidence quando ausentes`() {
        helper.registrarNdsSnapshotEnviado(
            schemaVersion = "1",
            blocosPresentes = "connection,speed",
            qtdBlocosPresentes = 2,
            camposPresentesCount = 4,
            blocosCriticosAusentes = "wifi",
            iaInvocada = false,
            duracaoMs = 300,
            outcome = "known_error",
        )

        val bundle = capturarBundle("nds_snapshot_enviado")
        assertEquals("wifi", bundle.getString("missing_critical_blocks"))
        assertFalse(bundle.getBoolean("ai_invoked"))
        assertFalse(bundle.containsKey("ai_provider"))
        assertFalse(bundle.containsKey("result_confidence"))
        assertNull(bundle.getString("ai_provider", null))
    }

    @Test
    fun `registrarNdsSnapshotEnviado nunca carrega SSID, BSSID ou IP em nenhuma propriedade`() {
        helper.registrarNdsSnapshotEnviado(
            schemaVersion = "1",
            blocosPresentes = "connection,wifi,wifiScan,speed",
            qtdBlocosPresentes = 4,
            camposPresentesCount = 20,
            blocosCriticosAusentes = "",
            iaInvocada = true,
            iaProvider = "nds-scoring-v1",
            duracaoMs = 700,
            resultConfidence = 0.88,
            outcome = "success",
        )

        val bundle = capturarBundle("nds_snapshot_enviado")
        bundle.keySet().forEach { chave ->
            @Suppress("DEPRECATION") // Bundle.get(String) generico e a unica forma de ler um valor sem saber o tipo previamente.
            val valor = bundle.get(chave)?.toString().orEmpty()
            assertFalse("propriedade '$chave' nao deveria conter SSID", valor.contains("ssid", ignoreCase = true))
            assertFalse("propriedade '$chave' nao deveria conter BSSID", valor.contains("bssid", ignoreCase = true))
        }
    }
}
