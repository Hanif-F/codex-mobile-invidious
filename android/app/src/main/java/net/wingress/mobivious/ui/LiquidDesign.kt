package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Shape
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
        primary = Color(0xFF8DBAFF), onPrimary = Color(0xFF082E66),
        primaryContainer = Color(0xFF18355E), onPrimaryContainer = Color(0xFFCEE1FF),
        secondary = Color(0xFFBCC7D9), onSecondary = Color(0xFF273347),
        secondaryContainer = Color(0xFF2A3241), onSecondaryContainer = Color(0xFFE0E7F3),
        tertiary = Color(0xFFA9BEDD), background = Color(0xFF101115),
        surface = Color(0xFF101115), surfaceContainer = Color(0xFF1D2027),
        surfaceContainerLow = Color(0xFF17191F), surfaceContainerHigh = Color(0xFF282C35),
        surfaceContainerHighest = Color(0xFF323744), onSurface = Color(0xFFF2F4F8),
        onSurfaceVariant = Color(0xFFB2BAC8), outline = Color(0xFF8993A4),
        outlineVariant = Color(0xFF353B47), surfaceTint = Color.Transparent,
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

/** Only content is recorded by the shell. Glass must never record itself into its own source. */
@Composable
internal fun Modifier.liquidGlass(shape: Shape = Liquid.pill, prominent: Boolean = false): Modifier {
    val source = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .3f
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val plate = if (prominent) colors.primaryContainer else if (dark) Color(0xFF252A34) else Color.White
    val rim = if (dark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .85f)
    val glass = if (source == null || opaque) Modifier.background(plate, shape) else Modifier.drawBackdrop(
        backdrop = source, shape = { shape },
        effects = { vibrancy(); blur(12.dp.toPx()); lens(12.dp.toPx(), 20.dp.toPx()) },
        onDrawSurface = { drawRect(plate.copy(alpha = if (prominent) .85f else if (dark) .80f else .78f)) },
    )
    return shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
        .then(glass).border(.75.dp, rim, shape).clip(shape)
}

/** Video controls use a translucent plate; decoding surfaces are deliberately never sampled. */
@Composable
internal fun Modifier.mediaGlass(shape: Shape = Liquid.pill): Modifier =
    background(if (LocalReduceTransparency.current) Color(0xFF171B24) else Color(0xCC171B24), shape).border(.75.dp, Color.White.copy(alpha = .22f), shape).clip(shape)

@Composable
internal fun GlassIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, prominent: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .8f), label = "glass-press")
    IconButton(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(48.dp)
        .liquidGlass(CircleShape, prominent), enabled = enabled, interactionSource = interaction) {
        Icon(icon, description, tint = if (prominent) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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
    Row(modifier.clip(Liquid.pill).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(Liquid.pill)
                .background(if (active) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                .selectable(active, role = Role.Tab, onClick = { select(option) }).testTag(tag(option))
                .padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
