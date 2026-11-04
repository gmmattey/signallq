package io.signallq.app.assist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.signallq.app.assist.data.AssistChatRepository
import io.signallq.app.assist.data.AssistChatSession
import io.signallq.app.assist.model.AssistContextoRede
import io.signallq.app.assist.model.AssistMensagem
import io.signallq.app.assist.model.AssistRole
import io.signallq.app.core.network.DefaultDispatcherProvider
import io.signallq.app.core.network.DispatcherProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class AssistChatUiState(
    val mensagens: List<AssistMensagem> = emptyList(),
    val contextoRede: AssistContextoRede? = null,
    val estaProcessando: Boolean = false,
    val erro: String? = null,
    val perguntasSugeridas: List<String> =
        listOf(
            "Por que está lento?",
            "Melhorar meu Wi-Fi",
            "Bom para jogar?",
        ),
)

@HiltViewModel
class AssistChatViewModel
    @Inject
    constructor(
        private val repository: AssistChatRepository,
        private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AssistChatUiState())
        val uiState: StateFlow<AssistChatUiState> = _uiState.asStateFlow()

        private var session: AssistChatSession? = null
        private var streamJob: Job? = null
        private var ultimaMensagemUsuario: String? = null

        fun inicializarContexto(contexto: AssistContextoRede?) {
            if (_uiState.value.contextoRede != null) return
            _uiState.update { it.copy(contextoRede = contexto) }
            runCatching {
                session = repository.criarChat(contexto)
            }.onFailure { erro ->
                Timber.w(erro, "Falha ao criar sessão de chat do Firebase AI Logic")
                _uiState.update { it.copy(erro = "Não foi possível iniciar o assistente no momento.") }
            }
        }

        fun enviarMensagem(texto: String) {
            val prompt = texto.trim()
            if (prompt.isBlank() || _uiState.value.estaProcessando) return

            if (session == null) {
                runCatching {
                    session = repository.criarChat(_uiState.value.contextoRede)
                }.onFailure { erro ->
                    Timber.w(erro, "Sessão nula e falha ao recriar")
                    _uiState.update { it.copy(erro = "Erro ao conectar com o assistente.") }
                    return
                }
            }

            ultimaMensagemUsuario = prompt
            val userMsgId = UUID.randomUUID().toString()
            val assistantMsgId = UUID.randomUUID().toString()

            val userMessage =
                AssistMensagem(
                    id = userMsgId,
                    role = AssistRole.USER,
                    texto = prompt,
                )

            val assistantMessage =
                AssistMensagem(
                    id = assistantMsgId,
                    role = AssistRole.ASSISTANT,
                    texto = "",
                    emAndamento = true,
                )

            _uiState.update {
                it.copy(
                    mensagens = it.mensagens + userMessage + assistantMessage,
                    estaProcessando = true,
                    erro = null,
                )
            }

            iniciarStream(assistantMsgId, prompt)
        }

        private fun iniciarStream(
            assistantMsgId: String,
            prompt: String,
        ) {
            streamJob?.cancel()
            streamJob =
                viewModelScope.launch(dispatchers.io) {
                    val currentSession = session
                    if (currentSession == null) {
                        marcarErro(assistantMsgId, "Assistente temporariamente indisponível.")
                        return@launch
                    }

                    var erroOcorrido = false
                    val buffer = StringBuilder()

                    try {
                        withTimeout(TIMEOUT_STREAM_MS) {
                            currentSession
                                .enviarMensagemStream(prompt)
                                .catch { throwable ->
                                    erroOcorrido = true
                                    Timber.e(throwable, "Erro durante streaming do Assist")
                                    marcarErro(
                                        assistantMsgId,
                                        "Não consegui responder agora. Verifique a conexão e tente novamente.",
                                    )
                                }.collect { chunk ->
                                    buffer.append(chunk)
                                    val textoAtualizado = buffer.toString()
                                    _uiState.update { state ->
                                        val mensagensAtualizadas =
                                            state.mensagens.map { msg ->
                                                if (msg.id == assistantMsgId) {
                                                    msg.copy(texto = textoAtualizado)
                                                } else {
                                                    msg
                                                }
                                            }
                                        state.copy(mensagens = mensagensAtualizadas)
                                    }
                                }
                        }
                    } catch (e: TimeoutCancellationException) {
                        erroOcorrido = true
                        Timber.w(e, "Timeout atingido durante streaming do Assist")
                        marcarErro(
                            assistantMsgId,
                            "Tempo de resposta esgotado. Verifique a conexão e tente novamente.",
                        )
                    } catch (e: Exception) {
                        erroOcorrido = true
                        Timber.e(e, "Falha na chamada do Assist")
                        marcarErro(
                            assistantMsgId,
                            "Não consegui responder agora. Verifique a conexão e tente novamente.",
                        )
                    }

                    if (!erroOcorrido) {
                        // Finaliza streaming com sucesso
                        _uiState.update { state ->
                            val mensagensAtualizadas =
                                state.mensagens.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(emAndamento = false)
                                    } else {
                                        msg
                                    }
                                }
                            state.copy(
                                mensagens = mensagensAtualizadas,
                                estaProcessando = false,
                            )
                        }
                    }
                }
        }

        private fun marcarErro(
            assistantMsgId: String,
            mensagemErro: String,
        ) {
            _uiState.update { state ->
                val mensagensAtualizadas =
                    state.mensagens.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(
                                texto = if (msg.texto.isBlank()) mensagemErro else msg.texto,
                                emAndamento = false,
                                erro = true,
                            )
                        } else {
                            msg
                        }
                    }
                state.copy(
                    mensagens = mensagensAtualizadas,
                    estaProcessando = false,
                    erro = mensagemErro,
                )
            }
        }

        fun tentarNovamente() {
            val ultimo = ultimaMensagemUsuario
            if (!ultimo.isNullOrBlank() && !_uiState.value.estaProcessando) {
                // Recria a sessão para restabelecer a conexão limpa
                runCatching {
                    session = repository.criarChat(_uiState.value.contextoRede)
                }
                // Remove a mensagem com erro e a mensagem do usuário correspondente para não duplicar na conversa
                _uiState.update { state ->
                    val ultimasSemErro = state.mensagens.dropLastWhile { it.role == AssistRole.ASSISTANT && it.erro }
                    val ultimasSemUsuarioDuplicado = ultimasSemErro.dropLastWhile { it.role == AssistRole.USER && it.texto == ultimo }
                    state.copy(mensagens = ultimasSemUsuarioDuplicado, erro = null)
                }
                enviarMensagem(ultimo)
            }
        }

        fun cancelarGeracao() {
            streamJob?.cancel()
            _uiState.update { state ->
                val mensagensAtualizadas =
                    state.mensagens.map { msg ->
                        if (msg.emAndamento) {
                            msg.copy(emAndamento = false)
                        } else {
                            msg
                        }
                    }
                state.copy(
                    mensagens = mensagensAtualizadas,
                    estaProcessando = false,
                )
            }
        }

        fun limparSessao() {
            cancelarGeracao()
            session = null
            _uiState.value = AssistChatUiState()
        }

        override fun onCleared() {
            super.onCleared()
            cancelarGeracao()
        }

        companion object {
            const val TIMEOUT_STREAM_MS = 20_000L
        }
    }
