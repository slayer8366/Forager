package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.ForagerThemeWithNoSignal
import com.zynergylabs.forager.app.ui.map.NavigationViewRequest
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.map.runOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186). The owner, seeing Amendment 1's ring on the S22: "Yeah, no ring. Only
 * have the ring in landscape when needing to re-center the map camera during navigation, because re-center seems to
 * struggle fitting there and the circle will be an answer to that. But only in that situation would it be useful, every
 * other situation it doesn't have a ring." To what happens to the Re-center pill then: "Ring replaces the pill" (the option
 * read: "In landscape navigation the pill doesn't show; the ringed locate icon is the re-center button. Portrait keeps the
 * pill as now"). To the step path "pan away > the ring appears > tap the ringed icon > the camera re-centres on your
 * navigation > the ring goes": "Yes, that's it".
 *
 * Everything here runs through the real Maps tab inside `MainActivity`'s own root ([ForagerThemeWithNoSignal]): a navigation
 * started (Return), the map reporting a move away as the real map does (the request's `onLeftView`), and real touches on the
 * locate row ("Center on my location"). The ring is read from the row's drawn pixels as Amendment 1's `LocateRowRingTest`
 * reads it: each side inwards from the row's own edge over its first 11 dp, for the ruled hex written out (Re-center's
 * outline, #505: `onSurface`, Bark #3B2E24 by day, Cream #EDE3D0 at night), at most ceil(1 dp) wide, on the badge circle 6 dp
 * in. Where no ring is ruled, no side carries that colour at all.
 */
abstract class LocateRingReCenterTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.37, lng = -121.69, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(id = "origin", lat = 45.38, lng = -121.69, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1")

    protected class Look(val name: String, val dark: Boolean, val ring: Color)

    /** The day `onSurface`, Bark. */
    protected val day = Look("day", dark = false, ring = Color(0xFF3B2E24))

    /** The night `onSurface`, Cream. */
    protected val night = Look("night", dark = true, ring = Color(0xFFEDE3D0))

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun px(dp: Float): Int = with(composeRule.density) { dp.dp.roundToPx() }

    private fun ringWidthPx(): Int = with(composeRule.density) { ceil(1.dp.toPx()).toInt() }

    /** The real Maps tab, navigating ([navigating]) or not. */
    protected fun setMapsTab(look: Look, navigating: Boolean = true) {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = look.dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = AbsentForecastCellStore,
                    uiStateTransform = { it.copy(liveFix = fix) },
                    navigation = if (navigating) AvailabilityScreenNavigation(isReturning = true, navigationTarget = origin) else AvailabilityScreenNavigation(),
                    recording = AvailabilityScreenRecording(isRecording = true),
                    currentTime = ROUTE_CARD_NOON_CLOCK,
                )
            }
        }
        settle()
    }

    private fun view(): NavigationViewRequest = map.renderMode?.navigationView ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** The map reports the user's move away from the navigation view, as the real map does. */
    protected fun moveAway() {
        val request = view()
        composeRule.runOnUiThread { request.onLeftView() }
        settle()
    }

    /** Real touches on the fullscreen row, as a user enters fullscreen. */
    protected fun enterFullscreen() {
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("fullscreen is on", composeRule.onAllNodesWithContentDescription("Exit fullscreen").fetchSemanticsNodes().isNotEmpty())
    }

    protected fun pillShown(): Boolean = composeRule.onAllNodesWithTag(RETURN_TO_ROUTE_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The four sides of the locate row, each read inwards from the row's own edge over its first 11 dp (the glyph starts at about 13 dp). */
    private fun sides(): List<Pair<String, List<Color>>> {
        val pixels = composeRule.onNodeWithContentDescription(LOCATE).captureToImage().toPixelMap()
        val n = px(11f)
        val across = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val down = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        return listOf("left" to across.take(n), "right" to across.takeLast(n).reversed(), "top" to down.take(n), "bottom" to down.takeLast(n).reversed())
    }

    protected fun assertRing(where: String, look: Look) {
        val bounds = composeRule.onNodeWithContentDescription(LOCATE).getUnclippedBoundsInRoot()
        assertEquals("$where: the locate row's width is unchanged, the 48 dp target", 48f, (bounds.right - bounds.left).value, 0.5f)
        assertEquals("$where: the locate row's height is unchanged, the 48 dp target", 48f, (bounds.bottom - bounds.top).value, 0.5f)
        val width = ringWidthPx()
        for ((side, edge) in sides()) {
            val run = runOf(edge, look.ring)
            assertNotNull("$where, ${look.name}, locate row, $side side: a ${look.ring.hex()} ring is drawn; the edge reads ${edge.map { it.hex() }}", run)
            run!!
            val drawn = run.last - run.first + 1
            println("MEASURED $where, ${look.name}, locate row, $side side: ${look.ring.hex()} for $drawn px from ${run.first} px in (6 dp = ${px(6f)} px, ceil(1 dp) = $width px)")
            assertTrue("$where, ${look.name}, $side side: the ring is at most ceil(1 dp) = $width px wide, drew $drawn", drawn <= width)
            assertTrue("$where, ${look.name}, $side side: the ring sits on the badge circle, 6 dp (${px(6f)} px) in, found ${run.first} px in", run.first in (px(6f) - 1)..(px(7f)))
        }
    }

    protected fun assertNoRing(where: String, look: Look) {
        for ((side, edge) in sides()) {
            assertNull("$where, ${look.name}, locate row, $side side: no ring; the edge reads ${edge.map { it.hex() }}", runOf(edge, look.ring))
        }
    }

    /**
     * CLAUDE.md: a semantic click proves wiring, not routing, and a finger is not a point. Real touches at five points across
     * the ringed locate row ([locateRowTouchFractions]: the centre, 8% in from the top, the bottom and the inboard side, inside
     * and outside the ring, and on the outer side just inboard of the minimise handle's box, which owns the row's outer sliver
     * in the L), each bringing the navigation view back as Re-center does (its `restoreRequestId`, which only "Re-center" and
     * locate move), after which the camera follows and the ring goes. The pill is absent throughout. Each touch is followed by
     * a fresh move away, so the ring is back.
     */
    protected fun assertTouchesReCentre(where: String, look: Look = day) {
        composeRule.assertLocateRowReCentres(
            where = where,
            restoreId = { view().restoreRequestId },
            settle = ::settle,
            before = { at ->
                assertRing("$at, before it", look)
                assertFalse("$at: before it Re-center does not show", pillShown())
            },
            after = { at ->
                assertTrue("$at: after it the camera follows the navigation again", view().following)
                assertNoRing("$at: following again after it", look)
                assertFalse("$at: after it Re-center does not show", pillShown())
                moveAway()
            },
        )
    }

    protected companion object {
        const val LOCATE = LOCATE_ROW
    }
}

/** The S22's landscape window (the L, the rail): "landscape" as Re-center's place beside the L reads it (`MapIconClusterState.landscape`). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class LocateRingReCenterLandscapeTest : LocateRingReCenterTests() {

    @Test
    fun `in landscape navigation moved away, by day, the locate row has a 1 dp Bark ring and Re-center does not show`() {
        setMapsTab(day)
        moveAway()
        assertRing("landscape navigation, moved away", day)
        assertFalse("landscape navigation, moved away: Re-center does not show, the ring replaces it", pillShown())
    }

    @Test
    fun `in landscape navigation moved away, at night, the locate row has a 1 dp pale ring and Re-center does not show`() {
        setMapsTab(night)
        moveAway()
        assertRing("landscape navigation, moved away", night)
        assertFalse("landscape navigation, moved away: Re-center does not show, the ring replaces it", pillShown())
    }

    @Test
    fun `in fullscreen landscape navigation moved away, the locate row is ringed and Re-center does not show`() {
        setMapsTab(day)
        enterFullscreen()
        moveAway()
        assertRing("fullscreen landscape navigation, moved away", day)
        assertFalse("fullscreen landscape navigation, moved away: Re-center does not show", pillShown())
    }

    @Test
    fun `real touches across the ringed locate row re-centre the navigation view, and the ring goes`() {
        setMapsTab(day)
        moveAway()
        assertTouchesReCentre("landscape navigation")
    }

    @Test
    fun `in landscape navigation while the camera follows, the locate row has no ring`() {
        setMapsTab(day)
        assertNoRing("landscape navigation, following", day)
        assertFalse("landscape navigation, following: no Re-center", pillShown())
    }

    @Test
    fun `in landscape not navigating, the locate row has no ring`() {
        setMapsTab(day, navigating = false)
        assertNoRing("landscape, not navigating", day)
    }
}

/** The S22's portrait window: the pill as now, no ring. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class LocateRingReCenterPortraitTest : LocateRingReCenterTests() {

    @Test
    fun `in portrait navigation moved away, by day, Re-center shows and the locate row has no ring`() {
        setMapsTab(day)
        moveAway()
        assertTrue("portrait navigation, moved away: Re-center shows, as now", pillShown())
        assertNoRing("portrait navigation, moved away", day)
    }

    @Test
    fun `in portrait navigation moved away, at night, Re-center shows and the locate row has no ring`() {
        setMapsTab(night)
        moveAway()
        assertTrue("portrait navigation, moved away: Re-center shows, as now", pillShown())
        assertNoRing("portrait navigation, moved away", night)
    }

    @Test
    fun `in portrait navigation while the camera follows, the locate row has no ring`() {
        setMapsTab(day)
        assertNoRing("portrait navigation, following", day)
    }
}
