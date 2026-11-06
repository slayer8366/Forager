package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

/**
 * Reads a GPX file someone picked or shared into what plan T16 imports (dispatch 2026-09-28-634, the
 * owner's answers in -636). A separate reader from [GpxCodec.decode], not a change to it: `decode` is the
 * export's own round-trip codec, reads only the first `<trk>`, drops untimed points and unnamed waypoints
 * and parses a DOCTYPE (the verify findings in -636). Import needs every track, every point, routes told
 * apart, and the DOCTYPE refused, so it is a new path (CLAUDE.md, "New capability is a new function").
 * Pure Kotlin, JDK XML only, so it is tested headless.
 *
 * What it does, each line one of the owner's answers or an accepted smaller call (-636):
 * - Every `<trk>` with at least one point is one track. Its `<trkseg>`s are joined in file order.
 * - A `<trk>` with no points is left out: there is no line to draw. A file whose only content is
 *   routes reads as [GpxImportRead.Failure] [GpxImportFailure.ROUTE_ONLY]; routes are not imported yet.
 * - A file with no track and no route ([GpxImportFailure.NO_TRACK]) is not imported either, waypoints or
 *   not. That case had no ruling; it is this coder's call, disclosed in the T16 report.
 * - A point's `<time>` is kept as the file gives it, to the millisecond; [ImportGpxUseCase] rounds it.
 *   A time with no zone is read as UTC, which the GPX 1.1 schema states for `<time>` ("Date and time
 *   in are in Univeral Coordinated Time (UTC), not local time!"). An unparseable time is no time.
 * - A waypoint with no `<name>` gets [GpxImportedWaypoint.name] `null`; the use case names it.
 * - Forager's own `forager:waypoint designation` is kept (as a label only); its `id` and `trackId` are
 *   never read: every imported record gets a fresh id.
 * - **DOCTYPE and external entities are off.** A file declaring a DOCTYPE is refused outright, before
 *   the parser sees it, and the parser's entity resolver refuses anything external. Done this way, not
 *   with `setFeature(".../disallow-doctype-decl")`, because Android's own `DocumentBuilderFactory`
 *   throws on features it does not know, which the JDK's (what these tests run on) does not show.
 * - Coordinates outside -90..90 / -180..180, or missing, make the whole file unreadable rather than
 *   being dropped one by one: a silent drop would import a different line from the one in the file.
 */
object GpxImportReader {

    fun read(bytes: ByteArray): GpxImportRead {
        if (declaresDoctype(bytes)) return GpxImportRead.Failure(GpxImportFailure.UNREADABLE, "the file declares a DOCTYPE")
        val root = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> throw SAXException("external entities are not read") }
            val document = builder.parse(ByteArrayInputStream(bytes))
            if (document.doctype != null) return GpxImportRead.Failure(GpxImportFailure.UNREADABLE, "the file declares a DOCTYPE")
            document.documentElement
        } catch (e: Exception) {
            return GpxImportRead.Failure(GpxImportFailure.UNREADABLE, "not well-formed XML: ${e.message}")
        }
        if (root == null || root.localNameOrTag() != "gpx") {
            return GpxImportRead.Failure(GpxImportFailure.UNREADABLE, "the root element is not <gpx>")
        }

        val tracks = mutableListOf<GpxImportedTrack>()
        val waypoints = mutableListOf<GpxImportedWaypoint>()
        var routes = 0
        var metadataName: String? = null
        for (child in root.childElements()) {
            when (child.localNameOrTag()) {
                "metadata" -> metadataName = child.childText("name")
                "trk" -> {
                    val points = child.childElements("trkseg").flatMap { segment ->
                        segment.childElements("trkpt").map { point -> readPoint(point) ?: return badCoordinates("trkpt") }
                    }
                    if (points.isNotEmpty()) tracks += GpxImportedTrack(name = child.childText("name"), points = points)
                }
                "rte" -> routes++
                "wpt" -> waypoints += readWaypoint(child) ?: return badCoordinates("wpt")
            }
        }
        if (tracks.isEmpty()) {
            return if (routes > 0) {
                GpxImportRead.Failure(GpxImportFailure.ROUTE_ONLY, "$routes route(s), no track with points")
            } else {
                GpxImportRead.Failure(GpxImportFailure.NO_TRACK, "no track with points (${waypoints.size} waypoint(s))")
            }
        }
        return GpxImportRead.Success(GpxImportedFile(metadataName = metadataName, tracks = tracks, waypoints = waypoints))
    }

    private fun badCoordinates(element: String) =
        GpxImportRead.Failure(GpxImportFailure.UNREADABLE, "a <$element> has a missing or out-of-range lat or lon")

    private fun readPoint(element: Element): GpxImportedPoint? {
        val (lat, lng) = element.coordinates() ?: return null
        return GpxImportedPoint(
            lat = lat,
            lng = lng,
            altitude = element.childText("ele")?.toDoubleOrNull(),
            timeEpochMillis = element.childText("time")?.let(::parseGpxTime),
        )
    }

    private fun readWaypoint(element: Element): GpxImportedWaypoint? {
        val (lat, lng) = element.coordinates() ?: return null
        val designation = element.childElements("extensions")
            .flatMap { it.childElements("waypoint") }
            .firstOrNull()
            ?.getAttribute("designation")
            ?.let { value -> WaypointDesignation.entries.firstOrNull { it.name == value } }
        return GpxImportedWaypoint(
            lat = lat,
            lng = lng,
            altitude = element.childText("ele")?.toDoubleOrNull(),
            name = element.childText("name"),
            note = element.childText("desc").orEmpty(),
            timeEpochMillis = element.childText("time")?.let(::parseGpxTime),
            designation = designation,
        )
    }

    private fun Element.coordinates(): Pair<Double, Double>? {
        val lat = getAttribute("lat").trim().toDoubleOrNull() ?: return null
        val lng = getAttribute("lon").trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    /**
     * `xsd:dateTime` as GPX files carry it: with a `Z`, with an offset, or with no zone at all (UTC, per
     * the schema's own note on `<time>`). `null` for anything else; [ImportGpxUseCase] then treats the
     * point as having no time.
     */
    internal fun parseGpxTime(text: String): Long? {
        val trimmed = text.trim()
        runCatching { return Instant.parse(trimmed).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC).toEpochMilli() }
        return null
    }

    /** A DOCTYPE in the first 4 KB, in the file's bytes as ASCII; a UTF-16 file is caught by the parsed document's own `doctype`. */
    private fun declaresDoctype(bytes: ByteArray): Boolean =
        String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1).contains("<!DOCTYPE")

    private fun Node.localNameOrTag(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.childElements(name: String? = null): List<Element> {
        val out = mutableListOf<Element>()
        var node = firstChild
        while (node != null) {
            if (node is Element && (name == null || node.localNameOrTag() == name)) out += node
            node = node.nextSibling
        }
        return out
    }

    private fun Element.childText(name: String): String? = childElements(name).firstOrNull()?.textContent?.trim()?.ifEmpty { null }
}

/** What [GpxImportReader.read] made of a file. */
sealed interface GpxImportRead {
    data class Success(val file: GpxImportedFile) : GpxImportRead

    /** [detail] is for the log, never shown: the user sees [reason]'s own message. */
    data class Failure(val reason: GpxImportFailure, val detail: String) : GpxImportRead
}

/** Why a file was not imported. Each has one message, the owner's words where there were some (`gpxImportFailureMessage`). */
enum class GpxImportFailure {
    /** Not GPX, not well-formed, a DOCTYPE, bad coordinates, or a file that could not be opened. */
    UNREADABLE,

    /** Routes and no track: "Leave routes out for now" (-636). */
    ROUTE_ONLY,

    /** No track and no route. No ruling; this coder's call (T16 report). */
    NO_TRACK,

    /** Over [GPX_IMPORT_MAX_BYTES]: "10 MB limit" (-636). */
    TOO_BIG,

    /** The file read fine and saving it failed; nothing was kept. */
    SAVE_FAILED,
}

data class GpxImportedFile(
    /** `<metadata><name>`, the file's own name for itself, used when a track has none of its own. */
    val metadataName: String?,
    val tracks: List<GpxImportedTrack>,
    /** The file's `<wpt>`s, in file order. They all go with the first track (-634, "One track each"). */
    val waypoints: List<GpxImportedWaypoint>,
)

data class GpxImportedTrack(val name: String?, val points: List<GpxImportedPoint>)

data class GpxImportedPoint(val lat: Double, val lng: Double, val altitude: Double?, val timeEpochMillis: Long?)

data class GpxImportedWaypoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val name: String?,
    val note: String,
    val timeEpochMillis: Long?,
    val designation: WaypointDesignation?,
)
