package com.forager.app.domain

/**
 * Whether the map draws in night mode, and how a manual override interacts with the automatic
 * civil-twilight decision from [CivilTwilight].
 *
 * Pure state, no Android, no clock of its own — the caller supplies what the sun says and this
 * decides what the map should do about it.
 *
 * ## The override is a hold, not a preference
 *
 * Long-pressing the map's render-mode control overrides the automatic decision **for the current
 * conditions**, and releases itself the next time those conditions change. Standing under heavy
 * canopy at 4pm, or stepping into a lit hut at dusk, is a reason to disagree with the sun right
 * now; it is not a reason to disagree with it tomorrow.
 *
 * That is why [NightModeHold] records what the automatic value *was* when the hold was placed. Once
 * the automatic value moves, the hold no longer refers to the situation it was placed in, and
 * [resolve] drops it.
 *
 * **Deliberately not persisted.** A hold whose whole meaning is "not right now, thanks" should not
 * survive a process death and resurrect hours later against conditions that have changed — and
 * `CLAUDE.md`'s DataStore guidance is for settings, which this is not. If a persistent
 * "always dark maps" preference is ever wanted, that is a different feature with a different
 * control, and it should not be built speculatively on top of this one.
 */
object MapNightMode {

    /**
     * A manual override of the automatic decision.
     *
     * [nightWanted] is what the user asked for. [automaticWhenPlaced] is what the sun said at that
     * moment, and is the thing that expires the hold.
     */
    data class NightModeHold(
        val nightWanted: Boolean,
        val automaticWhenPlaced: Boolean,
    )

    /**
     * What the map should draw, given the automatic decision and any outstanding hold.
     *
     * A hold placed while the sun said the same thing it says now still applies. A hold placed
     * under a different automatic value has expired, and the automatic value wins.
     */
    fun resolve(automaticNight: Boolean, hold: NightModeHold?): Boolean = when {
        hold == null -> automaticNight
        hold.automaticWhenPlaced != automaticNight -> automaticNight
        else -> hold.nightWanted
    }

    /**
     * The hold after the user long-presses, given the automatic decision and any current hold.
     *
     * Toggles what is currently on screen. Returns `null` — no hold at all — when the toggle lands
     * back on what the sun already wanted, so that pressing twice returns to automatic rather than
     * leaving a hold that happens to agree. Without that, the control would have a state the user
     * cannot see and cannot get out of.
     */
    fun toggled(automaticNight: Boolean, hold: NightModeHold?): NightModeHold? {
        val wanted = !resolve(automaticNight, hold)
        return if (wanted == automaticNight) {
            null
        } else {
            NightModeHold(nightWanted = wanted, automaticWhenPlaced = automaticNight)
        }
    }

    /**
     * Whether a hold is currently doing anything, for UI that wants to show the control as
     * overridden. A hold that has expired reads as no hold, the same way [resolve] treats it.
     */
    fun isHeld(automaticNight: Boolean, hold: NightModeHold?): Boolean =
        hold != null && hold.automaticWhenPlaced == automaticNight
}
