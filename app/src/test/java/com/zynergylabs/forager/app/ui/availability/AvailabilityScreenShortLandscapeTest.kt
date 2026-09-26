package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Landscape build step B1 (`docs/plans/landscape-phone-design.md`, P1-P3 and Resolutions R1,
 * R12-R18): a phone turned sideways — the S22 Ultra's real landscape window, `w823dp h384dp` —
 * is a *short* window, so it gets the compact tree rather than the wide one its 823dp width
 * would pick, and the bottom navigation bar becomes a `NavigationRail` on the charger-port edge
 * with the content laid out beside it.
 *
 * Everything is driven through the real [AvailabilityScreen]. The rail is identified by
 * geometry, not by a tag: its five labels form one vertical column on one side of the map, in
 * [CompactTab] order top to bottom. A bottom bar puts the same labels in one row, and the wide
 * tree has no "Journal" or "Tools" at all, so neither can pass for a rail here.
 *
 * **Rotation.** Robolectric reports `ROTATION_90` and `ROTATION_270` distinctly through
 * [ShadowDisplay.setRotation] (R10's question), and every rotation-specific test first proves the
 * screen itself read the pinned rotation — otherwise both rotations would run at `ROTATION_0` and
 * pass on the one sample that cannot tell the two port edges apart (CLAUDE.md).
 *
 * **Insets are zero under Robolectric** (CLAUDE.md, "Known pitfalls"), so nothing here checks an
 * inset value: which side the rail is on is checkable, how far it sits from the system bar is not.
 * Those are device items for B4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class AvailabilityScreenShortLandscapeTest {

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

    private val map = RecordingMapSlot()

    /** The rotation the screen's own view reported, read inside the composition — see [setScreen]. */
    private var rotationSeenByScreen: Int? = null

    private fun setScreen(rotation: Int, uiState: AvailabilityUiState = SEARCHED_STATE) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeenByScreen = LocalView.current.display?.rotation
            ShortLandscapeScreen(uiState = uiState, mapSlot = map.slot)
        }
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeenByScreen)
    }

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()

    private fun mapBounds(): DpRect = composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /** The rail's five labels, top to bottom, asserted to be one column in [RAIL_LABELS] order. */
    private fun assertRailColumn(): List<DpRect> {
        val labels = RAIL_LABELS.map { bounds(it) }
        val centreX = labels.first().centreX()
        labels.forEachIndexed { i, b ->
            assertEquals("${RAIL_LABELS[i]} is in the rail's one column", centreX, b.centreX(), 1f)
            if (i > 0) {
                assertTrue(
                    "${RAIL_LABELS[i]} (top ${b.top}) is below ${RAIL_LABELS[i - 1]} (top ${labels[i - 1].top})",
                    b.top > labels[i - 1].bottom,
                )
            }
        }
        val root = rootBounds()
        labels.forEachIndexed { i, b ->
            assertTrue("${RAIL_LABELS[i]} lies inside the window: $b in $root", b.top >= root.top && b.bottom <= root.bottom)
        }
        return labels
    }

    @Test
    fun `a short landscape window gets the compact tree, not the permanent drawer`() {
        setScreen(Surface.ROTATION_90)

        // The wide tree's permanent drawer shows Trip Planner and its own Photo Gallery entry
        // from the start; the compact tree keeps Trip Planner behind the closed Tools drawer and
        // has no Photo Gallery entry at all.
        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
        assertTrue(
            "the wide tree's Photo Gallery entry must not exist",
            composeRule.onAllNodesWithText("Photo Gallery").fetchSemanticsNodes().isEmpty(),
        )
        composeRule.onNodeWithTag(MAP_SLOT_TAG).assertIsDisplayed()
        RAIL_LABELS.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `at ROTATION_90 the rail is one column on the right, the port edge, with the map beside it`() {
        setScreen(Surface.ROTATION_90)

        val labels = assertRailColumn()
        val mapArea = mapBounds()
        val root = rootBounds()
        labels.forEach { assertTrue("rail label $it right of the map area $mapArea", it.left >= mapArea.right) }
        assertTrue("the rail is at the window's right: ${labels.first()} in $root", labels.first().centreX() > root.centreX())
        assertEquals("the map reaches the left edge (no inset under Robolectric)", root.left.value, mapArea.left.value, 0.5f)
        assertEquals("nothing reserves a band at the bottom", root.bottom.value, mapArea.bottom.value, 0.5f)
    }

    @Test
    fun `at ROTATION_270 the rail is one column on the left, the port edge, with the map beside it`() {
        setScreen(Surface.ROTATION_270)

        val labels = assertRailColumn()
        val mapArea = mapBounds()
        val root = rootBounds()
        labels.forEach { assertTrue("rail label $it left of the map area $mapArea", it.right <= mapArea.left) }
        assertTrue("the rail is at the window's left: ${labels.first()} in $root", labels.first().centreX() < root.centreX())
        assertEquals("the map reaches the right edge (no inset under Robolectric)", root.right.value, mapArea.right.value, 0.5f)
        assertEquals("nothing reserves a band at the bottom", root.bottom.value, mapArea.bottom.value, 0.5f)
    }

    @Test
    fun `a real touch on a rail item switches the tab, and the item shows as selected`() {
        setScreen(Surface.ROTATION_90, SEARCHED_STATE.copy(forecast = FORECAST, selectedMonth = LocalDate.now().monthValue))
        composeRule.onNodeWithText("Maps").assertIsSelected()

        composeRule.onRoot().performTouchInput { click(bounds("List").centre().toPx(this@AvailabilityScreenShortLandscapeTest)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("List").assertIsSelected()
        composeRule.onNodeWithText("artist's bracket").assertIsDisplayed()
        assertTrue("the map is gone with the Maps tab", composeRule.onAllNodesWithTag(MAP_SLOT_TAG).fetchSemanticsNodes().isEmpty())
        // Still the rail after the switch, not a bottom bar for the non-Map tabs.
        assertRailColumn()
    }

    @Test
    fun `at ROTATION_90 long-presses along the rail's inner edge reach the map`() {
        setScreen(Surface.ROTATION_90)
        val railInnerEdge = assertRailColumn().minOf { it.left }
        // The rail's own container reaches further in than its labels; its inner edge is the map
        // area's right edge, which the map sits flush against.
        val mapArea = mapBounds()
        assertEquals("the map area ends where the rail begins", mapArea.right.value, railInnerEdge.value, RAIL_LABEL_INSET_TOLERANCE)

        assertLongPressesReachMap(x = mapArea.right - EDGE_SAMPLE_INSET, mapArea = mapArea)
    }

    @Test
    fun `at ROTATION_270 long-presses along the rail's inner edge reach the map`() {
        setScreen(Surface.ROTATION_270)
        val railInnerEdge = assertRailColumn().maxOf { it.right }
        val mapArea = mapBounds()
        assertEquals("the map area begins where the rail ends", mapArea.left.value, railInnerEdge.value, RAIL_LABEL_INSET_TOLERANCE)

        assertLongPressesReachMap(x = mapArea.left + EDGE_SAMPLE_INSET, mapArea = mapArea)
    }

    /**
     * Planner's added check (ruling on the B1 stop, R13): in fullscreen the rail is gone and the
     * map area runs to the port edge, and a real long-press there reaches the map.
     */
    @Test
    fun `in fullscreen the rail is absent and a long-press at the map's port-side edge reaches the map`() {
        setScreen(Surface.ROTATION_90)
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        RAIL_LABELS.forEach { label ->
            assertTrue("rail label $label must be gone in fullscreen", composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty())
        }
        val mapArea = mapBounds()
        val root = rootBounds()
        assertEquals("the map runs to the port edge (right at ROTATION_90)", root.right.value, mapArea.right.value, 0.5f)

        assertLongPressesReachMap(x = mapArea.right - EDGE_SAMPLE_INSET, mapArea = mapArea)
    }

    /**
     * Long-presses at [SAMPLE_COUNT] heights down the column at [x], each a real touch. A point
     * that falls on the icon cluster (or its handle's band beside it) is a control, not the map,
     * so it is skipped — and the number actually sampled is asserted, so a layout that pushed
     * every point onto a control could not pass by sampling nothing.
     */
    private fun assertLongPressesReachMap(x: Dp, mapArea: DpRect) {
        val clusterNodes = composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes()
        val cluster = clusterNodes.firstOrNull()?.let { composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot() }
        val topChrome = composeRule.onAllNodesWithTag(SEARCH_ENTRY_BAR_TAG).fetchSemanticsNodes().firstOrNull()
            ?.let { composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).getUnclippedBoundsInRoot().bottom } ?: mapArea.top
        val top = maxOf(mapArea.top, topChrome) + EDGE_SAMPLE_INSET
        val bottom = mapArea.bottom - EDGE_SAMPLE_INSET
        var sampled = 0
        for (i in 0 until SAMPLE_COUNT) {
            val y = top + (bottom - top) * (i.toFloat() / (SAMPLE_COUNT - 1))
            if (cluster != null && y >= cluster.top - CLUSTER_MARGIN && y <= cluster.bottom + CLUSTER_MARGIN) continue
            val before = map.longPresses
            composeRule.onRoot().performTouchInput { longClick(DpPoint(x, y).toPx(this@AvailabilityScreenShortLandscapeTest)) }
            composeRule.waitForIdle()
            assertEquals("a long-press at ($x, $y) must reach the map", before + 1, map.longPresses)
            sampled++
        }
        assertTrue("at least $MIN_SAMPLED points were sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    private fun DpPoint.toPx(test: AvailabilityScreenShortLandscapeTest): Offset =
        with(test.composeRule.density) { Offset(x.toPx(), y.toPx()) }
}

/**
 * Turning the phone: portrait to short landscape keeps the selected tab (P15's stale-state
 * failure, checked for tab state). The window is turned by providing a landscape
 * [Configuration] to the same composition — the Activity handles orientation itself
 * (`AndroidManifest.xml`'s `configChanges`), so on a device the composition is not recreated
 * either; what changes is exactly what [LocalConfiguration] reports. The host window stays
 * portrait-shaped under Robolectric, so this test asserts on tab state and on which navigation
 * shows, not on landscape geometry — [AvailabilityScreenShortLandscapeTest] has that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-port")
class AvailabilityScreenTurnToShortLandscapeTest {

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

    private val map = RecordingMapSlot()

    @Test
    fun `turning from portrait to short landscape keeps the selected tab and leaves no bottom band`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        var landscape by mutableStateOf(false)
        composeRule.setContent {
            val base = LocalConfiguration.current
            val configuration = if (!landscape) base else Configuration(base).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = 823
                screenHeightDp = 384
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                ShortLandscapeScreen(uiState = SEARCHED_STATE, mapSlot = map.slot)
            }
        }
        composeRule.waitForIdle()

        // Portrait: the bottom bar is one row, and on the Map tab it has a real height, which the
        // screen hands the map as its attribution bottom inset.
        val portraitLabels = RAIL_LABELS.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        portraitLabels.forEach { assertEquals("portrait labels share one row", portraitLabels.first().centreY(), it.centreY(), 1f) }
        assertTrue("portrait: the bottom bar's band is handed to the map (${map.renderMode?.bottomInset})", (map.renderMode?.bottomInset ?: 0.dp) > 0.dp)

        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Cartography").assertIsDisplayed()

        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        landscape = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cartography").assertIsDisplayed()
        composeRule.onNodeWithText("Journal").assertIsSelected()
        val labels = RAIL_LABELS.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        labels.forEach { assertEquals("landscape labels are one column", labels.first().centreX(), it.centreX(), 1f) }

        // Back to the Map tab: the portrait bar's measured height must not survive as a bottom band.
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("no bottom band is handed to the map beside the rail", 0f, (map.renderMode?.bottomInset ?: (-1).dp).value, 0.01f)
    }
}

/**
 * Portrait is unchanged (required case `w360dp-h640dp`): the bottom bar, one row along the
 * bottom, no rail. A pin — it passes identically before and after B1, by design; the tests that
 * move under B1 are in [AvailabilityScreenShortLandscapeTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenPortraitBottomNavPinTest {

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

    private val map = RecordingMapSlot()

    @Test
    fun `a portrait phone keeps the bottom bar, one row along the bottom`() {
        composeRule.setContent { ShortLandscapeScreen(uiState = SEARCHED_STATE, mapSlot = map.slot) }
        composeRule.waitForIdle()

        val labels = RAIL_LABELS.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        labels.forEachIndexed { i, b ->
            assertEquals("${RAIL_LABELS[i]} is in the bar's one row", labels.first().centreY(), b.centreY(), 1f)
            if (i > 0) assertTrue("${RAIL_LABELS[i]} is right of ${RAIL_LABELS[i - 1]}", b.left > labels[i - 1].right)
        }
        assertTrue("the bar is at the bottom: ${labels.first()} in $root", labels.first().centreY() > (root.bottom - 100.dp).value)
    }
}

// ── shared fixtures ──

private const val MAP_SLOT_TAG = "short-landscape-map-slot"

/** [CompactTab]'s labels in its declared order — the rail's top-to-bottom order. */
private val RAIL_LABELS = listOf("List", "Seasonal", "Maps", "Journal", "Tools")

/** How far inside the map area each sample point sits from the edge being sampled. */
private val EDGE_SAMPLE_INSET = 4.dp

/** Vertical margin around the icon cluster within which a sample is treated as on a control. */
private val CLUSTER_MARGIN = 32.dp

/** A rail item's label is inset from the rail's own edge; this is how far, at most. */
private const val RAIL_LABEL_INSET_TOLERANCE = 40f

private const val SAMPLE_COUNT = 8
private const val MIN_SAMPLED = 4

private data class DpPoint(val x: Dp, val y: Dp)

private fun DpRect.centreX(): Float = ((left + right) / 2).value
private fun DpRect.centreY(): Float = ((top + bottom) / 2).value
private fun DpRect.centre(): DpPoint = DpPoint((left + right) / 2, (top + bottom) / 2)

/**
 * Stands in for the real map (see `AvailabilityScreenLayoutTest`'s `StubMapSlot` for why the real
 * one is not composed). Counts real long-presses landing on the map's own bounds, which is the
 * claim "a touch here reaches the map", and keeps the last [MapRenderMode] it was handed.
 */
private class RecordingMapSlot {
    var longPresses by mutableStateOf(0)
    var renderMode: MapRenderMode? = null

    val slot: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        this.renderMode = renderMode
        Box(
            modifier
                .testTag(MAP_SLOT_TAG)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) },
        )
    }
}

@androidx.compose.runtime.Composable
private fun ShortLandscapeScreen(uiState: AvailabilityUiState, mapSlot: MapSlot) {
    AvailabilityScreen(
        uiState = uiState,
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
        onOfflineMapLatChanged = {},
        onOfflineMapLngChanged = {},
        onOfflineMapRadiusChanged = {},
        onOfflineMapNameChanged = {},
        onOfflineMapsOpened = {},
        onDownloadOfflineMaps = {},
        onDeleteOfflineRegion = {},
        onNightModeMapsChanged = {},
        onThemeModeChanged = {},
        mapSlot = mapSlot,
    )
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 48473L,
    scientificName = "Ganoderma applanatum",
    commonName = "artist's bracket",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 14),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
)

private val FORECAST = AvailabilityForecast(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    entries = listOf(
        AvailabilityEntry(
            species = SpeciesObservationCount(
                taxonId = 48473L,
                scientificName = "Ganoderma applanatum",
                commonName = "artist's bracket",
                rank = "species",
                observationCount = 14,
                photoUrl = null,
                wikipediaUrl = null,
            ),
            relativeLikelihood = 1.0f,
        ),
    ),
)
