package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal redesign J3, C6: the photo reference count `MainActivity` hands `MushroomLogViewModel`
 * ([photoEntryReferenceCountOrZero]) logs through the app's [ErrorLog] seam when its fallback to 0
 * fires (CLAUDE.md, "no default fallback that isn't logged when it fires"), and stays silent when the
 * count succeeds. Plain JVM: the seam is a fake here, as the ViewModel tests fake it.
 */
class PhotoEntryReferenceCountTest {

    private data class Logged(val tag: String, val message: String, val error: Throwable)

    private val logged = mutableListOf<Logged>()
    private val fakeLog = ErrorLog { tag, message, error -> logged += Logged(tag, message, error) }

    @Test
    fun `a failed count shows 0 and is logged once, with the photo's id and the cause`() = runBlocking {
        val cause = IOException("database closed")

        val count = photoEntryReferenceCountOrZero("photo-7", { Result.failure(cause) }, fakeLog)

        assertEquals(0, count)
        assertEquals("one log call: $logged", 1, logged.size)
        val entry = logged.single()
        assertSame(cause, entry.error)
        assertTrue("the message names the photo: ${entry.message}", entry.message.contains("photo-7"))
        assertTrue("the message says what is shown instead: ${entry.message}", entry.message.contains("0"))
    }

    @Test
    fun `a successful count is returned as is and logs nothing`() = runBlocking {
        val count = photoEntryReferenceCountOrZero("photo-7", { Result.success(3) }, fakeLog)

        assertEquals(3, count)
        assertEquals(emptyList<Logged>(), logged)
    }

    @Test
    fun `a successful count of 0 logs nothing either`() = runBlocking {
        val count = photoEntryReferenceCountOrZero("photo-8", { Result.success(0) }, fakeLog)

        assertEquals(0, count)
        assertEquals(emptyList<Logged>(), logged)
    }
}
