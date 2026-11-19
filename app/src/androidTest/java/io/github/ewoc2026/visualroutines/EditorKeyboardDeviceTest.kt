package io.github.ewoc2026.visualroutines

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Opt-in real-Activity/IME regression on a disposable application-id variant.
 * The caller owns device font/orientation and restoration. Input uses Android
 * key events with the current software keyboard visible, not Compose text injection.
 */
@RunWith(AndroidJUnit4::class)
class EditorKeyboardDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val app get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            assumeTrue(arguments.getString("keyboardMatrix") == "true")
            check(app.packageName.startsWith("io.github.ewoc2026.visualroutines.")) {
                "Keyboard fixture requires a disposable application-id variant."
            }
            app.routineRepository.createRoutine(ROUTINE_ID, "Keyboard draft", (1..3).map { number ->
                RoutineStepContent(
                    instruction = "Step $number: prepare the cloth and place it beside the folded sheet. Check the work area before continuing.",
                    note = "Note $number: this is synthetic test content. Keep this note while rotating the screen.",
                    image = if (number == 3) RoutineStepImage(
                        source = RoutineImageSource.BUNDLED,
                        assetId = "bed-linen-remove-sheet",
                        semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                        descriptionOverride = "Description 3: a sheet being lifted from the bed.",
                    ) else null,
                )
            })
            SharedPreferencesSettingsStore(app).save(HomeSettings(showExampleRoutines = false))
            SharedPreferencesSessionStore(app).clear()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)
    private val output by lazy {
        val case = requireNotNull(arguments.getString("caseId"))
        require(case.matches(Regex("[a-z0-9-]+")))
        File(app.cacheDir, "keyboard-validation/$case").apply { mkdirs() }
    }
    private val observations = JSONObject()

    @Test fun lowerFieldsRemainEditableThroughKeyboardRotationSaveAndCancel() {
        val palette = requireNotNull(arguments.getString("palette"))
        require(palette in listOf("light", "black"))
        val initialOrientation = compose.activity.resources.configuration.orientation
        observations.put("fontScale", compose.activity.resources.configuration.fontScale)
            .put("orientation", initialOrientation).put("palette", palette)
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        compose.onNodeWithTag("dark-style-black").performScrollTo().performClick()
        if (palette == "light") compose.onNodeWithTag("appearance-light").performScrollTo().performClick()
        compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        capture("settings-top")
        compose.onNodeWithTag("dark-style-black").performScrollTo().assertIsDisplayed().assertIsSelected()
        if (palette == "light") compose.onNodeWithTag("dark-style-black").assertIsNotEnabled()
        else compose.onNodeWithTag("dark-style-black").assertIsEnabled()
        capture("settings-style")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Manage routines").performClick()
        compose.onNodeWithContentDescription("Edit Keyboard draft").performScrollTo().performClick()

        val note = typeInto("Note 3:", "note")
        dismissKeyboard()
        val description = typeInto("Description 3:", "description")
        // Both actions must be scroll-reachable with the real keyboard still open.
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        assertTrue(imeVisible())
        capture("actions-with-keyboard")

        rotate(if (initialOrientation == Configuration.ORIENTATION_PORTRAIT)
            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT)
        assertEquals(note, fieldText("Note 3:"))
        assertEquals(description, fieldText("Description 3:"))
        rotate(initialOrientation)
        assertEquals(note, fieldText("Note 3:"))
        assertEquals(description, fieldText("Description 3:"))
        field("Description 3:").performScrollTo().performClick()
        waitForKeyboard(true)
        dismissKeyboard()
        compose.onNodeWithText("Edit routine").assertIsDisplayed()
        assertEquals(note, fieldText("Note 3:"))
        assertEquals(description, fieldText("Description 3:"))
        capture("after-rotation-and-back")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Manage routines").assertIsDisplayed()
        val saved = app.routineRepository.loadCatalog().routines.single { it.id == ROUTINE_ID }
        assertEquals(note, saved.steps[2].supportingInstruction)
        assertEquals(description, saved.steps[2].image?.descriptionOverride)
        assertEquals(3, saved.steps.size)

        compose.onNodeWithContentDescription("Edit Keyboard draft").performScrollTo().performClick()
        typeInto("Note 3:", "cancel-draft")
        dismissKeyboard()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Manage routines").assertIsDisplayed()
        assertEquals(saved, app.routineRepository.loadCatalog().routines.single { it.id == ROUTINE_ID })
        observations.put("saveAndCancel", "passed").put("rotationAndBack", "passed")
        File(output, "result.json").writeText(observations.toString(2))
    }

    private fun field(prefix: String) = compose.onNode(SemanticsMatcher("editable text starts with $prefix") {
        it.config.getOrElse(SemanticsProperties.EditableText) { androidx.compose.ui.text.AnnotatedString("") }
            .text.startsWith(prefix)
    })

    private fun fieldText(prefix: String) = field(prefix).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun typeInto(prefix: String, name: String): String {
        field(prefix).performScrollTo().performClick()
        waitForKeyboard(true)
        val before = fieldText(prefix)
        instrumentation.sendStringSync("x")
        compose.waitUntil(5_000) { fieldText(prefix).length == before.length + 1 }
        capture("$name-middle")
        // Exercise the end of a wrapped draft as well as the tapped position.
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(0, 0, action, KeyEvent.KEYCODE_DPAD_DOWN, 0, KeyEvent.META_ALT_ON))
        }
        compose.waitUntil(5_000) {
            field(prefix).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].end == before.length + 1
        }
        instrumentation.sendStringSync(" tail")
        compose.waitUntil(5_000) { fieldText(prefix).length == before.length + 6 }
        val after = fieldText(prefix)
        capture(name)
        val selection = field(prefix).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        val decor = compose.activity.window.decorView
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
        val keyboardTop = decor.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val viewport = compose.onNodeWithTag("routine-editor-scroll").fetchSemanticsNode().boundsInRoot
        val layouts = mutableListOf<TextLayoutResult>()
        field(prefix).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val lineHeight = layouts.single().let { it.getLineBottom(0) - it.getLineTop(0) }
        assertTrue("Editor overlaps the keyboard: $viewport", viewport.bottom <= keyboardTop + 1)
        assertTrue("Editor cannot display one full text line: $viewport", viewport.height >= lineHeight)
        // Geometry verifies the viewport. The retained screenshot separately owns
        // visual evidence for the rendered line and caret.
        observations.put(name, JSONObject().put("text", after).put("selection", selection.toString())
            .put("viewport", viewport.toString()).put("keyboardTop", keyboardTop))
        return after
    }

    private fun imeVisible() = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun waitForKeyboard(visible: Boolean) = compose.waitUntil(5_000) { imeVisible() == visible }

    private fun dismissKeyboard() {
        assertTrue("Back must start with the keyboard shown", imeVisible())
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForKeyboard(false)
    }

    private fun rotate(orientation: Int) {
        // Android 16 can ignore Activity orientation requests on large displays.
        // Rotate the approved test display, as a user rotation would do.
        val rotation = if (orientation == Configuration.ORIENTATION_PORTRAIT) 0 else 1
        instrumentation.uiAutomation.executeShellCommand("wm user-rotation lock $rotation").use {
            FileInputStream(it.fileDescriptor).readBytes()
        }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == orientation }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).useBitmap { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) = try { block(this) } finally { recycle() }

    private companion object { const val ROUTINE_ID = "11111111-1111-4111-8111-111111111111" }
}
