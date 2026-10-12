package com.zynergylabs.forager.app.domain

/**
 * What Back by did, for a person to read afterwards (dispatch 2026-09-28-796). On the L3 walk Back by
 * was armed and did not fire, and nothing anywhere said whether it had been set, evaluated, fired or
 * ended (RECORD -793 to -795): the time lived in memory only. One entry per event, in the style of
 * [ReturnRecord]; the file is `FileBackByRecord`. **No positions, ever**: arrival is recorded as a
 * reason, never as where.
 */
fun interface BackByRecord {
    /** Records [event]. Never throws: a failure to record must not reach the recording. */
    fun write(event: BackByRecordEvent)
}

/** Records nothing: for tests and anything built without the file. */
object NoBackByRecord : BackByRecord {
    override fun write(event: BackByRecordEvent) = Unit
}

sealed interface BackByRecordEvent {
    /** The service began watching [trackId]: a recording started. [keptAtMillis] is a time set a moment before, kept. */
    data class Started(val trackId: String, val keptAtMillis: Long?) : BackByRecordEvent

    /** A time was set from the menu. [accepted] false when another recording was being watched ([watchedTrackId]). */
    data class Set(val trackId: String, val atMillis: Long, val accepted: Boolean, val watchedTrackId: String?) : BackByRecordEvent

    /** A choice from the menu with no recording on the screen: nothing set (TrackRecordingViewModel.setBackBy). */
    data object SetWithNoRecording : BackByRecordEvent

    /** "+30 min" on the alert: the time moved to [atMillis]. */
    data class Later(val trackId: String, val atMillis: Long) : BackByRecordEvent

    /**
     * One evaluation by the service while a time is set, at the moment of the entry: [due] whether the
     * time had come. Recorded so a device check can see how often the service actually evaluated:
     * the walk's question was whether it evaluated at all.
     */
    data class Evaluated(val trackId: String, val atMillis: Long, val due: Boolean, val trigger: EvaluationTrigger) : BackByRecordEvent

    /** The alert for the time [atMillis] was handed to delivery; [outcome] what delivery reported. */
    data class Fired(val trackId: String, val atMillis: Long, val outcome: AlertDeliveryOutcome?) : BackByRecordEvent

    /** A wake-up was asked for at [atMillis] (dispatch 2026-09-28-796, RECORD -797); [accepted] false when the platform refused it. */
    data class AlarmScheduled(val trackId: String, val atMillis: Long, val accepted: Boolean) : BackByRecordEvent

    /** The wake-up was cancelled: the reminder ended. */
    data class AlarmCancelled(val trackId: String) : BackByRecordEvent

    /** The wake-up arrived. [trackId] null when nothing was being watched by then (a process started fresh for it). */
    data class AlarmDelivered(val trackId: String?) : BackByRecordEvent

    /** The reminder ended, for [reason]. */
    data class Ended(val trackId: String, val reason: BackByEndReason) : BackByRecordEvent
}

/** What made an evaluation happen: the service's 15 s timer, a GPS fix, or a wake-up alarm. Shared with the sundown record. */
enum class EvaluationTrigger { TIMER, FIX, ALARM }

enum class BackByEndReason { ARRIVAL, IM_BACK, CLEAR, STOP, SERVICE_DESTROYED }
