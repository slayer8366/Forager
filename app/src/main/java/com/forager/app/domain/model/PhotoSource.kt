package com.forager.app.domain.model

/**
 * Where a new photo's bytes come from — a camera capture or a gallery pick. Deliberately opaque
 * here: this marker interface carries no data itself and names no platform type (not even
 * `android.net.Uri`), so `domain/` and any ViewModel handling one never need to know what backs
 * it — per CLAUDE.md's wrap-external-integrations rule, extended to cover the photo-picker/camera
 * result type the same way [com.forager.app.domain.LocationProvider] already covers
 * `android.location`. The real, `Uri`-carrying implementation (`ContentUriPhotoSource`) lives in
 * `com.forager.app.photo`, alongside the [com.forager.app.domain.PhotoStore] implementation that's
 * the only thing that ever inspects one.
 */
interface PhotoSource
