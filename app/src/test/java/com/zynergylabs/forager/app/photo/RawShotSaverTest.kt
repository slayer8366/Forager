package com.zynergylabs.forager.app.photo

import com.zynergylabs.forager.app.domain.LOW_STORAGE_FLOOR_BYTES
import com.zynergylabs.forager.app.data.raw.ForagerDngWriter
import com.zynergylabs.forager.app.data.raw.SyntheticDng
import com.zynergylabs.forager.app.data.raw.TiffPatch
import com.zynergylabs.forager.app.domain.raw.DngFailureStage
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.domain.raw.DngWriter
import com.zynergylabs.forager.app.domain.worstCaseRawShotBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The shot step (`RawShotSaver`, dispatch 2026-10-15-01 Amendment 1 items 3 and 5): a DNG is written only when there is
 * an input and room for it, and a writer that refuses leaves no file and the owner's -1003 message. The writer is a fake
 * here (the real one is built on its own branch, dispatch 2026-10-15-02); what is tested is this side's handling of each
 * answer the contract allows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RawShotSaverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val plenty = LOW_STORAGE_FLOOR_BYTES + worstCaseRawShotBytes(4, 4) + 1

    private fun saver(writer: DngWriter, free: Long = plenty, files: MutableList<File> = mutableListOf()) =
        RawShotSaver(
            newRawFile = { File(tmp.root, "shot-${files.size}.dng").also { files += it } },
            freeBytes = { free },
            writer = writer,
            ioDispatcher = Dispatchers.Unconfined,
        )

    private val shot = RawShot(dng = fakeDngImage(), dngUnavailable = null)

    @Test
    fun `a written DNG is kept, and it is the writer's own file`() = runTest {
        val writer = FakeDngWriter { _, destination -> destination.writeBytes(byteArrayOf(0x49, 0x49, 0x2A, 0)); DngWriteResult.Written(4) }
        val outcome = saver(writer).save(shot)
        val kept = outcome as? RawShotOutcome.Kept ?: throw AssertionError("expected Kept, got $outcome")
        assertArrayEquals(byteArrayOf(0x49, 0x49, 0x2A, 0), kept.dng.readBytes())
        assertEquals("the writer saw this shot's input", listOf(shot.dng), writer.images)
    }

    @Test
    fun `a DNG that fails its clean check leaves no file, and the user is given the owner's -1003 words`() = runTest {
        ShadowLog.clear()
        val files = mutableListOf<File>()
        // A writer that leaves its file behind against the contract: this side still removes it.
        val writer = FakeDngWriter { _, destination -> destination.writeBytes(byteArrayOf(1)); DngWriteResult.Failed("a planted Make tag", DngFailureStage.Check) }
        val outcome = saver(writer, files = files).save(shot)
        assertEquals(RawShotOutcome.JpgOnly(RAW_NOT_CLEAN_MESSAGE, "the DNG writer refused the file: a planted Make tag"), outcome)
        assertEquals("The RAW file couldn't be cleaned of camera details, so this photo was saved as JPG.", RAW_NOT_CLEAN_MESSAGE)
        assertFalse("no DNG left behind", files.single().exists())
        assertTrue("logged with its cause", ShadowLog.getLogsForTag("RawShotSaver").any { it.msg.contains("a planted Make tag") })
    }

    /** RECORD -1018: every failure that is not the clean check gets "couldn't be saved", never the -1003 words. */
    @Test
    fun `a refused input, a write error, or a writer that does not say why each show couldn't be saved, and leave no file`() = runTest {
        assertEquals("The RAW file couldn't be saved, so this photo was saved as JPG.", RAW_NOT_SAVED_MESSAGE)
        for (stage in listOf(DngFailureStage.Refused, DngFailureStage.Writing, null)) {
            val files = mutableListOf<File>()
            val writer = FakeDngWriter { _, destination -> destination.writeBytes(byteArrayOf(1)); DngWriteResult.Failed("why", stage) }
            val outcome = saver(writer, files = files).save(shot)
            assertTrue("$stage: $outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_SAVED_MESSAGE)
            assertFalse("$stage: no DNG left behind", files.single().exists())
        }
    }

    @Test
    fun `a writer that throws is treated as a failure to save, and its file is not trusted`() = runTest {
        val files = mutableListOf<File>()
        val writer = FakeDngWriter { _, destination -> destination.writeBytes(byteArrayOf(1)); throw IllegalStateException("boom") }
        val outcome = saver(writer, files = files).save(shot)
        assertTrue("$outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_SAVED_MESSAGE && outcome.cause.contains("boom"))
        assertFalse(files.single().exists())
    }

    @Test
    fun `a writer that reports success with no file is not believed`() = runTest {
        val outcome = saver(FakeDngWriter { _, _ -> DngWriteResult.Written(10) }).save(shot)
        assertTrue("$outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_SAVED_MESSAGE)
    }

    // ── With Forager's own writer (dispatch 2026-10-15-02), end to end ──────────────────────────────────────────────

    @Test
    fun `with the real writer, a planted Make tag fails the clean check and the -1003 words show`() = runTest {
        val files = mutableListOf<File>()
        val writer = ForagerDngWriter(listOf("sm-s908u"), beforeCheck = TiffPatch::plantMake)
        val outcome = saver(writer, free = Long.MAX_VALUE, files = files).save(RawShot(SyntheticDng.full(), null))
        assertTrue("$outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_CLEAN_MESSAGE)
        assertFalse(files.single().exists())
    }

    @Test
    fun `with the real writer, an input it refuses shows couldn't be saved`() = runTest {
        val good = SyntheticDng.full()
        val mismatched = good.copy(samples = ShortArray(good.samples.size - 1))
        val outcome = saver(ForagerDngWriter(listOf("sm-s908u")), free = Long.MAX_VALUE).save(RawShot(mismatched, null))
        assertTrue("$outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_SAVED_MESSAGE)
    }

    @Test
    fun `with the real writer, a file that cannot be written shows couldn't be saved`() = runTest {
        // The destination is a directory, so the write itself fails.
        val dir = File(tmp.root, "a-directory.dng").apply { mkdirs() }
        val saver = RawShotSaver(newRawFile = { dir }, freeBytes = { Long.MAX_VALUE }, writer = ForagerDngWriter(listOf("sm-s908u")), ioDispatcher = Dispatchers.Unconfined)
        val outcome = saver.save(RawShot(SyntheticDng.full(), null))
        assertTrue("$outcome", outcome is RawShotOutcome.JpgOnly && outcome.message == RAW_NOT_SAVED_MESSAGE)
    }

    @Test
    fun `below 500 MB plus the shot's worst case the DNG is never written, to the byte`() = runTest {
        val floor = LOW_STORAGE_FLOOR_BYTES + worstCaseRawShotBytes(4, 4)
        val writer = FakeDngWriter { _, destination -> destination.writeBytes(byteArrayOf(1)); DngWriteResult.Written(1) }
        val low = saver(writer, free = floor - 1).save(shot)
        assertTrue("$low", low is RawShotOutcome.JpgOnly && low.message == RAW_LOW_STORAGE_MESSAGE)
        assertEquals("the writer was never asked", 0, writer.images.size)
        assertEquals("Storage is low, so this photo was saved as JPG.", RAW_LOW_STORAGE_MESSAGE)

        assertTrue("at the floor it is written", saver(writer, free = floor).save(shot) is RawShotOutcome.Kept)
    }

    @Test
    fun `a shot the camera could not describe keeps its JPG, says so, and never reaches the writer`() = runTest {
        val writer = FakeDngWriter { _, _ -> error("not reached") }
        val outcome = saver(writer).save(RawShot(dng = null, dngUnavailable = "the capture result carried no neutral colour point"))
        assertEquals(RawShotOutcome.JpgOnly(RAW_NOT_SAVED_MESSAGE, "no DNG input: the capture result carried no neutral colour point"), outcome)
        assertEquals(0, writer.images.size)
    }

    @Test
    fun `production writes with Forager's own writer, which writes a real DNG and checks it`() = runTest {
        val writer = productionDngWriter(listOf("sm-s908u", "samsung"))
        assertTrue("the real writer, not a stand-in: ${writer::class.java.name}", writer is ForagerDngWriter)
        val target = File(tmp.root, "real.dng")
        val result = writer.write(SyntheticDng.full(), target)
        assertTrue("$result", result is DngWriteResult.Written)
        assertArrayEquals("a TIFF, little-endian", byteArrayOf(0x49, 0x49, 0x2A, 0x00), target.readBytes().copyOf(4))
    }
}

/** A [DngWriter] that runs [behaviour] and records every image it was given. */
internal class FakeDngWriter(private val behaviour: (DngImage, File) -> DngWriteResult) : DngWriter {
    val images = mutableListOf<DngImage>()

    override fun write(image: DngImage, destination: File): DngWriteResult {
        images += image
        return behaviour(image, destination)
    }
}
