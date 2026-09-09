package com.forager.app.domain

import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackPointRecord
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import java.time.Instant
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Encodes/decodes a [GpxDocument] as GPX 1.1 XML — the standard track-interchange format, so a
 * recorded track can leave this app (into Gaia, CalTopo, a GPS unit) and a GPX file from elsewhere
 * can come in. Pure Kotlin, no Android imports: `javax.xml.parsers`/`org.w3c.dom` are JDK APIs, not
 * `android.*`, and Android's runtime bundles a working JAXP implementation of them the same way a
 * plain JVM does — this is genuinely headless-testable, not Android code masquerading as domain.
 *
 * Actually writing the encoded string to a file the user picks, or reading one back, is Storage
 * Access Framework work needing a document-picker UI — Phase 1c, same as track breadcrumbs and
 * waypoint markers, once there's a screen to launch it from. This class is the codec only.
 *
 * [decode] skips a `<trkpt>`/`<wpt>` missing a parseable `lat`/`lon`/`<time>` rather than failing
 * the whole document or fabricating a timestamp — [TrackPoint.timestampEpochMillis] is not
 * nullable, and a GPX file with an incomplete point is a real, if unfortunate, case an import has
 * to tolerate rather than reject outright. This is a deliberate scope line, not a silently swallowed
 * error: malformed XML itself (not a well-formed document with some incomplete points) still throws.
 * The same tolerance applies to the `forager:` extension elements below: a malformed or missing one
 * degrades to an empty [GpxDocument.fullRecord] / an ordinary waypoint with no [Waypoint.trackId] or
 * [Waypoint.designation], never a thrown exception over extension content this app itself wrote.
 *
 * ## The `forager:` extension — GPX full-record export dispatch, format B1 (owner ruling)
 *
 * In-app display always reads the filtered track ([excludeNetworkProviderFixes] at the read seam);
 * a tester's export is a different rule (owner ruling 2: the export carries the full data set) —
 * the point being that a device whose GPS clock isn't second-aligned, where the read-seam rule
 * would misfire, needs testers' raw exports to find that out. [GpxDocument.fullRecord] carries the
 * complete stored sequence with each point's kept/excluded verdict inside `<trk><extensions>`,
 * declared authoritative in-band via `authoritative="true"` on `<forager:fullRecord>` — machine
 * readable, not a comment, because a round-tripping tool could otherwise edit the rendered
 * `<trkseg>` and leave the two disagreeing with nothing to say which side is ground truth.
 * `<trkseg>` remains exactly the filtered points, precisely what the app displayed. One `forager`
 * namespace serves both `<trk>`'s full-record extension and `<wpt>`'s (coder's call, since neither
 * shares fields with the other — one XML vocabulary for one app's data is simpler than two).
 */
object GpxCodec {

    /**
     * The `forager:` vocabulary's identifier. A namespace URI is an **identifier, not a fetchable
     * document** — nothing resolves it and nothing needs to — but it is a permanent string in every
     * exported file, and **a file already on a tester's phone can never be re-stamped**. That is the
     * same argument that made `rule` provenance a beta gate, applied to the domain: it must name a
     * domain this project controls, and the window to choose closes the moment a tester exports.
     *
     * Moved off `https://forager.app/gpx/1` (a domain registered to someone else) before the first
     * export, 2026-09-09. The `/1` is the schema version and **stays `/1`** — the format did not
     * change, only the identifier's domain, so this is not a version bump and `schema="1"` remains
     * absent per the format's ratification.
     *
     * Read at exactly one site, the `xmlns:forager` declaration below. [decode] never matches on it:
     * the parser is not namespace-aware and every lookup is `getElementsByTagName`, which matches the
     * qualified name — so the decoder keys on the `forager:` **prefix**, not on this URI.
     */
    private const val FORAGER_NAMESPACE = "https://zynergy-labs.com/forager/gpx/1"

    fun encode(document: GpxDocument): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append(
            "<gpx version=\"1.1\" creator=\"Forager\" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:forager=\"$FORAGER_NAMESPACE\">\n",
        )
        document.track?.let { track ->
            append("  <trk>\n")
            track.name?.let { append("    <name>${escapeXml(it)}</name>\n") }
            if (document.fullRecord.isNotEmpty()) append(encodeFullRecord(document.fullRecord, document.exclusionRules))
            append("    <trkseg>\n")
            track.points.forEach { point -> append(encodeTrackPoint(point)) }
            append("    </trkseg>\n")
            append("  </trk>\n")
        }
        document.waypoints.forEach { waypoint -> append(encodeWaypoint(waypoint)) }
        append("</gpx>\n")
    }

    /**
     * `<extensions>` is legal under `<trk>` immediately before its `<trkseg>` elements in the GPX
     * 1.1 schema (`trkType`: `name?, cmt?, desc?, src?, link*, number?, type?, extensions?,
     * trkseg*`) — checked against the schema, not assumed — so this scopes the raw record to its
     * own track, which a future multi-track file will need. `pointCount` on `forager:fullRecord`
     * lets a parser sanity-check it read every point without counting child nodes itself.
     *
     * `rule` names the exclusion rule set in force when the file was written — GPX rule-provenance
     * dispatch (owner ruling, 2026-09-09). It belongs here, on the block, and not only on the
     * points a rule caught: a **kept** point in a file that declares one rule set is a different
     * claim from a kept point in a file that declares two, and nothing on the point itself can
     * distinguish them. Space-separated, the XML `NMTOKENS` idiom, so today's single rule is
     * written as the bare literal a reader would expect and a second one appends without changing
     * the shape. Omitted entirely when [GpxDocument.exclusionRules] is empty: a document that
     * declares no rule set writes no attribute, rather than an empty one reading as "no rules ran".
     */
    private fun encodeFullRecord(records: List<TrackPointRecord>, exclusionRules: List<String>): String = buildString {
        append("    <extensions>\n")
        append(
            "      <forager:fullRecord authoritative=\"true\" trksegDerivedFromFullRecord=\"true\" " +
                "pointCount=\"${records.size}\"",
        )
        if (exclusionRules.isNotEmpty()) append(" rule=\"${escapeXml(exclusionRules.joinToString(" "))}\"")
        append(">\n")
        records.forEach { record -> append(encodeFullRecordPoint(record)) }
        append("      </forager:fullRecord>\n")
        append("    </extensions>\n")
    }

    /**
     * `timeEpochMillis` rather than an ISO string: the literal stored value, with no formatting or
     * timezone round-trip to lose precision through — full millisecond precision is the entire
     * reason this element exists (unlike the display `<trkpt><time>`, which [encodeTrackPoint]
     * leaves exactly as before). Stable numeric attribute names throughout, no free prose.
     *
     * `excludedByRule` names the rule that caught this point — GPX rule-provenance dispatch
     * (owner ruling, 2026-09-09). Written only where there is a rule to name, so a kept point
     * carries no such attribute and `kept="false"` with none means a producer that recorded no
     * provenance (see [TrackPointRecord.excludedByRule]) rather than a point nothing excluded. It
     * is deliberately not derived from `kept` and the block's `rule` set: with a second rule in
     * force that derivation would name the wrong one, and naming the wrong rule is worse than the
     * ambiguity this attribute exists to end.
     */
    private fun encodeFullRecordPoint(record: TrackPointRecord): String = buildString {
        val point = record.point
        append("        <forager:point")
        append(" lat=\"${point.lat}\"")
        append(" lon=\"${point.lng}\"")
        append(" timeEpochMillis=\"${point.timestampEpochMillis}\"")
        point.altitude?.let { append(" ele=\"$it\"") }
        point.accuracyMeters?.let { append(" accuracyMeters=\"$it\"") }
        point.speedMetersPerSecond?.let { append(" speedMetersPerSecond=\"$it\"") }
        point.speedAccuracyMetersPerSecond?.let { append(" speedAccuracyMetersPerSecond=\"$it\"") }
        append(" kept=\"${record.kept}\"")
        record.excludedByRule?.let { append(" excludedByRule=\"${escapeXml(it)}\"") }
        append("/>\n")
    }

    private fun encodeTrackPoint(point: TrackPoint): String = buildString {
        append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lng}\">\n")
        point.altitude?.let { append("        <ele>$it</ele>\n") }
        append("        <time>${Instant.ofEpochMilli(point.timestampEpochMillis)}</time>\n")
        append("      </trkpt>\n")
    }

    /**
     * [Waypoint.id], [Waypoint.trackId] and [Waypoint.designation] have no standard GPX element —
     * `wptType` fixes its child list and allows no identifier attribute — so all three ride in a
     * `<wpt><extensions>` `forager:waypoint`, the same namespace [encodeFullRecord] uses.
     *
     * [Waypoint.id] is written on every waypoint, which is why this block is no longer omitted for
     * an ordinary, not-track-related one the way it was when `trackId`/`designation` were its only
     * contents (GPX rule-provenance dispatch, 2026-09-09): an id every waypoint has cannot follow
     * the omit-when-absent pattern, and a waypoint exported without it is one no later export,
     * report or bug can be matched back to the row it came from. `trackId` and `designation` keep
     * that pattern individually — each still absent rather than empty when it is `null`.
     */
    private fun encodeWaypoint(waypoint: Waypoint): String = buildString {
        append("  <wpt lat=\"${waypoint.lat}\" lon=\"${waypoint.lng}\">\n")
        waypoint.altitude?.let { append("    <ele>$it</ele>\n") }
        append("    <time>${Instant.ofEpochMilli(waypoint.createdAtEpochMillis)}</time>\n")
        append("    <name>${escapeXml(waypoint.name)}</name>\n")
        if (waypoint.note.isNotBlank()) append("    <desc>${escapeXml(waypoint.note)}</desc>\n")
        append("    <extensions>\n")
        append("      <forager:waypoint id=\"${escapeXml(waypoint.id)}\"")
        waypoint.trackId?.let { append(" trackId=\"${escapeXml(it)}\"") }
        waypoint.designation?.let { append(" designation=\"${it.name}\"") }
        append("/>\n")
        append("    </extensions>\n")
        append("  </wpt>\n")
    }

    fun decode(xml: String): GpxDocument {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val root = builder.parse(xml.byteInputStream()).documentElement

        val trkElement = root.getElementsByTagName("trk").item(0) as? Element
        val track = trkElement?.let { trk ->
            val name = trk.getElementsByTagName("name").item(0)?.textContent
            val points = trk.getElementsByTagName("trkpt").let { nodeList ->
                (0 until nodeList.length).mapNotNull { i -> decodeTrackPoint(nodeList.item(i) as Element) }
            }
            Track(
                id = UUID.randomUUID().toString(),
                name = name,
                startedAtEpochMillis = points.minOfOrNull { it.timestampEpochMillis } ?: 0L,
                endedAtEpochMillis = points.maxOfOrNull { it.timestampEpochMillis },
                points = points,
            )
        }
        val fullRecordElement = trkElement?.getElementsByTagName("forager:fullRecord")?.item(0) as? Element

        val waypoints = root.getElementsByTagName("wpt").let { nodeList ->
            (0 until nodeList.length).mapNotNull { i -> decodeWaypoint(nodeList.item(i) as Element) }
        }

        return GpxDocument(
            track = track,
            waypoints = waypoints,
            fullRecord = fullRecordElement?.let { decodeFullRecord(it) }.orEmpty(),
            exclusionRules = fullRecordElement?.let { decodeExclusionRules(it) }.orEmpty(),
        )
    }

    private fun decodeFullRecord(fullRecordElement: Element): List<TrackPointRecord> {
        val pointNodes = fullRecordElement.getElementsByTagName("forager:point")
        return (0 until pointNodes.length).mapNotNull { i -> decodeFullRecordPoint(pointNodes.item(i) as Element) }
    }

    /**
     * The rule set **the file itself declares**, never the one this build applies: a file written
     * before `rule` existed decodes to an empty list, which is the honest answer to "which rules
     * produced these verdicts?" and the reason [GpxDocument.exclusionRules] does not default to
     * [NETWORK_FIX_EXCLUSION_RULES]. Space-separated, matching what [encodeFullRecord] writes.
     */
    private fun decodeExclusionRules(fullRecordElement: Element): List<String> =
        fullRecordElement.getAttribute("rule").split(' ').filter { it.isNotBlank() }

    private fun decodeFullRecordPoint(element: Element): TrackPointRecord? {
        val lat = element.getAttribute("lat").toDoubleOrNull() ?: return null
        val lng = element.getAttribute("lon").toDoubleOrNull() ?: return null
        val timestamp = element.getAttribute("timeEpochMillis").toLongOrNull() ?: return null
        val kept = element.getAttribute("kept").toBooleanVerdictOrNull() ?: return null
        return TrackPointRecord(
            point = TrackPoint(
                lat = lat,
                lng = lng,
                altitude = element.getAttribute("ele").toDoubleOrNull(),
                accuracyMeters = element.getAttribute("accuracyMeters").toFloatOrNull(),
                timestampEpochMillis = timestamp,
                speedMetersPerSecond = element.getAttribute("speedMetersPerSecond").toFloatOrNull(),
                speedAccuracyMetersPerSecond = element.getAttribute("speedAccuracyMetersPerSecond").toFloatOrNull(),
            ),
            kept = kept,
            excludedByRule = element.getAttribute("excludedByRule").ifBlank { null },
        )
    }

    private fun decodeTrackPoint(element: Element): TrackPoint? {
        val lat = element.getAttribute("lat").toDoubleOrNull() ?: return null
        val lng = element.getAttribute("lon").toDoubleOrNull() ?: return null
        val timestamp = element.firstChildTextOrNull("time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null
        val altitude = element.firstChildTextOrNull("ele")?.toDoubleOrNull()
        return TrackPoint(
            lat = lat,
            lng = lng,
            altitude = altitude,
            accuracyMeters = null,
            timestampEpochMillis = timestamp.toEpochMilli(),
        )
    }

    private fun decodeWaypoint(element: Element): Waypoint? {
        val lat = element.getAttribute("lat").toDoubleOrNull() ?: return null
        val lng = element.getAttribute("lon").toDoubleOrNull() ?: return null
        val name = element.firstChildTextOrNull("name") ?: return null
        val timestamp = element.firstChildTextOrNull("time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val altitude = element.firstChildTextOrNull("ele")?.toDoubleOrNull()
        val extension = element.getElementsByTagName("forager:waypoint").item(0) as? Element
        val trackId = extension?.getAttribute("trackId")?.ifBlank { null }
        val designation = extension?.getAttribute("designation")?.ifBlank { null }
            ?.let { runCatching { WaypointDesignation.valueOf(it) }.getOrNull() }
        return Waypoint(
            // A GPX file from elsewhere, or one this app wrote before the id was exported, carries
            // no id to restore — a fresh one, exactly as every decoded waypoint got before this.
            id = extension?.getAttribute("id")?.ifBlank { null } ?: UUID.randomUUID().toString(),
            lat = lat,
            lng = lng,
            altitude = altitude,
            name = name,
            note = element.firstChildTextOrNull("desc") ?: "",
            createdAtEpochMillis = timestamp?.toEpochMilli() ?: 0L,
            trackId = trackId,
            designation = designation,
        )
    }

    private fun Element.firstChildTextOrNull(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent

    /** `element.getAttribute(...)` returns `""`, never `null`, when the attribute is absent — that reads as "no verdict", not "false". */
    private fun String.toBooleanVerdictOrNull(): Boolean? = when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
