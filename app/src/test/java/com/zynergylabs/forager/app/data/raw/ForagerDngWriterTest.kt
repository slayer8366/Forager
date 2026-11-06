package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Forager's own DNG writer (dispatch 2026-10-15-02), driven through its real entry point, [ForagerDngWriter.write],
 * and read back from disk. Plain JUnit: the writer has no Android imports, so no Robolectric.
 *
 * The writer offers little-endian output only, so every round trip here is little-endian (dispatch, test 1).
 */
class ForagerDngWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val phoneStrings = listOf("ExampleMaker", "SM-S908U", "exampleBrand", "exampleBrand/b0q/b0q:15/AP3A/1:user/release-keys")

    private fun dest(name: String = "shot.dng") = File(tmp.root, name)

    private fun writeOk(image: DngImage, file: File = dest()): File {
        val result = ForagerDngWriter(phoneStrings).write(image, file)
        if (result !is DngWriteResult.Written) fail("expected Written, got $result")
        assertEquals("Written reports the file's size", file.length(), (result as DngWriteResult.Written).bytes)
        return file
    }

    private fun failedReason(result: DngWriteResult): String {
        if (result !is DngWriteResult.Failed) fail("expected Failed, got $result")
        return (result as DngWriteResult.Failed).reason
    }

    private fun ifd0(file: File) = TiffReader.read(file).ifd0

    private fun ints(file: File, tag: Int): List<Long> =
        ifd0(file).find(tag)?.unsignedIntegers() ?: error("tag $tag is missing")

    private fun rationals(file: File, tag: Int): List<Pair<Long, Long>> =
        ifd0(file).find(tag)?.rationals() ?: error("tag $tag is missing")

    // ---- 1. Round trip ----

    @Test
    fun `a 64x48 image in each CFA phase decodes back to its samples bit for bit, through both decoders`() {
        for (cfa in CfaPattern.values()) {
            val image = SyntheticDng.minimal(cfa = cfa)
            val file = writeOk(image, dest("$cfa.dng"))
            assertArrayEquals("$cfa, table decoder", image.samples, decodeDngSamples(file, TableDecoder::decode))
            assertArrayEquals("$cfa, bit-string decoder", image.samples, decodeDngSamples(file, BitStringDecoder::decode))
        }
    }

    @Test
    fun `an image spanning several tiles, with partial tiles at the right and bottom, decodes back bit for bit`() {
        val image = SyntheticDng.minimal(width = 520, height = 300, cfa = CfaPattern.GBRG)
        val file = writeOk(image)
        assertTrue("more than one tile", ints(file, 324).size > 1)
        assertArrayEquals(image.samples, decodeDngSamples(file, TableDecoder::decode))
        assertArrayEquals(image.samples, decodeDngSamples(file, BitStringDecoder::decode))
    }

    @Test
    fun `each tile is a two-component lossless JPEG with the left predictor at 16-bit precision`() {
        val file = writeOk(SyntheticDng.minimal())
        val offset = ints(file, 324).first().toInt()
        val count = ints(file, 325).first().toInt()
        val frame = TableDecoder.decode(file.readBytes().copyOfRange(offset, offset + count))
        assertEquals(16, frame.precision)
        assertEquals(2, frame.components)
        assertEquals(1, frame.predictor)
        assertEquals(ints(file, 322).single().toInt(), frame.width * 2)
        assertEquals(ints(file, 323).single().toInt(), frame.height)
    }

    @Test
    fun `an image narrower or shorter than one full tile gets tiles no larger than itself, rounded up to 16`() {
        // One common raw library places a tile's samples by wrapping at the image width when the tile is wider than
        // the image, so a 64-wide image in 256-wide tiles reads back scrambled there from its second row on (seen on a
        // CI-written file, dispatch 2026-10-15-02 completion report). TIFF 6.0 requires tile sizes in multiples of 16.
        val small = writeOk(SyntheticDng.minimal(width = 64, height = 48), dest("small.dng"))
        assertEquals(listOf(64L), ints(small, 322))
        assertEquals(listOf(48L), ints(small, 323))
        val odd = writeOk(SyntheticDng.minimal(width = 70, height = 34), dest("odd.dng"))
        assertEquals(listOf(80L), ints(odd, 322))
        assertEquals(listOf(48L), ints(odd, 323))
        assertArrayEquals(SyntheticDng.minimal(width = 70, height = 34).samples, decodeDngSamples(odd, TableDecoder::decode))
        val large = writeOk(SyntheticDng.minimal(width = 520, height = 300), dest("large.dng"))
        assertEquals(listOf(256L), ints(large, 322))
        assertEquals(listOf(256L), ints(large, 323))
    }

    // ---- 2. Required tags ----

    @Test
    fun `every required tag is present with the given value`() {
        for (cfa in CfaPattern.values()) {
            val image = SyntheticDng.minimal(cfa = cfa, orientation = 8, activeArea = intArrayOf(0, 2, 46, 62))
            val file = writeOk(image, dest("$cfa.dng"))
            val ifd = ifd0(file)
            assertEquals(listOf(1L, 4L, 0L, 0L), ints(file, 50706))
            assertEquals(listOf(1L, 1L, 0L, 0L), ints(file, 50707))
            assertEquals("Forager", ifd.find(50708)!!.ascii())
            assertEquals(listOf(0L), ints(file, 254))
            assertEquals(listOf(64L), ints(file, 256))
            assertEquals(listOf(48L), ints(file, 257))
            assertEquals(listOf(16L), ints(file, 258))
            assertEquals(listOf(7L), ints(file, 259))
            assertEquals(listOf(32803L), ints(file, 262))
            assertEquals(listOf(8L), ints(file, 274))
            assertEquals(listOf(1L), ints(file, 277))
            assertEquals(listOf(2L, 2L), ints(file, 33421))
            assertEquals("$cfa", SyntheticDng.CFA_BYTES.getValue(cfa).map { it.toLong() }, ints(file, 33422))
            assertEquals(listOf(0L, 1L, 2L), ints(file, 50710))
            assertEquals(listOf(1L), ints(file, 50711))
            assertEquals(listOf(2L, 2L), ints(file, 50713))
            assertEquals(listOf(64L, 65L, 63L, 64L), ints(file, 50714))
            assertEquals(listOf(1023L), ints(file, 50717))
            assertEquals(
                image.calibration1.colorMatrix.map { it.numerator.toLong() to it.denominator.toLong() },
                rationals(file, 50721),
            )
            assertEquals(listOf(21L), ints(file, 50778))
            assertEquals(image.asShotNeutral.map { it.numerator to it.denominator }, rationals(file, 50728))
            assertEquals(listOf(0L, 2L, 46L, 62L), ints(file, 50829))
            assertTrue(ints(file, 322).single() > 0 && ints(file, 323).single() > 0)
            assertEquals(ints(file, 324).size, ints(file, 325).size)
        }
    }

    @Test
    fun `an image with no active area given carries no ActiveArea tag`() {
        val file = writeOk(SyntheticDng.minimal(activeArea = null))
        assertNull(ifd0(file).find(50829))
    }

    // ---- 3. Forbidden tags ----

    @Test
    fun `no forbidden tag and no other text is ever written, whatever the input`() {
        val forbidden = mapOf(
            271 to "Make", 272 to "Model", 305 to "Software", 306 to "DateTime", 36867 to "DateTimeOriginal",
            36868 to "DateTimeDigitized", 34665 to "ExifIFD", 34853 to "GPSInfo", 40965 to "InteropIFD",
            50735 to "CameraSerialNumber", 50709 to "LocalizedCameraModel", 330 to "SubIFDs",
        )
        val inputs = CfaPattern.values().flatMap { cfa ->
            listOf(SyntheticDng.minimal(cfa = cfa, orientation = 1), SyntheticDng.full(cfa = cfa))
        }
        for ((index, image) in inputs.withIndex()) {
            val file = writeOk(image, dest("in$index.dng"))
            val structure = TiffReader.read(file)
            assertEquals("one IFD, no chain and no sub-IFDs", 1, structure.ifds.size)
            for (entry in structure.ifd0.entries) {
                assertFalse("input $index wrote ${forbidden[entry.tag]} (${entry.tag})", entry.tag in forbidden)
            }
            val asciiTags = structure.ifd0.entries.filter { it.type == TiffEntry.TYPE_ASCII }.map { it.tag }
            assertEquals("the only text is UniqueCameraModel", listOf(50708), asciiTags)
        }
    }

    // ---- 4. The check fails closed ----

    @Test
    fun `the same image is written when nothing is planted, the control for the planted cases`() {
        val file = dest()
        val result = ForagerDngWriter(phoneStrings, beforeCheck = {}).write(SyntheticDng.minimal(), file)
        assertTrue("$result", result is DngWriteResult.Written)
        assertTrue(file.exists())
    }

    @Test
    fun `a planted Make tag makes write fail and leaves no file`() {
        val file = dest()
        val result = ForagerDngWriter(phoneStrings, beforeCheck = TiffPatch::plantMake).write(SyntheticDng.minimal(), file)
        val reason = failedReason(result)
        assertTrue(reason, reason.contains("Make (271)"))
        assertFalse("the file is deleted", file.exists())
    }

    @Test
    fun `a planted GPS IFD makes write fail and leaves no file`() {
        val file = dest()
        val result = ForagerDngWriter(phoneStrings, beforeCheck = TiffPatch::plantGpsIfd).write(SyntheticDng.minimal(), file)
        val reason = failedReason(result)
        assertTrue(reason, reason.contains("GPSInfo (34853)"))
        assertFalse("the file is deleted", file.exists())
    }

    @Test
    fun `a forbidden string planted in an ASCII tag makes write fail, leaves no file, and is not repeated in the reason`() {
        val file = dest()
        val writer = ForagerDngWriter(phoneStrings, beforeCheck = { TiffPatch.plantAscii(it, "shot on a sm-s908u") })
        val reason = failedReason(writer.write(SyntheticDng.minimal(), file))
        assertTrue(reason, reason.contains("forbidden string") && reason.contains("270"))
        assertFalse(reason, reason.contains("908", ignoreCase = true))
        assertFalse("the file is deleted", file.exists())
    }

    @Test
    fun `a planted ASCII tag holding no forbidden string passes, so the string case fails on the string itself`() {
        val file = dest()
        val writer = ForagerDngWriter(phoneStrings, beforeCheck = { TiffPatch.plantAscii(it, "a mushroom") })
        assertTrue(writer.write(SyntheticDng.minimal(), file) is DngWriteResult.Written)
    }

    // ---- 5. Calibration round trip ----

    @Test
    fun `matrices, illuminants, NoiseProfile, BaselineExposure and the GainMaps read back as given`() {
        for (cfa in CfaPattern.values()) {
            val image = SyntheticDng.full(cfa = cfa)
            val file = writeOk(image, dest("$cfa.dng"))
            fun pairs(list: List<com.zynergylabs.forager.app.domain.raw.SRational>?) =
                list!!.map { it.numerator.toLong() to it.denominator.toLong() }

            assertEquals(pairs(image.calibration1.colorMatrix), rationals(file, 50721))
            assertEquals(pairs(image.calibration2!!.colorMatrix), rationals(file, 50722))
            assertEquals(pairs(image.calibration1.cameraCalibration), rationals(file, 50723))
            assertEquals(pairs(image.calibration2.cameraCalibration), rationals(file, 50724))
            assertEquals(pairs(image.calibration1.forwardMatrix), rationals(file, 50964))
            assertEquals(pairs(image.calibration2.forwardMatrix), rationals(file, 50965))
            assertEquals(listOf(17L), ints(file, 50778))
            assertEquals(listOf(21L), ints(file, 50779))
            assertEquals(listOf(-1L to 2L), rationals(file, 50730))
            assertEquals(listOf(4095L), ints(file, 50717))

            // Fractional black levels are stored as RATIONAL and come back exactly.
            val black = rationals(file, 50714).map { (n, d) -> n.toDouble() / d }
            assertEquals(image.blackLevel.map { it.toDouble() }, black)

            // NoiseProfile: the camera's four CFA-ordered pairs become one pair per colour plane, in CFAPlaneColor
            // order (red, green, blue), each taken from the first CFA position of that colour.
            val cfaBytes = SyntheticDng.CFA_BYTES.getValue(cfa)
            val expectedNoise = (0..2).flatMap { plane ->
                val i = cfaBytes.indexOf(plane)
                listOf(image.noiseProfile!![2 * i], image.noiseProfile[2 * i + 1])
            }
            assertEquals("$cfa", expectedNoise, ifd0(file).find(51041)!!.doubles())

            assertGainMaps(file, image, cfaBytes)
        }
    }

    private class GainMap(
        val top: Int, val left: Int, val bottom: Int, val right: Int, val plane: Int, val planes: Int,
        val rowPitch: Int, val colPitch: Int, val pointsV: Int, val pointsH: Int,
        val spacingV: Double, val spacingH: Double, val originV: Double, val originH: Double,
        val mapPlanes: Int, val gains: FloatArray,
    )

    /** Parses OpcodeList2 by hand, big-endian as DNG 1.4 chapter 7 requires, independently of the writer's builder. */
    private fun gainMaps(file: File): List<GainMap> {
        val list = ByteBuffer.wrap(ifd0(file).find(51009)!!.value).order(ByteOrder.BIG_ENDIAN)
        val count = list.int
        return List(count) {
            assertEquals("opcode ID GainMap", 9, list.int)
            assertEquals("DNG version 1.3.0.0", 0x01030000, list.int)
            assertEquals("flags: optional", 1, list.int)
            val size = list.int
            val start = list.position()
            val map = GainMap(
                list.int, list.int, list.int, list.int, list.int, list.int, list.int, list.int, list.int, list.int,
                list.double, list.double, list.double, list.double, list.int, FloatArray(0),
            )
            val gains = FloatArray(map.pointsV * map.pointsH * map.mapPlanes) { list.float }
            assertEquals("parameter size", size, list.position() - start)
            GainMap(
                map.top, map.left, map.bottom, map.right, map.plane, map.planes, map.rowPitch, map.colPitch,
                map.pointsV, map.pointsH, map.spacingV, map.spacingH, map.originV, map.originH, map.mapPlanes, gains,
            )
        }.also { assertFalse("nothing after the last opcode", list.hasRemaining()) }
    }

    private fun assertGainMaps(file: File, image: DngImage, cfaBytes: List<Int>) {
        val shading = image.lensShading!!
        val area = image.activeArea!!
        val maps = gainMaps(file)
        assertEquals(4, maps.size)
        // The camera's channels, in its order R, Geven, Godd, B, and where each sits in the 2x2 CFA cell.
        val positions = listOf(
            cfaBytes.indexOf(0),
            (0..1).first { cfaBytes[it] == 1 },
            (2..3).first { cfaBytes[it] == 1 },
            cfaBytes.indexOf(2),
        )
        for ((channel, position) in positions.withIndex()) {
            val map = maps.singleOrNull { it.top == position / 2 && it.left == position % 2 }
                ?: throw AssertionError("no GainMap starts at the cell position of channel $channel")
            assertEquals(area[2] - area[0], map.bottom)
            assertEquals(area[3] - area[1], map.right)
            assertEquals(0, map.plane)
            assertEquals(1, map.planes)
            assertEquals(2, map.rowPitch)
            assertEquals(2, map.colPitch)
            assertEquals(shading.rows, map.pointsV)
            assertEquals(shading.columns, map.pointsH)
            assertEquals(1.0 / (shading.rows - 1), map.spacingV, 0.0)
            assertEquals(1.0 / (shading.columns - 1), map.spacingH, 0.0)
            assertEquals(0.0, map.originV, 0.0)
            assertEquals(0.0, map.originH, 0.0)
            assertEquals(1, map.mapPlanes)
            val expected = FloatArray(shading.rows * shading.columns) { shading.gains[it * 4 + channel] }
            assertArrayEquals("channel $channel", expected, map.gains, 0f)
        }
    }

    // ---- 6. Limits ----

    @Test
    fun `an image above 12_6 MP is refused and leaves no file, while the largest test phone's size is written`() {
        val over = dest("over.dng")
        val big = SyntheticDng.minimal(width = 4100, height = 3100, samples = ShortArray(4100 * 3100))
        val reason = failedReason(ForagerDngWriter(phoneStrings).write(big, over))
        assertTrue(reason, reason.contains("12.6 MP"))
        assertFalse(over.exists())

        // The S26 Ultra's RAW size (RECORD -998), 12.48 MP, is inside the limit.
        val ok = dest("ok.dng")
        val s26 = SyntheticDng.minimal(width = 4080, height = 3060, samples = ShortArray(4080 * 3060) { (it % 1024).toShort() })
        assertTrue(ForagerDngWriter(phoneStrings).write(s26, ok) is DngWriteResult.Written)
    }

    @Test
    fun `an image whose sample array does not match its dimensions is refused and leaves no file`() {
        val file = dest()
        val image = SyntheticDng.minimal(samples = ShortArray(64 * 48 - 1))
        val reason = failedReason(ForagerDngWriter(phoneStrings).write(image, file))
        assertTrue(reason, reason.contains("samples"))
        assertFalse(file.exists())
    }

    @Test
    fun `arrays of the wrong size elsewhere are refused too`() {
        val base = SyntheticDng.full()
        val bad = listOf(
            "black level" to base.copy(blackLevel = listOf(64f, 64f, 64f)),
            "AsShotNeutral" to base.copy(asShotNeutral = base.asShotNeutral.take(2)),
            "colour matrix" to base.copy(calibration1 = base.calibration1.copy(colorMatrix = base.calibration1.colorMatrix.take(8))),
            "NoiseProfile" to base.copy(noiseProfile = base.noiseProfile!!.take(6)),
            "lens shading" to base.copy(lensShading = base.lensShading!!.copy(columns = 16)),
            "active area" to base.copy(activeArea = intArrayOf(0, 0, 49, 64)),
            "orientation" to base.copy(orientation = 9),
        )
        for ((what, image) in bad) {
            val file = dest("$what.dng")
            val result = ForagerDngWriter(phoneStrings).write(image, file)
            assertTrue("$what: $result", result is DngWriteResult.Failed && result.reason.contains(what, ignoreCase = true))
            assertFalse(what, file.exists())
        }
    }

    @Test
    fun `a refused write removes a file already at the destination`() {
        val file = dest()
        file.writeText("an older file")
        val result = ForagerDngWriter(phoneStrings).write(SyntheticDng.minimal(samples = ShortArray(3)), file)
        assertTrue(failedReason(result).contains("samples"))
        assertFalse(file.exists())
    }

    @Test
    fun `a destination that cannot be written gives Failed, not an exception`() {
        val file = File(tmp.root, "no-such-folder/shot.dng")
        val reason = failedReason(ForagerDngWriter(phoneStrings).write(SyntheticDng.minimal(), file))
        assertTrue(reason, reason.contains("could not be written"))
        assertFalse(file.exists())
    }
}
