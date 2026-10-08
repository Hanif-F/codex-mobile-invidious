package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.Account
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
internal fun YouIdentity(account: Account?, server: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("you-identity")) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (account == null) Icon(Icons.Default.PersonOutline, null, Modifier.size(28.dp))
                    else Text(account.username.take(2).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account?.username ?: "Your personal space", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(server.toHttpUrlOrNull()?.host ?: server, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun LibraryShortcuts(history: () -> Unit, clips: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 340.dp && fontScale <= 1.3f) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryShortcut("Watch history", "Pick up where you left off", Icons.Default.History, Modifier.weight(1f).testTag("you-history"), history)
                LibraryShortcut("My Clips", "Moments you’ve shared", Icons.Default.ContentCut, Modifier.weight(1f).testTag("you-clips"), clips)
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryShortcut("Watch history", "Pick up where you left off", Icons.Default.History, Modifier.fillMaxWidth().testTag("you-history"), history)
            LibraryShortcut("My Clips", "Moments you’ve shared", Icons.Default.ContentCut, Modifier.fillMaxWidth().testTag("you-clips"), clips)
        }
    }
}

@Composable
private fun LibraryShortcut(title: String, detail: String, icon: ImageVector, modifier: Modifier, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GuestYouScreen(server: String, state: LazyListState, signIn: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("you-guest"), state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("You", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { YouIdentity(null, server) }
        item {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Your videos, together", style = MaterialTheme.typography.headlineSmall)
                Text("Save videos into playlists, revisit your watch history, and keep the moments you love. Sign in with your Invidious account to make this space yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = signIn, modifier = Modifier.heightIn(min = 48.dp).testTag("you-sign-in")) {
                    Icon(Icons.AutoMirrored.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Sign in")
                }
                Text("No Google account needed. You can also create an account when your instance allows it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
