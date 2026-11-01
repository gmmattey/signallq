package io.signallq.app.assist.data

import io.signallq.app.assist.model.AssistContextoRede
import io.signallq.app.core.diagnostico.ConnectionType
import io.signallq.app.core.diagnostico.DiagnosticInput
import io.signallq.app.feature.speedtest.MeasurementStatus
import io.signallq.app.feature.speedtest.ResultadoSpeedtest

object AssistContextoMapper {
    fun map(
        resultado: ResultadoSpeedtest?,
        input: DiagnosticInput?,
        ispNome: String?,
        operadoraMovel: String?,
    ): AssistContextoRede {
        if (resultado == null ||
            resultado.status == MeasurementStatus.CANCELLED ||
            resultado.status == MeasurementStatus.CONTAMINATED
        ) {
            val tipo =
                when {
                    input?.wifi != null || input?.connectionType == ConnectionType.wifi -> "Wi-Fi"
                    input?.mobile != null || input?.connectionType == ConnectionType.mobile -> "Rede Móvel"
                    input?.connectionType == ConnectionType.ethernet -> "Ethernet"
                    else -> null
                }
            return AssistContextoRede(
                tipoRede = tipo,
                operadoraOuIsp = ispNome ?: operadoraMovel,
                semMedicao = true,
            )
        }

        val tipoRedeFormatado =
            buildString {
                when {
                    resultado.connectionType?.contains("wifi", ignoreCase = true) == true || input?.wifi != null -> {
                        append("Wi-Fi")
                        val freq = input?.wifi?.frequenciaMhz
                        if (freq != null) {
                            if (freq >= 4900) append(" (5 GHz)") else append(" (2.4 GHz)")
                        }
                    }
                    resultado.connectionType?.contains("cell", ignoreCase = true) == true || input?.mobile != null -> {
                        append("Rede Móvel")
                        resultado.tecnologia?.let { append(" ($it)") }
                    }
                    else -> append(resultado.connectionType ?: "Conexão de Rede")
                }
            }

        val statusQualidade =
            "Streaming: ${resultado.diagnosticoQualidade.vereditoStreaming.name}, Jogos: ${resultado.diagnosticoQualidade.vereditoGamer.name}"

        return AssistContextoRede(
            downloadMbps = resultado.downloadMbps,
            uploadMbps = resultado.uploadMbps,
            latenciaMs = resultado.latenciaMs.toLong(),
            jitterMs = resultado.jitterMs.toLong(),
            perdaPct = resultado.perdaPercentual,
            tipoRede = tipoRedeFormatado,
            sinalDbm = input?.wifi?.rssiDbm ?: input?.mobile?.rsrpDbm,
            operadoraOuIsp = ispNome ?: operadoraMovel,
            statusRede = statusQualidade,
            semMedicao = false,
        )
    }
}
