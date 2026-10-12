package com.zynergylabs.forager.app.forecast

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Walk logger" switch's storage (dispatch 2026-09-28-532, Amendment 3, RECORD -560): off by
 * default, remembered across a recreated store, kept beside the synthetic forecast switch in the
 * debug build's one diagnostics DataStore file under its own key, and independent of that switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WalkLoggerSwitchStoreTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun switchFile() = File(context().filesDir, "datastore/debug_diagnostics_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()

    private fun store(): Pair<SyntheticForecastCellStore, Job> {
        val job = SupervisorJob().also { scopes += it }
        return SyntheticForecastCellStore(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before
    fun setUp() {
        switchFile().delete()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        switchFile().delete()
    }

    @Test
    fun `off until turned on`() = runTest {
        val (store, _) = store()

        assertEquals(false, store.isWalkLoggerEnabled().getOrThrow())
    }

    @Test
    fun `stored as diagnostics-walk_logger in debug_diagnostics_preferences, surviving a recreated store`() = runTest {
        val (first, firstScope) = store()
        first.setWalkLoggerEnabled(true).getOrThrow()
        firstScope.cancelAndJoin()

        val (second, secondScope) = store()
        assertEquals(true, second.isWalkLoggerEnabled().getOrThrow())
        secondScope.cancelAndJoin()

        val rawScope = SupervisorJob().also { scopes += it }
        val raw = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + rawScope)) { switchFile() }
        assertEquals(true, raw.data.first()[booleanPreferencesKey("diagnostics.walk_logger")])
    }

    @Test
    fun `the two switches are independent`() = runTest {
        val (store, _) = store()

        store.setWalkLoggerEnabled(true).getOrThrow()
        assertEquals("the forecast switch is untouched", false, store.isEnabled().getOrThrow())

        store.setEnabled(true).getOrThrow()
        store.setWalkLoggerEnabled(false).getOrThrow()
        assertEquals(true, store.isEnabled().getOrThrow())
        assertEquals(false, store.isWalkLoggerEnabled().getOrThrow())
    }
}
