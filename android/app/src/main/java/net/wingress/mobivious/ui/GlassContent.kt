package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Labels paint only their glyphs; readability belongs to the shared glass material. */
@Composable
internal fun GlassLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null, textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, style: TextStyle = LocalTextStyle.current) {
    Text(text, modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, style = style)
}

@Composable
internal fun GlassIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current) {
    Icon(imageVector, contentDescription, modifier, tint)
}

/** Menu content stays transparent within the popup's shared material. */
@Composable
internal fun GlassMenuItem(text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true, colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = MenuDefaults.DropdownMenuItemContentPadding) {
    DropdownMenuItem(text = text, onClick = onClick, modifier = modifier,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        enabled = enabled, colors = colors, contentPadding = contentPadding)
}

/** Controls inside a shared material keep one optical layer and one semantic interaction source. */
@Composable
internal fun GlassInsetIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    content: @Composable () -> Unit) {
    val source = interactionSource ?: androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    IconButton(onClick, modifier.glassItemFeedback(source), enabled = enabled, interactionSource = source, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TooltipScope.GlassTooltip(label: String) {
    val palette = originatingGlassPalette()
    val source = rememberGlassWindowSource()
    CompositionLocalProvider(LocalGlassEnvironmentPalette provides palette) {
        GlassOverlayScene(source) {
            GlassSurface(shape = MaterialTheme.shapes.small, role = GlassMaterialRole.Section) {
                PlainTooltip(containerColor = Color.Transparent, contentColor = LocalContentColor.current) {
                    GlassLabel(label)
                }
            }
        }
    }
}

/** Transient messages use the same material and preserve Undo/dismiss accessibility actions. */
@Composable
internal fun GlassSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        GlassSurface(Modifier.padding(12.dp).widthIn(max = 640.dp).fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite; dismiss { data.dismiss(); true } },
            shape = Liquid.card, role = GlassMaterialRole.Section) {
            Snackbar(containerColor = Color.Transparent, contentColor = LocalContentColor.current,
                shape = Liquid.card, action = data.visuals.actionLabel?.let { label -> {
                    TextButton(onClick = data::performAction) { GlassLabel(label) }
                } }, dismissAction = if (data.visuals.withDismissAction) ({
                    GlassInsetIconButton(data::dismiss) { GlassIcon(Icons.Default.Close, "Dismiss message") }
                }) else null) { GlassLabel(data.visuals.message) }
        }
    }
}
