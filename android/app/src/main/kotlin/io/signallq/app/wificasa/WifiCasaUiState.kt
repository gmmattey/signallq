package io.signallq.app.wificasa

import io.signallq.app.core.database.wificasa.MapeamentoWifiEntity
import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.sinalwifi.SinalWifiUiState

/**
 * Telas do fluxo "WiFi Casa" (`docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`, seção 6).
 */
enum class WifiCasaTela {
    INICIAL,
    GRID,
    CAPTURA,
    LISTA_ANTERIORES,
    COMPARACAO,
}

/**
 * Um marcador já persistido, pronto para exibição -- [categoria] é sempre recalculada em runtime
 * (`signalQuality`/`classificarRssiWifiLocal`), nunca lida de uma coluna persistida (RF-03,
 * AGENTS.md §8).
 */
data class MarcadorUi(
    val id: String,
    val rotulo: String,
    val ehRoteador: Boolean,
    val posX: Float,
    val posY: Float,
    val rssiDbm: Int?,
    val banda: BandaWifi,
    val categoria: String?,
)

/**
 * Estado da captura em andamento (RF-02) -- [leituraAtual] é alimentado pelo `SinalWifiViewModel`
 * interno reaproveitado (`.agents/architecture-plan.md`, seção 4.3).
 */
data class CapturaMarcadorUiState(
    val posX: Float,
    val posY: Float,
    val ehRoteador: Boolean,
    val rotulo: String = "",
    val leituraAtual: SinalWifiUiState = SinalWifiUiState(permissaoConcedida = false),
) {
    /** RF-02: marcador de cômodo exige medição válida; marcador de roteador é opcional (RF-04). */
    val podeConfirmar: Boolean
        get() = rotulo.isNotBlank() && (ehRoteador || (leituraAtual.amostrado && leituraAtual.conectado))
}

/** Resultado textual da comparação Antes×Depois por marcador correspondente (RF-08). */
enum class ResultadoComparacaoMarcador {
    MELHOROU,
    PIOROU,
    NAO_MUDOU,
    NOVO,
    REMOVIDO,
}

data class ComparacaoMarcadorUi(
    val rotulo: String,
    val categoriaAntes: String?,
    val categoriaDepois: String?,
    val resultado: ResultadoComparacaoMarcador,
)

data class WifiCasaUiState(
    val telaAtual: WifiCasaTela = WifiCasaTela.INICIAL,
    val carregando: Boolean = true,
    val mapeamentoAtual: MapeamentoWifiEntity? = null,
    val marcadores: List<MarcadorUi> = emptyList(),
    val somenteLeitura: Boolean = false,
    val historico: List<MapeamentoWifiEntity> = emptyList(),
    val captura: CapturaMarcadorUiState? = null,
    val comparacao: List<ComparacaoMarcadorUi>? = null,
)
