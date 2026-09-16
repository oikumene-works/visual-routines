package io.github.ewoc2026.visualroutines

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartScreen(state: VisualRoutinesState, routine: Routine) {
    BackHandler(onBack = state::backHome)
    val backActionText = stringResource(R.string.back_action)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = routine.name,
                        modifier = Modifier.semantics {
                            heading()
                            traversalIndex = 0f
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = state::backHome,
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.routine_start_supporting_text),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.step_count,
                        routine.steps.size,
                        routine.steps.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.routine_start_pause_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { state.startRoutine(routine.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.start_action), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunnerScreen(state: VisualRoutinesState) {
    val session = state.activeSession ?: return
    val routine = state.routine(session.routineId)
    val currentStepIndex = routine.stepIndex(session.currentStepId)
    val step = routine.steps[currentStepIndex]
    val view = LocalView.current
    val stepPositionText = stringResource(
        R.string.step_position,
        currentStepIndex + 1,
        routine.steps.size,
    )
    val stepStatusText = when (session.statuses[step.id]) {
        StepStatus.DONE -> stringResource(R.string.step_status_done)
        StepStatus.SKIPPED -> stringResource(R.string.step_status_skipped)
        null -> null
    }
    val resolvedImage = step.image?.let { reference ->
        val painter = rememberStepImagePainter(reference.source, reference.assetId, ImportedImageStore.MAX_SIDE)
        val bundled = BundledImageLibrary.resolve(reference)
        val imageDescription = if (reference.semanticRole == RoutineImageSemanticRole.INFORMATIVE) {
            reference.descriptionOverride ?: bundled?.let { stringResource(it.defaultDescriptionResourceId) }
        } else null
        painter?.let { RunnerImagePresentation(contentDescription = imageDescription, painter = it) }
    }
    var lastAnnouncedStepIndex by remember(routine.id, state.screen) {
        mutableIntStateOf(-1)
    }
    BackHandler(onBack = state::requestPause)
    KeepScreenAwake()

    LaunchedEffect(routine.id, step.id, step.instruction) {
        if (lastAnnouncedStepIndex != currentStepIndex) {
            delay(900)
            view.announceAccessibilityMessage("$stepPositionText. ${step.instruction}")
            lastAnnouncedStepIndex = currentStepIndex
        }
    }

    RunnerScreenContent(
        routineName = routine.name,
        stepPositionText = stepPositionText,
        stepStatusText = stepStatusText,
        instruction = step.instruction,
        supportingInstruction = step.supportingInstruction,
        image = resolvedImage,
        canGoPrevious = currentStepIndex > 0,
        onPause = state::requestPause,
        onPrevious = state::previous,
        onSkip = state::skip,
        onDone = state::done,
    )
}

internal enum class RunnerLayoutMode {
    STACKED,
    SIDE_BY_SIDE,
}

internal data class RunnerLayoutSpec(
    val mode: RunnerLayoutMode,
    val contentSpacing: Dp,
    val contentVerticalPadding: Dp,
    val maximumImageWidth: Dp,
    val maximumImageHeight: Dp,
)

internal data class RunnerImagePresentation(
    val drawableResourceId: Int = 0,
    val painter: Painter? = null,
    val contentDescription: String?,
)

private val RunnerSideBySideMinimumWidth = 720.dp
private val RunnerSideBySideMaximumHeight = 600.dp
private val RunnerCompactHeight = 360.dp

internal fun runnerLayoutPolicy(availableSize: DpSize): RunnerLayoutSpec {
    val compactHeight = availableSize.height < RunnerCompactHeight
    val sideBySide = availableSize.width >= RunnerSideBySideMinimumWidth &&
        availableSize.height <= RunnerSideBySideMaximumHeight
    return RunnerLayoutSpec(
        mode = if (sideBySide) RunnerLayoutMode.SIDE_BY_SIDE else RunnerLayoutMode.STACKED,
        contentSpacing = if (compactHeight) 12.dp else 24.dp,
        contentVerticalPadding = if (compactHeight) 12.dp else 28.dp,
        maximumImageWidth = if (sideBySide) 400.dp else 520.dp,
        maximumImageHeight = when {
            compactHeight -> 180.dp
            sideBySide -> 360.dp
            else -> 390.dp
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunnerScreenContent(
    routineName: String,
    stepPositionText: String,
    stepStatusText: String?,
    instruction: String,
    supportingInstruction: String?,
    image: RunnerImagePresentation?,
    canGoPrevious: Boolean,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics { traversalIndex = 0f },
                title = {
                    Text(
                        text = routineName,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    TextButton(
                        onClick = onPause,
                        modifier = Modifier.sizeIn(minHeight = 48.dp),
                    ) {
                        Text(stringResource(R.string.pause_action))
                    }
                },
            )
        },
        bottomBar = {
            RunnerActions(
                canGoPrevious = canGoPrevious,
                onPrevious = onPrevious,
                onSkip = onSkip,
                onDone = onDone,
                modifier = Modifier.semantics { traversalIndex = 2f },
            )
        },
        modifier = Modifier
            .statusBarsPadding()
            .semantics { isTraversalGroup = true },
    ) { padding ->
        RunnerBody(
            stepPositionText = stepPositionText,
            stepStatusText = stepStatusText,
            instruction = instruction,
            supportingInstruction = supportingInstruction,
            image = image,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics { traversalIndex = 1f },
        )
    }
}

@Composable
internal fun RunnerBody(
    stepPositionText: String,
    stepStatusText: String?,
    instruction: String,
    supportingInstruction: String?,
    image: RunnerImagePresentation?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val layoutSpec = runnerLayoutPolicy(DpSize(maxWidth, maxHeight))
        val layoutMode = if (image == null) RunnerLayoutMode.STACKED else layoutSpec.mode
        val instructionStyle = if (maxHeight < RunnerCompactHeight) {
            MaterialTheme.typography.headlineLarge.copy(
                fontSize = 30.sp,
                lineHeight = 38.sp,
            )
        } else {
            MaterialTheme.typography.headlineLarge.copy(
                fontSize = 34.sp,
                lineHeight = 43.sp,
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = if (layoutMode == RunnerLayoutMode.SIDE_BY_SIDE) 1040.dp else 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = layoutSpec.contentVerticalPadding)
                .testTag(
                    when (layoutMode) {
                        RunnerLayoutMode.STACKED -> "runner-layout-stacked"
                        RunnerLayoutMode.SIDE_BY_SIDE -> "runner-layout-side-by-side"
                    },
                ),
        ) {
            when (layoutMode) {
                RunnerLayoutMode.STACKED -> {
                    RunnerTextContent(
                        stepPositionText = stepPositionText,
                        stepStatusText = stepStatusText,
                        instruction = instruction,
                        supportingInstruction = supportingInstruction,
                        instructionStyle = instructionStyle,
                        spacing = layoutSpec.contentSpacing,
                    )
                    image?.let {
                        Spacer(Modifier.height(layoutSpec.contentSpacing))
                        RunnerStepImage(
                            image = it,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .sizeIn(
                                    maxWidth = layoutSpec.maximumImageWidth,
                                    maxHeight = layoutSpec.maximumImageHeight,
                                )
                                .fillMaxWidth(),
                        )
                    }
                }
                RunnerLayoutMode.SIDE_BY_SIDE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(layoutSpec.contentSpacing),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RunnerTextContent(
                            stepPositionText = stepPositionText,
                            stepStatusText = stepStatusText,
                            instruction = instruction,
                            supportingInstruction = supportingInstruction,
                            instructionStyle = instructionStyle,
                            spacing = layoutSpec.contentSpacing,
                            modifier = Modifier
                                .weight(1.25f)
                                .semantics { isTraversalGroup = true }
                                .testTag("runner-text-column"),
                        )
                        RunnerStepImage(
                            image = requireNotNull(image),
                            modifier = Modifier
                                .weight(0.85f)
                                .sizeIn(
                                    maxWidth = layoutSpec.maximumImageWidth,
                                    maxHeight = layoutSpec.maximumImageHeight,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunnerTextContent(
    stepPositionText: String,
    stepStatusText: String?,
    instruction: String,
    supportingInstruction: String?,
    instructionStyle: androidx.compose.ui.text.TextStyle,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = stepPositionText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        stepStatusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = instruction,
            style = instructionStyle,
            fontWeight = FontWeight.SemiBold,
        )
        supportingInstruction?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RunnerStepImage(
    image: RunnerImagePresentation,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = image.painter ?: painterResource(image.drawableResourceId),
        contentDescription = image.contentDescription,
        modifier = modifier.testTag("runner-step-image"),
        contentScale = ContentScale.Fit,
    )
}

@Preview(name = "Compact phone", widthDp = 360, heightDp = 720, showBackground = true)
@Preview(name = "Short landscape", widthDp = 900, heightDp = 430, showBackground = true)
@Preview(name = "Generic tablet", widthDp = 800, heightDp = 1280, showBackground = true)
@Preview(name = "Generic tablet landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(
    name = "Phone at font scale 2.0",
    widthDp = 412,
    heightDp = 915,
    fontScale = 2f,
    showBackground = true,
)
@Preview(
    name = "Short landscape at font scale 2.0",
    widthDp = 832,
    heightDp = 384,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun RunnerScreenPreview() {
    VisualRoutinesTheme {
        RunnerScreenContent(
            routineName = "Change bed linen",
            stepPositionText = "Step 2 of 9",
            stepStatusText = null,
            instruction = "Take the duvet out of the duvet cover without losing the corners.",
            supportingInstruction = "Put the old cover with the other laundry.",
            image = RunnerImagePresentation(
                drawableResourceId = R.drawable.bed_linen_remove_duvet_cover,
                contentDescription = "Two hands separate a white duvet from a gray duvet cover on a bed.",
            ),
            canGoPrevious = true,
            onPause = {},
            onPrevious = {},
            onSkip = {},
            onDone = {},
        )
    }
}

@Preview(name = "Unresolved image fallback", widthDp = 384, heightDp = 832, showBackground = true)
@Composable
private fun RunnerMissingImagePreview() {
    VisualRoutinesTheme {
        RunnerScreenContent(
            routineName = "Change bed linen",
            stepPositionText = "Step 4 of 9",
            stepStatusText = null,
            instruction = "Keep following this complete instruction even when the optional image is unavailable.",
            supportingInstruction = "No placeholder or empty image area should appear.",
            image = null,
            canGoPrevious = true,
            onPause = {},
            onPrevious = {},
            onSkip = {},
            onDone = {},
        )
    }
}

@Suppress("DEPRECATION")
private fun View.announceAccessibilityMessage(message: String) {
    announceForAccessibility(message)
}

@Composable
private fun RunnerActions(
    canGoPrevious: Boolean,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skipStepAction = stringResource(R.string.skip_step_action)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 52.dp),
            ) {
                Text(stringResource(R.string.previous_action))
            }
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 52.dp)
                    .semantics { contentDescription = skipStepAction },
            ) {
                Text(stringResource(R.string.skip_action))
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 60.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Text(
                text = stringResource(R.string.done_action),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun CompleteScreen(state: VisualRoutinesState, routine: Routine) {
    BackHandler(onBack = state::completeToHome)
    ScreenSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.complete_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.complete_supporting_text, routine.name),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = state::completeToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 56.dp),
            ) {
                Text(stringResource(R.string.return_to_routine_list_action))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { state.runAgain(routine.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 52.dp),
            ) {
                Text(stringResource(R.string.repeat_this_routine_action))
            }
        }
    }
}
