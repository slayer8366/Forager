package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
 * The icon cluster's side and height (RECORD -711) through the real `map_preferences` DataStore: written by
 * one [DataStoreMapPreferencesRepository], read back by a recreated one, beside the settings already in the
 * file. The release-then-reopen shape is `DataStoreMapLayerPreferencesTest`'s, for the reason given there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreMapIconClusterPlacementTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/map_preferences.preferences_pb")

    private val scopes = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreMapPreferencesRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreMapPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    @Test
    fun `nothing stored reads back as no placement`() = runTest {
        val (repository, _) = repository()

        assertNull(repository.getMapIconClusterPlacement().getOrThrow())
    }

    @Test
    fun `a placement written by one instance is read back by a recreated one, and the basemap beside it is untouched`() = runTest {
        val placement = MapIconClusterPlacement(portraitOnLeft = true, portraitOffsetDp = 87.5f, landscapeOnPortSide = true, landscapeOffsetDp = -12.25f)
        val (writer, writerScope) = repository()
        writer.setBasemapKey("street").getOrThrow()
        writer.setMapIconClusterPlacement(placement).getOrThrow()
        writerScope.cancelAndJoin()

        val (reader, _) = repository()

        assertEquals(placement, reader.getMapIconClusterPlacement().getOrThrow())
        assertEquals("street", reader.getBasemapKey().getOrThrow())
    }
}
