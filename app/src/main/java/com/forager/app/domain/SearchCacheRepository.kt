package com.forager.app.domain

import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * Local storage for previously-fetched ranked species lists, so a search that has been run before
 * still has an answer with no network.
 *
 * **Only the ranked list is cached.** Not sightings, not foraging areas, and explicitly not
 * Conditions or Trip Windows: those two are "as of today" readings (see
 * [GetConditionsUseCase]'s and
 * [com.forager.app.data.repository.OpenMeteoWeatherProvider]'s doc comments), and replaying a
 * stored rainfall total or forecast offline would present a reading from days ago as the current
 * one. The ranked list has no such problem — it is a summary of years of historical observations,
 * so a copy from last week is the same answer today, and it is labelled with its age on screen
 * either way.
 *
 * **A match is exact equality on region + month + filter**, the same rule
 * [com.forager.app.ui.availability.AvailabilityViewModel]'s in-memory sightings cache already
 * uses. No rounding of coordinates or radius and no fuzzy "close enough" region: a search 400m
 * away is a different search, and quietly answering it with this one's results would be a claim
 * this app cannot support.
 *
 * **Storage failures degrade to "no cache", they do not throw.** A cache is an optimization over
 * an answer that is already correct without it, so letting a Room error out of these methods would
 * let a storage problem masquerade as a failed search — the one thing
 * [GetAvailabilityUseCase] exists to prevent. Implementations report the failure (CLAUDE.md: never
 * silently swallowed — [com.forager.app.data.repository.RoomSearchCacheRepository] logs it) and
 * then behave as though nothing was stored.
 */
interface SearchCacheRepository {
    /** The stored result for this exact search, or null if there is none (or storage failed). */
    suspend fun getCached(region: Region, month: Int, filter: TaxonFilter): CachedAvailability?

    /** Stores [forecast], replacing any previous result for the same search. Best-effort; see above. */
    suspend fun save(forecast: AvailabilityForecast)

    /** Up to 5 most-recently-accessed cached searches, most recent first. */
    suspend fun getRecent(): List<CachedSearchSummary>
}

/**
 * A cached ranked list together with when it was fetched, so the UI can say how old it is rather
 * than showing it as though it were live.
 */
data class CachedAvailability(val forecast: AvailabilityForecast, val cachedAtEpochMillis: Long)

/** One entry in the recent-searches picker: enough to describe a search and to re-run it. */
data class CachedSearchSummary(
    val region: Region,
    val month: Int,
    val filter: TaxonFilter,
    val cachedAtEpochMillis: Long,
)
