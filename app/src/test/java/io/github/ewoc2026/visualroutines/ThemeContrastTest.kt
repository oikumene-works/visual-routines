package io.github.ewoc2026.visualroutines

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checks foreground/background pairs used by Black, including elevated dialogs and actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeContrastTest {
    @Test fun blackTextAndControlBoundariesHaveSufficientContrast() {
        val colors = colorsForPalette(ThemePalette.Black)
        fun contrast(label: String, foreground: Color, background: Color, minimum: Double = 4.5) {
            val ratio = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
            assertTrue("$label contrast $ratio is below $minimum", ratio >= minimum)
        }
        contrast("Background text", colors.onBackground, colors.background)
        contrast("Surface text", colors.onSurface, colors.surface)
        contrast("Secondary text", colors.onSurfaceVariant, colors.surface)
        contrast("Dialog text", colors.onSurface, colors.surfaceContainerHigh)
        contrast("Field labels", colors.onSurfaceVariant, colors.surfaceContainerHighest)
        contrast("Primary action", colors.onPrimary, colors.primary)
        contrast("Active routine", colors.onPrimaryContainer, colors.primaryContainer)
        contrast("Text actions", colors.primary, colors.surface)
        contrast("Error text", colors.error, colors.surface)
        contrast("Error action", colors.onError, colors.error)
        contrast("Outline", colors.outline, colors.surface, 3.0)
        contrast("Card outline", colors.outlineVariant, colors.surface, 3.0)
    }
}
