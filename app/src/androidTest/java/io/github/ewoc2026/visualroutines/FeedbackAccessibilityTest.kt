package io.github.ewoc2026.visualroutines

import android.app.UiAutomation
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the actual feedback controls without opening an external app or changing the clipboard. */
@RunWith(AndroidJUnit4::class)
class FeedbackAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun feedbackActionsRemainNamedAndReachable() {
        compose.enableAccessibilityChecks()
        val state = VisualRoutinesState(FeedbackEmptySessionStore)
        compose.setContent { VisualRoutinesTheme { SettingsScreen(state) } }
        compose.onNodeWithText(FEEDBACK_EMAIL).performScrollTo().assertIsDisplayed()
        for (label in listOf("Send feedback", "Copy email address")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
            compose.onRoot().tryPerformAccessibilityChecks()
        }
    }
    /** Explicit opt-in listening run; preserves the clipboard without retaining its contents. */
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun pacedFeedbackListen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("human_listen") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains("talkback", true))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        compose.setContent { VisualRoutinesTheme { SettingsScreen(VisualRoutinesState(FeedbackEmptySessionStore)) } }
        compose.onNodeWithText("Send feedback").performScrollTo()
        Thread.sleep(5_000)
        fun focus(label: String) {
            automation.clearCache()
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                node ?: return null
                if (node.text?.toString() == label) return node
                repeat(node.childCount) { find(node.getChild(it))?.let { found -> return found } }
                return null
            }
            val node = requireNotNull(find(automation.rootInActiveWindow))
            val target = generateSequence(node) { it.parent }.take(4).firstOrNull { it.isClickable } ?: node
            automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        }
        focus("Send feedback")
        Thread.sleep(5_000)
        compose.onNodeWithText("Copy email address").performScrollTo()
        focus("Copy email address")
        Thread.sleep(5_000)
        val previousClip = clipboard.primaryClip
        try {
            compose.onNodeWithText("Copy email address").performClick()
            compose.onNodeWithText("Email address copied.").performScrollTo().assertIsDisplayed()
            Thread.sleep(7_000)
        } finally {
            if (previousClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previousClip)
        }
    }

}

private object FeedbackEmptySessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null
    override fun save(session: ActiveSession) = Unit
    override fun clear() = Unit
}
