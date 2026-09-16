package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PrivacyPolicyScreenshotTest {
    @Test
    @Config(qualifiers = "w360dp-h720dp-mdpi")
    fun compactPhone() {
        capturePolicy(fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun genericTablet() {
        capturePolicy(fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "w832dp-h384dp-mdpi")
    fun shortLandscapeAtFontScaleTwo() {
        capturePolicy(fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w360dp-h720dp-mdpi")
    fun settingsAtFontScaleTwo() {
        captureScreen(fontScale = 2f) { state -> SettingsScreen(state) }
    }

    private fun capturePolicy(fontScale: Float) {
        captureScreen(fontScale = fontScale) { state -> PrivacyPolicyScreen(state) }
    }

    private fun captureScreen(
        fontScale: Float,
        content: @Composable (VisualRoutinesState) -> Unit,
    ) {
        val state = VisualRoutinesState(PrivacyPolicyScreenshotSessionStore)
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                VisualRoutinesTheme {
                    content(state)
                }
            }
        }
    }
}

private object PrivacyPolicyScreenshotSessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null

    override fun save(session: ActiveSession) = Unit

    override fun clear() = Unit
}
