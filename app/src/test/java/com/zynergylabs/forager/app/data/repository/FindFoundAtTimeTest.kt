package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The time a find was found (data part D, dispatch -697 Amendment 4, RECORD -703; the owner: "Option 1"): written when a find
 * is created on its own day, stored in `mushroom_log_entries.foundAtEpochMillis` (schema 20), read back, and carried unchanged
 * through a re-edit and its Save. Through the real use cases over the real Room repository, in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindFoundAtTimeTest {

    private lateinit var database: ForagerDatabase
    private lateinit var repository: RoomMushroomLogRepository

    /** 2026-10-07 14:14 UTC. */
    private val twoFourteen = LocalDate.of(2026, 10, 7).atTime(14, 14).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java).build()
        repository = RoomMushroomLogRepository(database.mushroomLogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun create(today: LocalDate) = CreateMushroomLogEntryUseCase(
        repository,
        today = { today },
        idGenerator = { "find-1" },
        nowEpochMillis = { twoFourteen },
        zone = { ZoneOffset.UTC },
    )

    @Test
    fun `a find created on its own day stores the moment it was created, and reads it back`() = runTest {
        create(LocalDate.of(2026, 10, 7))(location = null).getOrThrow()

        assertEquals(twoFourteen, repository.getAll().getOrThrow().single().foundAtEpochMillis)
    }

    @Test
    fun `a find created for another day stores no time`() = runTest {
        create(LocalDate.of(2026, 10, 7))(location = null, date = LocalDate.of(2026, 10, 5)).getOrThrow()

        assertNull(repository.getAll().getOrThrow().single().foundAtEpochMillis)
    }

    @Test
    fun `the time survives a re-edit and its Save`() = runTest {
        val created = create(LocalDate.of(2026, 10, 7))(location = null).getOrThrow()
        val committed = CommitDraftEntryUseCase(repository)(created).getOrThrow()
        val draft = StartEditingLogEntryUseCase(repository, idGenerator = { "draft-1" })(committed).getOrThrow()
        CommitDraftEntryUseCase(repository)(draft.copy(ownIdentification = "Morel")).getOrThrow()

        val stored = repository.getAll().getOrThrow().single()
        assertEquals("Morel", stored.ownIdentification)
        assertEquals(twoFourteen, stored.foundAtEpochMillis)
    }
}
