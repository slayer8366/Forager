package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through DataStore for the off-track reminder's two values (dispatch
 * 2026-09-28-626; Amendment 1, RECORD -627), read back through a recreated instance the way a
 * restart reads them. Each instance's scope is cancelled before the next is made on the same file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreOffTrackReminderPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/off_track_reminder_preferences.preferences_pb")
    private val jobs = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreOffTrackReminderPreferenceRepository, Job> {
        val job = SupervisorJob()
        jobs += job
        return DataStoreOffTrackReminderPreferenceRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        jobs.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install has the reminder on and no block seen`() = runBlocking {
        val (repository, _) = repository()
        assertEquals(true, repository.getEnabled().getOrThrow())
        assertEquals(true, repository.enabledNow())
        assertEquals(false, repository.getLastSeenBlocked().getOrThrow())
    }

    @Test
    fun `both values survive a recreated instance, and its enabledNow holds the stored off once read`() = runBlocking {
        val (first, job) = repository()
        first.setEnabled(false).getOrThrow()
        first.setLastSeenBlocked(true).getOrThrow()
        assertEquals("set is seen at once by the synchronous read", false, first.enabledNow())
        job.cancelAndJoin()

        val (second, _) = repository()
        assertEquals(false, second.getEnabled().getOrThrow())
        assertEquals("once read after a restart, enabledNow holds the stored off", false, second.enabledNow())
        assertEquals(true, second.getLastSeenBlocked().getOrThrow())
    }

    @Test
    fun `turning it back on is seen by enabledNow`() = runBlocking {
        val (repository, _) = repository()
        repository.setEnabled(false).getOrThrow()
        repository.setEnabled(true).getOrThrow()
        assertEquals(true, repository.enabledNow())
        assertEquals(true, repository.getEnabled().getOrThrow())
    }
}
