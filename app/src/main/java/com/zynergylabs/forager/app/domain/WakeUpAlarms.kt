package com.zynergylabs.forager.app.domain

/**
 * A wake-up at a set time, even in Doze (dispatch 2026-09-28-796; the owner, RECORD -797: "Check on GPS +
 * wake-up alarm (Recommended)"). Back by and the sundown alerts were evaluated only on the recording
 * service's 15 s coroutine timer, which counts only time the processor is awake; with the screen off the
 * S22 sleeps, and on the L3 walk Back by never posted. Each watch now also asks for a wake-up at its
 * next due time. The Android side is `AndroidWakeUpAlarms`: an **inexact** allow-while-idle alarm, so no
 * exact-alarm permission (the reason -646 chose the timer); it may be minutes late in deep Doze, never
 * missed. Scheduling an alarm again replaces it; there is one per [WakeUpAlarm].
 */
interface WakeUpAlarms {
    /** Wakes [alarm]'s watch at or after [atEpochMillis]. `false` when the platform refused it (logged by the implementation). */
    fun schedule(alarm: WakeUpAlarm, atEpochMillis: Long): Boolean

    fun cancel(alarm: WakeUpAlarm)
}

enum class WakeUpAlarm { BACK_BY, SUNDOWN }

/** No alarms: for tests and anything built without the platform. */
object NoWakeUpAlarms : WakeUpAlarms {
    override fun schedule(alarm: WakeUpAlarm, atEpochMillis: Long) = false
    override fun cancel(alarm: WakeUpAlarm) = Unit
}
