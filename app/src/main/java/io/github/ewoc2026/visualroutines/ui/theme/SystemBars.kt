package io.github.ewoc2026.visualroutines.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.view.WindowCompat

/** Matches the current app window to the rendered palette, without changing system night mode. */
@Composable
internal fun ThemeSystemBars() {
    if (LocalInspectionMode.current) return
    val window = LocalContext.current.activityOrNull()?.window ?: return
    val background = MaterialTheme.colorScheme.background
    SideEffect { applyThemeSystemBars(window, background) }
}

/** Owns app-window colors only; the earlier system splash remains a separate validation boundary. */
@Suppress("DEPRECATION")
internal fun applyThemeSystemBars(window: Window, background: Color) {
    val darkIcons = background.luminance() > 0.5f
    window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    // API 23–25 cannot draw dark navigation icons. Keep their white icons on black.
    window.navigationBarColor = if (Build.VERSION.SDK_INT < 26 && darkIcons) {
        android.graphics.Color.BLACK
    } else {
        android.graphics.Color.TRANSPARENT
    }
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
    if (Build.VERSION.SDK_INT >= 28) {
        window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= 29) {
        // The root surface supplies the background behind both navigation modes.
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.decorView.isForceDarkAllowed = false
    }
}

private tailrec fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activityOrNull()
    else -> null
}
