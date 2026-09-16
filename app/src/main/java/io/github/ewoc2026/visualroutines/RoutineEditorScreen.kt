package io.github.ewoc2026.visualroutines

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button as AndroidButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnNextLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateRoutineScreen(
    state: VisualRoutinesState,
    draft: CreateRoutineDraft,
    onEditorExit: () -> Unit,
) {
    RoutineEditorScreen(
        draft = draft,
        title = stringResource(R.string.create_routine_title),
        onCancel = {
            onEditorExit()
            state.cancelCreateRoutine()
        },
        onSave = { title, steps ->
            state.createRoutine(title, steps).also { saved ->
                if (saved) onEditorExit()
            }
        },
    )
}

@Composable
internal fun EditRoutineScreen(
    state: VisualRoutinesState,
    routineId: String,
    draft: CreateRoutineDraft,
    onEditorExit: () -> Unit,
) {
    RoutineEditorScreen(
        draft = draft,
        title = stringResource(R.string.edit_routine_title),
        onCancel = {
            onEditorExit()
            state.cancelEditRoutine()
        },
        onSave = { title, steps ->
            state.updateRoutine(routineId, title, steps).also { saved ->
                if (saved) onEditorExit()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorScreen(
    draft: CreateRoutineDraft,
    title: String,
    onCancel: () -> Unit,
    onSave: (String, List<RoutineStepContent>) -> Boolean,
) {
    val backActionText = stringResource(R.string.back_action)
    val stepsKey = draft.editorStateKey()
    var routineName by rememberSaveable(draft.title) { mutableStateOf(draft.title) }
    var editorSteps by rememberSaveable(
        stepsKey,
        stateSaver = editorStepsSaver,
    ) {
        mutableStateOf(draft.steps.map(RoutineStepContent::toEditorStep))
    }
    val context = LocalContext.current
    val application = context.applicationContext as? VisualRoutinesApplication
    val imageStore = application?.importedImages
    val imageDraftId = rememberSaveable(stepsKey) { UUID.randomUUID().toString() }
    var imageStoreReady by remember { mutableStateOf(false) }
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImageStepId by rememberSaveable { mutableStateOf<String?>(null) }
    var importError by rememberSaveable { mutableStateOf<Int?>(null) }
    val importBusy = pendingImageUri != null
    var chooserStepId by rememberSaveable(stepsKey) { mutableStateOf<String?>(null) }
    var pendingEditorFeedback by remember { mutableStateOf<EditorFeedback?>(null) }
    var displayedEditorFeedback by remember { mutableStateOf<EditorFeedback?>(null) }
    var editorFeedbackToken by remember { mutableIntStateOf(0) }
    val editorScrollState = rememberScrollState()
    val canSave = !importBusy && routineName.isNotBlank() &&
        editorSteps.isNotEmpty() &&
        editorSteps.all(EditorStep::canSave)
    val resources = LocalResources.current
    val stepMovedUpAnnouncement = stringResource(R.string.step_moved_up_announcement)
    val stepMovedDownAnnouncement = stringResource(R.string.step_moved_down_announcement)

    fun exitEditor() {
        imageStore?.finishDraft(imageDraftId)
        application?.let { imageStore?.collect(it.routineRepository.loadCatalog()) }
        onCancel()
    }

    LaunchedEffect(imageDraftId) {
        if (imageStore != null) {
            try {
                withContext(Dispatchers.IO) {
                    imageStore.beginDraft(imageDraftId, editorSteps.mapNotNull { it.image }
                        .filter { it.source == RoutineImageSource.IMPORTED }.mapTo(mutableSetOf()) { it.assetId })
                }
                imageStoreReady = true
            } catch (_: ImageImportException) {
                importError = R.string.image_import_storage_error
                pendingImageUri = null
                pendingImageStepId = null
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            pendingImageUri = uri.toString()
        } else {
            pendingImageStepId = null
        }
    }

    BackHandler(enabled = importBusy) { /* Keep the draft mounted until import has a result. */ }
    BackHandler(enabled = !importBusy) {
        if (chooserStepId != null) {
            chooserStepId = null
        } else {
            exitEditor()
        }
    }

    LaunchedEffect(pendingEditorFeedback?.token) {
        pendingEditorFeedback?.let { feedback ->
            displayedEditorFeedback = null
            withFrameNanos { }
            displayedEditorFeedback = feedback
        }
    }

    fun publishEditorFeedback(message: String) {
        editorFeedbackToken += 1
        pendingEditorFeedback = EditorFeedback(message, editorFeedbackToken)
    }

    LaunchedEffect(pendingImageUri, imageStoreReady) {
        val uri = pendingImageUri ?: return@LaunchedEffect
        if (!imageStoreReady || imageStore == null) return@LaunchedEffect
        try {
            val id = withContext(Dispatchers.IO) {
                imageStore.importImage(imageDraftId) { context.contentResolver.openInputStream(Uri.parse(uri)) }
            }
            val index = editorSteps.indexOfFirst { it.id == pendingImageStepId }
            if (index >= 0) {
                val hadImage = editorSteps[index].image != null
                editorSteps = editorSteps.updated(index) {
                    it.copy(image = EditorStepImage(RoutineImageSource.IMPORTED, id))
                }
                publishEditorFeedback(resources.getString(
                    if (hadImage) R.string.step_image_replaced_announcement else R.string.step_image_selected_announcement,
                    resources.getString(R.string.local_image_name), index + 1,
                ))
                chooserStepId = null
            }
            importError = null
            pendingImageUri = null
            pendingImageStepId = null
        } catch (error: ImageImportException) {
            importError = when (error.failure) {
                ImageImportFailure.UNREADABLE -> R.string.image_import_unreadable_error
                ImageImportFailure.UNSUPPORTED -> R.string.image_import_unsupported_error
                ImageImportFailure.TOO_LARGE -> R.string.image_import_size_error
                ImageImportFailure.STORAGE -> R.string.image_import_storage_error
            }
            pendingImageUri = null
            pendingImageStepId = null
        }
    }

    val chooserStepIndex = chooserStepId?.let { stepId ->
        editorSteps.indexOfFirst { it.id == stepId }.takeIf { it >= 0 }
    }
    if (chooserStepIndex != null) {
        val chooserStep = editorSteps[chooserStepIndex]
        BundledImageChooserScreen(
            stepNumber = chooserStepIndex + 1,
            selectedAssetId = chooserStep.image
                ?.takeIf { it.source == RoutineImageSource.BUNDLED }
                ?.assetId,
            onCancel = { if (!importBusy) chooserStepId = null },
            importing = importBusy,
            importError = importError,
            canImport = imageStoreReady,
            onSelectLocal = {
                pendingImageStepId = chooserStep.id
                importError = null
                try {
                    imagePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    })
                } catch (_: android.content.ActivityNotFoundException) {
                    pendingImageStepId = null
                    importError = R.string.image_picker_unavailable
                }
            },
            onSelect = { asset ->
                val currentIndex = editorSteps.indexOfFirst { it.id == chooserStep.id }
                if (currentIndex >= 0) {
                    val previousImage = editorSteps[currentIndex].image
                    val keepsExistingChoice = previousImage?.source == RoutineImageSource.BUNDLED &&
                        previousImage.assetId == asset.assetId
                    editorSteps = editorSteps.updated(currentIndex) { step ->
                        step.copy(
                            image = if (keepsExistingChoice) {
                                previousImage
                            } else {
                                EditorStepImage(
                                    source = RoutineImageSource.BUNDLED,
                                    assetId = asset.assetId,
                                )
                            },
                        )
                    }
                    val assetName = resources.getString(asset.nameResourceId)
                    publishEditorFeedback(
                        resources.getString(
                            if (previousImage == null) {
                                R.string.step_image_selected_announcement
                            } else {
                                R.string.step_image_replaced_announcement
                            },
                            assetName,
                            currentIndex + 1,
                        ),
                    )
                }
                chooserStepId = null
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.semantics {
                            heading()
                            traversalIndex = 0f
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::exitEditor,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = backActionText
                                traversalIndex = 1f
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        modifier = Modifier
            .statusBarsPadding()
            .semantics { isTraversalGroup = true },
    ) { padding ->
        ScreenSurface(
            modifier = Modifier
                .padding(padding)
                .semantics { traversalIndex = 1f },
            applySystemBarsPadding = false,
            applyTopContentPadding = false,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(editorScrollState)
                        .padding(top = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = routineName,
                        onValueChange = { routineName = it },
                        label = { Text(stringResource(R.string.routine_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.steps_heading, editorSteps.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    editorSteps.forEachIndexed { index, editorStep ->
                        key(editorStep.id) {
                            StepEditorRow(
                                stepNumber = index + 1,
                                instruction = editorStep.instruction,
                                note = editorStep.note,
                                image = editorStep.image,
                                canMoveUp = index > 0,
                                canMoveDown = index < editorSteps.lastIndex,
                                canDelete = editorSteps.size > 1,
                                onInstructionChange = { updated ->
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(instruction = updated)
                                    }
                                },
                                onNoteChange = { updated ->
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(note = updated)
                                    }
                                },
                                onAddNote = {
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(note = "")
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            R.string.step_note_added_announcement,
                                            index + 1,
                                        ),
                                    )
                                },
                                onRemoveNote = {
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(note = null)
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            R.string.step_note_removed_announcement,
                                            index + 1,
                                        ),
                                    )
                                },
                                onAddImage = {
                                    chooserStepId = editorStep.id
                                },
                                onReplaceImage = {
                                    chooserStepId = editorStep.id
                                },
                                onRemoveImage = {
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(image = null)
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            R.string.step_image_removed_announcement,
                                            index + 1,
                                        ),
                                    )
                                },
                                onImageRoleChange = { semanticRole ->
                                    editorSteps = editorSteps.updated(index) {
                                        val image = requireNotNull(it.image)
                                        it.copy(
                                            image = image.copy(
                                                semanticRole = semanticRole,
                                                descriptionOverride = image.descriptionOverride
                                                    ?.takeUnless { description ->
                                                        semanticRole == RoutineImageSemanticRole.REDUNDANT &&
                                                            description.isBlank()
                                                    },
                                            ),
                                        )
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            when (semanticRole) {
                                                RoutineImageSemanticRole.INFORMATIVE ->
                                                    R.string.step_image_described_announcement
                                                RoutineImageSemanticRole.REDUNDANT ->
                                                    R.string.step_image_not_described_announcement
                                            },
                                            index + 1,
                                        ),
                                    )
                                },
                                onAddDescriptionOverride = {
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(
                                            image = requireNotNull(it.image).copy(
                                                descriptionOverride = "",
                                            ),
                                        )
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            R.string.step_image_description_added_announcement,
                                            index + 1,
                                        ),
                                    )
                                },
                                onDescriptionOverrideChange = { description ->
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(
                                            image = requireNotNull(it.image).copy(
                                                descriptionOverride = description,
                                            ),
                                        )
                                    }
                                },
                                onRemoveDescriptionOverride = {
                                    editorSteps = editorSteps.updated(index) {
                                        it.copy(
                                            image = requireNotNull(it.image).copy(
                                                descriptionOverride = null,
                                            ),
                                        )
                                    }
                                    publishEditorFeedback(
                                        resources.getString(
                                            R.string.step_image_description_removed_announcement,
                                            index + 1,
                                        ),
                                    )
                                },
                                onMoveUp = {
                                    editorSteps = editorSteps.moved(index, index - 1)
                                    publishEditorFeedback(stepMovedUpAnnouncement)
                                },
                                onMoveDown = {
                                    editorSteps = editorSteps.moved(index, index + 1)
                                    publishEditorFeedback(stepMovedDownAnnouncement)
                                },
                                onDelete = {
                                    editorSteps = editorSteps.toMutableList().also { it.removeAt(index) }
                                    publishEditorFeedback(
                                        resources.getQuantityString(
                                            R.plurals.step_deleted_announcement,
                                            editorSteps.size,
                                            index + 1,
                                            editorSteps.size,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val addedStep = EditorStep(instruction = "")
                            editorSteps = editorSteps + addedStep
                            publishEditorFeedback(
                                resources.getString(
                                    R.string.step_added_announcement,
                                    editorSteps.size,
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 52.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.add_step_action),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (onSave(routineName, editorSteps.map(EditorStep::toRoutineStepContent))) {
                                imageStore?.finishDraft(imageDraftId)
                                application?.let { imageStore?.collect(it.routineRepository.loadCatalog()) }
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 56.dp),
                    ) {
                        Text(stringResource(R.string.save_action))
                    }
                    OutlinedButton(
                        onClick = ::exitEditor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 52.dp),
                    ) {
                        Text(stringResource(R.string.cancel_action))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clearAndSetSemantics {
                            liveRegion = LiveRegionMode.Assertive
                            displayedEditorFeedback?.let { feedback ->
                                contentDescription = feedback.message
                            }
                        },
                ) {
                    displayedEditorFeedback?.let { feedback ->
                        Text(
                            text = feedback.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepEditorRow(
    stepNumber: Int,
    instruction: String,
    note: String?,
    image: EditorStepImage?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onInstructionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAddNote: () -> Unit,
    onRemoveNote: () -> Unit,
    onAddImage: () -> Unit,
    onReplaceImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onImageRoleChange: (RoutineImageSemanticRole) -> Unit,
    onAddDescriptionOverride: () -> Unit,
    onDescriptionOverrideChange: (String) -> Unit,
    onRemoveDescriptionOverride: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val moveUpText = stringResource(R.string.move_step_up_action)
    val moveDownText = stringResource(R.string.move_step_down_action)
    val deleteText = stringResource(R.string.delete_step_action)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = instruction,
            onValueChange = onInstructionChange,
            label = { Text(stringResource(R.string.step_instruction_label, stepNumber)) },
            modifier = Modifier.fillMaxWidth(),
        )
        note?.let {
            OutlinedTextField(
                value = it,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.step_note_label, stepNumber)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxItemsInEachRow = 2,
        ) {
            Row(
                modifier = Modifier.testTag("step-$stepNumber-content-actions"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = if (note == null) onAddNote else onRemoveNote,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (note == null) {
                                R.string.add_note_action
                            } else {
                                R.string.remove_note_action
                            },
                        ),
                    )
                }
                AccessibilityRestoringTextButton(
                    text = stringResource(
                        if (image == null) {
                            R.string.add_image_action
                        } else {
                            R.string.remove_image_action
                        },
                    ),
                    onClick = if (image == null) onAddImage else onRemoveImage,
                    restoreAccessibilityFocusAfterClick = image != null,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .testTag("step-$stepNumber-image-action"),
                )
            }
            Row(
                modifier = Modifier.testTag("step-$stepNumber-order-actions"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.semantics { contentDescription = moveUpText },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.semantics { contentDescription = moveDownText },
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.semantics { contentDescription = deleteText },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        }
        image?.let {
            StepImageEditor(
                stepNumber = stepNumber,
                image = it,
                onReplaceImage = onReplaceImage,
                onRoleChange = onImageRoleChange,
                onAddDescriptionOverride = onAddDescriptionOverride,
                onDescriptionOverrideChange = onDescriptionOverrideChange,
                onRemoveDescriptionOverride = onRemoveDescriptionOverride,
            )
        }
    }
}

@Composable
private fun AccessibilityRestoringTextButton(
    text: String,
    onClick: () -> Unit,
    restoreAccessibilityFocusAfterClick: Boolean,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSize = MaterialTheme.typography.labelLarge.fontSize.value

    AndroidView(
        factory = { context ->
            AndroidButton(context, null, android.R.attr.borderlessButtonStyle).apply {
                isAllCaps = false
                includeFontPadding = false
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        },
        modifier = modifier,
        update = { button ->
            button.text = text
            button.setTextColor(textColor)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            button.setOnClickListener {
                val shouldRestore =
                    restoreAccessibilityFocusAfterClick && button.isAccessibilityFocused
                onClick()
                if (shouldRestore) {
                    button.doOnNextLayout { laidOutButton ->
                        laidOutButton.post {
                            if (
                                laidOutButton.isAttachedToWindow &&
                                !laidOutButton.isAccessibilityFocused
                            ) {
                                laidOutButton.performAccessibilityAction(
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                                    null,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun StepImageEditor(
    stepNumber: Int,
    image: EditorStepImage,
    onReplaceImage: () -> Unit,
    onRoleChange: (RoutineImageSemanticRole) -> Unit,
    onAddDescriptionOverride: () -> Unit,
    onDescriptionOverrideChange: (String) -> Unit,
    onRemoveDescriptionOverride: () -> Unit,
) {
    val asset = image
        .takeIf { it.source == RoutineImageSource.BUNDLED }
        ?.let { BundledImageLibrary.resolve(it.assetId) }
    val imagePainter = rememberStepImagePainter(image.source, image.assetId, 400)
    val assetName = if (image.source == RoutineImageSource.IMPORTED) stringResource(R.string.local_image_name)
        else asset?.let { stringResource(it.nameResourceId) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.selected_image_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (imagePainter == null) {
                Text(
                    text = stringResource(R.string.selected_image_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.selected_image_unavailable_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Image(
                    painter = imagePainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 200.dp)
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                )
                Text(
                    text = requireNotNull(assetName),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (image.source == RoutineImageSource.IMPORTED && imagePainter != null) {
                Text(stringResource(R.string.local_image_preview_help), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = onReplaceImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.replace_image_action))
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.image_role_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.image_role_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImageRoleChoice(
                selected = image.semanticRole == RoutineImageSemanticRole.INFORMATIVE,
                title = stringResource(R.string.describe_image_option),
                explanation = stringResource(R.string.describe_image_option_explanation),
                onSelect = { onRoleChange(RoutineImageSemanticRole.INFORMATIVE) },
            )
            ImageRoleChoice(
                selected = image.semanticRole == RoutineImageSemanticRole.REDUNDANT,
                title = stringResource(R.string.do_not_describe_image_option),
                explanation = stringResource(R.string.do_not_describe_image_option_explanation),
                onSelect = { onRoleChange(RoutineImageSemanticRole.REDUNDANT) },
            )
            if (image.semanticRole == null) {
                Text(
                    text = stringResource(R.string.image_role_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (image.semanticRole == RoutineImageSemanticRole.INFORMATIVE) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.image_description_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (image.source == RoutineImageSource.BUNDLED) AccessibilityRestoringTextButton(
                    text = stringResource(
                        when {
                            image.descriptionOverride == null ->
                                R.string.write_step_image_description_action
                            asset == null ->
                                R.string.remove_step_image_description_action
                            else -> R.string.use_bundled_image_description_action
                        },
                    ),
                    onClick = if (image.descriptionOverride == null) {
                        onAddDescriptionOverride
                    } else {
                        onRemoveDescriptionOverride
                    },
                    restoreAccessibilityFocusAfterClick = true,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .testTag("step-$stepNumber-description-action"),
                )
                if (image.descriptionOverride != null || image.source == RoutineImageSource.IMPORTED) {
                    OutlinedTextField(
                        value = image.descriptionOverride.orEmpty(),
                        onValueChange = onDescriptionOverrideChange,
                        label = {
                            Text(
                                stringResource(
                                    R.string.step_image_description_label,
                                    stepNumber,
                                ),
                            )
                        },
                        supportingText = {
                            Text(stringResource(if (image.source == RoutineImageSource.IMPORTED)
                                R.string.local_image_description_help else R.string.image_description_required))
                        },
                        isError = image.descriptionOverride.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = asset?.let {
                            stringResource(it.defaultDescriptionResourceId)
                        } ?: stringResource(R.string.image_description_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageRoleChoice(
    selected: Boolean,
    title: String,
    explanation: String,
    onSelect: () -> Unit,
) {
    val accessibilityLabel = "$title. $explanation"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .sizeIn(minHeight = 56.dp)
            .padding(vertical = 6.dp)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                this.selected = selected
                role = Role.RadioButton
                onClick {
                    onSelect()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BundledImageChooserScreen(
    stepNumber: Int,
    selectedAssetId: String?,
    onCancel: () -> Unit,
    onSelect: (BundledImageAsset) -> Unit,
    onSelectLocal: () -> Unit,
    importing: Boolean,
    importError: Int?,
    canImport: Boolean,
) {
    val backActionText = stringResource(R.string.back_action)
    val chooserTitle = stringResource(R.string.choose_image_title)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = chooserTitle,
                        modifier = Modifier.semantics {
                            heading()
                            traversalIndex = 0f
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = backActionText
                                traversalIndex = 1f
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        modifier = Modifier
            .statusBarsPadding()
            .semantics { isTraversalGroup = true },
    ) { padding ->
        ScreenSurface(
            modifier = Modifier
                .padding(padding)
                .semantics {
                    traversalIndex = 1f
                    paneTitle = chooserTitle
                },
            applySystemBarsPadding = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.choose_image_explanation, stepNumber),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = onSelectLocal,
                    enabled = canImport && !importing,
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
                ) { Text(stringResource(R.string.select_local_image_action)) }
                Text(stringResource(R.string.local_image_selection_help), style = MaterialTheme.typography.bodyMedium)
                if (importing || importError != null) {
                    Text(
                        stringResource(if (importing) R.string.image_import_progress else requireNotNull(importError)),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (!importing) BundledImageLibrary.all.forEach { asset ->
                    BundledImageChoice(
                        asset = asset,
                        selected = asset.assetId == selectedAssetId,
                        onSelect = { onSelect(asset) },
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 52.dp),
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        }
    }
}

@Composable
private fun BundledImageChoice(
    asset: BundledImageAsset,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val assetName = stringResource(asset.nameResourceId)
    val assetDescription = stringResource(asset.defaultDescriptionResourceId)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .clearAndSetSemantics {
                contentDescription = "$assetName. $assetDescription"
                this.selected = selected
                role = Role.RadioButton
                onClick {
                    onSelect()
                    true
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(asset.drawableResourceId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
            )
            Text(
                text = assetName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = assetDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.current_image),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private data class EditorFeedback(
    val message: String,
    val token: Int,
)

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> {
    return toMutableList().also { it[index] = transform(it[index]) }
}
