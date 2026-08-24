package com.forager.app.domain.model

import java.time.LocalDate

/**
 * A structured field record of one mushroom find — what the forager observed, recorded faithfully.
 *
 * **This type never identifies the mushroom.** No field here is, or feeds, a species suggestion, a
 * candidate list, a "likely," or a confidence score — that is a stated safety property from the
 * project owner (some species this data separates can be lethal), not a scope cut. [ownIdentification]
 * is the one exception in spirit only: it is the forager's own claim, stored as theirs and never
 * app-generated, the same way a paper field notebook would record "I think this is X" in the
 * finder's own words.
 *
 * ## Deferred observation — this entry is meant to be edited
 *
 * A spore print is read overnight, so an entry is normally created in the field with several
 * sections still [Observed.NotObserved]/[Feature.NotObserved], then completed later once the print
 * is read. Unlike [PlannedTrip] (which deliberately has no rename-after-creation flow), a log entry
 * needs a real edit path — see `SaveMushroomLogEntryUseCase`, which upserts by [id] the same way
 * [com.forager.app.domain.SavePlannedTripUseCase] does.
 *
 * ## Why so many nested section types instead of one flat field list
 *
 * See [Observed] and [Feature] for the two-vs-three-state distinction, and [HymenophoreDetails]/
 * [StipeDetails] for why dependent fields (gill attachment, a stipe's interior) are nested inside
 * the sealed choice that makes them applicable rather than living as top-level nullable fields:
 * nesting is what makes "an entry with pores carrying a gill attachment" impossible to construct,
 * rather than merely invalid.
 */
data class MushroomLogEntry(
    val id: String,
    /**
     * `null` for an entry created with no location — L4 (`docs/plans/pr26-rework.md`'s Workstream
     * L) routes entry creation to the entry page rather than through a location-placement step
     * first, so an entry will routinely start without one. Nothing in this codebase constructs a
     * location-less entry yet ([draft] still takes [LatLng] wherever a caller passes one); L3 only
     * makes the type able to represent it.
     */
    val foundAt: LatLng?,
    val foundOn: LocalDate,
    val cap: CapSection,
    val hymenophore: HymenophoreSection,
    val stipe: StipeSection,
    val veil: VeilSection,
    val contextFlesh: ContextFleshSection,
    val sporePrint: SporePrintSection,
    val hostSubstrate: HostSubstrateSection,
    val notes: String,
    /** The forager's own claim about what this is — explicitly theirs, never app-generated. See this type's own doc comment. */
    val ownIdentification: String?,
    val photos: List<LogPhoto>,
    val syncState: LogSyncState,
) {
    companion object {
        /**
         * A freshly-started entry at [location] on [date], with every section unrecorded.
         *
         * [location] is `LatLng?`, not defaulted — every call in this codebase today passes a
         * concrete [LatLng] (nothing yet constructs a location-less entry; that's L4's job), so
         * widening the parameter type alone required no call-site changes. No default value: a
         * caller that wants `foundAt == null` says `location = null` explicitly rather than falling
         * into it by omitting the argument.
         */
        fun draft(id: String, location: LatLng?, date: LocalDate): MushroomLogEntry = MushroomLogEntry(
            id = id,
            foundAt = location,
            foundOn = date,
            cap = CapSection.EMPTY,
            hymenophore = HymenophoreSection.EMPTY,
            stipe = StipeSection.EMPTY,
            veil = VeilSection.EMPTY,
            contextFlesh = ContextFleshSection.EMPTY,
            sporePrint = SporePrintSection.EMPTY,
            hostSubstrate = HostSubstrateSection.EMPTY,
            notes = "",
            ownIdentification = null,
            photos = emptyList(),
            syncState = LogSyncState.Draft,
        )
    }
}
