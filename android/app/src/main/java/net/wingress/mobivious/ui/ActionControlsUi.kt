package net.wingress.mobivious.ui

import androidx.compose.foundation.clickable
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

/** Popups own a quiet recording source instead of sampling another window. */
@Composable
internal fun GlassDropdownMenu(expanded: Boolean, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, dismiss, shape = Liquid.card, containerColor = Color.Transparent,
        tonalElevation = 0.dp, shadowElevation = 0.dp) {
        LibraryControlScene { Column(Modifier.browseGlass(Liquid.card).padding(vertical = 4.dp), content = content) }
    }
}

/** Reset open menus when their entity/account changes; callers close before dispatching actions. */
@Composable
internal fun OverflowMenu(description: String, resetKey: Any?, modifier: Modifier = Modifier,
    content: @Composable (close: () -> Unit) -> Unit) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(Icons.Default.MoreVert, description)
        }
        GlassDropdownMenu(expanded, { expanded = false }) { content { expanded = false } }
    }
}

@Composable
internal fun ActionRow(title: String, modifier: Modifier = Modifier, detail: String? = null,
    icon: ImageVector? = null, trailingIcon: ImageVector = Icons.Default.ChevronRight,
    enabled: Boolean = true, actionLabel: String = title, trailingRotation: Float = 0f, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    ListItem(headlineContent = { Text(title, color = color) },
        supportingContent = detail?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)) } },
        leadingContent = icon?.let { { Icon(it, null, tint = color) } },
        trailingContent = { Icon(trailingIcon, null, Modifier.rotate(trailingRotation), tint = color) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Liquid.card).heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = actionLabel, onClick = onClick))
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
        GlassDropdownMenu(expanded && enabled, { expanded = false }) {
            choices.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, modifier = Modifier.semantics { this.selected = value == selected },
                    leadingIcon = { if (value == selected) Icon(Icons.Default.Check, null) },
                    onClick = { expanded = false; change(value) })
            }
        }
    }
}
