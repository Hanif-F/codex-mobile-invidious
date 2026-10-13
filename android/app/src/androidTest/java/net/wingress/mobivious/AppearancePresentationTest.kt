package net.wingress.mobivious

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preferencesDefaultOffAndPersistAcrossStoresAndContexts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "appearance-controls-test-${System.nanoTime()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
        try {
            val store = SessionStore(isolated)
            assertFalse(store.coloredBackgrounds.value)
            assertFalse(store.adaptiveControlColors.value)
            assertTrue(store.ambientMode.value)
            store.coloredBackgrounds(true)
            store.adaptiveControlColors(true)
            assertTrue(store.coloredBackgrounds.value)
            assertTrue(store.adaptiveControlColors.value)
            store.server = "https://another.example"
            store.save(null)
            val restored = SessionStore(isolated)
            assertTrue(restored.coloredBackgrounds.value)
            assertTrue(restored.adaptiveControlColors.value)
            restored.coloredBackgrounds(false)
            assertFalse(SessionStore(isolated).coloredBackgrounds.value)
            assertTrue(SessionStore(isolated).adaptiveControlColors.value)
            restored.adaptiveControlColors(false)
            assertFalse(SessionStore(isolated).adaptiveControlColors.value)
            assertTrue(restored.ambientMode.value)
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun backgroundSettingsReachPagesOverlayFallbacksAndGeneratedRecordings() {
        var dark by mutableStateOf(false)
        var colored by mutableStateOf(false)
        var adaptive by mutableStateOf(false)
        val scenes = mutableMapOf<String, GlassScene>()
        compose.setContent {
            MaterialTheme(colorScheme = liquidColorScheme(dark, colored)) {
                CompositionLocalProvider(LocalColoredBackgrounds provides colored,
                    LocalAdaptiveControlColors provides adaptive) {
                    Column {
                        GlassBackgroundScene(Modifier.size(280.dp, 150.dp).testTag("page-background")) {
                            val scene = LocalGlassScene.current!!
                            SideEffect { scenes["page"] = scene }
                        }
                        // An inherited originating-window palette must not bring color
                        // back into an overlay fallback after the setting is disabled.
                        CompositionLocalProvider(LocalGlassEnvironmentPalette provides
                            GlassEnvironmentPalette(Color.Magenta, listOf(Color.Blue, Color.Cyan, Color.Red))) {
                            GlassOverlayScene(null, Modifier.size(280.dp, 150.dp).testTag("overlay-background")) {
                                val scene = LocalGlassScene.current!!
                                SideEffect { scenes["overlay"] = scene }
                            }
                        }
                        val fallback = rememberGlassScene(environmentOnly = true)
                        SideEffect { scenes["generated"] = fallback }
                        Box(Modifier.size(280.dp, 150.dp).recordGlassScene(fallback))
                    }
                }
            }
        }
        for (isDark in listOf(false, true)) for (isAdaptive in listOf(false, true)) {
            // Check a return to neutral as well as the initial default.
            for (isColored in listOf(false, true, false)) {
                compose.runOnIdle { dark = isDark; colored = isColored; adaptive = isAdaptive }
                val base = neutralGlassSurface(isDark)
                compose.waitUntil(5_000) {
                    scenes.size == 3 && scenes.values.all { scene ->
                        val pixels = scene.samples
                        pixels != null && (if (isColored) pixels.any { pixel ->
                            val color = Color(pixel)
                            kotlin.math.abs(color.red - color.blue) > .015f
                        } else pixels.all { pixel ->
                            val color = Color(pixel)
                            kotlin.math.abs(color.red - base.red) < .005f &&
                                kotlin.math.abs(color.green - base.green) < .005f &&
                                kotlin.math.abs(color.blue - base.blue) < .005f
                        })
                    }
                }
                for (tag in listOf("page-background", "overlay-background")) {
                    val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                    val actual = pixels[pixels.width / 2, pixels.height / 2]
                    if (!isColored) {
                        assertEquals(base.red, actual.red, .005f)
                        assertEquals(base.green, actual.green, .005f)
                        assertEquals(base.blue, actual.blue, .005f)
                    } else assertTrue("Colored lighting must be visible in $tag",
                        kotlin.math.abs(actual.red - actual.blue) > .015f)
                }
                if (!isColored) compose.runOnIdle {
                    for (scene in scenes.values) {
                        val bounds = scene.contrastAt(scene.bounds)
                        assertTrue(bounds.bounded)
                        assertEquals(base, bounds.darkest)
                        assertEquals(base, bounds.brightest)
                    }
                }
            }
        }
    }

    @Test fun foregroundsFollowThemeUnlessAdaptiveAndKeepContrastOverChangingArtwork() {
        var dark by mutableStateOf(true)
        var adaptive by mutableStateOf(false)
        var opaque by mutableStateOf(false)
        var background by mutableStateOf(Color.White)
        var detailed by mutableStateOf(false)
        lateinit var scene: GlassScene
        val foregrounds = mutableMapOf<GlassMaterialRole, GlassContentColors>()
        compose.setContent {
            MaterialTheme(colorScheme = liquidColorScheme(dark, false)) {
                CompositionLocalProvider(LocalAdaptiveControlColors provides adaptive,
                    LocalReduceTransparency provides opaque) {
                    scene = rememberGlassScene()
                    Box(Modifier.size(320.dp, 300.dp)) {
                        Canvas(Modifier.matchParentSize().recordGlassScene(scene)) {
                            drawRect(background)
                            if (detailed) {
                                val cell = 12.dp.toPx()
                                for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
                                    if ((x + y) % 2 == 0) drawRect(Color.White, Offset(x * cell, y * cell), Size(cell, cell))
                                }
                            }
                        }
                        CompositionLocalProvider(LocalGlassScene provides scene, LocalGlassBackdrop provides scene.backdrop) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                for (role in listOf(GlassMaterialRole.Control, GlassMaterialRole.Section)) {
                                    GlassSurface(Modifier.fillMaxWidth().height(90.dp), role = role) {
                                        val colors = LocalGlassContentColors.current
                                        SideEffect { foregrounds[role] = colors }
                                        GlassLabel("Readable control", Modifier.padding(20.dp).testTag("appearance-label-$role"),
                                            color = colors.secondary)
                                    }
                                }
                            }
                            val media = rememberGlassContentColors(role = GlassMaterialRole.MediaOverlay)
                            SideEffect { assertEquals(GlassContentColors.light.primary, media.primary) }
                        }
                    }
                }
            }
        }
        for (isDark in listOf(true, false)) for (isAdaptive in listOf(false, true, false)) {
            for (kind in listOf("bright", "dark", "detailed", "opaque")) {
                compose.runOnIdle {
                    dark = isDark; adaptive = isAdaptive; opaque = kind == "opaque"
                    background = if (kind == "bright" || kind == "opaque") Color.White else Color.Black
                    detailed = kind == "detailed"
                }
                val lightInk = if (!isAdaptive || kind == "opaque" || kind == "detailed") isDark else kind == "dark"
                compose.waitUntil(5_000) {
                    (opaque || scene.samples != null && scene.backgroundAt(scene.bounds).let {
                        when (kind) { "bright" -> it.red > .99f; "dark" -> it.red < .01f; else -> it.red in .2f.. .8f }
                    }) && foregrounds.size == 2 && (kind == "detailed" && isAdaptive ||
                        foregrounds.values.all { it.primary == if (lightInk) GlassContentColors.light.primary else GlassContentColors.dark.primary })
                }
                compose.waitForIdle()
                for ((role, colors) in foregrounds) {
                    if (!isAdaptive) assertEquals("Fixed colors must follow the app theme", isDark, colors.primary.luminance() > .5f)
                    val pixels = compose.onNodeWithTag("appearance-label-$role").captureToImage().toPixelMap()
                    for (y in listOf(1, pixels.height - 2)) {
                        val backgroundPixel = pixels[pixels.width / 2, y]
                        val ratio = glassContrast(colors.secondary, backgroundPixel)
                        assertTrue("Unreadable $role: dark=$isDark adaptive=$isAdaptive backdrop=$kind colors=$colors pixel=$backgroundPixel contrast=$ratio",
                            ratio >= 4.5f)
                    }
                }
            }
        }
    }

    @Test fun appearanceChangesPreserveOpenMenusAndFocusedFieldDrafts() {
        var dark by mutableStateOf(false)
        var colored by mutableStateOf(false)
        var adaptive by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = liquidColorScheme(dark, colored)) {
                CompositionLocalProvider(LocalColoredBackgrounds provides colored,
                    LocalAdaptiveControlColors provides adaptive) {
                    GlassBackgroundScene(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(24.dp)) {
                            var text by remember { mutableStateOf("") }
                            OutlinedTextField(text, { text = it }, Modifier.testTag("appearance-draft"))
                            DropdownChoiceRow("Choose an item", listOf("one" to "Keep this menu open"), "one") {}
                        }
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("appearance-draft")
        field.performClick().performTextInput("Unsaved draft")
        for ((color, adapt, night) in listOf(Triple(true, false, false), Triple(true, true, true), Triple(false, false, false))) {
            compose.runOnIdle { colored = color; adaptive = adapt; dark = night }
            field.assertIsFocused().assertTextContains("Unsaved draft")
        }
        compose.onNodeWithText("Choose an item").performClick()
        val menuItem = compose.onNode(hasText("Keep this menu open") and hasAnyAncestor(isPopup()))
        for ((color, adapt, night) in listOf(Triple(true, false, false), Triple(true, true, true), Triple(false, false, false))) {
            compose.runOnIdle { colored = color; adaptive = adapt; dark = night }
            menuItem.assertIsDisplayed()
        }
        menuItem.performClick()
        menuItem.assertDoesNotExist()
        field.assertTextContains("Unsaved draft")
    }
}
