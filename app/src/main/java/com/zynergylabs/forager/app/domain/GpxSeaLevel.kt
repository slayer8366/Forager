package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * [track] and [waypoints] with each height as a GPX file's standard `<ele>` should carry it, above sea level (dispatch
 * 2026-10-28-13, item 5 and Amendment 1, RECORD -1793). Other apps read `<ele>` as height above sea level; what is
 * stored is the phone's raw height, about 21 m lower in western Oregon. Only the copies handed to [GpxCodec.encode]'s
 * `<trkpt>` and `<wpt>` change. The stored data, and the file's own full record (`forager:point ele=`, "Keep it raw
 * (Recommended)"), keep the raw height.
 *
 * - **A recorded track:** each point's and each waypoint's height converted where it stands.
 * - **A phone that cannot convert:** the height is left out (`null`, so no `<ele>`; "Leave heights out
 *   (Recommended)"), never the raw height written as if it were sea level.
 * - **An imported track** ([Track.importedAtEpochMillis] set) and its waypoints: as the file gave them, already sea
 *   level ("Show as the file gave them (Recommended)"). A file this app exported before this change holds raw heights
 *   and re-imported reads about 21 m low; that gap was accepted, and nothing detects it.
 */
suspend fun gpxHeightsAboveSeaLevel(track: Track, waypoints: List<Waypoint>, converter: SeaLevelConverter): Pair<Track, List<Waypoint>> {
    if (track.importedAtEpochMillis != null) return track to waypoints
    val points = track.points.map { point -> point.copy(altitude = converter.aboveSeaLevel(point.lat, point.lng, point.altitude)) }
    val marks = waypoints.map { waypoint -> waypoint.copy(altitude = converter.aboveSeaLevel(waypoint.lat, waypoint.lng, waypoint.altitude)) }
    return track.copy(points = points) to marks
}

private suspend fun SeaLevelConverter.aboveSeaLevel(lat: Double, lng: Double, raw: Double?): Double? =
    (seaLevel(lat, lng, raw) as? SeaLevelHeight.Known)?.meters
