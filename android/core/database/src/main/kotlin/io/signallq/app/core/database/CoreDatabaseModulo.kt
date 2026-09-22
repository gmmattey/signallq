package io.signallq.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CoreDatabaseModulo {
    private val migracao1para2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN jitterMs REAL")
                db.execSQL("ALTER TABLE medicao ADD COLUMN perdaPercentual REAL")
                db.execSQL("ALTER TABLE medicao ADD COLUMN bufferbloatMs REAL")
            }
        }

    private val migracao2para3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN speedtestMode TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN specVersion TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN packetLossSource TEXT")
            }
        }

    private val migracao3para4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN connectionTypeStart TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN connectionTypeEnd TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN contaminado INTEGER NOT NULL DEFAULT 0")
            }
        }

    private val migracao4para5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN vereditoStreaming TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN vereditoGamer TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN vereditoVideoChamada TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN gargaloPrimario TEXT")
            }
        }

    private val migracao5para6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS apelido_dispositivo " +
                        "(mac TEXT NOT NULL PRIMARY KEY, apelido TEXT NOT NULL)",
                )
            }
        }

    private val migracao6para7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN fonte TEXT")
            }
        }

    /**
     * Torna apelido_dispositivo.apelido nullable.
     * SQLite nao suporta DROP CONSTRAINT — recria a tabela via rename/create/copy/drop.
     */
    private val migracao7para8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS apelido_dispositivo_new " +
                        "(mac TEXT NOT NULL PRIMARY KEY, apelido TEXT)",
                )
                db.execSQL(
                    "INSERT INTO apelido_dispositivo_new (mac, apelido) " +
                        "SELECT mac, apelido FROM apelido_dispositivo",
                )
                db.execSQL("DROP TABLE apelido_dispositivo")
                db.execSQL("ALTER TABLE apelido_dispositivo_new RENAME TO apelido_dispositivo")
            }
        }

    private val migracao8para9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN operadoraMovel TEXT")
            }
        }

    private val migracao9para10 =
        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                        "`id` TEXT NOT NULL, " +
                        "`titulo` TEXT NOT NULL, " +
                        "`criadoEmEpochMs` INTEGER NOT NULL, " +
                        "`atualizadoEmEpochMs` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`tipoDiagnostico` TEXT, " +
                        "`nomeModelo` TEXT, " +
                        "`diagnosticoPayloadJson` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_sessions_atualizadoEmEpochMs` " +
                        "ON `chat_sessions` (`atualizadoEmEpochMs`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, " +
                        "`sessionId` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`metadataJson` TEXT, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` " +
                        "ON `chat_messages` (`sessionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId_createdAtEpochMs` " +
                        "ON `chat_messages` (`sessionId`, `createdAtEpochMs`)",
                )
            }
        }

    private val migracao10para11 =
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN diagnosticoTexto TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN diagnosticoOrigem TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN diagnosticoProblemas TEXT")
            }
        }

    /**
     * SIG-160: score em MedicaoEntity
     * SIG-163: status em MedicaoEntity (remove hardcode "completed" no ingest)
     * SIG-161: diagnosisId em ChatSessionEntity (correlação com medicao no ingest)
     * SIG-162: tokens de IA em ChatSessionEntity
     */
    private val migracao11para12 =
        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN score REAL")
                db.execSQL("ALTER TABLE medicao ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN diagnosisId TEXT")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN promptTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN completionTokens INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN totalTokens INTEGER NOT NULL DEFAULT 0")
            }
        }

    /** SIG-812: historico de exibicao/feedback de recomendacoes do RecommendationEngine (#790). */
    private val migracao12para13 =
        object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recommendation_history` (" +
                        "`id` TEXT NOT NULL, " +
                        "`recommendationId` TEXT NOT NULL, " +
                        "`shownAtEpochMs` INTEGER NOT NULL, " +
                        "`feedback` TEXT, " +
                        "`feedbackAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recommendation_history_shownAtEpochMs` " +
                        "ON `recommendation_history` (`shownAtEpochMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recommendation_history_recommendationId` " +
                        "ON `recommendation_history` (`recommendationId`)",
                )
            }
        }

    /** GH#1027: banda Wi-Fi (2.4/5GHz) capturada no momento da medicao, exibida no Historico. */
    private val migracao13para14 =
        object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN bandaWifi TEXT")
            }
        }

    /** GH#1512: historico do diagnostico local de conectividade (Wi-Fi conectado sem
     *  internet). So campos ja avaliados/sanitizados -- nunca SSID/BSSID/IP/DNS brutos. */
    private val migracao14para15 =
        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `connectivity_diagnosis_history` (" +
                        "`id` TEXT NOT NULL, " +
                        "`startedAtEpochMs` INTEGER NOT NULL, " +
                        "`finishedAtEpochMs` INTEGER NOT NULL, " +
                        "`transport` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`confidence` TEXT NOT NULL, " +
                        "`wifiConnected` INTEGER NOT NULL, " +
                        "`localAddressAvailable` INTEGER NOT NULL, " +
                        "`gatewayConfigured` INTEGER NOT NULL, " +
                        "`gatewayReachableOutcome` TEXT NOT NULL, " +
                        "`dnsConfigured` INTEGER NOT NULL, " +
                        "`dnsReachableOutcome` TEXT NOT NULL, " +
                        "`externalIpReachableOutcome` TEXT NOT NULL, " +
                        "`hostnameReachableOutcome` TEXT NOT NULL, " +
                        "`androidInternetCapability` INTEGER NOT NULL, " +
                        "`androidValidated` INTEGER NOT NULL, " +
                        "`captivePortalDetected` INTEGER NOT NULL, " +
                        "`mobileFallbackAvailable` INTEGER NOT NULL, " +
                        "`incompleteReason` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_connectivity_diagnosis_history_startedAtEpochMs` " +
                        "ON `connectivity_diagnosis_history` (`startedAtEpochMs`)",
                )
            }
        }

    /** GH#1228 (Fase 3) — adiciona `executionId`/`rulesVersion` a `medicao`, requisito
     *  não-negociável da issue #1228 ("mudança futura de regra não reescreve
     *  silenciosamente o significado de resultados antigos"). Aditiva: nenhuma coluna
     *  existente é alterada/removida, nenhuma linha é perdida.
     *
     *  Linhas já existentes (gravadas antes desta migração) recebem:
     *  - `executionId = 'legacy-' || id` — nunca vazio, nunca reaproveitado entre linhas
     *    (usa o próprio PK da linha, que já é único).
     *  - `rulesVersion = 'legacy-unversioned'` (default da própria coluna) — nunca
     *    inventamos qual conjunto de regras classificou esses dados.
     *
     *  Escrita nova (pós-migração) sempre grava os dois campos explicitamente via
     *  `SpeedtestPersistenceCoordinator` (nunca depende do default SQL da coluna). */
    private val migracao15para16 =
        object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN executionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicao ADD COLUMN rulesVersion TEXT NOT NULL DEFAULT 'legacy-unversioned'")
                db.execSQL("UPDATE medicao SET executionId = 'legacy-' || id")
            }
        }

    /** GH#1462 (parte de #951): cache local por resultado individual do diretorio remoto
     *  de provedores -- nunca uma copia da tabela inteira do worker (ver kdoc de
     *  `ProviderDirectoryCacheEntity`, `:coreDatabase`). */
    private val migracao16para17 =
        object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `provider_directory_cache` (" +
                        "`cacheKey` TEXT NOT NULL, " +
                        "`providerId` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`logoUrl` TEXT, " +
                        "`sacPhone` TEXT, " +
                        "`technicalSupportPhone` TEXT, " +
                        "`whatsappUrl` TEXT, " +
                        "`websiteUrl` TEXT, " +
                        "`customerAreaUrl` TEXT, " +
                        "`ombudsmanPhone` TEXT, " +
                        "`status` TEXT, " +
                        "`cacheVersion` INTEGER, " +
                        "`cacheExpiresAtMs` INTEGER, " +
                        "`fetchedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`cacheKey`))",
                )
            }
        }

    internal val MIGRATION_17_18 =
        object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `analytics_outbox` (" +
                        "`id` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                        "`nextAttemptAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analytics_outbox_nextAttemptAtEpochMs` " +
                        "ON `analytics_outbox` (`nextAttemptAtEpochMs`)",
                )
            }
        }

    /** GH#1707 (Task 2.0.09e, épico #1647) — `networkId` em `medicao`, requisito de schema pra
     *  comparar um reteste com a análise original só quando as condições de rede são equivalentes
     *  (spec §8.8: "reteste compara condições equivalentes ou declara limite"). Aditiva, coluna
     *  nullable — nenhuma coluna existente é alterada/removida, nenhuma linha é perdida.
     *
     *  Linhas já existentes (gravadas antes desta migração) recebem `networkId = NULL` — nunca
     *  inventamos qual rede gerou uma medição antiga. `NULL` já é o resultado documentado de
     *  [io.signallq.app.core.database.rede.ResolvedorNetworkId] quando não há sinal estável (ex.:
     *  Ethernet), então a comparação de reteste trata "nunca resolvido" e "resolvido sem sinal"
     *  da mesma forma: `comparavel = false`, nunca comparação com aviso. */
    internal val MIGRATION_18_19 =
        object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN networkId TEXT")
            }
        }

    /** GH#1787 -- corrige a inconsistência original da migração 17->18: aquela migração já
     *  criava `index_analytics_outbox_nextAttemptAtEpochMs` via SQL bruto, mas
     *  `AnalyticsOutboxEntity` nunca declarou o `@Index` correspondente. Quem migrou pela
     *  17->18 (ou além) já tem o índice físico no SQLite; instalação nova antes desta correção
     *  não tinha. `CREATE INDEX IF NOT EXISTS` é seguro para os dois casos -- não falha em quem
     *  já tem o índice, cria em quem não tem. Nenhum dado é alterado ou perdido. */
    internal val MIGRATION_19_20 =
        object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analytics_outbox_nextAttemptAtEpochMs` " +
                        "ON `analytics_outbox` (`nextAttemptAtEpochMs`)",
                )
            }
        }

    /** `.agents/architecture-plan.md` ("Confiabilidade estatística do diagnóstico de rede",
     *  seção 8/10 passo 5) — persiste a confiança amostral da perda de pacotes e os campos de
     *  latência (p95/máximo/picos) que o motor já calculava mas não propagava até `medicao`.
     *  100% aditiva: 4 colunas nullable novas, nenhuma coluna existente alterada/removida,
     *  nenhuma linha perdida. Linhas já existentes recebem `NULL` nas 4 colunas — nunca inferido
     *  retroativamente (SQLite já faz isso por padrão em `ADD COLUMN` sem `DEFAULT`, sem
     *  necessidade de `UPDATE`). */
    internal val MIGRATION_20_21 =
        object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicao ADD COLUMN perdaConfianca TEXT")
                db.execSQL("ALTER TABLE medicao ADD COLUMN latenciaP95Ms REAL")
                db.execSQL("ALTER TABLE medicao ADD COLUMN latenciaMaxMs REAL")
                db.execSQL("ALTER TABLE medicao ADD COLUMN latenciaPicos INTEGER")
            }
        }

    fun criarBanco(context: Context): SignallQDatabase =
        Room
            .databaseBuilder(
                context.applicationContext,
                SignallQDatabase::class.java,
                "linkaKotlin.db",
            ).addMigrations(migracao1para2)
            .addMigrations(migracao2para3)
            .addMigrations(migracao3para4)
            .addMigrations(migracao4para5)
            .addMigrations(migracao5para6)
            .addMigrations(migracao6para7)
            .addMigrations(migracao7para8)
            .addMigrations(migracao8para9)
            .addMigrations(migracao9para10)
            .addMigrations(migracao10para11)
            .addMigrations(migracao11para12)
            .addMigrations(migracao12para13)
            .addMigrations(migracao13para14)
            .addMigrations(migracao14para15)
            .addMigrations(migracao15para16)
            .addMigrations(migracao16para17)
            .addMigrations(MIGRATION_17_18)
            .addMigrations(MIGRATION_18_19)
            .addMigrations(MIGRATION_19_20)
            .addMigrations(MIGRATION_20_21)
            .build()
}
