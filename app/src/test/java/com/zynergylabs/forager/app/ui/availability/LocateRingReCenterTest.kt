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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.ForagerThemeWithNoSignal
import com.zynergylabs.forager.app.ui.map.NavigationViewRequest
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OUTLINE_DAY
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OUTLINE_NIGHT
import com.zynergylabs.forager.app.ui.map.firstNotUnder
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.map.outlineOver
import com.zynergylabs.forager.app.ui.map.outlineRunOf
import com.zynergylabs.forager.app.ui.map.ringBand
import com.zynergylabs.forager.app.ui.map.ringUnder
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
 *
 * **Amendment 4 (RECORD -2186) changes the colour the ring is read for.** The owner, to the ring: "Yes, same outline": it takes
 * the map chrome's own outline, the bubbles' and the icon bar's ([com.zynergylabs.forager.app.ui.map.mapChromeOutline]: 1 dp,
 * Bark at 40% by day, white at 40% at night). That line is translucent, so each side is read for the ruled colour composited
 * over the row's own fill just inside the ring (two pixels past its place), as `MapChromeOutlineRead.kt` describes, and "no ring" now reads the
 * ring's place (6 to 7 dp in, a pixel either side) as that fill alone, so a ring of any colour fails it. Until Amendment 4 these
 * read the solid `onSurface` (#3B2E24 / #EDE3D0) and "no ring" looked for that colour only. When the ring shows, where and how
 * wide, and every touch, are unchanged.
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

    /** By day the map chrome's outline, Bark at 40% (Amendment 4). */
    protected val day = Look("day", dark = false, ring = MAP_CHROME_OUTLINE_DAY)

    /** At night the map chrome's outline, white at 40% (Amendment 4). */
    protected val night = Look("night", dark = true, ring = MAP_CHROME_OUTLINE_NIGHT)

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
        val band = ringBand(::px)
        for ((side, edge) in sides()) {
            val under = edge[ringUnder(::px)]
            val expected = outlineOver(look.ring, under)
            val run = outlineRunOf(edge, look.ring, under, from = band.first, to = band.last)
            assertNotNull("$where, ${look.name}, locate row, $side side: the map chrome's outline is drawn as the ring, ${expected.hex()} over the fill ${under.hex()}; the edge reads ${edge.map { it.hex() }}", run)
            run!!
            val drawn = run.last - run.first + 1
            println("MEASURED $where, ${look.name}, locate row, $side side: ${expected.hex()} (over ${under.hex()}) for $drawn px from ${run.first} px in (6 dp = ${px(6f)} px, ceil(1 dp) = $width px); the edge reads ${edge.map { it.hex() }}")
            assertTrue("$where, ${look.name}, $side side: the ring is ceil(1 dp) = $width px wide, its outer pixel at most softened, drew $drawn", drawn in (width - 1)..width)
            assertTrue("$where, ${look.name}, $side side: the ring sits on the badge circle, 6 dp (${px(6f)} px) in, found ${run.first} px in", run.first in (px(6f) - 1)..(px(7f)))
        }
    }

    protected fun assertNoRing(where: String, look: Look) {
        val band = ringBand(::px)
        for ((side, edge) in sides()) {
            val under = edge[ringUnder(::px)]
            val off = firstNotUnder(edge, under, band.first, band.last)
            assertNull("$where, ${look.name}, locate row, $side side: no ring, the ring's place ($band px in) is the fill ${under.hex()} alone; ${off?.let { "$it px in reads ${edge[it].hex()}" }}; the edge reads ${edge.map { it.hex() }}", off)
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

    /**
     * Dispatch 2026-11-06-04, Amendment 3 (RECORD -2186), the owner: "(b) Pill where it fits". Where Re-center's word fits at bottom
     * centre the pill shows and the locate row has no ring, and the locate row's tap does what it did before Amendment 2: while
     * navigating it brings the navigation view back (Ruling D). The same five real touches across the row
     * ([locateRowTouchFractions]), with the pill there and no ring before each, and the camera following after it.
     */
    protected fun assertTouchesReCentreBesideThePill(where: String, look: Look = day) {
        composeRule.assertLocateRowReCentres(
            where = where,
            restoreId = { view().restoreRequestId },
            settle = ::settle,
            before = { at ->
                assertNoRing("$at, before it", look)
                assertTrue("$at: before it Re-center shows, its word fitting at bottom centre", pillShown())
            },
            after = { at ->
                assertTrue("$at: after it the camera follows the navigation again", view().following)
                assertNoRing("$at: following again after it", look)
                moveAway()
            },
        )
    }

    /** Amendment 3: the pill's bounds, for a test that reads where it is. */
    protected fun pillBounds() = composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Amendment 3: the L's bounds. */
    protected fun lBounds() = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Amendment 3: the screen's bounds. */
    protected fun screenBounds() = composeRule.onRoot().getUnclippedBoundsInRoot()

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
    fun `in landscape navigation moved away, by day, the locate row has the map chrome's 1 dp outline as its ring and Re-center does not show`() {
        setMapsTab(day)
        moveAway()
        assertRing("landscape navigation, moved away", day)
        assertFalse("landscape navigation, moved away: Re-center does not show, the ring replaces it", pillShown())
    }

    @Test
    fun `in landscape navigation moved away, at night, the locate row has the map chrome's 1 dp outline as its ring and Re-center does not show`() {
        setMapsTab(night)
        moveAway()
        assertRing("landscape navigation, moved away", night)
        assertFalse("landscape navigation, moved away: Re-center does not show, the ring replaces it", pillShown())
    }

    /**
     * Amendment 3 (RECORD -2186, the owner: "(b) Pill where it fits"). In fullscreen the rail leaves and the navigation card moves
     * with it to the screen's edge (from 396.3 dp to 476.3 dp here), so Re-center's word at bottom centre (355.7..467.3 dp) covers no
     * card and the pill shows, with no ring; measured on run 42409624489. Out of fullscreen it covers the card, so the ring (above).
     * Until Amendment 3 this read the ring here too.
     */
    @Test
    fun `in fullscreen landscape navigation moved away, the word fits at bottom centre, so Re-center shows and the locate row has no ring`() {
        setMapsTab(day)
        enterFullscreen()
        moveAway()
        assertTrue("fullscreen landscape navigation, moved away: Re-center shows, its word fitting at bottom centre", pillShown())
        val pill = pillBounds()
        assertTrue("fullscreen landscape navigation: Re-center is not beside the L (${lBounds().right}); its left is ${pill.left}", pill.left > lBounds().right + 16.dp)
        assertNoRing("fullscreen landscape navigation, moved away", day)
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

/**
 * Dispatch 2026-11-06-04, Amendment 3 (RECORD -2186). The owner: "Tablets keep the pill", then "(b) Pill where it fits" (the option:
 * "A real fit test: where the Re-center word fits at bottom centre without covering a card, the pill shows there and there's no ring;
 * where it doesn't fit, the ring"). In the windows below the word at bottom centre covers no card (measured on run 42405188355), so
 * in landscape navigation moved away the pill shows at bottom centre, the locate row has no ring, and the locate row's tap
 * re-centres as it did before Amendment 2. The S22's own window, where it does cover the navigation card, is
 * [LocateRingReCenterLandscapeTest]: the ring, as Amendment 2.
 */
abstract class LocateRingReCenterFitsTests : LocateRingReCenterTests() {

    private fun assertPillAtBottomCentre(where: String) {
        assertTrue("$where: Re-center shows, its word fitting at bottom centre", pillShown())
        val pill = pillBounds()
        val l = lBounds()
        val screen = screenBounds()
        println("A3 $where: pill ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, L ${l.left.value}..${l.right.value}, screen ${screen.right.value} x ${screen.bottom.value}")
        assertTrue("$where: Re-center is not beside the L (D17's place, within 16 dp of the L's right ${l.right}); its left is ${pill.left}", pill.left > l.right + 16.dp)
        assertTrue("$where: Re-center keeps its word (wider than the 48 dp icon alone), ${pill.right - pill.left}", pill.right - pill.left > 60.dp)
    }

    @Test
    fun `in landscape navigation moved away, by day, Re-center shows at bottom centre and the locate row has no ring`() {
        setMapsTab(day)
        moveAway()
        assertPillAtBottomCentre("landscape navigation, moved away, day")
        assertNoRing("landscape navigation, moved away", day)
    }

    @Test
    fun `in landscape navigation moved away, at night, Re-center shows at bottom centre and the locate row has no ring`() {
        setMapsTab(night)
        moveAway()
        assertPillAtBottomCentre("landscape navigation, moved away, night")
        assertNoRing("landscape navigation, moved away", night)
    }

    @Test
    fun `in fullscreen landscape navigation moved away, Re-center shows and the locate row has no ring`() {
        setMapsTab(day)
        enterFullscreen()
        moveAway()
        assertPillAtBottomCentre("fullscreen landscape navigation, moved away")
        assertNoRing("fullscreen landscape navigation, moved away", day)
    }

    @Test
    fun `in landscape navigation, real touches across the locate row re-centre as before, beside the pill`() {
        setMapsTab(day)
        moveAway()
        assertTouchesReCentreBesideThePill("landscape navigation")
    }
}

/** A tablet in landscape, the window `RouteCardRecenterTabletLandscapeTest` uses: the word at 565..673 dp, the cards from 894.5 dp. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class LocateRingReCenterTabletLandscapeTest : LocateRingReCenterFitsTests()

/** A 915 x 412 landscape phone: the word at 363..472 dp, the cards from 492 dp. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class LocateRingReCenterWidePhoneLandscapeTest : LocateRingReCenterFitsTests()
