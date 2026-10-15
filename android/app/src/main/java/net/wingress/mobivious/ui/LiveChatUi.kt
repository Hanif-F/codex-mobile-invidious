package net.wingress.mobivious.ui

import kotlinx.coroutines.launch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val menuContent: @Composable RowScope.() -> Unit = {
        Box {
            AppInsetIconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("chat-menu")) {
                Icon(Icons.Default.MoreVert, "Chat menu")
            }
            AppDropdownMenu(expanded = menu, dismiss = { menu = false }) {
                DropdownMenuItem(text = { Text("Chat settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { menu = false; settings() }, modifier = Modifier.testTag("chat-settings"))
                if (adjustOverlay != null) DropdownMenuItem(text = { Text("Adjust overlay") },
                    leadingIcon = { Icon(Icons.Default.OpenInFull, null) }, onClick = { menu = false; adjustOverlay() },
                    modifier = Modifier.testTag("chat-overlay-edit"))
                DropdownMenuItem(text = { Text("Close chat") }, leadingIcon = { Icon(Icons.Default.Close, null) },
                    onClick = { menu = false; close() }, modifier = Modifier.testTag("chat-menu-close"))
            }
        }
    }
    if (watchStyle) {
        PanelHeader("Chat replay", "Close chat replay", close, closeTag = "chat-close", actions = menuContent)
    } else Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 200.dp
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = if (compact) 0.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact && adjustOverlay == null) Arrangement.End else Arrangement.Start) {
                if (!compact) Text("Chat replay", Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                menuContent()
                if (!compact) AppInsetIconButton(onClick = close, modifier = Modifier.size(48.dp).testTag("chat-close")) {
                    Icon(Icons.Default.Close, "Close chat replay")
                }
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
    var geometrySaving by remember(editor) { mutableStateOf(false) }
    var geometryError by remember(editor) { mutableStateOf<String?>(null) }
    val geometryScope = rememberCoroutineScope()
    val currentDraft by rememberUpdatedState(draft)
    BackHandler(editing && !geometrySaving) { editing = false }
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
            settings, overlay = true, adjustOverlay = { geometryError=null; draft = clamp(appearance); editing = true },
            editorHeader = if (editing) ({
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                geometryError?.let { Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error) }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val narrow = maxWidth < 144.dp
                    @Composable fun controls() {
                        AppInsetIconButton(onClick = { editing = false }, enabled = !geometrySaving, modifier = Modifier.size(48.dp).testTag("chat-overlay-cancel")) {
                            Icon(Icons.Default.Close, "Cancel overlay adjustment")
                        }
                        AppInsetIconButton(onClick = {
                            val submitted = clamp(draft); geometrySaving=true; geometryError=null
                            geometryScope.launch {
                                try { vm.saveChatGeometry(submitted,editorContext); editing=false }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { geometryError=e.message ?: "Could not save the overlay. Try Done again." }
                                finally { geometrySaving=false }
                            }
                        }, enabled = !geometrySaving, modifier = Modifier.size(48.dp).testTag("chat-overlay-save")) { Icon(Icons.Default.Check, "Done adjusting overlay") }
                    }
                    if (narrow) Column {
                        Box(move.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { controls() }
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(move.weight(1f).height(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null) }
                        controls()
                    }
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
    val savedAppearance by vm.chatAppearance.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val ctx = vm.api.context()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val appearance = remember(savedAppearance, edits) { vm.requestedChatAppearance() }
    val filterEdit=edits["chat_filters"]
    val timestampEdit=edits["chat_timestamps"]
    val filters=remember(prefs.chat,edits) {
        val json=prefs.chat.json()
        listOf("chat_filters","chat_timestamps").forEach { key -> edits[key]?.let { edit ->
            val patch=org.json.JSONObject(edit.requested);patch.keys().forEach { json.put(it,patch.get(it)) }
        } }
        ChatPreferences.parse(json)
    }
    val delayKey="chat_delay:${state.videoId}"
    val delayEdit=edits[delayKey]
    val timing=delayEdit?.let { org.json.JSONObject(it.requested).optString("text") } ?: (state.timingOffsetMs/1000.0).toString()
    DisposableEffect(ctx,state.occurrence) { onDispose { vm.leavePreferencePage("Chat settings") } }
    LibraryPanel("Chat settings", dismiss, closeLabel = "Close chat settings") { body, top ->
        Column(body.testTag("chat-settings-sheet")) {
            LazyColumn(Modifier.weight(1f).testTag("chat-settings-list"),
                contentPadding = PaddingValues(start = AppTokens.inset, end = AppTokens.inset, top = top + 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { AppSection {
                    Text("Chat layout", style = MaterialTheme.typography.labelLarge)
                    Box { AppSegments(listOf(false, true), appearance.overlay,
                        { if (it) "Overlay" else "Docked" }, Modifier.fillMaxWidth(),
                        tag = { "chat-layout-${if (it) "overlay" else "docked"}" }) { vm.setChatAppearance(appearance.copy(overlay = it), ctx) } }
                    Text("Appearance saves automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (appearance.overlay) {
                    run { ChatSlider("Background opacity", "chat-opacity", appearance.opacity.toFloat(), 0f..100f, "${appearance.opacity}%") {
                        vm.setChatAppearance(appearance.copy(opacity = it.roundToInt()), ctx) } }
                } else {
                    run { ChatSlider("Side panel width", "chat-side-width", appearance.besideFraction, ChatAppearance.BESIDE_FRACTION_RANGE) {
                        vm.setChatAppearance(appearance.copy(besideFraction = it), ctx) } }
                    run { ChatSlider("Bottom panel height", "chat-bottom-height", appearance.belowFraction, .3f.. .7f) {
                        vm.setChatAppearance(appearance.copy(belowFraction = it), ctx) } }
                }
                run { ChatSlider("Text size", "chat-text-size", appearance.fontScale.toFloat(), 25f..300f, "${appearance.fontScale}%") {
                    vm.setChatAppearance(appearance.copy(fontScale = it.roundToInt()), ctx) } }
                run { ChatSwitch("Show channel IDs", "chat-channel-ids", !appearance.hideUserIds) {
                    vm.setChatAppearance(appearance.copy(hideUserIds = !it), ctx) } }
                } }
                item { AppSection {
                run { ChatSwitch("Show timestamps","chat-timestamps",filters.timestamps) {
                    vm.requestChatPreferences(filters.copy(timestamps=it),before=filters,context=ctx)
                } }
                PreferenceSaveFeedback(timestampEdit?.status,timestampEdit?.error,{vm.preferenceSaves.retry("chat_timestamps")},{vm.preferenceSaves.revert("chat_timestamps")})
                OutlinedTextField(filters.users,{vm.requestChatPreferences(filters.copy(users=it),before=filters,debounce=true,context=ctx)},
                    label={Text("Blocked users")},supportingText={Text("Channel IDs or @handles, separated by spaces.")},
                    isError=filterEdit?.status==PreferenceSaveStatus.INVALID,
                    keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={vm.preferenceSaves.flush("chat_filters")}),
                    modifier=Modifier.fillMaxWidth().testTag("chat-users").onFocusChanged { if(!it.isFocused) vm.preferenceSaves.flush("chat_filters") })
                OutlinedTextField(filters.words,{vm.requestChatPreferences(filters.copy(words=it),before=filters,debounce=true,context=ctx)},
                    label={Text("Blocked words")},supportingText={Text("For example: spam /pattern/. Separate entries with spaces.")},
                    isError=filterEdit?.status==PreferenceSaveStatus.INVALID,
                    keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={vm.preferenceSaves.flush("chat_filters")}),
                    modifier=Modifier.fillMaxWidth().testTag("chat-words").onFocusChanged { if(!it.isFocused) vm.preferenceSaves.flush("chat_filters") })
                PreferenceSaveFeedback(filterEdit?.status,filterEdit?.error,{vm.preferenceSaves.retry("chat_filters")},{vm.preferenceSaves.revert("chat_filters")})
                } }
                item { AppSection {
                    OutlinedTextField(timing,{vm.requestChatTiming(it,videoId=state.videoId,context=ctx)},label={Text("Chat delay (seconds)")},singleLine=true,
                        supportingText={Text(if(state.open || delayEdit!=null) "Positive values show messages later; negative values show them earlier." else "Open chat replay to adjust this video’s delay.")},
                        enabled=state.videoId.isNotBlank() && (state.open || delayEdit!=null),
                        isError=delayEdit?.status==PreferenceSaveStatus.INVALID,
                        keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done,keyboardType=KeyboardType.Decimal),
                        keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={vm.preferenceSaves.flush(delayKey)}),
                        modifier=Modifier.fillMaxWidth().testTag("chat-timing").onFocusChanged { if(!it.isFocused) vm.preferenceSaves.flush(delayKey) })
                    PreferenceSaveFeedback(delayEdit?.status,delayEdit?.error,{vm.preferenceSaves.retry(delayKey)},{vm.preferenceSaves.revert(delayKey)})
                    state.timingError?.let { Text(it,color=MaterialTheme.colorScheme.error)
                        TextButton(vm::retryChatTiming) {Text("Retry delay sync")} }
                } }
                edits.values.filter { it.key.startsWith("profile:chat.") }.forEach { edit -> item(key = edit.key) {
                    Text("Chat ${edit.key.substringAfter("profile:chat.")}", style = MaterialTheme.typography.labelLarge)
                    PreferenceSaveFeedback(edit.status,edit.error,{vm.preferenceSaves.retry(edit.key)},{vm.preferenceSaves.revert(edit.key)})
                } }
                state.filterWarning?.let { warning -> item { Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
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
        var requested by remember(value) { mutableFloatStateOf(value) }
        Slider(requested.coerceIn(range),{requested=it},onValueChangeFinished={change(requested)},
            valueRange=range,modifier=Modifier.testTag(tag).semantics {contentDescription=label})
    }
}
