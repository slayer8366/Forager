package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SaveCartographyEntryUseCase] stamps [CartographyEntry.updatedAtEpochMillis] on every save —
 * plate-pulse follow-up, owner ruling on item 2. Before this the field changed only at create and
 * commit, so a committed entry edited in place kept a modification time that did not record the
 * modification. Asserted at the seam, on what the repository received and on what the caller gets
 * back, since both are what the fix is for.
 */
class SaveCartographyEntryUseCaseTest {

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, text = "Edited after commit.")

    @Test
    fun `the entry handed to the repository carries the save time, not the time it came in with`() = runTest {
        val repository = RecordingCartographyEntryRepository()
        val useCase = SaveCartographyEntryUseCase(repository, now = { 5_000L })

        useCase(entry).getOrThrow()

        assertEquals(5_000L, repository.saved.single().updatedAtEpochMillis)
        assertEquals("nothing but the stamp may change on the way to disk", entry.copy(updatedAtEpochMillis = 5_000L), repository.saved.single())
    }

    @Test
    fun `the returned entry is the stamped copy, so a caller keeping it keeps the right time`() = runTest {
        val useCase = SaveCartographyEntryUseCase(RecordingCartographyEntryRepository(), now = { 5_000L })

        val returned = useCase(entry).getOrThrow()

        assertEquals(5_000L, returned.updatedAtEpochMillis)
    }

    @Test
    fun `a draft's autosave is stamped the same way as a committed entry's explicit save`() = runTest {
        val repository = RecordingCartographyEntryRepository()
        val useCase = SaveCartographyEntryUseCase(repository, now = { 7_000L })

        useCase(entry.copy(isDraft = true)).getOrThrow()

        assertEquals(7_000L, repository.saved.single().updatedAtEpochMillis)
    }
}
