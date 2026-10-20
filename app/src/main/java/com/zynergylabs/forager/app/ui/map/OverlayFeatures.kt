package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * The [TapHit] for [feature], queried on [layerId]: a sighting's id is its `observationId` number
 * property, written in decimal; every other layer's is its [FEATURE_ID_PROPERTY] string property.
 * `null` when the feature has neither. A plain function over the pure GeoJSON [Feature], so a
 * headless test can round-trip a builder's feature through it.
 */
internal fun tapHitOf(layerId: String, feature: Feature): TapHit = TapHit(
    layerId = layerId,
    featureId = if (layerId == SIGHTING_LAYER_ID) {
        feature.getNumberProperty("observationId")?.toLong()?.toString()
    } else {
        feature.getStringProperty(FEATURE_ID_PROPERTY)
    },
)

/**
 * Pushes the real content into every source [initializeOverlayLayers] created, replacing whatever
 * was there before. Cheap and safe to call on every relevant prop change — `GeoJsonSource.setGeoJson`
 * updates in place; it does not touch the layers referencing the source, unlike `setStyle`.
 */
internal fun refreshOverlayData(
    style: Style,
    region: Region,
    sightings: List<Sighting>,
    plannedTrips: List<PlannedTrip>,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    focusedObservationId: Long?,
    keptTrackPolylines: List<RecordPolyline>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
    offlineRegionCircles: List<RecordRegion>,
    showSearchCentre: Boolean,
    journalHighlights: JournalEntryHighlights,
) {
    style.getSourceAs<GeoJsonSource>(SEARCH_CENTER_SOURCE_ID)?.setGeoJson(searchCentreOverlay(region, showSearchCentre))
    style.getSourceAs<GeoJsonSource>(SIGHTING_SOURCE_ID)?.setGeoJson(sightingsFeatureCollection(sightings, focusedObservationId))
    style.getSourceAs<GeoJsonSource>(PLANNED_TRIP_SOURCE_ID)?.setGeoJson(plannedTripsFeatureCollection(plannedTrips))
    style.getSourceAs<GeoJsonSource>(BREADCRUMB_SOURCE_ID)?.setGeoJson(breadcrumbFeatureCollection(breadcrumbPoints))
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE_ID)?.setGeoJson(waypointsFeatureCollection(waypoints))
    style.getSourceAs<GeoJsonSource>(KEPT_TRACKS_SOURCE_ID)?.setGeoJson(keptTracksFeatureCollection(keptTrackPolylines))
    style.getSourceAs<GeoJsonSource>(FIND_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(findMarkers))
    style.getSourceAs<GeoJsonSource>(PHOTO_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(photoMarkers))
    style.getSourceAs<GeoJsonSource>(OFFLINE_REGION_CIRCLE_SOURCE_ID)?.setGeoJson(offlineRegionCirclesFeatureCollection(offlineRegionCircles))
    // J8: the halos under the shown entries' kept records.
    journalHighlightFeatureCollections(journalHighlights).forEach { (sourceId, collection) ->
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
    }
}

internal fun emptyFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList())

/**
 * The search-centre marker. title/snippet kept as properties — see this file's class doc comment on
 * the deferred tap-to-see popup.
 *
 * `internal`, not `private`: [GeoJsonSource]/[Style] are native-backed (verified with `javap` against
 * the pinned artifact — every constructor calls a `native` `initialize`), so they cannot be built in
 * a JVM unit test at all, on-device or Robolectric, without `UnsatisfiedLinkError`. This function and
 * its siblings below are the actual production code that decides *what* goes into those sources —
 * plain [org.maplibre.geojson] data classes, which carry no native methods (also checked with
 * `javap`) — so pulling the boundary here and testing up to it (`SightingsMapOverlayDataTest`) is the
 * same split [MapLibreOfflineMapRepository]'s own doc comment already draws for `OfflineRegion`.
 */
internal fun searchCenterFeatureCollection(region: Region): FeatureCollection {
    val feature = Feature.fromGeometry(Point.fromLngLat(region.lng, region.lat))
    feature.addStringProperty(FEATURE_ID_PROPERTY, SEARCH_CENTRE_FEATURE_ID)
    feature.addStringProperty("title", "Search location")
    feature.addStringProperty("snippet", "Radius: ${region.radiusKm} km")
    return FeatureCollection.fromFeature(feature)
}

/**
 * What the search-centre source actually receives: [searchCenterFeatureCollection] when
 * [showSearchCentre] is on, an empty collection when it is off — the source and layer stay in the
 * style either way, so a caller flipping the flag never triggers a style rebuild. See
 * [com.zynergylabs.forager.app.ui.map.MapRenderMode.showSearchCentre] for which callers turn it off and why.
 * Split out from [refreshOverlayData] so the decision is testable without a [Style]
 * (`SightingsMapOverlayDataTest`), the same boundary [searchCenterFeatureCollection]'s own doc
 * comment describes.
 */
internal fun searchCentreOverlay(region: Region, showSearchCentre: Boolean): FeatureCollection =
    if (showSearchCentre) searchCenterFeatureCollection(region) else emptyFeatureCollection()

/**
 * [focusedObservationId] bakes a `"selected"` boolean into whichever feature it names, `false` on
 * every other — see [sightingStrokeColorExpression]'s own doc comment for why the paint layer
 * reads this property instead of comparing `observationId` itself inside a GL expression.
 */
internal fun sightingsFeatureCollection(sightings: List<Sighting>, focusedObservationId: Long? = null): FeatureCollection {
    val features = sightings.map { sighting ->
        Feature.fromGeometry(Point.fromLngLat(sighting.lng, sighting.lat)).apply {
            addStringProperty("title", sighting.commonName ?: sighting.scientificName)
            addStringProperty("snippet", sighting.observedOn?.toString() ?: sighting.scientificName)
            // Round-trips through queryRenderedFeatures in the map click listener below, to look the
            // tapped feature back up in the current `sightings` list — the only property here that's
            // actually read back, rather than kept "for a future click handler" the way title/snippet
            // were before this.
            addNumberProperty("observationId", sighting.observationId)
            // Computed in Kotlin, once, per push — not read back by anything on this side, only by
            // sightingStrokeColorExpression's own GL expression.
            addBooleanProperty("selected", sighting.observationId == focusedObservationId)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * The active track's recorded points as a single [LineString] feature, oldest first — an empty
 * [FeatureCollection] when [points] has fewer than two points (nothing recorded yet, or only the
 * first fix so far): a `LineString` needs at least two points.
 */
internal fun breadcrumbFeatureCollection(points: List<LatLng>): FeatureCollection {
    if (points.size < 2) return emptyFeatureCollection()
    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
    val feature = Feature.fromGeometry(line).apply { addStringProperty(FEATURE_ID_PROPERTY, BREADCRUMB_FEATURE_ID) }
    return FeatureCollection.fromFeature(feature)
}

internal fun plannedTripsFeatureCollection(plannedTrips: List<PlannedTrip>): FeatureCollection {
    val features = plannedTrips.map { trip ->
        Feature.fromGeometry(Point.fromLngLat(trip.location.lng, trip.location.lat)).apply {
            addStringProperty(FEATURE_ID_PROPERTY, trip.id)
            addStringProperty("title", "Planned trip")
            addStringProperty("snippet", trip.date.toString())
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/** Every saved [Waypoint] as a point feature carrying its own id and name, for the pin's [SymbolLayer]. */
internal fun waypointsFeatureCollection(waypoints: List<Waypoint>): FeatureCollection {
    val features = waypoints.map { waypoint ->
        Feature.fromGeometry(Point.fromLngLat(waypoint.lng, waypoint.lat)).apply {
            addStringProperty(FEATURE_ID_PROPERTY, waypoint.id)
            addStringProperty("title", waypoint.name)
            addStringProperty("snippet", waypoint.note)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * A Cartography entry's kept tracks as one [LineString] feature per inner list — a genuine
 * `MultiLineString`, not [breadcrumbFeatureCollection]'s single connected line. Journal Stage 2d. An
 * inner list with fewer than two points contributes no feature at all (same "a LineString needs at
 * least two points" reasoning [breadcrumbFeatureCollection] already has) rather than a degenerate
 * one-point line; this is also what makes a kept track that resolved to zero/one usable point (data
 * already thin before this reaches here) draw nothing instead of erroring.
 */
internal fun keptTracksFeatureCollection(polylines: List<RecordPolyline>): FeatureCollection {
    val features = polylines
        .filter { it.points.size >= 2 }
        .map { track ->
            Feature.fromGeometry(LineString.fromLngLats(track.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
                addStringProperty(FEATURE_ID_PROPERTY, track.recordId)
            }
        }
    return FeatureCollection.fromFeatures(features)
}

/** Journal Stage 2d's find and photo pins, one point feature per record, carrying the record's id. */
internal fun pointsFeatureCollection(points: List<RecordPoint>): FeatureCollection =
    FeatureCollection.fromFeatures(
        points.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.at.lng, point.at.lat)).apply {
                addStringProperty(FEATURE_ID_PROPERTY, point.recordId)
            }
        },
    )

/**
 * A Cartography entry's kept offline regions as filled polygon features — Journal Stage 2d. Each
 * [Region] needs no fetch to resolve (its own snapshot already carries lat/lng/radius); the polygon
 * ring itself comes from [GeoDistance.circlePolygonPoints], the true-circle approximation built for
 * exactly this drawn-shape use (as opposed to [GeoDistance.boundingBox]'s tile-download rectangle).
 */
internal fun offlineRegionCirclesFeatureCollection(regions: List<RecordRegion>): FeatureCollection {
    val features = regions.map { kept ->
        val region = kept.region
        val ring = GeoDistance.circlePolygonPoints(LatLng(region.lat, region.lng), region.radiusKm)
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.lng, it.lat) }))).apply {
            // Read back through the outline layer, which shares this source: the fill is not tappable.
            addStringProperty(FEATURE_ID_PROPERTY, kept.recordId)
        }
    }
    return FeatureCollection.fromFeatures(features)
}
