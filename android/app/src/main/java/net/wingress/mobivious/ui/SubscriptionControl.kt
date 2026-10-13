package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** A state label never doubles as an unexplained destructive action. */
@Composable
internal fun SubscriptionControl(subscribed: Boolean, onChange: () -> Unit, modifier: Modifier = Modifier,
    busy: Boolean = false, known: Boolean = true, checking: Boolean = false, error: String? = null,
    watch: Boolean = false, resetKey: Any? = null) {
    var expanded by remember(resetKey, subscribed) { mutableStateOf(false) }
    val label = when {
        busy -> if (subscribed) "Unsubscribing…" else "Subscribing…"
        !known -> if (checking) "Checking…" else "Check subscription"
        subscribed -> "Subscribed"
        else -> "Subscribe"
    }
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberGlassInteraction(interaction)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds, prominent = !subscribed)
    Column {
        Box {
            TextButton(onClick = { if (subscribed && known) expanded = true else onChange() },
                enabled = !busy && !(checking && !known), interactionSource = interaction,
                colors = ButtonDefaults.textButtonColors(contentColor = foreground.primary),
                modifier = modifier.heightIn(min = 48.dp)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .glassMaterial(prominent = !subscribed, interaction = feedback)
                    .semantics {
                        stateDescription = when { busy -> label; !known -> "Subscription status unavailable"; subscribed -> "Subscribed"; else -> "Not subscribed" }
                    }, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                GlassForeground(foreground) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (subscribed && known && !busy) { GlassIcon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                        GlassLabel(label)
                        if (subscribed && known && !busy) { Spacer(Modifier.width(4.dp)); GlassIcon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) }
                    }
                }
            }
            GlassDropdownMenu(expanded && !busy, { expanded = false }) {
                GlassMenuItem(text = { Text("Unsubscribe") }, modifier = Modifier.testTag("subscription-unsubscribe"),
                    onClick = { expanded = false; onChange() })
            }
        }
        error?.let { Text(it, Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
