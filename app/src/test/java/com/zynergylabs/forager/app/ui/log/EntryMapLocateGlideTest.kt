package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.map.MapDataRefresh
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TRANSITION_MILLIS
import com.zynergylabs.forager.app.ui.map.rememberCameraFixtureMapState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.CameraMapFixture
import org.maplibre.android.maps.StyleLifecycleFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-07-01, Amendment 1 (the owner: "Glide, keep zoom, no follow"; "Glides to your position, keeps the zoom,
 * doesn't follow"): on a journal entry's full-screen map, the locate row's fix is reached by an animated move over Re-center's
 * duration, at the zoom the user had, and the map does not start following.
 *
 * Through the real [CartographyEntryReportScreen], with a real touch on the icon bar's locate row. The map slot runs the app's
 * own [MapDataRefresh] over a real MapLibre map ([CameraMapFixture]), with the entry map's own opening frame. The user's pan is
 * a camera set on the map; entering fullscreen is the map's own tap callback, as the screen's other fullscreen tests do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryMapLocateGlideTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(
            isDraft = false,
            waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)),
        )
    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private val start = camera(45.5, -122.5, 14.0)
    private val panned = camera(45.47, -122.44, 17.25)
    private val fix = LatLng(46.1, -121.9)

    private lateinit var made: Pair<org.maplibre.android.maps.MapLibreMap, CameraMapFixture.Recording>
    private var renderMode: MapRenderMode? = null
    private var onMapTap: (() -> Unit)? = null

    private fun camera(lat: Double, lng: Double, zoom: Double): CameraPosition =
        CameraPosition.Builder().target(MapLibreLatLng(lat, lng)).zoom(zoom).bearing(0.0).tilt(0.0).build()

    private val slot: MapSlot = { region, content, mode, focusOverride, _, onTap, _, _, modifier ->
        renderMode = mode
        onMapTap = onTap
        val map = remember { CameraMapFixture.map(start = start, fit = start).also { made = it } }
        val state = rememberCameraFixtureMapState(map.first, rememberUpdatedState(mode.cameraMemory))
        val style = remember { StyleLifecycleFixture.loadedStyle() }
        LaunchedEffect(Unit) { state.loadedStyle = style }
        Box(modifier)
        MapDataRefresh(
            state = state,
            route = null,
            region = region,
            sightings = emptyList(),
            plannedTrips = emptyList(),
            focusOverride = focusOverride,
            breadcrumbPoints = emptyList(),
            waypoints = emptyList(),
            focusedObservationId = null,
            keptTrackPolylines = emptyList(),
            findMarkers = emptyList(),
            photoMarkers = emptyList(),
            offlineRegionCircles = emptyList(),
            showSearchCentre = false,
            cameraRequest = mode.cameraRequest,
            journalHighlights = com.zynergylabs.forager.app.domain.JournalEntryHighlights.NONE,
            searchFrameRequestId = content.searchFrameRequestId,
            resources = LocalContext.current.resources,
        )
    }

    private fun describe(c: CameraPosition?) = "(${c?.target?.latitude}, ${c?.target?.longitude}) at zoom ${c?.zoom}"

    @Test
    fun `the entry map's locate glides to the fix over Re-center's duration, keeping the zoom, without following`() {
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = entry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = slot,
                night = false,
                getMapData = { _, _ -> mapData },
                getCoveringOfflineRegion = { _, _ -> null },
                getCurrentLocation = { LocationResult.Success(lat = fix.lat, lng = fix.lng) },
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()
        assertNotNull("the map section resolved", onMapTap)
        onMapTap?.invoke()
        composeRule.waitForIdle()

        val recording = made.second
        composeRule.runOnUiThread { made.first.cameraPosition = panned }
        composeRule.waitForIdle()
        val jumpsAfterPan = recording.jumps
        val easesAfterPan = recording.eases.size

        val node = composeRule.onNode(hasContentDescription("Center on my location"))
        val b = node.fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(b.center.x, b.center.y)) }
        composeRule.waitForIdle()

        assertEquals(
            "the locate moved the camera instantly (to ${describe(recording.camera)}) instead of gliding",
            jumpsAfterPan,
            recording.jumps,
        )
        val glides = recording.eases.drop(easesAfterPan)
        assertEquals("one animated move for one locate, was $glides", 1, glides.size)
        val glide = glides.single()
        assertTrue(
            "the glide ends on the fix at the user's zoom: expected (${fix.lat}, ${fix.lng}) at zoom ${panned.zoom}, was ${describe(glide.camera)}",
            glide.camera.target?.latitude == fix.lat && glide.camera.target?.longitude == fix.lng && glide.camera.zoom == panned.zoom,
        )
        assertEquals("over Re-center's duration", NAVIGATION_VIEW_TRANSITION_MILLIS, glide.durationMs)
        assertEquals("an ease, as Re-center's camera moves are", "easeTo", glide.call)
        assertEquals("no following on the entry map", CameraMode.NONE, recording.cameraMode)
        assertEquals("the entry map still never tracks live location", false, renderMode?.trackLiveLocation)
    }
}
