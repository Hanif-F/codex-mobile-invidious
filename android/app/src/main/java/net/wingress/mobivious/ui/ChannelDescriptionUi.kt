package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.Comment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelDescriptionSheet(channel: Channel, server: String = "", link: (String) -> Unit = {}, dismiss: () -> Unit) {
    AppModalSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PanelScaffold(Modifier.fillMaxWidth().fillMaxHeight().testTag("channel-description-sheet"), toolbar = {
            BrowsePanelToolbar("Channel description", "Close channel description", dismiss, channel.name)
        }) { contentModifier, topInset ->
            Column(contentModifier.verticalScroll(rememberScrollState()).testTag("channel-description-scroll")
                .padding(start = AppTokens.inset, end = AppTokens.inset, top = topInset, bottom = 24.dp)) {
                RichCommentText(Comment(channel.name, channel.description, "", 0, id = "channel:${channel.id}", html = channel.descriptionHtml),
                    server, "", link, collapsedLines = Int.MAX_VALUE, tag = "channel-description-text")
            }
        }
    }
}
