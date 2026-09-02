package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.OfflineRegionDecision
import com.forager.app.domain.model.PhotoAttachment
import com.forager.app.domain.model.WaypointDecision
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapSlot
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

/**
 * [CartographyEntryReportScreen]'s own map — Journal Stage 2d. [SightingsMap] itself can't be
 * composed under Robolectric (see [com.forager.app.ui.map.SightingsMapOverlayDataTest]'s own doc
 * comment for why: native MapLibre calls), so this drives the screen through a capturing stub
 * [MapSlot] — the same pattern [com.forager.app.ui.map.CentrePinLocationPickerTest] already
 * establishes — that records exactly what [MapOverlayContent] the screen built, rather than
 * asserting on anything actually rendered by a real map.
 *
 * [getMapData] is resolved asynchronously (a `LaunchedEffect` keyed on [CartographyEntry.id] — see
 * that composable's own doc comment), so every test here supplies a synchronous lambda and calls
 * `composeRule.waitForIdle()` before asserting, the same shape any other one-shot-effect test in
 * this codebase uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryReportScreenMapTest {

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

    private val baseEntry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false)

    private var capturedContent: MapOverlayContent? = null
    private var capturedRegion: com.forager.app.domain.model.Region? = null

    private val capturingMapSlot: MapSlot = { region, content, _, _, _, _, _, _, _ ->
        capturedRegion = region
        capturedContent = content
    }

    private fun setScreen(entry: CartographyEntry, mapData: CartographyEntryMapData) {
        capturedContent = null
        capturedRegion = null
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = entry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = capturingMapSlot,
                basemap = Basemap.DEFAULT,
                night = false,
                getMapData = { _, _ -> mapData },
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `two kept tracks reach the map as two separate polylines, not one joined trail`() {
        val trackOne = listOf(LatLng(45.20, -122.50), LatLng(45.21, -122.51))
        val trackTwo = listOf(LatLng(46.00, -123.00), LatLng(46.01, -123.01))
        setScreen(
            baseEntry,
            CartographyEntryMapData(
                trackPolylines = listOf(trackOne, trackTwo),
                findMarkers = emptyList(),
                waypointMarkers = emptyList(),
                photoMarkers = emptyList(),
                offlineRegionCircles = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertIsDisplayed()
        assertEquals(2, capturedContent?.keptTrackPolylines?.size)
        assertEquals(trackOne, capturedContent?.keptTrackPolylines?.get(0))
        assertEquals(trackTwo, capturedContent?.keptTrackPolylines?.get(1))
    }

    /**
     * A kept track deleted from Records never reaches this screen at all — [getMapData] (in
     * production, [com.forager.app.domain.GetCartographyEntryMapDataUseCase]) is where that
     * resolution/omission happens, see that class's own test for the dangling-reference case
     * directly. What this screen must do is simply render whatever it's handed with no track in
     * it, without erroring — proven here by an empty [MapOverlayContent.keptTrackPolylines] reaching
     * the map cleanly, framed on the one thing that did resolve (a waypoint), same as if the track
     * had never been kept at all.
     */
    @Test
    fun `an unresolved (deleted) track reaches the map as simply absent, never an error`() {
        setScreen(
            baseEntry,
            CartographyEntryMapData(
                trackPolylines = emptyList(),
                findMarkers = emptyList(),
                waypointMarkers = listOf(LatLng(45.5, -122.5)),
                photoMarkers = emptyList(),
                offlineRegionCircles = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertIsDisplayed()
        assertTrue(capturedContent?.keptTrackPolylines?.isEmpty() == true)
    }

    /**
     * A photo with no resolved coordinate is filtered out before it ever reaches this screen (see
     * [com.forager.app.domain.GetCartographyEntryMapDataUseCase]'s own test for that filtering
     * directly) — this proves the screen itself renders correctly with an empty photo-marker list,
     * silently, alongside data that did resolve.
     */
    @Test
    fun `a photo with no coordinate contributes no photo marker, silently`() {
        setScreen(
            baseEntry.copy(photos = listOf(PhotoAttachment(photoId = "p1", attachedAtEpochMillis = 1_000L))),
            CartographyEntryMapData(
                trackPolylines = emptyList(),
                findMarkers = emptyList(),
                waypointMarkers = listOf(LatLng(45.5, -122.5)),
                photoMarkers = emptyList(),
                offlineRegionCircles = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertIsDisplayed()
        assertTrue(capturedContent?.photoMarkers?.isEmpty() == true)
    }

    @Test
    fun `nothing drawable renders no map section, but the entry's own text still renders below`() {
        setScreen(
            baseEntry.copy(text = "A quiet ridge walk."),
            CartographyEntryMapData(
                trackPolylines = emptyList(),
                findMarkers = emptyList(),
                waypointMarkers = emptyList(),
                photoMarkers = emptyList(),
                offlineRegionCircles = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertDoesNotExist()
        assertEquals(null, capturedContent)
        composeRule.onNodeWithText("A quiet ridge walk.").assertIsDisplayed()
    }

    @Test
    fun `while loading (before getMapData resolves) no map section renders yet`() {
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = baseEntry.copy(text = "A quiet ridge walk."),
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = capturingMapSlot,
                basemap = Basemap.DEFAULT,
                night = false,
                // Never resolves within this test -- simulates the moment before the LaunchedEffect's
                // suspend call returns.
                getMapData = { _, _ -> kotlinx.coroutines.delay(Long.MAX_VALUE); throw IllegalStateException("unreachable") },
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("A quiet ridge walk.").assertIsDisplayed()
    }

    @Test
    fun `the camera region fits every resolved point, not a default location`() {
        setScreen(
            baseEntry.copy(waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true))),
            CartographyEntryMapData(
                trackPolylines = emptyList(),
                findMarkers = emptyList(),
                waypointMarkers = listOf(LatLng(45.5, -122.5)),
                photoMarkers = emptyList(),
                offlineRegionCircles = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertIsDisplayed()
        assertEquals(45.5, capturedRegion?.lat)
        assertEquals(-122.5, capturedRegion?.lng)
    }

    @Test
    fun `offline regions reach the map's overlay content`() {
        setScreen(
            baseEntry.copy(offlineRegionDecisions = listOf(OfflineRegionDecision(offlineRegionId = 1L, name = "Ridge Region", lat = 45.6, lng = -122.6, radiusKm = 10, kept = true))),
            CartographyEntryMapData(
                trackPolylines = emptyList(),
                findMarkers = emptyList(),
                waypointMarkers = emptyList(),
                photoMarkers = emptyList(),
                offlineRegionCircles = listOf(com.forager.app.domain.model.Region(lat = 45.6, lng = -122.6, radiusKm = 10)),
            ),
        )

        composeRule.onNodeWithTag(CARTOGRAPHY_MAP_TEST_TAG).assertIsDisplayed()
        assertEquals(1, capturedContent?.offlineRegionCircles?.size)
    }
}
