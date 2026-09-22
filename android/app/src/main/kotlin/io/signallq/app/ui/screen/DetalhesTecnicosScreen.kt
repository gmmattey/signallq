package io.signallq.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.signallq.app.core.network.contracts.localdevice.LocalNetworkDeviceSnapshot
import io.signallq.app.feature.speedtest.ResultadoSpeedtest
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.component.LkSectionOverline
import io.signallq.app.ui.component.LocalDeviceSection
import io.signallq.app.ui.component.SignallQButton
import io.signallq.app.ui.component.copyInstabilidadePerdaPacotes
import io.signallq.app.ui.component.mapLocalDeviceSectionUiState

/**
 * "Detalhes técnicos" como tela própria — Feature #550, issue #1475. Antes vivia como
 * accordion dentro da sheet automática "Análise detalhada" (`DiagnosticoDetalhadoSheet`,
 * retirada nesta issue); agora é um dos 3 destinos do resumo pós-teste
 * ([ResultadoVelocidadeScreen]), sem nenhum dado de IA/recomendação — só a leitura
 * crua das métricas do teste, igual ao protótipo #1474
 * (`diagnostico-guiado.jsx` → `DetalhesTecnicosScreen`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalhesTecnicosScreen(
    resultado: ResultadoSpeedtest,
    localizacaoServidor: String?,
    localDevice: LocalNetworkDeviceSnapshot?,
    onVoltar: () -> Unit,
    onGerarLaudo: () -> Unit,
) {
    val c = LocalLkTokens.current
    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Detalhes técnicos", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(c.bgPrimary)
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = LkSpacing.xl, vertical = LkSpacing.lg),
        ) {
            Text(
                text = "Dados medidos",
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(LkSpacing.xs))
            Text(
                text = "Valores brutos para quem precisa investigar ou falar com o suporte.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )

            Spacer(Modifier.height(LkSpacing.xl))
            DetalheRow("Download", "%.1f Mbps".format(resultado.downloadMbps), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Upload", "%.1f Mbps".format(resultado.uploadMbps), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Latência", "%.0f ms".format(resultado.latenciaMs), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Jitter", "%.0f ms".format(resultado.jitterMs), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow(
                label = "Falhas estimadas",
                valor = "%.1f%%".format(resultado.perdaPercentual),
                c = c,
                sublabel = copyInstabilidadePerdaPacotes(resultado.perdaPercentual, resultado.perdaConfianca),
            )
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Servidor", localizacaoServidor ?: "Não informado pelo teste", c)

            Spacer(Modifier.height(LkSpacing.xl))
            SignallQButton(
                label = "Gerar laudo",
                onClick = onGerarLaudo,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(LkSpacing.xl))
            LkSectionOverline(text = "Mais detalhes da conexão")
            Spacer(Modifier.height(LkSpacing.xs))
            Text(
                text = orientacaoPorTipoDeRede(resultado.connectionType, resultado.tecnologia),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(LkSpacing.lg))
            DetalheRow("Resposta com a rede ocupada", "%.0f ms".format(resultado.bufferbloatMs), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Resposta durante download", "%.0f ms".format(resultado.latencyDownloadMs), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Resposta durante upload", "%.0f ms".format(resultado.latencyUploadMs), c)
            // .agents/architecture-plan.md ("Confiabilidade estatística do diagnóstico de
            // rede", passo 6) — visão de detalhe técnico: p95/máximo/amostras/timeouts/janela
            // de confirmação da amostragem de latência, sempre que existirem. Nunca aparece
            // pra dado legado — os 3 campos de fases usam default 0.0/0 só por compatibilidade
            // de construtor, mas todo resultado real de speedtest atual já os calcula.
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Latência (p95)", "%.0f ms".format(resultado.diagnosticoFases.latenciaP95Ms), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow("Latência (máxima)", "%.0f ms".format(resultado.diagnosticoFases.latenciaMaxMs), c)
            HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
            DetalheRow(
                label = "Amostras de latência",
                valor =
                    "${resultado.diagnosticoFases.latenciaAmostrasValidas} válidas de " +
                        "${resultado.diagnosticoFases.latenciaAmostrasTotais}",
                c = c,
                sublabel = "Timeouts: ${resultado.diagnosticoFases.latenciaTimeouts}",
            )
            if (resultado.diagnosticoFases.latenciaConfirmacaoExecutada) {
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                DetalheRow("Amostragem confirmada", "Sim, rodou uma segunda janela de leitura", c)
            }
            if (resultado.dnsLatencyMs != null) {
                HorizontalDivider(color = c.outlineVariant, thickness = 1.dp)
                DetalheRow(
                    label = "Tempo para localizar sites",
                    valor = "${resultado.dnsLatencyMs} ms",
                    c = c,
                    sublabel = formatarIdentificacaoServidorDns(resultado.dnsProvider, resultado.dnsResolverIp),
                )
            }

            Spacer(Modifier.height(LkSpacing.xl))
            LkSectionOverline(text = "Seu equipamento de internet")
            Spacer(Modifier.height(LkSpacing.xs))
            LocalDeviceSection(state = mapLocalDeviceSectionUiState(localDevice))
            Spacer(Modifier.height(LkSpacing.xl))
        }
    }
}

/** Orientação curta por tipo de conexão — não é chat, é texto fixo condicionado
 * ao tipo detectado. Extraída como função pura pra ser testável isoladamente
 * (GH#536), reaproveitada por [ResultadoVelocidadeScreenTest]. */
internal fun orientacaoPorTipoDeRede(
    connectionType: String?,
    tecnologia: String?,
): String =
    when {
        connectionType.equals("wifi", ignoreCase = true) ->
            "Conexão via Wi-Fi. Se o resultado ficou abaixo do esperado, teste perto do roteador " +
                "ou com um cabo de rede para isolar se o problema é do Wi-Fi ou da internet contratada."
        connectionType.equals("movel", ignoreCase = true) -> {
            val tecLabel =
                when {
                    tecnologia?.contains("5G", ignoreCase = true) == true -> "5G"
                    tecnologia?.contains("4G", ignoreCase = true) == true ||
                        tecnologia?.contains("LTE", ignoreCase = true) == true -> "4G"
                    else -> "rede móvel"
                }
            "Conexão via $tecLabel. Sinal fraco e congestionamento da torre variam por local e horário. " +
                "Repita o teste em outro ponto ou horário antes de concluir que há um problema fixo."
        }
        else ->
            "Tipo de conexão não identificado neste teste. Repita o teste conectado por Wi-Fi ou dados " +
                "móveis para ter uma orientação mais precisa."
    }

@Composable
private fun DetalheRow(
    label: String,
    valor: String,
    c: LkTokens,
    sublabel: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = LkSpacing.sm)
                // GH#1502 (revisão independente da PR #1515) -- rótulo, identificação
                // secundária do servidor e valor viram UM anúncio coerente pro TalkBack
                // (ex.: "Tempo para localizar sites, Servidor DNS: Google DNS, 42 ms"),
                // em vez de três leituras desconexas -- mesmo padrão de LocalDeviceSection.
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
            )
            // GH#1502 (revisão independente da PR #1515) -- identificação do servidor DNS
            // (nome ou IP) é informação secundária, nunca parte da frase principal do
            // rótulo -- evita construções como "Tempo para localizar sites (8.8.8.8)".
            if (!sublabel.isNullOrBlank()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                )
            }
        }
        Text(
            text = valor,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
    }
}

/**
 * Identificação secundária e opcional do servidor DNS usado na sondagem (GH#1502, revisão
 * independente da PR #1515 -- corrige "Tempo para localizar sites (8.8.8.8)": o servidor
 * nunca deve parecer parte da frase principal). `internal` para ser testável direto
 * (`DetalhesTecnicosScreenTest`) sem infraestrutura de teste de Compose.
 *
 * - Sem nome nem IP disponível -> `null` (sem linha secundária, sem parênteses vazios).
 * - Só nome conhecido (ex.: "Cloudflare") -> prefixo + nome, sem sufixo.
 * - Só IP (IPv4 ou IPv6, sem tratamento especial de formato -- exibido como veio) ->
 *   prefixo + endereço puro.
 * - Nome e IP disponíveis -> prefixo + nome, com o IP entre parênteses logo em seguida
 *   numa única linha compacta, sem duplicar o prefixo nem o rótulo principal da métrica.
 */
internal fun formatarIdentificacaoServidorDns(
    nome: String?,
    ip: String?,
): String? {
    val nomeLimpo = nome?.trim()?.takeIf { it.isNotBlank() }
    val ipLimpo = ip?.trim()?.takeIf { it.isNotBlank() }
    val identificacao =
        when {
            nomeLimpo != null && ipLimpo != null && ipLimpo != nomeLimpo -> "$nomeLimpo ($ipLimpo)"
            nomeLimpo != null -> nomeLimpo
            ipLimpo != null -> ipLimpo
            else -> return null
        }
    return "Servidor DNS: $identificacao"
}
