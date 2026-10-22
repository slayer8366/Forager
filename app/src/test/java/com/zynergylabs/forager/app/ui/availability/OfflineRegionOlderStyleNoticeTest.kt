package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * RECORD -1294 as built by dispatch 2026-10-18-05 Part 2: an offline region downloaded against an older map style keeps
 * its row and gains one line, "Older map style. Download again to update.", once the own Street is on; and nothing
 * changes on the row while the switch is off, which is the release value.
 *
 * Composes [OfflineRegionRow], the row both its callers (the offline maps panel and the All logbook) draw, rather than
 * the whole screen: the switch is a compile-time constant, so the screen can only ever show the release value, and the
 * row takes the switch as a parameter for exactly this test. The release value is tested through the row's own default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineRegionOlderStyleNoticeTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun region(id: Long, styleUrl: String?, isDownloaded: Boolean = true) = OfflineRegionSummary(
        id = id,
        name = "Region $id",
        region = Region(45.5, -122.6, 5),
        minZoom = 10.0,
        maxZoom = 15.0,
        tileCount = 100,
        sizeBytes = 2_000_000L,
        createdAtEpochMillis = 1_000L,
        isDownloaded = isDownloaded,
        styleUrl = styleUrl,
    )

    // The ownStreet parameter went with the release switch (dispatch 2026-10-22-01): every row now compares with Forager
    // Trail Map's style, the one downloads are made in.
    private fun show(region: OfflineRegionSummary) = composeRule.setContent {
        ForagerTheme {
            OfflineRegionRow(region = region, isStale = false, distanceUnit = DistanceUnit.MILES, nowEpochMillis = 2_000L)
        }
    }

    @Test
    fun `an older region shows the one-line notice`() {
        show(region(1, OFFLINE_STYLE_URL))
        composeRule.onNodeWithTag(offlineRegionOlderStyleTag(1)).assertIsDisplayed()
        composeRule.onNodeWithText("Older map style. Download again to update.").assertIsDisplayed()
        composeRule.onNodeWithText("Region 1").assertIsDisplayed()
    }

    @Test
    fun `a Forager Trail Map region shows no notice`() {
        show(region(2, STREET_STYLE_URL))
        composeRule.onNodeWithTag(offlineRegionOlderStyleTag(2)).assertDoesNotExist()
    }

    @Test
    fun `a region not downloaded on this phone shows no notice`() {
        show(region(3, null, isDownloaded = false))
        composeRule.onNodeWithTag(offlineRegionOlderStyleTag(3)).assertDoesNotExist()
    }
}
