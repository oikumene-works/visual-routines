package io.github.ewoc2026.visualroutines

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks the real Activity's pre-content window, not the system-owned splash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "notnight")
class MainActivityAppearanceTest {
    @Test fun savedBlackSetsTheInitialWindowWhileTheDeviceIsLight() {
        checkInitialWindow(AppearanceMode.Dark, DarkThemeStyle.Black, ThemePalette.Black)
    }

    @Test @Config(qualifiers = "night")
    fun fixedLightSetsTheInitialWindowWhileTheDeviceIsDark() {
        checkInitialWindow(AppearanceMode.Light, DarkThemeStyle.Black, ThemePalette.Light)
    }

    @Test @Config(qualifiers = "night")
    fun systemUsesTheSavedBlackStyleBeforeContent() {
        checkInitialWindow(AppearanceMode.System, DarkThemeStyle.Black, ThemePalette.Black)
    }

    @Test fun systemLightDoesNotApplyThePreselectedBlackStyle() {
        checkInitialWindow(AppearanceMode.System, DarkThemeStyle.Black, ThemePalette.Light)
    }

    private fun checkInitialWindow(mode: AppearanceMode, style: DarkThemeStyle, expected: ThemePalette) {
        val app = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
        SharedPreferencesSettingsStore(app).save(HomeSettings(appearanceMode = mode, darkThemeStyle = style))
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val window = controller.get().window
            assertEquals(colorsForPalette(expected).background.toArgb(), (window.decorView.background as ColorDrawable).color)
            val icons = WindowCompat.getInsetsController(window, window.decorView)
            assertEquals(expected == ThemePalette.Light, icons.isAppearanceLightStatusBars)
            assertEquals(expected == ThemePalette.Light, icons.isAppearanceLightNavigationBars)
        }
    }
}
