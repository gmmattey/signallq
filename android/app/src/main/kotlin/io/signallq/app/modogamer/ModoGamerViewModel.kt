package io.signallq.app.modogamer

import io.signallq.app.core.datastore.ModoGamerPadraoPersistido
import io.signallq.app.core.diagnostico.CatalogoJogosModoGamer
import io.signallq.app.core.diagnostico.CategoriaJogoModoGamer
import io.signallq.app.core.diagnostico.DeviceJogo
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.core.diagnostico.JogoCatalogoModoGamer
import io.signallq.app.core.diagnostico.ModoGamerEngine
import io.signallq.app.core.diagnostico.ResultadoModoGamer
import io.signallq.app.feature.diagnostico.topology.lan.NatUdpResultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface SelecaoJogoModoGamer {
    val categoria: CategoriaJogoModoGamer
    val nomeExibido: String
    val specificProbeHost: String?
    val platforms: List<DeviceJogo>?

    data class Catalogado(
        val jogo: JogoCatalogoModoGamer,
    ) : SelecaoJogoModoGamer {
        override val categoria get() = jogo.categoria
        override val nomeExibido get() = jogo.nome
        override val specificProbeHost get() = jogo.specificProbeHost
        override val platforms get() = jogo.platforms
    }

    data class ForaDoCatalogo(
        override val categoria: CategoriaJogoModoGamer,
    ) : SelecaoJogoModoGamer {
        override val nomeExibido get() = categoria.label
        override val specificProbeHost get() = null
        override val platforms: List<DeviceJogo>? get() = null
    }
}

sealed interface ModoGamerEtapa {
    data class SelecaoJogo(
        val busca: String = "",
    ) : ModoGamerEtapa

    data class SelecaoDevice(
        val selecaoJogo: SelecaoJogoModoGamer,
    ) : ModoGamerEtapa

    /**
     * Jogo e aparelho já foram escolhidos, mas ainda não há uma medição de velocidade
     * reutilizável para esta rede. Não é resultado sem dados: a pessoa precisa iniciar o
     * teste rápido antes de avaliarmos o jogo.
     */
    data class AguardandoTesteRapido(
        val selecaoJogo: SelecaoJogoModoGamer,
        val device: DeviceJogo,
    ) : ModoGamerEtapa

    data class Medindo(
        val selecaoJogo: SelecaoJogoModoGamer,
        val device: DeviceJogo,
    ) : ModoGamerEtapa

    data class Resultado(
        val selecaoJogo: SelecaoJogoModoGamer,
        val device: DeviceJogo,
        val resultado: ResultadoModoGamer,
        val salvoComoPadrao: Boolean,
        val natUdp: NatUdpResultado? = null,
    ) : ModoGamerEtapa
}

class ModoGamerViewModel(
    padraoInicial: Pair<SelecaoJogoModoGamer, DeviceJogo>?,
    private val inputAtual: () -> DiagnosticInput?,
    private val evidenciaBaseDisponivel: () -> Boolean = { true },
    private val onSalvarPadrao: suspend (jogoId: String?, categoriaFallback: String?, deviceId: String) -> Unit,
) {
    private val mutableEtapa =
        MutableStateFlow<ModoGamerEtapa>(
            if (padraoInicial != null) {
                val (selecaoJogo, device) = padraoInicial
                etapaParaEvidenciaBase(selecaoJogo, device)
            } else {
                ModoGamerEtapa.SelecaoJogo()
            },
        )
    val etapa: StateFlow<ModoGamerEtapa> = mutableEtapa.asStateFlow()

    fun buscar(query: String) {
        mutableEtapa.update { atual -> if (atual is ModoGamerEtapa.SelecaoJogo) atual.copy(busca = query) else atual }
    }

    fun selecionarJogo(jogo: JogoCatalogoModoGamer) {
        mutableEtapa.value = ModoGamerEtapa.SelecaoDevice(SelecaoJogoModoGamer.Catalogado(jogo))
    }

    fun selecionarCategoriaFallback(categoria: CategoriaJogoModoGamer) {
        mutableEtapa.value = ModoGamerEtapa.SelecaoDevice(SelecaoJogoModoGamer.ForaDoCatalogo(categoria))
    }

    fun selecionarDevice(device: DeviceJogo) {
        mutableEtapa.update { atual ->
            val selecaoJogo = (atual as? ModoGamerEtapa.SelecaoDevice)?.selecaoJogo ?: return
            etapaParaEvidenciaBase(selecaoJogo, device)
        }
    }

    /** A AppShell publicou uma medição fast nova e elegível; agora mede a rota do jogo. */
    fun onEvidenciaBaseDisponivel() {
        val aguardando = mutableEtapa.value as? ModoGamerEtapa.AguardandoTesteRapido ?: return
        if (evidenciaBaseDisponivel()) {
            mutableEtapa.value = ModoGamerEtapa.Medindo(aguardando.selecaoJogo, aguardando.device)
        }
    }

    /**
     * A rede ou a idade da medição deixou a base inelegível. Um veredito em andamento ou já
     * composto não pode permanecer visível como se ainda descrevesse a conexão atual.
     */
    fun onEvidenciaBaseIndisponivel() {
        mutableEtapa.update { atual ->
            when (atual) {
                is ModoGamerEtapa.Medindo -> ModoGamerEtapa.AguardandoTesteRapido(atual.selecaoJogo, atual.device)
                is ModoGamerEtapa.Resultado -> ModoGamerEtapa.AguardandoTesteRapido(atual.selecaoJogo, atual.device)
                else -> atual
            }
        }
    }

    fun voltarParaSelecaoJogo() {
        mutableEtapa.value = ModoGamerEtapa.SelecaoJogo()
    }

    fun voltarParaSelecaoDevice() {
        mutableEtapa.update { atual ->
            val selecaoJogo =
                when (atual) {
                    is ModoGamerEtapa.Medindo -> atual.selecaoJogo
                    is ModoGamerEtapa.AguardandoTesteRapido -> atual.selecaoJogo
                    is ModoGamerEtapa.Resultado -> atual.selecaoJogo
                    else -> return
                }
            ModoGamerEtapa.SelecaoDevice(selecaoJogo)
        }
    }

    suspend fun confirmarMedicao(
        pingEspecificoMs: Double?,
        jitterMs: Double?,
        perdaPercentual: Double?,
        natUdp: NatUdpResultado?,
    ) {
        val medindo = mutableEtapa.value as? ModoGamerEtapa.Medindo ?: return

        if (!evidenciaBaseDisponivel()) {
            mutableEtapa.value = ModoGamerEtapa.AguardandoTesteRapido(medindo.selecaoJogo, medindo.device)
            return
        }

        val salvarComoPadrao = true

        if (salvarComoPadrao) {
            val jogoId = (medindo.selecaoJogo as? SelecaoJogoModoGamer.Catalogado)?.jogo?.gameId
            val categoriaFallback = (medindo.selecaoJogo as? SelecaoJogoModoGamer.ForaDoCatalogo)?.categoria?.name
            onSalvarPadrao(jogoId, categoriaFallback, medindo.device.name)
        }

        val resultado =
            ModoGamerEngine.avaliar(
                medindo.selecaoJogo.categoria,
                medindo.device,
                inputAtual(),
                pingEspecificoMs,
                jitterMs,
                perdaPercentual,
            )

        mutableEtapa.value =
            ModoGamerEtapa.Resultado(
                selecaoJogo = medindo.selecaoJogo,
                device = medindo.device,
                resultado = resultado,
                salvoComoPadrao = salvarComoPadrao,
                natUdp = natUdp,
            )
    }

    suspend fun alternarSalvarPadrao(salvarComoPadrao: Boolean) {
        val resultadoAtual = mutableEtapa.value as? ModoGamerEtapa.Resultado ?: return

        if (salvarComoPadrao) {
            val jogoId = (resultadoAtual.selecaoJogo as? SelecaoJogoModoGamer.Catalogado)?.jogo?.gameId
            val categoriaFallback = (resultadoAtual.selecaoJogo as? SelecaoJogoModoGamer.ForaDoCatalogo)?.categoria?.name
            onSalvarPadrao(jogoId, categoriaFallback, resultadoAtual.device.name)
        } else {
            onSalvarPadrao(null, null, resultadoAtual.device.name)
        }

        mutableEtapa.value = resultadoAtual.copy(salvoComoPadrao = salvarComoPadrao)
    }

    fun trocarJogoOuDevice() {
        mutableEtapa.value = ModoGamerEtapa.SelecaoJogo()
    }

    private fun etapaParaEvidenciaBase(
        selecaoJogo: SelecaoJogoModoGamer,
        device: DeviceJogo,
    ): ModoGamerEtapa =
        if (evidenciaBaseDisponivel()) {
            ModoGamerEtapa.Medindo(selecaoJogo, device)
        } else {
            ModoGamerEtapa.AguardandoTesteRapido(selecaoJogo, device)
        }
}

fun resolverPadraoModoGamer(persistido: ModoGamerPadraoPersistido?): Pair<SelecaoJogoModoGamer, DeviceJogo>? {
    if (persistido == null) return null
    val device = runCatching { DeviceJogo.valueOf(persistido.deviceId) }.getOrNull() ?: return null
    val selecaoJogo =
        persistido.jogoId?.let { CatalogoJogosModoGamer.porId(it) }?.let { SelecaoJogoModoGamer.Catalogado(it) }
            ?: persistido.categoriaFallback
                ?.let { runCatching { CategoriaJogoModoGamer.valueOf(it) }.getOrNull() }
                ?.let { SelecaoJogoModoGamer.ForaDoCatalogo(it) }
            ?: return null
    return selecaoJogo to device
}
