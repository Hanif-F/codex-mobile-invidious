package net.wingress.mobivious.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.*

@Composable
internal fun WatchPrimaryActions(vm: AppViewModel, details: VideoDetails, save: () -> Unit, signIn: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember(details.video.id) { mutableStateOf(false) }
    WatchActionRow(downloadEnabled = !details.video.live && details.upcoming != true, save = save,
        download = { vm.openDownload(details.video) }, share = {
            VideoLinks.share(vm.store.server, vm.queue.value, vm.controller.value?.currentPosition ?: vm.playback.value.position)?.let { url ->
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share video")) }
                    .onFailure { vm.message.value = "No app could share this link." }
            }
        }, more = { expanded = true }, moreMenu = {
            DropdownMenu(expanded, { expanded = false }, shape = Liquid.card,
                containerColor = Color.Transparent, tonalElevation = 0.dp, shadowElevation = 0.dp) {
              WatchGlassScene(Modifier.clip(Liquid.card)) { Column(Modifier.watchGlass()) {
                if (ClipRules.eligible(details)) DropdownMenuItem(text = { Text("Create clip") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null) }, modifier = Modifier.testTag("create-clip"), onClick = {
                        expanded = false
                        if (vm.account.value == null) { vm.requestClipSignIn(); signIn() } else vm.openClipEditor()
                    })
                DropdownMenuItem(text = { Text("DeArrow Title") }, leadingIcon = { Icon(Icons.Default.Title, null) },
                    onClick = { expanded = false; if (vm.account.value == null) signIn() else vm.openDeArrow(details.video.id) })
              } }
            }
        })
}

/** Callbacks keep action layout independently testable without a playback service. */
@Composable
internal fun WatchActionRow(downloadEnabled: Boolean, save: () -> Unit, download: () -> Unit,
    share: () -> Unit, more: () -> Unit, moreMenu: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("watch-actions")) {
        val size = if (maxWidth < 272.dp) 48.dp else 56.dp
        val style = MaterialTheme.typography.labelMedium
        val available = with(LocalDensity.current) { (maxWidth / 4 - 4.dp).toPx() }
        val longest = rememberTextMeasurer().measure(AnnotatedString("Download"), style, softWrap = false).size.width
        val fit = (available / longest.coerceAtLeast(1)).coerceAtMost(1f)
        val labelStyle = style.copy(fontSize = style.fontSize * fit, lineHeight = style.lineHeight * fit)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            WatchLabeledAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Save", "watch-save", size, Modifier.weight(1f), labelStyle, onClick = save)
            WatchLabeledAction(Icons.Default.Download, "Download", "watch-download", size, Modifier.weight(1f), labelStyle, downloadEnabled, download)
            WatchLabeledAction(Icons.Default.Share, "Share", "watch-share", size, Modifier.weight(1f), labelStyle, onClick = share)
            WatchLabeledAction(Icons.Default.MoreHoriz, "More", "watch-more", size, Modifier.weight(1f), labelStyle, onClick = more, menu = moreMenu)
        }
    }
}

@Composable
private fun WatchLabeledAction(icon: ImageVector, label: String, tag: String, size: Dp,
    modifier: Modifier, labelStyle: TextStyle, enabled: Boolean = true, onClick: () -> Unit, menu: @Composable () -> Unit = {}) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            WatchIconButton(icon, label, Modifier.testTag(tag), enabled = enabled, size = size, onClick = onClick)
            menu()
        }
        Text(label, Modifier.fillMaxWidth().semantics { hideFromAccessibility() }, textAlign = TextAlign.Center,
            style = labelStyle, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .85f else .38f))
    }
}
