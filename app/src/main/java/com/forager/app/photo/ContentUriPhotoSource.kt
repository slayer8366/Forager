package com.forager.app.photo

import android.net.Uri
import com.forager.app.domain.model.PhotoSource

/**
 * The real, `Uri`-carrying [PhotoSource] — camera captures and gallery picks both resolve to a
 * `content://` URI (a camera capture's is our own [CameraCaptureFiles]-issued `FileProvider` URI;
 * a gallery pick's is whatever the system photo picker hands back), so one wrapper covers both.
 * Only [FilePhotoStore] ever unwraps this — see [com.forager.app.domain.model.PhotoSource]'s doc
 * comment for why the domain-visible type stays opaque.
 */
data class ContentUriPhotoSource(val uri: Uri) : PhotoSource
