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
 * "unknown" rather than a fabricated timestamp. Every photo persisted from here forward has one.
 */
data class LogPhoto(
    val id: String,
    val relativePath: String,
    val createdAtEpochMillis: Long?,
)
