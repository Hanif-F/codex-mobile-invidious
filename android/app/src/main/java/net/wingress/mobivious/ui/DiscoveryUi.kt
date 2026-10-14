package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*

@Composable
internal fun RegionChoice(selected: String, enabled: Boolean = true, tag: String, change: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var open by rememberSaveable { mutableStateOf(false) }
    ActionRow("Trending region", detail = ContentRegions.label(selected, locale), enabled = enabled,
        modifier = Modifier.testTag(tag), trailingIcon = Icons.Default.ExpandMore) { open = true }
    if (open && enabled) RegionPicker(selected, dismiss = { open = false }) { code -> open = false; change(code) }
}

@Composable
private fun RegionPicker(selected: String, dismiss: () -> Unit, change: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val codes = remember(query, locale) { ContentRegions.choices(query, locale) }
    LibraryPanel("Trending region", dismiss, closeLabel = "Cancel") { body, top ->
        LazyColumn(body.testTag("region-picker"), contentPadding = PaddingValues(start = AppTokens.inset, end = AppTokens.inset,
            top = top + 16.dp, bottom = 24.dp)) {
            item { OutlinedTextField(query, { query = it }, label = { Text("Search countries or codes") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("region-search")) }
            items(codes, key = { it }) { code ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(code == selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { change(code) })
                    .testTag("region-$code").padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ContentRegions.label(code, locale), Modifier.weight(1f))
                    if (code == selected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            if (codes.isEmpty()) item { Text("No matching countries", Modifier.padding(vertical = 16.dp)) }
        }
    }
}

@Composable
internal fun TrendingControls(vm: AppViewModel, region: String) {
    val confirmedCategory by vm.trendingCategory.collectAsStateWithLifecycle()
    val edits by vm.preferenceSaves.edits.collectAsStateWithLifecycle()
    val category = TrendingCategory.saved(vm.localPreferenceValue("profile:trendingCategory",confirmedCategory.apiValue) as String)
    val categoryEdit = edits["profile:trendingCategory"]
    val edit=edits["region"]
    val requestedRegion=edit?.let { org.json.JSONObject(it.requested).getString("region") } ?: region
    val locale = LocalConfiguration.current.locales[0]
    var regionOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrowseTabs(TrendingCategory.entries, category, { it.label }, { "trending-${it.apiValue}" }, select = vm::selectTrendingCategory)
        PreferenceSaveFeedback(categoryEdit?.status,categoryEdit?.error,{vm.preferenceSaves.retry("profile:trendingCategory")},{vm.preferenceSaves.revert("profile:trendingCategory")})
        LibraryActionButton(ContentRegions.label(requestedRegion, locale), Modifier.testTag("trending-region"), icon = Icons.Default.ExpandMore) { regionOpen = true }
        if (regionOpen) RegionPicker(requestedRegion, { regionOpen = false }) { regionOpen = false; vm.selectTrendingRegion(it) }
        PreferenceSaveFeedback(edit?.status,edit?.error,{vm.preferenceSaves.retry("region")},{vm.preferenceSaves.revert("region")},
            modifier=if(edit?.status in listOf(PreferenceSaveStatus.FAILED,PreferenceSaveStatus.INVALID)) Modifier.testTag("trending-region-error") else Modifier)
    }
}
