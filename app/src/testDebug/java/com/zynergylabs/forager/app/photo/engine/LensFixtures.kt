package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.PixelSize

/**
 * Both test phones' back cameras as their characteristics read, for the lens chooser's tests (dispatch 2026-10-16-04).
 * Focal lengths, sensor sizes, colour filters and RAW sizes are the reach probe's logged ones (RECORD -1095, -1101;
 * `~/Zynergy/device-evidence/2026-10-16-camera-reach-s2{6,2}/reach.jsonl`); JPEG sizes, active arrays, minimum focus
 * distances and sensor orientations are the dumpsys camera tables' (the S26's of 2026-10-15, the S22's of 2026-10-16,
 * read by the planner and by this coder, RECORD -1106). Only the JPEG sizes the lens rule can choose between are listed
 * (each lens's 4:3 sizes, and one wider size so the 4:3 filter is exercised); the tables list more.
 */
internal object LensFixtures {
    private fun size(w: Int, h: Int) = PixelSize(w, h)
    private val preview = listOf(size(1920, 1080), size(1440, 1080), size(960, 720), size(640, 480))

    private fun reading(
        id: String,
        focal: Float,
        sizeMm: List<Float>,
        cfa: Int,
        jpeg: List<PixelSize>,
        raw: PixelSize,
        minFocus: Float,
        back: Boolean = true,
    ) = LensReading(
        cameraId = id,
        back = back,
        focalLengths = listOf(focal),
        physicalSizeMm = sizeMm,
        cfa = cfa,
        jpegSizes = jpeg,
        previewSizes = preview,
        rawCapable = true,
        rawSizes = listOf(raw),
        activeArray = raw,
        minFocusDiopters = minFocus,
        sensorOrientation = 90,
    )

    // CFA codes as CameraCharacteristics reports them: 0 RGGB, 1 GRBG, 2 GBRG.
    val s26Logical = reading("0", 6.5f, listOf(9.792f, 7.344f), 2, listOf(size(4080, 3060), size(4000, 3000), size(3648, 2736), size(4000, 2252)), size(4080, 3060), 10f)
    val s26 = listOf(
        reading("2", 2.2f, listOf(5.712f, 4.284f), 1, listOf(size(4080, 3060), size(4000, 3000), size(4000, 2252), size(1920, 1440)), size(4080, 3060), 20f),
        reading("5", 6.5f, listOf(9.792f, 7.344f), 2, listOf(size(4080, 3060), size(4000, 2252), size(1920, 1440)), size(4080, 3060), 10f),
        reading("6", 7.0f, listOf(3.648f, 2.736f), 2, listOf(size(3648, 2736), size(3648, 2052), size(1920, 1440)), size(3648, 2736), 2.5f),
        reading("7", 18.6f, listOf(5.712f, 4.284f), 1, listOf(size(4080, 3060), size(4000, 2252), size(1920, 1440)), size(4080, 3060), 1.25f),
    )

    val s22Logical = reading("0", 6.4f, listOf(9.6f, 7.2f), 1, listOf(size(4000, 3000), size(3648, 2736), size(4000, 2252)), size(4000, 3000), 10f)
    val s22 = listOf(
        reading("2", 2.2f, listOf(5.6f, 4.2f), 0, listOf(size(4000, 3000), size(4000, 2252), size(1920, 1440)), size(4000, 3000), 10f),
        reading("5", 6.4f, listOf(9.6f, 7.2f), 1, listOf(size(4000, 3000), size(4000, 2252), size(1920, 1440)), size(4000, 3000), 10f),
        reading("6", 7.9f, listOf(4.08576f, 3.06432f), 1, listOf(size(3648, 2736), size(3648, 2052), size(1920, 1440)), size(3648, 2736), 2.5f),
        reading("7", 27.2f, listOf(4.08576f, 3.06432f), 1, listOf(size(3648, 2736), size(3648, 2052), size(1920, 1440)), size(3648, 2736), 1.25f),
    )

    fun s26Set(): LensSet = (buildLensSet(s26Logical, s26) as LensSetOutcome.Built).set
    fun s22Set(): LensSet = (buildLensSet(s22Logical, s22) as LensSetOutcome.Built).set

    /** The S26's set with its 3x telephoto unable to give RAW, for the no-RAW line. Not a real phone. */
    fun s26SetWithoutRawOn6(): LensSet =
        (buildLensSet(s26Logical, s26.map { if (it.cameraId == "6") it.copy(rawCapable = false) else it }) as LensSetOutcome.Built).set
}
