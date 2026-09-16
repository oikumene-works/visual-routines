package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.saveable.listSaver
import java.util.UUID

internal data class EditorStep(
    val id: String = UUID.randomUUID().toString(),
    val instruction: String,
    val note: String? = null,
    val image: EditorStepImage? = null,
)

internal data class EditorStepImage(
    val source: RoutineImageSource,
    val assetId: String,
    val semanticRole: RoutineImageSemanticRole? = null,
    val descriptionOverride: String? = null,
)

internal sealed interface RestorableEditorScreen {
    val draft: CreateRoutineDraft

    data class Create(
        override val draft: CreateRoutineDraft,
        val returnDestination: CreateRoutineReturnDestination,
    ) : RestorableEditorScreen

    data class Edit(
        val routineId: String,
        override val draft: CreateRoutineDraft,
    ) : RestorableEditorScreen
}

private const val EDITOR_STEPS_SAVER_VERSION = "editor-steps-v2"
private const val EDITOR_STEP_SAVED_FIELD_COUNT = 10
private const val EDITOR_SCREEN_SAVER_VERSION = "editor-screen-v1"
private const val ROUTINE_STEP_CONTENT_SAVED_FIELD_COUNT = 11
private const val CREATE_EDITOR_SCREEN = "create"
private const val EDIT_EDITOR_SCREEN = "edit"

internal val editorStepsSaver = listSaver<List<EditorStep>, String>(
    save = { editorSteps -> encodeEditorSteps(editorSteps) },
    restore = ::decodeEditorSteps,
)

internal val editorScreenSaver = listSaver<RestorableEditorScreen?, String>(
    save = { screen -> screen?.let(::encodeEditorScreen).orEmpty() },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let(::decodeEditorScreen) },
)

internal fun encodeEditorSteps(editorSteps: List<EditorStep>): List<String> {
    return buildList {
        add(EDITOR_STEPS_SAVER_VERSION)
        editorSteps.forEach { step ->
            add(step.id)
            add(step.instruction)
            add(if (step.note == null) "0" else "1")
            add(step.note.orEmpty())
            add(if (step.image == null) "0" else "1")
            add(step.image?.source?.name.orEmpty())
            add(step.image?.assetId.orEmpty())
            add(step.image?.semanticRole?.name.orEmpty())
            add(if (step.image?.descriptionOverride == null) "0" else "1")
            add(step.image?.descriptionOverride.orEmpty())
        }
    }
}

internal fun decodeEditorSteps(savedSteps: List<String>): List<EditorStep>? {
    if (savedSteps.firstOrNull() != EDITOR_STEPS_SAVER_VERSION) {
        return savedSteps
            .takeIf { it.size % 4 == 0 }
            ?.chunked(4)
            ?.map { (id, instruction, hasNote, note) ->
                EditorStep(
                    id = id,
                    instruction = instruction,
                    note = note.takeIf { hasNote == "1" },
                )
            }
    }
    val fields = savedSteps.drop(1)
    if (fields.size % EDITOR_STEP_SAVED_FIELD_COUNT != 0) return null
    return fields.chunked(EDITOR_STEP_SAVED_FIELD_COUNT).map { values ->
        val imageSource = values[5]
        val semanticRole = values[7]
        EditorStep(
            id = values[0],
            instruction = values[1],
            note = values[3].takeIf { values[2] == "1" },
            image = if (values[4] == "1") {
                EditorStepImage(
                    source = enumValueOrNull<RoutineImageSource>(imageSource) ?: return null,
                    assetId = values[6],
                    semanticRole = semanticRole
                        .takeIf { it.isNotEmpty() }
                        ?.let { enumValueOrNull<RoutineImageSemanticRole>(it) ?: return null },
                    descriptionOverride = values[9].takeIf { values[8] == "1" },
                )
            } else {
                null
            },
        )
    }
}

internal fun encodeEditorScreen(screen: RestorableEditorScreen): List<String> {
    return buildList {
        add(EDITOR_SCREEN_SAVER_VERSION)
        when (screen) {
            is RestorableEditorScreen.Create -> {
                add(CREATE_EDITOR_SCREEN)
                add("")
                add(screen.returnDestination.name)
            }
            is RestorableEditorScreen.Edit -> {
                add(EDIT_EDITOR_SCREEN)
                add(screen.routineId)
                add("")
            }
        }
        add(screen.draft.title)
        screen.draft.steps.forEach { step ->
            add(if (step.id == null) "0" else "1")
            add(step.id.orEmpty())
            add(step.instruction)
            add(if (step.note == null) "0" else "1")
            add(step.note.orEmpty())
            add(if (step.image == null) "0" else "1")
            add(step.image?.source?.name.orEmpty())
            add(step.image?.assetId.orEmpty())
            add(step.image?.semanticRole?.name.orEmpty())
            add(if (step.image?.descriptionOverride == null) "0" else "1")
            add(step.image?.descriptionOverride.orEmpty())
        }
    }
}

internal fun decodeEditorScreen(savedScreen: List<String>): RestorableEditorScreen? {
    if (savedScreen.size < 5 || savedScreen[0] != EDITOR_SCREEN_SAVER_VERSION) return null
    val fields = savedScreen.drop(5)
    if (
        fields.isEmpty() ||
        fields.size % ROUTINE_STEP_CONTENT_SAVED_FIELD_COUNT != 0
    ) {
        return null
    }
    val steps = fields.chunked(ROUTINE_STEP_CONTENT_SAVED_FIELD_COUNT).map { values ->
        val imageSource = values[6]
        val semanticRole = values[8]
        RoutineStepContent(
            id = values[1].takeIf { values[0] == "1" },
            instruction = values[2],
            note = values[4].takeIf { values[3] == "1" },
            image = if (values[5] == "1") {
                RoutineStepImage(
                    source = enumValueOrNull<RoutineImageSource>(imageSource) ?: return null,
                    assetId = values[7],
                    semanticRole = enumValueOrNull<RoutineImageSemanticRole>(semanticRole)
                        ?: return null,
                    descriptionOverride = values[10].takeIf { values[9] == "1" },
                )
            } else {
                null
            },
        )
    }
    val draft = CreateRoutineDraft(
        title = savedScreen[4],
        steps = steps,
    )
    return when (savedScreen[1]) {
        CREATE_EDITOR_SCREEN -> RestorableEditorScreen.Create(
            draft = draft,
            returnDestination = enumValueOrNull<CreateRoutineReturnDestination>(savedScreen[3])
                ?: return null,
        )
        EDIT_EDITOR_SCREEN -> savedScreen[2]
            .takeIf { it.isNotEmpty() }
            ?.let { routineId ->
                RestorableEditorScreen.Edit(
                    routineId = routineId,
                    draft = draft,
                )
            }
        else -> null
    }
}

internal fun AppScreen.restorableEditorScreenOrNull(): RestorableEditorScreen? {
    return when (this) {
        is AppScreen.CreateRoutine -> RestorableEditorScreen.Create(draft, returnDestination)
        is AppScreen.EditRoutine -> RestorableEditorScreen.Edit(routineId, draft)
        else -> null
    }
}

internal fun RestorableEditorScreen.toAppScreen(): AppScreen {
    return when (this) {
        is RestorableEditorScreen.Create -> AppScreen.CreateRoutine(draft, returnDestination)
        is RestorableEditorScreen.Edit -> AppScreen.EditRoutine(routineId, draft)
    }
}

internal fun CreateRoutineDraft.editorStateKey(): String {
    return steps.joinToString(separator = "\u001E") { step ->
        listOf(
            step.id.orEmpty(),
            step.instruction,
            step.note.orEmpty(),
            (step.note != null).toString(),
            step.image?.source?.name.orEmpty(),
            step.image?.assetId.orEmpty(),
            step.image?.semanticRole?.name.orEmpty(),
            step.image?.descriptionOverride.orEmpty(),
            (step.image?.descriptionOverride != null).toString(),
        ).joinToString(separator = "\u001F")
    }
}

internal fun RoutineStepContent.toEditorStep(): EditorStep {
    return EditorStep(
        id = id ?: UUID.randomUUID().toString(),
        instruction = instruction,
        note = note,
        image = image?.let {
            EditorStepImage(
                source = it.source,
                assetId = it.assetId,
                semanticRole = it.semanticRole,
                descriptionOverride = it.descriptionOverride,
            )
        },
    )
}

internal fun EditorStep.toRoutineStepContent(): RoutineStepContent {
    return RoutineStepContent(
        instruction = instruction,
        note = note,
        id = id,
        image = image?.let {
            RoutineStepImage(
                source = it.source,
                assetId = it.assetId,
                semanticRole = requireNotNull(it.semanticRole) {
                    "An editor image must have a semantic role before saving."
                },
                descriptionOverride = it.descriptionOverride,
            )
        },
    )
}

internal fun EditorStep.canSave(): Boolean {
    val imageCanSave = image?.let {
        it.semanticRole != null &&
            (it.descriptionOverride == null || it.descriptionOverride.isNotBlank()) &&
            (it.source != RoutineImageSource.IMPORTED ||
                it.semanticRole != RoutineImageSemanticRole.INFORMATIVE ||
                !it.descriptionOverride.isNullOrBlank())
    } ?: true
    return instruction.isNotBlank() &&
        (note == null || note.isNotBlank()) &&
        imageCanSave
}

internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    return toMutableList().also {
        val item = it.removeAt(fromIndex)
        it.add(toIndex, item)
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? {
    return enumValues<T>().firstOrNull { it.name == name }
}
