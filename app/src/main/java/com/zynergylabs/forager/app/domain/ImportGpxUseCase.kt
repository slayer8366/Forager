package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import java.util.UUID

/**
 * Plan T16 (dispatch 2026-09-28-634, the owner's answers in -636): a GPX file becomes tracks in Records.
 * Both ways in end here: Records > Tracks > "Import GPX" (the system file picker) and Open with or Share
 * from another app (`GpxImportActivity`). Reads through [GpxFileSource], so this class never sees a `Uri`.
 *
 * **What it never touches.** Recording, navigation and alerts: it writes ended tracks and their
 * waypoints through the two repositories and calls nothing else. Every imported track gets an end time,
 * because a track with none is read as one being recorded (the verify findings in -636).
 *
 * The rules, each one the owner's answer or an accepted smaller call (-636):
 * - **10 MB limit**: a file over [GPX_IMPORT_MAX_BYTES] is not read past the limit.
 * - **One track each**: every `<trk>` is its own track; the file's waypoints all go with the first.
 * - **Name**: the track's own `<name>`, else the file's `<metadata><name>`, else the file name without
 *   `.gpx`. When a file has several tracks and one has no name of its own, the fallback gets " (n)",
 *   its place in the file, so two tracks are never shown under one name (this coder's call).
 * - **Round to the second**: every time is rounded to the nearest whole second. A millisecond-timed
 *   point would otherwise be hidden by the network-fix rule ([isNetworkProviderTimestamp]).
 * - **No times**: a track in which any point has no time is imported as one with no times
 *   ([Track.importedWithoutTimes]): dated the import moment, its points kept in file order. The stored
 *   point times are then one whole second apart from the import moment, because the points are read
 *   back ordered by time; they are an ordering, never shown (the surfaces say "No times in file").
 *   A track with some times and some gaps is treated the same way rather than guessing the gaps.
 * - **Waypoints**: a fresh id each (waypoints insert with REPLACE, so the file's ids could overwrite
 *   existing ones); no name becomes "Waypoint N", N its place among the file's waypoints; no time takes
 *   the first track's start; a Forager ORIGIN or END designation is kept as a label only (no track's
 *   `originWaypointId` is set from it).
 * - **All or nothing**: a failure while saving deletes whatever this import had already written, and
 *   the outcome says so ([GpxImportFailure.SAVE_FAILED]); a partial import is never reported as done.
 */
class ImportGpxUseCase(
    private val trackRepository: TrackRepository,
    private val waypointRepository: WaypointRepository,
    private val currentTime: CurrentTimeProvider,
    private val errorLog: ErrorLog,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(source: GpxFileSource): GpxImportOutcome {
        val bytes = when (val read = source.read(GPX_IMPORT_MAX_BYTES)) {
            is GpxFileRead.Bytes -> read
            GpxFileRead.TooBig -> return GpxImportOutcome.Failed(GpxImportFailure.TOO_BIG)
            is GpxFileRead.Failed -> {
                errorLog.w(TAG, "Couldn't open the GPX file; nothing imported.", read.error)
                return GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE)
            }
        }
        val file = when (val parsed = GpxImportReader.read(bytes.bytes)) {
            is GpxImportRead.Success -> parsed.file
            is GpxImportRead.Failure -> {
                errorLog.w(TAG, "GPX file not imported (${parsed.reason}): ${parsed.detail}", GpxImportException(parsed.detail))
                return GpxImportOutcome.Failed(parsed.reason)
            }
        }
        val importedAt = currentTime.nowEpochMillis()
        val tracks = buildTracks(file, fileBaseName(bytes.displayName), importedAt)
        val waypoints = buildWaypoints(file, tracks.first())
        return save(tracks, waypoints)
    }

    private fun buildTracks(file: GpxImportedFile, fileBaseName: String?, importedAt: Long): List<Track> {
        val several = file.tracks.size > 1
        val fallback = file.metadataName ?: fileBaseName
        return file.tracks.mapIndexed { index, imported ->
            val name = imported.name ?: fallback?.let { if (several) "$it (${index + 1})" else it }
            val timed = imported.points.all { it.timeEpochMillis != null }
            val points = if (timed) {
                imported.points.map { it.toTrackPoint(roundToSecond(it.timeEpochMillis!!)) }
            } else {
                val start = floorToSecond(importedAt)
                imported.points.mapIndexed { i, point -> point.toTrackPoint(start + i * 1_000L) }
            }
            Track(
                id = idGenerator(),
                name = name,
                startedAtEpochMillis = points.minOf { it.timestampEpochMillis },
                endedAtEpochMillis = points.maxOf { it.timestampEpochMillis },
                points = points,
                importedAtEpochMillis = importedAt,
                importedWithoutTimes = !timed,
            )
        }
    }

    private fun buildWaypoints(file: GpxImportedFile, firstTrack: Track): List<Waypoint> =
        file.waypoints.mapIndexed { index, imported ->
            Waypoint(
                id = idGenerator(),
                lat = imported.lat,
                lng = imported.lng,
                altitude = imported.altitude,
                name = imported.name ?: "Waypoint ${index + 1}",
                note = imported.note,
                createdAtEpochMillis = imported.timeEpochMillis?.let(::roundToSecond) ?: firstTrack.startedAtEpochMillis,
                trackId = firstTrack.id,
                designation = imported.designation,
            )
        }

    private suspend fun save(tracks: List<Track>, waypoints: List<Waypoint>): GpxImportOutcome {
        val savedTracks = mutableListOf<String>()
        val savedWaypoints = mutableListOf<String>()
        val failure: Throwable? = run {
            for (track in tracks) {
                trackRepository.createWithPoints(track).onFailure { return@run it }
                savedTracks += track.id
            }
            for (waypoint in waypoints) {
                waypointRepository.save(waypoint).onFailure { return@run it }
                savedWaypoints += waypoint.id
            }
            null
        }
        if (failure == null) return GpxImportOutcome.Imported(tracks.map { it.id })
        errorLog.w(TAG, "Saving an imported GPX file failed; removing the ${savedTracks.size} track(s) and ${savedWaypoints.size} waypoint(s) it had written.", failure)
        savedWaypoints.forEach { id -> waypointRepository.delete(id).onFailure { errorLog.w(TAG, "Couldn't remove imported waypoint $id after a failed import.", it) } }
        savedTracks.forEach { id -> trackRepository.delete(id).onFailure { errorLog.w(TAG, "Couldn't remove imported track $id after a failed import.", it) } }
        return GpxImportOutcome.Failed(GpxImportFailure.SAVE_FAILED)
    }

    private fun GpxImportedPoint.toTrackPoint(timestamp: Long) =
        TrackPoint(lat = lat, lng = lng, altitude = altitude, accuracyMeters = null, timestampEpochMillis = timestamp)

    private companion object {
        const val TAG = "ImportGpx"
    }
}

/** The owner's "10 MB limit" (-636), as 10 MiB: a file larger than this is not imported. */
const val GPX_IMPORT_MAX_BYTES: Int = 10 * 1024 * 1024

/** Nearest whole second, halves up ("Round to the second", -636). */
internal fun roundToSecond(epochMillis: Long): Long = Math.floorDiv(epochMillis + 500L, 1_000L) * 1_000L

private fun floorToSecond(epochMillis: Long): Long = Math.floorDiv(epochMillis, 1_000L) * 1_000L

/** "Morning loop.gpx" to "Morning loop"; a blank name is no name. */
internal fun fileBaseName(displayName: String?): String? {
    val trimmed = displayName?.trim() ?: return null
    val base = if (trimmed.endsWith(".gpx", ignoreCase = true)) trimmed.dropLast(4) else trimmed
    return base.trim().ifEmpty { null }
}

/** The file to import, behind an interface this project owns (CLAUDE.md, external integrations): the app reads a content `Uri`. */
fun interface GpxFileSource {
    /** Reads at most [maxBytes]; a file with more than that answers [GpxFileRead.TooBig] without reading the rest. */
    suspend fun read(maxBytes: Int): GpxFileRead
}

sealed interface GpxFileRead {
    /** [displayName] is the file's name as its provider gives it ("Morning loop.gpx"), or `null`. */
    class Bytes(val bytes: ByteArray, val displayName: String?) : GpxFileRead

    data object TooBig : GpxFileRead

    data class Failed(val error: Throwable) : GpxFileRead
}

sealed interface GpxImportOutcome {
    /** The new tracks' ids, in file order; never empty. */
    data class Imported(val trackIds: List<String>) : GpxImportOutcome

    data class Failed(val reason: GpxImportFailure) : GpxImportOutcome
}

/** Carries a parse failure's detail into the log, which takes a [Throwable]. */
private class GpxImportException(message: String) : Exception(message)
