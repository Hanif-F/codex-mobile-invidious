package net.wingress.mobivious

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import net.wingress.mobivious.data.AccountPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Real window/configuration changes; the hardware remains the user's running emulator. */
@RunWith(AndroidJUnit4::class)
class ResponsiveShellSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val instrument get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var originalSize: String
    private lateinit var originalDensity: String
    private lateinit var originalFont: String
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun ui(block: () -> Unit) = instrument.runOnMainSync(block)
    private fun until(condition: () -> Boolean) = compose.waitUntil(25_000,condition)
    @Before fun launch() {
        originalSize = shell("wm size").let { if(it.contains("Override size:")) it.substringAfter("Override size: ").trim() else "reset" }
        originalDensity = shell("wm density").let { if(it.contains("Override density:")) it.substringAfter("Override density: ").trim() else "reset" }
        originalFont = shell("settings get system font_scale")
        (URL("http://127.0.0.1:18080/test/reset").openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; doOutput=true; outputStream.use { it.write("{}".toByteArray()) }; inputStream.close(); disconnect()
        }
        activity=instrument.startActivitySync(Intent(instrument.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            activity.model.closePlayer(); activity.model.store.save(null); activity.model.switchServer("http://127.0.0.1:18080")
            activity.model.store.guestPreferences(AccountPreferences(autoplay=false,darkMode="light")); activity.model.refreshSharedSettings()
            activity.model.navigate("Popular")
        }
        until { !activity.model.browse.value.loading }
    }
    @After fun restore() {
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::originalDensity.isInitialized) shell("wm density $originalDensity")
        if (::originalFont.isInitialized) shell(if(originalFont=="null") "settings delete system font_scale" else "settings put system font_scale $originalFont")
        ui { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().forEach { it.model.closePlayer();it.finishAndRemoveTask() } }
    }
    private fun resize(width: Int, font: Float) {
        shell("wm size ${width}x900"); shell("wm density 160"); shell("settings put system font_scale $font")
        until {
            var ready=false
            ui {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()?.let {
                    activity=it
                    ready=kotlin.math.abs(it.resources.configuration.screenWidthDp-width)<=1 && kotlin.math.abs(it.resources.configuration.fontScale-font)<.02f
                }
            }
            ready && compose.onAllNodesWithTag("navigation-Popular").fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun fourRootsKeepLabelsTargetsAndReadingBoundsAcrossWindowAndFontMatrix() {
        for(width in listOf(320,360,448,600,840,1200)) for(font in listOf(1f,1.3f,2f)) {
            resize(width,font)
            for(dark in listOf(false,true)) {
                ui { activity.model.store.guestPreferences(AccountPreferences(autoplay=false,darkMode=if(dark) "dark" else "light")); activity.model.refreshSharedSettings() }
                compose.waitForIdle()
                val bounds=listOf("Popular","Trending","Subscriptions","You").map { name ->
                    val node=compose.onNodeWithTag("navigation-$name")
                    node.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                    val layouts=mutableListOf<TextLayoutResult>()
                    val label = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("navigation-$name")),useUnmergedTree=true)
                    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertLabelGlyphsFit(layouts.single(),"$name at width=$width font=$font")
                    val target = node.fetchSemanticsNode().boundsInRoot
                    val text = label.getUnclippedBoundsInRoot()
                    val touch = node.getUnclippedBoundsInRoot()
                    assertTrue("$name label escapes its target at width=$width font=$font: $text vs $touch",
                        text.left >= touch.left-1.dp && text.right <= touch.right+1.dp && text.top >= touch.top-1.dp && text.bottom <= touch.bottom+1.dp)
                    target
                }
                if(width<600) assertTrue("Phone destinations share one navigation row",bounds.maxOf { it.top } < bounds.minOf { it.bottom })
                else assertTrue("Wide destinations share one rail",bounds.maxOf { it.left } < bounds.minOf { it.right })
                bounds.zipWithNext().forEach { (a,b) -> assertFalse("Navigation targets overlap",a.overlaps(b)) }
                captureWatchScreenshot("responsive-$width-${font.toString().replace('.','-')}-${if(dark) "dark" else "light"}")
            }
        }
    }
    @Test fun keyboardTabAndEnterNavigateAndSubmitSearchExactlyOnce() {
        resize(360,1f)
        compose.waitForIdle()
        val popular=compose.onNodeWithTag("navigation-Popular")
        popular.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_TAB)
        compose.onNodeWithTag("navigation-Trending").assertIsFocused()
        instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
        until { activity.model.tab=="Trending" && !activity.model.browse.value.loading }
        val search=compose.onNodeWithTag("global-search")
        search.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
        compose.onNodeWithTag("main-search").performClick().performTextReplacement("keyboard")
        instrument.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
        until { activity.model.searchInput.value.submitted=="keyboard" && !activity.model.browse.value.loading }
        val state=org.json.JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
        val requests=state.getJSONArray("allRequests")
        assertEquals(1,(0 until requests.length()).count {
            requests.getJSONObject(it).getString("path").let { path -> path.startsWith("/api/v1/search?") && path.contains("q=keyboard") }
        })
        compose.onNodeWithContentDescription("Close search").performClick()
        until { activity.model.tab=="Trending" }
    }
    @Test fun authenticationFieldsAndSubmitRemainReachableAtNarrowLargeTextAndWideSizes() {
        for ((width,font) in listOf(320 to 2f,840 to 1f)) {
            resize(width,font)
            for(dark in listOf(false,true)) {
                ui {
                    activity.model.store.guestPreferences(AccountPreferences(autoplay=false,darkMode=if(dark) "dark" else "light"))
                    activity.model.refreshSharedSettings();activity.model.navigate("You")
                }
                compose.onNodeWithTag("you-guest").assertExists()
                compose.onNodeWithText("Sign in",substring=false).performScrollTo().performClick()
                for(creating in listOf(false,true)) {
                    val tag="authentication-$creating"
                    val chip=compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                    val label=compose.onNode(hasText(if(creating) "Create account" else "Sign in") and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
                    val layouts=mutableListOf<TextLayoutResult>()
                    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                    assertLabelGlyphsFit(layouts.single(),"Authentication choice at width=$width font=$font")
                    val viewport=compose.onNodeWithTag("sign-in-viewport").getUnclippedBoundsInRoot()
                    val target=chip.getUnclippedBoundsInRoot()
                    assertTrue("Authentication choice escapes visible width",target.left>=viewport.left && target.right<=viewport.right)
                }
                val appearance=if(dark) "dark" else "light"
                captureWatchScreenshot("acceptance-auth-$width-$appearance-modes")
                compose.onNodeWithTag("account-username").performTextReplacement("PreviewViewer")
                compose.onNodeWithTag("account-password").performScrollTo().performTextReplacement("disposable-fixture-password")
                compose.onNodeWithTag("account-password").assertIsDisplayed()
                captureWatchScreenshot("acceptance-auth-$width-$appearance-field")
                compose.onNodeWithTag("account-auth-submit").performScrollTo().assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).assertIsEnabled()
                captureWatchScreenshot("acceptance-auth-$width-$appearance-submit")
                // No credentials are submitted by this layout/accessibility check.
                ui {activity.model.navigate("Popular")}
            }
        }
    }
}
