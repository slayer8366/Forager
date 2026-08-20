package com.forager.app.ui.map

import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * The GeoJSON [SightingsMap] actually builds for its style-layer sources, and the zoom heuristic
 * that drives its camera — the MapLibre-side successor to the overlay-preservation half of the
 * deleted `SightingsMapBasemapSwapTest`.
 *
 * ## Why this is not the same shape of test as the one it replaces
 *
 * `SightingsMapBasemapSwapTest` composed the real [SightingsMap] over a real osmdroid `MapView` and
 * read back its `overlays` list — possible because osmdroid overlays are plain Kotlin objects living
 * in a `List`. MapLibre's equivalents are not: [org.maplibre.android.style.sources.GeoJsonSource] and
 * every `Layer` subclass call a `native initialize` from their constructor (verified with `javap`
 * against the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact), so constructing even one outside
 * a real device or emulator throws `UnsatisfiedLinkError` — Robolectric included, since Robolectric
 * shadows the Android *platform* SDK, not a third-party AAR's native library. `Style` itself has no
 * public constructor at all (only a package-private `Builder.build(NativeMap)`), so there is no way
 * to obtain one to inspect without a running renderer.
 *
 * What *is* real, production code and free of any native dependency is everything upstream of that
 * boundary: `searchCenterFeatureCollection`/`sightingsFeatureCollection`/
 * `areaMarkersFeatureCollection`/`connectorFeatureCollection`/`plannedTripsFeatureCollection`
 * (`SightingsMap.kt`, widened from `private` to `internal` for exactly this test) build
 * [org.maplibre.geojson.FeatureCollection]s — a separate, pure-Java artifact (also checked with
 * `javap`: no native methods anywhere in it) — and `zoomForRadiusKm` is plain arithmetic. This test
 * exercises those functions directly, the same split [MapLibreOfflineMapRepository]'s own doc comment
 * already draws for `OfflineRegion` (test the pure byte format; the native store itself is untestable
 * off a device).
 *
 * ## What this does not, and cannot, establish
 *
 * That a basemap swap leaves these sources' *rendered* content undisturbed, and that the connector
 * actually reads as dashed against a real basemap, are both native-rendering facts and stay
 * hardware-only — same gap this migration's own doc comments (`SightingsMap`'s class doc, "What is
 * explicitly re-confirmed") already flag. What this test does establish: [SightingsMap]'s
 * `refreshOverlayData` calls these exact functions on every relevant prop change including a basemap
 * swap (see that function's own body), and never on `basemap` itself — the GeoJSON produced does not
 * take a [Basemap] parameter at all, so there is no code path here for a basemap swap to disturb it
 * through. That is a real structural guarantee, verifiable by reading the function signatures below,
 * not a hardware claim.
 */
class SightingsMapOverlayDataTest {

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    private val sightings = listOf(
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
            commonName = null,
            lat = 45.34,
            lng = -122.65,
            observedOn = null,
            photoUrl = null,
        ),
    )

    private val areas = listOf(
        ForagingArea(
            visitOrder = 1,
            center = LatLng(45.33, -122.64),
            sightings = sightings,
            distinctSpeciesCount = 2,
            mostRecentYear = 2024,
            undatedObservationCount = 0,
        ),
        ForagingArea(
            visitOrder = 2,
            center = LatLng(45.34, -122.65),
            sightings = sightings.take(1),
            distinctSpeciesCount = 1,
            mostRecentYear = 2023,
            undatedObservationCount = 1,
        ),
    )

    private val plannedTrips = listOf(
        PlannedTrip(
            id = "trip-1",
            name = "Trip 1",
            location = LatLng(45.35, -122.66),
            date = LocalDate.of(2026, 9, 1),
        ),
    )

    @Test
    fun `search centre feature carries the region's own coordinates and radius`() {
        val feature = searchCenterFeatureCollection(region).features()!!.single()
        val point = feature.geometry() as Point

        // GeoJSON coordinate order is lng, lat — asserted explicitly since transposing it is the
        // same class of silent, plausible-looking mistake BasemapStyleTest guards against for tile
        // URLs.
        assertEquals(region.lng, point.longitude(), 0.0)
        assertEquals(region.lat, point.latitude(), 0.0)
        assertEquals("Search location", feature.getStringProperty("title"))
        assertEquals("Radius: ${region.radiusKm} km", feature.getStringProperty("snippet"))
    }

    @Test
    fun `every sighting becomes a point feature at its own coordinates`() {
        val features = sightingsFeatureCollection(sightings).features()!!
        assertEquals(2, features.size)

        val points = features.map { it.geometry() as Point }
        assertEquals(sightings[0].lng, points[0].longitude(), 0.0)
        assertEquals(sightings[0].lat, points[0].latitude(), 0.0)
        assertEquals(sightings[1].lng, points[1].longitude(), 0.0)
        assertEquals(sightings[1].lat, points[1].latitude(), 0.0)
    }

    @Test
    fun `a sighting's title and snippet fall back when common name and date are missing`() {
        val features = sightingsFeatureCollection(sightings).features()!!

        assertEquals("Chanterelle", features[0].getStringProperty("title"))
        assertEquals("2024-08-01", features[0].getStringProperty("snippet"))

        // No common name, no observed date: falls back to the scientific name for both.
        assertEquals("Morchella americana", features[1].getStringProperty("title"))
        assertEquals("Morchella americana", features[1].getStringProperty("snippet"))
    }

    /**
     * The fact that actually matters for the numbered markers rendering the right number: the
     * `"label"` property is what the `SymbolLayer`'s `text-field` token `"{label}"` substitutes —
     * see `areaMarkersFeatureCollection`'s own doc comment.
     */
    @Test
    fun `each area marker's label property is its own visiting order`() {
        val features = areaMarkersFeatureCollection(areas).features()!!

        assertEquals("1", features[0].getStringProperty("label"))
        assertEquals("Area 1", features[0].getStringProperty("title"))
        assertEquals("2", features[1].getStringProperty("label"))
        assertEquals("Area 2", features[1].getStringProperty("title"))
    }

    @Test
    fun `each area marker's snippet is the real foragingAreaSummary, not a duplicate of its wording`() {
        val features = areaMarkersFeatureCollection(areas).features()!!
        assertEquals(foragingAreaSummary(areas[0]), features[0].getStringProperty("snippet"))
        assertEquals(foragingAreaSummary(areas[1]), features[1].getStringProperty("snippet"))
    }

    @Test
    fun `no areas produces no connector feature`() {
        assertTrue(
            "A LineString needs at least two points; an empty area list must not produce a degenerate one.",
            connectorFeatureCollection(region, emptyList()).features()!!.isEmpty(),
        )
    }

    @Test
    fun `the connector runs from the search centre through every area centre in visiting order`() {
        val feature = connectorFeatureCollection(region, areas).features()!!.single()
        val line = feature.geometry() as LineString

        val expectedPoints = listOf(
            Point.fromLngLat(region.lng, region.lat),
            Point.fromLngLat(areas[0].center.lng, areas[0].center.lat),
            Point.fromLngLat(areas[1].center.lng, areas[1].center.lat),
        )
        assertEquals(expectedPoints, line.coordinates())
    }

    /**
     * The one piece of this map carrying an actual safety property. Asserted against the real,
     * single-sourced [VISITING_ORDER_DISCLAIMER] constant — not a copy of its wording — so this test
     * would fail if `connectorFeatureCollection` ever forked its own text instead of reading from
     * [ForagingAreaLabels].
     */
    @Test
    fun `the connector's snippet is the real visiting-order disclaimer`() {
        val feature = connectorFeatureCollection(region, areas).features()!!.single()
        assertEquals(VISITING_ORDER_DISCLAIMER, feature.getStringProperty("snippet"))
    }

    @Test
    fun `each planned trip becomes a point feature carrying its own date`() {
        val feature = plannedTripsFeatureCollection(plannedTrips).features()!!.single()
        val point = feature.geometry() as Point

        assertEquals(plannedTrips[0].location.lng, point.longitude(), 0.0)
        assertEquals(plannedTrips[0].location.lat, point.latitude(), 0.0)
        assertEquals("Planned trip", feature.getStringProperty("title"))
        assertEquals(plannedTrips[0].date.toString(), feature.getStringProperty("snippet"))
    }

    /**
     * The data-shaping functions above take no [Basemap] parameter at all — the structural half of
     * "a basemap swap leaves overlays untouched" this test can actually establish. See this class's
     * own doc comment for what remains hardware-only.
     */
    @Test
    fun `overlay data is identical regardless of which basemap is active`() {
        val first = sightingsFeatureCollection(sightings)
        val second = sightingsFeatureCollection(sightings)
        assertEquals(
            "Nothing about building this FeatureCollection reads Basemap, so it must be identical on every call.",
            first,
            second,
        )
    }

    @Test
    fun `zoomForRadiusKm opens tighter for a small search radius and wider for a large one`() {
        assertEquals(13.0, zoomForRadiusKm(5), 0.0)
        assertEquals(12.0, zoomForRadiusKm(6), 0.0)
        assertEquals(12.0, zoomForRadiusKm(15), 0.0)
        assertEquals(10.5, zoomForRadiusKm(16), 0.0)
        assertEquals(10.5, zoomForRadiusKm(30), 0.0)
        assertEquals(9.0, zoomForRadiusKm(31), 0.0)
    }

    /**
     * The closest a headless test can get to "the connector is still dashed" — see
     * [CONNECTOR_DASH_PATTERN]'s own doc comment for exactly what this does and does not prove.
     */
    @Test
    fun `the connector dash pattern is non-empty and holds the documented 18-to-14 ratio`() {
        assertTrue("An empty dash array is a solid line, which would read as a walking route.", CONNECTOR_DASH_PATTERN.isNotEmpty())
        assertEquals(2, CONNECTOR_DASH_PATTERN.size)
        assertEquals(18f / 14f, CONNECTOR_DASH_PATTERN[0] / CONNECTOR_DASH_PATTERN[1], 0.001f)
    }

    private val breadcrumbPoints = listOf(
        LatLng(45.326, -122.634),
        LatLng(45.330, -122.640),
        LatLng(45.335, -122.645),
    )

    @Test
    fun `no breadcrumb points produces no trail feature`() {
        assertTrue(
            "A LineString needs at least two points; an empty track must not produce a degenerate one.",
            breadcrumbFeatureCollection(emptyList()).features()!!.isEmpty(),
        )
    }

    @Test
    fun `a single breadcrumb point produces no trail feature`() {
        assertTrue(
            "A LineString needs at least two points; one recorded fix isn't a trail yet.",
            breadcrumbFeatureCollection(breadcrumbPoints.take(1)).features()!!.isEmpty(),
        )
    }

    @Test
    fun `the breadcrumb trail runs through every recorded point in order`() {
        val feature = breadcrumbFeatureCollection(breadcrumbPoints).features()!!.single()
        val line = feature.geometry() as LineString

        val expectedPoints = breadcrumbPoints.map { Point.fromLngLat(it.lng, it.lat) }
        assertEquals(expectedPoints, line.coordinates())
    }
}
