package io.github.ewoc2026.visualroutines

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme

@Composable
internal fun VisualRoutinesApp(
    state: VisualRoutinesState,
) {
    var restorableEditorScreen by rememberSaveable(stateSaver = editorScreenSaver) {
        mutableStateOf<RestorableEditorScreen?>(null)
    }
    val application = LocalContext.current.applicationContext as? VisualRoutinesApplication
    val liveScreen = state.screen
    val visibleScreen = if (liveScreen == AppScreen.Home) {
        restorableEditorScreen?.toAppScreen() ?: liveScreen
    } else {
        liveScreen
    }

    LaunchedEffect(liveScreen) {
        liveScreen.restorableEditorScreenOrNull()?.let { editorScreen ->
            restorableEditorScreen = editorScreen
        }
    }
    LaunchedEffect(Unit) {
        if (liveScreen == AppScreen.Home && restorableEditorScreen == null) {
            application?.let {
                it.importedImages.recoverDraft(null)
                it.importedImages.collect(it.routineRepository.loadCatalog())
            }
        }
        if (liveScreen == AppScreen.Home) {
            when (val editorScreen = restorableEditorScreen) {
                is RestorableEditorScreen.Create -> state.openCreateRoutine(
                    draft = editorScreen.draft,
                    returnDestination = editorScreen.returnDestination,
                )
                is RestorableEditorScreen.Edit -> state.openEditRoutine(editorScreen.routineId)
                null -> Unit
            }
        }
    }

    VisualRoutinesTheme {
        when (val screen = visibleScreen) {
            AppScreen.Home -> HomeScreen(state)
            AppScreen.Settings -> SettingsScreen(state)
            AppScreen.PrivacyPolicy -> PrivacyPolicyScreen(state)
            AppScreen.ManageRoutines -> ManageRoutinesScreen(state)
            AppScreen.ReorderRoutines -> ReorderRoutinesScreen(state)
            is AppScreen.CreateRoutine -> CreateRoutineScreen(
                state = state,
                draft = screen.draft,
                onEditorExit = { restorableEditorScreen = null },
            )
            is AppScreen.EditRoutine -> EditRoutineScreen(
                state = state,
                routineId = screen.routineId,
                draft = screen.draft,
                onEditorExit = { restorableEditorScreen = null },
            )
            is AppScreen.RoutineStart -> StartScreen(state, state.routine(screen.routineId))
            AppScreen.Runner -> RunnerScreen(state)
            is AppScreen.Complete -> CompleteScreen(state, state.routine(screen.routineId))
        }
        AppDialog(state)
    }
}

@Composable
private fun AppDialog(state: VisualRoutinesState) {
    when (val dialog = state.dialog) {
        null -> Unit
        AppDialog.Pause -> ConfirmationDialog(
            title = stringResource(R.string.pause_title),
            text = stringResource(R.string.pause_supporting_text),
            confirmText = stringResource(R.string.pause_action),
            dismissText = stringResource(R.string.keep_going_action),
            onConfirm = state::confirmPause,
            onDismiss = state::dismissDialog,
        )
        AppDialog.Reset -> {
            KeepScreenAwake()
            ConfirmationDialog(
                title = stringResource(R.string.reset_title),
                text = stringResource(R.string.reset_supporting_text),
                confirmText = stringResource(R.string.reset_confirm_action),
                dismissText = stringResource(R.string.keep_progress_action),
                onConfirm = state::confirmReset,
                onDismiss = state::dismissDialog,
                destructive = true,
            )
        }
        is AppDialog.Replace -> {
            KeepScreenAwake()
            ConfirmationDialog(
                title = stringResource(R.string.replace_title),
                text = stringResource(R.string.replace_supporting_text),
                confirmText = stringResource(R.string.start_new_routine_action),
                dismissText = stringResource(R.string.keep_current_routine_action),
                onConfirm = { state.confirmReplacement(dialog.routineId) },
                onDismiss = state::keepCurrentRoutine,
                destructive = true,
            )
        }
        is AppDialog.DeleteRoutine -> ConfirmationDialog(
            title = stringResource(R.string.delete_routine_title),
            text = stringResource(R.string.delete_routine_supporting_text),
            confirmText = stringResource(R.string.delete_routine_confirm_action),
            dismissText = stringResource(R.string.keep_routine_action),
            onConfirm = { state.confirmDeleteRoutine(dialog.routineId) },
            onDismiss = state::dismissDialog,
        )
    }
}

@Composable
internal fun KeepScreenAwake() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}

@Composable
internal fun ScreenSurface(
    modifier: Modifier = Modifier,
    applySystemBarsPadding: Boolean = true,
    applyTopContentPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemBarsModifier = if (applySystemBarsPadding) {
        Modifier
            .statusBarsPadding()
            .navigationBarsPadding()
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(systemBarsModifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(
                    start = 24.dp,
                    top = if (applyTopContentPadding) 28.dp else 0.dp,
                    end = 24.dp,
                    bottom = 28.dp,
                ),
        ) {
            content()
        }
    }
}
