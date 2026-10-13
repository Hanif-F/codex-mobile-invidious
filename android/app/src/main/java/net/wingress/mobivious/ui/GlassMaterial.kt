package net.wingress.mobivious.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/** All optical constants live here. Roles change thickness, never the renderer. */
internal object GlassOptics {
    val darkPlate = Color(0xFF18243B)
    val mediaPlate = Color(0xFF34445B)
    const val mediaTint = .80f
    fun controlTint(dark: Boolean, prominent: Boolean) = if (dark) {
        if (prominent) .20f else .14f
    } else { if (prominent) .26f else .20f }
    fun sectionTint(dark: Boolean) = if (dark) .20f else .28f
    const val magnification = 1.035f
    val controlDepth = 12.dp
    val sectionDepth = 18.dp
    val controlRefraction = 18.dp
    val sectionRefraction = 20.dp
    const val controlDeformation = .02f
    const val sectionDeformation = .01f
    val maxDisplacement = 2.dp
}

/** The deferred player retains its current material, including popup backgrounds. */
internal enum class GlassMaterialProfile { Refractive, DeferredPlayer }
internal val LocalGlassMaterialProfile = staticCompositionLocalOf { GlassMaterialProfile.Refractive }

@Composable
internal fun DeferredPlayerGlass(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassMaterialProfile provides GlassMaterialProfile.DeferredPlayer, content = content)
}

@Stable
internal class GlassInteraction {
    internal var pressed by mutableStateOf(false)
    internal var focused by mutableStateOf(false)
    internal var selected by mutableStateOf(false)
    internal var position by mutableStateOf(Offset.Unspecified)
    internal var progress: State<Float> = mutableFloatStateOf(0f)
}

/** Observe the existing semantic control's source, without intercepting pointer/scroll gestures. */
@Composable
internal fun rememberGlassInteraction(source: MutableInteractionSource, selected: Boolean = false): GlassInteraction {
    val state = remember(source) { GlassInteraction() }
    SideEffect { state.selected = selected }
    LaunchedEffect(source) {
        val presses = mutableSetOf<PressInteraction.Press>()
        val focuses = mutableSetOf<FocusInteraction.Focus>()
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> { presses.add(interaction); state.position = interaction.pressPosition }
                is PressInteraction.Release -> presses.remove(interaction.press)
                is PressInteraction.Cancel -> presses.remove(interaction.press)
                is FocusInteraction.Focus -> focuses.add(interaction)
                is FocusInteraction.Unfocus -> focuses.remove(interaction.focus)
            }
            state.pressed = presses.isNotEmpty(); state.focused = focuses.isNotEmpty()
        }
    }
    state.progress = animateFloatAsState(if (state.pressed) 1f else if (state.focused) .55f else 0f,
        spring(dampingRatio = .78f, stiffness = 460f), label = "glass-illumination")
    return state
}

@Composable
internal fun Modifier.glassMaterial(shape: Shape = Liquid.pill, prominent: Boolean = false,
    role: GlassMaterialRole = GlassMaterialRole.Control, interaction: GlassInteraction? = null): Modifier {
    if (LocalInsideGlassSection.current && role == GlassMaterialRole.Control) {
        val fill = LocalGlassContentColors.current.primary
        return clip(shape).drawWithContent {
            drawRect(fill.copy(alpha = (if (prominent) .12f else .045f) + (interaction?.progress?.value ?: 0f) * .07f))
            drawContent()
        }
    }
    val motion = LocalGlassMotionEnabled.current
    val source = LocalGlassBackdrop.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val opaque = LocalReduceTransparency.current || !LocalView.current.isHardwareAccelerated
    val media = role == GlassMaterialRole.MediaOverlay
    val section = role == GlassMaterialRole.Section
    val deferred = LocalGlassMaterialProfile.current == GlassMaterialProfile.DeferredPlayer
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val colors = rememberGlassContentColors(bounds, role, prominent)
    val plate = colors.plate
    val tint = colors.tint
    val reflection = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .055f else .16f),
        Color.Transparent, Color.White.copy(alpha = .025f)))
    val material = if (opaque || source == null || media) Modifier.background(plate.copy(alpha = if (opaque || !media) 1f else tint), shape)
    else Modifier.drawBackdrop(backdrop = source, shape = { shape }, effects = {
        if (deferred) {
            blur((if (section) 10.dp else 4.dp).toPx())
            lens(8.dp.toPx(), 18.dp.toPx())
        } else {
            val depth = (if (section) GlassOptics.sectionDepth else GlassOptics.controlDepth).toPx()
                .coerceAtMost(size.minDimension / 2f)
            lens(depth, (if (section) GlassOptics.sectionRefraction else GlassOptics.controlRefraction).toPx(),
                depthEffect = true, chromaticAberration = false)
        }
    }, onDrawBackdrop = { drawBackdrop ->
        if (deferred) drawBackdrop()
        else scale(GlassOptics.magnification, pivot = center) { drawBackdrop() }
    }, highlight = {
        Highlight(width = .8.dp, blurRadius = .5.dp, alpha = .30f + (if (interaction?.selected == true) .05f else 0f) + (interaction?.progress?.value ?: 0f) * .18f,
            style = HighlightStyle.Default(angle = 45f + (interaction?.progress?.value ?: 0f) * 18f))
    }, layerBlock = {
        val progress = if (motion) interaction?.progress?.value ?: 0f else 0f
        val deformation = if (section) GlassOptics.sectionDeformation else GlassOptics.controlDeformation
        scaleX = 1f + progress * deformation
        scaleY = 1f - progress * deformation * .55f
        translationY = -GlassOptics.maxDisplacement.toPx() * progress
    }, onDrawSurface = {
        drawRect(plate.copy(alpha = tint))
        drawRect(reflection)
        val progress = interaction?.progress?.value ?: 0f
        if (progress > .001f) {
            val touch = interaction?.position?.takeIf { it.isSpecified } ?: center
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .18f * progress), Color.Transparent),
                center = touch, radius = maxOf(size.width, size.height) * .9f))
        }
    })
    // Opaque/software/media fallbacks use the same directional edge, with no recording readback.
    return onGloballyPositioned { bounds = it.boundsInRoot() }.then(material).drawWithContent {
        val progress = interaction?.progress?.value ?: 0f
        if ((opaque || source == null || media) && progress > .001f) {
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .14f * progress), Color.Transparent),
                center = interaction?.position?.takeIf { it.isSpecified } ?: center, radius = maxOf(size.width, size.height)))
        }
        drawContent()
    }.then(if (opaque || source == null || media) Modifier.border(.75.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark || media) .28f else .8f),
            Color.Transparent, Color.White.copy(alpha = .09f))), shape) else Modifier).clip(shape)
}

/** Thin feedback within a shared container; no second glass layer or gesture observer. */
@Composable
internal fun Modifier.glassItemFeedback(source: MutableInteractionSource, selected: Boolean = false): Modifier {
    val interaction = rememberGlassInteraction(source, selected)
    val ink = LocalContentColor.current
    val motion = LocalGlassMotionEnabled.current
    return graphicsLayer {
        val progress = if (motion) interaction.progress.value else 0f
        scaleX = 1f + GlassOptics.controlDeformation * progress
        scaleY = 1f - GlassOptics.controlDeformation * .5f * progress
    }.drawWithContent {
        val progress = interaction.progress.value
        val illumination = .14f * progress + if (interaction.selected) .035f else 0f
        if (illumination > .001f) drawRect(Brush.radialGradient(listOf(ink.copy(alpha = illumination), Color.Transparent),
            center = interaction.position.takeIf { it.isSpecified } ?: center, radius = maxOf(size.width, size.height)))
        drawContent()
    }
}
