package com.zynergylabs.forager.app.domain.referencetrails

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8d (dispatch 2026-10-26-24): the card's dates across fetches. The kept copy shows at the first refresh; a good fetch
 * replaces and keeps it; a failed or unreadable fetch keeps the copy there was and is logged ("never blank good dates").
 * The scope is unconfined and the fakes never suspend, so each call has finished when it returns.
 */
class ReferenceTrailsSourcesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val logs = mutableListOf<String>()

    @After
    fun tearDown() = scope.cancel()

    private class FakeStore(var kept: ReferenceTrailsSourceDates?, val readFails: Boolean = false) : ReferenceTrailsDatesStore {
        val writes = mutableListOf<ReferenceTrailsSourceDates>()
        override suspend fun read(): Result<ReferenceTrailsSourceDates?> =
            if (readFails) Result.failure(IOException("unreadable")) else Result.success(kept)
        override suspend fun write(dates: ReferenceTrailsSourceDates): Result<Unit> {
            writes += dates
            kept = dates
            return Result.success(Unit)
        }
    }

    private fun sources(store: FakeStore, answer: () -> Result<String>): Pair<ReferenceTrailsSources, () -> Int> {
        var fetches = 0
        val sources = ReferenceTrailsSources(
            source = { fetches++; answer() },
            store = store,
            scope = scope,
            log = { message, _ -> logs += message },
        )
        return sources to { fetches }
    }

    private val kept = ReferenceTrailsSourceDates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))
    private val fetched = ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25))

    @Test
    fun `nothing is read until the first refresh, which shows the kept copy, or none when nothing was ever kept`() {
        val (withKept, _) = sources(FakeStore(kept)) { Result.failure(IOException("offline")) }
        assertEquals("the file is not opened at construction", ReferenceTrailsCopy.Loading, withKept.copy.value)
        withKept.refresh()
        assertEquals(ReferenceTrailsCopy.Known(kept), withKept.copy.value)
        val (withNone, _) = sources(FakeStore(null)) { Result.failure(IOException("offline")) }
        withNone.refresh()
        assertEquals(ReferenceTrailsCopy.None, withNone.copy.value)
    }

    @Test
    fun `a good fetch shows the new dates and keeps them`() {
        val store = FakeStore(kept)
        val (sources, fetches) = sources(store) { Result.success(ReferenceTrailsManifestTest.manifest()) }
        sources.refresh()
        assertEquals(1, fetches())
        assertEquals(ReferenceTrailsCopy.Known(fetched), sources.copy.value)
        assertEquals(listOf(fetched), store.writes)
    }

    @Test
    fun `a failed fetch keeps the old dates, writes nothing, and is logged`() {
        val store = FakeStore(kept)
        val (sources, _) = sources(store) { Result.failure(IOException("offline")) }
        sources.refresh()
        assertEquals(ReferenceTrailsCopy.Known(kept), sources.copy.value)
        assertEquals(emptyList<ReferenceTrailsSourceDates>(), store.writes)
        assertEquals(listOf("The official-trails manifest could not be fetched; the card keeps the dates it had."), logs)
    }

    @Test
    fun `an unreadable answer keeps the old dates, writes nothing, and is logged`() {
        val store = FakeStore(kept)
        val (sources, _) = sources(store) { Result.success("<html>not the manifest</html>") }
        sources.refresh()
        assertEquals(ReferenceTrailsCopy.Known(kept), sources.copy.value)
        assertEquals(emptyList<ReferenceTrailsSourceDates>(), store.writes)
        assertEquals(listOf("The official-trails manifest could not be read; the card keeps the dates it had."), logs)
    }

    @Test
    fun `with no copy, a failed fetch stays at no copy`() {
        val (sources, _) = sources(FakeStore(null)) { Result.failure(IOException("offline")) }
        sources.refresh()
        assertEquals(ReferenceTrailsCopy.None, sources.copy.value)
    }

    @Test
    fun `a kept copy that cannot be read is logged and shows as none`() {
        val (sources, _) = sources(FakeStore(kept, readFails = true)) { Result.failure(IOException("offline")) }
        sources.refresh()
        assertEquals(ReferenceTrailsCopy.None, sources.copy.value)
        assertTrue(logs.first().startsWith("The kept official-trails dates could not be read"))
    }

    @Test
    fun `a good fetch with one date missing replaces the copy, so that date is left out, and the gap is logged`() {
        val store = FakeStore(kept)
        val (sources, _) = sources(store) { Result.success(ReferenceTrailsManifestTest.manifest(sources = listOf(ReferenceTrailsManifestTest.FS_SOURCE))) }
        sources.refresh()
        assertEquals(ReferenceTrailsCopy.Known(ReferenceTrailsSourceDates(LocalDate.of(2026, 10, 24), null)), sources.copy.value)
        assertEquals(listOf("The manifest has no 'blm_trails' source; its date is left out."), logs)
    }
}
