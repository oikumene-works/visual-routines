package io.github.ewoc2026.visualroutines

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses actual typed preference reads, rather than the in-memory session fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesSessionStoreTest {
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private val preferences get() = application.getSharedPreferences("active_routine", Context.MODE_PRIVATE)
    private val routine = BundledRoutines.all.first()
    private fun freshState() = VisualRoutinesState(SharedPreferencesSessionStore(application), application.routineRepository)

    private fun checkInvalidValue(change: SharedPreferences.Editor.() -> Unit) {
        application.routineRepository.createRoutine(
            "11111111-1111-4111-8111-111111111111", "Saved routine", listOf(RoutineStepContent("Keep this step.")),
        )
        val catalog = JsonRoutineStore.appPrivateFile(application.filesDir)
        val original = catalog.readBytes()
        // An unrelated file sentinel checks that rejecting progress does not wipe app data.
        val sentinel = File(application.filesDir, "user_images/22222222-2222-4222-8222-222222222222.png")
        sentinel.parentFile!!.mkdirs()
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        sentinel.writeBytes(imageBytes)
        assertTrue(preferences.edit()
            .putString("routine_id", routine.id)
            .putString("current_step_id", routine.steps[1].id)
            .apply(change).commit())
        val before = preferences.all.toMap()

        val state = freshState()

        assertEquals(AppScreen.Home, state.screen)
        assertNull(state.activeSession)
        assertTrue(state.routines.any { it.name == "Saved routine" })
        assertEquals(before, preferences.all)
        assertArrayEquals(original, catalog.readBytes())
        assertArrayEquals(imageBytes, sentinel.readBytes())
    }

    @Test fun wrongTypedCursorDoesNotPreventStartup() = checkInvalidValue { putInt("current_step_id", 1) }

    @Test fun wrongTypedStatusSetDoesNotPreventStartup() = checkInvalidValue { putString("done_step_ids", "step") }

    @Test fun wrongTypedRoutineIdDoesNotPreventStartup() = checkInvalidValue { putInt("routine_id", 1) }

    @Test fun wrongTypedLegacyCursorDoesNotRewritePreferences() = checkInvalidValue {
        remove("current_step_id")
        putString("current_step", "1")
    }

    @Test fun wrongTypedLegacyStatusSetDoesNotRewritePreferences() = checkInvalidValue {
        remove("current_step_id")
        putInt("current_step", 1)
        putInt("done_steps", 0)
    }

    @Test fun missingPreferencesLoadWithoutProgress() {
        assertNull(freshState().activeSession)
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun validProgressRetainsItsCursorAndBothStatusKinds() {
        val saved = ActiveSession(routine.id, routine.steps[2].id, mapOf(
            routine.steps[0].id to StepStatus.DONE,
            routine.steps[1].id to StepStatus.SKIPPED,
        ))
        SharedPreferencesSessionStore(application).save(saved)
        assertEquals(saved, freshState().activeSession)
    }

    @Test fun validLegacyProgressStillMigratesThroughTheRealStore() {
        assertTrue(preferences.edit().putString("routine_id", routine.id)
            .putInt("current_step", 2)
            .putStringSet("done_steps", setOf("0"))
            .putStringSet("skipped_steps", setOf("1"))
            .commit())
        val state = freshState()
        assertEquals(ActiveSession(routine.id, routine.steps[2].id, mapOf(
            routine.steps[0].id to StepStatus.DONE,
            routine.steps[1].id to StepStatus.SKIPPED,
        )), state.activeSession)
        assertEquals(routine.steps[2].id, preferences.getString("current_step_id", null))
        assertFalse(preferences.contains("current_step"))
    }
}
