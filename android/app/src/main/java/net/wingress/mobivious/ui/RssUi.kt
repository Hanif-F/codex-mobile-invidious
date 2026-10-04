package net.wingress.mobivious.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.data.RssState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSheet(vm: AppViewModel) {
    val state by vm.rss.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<RssState?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/xml")) { uri ->
        val selected = pendingSave; pendingSave = null
        if (uri != null && selected?.context == vm.api.context() && selected.xml != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (selected.context != vm.api.context()) return@withContext
                    context.contentResolver.openOutputStream(uri)?.use { it.write(selected.xml.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the selected document.")
                }
                if (selected.context == vm.api.context()) vm.message.value = "Feed file saved"
            } catch (e: Exception) { if (selected.context == vm.api.context()) vm.message.value = e.message ?: "Could not save the feed file." }
        }
    }
    LaunchedEffect(state.context, state.open) { if (!state.open || pendingSave?.context != state.context) pendingSave = null }
    if (!state.open) return
    fun external(intent: Intent, chooser: Boolean = false) {
        if (state.context != vm.api.context()) return
        try { context.startActivity(if (chooser) Intent.createChooser(intent, "Share feed") else intent) }
        catch (_: ActivityNotFoundException) { vm.message.value = "No app can open this feed. Copy its link or save the file." }
        catch (_: SecurityException) { vm.message.value = "The selected app could not open this feed." }
    }
    fun fileIntent(share: Boolean) {
        val selected = state
        if (selected.context != vm.api.context()) return
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "rss").apply { mkdirs() }
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    File(dir, "${UUID.randomUUID()}-${selected.filename}").apply { writeText(selected.xml ?: error("Feed is not ready.")) }
                }
                if (selected.context != vm.api.context() || !vm.rss.value.open) { file.delete(); return@launch }
                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.rss", file)
                val mime = if (selected.opml) "application/xml" else "application/atom+xml"
                val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).clipData = ClipData.newRawUri("Feed", uri)
                external(intent, share)
            } catch (e: Exception) { if (selected.context == vm.api.context()) vm.message.value = e.message ?: "Could not export this feed." }
        }
    }
    ModalBottomSheet(onDismissRequest = vm::dismissRss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("rss-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = vm::dismissRss) { Icon(Icons.Default.Close, "Close feed") }
            }
            if (state.privateLink) Text("Anyone with this link can read your subscription feed. Share it only when you want to grant that access.")
            if (state.playlist?.let { it.owned && it.privacy == "private" } == true) Text("This exports a snapshot of your private playlist. The file does not update automatically.")
            if (state.opml) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.format == "rss", onClick = { vm.openOpml("rss") }, label = { Text("Invidious feeds") })
                FilterChip(selected = state.format == "newpipe", onClick = { vm.openOpml("newpipe") }, label = { Text("YouTube feeds") })
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = vm::retryRss) { Text("Retry") } }
            if (!state.loading && state.error == null && state.url != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { external(Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) }) { Text("Open") }
                OutlinedButton(onClick = {
                    if (state.context == vm.api.context()) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RSS feed", state.url))
                        vm.message.value = "Feed link copied"
                    }
                }) { Text("Copy") }
                OutlinedButton(onClick = { external(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.url), true) }) { Text("Share") }
            }
            if (!state.loading && state.error == null && state.xml != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { fileIntent(false) }) { Text("Open") }
                OutlinedButton(onClick = { if (state.context == vm.api.context()) { pendingSave = state; save.launch(state.filename) } }) { Text("Save file") }
                OutlinedButton(onClick = { fileIntent(true) }) { Text("Share") }
            }
        }
    }
}
