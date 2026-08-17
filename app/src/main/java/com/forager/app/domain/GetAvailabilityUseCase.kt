package com.forager.app.domain

import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * What a search returned, and whether it came off the network or out of the cache.
 *
 * A sealed type rather than a nullable timestamp on one class, because the two cases are read
 * differently by the UI and must not be renderable by accident as the same thing: CLAUDE.md
 * requires a fallback result to be reported as a fallback, so [Cached] carries the age the List
 * tab's offline banner states out loud.
 */
sealed interface AvailabilitySearchResult {
    data class Live(val forecast: AvailabilityForecast) : AvailabilitySearchResult
    data class Cached(val forecast: AvailabilityForecast, val cachedAtEpochMillis: Long) : AvailabilitySearchResult
}

/**
 * Runs a ranked-species search, falling back to the local cache when the live one fails.
 *
 * Order of business, and none of it is conditional on a connectivity check — this app has no
 * reliable way to ask "is there internet" that is better than trying:
 *
 * 1. Ask [predictAvailability]. On success, write the result through to [cache] and return
 *    [AvailabilitySearchResult.Live].
 * 2. On failure, look for a cached result for that exact region+month+filter. If there is one,
 *    return [AvailabilitySearchResult.Cached] with the age the UI will label it with.
 * 3. If there is not, return **the original failure, unchanged**. The cache miss is not reported
 *    in its place and no substitute error is invented: what the user needs to see is why the
 *    search failed (CLAUDE.md — failures are reported, never swallowed or replaced with a
 *    plausible value).
 *
 * A new class rather than a cache branch threaded into [PredictAvailabilityUseCase] or into
 * `AvailabilityViewModel.refresh()`'s existing `onFailure` arm, per CLAUDE.md: new capability is a
 * new path. [PredictAvailabilityUseCase] is untouched and still means exactly "ask iNaturalist and
 * rank the answer", which is what its own tests pin down.
 *
 * Pure Kotlin and framework-free, so the whole live/cached/failed decision is unit-testable
 * headless — see `GetAvailabilityUseCaseTest`.
 */
class GetAvailabilityUseCase(
    private val predictAvailability: PredictAvailabilityUseCase,
    private val cache: SearchCacheRepository,
) {
    suspend operator fun invoke(
        region: Region,
        month: Int,
        filter: TaxonFilter,
    ): Result<AvailabilitySearchResult> = predictAvailability(region, month, filter).fold(
        onSuccess = { forecast ->
            // Write-through, not a separate "save this" call the ViewModel could forget to make.
            // save() is best-effort by contract (see SearchCacheRepository) precisely so a storage
            // problem here cannot turn a successful live search into a failed one.
            cache.save(forecast)
            Result.success(AvailabilitySearchResult.Live(forecast))
        },
        onFailure = { error ->
            val cached = cache.getCached(region, month, filter)
            if (cached == null) {
                Result.failure(error)
            } else {
                Result.success(
                    AvailabilitySearchResult.Cached(
                        forecast = cached.forecast,
                        cachedAtEpochMillis = cached.cachedAtEpochMillis,
                    ),
                )
            }
        },
    )
}
