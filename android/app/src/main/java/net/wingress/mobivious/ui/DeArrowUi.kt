package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*

@Composable
internal fun DeArrowTitle(vm: AppViewModel, video: Video, style: TextStyle, modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, fontWeight: FontWeight? = null) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val titles by vm.dearrowTitles.titles.collectAsStateWithLifecycle()
    LaunchedEffect(video.id, prefs.dearrowEnabled, video.id in titles) { vm.ensureDeArrow(video.id) }
    val replacement = if (prefs.dearrowEnabled) titles[video.id]?.takeIf { it != video.title } else null
    var original by remember(video.id, vm.store.server, replacement, prefs.dearrowShowOriginal) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (original) video.title else replacement ?: video.title, modifier.weight(1f), style = style,
            maxLines = maxLines, fontWeight = fontWeight, overflow = TextOverflow.Ellipsis)
        if (replacement != null && prefs.dearrowShowOriginal) IconButton(onClick = { original = !original },
            modifier = Modifier.semantics { stateDescription = if (original) "Original title" else "DeArrow title" }) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, if (original) "Show DeArrow title" else "Show original title", tint = Color(0xFF2878D0))
        }
    }
}

@Composable
internal fun DeArrowIdentitySettings(vm: AppViewModel) {
    val identity by vm.dearrowIdentity.collectAsStateWithLifecycle()
    val status by vm.dearrowIdentityError.collectAsStateWithLifecycle()
    var privateId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text("Contribution identity", style = MaterialTheme.typography.titleSmall)
    if (status != null) Text(status!!, color = MaterialTheme.colorScheme.error)
    else if (identity == null) Text("Loading contribution settings…")
    else if (identity?.ready != true) Text("The instance administrator must configure DeArrow contribution storage.")
    else {
        Text(if (identity?.configured == true) "A private contribution identity is saved for this account." else "An identity will be created on your first contribution.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(privateId, { privateId = it; error = null }, label = { Text("DeArrow private user ID") }, singleLine = true,
            enabled = !busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("dearrow-private-id"))
        Text("Optional: import your existing private ID to share its contribution identity. This is not a public ID or license key. Leave blank to keep it. Your instance stores it encrypted.", style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        TextButton(enabled = !busy && DeArrowRules.validPrivateId(privateId), onClick = {
            val context = vm.api.context(); val submitted = privateId; busy = true; error = null
            scope.launch {
                try { vm.importDeArrowIdentity(submitted, context); privateId = "" }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Could not save the DeArrow identity." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Importing…" else "Import private user ID") }
    }
}

private val guidelines = listOf(
    "My title uses sentence capitalization." to "Capitalize the first word, proper nouns and acronyms.",
    "My title does not merely answer the original title’s question." to "Watch the video and describe its subject or story.",
    "My title avoids unnecessary spoilers and conclusions." to "Help viewers decide whether to watch without replacing the experience.",
    "My title does not fact-check, mock or critique the video or creator." to "Describe the video from its own perspective. This is not a comment section."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeArrowContributionSheet(vm: AppViewModel) {
    val state by vm.dearrowContribution.collectAsStateWithLifecycle()
    val identity by vm.dearrowIdentity.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val ready = identity?.ready == true && !state.busy
    ModalBottomSheet(onDismissRequest = vm::closeDeArrow, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Suggest / vote on titles", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = vm::closeDeArrow) { Icon(Icons.Default.Close, "Close DeArrow contributions") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).testTag("dearrow-contributions-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.status?.let { text -> item { Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
                if (state.review) {
                    item { Text(state.draft.trim(), style = MaterialTheme.typography.titleMedium) }
                    items(guidelines.indices.toList()) { index ->
                        val (title, detail) = guidelines[index]
                        Row(Modifier.fillMaxWidth().testTag("dearrow-guideline-$index").toggleable(
                            value = index in state.acknowledgements, enabled = ready, role = Role.Checkbox,
                            onValueChange = { vm.acknowledgeDeArrow(index, it) }).semantics(mergeDescendants = true) { contentDescription = title },
                            verticalAlignment = Alignment.Top) {
                            Checkbox(index in state.acknowledgements, onCheckedChange = null, enabled = ready)
                            Column(Modifier.weight(1f).padding(top = 12.dp)) { Text(title); Text(detail, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    item {
                        Text("Check all four guidelines to submit.")
                        Button(enabled = ready && state.acknowledgements.size == 4, onClick = { vm.contributeDeArrow("submit") }, modifier = Modifier.fillMaxWidth()) { Text("Submit title") }
                        TextButton(enabled = !state.busy, onClick = { vm.reviewDeArrow(false) }) { Text("Back to editing") }
                    }
                } else {
                    item {
                        Text("Original title", style = MaterialTheme.typography.titleMedium)
                        val original = state.titles.firstOrNull { it.original }
                        DeArrowVoteRow(vm, playback.details?.video?.title.orEmpty(), original, true, ready && state.loaded)
                    }
                    item {
                        OutlinedTextField(state.draft, vm::editDeArrowDraft, label = { Text("Your suggested title") }, enabled = !state.busy, singleLine = true,
                            isError = state.draft.isNotBlank() && !DeArrowRules.validTitle(state.draft), supportingText = { Text("1–110 characters") },
                            modifier = Modifier.fillMaxWidth().testTag("dearrow-draft"))
                        Button(enabled = ready && DeArrowRules.validTitle(state.draft), onClick = { vm.reviewDeArrow(true) }) { Text("Review title") }
                    }
                    item { Text("Community titles", style = MaterialTheme.typography.titleMedium) }
                    val proposals = state.titles.filter { !it.original }
                    if (state.loaded && proposals.isEmpty()) item { Text("No community titles yet. Suggest the first one.") }
                    items(proposals, key = { it.uuid }) { item -> DeArrowVoteRow(vm, item.title.replace(">", ""), item, false, ready && state.loaded) }
                    item { TextButton(enabled = !state.busy, onClick = vm::refreshDeArrow) { Text("Refresh submissions") } }
                }
                item {
                    TextButton(onClick = { uri.openUri("https://wiki.sponsor.ajay.app/w/DeArrow/Guidelines") }) { Text("DeArrow guidelines") }
                    Text("Your titles and votes are public contributions sent to DeArrow when you submit. No license key is required.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DeArrowVoteRow(vm: AppViewModel, title: String, item: DeArrowSubmission?, original: Boolean, enabled: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = enabled, onClick = { vm.contributeDeArrow("upvote", item, original) }) { Icon(Icons.Default.ThumbUp, "Upvote: $title") }
            IconButton(enabled = enabled && DeArrowRules.canDownvote(item), onClick = { vm.contributeDeArrow("downvote", item, original) }) { Icon(Icons.Default.ThumbDown, "Downvote: $title") }
            if (item != null) Text("${item.votes} votes", style = MaterialTheme.typography.labelMedium)
        }
        if (item?.locked == true) Text("This title is locked.", style = MaterialTheme.typography.bodySmall)
        else if (original && item == null) Text("The original title has no submission to downvote yet.", style = MaterialTheme.typography.bodySmall)
    }
}
