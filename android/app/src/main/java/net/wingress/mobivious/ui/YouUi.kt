package net.wingress.mobivious.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.Account
import net.wingress.mobivious.data.Playlist
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun YouIdentity(account: Account?, server: String) {
    Row(Modifier.fillMaxWidth().testTag("you-identity").padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (account == null) Icon(Icons.Default.PersonOutline, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
            else Text(account.username.take(2).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(account?.username ?: "Your personal space", style = MaterialTheme.typography.headlineSmall)
            Text(server.toHttpUrlOrNull()?.host ?: server, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LibraryDestinations(navigate: (String) -> Unit) {
    LibraryControlScene(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            val destinations = listOf(
                Triple("Playlists", "Collections you keep", Icons.Default.VideoLibrary),
                Triple("My Clips", "Moments you’ve shared", Icons.Default.ContentCut),
                Triple("History", "Pick up where you left off", Icons.Default.History),
                Triple("Downloads", "Watch and listen offline", Icons.Default.DownloadDone))
            val routes = listOf("playlists", "clips", "history", "downloads")
            val columns = if (maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.3f) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                destinations.indices.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { i -> val (title, detail, icon) = destinations[i]
                            LibraryDestination(title, detail, icon, Modifier.weight(1f).testTag("you-${routes[i]}")) { navigate(routes[i]) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryDestination(title: String, detail: String, icon: ImageVector, modifier: Modifier, action: () -> Unit) {
    Row(modifier.browseGlass().clickable(role = Role.Button, onClick = action).heightIn(min = 80.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun YouDashboard(account: Account?, server: String, state: LazyListState, top: Dp,
    navigate: (String) -> Unit, signIn: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag(if (account == null) "you-guest" else "you-library-list"), state = state,
        contentPadding = PaddingValues(start = Liquid.inset, end = Liquid.inset, top = top, bottom = LocalContentBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { YouIdentity(account, server) }
        item { Text("Your library", style = MaterialTheme.typography.titleLarge) }
        item { LibraryDestinations(navigate) }
        if (account == null) item {
            LibraryControlScene(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Your videos, together", style = MaterialTheme.typography.headlineSmall)
                    Text("Sign in to keep your playlists, history, and clips together. Downloads stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LibraryActionButton("Sign in", Modifier.testTag("you-sign-in"), Icons.AutoMirrored.Filled.Login, prominent = true, onClick = signIn)
                    Text("No Google account needed. Create an account when your instance allows it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun PlaylistLibrary(vm: AppViewModel, playlists: List<Playlist>, section: String, state: BrowseState,
    list: LazyListState, top: Dp, open: (Playlist) -> Unit, create: () -> Unit, signIn: () -> Unit) {
    val visible = playlists.filter { if (section == "owned") it.owned else !it.owned && it.saved }
    LazyColumn(Modifier.fillMaxSize().testTag("playlist-library-list"), state = list,
        contentPadding = PaddingValues(top = top, bottom = LocalContentBottomInset.current)) {
        item { SectionHeading(if (section == "owned") "My playlists (${visible.size})" else "Subscribed playlists (${visible.size})",
            Modifier.padding(horizontal = Liquid.inset, vertical = 12.dp)) }
        items(visible, key = { it.id }) { playlist -> PlaylistCard(vm, playlist, { open(playlist) }, signIn) }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(Liquid.inset)) }
        state.error?.let { error -> item { LibraryEmptyState("Couldn’t load playlists", error, Icons.Default.CloudOff, "Retry", vm::refresh) } }
        if (!state.loading && state.error == null && visible.isEmpty()) item {
            LibraryEmptyState(if (section == "owned") "Make room for your favorites" else "Follow a collection",
                if (section == "owned") "Create a playlist to keep videos together." else "Subscribe to playlists or mixes from search, a channel, or a shared link.",
                Icons.Default.VideoLibrary, if (section == "owned") "New playlist" else null, create)
        }
    }
}
