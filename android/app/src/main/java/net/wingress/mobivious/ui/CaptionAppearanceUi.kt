package net.wingress.mobivious.ui

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.accessibility.CaptioningManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import net.wingress.mobivious.data.CaptionAppearance
import java.util.WeakHashMap

private data class CaptionRenderKey(val appearance: CaptionAppearance, val foreground: Int, val edge: Int, val edgeColor: Int, val typeface: Typeface?, val scale: Float)
private val renderedCaptions = WeakHashMap<android.view.View, CaptionRenderKey>()

@Composable
internal fun rememberCaptionAppearance(vm: AppViewModel): CaptionAppearance {
    val saved by vm.captionAppearance.collectAsStateWithLifecycle()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    return remember(saved, edits) { vm.requestedCaptionAppearance() }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun SubtitleView.applyCaptionAppearance(value: CaptionAppearance) {
    val a = value.normalized()
    val manager = context.getSystemService(CaptioningManager::class.java)
    val preferred = manager?.userStyle
        ?.let(CaptionStyleCompat::createFromCaptionStyle) ?: CaptionStyleCompat.DEFAULT
    val scale = manager?.fontScale?.takeIf { it.isFinite() && it > 0 } ?: 1f
    val key = CaptionRenderKey(a, preferred.foregroundColor, preferred.edgeType, preferred.edgeColor, preferred.typeface, scale)
    // Position updates must not recreate native caption layouts when styling is unchanged.
    if (renderedCaptions[this] == key) return
    renderedCaptions[this] = key
    fun color(hex: String, opacity: Int) = (AndroidColor.parseColor(hex) and 0x00ffffff) or ((255 * opacity / 100) shl 24)
    val foreground = if (a.textColor == "system") (preferred.foregroundColor and 0x00ffffff) or ((255 * a.textOpacity / 100) shl 24)
        else color(a.textColor, a.textOpacity)
    val edge = when (a.edge) {
        "none" -> CaptionStyleCompat.EDGE_TYPE_NONE
        "uniform" -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        "dropshadow" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        "raised" -> CaptionStyleCompat.EDGE_TYPE_RAISED
        "depressed" -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
        else -> preferred.edgeType.takeUnless { it == CaptionStyleCompat.EDGE_TYPE_NONE } ?: CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
    }
    val family = when (a.font) {
        "proportionalSansSerif" -> "sans-serif"
        "monospaceSansSerif" -> "monospace"
        "proportionalSerif" -> "serif"
        "monospaceSerif" -> "serif-monospace"
        "casual" -> "casual"
        "script" -> "cursive"
        "small-caps" -> "sans-serif-smallcaps"
        else -> null
    }
    val edgeColor = if (a.edge == "system" && preferred.edgeType != CaptionStyleCompat.EDGE_TYPE_NONE) preferred.edgeColor else AndroidColor.BLACK
    setStyle(CaptionStyleCompat(foreground, color(a.backgroundColor, a.backgroundOpacity), color(a.windowColor, a.windowOpacity),
        if (a.textOpacity == 0) CaptionStyleCompat.EDGE_TYPE_NONE else edge,
        (edgeColor and 0x00ffffff) or ((255 * a.textOpacity / 100) shl 24),
        family?.let { Typeface.create(it, Typeface.NORMAL) } ?: preferred.typeface))
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale * a.size / 100f)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun CaptionPreview(appearance: CaptionAppearance, modifier: Modifier = Modifier) {
    AndroidView(factory = { context -> SubtitleView(context).apply {
        setCues(listOf(Cue.Builder().setText("Caption preview\nAa Bb 123 · 日本語").build()))
    } }, update = { it.applyCaptionAppearance(appearance) },
        modifier = modifier.fillMaxWidth().height(180.dp).background(Color(0xFF42647A)).testTag("caption-preview"))
}

@Composable
internal fun CaptionAppearanceEditor(vm: AppViewModel) {
    val appearance = rememberCaptionAppearance(vm)
    val context = vm.api.context()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val colors = CaptionAppearance.colors
    Column(Modifier.fillMaxWidth().testTag("caption-appearance-editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved for this account or guest and instance on this device.", style = MaterialTheme.typography.bodySmall)
        CaptionPreview(appearance)
        fun save(value: CaptionAppearance) = vm.setCaptionAppearance(value, context)
        DropdownChoiceRow("Text size", CaptionAppearance.sizes.map { "$it" to "$it%" }, "${appearance.size}", change = { save(appearance.copy(size = it.toInt())) })
        DropdownChoiceRow("Font", CaptionAppearance.fonts, appearance.font, change = { save(appearance.copy(font = it)) })
        DropdownChoiceRow("Text color", listOf("system" to "Device default") + colors, appearance.textColor, change = { save(appearance.copy(textColor = it)) })
        DropdownChoiceRow("Text opacity", CaptionAppearance.opacities.map { "$it" to "$it%" }, "${appearance.textOpacity}", change = { save(appearance.copy(textOpacity = it.toInt())) })
        DropdownChoiceRow("Background color", colors, appearance.backgroundColor, change = { save(appearance.copy(backgroundColor = it)) })
        DropdownChoiceRow("Background opacity", CaptionAppearance.opacities.map { "$it" to "$it%" }, "${appearance.backgroundOpacity}", change = { save(appearance.copy(backgroundOpacity = it.toInt())) })
        DropdownChoiceRow("Window color", colors, appearance.windowColor, change = { save(appearance.copy(windowColor = it)) })
        DropdownChoiceRow("Window opacity", CaptionAppearance.opacities.map { "$it" to "$it%" }, "${appearance.windowOpacity}", change = { save(appearance.copy(windowOpacity = it.toInt())) })
        DropdownChoiceRow("Text edge", CaptionAppearance.edges, appearance.edge, change = { save(appearance.copy(edge = it)) })
        TextButton(onClick = { save(CaptionAppearance()) }, modifier = Modifier.testTag("caption-reset")) { Text("Reset to defaults") }
        edits[CaptionAppearance.SAVE_KEY]?.let { edit ->
            PreferenceSaveFeedback(edit.status, edit.error, { vm.preferenceSaves.retry(edit.key) }, { vm.preferenceSaves.revert(edit.key) })
        }
    }
}
