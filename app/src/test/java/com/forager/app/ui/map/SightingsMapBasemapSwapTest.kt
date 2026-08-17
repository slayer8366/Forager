package com.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * That changing the basemap leaves everything the app draws on the map exactly as it was.
 *
 * ## Why this is a test and not an argument
 *
 * The argument is sound: tile sources feed osmdroid's `TilesOverlay`, while the sighting dots, the
 * numbered area markers, the dashed connector, the planned-trip diamonds and the search-centre pin
 * are app-drawn entries in `MapView.overlays`, so the two have nothing to do with each other. But
 * [SightingsMap]'s `update` block rebuilds that overlay list on every recomposition — and it does it
 * by *truncating* the list back to one entry (`while (view.overlays.size > 1)`) on the standing
 * assumption that the survivor at index 0 is the `CopyrightOverlay`. A basemap change is a
 * recomposition, so it runs that truncation. An off-by-one there, or anything that inserted an
 * overlay ahead of the copyright one, would quietly delete or duplicate real map content on a
 * basemap switch. That is a cheap mistake to make and an expensive one to notice by eye.
 *
 * So this composes the real [SightingsMap] with a real osmdroid `MapView` and compares the whole
 * overlay list either side of the switch.
 *
 * ## Why the swap itself is asserted first
 *
 * CLAUDE.md: a check that passes identically before and after a change is suspect. A test that only
 * compared overlays would pass just as happily if the basemap never changed at all — which is the
 * single most likely way this feature breaks, given [SightingsMap] deliberately skips
 * `setTileSource` when the source already matches. Every test here therefore asserts the tile source
 * actually moved before drawing any conclusion from what survived.
 *
 * ## What this does not cover
 *
 * Nothing here renders. Robolectric gives a real `MapView` with a real overlay list and a real tile
 * provider, but no pixels: that the USGS tiles are legible, and that the dot and connector colours
 * read against topographic terrain rather than OpenStreetMap's flatter palette, is unverifiable
 * headlessly and is listed as such in README's "Not yet verified".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SightingsMapBasemapSwapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Declares the Compose test host activity on Robolectric's package manager before [composeRule]
     * tries to launch it, exactly as `AvailabilityScreenLayoutTest` does and for the same reason:
     * `androidx.compose.ui:ui-test-manifest` would put that activity into the debug APK a tester
     * installs, so it is registered at runtime instead and the shipped manifest stays untouched.
     * An [ExternalResource] in a [RuleChain] rather than an `@Before`, because JUnit runs every
     * rule's `before` ahead of every `@Before`, and the compose rule launches the activity in its own.
     */
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `every overlay survives a basemap change`() {
        // Starts on OpenStreetMap and switches to USGS Topo, rather than the reverse: the
        // interesting direction is the one a user takes when they open the app somewhere USGS has no
        // tiles and reach for the standard map, and back.
        val map = composeMapWithOverlays(initialBasemap = Basemap.OSM_STANDARD)
        val sourceBefore = map.tileProvider.tileSource.name()
        val before = map.snapshotOverlays()

        // The snapshot is only worth comparing if it holds the content this test claims to protect.
        // Asserted explicitly rather than as "not empty", so a future change that dropped the area
        // markers or the connector fails here instead of comparing two equally impoverished lists.
        assertEquals(
            listOf(
                "Search location",
                "Chanterelle",
                "Morel",
                "Area 1",
                "Area 2",
                "Planned trip",
            ),
            before.markerTitles,
        )
        assertEquals(1, before.polylineCount)
        assertEquals(
            "The connector runs from the search centre through both area centres.",
            3,
            before.connectorPointCount,
        )
        assertTrue("The connector must still be dashed before the swap.", before.connectorIsDashed)
        assertEquals(VISITING_ORDER_DISCLAIMER, before.connectorSnippet)
        assertEquals(1, before.copyrightOverlayCount)
        assertEquals(1, before.mapEventsOverlayCount)

        basemapUnderTest = Basemap.USGS_TOPO
        composeRule.waitForIdle()

        assertNotEquals(
            "The basemap did not actually change, so nothing below proves anything survived one.",
            sourceBefore,
            map.tileProvider.tileSource.name(),
        )
        assertEquals(
            "Changing the basemap altered the app's own overlays.",
            before,
            map.snapshotOverlays(),
        )
    }

    /**
     * The honesty affordances specifically. They are covered by the whole-snapshot comparison above
     * too, but they are the part of this map carrying a claim about what the app does *not* know, so
     * they get named assertions that say what broke rather than "a data class differed".
     */
    @Test
    fun `the visiting-order disclaimer and dashed connector survive a basemap change`() {
        // Starts on OSM_STANDARD rather than the default: Basemap.DEFAULT now resolves to
        // OPEN_TOPO_MAP (see MapService), and the switch below is to that same basemap, so starting
        // there would make this "switch" a no-op the assertNotEquals below is specifically checking
        // didn't happen.
        val map = composeMapWithOverlays(initialBasemap = Basemap.OSM_STANDARD)
        val sourceBefore = map.tileProvider.tileSource.name()

        basemapUnderTest = Basemap.OPEN_TOPO_MAP
        composeRule.waitForIdle()
        assertNotEquals(sourceBefore, map.tileProvider.tileSource.name())

        val connector = map.overlays.filterIsInstance<Polyline>().single()
        assertTrue(
            "The order connector must stay dashed on every basemap: the dash is what says this is " +
                "a visiting order and not a walking route.",
            connector.outlinePaint.pathEffect != null,
        )
        assertEquals(VISITING_ORDER_DISCLAIMER, connector.snippet)
    }

    @Test
    fun `switching to USGS Topo lowers the map's zoom ceiling and switching back raises it`() {
        val map = composeMapWithOverlays(initialBasemap = Basemap.OSM_STANDARD)
        assertEquals(
            "OpenStreetMap's ceiling should be in place to start with.",
            Basemap.OSM_STANDARD.maxZoom.toDouble(),
            map.maxZoomLevel,
            0.0,
        )

        basemapUnderTest = Basemap.USGS_TOPO
        composeRule.waitForIdle()
        assertEquals(
            "USGS Topo's lower ceiling must be installed on the MapView, not just recorded in Basemap.",
            Basemap.USGS_TOPO.maxZoom.toDouble(),
            map.maxZoomLevel,
            0.0,
        )

        // Back again: the ceiling is sticky in osmdroid (MapView prefers its own field over the tile
        // source's declared value once set), so a swap that installed a limit without ever lifting it
        // would leave the user stuck at 15 on OpenStreetMap.
        basemapUnderTest = Basemap.OSM_STANDARD
        composeRule.waitForIdle()
        assertEquals(
            "Switching back must restore the higher ceiling.",
            Basemap.OSM_STANDARD.maxZoom.toDouble(),
            map.maxZoomLevel,
            0.0,
        )
    }

    /**
     * The user-facing half of the ceiling: asking to zoom past it gets refused rather than served a
     * wall of missing tiles.
     *
     * `controller.setZoom` is what osmdroid's own pinch-zoom and double-tap handlers call, so this
     * drives the same path a gesture would; there is no way to deliver a real pinch headlessly.
     */
    @Test
    fun `zooming past the USGS ceiling is clamped to it`() {
        val map = composeMapWithOverlays(initialBasemap = Basemap.USGS_TOPO)

        map.controller.setZoom(Basemap.OSM_STANDARD.maxZoom.toDouble())

        assertEquals(
            "A zoom request beyond USGS Topo's ${Basemap.USGS_TOPO.maxZoom} must be clamped to it. " +
                "Unclamped, the user reaches zoom levels the service answers with 404 and reads the " +
                "resulting blank map as the app being broken.",
            Basemap.USGS_TOPO.maxZoom.toDouble(),
            map.zoomLevelDouble,
            0.0,
        )
    }

    /** The basemap the composed [SightingsMap] is currently showing; reassigned to drive a swap. */
    private var basemapUnderTest by mutableStateOf(Basemap.DEFAULT)

    /**
     * Composes the real [SightingsMap] over data that exercises every overlay kind at once, and
     * returns the osmdroid `MapView` it created.
     */
    private fun composeMapWithOverlays(initialBasemap: Basemap = Basemap.DEFAULT): MapView {
        basemapUnderTest = initialBasemap
        composeRule.setContent {
            SightingsMap(
                region = REGION,
                sightings = SIGHTINGS,
                areas = AREAS,
                plannedTrips = PLANNED_TRIPS,
                basemap = basemapUnderTest,
            )
        }
        composeRule.waitForIdle()
        return requireNotNull(composeRule.activity.window.decorView.findMapView()) {
"SightingsMap did not put a MapView in the view tree."
        }
    }
}

/**
 * The overlay list reduced to the things about it worth comparing.
 *
 * A data class rather than a count: "the same number of overlays" would be satisfied by markers that
 * had moved, lost their titles, or swapped identities, and the positions are what tie a marker to a
 * real observation. Titles are compared in list order because [SightingsMap] rebuilds the overlays in
 * a fixed order (centre pin, sighting dots, connector, numbered areas, planned trips), so a reordering
 * is itself a change worth failing on.
 */
private data class OverlaySnapshot(
    val markerTitles: List<String?>,
    val markerPositions: List<Pair<Double, Double>>,
    val markerSnippets: List<String?>,
    val polylineCount: Int,
    val connectorPointCount: Int?,
    val connectorIsDashed: Boolean,
    val connectorSnippet: String?,
    val copyrightOverlayCount: Int,
    val mapEventsOverlayCount: Int,
)

private fun MapView.snapshotOverlays(): OverlaySnapshot {
    val markers = overlays.filterIsInstance<Marker>()
    val connector = overlays.filterIsInstance<Polyline>().firstOrNull()
    return OverlaySnapshot(
        markerTitles = markers.map { it.title },
        markerPositions = markers.map { it.position.latitude to it.position.longitude },
        markerSnippets = markers.map { it.snippet },
        polylineCount = overlays.count { it is Polyline },
        connectorPointCount = connector?.actualPoints?.size,
        connectorIsDashed = connector?.outlinePaint?.pathEffect != null,
        connectorSnippet = connector?.snippet,
        copyrightOverlayCount = overlays.count { it is CopyrightOverlay },
        mapEventsOverlayCount = overlays.count { it is MapEventsOverlay },
    )
}

/** Depth-first search for the hosted osmdroid view, which `AndroidView` puts inside a holder. */
private fun View.findMapView(): MapView? = when {
    this is MapView -> this
    this is ViewGroup -> (0 until childCount).asSequence()
        .mapNotNull { getChildAt(it).findMapView() }
        .firstOrNull()

    else -> null
}

/** Portland, Oregon — the reference location used throughout this project. */
private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val SIGHTINGS = listOf(
    Sighting(
        observationId = 1L,
        taxonId = 47348L,
        scientificName = "Cantharellus formosus",
        commonName = "Chanterelle",
        lat = 45.33,
        lng = -122.64,
        observedOn = LocalDate.of(2024, 8, 1),
        photoUrl = null,
    ),
    Sighting(
        observationId = 2L,
        taxonId = 48522L,
        scientificName = "Morchella americana",
        commonName = "Morel",
        lat = 45.34,
        lng = -122.65,
        observedOn = LocalDate.of(2024, 8, 2),
        photoUrl = null,
    ),
)

private val AREAS = listOf(
    ForagingArea(
        visitOrder = 1,
        center = LatLng(45.33, -122.64),
        sightings = SIGHTINGS,
        distinctSpeciesCount = 2,
        mostRecentYear = 2024,
        undatedObservationCount = 0,
    ),
    ForagingArea(
        visitOrder = 2,
        center = LatLng(45.34, -122.65),
        sightings = SIGHTINGS.take(1),
        distinctSpeciesCount = 1,
        mostRecentYear = 2023,
        undatedObservationCount = 0,
    ),
)

private val PLANNED_TRIPS = listOf(
    PlannedTrip(
        id = "trip-1",
        name = "Trip 1",
        location = LatLng(45.35, -122.66),
        date = LocalDate.of(2026, 9, 1),
    ),
)
