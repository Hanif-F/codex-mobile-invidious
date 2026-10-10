package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.exposureAdjustment
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal object Liquid {
    val inset = 20.dp
    val card = RoundedCornerShape(24.dp)
    val media = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
    val light = lightColorScheme(
        primary = Color(0xFF1765D8), onPrimary = Color.White,
        primaryContainer = Color(0xFFE4EEFF), onPrimaryContainer = Color(0xFF124A9C),
        secondary = Color(0xFF586275), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9EDF4), onSecondaryContainer = Color(0xFF283447),
        tertiary = Color(0xFF526582), background = Color(0xFFF5F6F9),
        surface = Color(0xFFF5F6F9), surfaceContainer = Color.White,
        surfaceContainerLow = Color(0xFFF0F2F6), surfaceContainerHigh = Color(0xFFE9EDF3),
        surfaceContainerHighest = Color(0xFFE2E7EF), onSurface = Color(0xFF171A21),
        onSurfaceVariant = Color(0xFF555E6D), outline = Color(0xFF7C8798),
        outlineVariant = Color(0xFFDCE1E9), surfaceTint = Color.Transparent,
    )
    val dark = darkColorScheme(
        primary = Color(0xFFE7E9ED), onPrimary = Color(0xFF202225),
        primaryContainer = Color(0xFF34363A), onPrimaryContainer = Color(0xFFF0F1F3),
        inversePrimary = Color(0xFF53565C),
        secondary = Color(0xFFC6C8CD), onSecondary = Color(0xFF24262A),
        secondaryContainer = Color(0xFF2B2D31), onSecondaryContainer = Color(0xFFE5E7EB),
        tertiary = Color(0xFFD0D2D6), onTertiary = Color(0xFF282A2E),
        tertiaryContainer = Color(0xFF303236), onTertiaryContainer = Color(0xFFE8EAED),
        background = Color(0xFF111214), onBackground = Color(0xFFEEF0F3),
        surface = Color(0xFF111214), onSurface = Color(0xFFEEF0F3),
        surfaceDim = Color(0xFF111214), surfaceBright = Color(0xFF383A3E),
        surfaceContainerLowest = Color(0xFF0D0E10), surfaceContainerLow = Color(0xFF17191C),
        surfaceContainer = Color(0xFF1C1E21), surfaceContainerHigh = Color(0xFF282A2E),
        surfaceContainerHighest = Color(0xFF34363A), surfaceVariant = Color(0xFF34363A),
        onSurfaceVariant = Color(0xFFC3C5CA), outline = Color(0xFF909399),
        outlineVariant = Color(0xFF393C41), surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFE5E7EB), inverseOnSurface = Color(0xFF292B2F),
        primaryFixed = Color(0xFFE5E7EB), primaryFixedDim = Color(0xFFC5C7CD),
        onPrimaryFixed = Color(0xFF202225), onPrimaryFixedVariant = Color(0xFF44474D),
        secondaryFixed = Color(0xFFE0E2E6), secondaryFixedDim = Color(0xFFC2C4C9),
        onSecondaryFixed = Color(0xFF24262A), onSecondaryFixedVariant = Color(0xFF44474D),
        tertiaryFixed = Color(0xFFE4E5E6), tertiaryFixedDim = Color(0xFFC7C8CA),
        onTertiaryFixed = Color(0xFF282A2E), onTertiaryFixedVariant = Color(0xFF44474D),
    )
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.3).sp),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    )
    val shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp), large = card, extraLarge = RoundedCornerShape(32.dp))
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalReduceTransparency = staticCompositionLocalOf { false }
internal val LocalContentBottomInset = staticCompositionLocalOf { 20.dp }

/** One optical treatment for dark navigation, browse and watch chrome. */
internal object DarkGlass {
    val plate = Color(0xFF292B2E)
    const val tintAlpha = .58f
    const val prominentTintAlpha = .62f
    const val saturation = .78f
    // Android color filters operate in the renderer's working color space; validate the composed result on-device.
    const val exposure = -2.8f
    val reflection = Brush.verticalGradient(listOf(Color.White.copy(alpha = .035f),
        Color.White.copy(alpha = .008f), Color.Transparent))
    val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = .20f),
        Color.White.copy(alpha = .035f), Color.White.copy(alpha = .10f)))
}

@Composable
internal fun Modifier.darkLiquidGlass(shape: Shape, prominent: Boolean): Modifier {
    val source = LocalGlassBackdrop.current
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val plate = if (prominent) MaterialTheme.colorScheme.primaryContainer else DarkGlass.plate
    val material = if (opaque || source == null) Modifier.background(plate, shape) else Modifier.drawBackdrop(
        backdrop = source, shape = { shape },
        effects = {
            blur(10.dp.toPx()); lens(8.dp.toPx(), 18.dp.toPx())
            // Bound bright artwork inside the material, without dimming the actual content or its labels.
            colorControls(saturation = DarkGlass.saturation); exposureAdjustment(DarkGlass.exposure)
        },
        onDrawSurface = { drawRect(plate.copy(alpha = if (prominent) DarkGlass.prominentTintAlpha else DarkGlass.tintAlpha)) },
    )
    return shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .20f))
        .then(material)
        .then(if (opaque) Modifier else Modifier.background(DarkGlass.reflection, shape))
        .border(.75.dp, DarkGlass.rim, shape).clip(shape)
}

/** The small marker makes neutral selection recognizable independently of the fill's brightness. */
internal fun DrawScope.drawLiquidSelection(fill: Color, indicator: Color?, topLeft: Offset = Offset.Zero,
    selectionSize: Size = size) {
    drawRoundRect(fill, topLeft, selectionSize, CornerRadius(selectionSize.height / 2))
    if (indicator != null) {
        val width = minOf(20.dp.toPx(), selectionSize.width / 3)
        val height = 2.dp.toPx()
        drawRoundRect(indicator, Offset(topLeft.x + (selectionSize.width - width) / 2,
            topLeft.y + selectionSize.height - 5.dp.toPx()), Size(width, height), CornerRadius(height))
    }
}

@Composable
internal fun Modifier.liquidSelection(active: Boolean): Modifier {
    if (!active) return this
    val colors = MaterialTheme.colorScheme
    val fill = colors.primary.copy(alpha = .12f)
    val indicator = colors.primary.takeIf { colors.background.red < .3f }
    return drawBehind { drawLiquidSelection(fill, indicator) }
}

/** Only content is recorded by the shell. Glass must never record itself into its own source. */
@Composable
internal fun Modifier.liquidGlass(shape: Shape = Liquid.pill, prominent: Boolean = false): Modifier {
    val source = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    if (dark) return darkLiquidGlass(shape, prominent)
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val plate = if (prominent) colors.primaryContainer else Color.White
    val rim = Color.White.copy(alpha = .85f)
    val glass = if (source == null || opaque) Modifier.background(plate, shape) else Modifier.drawBackdrop(
        backdrop = source, shape = { shape },
        effects = { vibrancy(); blur(12.dp.toPx()); lens(12.dp.toPx(), 20.dp.toPx()) },
        onDrawSurface = { drawRect(plate.copy(alpha = if (prominent) .85f else .78f)) },
    )
    return shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
        .then(glass).border(.75.dp, rim, shape).clip(shape)
}

/** Video controls use a translucent plate; decoding surfaces are deliberately never sampled. */
@Composable
internal fun Modifier.mediaGlass(shape: Shape = Liquid.pill): Modifier {
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    if (MaterialTheme.colorScheme.background.red < .3f) {
        // SurfaceView video is never sampled. Keep a contrast plate even in transparent mode.
        return shadow(8.dp, shape).background(if (opaque) Color(0xFF181A1D) else Color(0xD9181A1D), shape)
            .then(if (opaque) Modifier else Modifier.background(DarkGlass.reflection, shape))
            .border(.75.dp, DarkGlass.rim, shape).clip(shape)
    }
    return shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .16f), spotColor = Color.Black.copy(alpha = .2f))
        .background(if (opaque) Color(0xFF171B24) else Color(0x99171B24), shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .025f),
            Color.Black.copy(alpha = .08f))), shape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .62f), Color.White.copy(alpha = .08f),
            Color.White.copy(alpha = .28f))), shape).clip(shape)
}

@Composable
internal fun GlassIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, prominent: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .8f), label = "glass-press")
    IconButton(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(48.dp)
        .liquidGlass(CircleShape, prominent), enabled = enabled, interactionSource = interaction) {
        Icon(icon, description, tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            else if (prominent) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
}

@Composable
internal fun LiquidTopBar(title: String, modifier: Modifier = Modifier, large: Boolean = false,
    navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().statusBarsPadding().heightIn(min = if (large) 76.dp else 64.dp)
        .padding(horizontal = Liquid.inset, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        navigation?.invoke()
        Text(title, Modifier.weight(1f), style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun SectionHeading(title: String, modifier: Modifier = Modifier, detail: String? = null,
    action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
internal fun <T> LiquidSegments(options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier, tag: (T) -> String = { "" }, select: (T) -> Unit) {
    if (options.isEmpty()) return
    val position by animateFloatAsState(options.indexOf(selected).coerceAtLeast(0).toFloat(),
        spring(dampingRatio = .86f, stiffness = 420f), label = "segment-selection")
    val selection = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    val indicator = MaterialTheme.colorScheme.primary.takeIf { MaterialTheme.colorScheme.background.red < .3f }
    Row(modifier.browseGlass().padding(4.dp).selectableGroup().drawBehind {
        val gap = 4.dp.toPx()
        val cell = (size.width - gap * (options.size - 1)) / options.size
        drawLiquidSelection(selection, indicator, Offset(position * (cell + gap), 0f), Size(cell, size.height))
    },
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(Liquid.pill)
                .selectable(active, role = Role.Tab, onClick = { select(option) }).testTag(tag(option))
                .padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
