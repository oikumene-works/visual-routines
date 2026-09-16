package io.github.ewoc2026.visualroutines

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicDocumentIoTest {
    @Test
    fun androidXBackendRoundTripsFirstWriteAndReplacementInPlainJunit() {
        val file = Files.createTempDirectory("vr-atomic-document").resolve("document.bin").toFile()
        val documentIo = AtomicDocumentIo(file)

        assertEquals(null, documentIo.read())

        documentIo.replace("first".toByteArray())
        assertArrayEquals("first".toByteArray(), documentIo.read())

        documentIo.replace("second".toByteArray())
        assertArrayEquals("second".toByteArray(), documentIo.read())
        assertFalse(File("${file.path}.new").exists())
    }

    @Test
    fun androidXBackendAbortAfterStartedWriteKeepsPreviousDocument() {
        val file = Files.createTempDirectory("vr-atomic-document").resolve("document.bin").toFile()
        val documentIo = AtomicDocumentIo(file)
        documentIo.replace("previous".toByteArray())
        val pendingWrite = AndroidXAtomicDocumentBackend(file).startWrite()

        pendingWrite.write("partial replacement".toByteArray())
        pendingWrite.abort()

        assertArrayEquals("previous".toByteArray(), documentIo.read())
        assertFalse(File("${file.path}.new").exists())
    }

    @Test
    fun androidXBackendReadDiscardsStaleNewFileWhenBaseIsValid() {
        val file = Files.createTempDirectory("vr-atomic-document").resolve("document.bin").toFile()
        val documentIo = AtomicDocumentIo(file)
        documentIo.replace("committed".toByteArray())
        val staleNewFile = File("${file.path}.new")
        staleNewFile.writeBytes("stale partial replacement".toByteArray())

        assertArrayEquals("committed".toByteArray(), documentIo.read())
        assertFalse(staleNewFile.exists())
    }

    @Test
    fun androidXBackendRestoresLegacyBackupAcrossExistingFileStates() {
        data class ExistingFiles(val base: Boolean, val new: Boolean)

        listOf(
            ExistingFiles(base = false, new = false),
            ExistingFiles(base = true, new = false),
            ExistingFiles(base = false, new = true),
            ExistingFiles(base = true, new = true),
        ).forEach { existing ->
            val file = Files.createTempDirectory("vr-atomic-document")
                .resolve("document.bin")
                .toFile()
            val newFile = File("${file.path}.new")
            val backupFile = File("${file.path}.bak")
            if (existing.base) file.writeText("base")
            if (existing.new) newFile.writeText("new")
            backupFile.writeText("backup")

            assertArrayEquals("backup".toByteArray(), AtomicDocumentIo(file).read())
            assertArrayEquals("backup".toByteArray(), file.readBytes())
            assertFalse(newFile.exists())
            assertFalse(backupFile.exists())
        }
    }

    @Test
    fun androidXBackendTreatsLoneNewAsUncommittedAndAllowsReplacement() {
        val file = Files.createTempDirectory("vr-atomic-document").resolve("document.bin").toFile()
        val newFile = File("${file.path}.new")
        newFile.writeText("interrupted first write")
        val documentIo = AtomicDocumentIo(file)

        assertEquals(null, documentIo.read())
        assertTrue(newFile.exists())

        documentIo.replace("committed".toByteArray())

        assertArrayEquals("committed".toByteArray(), documentIo.read())
        assertFalse(newFile.exists())
    }

    @Test
    fun startWriteFailureIsNormalizedWithoutAttemptingAbort() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failStartWrite = true
        }

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            AtomicDocumentIo(backend).replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.WRITE, error.operation)
        assertEquals(DocumentCommitOutcome.NOT_COMMITTED, error.commitOutcome)
        assertEquals(0, backend.abortCount)
        assertArrayEquals("previous".toByteArray(), backend.committedBytes())
    }

    @Test
    fun writeFailureIsNormalizedAndAbortsPendingReplacement() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failWrite = true
        }
        val documentIo = AtomicDocumentIo(backend)

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            documentIo.replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.WRITE, error.operation)
        assertEquals(DocumentCommitOutcome.NOT_COMMITTED, error.commitOutcome)
        assertEquals(1, backend.abortCount)
        assertArrayEquals("previous".toByteArray(), documentIo.read())
    }

    @Test
    fun abortFailureIsSuppressedOnThePrimaryWriteFailure() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failWrite = true
            failAbort = true
        }

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            AtomicDocumentIo(backend).replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.WRITE, error.operation)
        assertEquals(1, error.suppressed.size)
        assertEquals("Injected abort failure.", error.suppressed.single().message)
        assertArrayEquals("previous".toByteArray(), backend.committedBytes())
    }

    @Test
    fun explicitSyncFailureIsObservableAndAbortsPendingReplacement() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failSync = true
        }
        val documentIo = AtomicDocumentIo(backend)

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            documentIo.replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.SYNC, error.operation)
        assertEquals(DocumentCommitOutcome.NOT_COMMITTED, error.commitOutcome)
        assertEquals(1, backend.abortCount)
        assertEquals(0, backend.commitCount)
        assertArrayEquals("previous".toByteArray(), documentIo.read())
    }

    @Test
    fun unchangedReadbackMakesSwallowedCommitFailureObservable() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            applyCommit = false
        }
        val documentIo = AtomicDocumentIo(backend)

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            documentIo.replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.VERIFY, error.operation)
        assertEquals(DocumentCommitOutcome.MAY_HAVE_COMMITTED, error.commitOutcome)
        assertEquals(1, backend.commitCount)
        assertArrayEquals("previous".toByteArray(), documentIo.read())
    }

    @Test
    fun commitExceptionIsNormalizedWithoutClaimingRollback() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failCommit = true
        }
        val documentIo = AtomicDocumentIo(backend)

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            documentIo.replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.COMMIT, error.operation)
        assertEquals(DocumentCommitOutcome.MAY_HAVE_COMMITTED, error.commitOutcome)
        assertEquals(1, backend.commitCount)
        assertEquals(0, backend.abortCount)
        assertArrayEquals("previous".toByteArray(), documentIo.read())
    }

    @Test
    fun verificationReadFailureReportsThatReplacementMayHaveCommitted() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            readFailuresAfterCommit = 1
        }
        val documentIo = AtomicDocumentIo(backend)

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            documentIo.replace("replacement".toByteArray())
        }

        assertEquals(DocumentIoOperation.VERIFY, error.operation)
        assertEquals(DocumentCommitOutcome.MAY_HAVE_COMMITTED, error.commitOutcome)
        assertArrayEquals("replacement".toByteArray(), documentIo.read())
    }

    @Test
    fun fakeBackendExposesReadFailureWithoutAndroidFilesystemTricks() {
        val backend = FakeAtomicDocumentBackend("previous".toByteArray()).apply {
            failRead = true
        }

        val error = assertThrows(AtomicDocumentIoException::class.java) {
            AtomicDocumentIo(backend).read()
        }

        assertEquals(DocumentIoOperation.READ, error.operation)
        assertEquals(DocumentCommitOutcome.NOT_ATTEMPTED, error.commitOutcome)
    }

    @Test
    fun readOrReplaceCanKeepCurrentBytesWithoutStartingAWrite() {
        val backend = FakeAtomicDocumentBackend("current".toByteArray())
        val documentIo = AtomicDocumentIo(backend)

        val result = documentIo.readOrReplace { current ->
            assertArrayEquals("current".toByteArray(), current)
            null
        }

        assertArrayEquals("current".toByteArray(), result)
        assertEquals(0, backend.startWriteCount)
        assertArrayEquals("current".toByteArray(), documentIo.read())
    }

    @Test
    fun updateSerializesCompleteReadModifyWriteOperations() {
        val documentIo = AtomicDocumentIo(FakeAtomicDocumentBackend("0".toByteArray()))
        val firstTransformEntered = CountDownLatch(1)
        val releaseFirstTransform = CountDownLatch(1)
        val secondTransformAttempted = CountDownLatch(1)
        val secondTransformEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit {
                documentIo.update { current ->
                    firstTransformEntered.countDown()
                    assertTrue(releaseFirstTransform.await(5, TimeUnit.SECONDS))
                    ((current!!.decodeToString().toInt()) + 1).toString().toByteArray()
                }
            }
            assertTrue(firstTransformEntered.await(5, TimeUnit.SECONDS))

            val second = executor.submit {
                secondTransformAttempted.countDown()
                documentIo.update { current ->
                    secondTransformEntered.countDown()
                    ((current!!.decodeToString().toInt()) + 1).toString().toByteArray()
                }
            }

            assertTrue(secondTransformAttempted.await(5, TimeUnit.SECONDS))
            assertFalse(secondTransformEntered.await(200, TimeUnit.MILLISECONDS))
            releaseFirstTransform.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)

            assertArrayEquals("2".toByteArray(), documentIo.read())
        } finally {
            releaseFirstTransform.countDown()
            executor.shutdownNow()
        }
    }
}

private class FakeAtomicDocumentBackend(
    initialBytes: ByteArray? = null,
) : AtomicDocumentBackend {
    private var committedBytes: ByteArray? = initialBytes?.copyOf()

    var failRead: Boolean = false
    var failStartWrite: Boolean = false
    var failWrite: Boolean = false
    var failSync: Boolean = false
    var failCommit: Boolean = false
    var failAbort: Boolean = false
    var applyCommit: Boolean = true
    var readFailuresAfterCommit: Int = 0
    private var queuedReadFailures: Int = 0

    var abortCount: Int = 0
        private set
    var commitCount: Int = 0
        private set
    var startWriteCount: Int = 0
        private set

    override val description: String = "fake document"

    override fun hasReadableData(): Boolean = committedBytes != null

    override fun readFully(): ByteArray {
        if (queuedReadFailures > 0) {
            queuedReadFailures -= 1
            throw IOException("Injected verification read failure.")
        }
        if (failRead) throw IOException("Injected read failure.")
        return committedBytes?.copyOf() ?: throw FileNotFoundException(description)
    }

    override fun startWrite(): AtomicDocumentPendingWrite {
        startWriteCount += 1
        if (failStartWrite) throw IOException("Injected start-write failure.")
        return object : AtomicDocumentPendingWrite {
            private val pendingBytes = ByteArrayOutputStream()

            override fun write(bytes: ByteArray) {
                if (failWrite) {
                    pendingBytes.write(bytes, 0, bytes.size / 2)
                    throw IOException("Injected write failure.")
                }
                pendingBytes.write(bytes)
            }

            override fun sync() {
                if (failSync) throw IOException("Injected sync failure.")
            }

            override fun commit() {
                commitCount += 1
                if (failCommit) throw IOException("Injected commit failure.")
                if (applyCommit) {
                    committedBytes = pendingBytes.toByteArray()
                }
                queuedReadFailures = readFailuresAfterCommit
            }

            override fun abort() {
                abortCount += 1
                if (failAbort) throw IOException("Injected abort failure.")
            }
        }
    }

    fun committedBytes(): ByteArray? = committedBytes?.copyOf()
}
