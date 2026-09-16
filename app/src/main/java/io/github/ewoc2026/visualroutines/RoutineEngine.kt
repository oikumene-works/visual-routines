package io.github.ewoc2026.visualroutines

/**
 * Applies the prototype's navigation semantics without depending on Android.
 *
 * Previous is navigation rather than undo: existing step statuses remain
 * unchanged until the user explicitly chooses Done or Skip on that step.
 */
internal class RoutineEngine(
    private val routine: Routine,
    initialSession: ActiveSession,
) {
    var session: ActiveSession = initialSession
        private set

    private val currentStepIndex: Int
        get() = routine.stepIndex(session.currentStepId)

    init {
        require(initialSession.routineId == routine.id)
        require(currentStepIndex in routine.steps.indices)
    }

    fun previous(): ActiveSession {
        val previousStepIndex = (currentStepIndex - 1).coerceAtLeast(0)
        session = session.copy(
            currentStepId = routine.steps[previousStepIndex].id,
        )
        return session
    }

    fun done(): AdvanceResult = recordAndAdvance(StepStatus.DONE)

    fun skip(): AdvanceResult = recordAndAdvance(StepStatus.SKIPPED)

    private fun recordAndAdvance(status: StepStatus): AdvanceResult {
        val updatedStatuses = session.statuses + (session.currentStepId to status)
        if (currentStepIndex == routine.steps.lastIndex) {
            return AdvanceResult.Complete
        }

        session = session.copy(
            currentStepId = routine.steps[currentStepIndex + 1].id,
            statuses = updatedStatuses,
        )
        return AdvanceResult.Active(session)
    }
}

internal sealed interface AdvanceResult {
    data class Active(val session: ActiveSession) : AdvanceResult
    data object Complete : AdvanceResult
}
