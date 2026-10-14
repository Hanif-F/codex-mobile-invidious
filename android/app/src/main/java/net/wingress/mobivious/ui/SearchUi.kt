package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** A single dispatch path consumes physical Enter before an IME can submit it twice. */
@Composable
internal fun SearchField(value: String, update: (String) -> Unit, label: String, tag: String, modifier: Modifier=Modifier, enabled: Boolean=true, submit: () -> Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val search={ keyboard?.hide();focus.clearFocus();submit() }
    val c=MaterialTheme.colorScheme
    OutlinedTextField(value,update,placeholder={Text(label)},singleLine=true,enabled=enabled,
        shape=MaterialTheme.shapes.medium,
        colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=c.surfaceContainerHighest,
            unfocusedContainerColor=c.surfaceContainerHighest,unfocusedBorderColor=c.outline,focusedBorderColor=c.primary),
        modifier=modifier.testTag(tag).onPreviewKeyEvent { event ->
            if(enabled && (event.key==Key.Enter || event.key==Key.NumPadEnter)) {
                if(event.type==KeyEventType.KeyUp) search()
                true
            } else false
        },keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}),
        trailingIcon={ Row {
            if(value.isNotEmpty()) IconButton({update("")},Modifier.size(48.dp).testTag("$tag-clear"),enabled=enabled) { Icon(Icons.Default.Close,"Clear search input") }
            IconButton(search,Modifier.size(48.dp).testTag("$tag-submit"),enabled=enabled) {Icon(Icons.Default.Search,"Search")}
        } })
}
