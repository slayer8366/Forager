package com.forager.app.data.repository

import com.forager.app.data.remote.INaturalistApi
import com.forager.app.data.remote.dto.SpeciesCountDto
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount

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
}
