package io.signallq.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.signallq.app.ui.LkRadius
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LkTokens

enum class OpcaoCompartilhamento {
    FEED,
    STORY,
    PDF,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompartilharSheet(
    c: LkTokens,
    mostrarOpcaoPdf: Boolean = true,
    onSelecionarOpcao: (OpcaoCompartilhamento) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {},
        containerColor = c.bgSecondary,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LkSpacing.md, bottom = LkSpacing.xxl)
                    .navigationBarsPadding(),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.border)
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "Arrastar para fechar" },
            )
            Spacer(Modifier.height(LkSpacing.md))
            Text(
                text = "Compartilhar resultado",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
            )
            Spacer(Modifier.height(LkSpacing.xs))
            Text(
                text = "Gere uma imagem com o visual oficial para redes sociais ou envie o laudo completo.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
            )
            Spacer(Modifier.height(LkSpacing.lg))

            Column(
                modifier = Modifier.padding(horizontal = LkSpacing.lg),
            ) {
                OpcaoCompartilhamentoItem(
                    c = c,
                    titulo = "Card para Feed e Redes (1200×630)",
                    descricao = "Ideal para WhatsApp, Telegram e Twitter/X",
                    icone = Icons.Outlined.Image,
                    onClick = {
                        onSelecionarOpcao(OpcaoCompartilhamento.FEED)
                        onDismiss()
                    },
                )

                Spacer(Modifier.height(LkSpacing.sm))

                OpcaoCompartilhamentoItem(
                    c = c,
                    titulo = "Card para Stories e Status (9:16)",
                    descricao = "Otimizado para Instagram Stories, TikTok e Status",
                    icone = Icons.Outlined.StayCurrentPortrait,
                    onClick = {
                        onSelecionarOpcao(OpcaoCompartilhamento.STORY)
                        onDismiss()
                    },
                )

                if (mostrarOpcaoPdf) {
                    Spacer(Modifier.height(LkSpacing.sm))

                    OpcaoCompartilhamentoItem(
                        c = c,
                        titulo = "Relatório técnico em PDF",
                        descricao = "Documento completo com diagnóstico para envio à operadora",
                        icone = Icons.Outlined.Description,
                        onClick = {
                            onSelecionarOpcao(OpcaoCompartilhamento.PDF)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OpcaoCompartilhamentoItem(
    c: LkTokens,
    titulo: String,
    descricao: String,
    icone: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LkRadius.card))
                .background(c.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = LkSpacing.md, vertical = LkSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icone,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(LkSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = descricao,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }

        Spacer(Modifier.width(LkSpacing.sm))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}
