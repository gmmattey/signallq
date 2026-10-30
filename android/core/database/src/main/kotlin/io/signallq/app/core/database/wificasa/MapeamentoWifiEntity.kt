package io.signallq.app.core.database.wificasa

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Valores possíveis de [MapeamentoWifiEntity.status]. */
object StatusMapeamentoWifi {
    const val EM_ANDAMENTO = "em_andamento"
    const val CONCLUIDO = "concluido"
    const val BASELINE_PENDENTE = "baseline_pendente"
    const val BASELINE_COMPARADO = "baseline_comparado"
}

/**
 * Sessão de mapeamento espacial de Wi-Fi (feature "WiFi Casa",
 * `docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`, `.agents/architecture-plan.md`).
 *
 * Tabela: mapeamento_wifi
 *
 * Segue o precedente sessão pai + itens filhos de `ChatSessionEntity`/`ChatMessageEntity`
 * (`core/database/chat/`) -- ver KDoc de [io.signallq.app.core.database.wificasa.MarcadorMapeamentoEntity]
 * para os marcadores filhos.
 *
 * [comparadoComSessaoId] é o vínculo do fluxo Antes×Depois (spec 3.3/RF-07): aponta da sessão
 * "depois" para a sessão "antes" (baseline). `ON DELETE SET_NULL` -- apagar uma sessão do par
 * nunca apaga a outra, só perde o vínculo entre elas.
 *
 * [networkId] reaproveita [io.signallq.app.core.database.rede.ResolvedorNetworkId] (mesmo formato
 * de `MedicaoEntity.networkId`) -- é o que permite RF-07 exigir "mesma rede" antes de oferecer a
 * comparação automática.
 */
@Entity(
    tableName = "mapeamento_wifi",
    indices = [
        Index(value = ["atualizadoEmEpochMs"]),
        Index(value = ["networkId"]),
        Index(value = ["comparadoComSessaoId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MapeamentoWifiEntity::class,
            parentColumns = ["id"],
            childColumns = ["comparadoComSessaoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class MapeamentoWifiEntity(
    @PrimaryKey
    val id: String,
    val nome: String,
    val networkId: String?,
    val criadoEmEpochMs: Long,
    val atualizadoEmEpochMs: Long,
    /** Ver [StatusMapeamentoWifi]. */
    val status: String,
    val comparadoComSessaoId: String? = null,
)
