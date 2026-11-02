package io.signallq.app.ui.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import io.signallq.app.ads.AdsTelemetry
import io.signallq.app.ads.NativeAdContentSignal
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber

/**
 * Contrato tipado para as migrações 2.0. A chave estável da coroutine impede request duplicada
 * por recomposição; troca de slot/configuração cancela a sessão e destrói exatamente o fill
 * anterior. Estados sem fill não devem ser renderizados pelos consumidores.
 */
@Composable
fun rememberNativeAdState(
    adUnitId: String,
    contentSignal: NativeAdContentSignal,
    eligibility: NativeAdEligibility,
): State<NativeAdLoadState> {
    val context = LocalContext.current
    val telemetry = LocalAdsTelemetry.current
    val requester = remember(context, telemetry) { GoogleNativeAdRequester(context, telemetry) }
    return rememberNativeAdState(adUnitId, contentSignal, eligibility, requester, telemetry)
}

@Composable
internal fun rememberNativeAdState(
    adUnitId: String,
    contentSignal: NativeAdContentSignal,
    eligibility: NativeAdEligibility,
    requester: NativeAdRequester,
    telemetry: AdsTelemetry? = null,
): State<NativeAdLoadState> {
    return produceState<NativeAdLoadState>(
        initialValue = eligibility.initialState(),
        adUnitId,
        contentSignal,
        eligibility,
    ) {
        value = eligibility.initialState()
        if (!eligibility.canLoad) {
            telemetry?.registrarElegibilidadeInvalida(
                slot = eligibility.slot,
                reason = eligibility.initialState().analyticsOutcome(),
            )
            return@produceState
        }

        var adCarregado: NativeAd? = null
        var sessionActive = true
        val requestHandle =
            requester.load(
                adUnitId = adUnitId,
                contentSignal = contentSignal,
                onFill = { nativeAd ->
                    if (!sessionActive) {
                        nativeAd.destroy()
                        return@load
                    }
                    adCarregado?.destroy()
                    adCarregado = nativeAd
                    value = NativeAdLoadState.Fill(nativeAd)
                    telemetry?.registrarResultadoDeLoad(eligibility.slot, outcome = "fill")
                },
                onFailure = { errorCode ->
                    if (!sessionActive) return@load
                    value =
                        if (errorCode == ADMOB_NO_FILL_ERROR_CODE) {
                            NativeAdLoadState.NoFill
                        } else {
                            NativeAdLoadState.RecoverableError(errorCode)
                        }
                    telemetry?.registrarResultadoDeLoad(
                        slot = eligibility.slot,
                        outcome = if (errorCode == ADMOB_NO_FILL_ERROR_CODE) "no_fill" else "error",
                        errorCode = errorCode,
                    )
                },
            )

        try {
            awaitCancellation()
        } finally {
            sessionActive = false
            requestHandle.cancel()
            adCarregado?.destroy()
        }
    }
}

internal fun interface NativeAdRequestHandle {
    fun cancel()
}

internal interface NativeAdRequester {
    fun load(
        adUnitId: String,
        contentSignal: NativeAdContentSignal,
        onFill: (NativeAd) -> Unit,
        onFailure: (Int) -> Unit,
    ): NativeAdRequestHandle
}

private class GoogleNativeAdRequester(
    private val context: Context,
    private val telemetry: AdsTelemetry?,
) : NativeAdRequester {
    override fun load(
        adUnitId: String,
        contentSignal: NativeAdContentSignal,
        onFill: (NativeAd) -> Unit,
        onFailure: (Int) -> Unit,
    ): NativeAdRequestHandle {
        telemetry?.registrarTentativaDeLoad(contentSignal.slot)
        val loader =
            AdLoader
                .Builder(context, adUnitId)
                .forNativeAd(onFill)
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            // Com Throwable, o ReleaseTree registra o erro como nao-fatal no Crashlytics:
                            // sem isso o codigo do AdMob (3 = sem anuncio, 0 = erro interno) so existia
                            // num log local e ninguem via por que o anuncio nao aparecia em producao.
                            Timber.w(
                                NativeAdLoadException(contentSignal.slot.name, adError.code, adError.message),
                                "NativeAd falhou ao carregar: slot=${contentSignal.slot} code=${adError.code}",
                            )
                            onFailure(adError.code)
                        }
                    },
                ).build()
        loader.loadAd(buildAdRequest(contentSignal))
        // O SDK nao oferece cancelamento da request em voo; o handle invalida a sessao Compose,
        // enquanto qualquer fill pertencente a ela e destruido no descarte da composicao.
        return NativeAdRequestHandle {}
    }
}

/** Falha de carregamento de anuncio nativo, com o codigo do AdMob visivel no relatorio. */
internal class NativeAdLoadException(
    slot: String,
    val code: Int,
    detalhe: String,
) : Exception("NativeAd slot=$slot code=$code: $detalhe")

private fun NativeAdLoadState.analyticsOutcome(): String =
    when (this) {
        is NativeAdLoadState.Ineligible ->
            when (reason) {
                NativeAdIneligibleReason.FlagDisabled -> "flag_disabled"
                NativeAdIneligibleReason.BuildDisabled -> "build_disabled"
                NativeAdIneligibleReason.ConsentUnavailable -> "consent_unavailable"
            }
        NativeAdLoadState.Offline -> "offline"
        else -> "not_loadable"
    }

private fun buildAdRequest(signal: NativeAdContentSignal): AdRequest {
    // GH#1717 — `setNeighboringContentUrls` saiu junto com os marcadores de diagnóstico. O único
    // sinal enviado é o tópico da tela; ver o KDoc de `NativeAdContentSignal`.
    val builder = AdRequest.Builder().setContentUrl(signal.contentUrl)
    return builder.build()
}
