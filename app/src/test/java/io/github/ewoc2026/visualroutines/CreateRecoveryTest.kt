package io.github.ewoc2026.visualroutines

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises an applied file commit whose two acknowledgement reads fail. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CreateRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val application get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
    private val file get() = JsonRoutineStore.appPrivateFile(application.filesDir)
    private val backend by lazy { UnacknowledgedCommitBackend(file) }
    private var state = lazy { freshState(backend) }
    private val restoration = StateRestorationTester(compose)

    private fun freshState(documentBackend: AtomicDocumentBackend = AndroidXAtomicDocumentBackend(file)) =
        VisualRoutinesState(
            SharedPreferencesSessionStore(application),
            JsonRoutineRepository(JsonRoutineStore(AtomicDocumentIo(documentBackend), "recovery test")),
        )

    private fun checkRetry(restore: Boolean) {
        state.value.openCreateRoutine(CreateRoutineDraft("My routine", listOf(RoutineStepContent("First step."))))
        restoration.setContent { VisualRoutinesApp(state.value) }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(state.value.screen is AppScreen.CreateRoutine)
            assertTrue(requireNotNull(state.value.userRoutineLoadError).mayHaveCommitted)
            assertEquals(2, backend.failedReads)
        }
        val committed = JsonRoutineStore(file).load().routines.single()
        compose.onNodeWithText("Routine name").performScrollTo().performTextReplacement("Revised after failure")
        if (restore) {
            // The composition's saved state survives, but the old state/repository do not.
            compose.runOnIdle { state = lazy { freshState() } }
            restoration.emulateSavedInstanceStateRestore()
        }
        compose.onNodeWithText("Revised after failure").assertExists()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val saved = JsonRoutineStore(file).load().routines
        assertEquals(listOf(committed.id), saved.map { it.id })
        assertEquals("Revised after failure", saved.single().title)
        assertEquals(committed.steps, saved.single().steps)
        compose.runOnIdle { assertEquals(AppScreen.Home, state.value.screen) }
    }

    @Test fun uncertainCreateRetryAfterRestorationKeepsOneRoutine() = checkRetry(restore = true)

    @Test fun uncertainCreateRetryInSameStateKeepsOneRoutine() = checkRetry(restore = false)
}

private class UnacknowledgedCommitBackend(file: File) : AtomicDocumentBackend {
    private val delegate = AndroidXAtomicDocumentBackend(file)
    private var failuresRemaining = 0
    private var injectFailure = true
    var failedReads = 0
        private set
    override val description = "uncertain-create fixture"
    override fun hasReadableData() = delegate.hasReadableData()
    override fun readFully(): ByteArray {
        if (failuresRemaining > 0) {
            failuresRemaining--
            failedReads++
            throw IOException("Injected acknowledgement read failure")
        }
        return delegate.readFully()
    }
    override fun startWrite(): AtomicDocumentPendingWrite {
        val pending = delegate.startWrite()
        return object : AtomicDocumentPendingWrite by pending {
            override fun commit() {
                pending.commit()
                if (injectFailure) {
                    failuresRemaining = 2
                    injectFailure = false
                }
            }
        }
    }
}
