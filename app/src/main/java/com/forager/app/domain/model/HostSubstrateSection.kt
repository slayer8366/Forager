package com.forager.app.domain.model

/** Coniferous/deciduous/mixed makeup of the surrounding forest. */
enum class ForestType(val label: String) {
    CONIFEROUS("Coniferous"),
    DECIDUOUS("Deciduous"),
    MIXED("Mixed"),
}

/** Condition of the host tree, where [Association] is [Association.Mycorrhizal] or [Association.DeadWood]. */
enum class HostHealth(val label: String) {
    HEALTHY("Healthy"),
    DISEASED("Diseased"),
    DYING("Dying"),
    DEAD("Dead"),
}

/**
 * What the specimen was growing on/with. [hostSpecies] on [Mycorrhizal] and [DeadWood] is free
 * text, not an enum — tree taxonomy is not a closed list this app should own, the same reasoning
 * [TaxonFilter] applies to searched species names.
 */
sealed interface Association {
    data class Mycorrhizal(val hostSpecies: String) : Association
    data class DeadWood(val hostSpecies: String) : Association
    data object SoilOrLitter : Association
    data object Dung : Association
    data class Other(val text: String) : Association
}

/**
 * Host and substrate context. [association] is [Observed] because "what was it growing on" is a
 * question that always has an answer once looked at, even if the answer is [Association.SoilOrLitter].
 * [forestType]/[hostHealth] describe the surroundings and the host respectively, independent of
 * which [Association] applies.
 */
data class HostSubstrateSection(
    val association: Observed<Association>,
    val forestType: Observed<ForestType>,
    val hostHealth: Observed<HostHealth>,
    val notes: String,
) {
    companion object {
        val EMPTY = HostSubstrateSection(
            association = Observed.NotObserved,
            forestType = Observed.NotObserved,
            hostHealth = Observed.NotObserved,
            notes = "",
        )
    }
}
