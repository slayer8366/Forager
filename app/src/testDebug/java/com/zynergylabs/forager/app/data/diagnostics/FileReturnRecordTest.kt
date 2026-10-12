package com.zynergylabs.forager.app.data.diagnostics

import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ReturnEndReason
import com.zynergylabs.forager.app.domain.ReturnRecordEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Dispatch 2026-09-28-451: the record is a file, one line per entry, that outlives the process that
 * wrote it. A new instance over the same file (a new process; the stand-in for the log rotation that
 * lost the walk's Return) appends to what is there.
 */
class FileReturnRecordTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_791_057_600_000L // 2026-10-03T20:00:00Z
    private val clock = CurrentTimeProvider { now }

    @Test
    fun `each entry is one line with its UTC time, kind and track, and a later process appends to it`() {
        val file = folder.root.resolve("return-record.log")
        FileReturnRecord(file, clock).write(ReturnRecordEvent.ReturnStarted("track-1"))
        now += 61_500L
        FileReturnRecord(file, clock).write(ReturnRecordEvent.ReturnEnded("track-1", ReturnEndReason.BY_WALKER))

        assertEquals(
            listOf(
                "2026-10-03T20:00:00.000Z return-started track=track-1",
                "2026-10-03T20:01:01.500Z return-ended track=track-1 reason=by-walker",
            ),
            file.readLines(),
        )
    }

    /** RECORD -844: a return that ended because the walker reached the start. */
    @Test
    fun `a return ended on arrival is one line with reason arrival`() {
        val file = folder.root.resolve("return-record.log")
        FileReturnRecord(file, clock).write(ReturnRecordEvent.ReturnEnded("track-1", ReturnEndReason.ARRIVAL))
        assertEquals(listOf("2026-10-03T20:00:00.000Z return-ended track=track-1 reason=arrival"), file.readLines())
    }

    @Test
    fun `the off-track decisions carry their reading's time, and a refusal names the watched recording`() {
        val file = folder.root.resolve("return-record.log")
        val record = FileReturnRecord(file, clock)
        record.write(ReturnRecordEvent.WentOffTrack("track-1", readingAtMillis = 1_791_057_627_000L))
        record.write(ReturnRecordEvent.ReArmed("track-1", readingAtMillis = 1_791_057_660_000L))
        record.write(ReturnRecordEvent.ReturnRefused("track-2", watchedTrackId = "track-1"))
        assertEquals(
            listOf(
                "2026-10-03T20:00:00.000Z went-off-track track=track-1 reading=2026-10-03T20:00:27.000Z",
                "2026-10-03T20:00:00.000Z re-armed track=track-1 reading=2026-10-03T20:01:00.000Z",
                "2026-10-03T20:00:00.000Z return-refused track=track-2 watched=track-1",
            ),
            file.readLines(),
        )
    }

    @Test
    fun `a delivery's outcome is one line, reported or not`() {
        val file = folder.root.resolve("return-record.log")
        val record = FileReturnRecord(file, clock)
        record.write(ReturnRecordEvent.AlertDelivered("track-1", AlertDeliveryOutcome(false, "SecurityException", true, null)))
        record.write(ReturnRecordEvent.AlertDelivered("track-1", null))
        assertEquals(
            listOf(
                "2026-10-03T20:00:00.000Z alert-delivery track=track-1 notification=not-posted(SecurityException) vibration=done",
                "2026-10-03T20:00:00.000Z alert-delivery track=track-1 outcome=not-reported",
            ),
            file.readLines(),
        )
    }

    @Test
    fun `past its size limit the oldest half is dropped and the newest kept`() {
        val file = folder.root.resolve("return-record.log")
        val record = FileReturnRecord(file, clock, maxBytes = 2_000L)
        repeat(100) { i -> record.write(ReturnRecordEvent.ReturnStarted("track-$i")) }
        assertTrue("within the limit: ${file.length()}", file.length() <= 2_000L)
        val lines = file.readLines()
        assertEquals("the newest is kept", "2026-10-03T20:00:00.000Z return-started track=track-99", lines.last())
        assertTrue("the oldest is gone", lines.none { it.endsWith("track=track-0") })
    }
}
