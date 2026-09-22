package io.signallq.app

import io.signallq.app.core.database.MedicaoEntity
import io.signallq.app.core.diagnostico.IntegridadeMedicaoModoGamer
import io.signallq.app.core.diagnostico.InternetDiagnosticInput
import io.signallq.app.core.diagnostico.MedicaoBaseModoGamer

/** Projeção de Room para a evidência que o Modo gamer pode avaliar. */
internal fun MedicaoEntity.paraMedicaoBaseModoGamer(): MedicaoBaseModoGamer =
    MedicaoBaseModoGamer(
        internet =
            InternetDiagnosticInput(
                downloadMbps = downloadMbps,
                uploadMbps = uploadMbps,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                perdaPercentual = perdaPercentual,
                bufferbloatMs = bufferbloatMs,
                packetLossSource = packetLossSource,
                perdaConfianca = perdaConfianca.paraConfiancaAmostral(),
            ),
        medidoEmEpochMs = timestampEpochMs,
        networkId = networkId,
        integridade =
            when {
                contaminado || status == "contaminated" -> IntegridadeMedicaoModoGamer.CONTAMINADA
                status == "completed" -> IntegridadeMedicaoModoGamer.COMPLETA
                status == "partial" -> IntegridadeMedicaoModoGamer.PARCIAL
                status == "inconclusive" -> IntegridadeMedicaoModoGamer.INCONCLUSIVA
                else -> IntegridadeMedicaoModoGamer.CANCELADA
            },
    )
