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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.width
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
 * R12-R18, with R12, R13 and R17 as revised on the owner's correction): a phone turned sideways —
 * the S22 Ultra's real landscape window, `w823dp h384dp` — is a *short* window, so it gets the
 * compact tree rather than the wide one its 823dp width would pick, and the bottom navigation bar
 * becomes a `NavigationRail` on the charger-port edge.
 *
 * On the Map tab the rail is an overlay: the map stays full-bleed and never changes size when the
 * rail hides ("the map resizes when hiding the UI and that's a UX problem" — the owner), and the
 * map's controls are padded clear of the rail instead. On every other tab the rail is opaque and
 * the content lies beside it.
 *
 * Everything is driven through the real [AvailabilityScreen]. The rail's five labels are also
 * checked as geometry — one vertical column, in [CompactTab] order top to bottom — because a bottom
 * bar puts the same labels in one row, and the wide tree has no "Journal" or "Tools" at all.
 *
 * **Rotation.** Robolectric reports `ROTATION_90` and `ROTATION_270` distinctly through
 * [ShadowDisplay.setRotation] (R10's question), and every rotation-specific test first proves the
 * screen itself read the pinned rotation — otherwise both rotations would run at `ROTATION_0` and
 * pass on the one sample that cannot tell the two port edges apart (CLAUDE.md).
 *
 * **Insets are zero under Robolectric** (CLAUDE.md, "Known pitfalls"), so nothing here checks an
 * inset value: which side the rail is on, and that the controls clear it, is checkable; how far
 * the rail sits from the system bar, and the cut-out padding, are not. Those are device items (B4).
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

    private fun railBounds(): DpRect = composeRule.onNodeWithTag(RAIL_TAG).getUnclippedBoundsInRoot()

    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    private fun railExists(): Boolean = composeRule.onAllNodesWithTag(RAIL_TAG).fetchSemanticsNodes().isNotEmpty()

    /** The rail's five labels, top to bottom, asserted to be one column in [RAIL_LABELS] order, inside the rail. */
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
        val rail = railBounds()
        val root = rootBounds()
        labels.forEachIndexed { i, b ->
            assertTrue("${RAIL_LABELS[i]} lies inside the rail: $b in $rail", b.isInside(rail))
            assertTrue("${RAIL_LABELS[i]} lies inside the window: $b in $root", b.top >= root.top && b.bottom <= root.bottom)
        }
        return labels
    }

    /** The map is full-bleed: the whole window's width, down to the bottom edge. */
    private fun assertMapFullBleed() {
        val mapArea = mapBounds()
        val root = rootBounds()
        assertEquals("the map reaches the window's left edge", root.left.value, mapArea.left.value, 0.5f)
        assertEquals("the map reaches the window's right edge", root.right.value, mapArea.right.value, 0.5f)
        assertEquals("nothing reserves a band at the bottom", root.bottom.value, mapArea.bottom.value, 0.5f)
    }

    /**
     * Every node a finger can activate — anything with a click action, in the unmerged tree — that
     * is not part of the rail itself, checked against the rail's own bounds. Generic on purpose:
     * "no control sits under the rail" is a claim about every control, not a list of the ones
     * somebody remembered, and the count checked is reported so an empty sample cannot pass.
     */
    private fun assertNoControlUnderRail() {
        val rail = railBounds()
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filterNot { it.isInsideRail() }
        assertTrue("there are controls on the map to check (found ${controls.size})", controls.size >= MIN_MAP_CONTROLS)
        val overlapping = controls.map { it to it.boundsInRoot.toDp() }.filter { (_, b) -> b.intersects(rail) }
        assertTrue(
            "no control may sit under the rail $rail; these do: " +
                overlapping.joinToString { (n, b) -> "${n.config.getOrNull(SemanticsProperties.ContentDescription) ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: n.id} $b" },
            overlapping.isEmpty(),
        )
    }

    private fun SemanticsNode.isInsideRail(): Boolean {
        var node: SemanticsNode? = this
        while (node != null) {
            if (node.config.getOrNull(SemanticsProperties.TestTag) == RAIL_TAG) return true
            node = node.parent
        }
        return false
    }

    private fun androidx.compose.ui.geometry.Rect.toDp(): DpRect = with(composeRule.density) {
        DpRect(left.toDp(), top.toDp(), right.toDp(), bottom.toDp())
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
    fun `at ROTATION_90 the Map tab's rail overlays the map on the right, the port edge, and the map is full-bleed`() {
        setScreen(Surface.ROTATION_90)

        assertRailColumn()
        val rail = railBounds()
        val root = rootBounds()
        assertEquals("the rail is on the window's right edge", root.right.value, rail.right.value, 0.5f)
        assertTrue("the rail is a narrow column, not a bar: $rail", rail.width < root.width / 4)
        assertMapFullBleed()
        assertTrue("the rail lies over the map: $rail in ${mapBounds()}", rail.isInside(mapBounds()))
    }

    @Test
    fun `at ROTATION_270 the Map tab's rail overlays the map on the left, the port edge, and the map is full-bleed`() {
        setScreen(Surface.ROTATION_270)

        assertRailColumn()
        val rail = railBounds()
        val root = rootBounds()
        assertEquals("the rail is on the window's left edge", root.left.value, rail.left.value, 0.5f)
        assertTrue("the rail is a narrow column, not a bar: $rail", rail.width < root.width / 4)
        assertMapFullBleed()
        assertTrue("the rail lies over the map: $rail in ${mapBounds()}", rail.isInside(mapBounds()))
    }

    /** Planner's added check (owner's correction): the cluster defaults to the right, the rail's side at ROTATION_90. */
    @Test
    fun `at ROTATION_90 no control on the map sits under the rail`() {
        setScreen(Surface.ROTATION_90)
        composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).assertIsDisplayed()

        assertNoControlUnderRail()
    }

    @Test
    fun `at ROTATION_270 no control on the map sits under the rail`() {
        setScreen(Surface.ROTATION_270)

        assertNoControlUnderRail()
    }

    /** The owner's point, as a check that can fail on it: hiding the rail never resizes the map. */
    @Test
    fun `the map's measured bounds are identical with the rail shown and hidden`() {
        setScreen(Surface.ROTATION_90)
        assertTrue("the rail is showing to begin with", railExists())
        val shown = mapBounds()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("the rail is gone in fullscreen", !railExists())
        val hidden = mapBounds()

        assertEquals("left", shown.left.value, hidden.left.value, 0.01f)
        assertEquals("top", shown.top.value, hidden.top.value, 0.01f)
        assertEquals("right", shown.right.value, hidden.right.value, 0.01f)
        assertEquals("bottom", shown.bottom.value, hidden.bottom.value, 0.01f)
    }

    @Test
    fun `on another tab the rail is opaque beside the content, and a real touch on a rail item switches the tab`() {
        setScreen(Surface.ROTATION_90, SEARCHED_STATE.copy(forecast = FORECAST, selectedMonth = LocalDate.now().monthValue))
        composeRule.onNodeWithText("Maps").assertIsSelected()

        composeRule.onRoot().performTouchInput { click(bounds("List").centre().toPx(this@AvailabilityScreenShortLandscapeTest)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("List").assertIsSelected()
        composeRule.onNodeWithText("artist's bracket").assertIsDisplayed()
        assertTrue("the map is gone with the Maps tab", composeRule.onAllNodesWithTag(MAP_SLOT_TAG).fetchSemanticsNodes().isEmpty())
        assertRailColumn()
        val rail = railBounds()
        assertEquals("still on the port edge", rootBounds().right.value, rail.right.value, 0.5f)
        val listRow = bounds("artist's bracket")
        assertTrue("the list lies beside the rail, not under it: $listRow vs $rail", listRow.right <= rail.left)
    }

    @Test
    fun `at ROTATION_90 long-presses beside the rail, along its inner edge, reach the map`() {
        setScreen(Surface.ROTATION_90)
        val rail = railBounds()

        assertLongPressesReachMap(x = rail.left - EDGE_SAMPLE_INSET, mapArea = mapBounds())
    }

    @Test
    fun `at ROTATION_270 long-presses beside the rail, along its inner edge, reach the map`() {
        setScreen(Surface.ROTATION_270)
        val rail = railBounds()

        assertLongPressesReachMap(x = rail.right + EDGE_SAMPLE_INSET, mapArea = mapBounds())
    }

    /** Planner's added check: the rail is translucent over the map, but a touch on it is the rail's. */
    @Test
    fun `a long-press on the translucent rail's own area selects the rail item, not the map`() {
        setScreen(Surface.ROTATION_90, SEARCHED_STATE.copy(forecast = FORECAST, selectedMonth = LocalDate.now().monthValue))
        val before = map.longPresses
        // The point pressed must be the rail's own area, over the map — not merely something
        // labelled "List" (the wide tree's tab row has one too, and pressing it also leaves the
        // map alone, which is how the first version of this test passed before the rail existed).
        val point = bounds("List").centre()
        val rail = railBounds()
        val mapArea = mapBounds()
        assertTrue("the pressed point $point is on the rail $rail", point.x >= rail.left && point.x <= rail.right && point.y >= rail.top && point.y <= rail.bottom)
        assertTrue("the pressed point $point is over the map $mapArea", point.x >= mapArea.left && point.x <= mapArea.right && point.y >= mapArea.top && point.y <= mapArea.bottom)

        composeRule.onRoot().performTouchInput { longClick(point.toPx(this@AvailabilityScreenShortLandscapeTest)) }
        composeRule.waitForIdle()

        assertEquals("the map must not receive a long-press made on the rail", before, map.longPresses)
        composeRule.onNodeWithText("List").assertIsSelected()
    }

    /**
     * Planner's added check (R13): in fullscreen the rail is gone, the map keeps its size, and a
     * real long-press at the map's port-side edge reaches the map.
     */
    @Test
    fun `in fullscreen the rail is absent and a long-press at the map's port-side edge reaches the map`() {
        setScreen(Surface.ROTATION_90)
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertTrue("the rail must be gone in fullscreen", !railExists())
        RAIL_LABELS.forEach { label ->
            assertTrue("rail label $label must be gone in fullscreen", composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty())
        }
        assertMapFullBleed()
        val mapArea = mapBounds()

        assertLongPressesReachMap(x = mapArea.right - EDGE_SAMPLE_INSET, mapArea = mapArea)
    }

    /**
     * Long-presses at [SAMPLE_COUNT] heights down the column at [x], each a real touch. A point
     * that falls inside a control — the icon cluster, or any node with a click action — is a
     * control, not the map, so it is skipped; a point merely *beside* one is not. The number
     * actually sampled is asserted, so a layout that put every point on a control could not pass
     * by sampling nothing (the first version of this skipped by height alone, and sampled zero:
     * in a 384dp window the cluster spans nearly the whole height).
     */
    private fun assertLongPressesReachMap(x: Dp, mapArea: DpRect) {
        val controls = buildList {
            composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().firstOrNull()
                ?.let { add(composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()) }
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .forEach { add(it.boundsInRoot.toDp()) }
        }
        val topChrome = composeRule.onAllNodesWithTag(SEARCH_ENTRY_BAR_TAG).fetchSemanticsNodes().firstOrNull()
            ?.let { composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).getUnclippedBoundsInRoot().bottom } ?: mapArea.top
        val top = maxOf(mapArea.top, topChrome) + EDGE_SAMPLE_INSET
        val bottom = mapArea.bottom - EDGE_SAMPLE_INSET
        var sampled = 0
        for (i in 0 until SAMPLE_COUNT) {
            val y = top + (bottom - top) * (i.toFloat() / (SAMPLE_COUNT - 1))
            if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
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

/** The rail's own container tag — a literal here, so this file states the contract rather than borrowing it. */
private const val RAIL_TAG = "compact-navigation-rail"

/** The fewest click-actionable nodes the Map tab can have (the cluster alone has more); fewer means nothing was checked. */
private const val MIN_MAP_CONTROLS = 5

/** [CompactTab]'s labels in its declared order — the rail's top-to-bottom order. */
private val RAIL_LABELS = listOf("List", "Seasonal", "Maps", "Journal", "Tools")

/** How far inside the map area each sample point sits from the edge being sampled. */
private val EDGE_SAMPLE_INSET = 4.dp

private const val SAMPLE_COUNT = 8
private const val MIN_SAMPLED = 4

private data class DpPoint(val x: Dp, val y: Dp)

private fun DpRect.centreX(): Float = ((left + right) / 2).value
private fun DpRect.centreY(): Float = ((top + bottom) / 2).value
private fun DpRect.centre(): DpPoint = DpPoint((left + right) / 2, (top + bottom) / 2)

/** Whether this rect lies wholly inside [outer]. */
private fun DpRect.isInside(outer: DpRect): Boolean =
    left >= outer.left && top >= outer.top && right <= outer.right && bottom <= outer.bottom

private fun DpRect.intersects(other: DpRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

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
