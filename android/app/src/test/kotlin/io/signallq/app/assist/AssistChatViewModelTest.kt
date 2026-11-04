package io.signallq.app.assist

import io.signallq.app.assist.data.AssistChatRepository
import io.signallq.app.assist.data.AssistChatSession
import io.signallq.app.assist.model.AssistContextoRede
import io.signallq.app.assist.model.AssistRole
import io.signallq.app.assist.ui.AssistChatViewModel
import io.signallq.app.core.network.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AssistChatViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = testDispatcher
            override val main: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }

    private lateinit var repository: FakeAssistChatRepository
    private lateinit var viewModel: AssistChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAssistChatRepository()
        viewModel = AssistChatViewModel(repository = repository, dispatchers = dispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `inicializarContexto define contexto e cria sessao`() {
        val contexto = AssistContextoRede(downloadMbps = 200.0, semMedicao = false)

        viewModel.inicializarContexto(contexto)

        assertEquals(contexto, viewModel.uiState.value.contextoRede)
        assertEquals(contexto, repository.ultimoContexto)
        assertNull(viewModel.uiState.value.erro)
    }

    @Test
    fun `enviarMensagem com streaming bem-sucedido acumula chunks e finaliza`() =
        runTest(testDispatcher) {
            repository.session =
                FakeAssistChatSession(
                    chunks = listOf("Olá! ", "Sua internet ", "está normal."),
                )

            viewModel.enviarMensagem("Por que está lenta?")

            val estado = viewModel.uiState.value
            assertFalse(estado.estaProcessando)
            assertNull(estado.erro)
            assertEquals(2, estado.mensagens.size)

            val msgUsuario = estado.mensagens[0]
            assertEquals(AssistRole.USER, msgUsuario.role)
            assertEquals("Por que está lenta?", msgUsuario.texto)

            val msgAssistente = estado.mensagens[1]
            assertEquals(AssistRole.ASSISTANT, msgAssistente.role)
            assertEquals("Olá! Sua internet está normal.", msgAssistente.texto)
            assertFalse(msgAssistente.emAndamento)
            assertFalse(msgAssistente.erro)
        }

    @Test
    fun `enviarMensagem com erro de rede marca mensagem com erro e permite retry`() =
        runTest(testDispatcher) {
            repository.session = FakeAssistChatSession(shouldFail = true)

            viewModel.enviarMensagem("Minha conexão é boa?")

            val estado = viewModel.uiState.value
            assertFalse(estado.estaProcessando)
            assertNotNull(estado.erro)
            assertEquals(2, estado.mensagens.size)

            val msgAssistente = estado.mensagens[1]
            assertTrue(msgAssistente.erro)
            assertFalse(msgAssistente.emAndamento)

            // Simula reenvio após restabelecimento
            repository.session = FakeAssistChatSession(chunks = listOf("Sim, está ótima!"))
            viewModel.tentarNovamente()

            val estadoAposRetry = viewModel.uiState.value
            assertNull(estadoAposRetry.erro)
            assertEquals(2, estadoAposRetry.mensagens.size)
            assertEquals("Sim, está ótima!", estadoAposRetry.mensagens[1].texto)
        }

    @Test
    fun `cancelarGeracao encerra processamento imediatamente`() =
        runTest(testDispatcher) {
            viewModel.enviarMensagem("Pergunta longa...")
            viewModel.cancelarGeracao()

            assertFalse(viewModel.uiState.value.estaProcessando)
        }

    @Test
    fun `enviarMensagem com timeout encerra streaming e marca erro`() =
        runTest(testDispatcher) {
            repository.session = FakeAssistChatSession(delayMs = 25_000L)

            viewModel.enviarMensagem("Pergunta demorada...")
            testScheduler.advanceTimeBy(25_000L)

            val estado = viewModel.uiState.value
            assertFalse(estado.estaProcessando)
            assertNotNull(estado.erro)
            assertEquals(2, estado.mensagens.size)

            val msgAssistente = estado.mensagens[1]
            assertTrue(msgAssistente.erro)
            assertFalse(msgAssistente.emAndamento)
            assertEquals(
                "Tempo de resposta esgotado. Verifique a conexão e tente novamente.",
                msgAssistente.texto,
            )
        }

    @Test
    fun `limparSessao reseta todas as mensagens e estado`() =
        runTest(testDispatcher) {
            viewModel.enviarMensagem("Teste")
            viewModel.limparSessao()

            val estado = viewModel.uiState.value
            assertTrue(estado.mensagens.isEmpty())
            assertNull(estado.contextoRede)
            assertFalse(estado.estaProcessando)
        }
}

private class FakeAssistChatSession(
    private val chunks: List<String> = listOf("Resposta teste"),
    private val shouldFail: Boolean = false,
    private val delayMs: Long = 0L,
) : AssistChatSession {
    override fun enviarMensagemStream(prompt: String): Flow<String> =
        flow {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            if (shouldFail) throw IOException("Falha simulada na chamada de IA")
            for (chunk in chunks) {
                emit(chunk)
            }
        }
}

private class FakeAssistChatRepository : AssistChatRepository {
    var session: AssistChatSession = FakeAssistChatSession()
    var ultimoContexto: AssistContextoRede? = null

    override fun criarChat(
        contexto: AssistContextoRede?,
        modelName: String,
    ): AssistChatSession {
        ultimoContexto = contexto
        return session
    }
}
