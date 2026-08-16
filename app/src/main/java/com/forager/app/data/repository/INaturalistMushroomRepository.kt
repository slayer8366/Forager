package com.forager.app.data.repository

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.ObservationDto
import com.forager.app.data.remote.dto.SpeciesCountDto
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SpeciesObservationCount
import java.time.LocalDate
import java.time.format.DateTimeParseException

class INaturalistMushroomRepository(
    private val api: INaturalistApi,
) : MushroomRepository {

    override suspend fun getSpeciesCounts(region: Region, month: Int): Result<List<SpeciesObservationCount>> {
        return runCatching {
            api.getSpeciesCounts(
                lat = region.lat,
                lng = region.lng,
                radiusKm = region.radiusKm,
                month = month,
            )
        }.map { response -> response.results.map(::toDomain) }
    }

    override suspend fun getSightings(region: Region, month: Int): Result<List<Sighting>> {
        return runCatching {
            api.getObservations(
                lat = region.lat,
                lng = region.lng,
                radiusKm = region.radiusKm,
                month = month,
            )
        }.map { response -> response.results.mapNotNull(::toDomain) }
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

    /** Null when iNaturalist has no plottable position for this observation, rather than a fabricated one. */
    private fun toDomain(dto: ObservationDto): Sighting? {
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
