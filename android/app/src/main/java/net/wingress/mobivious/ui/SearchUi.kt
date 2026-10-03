package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction

/** All submission paths share this action; consume hardware Enter before the IME sees it. */
@Composable
internal fun SearchField(value: String, update: (String) -> Unit, label: String, tag: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, submit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val search = { keyboard?.hide(); focus.clearFocus(); submit() }
    OutlinedTextField(value, update, label = { Text(label) }, singleLine = true, enabled = enabled,
        modifier = modifier.testTag(tag).onPreviewKeyEvent { event ->
            if (enabled && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                if (event.type == KeyEventType.KeyUp) search()
                true
            } else false
        }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
        trailingIcon = { IconButton(enabled = enabled, onClick = search, modifier = Modifier.testTag("$tag-submit")) {
            Icon(Icons.Default.Search, "Search")
        } })
}
