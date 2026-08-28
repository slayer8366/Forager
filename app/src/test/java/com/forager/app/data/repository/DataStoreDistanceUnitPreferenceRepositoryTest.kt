package com.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.DistanceUnit
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore (Robolectric, not a fake) — same discipline
 * [DataStoreMapPreferencesRepositoryTest] applies to the sibling preference this mirrors.
 *
 * [dataStoreFile] is deleted before and after every test for the same isolation reason that class
 * documents on itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreDistanceUnitPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/distance_unit_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    private fun repository() = DataStoreDistanceUnitPreferenceRepository(context())

    @Test
    fun `nothing picked yet defaults to miles`() = runTest {
        assertEquals(DistanceUnit.MILES, repository().getDistanceUnit().getOrThrow())
    }

    @Test
    fun `a saved unit round-trips exactly`() = runTest {
        val repository = repository()

        repository.setDistanceUnit(DistanceUnit.KILOMETERS).getOrThrow()

        assertEquals(DistanceUnit.KILOMETERS, repository.getDistanceUnit().getOrThrow())
    }

    @Test
    fun `saving a second unit replaces the first`() = runTest {
        val repository = repository()
        repository.setDistanceUnit(DistanceUnit.KILOMETERS).getOrThrow()

        repository.setDistanceUnit(DistanceUnit.MILES).getOrThrow()

        assertEquals(DistanceUnit.MILES, repository.getDistanceUnit().getOrThrow())
    }

}
