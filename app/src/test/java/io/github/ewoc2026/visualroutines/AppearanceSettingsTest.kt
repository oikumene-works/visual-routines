package io.github.ewoc2026.visualroutines

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses the real app root and stored choices; it never injects a rendered palette. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private fun freshState() = VisualRoutinesState(
        SharedPreferencesSessionStore(application), application.routineRepository,
        SharedPreferencesSettingsStore(application),
    )
    private val optionTags = listOf("appearance-system", "appearance-light", "appearance-dark", "dark-style-standard", "dark-style-black")
    private var inputModeManager: InputModeManager? = null

    private fun launch(state: VisualRoutinesState, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                VisualRoutinesApp(state)
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, expected: ThemePalette) {
        val file = File("build/reports/amoled-finalization/$name.png")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        assertEquals(name, colorsForPalette(expected).background.toArgb(), bitmap.getPixel(1, bitmap.height - 2))
        bitmap.recycle()
    }

    private fun checkLayout(name: String, fontScale: Float = 1f) {
        val state = freshState().apply {
            updateDarkThemeStyle(DarkThemeStyle.Black)
            openSettings()
        }
        launch(state, fontScale)
        for ((mode, palette) in listOf(AppearanceMode.Light to ThemePalette.Light, AppearanceMode.Dark to ThemePalette.Black)) {
            compose.runOnIdle { state.updateAppearanceMode(mode) }
            compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
            capture("$name-${palette.name}-top", palette)
            for (tag in optionTags) {
                compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            }
            compose.onNodeWithTag("dark-style-black").assertIsSelected()
            if (mode == AppearanceMode.Light) {
                compose.onNodeWithTag("dark-style-black").assertIsNotEnabled()
                compose.onNodeWithText("Choose System or Dark to change this.").performScrollTo().assertIsDisplayed()
            } else {
                compose.onNodeWithTag("dark-style-black").assertIsEnabled()
                compose.onNodeWithText("Used when the appearance is dark.").performScrollTo().assertIsDisplayed()
            }
            capture("$name-${palette.name}-style", palette)
            for (label in listOf("Show example routines, On", "Allow routine editing, On")) {
                compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
            }
            for (label in listOf("Privacy policy", "Send feedback", "Copy email address")) {
                compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
            }
            capture("$name-${palette.name}-actions", palette)
            // Inspect all text layout results, including off-screen composed content.
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
                    results.forEach { result ->
                        val message = "$name: ${result.layoutInput.text}"
                        // The semantics result can retain the paragraph's available width while
                        // size uses the shorter text width. Check actual lines, not that container.
                        assertTrue(message, result.multiParagraph.height <= result.size.height + 1f)
                        repeat(result.lineCount) { line ->
                            assertFalse(message, result.isLineEllipsized(line))
                            assertTrue(message, result.getLineLeft(line) >= -1f)
                            assertTrue(message, result.getLineRight(line) <= result.size.width + 1f)
                        }
                    }
                }
        }
    }

    @Test fun phonePortrait() = checkLayout("phone-portrait")
    @Test @Config(qualifiers = "w832dp-h384dp-notnight-mdpi") fun phoneLandscape() = checkLayout("phone-landscape")
    @Test @Config(qualifiers = "w800dp-h1280dp-notnight-mdpi") fun tabletPortrait() = checkLayout("tablet-portrait")
    @Test @Config(qualifiers = "w1280dp-h800dp-notnight-mdpi") fun tabletLandscape() = checkLayout("tablet-landscape")
    @Test fun phoneLargeText() = checkLayout("phone-large", 2f)
    @Test @Config(qualifiers = "w832dp-h384dp-notnight-mdpi") fun phoneLandscapeLargeText() = checkLayout("phone-landscape-large", 2f)
    @Test @Config(qualifiers = "w800dp-h1280dp-notnight-mdpi") fun tabletLargeText() = checkLayout("tablet-large", 2f)
    @Test @Config(qualifiers = "w1280dp-h800dp-notnight-mdpi") fun tabletLandscapeLargeText() = checkLayout("tablet-landscape-large", 2f)

    @Test fun choiceRowsExposeOneSelectionAndDisableOnlyFixedLight() {
        val state = freshState().apply { updateAllowRoutineEditing(false); openSettings() }
        launch(state)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup), useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected), useUnmergedTree = true).assertCountEquals(5)
        compose.onNodeWithTag("appearance-system").assertIsSelected()
        compose.onNodeWithTag("dark-style-black").performScrollTo().assertIsEnabled().performClick().assertIsSelected()
        capture("system-light-black-preselected", ThemePalette.Light)
        assertEquals(DarkThemeStyle.Black, freshState().homeSettings.darkThemeStyle)
        compose.onNodeWithTag("appearance-light").performScrollTo().performClick()
        compose.onNodeWithTag("dark-style-black").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("dark-style-standard").performScrollTo().performClick()
        assertEquals(DarkThemeStyle.Black, state.homeSettings.darkThemeStyle)
        compose.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        compose.onNodeWithTag("dark-style-black").assertIsEnabled().assertIsSelected()
        capture("fixed-dark-black", ThemePalette.Black)
        compose.onNodeWithTag("dark-style-standard").performScrollTo().performClick()
        capture("fixed-dark-standard", ThemePalette.Dark)
        compose.onNodeWithTag("appearance-system").performScrollTo().performClick()
        capture("returned-to-system-light", ThemePalette.Light)
        assertFalse(freshState().homeSettings.allowRoutineEditing)
    }

    @Test fun systemChangesRecolorAndFixedModesIgnoreThemWithoutResettingProgress() {
        val state = freshState().apply {
            openHomeWithPausedSession("prepare-for-exercise", 2)
            updateDarkThemeStyle(DarkThemeStyle.Black)
        }
        val progress = state.activeSession
        val configuration = mutableStateOf(Configuration(application.resources.configuration))
        compose.setContent { CompositionLocalProvider(LocalConfiguration provides configuration.value) { VisualRoutinesApp(state) } }
        fun system(dark: Boolean) = compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
        }
        system(false); capture("system-sequence-light", ThemePalette.Light)
        system(true); capture("system-sequence-black", ThemePalette.Black)
        compose.runOnIdle { state.updateAppearanceMode(AppearanceMode.Light) }
        capture("fixed-light-system-dark", ThemePalette.Light)
        system(false); capture("fixed-light-system-light", ThemePalette.Light)
        compose.runOnIdle { state.updateAppearanceMode(AppearanceMode.Dark) }
        capture("fixed-black-system-light", ThemePalette.Black)
        system(true); capture("fixed-black-system-dark", ThemePalette.Black)
        compose.runOnIdle { state.updateAppearanceMode(AppearanceMode.System) }
        system(false); capture("resumed-system-following", ThemePalette.Light)
        assertEquals(progress, state.activeSession)
        assertEquals(progress, freshState().activeSession)
    }

    @Test fun selectedRowFocusAndScrollSurviveImmediatePaletteChanges() {
        val state = freshState().apply { updateAppearanceMode(AppearanceMode.Dark); openSettings() }
        launch(state)
        val row = compose.onNodeWithTag("dark-style-black").performScrollTo()
        compose.runOnIdle { assertTrue(requireNotNull(inputModeManager).requestInputMode(InputMode.Keyboard)) }
        row.performSemanticsAction(SemanticsActions.RequestFocus)
        row.assertIsFocused()
        val before = row.fetchSemanticsNode().boundsInRoot
        row.performClick()
        row.assertIsFocused().assertIsSelected()
        assertEquals(before, row.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun realPreferenceChangesAndSavedStateRestorationKeepEditorTextAndPausedProgress() {
        var state = freshState().apply {
            openHomeWithPausedSession("prepare-for-exercise", 2)
            openCreateRoutine(CreateRoutineDraft("Draft", listOf(RoutineStepContent("Keep the cloth."))))
        }
        val progress = state.activeSession
        val restoration = StateRestorationTester(compose)
        restoration.setContent { VisualRoutinesApp(state) }
        compose.onNodeWithText("Routine name").performTextReplacement("Retained draft")
        compose.runOnIdle {
            state.updateDarkThemeStyle(DarkThemeStyle.Black)
            state.updateAppearanceMode(AppearanceMode.Dark)
        }
        compose.onNodeWithText("Retained draft").assertExists()
        compose.runOnIdle { state = freshState() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Retained draft").assertExists()
        assertEquals(progress, state.activeSession)
        assertEquals(DarkThemeStyle.Black, state.homeSettings.darkThemeStyle)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(progress, state.activeSession)
    }
}
