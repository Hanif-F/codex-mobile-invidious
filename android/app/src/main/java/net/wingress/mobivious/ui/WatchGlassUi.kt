package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kyant.backdrop.effects.blur

/** A background-only recording, never an ancestor recording the glass that samples it. */
@Composable
internal fun WatchGlassScene(modifier: Modifier = Modifier, artwork: String? = null, inheritAmbient: Boolean = false,
    content: @Composable BoxScope.() -> Unit) {
    val ambient = LocalWatchAmbientMaterial.current.takeIf { inheritAmbient }
    val localScene = rememberGlassScene(samplingEnabled = ambient == null && !inheritAmbient)
    val scene = if (ambient != null) LocalGlassScene.current ?: localScene else localScene
    val backdrop = scene.backdrop
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    val washHeight = if (dark) 280.dp else 380.dp
    var artworkFailed by remember(artwork) { mutableStateOf(false) }
    SideEffect {
        localScene.contentRange = if (inheritAmbient) colors.surface to colors.surface else null
        localScene.generated = if (inheritAmbient) listOf(colors.surface) else null
    }
    Box(modifier) {
        if (ambient == null) {
            Box(Modifier.matchParentSize().clipToBounds().recordGlassScene(scene)
                .background(colors.surface)) {
                if (!inheritAmbient) GlassEnvironment(Modifier.matchParentSize())
                if (!inheritAmbient && artwork != null && !artworkFailed && !LocalReduceTransparency.current) {
                    AsyncImage(artwork, null, Modifier.fillMaxWidth().height(washHeight)
                        .graphicsLayer { alpha = if (dark) .32f else .26f }
                        .then(if (LocalGlassMaterialProfile.current == GlassMaterialProfile.DeferredPlayer) Modifier.blur(64.dp) else Modifier),
                        contentScale = ContentScale.Crop, onError = { artworkFailed = true })
                    Box(Modifier.fillMaxWidth().height(washHeight).background(Brush.verticalGradient(listOf(
                        colors.surface.copy(alpha = .25f), colors.surface))))
                }
            }
        }
        // Keep one content call site when the first ambient palette arrives. Moving the
        // subtree between branches would discard open menus and focused controls.
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassScene provides scene,
            LocalWatchAmbientMaterial provides ambient) { content() }
    }
}

/** Watch and browse share optics; ambient scenes supply generated colors, never video. */
@Composable
internal fun Modifier.watchGlass(shape: Shape = Liquid.card, prominent: Boolean = false): Modifier =
    glassMaterial(shape, prominent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, size: Dp = 48.dp, media: Boolean = false, prominent: Boolean = false, busy: Boolean = false,
    onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberGlassInteraction(interaction)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = if (media) GlassContentColors.light else rememberGlassContentColors(bounds, prominent = prominent)
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { GlassTooltip(description) }, state = rememberTooltipState()) {
        IconButton(onClick, modifier.size(size).onGloballyPositioned { bounds = it.boundsInRoot() }
            .glassMaterial(CircleShape, prominent, if (media) GlassMaterialRole.MediaOverlay else GlassMaterialRole.Control, feedback),
            enabled = enabled, interactionSource = interaction) {
            GlassForeground(foreground) {
            GlassIcon(icon, description, Modifier.size(if (size > 56.dp) 36.dp else 24.dp),
                tint = foreground.primary.copy(alpha = if (enabled) 1f else .38f))
            if (busy) CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Buffering" },
                color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
internal fun WatchDisclosureRow(title: String, icon: ImageVector, modifier: Modifier = Modifier,
    detail: String? = null, expanded: Boolean = false, actionLabel: String = "Open $title", onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberGlassInteraction(interaction)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 1f), label = "watch-disclosure")
    GlassForeground(foreground) {
    Row(modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.glassMaterial(Liquid.card, interaction = feedback)
        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button,
            onClickLabel = actionLabel, onClick = onClick).heightIn(min = 56.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassIcon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlassLabel(title, style = MaterialTheme.typography.titleSmall)
            detail?.takeIf(String::isNotBlank)?.let { GlassLabel(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        GlassIcon(Icons.Default.ExpandMore, null, Modifier.size(20.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
}

@Composable
internal fun WatchPanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    WatchGlassScene(modifier.clip(Liquid.card)) { Column(Modifier.fillMaxSize(), content = content) }
}

/** Only the reading surface is recorded; floating toolbar glass is a sibling of its source. */
@Composable
internal fun WatchCommentsSurface(modifier: Modifier, toolbar: @Composable () -> Unit,
    content: @Composable (Modifier, Dp) -> Unit) {
    CompositionLocalProvider(LocalWatchAmbientMaterial provides null) {
        BrowseGlassSurface(modifier.clip(Liquid.card), softEdge = true, toolbar = {
            Column(Modifier.testTag("comments-toolbar")) { toolbar() }
        }, content = content)
    }
}

/** The toolbar is one glass surface; the controls inside it deliberately have no glass plate. */
@Composable
internal fun WatchPanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    icon: ImageVector, detail: String? = null, backLabel: String = "Back", back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds)
    GlassForeground(foreground) {

    Row(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.padding(horizontal = 12.dp, vertical = 8.dp).watchGlass()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back != null) GlassInsetIconButton(onClick = back, modifier = Modifier.size(48.dp)) { GlassIcon(Icons.AutoMirrored.Filled.ArrowBack, backLabel) }
        else GlassIcon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            GlassLabel(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { GlassLabel(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
        GlassInsetIconButton(onClick = close, modifier = Modifier.size(48.dp)) { GlassIcon(Icons.Default.Close, closeLabel) }
    }

    }
}
