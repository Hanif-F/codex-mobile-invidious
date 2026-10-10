package net.wingress.mobivious.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.wingress.mobivious.data.*
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable internal fun ChatReplayPanel(
    vm: AppViewModel, modifier: Modifier, settings: () -> Unit, overlay: Boolean = false,
    adjustOverlay: (() -> Unit)? = null, editorHeader: (@Composable () -> Unit)? = null,
    editorFooter: (@Composable () -> Unit)? = null,
) {
    val state by vm.chatReplay.collectAsStateWithLifecycle()
    val appearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val list = key(state.context, state.occurrence) { rememberLazyListState(state.position.index, state.position.offset) }
    val current by rememberUpdatedState(state)
    var positioning by remember(list) { mutableStateOf(false) }
    LaunchedEffect(list, state.occurrence) {
        snapshotFlow { if (positioning) null else Triple(list.isScrollInProgress, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().collect { position ->
                if (position != null) {
                    val (scrolling, index, offset) = position
                    if (scrolling) vm.chatPosition(CommentPosition(index, offset), !list.canScrollForward, current.occurrence, current.context)
                }
            }
    }
    LaunchedEffect(list, state.windowRevision, state.messages.lastOrNull()?.id, state.following) {
        if (state.messages.isNotEmpty() && (state.following || state.windowRevision > 0 && state.position.index >= state.messages.lastIndex)) {
            // Reflow and follow-playback positioning are not manual browsing, even for a row taller than the panel.
            positioning = true
            try { list.scrollToItem(state.messages.lastIndex) } finally { positioning = false }
        }
    }
    DisposableEffect(list) {
        // The old panel may leave composition before receiving a just-updated follow state.
        onDispose { vm.chatPosition(CommentPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset), vm.chatReplay.value.following, state.occurrence, state.context) }
    }
    val colors = MaterialTheme.colorScheme
    val background = colors.surfaceContainer.copy(alpha = if (overlay) appearance.opacity / 100f else 1f)
    val shape = RoundedCornerShape(12.dp)
    val content: @Composable ColumnScope.() -> Unit = {
        if (editorHeader != null) editorHeader() else ChatPanelHeader(settings, vm::closeChat, adjustOverlay, watchStyle = !overlay)
        if (overlay) HorizontalDivider(color = colors.outlineVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("chat-list"), state = list,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            if (!state.following) item(key = "older") { TextButton(onClick = vm::olderChat) { Text("Earlier messages") } }
            items(state.messages, key = ChatMessage::id) { message ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("chat-message-${message.id}")) {
                    val metadata = listOf(
                        when (message.kind) { "paid", "paid_sticker" -> "Paid message"; "membership" -> "Membership"; else -> "" },
                        message.amount).filter(String::isNotBlank).joinToString(" · ")
                    if (metadata.isNotBlank()) Text(metadata, fontSize = (11 * appearance.fontScale / 100f).sp,
                        color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    Text(buildAnnotatedString {
                        if (prefs.chat.timestamps) withStyle(SpanStyle(color = colors.onSurfaceVariant, fontSize = (11 * appearance.fontScale / 100f).sp)) {
                            append(playerTime(message.offsetMs)); append("  ")
                        }
                        withStyle(SpanStyle(color = colors.primary, fontSize = (13 * appearance.fontScale / 100f).sp, fontWeight = FontWeight.SemiBold)) {
                            append(message.author.ifBlank { "Viewer" }); append(": ")
                        }
                        append(message.text)
                    }, fontSize = (14 * appearance.fontScale / 100f).sp, color = colors.onSurface,
                        lineHeight = (19 * appearance.fontScale / 100f).sp)
                    if (!appearance.hideUserIds && message.authorChannelId.isNotBlank()) Text(message.authorChannelId,
                        fontSize = (10 * appearance.fontScale / 100f).sp, color = colors.onSurfaceVariant)
                }
            }
        }
        if (state.timingLoading || state.loading && state.messages.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("chat-loading"))
        if (state.unavailable) Text("Chat replay is unavailable for this video.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        else if (state.error != null) Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(state.error!!, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::retryChat, modifier = Modifier.testTag("chat-retry")) { Text("Retry") }
        } else if (state.loaded && state.messages.isEmpty()) Text("No messages at this time.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        if (state.timingError != null) Text("Chat delay could not be synced. Open chat settings to retry.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
        if (!state.following) TextButton(onClick = vm::followChat, modifier = Modifier.fillMaxWidth().testTag("chat-follow")) { Text("Follow playback") }
        editorFooter?.invoke()
    }
    if (overlay) Column(modifier.clip(shape).background(background).testTag("chat-panel"), content = content)
    else WatchPanelSurface(modifier.testTag("chat-panel"), content)
}

@Composable private fun ChatPanelHeader(settings: () -> Unit, close: () -> Unit, adjustOverlay: (() -> Unit)?, watchStyle: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (watchStyle) Modifier.padding(horizontal = 12.dp, vertical = 8.dp).watchGlass()
        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer))) {
        val compact = maxWidth < 200.dp
        Row(Modifier.fillMaxWidth().heightIn(min = if (watchStyle) 56.dp else 48.dp).padding(start = if (compact) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (compact) Arrangement.End else Arrangement.Start) {
            if (!compact) Text("Chat replay", Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("chat-menu")) {
                    Icon(Icons.Default.MoreVert, "Chat menu")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Chat settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                        onClick = { menu = false; settings() }, modifier = Modifier.testTag("chat-settings"))
                    if (adjustOverlay != null) DropdownMenuItem(text = { Text("Adjust overlay") },
                        leadingIcon = { Icon(Icons.Default.OpenInFull, null) }, onClick = { menu = false; adjustOverlay() },
                        modifier = Modifier.testTag("chat-overlay-edit"))
                    DropdownMenuItem(text = { Text("Close chat") }, leadingIcon = { Icon(Icons.Default.Close, null) },
                        onClick = { menu = false; close() }, modifier = Modifier.testTag("chat-menu-close"))
                }
            }
            if (!compact) IconButton(onClick = close, modifier = Modifier.size(48.dp).testTag("chat-close")) {
                Icon(Icons.Default.Close, "Close chat replay")
            }
        }
    }
}

/** Owned by the player host so hiding the surface during recreation retains the draft. */
internal class ChatOverlayEditor(appearance: ChatAppearance, adjusting: Boolean = false) {
    val editing = mutableStateOf(adjusting)
    val draft = mutableStateOf(appearance)
    companion object {
        val StateSaver = Saver<ChatOverlayEditor, String>(
            save = { it.draft.value.json().put("editing", it.editing.value).toString() },
            restore = { val json = JSONObject(it); ChatOverlayEditor(ChatAppearance.parse(json), json.optBoolean("editing")) })
    }
}

@Composable internal fun ChatOverlay(
    vm: AppViewModel, modifier: Modifier, settings: () -> Unit, editor: ChatOverlayEditor,
) {
    val appearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val editorContext = remember(editor) { vm.api.context() }
    var editing by editor.editing
    var draft by editor.draft
    val currentDraft by rememberUpdatedState(draft)
    BackHandler(editing) { editing = false }
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val minW = with(LocalDensity.current) { 160.dp.toPx() }.coerceAtMost(widthPx)
        val minH = with(LocalDensity.current) { 144.dp.toPx() }.coerceAtMost(heightPx)
        fun clamp(value: ChatAppearance): ChatAppearance = value.copy(
            width = value.width.coerceAtLeast(minW / widthPx), height = value.height.coerceAtLeast(minH / heightPx)).bounded()
        val box = clamp(if (editing) draft else appearance)
        val geometry = Modifier.offset { IntOffset((box.x * widthPx).roundToInt(), (box.y * heightPx).roundToInt()) }
            .size(maxWidth * box.width, maxHeight * box.height)
        val move = Modifier.testTag("chat-overlay-move").semantics {
            contentDescription = "Move chat overlay"
            customActions = listOf(
                CustomAccessibilityAction("Move left") { draft = clamp(draft.copy(x = draft.x - .02f)); true },
                CustomAccessibilityAction("Move right") { draft = clamp(draft.copy(x = draft.x + .02f)); true },
                CustomAccessibilityAction("Move up") { draft = clamp(draft.copy(y = draft.y - .02f)); true },
                CustomAccessibilityAction("Move down") { draft = clamp(draft.copy(y = draft.y + .02f)); true })
        }.pointerInput(widthPx, heightPx) {
            detectDragGestures { change, delta ->
                change.consume(); val old = clamp(currentDraft)
                draft = clamp(old.copy(x = old.x + delta.x / widthPx, y = old.y + delta.y / heightPx))
            }
        }
        val resize = Modifier.testTag("chat-overlay-resize").semantics {
            contentDescription = "Resize chat overlay"
            customActions = listOf(
                CustomAccessibilityAction("Wider") { draft = clamp(draft.copy(width = draft.width + .02f)); true },
                CustomAccessibilityAction("Narrower") { draft = clamp(draft.copy(width = draft.width - .02f)); true },
                CustomAccessibilityAction("Taller") { draft = clamp(draft.copy(height = draft.height + .02f)); true },
                CustomAccessibilityAction("Shorter") { draft = clamp(draft.copy(height = draft.height - .02f)); true })
        }.pointerInput(widthPx, heightPx) {
            detectDragGestures { change, delta ->
                change.consume(); val old = clamp(currentDraft)
                draft = clamp(old.copy(width = old.width + delta.x / widthPx, height = old.height + delta.y / heightPx))
            }
        }
        ChatReplayPanel(vm, geometry.testTag("chat-overlay").border(if (editing) 2.dp else 1.dp,
            if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            settings, overlay = true, adjustOverlay = { draft = clamp(appearance); editing = true },
            editorHeader = if (editing) ({
                BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    val narrow = maxWidth < 144.dp
                    @Composable fun controls() {
                        IconButton(onClick = { editing = false }, modifier = Modifier.size(48.dp).testTag("chat-overlay-cancel")) {
                            Icon(Icons.Default.Close, "Cancel overlay adjustment")
                        }
                        IconButton(onClick = { if (vm.setChatAppearance(clamp(draft), editorContext)) editing = false },
                            modifier = Modifier.size(48.dp).testTag("chat-overlay-save")) { Icon(Icons.Default.Check, "Done adjusting overlay") }
                    }
                    if (narrow) Column {
                        Box(move.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { controls() }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(move.weight(1f).height(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null) }
                        controls()
                    }
                }
            }) else null,
            editorFooter = if (editing) ({
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Box(resize.align(Alignment.BottomEnd).size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(topStart = 12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.OpenInFull, null) }
                }
            }) else null)
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
    val busy = saving || state.timingSaving
    fun save() {
        val offset = timing.toDoubleOrNull()?.takeIf { it.isFinite() && it in -3600.0..3600.0 }?.let { (it * 1000).roundToInt() }
        if (offset == null) { error = "Enter a chat delay between −3600 and 3600 seconds."; return }
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
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = height).testTag("chat-settings-sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chat settings", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close chat settings") }
            }
            LazyColumn(Modifier.weight(1f, fill = false).testTag("chat-settings-list"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Chat layout", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Docked", "Overlay").forEachIndexed { index, label ->
                            SegmentedButton(selected = appearance.overlay == (index == 1), onClick = { vm.setChatAppearance(appearance.copy(overlay = index == 1), ctx) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2), modifier = Modifier.heightIn(min = 48.dp).testTag("chat-layout-${label.lowercase()}")) { Text(label) }
                        }
                    }
                    Text("Appearance saves automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (appearance.overlay) {
                    item { ChatSlider("Background opacity", "chat-opacity", appearance.opacity.toFloat(), 0f..100f, "${appearance.opacity}%") {
                        vm.setChatAppearance(appearance.copy(opacity = it.roundToInt()), ctx) } }
                } else {
                    item { ChatSlider("Side panel width", "chat-side-width", appearance.besideFraction, ChatAppearance.BESIDE_FRACTION_RANGE) {
                        vm.setChatAppearance(appearance.copy(besideFraction = it), ctx) } }
                    item { ChatSlider("Bottom panel height", "chat-bottom-height", appearance.belowFraction, .3f.. .7f) {
                        vm.setChatAppearance(appearance.copy(belowFraction = it), ctx) } }
                }
                item { ChatSlider("Text size", "chat-text-size", appearance.fontScale.toFloat(), 25f..300f, "${appearance.fontScale}%") {
                    vm.setChatAppearance(appearance.copy(fontScale = it.roundToInt()), ctx) } }
                item { ChatSwitch("Show channel IDs", "chat-channel-ids", !appearance.hideUserIds) {
                    vm.setChatAppearance(appearance.copy(hideUserIds = !it), ctx) } }
                item { ChatSwitch("Show timestamps", "chat-timestamps", timestamps, enabled = !busy) { timestamps = it; error = null } }
                item { OutlinedTextField(users, { users = it; error = null }, label = { Text("Blocked users") },
                    supportingText = { Text("Channel IDs or @handles, separated by spaces.") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("chat-users")) }
                item { OutlinedTextField(words, { words = it; error = null }, label = { Text("Blocked words") },
                    supportingText = { Text("For example: spam /pattern/. Separate entries with spaces.") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("chat-words")) }
                item { OutlinedTextField(timing, { timing = it; error = null }, label = { Text("Chat delay (seconds)") }, singleLine = true,
                    supportingText = { Text("Positive values show messages later; negative values show them earlier.") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("chat-timing")) }
                state.filterWarning?.let { warning -> item { Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("chat-settings-footer")) {
                state.timingError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::retryChatTiming, enabled = !busy, modifier = Modifier.testTag("chat-timing-retry")) { Text("Retry delay sync") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("chat-settings-error")) }
                Text("Save timestamps, blocked users and words, and chat delay.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chat-settings-save")) {
                    Text(if (busy) "Saving…" else "Save")
                }
            }
        }
    }
}

@Composable private fun ChatSwitch(label: String, tag: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
@Composable private fun ChatSlider(label: String, tag: String, value: Float, range: ClosedFloatingPointRange<Float>,
    display: String = "${(value * 100).roundToInt()}%", change: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value.coerceIn(range), change, valueRange = range, modifier = Modifier.testTag(tag).semantics { contentDescription = label })
    }
}
