package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
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
 * Landscape build B3, P12 as corrected by R2 (`docs/plans/landscape-phone-design.md`) and decided
 * in `docs/audits/2026-09-27-landscape-b3-prebuild-report.md` section 5: the compact Tools drawer.
 *
 * 1. **A scrim tap closes it, in both orientations** (owner ruling 1, `gesturesEnabled =
 *    drawerState.isOpen`). Every scrim tap is a real `performTouchInput` at several points across
 *    the scrim region outside the sheet (CLAUDE.md, "a semantic performClick asserts wiring, not
 *    routing"). "Closed" is checked twice: the sheet has left the window, **and** the drawer opens
 *    again from Tools afterwards. The second half is there because the screen's own authority for
 *    "should the drawer be open" is `isDrawerOpen`, not `drawerState`, and M3's scrim closes
 *    `drawerState` directly; a sheet that slides away while the flag stays true would look closed
 *    and then refuse to open again.
 * 2. **Swipe-to-open stays off** while the drawer is closed: a horizontal drag across the map from
 *    either edge, in portrait and in short landscape, leaves it closed. This passes before and
 *    after B3 by design; its revert check (`gesturesEnabled = true`) is what makes it evidence.
 * 3. **The drawer opens from the rail side in a short landscape window** (owner ruling 2, "Flip
 *    layout direction"): the rail's edge, the right at both rotations since RECORD -956 (was the port edge, the left at
 *    `ROTATION_270`), while
 *    the drawer's own content still reads in the ambient direction. Portrait is unchanged.
 *
 * The sheet's horizontal extent is read from the drawer header ("Close search options"), a
 * full-width row at the top of the sheet, so no production test tag is needed. Content direction
 * is read from the Settings entry row: its label sits after a leading icon, so in a left-to-right
 * row the gap before the label is on the left.
 *
 * **What this cannot show** (CLAUDE.md, "Robolectric reports zero window insets"): the sheet's
 * inset padding at the system bar and the cut-out, and the real drawer animation. Those are device
 * items (B4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompactToolsDrawerTest {

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

    private var rotationSeenByScreen: Int? = null
    private var directionSeenByScreen: LayoutDirection? = null

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeenByScreen = LocalView.current.display?.rotation
            directionSeenByScreen = LocalLayoutDirection.current
            DrawerTestScreen()
        }
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeenByScreen)
    }

    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /** The sheet's horizontal extent: the full-width close bar at its top. */
    private fun sheetBounds(): DpRect = composeRule.onNodeWithContentDescription(CLOSE_BAR).getUnclippedBoundsInRoot()

    /**
     * True only for a placed sheet that overlaps the window. A sheet that is not placed at all
     * reports [Dp.Unspecified] bounds (seen after a close); every comparison with those is false,
     * so it counts as off screen, which is what an unplaced sheet is. The node itself must exist,
     * or [sheetBounds] throws, so a missing drawer cannot pass as a closed one.
     */
    private fun sheetIsOnScreen(): Boolean {
        val sheet = sheetBounds()
        val root = rootBounds()
        return sheet.right > root.left && sheet.left < root.right
    }

    private fun openDrawer() {
        // Setup, not the claim under test: the Tools entry's own routing is covered elsewhere.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
        assertTrue("the drawer opens from Tools (sheet ${sheetBounds()} in ${rootBounds()})", sheetIsOnScreen())
    }

    private fun assertClosed(context: String) {
        assertTrue("$context: the sheet has left the window, but it is at ${sheetBounds()} in ${rootBounds()}", !sheetIsOnScreen())
    }

    private fun touch(x: Dp, y: Dp) {
        composeRule.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        composeRule.waitForIdle()
    }

    /**
     * Opens the drawer, taps one point of the scrim, checks it closed; repeated across
     * [SCRIM_SAMPLES_X] x [SCRIM_SAMPLES_Y] points spread over the scrim region beside the sheet.
     * Each reopening also checks the screen's own open flag followed the scrim (see the class doc).
     */
    private fun assertScrimTapsClose() {
        openDrawer()
        val sheet = sheetBounds()
        val root = rootBounds()
        val sheetOnLeft = sheet.left <= root.left + 0.5.dp
        val scrimLeft = if (sheetOnLeft) sheet.right + SCRIM_EDGE_INSET else root.left + SCRIM_EDGE_INSET
        val scrimRight = if (sheetOnLeft) root.right - SCRIM_EDGE_INSET else sheet.left - SCRIM_EDGE_INSET
        assertTrue("there is a scrim region beside the sheet to tap: $scrimLeft..$scrimRight", scrimRight > scrimLeft)
        val top = root.top + SCRIM_EDGE_INSET
        val bottom = root.bottom - SCRIM_EDGE_INSET
        var sampled = 0
        for (i in 0 until SCRIM_SAMPLES_X) {
            for (j in 0 until SCRIM_SAMPLES_Y) {
                if (sampled > 0) openDrawer()
                val x = scrimLeft + (scrimRight - scrimLeft) * (i.toFloat() / (SCRIM_SAMPLES_X - 1))
                val y = top + (bottom - top) * (j.toFloat() / (SCRIM_SAMPLES_Y - 1))
                touch(x, y)
                assertClosed("a scrim tap at ($x, $y)")
                sampled++
            }
        }
        assertEquals(SCRIM_SAMPLES_X * SCRIM_SAMPLES_Y, sampled)
        openDrawer()
    }

    /** Drags across the map from the start edge and from the end edge; neither may open the drawer. */
    private fun assertEdgeDragsDoNotOpen() {
        assertClosed("before any drag")
        val root = rootBounds()
        val y = root.top + (root.bottom - root.top) * DRAG_Y_FRACTION
        val drags = listOf(
            "from the left edge" to (root.left + 2.dp to root.left + DRAG_LENGTH),
            "from the right edge" to (root.right - 2.dp to root.right - DRAG_LENGTH),
        )
        drags.forEach { (name, xs) ->
            composeRule.onRoot().performTouchInput {
                swipe(Offset(xs.first.toPx(), y.toPx()), Offset(xs.second.toPx(), y.toPx()), durationMillis = 300)
            }
            composeRule.waitForIdle()
            assertClosed("a horizontal drag across the map $name")
        }
    }

    private fun assertSheetOnEdge(left: Boolean) {
        openDrawer()
        val sheet = sheetBounds()
        val root = rootBounds()
        if (left) {
            assertEquals("the open sheet touches the window's left edge: $sheet in $root", root.left.value, sheet.left.value, 0.5f)
            assertTrue("and stops short of the right edge: $sheet in $root", sheet.right < root.right)
        } else {
            assertEquals("the open sheet touches the window's right edge: $sheet in $root", root.right.value, sheet.right.value, 0.5f)
            assertTrue("and stops short of the left edge: $sheet in $root", sheet.left > root.left)
        }
    }

    /**
     * The Settings row's label follows its leading icon. Left to right, the gap before the label is
     * on the left (padding + 24dp icon + spacing, about 48dp) and the label sits in the left half;
     * right to left, the same gap is on the right.
     */
    private fun assertDrawerContentReads(direction: LayoutDirection) {
        val row = composeRule.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText("Settings", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val leftGap = label.left - row.left
        val rightGap = row.right - label.right
        val sheet = sheetBounds()
        assertTrue("the Settings row is inside the sheet: $row in $sheet", row.left >= sheet.left - 0.5.dp && row.right <= sheet.right + 0.5.dp)
        if (direction == LayoutDirection.Ltr) {
            assertTrue("left to right: room for the leading icon left of the label (gap $leftGap, row $row, label $label)", leftGap >= LEADING_ICON_GAP)
            assertTrue("left to right: the label sits in the row's left half (right gap $rightGap > left gap $leftGap)", rightGap > leftGap)
        } else {
            assertTrue("right to left: room for the leading icon right of the label (gap $rightGap, row $row, label $label)", rightGap >= LEADING_ICON_GAP)
            assertTrue("right to left: the label sits in the row's right half (left gap $leftGap > right gap $rightGap)", leftGap > rightGap)
        }
    }

    // ── T1, T3, T5: portrait ──

    @Test
    @Config(qualifiers = PORTRAIT)
    fun `portrait - a real tap anywhere on the scrim closes the drawer, and it opens again`() {
        setScreen(Surface.ROTATION_0)
        assertScrimTapsClose()
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun `portrait - with the drawer closed, a drag across the map from either edge does not open it`() {
        setScreen(Surface.ROTATION_0)
        assertEdgeDragsDoNotOpen()
    }

    @Test
    @Config(qualifiers = PORTRAIT)
    fun `portrait - the open sheet is on the left edge, as before B3, reading left to right`() {
        setScreen(Surface.ROTATION_0)
        assertSheetOnEdge(left = true)
        assertDrawerContentReads(LayoutDirection.Ltr)
    }

    // ── T2, T3, T4: short landscape ──

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_90 - a real tap anywhere on the scrim closes the drawer, and it opens again`() {
        setScreen(Surface.ROTATION_90)
        assertScrimTapsClose()
    }

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_270 - a real tap anywhere on the scrim closes the drawer, and it opens again`() {
        setScreen(Surface.ROTATION_270)
        assertScrimTapsClose()
    }

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_90 - with the drawer closed, a drag across the map from either edge does not open it`() {
        setScreen(Surface.ROTATION_90)
        assertEdgeDragsDoNotOpen()
    }

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_270 - with the drawer closed, a drag across the map from either edge does not open it`() {
        setScreen(Surface.ROTATION_270)
        assertEdgeDragsDoNotOpen()
    }

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_90 - the open sheet is on the right edge, the rail side, reading left to right`() {
        setScreen(Surface.ROTATION_90)
        assertEquals(LayoutDirection.Ltr, directionSeenByScreen)
        assertSheetOnEdge(left = false)
        assertDrawerContentReads(LayoutDirection.Ltr)
    }

    // RECORD -956 (the owner: "One should look exactly line the other"): the rail, and so the drawer, is on the right at 270 too. Was
    // `the open sheet is on the left edge`, `assertSheetOnEdge(left = true)`.
    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_270 - the open sheet is on the right edge, the rail side, reading left to right`() {
        setScreen(Surface.ROTATION_270)
        assertEquals(LayoutDirection.Ltr, directionSeenByScreen)
        assertSheetOnEdge(left = false)
        assertDrawerContentReads(LayoutDirection.Ltr)
    }

    @Test
    @Config(qualifiers = SHORT_LANDSCAPE)
    fun `short landscape at ROTATION_90 - after the drawer closes, the rail is still on the right`() {
        setScreen(Surface.ROTATION_90)
        openDrawer()
        touch(rootBounds().left + 40.dp, (rootBounds().top + rootBounds().bottom) / 2)
        assertClosed("after a scrim tap")
        val tools = composeRule.onNodeWithText("Tools").getUnclippedBoundsInRoot()
        assertTrue("the rail's Tools item is still at the right edge: $tools in ${rootBounds()}", tools.right > rootBounds().right - 80.dp)
    }

    // ── A right-to-left locale: the drawer still opens from the rail side and still reads RTL ──

    @Test
    @Config(qualifiers = RTL_SHORT_LANDSCAPE)
    fun `RTL short landscape at ROTATION_90 - the open sheet is on the right edge, the rail side, reading right to left`() {
        setScreen(Surface.ROTATION_90)
        assertEquals("the ambient direction is right to left", LayoutDirection.Rtl, directionSeenByScreen)
        assertSheetOnEdge(left = false)
        assertDrawerContentReads(LayoutDirection.Rtl)
    }

    // RECORD -956 (the owner: "One should look exactly line the other"). Was `the open sheet is on the left edge`, `assertSheetOnEdge(left = true)`.
    @Test
    @Config(qualifiers = RTL_SHORT_LANDSCAPE)
    fun `RTL short landscape at ROTATION_270 - the open sheet is on the right edge, the rail side, reading right to left`() {
        setScreen(Surface.ROTATION_270)
        assertEquals("the ambient direction is right to left", LayoutDirection.Rtl, directionSeenByScreen)
        assertSheetOnEdge(left = false)
        assertDrawerContentReads(LayoutDirection.Rtl)
    }

    @Test
    @Config(qualifiers = RTL_PORTRAIT)
    fun `RTL portrait - the open sheet is on the right edge, the start side, as before B3`() {
        setScreen(Surface.ROTATION_0)
        assertEquals("the ambient direction is right to left", LayoutDirection.Rtl, directionSeenByScreen)
        assertSheetOnEdge(left = false)
        assertDrawerContentReads(LayoutDirection.Rtl)
    }
}

// ── fixtures ──

/** The S22 Ultra's portrait window: wide enough (384dp) that the 360dp sheet leaves a scrim strip. */
private const val PORTRAIT = "w384dp-h823dp-port"
private const val SHORT_LANDSCAPE = "w823dp-h384dp-land"
private const val RTL_SHORT_LANDSCAPE = "ar-ldrtl-w823dp-h384dp-land"
private const val RTL_PORTRAIT = "ar-ldrtl-w384dp-h823dp-port"

private const val CLOSE_BAR = "Close search options"
private const val MAP_SLOT_TAG = "compact-tools-drawer-test-map"

private const val SCRIM_SAMPLES_X = 3
private const val SCRIM_SAMPLES_Y = 3
private val SCRIM_EDGE_INSET = 4.dp
private val LEADING_ICON_GAP = 40.dp
private val DRAG_LENGTH = 300.dp
private const val DRAG_Y_FRACTION = 0.6f

/** Stands in for the real map, as the other availability screen tests do. */
private val stubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag(MAP_SLOT_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = {}) })
}

@androidx.compose.runtime.Composable
private fun DrawerTestScreen() {
    AvailabilityScreen(
        uiState = SEARCHED,
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
        mapSlot = stubMapSlot,
    )
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val SEARCHED = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { index ->
        Sighting(
            observationId = index.toLong(),
            taxonId = 48473L,
            scientificName = "Ganoderma applanatum",
            commonName = "artist's bracket",
            lat = REGION.lat + index * 0.001,
            lng = REGION.lng + index * 0.001,
            observedOn = LocalDate.of(2025, 8, 14),
            photoUrl = null,
        )
    },
)
