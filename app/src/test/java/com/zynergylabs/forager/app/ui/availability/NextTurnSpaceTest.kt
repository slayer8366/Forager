package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The next turn's own space, composed (dispatch 2026-11-06-01, Amendment 1, J, 2b-2 step 1). Its window: CLAUDE.md, "Changing info
 * gets its own window", the owner: "Info that changes and shares a line with other text should get its own window so it doesn't
 * shift all text on the same line." Its arrow: C1 to C3, Material's icons, "not mirrored". Its words: G3, read by TalkBack alone.
 * Not on the card yet (step 2), so it is composed on its own, in the app's theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NextTurnSpaceTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val shortTurn = NextTurnSpaceShown(NextTurnArrow.SLIGHT_LEFT, "8 ft", "slight left in 8 feet")
    private val longTurn = NextTurnSpaceShown(NextTurnArrow.SHARP_RIGHT, "88.8 mi", "make a sharp right in 88.8 miles")
    private val straightOn = NextTurnSpaceShown(NextTurnArrow.STRAIGHT, "0.4 mi", "keep straight in 0.4 miles")

    private fun space() = composeRule.onNodeWithTag(NEXT_TURN_SPACE_TAG, useUnmergedTree = true)

    private fun width(): Dp {
        val b = space().getBoundsInRoot()
        return b.right - b.left
    }

    @Test
    fun `its own window - blank, a short figure, the widest and the straight-ahead arrow are all one width, in each unit`() {
        var shown by mutableStateOf(NextTurnSpaceShown.BLANK)
        var unit by mutableStateOf(DistanceUnit.MILES)
        composeRule.setContent { ForagerTheme { NextTurnSpace(shown, unit) } }
        for (u in DistanceUnit.entries) {
            unit = u
            val widths = listOf(NextTurnSpaceShown.BLANK, shortTurn, longTurn, straightOn, NextTurnSpaceShown.BLANK).map {
                shown = it
                composeRule.waitForIdle()
                width()
            }
            assertTrue("$u: a space with width, $widths", widths.first() > COMPASS_ICON_SIZE)
            assertEquals("$u: one width whatever it holds, $widths", 1, widths.distinct().size)
        }
    }

    @Test
    fun `TalkBack reads the words alone - not the drawn figure`() {
        composeRule.setContent { ForagerTheme { NextTurnSpace(shortTurn, DistanceUnit.MILES) } }
        val node = space().fetchSemanticsNode()
        assertEquals(listOf("slight left in 8 feet"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(NextTurnArrow.SLIGHT_LEFT, node.config.getOrNull(NextTurnArrowKey))
        assertEquals("8 ft", node.config.getOrNull(NextTurnFigureKey))
        composeRule.onAllNodesWithText("8 ft").assertCountEquals(0)
    }

    @Test
    fun `E1 - blank, the space says nothing and draws no arrow`() {
        composeRule.setContent { ForagerTheme { NextTurnSpace(NextTurnSpaceShown.BLANK, DistanceUnit.MILES) } }
        val node = space().fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(node.config.getOrNull(NextTurnArrowKey))
        assertNull(node.config.getOrNull(NextTurnFigureKey))
    }

    @Test
    fun `C1 to C3 - each arrow is Material's own icon, not mirrored`() {
        val expected = mapOf(
            NextTurnArrow.SLIGHT_LEFT to Icons.Filled.TurnSlightLeft,
            NextTurnArrow.LEFT to Icons.Filled.TurnLeft,
            NextTurnArrow.SHARP_LEFT to Icons.Filled.TurnSharpLeft,
            NextTurnArrow.SLIGHT_RIGHT to Icons.Filled.TurnSlightRight,
            NextTurnArrow.RIGHT to Icons.Filled.TurnRight,
            NextTurnArrow.SHARP_RIGHT to Icons.Filled.TurnSharpRight,
            NextTurnArrow.FORK_LEFT to Icons.Filled.ForkLeft,
            NextTurnArrow.FORK_RIGHT to Icons.Filled.ForkRight,
            NextTurnArrow.STRAIGHT to Icons.Filled.Straight,
        )
        assertEquals(NextTurnArrow.entries.toSet(), expected.keys)
        for ((arrow, icon) in expected) {
            assertEquals(arrow.name, icon.name, nextTurnIcon(arrow).name)
            assertFalse("${arrow.name} not mirrored", nextTurnIcon(arrow).autoMirror)
        }
    }
}
