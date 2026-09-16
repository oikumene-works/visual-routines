package io.github.ewoc2026.visualroutines

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
import io.github.ewoc2026.visualroutines.ui.theme.VisualRoutinesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyPolicyAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsProvidesPrivacyPolicyAction() {
        val state = VisualRoutinesState(PrivacyPolicySessionStore)
        composeTestRule.setContent {
            VisualRoutinesTheme {
                SettingsScreen(state)
            }
        }

        composeTestRule
            .onNodeWithText("Privacy policy")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(AppScreen.PrivacyPolicy, state.screen)
    }

    @Test
    fun policyContentIsScrollableAndAccessible() {
        val state = VisualRoutinesState(PrivacyPolicySessionStore)
        composeTestRule.enableAccessibilityChecks()
        composeTestRule.setContent {
            VisualRoutinesTheme {
                PrivacyPolicyScreen(state)
            }
        }

        composeTestRule.onNodeWithText("Data stored on your device").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Project contact")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }
}

private object PrivacyPolicySessionStore : SessionStore {
    override fun load(routines: List<Routine>): ActiveSession? = null

    override fun save(session: ActiveSession) = Unit

    override fun clear() = Unit
}
