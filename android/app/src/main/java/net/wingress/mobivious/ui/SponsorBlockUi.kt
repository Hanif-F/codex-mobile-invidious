package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*

internal fun sponsorColor(value: String): Color = Color(value.toColorInt())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SponsorBlockSheet(vm: AppViewModel, initialChannel: String, signIn: () -> Unit, dismiss: () -> Unit, fullScreen: Boolean = false) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val context = remember { vm.api.context() }
    var page by remember { mutableStateOf(if (initialChannel.isBlank()) "Global SponsorBlock settings" else "Channel SponsorBlock settings") }
    var channelId by remember { mutableStateOf(initialChannel) }
    var input by remember { mutableStateOf("") }
    var before by remember { mutableStateOf(prefs) }
    var global by remember { mutableStateOf(prefs.sponsorBlock) }
    var channel by remember { mutableStateOf(SponsorBlockChannel(initialChannel)) }
    var edited by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .9f }
    LaunchedEffect(prefs, page, channelId) {
        if (!edited) { before = prefs; global = prefs.sponsorBlock; channel = prefs.sponsorBlock.channels[channelId] ?: SponsorBlockChannel(channelId) }
    }
    LaunchedEffect(channelId, page, account) {
        if (page == "Channel SponsorBlock settings" && channelId.isNotBlank() && channelId !in prefs.sponsorBlock.channels) {
            loading = true; error = null
            try { val found = vm.api.channel(channelId); if (vm.api.context() == context) channel = channel.copy(name = found.name.ifBlank { channelId }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not load this channel. Please try again." }
            finally { loading = false }
        }
    }
    fun navigate(value: String, id: String = "") { page = value; channelId = id; edited = false; error = null }
    fun back() {
        if (busy) return
        when (page) {
            "Configured channels" -> navigate("Global SponsorBlock settings")
            "Channel SponsorBlock settings" -> navigate("Configured channels")
            else -> dismiss()
        }
    }
    fun save(reset: Boolean = false) {
        busy = true; error = null
        val baseline = before
        val value = if (page == "Global SponsorBlock settings") baseline.copy(sponsorBlock = global)
        else baseline.copy(sponsorBlock = baseline.sponsorBlock.copy(channels = baseline.sponsorBlock.channels.toMutableMap().apply {
            if (reset || channel.enabled == null && channel.modes.isEmpty()) remove(channelId) else put(channelId, channel)
        }))
        scope.launch {
            try { vm.savePreferences(value, baseline, context); edited = false; if (page == "Global SponsorBlock settings") dismiss() else navigate("Configured channels") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not save SponsorBlock settings." }
            finally { busy = false }
        }
    }
    val content: @Composable () -> Unit = {
        BackHandler { back() }
        Column((if (fullScreen) Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.fillMaxWidth().heightIn(max = height)).imePadding().testTag("sponsorblock-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (fullScreen || page != "Global SponsorBlock settings") IconButton(onClick = ::back, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to SponsorBlock settings") }
                Text(page, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (!fullScreen) IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Default.Close, "Close SponsorBlock settings") }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f, fill = fullScreen).fillMaxWidth().testTag("sponsorblock-settings-list"), contentPadding = PaddingValues(16.dp)) {
                when (page) {
                    "Global SponsorBlock settings" -> {
                        item {
                            Text(if (account != null) "These settings are shared with the website." else "Global settings are saved on this device for this instance.", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable SponsorBlock", Modifier.weight(1f)); Switch(global.enabled, { global = global.copy(enabled = it); edited = true }, enabled = !busy) }
                            Text("Choose how to handle community-submitted segments. Auto and manual modes also show timeline markers. Disabled categories are hidden.", style = MaterialTheme.typography.bodySmall)
                            ActionRow("Channel SponsorBlock settings", enabled = !busy) { navigate("Configured channels") }
                        }
                        items(SponsorBlockCategory.entries) { category ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                val color = global.colors[category] ?: category.color
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(20.dp).clip(CircleShape).background(sponsorColor(color.takeIf(SponsorBlockRules::validColor) ?: category.color)))
                                    Text(category.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                                }
                                DropdownChoiceRow("Skip option", SponsorBlockMode.entries.map { it.wire to it.label }, global.modes[category]?.wire ?: "manual", enabled = !busy) {
                                    global = global.copy(modes = global.modes + (category to SponsorBlockMode.parse(it)!!)); edited = true
                                }
                                OutlinedTextField(color, { global = global.copy(colors = global.colors + (category to it)); edited = true }, label = { Text("${category.label} color (#RRGGBB)") },
                                    singleLine = true, enabled = !busy, isError = !SponsorBlockRules.validColor(color), modifier = Modifier.fillMaxWidth())
                                OutlinedButton(onClick = { global = global.copy(colors = global.colors + (category to category.color)); edited = true }, enabled = !busy) { Text("Restore default color") }
                            }
                        }
                        item {
                            TextButton(onClick = { uri.openUri("https://sponsor.ajay.app/") }) { Text("Segment data: SponsorBlock") }
                            TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by-nc-sa/4.0/") }) { Text("CC BY-NC-SA 4.0") }
                        }
                    }
                    "Configured channels" -> {
                        run {
                            item {
                                Text("Channel settings override global settings. Use global follows future global changes. Colors remain global.")
                                OutlinedTextField(input, { input = it }, label = { Text("Channel ID or /channel/UC… URL") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                Button(enabled = !busy, onClick = { val id = SponsorBlockRules.channelId(input); if (id == null) error = "Enter a valid channel ID or /channel/UC… URL." else navigate("Channel SponsorBlock settings", id) }) { Text("Edit channel settings") }
                                if (prefs.sponsorBlock.channels.isEmpty()) Text("No channel overrides yet.")
                            }
                            items(prefs.sponsorBlock.channels.toList().sortedBy { it.second.name.lowercase() }, key = { it.first }) { (id, saved) -> ActionRow(saved.name, modifier = Modifier.testTag("sponsor-configured-channel-$id"), detail = "Channel SponsorBlock settings", enabled = !busy) { navigate("Channel SponsorBlock settings", id) } }
                        }
                    }
                    "Channel SponsorBlock settings" -> {
                        run {
                            item {
                                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                DropdownChoiceRow("SponsorBlock", listOf("inherit" to "Use global (${if (prefs.sponsorBlock.enabled) "Enabled" else "Disabled"})", "true" to "Enabled", "false" to "Disabled"),
                                    channel.enabled?.toString() ?: "inherit", enabled = !busy && !loading, modifier = Modifier.testTag("sponsor-channel-enabled")) { channel = channel.copy(enabled = it.toBooleanStrictOrNull()); edited = true }
                                Text("Category colors remain global.", style = MaterialTheme.typography.bodySmall)
                            }
                            items(SponsorBlockCategory.entries) { category ->
                                Text(category.label, style = MaterialTheme.typography.titleMedium)
                                DropdownChoiceRow("Skip option", listOf("inherit" to "Use global (${prefs.sponsorBlock.modes[category]?.label})") + SponsorBlockMode.entries.map { it.wire to it.label },
                                    channel.modes[category]?.wire ?: "inherit", enabled = !busy && !loading) {
                                    channel = channel.copy(modes = channel.modes.toMutableMap().apply { SponsorBlockMode.parse(it)?.let { mode -> put(category, mode) } ?: remove(category) }); edited = true
                                }
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("sponsorblock-save-error")) }
            if (page != "Configured channels") {
                HorizontalDivider()
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (page == "Channel SponsorBlock settings") OutlinedButton(onClick = { save(true) }, enabled = !busy && !loading) { Text("Reset to global") }
                    Button(onClick = { save() }, enabled = !busy && !loading && (page != "Global SponsorBlock settings" || global.colors.values.all(SponsorBlockRules::validColor))) { Text(if (busy) "Saving…" else "Save") }
                }
            }
        }
    }
    if (fullScreen) Surface { content() }
    else ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetMaxWidth = 640.dp) { content() }
}
