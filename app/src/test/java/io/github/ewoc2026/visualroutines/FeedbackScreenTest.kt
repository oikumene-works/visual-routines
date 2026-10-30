package io.github.ewoc2026.visualroutines

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<VisualRoutinesApplication>()

    private fun launch(fontScale: Float = 1f, context: Context = app) {
        val state = VisualRoutinesState(SharedPreferencesSessionStore(app))
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) { VisualRoutinesTheme { SettingsScreen(state) } }
        }
    }

    private fun layout(name: String, fontScale: Float = 1f) {
        launch(fontScale)
        compose.onNodeWithText(FEEDBACK_EMAIL).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send feedback").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Copy email address").performScrollTo().assertIsDisplayed().assertHasClickAction()
        if (System.getenv("VR_CAPTURE_FEEDBACK_PREVIEWS") == "1") {
            val output = File("build/reports/feedback/$name.png")
            output.parentFile!!.mkdirs()
            compose.onRoot().captureRoboImage(output.absolutePath)
        }
    }

    @Test @Config(qualifiers = "w360dp-h720dp-mdpi") fun phonePortrait() = layout("phone-portrait")
    @Test @Config(qualifiers = "w832dp-h384dp-mdpi") fun phoneLandscape() = layout("phone-landscape")
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun tabletPortrait() = layout("tablet-portrait")
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun tabletLandscape() = layout("tablet-landscape")
    @Test @Config(qualifiers = "w360dp-h720dp-mdpi") fun phoneLargeText() = layout("phone-large", 2f)
    @Test @Config(qualifiers = "w832dp-h384dp-mdpi") fun phoneLandscapeLargeText() = layout("phone-landscape-large", 2f)
    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi") fun tabletLargeText() = layout("tablet-large", 2f)
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun tabletLandscapeLargeText() = layout("tablet-landscape-large", 2f)

    @Test fun missingEmailAppKeepsAddressCopyAvailable() {
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        launch(context = context)
        compose.onNodeWithText("Send feedback").performScrollTo().performClick()
        compose.onNodeWithText("No email app is available. You can copy the address and use it elsewhere.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Copy email address").performScrollTo().performClick()
        compose.onNodeWithText("Email address copied.").performScrollTo().assertIsDisplayed()
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(FEEDBACK_EMAIL, clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(1, clipboard.primaryClip!!.itemCount)
    }
}
