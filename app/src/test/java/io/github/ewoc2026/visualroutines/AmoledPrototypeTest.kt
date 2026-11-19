package io.github.ewoc2026.visualroutines

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment

/** Renders the real app root with transient palettes; no selector or preference is introduced. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmoledPrototypeTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private val palette = mutableStateOf<ThemePalette?>(ThemePalette.Black)
    private var hostView: View? = null

    private fun freshState() = VisualRoutinesState(
        SharedPreferencesSessionStore(application), application.routineRepository,
        SharedPreferencesSettingsStore(application),
    )

    private fun launch(state: VisualRoutinesState, fontScale: Float = 1f) {
        // Native AndroidView controls also need the resource font scale, not just LocalDensity.
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            hostView = LocalView.current.rootView
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                VisualRoutinesApp(state, palette.value)
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, background: Color = Color.Black, dialog: Boolean = false) {
        val output = File("build/reports/amoled-prototype/$name.png")
        output.parentFile!!.mkdirs()
        // These are inspected evidence captures, not auto-updated golden baselines.
        val options = RoborazziOptions(taskType = RoborazziTaskType.Record)
        if (dialog) {
            compose.onAllNodes(isRoot()).onLast().captureRoboImage(output.absolutePath, roborazziOptions = options)
        } else {
            compose.onRoot().captureRoboImage(output.absolutePath, roborazziOptions = options)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(output.absolutePath))
            // These previously uncovered app-root regions include the completion screen.
            assertEquals("$name top corner", background.toArgb(), bitmap.getPixel(1, 1))
            assertEquals("$name bottom corner", background.toArgb(), bitmap.getPixel(bitmap.width - 2, bitmap.height - 2))
            bitmap.recycle()
        }
    }

    private fun checkLayout(name: String, fontScale: Float = 1f) {
        val state = freshState()
        state.openRunnerAt("prepare-for-exercise", 1)
        launch(state, fontScale)
        compose.onNodeWithText("Previous").assertIsNotEnabled()
        compose.onNodeWithText("Done").assertIsDisplayed().assertHasClickAction()
        capture("$name-runner")
        compose.runOnIdle { state.openComplete("prepare-for-exercise") }
        compose.onNodeWithText("Return to routine list").performScrollTo().assertIsDisplayed()
        capture("$name-complete")
        compose.runOnIdle {
            state.openCreateRoutine(CreateRoutineDraft("My routine", listOf(RoutineStepContent("Take the cloth."))))
        }
        compose.onNodeWithTag("step-1-image-action").performScrollTo().assertIsDisplayed()
        capture("$name-editor")
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun phonePortrait() = checkLayout("phone-portrait")
    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscape() = checkLayout("phone-landscape")
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletPortrait() = checkLayout("tablet-portrait")
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletLandscape() = checkLayout("tablet-landscape")
    @Test fun phoneLargeText() = checkLayout("phone-large-text", 2f)
    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscapeLargeText() = checkLayout("phone-landscape-large-text", 2f)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletLargeText() = checkLayout("tablet-large-text", 2f)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletLandscapeLargeText() = checkLayout("tablet-landscape-large-text", 2f)

    @Test fun blackGalleryIncludesCardsSettingsAndDialogs() {
        val state = freshState()
        launch(state)
        compose.onNodeWithText("Visual Routines").assertIsDisplayed()
        capture("gallery-home")
        compose.runOnIdle { state.openHomeWithPausedSession("prepare-for-exercise", 2) }
        capture("gallery-paused")
        compose.runOnIdle { state.openSettings() }
        capture("gallery-settings")
        compose.onNodeWithText("Copy email address").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.openPrivacyPolicy() }
        capture("gallery-privacy")
        compose.runOnIdle { state.openManageRoutines() }
        capture("gallery-management")
        compose.runOnIdle { state.openExampleChooser() }
        capture("gallery-examples")
        compose.runOnIdle { state.openRoutineStart("prepare-for-exercise") }
        capture("gallery-start")
        compose.runOnIdle { state.openRunnerAt("prepare-for-exercise", 1); state.requestPause() }
        compose.onNodeWithText(application.getString(R.string.pause_title)).assertIsDisplayed()
        capture("gallery-pause-dialog", dialog = true)
    }

    @Test fun transientPaletteChangePreservesProgressAndExistingSettings() {
        val state = freshState()
        state.updateShowExampleRoutines(false)
        state.updateAllowRoutineEditing(false)
        state.openHomeWithPausedSession("prepare-for-exercise", 2)
        val progress = state.activeSession
        val preferences = application.getSharedPreferences("home_settings", Context.MODE_PRIVATE)
        val stored = preferences.all.toMap()
        launch(state)
        for (choice in listOf(ThemePalette.Light, ThemePalette.Dark, ThemePalette.Black)) {
            compose.runOnIdle { palette.value = choice }
            capture("palette-${choice.name.lowercase()}", colorsForPalette(choice).background)
            compose.runOnIdle {
                assertEquals(progress, state.activeSession)
                assertEquals(stored, preferences.all)
                assertEquals(state.homeSettings, freshState().homeSettings)
                assertEquals(AppScreen.Home, state.screen)
            }
        }
    }

    @Test fun paletteChangePreservesEditorTextAndNativeButtonInstance() {
        val state = freshState()
        state.openCreateRoutine(CreateRoutineDraft("My routine", listOf(RoutineStepContent("Take the cloth."))))
        launch(state)
        compose.onNodeWithText("Routine name").performTextReplacement("My edited routine")
        compose.onNodeWithTag("step-1-image-action").performScrollTo()
        var native: Button? = null
        compose.runOnIdle { native = nativeButtons(requireNotNull(hostView)).first { it.text == "Add image" } }
        for (choice in listOf(ThemePalette.Light, ThemePalette.Black)) {
            compose.runOnIdle { palette.value = choice }
            compose.onNodeWithText("My edited routine").assertExists()
            compose.runOnIdle {
                val current = nativeButtons(requireNotNull(hostView)).first { it.text == "Add image" }
                assertSame(native, current)
                assertEquals(colorsForPalette(choice).primary.toArgb(), current.currentTextColor)
                assertTrue(current.background is RippleDrawable)
            }
        }
        compose.onNodeWithTag("step-1-image-action").performClick()
        compose.onNodeWithText("Choose from device").assertExists()
        capture("gallery-image-chooser")
    }

    @Test @Config(qualifiers = "w412dp-h915dp-night-mdpi")
    fun defaultStillUsesExistingSystemDarkPalette() = checkDefaultPalette(ThemePalette.Dark)

    @Test @Config(qualifiers = "w412dp-h915dp-notnight-mdpi")
    fun defaultStillUsesExistingSystemLightPalette() = checkDefaultPalette(ThemePalette.Light)

    private fun checkDefaultPalette(expected: ThemePalette) {
        palette.value = null
        val state = freshState()
        state.openComplete("prepare-for-exercise")
        launch(state)
        capture("default-${expected.name.lowercase()}", colorsForPalette(expected).background)
    }

    private fun nativeButtons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { nativeButtons(view.getChildAt(it)) }
        else -> emptyList()
    }
}
