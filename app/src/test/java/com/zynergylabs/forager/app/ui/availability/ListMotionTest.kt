package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.CartographyEntryListScreen
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.entrySwipeTag
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import com.zynergylabs.forager.app.ui.motion.ListRowLeavingKey
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 3, item 2 (dispatch 2026-09-28-676; the owner, RECORD -651: Lists "Slide and close up": "Removed rows fade and
 * shrink, the rest glide up, Undo reverses"), through the real lists: the waypoint list (a plain column) and the Entries list (a
 * lazy grid). A row is deleted by its own Delete accessibility action, the swipe row's real path for a screen reader, and every
 * touch on a leaving row is a real coordinate touch where it is still drawn.
 *
 * The clock is stopped for every mid-animation read, and every state write made with it stopped is applied before frames are
 * stepped (motion Part 1's report). Reduce motion is the transition scale alone at 0, as Part 2's tests set it.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class ListMotionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun reduceMotionOn() = Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)

    private fun waypoint(id: String, name: String) =
        Waypoint(id = id, lat = 45.0, lng = -122.0, altitude = null, name = name, note = "", createdAtEpochMillis = 0L)

    private val oak = waypoint("w-oak", "Big oak")
    private val creek = waypoint("w-creek", "Creek")
    private val stump = waypoint("w-stump", "Stump")

    private val waypoints = mutableStateOf(listOf(oak, creek, stump))
    private val opened = mutableListOf<String>()

    private fun setWaypoints() {
        composeRule.setContent {
            ForagerTheme {
                Box(Modifier.fillMaxSize()) {
                    WaypointsSection(
                        waypoints = waypoints.value,
                        errorMessage = null,
                        onDeleteWaypoint = { id -> waypoints.value = waypoints.value.filterNot { it.id == id } },
                        onOpenWaypointDetails = { id -> opened += id },
                    )
                }
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun pause() {
        composeRule.mainClock.autoAdvance = false
    }

    /** Applies a write made with the clock stopped, so the next frame sees it. */
    private fun applyWrites() = composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frame() = composeRule.mainClock.advanceTimeByFrame()

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun leavingRows() = composeRule.onAllNodes(SemanticsMatcher.expectValue(ListRowLeavingKey, true))

    private fun deleteByAction(tag: String) {
        val delete = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        composeRule.runOnUiThread { delete.action() }
        applyWrites()
    }

    private fun tapAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
    }

    /** Steps frames until [condition] holds, and fails if it never does within [maxFrames]. */
    private fun stepUntil(what: String, maxFrames: Int = 40, condition: () -> Boolean) {
        repeat(maxFrames) {
            frame()
            if (condition()) return
        }
        throw AssertionError("never saw: $what")
    }

    private fun creekTag() = swipeToDeleteTag(RecordType.WAYPOINTS, creek.id)

    /** The creek row's own enter-and-exit box (not leaving), whose height is what is drawn of the row. */
    private fun creekBox(): DpRect = composeRule.onNode(
        SemanticsMatcher.expectValue(ListRowLeavingKey, false) and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasTestTag(creekTag())),
    ).getUnclippedBoundsInRoot()
    private fun stumpTag() = swipeToDeleteTag(RecordType.WAYPOINTS, stump.id)

    @Test
    fun `a deleted row fades and shrinks where it was, takes no touch, and the row below glides up`() {
        setWaypoints()
        val creekAt = bounds(creekTag())
        val stumpAt = bounds(stumpTag())
        // The point the leaving row is touched at, first shown to be on the row: a tap there opens it.
        val x = creekAt.left + 16.dp
        val y = creekAt.top + 8.dp
        tapAt(x, y)
        settle()
        assertEquals("the sample point is on the creek row", listOf(creek.id), opened)
        opened.clear()

        pause()
        deleteByAction(creekTag())
        stepUntil("the creek row part-way out") {
            val leaving = leavingRows().fetchSemanticsNodes()
            leaving.size == 1 && with(composeRule.density) {
                val h = leaving.single().boundsInRoot.height.toDp()
                h > 12.dp && h < creekAt.height - 4.dp
            }
        }
        val leaving = leavingRows().onFirst().getUnclippedBoundsInRoot()
        // Shrinking where it was: still from its own top.
        assertEquals(creekAt.top.value, leaving.top.value, 1f)
        // The row below is between where it was and where it settles (closing up, not jumped).
        val stumpNow = bounds(stumpTag())
        assertTrue("the row below has started up: ${stumpNow.top} < ${stumpAt.top}", stumpNow.top < stumpAt.top - 1.dp)
        assertTrue("and has not jumped to the creek row's place: ${stumpNow.top} > ${creekAt.top}", stumpNow.top > creekAt.top + 1.dp)

        // A real touch on the leaving row, where it is still drawn, reaches nothing of it.
        assertTrue("the sample point is inside what is left of the row", y < leaving.bottom)
        tapAt(x, y)
        applyWrites()
        frame()
        assertEquals("a leaving row opens nothing", emptyList<String>(), opened)

        settle()
        assertEquals(0, leavingRows().fetchSemanticsNodes().size)
        assertEquals("the row below has closed up into the creek row's place", creekAt.top.value, bounds(stumpTag()).top.value, 1f)
    }

    @Test
    fun `Undo while a row is leaving turns it round, and it grows back to its own height`() {
        setWaypoints()
        val creekAt = bounds(creekTag())

        pause()
        deleteByAction(creekTag())
        stepUntil("the creek row part-way out") {
            val leaving = leavingRows().fetchSemanticsNodes()
            leaving.size == 1 && with(composeRule.density) { leaving.single().boundsInRoot.height.toDp() < creekAt.height - 4.dp }
        }
        val shrunkTo = leavingRows().onFirst().getUnclippedBoundsInRoot().height

        // Undo: the waypoint is back in the list, in its place.
        composeRule.runOnUiThread { waypoints.value = listOf(oak, creek, stump) }
        applyWrites()
        frame()
        assertEquals("no longer leaving", 0, leavingRows().fetchSemanticsNodes().size)
        // The same row turned round: its spring carries on a little the way it was going before it turns (the critically damped
        // spring keeps its speed when retargeted), so the heights are read frame by frame. It never starts again from nothing,
        // and it opens back to its own height.
        val heights = mutableListOf<Dp>()
        repeat(30) {
            frame()
            heights += creekBox().height
        }
        assertTrue("never back to nothing, as a new row would start: $heights (shrunk to $shrunkTo)", heights.all { it > 1.dp })
        assertTrue("it turned and grew: $heights", heights.last() > heights.min() + 4.dp)
        assertTrue("caught part-way back: $heights", heights.any { it < creekAt.height - 1.dp })
        settle()
        assertEquals(creekAt.height.value, creekBox().height.value, 1f)
    }

    @Test
    fun `under reduced motion a deleted row goes at once and the row below takes its place`() {
        reduceMotionOn()
        setWaypoints()
        val creekAt = bounds(creekTag())

        pause()
        deleteByAction(creekTag())
        frame()
        frame()
        assertEquals("no row is kept to leave", 0, leavingRows().fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(creekTag()).assertDoesNotExist()
        assertEquals(creekAt.top.value, bounds(stumpTag()).top.value, 1f)
    }

    // The Entries list: a lazy grid, one column in portrait.

    private val first = entry("e-1", LocalDate.of(2026, 9, 3))
    private val second = entry("e-2", LocalDate.of(2026, 9, 2))
    private val third = entry("e-3", LocalDate.of(2026, 9, 1))
    private val entries = mutableStateOf(listOf(first, second, third))
    private val openedEntries = mutableListOf<String>()

    private fun entry(id: String, date: LocalDate): CartographyEntry =
        CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = 0L).copy(isDraft = false)

    private fun setEntries() {
        composeRule.setContent {
            ForagerTheme {
                CartographyEntryListScreen(
                    entries = entries.value,
                    isLoading = false,
                    onOpenEntry = { id -> openedEntries += id },
                    emptyMessage = "none",
                    distanceUnit = DistanceUnit.KILOMETERS,
                    columns = 1,
                    onDeleteEntry = { id -> entries.value = entries.value.filterNot { it.id == id } },
                )
            }
        }
        settle()
    }

    @Test
    fun `a deleted entry card leaves the lazy list the same way, and takes no touch while it goes`() {
        setEntries()
        val cardAt = bounds(entrySwipeTag(second.id))
        val x = cardAt.left + 24.dp
        val y = cardAt.top + 12.dp
        tapAt(x, y)
        settle()
        assertEquals("the sample point is on the second card", listOf(second.id), openedEntries)
        openedEntries.clear()

        pause()
        deleteByAction(entrySwipeTag(second.id))
        stepUntil("the second card part-way out") {
            val leaving = leavingRows().fetchSemanticsNodes()
            leaving.size == 1 && with(composeRule.density) {
                val h = leaving.single().boundsInRoot.height.toDp()
                h > 16.dp && h < cardAt.height - 4.dp
            }
        }
        val leaving = leavingRows().onFirst().getUnclippedBoundsInRoot()
        assertTrue("the sample point is inside what is left of the card", y < leaving.bottom)
        tapAt(x, y)
        applyWrites()
        frame()
        assertEquals("a leaving card opens nothing", emptyList<String>(), openedEntries)

        settle()
        assertEquals(0, leavingRows().fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(entrySwipeTag(second.id)).assertDoesNotExist()
        assertEquals("the third card has closed up into its place", cardAt.top.value, bounds(entrySwipeTag(third.id)).top.value, 1f)
    }
}
