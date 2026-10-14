package net.wingress.mobivious.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.heading
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
    WatchContent(modifier.clip(AppTokens.card)) { Column(Modifier.fillMaxSize(), content = content) }
}


@Composable
internal fun WatchCommentsSurface(modifier: Modifier, toolbar: @Composable () -> Unit,
    content: @Composable (Modifier, Dp) -> Unit) {
    BrowseAppSurface(modifier.clip(AppTokens.card), toolbar = {
        Column(Modifier.testTag("comments-toolbar")) { toolbar() }
    }, content = content)
}


@Composable
internal fun WatchPanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    icon: ImageVector, detail: String? = null, backLabel: String = "Back", back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}) {
    AppForeground {

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).appControl()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back != null) AppInsetIconButton(onClick = back, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel) }
        else Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
        AppInsetIconButton(onClick = close, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, closeLabel) }
    }

    }
}
