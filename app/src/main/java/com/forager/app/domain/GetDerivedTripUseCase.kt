package com.forager.app.domain

import com.forager.app.domain.model.DerivedTrip
import java.time.LocalDate
import java.time.ZoneId

/**
 * Assembles one local day's derived trip — Journal Stage 2a. Gathers [date]'s finds, tracks,
 * waypoints, and downloaded offline regions via each repository's own day-scoped read
 * ([MushroomLogRepository.getForDay]/[TrackRepository.getForDay]/[WaypointRepository.getForDay]/
 * [OfflineRegionDayIndex.getRegionsCreatedOn]), all scoped from the same [LocalDayRange] so every
 * one of the four reads agrees on exactly which local day they're answering for.
 *
 * **Computes nothing new, persists nothing** — see [DerivedTrip]'s own doc comment. This is a thin
 * fetch-and-assemble seam, the same shape [GetSeasonalPatternUseCase] is for its own two-source
 * fetch, just widened to four independent reads instead of two.
 *
 * **No draft filtering on [DerivedTrip.finds].** [GetMushroomLogEntriesUseCase] excludes
 * [com.forager.app.domain.model.MushroomLogEntry.isDraft] rows for "the log" (owner decision:
 * "a draft never appears in the log") — but that's a *display* policy for one particular screen, not
 * a fact about what happened on this date, and this dispatch is explicit that entries/drafts
 * curation is Stage 2b's call, not 2a's. Whether a derived trip's own report should apply the same
 * filter is exactly the kind of decision this dispatch was told not to make (`docs/plans` — "the
 * trip report surface... is 2b"), so every entry [MushroomLogRepository.getForDay] returns for
 * [date] is passed through unfiltered, draft and committed alike.
 *
 * **Fails on the first repository error**, same short-circuit shape [GetSeasonalPatternUseCase]
 * uses — a derived trip with silently-missing tracks because the track read failed would misreport
 * what happened that day, which is worse than reporting the whole assembly as failed.
 */
class GetDerivedTripUseCase(
    private val mushroomLogRepository: MushroomLogRepository,
    private val trackRepository: TrackRepository,
    private val waypointRepository: WaypointRepository,
    private val offlineRegionDayIndex: OfflineRegionDayIndex,
) {
    /** [zone] defaults to the device's current zone — see [LocalDayRange.of]'s own doc comment for why a test would override it. */
    suspend operator fun invoke(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Result<DerivedTrip> {
        val range = LocalDayRange.of(date, zone)

        val finds = mushroomLogRepository.getForDay(range.foundOnKey).getOrElse { return Result.failure(it) }
        val tracks = trackRepository.getForDay(range.epochMillisStartInclusive, range.epochMillisEndExclusive)
            .getOrElse { return Result.failure(it) }
        val waypoints = waypointRepository.getForDay(range.epochMillisStartInclusive, range.epochMillisEndExclusive)
            .getOrElse { return Result.failure(it) }
        val offlineRegions = offlineRegionDayIndex.getRegionsCreatedOn(range.epochMillisStartInclusive, range.epochMillisEndExclusive)
            .getOrElse { return Result.failure(it) }

        return Result.success(
            DerivedTrip(
                date = date,
                finds = finds,
                tracks = tracks,
                waypoints = waypoints,
                offlineRegions = offlineRegions,
            ),
        )
    }
}
