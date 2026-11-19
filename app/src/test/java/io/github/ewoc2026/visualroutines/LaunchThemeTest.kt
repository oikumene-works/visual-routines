package io.github.ewoc2026.visualroutines

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import io.github.ewoc2026.visualroutines.ui.theme.colorsForPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Resolves the actual Android splash resources against both device configurations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchThemeTest {
    @Test fun allSavedChoicesMatchTheContentAndSystemBarContrast() {
        val app = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
        for (dark in listOf(false, true)) {
            val configuration = Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            for (mode in AppearanceMode.entries) for (style in DarkThemeStyle.entries) {
                val settings = HomeSettings(appearanceMode = mode, darkThemeStyle = style)
                val context = ContextThemeWrapper(app.createConfigurationContext(configuration), launchThemeFor(settings))
                val expected = colorsForPalette(resolveThemePalette(mode, style, dark)).background.toArgb()
                val light = mode == AppearanceMode.Light || mode == AppearanceMode.System && !dark
                val attrs = context.obtainStyledAttributes(intArrayOf(
                    android.R.attr.windowSplashScreenBackground,
                    android.R.attr.windowBackground,
                    android.R.attr.statusBarColor,
                    android.R.attr.navigationBarColor,
                    android.R.attr.windowLightStatusBar,
                    android.R.attr.windowLightNavigationBar,
                    android.R.attr.windowDisablePreview,
                ))
                try {
                    for (index in 0..3) assertEquals("$mode/$style/deviceDark=$dark attribute $index", expected, attrs.getColor(index, 1))
                    assertEquals(light, attrs.getBoolean(4, !light))
                    assertEquals(light, attrs.getBoolean(5, !light))
                    assertFalse(attrs.getBoolean(6, true))
                } finally {
                    attrs.recycle()
                }
            }
        }
    }

    @Test @Config(sdk = [23, 29])
    fun olderAndroidDoesNotShowAnIncorrectStartingPreview() {
        val app = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()
        SharedPreferencesSettingsStore(app).save(HomeSettings(
            appearanceMode = AppearanceMode.Dark,
            darkThemeStyle = DarkThemeStyle.Black,
        ))
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            assertEquals(Color.BLACK, (controller.get().window.decorView.background as ColorDrawable).color)
            val attrs = controller.get().obtainStyledAttributes(intArrayOf(android.R.attr.windowDisablePreview))
            try { assertTrue(attrs.getBoolean(0, false)) } finally { attrs.recycle() }
        }
    }
}
