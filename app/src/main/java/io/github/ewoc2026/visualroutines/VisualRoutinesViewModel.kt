package io.github.ewoc2026.visualroutines

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Owns app state across Android configuration changes. */
internal class VisualRoutinesViewModel(
    sessionStore: SessionStore,
    routineRepository: RoutineRepository,
    settingsStore: SettingsStore,
) : ViewModel() {
    val state: VisualRoutinesState = VisualRoutinesState(sessionStore, routineRepository, settingsStore)

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == VisualRoutinesViewModel::class.java)
            val applicationContext = context.applicationContext
            val application = applicationContext as? VisualRoutinesApplication
                ?: error("VisualRoutinesApplication must own process-scoped dependencies.")
            return VisualRoutinesViewModel(
                SharedPreferencesSessionStore(applicationContext),
                application.routineRepository,
                SharedPreferencesSettingsStore(applicationContext),
            ) as T
        }
    }
}
