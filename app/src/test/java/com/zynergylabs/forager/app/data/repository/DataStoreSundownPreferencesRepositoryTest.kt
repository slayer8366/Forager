package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real round trip through Jetpack DataStore, not a fake, matching
 * [DataStoreAppThemePreferenceRepositoryTest] and [DataStoreMapPreferencesRepositoryTest].
 *
 * The backing file is deleted before and after every test for the isolation reason those two
 * document: the repository builds its own `DataStore` per instance precisely so that deleting the
 * file between tests actually resets it, which the process-wide `by preferencesDataStore` delegate
 * would defeat. `isolation between tests` below is the check that this still holds; without the
 * per-instance factory it fails, which is the whole reason the factory is written that way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreSundownPreferencesRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/sundown_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    @Test
    fun `an untouched install gets the stated defaults`() = runTest {
        val repository = DataStoreSundownPreferencesRepository(context())

        assertEquals(
            "the owner's stated default margin",
            DEFAULT_DARKNESS_MARGIN_MINUTES,
            repository.getDarknessMarginMinutes().getOrThrow(),
        )
        assertEquals(
            "alerts on by default: a safety feature disabled by default looks like one that is absent",
            true,
            repository.getAlertsEnabled().getOrThrow(),
        )
    }

    @Test
    fun `the default margin is one hour, in minutes`() = runTest {
        // Guards the unit as much as the number. Storing millis here and reading it as minutes
        // would still round-trip cleanly and be wrong by a factor of sixty thousand.
        assertEquals(60, DEFAULT_DARKNESS_MARGIN_MINUTES)
    }

    @Test
    fun `the margin round-trips`() = runTest {
        val repository = DataStoreSundownPreferencesRepository(context())

        assertTrue(repository.setDarknessMarginMinutes(25).isSuccess)
        assertEquals(25, repository.getDarknessMarginMinutes().getOrThrow())

        assertTrue(repository.setDarknessMarginMinutes(90).isSuccess)
        assertEquals("a second write replaces the first", 90, repository.getDarknessMarginMinutes().getOrThrow())
    }

    @Test
    fun `the alerts flag round-trips both ways`() = runTest {
        val repository = DataStoreSundownPreferencesRepository(context())

        assertTrue(repository.setAlertsEnabled(false).isSuccess)
        assertEquals(false, repository.getAlertsEnabled().getOrThrow())

        // Turning it back on has to actually write, not fall through to the default and look right.
        assertTrue(repository.setAlertsEnabled(true).isSuccess)
        assertEquals(true, repository.getAlertsEnabled().getOrThrow())
    }

    @Test
    fun `the two settings do not disturb each other`() = runTest {
        val repository = DataStoreSundownPreferencesRepository(context())

        repository.setDarknessMarginMinutes(45).getOrThrow()
        repository.setAlertsEnabled(false).getOrThrow()

        assertEquals(45, repository.getDarknessMarginMinutes().getOrThrow())
        assertEquals(false, repository.getAlertsEnabled().getOrThrow())

        repository.setAlertsEnabled(true).getOrThrow()
        assertEquals("changing the flag must not reset the margin", 45, repository.getDarknessMarginMinutes().getOrThrow())
    }

    @Test
    fun `a second live instance against the same file is refused`() = runTest {
        // Written first as "a value written by one instance is read by the next", which failed:
        // DataStore refuses two active instances for one file, and in that test the first was
        // still alive. A real process restart does not have that problem, so the original premise
        // was untestable in one JVM rather than wrong about persistence.
        //
        // What is worth pinning is the invariant that failure revealed. `ForagerApplication`
        // builds `AppContainer(this)` exactly once, and `AppContainer` constructs exactly one of
        // these, so production holds one instance per file. Anything that news up a second one ad
        // hoc, in a screen or a ViewModel, throws at runtime. All four DataStore repositories in
        // this project share the constraint; this test states it out loud for one of them.
        val first = DataStoreSundownPreferencesRepository(context())
        first.setDarknessMarginMinutes(15).getOrThrow()

        val second = runCatching { DataStoreSundownPreferencesRepository(context()).getDarknessMarginMinutes() }
        val thrown = second.exceptionOrNull() ?: second.getOrNull()?.exceptionOrNull()
        assertTrue(
            "a second live instance must fail loudly rather than quietly serve stale data, got $second",
            thrown is IllegalStateException,
        )
    }

    @Test
    fun `isolation between tests`() = runTest {
        // If this reads 45 rather than the default, the file deletion in setUp did not reset the
        // store, which is exactly what the per-process delegate would cause. The value 45 is the
        // one written by `the two settings do not disturb each other` above.
        val repository = DataStoreSundownPreferencesRepository(context())
        assertEquals(
            "a prior test's write must not leak into this one",
            DEFAULT_DARKNESS_MARGIN_MINUTES,
            repository.getDarknessMarginMinutes().getOrThrow(),
        )
    }
}
