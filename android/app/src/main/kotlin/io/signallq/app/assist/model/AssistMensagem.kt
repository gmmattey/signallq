package io.signallq.app.assist.model

enum class AssistRole {
    USER,
    ASSISTANT,
}

data class AssistMensagem(
    val id: String,
    val role: AssistRole,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emAndamento: Boolean = false,
    val erro: Boolean = false,
)
