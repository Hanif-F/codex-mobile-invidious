package net.wingress.mobivious.ui

import android.app.Activity
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.wingress.mobivious.BuildConfig
import net.wingress.mobivious.data.RssState
import net.wingress.mobivious.data.SubscriptionExportFormat

@Composable
internal fun SubscriptionExportFormats(selected: SubscriptionExportFormat, select: (SubscriptionExportFormat) -> Unit) {
    BrowseTabs(SubscriptionExportFormat.entries, selected, { it.label }, { "feed-format-${it.apiValue}" }, select = select)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSheet(vm: AppViewModel) {
    val state by vm.rss.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<RssState?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        val selected = pendingSave; pendingSave = null
        if (uri != null && selected != null && selected.context == vm.api.context() && vm.rss.value === selected && selected.body != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (selected.context != vm.api.context() || vm.rss.value !== selected) return@withContext
                    context.contentResolver.openOutputStream(uri)?.use { it.write(selected.body.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the selected document.")
                }
                if (selected.context == vm.api.context() && vm.rss.value === selected) vm.message.value = "Export file saved"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (selected.context == vm.api.context() && vm.rss.value === selected) vm.message.value = e.message ?: "Could not save the export file." }
        }
    }
    LaunchedEffect(state.context, state.open) { if (!state.open || pendingSave?.context != state.context) pendingSave = null }
    if (!state.open) return
    fun external(intent: Intent, chooser: Boolean = false) {
        if (state.context != vm.api.context() || vm.rss.value !== state) return
        try { context.startActivity(if (chooser) Intent.createChooser(intent, "Share export") else intent) }
        catch (_: ActivityNotFoundException) { vm.message.value = "No app can open this export. Copy its link or save the file." }
        catch (_: SecurityException) { vm.message.value = "The selected app could not open this export." }
    }
    fun fileIntent(share: Boolean) {
        val selected = state
        if (selected.context != vm.api.context() || vm.rss.value !== selected) return
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "rss").apply { mkdirs() }
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.deleteRecursively() }
                    val folder = File(dir, UUID.randomUUID().toString()).apply { mkdirs() }
                    File(folder, selected.filename).apply { writeText(selected.body ?: error("Export is not ready.")) }
                }
                if (selected.context != vm.api.context() || vm.rss.value !== selected) { file.delete(); return@launch }
                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.rss", file)
                val mime = selected.mimeType
                val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).clipData = ClipData.newRawUri("Feed", uri)
                external(intent, share)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (selected.context == vm.api.context() && vm.rss.value === selected) vm.message.value = e.message ?: "Could not export this file." }
        }
    }
    AppModalSheet(onDismissRequest = vm::dismissRss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PanelScaffold(Modifier.fillMaxWidth().heightIn(max = 480.dp).fillMaxHeight().testTag("rss-sheet"), toolbar = {
            BrowsePanelToolbar(state.title, "Close feed", vm::dismissRss)
        }) { contentModifier, topInset ->
        Column(contentModifier.verticalScroll(rememberScrollState()).padding(start = AppTokens.inset, end = AppTokens.inset, top = topInset, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSection {

            if (state.privateLink) Text("Anyone with this link can read your subscription feed. Share it only when you want to grant that access.")
            if (state.playlist?.let { it.owned && it.privacy == "private" } == true) Text("This exports a snapshot of your private playlist. The file does not update automatically.")
            state.exportFormat?.let { format -> Box { SubscriptionExportFormats(format, vm::openSubscriptionExport) } }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Box { LibraryActionButton("Retry", onClick = vm::retryRss) } }
            if (!state.loading && state.error == null && state.url != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton("Open", prominent = true, onClick = { external(Intent(Intent.ACTION_VIEW, Uri.parse(state.url))) })
                LibraryActionButton("Copy", onClick = {
                    if (state.context == vm.api.context()) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RSS feed", state.url))
                        vm.message.value = "Feed link copied"
                    }
                })
                LibraryActionButton("Share", onClick = { external(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.url), true) })
            }
            if (!state.loading && state.error == null && state.body != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryActionButton("Open", prominent = true, onClick = { fileIntent(false) })
                LibraryActionButton("Save file", onClick = { if (state.context == vm.api.context() && vm.rss.value === state) {
                    pendingSave = state
                    save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(state.mimeType).putExtra(Intent.EXTRA_TITLE, state.filename))
                } })
                LibraryActionButton("Share", onClick = { fileIntent(true) })
            }

            }
        }
    }
    }
}
