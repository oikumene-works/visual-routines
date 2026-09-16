package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

internal class VisualRoutinesState(
    private val sessionStore: SessionStore,
    private val routineRepository: RoutineRepository = BundledRoutineRepository,
    private val settingsStore: SettingsStore = InMemorySettingsStore(),
) {
    private var pendingCreateRoutineId: String? = null

    private val routineCatalog = routineRepository.loadCatalog()

    var routines: List<Routine> by mutableStateOf(routineCatalog.routines)
        private set

    var userRoutineIds: Set<String> by mutableStateOf(routineCatalog.userRoutineIds)
        private set

    var userRoutineLoadError: RoutineStoreException? by mutableStateOf(routineCatalog.userRoutineLoadError)
        private set

    var homeSettings: HomeSettings by mutableStateOf(settingsStore.load())
        private set

    var screen: AppScreen by mutableStateOf(AppScreen.Home)
        private set

    var dialog: AppDialog? by mutableStateOf(null)
        private set

    var activeSession: ActiveSession? by mutableStateOf(validSession(sessionStore.load(routines)))
        private set

    val homeRoutines: List<Routine>
        get() = routines.filter { routine ->
            routine.id in userRoutineIds || homeSettings.showExampleRoutines
        }

    fun routine(id: String): Routine = routines.first { it.id == id }

    fun openRoutine(routineId: String) {
        screen = AppScreen.RoutineStart(routineId)
    }

    fun openManageRoutines() {
        if (!homeSettings.allowRoutineEditing) return
        dialog = null
        screen = AppScreen.ManageRoutines
    }

    fun openSettings() {
        dialog = null
        screen = AppScreen.Settings
    }

    fun openPrivacyPolicy() {
        dialog = null
        screen = AppScreen.PrivacyPolicy
    }

    fun backToSettings() {
        dialog = null
        screen = AppScreen.Settings
    }

    fun updateShowExampleRoutines(show: Boolean) {
        updateHomeSettings(homeSettings.copy(showExampleRoutines = show))
    }

    fun updateAllowRoutineEditing(allow: Boolean) {
        updateHomeSettings(homeSettings.copy(allowRoutineEditing = allow))
    }

    fun openCreateRoutine(
        draft: CreateRoutineDraft = CreateRoutineDraft(),
        returnDestination: CreateRoutineReturnDestination = CreateRoutineReturnDestination.Home,
    ) {
        if (!homeSettings.allowRoutineEditing) return
        pendingCreateRoutineId = UUID.randomUUID().toString()
        dialog = null
        screen = AppScreen.CreateRoutine(draft, returnDestination)
    }

    fun openCreateRoutineFromManage() {
        openCreateRoutine(returnDestination = CreateRoutineReturnDestination.ManageRoutines)
    }

    fun openReorderRoutines() {
        if (homeSettings.allowRoutineEditing && userRoutineIds.size > 1) {
            dialog = null
            screen = AppScreen.ReorderRoutines
        }
    }

    fun backToManageRoutines() {
        dialog = null
        screen = AppScreen.ManageRoutines
    }

    fun openEditRoutine(routineId: String) {
        if (!homeSettings.allowRoutineEditing) return
        val routine = routines.find { it.id == routineId } ?: return
        if (routineId !in userRoutineIds || activeSession?.routineId == routineId) return

        dialog = null
        screen = AppScreen.EditRoutine(
            routineId = routineId,
            draft = CreateRoutineDraft(
                title = routine.name,
                steps = routine.steps.map { step ->
                    RoutineStepContent(
                        instruction = step.instruction,
                        note = step.supportingInstruction,
                        id = step.id,
                        image = step.image,
                    )
                },
            ),
        )
    }

    fun cancelCreateRoutine() {
        pendingCreateRoutineId = null
        dialog = null
        screen = when ((screen as? AppScreen.CreateRoutine)?.returnDestination) {
            CreateRoutineReturnDestination.ManageRoutines -> AppScreen.ManageRoutines
            CreateRoutineReturnDestination.Home,
            null -> AppScreen.Home
        }
    }

    fun cancelEditRoutine() {
        dialog = null
        screen = AppScreen.ManageRoutines
    }

    fun clearSessionAndOpenHome() {
        dialog = null
        activeSession = null
        sessionStore.clear()
        screen = AppScreen.Home
    }

    fun openRoutineStart(routineId: String): Boolean {
        return if (routines.any { it.id == routineId }) {
            dialog = null
            activeSession = null
            sessionStore.clear()
            screen = AppScreen.RoutineStart(routineId)
            true
        } else {
            false
        }
    }

    fun openRunnerAt(routineId: String, stepNumber: Int): Boolean {
        return setValidationSession(routineId, stepNumber) {
            screen = AppScreen.Runner
        }
    }

    fun resumeValidationRunner(): Boolean {
        if (activeSession == null) return false
        dialog = null
        screen = AppScreen.Runner
        return true
    }

    fun openHomeWithPausedSession(routineId: String, stepNumber: Int): Boolean {
        return setValidationSession(routineId, stepNumber) {
            screen = AppScreen.Home
        }
    }

    fun openReplaceDialog(
        pausedRoutineId: String,
        stepNumber: Int,
        replacementRoutineId: String,
    ): Boolean {
        if (routines.none { it.id == replacementRoutineId }) return false
        return setValidationSession(pausedRoutineId, stepNumber) {
            screen = AppScreen.RoutineStart(replacementRoutineId)
            dialog = AppDialog.Replace(replacementRoutineId)
        }
    }

    fun openComplete(routineId: String): Boolean {
        return if (routines.any { it.id == routineId }) {
            dialog = null
            activeSession = null
            sessionStore.clear()
            screen = AppScreen.Complete(routineId)
            true
        } else {
            false
        }
    }

    fun backHome() {
        dialog = null
        screen = AppScreen.Home
    }

    fun createRoutine(title: String, steps: List<RoutineStepContent>): Boolean {
        if (!homeSettings.allowRoutineEditing) return false
        val normalizedTitle = title.trim()
        val normalizedSteps = steps.map(RoutineStepContent::normalized)
        if (
            normalizedTitle.isEmpty() ||
                normalizedSteps.isEmpty() ||
                normalizedSteps.any { it.instruction.isEmpty() || it.note?.isEmpty() == true }
        ) {
            return false
        }

        val routineId = pendingCreateRoutineId
            ?: UUID.randomUUID().toString().also { pendingCreateRoutineId = it }
        return try {
            val catalog = routineRepository.createRoutine(routineId, normalizedTitle, normalizedSteps)
            updateCatalog(catalog)
            pendingCreateRoutineId = null
            screen = AppScreen.Home
            true
        } catch (error: RoutineStoreException) {
            userRoutineLoadError = error
            false
        }
    }

    fun updateRoutine(routineId: String, title: String, steps: List<RoutineStepContent>): Boolean {
        if (!homeSettings.allowRoutineEditing) return false
        val normalizedTitle = title.trim()
        val normalizedSteps = steps.map(RoutineStepContent::normalized)
        if (
            routineId !in userRoutineIds ||
                activeSession?.routineId == routineId ||
                normalizedTitle.isEmpty() ||
                normalizedSteps.isEmpty() ||
                normalizedSteps.any { it.instruction.isEmpty() || it.note?.isEmpty() == true }
        ) {
            return false
        }

        return try {
            updateCatalog(routineRepository.updateRoutine(routineId, normalizedTitle, normalizedSteps))
            screen = AppScreen.ManageRoutines
            true
        } catch (error: RoutineStoreException) {
            userRoutineLoadError = error
            false
        }
    }

    fun requestDeleteRoutine(routineId: String) {
        if (
            homeSettings.allowRoutineEditing &&
                routineId in userRoutineIds &&
                activeSession?.routineId != routineId
        ) {
            dialog = AppDialog.DeleteRoutine(routineId)
        }
    }

    fun confirmDeleteRoutine(routineId: String) {
        if (
            !homeSettings.allowRoutineEditing ||
                routineId !in userRoutineIds ||
                activeSession?.routineId == routineId
        ) {
            dialog = null
            return
        }

        try {
            updateCatalog(routineRepository.deleteRoutine(routineId))
            dialog = null
            screen = AppScreen.ManageRoutines
        } catch (error: RoutineStoreException) {
            userRoutineLoadError = error
            dialog = null
        }
    }

    fun moveUserRoutine(routineId: String, offset: Int) {
        if (!homeSettings.allowRoutineEditing) return
        val orderedIds = routines.filter { it.id in userRoutineIds }.map(Routine::id)
        val fromIndex = orderedIds.indexOf(routineId)
        val toIndex = fromIndex + offset
        if (fromIndex == -1 || toIndex !in orderedIds.indices) return

        val reorderedIds = orderedIds.toMutableList().also {
            val routine = it.removeAt(fromIndex)
            it.add(toIndex, routine)
        }
        try {
            updateCatalog(routineRepository.reorderUserRoutines(reorderedIds))
        } catch (error: RoutineStoreException) {
            userRoutineLoadError = error
        }
    }

    fun startRoutine(routineId: String) {
        val current = activeSession
        when {
            current == null -> beginFresh(routineId)
            current.routineId == routineId -> dialog = AppDialog.Reset
            else -> dialog = AppDialog.Replace(routineId)
        }
    }

    fun continueRoutine() {
        if (activeSession != null) {
            screen = AppScreen.Runner
        }
    }

    fun requestReset() {
        activeSession?.let { dialog = AppDialog.Reset }
    }

    fun confirmReset() {
        clearSessionAndOpenHome()
    }

    fun confirmReplacement(routineId: String) {
        dialog = null
        beginFresh(routineId)
    }

    fun keepCurrentRoutine() {
        dialog = null
        screen = AppScreen.Home
    }

    fun requestPause() {
        dialog = AppDialog.Pause
    }

    fun confirmPause() {
        dialog = null
        screen = AppScreen.Home
    }

    fun dismissDialog() {
        dialog = null
    }

    fun previous() {
        updateActive { engine -> engine.previous() }
    }

    fun done() {
        advance(RoutineEngine::done)
    }

    fun skip() {
        advance(RoutineEngine::skip)
    }

    fun completeToHome() {
        screen = AppScreen.Home
    }

    fun runAgain(routineId: String) {
        beginFresh(routineId)
    }

    private fun beginFresh(routineId: String) {
        val routine = routine(routineId)
        val session = ActiveSession(
            routineId = routineId,
            currentStepId = routine.steps.first().id,
        )
        activeSession = session
        sessionStore.save(session)
        screen = AppScreen.Runner
    }

    private fun updateCatalog(catalog: RoutineCatalog) {
        routines = catalog.routines
        userRoutineIds = catalog.userRoutineIds
        userRoutineLoadError = catalog.userRoutineLoadError
    }

    private fun updateHomeSettings(settings: HomeSettings) {
        homeSettings = settings
        settingsStore.save(settings)
    }

    private fun setValidationSession(
        routineId: String,
        stepNumber: Int,
        openScreen: () -> Unit,
    ): Boolean {
        val routine = routines.find { it.id == routineId } ?: return false
        val stepIndex = stepNumber - 1
        if (stepIndex !in routine.steps.indices) return false

        dialog = null
        val statuses = routine.steps
            .take(stepIndex)
            .associate { step -> step.id to StepStatus.DONE }
        val session = ActiveSession(
            routineId = routineId,
            currentStepId = routine.steps[stepIndex].id,
            statuses = statuses,
        )
        activeSession = session
        sessionStore.save(session)
        openScreen()
        return true
    }

    private fun updateActive(block: (RoutineEngine) -> ActiveSession) {
        val current = activeSession ?: return
        val engine = RoutineEngine(routine(current.routineId), current)
        val updated = block(engine)
        activeSession = updated
        sessionStore.save(updated)
    }

    private fun advance(action: (RoutineEngine) -> AdvanceResult) {
        val current = activeSession ?: return
        val currentRoutine = routine(current.routineId)
        val engine = RoutineEngine(currentRoutine, current)
        when (val result = action(engine)) {
            is AdvanceResult.Active -> {
                activeSession = result.session
                sessionStore.save(result.session)
            }
            AdvanceResult.Complete -> {
                activeSession = null
                sessionStore.clear()
                screen = AppScreen.Complete(currentRoutine.id)
            }
        }
    }

    private fun validSession(session: ActiveSession?): ActiveSession? {
        val value = session ?: return null
        val routine = routines.find { it.id == value.routineId } ?: return null
        val stepIds = routine.steps.mapTo(mutableSetOf(), RoutineStep::id)
        return value.takeIf {
            value.currentStepId in stepIds && value.statuses.keys.all { stepId -> stepId in stepIds }
        }
    }
}

internal sealed interface AppScreen {
    data object Home : AppScreen
    data object Settings : AppScreen
    data object PrivacyPolicy : AppScreen
    data object ManageRoutines : AppScreen
    data object ReorderRoutines : AppScreen
    data class CreateRoutine(
        val draft: CreateRoutineDraft = CreateRoutineDraft(),
        val returnDestination: CreateRoutineReturnDestination = CreateRoutineReturnDestination.Home,
    ) : AppScreen
    data class EditRoutine(
        val routineId: String,
        val draft: CreateRoutineDraft,
    ) : AppScreen
    data class RoutineStart(val routineId: String) : AppScreen
    data object Runner : AppScreen
    data class Complete(val routineId: String) : AppScreen
}

internal enum class CreateRoutineReturnDestination {
    Home,
    ManageRoutines,
}

internal data class CreateRoutineDraft(
    val title: String = "",
    val steps: List<RoutineStepContent> = listOf(RoutineStepContent("")),
) {
    init {
        require(steps.isNotEmpty()) {
            "Create routine draft must have at least one step field."
        }
    }
}

private fun RoutineStepContent.normalized(): RoutineStepContent {
    return copy(
        instruction = instruction.trim(),
        note = note?.trim(),
        image = image?.copy(
            assetId = image.assetId.trim(),
            descriptionOverride = image.descriptionOverride?.trim(),
        ),
    )
}

internal sealed interface AppDialog {
    data object Pause : AppDialog
    data object Reset : AppDialog
    data class Replace(val routineId: String) : AppDialog
    data class DeleteRoutine(val routineId: String) : AppDialog
}
