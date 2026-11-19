package io.github.ewoc2026.visualroutines

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises Home startup and catalog recovery without sacrificing surviving images. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MissingCatalogRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private fun freshState() = VisualRoutinesState(
        SharedPreferencesSessionStore(application), application.routineRepository,
    )

    private fun checkStartup(missing: Boolean) {
        val images = application.importedImages
        val draft = UUID.randomUUID().toString()
        val routineId = UUID.randomUUID().toString()
        val source = File(application.cacheDir, "source.png")
        Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            source.outputStream().use { assertTrue(compress(Bitmap.CompressFormat.PNG, 100, it)) }
            recycle()
        }
        images.beginDraft(draft, emptySet())
        val imageId = images.importImage(draft) { source.inputStream() }
        application.routineRepository.createRoutine(routineId, "Recovery fixture", listOf(RoutineStepContent(
            "Take the blue cloth.", image = RoutineStepImage(
                RoutineImageSource.IMPORTED, imageId, RoutineImageSemanticRole.INFORMATIVE, "Blue cloth.",
            ),
        )))
        images.finishDraft(draft)
        val catalog = JsonRoutineStore.appPrivateFile(application.filesDir)
        val original = catalog.readBytes()
        val image = File(application.filesDir, "${ImportedImageStore.DIRECTORY_NAME}/$imageId.png")
        val imageBytes = image.readBytes()
        assertFalse(File("${catalog.path}.bak").exists())
        if (missing) assertTrue(catalog.delete()) else catalog.writeText("invalid JSON")
        val state = mutableStateOf(freshState())
        assertEquals(!missing, state.value.userRoutineLoadError != null)
        assertTrue(image.isFile)

        compose.setContent { VisualRoutinesApp(state.value) }
        compose.waitForIdle()
        assertTrue(image.isFile)
        assertArrayEquals(imageBytes, image.readBytes())

        catalog.writeBytes(original)
        compose.runOnIdle {
            state.value = freshState().apply { startRoutine(routineId) }
        }
        compose.onNodeWithText("Take the blue cloth.").assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("runner-step-image", useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithContentDescription("Blue cloth.").assertExists()
        assertArrayEquals(imageBytes, image.readBytes())
        assertArrayEquals(original, catalog.readBytes())
    }

    @Test fun missingCatalogStartupRetainsTheSavedImageForCatalogRecovery() = checkStartup(missing = true)

    @Test fun corruptCatalogStartupRetainsTheSavedImageForCatalogRecovery() = checkStartup(missing = false)
}
