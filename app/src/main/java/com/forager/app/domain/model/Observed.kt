package com.forager.app.domain.model

/**
 * A characteristic that necessarily has some value once the part it describes exists and was
 * looked at — cap shape, gill attachment, stipe interior. There is no "absent" case here on
 * purpose: if the part exists, it has *a* shape/attachment/interior, so the only two honest states
 * are "recorded" and "wasn't looked at". See [Feature] for the other kind of characteristic, where
 * absence is itself a real, diagnostic observation.
 *
 * Modeled as its own type rather than `T?` because a nullable field collapses "recorded as X" and
 * "never looked at" into the same `null`, which is exactly the fabricated-plausible-value failure
 * CLAUDE.md forbids — a record whose entire purpose is careful observation cannot afford to treat
 * "I didn't check" as indistinguishable from a value.
 */
sealed interface Observed<out T> {
    data class Recorded<T>(val value: T) : Observed<T>
    data object NotObserved : Observed<Nothing>
}

/** `true` for [Observed.Recorded], `false` for [Observed.NotObserved]. */
val Observed<*>.isRecorded: Boolean get() = this is Observed.Recorded

/** The recorded value, or `null` if [Observed.NotObserved] — for call sites that just want a nullable read. */
fun <T> Observed<T>.valueOrNull(): T? = (this as? Observed.Recorded<T>)?.value
