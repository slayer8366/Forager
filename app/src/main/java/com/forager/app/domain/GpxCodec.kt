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

    private const val FORAGER_NAMESPACE = "https://forager.app/gpx/1"

    fun encode(document: GpxDocument): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append(
            "<gpx version=\"1.1\" creator=\"Forager\" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:forager=\"$FORAGER_NAMESPACE\">\n",
        )
        document.track?.let { track ->
            append("  <trk>\n")
            track.name?.let { append("    <name>${escapeXml(it)}</name>\n") }
            if (document.fullRecord.isNotEmpty()) append(encodeFullRecord(document.fullRecord))
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
     */
    private fun encodeFullRecord(records: List<TrackPointRecord>): String = buildString {
        append("    <extensions>\n")
        append(
            "      <forager:fullRecord authoritative=\"true\" trksegDerivedFromFullRecord=\"true\" " +
                "pointCount=\"${records.size}\">\n",
        )
        records.forEach { record -> append(encodeFullRecordPoint(record)) }
        append("      </forager:fullRecord>\n")
        append("    </extensions>\n")
    }

    /**
     * `timeEpochMillis` rather than an ISO string: the literal stored value, with no formatting or
     * timezone round-trip to lose precision through — full millisecond precision is the entire
     * reason this element exists (unlike the display `<trkpt><time>`, which [encodeTrackPoint]
     * leaves exactly as before). Stable numeric attribute names throughout, no free prose.
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
        append("/>\n")
    }

    private fun encodeTrackPoint(point: TrackPoint): String = buildString {
        append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lng}\">\n")
        point.altitude?.let { append("        <ele>$it</ele>\n") }
        append("        <time>${Instant.ofEpochMilli(point.timestampEpochMillis)}</time>\n")
        append("      </trkpt>\n")
    }

    /**
     * [Waypoint.trackId] and [Waypoint.designation] have no standard GPX element — carried in a
     * `<wpt><extensions>` `forager:waypoint`, the same namespace [encodeFullRecord] uses. Omitted
     * entirely when both are `null` (an ordinary, not-track-related waypoint), matching this file's
     * existing omit-when-absent pattern for `<ele>`/`<desc>`.
     */
    private fun encodeWaypoint(waypoint: Waypoint): String = buildString {
        append("  <wpt lat=\"${waypoint.lat}\" lon=\"${waypoint.lng}\">\n")
        waypoint.altitude?.let { append("    <ele>$it</ele>\n") }
        append("    <time>${Instant.ofEpochMilli(waypoint.createdAtEpochMillis)}</time>\n")
        append("    <name>${escapeXml(waypoint.name)}</name>\n")
        if (waypoint.note.isNotBlank()) append("    <desc>${escapeXml(waypoint.note)}</desc>\n")
        if (waypoint.trackId != null || waypoint.designation != null) {
            append("    <extensions>\n")
            append("      <forager:waypoint")
            waypoint.trackId?.let { append(" trackId=\"${escapeXml(it)}\"") }
            waypoint.designation?.let { append(" designation=\"${it.name}\"") }
            append("/>\n")
            append("    </extensions>\n")
        }
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
        val fullRecord = trkElement?.let { trk -> decodeFullRecord(trk) }.orEmpty()

        val waypoints = root.getElementsByTagName("wpt").let { nodeList ->
            (0 until nodeList.length).mapNotNull { i -> decodeWaypoint(nodeList.item(i) as Element) }
        }

        return GpxDocument(track = track, waypoints = waypoints, fullRecord = fullRecord)
    }

    private fun decodeFullRecord(trkElement: Element): List<TrackPointRecord> {
        val fullRecordElement = trkElement.getElementsByTagName("forager:fullRecord").item(0) as? Element
            ?: return emptyList()
        val pointNodes = fullRecordElement.getElementsByTagName("forager:point")
        return (0 until pointNodes.length).mapNotNull { i -> decodeFullRecordPoint(pointNodes.item(i) as Element) }
    }

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
            id = UUID.randomUUID().toString(),
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
