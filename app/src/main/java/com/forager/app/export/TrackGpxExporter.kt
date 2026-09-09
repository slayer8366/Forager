package com.forager.app.export

import android.content.Context
import com.forager.app.domain.GpxCodec
import com.forager.app.domain.NETWORK_FIX_EXCLUSION_RULES
import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPointRecord
import com.forager.app.domain.model.Waypoint
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes a recorded [Track] out as a GPX file, the missing "write it somewhere shareable" half of
 * [GpxCodec] — that class's own doc comment is explicit it is the codec only. This is Android-layer
 * (a real [File], a real [Context]-derived directory), same split as
 * [com.forager.app.crash.CrashFileStore] over the pure-Kotlin thing it persists.
 *
 * Filenames are derived from [Track.startedAtEpochMillis], not the moment of export: two tracks
 * from the same trip get two distinct, stable names regardless of when either is shared (field-test
 * dispatch's own requirement — "so multiple tracks from one trip don't collide"), and exporting the
 * same track twice overwrites the same file rather than accumulating duplicates.
 */
class TrackGpxExporter(private val exportDir: File) {

    /**
     * [fullRecord] and [waypoints] are required, not defaulted — GPX full-record export dispatch.
     * [track] alone (the filtered read) has no way to reach a network-provider fix the read seam
     * excluded; only a caller that separately fetched [com.forager.app.domain.TrackRepository.getFullRecord]
     * can hand one in, and requiring the parameter here means a caller can't omit it by accident and
     * silently ship a file the export rule was built to prevent (ruling 2: the export carries the
     * full data set). Pass an empty list deliberately when there's genuinely nothing more to carry
     * — that's still an honest value, not this default doing the omitting for you.
     *
     * [GpxDocument.exclusionRules] is not a parameter: which rules the read seam applies is a
     * property of this build, not of the caller, so it is stamped here from
     * [NETWORK_FIX_EXCLUSION_RULES] rather than threaded down through the UI, where a screen
     * could get it wrong or omit it (GPX
     * rule-provenance dispatch, 2026-09-09). The same constant is what
     * [com.forager.app.data.repository.RoomTrackRepository.getFullRecord] names per excluded
     * point, so if the two ever drift the file says so — the block declares a rule set that its
     * own points do not name.
     */
    fun write(track: Track, fullRecord: List<TrackPointRecord>, waypoints: List<Waypoint>): File {
        exportDir.mkdirs()
        val file = File(exportDir, fileNameFor(track))
        file.writeText(
            GpxCodec.encode(
                GpxDocument(
                    track = track,
                    waypoints = waypoints,
                    fullRecord = fullRecord,
                    exclusionRules = NETWORK_FIX_EXCLUSION_RULES,
                ),
            ),
        )
        return file
    }

    private fun fileNameFor(track: Track): String {
        val timestamp = FILE_NAME_FORMAT.format(
            Instant.ofEpochMilli(track.startedAtEpochMillis).atZone(ZoneId.systemDefault()),
        )
        return "forager-track-$timestamp.gpx"
    }

    companion object {
        private val FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        /**
         * Cache storage, not external-files: unlike [com.forager.app.crash.CrashFileStore.forContext]'s
         * `crashes/` (meant to accumulate and be inspected later from Settings), an exported GPX file
         * only needs to exist long enough for the share-sheet target app to read it — see
         * `res/xml/file_paths.xml`'s `tracks` cache-path entry.
         */
        fun forContext(context: Context): TrackGpxExporter = TrackGpxExporter(File(context.cacheDir, "tracks"))
    }
}
