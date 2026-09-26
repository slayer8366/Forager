package com.zynergylabs.forager.app.photo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** STUB. */
internal enum class TimerMode(val seconds: Int) { Off(0), ThreeSeconds(3), TenSeconds(10) }

/** STUB: does not advance. */
internal fun TimerMode.next(): TimerMode = this

/** STUB: never counts. */
internal class CaptureCountdown(@Suppress("unused") private val scope: CoroutineScope) {
    private val remaining = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = remaining
    val isRunning: Boolean get() = false
    fun start(seconds: Int, onZero: () -> Unit) {}
    fun cancel() {}
}
