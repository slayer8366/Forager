package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.zynergylabs.forager.app.ui.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' scrolling content ends one bottom padding ([Spacing.md]) below its last row: no divider
 * is drawn under the last row (dispatch 2026-10-11, the planner's follow-up to RECORD -830: "release
 * Settings must not end with a stray divider under Backup"). The last row was the Backup section; since
 * dispatch 2026-10-13-05 it is "Sources & credits", and the rule holds for it unchanged.
 *
 * A divider carries no semantics, so it is measured rather than found: scrolled to the end, the gap
 * between the lowest node inside the scrolling column and the column's own bottom edge. With nothing
 * under the last row it is the 12 dp bottom padding, plus up to 4 dp where the last node is a Material
 * button laid out in a 48 dp touch target around a 40 dp body. Anything drawn after the last row adds
 * at least the column's own 12 dp spacing, so the bound is under 24 dp: a trailing divider measured
 * 29 dp in the release build before the fix (12 spacing, 1 line, 12 padding, 4 touch target).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class SettingsEndsAtItsLastRowTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettingsPageHarness.declareHostActivity()).around(composeRule)

    @Test
    fun `Settings ends one bottom padding below its last row, with no divider under it`() {
        SettingsPageHarness.openSettings(composeRule)
        val settings = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText(SettingsPageHarness.SETTINGS_CONTROL_ROW)), useUnmergedTree = true)
        settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        composeRule.waitForIdle()

        val column = settings.fetchSemanticsNode()
        val lowest = descendantsOf(column).filter { it.boundsInRoot.height > 0f }.maxOf { it.boundsInRoot.bottom }
        val gapDp = (column.boundsInRoot.bottom - lowest) / composeRule.density.density

        assertTrue(
            "the gap under Settings' last row is $gapDp dp: ${Spacing.md.value} dp is the padding alone, and " +
                "${2 * Spacing.md.value} dp or more means something is drawn under the last row",
            gapDp >= Spacing.md.value - 0.5f && gapDp < 2 * Spacing.md.value,
        )
    }

    private fun descendantsOf(node: SemanticsNode): List<SemanticsNode> =
        node.children.flatMap { listOf(it) + descendantsOf(it) }
}
