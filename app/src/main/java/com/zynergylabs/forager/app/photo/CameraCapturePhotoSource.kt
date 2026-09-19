package com.zynergylabs.forager.app.photo

import android.net.Uri
import android.util.Log
import com.zynergylabs.forager.app.domain.model.PhotoSource

/**
 * A [PhotoSource] whose bytes came from this app's own camera just now — a
 * [CameraCaptureFiles.Capture], with its `FileProvider` `content://` URI and the file behind it.
 * The one [PhotoSource] implementation [FilePhotoStore.persist] treats as eligible for a live GPS
 * fix, and [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel] treats as eligible for the
 * fire-and-forget location patch after persisting — see that class's own doc comment for why the
 * coordinate lands via a follow-up write rather than blocking capture on
 * [com.zynergylabs.forager.app.domain.LocationProvider]. Only [FilePhotoStore] ever unwraps this —
 * see [PhotoSource]'s doc comment for why the domain-visible type stays opaque.
 *
 * ## This source owns its file, and [release] is where that ends
 *
 * A capture is scratch: [CameraCaptureFiles] issues it, the camera writes into it, and
 * [FilePhotoStore.persist] copies it into permanent storage. Until 2026-09-14 nothing deleted it
 * afterwards — every successful capture left a full-size JPEG in `captures/` for good, beside the
 * persisted copy — because a doc comment said `MainActivity` cleaned up and `MainActivity` never
 * had. The multi-shot camera made that a twenty-orphan session instead of one per round trip.
 *
 * [release] deletes the file, and `persist` calls it in a `finally`, so the file goes whether the
 * copy succeeded or not: on failure the user retakes and the file has no further use. Carrying the
 * [CameraCaptureFiles.Capture] rather than a bare `Uri` is what makes that possible without this
 * class having to reach back through a `ContentResolver` for a file it already knows.
 */
data class CameraCapturePhotoSource(val capture: CameraCaptureFiles.Capture) : PhotoSource {

    val uri: Uri get() = capture.uri

    override fun release() {
        val file = capture.file
        // Already gone is fine — release is idempotent. Present and undeletable is not, and is
        // the one case that would silently recreate the leak this exists to close.
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Couldn't delete capture '${file.name}' after persisting it; it stays in captures/ until the next startup sweep.")
        }
    }

    private companion object {
        const val TAG = "CameraCapturePhotoSource"
    }
}
