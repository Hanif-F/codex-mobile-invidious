package net.wingress.mobivious.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import net.wingress.mobivious.data.*

@Composable
internal fun WatchPrimaryActions(vm: AppViewModel, details: VideoDetails, save: () -> Unit, signIn: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember(details.video.id) { mutableStateOf(false) }
    WatchActionRow(downloadEnabled = !details.video.live && details.upcoming != true, save = save,
        download = { vm.openDownload(details.video) }, share = {
            vm.queue.value.share(vm.store.server, vm.controller.value?.currentPosition ?: vm.playback.value.position)?.let { url ->
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share video")) }
                    .onFailure { vm.message.value = "No app could share this link." }
            }
        }, more = { expanded = true }, moreMenu = {
            AppDropdownMenu(expanded, { expanded = false }) {
                if (ClipRules.eligible(details)) DropdownMenuItem(text = { Text("Create clip") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null) }, modifier = Modifier.testTag("create-clip"), onClick = {
                        expanded = false
                        if (vm.account.value == null) { vm.requestClipSignIn(); signIn() } else vm.openClipEditor()
                    })
                DropdownMenuItem(text = { Text("DeArrow Title") }, leadingIcon = { Icon(Icons.Default.Title, null) },
                    onClick = { expanded = false; if (vm.account.value == null) signIn() else vm.openDeArrow(details.video.id) })
            }
        })
}

/** Callbacks keep action layout independently testable without a playback service. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchActionRow(downloadEnabled: Boolean, save: () -> Unit, download: () -> Unit,
    share: () -> Unit, more: () -> Unit, moreMenu: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val gap = minOf(8.dp, ((maxWidth - 192.dp) / 3).coerceAtLeast(0.dp))
    val circleSize = if ((maxWidth - gap * 3) / 4 >= 56.dp) 56.dp else 48.dp
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("watch-actions"),
        horizontalArrangement=Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
        val action: @Composable (ImageVector,String,String,Boolean,() -> Unit) -> Unit = {icon,label,tag,enabled,click ->
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { AppTooltip(label) }, state = rememberTooltipState()) {
                FilledIconButton(click, Modifier.size(circleSize).testTag(tag).semantics { contentDescription = label },
                    enabled = enabled, shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(icon, null, Modifier.size(24.dp))
                }
            }
            Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
          }
        }
        Box(Modifier.weight(1f)) { action(Icons.AutoMirrored.Filled.PlaylistAdd,"Save","watch-save",true,save) }
        Box(Modifier.weight(1f)) { action(Icons.Default.Download,"Download","watch-download",downloadEnabled,download) }
        Box(Modifier.weight(1f)) { action(Icons.Default.Share,"Share","watch-share",true,share) }
        Box(Modifier.weight(1f)) { action(Icons.Default.MoreHoriz,"More","watch-more",true,more);moreMenu() }
    }
    }
}
