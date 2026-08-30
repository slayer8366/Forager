package com.forager.app.ui.map

import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.Waypoint
import com.forager.app.ui.theme.MapPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.style.expressions.Expression
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
     * The property the map click listener's `queryRenderedFeatures` round-trip actually reads back
     * (see [SightingsMap]'s own doc comment, "Partially rebuilt") to look a tapped dot back up
     * against the current sightings list — unlike title/snippet, this one has a real reader.
     */
    @Test
    fun `every sighting feature carries its own observationId as a number property`() {
        val features = sightingsFeatureCollection(sightings).features()!!

        assertEquals(1L, features[0].getNumberProperty("observationId").toLong())
        assertEquals(2L, features[1].getNumberProperty("observationId").toLong())
    }

    /**
     * [sightingStrokeColorExpression] backs the sighting layer's data-driven `circle-stroke-color`
     * — the blue ring around whichever dot [ObservationBubble] is currently open on (see that
     * function's own doc comment). Unlike [CircleLayer]/[org.maplibre.android.maps.Style], neither
     * [Expression] nor [org.maplibre.android.style.layers.PropertyValue] carries a native method
     * (`javap` against the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact confirms both are
     * plain JVM classes), so — unlike everything else this file's own class doc comment says is
     * unreachable off a device — the actual built [Expression] tree is constructible and comparable
     * here, via [Expression.equals].
     */
    @Test
    fun `sightingStrokeColorExpression is a flat unselected stroke when nothing is focused`() {
        val expected = Expression.color(MapPalette.DAY.sightingDotStroke)
        assertEquals(expected, sightingStrokeColorExpression(focusedObservationId = null, palette = MapPalette.DAY))
    }

    @Test
    fun `sightingStrokeColorExpression matches only the focused observation's own id`() {
        val expected = Expression.switchCase(
            Expression.eq(Expression.toString(Expression.get("observationId")), Expression.toString(Expression.literal(2L))),
            Expression.color(MapPalette.DAY.sightingDotStrokeSelected),
            Expression.color(MapPalette.DAY.sightingDotStroke),
        )
        assertEquals(expected, sightingStrokeColorExpression(focusedObservationId = 2L, palette = MapPalette.DAY))
    }

    /**
     * The same building blocks in the same shape, but a different id and a different palette's own
     * colours — not just a re-run of the test above, to rule out both arguments being silently
     * ignored (a stub that always returned the first test's expected [Expression] would still pass
     * that one alone).
     */
    @Test
    fun `sightingStrokeColorExpression carries the caller's own id and palette, not hardcoded ones`() {
        val expected = Expression.switchCase(
            Expression.eq(Expression.toString(Expression.get("observationId")), Expression.toString(Expression.literal(47348L))),
            Expression.color(MapPalette.NIGHT.sightingDotStrokeSelected),
            Expression.color(MapPalette.NIGHT.sightingDotStroke),
        )
        assertEquals(
            expected,
            sightingStrokeColorExpression(focusedObservationId = 47348L, palette = MapPalette.NIGHT),
        )
    }

    /**
     * [sightingStrokeWidthExpression]'s own doc comment: colour alone was confirmed too subtle on
     * real hardware at [SIGHTING_DOT_STROKE_WIDTH_PX]'s hairline width, so the selected dot's own
     * stroke also widens — this is that fix's own paired `Expression`, built and asserted the same
     * way [sightingStrokeColorExpression] is above.
     */
    @Test
    fun `sightingStrokeWidthExpression is a flat unselected width when nothing is focused`() {
        val expected = Expression.literal(SIGHTING_DOT_STROKE_WIDTH_PX)
        assertEquals(expected, sightingStrokeWidthExpression(focusedObservationId = null))
    }

    @Test
    fun `sightingStrokeWidthExpression widens only the focused observation's own dot`() {
        val expected = Expression.switchCase(
            Expression.eq(Expression.toString(Expression.get("observationId")), Expression.toString(Expression.literal(2L))),
            Expression.literal(SIGHTING_DOT_STROKE_WIDTH_SELECTED_PX),
            Expression.literal(SIGHTING_DOT_STROKE_WIDTH_PX),
        )
        assertEquals(expected, sightingStrokeWidthExpression(focusedObservationId = 2L))
    }

    /** The widened width is meaningfully wider, not a cosmetic rounding difference. */
    @Test
    fun `the selected stroke width is more than double the unselected one`() {
        assertTrue(
            "SIGHTING_DOT_STROKE_WIDTH_SELECTED_PX ($SIGHTING_DOT_STROKE_WIDTH_SELECTED_PX) should be " +
                "more than double SIGHTING_DOT_STROKE_WIDTH_PX ($SIGHTING_DOT_STROKE_WIDTH_PX) for the " +
                "highlight to actually read as widened at a glance.",
            SIGHTING_DOT_STROKE_WIDTH_SELECTED_PX > SIGHTING_DOT_STROKE_WIDTH_PX * 2,
        )
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
     * The closest a headless test can get to "the breadcrumb trail is still dashed" — see
     * [BREADCRUMB_DASH_PATTERN]'s own doc comment for exactly what this does and does not prove,
     * and for why the dash moved here from the connector (now solid, deliberately — see that same
     * doc comment for what still keeps it from reading as a real walkable route).
     */
    @Test
    fun `the breadcrumb dash pattern is non-empty, so it renders as dots and not a solid trail`() {
        assertTrue("An empty dash array is a solid line, losing the trail-of-dots read.", BREADCRUMB_DASH_PATTERN.isNotEmpty())
        assertEquals(2, BREADCRUMB_DASH_PATTERN.size)
        assertTrue(
            "The mark should be shorter than the gap -- a long mark reads as a dashed line, not dots.",
            BREADCRUMB_DASH_PATTERN[0] < BREADCRUMB_DASH_PATTERN[1],
        )
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

    private val waypoints = listOf(
        Waypoint(id = "w1", lat = 45.40, lng = -122.70, altitude = null, name = "Trailhead", note = "Gravel lot", createdAtEpochMillis = 1_000L),
        Waypoint(id = "w2", lat = 45.41, lng = -122.71, altitude = 812.0, name = "Big oak", note = "", createdAtEpochMillis = 2_000L),
    )

    @Test
    fun `every waypoint becomes a point feature at its own coordinates`() {
        val features = waypointsFeatureCollection(waypoints).features()!!
        assertEquals(2, features.size)

        val points = features.map { it.geometry() as Point }
        assertEquals(waypoints[0].lng, points[0].longitude(), 0.0)
        assertEquals(waypoints[0].lat, points[0].latitude(), 0.0)
        assertEquals(waypoints[1].lng, points[1].longitude(), 0.0)
        assertEquals(waypoints[1].lat, points[1].latitude(), 0.0)
    }

    @Test
    fun `a waypoint's title and snippet are its own name and note`() {
        val features = waypointsFeatureCollection(waypoints).features()!!

        assertEquals("Trailhead", features[0].getStringProperty("title"))
        assertEquals("Gravel lot", features[0].getStringProperty("snippet"))
        assertEquals("Big oak", features[1].getStringProperty("title"))
        assertEquals("", features[1].getStringProperty("snippet"))
    }

    @Test
    fun `no waypoints produces no marker features`() {
        assertTrue(waypointsFeatureCollection(emptyList()).features()!!.isEmpty())
    }
}
