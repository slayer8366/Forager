package com.forager.app.crash

import com.forager.app.domain.CurrentTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [CrashFileStore] is pure `java.io.File` plus [CurrentTimeProvider] — no Android dependency — so
 * this runs as a plain JVM test, no Robolectric needed. See that class's own doc comment.
 */
class CrashFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `write creates a file containing the timestamp, thread name, API level, and the full Caused by chain`() {
        val store = CrashFileStore(tempFolder.newFolder("crashes"), FakeCurrentTimeProvider(1_700_000_000_000L))
        val thread = Thread("locate-me-test-thread")
        val throwable = IllegalStateException("boom", RuntimeException("root cause"))

        val file = store.write(thread, throwable, apiLevel = 34)

        assertTrue(file.exists())
        val content = file.readText()
        assertTrue("expected a Timestamp line, got:\n$content", content.contains("Timestamp: 2023-11-14T22:13:20Z"))
        assertTrue(content.contains("Thread: locate-me-test-thread"))
        assertTrue(content.contains("API level: 34"))
        assertTrue(content.contains("java.lang.IllegalStateException: boom"))
        assertTrue("expected the cause chain to be included", content.contains("Caused by: java.lang.RuntimeException: root cause"))
    }

    @Test
    fun `write creates the crash directory if it does not exist yet`() {
        val crashDir = tempFolder.newFolder("app-external").resolve("crashes")
        assertFalse(crashDir.exists())
        val store = CrashFileStore(crashDir, FakeCurrentTimeProvider(1_000L))

        store.write(Thread.currentThread(), RuntimeException("boom"), apiLevel = 30)

        assertTrue(crashDir.isDirectory)
        assertEquals(1, store.list().size)
    }

    @Test
    fun `list returns every written file, newest first`() {
        val dir = tempFolder.newFolder("crashes")
        val time = FakeCurrentTimeProvider(start = 1_000L)
        val store = CrashFileStore(dir, time, maxFiles = 10)

        store.write(Thread.currentThread(), RuntimeException("first"), apiLevel = 30)
        store.write(Thread.currentThread(), RuntimeException("second"), apiLevel = 30)
        store.write(Thread.currentThread(), RuntimeException("third"), apiLevel = 30)

        val contents = store.list().map { it.readText() }
        assertTrue("expected newest-first order", contents[0].contains("third"))
        assertTrue(contents[1].contains("second"))
        assertTrue(contents[2].contains("first"))
    }

    @Test
    fun `writing past the cap keeps only the newest maxFiles crash files, deleting the oldest`() {
        val dir = tempFolder.newFolder("crashes")
        val time = FakeCurrentTimeProvider(start = 1_000L)
        // A cap distinct from CrashFileStore's own DEFAULT_MAX_CRASH_FILES, chosen for this test —
        // not an assertion about that constant's value (see the DISPATCH task's own instruction
        // not to assert a constant against a range defined in the same file).
        val store = CrashFileStore(dir, time, maxFiles = 3)

        repeat(5) { i -> store.write(Thread.currentThread(), RuntimeException("crash-$i"), apiLevel = 30) }

        val remaining = store.list()
        assertEquals(3, remaining.size)
        val remainingContents = remaining.map { it.readText() }
        assertTrue("expected the three newest crashes to survive", remainingContents.any { it.contains("crash-4") })
        assertTrue(remainingContents.any { it.contains("crash-3") })
        assertTrue(remainingContents.any { it.contains("crash-2") })
        assertTrue("expected the oldest crashes to have been pruned", remainingContents.none { it.contains("crash-0") })
        assertTrue(remainingContents.none { it.contains("crash-1") })
    }

    @Test
    fun `epochMillisOf parses this store's own filenames and rejects anything else`() {
        val dir = tempFolder.newFolder("crashes")
        val store = CrashFileStore(dir, FakeCurrentTimeProvider(42_000L))

        val written = store.write(Thread.currentThread(), RuntimeException("boom"), apiLevel = 30)

        assertEquals(42_000L, CrashFileStore.epochMillisOf(written))
        assertEquals(null, CrashFileStore.epochMillisOf(dir.resolve("not-a-crash-file.txt")))
    }
}

private class FakeCurrentTimeProvider(start: Long) : CurrentTimeProvider {
    private var current = start
    override fun nowEpochMillis(): Long = current++
}
