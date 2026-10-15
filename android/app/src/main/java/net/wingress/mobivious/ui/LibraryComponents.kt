package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun LibraryActionButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, prominent: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    val body: @Composable RowScope.() -> Unit = {
        icon?.let { Icon(it,null,Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
    val target=modifier.heightIn(min=48.dp)
    if(prominent) Button(onClick,target,enabled=enabled,content=body)
    else TextButton(onClick,target,enabled=enabled,
        colors=ButtonDefaults.textButtonColors(contentColor=if(destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        content=body)
}

@Composable
internal fun LibraryConfirmation(title: String, dismiss: () -> Unit, confirm: () -> Unit,
    enabled: Boolean = true, confirmLabel: String = "Delete", confirmTag: String = "", destructive: Boolean = true, dismissTag: String = "",
    text: @Composable () -> Unit) {
    AlertDialog(tonalElevation=3.dp,onDismissRequest = { if (enabled) dismiss() }, title = { Text(title) }, text = text,

        confirmButton = { Box {
            LibraryActionButton(confirmLabel, Modifier.testTag(confirmTag), enabled = enabled, prominent = !destructive, destructive = destructive, onClick = confirm)
        } }, dismissButton = { TextButton(dismiss, Modifier.testTag(dismissTag), enabled = enabled) { Text("Cancel") } })
}

@Composable
internal fun LibraryToolbar(title: String, back: (() -> Unit)? = null, statusInset: Boolean = true, actions: @Composable RowScope.() -> Unit = {}, backLabel: String = "Back", backEnabled: Boolean = true, controls: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().then(if (statusInset) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = AppTokens.inset, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Let text grow independently of the fixed-size controls.
        val largeText = LocalDensity.current.fontScale > 1.3f
        if (largeText) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                back?.let { AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, Modifier.testTag("library-toolbar-back"), enabled = backEnabled, onClick = it) }
                Spacer(Modifier.weight(1f)); actions()
            }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            back?.let { AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, Modifier.testTag("library-toolbar-back"), enabled = backEnabled, onClick = it) }
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            actions()
        }
        controls()
    }
}


@Composable
internal fun LibraryPanel(title: String, close: () -> Unit, enabled: Boolean = true,
    closeLabel: String = "Close", actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (Modifier, Dp) -> Unit) {
    Dialog(onDismissRequest = { if (enabled) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val light = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let {
            WindowInsetsControllerCompat(it, view).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        } }
        AppUsablePane(rememberUsableWindowPane()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            val phone = maxWidth < 600.dp
            Surface(Modifier.then(if (phone) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxHeight(.95f)),
                shape = if (phone) androidx.compose.foundation.shape.RoundedCornerShape(0.dp) else AppTokens.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation=0.dp,shadowElevation=3.dp) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                PanelScaffold(Modifier.fillMaxSize(), toolbar = {
                    PanelHeader(title, closeLabel, close, enabled = enabled, actions = actions)
                }, content = content)
                }
            }
        }
    }
    }
}

@Composable
internal fun LibraryEmptyState(title: String, detail: String, icon: ImageVector,
    action: String? = null, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppTokens.inset, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { LibraryActionButton(it, prominent = true, onClick = onClick) }
        }
    }
}
