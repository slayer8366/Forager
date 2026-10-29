package com.zynergylabs.forager.app.diagnostics.walklog

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
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Walk logger" switch's storage (dispatch 2026-09-28-532, Amendment 3, RECORD -560): off by
 * default, remembered across a recreated store, in the debug build's diagnostics DataStore file under
 * `diagnostics.walk_logger`. Since dispatch 2026-10-29-05, Amendment 1, its own store,
 * [DataStoreWalkLoggerSwitch], held once per application.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WalkLoggerSwitchStoreTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun switchFile() = File(context().filesDir, "datastore/debug_diagnostics_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()

    private fun store(): Pair<DataStoreWalkLoggerSwitch, Job> {
        val job = SupervisorJob().also { scopes += it }
        return DataStoreWalkLoggerSwitch(context(), CoroutineScope(Dispatchers.IO + job)) to job
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
    fun `turned on then off reads off`() = runTest {
        val (store, _) = store()

        store.setWalkLoggerEnabled(true).getOrThrow()
        assertEquals(true, store.isWalkLoggerEnabled().getOrThrow())
        store.setWalkLoggerEnabled(false).getOrThrow()
        assertEquals(false, store.isWalkLoggerEnabled().getOrThrow())
    }

    /** One instance per application, which the logger and the Diagnostics panel both read; DataStore refuses a second on the file. */
    @Test
    fun `the app's switch is one instance, read the same from any context of the app`() {
        val app = context()
        val first = DataStoreWalkLoggerSwitch.of(app)

        assertSame(first, DataStoreWalkLoggerSwitch.of(app))
        assertSame(first, DataStoreWalkLoggerSwitch.of(app.applicationContext))
    }
}
