package net.wingress.mobivious

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.ContentLinks
import net.wingress.mobivious.ui.OpeningLinksScreen
import net.wingress.mobivious.ui.openAndroidLinkSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningLinksPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun matches(url: String): Boolean = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName),
        PackageManager.MATCH_DEFAULT_ONLY).any { it.activityInfo.name == MainActivity::class.java.name }

    @Test fun manifestMatchesAllEightHostsOverHttpAndHttps() {
        for (scheme in listOf("http", "https")) for (host in ContentLinks.youtubeHosts) {
            val path = if (host.endsWith("youtu.be")) "/testvideo01?t=2.345" else "/watch?v=testvideo01&t=2.345"
            assertTrue("$scheme://$host$path", matches("$scheme://$host$path"))
        }
        for (path in listOf("/shorts/testvideo01", "/live/testvideo01", "/embed/testvideo01", "/w/testvideo01",
            "/v/testvideo01", "/e/testvideo01", "/watch/testvideo01", "/playlist?list=PLlive", "/mix?list=RDtestvideo01",
            "/channel/UCaaaaaaaaaaaaaaaaaaaaaa", "/@creator", "/c/Creator", "/user/Creator", "/hashtag/music",
            "/post/Ugfixture", "/clip/legacy-id", "/profile?user=Creator", "/attribution_link?u=%2Fwatch%3Fv%3Dtestvideo01")) {
            assertTrue(path, matches("https://www.youtube.com$path"))
        }
        assertTrue(matches("https://www.youtube-nocookie.com/embed/testvideo01"))
        assertTrue(matches("https://youtu.be/testvideo01/"))
        assertTrue(matches("https://mobivious.wingress.net/watch?v=testvideo01"))
        assertTrue(matches("https://invidious.wingress.net/watch?v=testvideo01"))
    }

    @Test fun manifestExcludesUnrelatedPagesHostsAndSchemes() {
        for (url in listOf("https://youtube.com/", "https://youtube.com/results?search_query=test",
            "https://youtube.com/feed/subscriptions", "https://youtube.com/watchlater", "https://youtu.be/",
            "https://youtu.be/short", "https://youtu.be/testvideo01/extra", "https://youtube.com.evil.test/watch?v=testvideo01",
            "https://notyoutube.com/watch?v=testvideo01", "vnd.youtube:testvideo01")) {
            assertFalse(url, matches(url))
        }
    }

    @Test fun settingsScreenExplainsSelectionAndOffersTheSystemShortcut() {
        compose.setContent { MaterialTheme { OpeningLinksScreen(Modifier) { fail("Settings should be available") } } }
        compose.onNodeWithTag("opening-links-status").assertExists()
        compose.onNodeWithText("1. In Android’s app settings for YouTube, turn off Open supported links. If YouTube Music handles music.youtube.com, turn it off there too.")
            .assertExists()
        compose.onNodeWithTag("open-android-link-settings").performScrollTo().assertHasClickAction()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filter = android.content.IntentFilter(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
            addDataScheme("package")
            addDataSchemeSpecificPart(context.packageName, android.os.PatternMatcher.PATTERN_LITERAL)
        }
        val monitor = instrumentation.addMonitor(filter, android.app.Instrumentation.ActivityResult(0, null), true)
        try {
            compose.onNodeWithTag("open-android-link-settings").performClick()
            assertEquals(1, monitor.hits)
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithText("Apps that explicitly launch the YouTube app, and browsers that keep links inside their own pages, can bypass Android’s default link choice.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun shortcutTargetsTheInstalledPackageAndFallsBackToAppDetails() {
        val launched = mutableListOf<Intent>()
        assertTrue(openAndroidLinkSettings(context) { launched += it })
        assertEquals(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, launched.single().action)
        assertEquals("package:${context.packageName}", launched.single().dataString)
        assertNotNull(context.packageManager.resolveActivity(launched.single(), PackageManager.MATCH_DEFAULT_ONLY))
        launched.clear()
        assertTrue(openAndroidLinkSettings(context) {
            launched += it
            if (launched.size == 1) throw ActivityNotFoundException()
        })
        assertEquals(listOf(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS), launched.map { it.action })
        assertEquals("package:${context.packageName}", launched.last().dataString)
        assertFalse(openAndroidLinkSettings(context) { throw SecurityException() })
    }
}
