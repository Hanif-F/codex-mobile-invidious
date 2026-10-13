package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import coil.compose.AsyncImage
import com.kyant.backdrop.effects.blur

/** The content recording and the chrome that samples it are siblings, never ancestors. */
@Composable
internal fun BrowseGlassSurface(modifier: Modifier = Modifier, enabled: Boolean = true, softEdge: Boolean = false,
    toolbar: @Composable () -> Unit, content: @Composable (Modifier, Dp) -> Unit) {
    if (!enabled) { content(modifier, 0.dp); return }
    val pageScene = rememberGlassScene()
    val surface = MaterialTheme.colorScheme.surface
    GlassBackgroundScene(modifier) {
        // Sections inherit this background-only source; sibling chrome samples pageScene.
        val backgroundScene = LocalGlassScene.current
        SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
            val chrome = subcompose("toolbar") {
                CompositionLocalProvider(LocalGlassBackdrop provides pageScene.backdrop, LocalGlassScene provides pageScene) {
                    Column(Modifier.fillMaxWidth().testTag("browse-glass-toolbar"), horizontalAlignment = Alignment.CenterHorizontally) {
                        // The title's reading environment masks underlying letterforms, including wrapped text.
                        // Controls still refract the independent page recording.
                        Box(Modifier.fillMaxWidth()) {
                            GlassEnvironment(Modifier.matchParentSize())
                            Column(Modifier.fillMaxWidth()) { toolbar() }
                        }
                        if (softEdge) Box(Modifier.fillMaxWidth().height(12.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
                            }) { GlassEnvironment(Modifier.matchParentSize()) }
                        else Spacer(Modifier.fillMaxWidth().height(12.dp))
                    }
                }
            }.single().measure(constraints.copy(minHeight = 0))
            val body = subcompose("content") {
                Box(Modifier.fillMaxSize().recordGlassScene(pageScene)) {
                    GlassEnvironment(Modifier.matchParentSize())
                    CompositionLocalProvider(LocalGlassBackdrop provides backgroundScene?.backdrop, LocalGlassScene provides backgroundScene) {
                        content(Modifier.fillMaxSize(), chrome.height.toDp() + 8.dp)
                    }
                }
            }.map { it.measure(constraints) }
            layout(constraints.constrainWidth(maxOf(chrome.width, body.maxOfOrNull { it.width } ?: 0)),
                constraints.constrainHeight(body.maxOfOrNull { it.height } ?: chrome.height)) {
                body.forEach { it.placeRelative(0, 0) }
                chrome.placeRelative(0, 0)
            }
        }
    }
}

@Composable
internal fun Modifier.browseGlass(shape: Shape = Liquid.pill, prominent: Boolean = false): Modifier =
    glassMaterial(shape, prominent)

/** A separate background-only source gives the hero's Subscribe control its own safe material. */
@Composable
internal fun BrowseArtworkSurface(artwork: String?, plainBackground: Boolean = false, content: @Composable () -> Unit) {
    if (LocalInsideGlassSection.current && !plainBackground) { content(); return }
    val inheritedBackdrop = LocalGlassBackdrop.current
    val inheritedScene = LocalGlassScene.current
    val scene = rememberGlassScene(samplingEnabled = artwork != null && !plainBackground)
    val fallback = if (inheritedBackdrop == null && !plainBackground)
        rememberGlassScene(environmentOnly = true, samplingEnabled = artwork == null) else null
    val ownBackground = plainBackground || artwork != null
    val backdrop = if (ownBackground) scene.backdrop else inheritedBackdrop ?: fallback?.backdrop
    val surface = MaterialTheme.colorScheme.surface
    var failed by remember(artwork) { mutableStateOf(false) }
    SideEffect {
        scene.contentRange = if (plainBackground) surface to surface else null
        scene.generated = if (plainBackground) listOf(surface) else null
    }
    Box(Modifier.fillMaxWidth().then(if (!ownBackground && fallback != null) Modifier.recordGlassScene(fallback) else Modifier)) {
        // No-artwork controls keep the surrounding environment at their corners.
        if (ownBackground) Box(Modifier.matchParentSize().clipToBounds().recordGlassScene(scene).background(surface)) {
            if (!plainBackground) GlassEnvironment(Modifier.matchParentSize())
            if (!plainBackground && artwork != null && !failed && !LocalReduceTransparency.current) {
                AsyncImage(artwork, null, modifier = Modifier.matchParentSize().graphicsLayer {
                    alpha = if (surface.red < .3f) .32f else .26f
                }.then(if (LocalGlassMaterialProfile.current == GlassMaterialProfile.DeferredPlayer) Modifier.blur(56.dp) else Modifier),
                    contentScale = ContentScale.Crop, imageLoader = AvatarImageLoader.get(androidx.compose.ui.platform.LocalContext.current), onError = { failed = true })
            }
            if (!plainBackground) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
        }
        // Keep one content call site while artwork arrives, fails or is removed.
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop,
            LocalGlassScene provides (if (ownBackground) scene else inheritedScene ?: fallback)) { content() }
    }
}

@Composable
internal fun BrowsePageTitle(title: String, large: Boolean = false, back: (() -> Unit)? = null,
    statusInset: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (statusInset) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = Liquid.inset, vertical = 8.dp)) {
        val stacked = large && (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f)
        val titleContent: @Composable (Modifier) -> Unit = { modifier ->
            GlassLabel(title, modifier.semantics { heading() }, style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titleContent(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            back?.let { GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = it) }
            titleContent(Modifier.weight(1f)); actions()
        }
    }
}

@Composable
internal fun BrowsePanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    detail: String? = null, back: (() -> Unit)? = null, enabled: Boolean = true) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds)
    GlassForeground(foreground) {

    Row(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.padding(horizontal = 12.dp, vertical = 8.dp).browseGlass()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        back?.let { GlassInsetIconButton(onClick = it, enabled = enabled, modifier = Modifier.size(48.dp)) { GlassIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back to SponsorBlock settings") } }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            GlassLabel(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { GlassLabel(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        GlassInsetIconButton(onClick = close, enabled = enabled, modifier = Modifier.size(48.dp)) { GlassIcon(Icons.Default.Close, closeLabel) }
    }

    }
}

@Composable
internal fun <T> BrowseTabs(options: List<T>, selected: T?, label: (T) -> String,
    tag: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds)
    GlassForeground(foreground) {

    val positions = remember { mutableStateMapOf<T, Rect>() }
    val target = positions[selected] ?: Rect.Zero
    val x by animateFloatAsState(target.left, spring(.86f, 420f), label = "tab-selection-position")
    val width by animateFloatAsState(target.width, spring(.86f, 420f), label = "tab-selection-width")
    val selection = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    val indicator = MaterialTheme.colorScheme.primary
    Row(modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.browseGlass()
        .horizontalScroll(rememberScrollState()).padding(4.dp).selectableGroup().drawBehind {
            if (width > 0f) drawLiquidSelection(selection, indicator, Offset(x, 0f), Size(width, size.height))
        }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            val source = remember(option) { MutableInteractionSource() }
            Box(Modifier.onGloballyPositioned {
                val position = it.positionInParent()
                positions[option] = Rect(position, Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }.clip(Liquid.pill).glassItemFeedback(source, active)
                .heightIn(min = 48.dp).selectable(active, interactionSource = source, indication = null,
                    enabled = enabled && available(option), role = Role.Tab, onClick = { select(option) }).testTag(tag(option))
                .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                GlassLabel(label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (!enabled || !available(option)) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        else if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    }
}

@Composable
internal fun <T> BrowseSortMenu(options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier, optionTag: (T) -> String = { "" }, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val foreground = rememberGlassContentColors(bounds)
    GlassForeground(foreground) {

    val source = remember { MutableInteractionSource() }
    val feedback = rememberGlassInteraction(source)
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = modifier.onGloballyPositioned { bounds = it.boundsInRoot() }.heightIn(min = 48.dp).glassMaterial(interaction = feedback), interactionSource = source,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            GlassLabel("Sort: ${label(selected)}", Modifier.weight(1f, fill = false))
            GlassIcon(Icons.Default.ExpandMore, null, Modifier.padding(start = 8.dp))
        }
        GlassDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> GlassMenuItem(text = { GlassLabel(label(option)) }, enabled = available(option),
                leadingIcon = { if (option == selected) GlassIcon(Icons.Default.Check, null) },
                modifier = Modifier.testTag(optionTag(option)).semantics { this.selected = option == selected },
                onClick = { expanded = false; select(option) }) }
        }
    }

    }
}
