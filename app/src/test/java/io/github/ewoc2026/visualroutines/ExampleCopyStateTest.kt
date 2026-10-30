package io.github.ewoc2026.visualroutines

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Checks copy isolation against the real persistent repository, including richer future examples. */
class ExampleCopyStateTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val example = Routine(
        id = "example-with-image",
        name = "Change bed linen",
        steps = listOf(
            RoutineStep(
                id = "example-step-1",
                instruction = "Remove the sheet.",
                supportingInstruction = "Put it in the laundry basket.",
                image = RoutineStepImage(
                    source = RoutineImageSource.BUNDLED,
                    assetId = "bed-linen-remove-sheet",
                    semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                    descriptionOverride = "Lift the sheet off the mattress.",
                ),
            ),
            RoutineStep("example-step-2", "Put on the clean sheet."),
        ),
    )
    private val file by lazy { File(temporaryFolder.root, "routines.json") }
    private fun repository() = JsonRoutineRepository(JsonRoutineStore(file), listOf(example))
    private val sessions = CopySessionStore()
    private fun state() = VisualRoutinesState(sessions, repository())

    @Test fun savedCopyKeepsContentButHasFreshIdentitiesAndPreservesActiveExample() {
        val state = state()
        state.startRoutine(example.id)
        state.done()
        state.confirmPause()
        val progress = state.activeSession
        state.openExampleCopy(example.id, "Copy of ${example.name}")
        val draft = (state.screen as AppScreen.CreateRoutine).draft
        assertFalse(file.exists())
        assertTrue(draft.steps.none { step -> example.steps.any { it.id == step.id } })
        assertTrue(state.createRoutine(draft.title, draft.steps))

        val restored = state()
        val copy = restored.routines.single { it.id in restored.userRoutineIds }
        assertNotEquals(example.id, copy.id)
        assertEquals(draft.steps.map { it.id }, copy.steps.map { it.id })
        assertEquals(example.steps, copy.steps.mapIndexed { index, step -> step.copy(id = example.steps[index].id) })
        assertEquals(example, restored.routine(example.id))
        assertEquals(progress, restored.activeSession)
        assertEquals(progress, sessions.saved)

        restored.openEditRoutine(copy.id)
        val edit = (restored.screen as AppScreen.EditRoutine).draft
        assertTrue(restored.updateRoutine(copy.id, "My bed linen", edit.steps.map {
            it.copy(instruction = "Personal instruction", note = null, image = null)
        }))
        val edited = state()
        assertEquals("Personal instruction", edited.routine(copy.id).steps.first().instruction)
        assertNull(edited.routine(copy.id).steps.first().image)
        assertEquals(example, edited.routine(example.id))
        assertEquals(progress, edited.activeSession)
        edited.startRoutine(copy.id)
        assertEquals(AppDialog.Replace(copy.id), edited.dialog)
        assertEquals(progress, edited.activeSession)
        edited.confirmReplacement(copy.id)
        assertEquals(copy.id, edited.activeSession?.routineId)
        assertEquals(copy.steps.first().id, edited.activeSession?.currentStepId)
        repeat(copy.steps.size) { edited.done() }
        assertEquals(AppScreen.Complete(copy.id), edited.screen)
    }

    @Test fun cancelCreatesNothingAndRepeatedCopiesAreIndependent() {
        val state = state()
        state.openExampleChooser()
        state.openExampleCopy(example.id, "First draft")
        val cancelled = (state.screen as AppScreen.CreateRoutine).draft
        state.cancelCreateRoutine()
        assertEquals(AppScreen.ManageRoutines, state.screen)
        assertFalse(file.exists())
        assertTrue(state().userRoutineIds.isEmpty())
        repeat(2) { index ->
            state.openExampleCopy(example.id, "Copy $index")
            val draft = (state.screen as AppScreen.CreateRoutine).draft
            assertTrue(draft.steps.none { step -> cancelled.steps.any { it.id == step.id } })
            assertTrue(state.createRoutine(draft.title, draft.steps))
        }
        val copies = state().routines.filter { it.id in state.userRoutineIds }
        assertEquals(2, copies.size)
        assertEquals(4, copies.flatMap { it.steps }.map { it.id }.distinct().size)
    }

    @Test fun editingDisabledBlocksChooserDraftAndSaveWhileHiddenExamplesRemainAvailable() {
        val state = state()
        state.updateShowExampleRoutines(false)
        assertTrue(state.homeRoutines.isEmpty())
        assertEquals(listOf(example), state.exampleRoutines)
        state.updateAllowRoutineEditing(false)
        state.openExampleChooser()
        state.openExampleCopy(example.id, "Blocked")
        assertEquals(AppScreen.Home, state.screen)
        assertFalse(file.exists())
        state.updateAllowRoutineEditing(true)
        state.openExampleCopy(example.id, "Draft")
        val draft = (state.screen as AppScreen.CreateRoutine).draft
        state.updateAllowRoutineEditing(false)
        assertFalse(state.createRoutine(draft.title, draft.steps))
        assertFalse(file.exists())
    }

    @Test fun personalAndUnknownRoutinesCannotEnterExampleCopyPath() {
        val state = state()
        state.createRoutine("Personal", listOf(RoutineStepContent("Do something.")))
        state.openExampleChooser()
        state.openExampleCopy(state.userRoutineIds.single(), "Not an example")
        assertEquals(AppScreen.ChooseExample, state.screen)
        state.openExampleCopy("missing", "Missing")
        assertEquals(AppScreen.ChooseExample, state.screen)
        assertEquals(listOf(example), state.exampleRoutines)
    }
}

private class CopySessionStore : SessionStore {
    var saved: ActiveSession? = null
    override fun load(routines: List<Routine>) = saved
    override fun save(session: ActiveSession) { saved = session }
    override fun clear() { saved = null }
}
