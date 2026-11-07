package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.ForagerThemeWithNoSignal
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.map.runOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * Dispatch 2026-11-06-04 (RECORD -2186), the owner: "Add real quick: an outline around the re-enter button" (the map's
 * Re-center), "Same as the chips" (the option read: "1 dp, Bark #3B2E24 by day, pale #EDE3D0 at night, like the selected
 * map type chip's outline").
 *
 * The outline is read from the drawn pixels of Re-center on the real Maps tab, navigating and moved away from the
 * navigation view, inside `MainActivity`'s own root ([ForagerThemeWithNoSignal]) by day and with the app's Night Mode on.
 * The colours are written out as the ruled hex, not read from the code under test, so an outline drawn any other colour,
 * or none, fails. Each edge is read where the pill's edge crosses its middle row (left, right) and its centre column (top,
 * bottom). Where the edge there is straight (the top and bottom of the pill with its word), the outline is exactly
 * ceil(1 dp) wide; where it is a curve (its round ends, and every edge of the icon alone) anti-aliasing may soften its
 * outermost pixel, so at most that wide. The icon alone (D27, beside the L where its word does not fit) is composed on
 * its own inside the same root: the same surface draws both. (That form was reached on the Maps tab only beside the L in
 * landscape; since Amendments 2 and 3, RECORD -2186, the pill shows in landscape navigation only where its word fits at bottom
 * centre, so on the Maps tab it is no longer reached. The surface still draws it.)
 */
abstract class ReCenterOutlineTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.37, lng = -121.69, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(id = "origin", lat = 45.38, lng = -121.69, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1")

    private class Look(val name: String, val dark: Boolean, val outline: Color)

    /** The day `onSurface`, Bark. */
    private val day = Look("day", dark = false, outline = Color(0xFF3B2E24))

    /** The night `onSurface`, Cream. */
    private val night = Look("night", dark = true, outline = Color(0xFFEDE3D0))

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun px(dp: Float): Int = with(composeRule.density) { dp.dp.roundToPx() }

    /** ceil(1 dp) in pixels: what Foundation's border draws for a 1 dp stroke at this density. */
    private fun outlineWidthPx(): Int = with(composeRule.density) { ceil(1.dp.toPx()).toInt() }

    /** The real Maps tab, navigating and moved away from the navigation view, so Re-center shows. */
    private fun setMapsTab(look: Look) {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = look.dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = AbsentForecastCellStore,
                    uiStateTransform = { it.copy(liveFix = fix) },
                    navigation = AvailabilityScreenNavigation(isReturning = true, navigationTarget = origin),
                    recording = AvailabilityScreenRecording(isRecording = true),
                    currentTime = ROUTE_CARD_NOON_CLOCK,
                )
            }
        }
        settle()
        val view = map.renderMode?.navigationView ?: throw AssertionError("navigating, so the map was asked for its navigation view")
        composeRule.runOnUiThread { view.onLeftView() }
        settle()
    }

    /** Re-center's icon alone (D27), as it draws beside the L where its word does not fit, inside the same root. */
    private fun setIconAlone(look: Look) {
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = look.dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                Box(Modifier.padding(24.dp)) { ReturnToRoutePill(onClick = {}, roomPx = 0f) }
            }
        }
        settle()
    }

    /** The four edges, each read inwards from the pill's own edge, and whether the edge is straight there. */
    private fun edges(): List<Triple<String, List<Color>, Boolean>> {
        val pixels = composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val n = px(6f)
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        // The top and bottom are straight at the centre column only where the pill is wider than it is tall (its word shows).
        val topStraight = pixels.width > pixels.height
        return listOf(
            Triple("left edge", row.take(n), false),
            Triple("right edge", row.takeLast(n).reversed(), false),
            Triple("top edge", column.take(n), topStraight),
            Triple("bottom edge", column.takeLast(n).reversed(), topStraight),
        )
    }

    private fun assertOutline(where: String, look: Look) {
        val width = outlineWidthPx()
        for ((side, edge, straight) in edges()) {
            val run = runOf(edge, look.outline)
            assertNotNull("$where, ${look.name}, $side: a ${look.outline.hex()} outline is drawn; the edge reads ${edge.map { it.hex() }}", run)
            run!!
            val drawn = run.last - run.first + 1
            println("MEASURED $where, ${look.name}, $side: ${look.outline.hex()} for $drawn px from ${run.first} px in (ceil(1 dp) = $width px); the edge reads ${edge.map { it.hex() }}")
            if (straight) {
                assertEquals("$where, ${look.name}, $side: the outline is ceil(1 dp) = $width px wide; the edge reads ${edge.map { it.hex() }}", width, drawn)
            } else {
                assertTrue("$where, ${look.name}, $side: the outline is at most ceil(1 dp) = $width px wide on the curve, drew $drawn; the edge reads ${edge.map { it.hex() }}", drawn <= width)
            }
            assertTrue("$where, ${look.name}, $side: the outline is at the pill's own edge, ${run.first} px in", run.first <= px(1f))
        }
    }

    protected fun assertOutlineOnTheMapsTab(where: String, dark: Boolean) {
        val look = if (dark) night else day
        setMapsTab(look)
        val pill = composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("$where: Re-center's height is unchanged, the 48 dp target", 48f, (pill.bottom - pill.top).value, 0.5f)
        assertOutline(where, look)
    }

    protected fun assertOutlineOnTheIconAlone(dark: Boolean) {
        val look = if (dark) night else day
        setIconAlone(look)
        val pill = composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the icon alone: its width is unchanged, the 48 dp target", 48f, (pill.right - pill.left).value, 0.5f)
        assertEquals("the icon alone: its height is unchanged, the 48 dp target", 48f, (pill.bottom - pill.top).value, 0.5f)
        assertOutline("the icon alone", look)
    }

    /**
     * CLAUDE.md: a semantic click proves wiring, not routing. Real touches at five points across what the pill draws
     * ([ACROSS_ROUND], inside its round ends), each bringing the navigation view back. A guard that the outline changes
     * neither the pill's touch area nor what lies over it: it passes the same before and after the outline, by design.
     */
    protected fun assertTouchesReachIt(where: String) {
        setMapsTab(day)
        ACROSS_ROUND.forEach { (fx, fy) ->
            val before = map.renderMode!!.navigationView!!.restoreRequestId
            composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: a real touch at ($fx, $fy) on Re-center brings the view back", before + 1, map.renderMode!!.navigationView!!.restoreRequestId)
            composeRule.runOnUiThread { map.renderMode!!.navigationView!!.onLeftView() }
            settle()
        }
    }
}

/** The S22's portrait window: Re-center with its word at the bottom centre. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class ReCenterOutlinePortraitTest : ReCenterOutlineTests() {

    @Test
    fun `by day, Re-center has a 1 dp Bark outline`() = assertOutlineOnTheMapsTab("portrait", dark = false)

    @Test
    fun `at night, Re-center has a 1 dp pale outline`() = assertOutlineOnTheMapsTab("portrait", dark = true)

    @Test
    fun `by day, Re-center's icon alone has a 1 dp Bark outline`() = assertOutlineOnTheIconAlone(dark = false)

    @Test
    fun `at night, Re-center's icon alone has a 1 dp pale outline`() = assertOutlineOnTheIconAlone(dark = true)

    @Test
    fun `real touches across Re-center still reach it`() = assertTouchesReachIt("portrait")
}

/**
 * A 915 x 412 landscape phone: Re-center with its word at the bottom centre, inside the rail's padding. Dispatch 2026-11-06-04
 * Amendment 2 (RECORD -2186) had the ringed locate row replace it here; Amendment 3, the owner: "(b) Pill where it fits", brings it
 * back wherever its word at bottom centre covers no card, which it does not here (363..472 dp against the navigation card from 492 dp,
 * run 42405188355). The claims and names are pre-main's again.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class ReCenterOutlineLandscapeTest : ReCenterOutlineTests() {

    @Test
    fun `in landscape by day, Re-center has a 1 dp Bark outline`() = assertOutlineOnTheMapsTab("landscape", dark = false)

    @Test
    fun `in landscape at night, Re-center has a 1 dp pale outline`() = assertOutlineOnTheMapsTab("landscape", dark = true)

    @Test
    fun `in landscape, real touches across Re-center still reach it`() = assertTouchesReachIt("landscape")
}
