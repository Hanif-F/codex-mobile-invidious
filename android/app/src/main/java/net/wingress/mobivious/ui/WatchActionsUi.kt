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
import androidx.compose.ui.platform.testTag
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            WatchIconButton(Icons.AutoMirrored.Filled.PlaylistAdd, "Save", Modifier.testTag("watch-save"), size = size, onClick = save)
            WatchIconButton(Icons.Default.Download, "Download", Modifier.testTag("watch-download"),
                enabled = downloadEnabled, size = size, onClick = download)
            WatchIconButton(Icons.Default.Share, "Share", Modifier.testTag("watch-share"), size = size, onClick = share)
            Box {
                WatchIconButton(Icons.Default.MoreHoriz, "More", Modifier.testTag("watch-more"), size = size, onClick = more)
                moreMenu()
            }
        }
    }
}
