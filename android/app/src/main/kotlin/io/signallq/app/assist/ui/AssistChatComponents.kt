package io.signallq.app.assist.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.signallq.app.assist.model.AssistContextoRede
import io.signallq.app.assist.model.AssistMensagem
import io.signallq.app.assist.model.AssistRole
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun CardUltimaMedicao(
    contexto: AssistContextoRede?,
    modifier: Modifier = Modifier,
) {
    if (contexto == null || contexto.semMedicao) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricaItem(
                    label = "Download",
                    valor = contexto.downloadMbps?.let { String.format(Locale.US, "%.0f Mbps", it) } ?: "--",
                )
                MetricaItem(
                    label = "Upload",
                    valor = contexto.uploadMbps?.let { String.format(Locale.US, "%.0f Mbps", it) } ?: "--",
                )
                MetricaItem(
                    label = "Latência",
                    valor = contexto.latenciaMs?.let { "$it ms" } ?: "--",
                )
            }

            if (!contexto.tipoRede.isNullOrBlank() || !contexto.operadoraOuIsp.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOfNotNull(contexto.tipoRede, contexto.operadoraOuIsp).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricaItem(
    label: String,
    valor: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsPerguntasFrequentes(
    perguntas: List<String>,
    onPerguntaClick: (String) -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        perguntas.forEach { pergunta ->
            AssistChip(
                onClick = { onPerguntaClick(pergunta) },
                label = { Text(text = pergunta, style = MaterialTheme.typography.bodySmall) },
                enabled = habilitado,
                shape = RoundedCornerShape(20.dp),
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}

/**
 * Formata texto com marcação simples (**negrito**) para AnnotatedString no Compose.
 */
fun formatarTextoMarkdown(texto: String): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < texto.length) {
            val nextBold = texto.indexOf("**", i)
            if (nextBold == -1) {
                append(texto.substring(i))
                break
            }
            append(texto.substring(i, nextBold))
            val closingBold = texto.indexOf("**", nextBold + 2)
            if (closingBold == -1) {
                append(texto.substring(nextBold))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(texto.substring(nextBold + 2, closingBold))
            }
            i = closingBold + 2
        }
    }

/**
 * Indicador de composição com 3 pontos pulsantes sutis (estilo Qem / Gemini).
 * Não ocupa espaço de card e respeita o peso visual de elemento transitório.
 */
@Composable
fun IndicadorDigitando(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 1000
                        0.25f at 0
                        1f at 250
                        0.25f at 500
                        0.25f at 1000
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "dot1",
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 1000
                        0.25f at 150
                        1f at 400
                        0.25f at 650
                        0.25f at 1000
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "dot2",
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 1000
                        0.25f at 300
                        1f at 550
                        0.25f at 800
                        0.25f at 1000
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "dot3",
    )

    Row(
        modifier =
            modifier
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "Assistente compondo resposta" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dot1Alpha)),
        )
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dot2Alpha)),
        )
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = dot3Alpha)),
        )
    }
}

/**
 * Mensagem do usuário: balão arredondado à direita, discreto e compacto.
 */
@Composable
fun MensagemUsuario(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Resposta do assistente inspirada no padrão Qem:
 * Sem bolha pesada — o texto flui como leitura limpa direto no fundo da tela,
 * com tipografia confortável, rótulo minimalista e ação rápida de copiar.
 */
@Composable
fun RespostaAssistente(
    mensagem: AssistMensagem,
    onTentarNovamente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copiado by remember { mutableStateOf(false) }

    LaunchedEffect(copiado) {
        if (copiado) {
            delay(1500)
            copiado = false
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        // Rótulo discreto do assistente no topo da resposta (estilo Qem)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "SignallQ Assist",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        if (mensagem.erro) {
            Text(
                text = mensagem.texto.ifBlank { "Não foi possível concluir a resposta agora." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onTentarNovamente,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Tentar novamente", style = MaterialTheme.typography.labelSmall)
            }
        } else if (mensagem.emAndamento && mensagem.texto.isBlank()) {
            IndicadorDigitando()
        } else if (mensagem.texto.isNotBlank()) {
            Text(
                text = formatarTextoMarkdown(mensagem.texto),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!mensagem.erro && mensagem.emAndamento && mensagem.texto.isNotBlank()) {
            IndicadorDigitando()
        }

        if (!mensagem.emAndamento && mensagem.texto.isNotBlank() && !mensagem.erro) {
            Spacer(modifier = Modifier.height(6.dp))
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(mensagem.texto))
                    copiado = true
                },
                modifier =
                    Modifier
                        .size(28.dp)
                        .align(Alignment.Start),
            ) {
                Icon(
                    imageVector = if (copiado) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (copiado) "Copiado" else "Copiar resposta",
                    tint =
                        if (copiado) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.6f,
                            )
                        },
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Ponto de entrada de mensagem mantido para compatibilidade.
 */
@Composable
fun BalaoMensagem(
    mensagem: AssistMensagem,
    onTentarNovamente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mensagem.role == AssistRole.USER) {
        MensagemUsuario(texto = mensagem.texto, modifier = modifier)
    } else {
        RespostaAssistente(
            mensagem = mensagem,
            onTentarNovamente = onTentarNovamente,
            modifier = modifier,
        )
    }
}

/**
 * Composer em pílula única (estilo Qem):
 * O campo de texto e o botão de envio vivem no mesmo elemento arredondado,
 * com seta para cima integrada e visual leve sem barras pesadas.
 */
@Composable
fun BarraDigitacaoAssist(
    texto: String,
    onTextoChange: (String) -> Unit,
    onEnviar: () -> Unit,
    onCancelar: () -> Unit,
    estaProcessando: Boolean,
    modifier: Modifier = Modifier,
) {
    val podeEnviar = texto.trim().isNotBlank() && !estaProcessando

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = texto,
                    onValueChange = onTextoChange,
                    placeholder = {
                        Text(
                            text = if (estaProcessando) "Respondendo..." else "Pergunte sobre sua rede...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Campo de mensagem" },
                    maxLines = 4,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = true,
                            keyboardType = KeyboardType.Text,
                            imeAction = if (podeEnviar) ImeAction.Send else ImeAction.Default,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onSend = {
                                if (podeEnviar) {
                                    onEnviar()
                                }
                            },
                        ),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                )

                if (estaProcessando) {
                    IconButton(
                        onClick = onCancelar,
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Parar resposta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = onEnviar,
                        enabled = podeEnviar,
                        modifier =
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (podeEnviar) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    },
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = "Enviar",
                            tint =
                                if (podeEnviar) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
