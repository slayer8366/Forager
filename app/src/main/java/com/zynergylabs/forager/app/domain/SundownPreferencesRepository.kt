package com.zynergylabs.forager.app.domain

/**
 * The two settings the sundown countdown reads, owned behind an interface the same way
 * [MapPreferencesRepository] and [UnitSystemPreferenceRepository] are. Backed by DataStore rather
 * than Room: these are scalar preferences, not rows to query.
 *
 * Its own repository rather than two more keys on [MapPreferencesRepository], because this project
 * keeps one repository per concern, each with its own file (`map_preferences`,
 * `distance_unit_preferences`, `app_theme_preferences`), and because CLAUDE.md's rule is that new
 * capability is a new path rather than a conditional threaded into working code.
 */
interface SundownPreferencesRepository {

    /**
     * How long before sunset the turnaround moment sits, in **minutes**.
     *
     * Minutes rather than milliseconds because this is the number a person sets and reads back in
     * a bug report; the conversion to millis happens once, at the
     * [ComputeSundownCountdownUseCase] boundary, rather than being scattered.
     *
     * **It is a darkness margin, not a lead time**, and the name is load-bearing. Sunset is not
     * when the light runs out: under canopy or west of a ridge, useful light ends meaningfully
     * earlier, and this margin covers that. A separate allowance for how long the walk back takes
     * will land beside it once the return estimate is validated. Keeping them named apart is what
     * stops the two silently merging into one number that double-counts, which
     * [returnWalkingTime]'s own header warns about: "a padded input plus a margin double-counts by
     * an amount nobody could name."
     *
     * Not clamped here. An absurd value renders as a turnaround already in the past, which is the
     * honest reading of that setting; a range belongs in the control that offers it, not as a
     * second opinion in this layer that would eventually disagree with the first.
     */
    suspend fun getDarknessMarginMinutes(): Result<Int>

    suspend fun setDarknessMarginMinutes(minutes: Int): Result<Unit>

    /**
     * Whether the turnaround and sunset alerts are posted.
     *
     * Defaults to **on**, on the owner's ruling: to a user, a safety feature disabled by default
     * is indistinguishable from one that does not exist. Opt out, not opt in.
     *
     * This gates the notifications only. The on-screen countdown is passive and is always shown,
     * because nothing is interrupted by a line of text someone chose to look at.
     */
    suspend fun getAlertsEnabled(): Result<Boolean>

    suspend fun setAlertsEnabled(enabled: Boolean): Result<Unit>
}

/**
 * One hour, the owner's default.
 *
 * Not derived from data, and deliberately so: nothing in this project measures how much earlier
 * darkness arrives under cover, and the one input that could eventually sharpen it, the walk-back
 * estimate, has no production caller yet. It is a stated, editable starting point rather than a
 * fitted parameter, in the same spirit as [FruitingPatternAssumptions]' labelled assumptions.
 */
const val DEFAULT_DARKNESS_MARGIN_MINUTES: Int = 60
