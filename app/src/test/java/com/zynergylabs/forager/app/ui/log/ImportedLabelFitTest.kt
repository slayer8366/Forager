package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.trackExportRowTag
import java.time.Instant
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

/**
 * Dispatch 2026-09-28-685, fix 1 (the owner, RECORD -644: "Fix it (Recommended)"). On the S22 the "Imported" label
 * beside the title "forager-track-2026-09-27-192346" (an imported file's name, the fallback title) was laid out in a
 * 24 px column and drawn one letter per line. Now the label stays on one line and the title shortens with "…".
 *
 * Through the real Records screen at 360 dp: the Tracks chip, then the track's row by a real touch, then the details,
 * read through each text's own layout ([SemanticsActions.GetTextLayoutResult]) at font scale 1.0 and 2.0. Native
 * graphics, as `AvailabilityScreenNavigationWordsTest`: without it Robolectric measures text at a few dp wide and every
 * fit would pass on fake widths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportedLabelFitTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val start = Instant.parse("2026-09-27T19:23:46Z").toEpochMilli()
    private val importedAt = Instant.parse("2026-10-07T17:07:00Z").toEpochMilli()
    private val points = listOf(TrackPoint(45.5, -122.6, null, null, start), TrackPoint(45.6, -122.6, null, null, start + 60_000L))

    /** The longest real title seen: the S22 day check's imported Forager export, named by its file. */
    private val longTitle = "forager-track-2026-09-27-192346"

    private fun openDetails(title: String) {
        val track = Track("imp", title, start, start + 60_000L, points, importedAtEpochMillis = importedAt)
        composeRule.setContent {
            RecordsTab(
                waypoints = emptyList(),
                waypointsErrorMessage = null,
                onDeleteWaypoint = {},
                availabilityUiState = AvailabilityUiState(),
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
                mapSlot = STUB_MAP,
                night = false,
                onOfflineMapRegionPicked = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                tracks = listOf(track),
                onTracksOpened = {},
                findsContent = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(trackExportRowTag("imp")).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RECORD_DETAILS_SHEET_TAG).assertExists()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun layoutOf(interaction: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        interaction.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** "Imported" is one line, whole, in a box as wide as it needs, and inside the window. */
    private fun assertLabelWhole(scale: String) {
        val label = node(RECORD_DETAILS_LABEL_TAG)
        val layout = layoutOf(label)
        val box = with(composeRule.density) { label.fetchSemanticsNode().boundsInRoot.width.toDp() }
        val needs = with(composeRule.density) { layout.multiParagraph.maxIntrinsicWidth.toDp() }
        val bounds = label.getUnclippedBoundsInRoot()
        // The sheet is its own window, so there are two roots; both span the screen.
        val root = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot()
        println("MEASURED $scale label: box $box, needs $needs, lines ${layout.lineCount}, bounds $bounds, root right ${root.right}")
        assertEquals("$scale: the label is one line", 1, layout.lineCount)
        assertFalse("$scale: the label is not ellipsised", layout.isLineEllipsized(0))
        assertEquals("$scale: every character of the label shows", "Imported".length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("$scale: the label's box $box holds its $needs", box >= needs - 0.5.dp)
        assertTrue("$scale: the label ends inside the window ($bounds, root ${root.right})", bounds.right <= root.right)
    }

    private fun assertLongTitleShortened(scale: String) {
        val layout = layoutOf(node(RECORD_DETAILS_TITLE_TAG))
        println("MEASURED $scale title: lines ${layout.lineCount}, ellipsised ${layout.isLineEllipsized(0)}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${longTitle.length}")
        assertEquals("$scale: the title is one line", 1, layout.lineCount)
        assertTrue("$scale: the long title shortens with an ellipsis", layout.isLineEllipsized(0))
        val titleRight = node(RECORD_DETAILS_TITLE_TAG).getUnclippedBoundsInRoot().right
        val labelLeft = node(RECORD_DETAILS_LABEL_TAG).getUnclippedBoundsInRoot().left
        assertTrue("$scale: the title ($titleRight) ends before the label ($labelLeft)", titleRight <= labelLeft)
    }

    @Test
    fun `at 360 dp a long imported title shortens with an ellipsis and Imported stays on one line`() {
        openDetails(longTitle)
        assertLabelWhole("font 1.0")
        assertLongTitleShortened("font 1.0")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `at 360 dp and font scale 2,0 a long imported title shortens and Imported stays on one line`() {
        openDetails(longTitle)
        assertEquals("this case is at font scale 2.0", 2f, composeRule.density.fontScale)
        assertLabelWhole("font 2.0")
        assertLongTitleShortened("font 2.0")
    }

    @Test
    fun `a short imported title is shown whole beside the label`() {
        openDetails("Ridge loop")
        val layout = layoutOf(node(RECORD_DETAILS_TITLE_TAG))
        assertFalse("the short title is not ellipsised", layout.isLineEllipsized(0))
        assertEquals("every character of the short title shows", "Ridge loop".length, layout.getLineEnd(0, visibleEnd = true))
        assertLabelWhole("short title")
    }
}

private val STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
