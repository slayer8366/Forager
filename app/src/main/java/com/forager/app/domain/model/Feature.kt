package com.forager.app.domain.model

/**
 * A characteristic that may genuinely be absent from the specimen, where the absence itself is a
 * diagnostic observation — a volva, an annulus, latex, bruising, cap decorations. "No volva" and
 * "I didn't check the base" are different facts: the first is a positive finding a dichotomous key
 * consumes, the second is no information at all. Three states, not two, is the whole point of this
 * type — see [Observed] for the sibling type used where absence has no such meaning (a specimen
 * always has *some* cap shape; it does not always have a volva).
 *
 * Using this where [Observed] belongs (or vice versa) is a modeling error: the split exists so an
 * illegal state — "absent" where absence isn't meaningful, or a silently-dropped "didn't check"
 * where it is — has no slot to be written into.
 */
sealed interface Feature<out T> {
    data class Present<T>(val value: T) : Feature<T>
    data object Absent : Feature<Nothing>
    data object NotObserved : Feature<Nothing>
}

/** The present value, or `null` for [Feature.Absent]/[Feature.NotObserved] — for call sites that just want a nullable read. */
fun <T> Feature<T>.valueOrNull(): T? = (this as? Feature.Present<T>)?.value
