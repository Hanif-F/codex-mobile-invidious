package net.wingress.mobivious.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.*

@Composable
private fun WatchAction(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, action: () -> Unit) {
    Column(modifier.heightIn(min = 72.dp).clip(Liquid.media).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = action)
        .padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
internal fun WatchPrimaryActions(vm: AppViewModel, details: VideoDetails, save: () -> Unit, signIn: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember(details.video.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().testTag("watch-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchAction("Save", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), action = save)
        WatchAction("Download", Icons.Default.Download, Modifier.weight(1f).testTag("watch-download"),
            enabled = !details.video.live && details.upcoming != true) { vm.openDownload(details.video) }
        WatchAction("Share", Icons.Default.Share, Modifier.weight(1f)) {
            VideoLinks.share(vm.store.server, vm.queue.value, vm.controller.value?.currentPosition ?: vm.playback.value.position)?.let { url ->
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share video")) }
                    .onFailure { vm.message.value = "No app could share this link." }
            }
        }
        Box(Modifier.weight(1f)) {
            WatchAction("More", Icons.Default.MoreHoriz, Modifier.fillMaxWidth().testTag("watch-more")) { expanded = true }
            DropdownMenu(expanded, { expanded = false }) {
                if (ClipRules.eligible(details)) DropdownMenuItem(text = { Text("Create clip") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null) }, modifier = Modifier.testTag("create-clip"), onClick = {
                        expanded = false
                        if (vm.account.value == null) { vm.requestClipSignIn(); signIn() } else vm.openClipEditor()
                    })
                DropdownMenuItem(text = { Text("DeArrow Title") }, leadingIcon = { Icon(Icons.Default.Title, null) },
                    onClick = { expanded = false; if (vm.account.value == null) signIn() else vm.openDeArrow(details.video.id) })
            }
        }
    }
}
