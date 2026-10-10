package net.wingress.mobivious.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class ShellDestination(val label: String, val icon: ImageVector) {
    DISCOVER("Discover", Icons.Outlined.Explore),
    SUBSCRIPTIONS("Subscriptions", Icons.Outlined.Subscriptions),
    YOU("You", Icons.Outlined.AccountCircle),
    SEARCH("Search", Icons.Outlined.Search);
    companion object {
        fun from(tab: String) = when (tab) {
            "Subscriptions" -> SUBSCRIPTIONS; "You" -> YOU; "Search" -> SEARCH; else -> DISCOVER
        }
    }
}

@Composable
internal fun FloatingNavigation(selected: ShellDestination, compact: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, select: (ShellDestination) -> Unit) {
    val labels = !compact && LocalDensity.current.fontScale <= 1.3f
    val activePosition by animateFloatAsState(selected.ordinal.coerceAtMost(2).toFloat(),
        spring(dampingRatio = .86f, stiffness = 420f), label = "navigation-selection")
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    val indicator = MaterialTheme.colorScheme.primary.takeIf { MaterialTheme.colorScheme.background.red < .3f }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).liquidGlass().padding(5.dp).drawBehind {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * 2) / 3
            drawLiquidSelection(selectionColor, indicator, Offset(activePosition * (cell + gap), 0f), Size(cell, size.height))
        }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ShellDestination.entries.filter { it != ShellDestination.SEARCH }.forEach { destination ->
                val active = destination == selected
                val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).heightIn(min = 52.dp).clip(Liquid.pill)
                    .selectable(active, enabled = enabled, role = Role.Tab, onClick = { select(destination) })
                    .testTag("navigation-${destination.label}").semantics { contentDescription = destination.label }
                    .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(destination.icon, null, Modifier.size(24.dp), tint = color)
                    AnimatedVisibility(labels) {
                        Text(destination.label,
                            style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
        GlassIconButton(Icons.Outlined.Search, "Search", Modifier.size(58.dp).testTag("global-search"), enabled,
            prominent = selected == ShellDestination.SEARCH) { select(ShellDestination.SEARCH) }
    }
}

@Composable
internal fun FloatingSidebar(selected: ShellDestination, enabled: Boolean, modifier: Modifier = Modifier,
    select: (ShellDestination) -> Unit) {
    Column(modifier.width(204.dp).padding(16.dp).liquidGlass(Liquid.card).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Mobivious", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
        ShellDestination.entries.forEach { destination ->
            val active = destination == selected
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Liquid.pill).liquidSelection(active)
                .selectable(active, enabled = enabled, role = Role.Tab, onClick = { select(destination) })
                .testTag(if (destination == ShellDestination.SEARCH) "global-search" else "navigation-${destination.label}")
                .semantics { contentDescription = destination.label }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(destination.icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(destination.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
