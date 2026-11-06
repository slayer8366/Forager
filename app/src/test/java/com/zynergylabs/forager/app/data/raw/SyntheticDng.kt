package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngCalibration
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.LensShadingMap
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import kotlin.random.Random

/**
 * Synthetic DNG inputs for the writer tests. No file or reading from a device (dispatch 2026-10-15-01, Amendment 1,
 * item 10): every value here is made up, shaped like what a phone reports.
 */
internal object SyntheticDng {

    /** The CFAPattern bytes the DNG must carry for each phase (TIFF-EP: 0 red, 1 green, 2 blue), written out by hand. */
    val CFA_BYTES: Map<CfaPattern, List<Int>> = mapOf(
        CfaPattern.RGGB to listOf(0, 1, 1, 2),
        CfaPattern.GRBG to listOf(1, 0, 2, 1),
        CfaPattern.GBRG to listOf(1, 2, 0, 1),
        CfaPattern.BGGR to listOf(2, 1, 1, 0),
    )

    /**
     * Samples that exercise every Huffman category the lossless JPEG stream can carry: a smooth 10-bit scene, a band of
     * full-range 16-bit noise, and planted jumps of exactly 32768 between like-coloured neighbours (the category-16
     * difference, which the stream stores with no extra bits), plus 0 and 65535 side by side.
     */
    fun samples(width: Int, height: Int, seed: Int = 7): ShortArray {
        val random = Random(seed)
        val out = ShortArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val smooth = 64 + ((x * 7 + y * 3) % 900) + random.nextInt(0, 24)
                val value = if (y % 11 == 5) random.nextInt(0, 65536) else smooth
                out[y * width + x] = value.toShort()
            }
        }
        if (width >= 6 && height >= 2) {
            // Like-coloured neighbours are two columns apart.
            out[0] = 0
            out[2] = 32768.toShort()
            out[4] = 0
            out[width + 1] = 65535.toShort()
            out[width + 3] = 0
            out[width + 5] = 65535.toShort()
        }
        return out
    }

    private fun matrix(seed: Int): List<SRational> = List(9) { i -> SRational((i * 37 + seed * 101) % 2000 - 1000, 1024) }

    fun minimal(
        width: Int = 64,
        height: Int = 48,
        cfa: CfaPattern = CfaPattern.RGGB,
        samples: ShortArray = samples(width, height),
        orientation: Int = 1,
        activeArea: IntArray? = null,
    ): DngImage = DngImage(
        width = width,
        height = height,
        samples = samples,
        cfa = cfa,
        blackLevel = listOf(64f, 65f, 63f, 64f),
        whiteLevel = 1023,
        activeArea = activeArea,
        calibration1 = DngCalibration(illuminant = 21, colorMatrix = matrix(1)),
        asShotNeutral = listOf(URational(512, 1024), URational(1024, 1024), URational(700, 1024)),
        orientation = orientation,
    )

    /** Every optional value filled in, with fractional black levels and an active area that is not the whole image. */
    fun full(width: Int = 64, height: Int = 48, cfa: CfaPattern = CfaPattern.GRBG): DngImage {
        val columns = 17
        val rows = 13
        val gains = FloatArray(columns * rows * 4) { i -> 1f + (i % 97) / 64f }
        return DngImage(
            width = width,
            height = height,
            samples = samples(width, height, seed = 11),
            cfa = cfa,
            blackLevel = listOf(64.25f, 63.75f, 64.5f, 64f),
            whiteLevel = 4095,
            activeArea = intArrayOf(2, 4, height - 2, width - 4),
            calibration1 = DngCalibration(
                illuminant = 17,
                colorMatrix = matrix(2),
                forwardMatrix = matrix(3),
                cameraCalibration = matrix(4),
            ),
            calibration2 = DngCalibration(
                illuminant = 21,
                colorMatrix = matrix(5),
                forwardMatrix = matrix(6),
                cameraCalibration = matrix(7),
            ),
            asShotNeutral = listOf(URational(1, 2), URational(1, 1), URational(3, 5)),
            noiseProfile = listOf(1.1e-5, 2.1e-7, 1.2e-5, 2.2e-7, 1.3e-5, 2.3e-7, 1.4e-5, 2.4e-7),
            lensShading = LensShadingMap(columns, rows, gains),
            orientation = 6,
            baselineExposure = SRational(-1, 2),
        )
    }
}
