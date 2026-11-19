package io.github.ewoc2026.visualroutines

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses real routine files and fresh storage owners to test retention beyond one startup. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageRecoveryRetentionTest {
    private val root = Files.createTempDirectory("vr-image-recovery").toFile()
    private val catalogFile = JsonRoutineStore.appPrivateFile(root)
    private val imageDirectory = File(root, ImportedImageStore.DIRECTORY_NAME)
    private val draftsDirectory = File(root, ImportedImageStore.DRAFT_DIRECTORY_NAME)
    private var images = ImportedImageStore(imageDirectory, draftsDirectory)
    private var repository = newRepository()

    @After fun cleanup() { root.deleteRecursively() }

    private fun newRepository() = ImageRoutineRepository(JsonRoutineRepository(JsonRoutineStore(catalogFile)), images)

    private fun restartOwners(initializeHome: Boolean = true) {
        images = ImportedImageStore(imageDirectory, draftsDirectory)
        repository = newRepository()
        if (initializeHome) {
            images.recoverDraft(null)
            images.collect(repository.loadCatalog())
        }
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun imageFile(id: String) = File(imageDirectory, "$id.png")
    private fun step(imageId: String) = RoutineStepContent(
        "Take the cloth.", image = RoutineStepImage(
            RoutineImageSource.IMPORTED, imageId, RoutineImageSemanticRole.INFORMATIVE, "Blue cloth.",
        ),
    )

    private fun importImage(draft: String): String {
        val bytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
                assertTrue(compress(Bitmap.CompressFormat.PNG, 100, output))
                recycle()
            }
            output.toByteArray()
        }
        return images.importImage(draft) { bytes.inputStream() }
    }

    private fun saveImageRoutine(): Pair<String, String> {
        val draft = newId()
        images.beginDraft(draft, emptySet())
        val imageId = importImage(draft)
        val routineId = newId()
        repository.createRoutine(routineId, "Saved routine", listOf(step(imageId)))
        images.finishDraft(draft)
        images.collect(repository.loadCatalog())
        return routineId to imageId
    }

    @Test fun repeatedMissingCatalogStartupKeepsTheExactImage() {
        val (_, id) = saveImageRoutine()
        val original = imageFile(id).readBytes()
        assertFalse(File("${catalogFile.path}.bak").exists())
        assertTrue(catalogFile.delete())

        repeat(2) {
            restartOwners()
            assertFalse(catalogFile.exists())
            assertNull(repository.loadCatalog().userRoutineLoadError)
            assertArrayEquals(original, imageFile(id).readBytes())
        }
    }

    @Test fun writingBeforeHomeStartupDoesNotLoseAnImageFromTheMissingCatalog() {
        val (originalRoutine, originalImage) = saveImageRoutine()
        val originalCatalog = catalogFile.readBytes()
        val originalBytes = imageFile(originalImage).readBytes()
        assertTrue(catalogFile.delete())
        restartOwners(initializeHome = false)

        // A direct write must not let a new, valid document authorize deletion of old images.
        val (newRoutine, newImage) = saveImageRoutine()
        assertArrayEquals(originalBytes, imageFile(originalImage).readBytes())
        restartOwners()
        val textRoutine = newId()
        repository.createRoutine(textRoutine, "New text", listOf(RoutineStepContent("A new step.")))
        repository.reorderUserRoutines(listOf(textRoutine, newRoutine))
        repository.deleteRoutine(newRoutine)
        assertFalse(imageFile(newImage).exists())
        repository.deleteRoutine(textRoutine)
        restartOwners()
        assertArrayEquals(originalBytes, imageFile(originalImage).readBytes())

        catalogFile.writeBytes(originalCatalog)
        restartOwners()
        assertNotNull(images.decode(originalImage, 400)?.also { it.recycle() })
        assertArrayEquals(originalCatalog, catalogFile.readBytes())
        repository.deleteRoutine(originalRoutine)
        assertFalse(imageFile(originalImage).exists())
    }

    @Test fun readableButUnrelatedCatalogDoesNotAuthorizeDeletingSurvivingImages() {
        val (_, imageId) = saveImageRoutine()
        val original = imageFile(imageId).readBytes()
        JsonRoutineStore(catalogFile).update { StoredRoutineDocument() }

        restartOwners()
        val routine = newId()
        repository.createRoutine(routine, "New routine", listOf(RoutineStepContent("Keep going.")))
        repository.updateRoutine(routine, "Updated routine", listOf(RoutineStepContent("Finish.")))
        repository.deleteRoutine(routine)
        restartOwners()
        assertArrayEquals(original, imageFile(imageId).readBytes())
    }

    @Test fun corruptCatalogRejectsWritesAndKeepsBothTheCatalogAndImage() {
        val (_, imageId) = saveImageRoutine()
        val original = imageFile(imageId).readBytes()
        catalogFile.writeText("invalid JSON")
        restartOwners()

        assertNotNull(repository.loadCatalog().userRoutineLoadError)
        assertThrows(RoutineStoreException::class.java) {
            repository.createRoutine(newId(), "New routine", listOf(RoutineStepContent("A step.")))
        }
        assertEquals("invalid JSON", catalogFile.readText())
        assertArrayEquals(original, imageFile(imageId).readBytes())
    }

    @Test fun failedSaveAndLaterCatalogRecoveryKeepUncertainPublishedCopies() {
        val (routine, oldImage) = saveImageRoutine()
        val originalCatalog = catalogFile.readBytes()
        val draft = newId()
        images.beginDraft(draft, setOf(oldImage))
        val replacement = importImage(draft)
        catalogFile.writeText("invalid JSON")

        assertThrows(RoutineStoreException::class.java) {
            repository.updateRoutine(routine, "Replacement", listOf(step(replacement)))
        }
        val oldBytes = imageFile(oldImage).readBytes()
        val newBytes = imageFile(replacement).readBytes()
        images.finishDraft(draft)
        images.collect(repository.loadCatalog())
        catalogFile.writeBytes(originalCatalog)
        restartOwners()
        assertArrayEquals(oldBytes, imageFile(oldImage).readBytes())
        assertArrayEquals(newBytes, imageFile(replacement).readBytes())
    }

    @Test fun successfulReplacementCollectsTheOldImageAfterItsDraftLeaseEnds() {
        val (routine, oldImage) = saveImageRoutine()
        val draft = newId()
        images.beginDraft(draft, setOf(oldImage))
        val replacement = importImage(draft)

        repository.updateRoutine(routine, "Replacement", listOf(step(replacement)))
        assertTrue(imageFile(oldImage).isFile)
        images.finishDraft(draft)
        images.collect(repository.loadCatalog())

        assertFalse(imageFile(oldImage).exists())
        assertNotNull(images.decode(replacement, 400)?.also { it.recycle() })
    }

    @Test fun lastSharedReferenceDeletionLeavesUnrelatedImagesAlone() {
        val (firstRoutine, imageId) = saveImageRoutine()
        val secondRoutine = newId()
        repository.createRoutine(secondRoutine, "Shared image", listOf(step(imageId)))
        val unrelatedImage = newId()
        imageFile(imageId).copyTo(imageFile(unrelatedImage))
        val original = imageFile(unrelatedImage).readBytes()

        repository.deleteRoutine(firstRoutine)
        assertTrue(imageFile(imageId).isFile)
        repository.deleteRoutine(secondRoutine)
        assertFalse(imageFile(imageId).exists())
        assertArrayEquals(original, imageFile(unrelatedImage).readBytes())
    }

    private fun removeReferenceWithOpenDraft(): Pair<String, String> {
        val (routine, imageId) = saveImageRoutine()
        val draft = newId()
        images.beginDraft(draft, setOf(imageId))
        repository.updateRoutine(routine, "Text only", listOf(RoutineStepContent("Keep the text.")))
        assertTrue(imageFile(imageId).isFile)
        return imageId to draft
    }

    private fun checkUncertainCatalogDiscardsPendingRemoval(missing: Boolean) {
        val (imageId, draft) = removeReferenceWithOpenDraft()
        val currentCatalog = catalogFile.readBytes()
        val original = imageFile(imageId).readBytes()
        if (missing) assertTrue(catalogFile.delete()) else catalogFile.writeText("invalid JSON")
        // App-state reads must cancel stale removal evidence even without a Home cleanup pass.
        repository.loadCatalog()
        catalogFile.writeBytes(currentCatalog)
        images.finishDraft(draft)
        images.collect(repository.loadCatalog())
        assertArrayEquals(original, imageFile(imageId).readBytes())
    }

    @Test fun missingCatalogDiscardsPendingRemovalEvidence() =
        checkUncertainCatalogDiscardsPendingRemoval(missing = true)

    @Test fun corruptCatalogDiscardsPendingRemovalEvidence() =
        checkUncertainCatalogDiscardsPendingRemoval(missing = false)

    @Test fun restartingBeforeDraftExitKeepsThePreviouslyPinnedCopy() {
        val (imageId, _) = removeReferenceWithOpenDraft()
        val original = imageFile(imageId).readBytes()
        restartOwners()
        repository.createRoutine(newId(), "Unrelated", listOf(RoutineStepContent("A step.")))
        assertArrayEquals(original, imageFile(imageId).readBytes())
    }

    @Test fun aNewSavedReferenceInvalidatesAnEarlierPendingRemoval() {
        val (imageId, draft) = removeReferenceWithOpenDraft()
        repository.createRoutine(newId(), "Use it again", listOf(step(imageId)))
        val original = imageFile(imageId).readBytes()
        JsonRoutineStore(catalogFile).update { StoredRoutineDocument() }
        images.finishDraft(draft)
        images.collect(repository.loadCatalog())
        assertArrayEquals(original, imageFile(imageId).readBytes())
    }
}
