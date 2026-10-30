package io.signallq.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * Overlay "WiFi Casa" (`docs/functional/WIFI_CASA_MAPEAMENTO_SPEC.md`, evolução de "Sinal
 * WiFi"/GH#1201) do hub Ferramentas — extraído do corpo de [AppShell] pela issue #1695 (épico
 * #1647), entrada de exemplo do [AppShellOverlayRegistry].
 *
 * `TipoFerramenta.SINAL_WIFI` e o nome técnico do overlay (`AppShellOverlay.SinalWifi`) não
 * mudam (RF-12/`.agents/architecture-plan.md`) -- só o composable renderizado troca de
 * [SinalWifiScreen] (indicador ao vivo ponto único, sem persistência) para [WifiCasaScreen]
 * (mapeamento espacial persistido). [SinalWifiScreen]/`SinalWifiViewModel` continuam existindo,
 * reaproveitados por dentro do fluxo de captura de marcador do WiFi Casa.
 */
@Composable
internal fun AppShellSinalWifiOverlay(
    overlayStack: MutableList<AppShellOverlay>,
    temPermissaoLocalizacao: Boolean,
    localizacaoBloqueadaPermanentemente: Boolean,
    onSolicitarPermissaoLocalizacao: () -> Unit,
) {
    AnimatedVisibility(
        visible = AppShellOverlay.SinalWifi in overlayStack,
        modifier = Modifier.zIndex(rememberOverlayZIndex(AppShellOverlay.SinalWifi, overlayStack)),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        WifiCasaScreen(
            temPermissaoLocalizacao = temPermissaoLocalizacao,
            localizacaoBloqueadaPermanentemente = localizacaoBloqueadaPermanentemente,
            onSolicitarPermissaoLocalizacao = onSolicitarPermissaoLocalizacao,
            onVoltar = { overlayStack.remove(AppShellOverlay.SinalWifi) },
        )
    }
}
