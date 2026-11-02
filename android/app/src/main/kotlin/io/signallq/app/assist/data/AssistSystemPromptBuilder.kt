package io.signallq.app.assist.data

import io.signallq.app.assist.model.AssistContextoRede
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AssistSystemPromptBuilder {
    fun build(contexto: AssistContextoRede?): String {
        val basePrompt =
            """
            Você é o Assistente do SignallQ, um especialista em diagnóstico de conectividade e redes domésticas.
            Seu objetivo é ajudar a pessoa a entender o que está acontecendo com a internet dela, explicar o impacto em linguagem clara e indicar ações práticas.

            DIRETRIZES DE COMUNICAÇÃO:
            - Responda em Português do Brasil (PT-BR) de forma amigável, calma e direta.
            - Seja conciso: forneça respostas diretas e bem espaçadas para leitura rápida no celular.
            - Baseie-se rigorosamente nos dados medidos. Se uma métrica estiver ausente, declare que ela não foi medida.
            - Diferencie a rede local (Wi-Fi, sinal do roteador, distância) da rede externa (provedor, fibra, cabo). Só aponte culpados quando os dados isolarem o problema.
            - Nunca invente números de velocidade ou diagnósticos sem evidência.

            EVIDÊNCIA OBSERVADA NO APARELHO:
            """.trimIndent()

        val contextoMedido =
            if (contexto == null || contexto.semMedicao) {
                """
                - Situação: Nenhuma medição de velocidade recente realizada.
                - Instrução: Ao responder dúvidas sobre lentidão ou estabilidade, explique gentilmente que você ainda não possui as medições da conexão atual e convide o usuário a rodar o teste de velocidade do SignallQ para obter números precisos.
                """.trimIndent()
            } else {
                val sb = StringBuilder()
                contexto.medidoEmEpochMs?.let {
                    val quando = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(it))
                    sb.append("- Medição realizada em: $quando (última medição salva; a conexão pode ter mudado desde então)\n")
                }
                contexto.downloadMbps?.let {
                    sb.append(String.format(Locale.US, "- Velocidade de Download: %.1f Mbps\n", it))
                }
                contexto.uploadMbps?.let {
                    sb.append(String.format(Locale.US, "- Velocidade de Upload: %.1f Mbps\n", it))
                }
                contexto.latenciaMs?.let {
                    sb.append("- Latência (ping): $it ms\n")
                }
                contexto.jitterMs?.let {
                    sb.append("- Variação de latência (jitter): $it ms\n")
                }
                contexto.perdaPct?.let {
                    sb.append(String.format(Locale.US, "- Perda de pacotes: %.1f%%\n", it))
                }
                contexto.tipoRede?.let {
                    sb.append("- Tipo de conexão: $it\n")
                }
                contexto.sinalDbm?.let {
                    sb.append("- Sinal Wi-Fi / Móvel: $it dBm\n")
                }
                contexto.operadoraOuIsp?.let {
                    sb.append("- Provedor / Operadora identificada: $it\n")
                }
                contexto.statusRede?.let {
                    sb.append("- Classificação do status: $it\n")
                }
                sb.append("\nInstrução: Use os dados medidos acima como base para responder com exatidão.")
                sb.toString()
            }

        return "$basePrompt\n$contextoMedido"
    }
}
