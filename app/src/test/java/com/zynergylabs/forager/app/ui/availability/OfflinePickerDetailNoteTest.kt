package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.data.dropoffmap.FakeTileServer
import com.zynergylabs.forager.app.data.mapdetail.WorkerTrailMapDetail
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.mapdata.DetailTileCover
import com.zynergylabs.forager.app.domain.mapdata.TestMvt
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/** The owner's words (RECORD -1951), held here as a literal rather than read from the production constant. */
private const val NOTE = "Detailed offline maps aren't available here yet. A download here will be blank when you're offline."

/**
 * Known bug 14 (dispatch 2026-11-02-02; Amendment 2's B2, Amendment 3's B3): the line in the Offline Maps area picker, through
 * the real [AvailabilityScreen] and the real [AvailabilityViewModel] ([RealSearchScreenRig]), entered the owner's way, Tools then
 * the drawer's Offline Maps row, by real touches. The map server is behind this project's own interface, with the real client
 * ([WorkerTrailMapDetail]) over [FakeTileServer], so the request, the reading of each tile and the ViewModel are the app's and
 * only the network is faked. Its requests run on the test's thread (`Dispatchers.Unconfined`), so the answer has landed when
 * the rig has settled. The pin moves by a real drag on the picker map, which settles where the test says. Made-up points.
 */
abstract class OfflinePickerDetailNoteTests(private val landscape: Boolean) {

    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val server = FakeTileServer(TestMvt.DETAIL)
    private val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var noSignal = false
    private val rig = RealSearchScreenRig(
        composeRule,
        trailMapDetail = WorkerTrailMapDetail(server.client, FakeTileServer.TEMPLATE, io = Dispatchers.Unconfined),
        noSignal = { noSignal },
        errorLog = ErrorLog { _, message, _ -> logs += message },
        pickerPans = true,
    )

    private fun radiusKm() = rig.viewModel.uiState.value.offlineMapRadiusKm

    protected fun areaAt(point: LatLng, radiusKm: Int = radiusKm()) = Region(point.lat, point.lng, radiusKm)

    protected fun coverOf(area: Region): List<TileId> = DetailTileCover.of(routeAreaFor(area))!!.tiles()

    private fun detailLogs() = logs.filter { it.startsWith("Offline maps detail check") }

    private fun noteShown() = composeRule.onAllNodes(hasText(NOTE), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun anyOfTheWordsShown() = composeRule.onAllNodes(hasText("Detailed offline maps", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The screen up, with [before] set on the server once the area the picker opens on is known. */
    protected fun openOfflineMaps(before: (Region) -> Unit = {}) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(if (landscape) Surface.ROTATION_90 else Surface.ROTATION_0)
        rig.location = LocationResult.Success(START.lat, START.lng)
        rig.setScreen()
        before(areaAt(START))
        val nav = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
        rig.touch(composeRule.onNode(hasText("Tools") and hasClickAction() and hasAnyAncestor(hasTestTag(nav))))
        rig.touch(composeRule.onNodeWithTag(TOOLS_OFFLINE_MAPS_ROW_TAG))
        if (composeRule.onAllNodes(hasText("Download Maps")).fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("positive control: Offline Maps is not up; tree:\n" + composeRule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 60))
        }
        assertPinAt(START)
    }

    private fun assertPinAt(point: LatLng) {
        val line = "Pin at: ${"%.4f".format(point.lat)}, ${"%.4f".format(point.lng)}"
        assertTrue("positive control: the pin reads $line", composeRule.onAllNodes(hasText(line), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** A real drag on the picker map, which settles at [point]. Returns before the ViewModel's wait has run: [RealSearchScreenRig.settle] runs it. */
    protected fun dragPinTo(point: LatLng) {
        rig.panTo = point
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).performTouchInput {
            swipe(start = Offset(width * 0.3f, height * 0.3f), end = Offset(width * 0.6f, height * 0.7f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertPinAt(point)
    }

    private fun assertDownloadEnabledAndReachedByTouch() {
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
        for ((fx, fy) in TOUCH_SAMPLES) {
            val button = composeRule.onNodeWithText("Download Maps")
            if (!landscape) {
                button.performScrollTo()
                rig.settle()
            }
            rig.touch(button, fx, fy)
            assertTrue(
                "a real touch at ($fx, $fy) of Download Maps opens \"Download this area?\"",
                composeRule.onAllNodes(hasText("Download this area?")).fetchSemanticsNodes().isNotEmpty(),
            )
            composeRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
            rig.settle()
        }
    }

    @Test
    fun `every tile with detail - no line, Download Maps enabled, and exactly the area's zoom-11 cover was asked for`() {
        openOfflineMaps()
        assertEquals("the server was asked for the opening area's cover", coverOf(areaAt(START)).toSet(), server.asked.toSet())
        assertTrue("no line", !anyOfTheWordsShown())
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `one tile answering 204 - the line, as the owner's words, and Download Maps still works`() {
        openOfflineMaps { area -> server.answers[coverOf(area).last()] = { FakeTileServer.empty() } }
        composeRule.onNodeWithText(NOTE, useUnmergedTree = true).assertIsDisplayed()
        assertDownloadEnabledAndReachedByTouch()
        assertTrue("the line is still there after Download Maps was used", noteShown())
    }

    @Test
    fun `one tile of land only - the line`() {
        openOfflineMaps { area -> server.answers[coverOf(area).first()] = { FakeTileServer.tileAnswer(TestMvt.LAND_ONLY) } }
        composeRule.onNodeWithText(NOTE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `no signal - no line, nothing asked, and the log says why`() {
        noSignal = true
        openOfflineMaps { area -> server.answers[coverOf(area).last()] = { FakeTileServer.empty() } }
        assertTrue("no line", !anyOfTheWordsShown())
        assertEquals("nothing asked", emptyList<TileId>(), server.asked.toList())
        val lines = detailLogs()
        assertTrue("positive control: the check was reached and logged", lines.isNotEmpty())
        assertEquals(setOf("Offline maps detail check not run: no signal."), lines.toSet())
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `a pan from an area without detail into a covered one - the line goes`() {
        openOfflineMaps { area -> server.answers[coverOf(area).last()] = { FakeTileServer.empty() } }
        assertTrue("positive control: the line shows at the start", noteShown())

        dragPinTo(COVERED)
        assertTrue("the line goes as soon as the pin moves, before the new area is answered", !anyOfTheWordsShown())
        rig.settle()

        assertTrue("the covered area was asked about", server.asked.toSet().containsAll(coverOf(areaAt(COVERED))))
        assertEquals(OfflineDetailAnswer(areaAt(COVERED), someOutside = false), rig.viewModel.uiState.value.offlineDetail)
        assertTrue("no line over the covered area", !anyOfTheWordsShown())
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `a pan into an area whose answer fails - the line goes, and the log says why`() {
        openOfflineMaps { area -> server.answers[coverOf(area).last()] = { FakeTileServer.empty() } }
        assertTrue("positive control: the line shows at the start", noteShown())
        val failing = coverOf(areaAt(FAILING))
        server.answers[failing.last()] = { FakeTileServer.status(503) }

        dragPinTo(FAILING)
        assertTrue("the line goes as soon as the pin moves", !anyOfTheWordsShown())
        rig.settle()

        assertTrue("no line where no answer came", !anyOfTheWordsShown())
        assertEquals(
            listOf("Offline maps detail check not run: 1 of ${failing.size} zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail."),
            detailLogs(),
        )
    }

    @Test
    fun `a larger radius reaching a tile without detail shows the line`() {
        lateinit var opening: Region
        openOfflineMaps { area -> opening = area }
        assertTrue("positive control: no line at the opening radius", !anyOfTheWordsShown())
        val larger = areaAt(START, radiusKm = 24)
        assertTrue("positive control: the opening radius ${opening.radiusKm} km is under 24", opening.radiusKm < 24)
        val reached = coverOf(larger) - coverOf(opening).toSet()
        assertTrue("positive control: the larger area reaches tiles the opening one did not", reached.isNotEmpty())
        server.answers[reached.first()] = { FakeTileServer.empty() }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
        rig.settle()

        assertEquals("positive control: the radius moved", 24, radiusKm())
        composeRule.onNodeWithText(NOTE, useUnmergedTree = true).assertIsDisplayed()
    }

    protected fun noteBounds() = composeRule.onNodeWithTag(OFFLINE_DETAIL_NOTE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    protected fun pinAtBounds() = composeRule.onNode(hasText("Pin at:", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()

    protected fun showLineAtStart() = openOfflineMaps { area -> server.answers[coverOf(area).last()] = { FakeTileServer.empty() } }

    protected companion object {
        val START = LatLng(41.0, -102.0)
        val COVERED = LatLng(39.0, -98.0)
        val FAILING = LatLng(37.0, -96.0)

        /** Across the button's own bounds, not only its centre (CLAUDE.md: a finger is not a point). */
        val TOUCH_SAMPLES = listOf(0.5f to 0.5f, 0.1f to 0.5f, 0.9f to 0.5f, 0.5f to 0.15f, 0.5f to 0.85f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class OfflinePickerDetailNotePortraitTest : OfflinePickerDetailNoteTests(landscape = false) {
    /** A5 (the planner's call on the Phase 1 report's S3): in portrait, directly under "Pin at", above the Name box. */
    @Test
    fun `portrait - the line sits directly under Pin at, above the Name box`() {
        showLineAtStart()
        val note = noteBounds()
        val pinAt = pinAtBounds()
        val name = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the line $note is under \"Pin at\" $pinAt", note.top >= pinAt.bottom - 0.5.dp)
        assertTrue("and above the Name box $name", note.bottom <= name.top + 0.5.dp)
        assertTrue("and close under \"Pin at\": at most two notes' padding between", note.top - pinAt.bottom <= 17.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class OfflinePickerDetailNoteLandscapeTest : OfflinePickerDetailNoteTests(landscape = true) {
    /** A5: in landscape, pinned above "Pin at", outside the controls' scrolling part. */
    @Test
    fun `landscape - the line is pinned above Pin at, below the scrolling controls`() {
        showLineAtStart()
        val note = noteBounds()
        val pinAt = pinAtBounds()
        val scroll = composeRule.onNodeWithTag(OFFLINE_PICKER_CONTROLS_SCROLL_TAG).getUnclippedBoundsInRoot()
        assertTrue("the line $note is above \"Pin at\" $pinAt", note.bottom <= pinAt.top + 0.5.dp)
        assertTrue("and below the scrolling part $scroll, so it is always in view", note.top >= scroll.bottom - 0.5.dp)
    }
}
