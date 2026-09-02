package com.forager.app.domain.model

/**
 * Where a new photo's bytes come from — a camera capture or a gallery pick. Deliberately opaque
 * here: this marker interface carries no data itself and names no platform type (not even
 * `android.net.Uri`), so `domain/` and any ViewModel handling one never need to know what backs
 * it — per CLAUDE.md's wrap-external-integrations rule, extended to cover the photo-picker/camera
 * result type the same way [com.forager.app.domain.LocationProvider] already covers
 * `android.location`. The real, `Uri`-carrying implementations (`CameraCapturePhotoSource`,
 * `GalleryImportPhotoSource`) live in `com.forager.app.photo`, alongside the
 * [com.forager.app.domain.PhotoStore] implementation that's the only thing that ever inspects one.
 *
 * A plain (non-`sealed`) interface, not a `sealed` one — photo-geodata dispatch, deliberately kept
 * this way even though the two implementations below are now known and exhaustive. A `sealed`
 * interface only permits implementations declared in its own compilation unit, and several test
 * files across `src/test/` already implement this interface directly as an anonymous fake (e.g.
 * `FilePhotoStoreTest`'s "a PhotoSource this store doesn't understand" case) — sealing it would
 * break every one of those for a compiler guarantee this codebase doesn't otherwise rely on
 * ([FilePhotoStore.persist]'s own `when` already has to `error()` on an unrecognized source, since
 * it's reached through this same open interface either way).
 *
 * ## Camera vs. gallery, and why it matters (photo-geodata dispatch)
 *
 * The two implementations exist to answer one question a single `Uri`-wrapping type couldn't:
 * *did this photo's bytes come from the device's own camera just now, or from something already on
 * the device?* [FilePhotoStore.persist] and [com.forager.app.ui.log.MushroomLogViewModel] both
 * branch on it — a camera capture is eligible for a live GPS fix (the user is physically present);
 * a gallery import is eligible for an EXIF read (the file may already carry one). Neither path
 * substitutes for the other. See [com.forager.app.domain.model.LogPhoto]'s own doc comment for the
 * full reasoning.
 */
interface PhotoSource
