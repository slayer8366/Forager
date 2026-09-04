package com.forager.app.data.repository

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.ObservationDto
import com.forager.app.data.remote.dto.SpeciesCountDto
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import java.time.LocalDate
import java.time.format.DateTimeParseException

class INaturalistMushroomRepository(
    private val api: INaturalistApi,
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
            SightingsPage(
                sightings = response.results.mapNotNull(::toDomain),
                totalResults = response.totalResults,
            )
        }
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
