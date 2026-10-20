package net.wingress.mobivious.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.wingress.mobivious.data.ContentLinks

/** These choices belong to Android, independently of the active account or instance. */
@Composable
internal fun OpeningLinksScreen(modifier: Modifier, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by remember { mutableStateOf(selectedYouTubeDomains(context)) }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) selected = selectedYouTubeDomains(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LazyColumn(modifier.fillMaxSize().testTag("opening-links"), contentPadding = settingsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AppSection {
            SectionHeading("Open YouTube links in Mobivious")
            Text(selected?.let { "Enabled for $it of ${ContentLinks.youtubeHosts.size} YouTube domains" }
                ?: "Choose which YouTube links open in Mobivious in Android settings.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("opening-links-status"))
            Text("1. In Android’s app settings for YouTube, turn off Open supported links. If YouTube Music handles music.youtube.com, turn it off there too.")
            Text("2. Open Mobivious’s Android link settings below, turn on Open supported links, then select the YouTube web addresses under Add link or Supported web addresses.")
            ActionRow("Open Android link settings", modifier = Modifier.testTag("open-android-link-settings")) {
                if (!openAndroidLinkSettings(context)) onError()
            }
        } }
        item { AppSection {
            Text("Videos, short links, playlists, channels and other supported YouTube content links can open here from browsers and other apps.")
            Text("Apps that explicitly launch the YouTube app, and browsers that keep links inside their own pages, can bypass Android’s default link choice.",
                style = MaterialTheme.typography.bodySmall)
        } }
    }
}

private fun selectedYouTubeDomains(context: Context): Int? = try {
    context.getSystemService(DomainVerificationManager::class.java)
        ?.getDomainVerificationUserState(context.packageName)?.let { state ->
            if (!state.isLinkHandlingAllowed) 0 else ContentLinks.youtubeHosts.count { host ->
                state.hostToStateMap[host] in setOf(DomainVerificationUserState.DOMAIN_STATE_SELECTED,
                    DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
            }
        }
} catch (_: PackageManager.NameNotFoundException) { null }

internal fun openAndroidLinkSettings(context: Context, launch: (Intent) -> Unit = { context.startActivity(it) }): Boolean {
    val uri = "package:${context.packageName}".toUri()
    for (action in listOf(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) {
        try {
            launch(Intent(action, uri))
            return true
        } catch (_: ActivityNotFoundException) {
            // Some Android variants expose only the app-details page.
        } catch (_: SecurityException) {
            // Try the public app-details fallback if the direct page is restricted.
        }
    }
    return false
}
