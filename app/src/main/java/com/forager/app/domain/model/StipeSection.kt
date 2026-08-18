package com.forager.app.domain.model

/** Where the stipe attaches relative to the cap centre. Only meaningful for [StipeDetails.Present]. */
enum class StipePosition(val label: String) {
    CENTRAL("Central"),
    ECCENTRIC("Eccentric"),
    LATERAL("Lateral"),
}

/** What's inside the stipe when cut. Only meaningful for [StipeDetails.Present]. */
enum class StipeInterior(val label: String) {
    HOLLOW("Hollow"),
    STUFFED("Stuffed"),
    SOLID("Solid"),
    FIBROUS("Fibrous"),
}

/** The shape of the stipe's base. Only meaningful for [StipeDetails.Present]. */
enum class StipeBase(val label: String) {
    BULBOUS("Bulbous"),
    RADICATING("Radicating"),
    ABRUPT("Abrupt"),
    POINTED("Pointed"),
}

/**
 * Whether a stipe exists, and if so what it looks like.
 *
 * [Absent] is itself a real, recordable finding — some genera have no stipe at all, and that's a
 * diagnostic fact, not a gap. [Present]'s [position]/[interior]/[base] only apply once a stipe is
 * known to exist, so — the same inapplicability rule as [HymenophoreDetails] — they live only
 * inside [Present]: an entry recorded as [Absent] has nowhere to wrongly carry an interior or a
 * base.
 */
sealed interface StipeDetails {
    data object Absent : StipeDetails

    data class Present(
        val position: Observed<StipePosition>,
        val interior: Observed<StipeInterior>,
        val base: Observed<StipeBase>,
    ) : StipeDetails
}

/** The stipe section of an entry: [details] is [Observed] because whether a stipe exists at all is itself something that has to be looked at. */
data class StipeSection(
    val details: Observed<StipeDetails>,
    val notes: String,
) {
    companion object {
        val EMPTY = StipeSection(details = Observed.NotObserved, notes = "")
    }
}
