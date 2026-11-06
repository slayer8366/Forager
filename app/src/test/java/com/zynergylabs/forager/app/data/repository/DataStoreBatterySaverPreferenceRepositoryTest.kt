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
 * A real round trip through DataStore for the Battery saver switch (dispatch 2026-09-28-767), read back
 * through a recreated instance the way a restart reads it. Each instance's scope is cancelled before the
 * next is made on the same file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreBatterySaverPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/battery_saver_preferences.preferences_pb")
    private val jobs = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreBatterySaverPreferenceRepository, Job> {
        val job = SupervisorJob()
        jobs += job
        return DataStoreBatterySaverPreferenceRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        jobs.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install has Battery saver off`() = runBlocking {
        val (repository, _) = repository()
        assertEquals(false, repository.getEnabled().getOrThrow())
        assertEquals(false, repository.enabled.value)
    }

    @Test
    fun `on survives a restart, and the recreated instance's flow holds it once read`() = runBlocking {
        val (first, job) = repository()
        first.setEnabled(true).getOrThrow()
        assertEquals("a store is seen at once by the flow the tracker follows", true, first.enabled.value)
        job.cancelAndJoin()

        val (second, _) = repository()
        assertEquals("not read yet: the default", false, second.enabled.value)
        assertEquals(true, second.getEnabled().getOrThrow())
        assertEquals("once read after the restart, the flow holds the stored on", true, second.enabled.value)
    }

    @Test
    fun `turning it back off survives a restart too`() = runBlocking {
        val (first, job) = repository()
        first.setEnabled(true).getOrThrow()
        first.setEnabled(false).getOrThrow()
        job.cancelAndJoin()

        val (second, _) = repository()
        assertEquals(false, second.getEnabled().getOrThrow())
    }
}
