package com.forager.app.export

import android.content.Context
import com.forager.app.domain.GpxCodec
import com.forager.app.domain.model.GpxDocument
import com.forager.app.domain.model.Track
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

    fun write(track: Track): File {
        exportDir.mkdirs()
        val file = File(exportDir, fileNameFor(track))
        file.writeText(GpxCodec.encode(GpxDocument(track = track, waypoints = emptyList())))
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
