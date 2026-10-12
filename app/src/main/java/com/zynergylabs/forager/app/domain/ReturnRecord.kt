package com.zynergylabs.forager.app.domain

/**
 * A lasting record of Return taps and the off-track rule's decisions (dispatch 2026-09-28-451).
 *
 * The owner's walk of 2026-10-03 could not be read afterwards: the app's own log lines before
 * 20:12:10Z had rotated away, and nothing else recorded when Return was tapped
 * (`docs/navigation/2026-10-03-walk-findings-report.md`). This is that record. It changes nothing a
 * walker sees.
 *
 * **No positions:** times, track ids, decisions and reasons only.
 */
fun interface ReturnRecord {
    fun write(event: ReturnRecordEvent)
}

/** The record that keeps nothing: the default, so a watch built without one (in a test) writes nowhere. */
val NoReturnRecord = ReturnRecord { }

/** One entry. [trackId] is the recording it is for. */
sealed interface ReturnRecordEvent {
    val trackId: String

    /** Return was tapped and the watch took it. */
    data class ReturnStarted(override val trackId: String) : ReturnRecordEvent

    /** Return was tapped and refused: the watch is begun for another recording, [watchedTrackId]. */
    data class ReturnRefused(override val trackId: String, val watchedTrackId: String?) : ReturnRecordEvent

    /** A return ended, and why. */
    data class ReturnEnded(override val trackId: String, val reason: ReturnEndReason) : ReturnRecordEvent

    /** The off-track rule decided the walker has gone off the path: the alert. [readingAtMillis] is the deciding reading's own time. */
    data class WentOffTrack(override val trackId: String, val readingAtMillis: Long) : ReturnRecordEvent

    /** The alert's delivery and what it did; [outcome] `null` when the delivery cannot say. */
    data class AlertDelivered(override val trackId: String, val outcome: AlertDeliveryOutcome?) : ReturnRecordEvent

    /** The alert was decided and not delivered: Settings' "Off-track reminder" is off (dispatch 2026-09-28-626). */
    data class AlertWithheld(override val trackId: String) : ReturnRecordEvent

    /** The off-track rule decided the walker is back on the path long enough to alert again. */
    data class ReArmed(override val trackId: String, val readingAtMillis: Long) : ReturnRecordEvent
}

enum class ReturnEndReason {
    /** The walker ended it: the HUD's exit, or Return toggled off. */
    BY_WALKER,

    /** The recording was stopped. */
    RECORDING_STOPPED,

    /** The recording service was destroyed with a recording begun. */
    SERVICE_DESTROYED,

    /**
     * The walker reached the start (RECORD -844, -845): the return ended by itself and the recording
     * carries on. Written to `return-record.log` as `reason=arrival`.
     */
    ARRIVAL,
}
