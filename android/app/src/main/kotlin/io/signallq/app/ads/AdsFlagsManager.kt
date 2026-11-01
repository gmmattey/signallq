package io.signallq.app.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador de toggle remoto de anuncios nativos (issue #555). Singleton via Hilt,
 * espelha [io.signallq.app.featureflags.FeatureFlagManager] no padrao de inicializacao.
 *
 * [flags] comeca em [AdsFlags.DESLIGADO] e so muda apos o fetch do Remote Config
 * completar -- nenhuma tela deve travar esperando, elas simplesmente nao mostram
 * anuncio ate a primeira leitura chegar (fallback gracioso, sem buraco no layout).
 */
@Singleton
class AdsFlagsManager
    @Inject
    constructor(
        private val repository: AdsRemoteConfigRepository,
        private val telemetry: AdsTelemetry,
    ) {
        private val _flags = MutableStateFlow(AdsFlags.LIGADO)
        val flags: StateFlow<AdsFlags> = _flags

        fun inicializar(scope: CoroutineScope) {
            scope.launch {
                repository.buscarFlags().also { resultado ->
                    _flags.value = resultado.flags
                    telemetry.registrarFlagsRemotas(resultado)
                }
            }
        }
    }
