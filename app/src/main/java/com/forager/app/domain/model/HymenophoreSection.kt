package com.forager.app.domain.model

/** How the gills meet the stipe. Only meaningful for [HymenophoreDetails.Gills] — see that type's doc comment. */
enum class GillAttachment(val label: String) {
    FREE("Free"),
    ADNATE("Adnate"),
    ADNEXED("Adnexed"),
    DECURRENT("Decurrent"),
    SINUATE("Sinuate"),
}

/** How closely spaced the gills are. Only meaningful for [HymenophoreDetails.Gills]. */
enum class GillSpacing(val label: String) {
    CROWDED("Crowded"),
    CLOSE("Close"),
    DISTANT("Distant"),
}

/** The texture of the gill edge. Only meaningful for [HymenophoreDetails.Gills]. */
enum class GillEdge(val label: String) {
    SMOOTH("Smooth"),
    SERRATED("Serrated"),
}

/**
 * What's on the underside of the cap, and — for gills specifically — the details that only make
 * sense once you know it's gills. This is the type the whole "inapplicability" rule is written
 * for: [Gills.attachment]/[Gills.spacing]/[Gills.edge] have no meaning for a specimen with pores,
 * teeth, or a smooth underside, so rather than adding those fields to every hymenophore and hoping
 * nobody fills them in for the wrong kind, they exist *only* inside [Gills]. An entry recorded as
 * [Pores] has no field a gill attachment could be written into — the type system enforces it, not a
 * validation check that runs after the fact.
 */
sealed interface HymenophoreDetails {
    data class Gills(
        val attachment: Observed<GillAttachment>,
        val spacing: Observed<GillSpacing>,
        val edge: Observed<GillEdge>,
    ) : HymenophoreDetails

    data object Pores : HymenophoreDetails
    data object Teeth : HymenophoreDetails
    data object SmoothOrWrinkled : HymenophoreDetails
}

/** The hymenophore section of an entry: [details] is [Observed] because every specimen has *some* hymenophore once looked at. */
data class HymenophoreSection(
    val details: Observed<HymenophoreDetails>,
    val notes: String,
) {
    companion object {
        val EMPTY = HymenophoreSection(details = Observed.NotObserved, notes = "")
    }
}
