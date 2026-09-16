package io.github.ewoc2026.visualroutines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEngineTest {
    private val routine = Routine(
        id = "test",
        name = "Test routine",
        steps = listOf(
            RoutineStep("first", "First"),
            RoutineStep("second", "Second"),
            RoutineStep("third", "Third"),
        ),
    )

    @Test
    fun doneRecordsStatusAndAdvancesOneStep() {
        val engine = RoutineEngine(routine, ActiveSession(routine.id, routine.steps[0].id))

        val result = engine.done()

        assertTrue(result is AdvanceResult.Active)
        assertEquals(routine.steps[1].id, engine.session.currentStepId)
        assertEquals(StepStatus.DONE, engine.session.statuses[routine.steps[0].id])
    }

    @Test
    fun previousDoesNotChangeExistingStatuses() {
        val initial = ActiveSession(
            routineId = routine.id,
            currentStepId = routine.steps[1].id,
            statuses = mapOf(routine.steps[0].id to StepStatus.DONE),
        )
        val engine = RoutineEngine(routine, initial)

        engine.previous()

        assertEquals(routine.steps[0].id, engine.session.currentStepId)
        assertEquals(mapOf(routine.steps[0].id to StepStatus.DONE), engine.session.statuses)
    }

    @Test
    fun revisitingStepCanReplaceSkippedWithDone() {
        val initial = ActiveSession(
            routineId = routine.id,
            currentStepId = routine.steps[0].id,
            statuses = mapOf(routine.steps[0].id to StepStatus.SKIPPED),
        )
        val engine = RoutineEngine(routine, initial)

        engine.done()

        assertEquals(StepStatus.DONE, engine.session.statuses[routine.steps[0].id])
    }

    @Test
    fun finalStepCompletesWithoutCreatingAnotherCursor() {
        val engine = RoutineEngine(
            routine,
            ActiveSession(routine.id, routine.steps.last().id),
        )

        val result = engine.skip()

        assertEquals(AdvanceResult.Complete, result)
        assertEquals(routine.steps.last().id, engine.session.currentStepId)
    }
}
