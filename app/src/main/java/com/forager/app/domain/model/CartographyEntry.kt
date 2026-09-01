package com.forager.app.domain.model

import java.time.LocalDate

/**
 * The user's own account of one day — Journal Stage 2b, `amendment-2b-entry-definition.md`. **A new
 * entity, not [MushroomLogEntry].** A find is one mushroom's structured field record; a Cartography
 * entry is authored on top of a compiled [DerivedTrip] — what the user chose to keep from that day's
 * finds/tracks/waypoints/offline regions, plus — optionally — what they wrote. Nothing here is
 * derived: unlike [DerivedTrip], which is re-computed on every read and stores nothing, this is the
 * durable, authored result — "compiled input, authored output," in the amendment's own words.
 *
 * **[text] is optional, never gated on** — `amendment-2b-optional-writing.md`: selection alone (which
 * finds, tracks, waypoints, and regions to keep) is a complete act of authorship. An entry with kept
 * items and no writing at all is finished, not incomplete; [isDraft] reflects only whether the user
 * has committed it, never whether [text] is blank.
 *
 * [isDraft] — "a draft is an unfinished entry" — is this entity's own draft concept, deliberately the
 * same English word as [MushroomLogEntry.isDraft]/[MushroomLogEntry.draftOfEntryId] and deliberately
 * a *different* mechanism: two independent lifecycles that happen to share a name, per the amendment's
 * explicit "coexist; do not unify them." Unlike a find's re-edit, which spawns a second, separate draft
 * row so the committed one keeps showing its last-saved values, a Cartography entry has no such
 * shadow-draft step — the dispatch never asked for one, and this entity's only stated draft/committed
 * transition is "unfinished" -> "finished" on the same row. Editing a committed entry writes to it in
 * place.
 *
 * [tags] is freeform text, no group entity — grouping is simply "entries sharing a tag string," done
 * in memory over whatever [tags] this entry (and every other) currently holds. Stored as a single
 * delimited column rather than a normalized table, per the dispatch's explicit "no tag table with its
 * own lifecycle."
 *
 * ## Three states per trip-report candidate — Stage 2b follow-up dispatch, point 2
 *
 * The four `*Decision` lists are the snapshot rule made concrete — "anything the entry displays as
 * text is snapshotted; anything it draws on a map is a reference." Each decision carries its own
 * display text plus the id [MushroomLogEntry]/[Track]/[Waypoint]/[com.forager.app.domain.OfflineRegionMetadata]
 * would need for Stage 2c's map recall — never the full record, which the entry does not display and
 * does not own.
 *
 * **A decision list holds every candidate the user has ruled on, kept or withheld — not just kept
 * ones.** [TrackDecision.kept] (and its find/waypoint/offline-region siblings) is what distinguishes
 * the two; presence in the list at all is what distinguishes "decided" from the third state, "not yet
 * decided," which this entity does not store — a trip-report candidate absent from every decision list
 * is undecided by definition, offered fresh on the next open rather than persisted as a row with
 * nothing to say. Withholding is a first-class, revisitable operation: reopening an entry a month later
 * must show exactly what was decided then, plus an offer on anything new, never silently un-deciding
 * anything — see [com.forager.app.data.local.CartographyEntryTrackRefEntity]'s own doc comment for the
 * storage side of this rule.
 */
data class CartographyEntry(
    val id: String,
    val date: LocalDate,
    val text: String,
    val tags: List<String>,
    val isDraft: Boolean,
    val updatedAtEpochMillis: Long,
    val findDecisions: List<FindDecision>,
    val trackDecisions: List<TrackDecision>,
    val waypointDecisions: List<WaypointDecision>,
    val offlineRegionDecisions: List<OfflineRegionDecision>,
    /**
     * Standalone [GalleryPhoto]s manually attached to this entry — `amendment-2b-optional-writing.md`:
     * "attachment remains a user action, never automatic," the same rule [MushroomLogEntry]'s own
     * photo attachment already follows. Not a bare id list, on reconsideration — see
     * [com.forager.app.data.local.CartographyEntryPhotoRefEntity]'s own doc comment for why a photo
     * still needs *something* snapshotted even though it is neither text nor a map drawing.
     *
     * **No three-state model here, unlike the four decision lists above.** A photo has no candidate
     * pool to be "not yet decided" about — nothing ever auto-suggests one the way a trip report
     * auto-surfaces finds/tracks/waypoints/regions; attaching one is always an explicit, one-shot user
     * action with no opposite ("withhold") to record. Attached or not attached is the whole state.
     */
    val photos: List<PhotoAttachment> = emptyList(),
) {
    companion object {
        /** A freshly-started, undecided entry for [date] — every decision list empty, [isDraft] always `true`. Mirrors [MushroomLogEntry.draft]'s own shape: persisted immediately by its use case, not held only in memory. */
        fun draft(id: String, date: LocalDate, updatedAtEpochMillis: Long): CartographyEntry = CartographyEntry(
            id = id,
            date = date,
            text = "",
            tags = emptyList(),
            isDraft = true,
            updatedAtEpochMillis = updatedAtEpochMillis,
            findDecisions = emptyList(),
            trackDecisions = emptyList(),
            waypointDecisions = emptyList(),
            offlineRegionDecisions = emptyList(),
            photos = emptyList(),
        )
    }
}

/** A decision (kept or withheld) about one find, plus its own display text — [MushroomLogEntry] stays the source of truth; this is enough for the entry to read sensibly if that find is later deleted from Records. */
data class FindDecision(
    val findId: String,
    val foundOn: LocalDate,
    val ownIdentification: String?,
    val hasPhotos: Boolean,
    val kept: Boolean,
)

/** A decision about one track, plus its own display text — see [TrackStatistics] for where [distanceMeters]/[durationMillis]/[pointCount] are computed from at snapshot time. */
data class TrackDecision(
    val trackId: String,
    val name: String?,
    val distanceMeters: Double,
    val durationMillis: Long,
    val pointCount: Int,
    val kept: Boolean,
)

/** A decision about one waypoint, plus its own display text. */
data class WaypointDecision(
    val waypointId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val kept: Boolean,
)

/** A decision about one offline region, plus its own display text — [radiusKm] is the region's search-radius parameter, not a live tile count (see [com.forager.app.domain.OfflineRegionMetadata], which this is snapshotted from). */
data class OfflineRegionDecision(
    val offlineRegionId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val kept: Boolean,
)

/** One manually-attached photo's own minimal snapshot — [attachedAtEpochMillis] is when the user attached it, not [LogPhoto.createdAtEpochMillis]. See [com.forager.app.data.local.CartographyEntryPhotoRefEntity]'s own doc comment for why this exists at all. */
data class PhotoAttachment(
    val photoId: String,
    val attachedAtEpochMillis: Long,
)
