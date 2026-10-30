package io.signallq.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-21-22-test"

/**
 * `docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`/`.agents/architecture-plan.md` (WiFi Casa) --
 * migração real 21→22, rodando contra o schema 21 gerado (não um teste unitário da lógica).
 *
 * Cobre: banco existente (versão 21, sem as tabelas novas) sobrevive à migração e as duas
 * tabelas novas (`mapeamento_wifi`/`marcador_mapeamento`) aceitam escrita/leitura normalmente.
 *
 * Plano de rollback: migração puramente aditiva (`CREATE TABLE IF NOT EXISTS`) -- reverter é
 * remover `MIGRATION_21_22` de `addMigrations()` e voltar `version` para 21 em
 * [SignallQDatabase]. Nenhuma tabela/coluna existente é alterada.
 */
@RunWith(AndroidJUnit4::class)
class Migration21Para22Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SignallQDatabase::class.java)

    @Test
    fun migracao21Para22_criaTabelasNovasEAceitaEscrita() {
        val db = helper.createDatabase(TEST_DB, 21)
        db.close()

        val dbMigrada = helper.runMigrationsAndValidate(TEST_DB, 22, true, CoreDatabaseModulo.MIGRATION_21_22)

        dbMigrada.execSQL(
            "INSERT INTO mapeamento_wifi " +
                "(id, nome, networkId, criadoEmEpochMs, atualizadoEmEpochMs, status, comparadoComSessaoId) " +
                "VALUES ('map-1', 'Mapeamento 1', 'wifi-ssid:Casa', 1000, 1000, 'em_andamento', NULL)",
        )
        dbMigrada.execSQL(
            "INSERT INTO marcador_mapeamento " +
                "(id, mapeamentoId, rotulo, tipo, posX, posY, rssiDbm, bandaWifi, criadoEmEpochMs) " +
                "VALUES ('marc-1', 'map-1', 'Sala', 'comodo', 0.5, 0.5, -55, 'ghz5', 1000)",
        )

        dbMigrada.query("SELECT nome, status FROM mapeamento_wifi WHERE id = 'map-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Mapeamento 1", cursor.getString(0))
            assertEquals("em_andamento", cursor.getString(1))
        }
        dbMigrada.query("SELECT rotulo, rssiDbm FROM marcador_mapeamento WHERE id = 'marc-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Sala", cursor.getString(0))
            assertEquals(-55, cursor.getInt(1))
        }
    }
}
