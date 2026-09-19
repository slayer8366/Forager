package com.zynergylabs.forager.app.photo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Issues the destination a camera capture writes into, and the `FileProvider`-backed `content://`
 * URI the in-app camera hands to `ImageCapture` (and, until 2026-09-14, that
 * `ActivityResultContracts.TakePicture` handed to an external camera app).
 *
 * Captures land in `filesDir/captures/`, a scratch area distinct from [FilePhotoStore]'s
 * `filesDir/photos/`: a capture is a temporary handoff, not yet a persisted log photo.
 * [FilePhotoStore.persist] copies it into permanent storage and then releases it through
 * [CameraCapturePhotoSource.release], which is what deletes the temporary file.
 *
 * **A previous version of this comment said `MainActivity` cleaned the file up afterward.
 * `MainActivity` never did** — `git log -S deleteCapture` on it is empty — and nothing else did
 * either, so every successful capture since this class was written left its full-size JPEG here
 * for good. Corrected 2026-09-14 along with the leak; recorded because a doc comment that names a
 * caller that does not exist is precisely the stale-claim family CLAUDE.md indexes.
 *
 * ## [sweepOrphans], and the premise it rests on
 *
 * Deleting on release closes the leak going forward. It does nothing for the orphans already on
 * every install that has taken a photo, and nothing for a capture whose process died mid-write.
 * [sweepOrphans] handles both at startup — **on a background dispatcher; `Application.onCreate`
 * runs on the main thread**, which is the correction that put the dispatch there.
 *
 * It deletes only files older than the current process. That is the definition of an orphan — a
 * capture belongs to the process that issued it, and a process that no longer exists cannot come
 * back for its file — and it is also what makes the sweep safe to run asynchronously beside live
 * captures, in production and under Robolectric alike. A thresholdless sweep was considered and
 * would be wrong in one case: an external camera intent, where the platform kills this process
 * while the camera app runs, recreates it when the result comes back, and runs `onCreate` *before*
 * the result is delivered — the sweep would delete the file the result is about to name. That
 * path does not exist in this tree (`grep TakePicture\|ACTION_IMAGE_CAPTURE` finds comments and
 * CameraX's own `takePicture` only, checked 2026-09-14), and the age check means the sweep stays
 * correct even if it comes back, because a file the platform preserved across process death would
 * still be older than the new process. Checked at 2 s before process start rather than exactly at
 * it, for filesystems with coarse mtime.
 */
class CameraCaptureFiles(private val context: Context) {

    private val capturesDir: File get() = File(context.filesDir, CAPTURES_SUBDIR).apply { mkdirs() }

    /** A fresh capture destination: the `content://` URI to launch the camera with, and the underlying file. Creates the directory, not the file. */
    fun newCapture(): Capture {
        val file = File(capturesDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Capture(uri = uri, file = file)
    }

    fun deleteCapture(capture: Capture) {
        capture.file.delete()
    }

    /**
     * Deletes every capture file last modified before [processStartedAtMillis] minus a small
     * guard, and returns how many went. Files at or after that are this process's own and are
     * left alone. See the class doc for why this is the right line and why it is safe beside
     * live captures. A file that cannot be deleted is logged and counted as kept, never silently.
     */
    fun sweepOrphans(processStartedAtMillis: Long): Int {
        val cutoff = processStartedAtMillis - ORPHAN_MTIME_GUARD_MILLIS
        var deleted = 0
        for (file in capturesDir.listFiles().orEmpty()) {
            if (file.lastModified() >= cutoff) continue
            if (file.delete()) deleted++ else Log.w(TAG, "Couldn't delete orphaned capture '${file.name}'; leaving it for the next sweep.")
        }
        return deleted
    }

    data class Capture(val uri: Uri, val file: File)

    private companion object {
        const val CAPTURES_SUBDIR = "captures"
        const val TAG = "CameraCaptureFiles"

        /** Coarse-mtime filesystems round to 2 s. Internal storage is not one, but the guard costs nothing and an orphan is never this young. */
        const val ORPHAN_MTIME_GUARD_MILLIS = 2_000L
    }
}
