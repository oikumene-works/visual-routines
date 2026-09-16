package io.github.ewoc2026.visualroutines

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditorStateTest {
    @Test
    fun saverRoundTripPreservesEveryEditorImageField() {
        val steps = listOf(
            EditorStep(
                id = "first-step",
                instruction = "Take the sheet off the mattress.",
                note = "Put it with the laundry.",
                image = EditorStepImage(
                    source = RoutineImageSource.BUNDLED,
                    assetId = "bed-linen-remove-sheet",
                    semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                    descriptionOverride = "The old fitted sheet being pulled from the mattress.",
                ),
            ),
            EditorStep(
                id = "second-step",
                instruction = "Finish the bed with the bedspread.",
                image = EditorStepImage(
                    source = RoutineImageSource.BUNDLED,
                    assetId = "missing-bundled-image",
                    semanticRole = RoutineImageSemanticRole.REDUNDANT,
                    descriptionOverride = "Retained in case the role changes back.",
                ),
            ),
            EditorStep(
                id = "third-step",
                instruction = "Choose the image role.",
                note = "",
                image = EditorStepImage(
                    source = RoutineImageSource.BUNDLED,
                    assetId = "bed-linen-insert-duvet-cover",
                ),
            ),
            EditorStep(
                id = "fourth-step",
                instruction = "Keep this step text-only.",
            ),
        )

        assertEquals(steps, decodeEditorSteps(encodeEditorSteps(steps)))
    }

    @Test
    fun saverRestoresLegacyTextOnlyEditorState() {
        val restored = decodeEditorSteps(
            listOf(
                "first-step",
                "Take the sheet off the mattress.",
                "1",
                "Put it with the laundry.",
                "second-step",
                "Put on a clean sheet.",
                "0",
                "",
            ),
        )

        assertEquals(
            listOf(
                EditorStep(
                    id = "first-step",
                    instruction = "Take the sheet off the mattress.",
                    note = "Put it with the laundry.",
                ),
                EditorStep(
                    id = "second-step",
                    instruction = "Put on a clean sheet.",
                ),
            ),
            restored,
        )
        assertNull(decodeEditorSteps(listOf("incomplete")))
    }

    @Test
    fun editorScreenSaverPreservesCreateAndEditRoutesWithInitialImages() {
        val create = RestorableEditorScreen.Create(
            draft = prefilledCreateRoutineDraft,
            returnDestination = CreateRoutineReturnDestination.ManageRoutines,
        )
        val edit = RestorableEditorScreen.Edit(
            routineId = "11111111-1111-4111-8111-111111111111",
            draft = CreateRoutineDraft(
                title = "Change bed linen",
                steps = listOf(
                    RoutineStepContent(
                        id = "22222222-2222-4222-8222-222222222222",
                        instruction = "Finish the bed with the bedspread.",
                        image = RoutineStepImage(
                            source = RoutineImageSource.BUNDLED,
                            assetId = "bed-linen-finish-with-bedspread",
                            semanticRole = RoutineImageSemanticRole.REDUNDANT,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(create, decodeEditorScreen(encodeEditorScreen(create)))
        assertEquals(edit, decodeEditorScreen(encodeEditorScreen(edit)))
        assertNull(decodeEditorScreen(listOf("editor-screen-v1", "create")))
    }

    @Test
    fun draftMappingAndReorderingKeepImageAttachedToStableStep() {
        val storedImage = RoutineStepImage(
            source = RoutineImageSource.BUNDLED,
            assetId = "bed-linen-remove-duvet-cover",
            semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            descriptionOverride = "A duvet being removed from its old cover.",
        )
        val first = RoutineStepContent(
            id = "first-step",
            instruction = "Take the duvet out of the duvet cover.",
            image = storedImage,
        ).toEditorStep()
        val second = RoutineStepContent(
            id = "second-step",
            instruction = "Put the old cover with the laundry.",
        ).toEditorStep()

        val moved = listOf(first, second).moved(0, 1)

        assertEquals("first-step", moved.last().id)
        assertEquals("bed-linen-remove-duvet-cover", moved.last().image?.assetId)
        assertEquals(
            RoutineStepContent(
                id = "first-step",
                instruction = "Take the duvet out of the duvet cover.",
                image = storedImage,
            ),
            moved.last().toRoutineStepContent(),
        )
    }

    @Test
    fun imageRequiresExplicitRoleAndNonBlankOverrideBeforeSave() {
        val unclassified = EditorStep(
            instruction = "Put the duvet in a clean duvet cover.",
            image = EditorStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "bed-linen-insert-duvet-cover",
            ),
        )
        val blankOverride = unclassified.copy(
            image = requireNotNull(unclassified.image).copy(
                semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                descriptionOverride = " ",
            ),
        )
        val ready = blankOverride.copy(
            image = requireNotNull(blankOverride.image).copy(
                descriptionOverride = "A duvet being guided into a clean cover.",
            ),
        )
        val unavailableButStructurallyValid = EditorStep(
            instruction = "Keep this complete text instruction.",
            image = EditorStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "missing-bundled-image",
                semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            ),
        )

        assertFalse(unclassified.canSave())
        assertFalse(blankOverride.canSave())
        assertTrue(ready.canSave())
        assertTrue(unavailableButStructurallyValid.canSave())
        assertThrows(IllegalArgumentException::class.java) {
            unclassified.toRoutineStepContent()
        }
    }

    @Test
    fun prefilledDraftStaysNonPersistedUntilSaveThenRestoresAllFourImages() {
        val file = Files.createTempDirectory("vr-image-editor").resolve("routines.json").toFile()
        val repository = JsonRoutineRepository(JsonRoutineStore(file))
        val state = VisualRoutinesState(TestSessionStore(), repository)

        state.openCreateRoutine(prefilledCreateRoutineDraft)

        assertFalse(file.exists())
        assertEquals(4, prefilledCreateRoutineDraft.steps.count { it.image != null })
        assertTrue(prefilledCreateRoutineDraft.steps.any { it.image == null })
        assertNull(prefilledCreateRoutineDraft.steps[1].note)
        assertEquals(
            "Put the old bed linen in the laundry basket.",
            prefilledCreateRoutineDraft.steps[4].instruction,
        )
        assertEquals(
            BundledImageLibrary.all.map(BundledImageAsset::assetId).toSet(),
            prefilledCreateRoutineDraft.steps.mapNotNull { it.image?.assetId }.toSet(),
        )
        assertTrue(
            prefilledCreateRoutineDraft.steps
                .mapNotNull(RoutineStepContent::image)
                .any { it.semanticRole == RoutineImageSemanticRole.REDUNDANT },
        )

        assertTrue(
            state.createRoutine(
                prefilledCreateRoutineDraft.title,
                prefilledCreateRoutineDraft.steps,
            ),
        )

        assertTrue(file.readText().contains("\"formatVersion\": 3"))
        val restoredRoutine = JsonRoutineRepository(JsonRoutineStore(file))
            .loadCatalog()
            .routines
            .first { it.id in state.userRoutineIds }
        assertEquals(
            prefilledCreateRoutineDraft.steps.map { it.image },
            restoredRoutine.steps.map { it.image },
        )
    }
}

private class TestSessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null

    override fun save(session: ActiveSession) = Unit

    override fun clear() = Unit
}
