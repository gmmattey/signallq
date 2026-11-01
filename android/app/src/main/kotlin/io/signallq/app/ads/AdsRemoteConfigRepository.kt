package io.signallq.app.ads

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Le o toggle remoto de anuncios nativos (issue #555) via Firebase Remote Config.
 *
 * Chave mestra `ads_native_enabled` + 4 chaves por tela. Qualquer FALHA REAL de fetch
 * (rede, timeout, Remote Config nao inicializado) cai no fallback local seguro
 * [AdsFlags.DESLIGADO] -- nunca mostra anuncio por engano, nunca trava a tela
 * esperando a config (regra explicita do plano de implementacao da #555).
 *
 * GH#1224 -- correcao: `fetchAndActivate()` retornar `false` NAO e uma falha. O SDK
 * documenta esse retorno como "nao havia configuracao nova pra ativar" (valores atuais
 * ja estavam ativos, ou nao mudaram desde o ultimo fetch) -- e um resultado normal e
 * frequente, nao um erro. Antes, qualquer `false` desligava TODAS as flags
 * silenciosamente (regressao real: anuncio aparecia so na primeira execucao e sumia
 * depois). Agora o booleano de ativacao so e logado como diagnostico
 * (`novaConfiguracaoAtivada`) -- os valores ativos/cacheados sao sempre lidos apos o
 * fetch completar sem excecao. So excecao/timeout real aciona o fallback
 * [AdsFlags.DESLIGADO], e mesmo assim so quando a propria leitura dos valores ja
 * ativados tambem falhar (RF-03).
 *
 * Recebe [FirebaseRemoteConfig] como `dagger.Lazy` de proposito (nao `kotlin.Lazy` --
 * variancia do tipo Kotlin gera wildcard que o Dagger nao consegue casar com o binding):
 * `FirebaseRemoteConfig.getInstance()` exige `FirebaseApp` ja inicializado, o que nao
 * acontece em testes Robolectric. Como [AdsRemoteConfigRepository] e injetado eagerly
 * via Hilt em [io.signallq.app.ads.AdsFlagsManager] -> `SignallQApplication`, uma
 * chamada direta e nao-lazy no `@Provides` derrubava QUALQUER teste que instanciasse a
 * Application real (nao so os testes de ads) -- so `buscarFlags()` toca `.get()`.
 */
class AdsRemoteConfigRepository(
    private val remoteConfig: Lazy<FirebaseRemoteConfig>,
) {
    suspend fun buscarFlags(): ResultadoFlagsRemotas =
        withContext(Dispatchers.IO) {
            // GH#1224 -- `remoteConfig.get()` precisa estar DENTRO da protecao contra
            // excecao tambem: `dagger.Lazy.get()` pode lancar se o FirebaseApp ainda nao
            // foi inicializado (ambiente de teste, ou corrida de inicializacao real). O
            // contrato deste metodo e nunca lancar excecao -- isso vale pra QUALQUER
            // etapa dele, nao so pro fetch em si.
            runCatching { remoteConfig.get() }
                .onFailure { erro -> Timber.w(erro, "Falha ao obter instancia de Remote Config -- usando fallback desligado") }
                .getOrNull()
                ?.let { rc -> resolverFlags(rc) }
                ?: ResultadoFlagsRemotas.fallback()
        }

    private suspend fun resolverFlags(rc: FirebaseRemoteConfig): ResultadoFlagsRemotas {
        // RF-01/RF-02 -- o booleano de fetchAndActivate() so diz se uma config NOVA
        // foi ativada, nao se a operacao "deu certo". So entra no ramo de erro se a
        // chamada lancar excecao/timeout de verdade.
        val fetchResult =
            runCatching {
                withTimeoutOrNull(FETCH_TIMEOUT_MS) { rc.fetchAndActivate().aguardar() }
            }
        val novaConfiguracaoAtivada = fetchResult.getOrNull()
        if (fetchResult.isFailure || novaConfiguracaoAtivada == null) {
            Timber.w(
                fetchResult.exceptionOrNull(),
                "Fetch de Remote Config de anuncios falhou/expirou -- tentando usar valores ja ativos",
            )
        } else {
            Timber.i("Remote Config de anuncios: fetchAndActivate concluido, novaConfiguracaoAtivada=$novaConfiguracaoAtivada")
        }

        // RF-03/RF-04 -- le os valores ATIVOS/cacheados do SDK independente do
        // resultado do fetch acima (com sucesso, timeout ou excecao): o
        // FirebaseRemoteConfig sempre tem algum valor ativo (default local ou
        // ultima config buscada com sucesso), e so nao ha nada utilizavel se essa
        // propria leitura falhar (ex.: Remote Config nunca inicializado).
        val origem = if (fetchResult.getOrNull() == true) OrigemFlagsRemotas.REMOTE_NEW else OrigemFlagsRemotas.CACHE
        return runCatching { ResultadoFlagsRemotas(lerFlagsAtivas(rc), origem) }
            .onFailure { erro -> Timber.w(erro, "Falha ao ler flags ativas de anuncios -- usando fallback desligado") }
            .getOrElse { ResultadoFlagsRemotas.fallback() }
    }

    private fun lerFlagsAtivas(rc: FirebaseRemoteConfig): AdsFlags =
        AdsFlags(
            masterEnabled = rc.getBoolean(CHAVE_MASTER),
            velocidade = rc.getBoolean(CHAVE_VELOCIDADE),
            resultado = rc.getBoolean(CHAVE_RESULTADO),
            dispositivos = rc.getBoolean(CHAVE_DISPOSITIVOS),
            historico = rc.getBoolean(CHAVE_HISTORICO),
            jogos = rc.getBoolean(CHAVE_JOGOS),
        )

    companion object {
        private const val FETCH_TIMEOUT_MS = 8_000L

        const val CHAVE_MASTER = "ads_native_enabled"
        const val CHAVE_VELOCIDADE = "ads_native_velocidade_enabled"
        const val CHAVE_RESULTADO = "ads_native_resultado_enabled"
        const val CHAVE_DISPOSITIVOS = "ads_native_dispositivos_enabled"
        const val CHAVE_HISTORICO = "ads_native_historico_enabled"
        const val CHAVE_JOGOS = "ads_native_jogos_enabled"

        /** Defaults locais do proprio Remote Config: mantido ligado por padrão no app. */
        val DEFAULTS_REMOTE_CONFIG: Map<String, Any> =
            mapOf(
                CHAVE_MASTER to true,
                CHAVE_VELOCIDADE to true,
                CHAVE_RESULTADO to true,
                CHAVE_DISPOSITIVOS to true,
                CHAVE_HISTORICO to true,
                CHAVE_JOGOS to true,
            )
    }
}

/** Origem categorizada, sem expor valores ou detalhes do Firebase na telemetria. */
enum class OrigemFlagsRemotas(
    val analyticsId: String,
) {
    REMOTE_NEW("remote_new"),
    CACHE("cache"),
    FALLBACK_ERROR("fallback_error"),
}

data class ResultadoFlagsRemotas(
    val flags: AdsFlags,
    val origem: OrigemFlagsRemotas,
) {
    companion object {
        fun fallback() = ResultadoFlagsRemotas(AdsFlags.DESLIGADO, OrigemFlagsRemotas.FALLBACK_ERROR)
    }
}

/** Ponte suspend minima para [Task] sem trazer a dependencia inteira de
 *  kotlinx-coroutines-play-services so por causa de uma unica chamada. Mesmo padrao
 *  da implementacao oficial (`resumeWithException`, nunca `cancel` -- cancelamento e
 *  reservado para cancelamento cooperativo real, nao para propagar erro de negocio). */
private suspend fun <T> Task<T>.aguardar(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val erro = task.exception
            if (erro != null) {
                cont.resumeWithException(erro)
            } else {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            }
        }
    }
