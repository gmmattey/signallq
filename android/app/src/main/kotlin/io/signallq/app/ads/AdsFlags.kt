package io.signallq.app.ads

/**
 * Estado resolvido do toggle remoto de anuncios nativos (issue #555, Firebase Remote Config).
 *
 * [masterEnabled] liga/desliga tudo de uma vez; as 4 flags por tela permitem desligar
 * pontualmente uma tela sem afetar as outras. Default de todos os campos e `false` --
 * fetch falhou ou ainda nao completou nunca deve mostrar anuncio (regra do plano de
 * implementacao da #555: "nunca travar a tela esperando config" + fail-safe seguro).
 */
data class AdsFlags(
    val masterEnabled: Boolean = false,
    val velocidade: Boolean = false,
    val resultado: Boolean = false,
    val dispositivos: Boolean = false,
    val historico: Boolean = false,
    val jogos: Boolean = false,
) {
    fun habilitadoPara(slot: AdSlot): Boolean {
        if (!masterEnabled) return false
        return when (slot) {
            AdSlot.VELOCIDADE -> velocidade
            AdSlot.RESULTADO -> resultado
            AdSlot.DISPOSITIVOS -> dispositivos
            AdSlot.HISTORICO -> historico
            AdSlot.JOGOS -> jogos
        }
    }

    companion object {
        /** Fallback local desligado -- usado se o fetch falhar e leitura falhar. */
        val DESLIGADO = AdsFlags()

        /** Estado padrao com todos os slots e chave mestra ligados. */
        val LIGADO =
            AdsFlags(
                masterEnabled = true,
                velocidade = true,
                resultado = true,
                dispositivos = true,
                historico = true,
                jogos = true,
            )
    }
}
