package io.signallq.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.signallq.app.ui.LkSpacing
import io.signallq.app.ui.LocalLkTokens

enum class SignallQButtonStyle { Primary, Secondary, Text }

@Composable
fun SignallQButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SignallQButtonStyle = SignallQButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier =
                    Modifier
                        .alpha(if (loading) 0f else 1f)
                        .then(if (loading) Modifier.clearAndSetSemantics {} else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(LkSpacing.lg))
                    Spacer(Modifier.width(LkSpacing.sm))
                }
                Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (loading) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(LkSpacing.lg).clearAndSetSemantics {},
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
    val buttonModifier =
        modifier
            .defaultMinSize(minHeight = LkSpacing.compositionLarge)
            .semantics {
                if (loading) stateDescription = "Carregando"
            }
    when (style) {
        SignallQButtonStyle.Primary ->
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled && !loading,
                content = content,
            )
        SignallQButtonStyle.Secondary ->
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled && !loading,
                content = content,
            )
        SignallQButtonStyle.Text ->
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled && !loading,
                content = content,
            )
    }
}

@Composable
fun SignallQTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val c = LocalLkTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.defaultMinSize(minHeight = LkSpacing.compositionLarge),
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = false,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.primary,
                errorBorderColor = c.error,
            ),
    )
}

@Composable
fun SignallQChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = LocalLkTokens.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .defaultMinSize(minHeight = LkSpacing.compositionLarge),
        enabled = enabled,
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = c.primaryContainer,
                selectedLabelColor = c.onPrimaryContainer,
            ),
    )
}

enum class SignallQBadgeTone { Neutral, Success, Warning, Error, Incerto }

@Composable
fun SignallQBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: SignallQBadgeTone = SignallQBadgeTone.Neutral,
) {
    val c = LocalLkTokens.current
    val colors =
        when (tone) {
            SignallQBadgeTone.Neutral -> c.surfaceContainerHigh to c.onSurfaceVariant
            SignallQBadgeTone.Success -> c.successContainer to c.onSuccessContainer
            SignallQBadgeTone.Warning -> c.warningContainer to c.onWarningContainer
            SignallQBadgeTone.Error -> c.errorContainer to c.onErrorContainer
            SignallQBadgeTone.Incerto -> c.surfaceContainerHigh to c.onSurfaceVariant
        }
    androidx.compose.material3.Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        color = colors.first,
        contentColor = colors.second,
        shape = androidx.compose.foundation.shape.CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LkSpacing.md, vertical = LkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
    }
}
