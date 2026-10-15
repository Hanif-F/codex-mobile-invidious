package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalInsidePanel = staticCompositionLocalOf { false }

/** Panel styling stays local: browse toolbars and ordinary section cards keep their own roles. */
@Composable
internal fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalInsidePanel provides true, LocalInsideAppSection provides true,
        LocalPageInset provides 16.dp, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { testTagsAsResourceId = true }, content = content)
    }
}

@Composable
internal fun PanelScaffold(modifier: Modifier = Modifier, toolbar: @Composable () -> Unit,
    content: @Composable (Modifier, Dp) -> Unit) {
    PanelSurface(modifier) {
        toolbar()
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize(), 0.dp) }
    }
}

@Composable
internal fun PanelHeader(title: String, closeLabel: String, close: () -> Unit,
    detail: String? = null, back: (() -> Unit)? = null, backLabel: String = "Back",
    enabled: Boolean = true, divider: Boolean = true, closeTag: String = "",
    actions: (@Composable RowScope.() -> Unit)? = null) {
    val separateActions = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().testTag("panel-header")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            back?.let { AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, enabled = enabled, onClick = it) }
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                detail?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!separateActions) actions?.invoke(this)
            AppIconButton(Icons.Default.Close, closeLabel, Modifier.testTag(closeTag), enabled = enabled, onClick = close)
        }
        if (separateActions && actions != null) Row(Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        if (divider) PanelDivider()
    }
}

@Composable
internal fun PanelDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** The cap includes the handle and content insets, rather than sizing a second sheet inside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppModalSheet(onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    // Material's dialog handles the IME; only subtract the persistent window chrome here.
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val available = (LocalWindowInfo.current.containerSize.height - safe.getTop(density) - safe.getBottom(density)).coerceAtLeast(0)
    val maxHeight = with(density) { available.toDp() * .85f }
    var handleHeight by remember { mutableStateOf(48.dp) }
    val contentHeight = (maxHeight - handleHeight - with(density) { safe.getBottom(density).toDp() }).coerceAtLeast(0.dp)
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, sheetMaxWidth = 640.dp,
        modifier = Modifier.testTag("modal-panel"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.testTag("modal-panel-handle").onSizeChanged { handleHeight = with(density) { it.height.toDp() } }) { BottomSheetDefaults.DragHandle() }
        }) {
        CompositionLocalProvider(LocalInsidePanel provides true, LocalInsideAppSection provides true, LocalPageInset provides 16.dp) {
            // Keep the sheet's own constraints at window height so Material computes its bottom anchor correctly.
            Column(Modifier.fillMaxWidth().heightIn(max = contentHeight).testTag("modal-panel-body"), content = content)
        }
    }
}
