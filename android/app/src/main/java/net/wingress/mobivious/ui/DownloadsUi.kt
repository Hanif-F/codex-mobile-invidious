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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
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
    LibraryPanel("Download", vm::dismissDownload, enabled = !state.busy, actions = {
        LibraryActionButton("Download", Modifier.testTag("download-confirm"), enabled = catalog?.allowed == true && state.selection.hasMedia && !state.busy,
            prominent = true, onClick = vm::confirmDownload)
    }) { modifier, top ->
            LazyColumn(modifier.testTag("download-dialog"), contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = top, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                item { TextButton(onClick = vm::dismissDownload, enabled = !state.busy) { Text("Cancel") } }
            }
        }
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
internal fun DownloadedScreen(vm: AppViewModel, list: LazyListState, top: Dp = 8.dp, play: (String) -> Unit) {
    val records by vm.downloads.records.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
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
    LazyColumn(Modifier.fillMaxSize().testTag("downloads-page"), state = list, contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = top, bottom = LocalContentBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Saved on this device · ${DisplayFormats.inventory(records.size.toLong(), "download")}", style = MaterialTheme.typography.bodySmall) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm.downloads::refresh) { Text("Retry") } } }
        if (export.phase.isNotBlank()) item {
            Column { Text("Export: ${export.phase}", Modifier.testTag("download-export-status")); if (export.error.isNotBlank()) Text(export.error, color = MaterialTheme.colorScheme.error)
                if (export.busy) { LinearProgressIndicator(progress = { export.progress / 100f }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { DownloadExportService.cancel(context) }) { Text("Cancel export") } }
            }
        }
        if (records.isEmpty()) item { LibraryEmptyState("Your offline library", "Open a video’s Download action to save video, audio, and optional captions on this device.", Icons.Default.DownloadDone) }
        items(records, key = { it.id }) { record ->
            val video = vm.downloads.localDetails(record).video
            var details by rememberSaveable(record.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().testTag("download-entry-${record.id}").padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val heading: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(video.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DisplayFormats.duration(video.duration).takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (!prefs.thinMode) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(if (prefs.uiDensity == "compact") 80.dp else 112.dp).aspectRatio(16f / 9f).clip(Liquid.media), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.OndemandVideo, null)
                        if (video.thumbnail.isNotBlank()) AsyncImage(video.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.weight(1f)) { heading() }
                } else heading()
                val incomplete = record.assets.filterNot { it.artwork }.filter { it.status != DownloadStatus.COMPLETE }
                val status = if (record.playable && incomplete.isEmpty()) "Downloaded" else if (incomplete.any { it.status == DownloadStatus.FAILED }) "Some files failed" else if (record.active) "Downloading" else "Cancelled"
                Text("$status · ${DisplayFormats.bytes(record.bytes)}${if (record.total > 0) " / ${DisplayFormats.bytes(record.total)}" else ""}", Modifier.testTag("download-status-${record.id}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.active) { if (record.total > 0) LinearProgressIndicator(progress = { (record.bytes.toFloat() / record.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()) }
                incomplete.firstOrNull { it.error.isNotBlank() }?.let { Text(it.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                BrowseArtworkSurface(null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        LibraryActionButton("Play", Modifier.testTag("download-play-${record.id}"), Icons.Default.PlayArrow, enabled = record.playable, prominent = true) { play(record.id) }
                        if (record.active) TextButton(onClick = { vm.cancelDownload(record.id) }) { Text("Cancel") }
                        if (record.assets.any { it.status in listOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED) || it.status == DownloadStatus.QUEUED && it.transferId == 0L }) TextButton(onClick = { vm.retryDownload(record.id) }) { Text("Retry") }
                        TextButton(onClick = { details = !details }, modifier = Modifier.testTag("download-details-${record.id}")) { Text(if (details) "Hide details" else "Details") }
                        OverflowMenu("Actions for download ${video.title}", record.id, Modifier.size(48.dp).browseGlass(CircleShape).testTag("download-actions-${record.id}")) { close ->
                            DropdownMenuItem(text = { Text("Save to files") }, enabled = record.playable && !export.busy, modifier = Modifier.testTag("download-save-${record.id}"), onClick = { close(); exporting = record.id })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { close(); deleting = record.id })
                        }
                    }
                }
                if (details) record.assets.filterNot { it.artwork }.forEach { asset -> Text(choiceLabel(asset.choice), style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
        }
    }
    records.firstOrNull { it.id == deleting }?.let { record ->
        LibraryConfirmation("Delete download?", { deleting = null }, { vm.deleteDownload(record.id); deleting = null }) {
            Text("Remove ${record.details.video.title} and its saved files from this device?")
        }
    }
    records.firstOrNull { it.id == exporting }?.let { record ->
        LibraryPanel("Save to files", { exporting = null }) { modifier, top ->
            Column(modifier.verticalScroll(rememberScrollState()).padding(start = Liquid.inset, end = Liquid.inset, top = top, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(record.details.video.title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { save(record, null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (record.assets.count { it.media } == 2) "Combined video and audio" else "Media file") }
                record.assets.filter { it.choice.kind == DownloadKind.CAPTION && it.status == DownloadStatus.COMPLETE }.forEach { asset -> TextButton(onClick = { save(record, asset) }) { Text("Caption: ${asset.choice.label}") } }
                TextButton(onClick = { exporting = null }) { Text("Cancel") }
            }
        }
    }
}

@Composable
internal fun DownloadExportConsent() {
    val state by DownloadExportService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (state.phase == "Needs conversion") LibraryConfirmation("Convert before export?", { DownloadExportService.cancel(context) },
        { DownloadExportService.start(context, state.id, state.asset, Uri.parse(state.destination), true) },
        confirmLabel = "Convert and export", confirmTag = "download-convert-confirm", destructive = false, dismissTag = "download-convert-cancel") {
        Text("These tracks could not be merged unchanged into MP4. Necessary tracks will be converted to H.264 video or AAC audio. This may reduce quality and take longer. The original downloaded files stay intact. Resolution and frame rate will be preserved, or the export will report an error.")
    }
}
