package com.forager.app.crash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [Thread.UncaughtExceptionHandler] is plain `java.lang`, and [CrashFileStore] is pure JVM (see
 * its own doc comment), so this whole class runs as a plain JVM test — no Robolectric needed. The
 * one Android-specific input, the API level, is a plain `Int` passed to the constructor, exactly
 * so this stays testable without touching `android.os.Build`.
 */
class CrashUncaughtExceptionHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `uncaughtException writes a crash file and always delegates to the previous handler`() {
        val store = CrashFileStore(tempFolder.newFolder("crashes"))
        var delegatedThread: Thread? = null
        var delegatedThrowable: Throwable? = null
        val previous = Thread.UncaughtExceptionHandler { thread, throwable ->
            delegatedThread = thread
            delegatedThrowable = throwable
        }
        val handler = CrashUncaughtExceptionHandler(store, apiLevel = 33, previousHandler = previous)
        val thread = Thread("test-thread")
        val throwable = RuntimeException("kaboom")

        handler.uncaughtException(thread, throwable)

        assertEquals(1, store.list().size)
        assertTrue(store.list().first().readText().contains("kaboom"))
        assertEquals(thread, delegatedThread)
        assertEquals(throwable, delegatedThrowable)
    }

    @Test
    fun `a write failure still delegates to the previous handler, and does not throw`() {
        // A regular file sitting where the crash directory should be: File.mkdirs() under it
        // returns false silently, and the subsequent write then fails for real — this is the
        // "writing fails" case the handler must swallow rather than let escape.
        val blockerFile = tempFolder.newFile("blocks-the-crash-directory")
        val storeThatCannotWrite = CrashFileStore(File(blockerFile, "crashes"))
        var delegated = false
        val previous = Thread.UncaughtExceptionHandler { _, _ -> delegated = true }
        val handler = CrashUncaughtExceptionHandler(storeThatCannotWrite, apiLevel = 33, previousHandler = previous)

        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertTrue("expected the previous handler to still run after a write failure", delegated)
    }

    @Test
    fun `no previous handler does not throw`() {
        val store = CrashFileStore(tempFolder.newFolder("crashes"))
        val handler = CrashUncaughtExceptionHandler(store, apiLevel = 33, previousHandler = null)

        // Must not throw even with nothing to delegate to.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertFalse(store.list().isEmpty())
    }
}
