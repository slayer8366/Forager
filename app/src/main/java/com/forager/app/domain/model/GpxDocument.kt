package com.forager.app.domain.model

/**
 * What a GPX file actually carries in this app: at most one track, plus any number of waypoints —
 * the standard GPX shape (`<gpx><trk>…</trk><wpt>…</wpt>…</gpx>`), not this app's own invention.
 * [com.forager.app.domain.GpxCodec] is the only place this ever meets XML text.
 *
 * [fullRecord] — GPX full-record export dispatch, format B1 (owner ruling): the full raw stored
 * sequence for [track], each point paired with its network-provider-fix verdict. [GpxCodec.encode]
 * writes it into `<trk><extensions>`, declared authoritative in-band, with [track]'s `<trkseg>`
 * (the filtered points, exactly as displayed in-app) derived from it. Empty for a document with no
 * full record to carry — the ordinary case for [track] being `null`, and for any document
 * [GpxCodec.decode] builds from a file with no such extension (every GPX file from outside this
 * app, or one this app wrote before this dispatch).
 */
data class GpxDocument(
    val track: Track?,
    val waypoints: List<Waypoint>,
    val fullRecord: List<TrackPointRecord> = emptyList(),
)
