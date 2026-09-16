package io.github.ewoc2026.visualroutines

import android.annotation.TargetApi
import android.app.UiAutomation
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import java.io.File
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunnerAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun completeCopyNamesTheRoutineAndActionsRemainDistinct() {
        val state = VisualRoutinesState(EmptyRunnerSessionStore)
        val routine = state.routines.first()
        composeTestRule.setContent {
            VisualRoutinesTheme {
                CompleteScreen(state = state, routine = routine)
            }
        }

        composeTestRule
            .onNodeWithText("Routine finished")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("You reached the end of “${routine.name}”.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Return to routine list")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithText("Repeat this routine")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun informativeImageHasOneSeparateDescriptionAfterCompleteText() {
        composeTestRule.enableAccessibilityChecks()
        val description = "Two hands separate a white duvet from a gray duvet cover on a bed."
        setRunnerBody(
            image = RunnerImagePresentation(
                drawableResourceId = R.drawable.bed_linen_remove_duvet_cover,
                contentDescription = description,
            ),
        )

        composeTestRule.onNodeWithText("Step 2 of 9").assertIsDisplayed()
        composeTestRule.onNodeWithText("Take the duvet out of the duvet cover.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Put the old cover with the other laundry.").assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasContentDescriptionExactly(description), useUnmergedTree = true)
            .assertCountEquals(1)
        val imageNode = composeTestRule
            .onNodeWithTag("runner-step-image", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(SemanticsProperties.ContentDescription in imageNode.config)
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun redundantImageIsRenderedWithoutAContentDescription() {
        setRunnerBody(
            image = RunnerImagePresentation(
                drawableResourceId = R.drawable.bed_linen_finish_with_bedspread,
                contentDescription = null,
            ),
        )

        val imageNode = composeTestRule
            .onNodeWithTag("runner-step-image", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertFalse(SemanticsProperties.ContentDescription in imageNode.config)
        composeTestRule.onAllNodes(
            SemanticsMatcher("has a generic or blank image label") { node ->
                if (SemanticsProperties.ContentDescription in node.config) {
                    node.config[SemanticsProperties.ContentDescription].any { description ->
                        description.isBlank() || description.equals("image", ignoreCase = true)
                    }
                } else {
                    false
                }
            },
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun missingImageKeepsACompleteTextOnlyStepWithoutAnEmptyImageNode() {
        setRunnerBody(image = null)

        composeTestRule.onNodeWithTag("runner-layout-stacked").fetchSemanticsNode()
        composeTestRule.onNodeWithText("Take the duvet out of the duvet cover.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Put the old cover with the other laundry.").assertIsDisplayed()
        composeTestRule
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "runner-step-image"),
                useUnmergedTree = true,
            )
            .assertCountEquals(0)
    }

    @Test
    fun wideShortModeKeepsTheTextColumnAsATraversalGroup() {
        setRunnerBody(
            image = RunnerImagePresentation(
                drawableResourceId = R.drawable.bed_linen_remove_sheet,
                contentDescription = "Two hands pull a gray fitted sheet away from a mattress corner.",
            ),
            modifier = Modifier.requiredSize(width = 900.dp, height = 500.dp),
        )

        composeTestRule.onNodeWithTag("runner-layout-side-by-side").fetchSemanticsNode()
        val textColumn = composeTestRule
            .onNodeWithTag("runner-text-column", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(textColumn.config[SemanticsProperties.IsTraversalGroup])
    }

    @Test
    fun finalImageCanScrollIntoViewWhileActionsStayReachable() {
        val description = "Two hands guide a duvet into its cover."
        composeTestRule.setContent {
            VisualRoutinesTheme {
                RunnerScreenContent(
                    routineName = "Runner image validation",
                    stepPositionText = "Step 6 of 6",
                    stepStatusText = null,
                    instruction = "Read this deliberately long instruction from beginning to end, scroll until its final words remain fully visible, and confirm that the stable actions never cover any part of the text.",
                    supportingInstruction = "This is the final supporting line before the optional image.",
                    image = RunnerImagePresentation(
                        drawableResourceId = R.drawable.bed_linen_insert_duvet_cover,
                        contentDescription = description,
                    ),
                    canGoPrevious = true,
                    onPause = {},
                    onPrevious = {},
                    onSkip = {},
                    onDone = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("runner-step-image", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Previous").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    @TargetApi(Build.VERSION_CODES.N)
    fun pacedHumanListenTraversal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val transition = InstrumentationRegistry.getArguments().getString("human_transition")
        val expectedLabels = when (transition) {
            "informative" -> listOf(
                "Step 1 of 6",
                "Take the duvet out of the duvet cover.",
                "Put the old cover with the other laundry.",
                "Two hands separate a white duvet from a gray duvet cover on a bed.",
                "Previous",
            )
            "redundant" -> listOf(
                "Step 3 of 6",
                "Finish the bed with the bedspread.",
                "Previous",
            )
            else -> error("Unknown runner human-listen transition: $transition")
        }
        setPacedRunnerScreen(requireNotNull(transition))
        composeTestRule.onNodeWithTag("runner-layout-side-by-side").fetchSemanticsNode()
        composeTestRule.waitForIdle()

        val uiAutomation = instrumentation.runnerTalkBackUiAutomation()
        val progressFile = File(
            instrumentation.targetContext.cacheDir,
            RUNNER_HUMAN_LISTEN_PROGRESS_FILE,
        )
        val progressDirectory = requireNotNull(progressFile.parentFile)
        check(progressDirectory.mkdirs() || progressDirectory.isDirectory) {
            "Could not create the runner human-listen progress directory"
        }
        val observedLabels = mutableListOf<String>()
        var traversalCompleted = false
        try {
            progressFile.delete()
            Thread.sleep(4_000)
            val initialNode = composeTestRule.waitForRunnerAccessibilityNode(
                uiAutomation = uiAutomation,
                label = expectedLabels.first(),
            )
            uiAutomation.runnerAccessibilityFocus()?.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS.id,
            )
            assertTrue(
                "Could not focus ${expectedLabels.first()}",
                initialNode.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.id,
                ),
            )
            composeTestRule.waitUntil("TalkBack focuses ${expectedLabels.first()}", 2_000) {
                uiAutomation.runnerAccessibilityFocus()?.runnerAccessibilityLabel() ==
                    expectedLabels.first()
            }
            observedLabels += expectedLabels.first()
            progressFile.writeText("0:${expectedLabels.first()}")

            expectedLabels.drop(1).forEachIndexed { index, expectedLabel ->
                composeTestRule.waitUntil("TalkBack focuses $expectedLabel", 12_000) {
                    uiAutomation.runnerAccessibilityFocus()?.runnerAccessibilityLabel() == expectedLabel
                }
                val actualLabel = requireNotNull(
                    uiAutomation.runnerAccessibilityFocus()?.runnerAccessibilityLabel(),
                )
                assertEquals(expectedLabel, actualLabel)
                observedLabels += actualLabel
                progressFile.writeText("${index + 1}:$actualLabel")
            }
            Thread.sleep(3_500)
            traversalCompleted = true
        } finally {
            progressFile.delete()
            instrumentation.sendStatus(
                2,
                Bundle().apply {
                    putString("runner_human_listen_transition", observedLabels.joinToString(" -> "))
                    if (!traversalCompleted) {
                        putString("runner_talkback_tree_after", uiAutomation.runnerTreeSummary())
                    }
                },
            )
        }
    }

    private fun setRunnerBody(
        image: RunnerImagePresentation?,
        modifier: Modifier = Modifier.fillMaxSize(),
    ) {
        composeTestRule.setContent {
            VisualRoutinesTheme {
                RunnerBody(
                    stepPositionText = "Step 2 of 9",
                    stepStatusText = null,
                    instruction = "Take the duvet out of the duvet cover.",
                    supportingInstruction = "Put the old cover with the other laundry.",
                    image = image,
                    modifier = modifier,
                )
            }
        }
    }

    private fun setPacedRunnerScreen(transition: String) {
        val isInformative = transition == "informative"
        composeTestRule.setContent {
            VisualRoutinesTheme {
                RunnerScreenContent(
                    routineName = "Runner image validation",
                    stepPositionText = if (isInformative) "Step 1 of 6" else "Step 3 of 6",
                    stepStatusText = null,
                    instruction = if (isInformative) {
                        "Take the duvet out of the duvet cover."
                    } else {
                        "Finish the bed with the bedspread."
                    },
                    supportingInstruction = if (isInformative) {
                        "Put the old cover with the other laundry."
                    } else {
                        null
                    },
                    image = RunnerImagePresentation(
                        drawableResourceId = if (isInformative) {
                            R.drawable.bed_linen_remove_duvet_cover
                        } else {
                            R.drawable.bed_linen_finish_with_bedspread
                        },
                        contentDescription = if (isInformative) {
                            "Two hands separate a white duvet from a gray duvet cover on a bed."
                        } else {
                            null
                        },
                    ),
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

private object EmptyRunnerSessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null

    override fun save(session: ActiveSession) = Unit

    override fun clear() = Unit
}

@TargetApi(Build.VERSION_CODES.N)
private fun android.app.Instrumentation.runnerTalkBackUiAutomation(): UiAutomation {
    val enabledServices = Settings.Secure.getString(
        targetContext.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    assumeTrue(
        "TalkBack must be enabled for the runner human-listen traversal",
        enabledServices.contains("talkback", ignoreCase = true),
    )
    return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
}

private fun ComposeContentTestRule.waitForRunnerAccessibilityNode(
    uiAutomation: UiAutomation,
    label: String,
): AccessibilityNodeInfo {
    var result: AccessibilityNodeInfo? = null
    waitUntil("One accessibility node is labelled $label", 5_000) {
        uiAutomation.clearCache()
        uiAutomation.rootInActiveWindow
            ?.runnerDescendants()
            ?.filter { node -> node.runnerSpokenLabel() == label }
            ?.singleOrNull()
            ?.let { node ->
                result = node
                true
            } ?: false
    }
    return requireNotNull(result)
}

private fun UiAutomation.runnerAccessibilityFocus(): AccessibilityNodeInfo? {
    clearCache()
    return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
}

private fun AccessibilityNodeInfo.runnerSpokenLabel(): String? {
    return text?.toString()?.takeIf(String::isNotBlank)
        ?: contentDescription?.toString()?.takeIf(String::isNotBlank)
}

private fun AccessibilityNodeInfo.runnerAccessibilityLabel(): String? {
    return runnerSpokenLabel()
        ?: runnerDescendants().asSequence().drop(1)
            .mapNotNull(AccessibilityNodeInfo::runnerSpokenLabel)
            .firstOrNull()
}

private fun AccessibilityNodeInfo.runnerDescendants(): List<AccessibilityNodeInfo> {
    val result = mutableListOf<AccessibilityNodeInfo>()
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        result += node
        repeat(node.childCount) { index ->
            node.getChild(index)?.let(pending::addLast)
        }
    }
    return result
}

private fun UiAutomation.runnerTreeSummary(): String {
    clearCache()
    val root = rootInActiveWindow ?: return "No active accessibility root"
    return root.runnerDescendants()
        .filter { node ->
            node.runnerSpokenLabel() != null || node.isClickable || node.isAccessibilityFocused
        }
        .take(120)
        .joinToString(separator = "\n") { node ->
            "class=${node.className} label=${node.runnerSpokenLabel()} " +
                "clickable=${node.isClickable} focused=${node.isAccessibilityFocused}"
        }
}

private const val RUNNER_HUMAN_LISTEN_PROGRESS_FILE = "runner_human_listen_progress"
