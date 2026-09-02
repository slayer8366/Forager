package com.forager.app.photo

import android.net.Uri
import com.forager.app.domain.model.PhotoSource

/**
 * A [PhotoSource] whose bytes came from the system photo picker — a `content://` URI the picker
 * handed back, pointing at a file this app doesn't own and didn't just create. The one
 * [PhotoSource] implementation [FilePhotoStore.persist] reads for EXIF (location and capture
 * timestamp, when present) rather than a live GPS fix — see [PhotoSource]'s own doc comment for why
 * the two sources are never interchangeable. Only [FilePhotoStore] ever unwraps this — see
 * [PhotoSource]'s doc comment for why the domain-visible type stays opaque.
 */
data class GalleryImportPhotoSource(val uri: Uri) : PhotoSource
