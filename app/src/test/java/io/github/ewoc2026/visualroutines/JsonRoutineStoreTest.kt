package io.github.ewoc2026.visualroutines

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRoutineStoreTest {
    @Test
    fun missingFileLoadsEmptyCurrentDocument() {
        val store = JsonRoutineStore(Files.createTempDirectory("vr-routines").resolve("routines.json").toFile())

        val document = store.load()

        assertEquals(CURRENT_ROUTINE_STORE_FORMAT_VERSION, document.formatVersion)
        assertTrue(document.routines.isEmpty())
    }

    @Test
    fun versionOneDocumentMigratesToVersionThreeWithoutChangingRoutineData() {
        val directory = Files.createTempDirectory("vr-routines")
        val file = directory.resolve("routines.json").toFile()
        file.writeText(
            """
            {
              "formatVersion": 1,
              "routines": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "title": "  Change bed linen  ",
                  "steps": [
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "instruction": "  Take the old sheet off the mattress.  ",
                      "note": "  Put it in the laundry basket.  "
                    },
                    {
                      "id": "33333333-3333-4333-8333-333333333333",
                      "instruction": "Put a clean sheet on the mattress."
                    }
                  ]
                },
                {
                  "id": "44444444-4444-4444-8444-444444444444",
                  "title": "Second routine",
                  "steps": [
                    {
                      "id": "55555555-5555-4555-8555-555555555555",
                      "instruction": "Do the next thing."
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val document = JsonRoutineStore(file).load()

        assertEquals(
            StoredRoutineDocument(
                routines = listOf(
                    StoredRoutine(
                        id = "11111111-1111-4111-8111-111111111111",
                        title = "  Change bed linen  ",
                        steps = listOf(
                            StoredRoutineStep(
                                id = "22222222-2222-4222-8222-222222222222",
                                instruction = "  Take the old sheet off the mattress.  ",
                                note = "  Put it in the laundry basket.  ",
                            ),
                            StoredRoutineStep(
                                id = "33333333-3333-4333-8333-333333333333",
                                instruction = "Put a clean sheet on the mattress.",
                            ),
                        ),
                    ),
                    StoredRoutine(
                        id = "44444444-4444-4444-8444-444444444444",
                        title = "Second routine",
                        steps = listOf(
                            StoredRoutineStep(
                                id = "55555555-5555-4555-8555-555555555555",
                                instruction = "Do the next thing.",
                            ),
                        ),
                    ),
                ),
            ),
            document,
        )
        assertTrue(file.readText().contains("\"formatVersion\": 3"))
        assertFalse(file.readText().contains("\"image\""))
        assertFalse(directory.resolve("routines.json.new").toFile().exists())
        assertFalse(directory.resolve("routines.json.bak").toFile().exists())
    }

    @Test
    fun versionThreeLoadDoesNotRewriteTheCurrentDocument() {
        val bytes = """{"formatVersion":3,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(bytes)
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        val document = store.load()

        assertEquals(CURRENT_ROUTINE_STORE_FORMAT_VERSION, document.formatVersion)
        assertTrue(document.routines.isEmpty())
        assertEquals(0, backend.startWriteCount)
        assertArrayEquals(bytes, backend.committedBytes())
    }

    @Test
    fun failedVersionOneMigrationLeavesOriginalDocumentRecoverable() {
        val original = """{"formatVersion":1,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original).apply {
            failSync = true
        }
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        val error = assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(DocumentIoOperation.SYNC, (error.cause as AtomicDocumentIoException).operation)
        assertEquals(1, backend.abortCount)
        assertEquals(0, backend.commitCount)
        assertArrayEquals(original, backend.committedBytes())
    }

    @Test
    fun failedVersionTwoMigrationLeavesOriginalDocumentRecoverable() {
        val original = """{"formatVersion":2,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original).apply {
            failSync = true
        }
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        val error = assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(DocumentIoOperation.SYNC, (error.cause as AtomicDocumentIoException).operation)
        assertEquals(1, backend.abortCount)
        assertEquals(0, backend.commitCount)
        assertArrayEquals(original, backend.committedBytes())
    }

    @Test
    fun migrationReadbackMismatchIsReportedWithoutClaimingSuccess() {
        val original = """{"formatVersion":1,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original).apply {
            applyCommit = false
        }
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        val error = assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(DocumentIoOperation.VERIFY, (error.cause as AtomicDocumentIoException).operation)
        assertEquals(0, backend.abortCount)
        assertEquals(1, backend.commitCount)
        assertArrayEquals(original, backend.committedBytes())
    }

    @Test
    fun appliedMigrationWithVerificationReadFailureIsRecoverableOnNextLoad() {
        val original = """{"formatVersion":1,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original).apply {
            readFailuresAfterCommit = 1
        }
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        val error = assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertTrue(error.mayHaveCommitted)
        assertEquals(DocumentIoOperation.VERIFY, (error.cause as AtomicDocumentIoException).operation)
        assertEquals(1, backend.commitCount)

        assertEquals(CURRENT_ROUTINE_STORE_FORMAT_VERSION, store.load().formatVersion)
        assertEquals(1, backend.startWriteCount)
    }

    @Test
    fun invalidVersionOneDocumentIsRejectedBeforeMigrationWriteStarts() {
        val invalid = """
            {
              "formatVersion": 1,
              "routines": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "title": " ",
                  "steps": [
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "instruction": "Do the thing."
                    }
                  ]
                }
              ]
            }
        """.trimIndent().encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(invalid)
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(0, backend.startWriteCount)
        assertArrayEquals(invalid, backend.committedBytes())
    }

    @Test
    fun unknownVersionOneFieldIsRejectedWithoutMigrationWrite() {
        val original = """
            {
              "formatVersion": 1,
              "routines": [],
              "futureField": "must not be discarded"
            }
        """.trimIndent().encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original)
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(0, backend.startWriteCount)
        assertArrayEquals(original, backend.committedBytes())
    }

    @Test
    fun unknownVersionThreeFieldIsRejectedWithoutRepositoryMutation() {
        val original = """
            {
              "formatVersion": 2,
              "routines": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "title": "Existing routine",
                  "steps": [
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "instruction": "Existing step.",
                      "futureField": "must not be discarded"
                    }
                  ]
                }
              ]
            }
        """.trimIndent().encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original)
        val repository = JsonRoutineRepository(
            JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store"),
        )

        assertThrows(RoutineStoreException::class.java) {
            repository.createRoutine(
                routineId = "33333333-3333-4333-8333-333333333333",
                title = "New routine",
                steps = listOf(RoutineStepContent("New step.")),
            )
        }

        assertEquals(0, backend.startWriteCount)
        assertArrayEquals(original, backend.committedBytes())
    }

    @Test
    fun malformedUtf8IsRejectedWithoutMigrationWrite() {
        val original = """
            {
              "formatVersion": 1,
              "routines": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "title": "Routine X",
                  "steps": [
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "instruction": "Existing step."
                    }
                  ]
                }
              ]
            }
        """.trimIndent().encodeToByteArray()
        val malformed = original.copyOf().also { bytes ->
            bytes[bytes.indexOf('X'.code.toByte())] = 0xff.toByte()
        }
        val backend = RecordingAtomicDocumentBackend(malformed)
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")

        assertThrows(RoutineStoreException::class.java) {
            store.load()
        }

        assertEquals(0, backend.startWriteCount)
        assertArrayEquals(malformed, backend.committedBytes())
    }

    @Test
    fun savedDocumentRoundTripsThroughJsonFile() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val document = StoredRoutineDocument(
            routines = listOf(
                StoredRoutine(
                    id = "11111111-1111-4111-8111-111111111111",
                    title = "Change bed linen",
                    steps = listOf(
                        StoredRoutineStep(
                            id = "22222222-2222-4222-8222-222222222222",
                            instruction = "Take the old sheet off the mattress.",
                            note = "Put it in the laundry basket.",
                        ),
                        StoredRoutineStep(
                            id = "33333333-3333-4333-8333-333333333333",
                            instruction = "Put a clean sheet on the mattress.",
                        ),
                    ),
                ),
            ),
        )

        store.update { document }

        assertEquals(document, store.load())
        assertTrue(file.readText().contains("\"formatVersion\": 3"))
        assertTrue(file.readText().contains("\"title\": \"Change bed linen\""))
    }

    @Test
    fun versionThreeImageVariantsRoundTrip() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val document = StoredRoutineDocument(
            routines = listOf(
                StoredRoutine(
                    id = "11111111-1111-4111-8111-111111111111",
                    title = "Change bed linen",
                    steps = listOf(
                        StoredRoutineStep(
                            id = "22222222-2222-4222-8222-222222222222",
                            instruction = "Take the old sheet off the mattress.",
                        ),
                        StoredRoutineStep(
                            id = "33333333-3333-4333-8333-333333333333",
                            instruction = "Take the duvet out of the duvet cover.",
                            image = StoredRoutineStepImage(
                                source = StoredImageSource.BUNDLED,
                                assetId = "bed-linen-remove-duvet-cover",
                                semanticRole = StoredImageSemanticRole.INFORMATIVE,
                            ),
                        ),
                        StoredRoutineStep(
                            id = "44444444-4444-4444-8444-444444444444",
                            instruction = "Put the duvet into a clean duvet cover.",
                            image = StoredRoutineStepImage(
                                source = StoredImageSource.BUNDLED,
                                assetId = "bed-linen-insert-duvet-cover",
                                semanticRole = StoredImageSemanticRole.INFORMATIVE,
                                descriptionOverride = "A white duvet entering a gray cover.",
                            ),
                        ),
                        StoredRoutineStep(
                            id = "55555555-5555-4555-8555-555555555555",
                            instruction = "Finish the bed with the bedspread.",
                            image = StoredRoutineStepImage(
                                source = StoredImageSource.BUNDLED,
                                assetId = "bed-linen-finish-with-bedspread",
                                semanticRole = StoredImageSemanticRole.REDUNDANT,
                                descriptionOverride = "Retained while the role is redundant.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        store.update { document }

        assertEquals(document, store.load())
        assertTrue(file.readText().contains("\"source\": \"bundled\""))
        assertTrue(file.readText().contains("\"semanticRole\": \"informative\""))
        assertTrue(file.readText().contains("\"semanticRole\": \"redundant\""))
    }

    @Test
    fun blankImageAssetIdAndPresentBlankOverrideAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredRoutineStepImage(
                source = StoredImageSource.BUNDLED,
                assetId = " ",
                semanticRole = StoredImageSemanticRole.INFORMATIVE,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            StoredRoutineStepImage(
                source = StoredImageSource.BUNDLED,
                assetId = "bed-linen-remove-sheet",
                semanticRole = StoredImageSemanticRole.INFORMATIVE,
                descriptionOverride = " ",
            )
        }
    }

    @Test
    fun unknownStructurallyValidBundledAssetRemainsLoadable() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        file.writeText(
            """
            {
              "formatVersion": 2,
              "routines": [
                {
                  "id": "11111111-1111-4111-8111-111111111111",
                  "title": "Change bed linen",
                  "steps": [
                    {
                      "id": "22222222-2222-4222-8222-222222222222",
                      "instruction": "Take the old sheet off the mattress.",
                      "image": {
                        "source": "bundled",
                        "assetId": "future-bundled-image",
                        "semanticRole": "redundant"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val catalog = JsonRoutineRepository(JsonRoutineStore(file)).loadCatalog()
        val image = catalog.routines.first().steps.single().image

        assertEquals("future-bundled-image", image?.assetId)
        assertNull(catalog.userRoutineLoadError)
        assertNull(BundledImageLibrary.resolve(image))
    }

    @Test
    fun unknownFutureFormatVersionIsRejectedWithoutRewriting() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val unsupportedDocument = """{"formatVersion":99,"routines":[]}"""
        file.writeText(unsupportedDocument)

        assertThrows(RoutineStoreException::class.java) {
            JsonRoutineStore(file).load()
        }

        assertEquals(unsupportedDocument, file.readText())
        assertFalse(File("${file.path}.new").exists())
    }

    @Test
    fun blankRoutineTitleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredRoutine(
                id = "11111111-1111-4111-8111-111111111111",
                title = " ",
                steps = listOf(
                    StoredRoutineStep(
                        id = "22222222-2222-4222-8222-222222222222",
                        instruction = "Do the thing.",
                    ),
                ),
            )
        }
    }

    @Test
    fun duplicateStepIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredRoutine(
                id = "11111111-1111-4111-8111-111111111111",
                title = "Test routine",
                steps = listOf(
                    StoredRoutineStep(
                        id = "22222222-2222-4222-8222-222222222222",
                        instruction = "First.",
                    ),
                    StoredRoutineStep(
                        id = "22222222-2222-4222-8222-222222222222",
                        instruction = "Second.",
                    ),
                ),
            )
        }
    }

    @Test
    fun changedUpdateUsesAtomicFileWithoutLeavingNewFileBehind() {
        val directory = Files.createTempDirectory("vr-routines")
        val file = directory.resolve("routines.json").toFile()

        JsonRoutineStore(file).update { document ->
            document.copy(
                routines = listOf(
                    StoredRoutine(
                        id = "11111111-1111-4111-8111-111111111111",
                        title = "Change bed linen",
                        steps = listOf(
                            StoredRoutineStep(
                                id = "22222222-2222-4222-8222-222222222222",
                                instruction = "Take the old sheet off the mattress.",
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(file.exists())
        assertFalse(directory.resolve("routines.json.new").toFile().exists())
    }

    @Test
    fun repositoryLoadsUserRoutinesBeforeBundledExamples() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        JsonRoutineStore(file).update {
            StoredRoutineDocument(
                routines = listOf(
                    StoredRoutine(
                        id = "11111111-1111-4111-8111-111111111111",
                        title = "Change bed linen",
                        steps = listOf(
                            StoredRoutineStep(
                                id = "22222222-2222-4222-8222-222222222222",
                                instruction = "Take the old sheet off the mattress.",
                                note = "Put it in the laundry basket.",
                            ),
                        ),
                    ),
                ),
            )
        }

        val catalog = JsonRoutineRepository(JsonRoutineStore(file)).loadCatalog()

        assertEquals("11111111-1111-4111-8111-111111111111", catalog.routines.first().id)
        assertEquals("Change bed linen", catalog.routines.first().name)
        assertEquals("Take the old sheet off the mattress.", catalog.routines.first().steps.first().instruction)
        assertEquals("Put it in the laundry basket.", catalog.routines.first().steps.first().supportingInstruction)
        assertTrue(catalog.routines.drop(1).any { it.id == "prepare-for-exercise" })
    }

    @Test
    fun repositoryCreatesUserRoutineBeforeBundledExamples() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val repository = JsonRoutineRepository(store)

        val catalog = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "  Change bed linen  ",
            steps = listOf(
                RoutineStepContent(
                    instruction = "  Take the old sheet off the mattress.  ",
                    note = "  Put it in the laundry basket.  ",
                ),
                RoutineStepContent("Put a clean sheet on the mattress."),
            ),
        )

        val createdRoutine = catalog.routines.first()
        assertTrue(createdRoutine.id.isUuidLikeForTest())
        assertEquals("Change bed linen", createdRoutine.name)
        assertEquals("Take the old sheet off the mattress.", createdRoutine.steps.first().instruction)
        assertEquals("Put it in the laundry basket.", createdRoutine.steps.first().supportingInstruction)
        assertTrue(catalog.routines.drop(1).any { it.id == "prepare-for-exercise" })

        val storedRoutine = store.load().routines.single()
        assertTrue(storedRoutine.id.isUuidLikeForTest())
        assertTrue(storedRoutine.steps.all { it.id.isUuidLikeForTest() })
        assertEquals("Change bed linen", storedRoutine.title)
        assertEquals("Take the old sheet off the mattress.", storedRoutine.steps.first().instruction)
        assertEquals("Put it in the laundry basket.", storedRoutine.steps.first().note)
    }

    @Test
    fun repositoryReconcilesAppliedCreateAfterVerificationReadFailure() {
        val original = """{"formatVersion":3,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original).apply {
            readFailuresAfterCommit = 1
        }
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")
        val repository = JsonRoutineRepository(store)

        val catalog = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "Change bed linen",
            steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
        )

        assertEquals(1, backend.commitCount)
        assertEquals(1, backend.startWriteCount)
        assertEquals(
            listOf("11111111-1111-4111-8111-111111111111"),
            catalog.routines.filter { it.id in catalog.userRoutineIds }.map(Routine::id),
        )
        assertEquals(
            listOf("11111111-1111-4111-8111-111111111111"),
            store.load().routines.map(StoredRoutine::id),
        )
    }

    @Test
    fun repeatedCreateRequestIsIdempotentAndDoesNotRewriteCurrentDocument() {
        val original = """{"formatVersion":3,"routines":[]}""".encodeToByteArray()
        val backend = RecordingAtomicDocumentBackend(original)
        val repository = JsonRoutineRepository(
            JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store"),
        )

        repeat(2) {
            repository.createRoutine(
                routineId = "11111111-1111-4111-8111-111111111111",
                title = "Change bed linen",
                steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
            )
        }

        assertEquals(1, backend.startWriteCount)
        assertEquals(1, backend.commitCount)
    }

    @Test
    fun repeatedCreateRequestUpdatesItsStableIdentityWithoutDuplication() {
        val store = JsonRoutineStore(
            AtomicDocumentIo(RecordingAtomicDocumentBackend()),
            "recording routine store",
        )
        val repository = JsonRoutineRepository(store)
        val routineId = "11111111-1111-4111-8111-111111111111"

        repository.createRoutine(
            routineId = routineId,
            title = "First draft",
            steps = listOf(RoutineStepContent("First step.")),
        )
        val catalog = repository.createRoutine(
            routineId = routineId,
            title = "Revised draft",
            steps = listOf(RoutineStepContent("Revised step.")),
        )

        assertEquals(listOf(routineId), store.load().routines.map(StoredRoutine::id))
        assertEquals("Revised draft", catalog.routines.first().name)
        assertEquals("Revised step.", catalog.routines.first().steps.single().instruction)
    }

    @Test
    fun repositoryReconcilesAppliedDeleteAfterVerificationReadFailure() {
        val backend = RecordingAtomicDocumentBackend()
        val store = JsonRoutineStore(AtomicDocumentIo(backend), "recording routine store")
        val repository = JsonRoutineRepository(store)
        val routineId = "11111111-1111-4111-8111-111111111111"
        repository.createRoutine(
            routineId = routineId,
            title = "Change bed linen",
            steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
        )
        backend.readFailuresAfterCommit = 1

        val catalog = repository.deleteRoutine(routineId)

        assertTrue(catalog.userRoutineIds.isEmpty())
        assertTrue(store.load().routines.isEmpty())
        assertEquals(2, backend.commitCount)
    }

    @Test
    fun repositoryUpdatesExistingUserRoutineInPlace() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val repository = JsonRoutineRepository(store)
        val created = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "Change bed linen",
            steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
        ).routines.first()
        val originalStepId = store.load().routines.single().steps.single().id

        val catalog = repository.updateRoutine(
            routineId = created.id,
            title = "Change the bed linen",
            steps = listOf(
                RoutineStepContent(
                    instruction = "Put a clean sheet on the mattress.",
                    note = "  Smooth out the corners.  ",
                    id = originalStepId,
                ),
                RoutineStepContent("Put the pillow in a clean pillowcase."),
            ),
        )

        assertEquals(created.id, catalog.routines.first().id)
        assertEquals("Change the bed linen", catalog.routines.first().name)
        assertEquals(2, catalog.routines.first().steps.size)
        assertEquals(
            "Smooth out the corners.",
            catalog.routines.first().steps.first().supportingInstruction,
        )
        assertEquals(setOf(created.id), catalog.userRoutineIds)
        assertEquals("Change the bed linen", store.load().routines.single().title)
        assertEquals("Smooth out the corners.", store.load().routines.single().steps.first().note)
        assertEquals(originalStepId, store.load().routines.single().steps.first().id)
        assertTrue(store.load().routines.single().steps.last().id.isUuidLikeForTest())
    }

    @Test
    fun repositoryCreateReplaceAndRemovePreserveImageDataAndStepIdentity() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val repository = JsonRoutineRepository(store)
        val initialImage = RoutineStepImage(
            source = RoutineImageSource.BUNDLED,
            assetId = "  bed-linen-remove-sheet  ",
            semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            descriptionOverride = "  A fitted sheet being pulled from a mattress corner.  ",
        )

        val created = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "Change bed linen",
            steps = listOf(
                RoutineStepContent(
                    instruction = "Take the old sheet off the mattress.",
                    image = initialImage,
                ),
                RoutineStepContent("Put the old bed linen in the laundry basket."),
            ),
        ).routines.first()
        val originalStep = created.steps.first()

        assertEquals(
            initialImage.copy(
                assetId = "bed-linen-remove-sheet",
                descriptionOverride = "A fitted sheet being pulled from a mattress corner.",
            ),
            originalStep.image,
        )
        assertNull(created.steps.last().image)
        assertEquals(
            StoredRoutineStepImage(
                source = StoredImageSource.BUNDLED,
                assetId = "bed-linen-remove-sheet",
                semanticRole = StoredImageSemanticRole.INFORMATIVE,
                descriptionOverride = "A fitted sheet being pulled from a mattress corner.",
            ),
            store.load().routines.single().steps.first().image,
        )

        val replacementImage = RoutineStepImage(
            source = RoutineImageSource.BUNDLED,
            assetId = "bed-linen-remove-duvet-cover",
            semanticRole = RoutineImageSemanticRole.REDUNDANT,
            descriptionOverride = "Retain this text if the role changes back.",
        )
        val replaced = repository.updateRoutine(
            routineId = created.id,
            title = created.name,
            steps = listOf(
                RoutineStepContent(
                    id = originalStep.id,
                    instruction = originalStep.instruction,
                    image = replacementImage,
                ),
                RoutineStepContent(
                    id = created.steps.last().id,
                    instruction = created.steps.last().instruction,
                ),
            ),
        ).routines.first()

        assertEquals(originalStep.id, replaced.steps.first().id)
        assertEquals(originalStep.instruction, replaced.steps.first().instruction)
        assertEquals(replacementImage, replaced.steps.first().image)

        val removed = repository.updateRoutine(
            routineId = created.id,
            title = created.name,
            steps = replaced.steps.map { step ->
                RoutineStepContent(
                    id = step.id,
                    instruction = step.instruction,
                    note = step.supportingInstruction,
                    image = null,
                )
            },
        ).routines.first()

        assertEquals(replaced.steps.map(RoutineStep::id), removed.steps.map(RoutineStep::id))
        assertTrue(removed.steps.all { it.image == null })
    }

    @Test
    fun repositoryUpdateRemovesExistingStepNote() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val repository = JsonRoutineRepository(store)
        val created = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "Change bed linen",
            steps = listOf(
                RoutineStepContent(
                    instruction = "Take the old sheet off the mattress.",
                    note = "Put it in the laundry basket.",
                ),
            ),
        ).routines.first()
        val originalStepId = store.load().routines.single().steps.single().id

        val catalog = repository.updateRoutine(
            routineId = created.id,
            title = created.name,
            steps = listOf(
                RoutineStepContent(
                    instruction = "Take the old sheet off the mattress.",
                    id = originalStepId,
                ),
            ),
        )

        assertNull(catalog.routines.first().steps.single().supportingInstruction)
        assertNull(store.load().routines.single().steps.single().note)
        assertEquals(originalStepId, store.load().routines.single().steps.single().id)
    }

    @Test
    fun repositoryDeletesExistingUserRoutine() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val store = JsonRoutineStore(file)
        val repository = JsonRoutineRepository(store)
        val created = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "Change bed linen",
            steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
        ).routines.first()

        val catalog = repository.deleteRoutine(created.id)

        assertTrue(catalog.userRoutineIds.isEmpty())
        assertTrue(catalog.routines.none { it.id == created.id })
        assertTrue(catalog.routines.any { it.id == "prepare-for-exercise" })
        assertTrue(store.load().routines.isEmpty())
    }

    @Test
    fun repositoryPersistsUserRoutineOrder() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val repository = JsonRoutineRepository(JsonRoutineStore(file))
        val first = repository.createRoutine(
            routineId = "11111111-1111-4111-8111-111111111111",
            title = "First routine",
            steps = listOf(RoutineStepContent("Do the first thing.")),
        ).routines.first { it.name == "First routine" }
        val second = repository.createRoutine(
            routineId = "22222222-2222-4222-8222-222222222222",
            title = "Second routine",
            steps = listOf(RoutineStepContent("Do the second thing.")),
        ).routines.first { it.name == "Second routine" }

        val catalog = repository.reorderUserRoutines(listOf(second.id, first.id))

        assertEquals(
            listOf("Second routine", "First routine"),
            catalog.routines.filter { it.id in catalog.userRoutineIds }.map(Routine::name),
        )
        assertEquals(
            listOf("Second routine", "First routine"),
            JsonRoutineStore(file).load().routines.map(StoredRoutine::title),
        )
    }

    @Test
    fun repositoryFallsBackToBundledExamplesWhenUserStoreIsInvalid() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        file.writeText("""{"formatVersion":99,"routines":[]}""")

        val catalog = JsonRoutineRepository(JsonRoutineStore(file)).loadCatalog()

        assertEquals(BundledRoutines.all, catalog.routines)
        assertNotNull(catalog.userRoutineLoadError)
    }

    @Test
    fun repositoryMutationDoesNotOverwriteInvalidDocument() {
        val file = Files.createTempDirectory("vr-routines").resolve("routines.json").toFile()
        val unsupportedDocument = """{"formatVersion":99,"routines":[]}"""
        file.writeText(unsupportedDocument)
        val repository = JsonRoutineRepository(JsonRoutineStore(file))

        assertThrows(RoutineStoreException::class.java) {
            repository.createRoutine(
                routineId = "11111111-1111-4111-8111-111111111111",
                title = "Change bed linen",
                steps = listOf(RoutineStepContent("Take the old sheet off the mattress.")),
            )
        }

        assertEquals(unsupportedDocument, file.readText())
        assertFalse(File("${file.path}.new").exists())
    }
}

private fun String.isUuidLikeForTest(): Boolean {
    return Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    ).matches(this)
}

private class RecordingAtomicDocumentBackend(
    initialBytes: ByteArray? = null,
) : AtomicDocumentBackend {
    private var bytes: ByteArray? = initialBytes?.copyOf()

    var failSync: Boolean = false
    var applyCommit: Boolean = true
    var readFailuresAfterCommit: Int = 0
    private var queuedReadFailures: Int = 0
    var startWriteCount: Int = 0
        private set
    var commitCount: Int = 0
        private set
    var abortCount: Int = 0
        private set

    override val description: String = "recording routine store"

    override fun hasReadableData(): Boolean = bytes != null

    override fun readFully(): ByteArray {
        if (queuedReadFailures > 0) {
            queuedReadFailures -= 1
            throw IOException("Injected verification read failure.")
        }
        return bytes?.copyOf() ?: throw FileNotFoundException(description)
    }

    override fun startWrite(): AtomicDocumentPendingWrite {
        startWriteCount += 1
        return object : AtomicDocumentPendingWrite {
            private val pendingBytes = ByteArrayOutputStream()

            override fun write(bytes: ByteArray) {
                pendingBytes.write(bytes)
            }

            override fun sync() {
                if (failSync) throw IOException("Injected migration sync failure.")
            }

            override fun commit() {
                commitCount += 1
                if (applyCommit) {
                    bytes = pendingBytes.toByteArray()
                }
                queuedReadFailures = readFailuresAfterCommit
            }

            override fun abort() {
                abortCount += 1
            }
        }
    }

    fun committedBytes(): ByteArray? = bytes?.copyOf()
}
