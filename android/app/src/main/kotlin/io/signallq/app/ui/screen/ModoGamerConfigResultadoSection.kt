package io.signallq.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.signallq.app.ads.AdSlot
import io.signallq.app.ads.AdUnitIds
import io.signallq.app.ads.NativeAdContentSignal
import io.signallq.app.BuildConfig
import io.signallq.app.core.diagnostico.DeviceJogo
import io.signallq.app.core.probejogo.SondaGameLiftBeacon
import io.signallq.app.feature.diagnostico.topology.lan.NatUdpResultado
import io.signallq.app.feature.diagnostico.topology.lan.NatUdpTipo
import io.signallq.app.feature.diagnostico.topology.lan.StunNatProbe
import io.signallq.app.feature.speedtest.AnalisadorAmostragemPing
import io.signallq.app.feature.speedtest.PingExecutor
import io.signallq.app.modogamer.ModoGamerEtapa
import io.signallq.app.modogamer.SelecaoJogoModoGamer
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LocalLkTokens
import io.signallq.app.ui.ads.NativeAdEligibility
import io.signallq.app.ui.ads.NativeAdLoadState
import io.signallq.app.ui.ads.rememberNativeAdState
import io.signallq.app.ui.component.AcoesRecomendadasCard
import io.signallq.app.ui.component.DiagnosticoStatusBanner
import io.signallq.app.ui.component.LkSectionOverline
import io.signallq.app.ui.component.ads.NativeAdCard
import io.signallq.app.ui.component.ads.NativeAdSource
import io.signallq.app.ui.component.corSemantica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date

private const val AMOSTRAS_PING_ESPECIFICO = 24
private const val AMOSTRAS_SONDA_ROTA_REAL = 20
private const val TIMEOUT_NAT_UDP_MS = 3_000L

/** Medição dedicada opcional (issue #1487, "Medir o tempo de resposta agora") — reaproveita
 *  [PingExecutor]/[StunNatProbe], mesmas classes do fluxo legado "Jogos" (GH#935), sem
 *  duplicar a lógica de amostragem. Roda em paralelo, mesmo padrão do antigo
 *  `JogosViewModel.iniciarTeste` — o NAT UDP nunca atrasa além do próprio ping (timeout
 *  próprio de 3s, igual ao legado). */
internal data class MedicaoPingEspecifico(
    val latenciaMs: Double,
    val jitterMs: Double,
    val perdaPercentual: Double,
    val natUdp: NatUdpResultado,
)

/**
 * Architecture Plan "Modo gamer — medição real de rota..." (aprovado por Luiz 2026-09-20):
 * tenta primeiro o eco UDP real contra o beacon regional AWS GameLift ([SondaGameLiftBeacon])
 * antes de cair na estimativa HTTPS antiga ([PingExecutor] contra [probeUrl]). Sem resposta UDP
 * válida nenhuma ([AnalisadorAmostragemPing.analisar] devolve `amostrasValidas == 0`), nunca
 * fabrica sucesso — cai no fallback, exatamente como se a sonda real não existisse. Isso
 * preserva o comportamento e o contrato de [ModoGamerMedindoConteudo]/`confirmarMedicao` sem
 * exigir nenhuma mudança em `ModoGamerScreen.kt`.
 */
private suspend fun medirPingEspecifico(probeUrl: String): MedicaoPingEspecifico =
    coroutineScope {
        val rotaRealDeferred = async(Dispatchers.IO) { medirAmostrasRotaReal() }
        val natDeferred = async(Dispatchers.IO) { StunNatProbe().sondar() }

        val resultadoRotaReal = rotaRealDeferred.await()?.let { AnalisadorAmostragemPing.analisar(it) }
        val nat =
            withTimeoutOrNull(TIMEOUT_NAT_UDP_MS) { natDeferred.await() }
                ?: NatUdpResultado(NatUdpTipo.NAO_VERIFICADO).also { natDeferred.cancel() }

        if (resultadoRotaReal != null && resultadoRotaReal.amostrasValidas > 0) {
            MedicaoPingEspecifico(
                latenciaMs = resultadoRotaReal.latenciaMs,
                jitterMs = resultadoRotaReal.jitterMs,
                perdaPercentual = resultadoRotaReal.perdaPercentual,
                natUdp = nat,
            )
        } else {
            val ping = PingExecutor(targetUrl = probeUrl).executar(count = AMOSTRAS_PING_ESPECIFICO)
            MedicaoPingEspecifico(
                latenciaMs = ping.latenciaMs,
                jitterMs = ping.jitterMs,
                perdaPercentual = ping.perdaPercentual,
                natUdp = nat,
            )
        }
    }

/** `null` quando a sondagem UDP falha por completo (exceção de rede) — [runCatching] aqui é
 *  deliberado: rede indisponível, DNS falho ou UDP bloqueado pela operadora/roteador não podem
 *  derrubar o fluxo, só empurrar para o fallback HTTPS existente. */
private suspend fun medirAmostrasRotaReal(): List<Double?>? =
    runCatching {
        SondaGameLiftBeacon(
            host = BuildConfig.GAMELIFT_BEACON_HOST,
            port = BuildConfig.GAMELIFT_BEACON_PORT,
        ).sondar(AMOSTRAS_SONDA_ROTA_REAL)
    }.getOrNull()

/**
 * Tela de carregamento enquanto o app faz o teste de rede real e atualizado.
 */
@Composable
internal fun ModoGamerMedindoConteudo(
    modifier: Modifier = Modifier,
    selecaoJogo: SelecaoJogoModoGamer,
    device: DeviceJogo,
    probeUrl: String,
    onConcluido: (MedicaoPingEspecifico?) -> Unit,
) {
    val c = LocalLkTokens.current
    var iniciou by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!iniciou) {
            iniciou = true
            val medicao = runCatching { medirPingEspecifico(probeUrl) }.getOrNull()
            onConcluido(medicao)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = LkSpacing.xl, vertical = LkSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), color = c.primary)
        Spacer(Modifier.height(LkSpacing.xl))
        Text(
            text = "Testando a rota do jogo...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.sm))
        Text(
            text = "Aguarde alguns segundos",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}

/**
 * Sem uma base recente para a mesma rede, não há veredito gamer. O botão fica no próprio
 * fluxo para que jogo e aparelho escolhidos não se percam enquanto o teste rápido roda.
 */
@Composable
internal fun ModoGamerAguardandoTesteRapidoConteudo(
    modifier: Modifier = Modifier,
    selecaoJogo: SelecaoJogoModoGamer,
    device: DeviceJogo,
    estadoMedicao: EstadoAnaliseGuiada,
    redeMedida: Boolean,
    onIniciarTeste: () -> Unit,
    onCancelarTeste: () -> Unit,
) {
    val c = LocalLkTokens.current
    val emAndamento = estadoMedicao as? EstadoAnaliseGuiada.EmAndamento

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = LkSpacing.xl, vertical = LkSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = selecaoJogo.nomeExibido, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.W600, color = c.textPrimary)
        Spacer(Modifier.height(LkSpacing.xs))
        Text(text = device.label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(LkSpacing.xl))

        if (emAndamento != null) {
            CircularProgressIndicator(color = c.primary)
            Spacer(Modifier.height(LkSpacing.lg))
            Text(text = emAndamento.etapa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W600, color = c.textPrimary)
            Spacer(Modifier.height(LkSpacing.xs))
            Text(text = "Vamos medir sua conexão antes de avaliar o jogo.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            TextButton(onClick = onCancelarTeste, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Cancelar", style = MaterialTheme.typography.bodyMedium, color = c.primary)
            }
        } else {
            Text(text = "Vamos conferir como sua rede está agora", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.W600, color = c.textPrimary)
            Spacer(Modifier.height(LkSpacing.sm))
            Text(
                text = "Assim verificamos se ela está pronta para jogar neste momento.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            if (redeMedida) {
                Spacer(Modifier.height(LkSpacing.xs))
                Text(
                    text = "O teste pode usar dados móveis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                )
            }
            if (estadoMedicao is EstadoAnaliseGuiada.Falhou) {
                Spacer(Modifier.height(LkSpacing.sm))
                Text(text = estadoMedicao.mensagem, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
            Spacer(Modifier.height(LkSpacing.xl))
            OutlinedButton(onClick = onIniciarTeste, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(LkRadius.button)) {
                Text(text = "Fazer um teste rápido para jogar", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
        }
    }
}

/**
 * GH#1785 — mesmo mapeamento de [NativeAdEligibility] usado em `eligibilidadeAnuncioResultado`
 * (ResultadoVelocidadeScreen.kt): a tela só recebe o flag `adsEnabled` de fora, sem sinal de
 * consentimento UMP nem de conectividade separados (mesma limitação que `rememberNativeAd()`,
 * o wrapper antigo, já tinha).
 */
internal fun eligibilidadeAnuncioModoGamer(adsGate: io.signallq.app.ads.NativeAdsGate): NativeAdEligibility =
    adsGate.eligibilityFor(AdSlot.JOGOS)

/** Compatibilidade para testes legados; o fluxo de produção usa [NativeAdsGate]. */
internal fun eligibilidadeAnuncioModoGamer(adsEnabled: Boolean): NativeAdEligibility =
    NativeAdEligibility(AdSlot.JOGOS, buildEnabled = true, flagEnabled = adsEnabled, canRequestAds = adsEnabled, online = true)

/**
 * Resultado do Modo gamer: o veredito e as métricas vêm exclusivamente do motor determinístico.
 * Não solicita nem exibe uma explicação por IA nesse fluxo.
 */
@Composable
internal fun ModoGamerResultadoConteudo(
    modifier: Modifier = Modifier,
    etapa: ModoGamerEtapa.Resultado,
    timestampMedicaoBaseEpochMs: Long?,
    onAlternarSalvarPadrao: (Boolean) -> Unit,
    onTrocarJogoOuDevice: () -> Unit,
    onIrParaHome: () -> Unit,
    /** Toggle remoto (Firebase Remote Config) + gate de consentimento UMP -- issue #555,
     *  reconectado do fluxo legado "Jogos" (GH#935) pela issue #1489. Default `false`: nunca
     *  mostra anuncio sem sinal explicito de que pode. */
    adsGate: io.signallq.app.ads.NativeAdsGate =
        io.signallq.app.ads
            .NativeAdsGate(),
) {
    val c = LocalLkTokens.current
    val resultado = etapa.resultado
    val fallback = etapa.selecaoJogo is SelecaoJogoModoGamer.ForaDoCatalogo

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(c.bgPrimary)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LkSpacing.xl, vertical = LkSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Outlined.SportsEsports, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(LkSpacing.sm))
            Column {
                Text(text = etapa.selecaoJogo.nomeExibido, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W600, color = c.textPrimary)
                Text(text = etapa.device.label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        if (fallback) {
            Spacer(Modifier.height(LkSpacing.sm))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LkRadius.input))
                        .background(c.warningContainer)
                        .padding(LkSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = c.onWarningContainer, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(LkSpacing.sm))
                Text(
                    text = "Este jogo não está no catálogo. Estou usando o perfil de referência de ${etapa.selecaoJogo.categoria.label.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onWarningContainer,
                )
            }
        }

        Spacer(Modifier.height(LkSpacing.lg))
        // Issue #1667 — headline direta e simples (decisão do Luiz, 2026-08-19: "bom pra
        // jogar" / "não recomendado" em vez de fraseado de probabilidade), acima do banner
        // detalhado que já existia. Nunca substitui as evidências reais abaixo.
        Text(
            text = resultado.veredito,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W700,
            color = resultado.status.corSemantica(c),
        )
        Spacer(Modifier.height(LkSpacing.sm))
        DiagnosticoStatusBanner(status = resultado.status, mensagem = resultado.mensagemMotor, c = c)

        Spacer(Modifier.height(LkSpacing.lg))
        LkSectionOverline(text = "Dados deste teste")
        Spacer(Modifier.height(LkSpacing.sm))
        ModoGamerMetricasResumo(
            evidencias = resultado.evidencias,
            timestampMedicaoBaseEpochMs = timestampMedicaoBaseEpochMs,
        )

        if (resultado.acoes.isNotEmpty()) {
            Spacer(Modifier.height(LkSpacing.lg))
            LkSectionOverline(text = "O que fazer agora")
            Spacer(Modifier.height(LkSpacing.sm))
            AcoesRecomendadasCard(acoes = resultado.acoes, c = c)
        }

        // Issue #1487 — resultado do StunNatProbe reaproveitado do legado (GH#935/#1200),
        // só quando o usuário pediu a medição dedicada. Puramente informativo, mesmo
        // princípio "NAT nunca rebaixa o veredito sozinho" do legado — nunca aparece como
        // Dimensao/EvidenciaDiagnostico do motor.
        if (etapa.natUdp != null) {
            Spacer(Modifier.height(LkSpacing.sm))
            ModoGamerNatUdpRow(natUdp = etapa.natUdp)
        }

        Spacer(Modifier.height(LkSpacing.lg))
        val nativeAdState by rememberNativeAdState(
            adUnitId = AdUnitIds.para(AdSlot.JOGOS),
            contentSignal = NativeAdContentSignal.forSlot(AdSlot.JOGOS),
            eligibility = eligibilidadeAnuncioModoGamer(adsGate),
        )
        val nativeAd = (nativeAdState as? NativeAdLoadState.Fill)?.ad
        NativeAdCard(nativeAd = nativeAd, source = NativeAdSource.ADMOB)

        Spacer(Modifier.height(LkSpacing.lg))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LkRadius.input))
                    .background(c.bgSecondary)
                    .clickable { onAlternarSalvarPadrao(!etapa.salvoComoPadrao) }
                    .padding(LkSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (etapa.salvoComoPadrao) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = if (etapa.salvoComoPadrao) c.primary else c.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(LkSpacing.sm))
            Text(
                text =
                    if (etapa.salvoComoPadrao) {
                        "Salvo como padrão do Modo gamer (${etapa.selecaoJogo.nomeExibido} + ${etapa.device.label})"
                    } else {
                        "Toque para salvar como padrão"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }

        Spacer(Modifier.height(LkSpacing.lg))
        OutlinedButton(
            onClick = onTrocarJogoOuDevice,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(LkRadius.button),
        ) {
            Text(text = "Escolher outro jogo ou aparelho", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(LkSpacing.sm))
        TextButton(onClick = onIrParaHome, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Voltar ao início", style = MaterialTheme.typography.bodyMedium, color = c.primary)
        }
        Spacer(Modifier.height(LkSpacing.xl))
    }
}

@Composable
private fun ModoGamerMetricasResumo(
    evidencias: List<io.signallq.app.core.diagnostico.EvidenciaDiagnostico>,
    timestampMedicaoBaseEpochMs: Long?,
) {
    val c = LocalLkTokens.current
    val metricas =
        evidencias
            .filterNot { it.label == "Aparelho analisado" }
            .sortedByDescending { it.label == "Tempo de resposta medido agora" }
            .take(3)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LkRadius.card))
                .background(c.surfaceContainer)
                .padding(LkSpacing.lg),
    ) {
        metricas.forEach { evidencia ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = evidencia.label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(text = evidencia.valorExibido, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700, color = evidencia.status.corSemantica(c))
            }
        }
        if (timestampMedicaoBaseEpochMs != null) {
            Spacer(Modifier.height(LkSpacing.xs))
            Text(
                text = "Baseado no teste de ${formatarHorarioModoGamer(timestampMedicaoBaseEpochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
            )
        }
    }
}

@Composable
private fun ModoGamerNatUdpRow(natUdp: NatUdpResultado) {
    val c = LocalLkTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LkRadius.input))
                .background(c.bgSecondary)
                .padding(LkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(LkSpacing.sm))
        Text(
            text = "Conexão direta com outros jogadores: ${natUdp.tipo.rotulo()}",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
    }
}

/** Rótulo humano do NAT UDP (issue #1487) — versão compacta do texto do legado
 *  (`JogosScreen.textoNatUdp`), sem o card colorido dedicado (fora do escopo desta fusão). */
private fun NatUdpTipo.rotulo(): String =
    when (this) {
        NatUdpTipo.ABERTO -> "Boa"
        NatUdpTipo.MODERADO -> "Pode apresentar limitações"
        NatUdpTipo.RESTRITO -> "Limitada"
        NatUdpTipo.BLOQUEADO -> "Bloqueada"
        NatUdpTipo.NAO_VERIFICADO -> "Não foi possível verificar"
    }

internal fun formatarHorarioModoGamer(timestampEpochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampEpochMs))
