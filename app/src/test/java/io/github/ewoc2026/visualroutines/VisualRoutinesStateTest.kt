package io.github.ewoc2026.visualroutines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRoutinesStateTest {
    private val firstRoutine = BundledRoutines.all.first()
    private val secondRoutine = BundledRoutines.all.last()

    @Test
    fun pauseAndContinueReturnToTheSameStep() {
        val store = FakeSessionStore()
        val state = VisualRoutinesState(store)
        state.startRoutine(firstRoutine.id)
        state.done()

        state.requestPause()
        state.confirmPause()
        state.continueRoutine()

        assertEquals(AppScreen.Runner, state.screen)
        assertEquals(firstRoutine.steps[1].id, state.activeSession?.currentStepId)
        assertEquals(firstRoutine.steps[1].id, store.saved?.currentStepId)
    }

    @Test
    fun startingAnotherRoutineRequiresReplacementConfirmation() {
        val state = VisualRoutinesState(FakeSessionStore())
        state.startRoutine(firstRoutine.id)

        state.startRoutine(secondRoutine.id)

        assertEquals(AppDialog.Replace(secondRoutine.id), state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
    }

    @Test
    fun keepingCurrentRoutineFromReplacementReturnsHome() {
        val state = VisualRoutinesState(FakeSessionStore())
        state.startRoutine(firstRoutine.id)
        state.startRoutine(secondRoutine.id)

        state.keepCurrentRoutine()

        assertEquals(AppScreen.Home, state.screen)
        assertNull(state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
    }

    @Test
    fun startingActiveRoutineRequestsResetConfirmation() {
        val state = VisualRoutinesState(FakeSessionStore())
        state.startRoutine(firstRoutine.id)

        state.startRoutine(firstRoutine.id)

        assertEquals(AppDialog.Reset, state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
    }

    @Test
    fun requestingResetShowsResetConfirmation() {
        val state = VisualRoutinesState(FakeSessionStore())
        state.openHomeWithPausedSession(firstRoutine.id, stepNumber = 3)

        state.requestReset()

        assertEquals(AppDialog.Reset, state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
        assertEquals(firstRoutine.steps[2].id, state.activeSession?.currentStepId)
    }

    @Test
    fun confirmingResetClearsPersistedSessionAndReturnsHome() {
        val store = FakeSessionStore()
        val state = VisualRoutinesState(store)
        state.openHomeWithPausedSession(firstRoutine.id, stepNumber = 3)
        state.requestReset()

        state.confirmReset()

        assertEquals(AppScreen.Home, state.screen)
        assertNull(state.dialog)
        assertNull(state.activeSession)
        assertNull(store.saved)
        assertTrue(store.wasCleared)
    }

    @Test
    fun dismissingResetKeepsProgressOnHome() {
        val store = FakeSessionStore()
        val state = VisualRoutinesState(store)
        state.openHomeWithPausedSession(firstRoutine.id, stepNumber = 3)
        state.requestReset()

        state.dismissDialog()

        assertEquals(AppScreen.Home, state.screen)
        assertNull(state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
        assertEquals(firstRoutine.steps[2].id, state.activeSession?.currentStepId)
        assertEquals(state.activeSession, store.saved)
    }

    @Test
    fun completingFinalStepClearsPersistedSession() {
        val store = FakeSessionStore(
            saved = ActiveSession(
                routineId = secondRoutine.id,
                currentStepId = secondRoutine.steps.last().id,
            ),
        )
        val state = VisualRoutinesState(store)

        state.skip()

        assertNull(state.activeSession)
        assertNull(store.saved)
        assertEquals(AppScreen.Complete(secondRoutine.id), state.screen)
        assertTrue(store.wasCleared)
    }

    @Test
    fun validationRunnerTargetStoresPlausiblePriorProgress() {
        val store = FakeSessionStore()
        val state = VisualRoutinesState(store)

        val opened = state.openRunnerAt(firstRoutine.id, stepNumber = 3)

        assertTrue(opened)
        assertEquals(AppScreen.Runner, state.screen)
        assertEquals(firstRoutine.steps[2].id, state.activeSession?.currentStepId)
        assertEquals(
            mapOf(
                firstRoutine.steps[0].id to StepStatus.DONE,
                firstRoutine.steps[1].id to StepStatus.DONE,
            ),
            state.activeSession?.statuses,
        )
        assertEquals(state.activeSession, store.saved)
    }

    @Test
    fun validationRunnerResumeOpensThePersistedCursorWithoutRewritingIt() {
        val persistedSession = ActiveSession(
            routineId = firstRoutine.id,
            currentStepId = firstRoutine.steps[2].id,
            statuses = mapOf(firstRoutine.steps[0].id to StepStatus.DONE),
        )
        val store = FakeSessionStore(saved = persistedSession)
        val state = VisualRoutinesState(store)

        val resumed = state.resumeValidationRunner()

        assertTrue(resumed)
        assertEquals(AppScreen.Runner, state.screen)
        assertEquals(persistedSession, state.activeSession)
        assertEquals(persistedSession, store.saved)
    }

    @Test
    fun validationPausedHomeTargetShowsActiveSessionOnHome() {
        val state = VisualRoutinesState(FakeSessionStore())

        val opened = state.openHomeWithPausedSession(secondRoutine.id, stepNumber = 2)

        assertTrue(opened)
        assertEquals(AppScreen.Home, state.screen)
        assertEquals(secondRoutine.id, state.activeSession?.routineId)
        assertEquals(secondRoutine.steps[1].id, state.activeSession?.currentStepId)
    }

    @Test
    fun validationReplaceDialogTargetShowsPausedSessionAndReplacementDialog() {
        val state = VisualRoutinesState(FakeSessionStore())

        val opened = state.openReplaceDialog(
            pausedRoutineId = firstRoutine.id,
            stepNumber = 3,
            replacementRoutineId = secondRoutine.id,
        )

        assertTrue(opened)
        assertEquals(AppScreen.RoutineStart(secondRoutine.id), state.screen)
        assertEquals(AppDialog.Replace(secondRoutine.id), state.dialog)
        assertEquals(firstRoutine.id, state.activeSession?.routineId)
        assertEquals(firstRoutine.steps[2].id, state.activeSession?.currentStepId)
    }

    @Test
    fun validationCompleteTargetClearsPersistedSession() {
        val store = FakeSessionStore(saved = ActiveSession(firstRoutine.id, firstRoutine.steps[1].id))
        val state = VisualRoutinesState(store)

        val opened = state.openComplete(firstRoutine.id)

        assertTrue(opened)
        assertNull(state.activeSession)
        assertNull(store.saved)
        assertEquals(AppScreen.Complete(firstRoutine.id), state.screen)
    }

    @Test
    fun bundledCookingRoutineIsAvailableForRealUseTesting() {
        val routine = BundledRoutines.all.first { it.id == "chickpea-salad-bowl" }

        assertEquals("Chickpea salad bowl", routine.name)
        assertEquals(10, routine.steps.size)
        assertEquals("Open the chickpea container.", routine.steps[2].instruction)
    }

    @Test
    fun bundledCleaningRoutineUsesConcreteRealUseWording() {
        val routine = BundledRoutines.all.first { it.id == "clean-a-room" }

        assertEquals("Clean a room", routine.name)
        assertEquals(8, routine.steps.size)
        assertEquals("Clear the floor for vacuuming.", routine.steps[1].instruction)
        assertEquals("Put the vacuum cleaner away.", routine.steps.last().instruction)
    }

    @Test
    fun stateUsesRepositoryRoutineOrder() {
        val userRoutine = Routine(
            id = "11111111-1111-4111-8111-111111111111",
            name = "Change bed linen",
            steps = listOf(
                RoutineStep(
                    id = "11111111-1111-4111-8111-111111111112",
                    instruction = "Take the old sheet off the mattress.",
                ),
            ),
        )
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = FakeRoutineRepository(RoutineCatalog(listOf(userRoutine) + BundledRoutines.all)),
        )

        assertEquals(userRoutine.id, state.routines.first().id)
        assertEquals("prepare-for-exercise", state.routines[1].id)
    }

    @Test
    fun openingManageRoutinesShowsManageScreen() {
        val state = VisualRoutinesState(FakeSessionStore())

        state.openManageRoutines()

        assertEquals(AppScreen.ManageRoutines, state.screen)
    }

    @Test
    fun privacyPolicyReturnsToSettings() {
        val state = VisualRoutinesState(FakeSessionStore())

        state.openSettings()
        state.openPrivacyPolicy()

        assertEquals(AppScreen.PrivacyPolicy, state.screen)

        state.backToSettings()

        assertEquals(AppScreen.Settings, state.screen)
    }

    @Test
    fun homeSettingsPersistAndHideBundledRoutinesWithoutHidingUserRoutines() {
        val userRoutine = testUserRoutine()
        val settingsStore = InMemorySettingsStore()
        val repository = FakeRoutineRepository(
            RoutineCatalog(
                routines = listOf(userRoutine) + BundledRoutines.all,
                userRoutineIds = setOf(userRoutine.id),
            ),
        )
        val state = VisualRoutinesState(FakeSessionStore(), repository, settingsStore)

        state.openSettings()
        state.updateShowExampleRoutines(false)
        state.updateAllowRoutineEditing(false)

        assertEquals(AppScreen.Settings, state.screen)
        assertEquals(listOf(userRoutine), state.homeRoutines)
        assertEquals(
            HomeSettings(showExampleRoutines = false, allowRoutineEditing = false),
            settingsStore.load(),
        )

        val restoredState = VisualRoutinesState(FakeSessionStore(), repository, settingsStore)

        assertEquals(state.homeSettings, restoredState.homeSettings)
        assertEquals(listOf(userRoutine), restoredState.homeRoutines)
    }

    @Test
    fun disabledRoutineEditingBlocksManagementActions() {
        val userRoutine = testUserRoutine()
        val repository = FakeRoutineRepository(
            RoutineCatalog(
                routines = listOf(userRoutine) + BundledRoutines.all,
                userRoutineIds = setOf(userRoutine.id),
            ),
        )
        val state = VisualRoutinesState(FakeSessionStore(), repository)

        state.updateAllowRoutineEditing(false)
        state.openManageRoutines()
        state.openCreateRoutine()
        state.openEditRoutine(userRoutine.id)
        state.requestDeleteRoutine(userRoutine.id)
        state.moveUserRoutine(userRoutine.id, offset = 1)
        val created = state.createRoutine("A routine", listOf(RoutineStepContent("A step")))

        assertEquals(AppScreen.Home, state.screen)
        assertNull(state.dialog)
        assertEquals(false, created)
        assertNull(repository.createdTitle)
        assertNull(repository.updatedRoutineId)
        assertNull(repository.deletedRoutineId)
    }

    @Test
    fun cancellingCreateRoutineFromManageReturnsToManage() {
        val state = VisualRoutinesState(FakeSessionStore())
        state.openManageRoutines()
        state.openCreateRoutineFromManage()

        state.cancelCreateRoutine()

        assertEquals(AppScreen.ManageRoutines, state.screen)
    }

    @Test
    fun creatingRoutineUpdatesCatalogAndReturnsHome() {
        val repository = FakeRoutineRepository(RoutineCatalog(BundledRoutines.all))
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = repository,
        )
        state.openCreateRoutine()

        val created = state.createRoutine(
            title = "  Change bed linen  ",
            steps = listOf(
                RoutineStepContent(
                    instruction = "  Take the old sheet off the mattress.  ",
                    note = "  Put it in the laundry basket.  ",
                ),
                RoutineStepContent("Put a clean sheet on the mattress."),
            ),
        )

        assertTrue(created)
        assertEquals(AppScreen.Home, state.screen)
        assertEquals("Change bed linen", state.routines.first().name)
        assertEquals("Take the old sheet off the mattress.", state.routines.first().steps.first().instruction)
        assertEquals("Put it in the laundry basket.", state.routines.first().steps.first().supportingInstruction)
        assertTrue(repository.createdRoutineIds.single().isUuidLikeForTest())
        assertEquals("Change bed linen", repository.createdTitle)
        assertEquals(
            listOf(
                RoutineStepContent(
                    instruction = "Take the old sheet off the mattress.",
                    note = "Put it in the laundry basket.",
                ),
                RoutineStepContent("Put a clean sheet on the mattress."),
            ),
            repository.createdSteps,
        )
    }

    @Test
    fun creatingRoutineReusesStableIdAfterReportedFailure() {
        val repository = FakeRoutineRepository(RoutineCatalog(BundledRoutines.all)).apply {
            createFailuresRemaining = 1
        }
        val state = VisualRoutinesState(FakeSessionStore(), repository)
        state.openCreateRoutine()
        val steps = listOf(RoutineStepContent("Take the old sheet off the mattress."))

        assertFalse(state.createRoutine("Change bed linen", steps))
        assertTrue(state.createRoutine("Change bed linen", steps))

        assertEquals(2, repository.createdRoutineIds.size)
        assertEquals(repository.createdRoutineIds.first(), repository.createdRoutineIds.last())
        assertEquals(repository.createdRoutineIds.singleDistinct(), state.routines.first().id)
    }

    @Test
    fun openingCreateRoutineCanCarryPrefilledDraft() {
        val state = VisualRoutinesState(FakeSessionStore())
        val draft = CreateRoutineDraft(
            title = "Change bed linen",
            steps = listOf(
                RoutineStepContent("Take the old sheet off the mattress."),
                RoutineStepContent("Put a clean sheet on the mattress."),
            ),
        )

        state.openCreateRoutine(draft)

        assertEquals(AppScreen.CreateRoutine(draft), state.screen)
    }

    @Test
    fun creatingRoutineRejectsBlankFields() {
        val repository = FakeRoutineRepository(RoutineCatalog(BundledRoutines.all))
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = repository,
        )
        state.openCreateRoutine()

        val created = state.createRoutine(
            "Change bed linen",
            listOf(
                RoutineStepContent("Take the old sheet off."),
                RoutineStepContent(" "),
            ),
        )

        assertEquals(false, created)
        assertEquals(AppScreen.CreateRoutine(), state.screen)
        assertNull(repository.createdTitle)
    }

    @Test
    fun creatingRoutineRejectsBlankNote() {
        val repository = FakeRoutineRepository(RoutineCatalog(BundledRoutines.all))
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = repository,
        )
        state.openCreateRoutine()

        val created = state.createRoutine(
            "Change bed linen",
            listOf(RoutineStepContent("Take the old sheet off.", " ")),
        )

        assertEquals(false, created)
        assertEquals(AppScreen.CreateRoutine(), state.screen)
        assertNull(repository.createdTitle)
    }

    @Test
    fun openingEditRoutinePreservesStepContentInDraft() {
        val image = RoutineStepImage(
            source = RoutineImageSource.BUNDLED,
            assetId = "bed-linen-remove-sheet",
            semanticRole = RoutineImageSemanticRole.INFORMATIVE,
            descriptionOverride = "A fitted sheet being pulled from a mattress corner.",
        )
        val userRoutine = testUserRoutine(
            note = "Put the old sheet in the laundry basket.",
            image = image,
        )
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = FakeRoutineRepository(
                RoutineCatalog(
                    routines = listOf(userRoutine) + BundledRoutines.all,
                    userRoutineIds = setOf(userRoutine.id),
                ),
            ),
        )

        state.openEditRoutine(userRoutine.id)

        val screen = state.screen as AppScreen.EditRoutine
        assertEquals(
            RoutineStepContent(
                instruction = "Take the old sheet off the mattress.",
                note = "Put the old sheet in the laundry basket.",
                id = userRoutine.steps.single().id,
                image = image,
            ),
            screen.draft.steps.single(),
        )
    }

    @Test
    fun editingUserRoutineUpdatesItAndReturnsToManage() {
        val userRoutine = testUserRoutine()
        val repository = FakeRoutineRepository(
            RoutineCatalog(
                routines = listOf(userRoutine) + BundledRoutines.all,
                userRoutineIds = setOf(userRoutine.id),
            ),
        )
        val state = VisualRoutinesState(FakeSessionStore(), repository)

        state.openManageRoutines()
        state.openEditRoutine(userRoutine.id)
        val existingStep = (state.screen as AppScreen.EditRoutine).draft.steps.single()
        val saved = state.updateRoutine(
            userRoutine.id,
            "Change bed linen",
            listOf(
                existingStep.copy(
                    instruction = "Put a clean sheet on the mattress.",
                    note = "Smooth out the corners.",
                ),
            ),
        )

        assertTrue(saved)
        assertEquals(AppScreen.ManageRoutines, state.screen)
        assertEquals("Put a clean sheet on the mattress.", state.routine(userRoutine.id).steps.single().instruction)
        assertEquals("Smooth out the corners.", state.routine(userRoutine.id).steps.single().supportingInstruction)
        assertEquals(userRoutine.id, repository.updatedRoutineId)
        assertEquals(userRoutine.steps.single().id, repository.updatedSteps?.single()?.id)
    }

    @Test
    fun deletingUserRoutineRequiresConfirmationAndRemovesIt() {
        val userRoutine = testUserRoutine()
        val repository = FakeRoutineRepository(
            RoutineCatalog(
                routines = listOf(userRoutine) + BundledRoutines.all,
                userRoutineIds = setOf(userRoutine.id),
            ),
        )
        val state = VisualRoutinesState(FakeSessionStore(), repository)

        state.openManageRoutines()
        state.requestDeleteRoutine(userRoutine.id)

        assertEquals(AppDialog.DeleteRoutine(userRoutine.id), state.dialog)

        state.confirmDeleteRoutine(userRoutine.id)

        assertNull(state.dialog)
        assertEquals(AppScreen.ManageRoutines, state.screen)
        assertTrue(userRoutine.id !in state.userRoutineIds)
        assertEquals(userRoutine.id, repository.deletedRoutineId)
    }

    @Test
    fun activeUserRoutineCannotBeEditedOrDeleted() {
        val userRoutine = testUserRoutine()
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = FakeRoutineRepository(
                RoutineCatalog(
                    routines = listOf(userRoutine) + BundledRoutines.all,
                    userRoutineIds = setOf(userRoutine.id),
                ),
            ),
        )
        state.startRoutine(userRoutine.id)
        state.openManageRoutines()

        state.openEditRoutine(userRoutine.id)
        state.requestDeleteRoutine(userRoutine.id)

        assertEquals(AppScreen.ManageRoutines, state.screen)
        assertNull(state.dialog)
    }

    @Test
    fun movingUserRoutineUpdatesItsHomeOrder() {
        val firstUserRoutine = testUserRoutine()
        val secondUserRoutine = Routine(
            id = "33333333-3333-4333-8333-333333333333",
            name = "Prepare for a walk",
            steps = listOf(
                RoutineStep(
                    id = "33333333-3333-4333-8333-333333333334",
                    instruction = "Put on comfortable shoes.",
                ),
            ),
        )
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(),
            routineRepository = FakeRoutineRepository(
                RoutineCatalog(
                    routines = listOf(firstUserRoutine, secondUserRoutine) + BundledRoutines.all,
                    userRoutineIds = setOf(firstUserRoutine.id, secondUserRoutine.id),
                ),
            ),
        )

        state.openManageRoutines()
        state.openReorderRoutines()
        state.moveUserRoutine(secondUserRoutine.id, offset = -1)

        assertEquals(AppScreen.ReorderRoutines, state.screen)
        assertEquals(
            listOf(secondUserRoutine.id, firstUserRoutine.id),
            state.routines.filter { it.id in state.userRoutineIds }.map(Routine::id),
        )
    }

    @Test
    fun invalidSavedSessionIsIgnoredWhenRepositoryDoesNotContainRoutine() {
        val state = VisualRoutinesState(
            sessionStore = FakeSessionStore(saved = ActiveSession("missing", "missing-step")),
            routineRepository = FakeRoutineRepository(RoutineCatalog(BundledRoutines.all)),
        )

        assertNull(state.activeSession)
    }
}

private class FakeSessionStore(
    var saved: ActiveSession? = null,
) : SessionStore {
    var wasCleared = false
        private set

    override fun load(routines: List<Routine>): ActiveSession? = saved

    override fun save(session: ActiveSession) {
        saved = session
    }

    override fun clear() {
        saved = null
        wasCleared = true
    }
}

private class FakeRoutineRepository(
    initialCatalog: RoutineCatalog,
) : RoutineRepository {
    private var catalog = initialCatalog
    val createdRoutineIds = mutableListOf<String>()
    var createFailuresRemaining: Int = 0
    var createdTitle: String? = null
        private set
    var createdSteps: List<RoutineStepContent>? = null
        private set
    var updatedRoutineId: String? = null
        private set
    var updatedSteps: List<RoutineStepContent>? = null
        private set
    var deletedRoutineId: String? = null
        private set

    override fun loadCatalog(): RoutineCatalog = catalog

    override fun createRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog {
        createdRoutineIds += routineId
        createdTitle = title
        createdSteps = steps
        if (createFailuresRemaining > 0) {
            createFailuresRemaining -= 1
            throw RoutineStoreException("Injected create failure.")
        }
        val routine = Routine(
            id = routineId,
            name = title,
            steps = steps.mapIndexed { index, step ->
                RoutineStep(
                    id = step.id ?: "22222222-2222-4222-8222-${(index + 1).toString().padStart(12, '0')}",
                    instruction = step.instruction,
                    supportingInstruction = step.note,
                    image = step.image,
                )
            },
        )
        return catalog.copy(
            routines = listOf(routine) + catalog.routines,
            userRoutineIds = catalog.userRoutineIds + routine.id,
        ).also { catalog = it }
    }

    override fun updateRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog {
        updatedRoutineId = routineId
        updatedSteps = steps
        val updatedRoutine = Routine(
            id = routineId,
            name = title,
            steps = steps.mapIndexed { index, step ->
                RoutineStep(
                    id = step.id ?: "22222222-2222-4222-8222-${(index + 1).toString().padStart(12, '0')}",
                    instruction = step.instruction,
                    supportingInstruction = step.note,
                    image = step.image,
                )
            },
        )
        return catalog.copy(
            routines = catalog.routines.map { routine ->
                if (routine.id == routineId) updatedRoutine else routine
            },
        ).also { catalog = it }
    }

    override fun deleteRoutine(routineId: String): RoutineCatalog {
        deletedRoutineId = routineId
        return catalog.copy(
            routines = catalog.routines.filterNot { it.id == routineId },
            userRoutineIds = catalog.userRoutineIds - routineId,
        ).also { catalog = it }
    }

    override fun reorderUserRoutines(routineIds: List<String>): RoutineCatalog {
        val userRoutinesById = catalog.routines
            .filter { it.id in catalog.userRoutineIds }
            .associateBy(Routine::id)
        require(routineIds.toSet() == catalog.userRoutineIds)
        return catalog.copy(
            routines = routineIds.map(userRoutinesById::getValue) +
                catalog.routines.filterNot { it.id in catalog.userRoutineIds },
        ).also { catalog = it }
    }
}

private fun List<String>.singleDistinct(): String = distinct().single()

private fun String.isUuidLikeForTest(): Boolean {
    return Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    ).matches(this)
}

private fun testUserRoutine(
    note: String? = null,
    image: RoutineStepImage? = null,
): Routine {
    return Routine(
        id = "11111111-1111-4111-8111-111111111111",
        name = "Change bed linen",
        steps = listOf(
            RoutineStep(
                id = "11111111-1111-4111-8111-111111111112",
                instruction = "Take the old sheet off the mattress.",
                supportingInstruction = note,
                image = image,
            ),
        ),
    )
}
