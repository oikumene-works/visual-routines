package io.github.ewoc2026.visualroutines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {
    private val routine = Routine(
        id = "routine",
        name = "Routine",
        steps = listOf(
            RoutineStep(id = "step-1", instruction = "First"),
            RoutineStep(id = "step-2", instruction = "Second"),
            RoutineStep(id = "step-3", instruction = "Third"),
        ),
    )

    @Test
    fun legacyIndexesMigrateToStableStepIds() {
        val session = migrateLegacySession(
            routine = routine,
            currentStepIndex = 2,
            doneStepIndexes = setOf("0"),
            skippedStepIndexes = setOf("1"),
        )

        assertEquals(
            ActiveSession(
                routineId = routine.id,
                currentStepId = "step-3",
                statuses = mapOf(
                    "step-1" to StepStatus.DONE,
                    "step-2" to StepStatus.SKIPPED,
                ),
            ),
            session,
        )
    }

    @Test
    fun invalidLegacyCursorIsRejected() {
        val session = migrateLegacySession(
            routine = routine,
            currentStepIndex = routine.steps.size,
            doneStepIndexes = emptySet(),
            skippedStepIndexes = emptySet(),
        )

        assertNull(session)
    }
}
