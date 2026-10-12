package com.zynergylabs.forager.app.domain

/**
 * What the sundown alerts did, for a person to read afterwards (dispatch 2026-09-28-796; the owner,
 * RECORD -797: "Yes, same fix (Recommended)"), the same logging as [BackByRecord]: each evaluation and
 * what woke it, each alert with its delivery, and the wake-up alarm at the next alert's due time. The
 * file is `FileSundownRecord`. **No positions, ever**, and no sunset or walk-back figures beyond the
 * due time the alarm is set for.
 */
fun interface SundownRecord {
    /** Records [event]. Never throws. */
    fun write(event: SundownRecordEvent)
}

object NoSundownRecord : SundownRecord {
    override fun write(event: SundownRecordEvent) = Unit
}

sealed interface SundownRecordEvent {
    /** One evaluation with a position, and what made it happen. */
    data class Evaluated(val trackId: String, val trigger: EvaluationTrigger) : SundownRecordEvent

    /** [alert] was handed to delivery; [outcome] what delivery reported. */
    data class Fired(val trackId: String, val alert: SundownAlert, val outcome: AlertDeliveryOutcome?) : SundownRecordEvent

    /** A wake-up was asked for at [atMillis], [alert]'s due time; [accepted] false when the platform refused it. */
    data class AlarmScheduled(val trackId: String, val alert: SundownAlert, val atMillis: Long, val accepted: Boolean) : SundownRecordEvent

    /** The wake-up was cancelled: nothing more is due (all given, alerts off, arrived, or the recording ended). */
    data class AlarmCancelled(val trackId: String) : SundownRecordEvent

    /** The wake-up arrived. [trackId] null when nothing was being watched by then. */
    data class AlarmDelivered(val trackId: String?) : SundownRecordEvent
}
