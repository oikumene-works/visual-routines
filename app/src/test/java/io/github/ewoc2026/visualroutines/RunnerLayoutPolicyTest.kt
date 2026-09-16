package io.github.ewoc2026.visualroutines

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RunnerLayoutPolicyTest {
    @Test
    fun widthBoundaryRequiresTheAcceptedSideBySideMinimum() {
        assertEquals(
            RunnerLayoutMode.STACKED,
            runnerLayoutPolicy(DpSize(719.9.dp, 500.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.SIDE_BY_SIDE,
            runnerLayoutPolicy(DpSize(720.dp, 500.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.SIDE_BY_SIDE,
            runnerLayoutPolicy(DpSize(720.1.dp, 500.dp)).mode,
        )
    }

    @Test
    fun heightBoundaryKeepsSideBySideAtTheAcceptedMaximum() {
        assertEquals(
            RunnerLayoutMode.SIDE_BY_SIDE,
            runnerLayoutPolicy(DpSize(900.dp, 599.9.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.SIDE_BY_SIDE,
            runnerLayoutPolicy(DpSize(900.dp, 600.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.STACKED,
            runnerLayoutPolicy(DpSize(900.dp, 600.1.dp)).mode,
        )
    }

    @Test
    fun sideBySideRequiresBothWidthAndHeightConditions() {
        assertEquals(
            RunnerLayoutMode.STACKED,
            runnerLayoutPolicy(DpSize(719.dp, 601.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.STACKED,
            runnerLayoutPolicy(DpSize(900.dp, 601.dp)).mode,
        )
        assertEquals(
            RunnerLayoutMode.STACKED,
            runnerLayoutPolicy(DpSize(719.dp, 500.dp)).mode,
        )
    }

    @Test
    fun compactHeightBoundaryChangesSpacingAndImageBoundWithoutChangingContent() {
        val immediatelyBelow = runnerLayoutPolicy(DpSize(600.dp, 359.9.dp))
        val atBoundary = runnerLayoutPolicy(DpSize(600.dp, 360.dp))
        val immediatelyAbove = runnerLayoutPolicy(DpSize(600.dp, 360.1.dp))

        assertEquals(12.dp, immediatelyBelow.contentSpacing)
        assertEquals(12.dp, immediatelyBelow.contentVerticalPadding)
        assertEquals(180.dp, immediatelyBelow.maximumImageHeight)
        assertEquals(24.dp, atBoundary.contentSpacing)
        assertEquals(28.dp, atBoundary.contentVerticalPadding)
        assertEquals(390.dp, atBoundary.maximumImageHeight)
        assertEquals(atBoundary, immediatelyAbove)
    }
}
