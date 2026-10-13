package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Each dialog refracts its originating window; form controls share a single section. */
@Composable
internal fun GlassAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    val palette = originatingGlassPalette()
    val source = rememberGlassWindowSource()
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalGlassEnvironmentPalette provides palette) {
        GlassOverlayScene(source, modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp)
            .imePadding().clip(Liquid.card)) {
            GlassSection {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    icon?.invoke()
                    title?.let { Box(Modifier.semantics { heading() }) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                    } }
                    text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium, it) }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dismissButton?.invoke(); confirmButton()
                    }
                }
            }
        }
        }
    }
}
