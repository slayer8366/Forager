package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.ui.map.LocalNoSignal
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The route line on a region's row in Offline maps, composed through [OfflineRegionsSection], the section the panel
 * draws (dispatch 2026-10-23-03, the owner's step path). "Try again" is reached by real touches across its bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineRegionRouteRowTest {
    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun region(id: Long, routeData: RegionRouteData) = OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0,
        tileCount = 100, sizeBytes = 2_000_000L, createdAtEpochMillis = 1_000L, routeData = routeData,
    )

    private val retried = mutableListOf<Long>()
    private var signalReturns = 0

    private fun show(
        regions: List<OfflineRegionSummary>,
        failed: Set<Long> = emptySet(),
        fetching: Set<Long> = emptySet(),
        noSignal: Boolean? = null,
        signal: MutableStateFlow<Boolean>? = noSignal?.let { MutableStateFlow(it) },
    ) =
        composeRule.setContent {
            ForagerTheme {
                CompositionLocalProvider(LocalNoSignal provides signal) {
                    OfflineRegionsSection(
                        regions = regions, tilesUsed = 0, errorMessage = null, staleThresholdDays = 30,
                        distanceUnit = DistanceUnit.MILES, nowEpochMillis = 2_000L, onDeleteOfflineRegion = {},
                        onOpenRegionDetails = null, routeFetchingIds = fetching, routeFailedIds = failed,
                        routeActions = OfflineRouteActions(onTryAgain = { retried += it }, onSignalReturned = { signalReturns++ }),
                    )
                }
            }
        }

    @Test
    fun `a failed attempt shows B's line, and every touch on Try again asks for that region`() {
        show(listOf(region(1, RegionRouteData.Missing(RouteDataMissingReason.NETWORK))), failed = setOf(1L))

        composeRule.onNodeWithText("Map only. Trail routes didn't download.").assertIsDisplayed()
        val button = composeRule.onNodeWithTag(offlineRouteTryAgainTag(1))
        button.assertIsDisplayed()
        // A finger is not a point: touches at the centre and near each corner of the button's own bounds.
        listOf(0.5f to 0.5f, 0.1f to 0.2f, 0.9f to 0.2f, 0.1f to 0.8f, 0.9f to 0.8f).forEach { (fx, fy) ->
            button.performTouchInput { click(androidx.compose.ui.geometry.Offset(width * fx, height * fy)) }
        }
        assertEquals(List(5) { 1L }, retried)
    }

    @Test
    fun `stored routes add no line to the row`() {
        show(listOf(region(2, RegionRouteData.Stored("run", "t", 10L, File("/r"), missingSquares = listOf("W130_N45")))))

        composeRule.onNodeWithText("Region 2").assertIsDisplayed()
        composeRule.onNodeWithTag(offlineRouteLineTag(2)).assertDoesNotExist()
    }

    @Test
    fun `no routes here shows C's line and no button`() {
        show(listOf(region(3, RegionRouteData.NoneHere)))

        composeRule.onNodeWithText("Map only. No trail routes for this area yet.").assertIsDisplayed()
        composeRule.onNodeWithTag(offlineRouteTryAgainTag(3)).assertDoesNotExist()
    }

    @Test
    fun `with no signal an older region waits for a connection, with no button`() {
        show(listOf(region(4, RegionRouteData.NotFetched)), failed = setOf(4L), noSignal = true)

        composeRule.onNodeWithText("Map only. Trail routes download next time you're online.").assertIsDisplayed()
        composeRule.onNodeWithTag(offlineRouteTryAgainTag(4)).assertDoesNotExist()
    }

    @Test
    fun `signal coming back while the list is on screen is told once per return, and never at the first composition`() {
        val signal = MutableStateFlow(false)
        show(listOf(region(6, RegionRouteData.NotFetched)), signal = signal)
        composeRule.waitForIdle()
        assertEquals("opened with signal: nothing to tell", 0, signalReturns)

        signal.value = true
        composeRule.waitForIdle()
        signal.value = false
        composeRule.waitForIdle()
        assertEquals(1, signalReturns)

        signal.value = false
        composeRule.waitForIdle()
        assertEquals("no change is no return", 1, signalReturns)

        signal.value = true
        composeRule.waitForIdle()
        signal.value = false
        composeRule.waitForIdle()
        assertEquals(2, signalReturns)
    }

    @Test
    fun `opened with no signal, the first return is told`() {
        val signal = MutableStateFlow(true)
        show(listOf(region(7, RegionRouteData.NotFetched)), signal = signal)
        composeRule.waitForIdle()
        assertEquals(0, signalReturns)

        signal.value = false
        composeRule.waitForIdle()
        assertEquals(1, signalReturns)
    }

    @Test
    fun `a region whose fetch is running, the download's own included, says Getting trail routes`() {
        show(listOf(region(8, RegionRouteData.Fetching)))

        composeRule.onNodeWithText("Getting trail routes").assertIsDisplayed()
        composeRule.onNodeWithTag(offlineRouteTryAgainTag(8)).assertDoesNotExist()
    }

    @Test
    fun `a download's progress during its route step says Getting trail routes, not tiles`() {
        composeRule.setContent { ForagerTheme { OfflineDownloadStatusContent(OfflineMapStatus.Downloading(120, 120, gettingRoutes = true)) } }

        composeRule.onNodeWithText("Getting trail routes").assertIsDisplayed()
        composeRule.onNodeWithText("120 / 120 tiles").assertDoesNotExist()
    }

    @Test
    fun `while fetching the row says Getting trail routes`() {
        show(listOf(region(5, RegionRouteData.NotFetched)), fetching = setOf(5L))

        composeRule.onNodeWithText("Getting trail routes").assertIsDisplayed()
    }
}
