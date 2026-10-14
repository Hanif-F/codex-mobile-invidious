package net.wingress.mobivious

import net.wingress.mobivious.ui.appColorScheme
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccountPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Regression journeys use the disposable local fixture, including its optional CAPTCHA response. */
@RunWith(AndroidJUnit4::class)
class VisualFixesSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    private lateinit var activity: MainActivity
    private val vm get() = activity.model
    private var hardwareIme = "null"
    private var handwriting = "null"
    private fun ui(action: () -> Unit) = compose.runOnUiThread(action)
    private fun until(condition: () -> Boolean) = compose.waitUntil(30_000, condition)
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { String(it.readBytes()).trim() }
    private fun command(path: String, body: String = "{}") {
        (URL("http://127.0.0.1:18080/test/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(body.toByteArray()) }; inputStream.close(); disconnect()
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        shell("mkdir -p /data/local/tmp/mobivious-visual-fixes")
        shell("screencap -p /data/local/tmp/mobivious-visual-fixes/$name.png")
    }
    @Before fun launch() {
        command("reset")
        hardwareIme = shell("settings get secure show_ime_with_hard_keyboard")
        handwriting = shell("settings get secure stylus_handwriting_enabled")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        shell("settings put secure stylus_handwriting_enabled 0")
        val instrument = InstrumentationRegistry.getInstrumentation()
        activity = instrument.startActivitySync(Intent(instrument.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        ui {
            vm.closePlayer(); vm.store.save(null); vm.switchServer("http://127.0.0.1:18080")
            vm.store.guestPreferences(AccountPreferences(autoplay = false)); vm.refreshSharedSettings(); vm.navigate("Popular")
        }
        until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
    }
    @After fun close() {
        compose.mainClock.autoAdvance = true
        shell(if (hardwareIme == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $hardwareIme")
        shell(if (handwriting == "null") "settings delete secure stylus_handwriting_enabled" else "settings put secure stylus_handwriting_enabled $handwriting")
        if (::activity.isInitialized) ui {
            vm.closePlayer(); vm.store.save(null); vm.store.guestPreferences(AccountPreferences()); activity.finishAndRemoveTask()
        }
    }
    private fun watchBounds() = compose.onNodeWithTag("watch-content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun assertSameBounds(expected: Rect) {
        val actual = watchBounds()
        assertEquals(expected.left, actual.left, .5f); assertEquals(expected.right, actual.right, .5f)
        assertEquals(expected.top, actual.top, .5f); assertEquals(expected.bottom, actual.bottom, .5f)
    }
    @Test fun watchViewportStaysFixedAcrossMiniChromeChangesAndCoversTheBottomInset() {
        for ((tab, dark) in listOf("Popular" to false, "Search" to true)) {
            ui {
                vm.closePlayer(); vm.store.guestPreferences(AccountPreferences(autoplay = false, darkMode = if (dark) "dark" else "light"))
                vm.refreshSharedSettings(); vm.query = "fixture"; vm.navigate(tab)
            }
            until { !vm.browse.value.loading && vm.browse.value.videos.isNotEmpty() }
            ui { vm.more() }; until { !vm.browse.value.loading }
            compose.onNodeWithTag("video-card-testvideo01").performClick()
            until { vm.playback.value.details != null && compose.onAllNodesWithTag("watch-content").fetchSemanticsNodes().isNotEmpty() }
            val expanded = watchBounds()
            ui { activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("mini-player-preview").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("mini-player-preview").performClick()
            repeat(12) { compose.mainClock.advanceTimeBy(80); assertSameBounds(expanded) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle(); assertSameBounds(expanded)
            capture("watch-restored-${if (dark) "dark" else "light"}")
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val pixel = screenshot.getPixel(screenshot.width / 10, screenshot.height - 20)
            val surface = if (dark) appColorScheme(true).surface else appColorScheme(false).surface
            assertEquals(surface.red, android.graphics.Color.red(pixel) / 255f, .02f)
            assertEquals(surface.green, android.graphics.Color.green(pixel) / 255f, .02f)
            assertEquals(surface.blue, android.graphics.Color.blue(pixel) / 255f, .02f)
            compose.mainClock.autoAdvance = false
            ui { activity.onBackPressedDispatcher.onBackPressed() }
            repeat(4) { compose.mainClock.advanceTimeBy(80); assertSameBounds(expanded) }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("mini-player-preview").assertIsDisplayed()
        }
    }
    @Test fun captchaFocusAndTypedAnswerStayAboveNavigationAndKeyboard() {
        command("accounts", """{"captcha":{"image":"http://127.0.0.1:18080/media/thumbnail.jpg","token":"audit-clock"}}""")
        ui { vm.navigate("You", "sign-in") }
        compose.onNodeWithTag("authentication-true").performScrollTo().performClick()
        until { compose.onAllNodesWithTag("account-captcha-answer").fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNodeWithTag("account-captcha-answer")
        field.performScrollTo().performClick()
        until { ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        field.performTextInput("4:00:00")
        compose.waitForIdle()
        val input = field.fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("navigation-You").assertCountEquals(0)
        val ime = ViewCompat.getRootWindowInsets(activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val imeTop = activity.window.decorView.height - ime
        val viewport = compose.onNodeWithTag("sign-in-viewport").fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithTag("app-toolbar").fetchSemanticsNode().boundsInRoot
        val title = compose.onNode(hasText("Sign in") and hasAnyAncestor(hasTestTag("app-toolbar"))).fetchSemanticsNode().boundsInRoot
        val status = ViewCompat.getRootWindowInsets(activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.statusBars()).top
        assertTrue("Focused CAPTCHA is covered by the keyboard: $input vs $imeTop", input.bottom <= imeTop)
        assertTrue("Form scroll viewport includes the pinned toolbar", viewport.top >= toolbar.bottom)
        assertTrue("Focused CAPTCHA is under the pinned toolbar", input.top >= viewport.top)
        assertTrue("Title was panned under the status bar", title.top >= status)
        field.assertTextContains("4:00:00")
        capture(if (activity.resources.configuration.fontScale > 1.5f) "captcha-keyboard-large" else "captcha-keyboard")
        shell("input keyevent 4")
    }
}
