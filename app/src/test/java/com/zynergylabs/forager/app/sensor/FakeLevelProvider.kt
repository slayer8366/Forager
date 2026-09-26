package com.zynergylabs.forager.app.sensor

import com.zynergylabs.forager.app.domain.LevelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The [LevelProvider] every camera test drives the level with. [tilt] sets the roll, and
 * [collectors] says how many are listening, which is how a test sees that the level's sensor
 * registration starts and stops with what shows it (the real provider registers per collector).
 */
internal class FakeLevelProvider(initial: Float? = 0f) : LevelProvider {
    private val state = MutableStateFlow(initial)
    override val roll: StateFlow<Float?> = state

    /** How many are collecting [roll] right now. */
    val collectors: Int get() = state.subscriptionCount.value

    fun tilt(degrees: Float?) {
        state.value = degrees
    }
}
