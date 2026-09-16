package io.github.ewoc2026.visualroutines

import android.content.Context
import androidx.core.content.edit

internal interface SessionStore {
    fun load(routines: List<Routine>): ActiveSession?
    fun save(session: ActiveSession)
    fun clear()
}

/**
 * Stores only the active cursor and reached step states.
 *
 * This intentionally avoids a database until real-use testing establishes the
 * requirements for user-authored routines and migration behavior.
 */
internal class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(routines: List<Routine>): ActiveSession? {
        val routineId = preferences.getString(KEY_ROUTINE_ID, null) ?: return null
        val routine = routines.find { it.id == routineId } ?: return null
        val currentStepId = preferences.getString(KEY_CURRENT_STEP_ID, null)
        if (currentStepId == null) {
            return migrateLegacySession(
                routine = routine,
                currentStepIndex = preferences.getInt(KEY_CURRENT_STEP_INDEX, 0),
                doneStepIndexes = preferences.getStringSet(KEY_DONE_STEP_INDEXES, emptySet()).orEmpty(),
                skippedStepIndexes = preferences.getStringSet(KEY_SKIPPED_STEP_INDEXES, emptySet()).orEmpty(),
            )?.also(::save)
        }

        val done = preferences.getStringSet(KEY_DONE_STEP_IDS, emptySet()).orEmpty()
        val skipped = preferences.getStringSet(KEY_SKIPPED_STEP_IDS, emptySet()).orEmpty()
        val statuses = buildMap {
            done.forEach { put(it, StepStatus.DONE) }
            skipped.forEach { put(it, StepStatus.SKIPPED) }
        }
        return ActiveSession(routineId, currentStepId, statuses)
    }

    override fun save(session: ActiveSession) {
        val done = session.statuses.filterValues { it == StepStatus.DONE }.keys
        val skipped = session.statuses.filterValues { it == StepStatus.SKIPPED }.keys
        preferences.edit {
            clear()
            putString(KEY_ROUTINE_ID, session.routineId)
            putString(KEY_CURRENT_STEP_ID, session.currentStepId)
            putStringSet(KEY_DONE_STEP_IDS, done)
            putStringSet(KEY_SKIPPED_STEP_IDS, skipped)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val PREFERENCES_NAME = "active_routine"
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_CURRENT_STEP_ID = "current_step_id"
        const val KEY_DONE_STEP_IDS = "done_step_ids"
        const val KEY_SKIPPED_STEP_IDS = "skipped_step_ids"
        const val KEY_CURRENT_STEP_INDEX = "current_step"
        const val KEY_DONE_STEP_INDEXES = "done_steps"
        const val KEY_SKIPPED_STEP_INDEXES = "skipped_steps"
    }
}

internal fun migrateLegacySession(
    routine: Routine,
    currentStepIndex: Int,
    doneStepIndexes: Set<String>,
    skippedStepIndexes: Set<String>,
): ActiveSession? {
    if (currentStepIndex !in routine.steps.indices) return null

    val statuses = buildMap {
        doneStepIndexes.mapNotNull(String::toIntOrNull).forEach { stepIndex ->
            routine.steps.getOrNull(stepIndex)?.let { step -> put(step.id, StepStatus.DONE) }
        }
        skippedStepIndexes.mapNotNull(String::toIntOrNull).forEach { stepIndex ->
            routine.steps.getOrNull(stepIndex)?.let { step -> put(step.id, StepStatus.SKIPPED) }
        }
    }
    return ActiveSession(
        routineId = routine.id,
        currentStepId = routine.steps[currentStepIndex].id,
        statuses = statuses,
    )
}
