package io.signallq.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens
import io.signallq.app.ui.LocalLkTokens

/**
 * Reproduz o texto integral de docs_ai/legal/TERMS_OF_USE.md (ultima atualizacao 28/06/2026).
 * Nao parafrasear nem resumir aqui -- qualquer mudanca de conteudo legal comeca no .md e e
 * replicada para este Composable, nunca o contrario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermosDeUsoScreen(onVoltar: () -> Unit) {
    val c = LocalLkTokens.current

    Scaffold(
        containerColor = c.bgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Termos de Uso",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.W600,
                        color = c.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = c.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.bgPrimary),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(LkSpacing.base),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LkSpacing.lg)
                            .padding(top = LkSpacing.md, bottom = LkSpacing.xl),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Termos de uso",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.W600,
                        color = c.textPrimary,
                    )
                    Spacer(Modifier.height(LkSpacing.xs))
                    Text(
                        text = "Última atualização: 28 de junho de 2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textTertiary,
                    )
                }
            }

            item {
                TermosSection(
                    titulo = "1. Aceitação dos Termos",
                    corpo =
                        "Ao instalar e usar o aplicativo SignallQ (\"App\"), você concorda com estes " +
                            "Termos de Uso. Se não concordar, não utilize o App.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "2. Descrição do Serviço",
                    corpo =
                        "O SignallQ é um aplicativo gratuito de diagnóstico de conectividade que:\n" +
                            "• Mede a velocidade da sua conexão de internet (download, upload e latência)\n" +
                            "• Analisa a qualidade do sinal Wi-Fi e móvel\n" +
                            "• Gera diagnósticos com inteligência artificial\n" +
                            "• Mantém histórico local das suas medições",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "3. Uso Permitido",
                    corpo =
                        "Você pode usar o SignallQ para:\n" +
                            "• Diagnosticar problemas na sua conexão de internet\n" +
                            "• Monitorar a qualidade da sua conectividade ao longo do tempo\n" +
                            "• Gerar relatórios para compartilhar com sua operadora ou provedor\n\n" +
                            "Você NÃO pode:\n" +
                            "• Usar o App para atacar, sobrecarregar ou interferir em redes de terceiros\n" +
                            "• Modificar, descompilar ou fazer engenharia reversa do App\n" +
                            "• Usar o App para fins ilegais\n" +
                            "• Redistribuir o App fora dos canais oficiais (Google Play Store)",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "4. Diagnóstico com Inteligência Artificial",
                    corpo =
                        "O diagnóstico gerado por IA é uma ferramenta de apoio e informação. Ele analisa " +
                            "dados técnicos da sua conexão e oferece recomendações com base em padrões conhecidos.\n\n" +
                            "O diagnóstico NÃO constitui:\n" +
                            "• Parecer técnico profissional\n" +
                            "• Garantia de que o problema será resolvido\n" +
                            "• Substituição de suporte técnico especializado\n\n" +
                            "Para problemas persistentes, entre em contato com sua operadora ou provedor de internet.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "5. Gratuidade",
                    corpo =
                        "O SignallQ é oferecido gratuitamente, sem assinaturas e sem compras " +
                            "dentro do app. O app exibe anúncios do Google AdMob. A Buildea reserva-se o direito de introduzir funcionalidades premium " +
                            "no futuro, mas as funcionalidades atuais permanecerão gratuitas.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "6. Disponibilidade",
                    corpo =
                        "O serviço é fornecido \"como está\" (as is). A Buildea não garante:\n" +
                            "• Disponibilidade ininterrupta do App ou dos serviços de diagnóstico IA\n" +
                            "• Precisão absoluta das medições de velocidade\n" +
                            "• Que o diagnóstico resolverá todos os problemas de conectividade\n\n" +
                            "O App depende de serviços de terceiros (Cloudflare, Firebase) que podem sofrer " +
                            "indisponibilidades fora do nosso controle.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "7. Privacidade",
                    corpo =
                        "O tratamento dos seus dados é regido pela nossa Política de Privacidade, disponível " +
                            "em signallq-privacy.gmmattey.workers.dev/privacy e dentro do App.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "8. Propriedade Intelectual",
                    corpo =
                        "O SignallQ, incluindo seu código, design, marca e conteúdo, é propriedade da Buildea. " +
                            "Todos os direitos reservados.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "9. Limitação de Responsabilidade",
                    corpo =
                        "A Buildea não se responsabiliza por:\n" +
                            "• Danos diretos ou indiretos decorrentes do uso do App\n" +
                            "• Decisões tomadas com base nos diagnósticos gerados\n" +
                            "• Perda de dados armazenados localmente no dispositivo\n" +
                            "• Indisponibilidade temporária dos serviços",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "10. Alterações nos Termos",
                    corpo =
                        "A Buildea pode atualizar estes Termos a qualquer momento. Alterações significativas " +
                            "serão comunicadas via atualização do App. O uso continuado após alterações implica " +
                            "aceitação dos novos termos.",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "11. Legislação Aplicável",
                    corpo =
                        "Estes Termos são regidos pelas leis da República Federativa do Brasil, em conformidade " +
                            "com a Lei Geral de Proteção de Dados (LGPD — Lei 13.709/2018) e o Marco Civil da " +
                            "Internet (Lei 12.965/2014).",
                    c = c,
                )
            }
            item {
                TermosSection(
                    titulo = "12. Contato",
                    corpo =
                        "Para dúvidas sobre estes Termos:\n" +
                            "• Email: suporte@signallq.com\n" +
                            "• Desenvolvedor: Buildea",
                    c = c,
                )
            }

            item { Spacer(Modifier.height(LkSpacing.lg)) }

            item {
                Text(
                    text = "Buildea — São Paulo, Brasil",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LkSpacing.lg),
                )
            }

            item {
                Spacer(
                    Modifier
                        .navigationBarsPadding()
                        .height(LkSpacing.xl),
                )
            }
        }
    }
}

@Composable
private fun TermosSection(
    titulo: String,
    corpo: String,
    c: LkTokens,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LkSpacing.lg, vertical = LkSpacing.md),
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.W600,
            color = c.textPrimary,
        )
        Spacer(Modifier.height(LkSpacing.xs))
        Text(
            text = corpo,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
    }
}
