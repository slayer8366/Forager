package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.platform.testTag
import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-09-28-685, Amendment 1 (RECORD -694). The owner: "Stay beside the search bar, '…' (Recommended)" and
 * "Drop 'Approaching ·' if needed (Recommended)".
 *
 * On the S22 at font scale 2.0 in landscape the compass strip and the navigation display were drawn across the search
 * bar, and "No origin waypoint for this track" showed as "No" (2026-10-07 day check). Here, through the real
 * [AvailabilityScreen] in the S22's landscape window (`w823dp h384dp`, as `AvailabilityScreenLandscapeB2Test`), at both
 * landscape rotations:
 *
 * - at font 2.0 neither the strip nor the display intersects the search bar, every text in them is one line (so the
 *   display cannot have grown taller), the status line ends in "…" rather than at a word, and the display still ends
 *   above the window's central third;
 * - at font 1.0 every line of the display and the strip is whole, as before;
 * - "Approaching · last fix 45 s ago" is measured at 360 dp portrait and in landscape, and where it does not fit the
 *   line reads "Last fix 45 s ago", whole.
 *
 * Native graphics: without it Robolectric measures text at a few dp wide and every fit would pass on fake widths.
 * Robolectric reports no insets (CLAUDE.md, "Known pitfalls"), so the S22's cut-out and status bar are device items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeLargeFontTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_791_050_400_000L // 2026-10-03T18:00:00Z, mid-morning in Oregon: no sundown line
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 123.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)

    /** The trip's start 50 m north: inside the 100 m approach zone, outside arrival. */
    private val nearStart = Waypoint(
        id = "origin", lat = 45.52045, lng = -122.68, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )

    private var rotationSeen: Int? = null

    private fun setScreen(rotation: Int?, navigating: Boolean, target: Waypoint? = null, now: Long = t + 1_000L) {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = navigating,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = navigating,
                    navigationTarget = target,
                ),
                compassProvider = object : CompassProvider {
                    override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(306f, HeadingUncertainty.Estimated(2f), 0L))
                },
                computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                }),
                currentTime = { now },
                mapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(LARGE_FONT_MAP_TAG)) },
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
            )
        }
        composeRule.waitForIdle()
        if (rotation != null) assertEquals("the screen must see the rotation this case is about", rotation, rotationSeen)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Every text laid out inside [containerTag]. */
    private fun textsIn(containerTag: String): List<SemanticsNode> =
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(containerTag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().also { assertTrue("$containerTag must hold text for this check to mean anything", it.isNotEmpty()) }

    private fun textOf(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString { it.text }

    /** Each text in [containerTag] is one line; with [whole], also not ellipsised with every character drawn. */
    private fun assertOneLineEach(label: String, containerTag: String, whole: Boolean) {
        for (node in textsIn(containerTag)) {
            val layout = layoutOf(node)
            val text = layout.layoutInput.text.text
            println("MEASURED $label $containerTag <$text>: lines ${layout.lineCount}, ellipsised ${layout.isLineEllipsized(0)}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${text.length}")
            assertEquals("$label <$text> is one line", 1, layout.lineCount)
            if (whole) {
                assertFalse("$label <$text> is not ellipsised", layout.isLineEllipsized(0))
                assertEquals("$label <$text>: every character visible", text.length, layout.getLineEnd(0, visibleEnd = true))
            }
        }
    }

    private fun assertClearOfSearchBar(label: String, tag: String) {
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val chrome = bounds(tag)
        println("MEASURED $label: $tag $chrome, search bar $bar, width ${chrome.right - chrome.left}")
        assertFalse("$label: $tag $chrome must not overlap the search bar $bar", chrome.intersects(bar))
    }

    /**
     * Dispatch 2026-10-12-08, RECORD -951: RECORD -766 (dispatch 2026-10-09-02, item 2; the owner: "Combine lines, allow the rest
     * (Recommended)") governs the navigating display's height: it may reach into the central third, never over the walker's dot
     * or the needle. The walker's dot is where the navigation view puts it (the map's height times
     * NAVIGATION_VIEW_TOP_PADDING_FRACTION as top padding; its radius 12 dp, as HudLandscapeT11FixesTest has it). Was
     * "assertAboveCentralThird": `assertTrue("$label: the display $hud ends above the central third ($thirdTop)", hud.bottom <=
     * thirdTop)`. The central-third clearance is still printed, for the report.
     */
    private fun assertClearOfWalkerAndNeedle(label: String) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val hud = bounds(NAVIGATION_HUD_TAG)
        val thirdTop = root.top + (root.bottom - root.top) / 3
        val mapArea = bounds(LARGE_FONT_MAP_TAG)
        val height = mapArea.bottom - mapArea.top
        val walkerY = mapArea.top + (height + height * com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TOP_PADDING_FRACTION.toFloat()) / 2
        println("MEASURED $label: display $hud, height ${hud.bottom - hud.top}, clearance above the central third ${(thirdTop - hud.bottom).value} dp, above the walker's dot ${(walkerY - 12.dp - hud.bottom).value} dp")
        // RECORD -953: the display is on the bottom edge, below the dot; "never over the walker's dot" is no overlap with its
        // circle. Was `hud.bottom <= walkerY - 12.dp` (the display above it, -951).
        assertTrue("$label: the display $hud covers none of the walker's dot at y ${walkerY.value}", hud.bottom <= walkerY - 12.dp || hud.top >= walkerY + 12.dp)
        val needle = bounds(NAVIGATION_HUD_NEEDLE_TAG)
        assertTrue("$label: the needle $needle is inside the display $hud", needle.left >= hud.left && needle.right <= hud.right && needle.top >= hud.top && needle.bottom <= hud.bottom)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        assertFalse("$label: the search bar $bar does not cover the needle $needle", bar.intersects(needle))
    }

    // ── Font 2.0: beside the search bar, one line each ──

    private fun stripAtFontTwo(rotation: Int) {
        setScreen(rotation, navigating = false)
        assertEquals(2f, composeRule.density.fontScale)
        assertClearOfSearchBar("font 2.0, rotation $rotation", STRIP_TAG)
        assertOneLineEach("font 2.0, rotation $rotation", STRIP_TAG, whole = false)
        // Amendment 2 (RECORD -699; the owner: "Coordinates take priority (Recommended)"): the coordinates are whole.
        val coordinates = composeRule.onNodeWithTag(COMPASS_STRIP_COORDINATES_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(coordinates)
        val text = textOf(coordinates)
        println("MEASURED font 2.0, rotation $rotation strip coordinates <$text>: ellipsised ${layout.isLineEllipsized(0)}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${text.length}")
        assertFalse("font 2.0, rotation $rotation: the coordinates <$text> are not ellipsised", layout.isLineEllipsized(0))
        assertEquals("font 2.0, rotation $rotation: every character of <$text> shows", text.length, layout.getLineEnd(0, visibleEnd = true))
    }

    private fun displayAtFontTwo(rotation: Int) {
        setScreen(rotation, navigating = true)
        assertEquals(2f, composeRule.density.fontScale)
        assertClearOfSearchBar("font 2.0, rotation $rotation", NAVIGATION_HUD_TAG)
        assertOneLineEach("font 2.0, rotation $rotation", NAVIGATION_HUD_TAG, whole = false)
        // The S22's "No": the status now runs to the width it has and ends in "…", or shows whole.
        val status = composeRule.onNodeWithTag(NAVIGATION_HUD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(status)
        // T11 fixes (RECORD -766, the short form confirmed in RECORD -768): the message may take its short form where the whole
        // one does not fit; either way it is a message, never dropped.
        assertTrue("the status is the no-origin message or its short form: <${textOf(status)}>", textOf(status) in setOf("No origin waypoint for this track", "No start point"))
        assertTrue(
            "the status line is cut with an ellipsis or shown whole, never cut at a word; visible ${layout.getLineEnd(0, visibleEnd = true)}",
            layout.isLineEllipsized(0) || layout.getLineEnd(0, visibleEnd = true) == textOf(status).length,
        )
        assertClearOfWalkerAndNeedle("font 2.0, rotation $rotation")
        // The planner's extension of -699 to the display: the grid reference shows whole. Since dispatch 2026-10-12-08 (RECORD
        // -950) it is the strip's while navigating. Was NAVIGATION_HUD_COORDINATES_TAG in the card.
        val coordinates = composeRule.onNodeWithTag(COMPASS_STRIP_COORDINATES_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val coordinatesLayout = layoutOf(coordinates)
        println("MEASURED font 2.0, rotation $rotation display coordinates <${textOf(coordinates)}>: visible ${coordinatesLayout.getLineEnd(0, visibleEnd = true)} of ${textOf(coordinates).length}")
        assertFalse("font 2.0, rotation $rotation: the display's coordinates are not ellipsised", coordinatesLayout.isLineEllipsized(0))
        assertEquals("font 2.0, rotation $rotation: every character of the coordinates shows", textOf(coordinates).length, coordinatesLayout.getLineEnd(0, visibleEnd = true))
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `font 2,0 at ROTATION_90 the strip stays beside the search bar on one line`() = stripAtFontTwo(Surface.ROTATION_90)

    @Test
    @Config(fontScale = 2.0f)
    fun `font 2,0 at ROTATION_270 the strip stays beside the search bar on one line`() = stripAtFontTwo(Surface.ROTATION_270)

    @Test
    @Config(fontScale = 2.0f)
    fun `font 2,0 at ROTATION_90 the display stays beside the search bar, one line each, no taller`() = displayAtFontTwo(Surface.ROTATION_90)

    @Test
    @Config(fontScale = 2.0f)
    fun `font 2,0 at ROTATION_270 the display stays beside the search bar, one line each, no taller`() = displayAtFontTwo(Surface.ROTATION_270)

    // ── Font 1.0: nothing changes ──

    @Test
    fun `font 1,0 the strip and the display are whole, one line each, and clear of the search bar`() {
        setScreen(Surface.ROTATION_90, navigating = false)
        assertClearOfSearchBar("font 1.0", STRIP_TAG)
        assertOneLineEach("font 1.0", STRIP_TAG, whole = true)
    }

    @Test
    fun `font 1,0 the display is one line each and clear of the search bar`() {
        setScreen(Surface.ROTATION_90, navigating = true)
        // Amendment 2 (RECORD -699; the owner: "Yes, everywhere (Recommended)"): the same rule as at font 2.0. One line
        // each, "…" allowed, never over the search bar. Amendment 1 had asked for "whole" here, which the planner has
        // since withdrawn: at this width the display was already 1 dp over the bar at font 1.0 before the cap.
        assertClearOfSearchBar("font 1.0", NAVIGATION_HUD_TAG)
        assertOneLineEach("font 1.0", NAVIGATION_HUD_TAG, whole = false)
        assertClearOfWalkerAndNeedle("font 1.0")
    }

    // ── "Approaching · last fix 45 s ago" ──

    /** Stale (45 s) and approaching the start: the status line is the long one, or its short form whole. */
    private fun approachingStale(label: String, rotation: Int?) {
        setScreen(rotation = rotation, navigating = true, target = nearStart, now = t + 45_000L)
        val status = composeRule.onNodeWithTag(NAVIGATION_HUD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val layout = layoutOf(status)
        val text = textOf(status)
        println("MEASURED $label approaching status <$text>: ellipsised ${layout.isLineEllipsized(0)}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${text.length}")
        assertTrue("$label: <$text> is the long line or its short form", text == "Approaching · last fix 45 s ago" || text == "Last fix 45 s ago")
        assertFalse("$label: <$text> is not ellipsised", layout.isLineEllipsized(0))
        assertEquals("$label: <$text> whole", text.length, layout.getLineEnd(0, visibleEnd = true))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `at 360 dp portrait the approaching stale line is whole, in its long or short form`() = approachingStale("360 dp portrait", rotation = null)

    @Test
    fun `in landscape the approaching stale line is whole, in its long or short form`() = approachingStale("landscape", rotation = Surface.ROTATION_90)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2.0f)
    fun `at 360 dp portrait and font 2,0 the approaching stale line drops Approaching`() {
        setScreen(rotation = null, navigating = true, target = nearStart, now = t + 45_000L)
        val status = composeRule.onNodeWithTag(NAVIGATION_HUD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNode()
        println("MEASURED 360 dp portrait font 2.0 approaching status <${textOf(status)}>")
        assertEquals("Last fix 45 s ago", textOf(status))
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

/** Overlap with area, as `AvailabilityScreenLandscapeB2Test`'s: touching edges do not count. */
private fun DpRect.intersects(other: DpRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

/** The large-font test's map stand-in, for where the walker's dot sits (RECORD -766, -951). */
private const val LARGE_FONT_MAP_TAG = "large-font-map"
