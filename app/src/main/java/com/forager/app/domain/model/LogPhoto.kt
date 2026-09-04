package com.forager.app.domain.model

/**
 * One photo in the gallery — a first-class thing that exists whether or not any log entry
 * references it (owner decision, 2026-08-22: "photos live in a gallery in their own right; log
 * entries reference them, they do not own them"). [relativePath] is relative to app-private
 * storage (`context.filesDir`), never an absolute path — an absolute path breaks across app
 * reinstall/restore, since `filesDir`'s absolute location isn't guaranteed stable. See [PhotoStore].
 *
 * [createdAtEpochMillis] is `null` only for a photo migrated from before this column existed
 * (`MIGRATION_7_8`) — no real creation time is knowable for those rows, so this stays an honest
 * "unknown" rather than a fabricated timestamp. Every photo persisted from here forward has one —
 * for an import with a readable EXIF capture date, that date, not the import time (see
 * [FilePhotoStore]'s own doc comment on why).
 *
 * ## [latitude]/[longitude] — a service, never a requirement (photo-geodata dispatch)
 *
 * `null` is the ordinary, expected state, not an error or an incomplete one — most photos will
 * never have one, and nothing anywhere treats a missing coordinate as a problem to warn about or
 * block on. The two sources are deliberately **not interchangeable**, and neither ever falls back
 * to the other: a **camera capture** reads the device's own GPS at the moment the shutter fires
 * (the user is physically present, so this is honest by construction) and is written here only,
 * never into the image file itself, so the coordinate never leaks when the photo is shared. An
 * **imported** photo reads its own EXIF tags, and only its EXIF tags — never a substitute device
 * fix, which would record where the user stood at import time (their kitchen, a week later) while
 * looking exactly as authoritative as a real one. See [FilePhotoStore]'s own doc comment for where
 * each is actually read, and [MushroomLogViewModel]'s for why a camera capture's coordinate lands
 * here via a follow-up write, never the same write that creates the row.
 */
data class LogPhoto(
    val id: String,
    val relativePath: String,
    val createdAtEpochMillis: Long?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
