package io.github.ewoc2026.visualroutines

import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceResolutionTest {
    @Test fun allTwelveCombinationsMatchTheAcceptedTable() {
        // Explicit expected rows keep this independent of the resolver's branching.
        val rows = listOf(
            Row(AppearanceMode.System, DarkThemeStyle.Standard, ThemePalette.Light, ThemePalette.Dark),
            Row(AppearanceMode.System, DarkThemeStyle.Black, ThemePalette.Light, ThemePalette.Black),
            Row(AppearanceMode.Light, DarkThemeStyle.Standard, ThemePalette.Light, ThemePalette.Light),
            Row(AppearanceMode.Light, DarkThemeStyle.Black, ThemePalette.Light, ThemePalette.Light),
            Row(AppearanceMode.Dark, DarkThemeStyle.Standard, ThemePalette.Dark, ThemePalette.Dark),
            Row(AppearanceMode.Dark, DarkThemeStyle.Black, ThemePalette.Black, ThemePalette.Black),
        )
        rows.forEach { row ->
            assertEquals("$row, device light", row.light, resolveThemePalette(row.mode, row.style, false))
            assertEquals("$row, device dark", row.dark, resolveThemePalette(row.mode, row.style, true))
        }
    }

    @Test fun systemFollowingHasNoRememberedForcedMode() {
        val selections = listOf(
            Triple(AppearanceMode.System, false, ThemePalette.Light),
            Triple(AppearanceMode.System, true, ThemePalette.Black),
            Triple(AppearanceMode.Light, true, ThemePalette.Light),
            Triple(AppearanceMode.Dark, false, ThemePalette.Black),
            Triple(AppearanceMode.System, false, ThemePalette.Light),
            Triple(AppearanceMode.System, true, ThemePalette.Black),
            Triple(AppearanceMode.System, false, ThemePalette.Light),
        )
        selections.forEach { (mode, deviceDark, expected) ->
            assertEquals(expected, resolveThemePalette(mode, DarkThemeStyle.Black, deviceDark))
        }
    }

    private data class Row(
        val mode: AppearanceMode,
        val style: DarkThemeStyle,
        val light: ThemePalette,
        val dark: ThemePalette,
    )
}
