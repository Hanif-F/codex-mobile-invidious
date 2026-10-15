package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import net.wingress.mobivious.data.*
import org.json.JSONObject

internal const val channelSpoilerSettingsPage = "Channel title and thumbnail settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelSpoilerSettings(vm: AppViewModel, initialChannel: String, dismiss: () -> Unit, fullScreen: Boolean = false) {
    val context = remember { vm.api.context() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val confirmed by vm.preferences.collectAsStateWithLifecycle()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val state by vm.channelSpoilers.state.collectAsStateWithLifecycle()
    val loadError by vm.spoilerSettingsError.collectAsStateWithLifecycle()
    val requested = remember(confirmed, edits) { vm.preferenceValues() }
    var id by remember { mutableStateOf(initialChannel) }
    var input by remember { mutableStateOf("") }
    var name by remember(id) { mutableStateOf(id) }
    var lookupBusy by remember { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val editKey = "channel_spoiler_settings.$id"
    val edit = edits[editKey]
    val rule = requested.channelSpoilers[id] ?: ChannelSpoilerRule(name)
    val dayValue = edit?.let { JSONObject(it.requested).getJSONObject("channel_spoiler_settings").optJSONObject(id)?.opt("reveal_after_days") }
    val days = dayValue?.takeUnless { it == JSONObject.NULL }?.toString() ?: rule.revealAfterDays?.toString().orEmpty()
    LaunchedEffect(id) {
        if (id.isNotEmpty() && id !in confirmed.channelSpoilers) {
            lookupBusy = true
            try { val found = vm.api.channel(id); if (context == vm.api.context()) name = found.name.ifBlank { id } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this channel." }
            finally { lookupBusy = false }
        }
    }
    DisposableEffect(context) { onDispose { vm.leavePreferencePage(channelSpoilerSettingsPage) } }
    fun back() { if (id.isEmpty() || initialChannel.isNotEmpty()) dismiss() else { vm.flushPreferenceEdits(); id = "" } }
    val content: @Composable () -> Unit = {
        BackHandler { back() }
        PanelScaffold(Modifier.fillMaxWidth().then(if (fullScreen) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.fillMaxHeight())
            .imePadding().testTag("channel-spoiler-settings"), toolbar = {
            BrowsePanelToolbar(if (id.isEmpty()) channelSpoilerSettingsPage else "Channel settings", "Close channel title and thumbnail settings", dismiss,
                back = if (id.isNotEmpty()) ::back else null, backLabel = "Back to channel settings")
        }) { body, _ ->
            LazyColumn(body, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(if (context.account == null) "Saved on this device for this instance." else "Shared with your Invidious account on the website.") }
                if (!state.ready) item {
                    if (loadError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else { Text(loadError!!, color = MaterialTheme.colorScheme.error); TextButton(vm::refreshSharedSettings) { Text("Retry loading settings") } }
                }
                if (id.isEmpty()) {
                    item { AppSection {
                        Text("Configured channels", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(input, { input = it; error = null }, label = { Text("Channel ID or /channel/UC… URL") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("spoiler-channel-input"))
                        LibraryActionButton("Configure channel", enabled = state.ready && input.isNotBlank(), prominent = true) {
                            val channel = SponsorBlockRules.channelId(input)
                            if (channel == null) error = "Enter a valid channel ID or /channel/UC… URL." else { id = channel; input = "" }
                        }
                        if (requested.channelSpoilers.isEmpty()) Text("No channel rules yet.")
                    } }
                    items(requested.channelSpoilers.toList().sortedBy { it.second.name.lowercase() }, key = { it.first }) { (channel, saved) ->
                        ActionRow(saved.name, Modifier.testTag("spoiler-configured-channel-$channel"), detail = "Channel settings") { id = channel }
                    }
                } else {
                    item { AppSection {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium)
                        if (lookupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val enabled = state.ready && !lookupBusy && context == vm.api.context()
                        Row { Text("Hide title", Modifier.weight(1f)); Switch(rule.hideTitle, { vm.requestChannelSpoiler(id, rule.copy(hideTitle = it), days) }, enabled = enabled, modifier = Modifier.testTag("spoiler-hide-title")) }
                        Row { Text("Hide thumbnail", Modifier.weight(1f)); Switch(rule.hideThumbnail, { vm.requestChannelSpoiler(id, rule.copy(hideThumbnail = it), days) }, enabled = enabled, modifier = Modifier.testTag("spoiler-hide-thumbnail")) }
                        OutlinedTextField(days, { vm.requestChannelSpoiler(id, rule, it, debounce = true) },
                            label = { Text("Reveal after this many days since upload") }, supportingText = { Text("Leave blank to hide indefinitely. Unknown upload dates stay hidden.") },
                            enabled = enabled && rule.enabled, singleLine = true, isError = edit?.status == PreferenceSaveStatus.INVALID,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.preferenceSaves.flush(editKey); focus.clearFocus(); keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().testTag("spoiler-reveal-days").onFocusChanged { if (!it.isFocused) vm.preferenceSaves.flush(editKey) })
                        LibraryActionButton("Reset", enabled = enabled) { vm.requestChannelSpoiler(id, rule.copy(hideTitle = false, hideThumbnail = false), "") }
                    } }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                edits.values.filter { it.page == channelSpoilerSettingsPage }.forEach { saved -> item(key = saved.key) {
                    PreferenceSaveFeedback(saved.status, saved.error, { vm.preferenceSaves.retry(saved.key) }, { vm.preferenceSaves.revert(saved.key) })
                } }
            }
        }
    }
    if (fullScreen) content() else AppModalSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { content() }
}
