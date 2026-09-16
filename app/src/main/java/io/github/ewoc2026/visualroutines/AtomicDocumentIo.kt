package io.github.ewoc2026.visualroutines

import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reads and atomically replaces one small application-private document.
 *
 * The monitor covers complete byte-level read-modify-write operations for this
 * instance. Callers remain responsible for ensuring that only one instance and
 * one process own a document.
 */
internal class AtomicDocumentIo(
    private val backend: AtomicDocumentBackend,
) {
    constructor(file: File) : this(AndroidXAtomicDocumentBackend(file))

    private val monitor = Any()

    fun read(): ByteArray? = synchronized(monitor) {
        readLocked(DocumentIoOperation.READ)
    }

    fun replace(bytes: ByteArray) {
        val snapshot = bytes.copyOf()
        synchronized(monitor) {
            replaceLocked(snapshot)
        }
    }

    /**
     * Serializes a complete read-modify-write operation and returns the bytes
     * that were verified after commit. The transform runs while the instance
     * monitor is held and must be quick, deterministic, and side-effect-free.
     */
    fun update(transform: (ByteArray?) -> ByteArray): ByteArray = synchronized(monitor) {
        val current = readLocked(DocumentIoOperation.READ)?.copyOf()
        val replacement = transform(current).copyOf()
        replaceLocked(replacement)
        replacement.copyOf()
    }

    /**
     * Serializes a read and optional replacement. Returning `null` from
     * [transform] keeps the current document unchanged. The transform follows
     * the same locking and side-effect contract as [update].
     */
    fun readOrReplace(transform: (ByteArray?) -> ByteArray?): ByteArray? = synchronized(monitor) {
        val current = readLocked(DocumentIoOperation.READ)?.copyOf()
        val replacement = transform(current?.copyOf())
            ?: return@synchronized current?.copyOf()
        val snapshot = replacement.copyOf()
        replaceLocked(snapshot)
        snapshot.copyOf()
    }

    private fun readLocked(operation: DocumentIoOperation): ByteArray? {
        return try {
            if (backend.hasReadableData()) backend.readFully() else null
        } catch (error: Exception) {
            throw failure(operation, error)
        }
    }

    private fun replaceLocked(bytes: ByteArray) {
        val pendingWrite = try {
            backend.startWrite()
        } catch (error: Exception) {
            throw failure(DocumentIoOperation.WRITE, error)
        }

        try {
            pendingWrite.write(bytes)
        } catch (error: Exception) {
            abortAndThrow(pendingWrite, failure(DocumentIoOperation.WRITE, error))
        }

        try {
            pendingWrite.sync()
        } catch (error: Exception) {
            abortAndThrow(pendingWrite, failure(DocumentIoOperation.SYNC, error))
        }

        try {
            pendingWrite.commit()
        } catch (error: Exception) {
            throw failure(DocumentIoOperation.COMMIT, error)
        }

        val committedBytes = readLocked(DocumentIoOperation.VERIFY)
        if (committedBytes == null || !committedBytes.contentEquals(bytes)) {
            throw failure(
                DocumentIoOperation.VERIFY,
                IOException("Committed bytes did not match the requested replacement."),
            )
        }
    }

    private fun abortAndThrow(
        pendingWrite: AtomicDocumentPendingWrite,
        failure: AtomicDocumentIoException,
    ): Nothing {
        try {
            pendingWrite.abort()
        } catch (abortError: Exception) {
            failure.addSuppressed(abortError)
        }
        throw failure
    }

    private fun failure(
        operation: DocumentIoOperation,
        cause: Exception,
    ): AtomicDocumentIoException {
        return AtomicDocumentIoException(
            operation = operation,
            commitOutcome = operation.commitOutcome,
            documentDescription = backend.description,
            cause = cause,
        )
    }
}

internal enum class DocumentIoOperation(
    val description: String,
) {
    READ("read"),
    WRITE("write"),
    SYNC("sync"),
    COMMIT("commit"),
    VERIFY("verify"),
}

internal enum class DocumentCommitOutcome {
    NOT_ATTEMPTED,
    NOT_COMMITTED,
    MAY_HAVE_COMMITTED,
}

private val DocumentIoOperation.commitOutcome: DocumentCommitOutcome
    get() = when (this) {
        DocumentIoOperation.READ -> DocumentCommitOutcome.NOT_ATTEMPTED
        DocumentIoOperation.WRITE,
        DocumentIoOperation.SYNC -> DocumentCommitOutcome.NOT_COMMITTED
        DocumentIoOperation.COMMIT,
        DocumentIoOperation.VERIFY -> DocumentCommitOutcome.MAY_HAVE_COMMITTED
    }

internal class AtomicDocumentIoException(
    val operation: DocumentIoOperation,
    val commitOutcome: DocumentCommitOutcome,
    documentDescription: String,
    cause: Throwable,
) : IOException(
    "Could not ${operation.description} atomic document: $documentDescription",
    cause,
)

/**
 * Test seam around the AndroidX primitive. It intentionally exposes only the
 * operations needed by [AtomicDocumentIo].
 */
internal interface AtomicDocumentBackend {
    val description: String

    fun hasReadableData(): Boolean

    @Throws(IOException::class)
    fun readFully(): ByteArray

    @Throws(IOException::class)
    fun startWrite(): AtomicDocumentPendingWrite
}

internal interface AtomicDocumentPendingWrite {
    @Throws(IOException::class)
    fun write(bytes: ByteArray)

    @Throws(IOException::class)
    fun sync()

    fun commit()

    fun abort()
}

internal class AndroidXAtomicDocumentBackend(
    private val baseFile: File,
) : AtomicDocumentBackend {
    private val atomicFile = AtomicFile(baseFile)
    private val legacyBackupFile = File("${baseFile.path}.bak")

    override val description: String = baseFile.path

    override fun hasReadableData(): Boolean = baseFile.exists() || legacyBackupFile.exists()

    override fun readFully(): ByteArray = atomicFile.readFully()

    override fun startWrite(): AtomicDocumentPendingWrite {
        return AndroidXAtomicDocumentPendingWrite(
            atomicFile = atomicFile,
            output = atomicFile.startWrite(),
        )
    }
}

private class AndroidXAtomicDocumentPendingWrite(
    private val atomicFile: AtomicFile,
    private val output: FileOutputStream,
) : AtomicDocumentPendingWrite {
    private var completed = false

    override fun write(bytes: ByteArray) {
        check(!completed) { "Atomic write is already complete." }
        output.write(bytes)
    }

    override fun sync() {
        check(!completed) { "Atomic write is already complete." }
        output.fd.sync()
    }

    override fun commit() {
        check(!completed) { "Atomic write is already complete." }
        completed = true
        atomicFile.finishWrite(output)
    }

    override fun abort() {
        check(!completed) { "Atomic write is already complete." }
        completed = true
        atomicFile.failWrite(output)
    }
}
