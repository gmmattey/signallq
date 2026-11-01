package io.signallq.app.ads

import androidx.compose.runtime.Immutable
import io.signallq.app.ui.ads.NativeAdEligibility

/** Mantém separados os gates de build, UMP e Remote Config até o carregador nativo. */
@Immutable
data class NativeAdsGate(
    val buildEnabled: Boolean = false,
    val umpCanRequestAds: Boolean = false,
    val flags: AdsFlags = AdsFlags.LIGADO,
) {
    fun eligibilityFor(slot: AdSlot): NativeAdEligibility =
        NativeAdEligibility(
            slot = slot,
            buildEnabled = buildEnabled,
            flagEnabled = flags.habilitadoPara(slot),
            canRequestAds = umpCanRequestAds,
            // Não inferimos offline: o carregador deixa o SDK informar no-fill ou erro real.
            online = true,
        )
}
