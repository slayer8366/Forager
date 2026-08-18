package com.forager.app.domain.model

/**
 * What a GPX file actually carries in this app: at most one track, plus any number of waypoints —
 * the standard GPX shape (`<gpx><trk>…</trk><wpt>…</wpt>…</gpx>`), not this app's own invention.
 * [com.forager.app.domain.GpxCodec] is the only place this ever meets XML text.
 */
data class GpxDocument(
    val track: Track?,
    val waypoints: List<Waypoint>,
)
