package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.CancellationException
import net.wingress.mobivious.data.*

internal fun sponsorColor(value: String): Color = Color(value.toColorInt())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SponsorBlockSheet(vm: AppViewModel, initialChannel: String, dismiss: () -> Unit, fullScreen: Boolean = false) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = remember { vm.api.context() }
    var page by remember { mutableStateOf(if (initialChannel.isBlank()) "Global SponsorBlock settings" else "Channel SponsorBlock settings") }
    var channelId by remember { mutableStateOf(initialChannel) }
    var input by remember { mutableStateOf("") }
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val requested=remember(prefs,edits) {vm.preferenceValues()}
    val global=requested.sponsorBlock
    var channelName by remember(channelId,context) {mutableStateOf(channelId)}
    val channel=global.channels[channelId] ?: SponsorBlockChannel(channelName)
    var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    fun updateGlobal(next: SponsorBlockSettings, debounce: Boolean=false) {
        vm.requestPreferences(requested.copy(sponsorBlock=next),requested,"SponsorBlock",context,debounce)
    }
    fun updateChannel(next: SponsorBlockChannel) {
        updateGlobal(global.copy(channels=global.channels.toMutableMap().apply {
            if(next.enabled==null && next.modes.isEmpty()) remove(channelId) else put(channelId,next)
        }))
    }
    DisposableEffect(context) {onDispose {vm.leavePreferencePage("SponsorBlock")}}
    val uri = LocalUriHandler.current
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .9f }
    LaunchedEffect(channelId, page, account) {
        if (page == "Channel SponsorBlock settings" && channelId.isNotBlank() && channelId !in prefs.sponsorBlock.channels) {
            loading = true; error = null
            try { val found = vm.api.channel(channelId); if (vm.api.context() == context) channelName = found.name.ifBlank { channelId } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this channel. Please try again." }
            finally { loading = false }
        }
    }
    fun navigate(value: String, id: String = "") { page = value; channelId = id; error = null }
    fun back() {
        when (page) {
            "Configured channels" -> navigate("Global SponsorBlock settings")
            "Channel SponsorBlock settings" -> navigate("Configured channels")
            else -> dismiss()
        }
    }
    val content: @Composable () -> Unit = {
        BackHandler { back() }
        BrowseAppSurface((if (fullScreen) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.fillMaxWidth().height(height))
            .imePadding().testTag("sponsorblock-sheet"), toolbar = {
            BrowsePanelToolbar(page, "Close SponsorBlock settings", dismiss,
                back = if (fullScreen || page != "Global SponsorBlock settings") ::back else null)
        }) { contentModifier, topInset ->
        Column(contentModifier) {
            LazyColumn(Modifier.weight(1f, fill = fullScreen).fillMaxWidth().testTag("sponsorblock-settings-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset, bottom = 16.dp)) {
                when (page) {
                    "Global SponsorBlock settings" -> {
                        item { AppSection {
                            Text(if (account != null) "These settings are shared with the website." else "Global settings are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable SponsorBlock", Modifier.weight(1f)); Switch(global.enabled, { updateGlobal(global.copy(enabled=it)) }) }
                            Text("Choose how to handle community-submitted segments. Auto and manual modes also show timeline markers. Disabled categories are hidden.", style = MaterialTheme.typography.bodySmall)
                            ActionRow("Channel SponsorBlock settings") { navigate("Configured channels") }
                        } }
                        items(SponsorBlockCategory.entries) { category ->
                            AppSection(Modifier.padding(vertical = 8.dp)) {
                                val colorKey="sponsorblock_colors.${category.wire}"
                                val colorEdit=edits[colorKey]
                                val color=colorEdit?.let {org.json.JSONObject(it.requested).getJSONObject("sponsorblock_colors").getString(category.wire)} ?: global.colors[category] ?: category.color
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(sponsorColor(color.takeIf(SponsorBlockRules::validColor) ?: category.color)))
                                    Text(category.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                                }
                                DropdownChoiceRow("Skip option", SponsorBlockMode.entries.map { it.wire to it.label }, global.modes[category]?.wire ?: "manual") {
                                    updateGlobal(global.copy(modes=global.modes+(category to SponsorBlockMode.parse(it)!!)))
                                }
                                OutlinedTextField(color, { updateGlobal(global.copy(colors=global.colors+(category to it)),debounce=true) }, label = { Text("${category.label} color (#RRGGBB)") },
                                    singleLine=true,isError=!SponsorBlockRules.validColor(color),
                                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={vm.preferenceSaves.flush(colorKey)}),
                                    modifier=Modifier.fillMaxWidth().onFocusChanged {if(!it.isFocused) vm.preferenceSaves.flush(colorKey)})
                                PreferenceSaveFeedback(colorEdit?.status,colorEdit?.error,{vm.preferenceSaves.retry(colorKey)},{vm.preferenceSaves.revert(colorKey)})
                                TextButton(onClick = { updateGlobal(global.copy(colors=global.colors+(category to category.color))) }) { Text("Restore default color") }
                            }
                        }
                        item {
                            TextButton(onClick = { uri.openUri("https://sponsor.ajay.app/") }) { Text("Segment data: SponsorBlock") }
                            TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-nc-sa/4.0/") }) { Text("CC BY-NC-SA 4.0") }
                        }
                    }
                    "Configured channels" -> {
                        run {
                            item { AppSection {
                                Text("Channel settings override global settings. Use global follows future global changes. Colors remain global.")
                                OutlinedTextField(input, { input = it }, label = { Text("Channel ID or /channel/UC… URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                LibraryActionButton("Edit channel settings", onClick = { val id = SponsorBlockRules.channelId(input); if (id == null) error = "Enter a valid channel ID or /channel/UC… URL." else navigate("Channel SponsorBlock settings", id) })
                                if (prefs.sponsorBlock.channels.isEmpty()) Text("No channel overrides yet.")
                            } }
                            items(prefs.sponsorBlock.channels.toList().sortedBy { it.second.name.lowercase() }, key = { it.first }) { (id, saved) -> ActionRow(saved.name, modifier = Modifier.testTag("sponsor-configured-channel-$id"), detail = "Channel SponsorBlock settings") { navigate("Channel SponsorBlock settings", id) } }
                        }
                    }
                    "Channel SponsorBlock settings" -> {
                        run {
                            item { AppSection {
                                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                DropdownChoiceRow("SponsorBlock", listOf("inherit" to "Use global (${if (prefs.sponsorBlock.enabled) "Enabled" else "Disabled"})", "true" to "Enabled", "false" to "Disabled"),
                                    channel.enabled?.toString() ?: "inherit", enabled = !loading, modifier = Modifier.testTag("sponsor-channel-enabled")) { updateChannel(channel.copy(enabled=it.toBooleanStrictOrNull())) }
                                Text("Category colors remain global.",style=MaterialTheme.typography.bodySmall)
                                LibraryActionButton("Reset to global",enabled=!loading) {updateChannel(SponsorBlockChannel(channel.name))}
                            } }
                            items(SponsorBlockCategory.entries) { category -> AppSection {
                                Text(category.label, style = MaterialTheme.typography.titleMedium)
                                DropdownChoiceRow("Skip option", listOf("inherit" to "Use global (${prefs.sponsorBlock.modes[category]?.label})") + SponsorBlockMode.entries.map { it.wire to it.label },
                                    channel.modes[category]?.wire ?: "inherit", enabled = !loading) {
                                    updateChannel(channel.copy(modes=channel.modes.toMutableMap().apply { SponsorBlockMode.parse(it)?.let { mode -> put(category,mode) } ?: remove(category) }))
                                }
                            } }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("sponsorblock-save-error")) }
            edits.values.filter {it.page=="SponsorBlock" && !it.key.startsWith("sponsorblock_colors.")}.forEach { edit ->
                PreferenceSaveFeedback(edit.status,edit.error,{vm.preferenceSaves.retry(edit.key)},{vm.preferenceSaves.revert(edit.key)})
            }

        }
        }
    }
    if (fullScreen) Surface { content() }
    else ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) { content() }
}
