package io.github.ewoc2026.visualroutines

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Persists only the system-owned splash theme. Resource qualifiers keep System
 * following the device even when the process is stopped; no uiMode override or
 * Activity recreation is needed when the user changes appearance.
 */
internal fun Activity.persistLaunchTheme(settings: HomeSettings) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        splashScreen.setSplashScreenTheme(launchThemeFor(settings))
    }
}

/** These resource names are persisted by Android and must remain stable across updates. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun launchThemeFor(settings: HomeSettings): Int = when (settings.appearanceMode) {
    AppearanceMode.System -> if (settings.darkThemeStyle == DarkThemeStyle.Black) {
        R.style.Theme_VisualRoutines_Launch_SystemBlack
    } else {
        R.style.Theme_VisualRoutines
    }
    AppearanceMode.Light -> R.style.Theme_VisualRoutines_Launch_Light
    AppearanceMode.Dark -> if (settings.darkThemeStyle == DarkThemeStyle.Black) {
        R.style.Theme_VisualRoutines_Launch_Black
    } else {
        R.style.Theme_VisualRoutines_Launch_Dark
    }
}
