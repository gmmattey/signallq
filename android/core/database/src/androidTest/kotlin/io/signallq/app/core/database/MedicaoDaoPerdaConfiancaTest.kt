package io.signallq.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * `.agents/architecture-plan.md` ("Confiabilidade estatística do diagnóstico de rede", seção
 * 8/10 passo 5) — round-trip do DAO para `perdaConfianca`/`latenciaP95Ms`/`latenciaMaxMs`/
 * `latenciaPicos`: grava e lê de volta, confirmando que um registro sem os campos novos
 * (simulando dado legado) decodifica sem erro e sem inferir valor algum.
 */
@RunWith(AndroidJUnit4::class)
class MedicaoDaoPerdaConfiancaTest {
    private lateinit var db: SignallQDatabase
    private lateinit var dao: MedicaoDao

    @Before
    fun criarBanco() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SignallQDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.medicaoDao()
    }

    @After
    fun fecharBanco() {
        db.close()
    }

    private fun medicao(
        id: String = UUID.randomUUID().toString(),
        perdaConfianca: String? = null,
        latenciaP95Ms: Double? = null,
        latenciaMaxMs: Double? = null,
        latenciaPicos: Int? = null,
    ) = MedicaoEntity(
        id = id,
        timestampEpochMs = 1_000,
        connectionType = "wifi",
        connectionTypeStart = null,
        connectionTypeEnd = null,
        contaminado = false,
        speedtestMode = "fast",
        specVersion = "3",
        downloadMbps = 100.0,
        uploadMbps = 50.0,
        latencyMs = 20.0,
        jitterMs = 5.0,
        perdaPercentual = 7.14,
        bufferbloatMs = null,
        packetLossSource = "estimated",
        vereditoStreaming = null,
        vereditoGamer = null,
        vereditoVideoChamada = null,
        gargaloPrimario = null,
        executionId = "exec-$id",
        perdaConfianca = perdaConfianca,
        latenciaP95Ms = latenciaP95Ms,
        latenciaMaxMs = latenciaMaxMs,
        latenciaPicos = latenciaPicos,
    )

    @Test
    fun gravaELeDeVoltaOsCamposNovosQuandoPresentes() =
        runTest {
            dao.salvar(
                medicao(
                    id = "com-confianca",
                    perdaConfianca = "INSUFICIENTE",
                    latenciaP95Ms = 38.0,
                    latenciaMaxMs = 91.5,
                    latenciaPicos = 1,
                ),
            )

            val lida = dao.buscarTodas().first { it.id == "com-confianca" }

            assertEquals("INSUFICIENTE", lida.perdaConfianca)
            assertEquals(38.0, lida.latenciaP95Ms!!, 0.0001)
            assertEquals(91.5, lida.latenciaMaxMs!!, 0.0001)
            assertEquals(1, lida.latenciaPicos)
        }

    @Test
    fun registroSemOsCamposNovosDecodificaSemErroENuncaInfereValor() =
        runTest {
            // Simula dado legado: MedicaoEntity construído sem os 4 argumentos novos
            // (mesmo caminho de código de antes desta mudança).
            dao.salvar(medicao(id = "legado"))

            val lida = dao.buscarTodas().first { it.id == "legado" }

            assertNull("dado legado nunca tem confianca inferida", lida.perdaConfianca)
            assertNull("dado legado nunca tem p95 inferido", lida.latenciaP95Ms)
            assertNull("dado legado nunca tem maximo inferido", lida.latenciaMaxMs)
            assertNull("dado legado nunca tem picos inferidos", lida.latenciaPicos)
        }
}
