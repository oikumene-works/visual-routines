package io.github.ewoc2026.visualroutines

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** A deliberately bounded, non-identifying import failure for the author. */
internal enum class ImageImportFailure { UNREADABLE, UNSUPPORTED, TOO_LARGE, STORAGE }
internal class ImageImportException(val failure: ImageImportFailure) : Exception()

/**
 * Owns immutable normalized images and restorable draft leases. Callers share
 * this monitor with routine mutations so collection never races a commit.
 * Sources, source names and metadata are never part of the stored identity.
 */
internal class ImportedImageStore(
    private val imagesDirectory: File,
    private val draftsDirectory: File,
) {
    private var recovered = false
    private val activeDrafts = mutableSetOf<String>()

    /** Reconciles the single main editor's restored draft once per process. */
    @Synchronized
    fun recoverDraft(draftId: String?) {
        if (recovered) return
        recovered = true
        draftsDirectory.listFiles()?.filter { it.isDirectory && isAssetId(it.name) }
            ?.filter { it.name != draftId }?.forEach { it.deleteRecursively() }
    }

    /** Pins existing references too, so a live editor never loses its preview. */
    @Synchronized
    fun beginDraft(draftId: String, existingIds: Set<String>) {
        require(isAssetId(draftId))
        recoverDraft(draftId)
        val directory = File(draftsDirectory, draftId)
        if (!directory.exists() && !directory.mkdirs()) throw ImageImportException(ImageImportFailure.STORAGE)
        val pins = File(directory, "pins")
        if (!pins.exists()) {
            try {
                FileOutputStream(pins).use { output ->
                    output.write(existingIds.filter(::isAssetId).joinToString("\n").toByteArray())
                    output.fd.sync()
                }
            } catch (_: IOException) { throw ImageImportException(ImageImportFailure.STORAGE) }
        }
        // A completed import has no source file. These are interrupted imports
        // from a lost process, and cannot be referenced by the editor.
        directory.listFiles()?.filter { it.extension == "source" }?.forEach { it.delete() }
        activeDrafts += draftId
    }

    /**
     * Reads at most 32 MiB, inspects bounds before decoding, then writes an
     * oriented PNG of at most 1600 pixels per side. Original bytes are temporary.
     */
    @Synchronized
    fun importImage(draftId: String, openSource: () -> InputStream?): String {
        val directory = File(draftsDirectory, draftId)
        if (draftId !in activeDrafts || !directory.isDirectory) {
            throw ImageImportException(ImageImportFailure.STORAGE)
        }
        val id = UUID.randomUUID().toString()
        val source = File(directory, "$id.source")
        val target = File(directory, "$id.png")
        var bitmap: Bitmap? = null
        try {
            val input = try {
                openSource() ?: throw ImageImportException(ImageImportFailure.UNREADABLE)
            } catch (_: SecurityException) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            } catch (_: IOException) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            }
            try {
                input.use { stream ->
                    try {
                        FileOutputStream(source).use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = try { stream.read(buffer) } catch (_: IOException) {
                                    throw ImageImportException(ImageImportFailure.UNREADABLE)
                                }
                                if (count < 0) break
                                total += count
                                if (total > MAX_SOURCE_BYTES) throw ImageImportException(ImageImportFailure.TOO_LARGE)
                                output.write(buffer, 0, count)
                            }
                        }
                    } catch (_: IOException) {
                        throw ImageImportException(ImageImportFailure.STORAGE)
                    }
                }
            } catch (_: IOException) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outMimeType !in SUPPORTED_TYPES || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw ImageImportException(ImageImportFailure.UNSUPPORTED)
            }
            if (bounds.outMimeType == "image/png" && hasPngAnimation(source)) {
                throw ImageImportException(ImageImportFailure.UNSUPPORTED)
            }
            if (bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS) {
                throw ImageImportException(ImageImportFailure.TOO_LARGE)
            }
            // GIF/animated WebP are deliberately excluded rather than silently
            // turning motion into a potentially misleading first frame.
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            bitmap = BitmapFactory.decodeFile(source.path, options)
                ?: throw ImageImportException(ImageImportFailure.UNREADABLE)
            val exif = try { ExifInterface(source) } catch (_: IOException) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            }
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) { bitmap.recycle(); bitmap = oriented }
            FileOutputStream(target).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw ImageImportException(ImageImportFailure.STORAGE)
                }
                output.fd.sync()
            }
            val verified = decodeFile(target, MAX_SIDE)
                ?: throw ImageImportException(ImageImportFailure.STORAGE)
            verified.recycle()
            return id
        } catch (error: ImageImportException) {
            target.delete()
            throw error
        } catch (_: OutOfMemoryError) {
            target.delete()
            throw ImageImportException(ImageImportFailure.TOO_LARGE)
        } catch (_: Exception) {
            target.delete()
            throw ImageImportException(ImageImportFailure.STORAGE)
        } finally {
            bitmap?.recycle()
            source.delete()
        }
    }

    /** Publishes complete images before the routine can commit their ids. */
    @Synchronized
    fun prepareForSave(ids: Set<String>) {
        for (id in ids) {
            val source = findFile(id) ?: continue // Preserve an already unavailable reference.
            val target = File(imagesDirectory, "$id.png")
            if (source == target) continue
            if (!imagesDirectory.exists() && !imagesDirectory.mkdirs()) {
                throw ImageImportException(ImageImportFailure.STORAGE)
            }
            // Keep the draft copy until explicit exit, including a failed save.
            val temporary = File(imagesDirectory, "$id.pending")
            try {
                source.inputStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (!temporary.renameTo(target)) throw IOException()
            } catch (_: IOException) {
                throw ImageImportException(ImageImportFailure.STORAGE)
            } finally { temporary.delete() }
        }
    }

    /** Called only after explicit cancel or confirmed successful routine save. */
    @Synchronized
    fun finishDraft(draftId: String) {
        activeDrafts -= draftId
        if (isAssetId(draftId)) File(draftsDirectory, draftId).deleteRecursively()
    }

    /** On an unreadable catalog or lease, retain assets instead of guessing. */
    @Synchronized
    fun collect(catalog: RoutineCatalog) {
        if (catalog.userRoutineLoadError != null) return
        val retained = catalog.routines.flatMap { it.steps }.mapNotNull { it.image }
            .filter { it.source == RoutineImageSource.IMPORTED }.mapTo(mutableSetOf()) { it.assetId }
        try {
            val draftDirectories = if (draftsDirectory.exists()) draftsDirectory.listFiles() ?: return else emptyArray()
            for (draft in draftDirectories.filter { it.isDirectory }) {
                val pins = File(draft, "pins")
                if (!pins.isFile) return
                retained += pins.readLines()
                retained += draft.listFiles().orEmpty().filter { it.extension == "png" }.map { it.nameWithoutExtension }
            }
        } catch (_: IOException) { return }
        imagesDirectory.listFiles()?.filter { it.isFile && isAssetId(it.nameWithoutExtension) }
            ?.filter { it.nameWithoutExtension !in retained }?.forEach { it.delete() }
    }

    /** A bounded decode; missing or damaged files resolve to text-only content. */
    @Synchronized
    fun decode(id: String, maxSide: Int): Bitmap? = findFile(id)?.let { decodeFile(it, maxSide) }

    private fun findFile(id: String): File? {
        if (!isAssetId(id)) return null
        val committed = File(imagesDirectory, "$id.png")
        if (committed.isFile) return committed
        return draftsDirectory.listFiles()?.asSequence()?.filter { it.isDirectory }
            ?.map { File(it, "$id.png") }?.firstOrNull { it.isFile }
    }

    private fun hasPngAnimation(file: File): Boolean = DataInputStream(file.inputStream()).use { input ->
        input.readLong() // The bounds decoder already identified the PNG signature.
        while (input.available() > 0) {
            val length = input.readInt()
            val type = input.readInt()
            if (length < 0 || length.toLong() + 4 > input.available()) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            }
            if (type == 0x6163544c) return@use true // acTL precedes the first IDAT in APNG.
            if (type == 0x49444154 || type == 0x49454e44) return@use false
            if (input.skipBytes(length + 4) != length + 4) {
                throw ImageImportException(ImageImportFailure.UNREADABLE)
            }
        }
        false
    }

    private fun decodeFile(file: File, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outMimeType != "image/png" || bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE ||
            file.length() > MAX_SOURCE_BYTES) null
        else BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        })
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    companion object {
        const val DIRECTORY_NAME = "user_images"
        const val DRAFT_DIRECTORY_NAME = "user_image_drafts"
        const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 100_000_000L
        const val MAX_SIDE = 1600
        private val SUPPORTED_TYPES = setOf("image/jpeg", "image/png")
        private val ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun isAssetId(id: String): Boolean = ID_PATTERN.matches(id)
        fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while ((maxOf(width, height).toLong() + sample - 1) / sample > maxSide) sample *= 2
            return sample
        }
    }
}

/** Serializes import publication, document commits and safe asset collection. */
internal class ImageRoutineRepository(
    private val delegate: RoutineRepository,
    private val images: ImportedImageStore,
) : RoutineRepository {
    override fun loadCatalog(): RoutineCatalog = synchronized(images) { delegate.loadCatalog() }
    override fun createRoutine(routineId: String, title: String, steps: List<RoutineStepContent>): RoutineCatalog =
        mutate(steps) { delegate.createRoutine(routineId, title, steps) }
    override fun updateRoutine(routineId: String, title: String, steps: List<RoutineStepContent>): RoutineCatalog =
        mutate(steps) { delegate.updateRoutine(routineId, title, steps) }
    override fun deleteRoutine(routineId: String): RoutineCatalog = mutate(emptyList()) { delegate.deleteRoutine(routineId) }
    override fun reorderUserRoutines(routineIds: List<String>): RoutineCatalog =
        mutate(emptyList()) { delegate.reorderUserRoutines(routineIds) }

    private fun mutate(steps: List<RoutineStepContent>, action: () -> RoutineCatalog): RoutineCatalog = synchronized(images) {
        try {
            images.prepareForSave(steps.mapNotNull { it.image }
                .filter { it.source == RoutineImageSource.IMPORTED }.mapTo(mutableSetOf()) { it.assetId })
        } catch (_: ImageImportException) {
            throw RoutineStoreException("Could not retain the selected image.")
        }
        action().also(images::collect)
    }
}
