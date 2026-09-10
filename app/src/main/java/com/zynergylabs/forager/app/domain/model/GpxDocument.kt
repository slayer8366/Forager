package com.zynergylabs.forager.app.domain.model

/**
 * What a GPX file actually carries in this app: at most one track, plus any number of waypoints —
 * the standard GPX shape (`<gpx><trk>…</trk><wpt>…</wpt>…</gpx>`), not this app's own invention.
 * [com.zynergylabs.forager.app.domain.GpxCodec] is the only place this ever meets XML text.
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
    /**
     * The exclusion rule set in force when [fullRecord]'s verdicts were produced — GPX
     * rule-provenance dispatch (owner ruling, 2026-09-09), written on the record block as its
     * `rule` attribute. Empty means **this document declares no rule set**, which is a real and
     * different state from declaring one: it is what [com.zynergylabs.forager.app.domain.GpxCodec.decode]
     * produces for a file that carries no `rule` attribute, and defaulting it to this build's live
     * set instead would quietly stamp today's provenance onto a file written before provenance
     * existed. So it defaults to empty and the one production producer,
     * [com.zynergylabs.forager.app.export.TrackGpxExporter.write], sets it explicitly from
     * [com.zynergylabs.forager.app.domain.NETWORK_FIX_EXCLUSION_RULES].
     */
    val exclusionRules: List<String> = emptyList(),
)
