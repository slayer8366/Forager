package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailsSourceDates
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 8d: the official-trails card's kept copy (dispatch 2026-10-26-24, "Offline"), on disk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreReferenceTrailsDatesStoreTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/reference_trails_dates.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install has no copy`() = runTest {
        assertNull(DataStoreReferenceTrailsDatesStore(context()).read().getOrThrow())
    }

    @Test
    fun `a copy round-trips to a file on disk, and a later copy with a date missing drops that date but stays a copy`() = runTest {
        val store = DataStoreReferenceTrailsDatesStore(context())
        val both = ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25))
        assertTrue(store.write(both).isSuccess)
        assertEquals(both, store.read().getOrThrow())
        assertTrue("written to disk, not held in memory", dataStoreFile().exists())

        val blmMissing = ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 26), null)
        assertTrue(store.write(blmMissing).isSuccess)
        assertEquals(blmMissing, store.read().getOrThrow())

        val neither = ReferenceTrailsSourceDates(null, null)
        assertTrue(store.write(neither).isSuccess)
        assertEquals("a copy with no dates is still a copy, not 'never online'", neither, store.read().getOrThrow())
    }
}
