package io.github.ewoc2026.visualroutines

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the real editor and importer with a deterministic system-picker result. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalImageEditorTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private var requestCode: Int? = null
    private var pickerIntent: Intent? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            this@LocalImageEditorTest.requestCode = requestCode
            pickerIntent = contract.createIntent(application, input)
        }
    }
    private val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = registry
    }
    private val restoration = StateRestorationTester(compose)

    private fun launchEditor(fontScale: Float = 1f) {
        val state = VisualRoutinesState(SharedPreferencesSessionStore(application), application.routineRepository)
        state.openCreateRoutine(CreateRoutineDraft("Image test", listOf(RoutineStepContent("Take the cloth."))))
        restoration.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides owner,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                VisualRoutinesApp(state)
            }
        }
        // Add/remove is backed by an AndroidView; trigger it through its semantics.
        compose.onNodeWithTag("step-1-image-action").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Choose from device") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun chooseImage() {
        compose.onNodeWithText("Choose from device").performClick()
        val source = File(application.cacheDir, "synthetic-picker-image.png")
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        compose.runOnIdle {
            registry.dispatchResult(requireNotNull(requestCode), Activity.RESULT_OK, Intent().setData(Uri.fromFile(source)))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Selected image").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun checkEditorLayout(name: String, fontScale: Float = 1f) {
        launchEditor(fontScale)
        chooseImage()
        compose.onNode(hasContentDescription("The image needs a description", substring = true))
            .performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Screen reader use").performScrollTo()
        if (System.getenv("VR_CAPTURE_LOCAL_IMAGE_PREVIEWS") == "1") {
            val output = File("build/reports/local-user-images/$name.png")
            requireNotNull(output.parentFile).mkdirs()
            compose.onRoot().captureRoboImage(output.absolutePath)
        }
        compose.onNodeWithText("Description for step 1").performScrollTo().performTextInput("Blue cloth beside the sink.")
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phonePortraitLayout() = checkEditorLayout("phone-portrait")

    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscapeLayout() = checkEditorLayout("phone-landscape")

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletPortraitLayout() = checkEditorLayout("tablet-portrait")

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tabletLandscapeLayout() = checkEditorLayout("tablet-landscape")

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phoneLargeTextLayout() = checkEditorLayout("phone-large-text", 2f)

    @Test @Config(qualifiers = "w915dp-h412dp-mdpi")
    fun phoneLandscapeLargeTextLayout() = checkEditorLayout("phone-landscape-large-text", 2f)

    @Test fun pickerIsLocalSingleImageAndCancellationPreservesEditor() {
        launchEditor()
        compose.onNodeWithText("Choose from device").performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, pickerIntent?.action)
        assertTrue(pickerIntent!!.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertFalse(pickerIntent!!.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertArrayEquals(arrayOf("image/jpeg", "image/png"), pickerIntent!!.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        compose.runOnIdle {
            registry.dispatchResult(requireNotNull(requestCode), Activity.RESULT_CANCELED, null)
        }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Take the cloth.").assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled()
    }

    @Test fun informativeImageRequiresDescriptionAndRestoresUnfinishedDraft() {
        launchEditor()
        chooseImage()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasContentDescription("The image needs a description", substring = true)).performScrollTo().performClick()
        compose.onNodeWithText("Description for step 1").performScrollTo().performTextInput("Blue cloth beside the sink.")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Blue cloth beside the sink.").assertExists()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()
        val image = application.routineRepository.loadCatalog().routines.first().steps.first().image
        assertEquals(RoutineImageSource.IMPORTED, image?.source)
        assertEquals("Blue cloth beside the sink.", image?.descriptionOverride)
        assertNotNull(application.importedImages.decode(requireNotNull(image).assetId, 400))
    }

    @Test fun redundantImageCanSaveWithoutDescription() {
        launchEditor()
        chooseImage()
        compose.onNode(hasContentDescription("The step instruction is enough", substring = true)).performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()
        val image = application.routineRepository.loadCatalog().routines.first().steps.first().image
        assertEquals(RoutineImageSemanticRole.REDUNDANT, image?.semanticRole)
        assertNull(image?.descriptionOverride)
    }
}
