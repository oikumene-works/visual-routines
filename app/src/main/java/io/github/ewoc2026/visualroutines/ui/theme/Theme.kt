package io.github.ewoc2026.visualroutines.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC0EFE7),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF655E45),
    secondaryContainer = Color(0xFFECE2BD),
    tertiary = Color(0xFF7A4E58),
    background = Color(0xFFFCFCF8),
    surface = Color(0xFFFCFCF8),
    surfaceVariant = Color(0xFFE3E3DD),
    outline = Color(0xFF747873),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D2CA),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    secondary = Color(0xFFD0C6A2),
    tertiary = Color(0xFFEAB8C4),
    background = Color(0xFF1A1C1B),
    surface = Color(0xFF1A1C1B),
)

private val BlackColors = DarkColors.copy(
    background = Color.Black,
    onBackground = Color(0xFFE6E6E6),
    surface = Color.Black,
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF222222),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFF666666),
)

/** Rendered palettes only; this is not a persisted appearance preference. */
internal enum class ThemePalette { Light, Dark, Black }

/** Shares the same palette values between the UI and contrast checks. */
internal fun colorsForPalette(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.Light -> LightColors
    ThemePalette.Dark -> DarkColors
    ThemePalette.Black -> BlackColors
}

/** A transient override supports previews/tests; null retains existing system following. */
@Composable
internal fun VisualRoutinesTheme(
    palette: ThemePalette? = null,
    content: @Composable () -> Unit,
) {
    val resolved = palette ?: if (isSystemInDarkTheme()) ThemePalette.Dark else ThemePalette.Light
    // Keep base black surfaces black rather than adding Material's elevation tint.
    CompositionLocalProvider(LocalTonalElevationEnabled provides (resolved != ThemePalette.Black)) {
        MaterialTheme(
            colorScheme = colorsForPalette(resolved),
            typography = Typography(),
            content = content,
        )
    }
}
