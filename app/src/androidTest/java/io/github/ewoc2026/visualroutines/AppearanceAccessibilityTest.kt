package io.github.ewoc2026.visualroutines

import android.app.UiAutomation
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Technical semantics/ATF coverage; actual TalkBack listening is a separate device step. */
@RunWith(AndroidJUnit4::class)
class AppearanceAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothGroupsRemainAccessibleInLightAndBlack() {
        compose.enableAccessibilityChecks()
        val state = VisualRoutinesState(AppearanceEmptySessionStore).apply { openSettings() }
        compose.setContent { VisualRoutinesApp(state) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected), useUnmergedTree = true).assertCountEquals(5)
        compose.onNodeWithTag("dark-style-black").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("appearance-light").performScrollTo().performClick()
        compose.onNodeWithTag("dark-style-black").performScrollTo().assertIsNotEnabled().assertIsSelected()
        compose.onNodeWithText("Choose System or Dark to change this.").performScrollTo().assertIsDisplayed()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        for (tag in listOf("appearance-system", "appearance-light", "appearance-dark", "dark-style-standard", "dark-style-black")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
            compose.onRoot().tryPerformAccessibilityChecks()
        }
        compose.onNodeWithTag("dark-style-black").assertIsSelected().assertIsEnabled()
        compose.onNodeWithText("Copy email address").performScrollTo().assertIsDisplayed()
    }

    /** One explicitly cued listening stage; assertions establish focus, not heard speech. */
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun pacedAppearanceListen() {
        val stage = InstrumentationRegistry.getArguments().getString("appearance_listen")
        assumeTrue(stage in listOf("calibration", "groups", "transition"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(Settings.Secure.getString(instrumentation.targetContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains("talkback", true))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val state = VisualRoutinesState(AppearanceEmptySessionStore).apply {
            updateDarkThemeStyle(DarkThemeStyle.Black)
            updateAppearanceMode(AppearanceMode.Light)
            openSettings()
        }
        compose.setContent { VisualRoutinesApp(state) }
        Thread.sleep(5_000)

        fun focusedLabel(label: String): Boolean {
            automation.clearCache()
            return automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?.appearanceDescendants()?.any { it.appearanceLabelMatches(label) } == true
        }
        fun focus(label: String, option: Boolean = true): AccessibilityNodeInfo {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            var target: AccessibilityNodeInfo? = null
            compose.waitUntil(5_000) {
                automation.clearCache()
                val text = automation.rootInActiveWindow?.appearanceDescendants()?.firstOrNull {
                    it.isVisibleToUser && it.appearanceLabelMatches(label)
                }
                target = if (option) generateSequence(text) { it.parent }.take(4)
                    .firstOrNull { it.isCheckable } else text
                target != null
            }
            automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            compose.waitUntil(5_000) { focusedLabel(label) }
            Thread.sleep(4_500)
            return requireNotNull(target)
        }

        when (stage) {
            "calibration" -> focus("Light")
            "groups" -> {
                focus("Appearance", option = false)
                for (label in listOf("System", "Light", "Dark")) focus(label)
                focus("Dark theme style", option = false)
                for (label in listOf("Standard", "Black (AMOLED)")) focus(label)
                focus("Choose System or Dark to change this.", option = false)
            }
            "transition" -> {
                val system = focus("System")
                assertTrue(system.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.onNodeWithTag("appearance-system").assertIsSelected()
                compose.onNodeWithTag("dark-style-black").assertIsEnabled().assertIsSelected()
                compose.waitUntil(5_000) { focusedLabel("System") }
                Thread.sleep(6_000)
                val standard = focus("Standard")
                assertTrue(standard.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.onNodeWithTag("dark-style-standard").assertIsSelected()
                compose.waitUntil(5_000) { focusedLabel("Standard") }
                Thread.sleep(6_000)
                val black = focus("Black (AMOLED)")
                assertTrue(black.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.onNodeWithTag("dark-style-black").assertIsSelected()
                compose.waitUntil(5_000) { focusedLabel("Black (AMOLED)") }
                Thread.sleep(6_000)
                val dark = focus("Dark")
                assertTrue(dark.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                compose.onNodeWithTag("appearance-dark").assertIsSelected()
                compose.waitUntil(5_000) { focusedLabel("Dark") }
                Thread.sleep(6_000)
            }
        }
    }
}

private fun AccessibilityNodeInfo.appearanceLabelMatches(label: String): Boolean =
    text?.toString()?.let { it == label || it.startsWith("$label\n") } == true

private fun AccessibilityNodeInfo.appearanceDescendants(): List<AccessibilityNodeInfo> = buildList {
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this@appearanceDescendants)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        add(node)
        repeat(node.childCount) { node.getChild(it)?.let(pending::addLast) }
    }
}

private object AppearanceEmptySessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null
    override fun save(session: ActiveSession) = Unit
    override fun clear() = Unit
}
