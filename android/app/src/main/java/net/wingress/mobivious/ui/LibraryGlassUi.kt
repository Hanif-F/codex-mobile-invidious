package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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

/** Quiet controls paint their own shaped material without a rectangular backing or recursive sampling. */
@Composable
internal fun LibraryControlScene(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (LocalGlassBackdrop.current != null) Box(modifier, content = content)
    else {
        // Record a generated environment only. The actual container remains visible at corners.
        val scene = rememberGlassScene(environmentOnly = true)
        Box(modifier.recordGlassScene(scene)) {
            CompositionLocalProvider(LocalGlassScene provides scene, LocalGlassBackdrop provides scene.backdrop) { content() }
        }
    }
}

@Composable
internal fun LibraryActionButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, prominent: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberGlassInteraction(interaction)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds, prominent = prominent)
    TextButton(onClick, modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.heightIn(min = 48.dp).glassMaterial(prominent = prominent, interaction = feedback), enabled = enabled,
        interactionSource = interaction, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else foreground.primary)) {
        GlassForeground(foreground) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let { GlassIcon(it, null, Modifier.size(20.dp), tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else .38f)); Spacer(Modifier.width(8.dp)) }
                GlassLabel(label, color = (if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current).copy(alpha = if (enabled) 1f else .38f))
            }
        }
    }
}

@Composable
internal fun LibraryConfirmation(title: String, dismiss: () -> Unit, confirm: () -> Unit,
    enabled: Boolean = true, confirmLabel: String = "Delete", confirmTag: String = "", destructive: Boolean = true, dismissTag: String = "",
    text: @Composable () -> Unit) {
    GlassAlertDialog(onDismissRequest = { if (enabled) dismiss() }, title = { Text(title) }, text = text,

        confirmButton = { LibraryControlScene {
            LibraryActionButton(confirmLabel, Modifier.testTag(confirmTag), enabled = enabled, prominent = !destructive, destructive = destructive, onClick = confirm)
        } }, dismissButton = { TextButton(dismiss, Modifier.testTag(dismissTag), enabled = enabled) { Text("Cancel") } })
}

@Composable
internal fun LibraryToolbar(title: String, large: Boolean = false, back: (() -> Unit)? = null,
    statusInset: Boolean = true, actions: @Composable RowScope.() -> Unit = {},
    backLabel: String = "Back", backEnabled: Boolean = true,
    controls: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().then(if (statusInset) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = Liquid.inset, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Let text grow independently of the fixed-size controls.
        val largeText = LocalDensity.current.fontScale > 1.3f
        if (largeText) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                back?.let { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, Modifier.testTag("library-toolbar-back"), enabled = backEnabled, onClick = it) }
                Spacer(Modifier.weight(1f)); actions()
            }
            Text(title, Modifier.semantics { heading() }, style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge)
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            back?.let { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, Modifier.testTag("library-toolbar-back"), enabled = backEnabled, onClick = it) }
            Text(title, Modifier.weight(1f).semantics { heading() }, style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge)
            actions()
        }
        controls()
    }
}

/** Dialog windows own their recording: they never sample the activity or a decoder surface. */
@Composable
internal fun LibraryPanel(title: String, close: () -> Unit, enabled: Boolean = true,
    closeLabel: String = "Close", actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier, Dp) -> Unit) {
    val palette = originatingGlassPalette()
    Dialog(onDismissRequest = { if (enabled) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val light = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let {
            WindowInsetsControllerCompat(it, view).apply { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
        } }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            val phone = LocalConfiguration.current.smallestScreenWidthDp < 600
            Surface(Modifier.then(if (phone) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxHeight(.95f)),
                shape = if (phone) androidx.compose.foundation.shape.RoundedCornerShape(0.dp) else Liquid.card) {
                CompositionLocalProvider(LocalGlassEnvironmentPalette provides palette) {
                BrowseGlassSurface(Modifier.fillMaxSize(), softEdge = true, toolbar = {
                    LibraryToolbar(title, statusInset = false, actions = {
                        actions()
                        GlassIconButton(Icons.Default.Close, closeLabel, enabled = enabled) { close() }
                    })
                }, content = content)
                }
            }
        }
    }
}

@Composable
internal fun LibraryEmptyState(title: String, detail: String, icon: ImageVector,
    action: String? = null, onClick: () -> Unit = {}) {
    LibraryControlScene(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Liquid.inset, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.let { LibraryActionButton(it, prominent = true, onClick = onClick) }
        }
    }
}
