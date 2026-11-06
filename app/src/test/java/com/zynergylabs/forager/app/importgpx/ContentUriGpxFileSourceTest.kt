package com.zynergylabs.forager.app.importgpx

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GpxFileRead
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Plan T16's file read (the owner's "10 MB limit", RECORD -636): a content Uri is read up to the limit
 * and no further, a file at the limit is read whole, and one that cannot be opened is a failure with its
 * cause, never an empty file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContentUriGpxFileSourceTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val noLog = ErrorLog { _, _, _ -> }

    private fun sourceWith(uri: Uri, bytes: ByteArray): ContentUriGpxFileSource {
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return ContentUriGpxFileSource(app.contentResolver, uri, noLog)
    }

    @Test
    fun `a file one byte over the limit is too big, and one at the limit is read whole with its name`() = runTest {
        val limit = 300_000
        val over = sourceWith(Uri.parse("content://x/document/over.gpx"), ByteArray(limit + 1) { 'a'.code.toByte() })
        assertEquals(GpxFileRead.TooBig, over.read(limit))

        val exact = ByteArray(limit) { 'b'.code.toByte() }
        val read = sourceWith(Uri.parse("content://x/document/Ridge%20loop.gpx"), exact).read(limit)
        assertTrue(read is GpxFileRead.Bytes)
        assertArrayEquals(exact, (read as GpxFileRead.Bytes).bytes)
        assertEquals("the Uri's last segment when the provider gives no name", "Ridge loop.gpx", read.displayName)
    }

    @Test
    fun `a file that cannot be opened is a failure carrying its cause`() = runTest {
        val read = ContentUriGpxFileSource(app.contentResolver, Uri.parse("content://nobody/home.gpx"), noLog).read(1_000)

        assertTrue("got $read", read is GpxFileRead.Failed)
    }
}
