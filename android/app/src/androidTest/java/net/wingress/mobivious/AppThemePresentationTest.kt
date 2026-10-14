package net.wingress.mobivious

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wingress.mobivious.data.AccentPreset
import net.wingress.mobivious.data.SessionStore
import net.wingress.mobivious.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppThemePresentationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun twelvePalettesRenderOpaqueComponentsAndKeepMenusAndFocusedEdits() {
        var dark by mutableStateOf(false)
        var accent by mutableStateOf(AccentPreset.BLUE)
        var keyboard: SoftwareKeyboardController? = null
        compose.setContent { AppTheme(dark,accent) {
            keyboard = LocalSoftwareKeyboardController.current
            val view=LocalView.current
            SideEffect { (view.context as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
                }
            } }
            assertEquals(MaterialTheme.colorScheme.onSurface,LocalContentColor.current)
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp).testTag("component-gallery"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Material components",style=MaterialTheme.typography.titleLarge)
                var draft by remember {mutableStateOf("")}
                OutlinedTextField(draft,{draft=it},label={Text("Focused edit")},modifier=Modifier.fillMaxWidth().testTag("gallery-field"))
                AppSection { Text("Preference group");Text("Supporting text",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {Text("Enabled choice",Modifier.weight(1f));Switch(true,{})} }
                Row {Button({}) {Text("Primary")};TextButton({}) {Text("Text action")};AppIconButton(Icons.Default.Check,"Confirm",Modifier.testTag("gallery-confirm")) {}}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(false,{}, {Text("Choice")})
                    AppFilterChip(true,{}, {Text("Selected")})
                    Checkbox(false,{})
                    RadioButton(true,{})
                }
                Slider(.5f,{},modifier=Modifier.fillMaxWidth())
                DropdownChoiceRow("Choose an item",listOf("one" to "Keep this menu open"),"one") {}
            }
        } }
        val field=compose.onNodeWithTag("gallery-field")
        field.performClick().performTextInput("Retained edit")
        for(night in listOf(false,true)) for(preset in AccentPreset.entries) {
            compose.runOnIdle {dark=night;accent=preset;keyboard?.hide()}
            field.assertIsFocused().assertTextContains("Retained edit")
            compose.onNodeWithTag("gallery-confirm").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val pixel=compose.onNodeWithTag("component-gallery").captureToImage().toPixelMap()[0,0]
            val expected=appColorScheme(night,preset).surface
            assertEquals(expected.red,pixel.red,.01f);assertEquals(expected.green,pixel.green,.01f)
            captureWatchScreenshot("opaque-gallery-${if(night) "dark" else "light"}-${preset.id}")
        }
        compose.onNodeWithText("Choose an item").performScrollTo().performClick()
        val item=compose.onNode(hasText("Keep this menu open") and hasAnyAncestor(isPopup()))
        for(preset in AccentPreset.entries) {
            compose.runOnIdle {accent=preset;dark=!dark}
            item.assertIsDisplayed()
        }
        item.performClick();field.assertTextContains("Retained edit")
    }
    @Test fun appearanceMigrationRespectsExplicitAmbientAndIgnoresDormantGlassKeys() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("mobivious",0)
        val keys=listOf("appearance.ambientMode","appearance.accentPreset","appearance.coloredBackgrounds","appearance.adaptiveControlColors")
        val previous=keys.associateWith {prefs.all[it]}
        try {
            prefs.edit().remove(keys[0]).putString(keys[1],"unknown").putBoolean(keys[2],true).putBoolean(keys[3],true).commit()
            assertFalse(SessionStore(context).ambientMode.value)
            assertEquals(AccentPreset.BLUE,SessionStore(context).accentPreset.value)
            for(saved in listOf(true,false)) {
                prefs.edit().putBoolean(keys[0],saved).commit()
                val store=SessionStore(context);assertEquals(saved,store.ambientMode.value)
                store.accentPreset(AccentPreset.TEAL)
                assertEquals(AccentPreset.TEAL,SessionStore(context).accentPreset.value)
                assertEquals(saved,SessionStore(context).ambientMode.value)
            }
        } finally {
            prefs.edit().apply {previous.forEach { (key,value) -> when(value) {
                is Boolean -> putBoolean(key,value);is String ->putString(key,value);else ->remove(key)
            } }}.commit()
        }
    }
}
