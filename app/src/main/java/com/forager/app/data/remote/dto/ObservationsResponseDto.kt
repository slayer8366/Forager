package com.forager.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the response shape of GET /v1/observations.
 * https://api.inaturalist.org/v1/docs/#!/Observations/get_observations
 */
@Serializable
data class ObservationsResponseDto(
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("page") val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("results") val results: List<ObservationDto> = emptyList(),
)

@Serializable
data class ObservationDto(
    @SerialName("id") val id: Long,
    @SerialName("taxon") val taxon: TaxonDto,
    /** "lat,lng" if present. Null or absent when iNaturalist withholds the location entirely (`geoprivacy`/`taxon_geoprivacy` = "private"). */
    @SerialName("location") val location: String? = null,
    @SerialName("observed_on") val observedOn: String? = null,
    @SerialName("photos") val photos: List<ObservationPhotoDto> = emptyList(),
    /**
     * iNaturalist's own precomputed "is [location] the true position, or a randomized stand-in."
     * True whenever the observer's own [geoprivacy] or the taxon's [taxonGeoprivacy] obscures the
     * public coordinates — confirmed live (`api.inaturalist.org/v1/observations?geoprivacy=obscured`):
     * `location` is still present and parses as a valid "lat,lng" when `obscured=true`, just
     * randomized to a coarse cell tens of kilometres across, not withheld like the fully-private
     * case above. This is the field [INaturalistMushroomRepository] gates exclusion on — see its
     * `toDomain(ObservationDto)` doc comment for why, not [geoprivacy]/[taxonGeoprivacy] directly.
     * Defaults to `true` (exclude) on the off chance a future response omits this field, rather
     * than `false`: silently treating an unrecognised shape as "safe to plot" is exactly the
     * fabricated-plausible-value failure this field exists to prevent.
     */
    @SerialName("obscured") val obscured: Boolean = true,
    /**
     * The observer's own geoprivacy choice for this observation: `null` (open), `"obscured"`, or
     * `"private"`. Kept on this DTO for traceability even though [obscured] (not this field or
     * [taxonGeoprivacy] individually) is what the repository actually gates on — confirmed live
     * that [taxonGeoprivacy] can itself be the literal string `"open"` rather than only null, so
     * treating "this field is non-null" as "obscured" would be wrong.
     */
    @SerialName("geoprivacy") val geoprivacy: String? = null,
    /** Geoprivacy forced by the taxon itself (e.g. a globally sensitive species), independent of the observer's own [geoprivacy]. See [geoprivacy]'s doc comment. */
    @SerialName("taxon_geoprivacy") val taxonGeoprivacy: String? = null,
    /**
     * Positional accuracy, in metres, of the location iNaturalist actually shows publicly — i.e.
     * of [location] itself, not of whatever true position it may be standing in for. Deliberately
     * not `positional_accuracy`: confirmed live that for an obscured observation,
     * `positional_accuracy` reflects the observer's original (true, hidden) GPS reading — a small
     * number, tens of metres — while `public_positional_accuracy` reflects the coarse cell
     * actually shown, tens of kilometres. Using the wrong one would understate an obscured point's
     * real uncertainty by three orders of magnitude. For a non-obscured observation the two fields
     * are identical (confirmed live, sampled), so this is the correct field in both cases, not a
     * tradeoff. Null when iNaturalist has no accuracy figure at all, distinct from any accuracy
     * value including zero.
     */
    @SerialName("public_positional_accuracy") val publicPositionalAccuracy: Int? = null,
)

@Serializable
data class ObservationPhotoDto(
    @SerialName("url") val url: String? = null,
)
