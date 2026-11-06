package com.zynergylabs.forager.app.domain

/**
 * Where the Maps tab's icon cluster sits, as stored across restarts (dispatch RECORD -711). The owner:
 * "have the map icon bar persist between restarts so left handed users don't need to change it every
 * time they open the app", then "Side and height (Recommended)". So the side it is snapped to and the
 * height it was dragged to persist; whether it is minimised does not (session only, as before).
 *
 * Two pairs, because the cluster already keeps two positions in a session
 * (`MapIconClusterPositionState`): one for a portrait window and one for a short landscape window, where
 * the side is held as the rail's port side or the punch-hole side so turning the phone keeps it on the
 * same device edge. Both are stored, so a left-handed user who turns the phone does not have to move it
 * again there either.
 *
 * Heights are offsets from the cluster's centred position, in dp rather than pixels, so a change of
 * display size does not move it by a different physical distance. Plain values: the domain names no
 * Compose type.
 */
data class MapIconClusterPlacement(
    val portraitOnLeft: Boolean,
    val portraitOffsetDp: Float,
    val landscapeOnPortSide: Boolean,
    val landscapeOffsetDp: Float,
) {
    companion object {
        /** Where the cluster has always opened: on the right, centred; in landscape on the punch-hole side, centred. */
        val DEFAULT = MapIconClusterPlacement(
            portraitOnLeft = false,
            portraitOffsetDp = 0f,
            landscapeOnPortSide = false,
            landscapeOffsetDp = 0f,
        )
    }
}

/**
 * Flat settings, so DataStore (CLAUDE.md); the real implementation is `DataStoreMapPreferencesRepository`,
 * in `map_preferences` beside `map.basemap`, under `map.icon_cluster` keys.
 */
interface MapIconClusterPlacementRepository {
    /** The stored placement, or `null` when nothing has been stored (a phone that has never moved it). */
    suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?>

    suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit>
}
