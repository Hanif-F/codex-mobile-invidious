package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Shared artwork sizing without changing each row's navigation or child actions. */
@Composable
internal fun MediaListLayout(
    compact: Boolean,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    artwork: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = artwork != null && (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f)
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            artwork(Modifier.fillMaxWidth())
            content(Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = verticalAlignment) {
            artwork?.invoke(Modifier.width(if (compact) 144.dp else 160.dp))
            content(Modifier.weight(1f))
        }
    }
}
