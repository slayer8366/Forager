package com.forager.app.domain.model

/** Type of ring/skirt remnant left by a partial veil. Only meaningful when [VeilSection.annulus] is [Feature.Present]. */
enum class AnnulusType(val label: String) {
    RING("Ring"),
    SKIRT("Skirt"),
    CORTINA("Cortina (hair-zone)"),
}

/** Type of cup/sack remnant left by a universal veil. Only meaningful when [VeilSection.volva] is [Feature.Present]. */
enum class VolvaType(val label: String) {
    SACK("Sack"),
    CUP("Cup"),
    CONCENTRIC_RINGS("Concentric rings"),
}

/**
 * Veil remnants: the fields the three-state rule exists for. Both [annulus] and [volva] are
 * presence-optional, and both are diagnostic in their absence — a mushroom with no volva is a
 * genuinely different observation from a mushroom whose base was never checked, and only [Feature]
 * (not [Observed]) keeps those distinguishable.
 */
data class VeilSection(
    val annulus: Feature<AnnulusType>,
    val volva: Feature<VolvaType>,
    val notes: String,
) {
    companion object {
        val EMPTY = VeilSection(annulus = Feature.NotObserved, volva = Feature.NotObserved, notes = "")
    }
}
