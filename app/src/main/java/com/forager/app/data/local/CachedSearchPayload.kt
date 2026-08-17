package com.forager.app.data.local

import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.SpeciesObservationCount
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How a cached search's ranked entries are turned into the single [CachedSearchEntity.entriesJson]
 * string and back.
 *
 * The DTOs are here in `data/local/` and not `@Serializable` annotations bolted onto
 * [AvailabilityEntry] / [SpeciesObservationCount], for the same reason [PlannedTripEntity] is a
 * separate type from `PlannedTrip`: a storage format is the data layer's business, and annotating
 * the domain models would make every future rename of a domain field a silent on-disk format
 * change. This object is the only place the two shapes meet.
 *
 * `kotlinx-serialization-json` is already a direct dependency of this module (see
 * app/build.gradle.kts) and the Kotlin serialization plugin is already applied for the wire DTOs
 * in `data/remote/dto/`, so no new library was added for this.
 */
internal object CachedSearchPayload {

    /**
     * Strict by default — no `ignoreUnknownKeys`, no `isLenient`. Nothing writes this format except
     * [encode] two lines below, and the one way an older shape could be read back is a schema
     * change, which drops the whole table (see [ForagerDatabase]). Leniency here would only turn a
     * genuine "this row is not what we wrote" into a partially-populated result.
     */
    private val json = Json

    fun encode(entries: List<AvailabilityEntry>): String = json.encodeToString(entries.map(::toDto))

    /** Throws if [encoded] is not what [encode] wrote; the caller degrades to "no cache" and logs. */
    fun decode(encoded: String): List<AvailabilityEntry> =
        json.decodeFromString<List<CachedEntryDto>>(encoded).map(::toDomain)

    private fun toDto(entry: AvailabilityEntry) = CachedEntryDto(
        species = CachedSpeciesDto(
            taxonId = entry.species.taxonId,
            scientificName = entry.species.scientificName,
            commonName = entry.species.commonName,
            rank = entry.species.rank,
            observationCount = entry.species.observationCount,
            photoUrl = entry.species.photoUrl,
            wikipediaUrl = entry.species.wikipediaUrl,
        ),
        relativeLikelihood = entry.relativeLikelihood,
    )

    private fun toDomain(dto: CachedEntryDto) = AvailabilityEntry(
        species = SpeciesObservationCount(
            taxonId = dto.species.taxonId,
            scientificName = dto.species.scientificName,
            commonName = dto.species.commonName,
            rank = dto.species.rank,
            observationCount = dto.species.observationCount,
            photoUrl = dto.species.photoUrl,
            wikipediaUrl = dto.species.wikipediaUrl,
        ),
        relativeLikelihood = dto.relativeLikelihood,
    )
}

/**
 * One ranked entry as stored.
 *
 * [relativeLikelihood] is stored rather than recomputed from the counts on read. The trade-off,
 * stated rather than hidden: it is derived data, so a future change to how the ranking normalizes
 * would not reach rows already cached, and they would replay the old figure until they were
 * refetched. Recomputing instead would mean this file owning a copy of the ranking rule that
 * [com.forager.app.domain.PredictAvailabilityUseCase] owns, which is the worse duplication — a
 * cache replaying exactly the list that was on screen is the behaviour being asked for here.
 */
@Serializable
internal data class CachedEntryDto(
    val species: CachedSpeciesDto,
    val relativeLikelihood: Float,
)

/** One species' counts as stored; mirrors [SpeciesObservationCount] field for field. */
@Serializable
internal data class CachedSpeciesDto(
    val taxonId: Long,
    val scientificName: String,
    val commonName: String?,
    val rank: String?,
    val observationCount: Int,
    val photoUrl: String?,
    val wikipediaUrl: String?,
)
