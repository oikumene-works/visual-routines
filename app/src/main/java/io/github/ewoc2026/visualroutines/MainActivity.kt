package io.github.ewoc2026.visualroutines

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import io.github.ewoc2026.visualroutines.ui.theme.applyThemeSystemBars
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette

/** Hosts the Compose application as the single Android entry point. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val viewModel = ViewModelProvider(
            this,
            VisualRoutinesViewModel.Factory(applicationContext),
        )[VisualRoutinesViewModel::class.java]
        val settings = viewModel.state.homeSettings
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        // The system splash is drawn before this process can read saved preferences.
        // Persist its theme for subsequent launches, and match this window immediately.
        persistLaunchTheme(settings)
        applyThemeSystemBars(window, colorsForPalette(resolveThemePalette(
            settings.appearanceMode, settings.darkThemeStyle, systemDark,
        )).background)
        setContent {
            val currentSettings = viewModel.state.homeSettings
            LaunchedEffect(currentSettings.appearanceMode, currentSettings.darkThemeStyle) {
                persistLaunchTheme(currentSettings)
            }
            VisualRoutinesApp(viewModel.state)
        }
    }
}
