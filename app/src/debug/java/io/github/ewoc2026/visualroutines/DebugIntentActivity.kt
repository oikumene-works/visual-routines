package io.github.ewoc2026.visualroutines

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider

/** Debug-only entry point for opening validation states from ADB. */
internal class DebugIntentActivity : ComponentActivity() {
    private lateinit var viewModel: VisualRoutinesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        viewModel = ViewModelProvider(
            this,
            VisualRoutinesViewModel.Factory(applicationContext),
        )[VisualRoutinesViewModel::class.java]
        if (savedInstanceState == null) {
            applyIntentCommand(intent)
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentCommand(intent)
        render()
    }

    private fun applyIntentCommand(intent: Intent) {
        val stateAction = DebugIntentCommand.from(intent)
        viewModel.state.apply(stateAction)
    }

    private fun render() {
        setContent {
            VisualRoutinesApp(viewModel.state)
        }
    }
}

private sealed interface DebugIntentCommand {
    fun apply(state: VisualRoutinesState): Boolean

    data object CleanHome : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean {
            state.clearSessionAndOpenHome()
            return true
        }
    }

    data object Manage : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean {
            state.openManageRoutines()
            return true
        }
    }

    data object CreatePrefilled : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean {
            state.openCreateRoutine(prefilledCreateRoutineDraft)
            return true
        }
    }

    data class RoutineStart(val routineId: String) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean = state.openRoutineStart(routineId)
    }

    data class Runner(val routineId: String, val stepNumber: Int) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean = state.openRunnerAt(routineId, stepNumber)
    }

    data class PausedHome(val routineId: String, val stepNumber: Int) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean = state.openHomeWithPausedSession(routineId, stepNumber)
    }

    data class ReplaceDialog(
        val pausedRoutineId: String,
        val stepNumber: Int,
        val replacementRoutineId: String,
    ) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean {
            return state.openReplaceDialog(pausedRoutineId, stepNumber, replacementRoutineId)
        }
    }

    data class ResetDialog(val routineId: String, val stepNumber: Int) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean {
            val opened = state.openHomeWithPausedSession(routineId, stepNumber)
            if (opened) {
                state.requestReset()
            }
            return opened
        }
    }

    data class Complete(val routineId: String) : DebugIntentCommand {
        override fun apply(state: VisualRoutinesState): Boolean = state.openComplete(routineId)
    }

    companion object {
        private const val ACTION_HOME = "io.github.ewoc2026.visualroutines.debug.HOME"
        private const val ACTION_MANAGE = "io.github.ewoc2026.visualroutines.debug.MANAGE"
        private const val ACTION_CREATE_PREFILLED = "io.github.ewoc2026.visualroutines.debug.CREATE_PREFILLED"
        private const val ACTION_ROUTINE_START = "io.github.ewoc2026.visualroutines.debug.ROUTINE_START"
        private const val ACTION_RUNNER = "io.github.ewoc2026.visualroutines.debug.RUNNER"
        private const val ACTION_PAUSED_HOME = "io.github.ewoc2026.visualroutines.debug.PAUSED_HOME"
        private const val ACTION_REPLACE_DIALOG = "io.github.ewoc2026.visualroutines.debug.REPLACE_DIALOG"
        private const val ACTION_RESET_DIALOG = "io.github.ewoc2026.visualroutines.debug.RESET_DIALOG"
        private const val ACTION_RESTART_DIALOG = "io.github.ewoc2026.visualroutines.debug.RESTART_DIALOG"
        private const val ACTION_COMPLETE = "io.github.ewoc2026.visualroutines.debug.COMPLETE"
        private const val EXTRA_ROUTINE_ID = "routine_id"
        private const val EXTRA_REPLACEMENT_ROUTINE_ID = "replacement_routine_id"
        private const val EXTRA_STEP_NUMBER = "step"
        private const val DEFAULT_ROUTINE_ID = "prepare-for-exercise"
        private const val DEFAULT_REPLACEMENT_ROUTINE_ID = "clean-a-room"
        private const val DEFAULT_STEP_NUMBER = 1
        private const val TAG = "VRDebugIntent"

        fun from(intent: Intent): VisualRoutinesState.() -> Unit {
            val command = when (intent.action) {
                ACTION_HOME -> CleanHome
                ACTION_MANAGE -> Manage
                ACTION_CREATE_PREFILLED -> CreatePrefilled
                ACTION_ROUTINE_START -> RoutineStart(intent.routineId())
                ACTION_RUNNER -> Runner(intent.routineId(), intent.stepNumber())
                ACTION_PAUSED_HOME -> PausedHome(intent.routineId(), intent.stepNumber())
                ACTION_REPLACE_DIALOG -> ReplaceDialog(
                    pausedRoutineId = intent.routineId(),
                    stepNumber = intent.stepNumber(),
                    replacementRoutineId = intent.replacementRoutineId(),
                )
                ACTION_RESET_DIALOG,
                ACTION_RESTART_DIALOG -> ResetDialog(intent.routineId(), intent.stepNumber())
                ACTION_COMPLETE -> Complete(intent.routineId())
                else -> CleanHome
            }
            return {
                val applied = command.apply(this)
                if (!applied) {
                    Log.w(TAG, "Ignored invalid debug intent: ${intent.action} ${intent.extras}")
                }
            }
        }

        private fun Intent.routineId(): String = getStringExtra(EXTRA_ROUTINE_ID) ?: DEFAULT_ROUTINE_ID

        private fun Intent.replacementRoutineId(): String {
            return getStringExtra(EXTRA_REPLACEMENT_ROUTINE_ID) ?: DEFAULT_REPLACEMENT_ROUTINE_ID
        }

        private fun Intent.stepNumber(): Int = getIntExtra(EXTRA_STEP_NUMBER, DEFAULT_STEP_NUMBER)
    }
}

internal val prefilledCreateRoutineDraft = CreateRoutineDraft(
    title = "Change bed linen",
    steps = listOf(
        RoutineStepContent("Take the bedspread off the bed."),
        RoutineStepContent(
            instruction = "Take the duvet out of the duvet cover.",
            image = RoutineStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "bed-linen-remove-duvet-cover",
                semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            ),
        ),
        RoutineStepContent("Take the pillow out of the pillowcase."),
        RoutineStepContent(
            instruction = "Take the sheet off the mattress.",
            image = RoutineStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "bed-linen-remove-sheet",
                semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                descriptionOverride = "Two hands pull the old gray fitted sheet away from a bare mattress corner.",
            ),
        ),
        RoutineStepContent("Put the old bed linen in the laundry basket."),
        RoutineStepContent("Put a clean sheet on the mattress."),
        RoutineStepContent("Put the pillow in a clean pillowcase."),
        RoutineStepContent(
            instruction = "Put the duvet in a clean duvet cover.",
            image = RoutineStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "bed-linen-insert-duvet-cover",
                semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            ),
        ),
        RoutineStepContent(
            instruction = "Finish the bed with the bedspread.",
            image = RoutineStepImage(
                source = RoutineImageSource.BUNDLED,
                assetId = "bed-linen-finish-with-bedspread",
                semanticRole = RoutineImageSemanticRole.REDUNDANT,
            ),
        ),
    ),
)
