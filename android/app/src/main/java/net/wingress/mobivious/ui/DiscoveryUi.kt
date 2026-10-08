package net.wingress.mobivious.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wingress.mobivious.data.*

@Composable
internal fun RegionChoice(selected: String, enabled: Boolean = true, tag: String, change: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text("Trending region: ${ContentRegions.label(selected, locale)}")
    }
    if (open && enabled) RegionPicker(selected, dismiss = { open = false }) { code -> open = false; change(code) }
}

@Composable
private fun RegionPicker(selected: String, dismiss: () -> Unit, change: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val codes = remember(query, locale) { ContentRegions.choices(query, locale) }
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("region-picker"), title = { Text("Trending region") }, text = {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("Search countries or codes") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("region-search"))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).testTag("region-list")) {
                items(codes, key = { it }) { code ->
                    TextButton(onClick = { change(code) }, modifier = Modifier.fillMaxWidth().testTag("region-$code")) {
                        Text(ContentRegions.label(code, locale), Modifier.weight(1f))
                        if (code == selected) Text("Selected", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (codes.isEmpty()) item { Text("No matching countries", Modifier.padding(16.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun TrendingControls(vm: AppViewModel, region: String) {
    val category by vm.trendingCategory.collectAsStateWithLifecycle()
    val busy by vm.trendingRegionBusy.collectAsStateWithLifecycle()
    val error by vm.trendingRegionError.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendingCategory.entries.forEach { option ->
                FilterChip(selected = category == option, onClick = { vm.selectTrendingCategory(option) },
                    label = { Text(option.label) }, modifier = Modifier.testTag("trending-${option.apiValue}"))
            }
        }
        RegionChoice(region, !busy, "trending-region", vm::selectTrendingRegion)
        if (busy) Text("Saving region…", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trending-region-error")) }
    }
}
