package io.github.ewoc2026.visualroutines

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import io.github.ewoc2026.visualroutines.ui.theme.applyThemeSystemBars
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies window flags in Robolectric, not the pixels drawn by a device's system UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 26, 29, 35])
@Suppress("DEPRECATION")
class ThemeSystemBarsTest {
    @Test fun windowTracksPaletteChangesAndLegacyNavigationRemainsReadable() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val window = controller.get().window
            for (palette in listOf(ThemePalette.Light, ThemePalette.Black, ThemePalette.Dark, ThemePalette.Light)) {
                val background = colorsForPalette(palette).background
                applyThemeSystemBars(window, background)
                val icons = WindowCompat.getInsetsController(window, window.decorView)
                val light = palette == ThemePalette.Light
                assertEquals(background.toArgb(), (window.decorView.background as ColorDrawable).color)
                assertEquals(light, icons.isAppearanceLightStatusBars)
                assertEquals(light && Build.VERSION.SDK_INT >= 26, icons.isAppearanceLightNavigationBars)
                assertEquals(Color.Transparent.toArgb(), window.statusBarColor)
                assertEquals(
                    if (light && Build.VERSION.SDK_INT < 26) Color.Black.toArgb() else Color.Transparent.toArgb(),
                    window.navigationBarColor,
                )
                if (Build.VERSION.SDK_INT >= 29) {
                    assertFalse(window.isNavigationBarContrastEnforced)
                    assertFalse(window.isStatusBarContrastEnforced)
                    assertFalse(window.decorView.isForceDarkAllowed)
                }
            }
        }
    }
}
