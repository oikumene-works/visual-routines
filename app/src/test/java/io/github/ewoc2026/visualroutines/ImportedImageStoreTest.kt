package io.github.ewoc2026.visualroutines

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportedImageStoreTest {
    private val root = Files.createTempDirectory("vr-image-test").toFile()
    private val committed = File(root, "images")
    private val drafts = File(root, "drafts")
    private val store = ImportedImageStore(committed, drafts)
    private val draft = UUID.randomUUID().toString()
    private val routineId = UUID.randomUUID().toString()
    private val stepId = UUID.randomUUID().toString()

    private fun source(width: Int = 40, height: Int = 20, jpeg: Boolean = false): File {
        val file = File(root, UUID.randomUUID().toString())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        file.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        return file
    }

    private fun reference(id: String) = RoutineStepImage(
        RoutineImageSource.IMPORTED, id, RoutineImageSemanticRole.INFORMATIVE, "Blue cloth beside the sink.",
    )

    private fun catalog(id: String) = RoutineCatalog(listOf(Routine(routineId, "Clean", listOf(
        RoutineStep(stepId, "Take the blue cloth beside the sink.", image = reference(id)),
    ))), setOf(routineId))

    @Test fun importNormalizesOrientationAndDropsSourceMetadata() {
        val file = source(40, 20, jpeg = true)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_ARTIST, "synthetic-test-metadata")
            setLatLong(10.0, 20.0)
            saveAttributes()
        }
        val original = file.readBytes()
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { file.inputStream() }
        val decoded = requireNotNull(store.decode(id, 1600))
        assertEquals(20, decoded.width)
        assertEquals(40, decoded.height)
        decoded.recycle()
        val importedExif = ExifInterface(File(drafts, "$draft/$id.png"))
        assertNull(importedExif.getAttribute(ExifInterface.TAG_ARTIST))
        assertNull(importedExif.latLong)
        assertArrayEquals(original, file.readBytes())
        assertFalse(File(drafts, draft).listFiles()!!.any { it.extension == "source" })
    }

    @Test fun savedImageSurvivesSourceLossAndProcessRestart() {
        store.beginDraft(draft, emptySet())
        val file = source()
        val id = store.importImage(draft) { file.inputStream() }
        store.prepareForSave(setOf(id))
        store.finishDraft(draft)
        store.collect(catalog(id))
        file.delete()
        val restarted = ImportedImageStore(committed, drafts)
        restarted.recoverDraft(null)
        restarted.collect(catalog(id))
        assertNotNull(restarted.decode(id, 400))
    }

    @Test fun failedSaveAndRestoredDraftKeepBothOldAndNewImagesUntilExplicitExit() {
        store.beginDraft(draft, emptySet())
        val oldId = store.importImage(draft) { source().inputStream() }
        store.prepareForSave(setOf(oldId))
        store.finishDraft(draft)
        val nextDraft = UUID.randomUUID().toString()
        store.beginDraft(nextDraft, setOf(oldId))
        val newId = store.importImage(nextDraft) { source().inputStream() }
        store.prepareForSave(setOf(newId))
        // A failed routine write must leave both copies and the lease intact.
        store.collect(catalog(oldId))
        val restarted = ImportedImageStore(committed, drafts)
        restarted.beginDraft(nextDraft, setOf(oldId))
        restarted.collect(catalog(oldId))
        assertNotNull(restarted.decode(oldId, 400))
        assertNotNull(restarted.decode(newId, 400))
        restarted.finishDraft(nextDraft) // Cancel the edit after process recreation.
        restarted.collect(catalog(oldId))
        assertNotNull(restarted.decode(oldId, 400))
        assertNull(restarted.decode(newId, 400))
    }

    @Test fun deletingLastReferenceCollectsImageButAnotherRoutineProtectsIt() {
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { source().inputStream() }
        store.prepareForSave(setOf(id))
        store.finishDraft(draft)
        store.collect(catalog(id))
        assertNotNull(store.decode(id, 400))
        store.collect(RoutineCatalog(emptyList()))
        assertNull(store.decode(id, 400))
    }

    @Test fun unreadableCatalogNeverTriggersCollection() {
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { source().inputStream() }
        store.prepareForSave(setOf(id))
        store.finishDraft(draft)
        store.collect(RoutineCatalog(emptyList(), userRoutineLoadError = RoutineStoreException("test")))
        assertNotNull(store.decode(id, 400))
    }

    @Test fun abandonedDraftsAreCleanedOnlyWhenRestorationIsRuledOut() {
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { source().inputStream() }
        assertNotNull(ImportedImageStore(committed, drafts).apply { recoverDraft(draft) }.decode(id, 400))
        val coldStart = ImportedImageStore(committed, drafts)
        coldStart.recoverDraft(null)
        assertNull(coldStart.decode(id, 400))
    }

    @Test fun corruptAndDeniedSourcesLeaveNoImageOrOriginalBytes() {
        store.beginDraft(draft, emptySet())
        assertEquals(ImageImportFailure.UNSUPPORTED,
            assertThrows(ImageImportException::class.java) {
                store.importImage(draft) { ByteArrayInputStream("not an image".toByteArray()) }
            }.failure)
        assertEquals(ImageImportFailure.UNREADABLE,
            assertThrows(ImageImportException::class.java) {
                store.importImage(draft) { throw SecurityException() }
            }.failure)
        assertEquals(listOf("pins"), File(drafts, draft).list()!!.toList())
    }

    @Test fun animatedPngIsRejectedInsteadOfKeepingOnlyOneFrame() {
        val bytes = source().readBytes()
        val chunkData = java.io.ByteArrayOutputStream().apply {
            java.io.DataOutputStream(this).apply {
                writeBytes("acTL")
                writeInt(2)
                writeInt(0)
            }
        }.toByteArray()
        val animationChunk = java.io.ByteArrayOutputStream().apply {
            java.io.DataOutputStream(this).apply {
                writeInt(8)
                write(chunkData)
                writeInt(java.util.zip.CRC32().apply { update(chunkData) }.value.toInt())
            }
        }.toByteArray()
        val animated = bytes.copyOfRange(0, 33) + animationChunk + bytes.copyOfRange(33, bytes.size)
        store.beginDraft(draft, emptySet())
        assertEquals(ImageImportFailure.UNSUPPORTED,
            assertThrows(ImageImportException::class.java) {
                store.importImage(draft) { ByteArrayInputStream(animated) }
            }.failure)
    }

    @Test fun byteLimitIsEnforcedEvenWithoutProviderMetadata() {
        store.beginDraft(draft, emptySet())
        val endless = object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = length
        }
        assertEquals(ImageImportFailure.TOO_LARGE,
            assertThrows(ImageImportException::class.java) { store.importImage(draft) { endless } }.failure)
        assertEquals(listOf("pins"), File(drafts, draft).list()!!.toList())
    }

    @Test fun largePhotoIsSubsampledWithoutChangingItsAspectRatio() {
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { source(3200, 1600).inputStream() }
        val decoded = requireNotNull(store.decode(id, 1600))
        assertEquals(1600, decoded.width)
        assertEquals(800, decoded.height)
        decoded.recycle()
    }

    @Test fun pathLikeIdsAndDamagedCopiesResolveToNoImage() {
        assertNull(store.decode("../outside", 400))
        val id = UUID.randomUUID().toString()
        committed.mkdirs()
        File(committed, "$id.png").writeText("broken")
        assertNull(store.decode(id, 400))
    }

    @Test fun readableSourceAndUnwritableDraftAreReportedAsStorageFailure() {
        store.beginDraft(draft, emptySet())
        val bytes = source().readBytes()
        assertEquals(ImageImportFailure.STORAGE,
            assertThrows(ImageImportException::class.java) {
                store.importImage(draft) {
                    File(drafts, draft).deleteRecursively()
                    ByteArrayInputStream(bytes)
                }
            }.failure)
    }

    @Test fun storageFailureDoesNotPublishAnAsset() {
        store.beginDraft(draft, emptySet())
        val id = store.importImage(draft) { source().inputStream() }
        committed.writeText("not a directory")
        assertThrows(ImageImportException::class.java) { store.prepareForSave(setOf(id)) }
        assertNotNull(store.decode(id, 400))
    }
}
