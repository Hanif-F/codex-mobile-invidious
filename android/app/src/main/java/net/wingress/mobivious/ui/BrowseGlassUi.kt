package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** The content recording and the chrome that samples it are siblings, never ancestors. */
@Composable
internal fun BrowseGlassSurface(modifier: Modifier = Modifier, enabled: Boolean = true, softEdge: Boolean = false,
    toolbar: @Composable () -> Unit, content: @Composable (Modifier, Dp) -> Unit) {
    if (!enabled) { content(modifier, 0.dp); return }
    val backdrop = rememberLayerBackdrop()
    val surface = MaterialTheme.colorScheme.surface
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // Measure chrome before composing the list so its first frame already has the right inset.
        SubcomposeLayout(modifier.background(surface)) { constraints ->
            val chrome = subcompose("toolbar") {
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    Column(Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(0f to surface.copy(alpha = if (softEdge) .98f else 1f),
                            .75f to surface.copy(alpha = if (softEdge) .94f else .98f), 1f to Color.Transparent))
                        .padding(bottom = 12.dp).testTag("browse-glass-toolbar"), horizontalAlignment = Alignment.CenterHorizontally) { toolbar() }
                }
            }.single().measure(constraints.copy(minHeight = 0))
            val body = subcompose("content") {
                CompositionLocalProvider(LocalGlassBackdrop provides null) {
                    content(Modifier.fillMaxSize().layerBackdrop(backdrop), chrome.height.toDp() + 8.dp)
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
internal fun Modifier.browseGlass(shape: Shape = Liquid.pill, prominent: Boolean = false): Modifier {
    val source = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    val plate = if (prominent) colors.primaryContainer else if (dark) Color(0xFF252C38) else Color.White
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val material = if (opaque || source == null) Modifier.background(plate, shape) else Modifier.drawBackdrop(
        backdrop = source, shape = { shape },
        effects = { vibrancy(); blur(10.dp.toPx()); lens(8.dp.toPx(), 18.dp.toPx()) },
        onDrawSurface = { drawRect(plate.copy(alpha = if (prominent) .64f else if (dark) .58f else .50f)) })
    return shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .12f))
        .then(material)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .40f else .95f),
            Color.White.copy(alpha = .08f), Color.White.copy(alpha = if (dark) .20f else .60f))), shape).clip(shape)
}

/** A separate background-only source gives the hero's Subscribe control its own safe material. */
@Composable
internal fun BrowseArtworkSurface(artwork: String?, content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val surface = MaterialTheme.colorScheme.surface
    var failed by remember(artwork) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.matchParentSize().clipToBounds().layerBackdrop(backdrop).background(surface)) {
            if (artwork != null && !failed && !LocalReduceTransparency.current) {
                AsyncImage(artwork, null, modifier = Modifier.matchParentSize().graphicsLayer { alpha = .18f }.blur(56.dp),
                    contentScale = ContentScale.Crop, imageLoader = AvatarImageLoader.get(androidx.compose.ui.platform.LocalContext.current), onError = { failed = true })
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { content() }
    }
}

@Composable
internal fun BrowsePageTitle(title: String, large: Boolean = false, back: (() -> Unit)? = null,
    statusInset: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).then(if (statusInset) Modifier.statusBarsPadding() else Modifier)
        .padding(horizontal = Liquid.inset, vertical = 8.dp)) {
        val stacked = large && (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f)
        val titleContent: @Composable (Modifier) -> Unit = { modifier ->
            Text(title, modifier.semantics { heading() }, style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                maxLines = if (stacked) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titleContent(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            back?.let { IconButton(onClick = it, modifier = Modifier.size(48.dp).browseGlass(androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            } }
            titleContent(Modifier.weight(1f)); actions()
        }
    }
}

@Composable
internal fun BrowsePanelToolbar(title: String, closeLabel: String, close: () -> Unit,
    detail: String? = null, back: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).browseGlass()
        .heightIn(min = 56.dp).padding(start = if (back == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        back?.let { IconButton(onClick = it, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to SponsorBlock settings") } }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        IconButton(onClick = close, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, closeLabel) }
    }
}

@Composable
internal fun <T> BrowseTabs(options: List<T>, selected: T?, label: (T) -> String,
    tag: (T) -> String, modifier: Modifier = Modifier, enabled: Boolean = true, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    Row(modifier.browseGlass().horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.clip(Liquid.pill).background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                .heightIn(min = 48.dp).selectable(active, enabled = enabled && available(option), role = Role.Tab, onClick = { select(option) }).testTag(tag(option))
                .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (!enabled || !available(option)) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        else if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun <T> BrowseSortMenu(options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier, optionTag: (T) -> String = { "" }, available: (T) -> Boolean = { true }, select: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = modifier.heightIn(min = 48.dp).browseGlass(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Sort: ${label(selected)}", Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 8.dp))
        }
        GlassDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, enabled = available(option),
                leadingIcon = { if (option == selected) Icon(Icons.Default.Check, null) },
                modifier = Modifier.testTag(optionTag(option)).semantics { this.selected = option == selected },
                onClick = { expanded = false; select(option) }) }
        }
    }
}
