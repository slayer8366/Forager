package com.zynergylabs.forager.app.data.diagnostics

import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.EvaluationTrigger
import com.zynergylabs.forager.app.domain.SundownAlert
import com.zynergylabs.forager.app.domain.SundownRecordEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Dispatch 2026-09-28-796: the sundown record, each kind of entry as its whole line; no positions. */
class FileSundownRecordTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = CurrentTimeProvider { 1_791_057_600_000L } // 2026-10-03T20:00:00Z

    @Test
    fun `every entry is one line with its UTC time, kind, track and details`() {
        val file = folder.root.resolve(SUNDOWN_RECORD_FILE_NAME)
        FileSundownRecord(file, clock).apply {
            write(SundownRecordEvent.Evaluated("track-1", EvaluationTrigger.TIMER))
            write(SundownRecordEvent.Evaluated("track-1", EvaluationTrigger.ALARM))
            write(SundownRecordEvent.AlarmScheduled("track-1", SundownAlert.HEADS_UP, 1_791_060_300_000L, accepted = true))
            write(SundownRecordEvent.Fired("track-1", SundownAlert.LEAVE_BY, AlertDeliveryOutcome(true, null, true, null)))
            write(SundownRecordEvent.AlarmCancelled("track-1"))
            write(SundownRecordEvent.AlarmDelivered(null))
        }
        assertEquals(
            listOf(
                "2026-10-03T20:00:00.000Z evaluated track=track-1 by=timer",
                "2026-10-03T20:00:00.000Z evaluated track=track-1 by=alarm",
                "2026-10-03T20:00:00.000Z alarm-scheduled track=track-1 alert=heads-up at=2026-10-03T20:45:00.000Z accepted",
                "2026-10-03T20:00:00.000Z fired track=track-1 alert=leave-by notification=posted vibration=done",
                "2026-10-03T20:00:00.000Z alarm-cancelled track=track-1",
                "2026-10-03T20:00:00.000Z alarm-delivered nothing-watched",
            ),
            file.readLines(),
        )
    }
}
