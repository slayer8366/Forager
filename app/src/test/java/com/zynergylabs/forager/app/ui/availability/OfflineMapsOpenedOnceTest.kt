package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.printToString
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.Region
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-01, T3 (A3, Amendment 1 A8): one touch on the Tools drawer's Offline maps row is one opening of
 * Offline maps. In S22 session 14's step 2 one tap logged "Offline maps opened with no signal" twice, 0.23 s apart; the
 * Phase 1 report traced it to `RecordsTab` composing with the Tools latch already set (section 5).
 *
 * Driven through the real [AvailabilityScreen] and the real [AvailabilityViewModel] ([RealSearchScreenRig]), the way the
 * S22 run took: the Maps tab with the Journal on Entries (its default), Tools, then the row, by real touches at several
 * points across the row's own bounds. Back between touches returns to the drawer (RECORD -871) and restores the Journal's
 * top tab and chip, so each touch composes Records afresh with the latch set, as step 3's reopening did. An opening is
 * counted where the ViewModel's opening reads the region list (`AvailabilityOfflineMaps.onOfflineMapsOpened`, which
 * calls [OfflineMapRepository.listRegions] once); with no regions listed nothing else reads it after an opening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineMapsOpenedOnceTest {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val regions = CountingRegionLists()
    private val rig = RealSearchScreenRig(composeRule, regions)

    private fun openTools() = rig.touch(
        composeRule.onAllNodes(hasText("Tools") and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)), useUnmergedTree = true).onFirst(),
    )

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        rig.settle()
    }

    @Test
    fun `each touch on the Tools drawer's Offline maps row opens Offline maps once`() {
        rig.setScreen()
        openTools()
        for ((fx, fy) in TOUCH_SAMPLES) {
            val before = regions.lists.get()

            rig.touch(composeRule.onNodeWithTag(TOOLS_OFFLINE_MAPS_ROW_TAG), fx, fy)

            if (composeRule.onAllNodes(hasText("Download Maps")).fetchSemanticsNodes().isEmpty()) {
                throw AssertionError("positive control: the touch at ($fx, $fy) did not bring Offline maps up; tree:\n" + composeRule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 60))
            }
            assertEquals("openings of Offline maps for one touch at ($fx, $fy) of the row", 1, regions.lists.get() - before)

            pressBack()
            if (composeRule.onAllNodes(hasTestTag(TOOLS_OFFLINE_MAPS_ROW_TAG)).fetchSemanticsNodes().isEmpty()) {
                throw AssertionError("positive control: Back did not return to the Tools drawer")
            }
        }
    }

    private companion object {
        /** Across the row's own bounds, not only its centre (CLAUDE.md: a finger is not a point). */
        val TOUCH_SAMPLES = listOf(0.5f to 0.5f, 0.1f to 0.5f, 0.9f to 0.5f, 0.5f to 0.15f, 0.5f to 0.85f)
    }
}

/** No regions, and every read of the list counted: the count of openings ([OfflineMapsOpenedOnceTest]). */
private class CountingRegionLists : OfflineMapRepository {
    val lists = AtomicInteger()

    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> {
        lists.incrementAndGet()
        return Result.success(emptyList())
    }
}
