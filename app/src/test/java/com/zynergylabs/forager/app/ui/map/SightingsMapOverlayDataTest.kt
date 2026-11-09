package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.TRACK_WIDTH_ZOOM_STOPS
import com.zynergylabs.forager.app.ui.theme.MapPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * `plannedTripsFeatureCollection` (`SightingsMap.kt`, widened from `private` to `internal` for
 * exactly this test) build [org.maplibre.geojson.FeatureCollection]s — a separate, pure-Java
 * artifact (also checked with `javap`: no native methods anywhere in it) — and `zoomForRadiusKm`
 * is plain arithmetic. This test exercises those functions directly, the same split
 * [MapLibreOfflineMapRepository]'s own doc comment already draws for `OfflineRegion` (test the
 * pure byte format; the native store itself is untestable off a device).
 *
 * ## What this does not, and cannot, establish
 *
 * That a basemap swap leaves these sources' *rendered* content undisturbed is a native-rendering
 * fact and stays hardware-only — same gap this migration's own doc comments (`SightingsMap`'s
 * class doc, "What is explicitly re-confirmed") already flag. What this test does establish: [SightingsMap]'s
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

    private val plannedTrips = listOf(
        PlannedTrip(
            id = "trip-1",
            name = "Trip 1",
            location = LatLng(45.35, -122.66),
            date = LocalDate.of(2026, 9, 1),
        ),
    )

    @Test
    fun `the search-centre source is empty when the caller turns the marker off, and unchanged when it does not`() {
        // Plate-pulse follow-up, owner ruling on item 5: an entry map's region is a computed box
        // midpoint, and a marker there marks a point where nothing happened.
        assertTrue(searchCentreOverlay(region, showSearchCentre = false).features()!!.isEmpty())

        val shown = searchCentreOverlay(region, showSearchCentre = true).features()!!.single()
        assertEquals(region.lng, (shown.geometry() as Point).longitude(), 0.0)
        assertEquals(region.lat, (shown.geometry() as Point).latitude(), 0.0)
    }

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
     * The property [sightingStrokeColorExpression] actually reads to ring the selected dot — see
     * that function's own doc comment for why this is a plain boolean baked into the feature's own
     * data rather than an id comparison evaluated inside a GL expression.
     */
    @Test
    fun `no sighting feature is marked selected when nothing is focused`() {
        val features = sightingsFeatureCollection(sightings).features()!!
        assertEquals(false, features[0].getBooleanProperty("selected"))
        assertEquals(false, features[1].getBooleanProperty("selected"))
    }

    @Test
    fun `only the focused observation's own feature is marked selected`() {
        val features = sightingsFeatureCollection(sightings, focusedObservationId = 2L).features()!!
        assertEquals(false, features[0].getBooleanProperty("selected"))
        assertEquals(true, features[1].getBooleanProperty("selected"))
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
    fun `sightingStrokeColorExpression branches on the feature's own selected property`() {
        val expected = Expression.switchCase(
            Expression.get("selected"),
            Expression.color(MapPalette.DAY.sightingDotStrokeSelected),
            Expression.color(MapPalette.DAY.sightingDotStroke),
        )
        assertEquals(expected, sightingStrokeColorExpression(palette = MapPalette.DAY))
    }

    /**
     * A different palette's own colours — not just a re-run of the test above, to rule out the
     * palette argument being silently ignored (a stub that always returned the first test's
     * expected [Expression] would still pass that one alone).
     *
     * Changed in colour build C2: this used [MapPalette.NIGHT], but since C2 both palettes carry the
     * same two rings (white, and `#2196F3` selected, owner override), so NIGHT could no longer tell a
     * palette-ignoring stub from the real function. A palette with deliberately different rings can.
     */
    @Test
    fun `sightingStrokeColorExpression carries the caller's own palette, not a hardcoded one`() {
        val other = MapPalette.DAY.copy(sightingDotStroke = 0xFF123456.toInt(), sightingDotStrokeSelected = 0xFF654321.toInt())
        val expected = Expression.switchCase(
            Expression.get("selected"),
            Expression.color(0xFF654321.toInt()),
            Expression.color(0xFF123456.toInt()),
        )
        assertEquals(expected, sightingStrokeColorExpression(palette = other))
    }

    /**
     * Colour build C2 (owner-approved tweak): the selected ring is 3dp, the unselected 1.5dp. Expected
     * values are literals, not the constants, so a constant drifting back is a failure here.
     */
    @Test
    fun `the selected sighting ring is 3dp wide and every other ring half that`() {
        val expected = Expression.switchCase(
            Expression.get("selected"),
            Expression.literal(3f),
            Expression.literal(1.5f),
        )
        assertEquals(expected, sightingStrokeWidthExpression())
    }

    /**
     * Colour build C2 (c): each track has a casing line directly below it (added immediately before it,
     * so drawn immediately under it) on the same source, in the casing colour, 1.5dp wider on each
     * side at full width, solid even under the dashed breadcrumb, with the track's own round caps and
     * joins. Since track widths by zoom (owner, 2026-09-28) those are the widths at zoom 15 and above:
     * the track and its casing share the same zoom stops, so both thin out together
     * (`TrackWidthByZoomTest` holds the widths at every zoom).
     */
    @Test
    fun `each track has a solid casing line directly below it, wider by the casing on each side at full width, thinning with it, in the casing colour`() {
        val specs = trackLayerSpecs()
        val tracks = listOf(
            Triple("breadcrumb", BREADCRUMB_DASH_PATTERN.toList(), MapPalette::breadcrumb),
            Triple("kept track", null, MapPalette::keptTrack),
        )
        assertEquals("two tracks and two casings", 4, specs.size)
        for ((name, dash, role) in tracks) {
            val index = specs.indexOfFirst { it.colour(MapPalette.DAY) == role(MapPalette.DAY) }
            assertTrue("$name is in the stack", index >= 0)
            val track = specs[index]
            assertEquals("$name night colour", role(MapPalette.NIGHT), track.colour(MapPalette.NIGHT))
            assertEquals("$name dash", dash, track.dashPattern)
            assertEquals("$name width at full width", 6f, track.widthDp)
            assertTrue("$name has a layer directly below it", index >= 1)
            val casing = specs[index - 1]
            assertEquals("$name casing is on the track's own source", track.sourceId, casing.sourceId)
            assertEquals("$name casing, day", MapPalette.DAY.casing, casing.colour(MapPalette.DAY))
            assertEquals("$name casing, night", MapPalette.NIGHT.casing, casing.colour(MapPalette.NIGHT))
            assertEquals("$name casing width at full width: 1.5dp each side", 9f, casing.widthDp)
            assertEquals("$name thins out by the track stops", TRACK_WIDTH_ZOOM_STOPS, track.widthByZoom)
            assertEquals("$name casing thins out with its track", track.widthByZoom, casing.widthByZoom)
            assertEquals("$name casing is solid", null, casing.dashPattern)
            assertTrue("$name casing has round caps", casing.roundCaps)
            assertTrue("$name has round caps", track.roundCaps)
        }
        assertEquals("every layer id is distinct", specs.size, specs.map { it.layerId }.toSet().size)
    }

    /**
     * Colour build C2 (c): the offline region's outline is a dashed line in the casing colour, 1.5dp
     * wide, dash 6dp and gap 4dp. `line-dasharray` is in multiples of the line width, so the pattern
     * times the width is the dash and gap in dp.
     */
    @Test
    fun `the offline region's outline is a thin dashed casing line, dash 6dp and gap 4dp`() {
        val spec = offlineRegionOutlineSpec()
        assertEquals(MapPalette.DAY.casing, spec.colour(MapPalette.DAY))
        assertEquals(MapPalette.NIGHT.casing, spec.colour(MapPalette.NIGHT))
        assertEquals(1.5f, spec.widthDp)
        val dashDp = spec.dashPattern!!.map { it * spec.widthDp }
        assertEquals(2, dashDp.size)
        assertEquals(6f, dashDp[0], 1e-4f)
        assertEquals(4f, dashDp[1], 1e-4f)
        assertTrue("butt ends, not round", !spec.roundCaps)
    }

    /**
     * Dispatch `2026-09-28-79` (owner: "The outline should have a white border"; "Yes the night outline
     * only"): at night the offline region's dashed outline has a border line beneath it, in the track
     * casing's pattern: the outline's own line, [CASING_WIDTH_DP] wider on each side, solid, on the same
     * source, with the outline's caps and its constant width, in opaque white. Its opacity is the
     * registry's (`MapLayerRegistryTest`), and its figures against the night ground are in
     * `MapPaletteTest`.
     */
    @Test
    fun `at night the offline outline has a solid white border beneath it, the casing width wider on each side`() {
        val border = lineSpecForLayer(MapLayerIds.OFFLINE_REGION_BORDER)
        assertNotNull("no border line is built under the offline outline", border)
        val outline = offlineRegionOutlineSpec()
        assertEquals("on the outline's own source", outline.sourceId, border!!.sourceId)
        assertEquals("opaque white at night", "#FFFFFFFF", "#%08X".format(border.colour(MapPalette.NIGHT)))
        assertEquals("the casing width wider on each side", outline.widthDp + 2 * CASING_WIDTH_DP, border.widthDp, 0f)
        assertEquals("4.5 dp in all", 4.5f, border.widthDp, 0f)
        assertNull("solid, so the edge carries through the dashes' gaps", border.dashPattern)
        assertNull("a constant width, like the outline", border.widthByZoom)
        assertEquals("the outline's caps", outline.roundCaps, border.roundCaps)
    }

    /**
     * Dispatch `2026-09-28-79`: the border is night only, and the day outline does not change. By day
     * the border's colour is fully transparent, so it draws nothing, and the outline is still the white
     * dashed casing line, 1.5 dp, dash 6 dp and gap 4 dp, with butt ends.
     */
    @Test
    fun `by day the offline outline is unchanged and its border draws nothing`() {
        val border = lineSpecForLayer(MapLayerIds.OFFLINE_REGION_BORDER)
        assertNotNull("no border line is built under the offline outline", border)
        assertEquals("fully transparent by day", 0, border!!.colour(MapPalette.DAY) ushr 24)
        val outline = offlineRegionOutlineSpec()
        assertEquals("the day outline is white", "#FFFFFFFF", "#%08X".format(outline.colour(MapPalette.DAY)))
        assertEquals(1.5f, outline.widthDp, 0f)
        val dashDp = outline.dashPattern!!.map { it * outline.widthDp }
        assertEquals(2, dashDp.size)
        assertEquals(6f, dashDp[0], 1e-4f)
        assertEquals(4f, dashDp[1], 1e-4f)
        assertTrue("butt ends, not round", !outline.roundCaps)
        assertNull("a constant width", outline.widthByZoom)
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
     * [BREADCRUMB_DASH_PATTERN]'s own doc comment for exactly what this does and does not prove.
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

    // Journal Stage 2d: the Cartography entry map's own feature builders — pure GeoJSON, same
    // reasoning as the rest of this file for why these are testable off a device at all.

    private val trackOne = RecordPolyline("track-1", listOf(LatLng(45.20, -122.50), LatLng(45.21, -122.51), LatLng(45.22, -122.52)))
    private val trackTwo = RecordPolyline("track-2", listOf(LatLng(46.00, -123.00), LatLng(46.01, -123.01)))

    @Test
    fun `two kept tracks become two separate LineStrings, not one joined trail`() {
        val features = keptTracksFeatureCollection(listOf(trackOne, trackTwo)).features()!!
        assertEquals(2, features.size)

        val firstLine = features[0].geometry() as LineString
        assertEquals(trackOne.points.map { Point.fromLngLat(it.lng, it.lat) }, firstLine.coordinates())

        val secondLine = features[1].geometry() as LineString
        assertEquals(trackTwo.points.map { Point.fromLngLat(it.lng, it.lat) }, secondLine.coordinates())
    }

    @Test
    fun `a track with fewer than two points produces no LineString`() {
        val features = keptTracksFeatureCollection(listOf(trackOne, RecordPolyline("one-point", listOf(LatLng(45.0, -122.0))), RecordPolyline("empty", emptyList()))).features()!!
        assertEquals(
            "Only trackOne has two or more points -- the single-point and empty tracks must be silently dropped, not error.",
            1,
            features.size,
        )
    }

    @Test
    fun `no kept tracks produces no LineString features`() {
        assertTrue(keptTracksFeatureCollection(emptyList()).features()!!.isEmpty())
    }

    @Test
    fun `pointsFeatureCollection places one point feature per marker at its own coordinates`() {
        val markers = listOf(LatLng(45.5, -122.5), LatLng(45.6, -122.6))
        val features = pointsFeatureCollection(markers.mapIndexed { i, at -> RecordPoint("find-$i", at) }).features()!!
        assertEquals(2, features.size)

        val points = features.map { it.geometry() as Point }
        assertEquals(markers[0].lng, points[0].longitude(), 0.0)
        assertEquals(markers[0].lat, points[0].latitude(), 0.0)
        assertEquals(markers[1].lng, points[1].longitude(), 0.0)
        assertEquals(markers[1].lat, points[1].latitude(), 0.0)
    }

    @Test
    fun `no markers produces no point features`() {
        assertTrue(pointsFeatureCollection(emptyList()).features()!!.isEmpty())
    }

    @Test
    fun `each offline region becomes a closed polygon ring centred on its own coordinates`() {
        val region = Region(lat = 45.5, lng = -122.5, radiusKm = 5)
        val feature = offlineRegionCirclesFeatureCollection(listOf(RecordRegion("1", region))).features()!!.single()
        val polygon = feature.geometry() as org.maplibre.geojson.Polygon
        val ring = polygon.coordinates().single()

        assertEquals(
            "GeoDistance.circlePolygonPoints already closes its own ring (first == last); the polygon built from it must not duplicate or drop that closure.",
            GeoDistance.circlePolygonPoints(LatLng(region.lat, region.lng), region.radiusKm).map { Point.fromLngLat(it.lng, it.lat) },
            ring,
        )
        assertEquals("A closed linear ring's first and last points must be identical.", ring.first(), ring.last())
    }

    @Test
    fun `no offline regions produces no circle features`() {
        assertTrue(offlineRegionCirclesFeatureCollection(emptyList()).features()!!.isEmpty())
    }
}
