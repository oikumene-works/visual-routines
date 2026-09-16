package io.github.ewoc2026.visualroutines.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
internal fun VisualRoutinesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
