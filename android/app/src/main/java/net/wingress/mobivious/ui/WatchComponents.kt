package net.wingress.mobivious.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
internal fun WatchContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(modifier,color=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface) { Box(content=content) }
}

@Composable
internal fun WatchDisclosureRow(title: String, icon: ImageVector, modifier: Modifier = Modifier,
    detail: String? = null, expanded: Boolean = false, actionLabel: String = "Open $title", onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    val rotation = if (expanded) 180f else 0f
    AppForeground {
    Row(modifier.fillMaxWidth().appControl(AppTokens.card)
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button,
            onClickLabel = actionLabel, onClick = onClick).heightIn(min = 56.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            detail?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
}

@Composable
internal fun WatchPanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PanelSurface(if (LocalInsidePanel.current) modifier else modifier.clip(AppTokens.card), content)
}


@Composable
internal fun WatchCommentsSurface(modifier: Modifier, toolbar: @Composable () -> Unit,
    content: @Composable (Modifier, Dp) -> Unit) {
    PanelScaffold(if (LocalInsidePanel.current) modifier else modifier.clip(AppTokens.card), toolbar = {
        Column(Modifier.testTag("comments-toolbar")) { toolbar() }
    }, content = content)
}


@Composable
internal fun WatchPanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    detail: String? = null, backLabel: String = "Back", back: (() -> Unit)? = null, divider: Boolean = true) {
    PanelHeader(title, closeLabel, close, detail, back, backLabel, divider = divider)
}
