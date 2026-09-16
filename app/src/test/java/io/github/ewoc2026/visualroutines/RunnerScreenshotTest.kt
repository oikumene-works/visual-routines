package io.github.ewoc2026.visualroutines

import androidx.compose.runtime.CompositionLocalProvider
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
class RunnerScreenshotTest {
    @Test
    @Config(qualifiers = "w360dp-h720dp-mdpi")
    fun compactPhoneStacked() {
        captureRunner(fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "w900dp-h430dp-mdpi")
    fun shortLandscapeSideBySide() {
        captureRunner(fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun genericTabletStacked() {
        captureRunner(fontScale = 1f)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phoneAtFontScaleTwo() {
        captureRunner(fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w832dp-h384dp-mdpi")
    fun shortLandscapeAtFontScaleTwo() {
        captureRunner(fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-mdpi")
    fun textOnlyMissingReferenceFallback() {
        captureRunner(
            fontScale = 1f,
            instruction = "Keep following this complete instruction even when the optional image is unavailable.",
            supportingInstruction = "No placeholder or empty image area should appear.",
            image = null,
        )
    }

    private fun captureRunner(
        fontScale: Float,
        instruction: String = "Take the duvet out of the duvet cover without losing the corners.",
        supportingInstruction: String = "Put the old cover with the other laundry.",
        image: RunnerImagePresentation? = RunnerImagePresentation(
            drawableResourceId = R.drawable.bed_linen_remove_duvet_cover,
            contentDescription = "Two hands separate a white duvet from a gray duvet cover on a bed.",
        ),
    ) {
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                VisualRoutinesTheme {
                    RunnerScreenContent(
                        routineName = "Change bed linen",
                        stepPositionText = "Step 2 of 9",
                        stepStatusText = null,
                        instruction = instruction,
                        supportingInstruction = supportingInstruction,
                        image = image,
                        canGoPrevious = true,
                        onPause = {},
                        onPrevious = {},
                        onSkip = {},
                        onDone = {},
                    )
                }
            }
        }
    }
}
