package net.wingress.mobivious.benchmarks

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Only ordinary app controls configure the isolated target. No app seeding endpoint. */
@RunWith(AndroidJUnit4::class)
class UiScrollBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val args = InstrumentationRegistry.getArguments()
    private var currentJourney: String? = null
    private var gesturePhase = "setup"
    private val gestures = mutableListOf<JSONObject>()

    @After fun saveGestureDurations() {
        val journey=currentJourney ?: return
        val run=args.getString("evidenceRun") ?: error("Missing evidence run ID")
        check(run.matches(Regex("[a-z0-9-]+")))
        val json=org.json.JSONArray(gestures).toString()
        val directory=java.io.File(InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first(),"additional_test_output").apply {mkdirs()}
        java.io.File(directory,"mobivious-gestures-$run-$journey.json").writeText(json)
    }

    private fun required(selector: BySelector) = checkNotNull(device.wait(Until.findObject(selector), 20_000)) {
        val hierarchy = java.io.ByteArrayOutputStream()
        runCatching {
            device.executeShellCommand("screencap -p /data/local/tmp/mobivious-benchmark-setup.png")
            device.dumpWindowHierarchy(hierarchy)
        }
        "Required benchmark content missing: $selector\n${hierarchy.toString("UTF-8")}"
    }
    private fun click(text: String) {
        val selector=By.text(text)
        var bounds=required(selector).visibleBounds
        var stableSince=SystemClock.uptimeMillis()
        val deadline=stableSince+5000
        while(SystemClock.uptimeMillis()-stableSince<200) {
            SystemClock.sleep(50)
            val next=required(selector).visibleBounds
            if(next!=bounds) {bounds=next;stableSince=SystemClock.uptimeMillis()}
            check(SystemClock.uptimeMillis()<deadline) {"Control did not settle: $text"}
        }
        required(selector).click()
    }
    private fun desc(text: String) = required(By.desc(text)).click()
    private fun navigateControl(control: BySelector, destination: BySelector) {
        val deadline=SystemClock.uptimeMillis()+20_000
        do {
            device.findObject(control)?.click()
            if(device.wait(Until.hasObject(destination),500)) return
        } while(SystemClock.uptimeMillis()<deadline)
        required(destination)
    }
    private fun reveal(selector: BySelector, minHeight: Int = (16*InstrumentationRegistry.getInstrumentation().context.resources.displayMetrics.density).toInt()) {
        repeat(40) {
            if ((device.findObject(selector)?.visibleBounds?.height() ?: 0)>=minHeight) return
            swipe(true)
        }
        error("Benchmark control is outside the viewport: $selector")
    }
    private fun fixture(density: String = "balanced", thin: Boolean = false, cold: Boolean = false, pagination: Boolean = false, mixed: Boolean = false, downloads: Boolean = false, saving: Boolean = false) {
        (URL("http://127.0.0.1:18080/test/performance").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            outputStream.use { it.write(JSONObject().put("density",density).put("thin",thin).put("cold",cold).put("pagination",pagination).put("mixed",mixed).put("downloads",downloads).put("saving",saving).toString().toByteArray()) }
            check(responseCode == 200); inputStream.close(); disconnect()
        }
    }
    private fun scrollTo(text: String) {
        reveal(By.text(text))
    }
    private fun openSettings() {
        root("You")
        navigateControl(By.desc("Settings"),By.res("settings-root"))
    }
    private fun openPreferencePage(name: String) = navigateControl(By.text(name),By.desc("Back from $name"))
    private fun root(tab: String) {
        navigateControl(By.res("navigation-$tab"),
            if(tab=="You") By.res("you-identity") else By.res("navigation-$tab").selected(true))
    }
    @Before fun configureThroughUi() {
        Configurator.getInstance().waitForIdleTimeout=0
        fixture()
        device.executeShellCommand("am force-stop $TARGET")
        val launch = InstrumentationRegistry.getInstrumentation().context.packageManager.getLaunchIntentForPackage(TARGET)!!
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        InstrumentationRegistry.getInstrumentation().context.startActivity(launch)
        openSettings(); scrollTo("Server"); openPreferencePage("Server")
        required(By.clazz("android.widget.EditText")).text = "http://127.0.0.1:18080"
        click("Connect")
        val deadline=SystemClock.uptimeMillis()+20_000
        var confirmed=false;var discarded=false
        val shellRoot=By.res("navigation-Popular")
        while(!device.hasObject(By.res("settings-root")) && !device.hasObject(shellRoot)) {
            if(!confirmed && device.hasObject(By.text("Change server?"))) {
                device.findObjects(By.text("Connect")).last().click();confirmed=true
            }
            if(!discarded && device.hasObject(By.text("Continue"))) {click("Continue");discarded=true}
            if(SystemClock.uptimeMillis()>=deadline) required(By.res("settings-root"))
            SystemClock.sleep(50)
        }
        if(device.hasObject(By.res("settings-root")))
            navigateControl(By.desc("Back from Settings"),By.res("navigation-You"))
        root("You")
        if (!device.hasObject(By.text("Fixture").clazz("android.widget.TextView"))) {
            click("Sign in")
            required(By.res("account-username")).text = "Fixture"
            required(By.res("account-password")).text = "benchmark-fixture-password"
            reveal(By.res("account-auth-submit"),(48*InstrumentationRegistry.getInstrumentation().context.resources.displayMetrics.density).toInt())
            required(By.res("account-auth-submit").enabled(true)).click()
            required(By.text("Fixture").clazz("android.widget.TextView"))
            required(By.text("Your library"))
        }
    }
    private fun waitForAccountPreferences() {
        val deadline = SystemClock.uptimeMillis() + 20_000
        do {
            val state = JSONObject(URL("http://127.0.0.1:18080/test/state").readText())
            val requests = state.getJSONArray("preferencesRequests")
            if ((0 until requests.length()).any { requests.getJSONObject(it).optBoolean("completed") }) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Benchmark account preferences were not loaded; guest results are not comparable")
    }
    private fun swipe(forward: Boolean) {
        val view = scrollBounds ?: required(By.scrollable(true)).visibleBounds
        val x = view.centerX()
        val low = view.top + (view.height() * .85).toInt()
        val high = view.top + (view.height() * .15).toInt()
        val startY=if(forward) low else high
        val endY=if(forward) high else low
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val down=SystemClock.uptimeMillis()
        fun inject(action: Int,y: Float) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x.toFloat(),y,0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event,false)) {"Swipe input injection failed"} }
            finally {event.recycle()}
        }
        // Schedule ordinary touch events by elapsed time to keep the specified
        // 400ms gesture independent of refresh rate and renderer speed.
        inject(MotionEvent.ACTION_DOWN,startY.toFloat())
        var duration=0L
        try {
            while(SystemClock.uptimeMillis()-down<400) {
                val fraction=(SystemClock.uptimeMillis()-down)/400f
                inject(MotionEvent.ACTION_MOVE,startY+(endY-startY)*fraction)
                val remaining=400-(SystemClock.uptimeMillis()-down)
                if(remaining>0) SystemClock.sleep(minOf(10,remaining))
            }
        } finally {
            duration=SystemClock.uptimeMillis()-down
            inject(MotionEvent.ACTION_UP,endY.toFloat())
        }
        gestures.add(JSONObject().put("phase",gesturePhase).put("durationMs",duration)
            .put("injectionCompletionMs",SystemClock.uptimeMillis()-down).put("viewportHeight",view.height()))
        if(gesturePhase=="measure") check(duration in 400..475) {"400ms swipe exceeded scheduling tolerance: $duration ms"}
    }
    private var scrollBounds: android.graphics.Rect? = null
    private fun scrollJourney() {
        repeat(6) { swipe(true); SystemClock.sleep(100) }
        repeat(6) { swipe(false); SystemClock.sleep(100) }
    }
    private fun runJourney(id: String, density: String = "balanced", thin: Boolean = false,
        cold: Boolean = false, pagination: Boolean = false, mixed: Boolean = false,
        downloads: Boolean = false, saving: Boolean = false,
        prepare: () -> Unit, measure: () -> Unit = ::scrollJourney) {
        currentJourney=id
        fun seed() = fixture(density,thin,cold,pagination,mixed,downloads,saving)
        if (args.getString("recordVideo") == "true") {
            device.executeShellCommand("am force-stop $TARGET"); seed()
            val launch = InstrumentationRegistry.getInstrumentation().context.packageManager.getLaunchIntentForPackage(TARGET)!!
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            InstrumentationRegistry.getInstrumentation().context.startActivity(launch)
            waitForAccountPreferences(); prepare()
            scrollBounds=device.findObject(By.scrollable(true))?.visibleBounds
            val recordingRun = args.getString("recordingRun") ?: error("Recording requires a unique run ID")
            check(recordingRun.matches(Regex("[a-z0-9-]+")))
            val file="/data/local/tmp/mobivious-recording-$recordingRun-$id.mp4"
            val script=java.io.File(InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first(),"start-recording.sh")
            script.writeText("screenrecord --size 720x1280 --time-limit 30 --bit-rate 4000000 $file >/data/local/tmp/mobivious-recording.log 2>&1 < /dev/null & echo \$!\n")
            val pid=device.executeShellCommand("sh ${script.absolutePath}").trim().toInt()
            try { SystemClock.sleep(1000); gesturePhase="measure"; measure(); SystemClock.sleep(1000) }
            finally { device.executeShellCommand("kill -2 $pid"); SystemClock.sleep(1500) }
            return
        }
        // Warm-up and compilation never enter the timing region. Every iteration
        // reloads account preferences after the fixture is seeded, before navigation.
        benchmark.measureRepeated(
            packageName = TARGET, metrics = listOf(FrameTimingMetric()), iterations = 10,
            compilationMode = CompilationMode.Full(),
            setupBlock = {
                gesturePhase="setup"
                killProcess(); seed(); startActivityAndWait(); waitForAccountPreferences(); prepare()
                scrollBounds = device.findObject(By.scrollable(true))?.visibleBounds
                if (!cold && !downloads && !saving && scrollBounds != null) {
                    // Prime target-owned image caches using ordinary scrolling. A
                    // second process start retains disk art while resetting list/navigation state.
                    scrollJourney(); killProcess(); seed(); startActivityAndWait(); waitForAccountPreferences(); prepare()
                    scrollBounds = device.findObject(By.scrollable(true))?.visibleBounds
                }
                SystemClock.sleep(300)
            }, measureBlock = { gesturePhase="measure"; measure() }
        )
    }
    private fun feed(tab: String) {
        root(tab)
        if (device.hasObject(By.desc("Refresh $tab"))) desc("Refresh $tab")
        required(By.textContains("Benchmark video"))
    }
    @Test fun popular() = runJourney("popular", prepare = { feed("Popular") })
    @Test fun popularCompact() = runJourney("popular-compact", density = "compact", prepare = { feed("Popular") })
    @Test fun popularTextOnly() = runJourney("popular-text", thin = true, prepare = { feed("Popular") })
    @Test fun popularColdArt() = runJourney("popular-cold-art", cold = true, prepare = { feed("Popular") })
    // Popular is a single-page API. Exercise real load-more behavior on Search.
    @Test fun searchPagination() = runJourney("search-pagination", pagination = true, prepare = {
        root("Popular"); desc("Search")
        required(By.clazz("android.widget.EditText")).text = "benchmark"
        device.pressEnter(); required(By.textContains("Benchmark video"))
    }, measure = {
        scrollTo("Load more")
        required(By.text("Load more")).click()
        repeat(8) { swipe(true); SystemClock.sleep(100) }
        required(By.text(java.util.regex.Pattern.compile("Benchmark video 0[3-5][0-9]")))
    })
    @Test fun trending() = runJourney("trending", prepare = { feed("Trending") })
    @Test fun trendingChanges() = runJourney("trending-changes", prepare = {
        feed("Trending"); required(By.res("trending-livestreams")).click()
        required(By.text("Benchmark video 000 · livestreams US"))
    }, measure = {
        required(By.res("trending-gaming")).click(); required(By.text("Benchmark video 000 · gaming US"))
        required(By.res("trending-region")).click()
        required(By.res("region-search")).text = "JP"
        required(By.res("region-JP")).click(); required(By.text("Benchmark video 000 · gaming JP"))
    })
    @Test fun subscriptions() = runJourney("subscriptions", prepare = { feed("Subscriptions") })
    @Test fun channels() = runJourney("channels", prepare = {
        root("Subscriptions"); click("Channels"); required(By.textContains("Benchmark creator"))
    })
    @Test fun search() = runJourney("search", prepare = {
        root("Popular"); desc("Search")
        required(By.clazz("android.widget.EditText")).text = "benchmark"
        device.pressEnter(); required(By.textContains("Benchmark video"))
    })
    @Test fun mixedSearch() = runJourney("mixed-search", mixed = true, prepare = {
        root("Popular"); desc("Search"); required(By.clazz("android.widget.EditText")).text = "benchmark"
        device.pressEnter(); required(By.textContains("Benchmark video"))
    })
    @Test fun history() = runJourney("history", prepare = {
        root("You"); required(By.res("you-history")).click(); required(By.textContains("Benchmark video"))
    })
    @Test fun playlists() = runJourney("playlists", prepare = {
        root("You")
        required(By.res("you-playlists")).click()
        required(By.textContains("Benchmark playlist"))
    })
    @Test fun settings() = runJourney("settings", prepare = {
        root("You"); openSettings(); openPreferencePage("Playback"); required(By.text("Autoplay opened videos"))
    })
    @Test fun settingsSaving() = runJourney("settings-saving", saving = true, prepare = {
        root("You"); openSettings(); openPreferencePage("Playback"); required(By.text("Autoplay opened videos"))
    }, measure = {
        click("Autoplay opened videos")
        required(By.text("Retry")).click()
        scrollTo("Default speed"); click("Default speed"); click("1.5×")
        val deadline=SystemClock.uptimeMillis()+20_000
        do {
            val saved=JSONObject(URL("http://127.0.0.1:18080/test/state").readText()).getJSONObject("preferences")
            if (!saved.getBoolean("autoplay") && saved.getDouble("speed")==1.5) return@runJourney
            SystemClock.sleep(100)
        } while(SystemClock.uptimeMillis()<deadline)
        error("Benchmark preference changes were not confirmed")
    })
    @Test fun downloads() = runJourney("downloads", downloads = true, prepare = {
        root("You"); required(By.res("you-downloads")).click()
        val downloadAction = By.res(java.util.regex.Pattern.compile("download-actions-.*"))
        repeat(12) {
            device.findObject(downloadAction)?.let {
                val id=it.resourceName
                it.click(); click("Delete"); required(By.text("Delete download?")); click("Delete")
                check(device.wait(Until.gone(By.res(id)),20_000)) {"Prior benchmark download did not delete"}
            }
        }
        repeat(3) { index ->
            feed("Popular")
            val selector = By.res("video-actions-perf%07d".format(index))
            repeat(12) { if (!device.hasObject(selector)) swipe(true) }
            required(selector).click(); click("Download")
            required(By.res("download-choice-v360")).click()
            required(By.res("download-confirm").enabled(true)).click()
        }
        root("You"); required(By.res("you-downloads")).click(); required(By.textContains("Downloading"))
    }, measure = { SystemClock.sleep(7000) })
    @Test fun watch() = runJourney("watch", prepare = {
        feed("Popular"); click("Benchmark video 000"); required(By.text("Benchmark playing video"))
        required(By.desc("Pause"))
    })
    @Test fun navigation() = runJourney("navigation", prepare = { feed("Popular") }, measure = {
        listOf("Trending", "Subscriptions", "You", "Popular").forEach {root(it);SystemClock.sleep(350)}
        required(By.res("video-actions-perf0000000")).click()
        required(By.text("Save to playlist")); SystemClock.sleep(350); device.pressBack()
        desc("Search");required(By.clazz("android.widget.EditText"));SystemClock.sleep(350);desc("Close search")
        click("Benchmark video 000"); required(By.desc("Pause"))
        navigateControl(By.desc("Minimize player"),By.res("mini-player"))
        navigateControl(By.text("Benchmark playing video"),By.desc("Minimize player"))
        required(By.desc("Pause"));navigateControl(By.desc("Minimize player"),By.res("mini-player"))
    })
    companion object { const val TARGET = "net.wingress.mobivious.benchmark" }
}
