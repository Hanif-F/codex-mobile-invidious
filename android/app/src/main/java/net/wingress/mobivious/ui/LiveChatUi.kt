package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*
import kotlin.math.roundToInt

@Composable internal fun ChatReplayEntry(state: ChatReplayState, open: () -> Unit) {
    if (state.available) ActionRow("Chat replay", modifier = Modifier.testTag("chat-entry"), trailingIcon = Icons.AutoMirrored.Filled.Chat, onClick = open)
}

@Composable internal fun ChatReplayPanel(vm: AppViewModel, modifier: Modifier, settings: () -> Unit, overlay: Boolean = false) {
    val state by vm.chatReplay.collectAsStateWithLifecycle()
    val appearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val list = key(state.context, state.occurrence) { rememberLazyListState(state.position.index, state.position.offset) }
    val current by rememberUpdatedState(state)
    LaunchedEffect(list, state.occurrence) {
        snapshotFlow { Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().collect { (scrolling, index, offset) ->
                if (scrolling) vm.chatPosition(CommentPosition(index, offset), !list.canScrollForward, current.occurrence, current.context)
            }
    }
    LaunchedEffect(state.windowRevision, state.messages.lastOrNull()?.id, state.following) {
        if (state.messages.isNotEmpty()) {
            if (state.following) list.scrollToItem(state.messages.lastIndex)
            else if (state.windowRevision > 0 && state.position.index >= state.messages.lastIndex) list.scrollToItem(state.messages.lastIndex)
        }
    }
    DisposableEffect(list) {
        onDispose { vm.chatPosition(CommentPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset), current.following, state.occurrence, state.context) }
    }
    val background = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (overlay) appearance.opacity / 100f else 1f)
    Column(modifier.background(background, RoundedCornerShape(12.dp)).testTag("chat-panel")) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chat replay", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = settings, modifier = Modifier.size(48.dp).testTag("chat-settings")) { Icon(Icons.Default.Settings, "Chat settings") }
            IconButton(onClick = vm::closeChat, modifier = Modifier.size(48.dp).testTag("chat-close")) { Icon(Icons.Default.Close, "Close chat replay") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-list"), state = list, contentPadding = PaddingValues(8.dp)) {
            if (!state.following) item(key = "older") { TextButton(onClick = vm::olderChat) { Text("Earlier cached messages") } }
            items(state.messages, key = ChatMessage::id) { message ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag("chat-message-${message.id}")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (prefs.chat.timestamps) Text(playerTime(message.offsetMs), fontSize = (11 * appearance.fontScale / 100f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(message.author.ifBlank { "Viewer" }, fontSize = (13 * appearance.fontScale / 100f).sp, color = MaterialTheme.colorScheme.primary)
                    }
                    if (!appearance.hideUserIds && message.authorChannelId.isNotBlank()) Text(message.authorChannelId,
                        fontSize = (10 * appearance.fontScale / 100f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.kind != "text" || message.amount.isNotBlank()) Text(listOf(
                        when (message.kind) { "paid", "paid_sticker" -> "Paid message"; "membership" -> "Membership"; else -> "" },
                        message.amount).filter(String::isNotBlank).joinToString(" · "), fontSize = (11 * appearance.fontScale / 100f).sp)
                    Text(message.text, fontSize = (14 * appearance.fontScale / 100f).sp)
                }
            }
        }
        if (state.timingLoading || state.loading && state.messages.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("chat-loading"))
        if (state.unavailable) Text("Chat replay is unavailable for this video.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        else if (state.error != null) Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.error!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::retryChat, modifier = Modifier.testTag("chat-retry")) { Text("Retry") }
        } else if (state.loaded && state.messages.isEmpty()) Text("No messages at this time.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        if (state.timingError != null) Text("Timing could not be synced. Open chat settings to retry.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        if (!state.following) TextButton(onClick = vm::followChat, modifier = Modifier.fillMaxWidth().testTag("chat-follow")) { Text("Return to playback") }
    }
}

@Composable internal fun ChatOverlay(vm: AppViewModel, modifier: Modifier, settings: () -> Unit) {
    val appearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val state by vm.chatReplay.collectAsStateWithLifecycle()
    var editing by rememberSaveable(state.context?.server, state.occurrence) { mutableStateOf(false) }
    var draft by remember(appearance, editing) { mutableStateOf(appearance) }
    val currentDraft by rememberUpdatedState(draft)
    BackHandler(editing) { editing = false }
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val minW = with(LocalDensity.current) { 160.dp.toPx() }.coerceAtMost(widthPx)
        val minH = with(LocalDensity.current) { 144.dp.toPx() }.coerceAtMost(heightPx)
        fun clamp(value: ChatAppearance): ChatAppearance = value.copy(width = value.width.coerceAtLeast(minW / widthPx), height = value.height.coerceAtLeast(minH / heightPx)).bounded()
        val box = clamp(if (editing) draft else appearance)
        val geometry = Modifier.offset { IntOffset((box.x * widthPx).roundToInt(), (box.y * heightPx).roundToInt()) }
            .size(maxWidth * box.width, maxHeight * box.height)
        Box(geometry.testTag("chat-overlay")) {
            ChatReplayPanel(vm, Modifier.fillMaxSize(), settings, overlay = true)
            if (editing) {
                // Dedicated handles consume their drags before player seeking/minimization can claim them.
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .9f)), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("Move", Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chat-overlay-move")
                        .semantics {
                            contentDescription = "Move chat overlay"
                            customActions = listOf(
                                CustomAccessibilityAction("Move left") { draft = clamp(draft.copy(x = draft.x - .02f)); true },
                                CustomAccessibilityAction("Move right") { draft = clamp(draft.copy(x = draft.x + .02f)); true },
                                CustomAccessibilityAction("Move up") { draft = clamp(draft.copy(y = draft.y - .02f)); true },
                                CustomAccessibilityAction("Move down") { draft = clamp(draft.copy(y = draft.y + .02f)); true })
                        }
                        .pointerInput(widthPx, heightPx) { detectDragGestures { change, delta ->
                            change.consume(); val old = currentDraft
                            draft = clamp(old.copy(x = old.x + delta.x / widthPx, y = old.y + delta.y / heightPx))
                        } }.padding(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = { vm.setChatAppearance(clamp(draft)); editing = false }, modifier = Modifier.testTag("chat-overlay-save")) { Text("Save") }
                        TextButton(onClick = { editing = false }, modifier = Modifier.testTag("chat-overlay-cancel")) { Text("Cancel") }
                    }
                    Text("Resize", Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("chat-overlay-resize")
                        .semantics {
                            contentDescription = "Resize chat overlay"
                            customActions = listOf(
                                CustomAccessibilityAction("Wider") { draft = clamp(draft.copy(width = draft.width + .02f)); true },
                                CustomAccessibilityAction("Narrower") { draft = clamp(draft.copy(width = draft.width - .02f)); true },
                                CustomAccessibilityAction("Taller") { draft = clamp(draft.copy(height = draft.height + .02f)); true },
                                CustomAccessibilityAction("Shorter") { draft = clamp(draft.copy(height = draft.height - .02f)); true })
                        }
                        .pointerInput(widthPx, heightPx) { detectDragGestures { change, delta ->
                            change.consume(); val old = currentDraft
                            draft = clamp(old.copy(width = old.width + delta.x / widthPx, height = old.height + delta.y / heightPx))
                        } }.padding(12.dp))
                }
            }
        }
        if (!editing) TextButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.TopStart).testTag("chat-overlay-edit")) { Text("Move / resize chat") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ChatSettings(vm: AppViewModel, dismiss: () -> Unit) {
    val state by vm.chatReplay.collectAsStateWithLifecycle()
    val appearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val ctx = vm.api.context()
    val scope = rememberCoroutineScope()
    var before by remember(state.occurrence, ctx) { mutableStateOf(prefs.chat) }
    var timestamps by rememberSaveable(state.occurrence, ctx) { mutableStateOf(prefs.chat.timestamps) }
    var users by rememberSaveable(state.occurrence, ctx) { mutableStateOf(prefs.chat.users) }
    var words by rememberSaveable(state.occurrence, ctx) { mutableStateOf(prefs.chat.words) }
    var timing by rememberSaveable(state.occurrence, ctx) { mutableStateOf((state.timingOffsetMs / 1000.0).toString()) }
    var beforeTiming by rememberSaveable(state.occurrence, ctx) { mutableIntStateOf(state.timingOffsetMs) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.chat) {
        // A delayed account read updates untouched fields while keeping the user's draft.
        if (timestamps == before.timestamps) timestamps = prefs.chat.timestamps
        if (users == before.users) users = prefs.chat.users
        if (words == before.words) words = prefs.chat.words
        before = prefs.chat
    }
    LaunchedEffect(state.timingOffsetMs) {
        if (timing == (beforeTiming / 1000.0).toString()) timing = (state.timingOffsetMs / 1000.0).toString()
        beforeTiming = state.timingOffsetMs
    }
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .85f }
    fun save() {
        val offset = timing.toDoubleOrNull()?.takeIf { it.isFinite() && it in -3600.0..3600.0 }?.let { (it * 1000).roundToInt() }
        if (offset == null) { error = "Timing must be between −3600 and 3600 seconds."; return }
        val value = ChatPreferences(timestamps, users, words)
        try { ChatFilters.validateChanges(value, before) } catch (e: IllegalArgumentException) { error = e.message; return }
        saving = true; error = null
        scope.launch {
            try {
                vm.saveChatPreferences(value, before, ctx); before = value
                if (offset != state.timingOffsetMs || state.timingLoading) vm.chatTiming(offset)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Chat settings could not be saved." }
            finally { saving = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = height).testTag("chat-settings-sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chat settings", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss, enabled = !saving) { Icon(Icons.Default.Close, "Close chat settings") }
            }
            LazyColumn(Modifier.weight(1f, fill = false).testTag("chat-settings-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ChatSwitch("Overlay mode", appearance.overlay) { vm.setChatAppearance(appearance.copy(overlay = it)) } }
                item { ChatSwitch("Show timestamps", timestamps) { timestamps = it } }
                item { ChatSwitch("Hide user IDs", appearance.hideUserIds) { vm.setChatAppearance(appearance.copy(hideUserIds = it)) } }
                item { ChatSlider("Font size", appearance.fontScale.toFloat(), 25f..300f, "${appearance.fontScale}%") { vm.setChatAppearance(appearance.copy(fontScale = it.roundToInt())) } }
                item { ChatSlider("Dock below size", appearance.belowFraction, .3f.. .7f, "${(appearance.belowFraction * 100).roundToInt()}%") { vm.setChatAppearance(appearance.copy(belowFraction = it)) } }
                item { ChatSlider("Dock beside size", appearance.besideFraction, .3f.. .7f, "${(appearance.besideFraction * 100).roundToInt()}%") { vm.setChatAppearance(appearance.copy(besideFraction = it)) } }
                if (appearance.overlay) {
                    item { ChatSlider("Overlay opacity", appearance.opacity.toFloat(), 0f..100f, "${appearance.opacity}%") { vm.setChatAppearance(appearance.copy(opacity = it.roundToInt())) } }
                    item { Text("Overlay position and size", style = MaterialTheme.typography.titleSmall) }
                    item { ChatSlider("Horizontal position", appearance.x, 0f..(1f - appearance.width).coerceAtLeast(.001f)) { vm.setChatAppearance(appearance.copy(x = it)) } }
                    item { ChatSlider("Vertical position", appearance.y, 0f..(1f - appearance.height).coerceAtLeast(.001f)) { vm.setChatAppearance(appearance.copy(y = it)) } }
                    item { ChatSlider("Overlay width", appearance.width, .1f..1f) { vm.setChatAppearance(appearance.copy(width = it)) } }
                    item { ChatSlider("Overlay height", appearance.height, .1f..1f) { vm.setChatAppearance(appearance.copy(height = it)) } }
                }
                item { OutlinedTextField(users, { users = it }, label = { Text("Hide users: UC… @handle") }, modifier = Modifier.fillMaxWidth().testTag("chat-users")) }
                item { OutlinedTextField(words, { words = it }, label = { Text("Hide words: word /pattern/") }, modifier = Modifier.fillMaxWidth().testTag("chat-words")) }
                item { Text("Separate filters with spaces. Regex supports RE2 syntax; lookaround and backreferences are unsupported.", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(timing, { timing = it }, label = { Text("Timing offset (seconds)") }, supportingText = { Text("Positive delays chat; negative shows chat earlier.") }, modifier = Modifier.fillMaxWidth().testTag("chat-timing")) }
                state.filterWarning?.let { warning -> item { Text(warning, color = MaterialTheme.colorScheme.error) } }
                state.timingError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::retryChatTiming) { Text("Retry timing sync") } } }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item { Button(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("chat-settings-save")) { Text(if (saving || state.timingSaving) "Saving…" else if (error != null) "Retry save" else "Save filters and timing") } }
            }
        }
    }
}

@Composable private fun ChatSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change, modifier = Modifier.testTag("chat-$label"))
    }
}
@Composable private fun ChatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String = "${(value * 100).roundToInt()}%", change: (Float) -> Unit) {
    Column { Text("$label · $display"); Slider(value.coerceIn(range), change, valueRange = range, modifier = Modifier.testTag("chat-$label").semantics { contentDescription = label }) }
}
