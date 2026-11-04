package io.signallq.app.assist.data

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import io.signallq.app.assist.model.AssistContextoRede
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AssistChatRepository {
    fun criarChat(
        contexto: AssistContextoRede?,
        modelName: String = MODEL_DEFAULT,
    ): AssistChatSession

    companion object {
        const val MODEL_DEFAULT = "gemini-3.8-flash"
    }
}

interface AssistChatSession {
    fun enviarMensagemStream(prompt: String): Flow<String>
}

@Singleton
class FirebaseAssistChatRepository
    @Inject
    constructor() : AssistChatRepository {
        override fun criarChat(
            contexto: AssistContextoRede?,
            modelName: String,
        ): AssistChatSession {
            val systemPrompt = AssistSystemPromptBuilder.build(contexto)
            val generativeModel =
                Firebase.ai.generativeModel(
                    modelName = modelName,
                    systemInstruction = content { text(systemPrompt) },
                )
            val chat = generativeModel.startChat()
            return FirebaseAssistChatSession(chat)
        }
    }

class FirebaseAssistChatSession(
    private val chat: Chat,
) : AssistChatSession {
    override fun enviarMensagemStream(prompt: String): Flow<String> =
        chat.sendMessageStream(prompt).map { response ->
            response.text.orEmpty()
        }
}
