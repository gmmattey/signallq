package io.signallq.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-20-21-test"

/**
 * `.agents/architecture-plan.md` ("Confiabilidade estatística do diagnóstico de rede", seção
 * 8/10 passo 5) — migração real 20→21, rodando contra o schema 20 gerado (não um teste unitário
 * da lógica). Cobre os dois requisitos do critério de aceite:
 *
 * 1. Banco existente (linhas gravadas antes da migração) sobrevive e recebe `perdaConfianca`/
 *    `latenciaP95Ms`/`latenciaMaxMs`/`latenciaPicos` = `NULL` — nunca um valor inventado ou
 *    reaproveitado de default (0.0/0) do motor.
 * 2. Escrita nova pós-migração grava e lê as 4 colunas normalmente.
 *
 * Plano de rollback: a migração é puramente aditiva (`ALTER TABLE ... ADD COLUMN`, nullable, sem
 * `NOT NULL DEFAULT`) — reverter é remover `MIGRATION_20_21` da lista de `addMigrations()` em
 * [CoreDatabaseModulo] e voltar `version` pra 20 em [SignallQDatabase]. Nenhuma linha é perdida
 * ao reverter porque nenhuma coluna existente foi alterada; a única perda é o valor das 4 colunas
 * novas gravadas na versão 21, que não é lido em nenhum consumidor de schema 20 (colunas novas,
 * sem consumidor anterior).
 */
@RunWith(AndroidJUnit4::class)
class Migration20Para21Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SignallQDatabase::class.java)

    @Test
    fun migracao20Para21_preservaLinhaExistenteComCamposNovosNullEAceitaEscritaNova() {
        val db = helper.createDatabase(TEST_DB, 20)
        db.execSQL(
            "INSERT INTO medicao (id, timestampEpochMs, connectionType, contaminado, status, executionId, rulesVersion) " +
                "VALUES ('medicao-legado', 1000, 'wifi', 0, 'completed', 'legacy-medicao-legado', 'legacy-unversioned')",
        )
        db.close()

        val dbMigrada = helper.runMigrationsAndValidate(TEST_DB, 21, true, CoreDatabaseModulo.MIGRATION_20_21)

        dbMigrada
            .query(
                "SELECT perdaConfianca, latenciaP95Ms, latenciaMaxMs, latenciaPicos " +
                    "FROM medicao WHERE id = 'medicao-legado'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertNull("linha legada deve ter perdaConfianca NULL, nunca inventado", cursor.getString(0))
                assertNull("linha legada deve ter latenciaP95Ms NULL, nunca 0.0 fingindo calculo", cursor.getString(1))
                assertNull("linha legada deve ter latenciaMaxMs NULL, nunca 0.0 fingindo calculo", cursor.getString(2))
                assertNull("linha legada deve ter latenciaPicos NULL, nunca 0 fingindo calculo", cursor.getString(3))
            }

        dbMigrada.execSQL(
            "INSERT INTO medicao " +
                "(id, timestampEpochMs, connectionType, contaminado, status, executionId, rulesVersion, " +
                "perdaConfianca, latenciaP95Ms, latenciaMaxMs, latenciaPicos) " +
                "VALUES ('medicao-nova', 2000, 'wifi', 0, 'completed', 'exec-nova', 'legacy-unversioned', " +
                "'INSUFICIENTE', 45.5, 120.0, 1)",
        )
        dbMigrada
            .query(
                "SELECT perdaConfianca, latenciaP95Ms, latenciaMaxMs, latenciaPicos " +
                    "FROM medicao WHERE id = 'medicao-nova'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("INSUFICIENTE", cursor.getString(0))
                assertEquals(45.5, cursor.getDouble(1), 0.0001)
                assertEquals(120.0, cursor.getDouble(2), 0.0001)
                assertEquals(1, cursor.getInt(3))
            }
    }
}
