package io.signallq.app.wificasa

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.signallq.app.core.database.rede.ResolvedorNetworkId
import io.signallq.app.core.database.wificasa.MapeamentoWifiDao
import io.signallq.app.core.database.wificasa.MapeamentoWifiEntity
import io.signallq.app.core.database.wificasa.MarcadorMapeamentoEntity
import io.signallq.app.core.database.wificasa.StatusMapeamentoWifi
import io.signallq.app.core.database.wificasa.TipoMarcadorMapeamento
import io.signallq.app.core.diagnostico.BandaWifi
import io.signallq.app.sinalwifi.SinalWifiViewModel
import io.signallq.app.sinalwifi.normalizarSsid
import io.signallq.app.ui.screen.signalQuality
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel do fluxo "WiFi Casa" (`docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`,
 * `.agents/architecture-plan.md`) -- primeira ferramenta do hub Ferramentas com `@HiltViewModel`
 * de verdade (decisão de produto registrada no architecture plan, seção 4.3): precisa sobreviver
 * à navegação entre "adicionar marcador" e falar com o Room para retomar mapeamento incompleto
 * (RF-09), diferente do `remember{}` usado por `SinalWifiViewModel`/`ModoGamerViewModel`.
 *
 * Reaproveita [SinalWifiViewModel] internamente só para capturar a amostra de RSSI de cada
 * marcador (seção 4.3 do plano) -- nunca duplica a lógica de polling de `WifiManager`.
 */
@HiltViewModel
class WifiCasaViewModel
    @Inject
    constructor(
        private val dao: MapeamentoWifiDao,
        private val wifiManager: WifiManager,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(WifiCasaUiState())
        val uiState: StateFlow<WifiCasaUiState> = mutableUiState.asStateFlow()

        @Volatile
        private var permissaoLocalizacaoConcedida = false

        private var jobMapeamentoAtivo: Job? = null
        private var jobAmostragemCaptura: Job? = null

        private val sinalWifiViewModel by lazy {
            SinalWifiViewModel(wifiManager = wifiManager, permissaoConcedida = { permissaoLocalizacaoConcedida })
        }

        init {
            viewModelScope.launch {
                dao.observarMapeamentos().collect { historico ->
                    mutableUiState.update { it.copy(historico = historico) }
                }
            }
            viewModelScope.launch {
                val emAndamento = dao.buscarEmAndamento()
                if (emAndamento != null) {
                    abrirMapeamento(emAndamento.id, somenteLeitura = false)
                } else {
                    mutableUiState.update { it.copy(carregando = false, telaAtual = WifiCasaTela.INICIAL) }
                }
            }
        }

        /** Chamado pela tela a cada mudança de permissão de localização (mesmo padrão de
         * `temPermissaoLocalizacao` já usado por `SinalWifiScreen`). */
        fun atualizarPermissaoLocalizacao(concedida: Boolean) {
            permissaoLocalizacaoConcedida = concedida
        }

        fun iniciarNovoMapeamento() {
            viewModelScope.launch {
                val agora = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()
                dao.salvarMapeamento(
                    MapeamentoWifiEntity(
                        id = id,
                        nome = nomePadraoMapeamento(agora),
                        networkId = resolverNetworkIdAtual(),
                        criadoEmEpochMs = agora,
                        atualizadoEmEpochMs = agora,
                        status = StatusMapeamentoWifi.EM_ANDAMENTO,
                    ),
                )
                abrirMapeamento(id, somenteLeitura = false)
            }
        }

        /** RF-10: reabre um mapeamento anterior em modo leitura. */
        fun selecionarMapeamentoAnterior(id: String) {
            viewModelScope.launch {
                val mapeamento = dao.buscarMapeamento(id) ?: return@launch
                abrirMapeamento(id, somenteLeitura = mapeamento.status != StatusMapeamentoWifi.EM_ANDAMENTO)
            }
        }

        fun abrirListaAnteriores() {
            mutableUiState.update { it.copy(telaAtual = WifiCasaTela.LISTA_ANTERIORES) }
        }

        fun voltarParaInicio() {
            jobMapeamentoAtivo?.cancel()
            pararAmostragemCaptura()
            mutableUiState.update {
                WifiCasaUiState(carregando = false, telaAtual = WifiCasaTela.INICIAL, historico = it.historico)
            }
        }

        fun voltarParaGrid() {
            mutableUiState.update { it.copy(telaAtual = WifiCasaTela.GRID) }
        }

        fun iniciarCapturaMarcador(
            posX: Float,
            posY: Float,
            ehRoteador: Boolean,
        ) {
            if (uiState.value.somenteLeitura) return
            mutableUiState.update {
                it.copy(
                    telaAtual = WifiCasaTela.CAPTURA,
                    captura = CapturaMarcadorUiState(posX = posX, posY = posY, ehRoteador = ehRoteador),
                )
            }
            iniciarAmostragemCaptura()
        }

        fun atualizarRotuloCaptura(rotulo: String) {
            mutableUiState.update { atual ->
                atual.copy(captura = atual.captura?.copy(rotulo = rotulo))
            }
        }

        fun cancelarCaptura() {
            pararAmostragemCaptura()
            mutableUiState.update { it.copy(telaAtual = WifiCasaTela.GRID, captura = null) }
        }

        /** RF-02/RF-04: persiste o marcador com a última leitura de RSSI capturada. */
        fun confirmarCaptura() {
            val estado = uiState.value
            val captura = estado.captura
            val mapeamentoId = estado.mapeamentoAtual?.id
            if (captura == null || mapeamentoId == null || !captura.podeConfirmar) return
            viewModelScope.launch {
                dao.salvarMarcador(
                    MarcadorMapeamentoEntity(
                        id = UUID.randomUUID().toString(),
                        mapeamentoId = mapeamentoId,
                        rotulo = captura.rotulo.trim(),
                        tipo = if (captura.ehRoteador) TipoMarcadorMapeamento.ROTEADOR else TipoMarcadorMapeamento.COMODO,
                        posX = captura.posX,
                        posY = captura.posY,
                        rssiDbm = captura.leituraAtual.rssiAtual,
                        bandaWifi = captura.leituraAtual.banda.name,
                        criadoEmEpochMs = System.currentTimeMillis(),
                    ),
                )
                pararAmostragemCaptura()
                mutableUiState.update { it.copy(telaAtual = WifiCasaTela.GRID, captura = null) }
            }
        }

        /** RF-05: remove um marcador antes de concluir o mapeamento. */
        fun removerMarcador(id: String) {
            if (uiState.value.somenteLeitura) return
            viewModelScope.launch { dao.apagarMarcador(id) }
        }

        /**
         * RF-05: edita o rótulo de um marcador já persistido -- corrige o nome do cômodo sem exigir
         * remedição de RSSI (decisão de produto de Cora: só o nome muda, a leitura já capturada é
         * preservada). Mesma validação de não-vazio de [CapturaMarcadorUiState.podeConfirmar].
         */
        fun editarRotuloMarcador(
            id: String,
            novoRotulo: String,
        ) {
            if (uiState.value.somenteLeitura) return
            val rotuloValidado = novoRotulo.trim()
            if (rotuloValidado.isBlank()) return
            viewModelScope.launch { dao.atualizarRotuloMarcador(id, rotuloValidado) }
        }

        /**
         * RF-06/RF-07: conclui o mapeamento atual -- normalmente ou como baseline. Ao concluir
         * normalmente, verifica se há um baseline pendente compatível (mesma rede) e, se houver,
         * já abre a comparação Antes×Depois (spec, fluxo principal, passo 8).
         */
        fun concluirMapeamento(comoBaseline: Boolean) {
            val mapeamentoId = uiState.value.mapeamentoAtual?.id ?: return
            viewModelScope.launch {
                val agora = System.currentTimeMillis()
                val networkIdAtual = resolverNetworkIdAtual()
                dao.atualizarNetworkId(mapeamentoId, networkIdAtual, agora)
                val novoStatus = if (comoBaseline) StatusMapeamentoWifi.BASELINE_PENDENTE else StatusMapeamentoWifi.CONCLUIDO
                dao.atualizarStatus(mapeamentoId, novoStatus, agora)

                val baseline = networkIdAtual?.takeIf { !comoBaseline }?.let { dao.buscarBaselinePendente(it) }
                if (baseline != null && baseline.id != mapeamentoId) {
                    dao.vincularComparacao(idDepois = mapeamentoId, idAntes = baseline.id, atualizadoEmEpochMs = agora)
                    abrirComparacao(idAntes = baseline.id, idDepois = mapeamentoId)
                    return@launch
                }
                voltarParaInicio()
            }
        }

        fun fecharComparacao() {
            voltarParaInicio()
        }

        private fun abrirComparacao(
            idAntes: String,
            idDepois: String,
        ) {
            viewModelScope.launch {
                jobMapeamentoAtivo?.cancel()
                val marcadoresAntes = dao.buscarMarcadores(idAntes)
                val marcadoresDepois = dao.buscarMarcadores(idDepois)
                mutableUiState.update {
                    it.copy(
                        telaAtual = WifiCasaTela.COMPARACAO,
                        comparacao = compararMapeamentosWifiCasa(marcadoresAntes, marcadoresDepois),
                        captura = null,
                    )
                }
            }
        }

        private fun abrirMapeamento(
            id: String,
            somenteLeitura: Boolean,
        ) {
            jobMapeamentoAtivo?.cancel()
            jobMapeamentoAtivo =
                viewModelScope.launch {
                    combine(dao.observarMapeamento(id), dao.observarMarcadores(id)) { mapeamento, marcadores ->
                        mapeamento to marcadores
                    }.collect { (mapeamento, marcadores) ->
                        if (mapeamento == null) return@collect
                        mutableUiState.update {
                            it.copy(
                                carregando = false,
                                telaAtual = if (it.telaAtual == WifiCasaTela.CAPTURA) it.telaAtual else WifiCasaTela.GRID,
                                mapeamentoAtual = mapeamento,
                                marcadores = marcadores.map(::paraMarcadorUi),
                                somenteLeitura = somenteLeitura,
                            )
                        }
                    }
                }
        }

        private fun iniciarAmostragemCaptura() {
            jobAmostragemCaptura?.cancel()
            jobAmostragemCaptura =
                viewModelScope.launch {
                    launch { sinalWifiViewModel.iniciarAmostragem() }
                    sinalWifiViewModel.uiState.collect { leitura ->
                        mutableUiState.update { atual ->
                            atual.copy(captura = atual.captura?.copy(leituraAtual = leitura))
                        }
                    }
                }
        }

        private fun pararAmostragemCaptura() {
            jobAmostragemCaptura?.cancel()
            jobAmostragemCaptura = null
        }

        @SuppressLint("MissingPermission")
        private fun resolverNetworkIdAtual(): String? {
            if (!permissaoLocalizacaoConcedida) return null
            val info = runCatching { wifiManager.connectionInfo }.getOrNull()
            return info?.let { ResolvedorNetworkId.paraWifi(ssid = normalizarSsid(it.ssid), bssid = it.bssid) }
        }

        override fun onCleared() {
            super.onCleared()
            jobMapeamentoAtivo?.cancel()
            pararAmostragemCaptura()
        }
    }

private fun paraMarcadorUi(marcador: MarcadorMapeamentoEntity): MarcadorUi {
    val banda = runCatching { BandaWifi.valueOf(marcador.bandaWifi.orEmpty()) }.getOrDefault(BandaWifi.desconhecida)
    val categoria = marcador.rssiDbm?.let { signalQuality(it, banda) }
    return MarcadorUi(
        id = marcador.id,
        rotulo = marcador.rotulo,
        ehRoteador = marcador.tipo == TipoMarcadorMapeamento.ROTEADOR,
        posX = marcador.posX,
        posY = marcador.posY,
        rssiDbm = marcador.rssiDbm,
        banda = banda,
        categoria = categoria,
    )
}

private fun nomePadraoMapeamento(epochMs: Long): String {
    val formato = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
    return "Mapeamento de ${formato.format(Date(epochMs))}"
}
