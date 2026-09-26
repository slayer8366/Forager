package com.zynergylabs.forager.app.photo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The self-timer's settings (decision B8 / Closed decision C, 2026-09-26): Off, 3 s, 10 s. Held
 * for one camera session only, by the camera screen; nothing stores it.
 */
internal enum class TimerMode(val seconds: Int) { Off(0), ThreeSeconds(3), TenSeconds(10) }

/** What a tap on the Timer chip asks for next: Off, 3 s, 10 s, and back to Off. */
internal fun TimerMode.next(): TimerMode = when (this) {
    TimerMode.Off -> TimerMode.ThreeSeconds
    TimerMode.ThreeSeconds -> TimerMode.TenSeconds
    TimerMode.TenSeconds -> TimerMode.Off
}

/**
 * One self-timer countdown: shows the remaining whole seconds and calls its zero action once,
 * when they run out. Plain Kotlin on purpose — no Compose, no Android UI — so it runs headless
 * under virtual time (`CaptureCountdownTest`).
 *
 * **Where it lives, and why not the ViewModel** (decision B8, departing from the abandoned
 * 2026-09-26-02 plan): the camera screen holds one, with `remember`, on its own
 * `rememberCoroutineScope()`. `InAppCameraViewModel` is Activity-scoped and is not passed to the
 * screen, so a countdown held there would outlive Back unless every exit cancelled it by hand;
 * held here, anything that removes the screen cancels the [scope], and the countdown with it.
 *
 * The zero action runs inside the countdown's own coroutine, after the display has cleared. On
 * the camera screen it is the existing capture path, unchanged.
 */
internal class CaptureCountdown(private val scope: CoroutineScope) {

    private val remaining = MutableStateFlow<Int?>(null)

    /** The whole seconds left, counting down from the start; `null` when no countdown is running. */
    val remainingSeconds: StateFlow<Int?> = remaining.asStateFlow()

    private var job: Job? = null

    /** Whether a countdown is running. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts a countdown of [seconds] whole seconds that calls [onZero] once when it reaches
     * zero. The length is fixed here, so changing the timer while this runs affects the next start
     * only. Starting a second countdown while one runs is a programming error, refused with an
     * exception rather than silently ignored: the camera screen sends a second shutter press to
     * [cancel] instead.
     */
    fun start(seconds: Int, onZero: () -> Unit) {
        require(seconds > 0) { "a countdown needs at least one second, was $seconds" }
        check(!isRunning) { "a countdown is already running" }
        job = scope.launch {
            try {
                for (left in seconds downTo 1) {
                    remaining.value = left
                    delay(ONE_SECOND_MILLIS)
                }
            } catch (cancelled: CancellationException) {
                remaining.value = null
                throw cancelled
            }
            remaining.value = null
            onZero()
        }
    }

    /** Stops a running countdown with no capture, and clears the display. A no-op when none runs. */
    fun cancel() {
        job?.cancel()
        job = null
        remaining.value = null
    }

    private companion object {
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
