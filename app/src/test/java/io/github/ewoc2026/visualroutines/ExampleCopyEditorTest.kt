package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the management entry, real editor, saved-state restoration and persistent copy. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleCopyEditorTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private fun freshState() = VisualRoutinesState(
        SharedPreferencesSessionStore(application), application.routineRepository,
    )
    private var state = lazy { freshState() }
    private val restoration = StateRestorationTester(compose)

    private fun launch(fontScale: Float = 1f, previewName: String? = null) {
        state.value.openManageRoutines()
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                VisualRoutinesApp(state.value)
            }
        }
        compose.onNodeWithText("Create a routine from scratch").performScrollTo()
            .assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Create a routine from an example").performScrollTo()
            .assertIsDisplayed().assertHasClickAction()
        previewName?.let { capture("$it-management") }
        compose.onNodeWithText("Create a routine from an example").performClick()
        compose.onNodeWithText("Choose an example").assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (System.getenv("VR_CAPTURE_EXAMPLE_PREVIEWS") == "1") {
            val output = File("build/reports/example-copy/$name.png")
            requireNotNull(output.parentFile).mkdirs()
            compose.onRoot().captureRoboImage(output.absolutePath)
        }
    }

    private fun checkLayout(name: String, fontScale: Float = 1f) {
        launch(fontScale, name)
        capture("$name-chooser-top")
        compose.onNodeWithText("Chickpea salad bowl").performScrollTo().assertIsDisplayed().assertHasClickAction()
        capture("$name-chooser")
        compose.onNodeWithText("Chickpea salad bowl").performClick()
        compose.onNodeWithText("Copy of Chickpea salad bowl").assertExists()
        capture("$name-editor")
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Manage routines").assertIsDisplayed()
        assertTrue(freshState().userRoutineIds.isEmpty())
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phonePortrait() = checkLayout("phone-portrait")
    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscape() = checkLayout("phone-landscape")
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletPortrait() = checkLayout("tablet-portrait")
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletLandscape() = checkLayout("tablet-landscape")
    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phoneLargeText() = checkLayout("phone-large-text", 2f)
    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscapeLargeText() = checkLayout("phone-landscape-large-text", 2f)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletLargeText() = checkLayout("tablet-large-text", 2f)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletLandscapeLargeText() = checkLayout("tablet-landscape-large-text", 2f)

    @Test fun editedCopyRestoresThenSavesOnceAndRunsIndependently() {
        launch()
        compose.onNodeWithText("Prepare for exercise").performClick()
        val draft = (state.value.screen as AppScreen.CreateRoutine).draft
        compose.onNodeWithText("Routine name").performTextReplacement("My exercise")
        compose.onNodeWithText("Step 1", substring = false).performTextReplacement("Get my shoes.")
        compose.runOnIdle { state = lazy { freshState() } }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("My exercise").assertExists()
        compose.onNodeWithText("Get my shoes.").assertExists()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val restored = freshState()
        val copy = restored.routines.single { it.id in restored.userRoutineIds }
        assertEquals("My exercise", copy.name)
        assertEquals("Get my shoes.", copy.steps.first().instruction)
        assertEquals(draft.steps.map { it.id }, copy.steps.map { it.id })
        assertEquals(BundledRoutines.all.first(), restored.routine("prepare-for-exercise"))
        compose.onNodeWithText("My exercise").performScrollTo().performClick()
        compose.onNodeWithText("Start").performScrollTo().performClick()
        compose.onNodeWithText("Get my shoes.").assertIsDisplayed()
    }

    @Test fun chooserBackAndDisabledEditingDoNotCreateACopy() {
        launch()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Manage routines").assertIsDisplayed()
        compose.runOnIdle {
            state.value.backHome()
            state.value.updateAllowRoutineEditing(false)
        }
        compose.onNodeWithContentDescription("Manage routines").assertDoesNotExist()
        compose.onNodeWithText("Create a routine from an example").assertDoesNotExist()
        assertTrue(freshState().userRoutineIds.isEmpty())
    }
}
