package io.github.ewoc2026.visualroutines

import io.github.ewoc2026.visualroutines.ui.theme.ThemePalette

/** User intent, stored independently of the device's current light/dark appearance. */
internal enum class AppearanceMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        /** Unknown or wrong-type values default without changing the stored preference. */
        fun fromStoredValue(value: Any?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

/** The retained dark palette choice; fixed Light does not discard it. */
internal enum class DarkThemeStyle(val storageValue: String) {
    Standard("standard"),
    Black("black");

    companion object {
        /** Decodes this field independently of the appearance mode. */
        fun fromStoredValue(value: Any?): DarkThemeStyle =
            entries.firstOrNull { it.storageValue == value } ?: Standard
    }
}

/** Resolves saved intent against actual system appearance without platform side effects. */
internal fun resolveThemePalette(
    appearanceMode: AppearanceMode,
    darkThemeStyle: DarkThemeStyle,
    systemDark: Boolean,
): ThemePalette {
    val dark = when (appearanceMode) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    return when {
        !dark -> ThemePalette.Light
        darkThemeStyle == DarkThemeStyle.Black -> ThemePalette.Black
        else -> ThemePalette.Dark
    }
}
