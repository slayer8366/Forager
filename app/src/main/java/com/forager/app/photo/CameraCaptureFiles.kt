package com.forager.app.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Issues the destination a camera capture writes into, and the `FileProvider`-backed `content://`
 * URI to hand `ActivityResultContracts.TakePicture` so the camera app (a separate process) can
 * write there without this app granting broader file access.
 *
 * Captures land in `filesDir/captures/`, a scratch area distinct from [FilePhotoStore]'s
 * `filesDir/photos/`: a capture is a temporary handoff to the camera app, not yet a persisted log
 * photo — [FilePhotoStore.persist] is what actually copies it into permanent storage once the
 * capture succeeds and the entry is saved, and [deleteCapture] is how the caller (`MainActivity`)
 * cleans up the temporary file afterward, whether the capture was kept or the user backed out.
 */
class CameraCaptureFiles(private val context: Context) {

    private val capturesDir: File get() = File(context.filesDir, CAPTURES_SUBDIR).apply { mkdirs() }

    /** A fresh capture destination: the `content://` URI to launch the camera with, and the underlying file for [deleteCapture]. */
    fun newCapture(): Capture {
        val file = File(capturesDir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Capture(uri = uri, file = file)
    }

    fun deleteCapture(capture: Capture) {
        capture.file.delete()
    }

    data class Capture(val uri: Uri, val file: File)

    private companion object {
        const val CAPTURES_SUBDIR = "captures"
    }
}
