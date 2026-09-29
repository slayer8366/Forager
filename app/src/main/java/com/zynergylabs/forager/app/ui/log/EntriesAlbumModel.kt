package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day of the Entries album (journal redesign J2, T3; plan J3): the photos taken on [date], or,
 * when [date] is null, every photo whose creation time is not known.
 */
internal data class AlbumDay(val date: LocalDate?, val photos: List<GalleryPhoto>)

/**
 * Groups [photos] by the device-local calendar day of [com.zynergylabs.forager.app.domain.model.LogPhoto.createdAtEpochMillis],
 * for the album view. Pure Kotlin, no Compose, so it is unit-tested headless (CLAUDE.md,
 * Architecture).
 *
 * - **Days newest first**, the same direction the Records logbook uses (plan J4, J1's
 *   `buildRecordsLogbook`).
 * - **Within a day, input order** (the gallery's own order, `MushroomLogUiState.galleryPhotos`),
 *   so the album never reorders photos the gallery already ordered.
 * - **Unknown dates last, in one group.** A migrated photo's creation time is not knowable
 *   (`LogPhoto.createdAtEpochMillis`'s doc comment), and the removed Photo Gallery screen shows "Date unknown"
 *   rather than inventing one; this does the same at group level rather than filing it under a
 *   fabricated day.
 * - Day boundaries are device-local ([zone] defaults to the system zone), the convention
 *   `domain/LocalDayRange.kt` states.
 */
internal fun groupAlbumByDay(photos: List<GalleryPhoto>, zone: ZoneId = ZoneId.systemDefault()): List<AlbumDay> {
    val (dated, undated) = photos.partition { it.photo.createdAtEpochMillis != null }
    val days = dated
        .groupBy { Instant.ofEpochMilli(it.photo.createdAtEpochMillis!!).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (date, dayPhotos) -> AlbumDay(date, dayPhotos) }
    return if (undated.isEmpty()) days else days + AlbumDay(null, undated)
}
