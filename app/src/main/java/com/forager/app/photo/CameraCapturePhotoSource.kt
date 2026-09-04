package com.forager.app.photo

import android.net.Uri
import com.forager.app.domain.model.PhotoSource

/**
 * A [PhotoSource] whose bytes came from this app's own camera capture just now — our own
 * [CameraCaptureFiles]-issued `FileProvider` `content://` URI. The one [PhotoSource]
 * implementation [FilePhotoStore.persist] treats as eligible for a live GPS fix, and
 * [com.forager.app.ui.log.MushroomLogViewModel] treats as eligible for the fire-and-forget
 * location patch after persisting — see that class's own doc comment for why the coordinate lands
 * via a follow-up write rather than blocking capture on [com.forager.app.domain.LocationProvider].
 * Only [FilePhotoStore] ever unwraps this — see [PhotoSource]'s doc comment for why the
 * domain-visible type stays opaque.
 */
data class CameraCapturePhotoSource(val uri: Uri) : PhotoSource
