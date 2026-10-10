package net.wingress.mobivious

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.Account
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryGlassPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/mobivious-library-glass", "screencap -p /data/local/tmp/mobivious-library-glass/$name.png"))
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

    @Test fun narrowLargeTextDashboardKeepsEveryDestinationReachableInBothAppearances() {
        var dark by mutableStateOf(false)
        var opaque by mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent { MaterialTheme(colorScheme = if (dark) Liquid.dark else Liquid.light, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides opaque,
                LocalContentBottomInset provides 100.dp) {
                BrowseGlassSurface(Modifier.width(320.dp).height(850.dp), softEdge = true, toolbar = {
                    LibraryToolbar("You", large = true, statusInset = false)
                }) { modifier, top ->
                    Box(modifier) { YouDashboard(Account("", "A long library owner name", Long.MAX_VALUE, ""), "https://invidious.example", rememberLazyListState(), top, { opened += it }, {}) }
                }
            }
        } }
        for ((isDark, isOpaque) in listOf(false to false, true to false, true to true)) {
            compose.runOnIdle { dark = isDark; opaque = isOpaque }
            for (destination in listOf("playlists", "clips", "history", "downloads")) {
                compose.onNodeWithTag("you-library-list").performScrollToNode(hasTestTag("you-$destination"))
                compose.onNodeWithTag("you-$destination").assertHeightIsAtLeast(48.dp).performClick()
            }
            capture(if (isDark) "dashboard-narrow-large-dark-${if (isOpaque) "opaque" else "transparent"}" else "dashboard-narrow-large-light")
        }
        assertEquals(List(3) { listOf("playlists", "clips", "history", "downloads") }.flatten(), opened)
    }

    @Test fun wideDashboardUsesTwoColumnsAndGuestDownloadsRemainAvailable() {
        var opened = ""
        compose.setContent { MaterialTheme(colorScheme = Liquid.light, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(1.5f)) {
                Surface(Modifier.width(700.dp).height(850.dp)) {
                    YouDashboard(null, "https://invidious.example", rememberLazyListState(), 0.dp, { opened = it }, {})
                }
            }
        } }
        val playlists = compose.onNodeWithTag("you-playlists").fetchSemanticsNode().boundsInRoot
        val clips = compose.onNodeWithTag("you-clips").fetchSemanticsNode().boundsInRoot
        assertEquals(playlists.top, clips.top, 1f)
        assertTrue(clips.left >= playlists.right)
        compose.onNodeWithTag("you-downloads").performClick()
        assertEquals("downloads", opened)
        capture("dashboard-wide-guest")
    }

    @Test fun floatingToolbarLeavesFirstAndLastRowsAccessibleAtLargeText() {
        var created = false
        compose.setContent { MaterialTheme(colorScheme = Liquid.dark, typography = Liquid.typography) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f), LocalReduceTransparency provides true) {
                BrowseGlassSurface(Modifier.width(320.dp).height(850.dp), softEdge = true, toolbar = {
                    LibraryToolbar("Playlists", statusInset = false, back = {}, actions = {
                        IconButton({ created = true }, Modifier.size(48.dp).testTag("create")) { Icon(Icons.Default.Add, "New playlist") }
                    }, controls = { BrowseTabs(listOf("owned", "subscribed"), "owned", { if (it == "owned") "My playlists" else "Subscribed" }, { "tab-$it" }) {} })
                }) { modifier, top -> LazyColumn(modifier.testTag("rows"), contentPadding = PaddingValues(top = top, bottom = 100.dp)) {
                    items(30) { Text("Playlist $it", Modifier.fillMaxWidth().padding(20.dp).testTag("row-$it")) }
                } }
            }
        } }
        val toolbar = compose.onNodeWithTag("browse-glass-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("row-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top >= toolbar.bottom)
        compose.onNodeWithTag("rows").performScrollToIndex(29)
        compose.onNodeWithTag("row-29").assertIsDisplayed()
        compose.onNodeWithTag("create").assertIsDisplayed().performClick()
        assertTrue(created)
        compose.onNodeWithTag("tab-subscribed").performScrollTo().assertIsDisplayed()
        capture("playlist-toolbar-narrow-large-opaque")
    }

    @Test fun playlistFormKeepsSaveAvailableAndPreservesPrivacyAndTrimmedTitle() {
        var saved: Triple<String, String, String>? = null
        compose.setContent { MaterialTheme(colorScheme = Liquid.light, typography = Liquid.typography) {
            PlaylistDialog(null, {}) { title, privacy, description -> saved = Triple(title, privacy, description) }
        } }
        compose.onNodeWithTag("playlist-form-save").assertIsNotEnabled()
        compose.onNodeWithTag("playlist-form-title").performTextInput("  My collection  ")
        compose.onNodeWithTag("playlist-privacy-unlisted").performClick()
        compose.onNodeWithTag("playlist-form-save").assertIsDisplayed().performClick()
        assertEquals(Triple("My collection", "unlisted", ""), saved)
        capture("playlist-form-keyboard")
    }
}
