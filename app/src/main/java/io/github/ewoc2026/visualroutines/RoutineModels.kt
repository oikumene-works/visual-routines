package io.github.ewoc2026.visualroutines

/** A selectable routine loaded from bundled content or the local user store. */
internal data class Routine(
    val id: String,
    val name: String,
    val steps: List<RoutineStep>,
)

/** One concrete action with a stable identity inside its routine. */
internal data class RoutineStep(
    val id: String,
    val instruction: String,
    val supportingInstruction: String? = null,
    val image: RoutineStepImage? = null,
)

/** A stable reference and accessibility role for one optional step image. */
internal data class RoutineStepImage(
    val source: RoutineImageSource,
    val assetId: String,
    val semanticRole: RoutineImageSemanticRole,
    val descriptionOverride: String? = null,
) {
    init {
        require(assetId.trim().isNotEmpty()) { "Image asset id must not be blank." }
        require(descriptionOverride == null || descriptionOverride.trim().isNotEmpty()) {
            "Image description override must not be blank."
        }
    }
}

internal enum class RoutineImageSource {
    BUNDLED,
    IMPORTED,
}

internal enum class RoutineImageSemanticRole {
    INFORMATIVE,
    REDUNDANT,
}

internal enum class StepStatus {
    DONE,
    SKIPPED,
}

/** The ID-based persisted state for the prototype's single active routine. */
internal data class ActiveSession(
    val routineId: String,
    val currentStepId: String,
    val statuses: Map<String, StepStatus> = emptyMap(),
)

internal object BundledRoutines {
    val all: List<Routine> = listOf(
        Routine(
            id = "prepare-for-exercise",
            name = "Prepare for exercise",
            steps = bundledSteps(
                routineId = "prepare-for-exercise",
                "Change into workout clothes.",
                "Fill the water bottle.",
                "Put the towel and fan in place.",
                "Put on the heart rate strap.",
                "Turn on the exercise equipment.",
                "Open the workout app.",
                "Check the device connections.",
                "Start the workout.",
            ),
        ),
        Routine(
            id = "clean-a-room",
            name = "Clean a room",
            steps = bundledSteps(
                routineId = "clean-a-room",
                "Put misplaced items back where they belong.",
                "Clear the floor for vacuuming.",
                "Get a cloth and surface cleaner.",
                "Wipe the surfaces.",
                "Put the cloth and cleaner away.",
                "Get the vacuum cleaner.",
                "Vacuum the floor.",
                "Put the vacuum cleaner away.",
            ),
        ),
        Routine(
            id = "chickpea-salad-bowl",
            name = "Chickpea salad bowl",
            steps = bundledSteps(
                routineId = "chickpea-salad-bowl",
                "Put a bowl on the counter.",
                "Get the chickpea container.",
                "Open the chickpea container.",
                "Drain and rinse the chickpeas.",
                "Add the chickpeas to the bowl.",
                "Add ready salad to the bowl.",
                "Add dressing to the bowl.",
                "Mix the salad.",
                "Put away the remaining ingredients.",
                "Eat the salad.",
            ),
        ),
    )
}

internal fun Routine.stepIndex(stepId: String): Int = steps.indexOfFirst { it.id == stepId }

private fun bundledSteps(
    routineId: String,
    vararg instructions: String,
): List<RoutineStep> {
    return instructions.mapIndexed { index, instruction ->
        RoutineStep(
            id = "$routineId-step-${index + 1}",
            instruction = instruction,
        )
    }
}
