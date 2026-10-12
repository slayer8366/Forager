package com.zynergylabs.forager.app.data.diagnostics

import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.BackByEndReason
import com.zynergylabs.forager.app.domain.BackByRecordEvent
import com.zynergylabs.forager.app.domain.EvaluationTrigger
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Dispatch 2026-09-28-796: the Back by record is a file, one line per entry, as the Return record is
 * (`FileReturnRecordTest`). Every kind of entry, as its whole line: no positions in any of them.
 */
class FileBackByRecordTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_791_057_600_000L // 2026-10-03T20:00:00Z
    private val clock = CurrentTimeProvider { now }
    private val at = 1_791_060_300_000L // 2026-10-03T20:45:00Z

    @Test
    fun `every entry is one line with its UTC time, kind, track and details, and a later process appends`() {
        val file = folder.root.resolve(BACK_BY_RECORD_FILE_NAME)
        val record = FileBackByRecord(file, clock)
        record.write(BackByRecordEvent.Started("track-1", keptAtMillis = null))
        record.write(BackByRecordEvent.Set("track-1", at, accepted = true, watchedTrackId = null))
        record.write(BackByRecordEvent.Set("track-2", at, accepted = false, watchedTrackId = "track-1"))
        record.write(BackByRecordEvent.SetWithNoRecording)
        record.write(BackByRecordEvent.Evaluated("track-1", at, due = false, trigger = EvaluationTrigger.TIMER))
        now += 61_500L
        FileBackByRecord(file, clock).apply {
            write(BackByRecordEvent.Evaluated("track-1", at, due = true, trigger = EvaluationTrigger.FIX))
            write(BackByRecordEvent.Fired("track-1", at, AlertDeliveryOutcome(true, null, false, null, vibrationSkipped = "phone on silent")))
            write(BackByRecordEvent.Fired("track-1", at, AlertDeliveryOutcome(false, "POST_NOTIFICATIONS denied", true, null)))
            write(BackByRecordEvent.Fired("track-1", at, null))
            write(BackByRecordEvent.Later("track-1", at))
            write(BackByRecordEvent.Ended("track-1", BackByEndReason.SERVICE_DESTROYED))
            write(BackByRecordEvent.AlarmScheduled("track-1", at, accepted = true))
            write(BackByRecordEvent.AlarmScheduled("track-1", at, accepted = false))
            write(BackByRecordEvent.AlarmCancelled("track-1"))
            write(BackByRecordEvent.AlarmDelivered("track-1"))
            write(BackByRecordEvent.AlarmDelivered(null))
        }
        assertEquals(
            listOf(
                "2026-10-03T20:00:00.000Z started track=track-1 kept=none",
                "2026-10-03T20:00:00.000Z set track=track-1 at=2026-10-03T20:45:00.000Z accepted",
                "2026-10-03T20:00:00.000Z set track=track-2 at=2026-10-03T20:45:00.000Z refused(watching track-1)",
                "2026-10-03T20:00:00.000Z set-ignored reason=no-recording-on-screen",
                "2026-10-03T20:00:00.000Z evaluated track=track-1 at=2026-10-03T20:45:00.000Z due=no by=timer",
                "2026-10-03T20:01:01.500Z evaluated track=track-1 at=2026-10-03T20:45:00.000Z due=yes by=fix",
                "2026-10-03T20:01:01.500Z fired track=track-1 at=2026-10-03T20:45:00.000Z notification=posted vibration=skipped(phone on silent)",
                "2026-10-03T20:01:01.500Z fired track=track-1 at=2026-10-03T20:45:00.000Z notification=not-posted(POST_NOTIFICATIONS denied) vibration=done",
                "2026-10-03T20:01:01.500Z fired track=track-1 at=2026-10-03T20:45:00.000Z outcome=not-reported",
                "2026-10-03T20:01:01.500Z later track=track-1 at=2026-10-03T20:45:00.000Z",
                "2026-10-03T20:01:01.500Z ended track=track-1 reason=service-destroyed",
                "2026-10-03T20:01:01.500Z alarm-scheduled track=track-1 at=2026-10-03T20:45:00.000Z accepted",
                "2026-10-03T20:01:01.500Z alarm-scheduled track=track-1 at=2026-10-03T20:45:00.000Z refused",
                "2026-10-03T20:01:01.500Z alarm-cancelled track=track-1",
                "2026-10-03T20:01:01.500Z alarm-delivered track=track-1",
                "2026-10-03T20:01:01.500Z alarm-delivered nothing-set",
            ),
            file.readLines(),
        )
    }
}
