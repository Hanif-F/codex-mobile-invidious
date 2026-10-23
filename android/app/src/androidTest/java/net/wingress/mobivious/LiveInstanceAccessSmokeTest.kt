package net.wingress.mobivious

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.wingress.mobivious.data.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Opt-in real-provider smoke. It creates only an instance pass, never changes server policy/accounts. */
@RunWith(AndroidJUnit4::class)
class LiveInstanceAccessSmokeTest {
    @get:Rule val compose = createServiceComposeRule()
    @Test fun realProviderCompletesWithoutAccountOrPolicyChanges() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveInstanceAccess") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val vm = activity.model
        val before = vm.store.server
        try {
            compose.runOnUiThread {
                vm.store.save(null); vm.switchServer("https://invidious.wingress.net")
                vm.store.verificationPersistence.write(InstanceAccess.origin(vm.store.server.toHttpUrl()), null)
            }
            compose.waitUntil(60_000) { !vm.browse.value.loading }
            val status = runBlocking { vm.access.discover(vm.api.context()) }
            assertNotNull(status)
            Assume.assumeTrue("Live verification must be enabled and required", status!!.enabled && status.required)
            if (status.enabled && status.required) {
                compose.runOnUiThread { vm.access.contextChanged(); vm.access.report(vm.api.context(), AccessReason.VERIFICATION) }
                compose.onNodeWithTag("instance-access-action").assertDoesNotExist()
                compose.waitUntil(120_000) { vm.access.pass(vm.api.context()) != null }
                assertTrue(runBlocking { vm.access.discover(vm.api.context()) }!!.verified)
                compose.waitUntil(60_000) { !vm.browse.value.loading }
                compose.onNodeWithTag("instance-verification").assertDoesNotExist()
                compose.onNodeWithText("Access restored").assertDoesNotExist()
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(activity.filesDir, "access-live-complete.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            assertNull(vm.account.value)
        } finally {
            compose.runOnUiThread {
                vm.access.contextChanged()
                if (InstrumentationRegistry.getArguments().getString("liveKeepInstance") != "true") vm.switchServer(before)
                activity.finishAndRemoveTask()
            }
        }
    }
}
