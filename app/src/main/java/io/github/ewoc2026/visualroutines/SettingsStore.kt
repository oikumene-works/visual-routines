package io.github.ewoc2026.visualroutines

import android.content.Context
import androidx.core.content.edit

internal data class HomeSettings(
    val showExampleRoutines: Boolean = true,
    val allowRoutineEditing: Boolean = true,
    val appearanceMode: AppearanceMode = AppearanceMode.System,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.Standard,
)

internal interface SettingsStore {
    fun load(): HomeSettings

    fun save(settings: HomeSettings)
}

internal class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): HomeSettings {
        // Read raw appearance values so a wrong type cannot crash startup or erase its companion.
        val stored = preferences.all
        return HomeSettings(
            showExampleRoutines = preferences.getBoolean(KEY_SHOW_EXAMPLE_ROUTINES, true),
            allowRoutineEditing = preferences.getBoolean(KEY_ALLOW_ROUTINE_EDITING, true),
            appearanceMode = AppearanceMode.fromStoredValue(stored[KEY_APPEARANCE_MODE]),
            darkThemeStyle = DarkThemeStyle.fromStoredValue(stored[KEY_DARK_THEME_STYLE]),
        )
    }

    override fun save(settings: HomeSettings) {
        preferences.edit {
            putBoolean(KEY_SHOW_EXAMPLE_ROUTINES, settings.showExampleRoutines)
            putBoolean(KEY_ALLOW_ROUTINE_EDITING, settings.allowRoutineEditing)
            putString(KEY_APPEARANCE_MODE, settings.appearanceMode.storageValue)
            putString(KEY_DARK_THEME_STYLE, settings.darkThemeStyle.storageValue)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "home_settings"
        const val KEY_SHOW_EXAMPLE_ROUTINES = "show_example_routines"
        const val KEY_ALLOW_ROUTINE_EDITING = "allow_routine_editing"
        const val KEY_APPEARANCE_MODE = "appearance_mode"
        const val KEY_DARK_THEME_STYLE = "dark_theme_style"
    }
}

internal class InMemorySettingsStore(
    private var settings: HomeSettings = HomeSettings(),
) : SettingsStore {
    override fun load(): HomeSettings = settings

    override fun save(settings: HomeSettings) {
        this.settings = settings
    }
}
