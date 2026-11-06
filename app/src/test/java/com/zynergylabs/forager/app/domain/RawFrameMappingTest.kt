package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The adapter's rules (`domain/RawFrameMapping.kt`): which camera value fills which field of the writer's input, which
 * missing value means no DNG, and which only leaves an optional field out. Plain JUnit on plain readings; the copy out of
 * the platform's types is device-only (`photo/CameraXRawCapture.kt`).
 */
class RawFrameMappingTest {

    private val identity = List(9) { SRational(if (it % 4 == 0) 1 else 0, 1) }
    private val cm1 = List(9) { SRational(it + 1, 1024) }
    private val cm2 = List(9) { SRational(-(it + 1), 512) }

    private fun readings(
        width: Int = 8,
        height: Int = 6,
        samples: ShortArray = ShortArray(width * height) { it.toShort() },
        cfa: Int? = 1,
        staticBlack: IntArray? = intArrayOf(64, 65, 66, 67),
        dynamicBlack: FloatArray? = null,
        staticWhite: Int? = 1023,
        dynamicWhite: Int? = null,
        preCorrection: IntArray? = intArrayOf(0, 0, width, height),
        illuminant1: Int? = 21,
        illuminant2: Int? = 17,
        colorTransform1: List<SRational>? = cm1,
        colorTransform2: List<SRational>? = cm2,
        neutral: List<URational>? = listOf(URational(1, 2), URational(1, 1), URational(2, 3)),
        noise: List<Pair<Double, Double>>? = listOf(1e-5 to 2e-7, 3e-5 to 4e-7, 5e-5 to 6e-7, 7e-5 to 8e-7),
        shadingColumns: Int? = 2,
        shadingRows: Int? = 2,
        shadingGains: FloatArray? = FloatArray(16) { 1f + it / 10f },
        boost: Int? = 100,
        orientation: Int = 6,
    ) = RawFrameReadings(
        width = width,
        height = height,
        samples = samples,
        colorFilterArrangement = cfa,
        staticBlackLevel = staticBlack,
        dynamicBlackLevel = dynamicBlack,
        staticWhiteLevel = staticWhite,
        dynamicWhiteLevel = dynamicWhite,
        preCorrectionActiveArray = preCorrection,
        referenceIlluminant1 = illuminant1,
        referenceIlluminant2 = illuminant2,
        colorTransform1 = colorTransform1,
        colorTransform2 = colorTransform2,
        forwardMatrix1 = identity,
        forwardMatrix2 = identity,
        calibrationTransform1 = identity,
        calibrationTransform2 = identity,
        neutralColorPoint = neutral,
        noiseProfile = noise,
        lensShadingColumns = shadingColumns,
        lensShadingRows = shadingRows,
        lensShadingGains = shadingGains,
        postRawSensitivityBoost = boost,
        orientation = orientation,
    )

    private fun mapped(r: RawFrameReadings) = when (val m = mapRawFrame(r)) {
        is RawFrameMapping.Mapped -> m
        is RawFrameMapping.Unsupported -> throw AssertionError("expected a DNG input, got: ${m.reason}")
    }

    private fun unsupported(r: RawFrameReadings): String = when (val m = mapRawFrame(r)) {
        is RawFrameMapping.Unsupported -> m.reason
        is RawFrameMapping.Mapped -> throw AssertionError("expected no DNG input, got one")
    }

    @Test
    fun `a full reading maps every value through as the camera gave it`() {
        val r = readings()
        val image = mapped(r).image
        assertEquals(8, image.width)
        assertEquals(6, image.height)
        assertSame("the frame itself, not a copy", r.samples, image.samples)
        assertEquals(CfaPattern.GRBG, image.cfa)
        assertEquals(listOf(64f, 65f, 66f, 67f), image.blackLevel)
        assertEquals(1023, image.whiteLevel)
        assertArrayEquals(intArrayOf(0, 0, 6, 8), image.activeArea)
        assertEquals(21, image.calibration1.illuminant)
        assertEquals(cm1, image.calibration1.colorMatrix)
        assertEquals(identity, image.calibration1.forwardMatrix)
        assertEquals(identity, image.calibration1.cameraCalibration)
        assertEquals(17, image.calibration2?.illuminant)
        assertEquals(cm2, image.calibration2?.colorMatrix)
        assertEquals(listOf(URational(1, 2), URational(1, 1), URational(2, 3)), image.asShotNeutral)
        assertEquals("(S, O) per CFA channel, flattened", listOf(1e-5, 2e-7, 3e-5, 4e-7, 5e-5, 6e-7, 7e-5, 8e-7), image.noiseProfile)
        assertEquals(2, image.lensShading?.columns)
        assertEquals(2, image.lensShading?.rows)
        assertEquals(16, image.lensShading?.gains?.size)
        assertEquals("no boost is 0 EV", SRational(0, 100), image.baselineExposure)
        assertEquals("the JPG's orientation", 6, image.orientation)
    }

    @Test
    fun `each phone's CFA phase comes from its own reading, never a constant`() {
        // RECORD -998: GRBG on the S22 Ultra, GBRG on the S26 Ultra main camera.
        assertEquals(CfaPattern.RGGB, mapped(readings(cfa = 0)).image.cfa)
        assertEquals(CfaPattern.GRBG, mapped(readings(cfa = 1)).image.cfa)
        assertEquals(CfaPattern.GBRG, mapped(readings(cfa = 2)).image.cfa)
        assertEquals(CfaPattern.BGGR, mapped(readings(cfa = 3)).image.cfa)
        assertTrue(unsupported(readings(cfa = 4)).contains("not a Bayer"))
        assertTrue(unsupported(readings(cfa = null)).contains("no colour filter"))
    }

    @Test
    fun `the frame's own black and white levels are used when the camera reports them, the static ones otherwise`() {
        val dynamic = mapped(readings(dynamicBlack = floatArrayOf(63.5f, 64f, 64.25f, 65f), dynamicWhite = 1000)).image
        assertEquals(listOf(63.5f, 64f, 64.25f, 65f), dynamic.blackLevel)
        assertEquals(1000, dynamic.whiteLevel)
        val malformed = mapped(readings(dynamicBlack = floatArrayOf(1f, 2f)))
        assertEquals("a dynamic level that is not four values gives way to the static pattern", listOf(64f, 65f, 66f, 67f), malformed.image.blackLevel)
        assertTrue(malformed.notes.any { it.contains("static pattern") })
        assertEquals("a zero dynamic white level is not a level", 1023, mapped(readings(dynamicWhite = 0)).image.whiteLevel)
    }

    @Test
    fun `a value the file needs and the camera did not give means no DNG, and says which`() {
        assertTrue(unsupported(readings(staticBlack = null)).contains("black level"))
        assertTrue(unsupported(readings(staticWhite = null)).contains("white level"))
        assertTrue(unsupported(readings(illuminant1 = null)).contains("illuminant"))
        assertTrue(unsupported(readings(colorTransform1 = null)).contains("colour matrix"))
        assertTrue(unsupported(readings(colorTransform1 = cm1.take(6))).contains("colour matrix"))
        assertTrue(unsupported(readings(neutral = null)).contains("neutral"))
        assertTrue(unsupported(readings(orientation = 0)).contains("orientation"))
        assertTrue(unsupported(readings(width = 8, height = 6, samples = ShortArray(47))).contains("47 samples"))
    }

    @Test
    fun `optional values that are missing or malformed are left out, and noted`() {
        val sparse = mapped(readings(illuminant2 = null, noise = null, shadingGains = null, boost = null, preCorrection = null))
        assertNull(sparse.image.calibration2)
        assertNull(sparse.image.noiseProfile)
        assertNull(sparse.image.lensShading)
        assertNull(sparse.image.baselineExposure)
        assertNull(sparse.image.activeArea)
        assertTrue(sparse.notes.toString(), sparse.notes.any { it.contains("noise profile") } && sparse.notes.any { it.contains("lens shading") } && sparse.notes.any { it.contains("active array") })

        val odd = mapped(readings(colorTransform2 = null, noise = listOf(1.0 to 2.0), shadingGains = FloatArray(15)))
        assertNull("a second illuminant without its matrix describes nothing", odd.image.calibration2)
        assertNull(odd.image.noiseProfile)
        assertNull(odd.image.lensShading)
        assertEquals(3, odd.notes.size)
    }

    @Test
    fun `the active area follows the platform writer's rule for where the buffer starts`() {
        // A buffer that is the pre-correction rectangle starts at its origin; a larger one (the pixel array) keeps the offset.
        assertArrayEquals(intArrayOf(0, 0, 6, 8), mapped(readings(preCorrection = intArrayOf(10, 20, 18, 26))).image.activeArea)
        assertArrayEquals(intArrayOf(1, 2, 5, 6), mapped(readings(preCorrection = intArrayOf(2, 1, 6, 5))).image.activeArea)
        assertTrue(unsupported(readings(preCorrection = intArrayOf(4, 4, 20, 20))).contains("does not fit"))
    }

    /**
     * Part B's reading of the contract (Amendment 2, item 4): `cfa` and `blackLevel` describe the 2x2 cell at the top-left
     * of the active area, as the DNG specification has the pattern start there. The camera reports both for the cell at
     * the top-left of the pixel array, so an active area that starts on an odd row or column moves the phase.
     */
    @Test
    fun `an active area offset by an odd column or row moves the CFA phase and the black levels with it`() {
        // A pixel-array buffer (8x6) whose active area is smaller, so the offsets apply. Sensor cell, row-major: G R / B G.
        val black = intArrayOf(10, 11, 12, 13)
        val oddColumn = mapped(readings(preCorrection = intArrayOf(1, 0, 7, 6), staticBlack = black)).image
        assertEquals("one column in: R G / G B", CfaPattern.RGGB, oddColumn.cfa)
        assertEquals(listOf(11f, 10f, 13f, 12f), oddColumn.blackLevel)

        val oddRow = mapped(readings(preCorrection = intArrayOf(0, 1, 8, 5), staticBlack = black)).image
        assertEquals("one row in: B G / G R", CfaPattern.BGGR, oddRow.cfa)
        assertEquals(listOf(12f, 13f, 10f, 11f), oddRow.blackLevel)

        val both = mapped(readings(preCorrection = intArrayOf(1, 1, 7, 5), staticBlack = black, dynamicBlack = floatArrayOf(20f, 21f, 22f, 23f))).image
        assertEquals("one of each: G B / R G", CfaPattern.GBRG, both.cfa)
        assertEquals("the frame's own levels move the same way", listOf(23f, 22f, 21f, 20f), both.blackLevel)

        val even = mapped(readings(preCorrection = intArrayOf(2, 2, 6, 4), staticBlack = black)).image
        assertEquals("an even offset changes nothing", CfaPattern.GRBG, even.cfa)
        assertEquals(listOf(10f, 11f, 12f, 13f), even.blackLevel)
    }

    @Test
    fun `the lens-shading gains go through in the layout part B reads, point by point with four channels each`() {
        // (row * columns + col) * 4 + channel, as the platform's copyGainFactors lays them out.
        val gains = FloatArray(2 * 3 * 4) { it.toFloat() }
        val image = mapped(readings(shadingColumns = 3, shadingRows = 2, shadingGains = gains)).image
        assertEquals(3, image.lensShading?.columns)
        assertEquals(2, image.lensShading?.rows)
        assertEquals("row 1, column 2, channel 3", ((1 * 3 + 2) * 4 + 3).toFloat(), image.lensShading!!.gains[(1 * 3 + 2) * 4 + 3])
        assertSame(gains, image.lensShading!!.gains)
    }

    @Test
    fun `baseline exposure is the post-RAW boost in EV, hundredths`() {
        assertEquals("200 is one stop", SRational(100, 100), mapped(readings(boost = 200)).image.baselineExposure)
        assertEquals(SRational(-100, 100), mapped(readings(boost = 50)).image.baselineExposure)
    }

    @Test
    fun `the input type has nowhere to put make, model, software, time or location`() {
        // RECORD -975 and -1008. A structural check: if a field for any of them is ever added to the contract, this fails
        // and the mapping must say where its value comes from, and why it may be there.
        val forbidden = listOf("make", "model", "software", "time", "date", "gps", "location", "latitude", "longitude")
        val fields = com.zynergylabs.forager.app.domain.raw.DngImage::class.java.declaredFields.map { it.name.lowercase() }
        for (word in forbidden) {
            if (fields.any { it.contains(word) }) fail("DngImage has a field for '$word': $fields")
        }
    }
}
