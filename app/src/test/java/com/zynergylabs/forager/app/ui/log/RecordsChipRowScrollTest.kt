package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * J6a item 7 (owner: "if there's room to not need it scroll then it should be used. Tablets allow the
 * space. If not, then have it scroll."): the Records chip row scrolls sideways only when its chips do
 * not fit the width it is given. Both cases are read from the row's own scroll range, the semantics
 * `Modifier.horizontalScroll` reports: a range with a zero maximum is a row that cannot scroll.
 *
 * Measured in native graphics (J6b): the five chips with these counts need about 656 dp (a scroll range of 356
 * at a 300 dp window, 0 at 700 dp). The tablet's drawer column gives the row 328 dp, so there it always scrolls.
 *
 * All three tests GUARD: they hold at the base, where `horizontalScroll` on a row that fits already has
 * nothing to scroll. They are written to pin that reading of the ruling, and to fail if the row is
 * ever made to scroll (or to clip) when it fits. They do not, alone, prove the wide tree's row fits
 * or overflows in its 328 dp content: the wide test in `WideJournalTest` reads the real column.
 */
@RunWith(RobolectricTestRunner::class)
// A window wider than the widest row asked for: Robolectric's default window is 320 dp, and a 1,400 dp
// Box in it is measured at 320 (a first draft of these tests read a range of 94 for a "fits" row).
@Config(sdk = [36], qualifiers = "w1500dp-h800dp-mdpi")
// Native graphics: in Robolectric's default mode text has close to no width, so the chips are a fraction of their
// real size and "the chips fit" or "overflow" says nothing (found in J6b: a first version of this file, and the J6a
// report's "five chips need 414 dp", read that mode).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordsChipRowScrollTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun setRow(width: androidx.compose.ui.unit.Dp, fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(width)) {
                        RecordsFilterChipRow(
                            selected = RecordsSubTab.ALL,
                            counts = RecordsFilterCounts(finds = 2, tracks = 1, waypoints = 1, offlineMaps = 0),
                            onSelect = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollRangeMax(): Float =
        composeRule.onNodeWithTag(RECORDS_FILTER_CHIP_ROW_TAG).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue()

    @Test
    fun `GUARD a row given more than its chips need does not scroll`() {
        setRow(width = 1_400.dp)
        assertEquals("nothing to scroll when every chip fits", 0f, scrollRangeMax(), 0f)
    }

    @Test
    fun `GUARD a row given less than its chips need scrolls`() {
        setRow(width = 300.dp)
        assertTrue("the chips overflow 300 dp, so the row scrolls (range ${scrollRangeMax()})", scrollRangeMax() > 0f)
    }

    @Test
    fun `GUARD a 700 dp row at the default font scale does not scroll`() {
        setRow(width = 700.dp, fontScale = 1f)
        assertEquals("700 dp holds five chips at the default scale", 0f, scrollRangeMax(), 0f)
    }
}
