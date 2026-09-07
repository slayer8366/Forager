package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Entries feed's order — plate-pulse follow-up, owner ruling on item 2: **by day, newest day
 * first, with the modification time only as a tie-break** between entries on the same day. This
 * deliberately reverses the previous "most recently updated first": an entry is a day, and days do
 * not reorder because a typo was fixed. Each case here would pass under the old sort only by
 * coincidence of the fixture, so the fixtures are built to disagree with it.
 */
class GetCartographyEntriesUseCaseTest {

    private fun entry(id: String, date: LocalDate, updatedAt: Long) =
        CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = updatedAt).copy(isDraft = false)

    @Test
    fun `entries are ordered by day, newest day first, regardless of when each was last touched`() = runTest {
        // The older day was edited most recently — under "most recently updated first" it would
        // lead; under the day sort it must not.
        val olderDayEditedLater = entry("older", LocalDate.of(2026, 8, 1), updatedAt = 9_000L)
        val newerDayEditedEarlier = entry("newer", LocalDate.of(2026, 8, 20), updatedAt = 1_000L)
        val useCase = GetCartographyEntriesUseCase(RecordingCartographyEntryRepository(listOf(olderDayEditedLater, newerDayEditedEarlier)))

        val ordered = useCase().getOrThrow().map { it.id }

        assertEquals(listOf("newer", "older"), ordered)
    }

    @Test
    fun `two entries on the same day fall back to the most recently modified first`() = runTest {
        val day = LocalDate.of(2026, 8, 10)
        val touchedFirst = entry("first", day, updatedAt = 1_000L)
        val touchedLast = entry("last", day, updatedAt = 2_000L)
        val useCase = GetCartographyEntriesUseCase(RecordingCartographyEntryRepository(listOf(touchedFirst, touchedLast)))

        val ordered = useCase().getOrThrow().map { it.id }

        assertEquals(listOf("last", "first"), ordered)
    }

    @Test
    fun `a same-day tie-break never lifts an entry above a newer day`() = runTest {
        val sameDayA = entry("a", LocalDate.of(2026, 8, 10), updatedAt = 5_000L)
        val sameDayB = entry("b", LocalDate.of(2026, 8, 10), updatedAt = 8_000L)
        val newerDay = entry("c", LocalDate.of(2026, 8, 11), updatedAt = 100L)
        val useCase = GetCartographyEntriesUseCase(RecordingCartographyEntryRepository(listOf(sameDayA, sameDayB, newerDay)))

        val ordered = useCase().getOrThrow().map { it.id }

        assertEquals(listOf("c", "b", "a"), ordered)
    }
}
