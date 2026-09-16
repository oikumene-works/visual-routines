package io.github.ewoc2026.visualroutines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(state: VisualRoutinesState) {
    BackHandler(enabled = false) {}
    val appName = stringResource(R.string.app_name)
    val manageRoutinesText = stringResource(R.string.manage_routines_action)
    val settingsText = stringResource(R.string.settings_title)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = appName,
                        modifier = Modifier.semantics {
                            heading()
                            traversalIndex = 0f
                        },
                    )
                },
                actions = {
                    if (state.homeSettings.allowRoutineEditing) {
                        IconButton(
                            onClick = state::openManageRoutines,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = manageRoutinesText
                                    traversalIndex = 1f
                                },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
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
                    paneTitle = appName
                },
            applySystemBarsPadding = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_supporting_text),
                    style = MaterialTheme.typography.bodyLarge,
                )

                state.activeSession?.let { session ->
                    val routine = state.routine(session.routineId)
                    ActiveRoutineCard(
                        routine = routine,
                        session = session,
                        onContinue = state::continueRoutine,
                        onReset = state::requestReset,
                    )
                }

                HorizontalDivider()
                val activeRoutineId = state.activeSession?.routineId
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.homeRoutines.filterNot { it.id == activeRoutineId }.forEach { routine ->
                        RoutineCard(routine = routine, onClick = { state.openRoutine(routine.id) })
                    }
                }
                if (state.homeRoutines.none { it.id != activeRoutineId }) {
                    Text(
                        text = stringResource(
                            if (activeRoutineId == null) {
                                R.string.no_routines_to_show
                            } else {
                                R.string.no_other_routines_to_show
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = state::openSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp),
                ) {
                    Text(text = settingsText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(state: VisualRoutinesState) {
    BackHandler(onBack = state::backHome)
    val backActionText = stringResource(R.string.back_action)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = state::backHome,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = backActionText },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = Modifier.statusBarsPadding(),
    ) { padding ->
        ScreenSurface(
            modifier = Modifier.padding(padding),
            applySystemBarsPadding = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsToggle(
                    label = stringResource(R.string.show_example_routines),
                    checked = state.homeSettings.showExampleRoutines,
                    onCheckedChange = state::updateShowExampleRoutines,
                )
                SettingsToggle(
                    label = stringResource(R.string.allow_routine_editing),
                    checked = state.homeSettings.allowRoutineEditing,
                    onCheckedChange = state::updateAllowRoutineEditing,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = state::openPrivacyPolicy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp),
                ) {
                    Text(text = stringResource(R.string.privacy_policy_title))
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateText = stringResource(if (checked) R.string.setting_on else R.string.setting_off)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .clearAndSetSemantics {
                contentDescription = "$label, $stateText"
                role = Role.Switch
                onClick {
                    onCheckedChange(!checked)
                    true
                }
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageRoutinesScreen(state: VisualRoutinesState) {
    BackHandler(onBack = state::backHome)
    val backActionText = stringResource(R.string.back_action)
    val reorderRoutinesText = stringResource(R.string.reorder_routines_action)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = stringResource(R.string.manage_routines_title),
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
                actions = {
                    if (state.userRoutineIds.size > 1) {
                        IconButton(
                            onClick = state::openReorderRoutines,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = reorderRoutinesText },
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = state::openCreateRoutineFromManage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 56.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.create_routine_action),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val userRoutines = state.routines.filter { it.id in state.userRoutineIds }
                if (userRoutines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_personal_routines),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    userRoutines.forEach { routine ->
                        ManagedRoutineCard(
                            routine = routine,
                            isActive = state.activeSession?.routineId == routine.id,
                            onEdit = { state.openEditRoutine(routine.id) },
                            onDelete = { state.requestDeleteRoutine(routine.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderRoutinesScreen(state: VisualRoutinesState) {
    BackHandler(onBack = state::backToManageRoutines)
    val backActionText = stringResource(R.string.back_action)
    val routineMovedUp = stringResource(R.string.routine_moved_up_announcement)
    val routineMovedDown = stringResource(R.string.routine_moved_down_announcement)
    var feedback by remember { mutableStateOf("") }
    val userRoutines = state.routines.filter { it.id in state.userRoutineIds }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 0f
                },
                title = {
                    Text(
                        text = stringResource(R.string.reorder_routines_title),
                        modifier = Modifier.semantics {
                            heading()
                            traversalIndex = 0f
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = state::backToManageRoutines,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = backActionText
                                traversalIndex = 1f
                            },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = feedback
                        },
                ) {
                    if (feedback.isNotEmpty()) {
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                userRoutines.forEachIndexed { index, routine ->
                    key(routine.id) {
                        ReorderRoutineCard(
                            routine = routine,
                            canMoveUp = index > 0,
                            canMoveDown = index < userRoutines.lastIndex,
                            onMoveUp = {
                                state.moveUserRoutine(routine.id, offset = -1)
                                feedback = routineMovedUp.format(routine.name)
                            },
                            onMoveDown = {
                                state.moveUserRoutine(routine.id, offset = 1)
                                feedback = routineMovedDown.format(routine.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRoutineCard(
    routine: Routine,
    session: ActiveSession,
    onContinue: () -> Unit,
    onReset: () -> Unit,
) {
    val pausedRoutineText = stringResource(R.string.paused_routine)
    val currentStepIndex = routine.stepIndex(session.currentStepId)
    val progressDescription = stringResource(
        R.string.routine_progress,
        routine.name,
        currentStepIndex + 1,
        routine.steps.size,
    )
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$pausedRoutineText, $progressDescription"
                    heading()
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = pausedRoutineText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = progressDescription,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.continue_action), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(
                onClick = onReset,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.reset_action))
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: Routine, onClick: () -> Unit) {
    val stepCount = pluralStringResource(
        R.plurals.step_count,
        routine.steps.size,
        routine.steps.size,
    )
    OutlinedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 92.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stepCount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManagedRoutineCard(
    routine: Routine,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val stepCount = pluralStringResource(
        R.plurals.step_count,
        routine.steps.size,
        routine.steps.size,
    )
    val editRoutineText = stringResource(R.string.edit_routine_action, routine.name)
    val deleteRoutineText = stringResource(R.string.delete_routine_action, routine.name)
    OutlinedCard(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stepCount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isActive) {
                Text(
                    text = stringResource(R.string.active_routine_editing_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = editRoutineText },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = deleteRoutineText },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderRoutineCard(
    routine: Routine,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val stepCount = pluralStringResource(R.plurals.step_count, routine.steps.size, routine.steps.size)
    val moveUpText = stringResource(R.string.move_routine_up_action, routine.name)
    val moveDownText = stringResource(R.string.move_routine_down_action, routine.name)
    OutlinedCard(
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.semantics { traversalIndex = 0f },
                )
                Text(
                    text = stepCount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { traversalIndex = 1f },
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = moveUpText
                        traversalIndex = 2f
                    },
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics {
                        contentDescription = moveDownText
                        traversalIndex = 3f
                    },
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}
