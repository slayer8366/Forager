package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapSourceIds

// Source/layer ids. Fixed strings rather than generated, since every one of them is referenced by
// name from at least two places (the registry and refreshOverlayData, or a layer referencing its
// source) and a typo needs to be a compile error, not a silently-missing layer. Since map layers
// L0a the strings live in the layers package (MapLayerIds, MapSourceIds), beside the registry that
// orders them; these names are kept as aliases so the rest of this file reads as it did.
internal const val SEARCH_CENTER_SOURCE_ID = MapSourceIds.SEARCH_CENTRE
internal const val SEARCH_CENTER_LAYER_ID = MapLayerIds.SEARCH_CENTRE
internal const val SIGHTING_SOURCE_ID = MapSourceIds.SIGHTINGS
internal const val SIGHTING_LAYER_ID = MapLayerIds.SIGHTINGS
internal const val PLANNED_TRIP_SOURCE_ID = MapSourceIds.PLANNED_TRIPS
internal const val PLANNED_TRIP_LAYER_ID = MapLayerIds.PLANNED_TRIPS
internal const val BREADCRUMB_SOURCE_ID = MapSourceIds.BREADCRUMB
internal const val BREADCRUMB_LAYER_ID = MapLayerIds.BREADCRUMB
internal const val BREADCRUMB_CASING_LAYER_ID = MapLayerIds.BREADCRUMB_CASING
internal const val WAYPOINT_SOURCE_ID = MapSourceIds.WAYPOINTS
internal const val WAYPOINT_LAYER_ID = MapLayerIds.WAYPOINTS

// Journal Stage 2d.
internal const val KEPT_TRACKS_SOURCE_ID = MapSourceIds.KEPT_TRACKS
internal const val KEPT_TRACKS_LAYER_ID = MapLayerIds.KEPT_TRACKS
internal const val KEPT_TRACKS_CASING_LAYER_ID = MapLayerIds.KEPT_TRACKS_CASING
internal const val FIND_SOURCE_ID = MapSourceIds.FINDS
internal const val FIND_LAYER_ID = MapLayerIds.FINDS
internal const val PHOTO_SOURCE_ID = MapSourceIds.PHOTOS
internal const val PHOTO_LAYER_ID = MapLayerIds.PHOTOS
internal const val OFFLINE_REGION_CIRCLE_SOURCE_ID = MapSourceIds.OFFLINE_REGIONS
internal const val OFFLINE_REGION_CIRCLE_LAYER_ID = MapLayerIds.OFFLINE_REGION_FILL
internal const val OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID = MapLayerIds.OFFLINE_REGION_OUTLINE

/**
 * The property every overlay feature but a sighting carries its own id in (map layers L0a, A4): a
 * record id for a find, photo, kept track, waypoint, planned trip or offline region, and a fixed id
 * for the two singletons ([SEARCH_CENTRE_FEATURE_ID], [BREADCRUMB_FEATURE_ID]). A string property,
 * not the GeoJSON feature id, for the reason the sighting's `observationId` is a property: a
 * property is what this file has seen round-trip through `queryRenderedFeatures` on hardware. A
 * sighting keeps its `observationId` and gets no second id.
 */
internal const val FEATURE_ID_PROPERTY = "featureId"

/** The search-centre marker's fixed feature id: there is only ever one. */
internal const val SEARCH_CENTRE_FEATURE_ID = "search-centre"

/** The breadcrumb trail's fixed feature id: there is only ever one active track. */
internal const val BREADCRUMB_FEATURE_ID = "breadcrumb"
