package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** A background-only recording, never an ancestor recording the glass that samples it. */
@Composable
internal fun WatchGlassScene(modifier: Modifier = Modifier, artwork: String? = null,
    content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    var artworkFailed by remember(artwork) { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.matchParentSize().clipToBounds().layerBackdrop(backdrop)
            .background(colors.surface)) {
            Box(Modifier.fillMaxWidth().height(380.dp).background(Brush.verticalGradient(listOf(
                colors.primaryContainer.copy(alpha = if (dark) .32f else .48f), Color.Transparent))))
            if (artwork != null && !artworkFailed && !LocalReduceTransparency.current) {
                AsyncImage(artwork, null, Modifier.fillMaxWidth().height(380.dp)
                    .graphicsLayer { alpha = if (dark) .24f else .18f }.blur(64.dp),
                    contentScale = ContentScale.Crop, onError = { artworkFailed = true })
                Box(Modifier.fillMaxWidth().height(380.dp).background(Brush.verticalGradient(listOf(
                    colors.surface.copy(alpha = .25f), colors.surface))))
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { content() }
    }
}

/** Stronger optical definition than shell chrome, scoped to the watch experience. */
@Composable
internal fun Modifier.watchGlass(shape: Shape = Liquid.card, prominent: Boolean = false): Modifier {
    val source = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val plate = if (prominent) colors.primaryContainer else if (dark) Color(0xFF252C38) else Color.White
    val material = if (opaque || source == null) Modifier.background(plate, shape) else Modifier.drawBackdrop(
        backdrop = source, shape = { shape },
        effects = { vibrancy(); blur(10.dp.toPx()); lens(8.dp.toPx(), 18.dp.toPx()) },
        onDrawSurface = { drawRect(plate.copy(alpha = if (prominent) .64f else if (dark) .52f else .46f)) },
    )
    return shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .12f))
        .then(material)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .10f else .30f),
            Color.White.copy(alpha = .02f), Color.Black.copy(alpha = .025f))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .42f else .95f),
            Color.White.copy(alpha = .08f), Color.White.copy(alpha = if (dark) .20f else .65f))), shape).clip(shape)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, size: Dp = 48.dp, media: Boolean = false, prominent: Boolean = false, busy: Boolean = false,
    onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .93f else 1f, spring(dampingRatio = .78f), label = "watch-press")
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } }, state = rememberTooltipState()) {
        IconButton(onClick, modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (media) Modifier.mediaGlass(CircleShape) else Modifier.watchGlass(CircleShape, prominent)),
            enabled = enabled, interactionSource = interaction) {
            Icon(icon, description, Modifier.size(if (size > 56.dp) 36.dp else 24.dp),
                tint = (if (media) Color.White else if (prominent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else .38f))
            if (busy) CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Buffering" },
                color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun WatchDisclosureRow(title: String, icon: ImageVector, modifier: Modifier = Modifier,
    detail: String? = null, expanded: Boolean = false, actionLabel: String = "Open $title", onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else 1f, spring(dampingRatio = .85f), label = "watch-row-press")
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 1f), label = "watch-disclosure")
    Row(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.watchGlass()
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

@Composable
internal fun WatchPanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    WatchGlassScene(modifier.clip(Liquid.card)) { Column(Modifier.fillMaxSize(), content = content) }
}

/** Only the reading surface is recorded; floating toolbar glass is a sibling of its source. */
@Composable
internal fun WatchCommentsSurface(modifier: Modifier, toolbar: @Composable () -> Unit,
    content: @Composable (Modifier, Dp) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var toolbarHeight by remember { mutableStateOf(136.dp) }
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier.clip(Liquid.card).background(surface)) {
        CompositionLocalProvider(LocalGlassBackdrop provides null, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content(Modifier.fillMaxSize().layerBackdrop(backdrop).background(surface), toolbarHeight + 8.dp)
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxWidth().testTag("comments-toolbar").onSizeChanged { toolbarHeight = with(density) { it.height.toDp() } }
                .padding(bottom = 8.dp)) { toolbar() }
        }
    }
}

/** The toolbar is one glass surface; the controls inside it deliberately have no glass plate. */
@Composable
internal fun WatchPanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    icon: ImageVector, detail: String? = null, backLabel: String = "Back", back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).watchGlass()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel) }
        else Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
        IconButton(onClick = close) { Icon(Icons.Default.Close, closeLabel) }
    }
}
