package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

@Composable
internal fun AppTopBar(title: String, modifier: Modifier = Modifier, navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp)
        .padding(horizontal = AppTokens.inset, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        navigation?.invoke()
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun SectionHeading(title: String, modifier: Modifier = Modifier, detail: String? = null,
    action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> AppSegments(options: List<T>, selected: T, label: (T) -> String,
    modifier: Modifier = Modifier, tag: (T) -> String = { "" }, select: (T) -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            AppFilterChip(selected == option, { select(option) }, { Text(label(option)) },
                modifier = Modifier.heightIn(min=48.dp).testTag(tag(option)))
        }
    }
}
