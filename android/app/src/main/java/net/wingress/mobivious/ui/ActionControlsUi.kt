package net.wingress.mobivious.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color


@Composable
internal fun AppDropdownMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, dismiss, shape=MaterialTheme.shapes.medium,
        containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation=0.dp,shadowElevation=3.dp,content=content)
}

/** Reset open menus when their entity/account changes; callers close before dispatching actions. */
@Composable
internal fun OverflowMenu(description: String, resetKey: Any?, modifier: Modifier = Modifier,
    content: @Composable (close: () -> Unit) -> Unit) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    Box {
        AppIconButton(Icons.Default.MoreVert, description, modifier) { expanded = true }
        AppDropdownMenu(expanded, { expanded = false }) { content { expanded = false } }
    }
}

@Composable
internal fun ActionRow(title: String, modifier: Modifier = Modifier, detail: String? = null,
    icon: ImageVector? = null, trailingIcon: ImageVector = Icons.Default.ChevronRight,
    enabled: Boolean = true, actionLabel: String = title, trailingRotation: Float = 0f, onClick: () -> Unit) {
    val inside = LocalInsideAppSection.current
    val interaction = remember { MutableInteractionSource() }
    val row: @Composable () -> Unit = {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    ListItem(headlineContent = { Text(title, color = color) },
        supportingContent = detail?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)) } },
        leadingContent = icon?.let { { Icon(it, null, tint = color) } },
        trailingContent = { Icon(trailingIcon, null, Modifier.rotate(trailingRotation), tint = color) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().clip(AppTokens.shapes.small).heightIn(min = 64.dp)
            .appItemFeedback(interaction).clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = actionLabel, onClick = onClick))
    }
    if (inside) {
        row()
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = .10f))
    } else AppSurface(Modifier.fillMaxWidth().padding(vertical = 3.dp), AppTokens.card, role = AppSurfaceRole.SECTION) { row() }
}

/** A field-like row keeps the setting label and its current value visible. */
@Composable
internal fun DropdownChoiceRow(label: String, choices: List<Pair<String, String>>, selected: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, change: (String) -> Unit) {
    var expanded by remember(label, choices, selected) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box {
        ActionRow(label, modifier = modifier, detail = choices.firstOrNull { it.first == selected }?.second ?: selected,
            trailingIcon = Icons.Default.ArrowDropDown, enabled = enabled, actionLabel = "Choose $label") { expanded = true }
        AppDropdownMenu(expanded && enabled, { expanded = false }) {
            choices.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, modifier = Modifier.semantics { this.selected = value == selected },
                    leadingIcon = { if (value == selected) Icon(Icons.Default.Check, null) },
                    onClick = { expanded = false; change(value) })
            }
        }
    }
}
