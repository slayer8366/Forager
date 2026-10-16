package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngDefaultCrop
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The lens chooser's hard crop in the DNG (dispatch 2026-10-16-04, RECORD -1107: "Hard crop"), written through the writer's
 * real entry point, [ForagerDngWriter.write], and read back from disk. Dev branch only: the field is null on every file a
 * release build writes, and with it null the writer is unchanged ([ForagerDngWriterTest] holds that file as it was).
 */
class DngDefaultCropTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(image: DngImage, name: String): File {
        val file = File(tmp.root, name)
        val result = ForagerDngWriter(listOf("ExampleMaker")).write(image, file)
        if (result !is DngWriteResult.Written) fail("expected Written, got $result")
        return file
    }

    private fun tags(file: File): Map<Int, List<Long>?> =
        TiffReader.read(file).ifd0.entries.associate { it.tag to runCatching { it.unsignedIntegers() }.getOrNull() }

    @Test
    fun `a zoomed shot carries DefaultCropOrigin and DefaultCropSize with the values given`() {
        val image = SyntheticDng.minimal(width = 64, height = 48, activeArea = intArrayOf(0, 0, 48, 64))
            .copy(defaultCrop = DngDefaultCrop(originH = 16, originV = 12, sizeH = 32, sizeV = 24))
        val tags = tags(write(image, "zoomed.dng"))
        assertEquals("DefaultCropOrigin, H then V", listOf(16L, 12L), tags[50719])
        assertEquals("DefaultCropSize, H then V", listOf(32L, 24L), tags[50720])
    }

    @Test
    fun `the whole frame stays in the file under the crop, sample for sample`() {
        val image = SyntheticDng.minimal(width = 64, height = 48, cfa = CfaPattern.GRBG, activeArea = intArrayOf(0, 0, 48, 64))
            .copy(defaultCrop = DngDefaultCrop(originH = 16, originV = 12, sizeH = 32, sizeV = 24))
        val file = write(image, "whole.dng")
        val tags = tags(file)
        assertEquals("ImageWidth is the whole frame's", listOf(64L), tags[256])
        assertEquals("ImageLength is the whole frame's", listOf(48L), tags[257])
        assertArrayEquals("every sample of the uncropped mosaic decodes back", image.samples, decodeDngSamples(file, TableDecoder::decode))
    }

    @Test
    fun `with no crop the file has neither tag, and a crop changes those two tags and nothing else`() {
        val plain = SyntheticDng.minimal(activeArea = intArrayOf(0, 0, 48, 64))
        val cropped = plain.copy(defaultCrop = DngDefaultCrop(8, 6, 48, 36))
        val plainTags = tags(write(plain, "plain.dng"))
        val croppedTags = tags(write(cropped, "cropped.dng"))
        assertNull("no DefaultCropOrigin without a crop", plainTags[50719])
        assertNull("no DefaultCropSize without a crop", plainTags[50720])
        assertEquals("the tags that differ", setOf(50719, 50720), croppedTags.keys - plainTags.keys)
        assertTrue("no tag is lost", (plainTags.keys - croppedTags.keys).isEmpty())
        for (tag in plainTags.keys - setOf(273, 279, 324, 325)) {
            assertEquals("tag $tag is the same with or without the crop", plainTags[tag], croppedTags[tag])
        }
    }

    @Test
    fun `a crop that does not fit inside the active area is refused before a byte is written`() {
        val image = SyntheticDng.minimal(width = 64, height = 48, activeArea = intArrayOf(0, 2, 46, 62))
            .copy(defaultCrop = DngDefaultCrop(originH = 40, originV = 0, sizeH = 32, sizeV = 24))
        val file = File(tmp.root, "refused.dng")
        val result = ForagerDngWriter(emptyList()).write(image, file)
        assertTrue("refused: $result", result is DngWriteResult.Failed && "default crop" in result.reason)
        assertTrue("no file is left", !file.exists())
    }
}
