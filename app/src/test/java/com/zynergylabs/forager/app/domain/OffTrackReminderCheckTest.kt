package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [OffTrackReminderCheck] on a plain JVM (dispatch 2026-09-28-626; Amendment 1, RECORD -627):
 * "At Record", "Only when truly blocked", "App info page, once per block". Each case drives
 * [OffTrackReminderCheck.atRecordingStart] in sequence, the way consecutive recordings do, and
 * asserts both what it answered and what it stored.
 */
class OffTrackReminderCheckTest {

    private val preferences = InMemoryOffTrackReminderPreferences()
    private val logged = mutableListOf<String>()
    private var phone = BackgroundRun.ALLOWED

    private val check = OffTrackReminderCheck({ phone }, preferences) { _, message, _ -> logged += message }

    private fun recordingStarts(): Boolean = runBlocking { check.atRecordingStart() }

    @Test
    fun `allowed shows nothing, and stores that it was allowed`() {
        assertEquals(false, recordingStarts())
        assertEquals(false, preferences.lastSeenBlocked)
        assertEquals(listOf<String>(), logged)
    }

    @Test
    fun `blocked prompts once, and not again while it stays blocked`() {
        phone = BackgroundRun.BLOCKED
        assertEquals(listOf(true, false, false), List(3) { recordingStarts() })
        assertEquals(true, preferences.lastSeenBlocked)
    }

    @Test
    fun `blocked, then allowed, then blocked again prompts again`() {
        val answers = listOf(BackgroundRun.BLOCKED, BackgroundRun.BLOCKED, BackgroundRun.ALLOWED, BackgroundRun.BLOCKED).map {
            phone = it
            recordingStarts()
        }
        assertEquals(listOf(true, false, false, true), answers)
    }

    @Test
    fun `the reminder off shows nothing and stores nothing, even when blocked`() {
        preferences.enabled = false
        phone = BackgroundRun.BLOCKED
        assertEquals(false, recordingStarts())
        assertEquals(null, preferences.lastSeenBlocked)
    }

    @Test
    fun `a phone too old to say shows nothing and stores nothing`() {
        phone = BackgroundRun.UNSUPPORTED
        assertEquals(false, recordingStarts())
        assertEquals(null, preferences.lastSeenBlocked)
    }

    @Test
    fun `a failed read of the earlier state prompts, and says so in the log`() {
        preferences.failReads = true
        phone = BackgroundRun.BLOCKED
        assertEquals(true, recordingStarts())
        assertEquals(
            listOf(
                "Couldn't read whether the off-track reminder is on; checking as if on, the default.",
                "Couldn't read whether the block was already seen; prompting.",
            ),
            logged,
        )
    }
}

/** The repository in memory. `lastSeenBlocked` is `null` until something is stored. */
internal class InMemoryOffTrackReminderPreferences : OffTrackReminderPreferenceRepository {
    var enabled = true
    var lastSeenBlocked: Boolean? = null
    var failReads = false

    override suspend fun getEnabled(): Result<Boolean> =
        if (failReads) Result.failure(IllegalStateException("read failed")) else Result.success(enabled)

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        this.enabled = enabled
        return Result.success(Unit)
    }

    override fun enabledNow(): Boolean = enabled

    override suspend fun getLastSeenBlocked(): Result<Boolean> =
        if (failReads) Result.failure(IllegalStateException("read failed")) else Result.success(lastSeenBlocked ?: false)

    override suspend fun setLastSeenBlocked(blocked: Boolean): Result<Unit> {
        lastSeenBlocked = blocked
        return Result.success(Unit)
    }
}
