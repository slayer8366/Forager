package com.forager.app.domain

import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
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
 */
object GpxCodec {

    fun encode(document: GpxDocument): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Forager\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        document.track?.let { track ->
            append("  <trk>\n")
            track.name?.let { append("    <name>${escapeXml(it)}</name>\n") }
            append("    <trkseg>\n")
            track.points.forEach { point -> append(encodeTrackPoint(point)) }
            append("    </trkseg>\n")
            append("  </trk>\n")
        }
        document.waypoints.forEach { waypoint -> append(encodeWaypoint(waypoint)) }
        append("</gpx>\n")
    }

    private fun encodeTrackPoint(point: TrackPoint): String = buildString {
        append("      <trkpt lat=\"${point.lat}\" lon=\"${point.lng}\">\n")
        point.altitude?.let { append("        <ele>$it</ele>\n") }
        append("        <time>${Instant.ofEpochMilli(point.timestampEpochMillis)}</time>\n")
        append("      </trkpt>\n")
    }

    private fun encodeWaypoint(waypoint: Waypoint): String = buildString {
        append("  <wpt lat=\"${waypoint.lat}\" lon=\"${waypoint.lng}\">\n")
        waypoint.altitude?.let { append("    <ele>$it</ele>\n") }
        append("    <time>${Instant.ofEpochMilli(waypoint.createdAtEpochMillis)}</time>\n")
        append("    <name>${escapeXml(waypoint.name)}</name>\n")
        if (waypoint.note.isNotBlank()) append("    <desc>${escapeXml(waypoint.note)}</desc>\n")
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

        val waypoints = root.getElementsByTagName("wpt").let { nodeList ->
            (0 until nodeList.length).mapNotNull { i -> decodeWaypoint(nodeList.item(i) as Element) }
        }

        return GpxDocument(track = track, waypoints = waypoints)
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
        return Waypoint(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lng = lng,
            altitude = altitude,
            name = name,
            note = element.firstChildTextOrNull("desc") ?: "",
            createdAtEpochMillis = timestamp?.toEpochMilli() ?: 0L,
        )
    }

    private fun Element.firstChildTextOrNull(tagName: String): String? =
        getElementsByTagName(tagName).item(0)?.textContent

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
