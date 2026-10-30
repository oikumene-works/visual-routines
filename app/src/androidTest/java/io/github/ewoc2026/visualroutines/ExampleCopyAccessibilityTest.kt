package io.github.ewoc2026.visualroutines

import android.annotation.TargetApi
import android.app.UiAutomation
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the new management and chooser targets without reading or writing product data. */
@RunWith(AndroidJUnit4::class)
class ExampleCopyAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exampleChoiceHasNamedReachableActions() {
        val state = VisualRoutinesState(ExampleCopyEmptySessionStore)
        state.openManageRoutines()
        compose.enableAccessibilityChecks()
        compose.setContent {
            VisualRoutinesTheme {
                when (state.screen) {
                    AppScreen.ChooseExample -> ChooseExampleScreen(state)
                    else -> ManageRoutinesScreen(state)
                }
            }
        }
        compose.onNodeWithText("Create a routine from scratch").performScrollTo()
            .assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Create a routine from an example").performScrollTo()
            .assertIsDisplayed().assertHasClickAction()
        compose.onRoot().tryPerformAccessibilityChecks()
        compose.onNodeWithText("Create a routine from an example").performClick()
        compose.onNodeWithText("Choose an example").assertIsDisplayed()
        for (example in BundledRoutines.all) {
            compose.onNodeWithText(example.name).performScrollTo()
                .assertIsDisplayed().assertHasClickAction()
            compose.onRoot().tryPerformAccessibilityChecks()
        }
        compose.onAllNodes(
            SemanticsMatcher("has an empty content description") { node ->
                SemanticsProperties.ContentDescription in node.config &&
                    node.config[SemanticsProperties.ContentDescription].any(String::isBlank)
            },
            useUnmergedTree = true,
        ).assertCountEquals(0)
        compose.onNodeWithText("Chickpea salad bowl").performClick()
        assertEquals("Copy of Chickpea salad bowl", (state.screen as AppScreen.CreateRoutine).draft.title)
    }

    /** Run one announced transition at a time; a human report is required for spoken acceptance. */
    @Test
    @TargetApi(Build.VERSION_CODES.N)
    fun pacedHumanListenTransition() {
        val transition = InstrumentationRegistry.getArguments().getString("human_transition")
        assumeTrue("Requires an explicit listening transition", transition in listOf("chooser", "editor"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val services = Settings.Secure.getString(
            instrumentation.targetContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        assumeTrue("TalkBack must be enabled", services.contains("talkback", ignoreCase = true))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val state = VisualRoutinesState(ExampleCopyEmptySessionStore)
        if (transition == "chooser") state.openManageRoutines() else state.openExampleChooser()
        compose.setContent { VisualRoutinesApp(state) }
        val action = if (transition == "chooser") "Create a routine from an example" else "Prepare for exercise"
        compose.onNodeWithText(action).performScrollTo().assertIsDisplayed()
        // Let initial window speech settle before deliberately focusing the action.
        Thread.sleep(5_000)
        val node = focus(automation, action, clickable = true)
        Thread.sleep(5_000)
        assertTrue("Could not activate $action", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.onNodeWithText(if (transition == "chooser") "Choose an example" else "Create routine")
            .assertIsDisplayed()
        Thread.sleep(7_000)
        if (transition == "editor") {
            focus(automation, "Copy of Prepare for exercise")
            Thread.sleep(5_000)
            focus(automation, "Change into workout clothes.")
            Thread.sleep(5_000)
        }
    }

    private fun focus(automation: UiAutomation, label: String, clickable: Boolean = false): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        compose.waitUntil("Accessible node for $label", 5_000) {
            automation.clearCache()
            val labelled = automation.rootInActiveWindow?.descendants()?.firstOrNull {
                it.isVisibleToUser && it.hasLabel(label)
            }
            found = if (clickable) {
                generateSequence(labelled) { it.parent }.take(4).firstOrNull { it.isClickable }
            } else labelled
            found != null
        }
        val node = requireNotNull(found)
        automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        assertTrue("Could not focus $label", node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        compose.waitUntil("Focus settles on $label", 5_000) {
            automation.clearCache()
            automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let {
                it.descendants().any { descendant -> descendant.hasLabel(label) }
            } == true
        }
        return node
    }
}

private fun AccessibilityNodeInfo.hasLabel(label: String): Boolean =
    text?.contains(label) == true || contentDescription?.contains(label) == true

private fun AccessibilityNodeInfo.descendants(): List<AccessibilityNodeInfo> = buildList {
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this@descendants)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        add(node)
        repeat(node.childCount) { node.getChild(it)?.let(pending::addLast) }
    }
}

private object ExampleCopyEmptySessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null
    override fun save(session: ActiveSession) = Unit
    override fun clear() = Unit
}
