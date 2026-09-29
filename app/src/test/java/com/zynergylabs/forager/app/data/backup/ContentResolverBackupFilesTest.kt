package com.zynergylabs.forager.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The half of the Storage Access Framework wrapper a unit test can reach: a file's read and write, through
 * `file:` URIs that the same `ContentResolver` calls handle. Creating a file in a chosen folder and keeping
 * permission for it need a real document provider, so they are device items, listed in the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContentResolverBackupFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val files get() = ContentResolverBackupFiles(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `a file is written and read back byte for byte`() {
        val uri = Uri.fromFile(File(tmp.root, "backup.zip")).toString()

        files.openForWrite(uri).use { it.write(byteArrayOf(1, 2, 3, 4, 5)) }

        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), files.openForRead(uri).use { it.readBytes() }.toList())
    }

    @Test
    fun `writing over a longer existing file truncates it, so no tail of the old one is left`() {
        val target = File(tmp.root, "backup.zip").apply { writeBytes(ByteArray(100) { 9 }) }

        files.openForWrite(Uri.fromFile(target).toString()).use { it.write(byteArrayOf(1, 2)) }

        assertEquals(2, target.length())
        assertTrue(target.readBytes().contentEquals(byteArrayOf(1, 2)))
    }

    @Test
    fun `delete removes the file at the URI and reports it gone, and touches nothing beside it`() {
        val target = File(tmp.root, "created.zip").apply { writeBytes(byteArrayOf(1)) }
        val neighbour = File(tmp.root, "older-backup.zip").apply { writeBytes(byteArrayOf(2)) }

        val deleted = files.delete(Uri.fromFile(target).toString())

        assertTrue(deleted)
        assertTrue(!target.exists())
        assertTrue("the other backup is untouched", neighbour.exists())
    }

    @Test
    fun `delete of a file that is not there reports false`() {
        assertEquals(false, files.delete(Uri.fromFile(File(tmp.root, "never-existed.zip")).toString()))
    }

    @Test
    fun `sizeOf reports how many bytes the file holds now, 0 for a new empty one`() {
        val filled = File(tmp.root, "old.zip").apply { writeBytes(ByteArray(5) { 1 }) }
        val empty = File(tmp.root, "new.zip").apply { writeBytes(ByteArray(0)) }

        assertEquals(5L, files.sizeOf(Uri.fromFile(filled).toString()))
        assertEquals(0L, files.sizeOf(Uri.fromFile(empty).toString()))
    }

    @Test
    fun `sizeOf is null, not 0, when the provider cannot say`() {
        assertEquals(null, files.sizeOf(Uri.fromFile(File(tmp.root, "no-such-file.zip")).toString()))
    }
}
