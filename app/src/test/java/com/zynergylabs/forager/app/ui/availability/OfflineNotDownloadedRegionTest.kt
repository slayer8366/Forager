package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A region restored from a backup onto a phone that never downloaded it (owner, "1 B"): Offline maps lists it as
 * "Not downloaded", with a "Download again" that reaches the ViewModel's callback. The real screen, the Journal's
 * Records, its Offline maps chip.
 */
abstract class OfflineNotDownloadedRegionTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private val restored = BUBBLE_REGION.copy(id = 99L, name = "Restored creek", tileCount = 0, sizeBytes = 0L, isDownloaded = false)

    private fun openOfflineMaps() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a restored region is listed as Not downloaded with a Download again button, and a downloaded one is not`() {
        state.ui = state.ui.copy(offlineRegions = listOf(BUBBLE_REGION, restored))
        openOfflineMaps()

        composeRule.onNodeWithText("Restored creek").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not downloaded").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download again").performScrollTo().assertIsDisplayed()
        assertEquals("only the restored row carries them", 1, composeRule.onAllNodesWithText("Not downloaded").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Download again").fetchSemanticsNodes().size)
    }

    @Test
    fun `Download again reaches the callback with that region's id`() {
        state.ui = state.ui.copy(offlineRegions = listOf(BUBBLE_REGION, restored))
        openOfflineMaps()

        composeRule.onNodeWithText("Download again").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(99L), state.downloadedAgain)
    }

    @Test
    fun `a not-downloaded row does not claim to have been downloaded`() {
        state.ui = state.ui.copy(offlineRegions = listOf(restored))
        openOfflineMaps()

        assertEquals("no '... ago' line for a region with no tiles", 0, composeRule.onAllNodesWithText(" ago", substring = true).fetchSemanticsNodes().size)
        assertEquals("no zoom-readiness paragraph either", 0, composeRule.onAllNodesWithText("Ready to zoom", substring = true).fetchSemanticsNodes().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class OfflineNotDownloadedRegionPortraitTest : OfflineNotDownloadedRegionTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class OfflineNotDownloadedRegionShortLandscapeTest : OfflineNotDownloadedRegionTests()
