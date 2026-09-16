package io.github.ewoc2026.visualroutines

import android.annotation.TargetApi
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayDeque
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageEditorAccessibilityTest {
    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()

    @Before
    fun setContent() {
        val state = VisualRoutinesState(EmptySessionStore)
        state.openCreateRoutine(
            CreateRoutineDraft(
                title = "Change bed linen",
                steps = listOf(
                    RoutineStepContent("Take the bedspread off the bed."),
                    RoutineStepContent(
                        instruction = "Take the duvet out of the duvet cover.",
                        image = RoutineStepImage(
                            source = RoutineImageSource.BUNDLED,
                            assetId = "bed-linen-remove-duvet-cover",
                            semanticRole = RoutineImageSemanticRole.INFORMATIVE,
                        ),
                    ),
                ),
            ),
        )
        composeTestRule.setContent {
            VisualRoutinesApp(state)
        }
    }

    @Test
    fun editorHasNoSilentTargetAndRemoveRetainsTheSameStepAction() {
        composeTestRule.enableAccessibilityChecks()

        composeTestRule.onNodeWithText("Change bed linen").assertIsDisplayed()
        composeTestRule.onAllNodes(
            SemanticsMatcher("has an empty content description") { node ->
                SemanticsProperties.ContentDescription in node.config &&
                    node.config[SemanticsProperties.ContentDescription].any(String::isBlank)
            },
            useUnmergedTree = true,
        ).assertCountEquals(0)

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val stepTwoDescriptionAction = composeTestRule.onNodeWithTag("step-2-description-action")
        stepTwoDescriptionAction.performScrollTo().assertIsDisplayed()
        val writeDescriptionNode = composeTestRule.waitForClickableAccessibilityNode(
            uiAutomation = uiAutomation,
            label = "Write a description for this step",
        )
        assertTrue(
            "Could not activate Write a description for this step",
            writeDescriptionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        )
        composeTestRule
            .onNode(hasContentDescriptionExactly("Added a step-specific image description for step 2."))
            .assertIsDisplayed()
        composeTestRule.waitUntil("The description action changes to Use bundled description", 5_000) {
            uiAutomation.clickableNodes("Use bundled description").isNotEmpty()
        }
        stepTwoDescriptionAction.assertIsDisplayed()

        val useBundledDescriptionNode = composeTestRule.waitForClickableAccessibilityNode(
            uiAutomation = uiAutomation,
            label = "Use bundled description",
        )
        assertTrue(
            "Could not activate Use bundled description",
            useBundledDescriptionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        )
        composeTestRule
            .onNode(hasContentDescriptionExactly("Removed the step-specific image description from step 2."))
            .assertIsDisplayed()
        composeTestRule.waitUntil(
            "The description action changes to Write a description for this step",
            5_000,
        ) {
            uiAutomation.clickableNodes("Write a description for this step").isNotEmpty()
        }
        stepTwoDescriptionAction.assertIsDisplayed()

        val stepTwoImageAction = composeTestRule.onNodeWithTag("step-2-image-action")
        stepTwoImageAction.performScrollTo().assertIsDisplayed()
        val removeImageNode = composeTestRule.waitForClickableAccessibilityNode(
            uiAutomation = uiAutomation,
            label = "Remove image",
        )
        assertTrue(
            "Could not activate Remove image",
            removeImageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        )

        composeTestRule
            .onNode(hasContentDescriptionExactly("Removed the image from step 2."))
            .assertIsDisplayed()
        composeTestRule.waitUntil("The image action changes to Add image", 5_000) {
            uiAutomation.clickableNodes("Add image").isNotEmpty()
        }
        stepTwoImageAction.assertIsDisplayed()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun stepActionsShareOneLineWhenBothGroupsFit() {
        val contentActions = composeTestRule
            .onNodeWithTag("step-1-content-actions", useUnmergedTree = true)
            .fetchSemanticsNode()
        val orderActions = composeTestRule
            .onNodeWithTag("step-1-order-actions", useUnmergedTree = true)
            .fetchSemanticsNode()

        assertEquals(
            contentActions.boundsInRoot.center.y,
            orderActions.boundsInRoot.center.y,
            1f,
        )
    }

    @Test
    @TargetApi(Build.VERSION_CODES.N)
    fun talkBackEditorTraceRetainsAccessibilityFocusAcrossDisappearingControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.talkBackUiAutomation()
        val eventTrace = Collections.synchronizedList(mutableListOf<String>())
        uiAutomation.setOnAccessibilityEventListener { event ->
            if (event.packageName?.toString() == instrumentation.targetContext.packageName) {
                val sourceLabel = event.source?.accessibilityLabel()
                val relevantContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    sourceLabel?.contains("image", ignoreCase = true) == true
                if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || relevantContentChange) {
                    synchronized(eventTrace) {
                        if (eventTrace.size == 40) {
                            eventTrace.removeAt(0)
                        }
                        eventTrace += event.toTraceLine()
                    }
                }
            }
        }

        val transitionSummaries = mutableListOf<String>()
        var traceCompleted = false
        try {
            transitionSummaries += assertTalkBackTransition(
                uiAutomation = uiAutomation,
                actionTag = "step-2-description-action",
                initialLabel = "Write a description for this step",
                finalLabel = "Use bundled description",
                feedback = "Added a step-specific image description for step 2.",
                requireSameActionSlot = true,
                hasComposeText = false,
            )
            transitionSummaries += assertTalkBackTransition(
                uiAutomation = uiAutomation,
                actionTag = "step-2-image-action",
                initialLabel = "Remove image",
                finalLabel = "Add image",
                feedback = "Removed the image from step 2.",
                requireSameActionSlot = false,
                hasComposeText = false,
            )
            traceCompleted = true
        } finally {
            if (!traceCompleted) {
                transitionSummaries += uiAutomation.accessibilityFocus()?.let { focus ->
                    "finalFocus=${focus.accessibilityLabel()} bounds=${focus.screenBounds()}"
                } ?: "finalFocus=null"
            }
            uiAutomation.setOnAccessibilityEventListener(null)
            val trace = synchronized(eventTrace) {
                (eventTrace + transitionSummaries).joinToString(separator = "\n")
            }
            instrumentation.sendStatus(
                2,
                Bundle().apply {
                    putString("talkback_trace", trace)
                    if (!traceCompleted) {
                        putString("talkback_tree_after", uiAutomation.accessibilityTreeSummary())
                    }
                },
            )
        }
    }

    @Test
    @TargetApi(Build.VERSION_CODES.N)
    fun pacedHumanListenTransition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.talkBackUiAutomation()
        val transition = InstrumentationRegistry.getArguments().getString("human_transition")
        val summary = when (transition) {
            "description" -> assertTalkBackTransition(
                uiAutomation = uiAutomation,
                actionTag = "step-2-description-action",
                initialLabel = "Write a description for this step",
                finalLabel = "Use bundled description",
                feedback = "Added a step-specific image description for step 2.",
                requireSameActionSlot = true,
                hasComposeText = false,
                pauseBeforeActivationMillis = 4_000,
                pauseAfterTransitionMillis = 7_000,
            )
            "image" -> assertTalkBackTransition(
                uiAutomation = uiAutomation,
                actionTag = "step-2-image-action",
                initialLabel = "Remove image",
                finalLabel = "Add image",
                feedback = "Removed the image from step 2.",
                requireSameActionSlot = false,
                hasComposeText = false,
                pauseBeforeActivationMillis = 4_000,
                pauseAfterTransitionMillis = 7_000,
            )
            else -> error("Unknown human-listen transition: $transition")
        }

        instrumentation.sendStatus(
            2,
            Bundle().apply { putString("human_listen_transition", summary) },
        )
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun assertTalkBackTransition(
        uiAutomation: UiAutomation,
        actionTag: String,
        initialLabel: String,
        finalLabel: String,
        feedback: String,
        requireSameActionSlot: Boolean,
        hasComposeText: Boolean = true,
        pauseBeforeActivationMillis: Long = 0,
        pauseAfterTransitionMillis: Long = 0,
    ): String {
        val action = composeTestRule.onNodeWithTag(actionTag)
        action.performScrollTo().assertIsDisplayed()
        if (hasComposeText) {
            action.assertTextEquals(initialLabel)
        }
        composeTestRule.waitForIdle()

        lateinit var initialNode: AccessibilityNodeInfo
        lateinit var initialBounds: Rect
        var initialFocusSettled = false
        for (attempt in 1..3) {
            val candidate = composeTestRule.waitForClickableAccessibilityNode(
                uiAutomation = uiAutomation,
                label = initialLabel,
            )
            val candidateBounds = candidate.screenBounds()
            uiAutomation.accessibilityFocus()?.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS.id,
            )
            val focusRequested = candidate.performAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.id,
            )
            val focusSettled = focusRequested && runCatching {
                composeTestRule.waitUntil(
                    "$initialLabel receives accessibility focus on attempt $attempt",
                    1_500,
                ) {
                    uiAutomation.accessibilityFocus()?.let { focus ->
                        focus.screenBounds() == candidateBounds &&
                            focus.accessibilityLabel() == initialLabel
                    } == true
                }
            }.isSuccess
            if (focusSettled) {
                initialNode = candidate
                initialBounds = candidateBounds
                initialFocusSettled = true
                break
            }
        }
        assertTrue("Could not settle accessibility focus on $initialLabel", initialFocusSettled)
        if (pauseBeforeActivationMillis > 0) {
            Thread.sleep(pauseBeforeActivationMillis)
        }
        assertTrue(
            "Could not activate accessibility-focused $initialLabel",
            initialNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id),
        )
        composeTestRule.waitUntil("The action changes to $finalLabel", 5_000) {
            if (hasComposeText) {
                action.fetchSemanticsNode().config[SemanticsProperties.Text]
                    .any { it.text == finalLabel }
            } else {
                uiAutomation.clickableNodes(finalLabel).isNotEmpty()
            }
        }
        composeTestRule.waitUntil("Accessibility focus remains on $finalLabel", 5_000) {
            uiAutomation.accessibilityFocus()?.let { focus ->
                val slotMatches = !requireSameActionSlot ||
                    focus.screenBounds().isSameActionSlotAs(initialBounds)
                slotMatches && focus.accessibilityLabel() == finalLabel
            } == true
        }

        composeTestRule.onNode(hasContentDescriptionExactly(feedback)).assertIsDisplayed()
        action.assertIsDisplayed()
        if (hasComposeText) {
            action.assertTextEquals(finalLabel)
        }

        val finalFocus = requireNotNull(uiAutomation.accessibilityFocus())
        val finalBounds = finalFocus.screenBounds()
        if (requireSameActionSlot) {
            assertTrue(
                "Accessibility focus moved to a different action slot",
                finalBounds.isSameActionSlotAs(initialBounds),
            )
        }
        if (pauseAfterTransitionMillis > 0) {
            Thread.sleep(pauseAfterTransitionMillis)
        }
        return "$initialLabel -> $finalLabel focus=${finalFocus.accessibilityLabel()} bounds=$finalBounds"
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun android.app.Instrumentation.talkBackUiAutomation(): UiAutomation {
        val enabledServices = Settings.Secure.getString(
            targetContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        assumeTrue(
            "TalkBack must be enabled for the assisted trace",
            enabledServices.contains("talkback", ignoreCase = true),
        )
        return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    }
}

private object EmptySessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null

    override fun save(session: ActiveSession) = Unit

    override fun clear() = Unit
}

private fun ComposeContentTestRule.waitForClickableAccessibilityNode(
    uiAutomation: UiAutomation,
    label: String,
): AccessibilityNodeInfo {
    var result: AccessibilityNodeInfo? = null
    waitUntil("One clickable accessibility node is labelled $label", 5_000) {
        uiAutomation.clickableNodes(label).singleOrNull()?.let { node ->
            result = node
            true
        } ?: false
    }
    return requireNotNull(result)
}

private fun UiAutomation.clickableNodes(label: String): List<AccessibilityNodeInfo> {
    clearCache()
    val root = rootInActiveWindow ?: return emptyList()
    return root.allDescendants()
        .filter { node -> node.spokenLabel() == label }
        .mapNotNull { match ->
            generateSequence(match) { node -> node.parent }
                .take(4)
                .firstOrNull(AccessibilityNodeInfo::isClickable)
        }
        .distinctBy { node -> node.windowId to node.screenBounds() }
}

private fun UiAutomation.accessibilityFocus(): AccessibilityNodeInfo? {
    clearCache()
    return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
}

private fun AccessibilityNodeInfo.spokenLabel(): String? {
    return text?.toString()?.takeIf(String::isNotBlank)
        ?: contentDescription?.toString()?.takeIf(String::isNotBlank)
}

private fun AccessibilityNodeInfo.accessibilityLabel(): String? {
    return spokenLabel()
        ?: allDescendants().asSequence().drop(1).mapNotNull(AccessibilityNodeInfo::spokenLabel).firstOrNull()
}

private fun AccessibilityNodeInfo.screenBounds(): Rect {
    return Rect().also(::getBoundsInScreen)
}

private fun Rect.isSameActionSlotAs(other: Rect): Boolean {
    return left == other.left && top == other.top && bottom == other.bottom
}

private fun AccessibilityNodeInfo.allDescendants(): List<AccessibilityNodeInfo> {
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

private fun UiAutomation.accessibilityTreeSummary(): String {
    clearCache()
    val root = rootInActiveWindow ?: return "No active accessibility root"
    return root.allDescendants()
        .filter { node ->
            node.spokenLabel() != null || node.isClickable || node.isAccessibilityFocused
        }
        .take(120)
        .joinToString(separator = "\n") { node ->
            buildString {
                append("class=")
                append(node.className)
                append(" label=")
                append(node.spokenLabel())
                append(" clickable=")
                append(node.isClickable)
                append(" focused=")
                append(node.isAccessibilityFocused)
                append(" bounds=")
                append(node.screenBounds())
            }
        }
}

private fun AccessibilityEvent.toTraceLine(): String {
    val eventText = text.joinToString(separator = " | ")
    return buildString {
        append(AccessibilityEvent.eventTypeToString(eventType))
        append(" class=")
        append(className)
        if (eventText.isNotBlank()) {
            append(" text=")
            append(eventText)
        }
        contentDescription?.let { description ->
            append(" description=")
            append(description)
        }
        if (contentChangeTypes != 0) {
            append(" changeTypes=")
            append(contentChangeTypes)
        }
        source?.accessibilityLabel()?.let { sourceLabel ->
            append(" source=")
            append(sourceLabel)
        }
        if (
            eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            source?.let { sourceNode ->
                append(" bounds=")
                append(sourceNode.screenBounds())
            }
        }
    }
}
