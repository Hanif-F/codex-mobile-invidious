package net.wingress.mobivious.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.wingress.mobivious.data.*
import net.wingress.mobivious.downloads.DownloadExportService
import net.wingress.mobivious.player.StreamCatalog

private fun choiceLabel(choice: DownloadChoice): String {
    if (choice.kind == DownloadKind.CAPTION) return listOf(choice.label, choice.language).filter(String::isNotBlank).joinToString(" · ")
    val format = choice.format
    return listOf(if (choice.kind == DownloadKind.VIDEO) "${format.height}p${if (format.fps > 0) " · ${format.fps.toInt()} fps" else ""}" else listOf(format.audio?.name?.takeIf(String::isNotBlank) ?: "Audio", format.audio?.id?.substringBefore('.')?.takeIf { it.matches(Regex("[A-Za-z]{2,3}(-[A-Za-z0-9]+)*")) }).filterNotNull().joinToString(" · "),
        format.codec, if (format.drc == true) "Stable volume" else "", StreamCatalog.bitrateLabel(format.bitrate), StreamCatalog.bytesLabel(format.bytes))
        .filter(String::isNotBlank).joinToString(" · ")
}

@Composable
internal fun DownloadDialog(vm: AppViewModel) {
    val state by vm.downloadDialog.collectAsStateWithLifecycle()
    val catalog = state.catalog
    AlertDialog(onDismissRequest = vm::dismissDownload, title = { Text("Download") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).testTag("download-dialog"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(state.video?.title.orEmpty()); Text("Choose at least one video or audio track. Captions are optional.", style = MaterialTheme.typography.bodySmall) }
                if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { state.video?.let(vm::openDownload) }) { Text("Retry") } } }
                if (catalog != null && !catalog.allowed) item { Text(catalog.reason) }
                if (catalog?.allowed == true) {
                    listOf(DownloadKind.VIDEO, DownloadKind.AUDIO).forEach { kind ->
                        item { Text(if (kind == DownloadKind.VIDEO) "Video" else "Audio", style = MaterialTheme.typography.titleMedium) }
                        val selected = if (kind == DownloadKind.VIDEO) state.selection.video else state.selection.audio
                        fun pick(key: String?) { vm.selectDownload(if (kind == DownloadKind.VIDEO) state.selection.copy(video = key) else state.selection.copy(audio = key)) }
                        item {
                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { pick(null) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == null, { pick(null) }, enabled = !state.busy, modifier = Modifier.testTag("download-${kind.name.lowercase()}-none")); Text("None")
                            }
                        }
                        items(catalog.choices.filter { it.kind == kind }, key = { it.key }) { choice ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { pick(choice.key) }.testTag("download-choice-${choice.key}"), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == choice.key, { pick(choice.key) }, enabled = !state.busy); Text(choiceLabel(choice), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (catalog.choices.any { it.kind == DownloadKind.CAPTION }) item { Text("Captions", style = MaterialTheme.typography.titleMedium) }
                    items(catalog.choices.filter { it.kind == DownloadKind.CAPTION }, key = { it.key }) { choice ->
                        fun toggle() = vm.selectDownload(state.selection.copy(captions = if (choice.key in state.selection.captions) state.selection.captions - choice.key else state.selection.captions + choice.key))
                        Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { toggle() }.testTag("download-choice-${choice.key}"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(choice.key in state.selection.captions, { toggle() }, enabled = !state.busy); Text(choiceLabel(choice))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = vm::confirmDownload, enabled = catalog?.allowed == true && state.selection.hasMedia && !state.busy, modifier = Modifier.testTag("download-confirm")) { Text("Download") } },
        dismissButton = { TextButton(onClick = vm::dismissDownload, enabled = !state.busy) { Text("Cancel") } })
}

@Composable
internal fun DownloadChannelIdentity(video: Video) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null)
            if (video.authorAvatar.isNotBlank()) AsyncImage(video.authorAvatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(video.author)
    }
}

@Composable
internal fun DownloadedScreen(vm: AppViewModel, list: LazyListState, play: (String) -> Unit) {
    val records by vm.downloads.records.collectAsStateWithLifecycle()
    val error by vm.downloads.error.collectAsStateWithLifecycle()
    val export by DownloadExportService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable { mutableStateOf("") }
    var pendingAsset by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            DownloadExportService.start(context, pendingId, pendingAsset, uri)
        }
    }
    fun save(record: DownloadRecord, asset: DownloadAsset?) {
        pendingId = record.id; pendingAsset = asset?.choice?.key; exporting = null
        val media = record.assets.filter { it.media }
        val extension = asset?.choice?.extension ?: if (media.size == 2) "mp4" else media.single().choice.extension
        val mime = asset?.choice?.mime ?: if (media.size == 2) "video/mp4" else media.single().choice.mime
        val name = record.details.video.title.replace(Regex("[\\\\/\\r\\n]"), "_").take(120)
        picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
            .putExtra(Intent.EXTRA_TITLE, "$name-${record.id.take(8)}${asset?.choice?.language?.takeIf(String::isNotBlank)?.let { "-$it" }.orEmpty()}.$extension"))
    }
    LaunchedEffect(Unit) { vm.downloads.refresh() }
    LazyColumn(Modifier.fillMaxSize().testTag("downloads-page"), state = list, contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = 8.dp, bottom = LocalContentBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Saved on this device · ${DisplayFormats.inventory(records.size.toLong(), "download")}", style = MaterialTheme.typography.bodySmall) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm.downloads::refresh) { Text("Retry") } } }
        if (export.phase.isNotBlank()) item {
            Column { Text("Export: ${export.phase}", Modifier.testTag("download-export-status")); if (export.error.isNotBlank()) Text(export.error, color = MaterialTheme.colorScheme.error)
                if (export.busy) { LinearProgressIndicator(progress = { export.progress / 100f }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { DownloadExportService.cancel(context) }) { Text("Cancel export") } }
            }
        }
        if (records.isEmpty()) item { Text("Your downloads will appear here. Open a video's Download action to save media and optional captions.") }
        items(records, key = { it.id }) { record ->
            val video = vm.downloads.localDetails(record).video
            Card(Modifier.fillMaxWidth().testTag("download-entry-${record.id}"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(112.dp).height(64.dp).clip(Liquid.media), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.OndemandVideo, null)
                            if (video.thumbnail.isNotBlank()) AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f)) { Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            DisplayFormats.duration(video.duration).takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                    DownloadChannelIdentity(video)
                    record.assets.filterNot { it.artwork }.forEach { asset -> Text(choiceLabel(asset.choice), style = MaterialTheme.typography.bodySmall) }
                    val incomplete = record.assets.filterNot { it.artwork }.filter { it.status != DownloadStatus.COMPLETE }
                    val status = if (record.playable && incomplete.isEmpty()) "Downloaded" else if (incomplete.any { it.status == DownloadStatus.FAILED }) "Some files failed" else if (record.active) "Downloading" else "Cancelled"
                    Text("$status · ${DisplayFormats.bytes(record.bytes)}${if (record.total > 0) " / ${DisplayFormats.bytes(record.total)}" else ""}", Modifier.testTag("download-status-${record.id}"), style = MaterialTheme.typography.bodySmall)
                    if (record.active) { if (record.total > 0) LinearProgressIndicator(progress = { (record.bytes.toFloat() / record.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    incomplete.firstOrNull { it.error.isNotBlank() }?.let { Text(it.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { play(record.id) }, enabled = record.playable, modifier = Modifier.testTag("download-play-${record.id}")) { Text("Play") }
                        if (record.active) TextButton(onClick = { vm.cancelDownload(record.id) }) { Text("Cancel") }
                        if (record.assets.any { it.status in listOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED) || it.status == DownloadStatus.QUEUED && it.transferId == 0L }) TextButton(onClick = { vm.retryDownload(record.id) }) { Text("Retry") }
                        TextButton(onClick = { exporting = record.id }, enabled = record.playable && !export.busy, modifier = Modifier.testTag("download-save-${record.id}")) { Text("Save to files") }
                        TextButton(onClick = { deleting = record.id }) { Text("Delete") }
                    }
                }
            }
        }
    }
    records.firstOrNull { it.id == deleting }?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete download?") }, text = { Text("Remove ${record.details.video.title} and its saved files from this device?") },
            confirmButton = { TextButton(onClick = { vm.deleteDownload(record.id); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    records.firstOrNull { it.id == exporting }?.let { record ->
        AlertDialog(onDismissRequest = { exporting = null }, title = { Text("Save to files") }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) { TextButton(onClick = { save(record, null) }) { Text(if (record.assets.count { it.media } == 2) "Combined video and audio" else "Media file") }
                record.assets.filter { it.choice.kind == DownloadKind.CAPTION && it.status == DownloadStatus.COMPLETE }.forEach { asset -> TextButton(onClick = { save(record, asset) }) { Text("Caption: ${asset.choice.label}") } }
            }
        }, confirmButton = { TextButton(onClick = { exporting = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun DownloadExportConsent() {
    val state by DownloadExportService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.phase == "Needs conversion") AlertDialog(onDismissRequest = { DownloadExportService.cancel(context) },
        title = { Text("Convert before export?") },
        text = { Text("These tracks could not be merged unchanged into MP4. Necessary tracks will be converted to H.264 video or AAC audio. This may reduce quality and take longer. The original downloaded files stay intact. Resolution and frame rate will be preserved, or the export will report an error.") },
        confirmButton = { TextButton(onClick = { DownloadExportService.start(context, state.id, state.asset, Uri.parse(state.destination), true) }, modifier = Modifier.testTag("download-convert-confirm")) { Text("Convert and export") } },
        dismissButton = { TextButton(onClick = { DownloadExportService.cancel(context) }, modifier = Modifier.testTag("download-convert-cancel")) { Text("Cancel") } })
}
