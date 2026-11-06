package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.remote.INaturalistApi
import com.zynergylabs.forager.app.data.remote.dto.ObservationDto
import com.zynergylabs.forager.app.data.remote.dto.SpeciesCountDto
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import java.time.LocalDate
import java.time.format.DateTimeParseException

class INaturalistMushroomRepository(
    private val api: INaturalistApi,
    /**
     * Where malformed positions and dates are counted (dispatch 2026-09-28-658, D6). `AppContainer`
     * passes the real, `Log.w`-backed one; the default is for tests that do not look at it.
     */
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
) : MushroomRepository {

    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>> {
        val query = filter.toQuery()
        return runCatchingCancellable {
            api.getSpeciesCounts(
                lat = region.lat,
                lng = region.lng,
                radiusKm = region.radiusKm,
                month = month,
                iconicTaxa = query.iconicTaxa,
                taxonId = query.taxonId,
                withoutTaxonId = query.withoutTaxonId,
            )
        }.map { response -> response.results.map(::toDomain) }
    }

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> {
        val query = filter.toQuery()
        return runCatchingCancellable {
            api.getObservations(
                lat = region.lat,
                lng = region.lng,
                radiusKm = region.radiusKm,
                month = month,
                iconicTaxa = query.iconicTaxa,
                taxonId = query.taxonId,
                withoutTaxonId = query.withoutTaxonId,
            )
        }.map { response ->
            val sightings = response.results.mapNotNull(::toDomain)
            logMalformed(response.results, sightings.size)
            SightingsPage(
                sightings = sightings,
                totalResults = response.totalResults,
            )
        }
    }

    /**
     * Counts, for the log, what [toDomain] dropped or emptied because iNaturalist sent something that
     * did not parse, kept apart from what it legitimately left out (dispatch 2026-09-28-658, D6). A
     * missing `location` (private geoprivacy) and an obscured one are recorded exclusions, not
     * counted here; a `location` that is present but malformed or out of range drops the sighting,
     * and an `observed_on` that is present but malformed leaves a kept sighting with no date. One
     * line per page, only when either count is above zero. `totalResults` is the API's total and
     * still includes the dropped ones.
     */
    private fun logMalformed(results: List<ObservationDto>, kept: Int) {
        val badPositions = results.count { !it.obscured && it.location != null && parseLocation(it.location) == null }
        val badDates = results.count { dto ->
            !dto.obscured && parseLocation(dto.location) != null && dto.observedOn != null && parseObservedOn(dto.observedOn) == null
        }
        if (badPositions == 0 && badDates == 0) return
        val message = "Of ${results.size} iNaturalist observation(s), $badPositions dropped for a malformed position " +
            "and $badDates kept with no date for a malformed date; $kept shown."
        errorLog.w(TAG, message, MalformedObservationsException(message))
    }

    private fun toDomain(dto: SpeciesCountDto): SpeciesObservationCount {
        val taxon = dto.taxon
        return SpeciesObservationCount(
            taxonId = taxon.id,
            scientificName = taxon.name,
            commonName = taxon.preferredCommonName,
            rank = taxon.rank,
            observationCount = dto.count,
            photoUrl = taxon.defaultPhoto?.mediumUrl ?: taxon.defaultPhoto?.squareUrl,
            wikipediaUrl = taxon.wikipediaUrl,
        )
    }

    /**
     * Null when iNaturalist has no plottable, non-obscured position for this observation, rather
     * than a fabricated or randomized one.
     *
     * Two independent reasons to drop an observation here:
     * - `location` missing/malformed — iNaturalist withheld the coordinates entirely (fully
     *   `private` geoprivacy).
     * - [ObservationDto.obscured] — iNaturalist *did* return a `location`, but it's a coordinate
     *   randomized to a coarse cell tens of kilometres across, not the true point (`obscured`
     *   geoprivacy, set by the observer or forced by the taxon). Checked directly rather than by
     *   inspecting [ObservationDto.geoprivacy]/[ObservationDto.taxonGeoprivacy] ourselves — see
     *   [ObservationDto.obscured]'s own doc comment for why `obscured` is the one field that
     *   already combines both correctly.
     *
     * Exclusion, not fallback: an obscured observation's [ObservationDto.location] is never used
     * for anything, even as a rough estimate.
     */
    private fun toDomain(dto: ObservationDto): Sighting? {
        if (dto.obscured) return null
        val (lat, lng) = parseLocation(dto.location) ?: return null
        val taxon = dto.taxon
        return Sighting(
            observationId = dto.id,
            taxonId = taxon.id,
            scientificName = taxon.name,
            commonName = taxon.preferredCommonName,
            lat = lat,
            lng = lng,
            observedOn = parseObservedOn(dto.observedOn),
            photoUrl = dto.photos.firstOrNull()?.url,
            positionalAccuracyMeters = dto.publicPositionalAccuracy,
        )
    }

}

/** What [INaturalistMushroomRepository] logs malformed observations with: [ErrorLog] takes a throwable, and this one carries the counts. */
internal class MalformedObservationsException(message: String) : Exception(message)

private const val TAG = "INaturalistRepository"

/** Parses iNaturalist's "lat,lng" location string. Null on missing or malformed input. */
internal fun parseLocation(raw: String?): Pair<Double, Double>? {
    if (raw == null) return null
    val parts = raw.split(",")
    if (parts.size != 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lng = parts[1].trim().toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

/** Parses iNaturalist's "YYYY-MM-DD" observed_on string. Null on missing or malformed input. */
internal fun parseObservedOn(raw: String?): LocalDate? {
    if (raw == null) return null
    return try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        null
    }
}
