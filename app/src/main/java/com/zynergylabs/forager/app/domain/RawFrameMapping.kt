package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngCalibration
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.LensShadingMap
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import kotlin.math.ln
import kotlin.math.roundToInt

/*
 * From what the camera reported for one RAW frame to the writer's input (`DngImage`, the contract in dispatch
 * 2026-10-15-02). Plain Kotlin: the camera adapter (`photo/CameraXRawCapture.kt`) copies each value out of the platform's
 * types into [RawFrameReadings], and every rule about which value is used, and what is missing, lives here with a test
 * (`RawFrameMappingTest`).
 *
 * Where each value comes from, and the rule, follows the platform's own DNG writer (AOSP
 * `android_hardware_camera2_DngCreator.cpp`, read 2026-10-15) wherever it decides something, so the files Forager writes
 * describe the sensor the way the phone's own would. What it is never given: make, model, software, time or location
 * (RECORD -975, -1008). A required value the phone did not give is never guessed (CLAUDE.md): the mapping says which one
 * was missing, the shot keeps its JPG, and the user is told (RECORD -1003).
 */

/** One RAW frame and the camera's metadata for it, copied out of the platform's types. A null is a value the camera did not report. */
class RawFrameReadings(
    val width: Int,
    val height: Int,
    /** Row-major, one 16-bit sample per pixel, as the sensor delivered it. */
    val samples: ShortArray,
    /** `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT` (characteristics): 0 RGGB, 1 GRBG, 2 GBRG, 3 BGGR, 4 RGB, 5 mono, 6 NIR. */
    val colorFilterArrangement: Int?,
    /** `SENSOR_BLACK_LEVEL_PATTERN` (characteristics), the 2x2 repeat, row-major. */
    val staticBlackLevel: IntArray?,
    /** `SENSOR_DYNAMIC_BLACK_LEVEL` (capture result), the 2x2 repeat, row-major. Preferred when present. */
    val dynamicBlackLevel: FloatArray?,
    /** `SENSOR_INFO_WHITE_LEVEL` (characteristics). */
    val staticWhiteLevel: Int?,
    /** `SENSOR_DYNAMIC_WHITE_LEVEL` (capture result). Preferred when present and positive. */
    val dynamicWhiteLevel: Int?,
    /** `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE` (characteristics): left, top, right, bottom in pixel-array coordinates. */
    val preCorrectionActiveArray: IntArray?,
    /** `SENSOR_REFERENCE_ILLUMINANT1` and `2` (characteristics), EXIF LightSource codes. */
    val referenceIlluminant1: Int?,
    val referenceIlluminant2: Int?,
    /** `SENSOR_COLOR_TRANSFORM1/2`, `SENSOR_FORWARD_MATRIX1/2`, `SENSOR_CALIBRATION_TRANSFORM1/2` (characteristics), 3x3 row-major. */
    val colorTransform1: List<SRational>?,
    val colorTransform2: List<SRational>?,
    val forwardMatrix1: List<SRational>?,
    val forwardMatrix2: List<SRational>?,
    val calibrationTransform1: List<SRational>?,
    val calibrationTransform2: List<SRational>?,
    /** `SENSOR_NEUTRAL_COLOR_POINT` (capture result). */
    val neutralColorPoint: List<URational>?,
    /** `SENSOR_NOISE_PROFILE` (capture result): an (S, O) pair per CFA channel. */
    val noiseProfile: List<Pair<Double, Double>>?,
    /** `STATISTICS_LENS_SHADING_CORRECTION_MAP` (capture result): columns, rows, and the gains (R, Geven, Godd, B) per point. */
    val lensShadingColumns: Int?,
    val lensShadingRows: Int?,
    val lensShadingGains: FloatArray?,
    /** `CONTROL_POST_RAW_SENSITIVITY_BOOST` (capture result), in ISO units where 100 is no boost. */
    val postRawSensitivityBoost: Int?,
    /** TIFF Orientation, 1 to 8: the same as the shot's JPG (Amendment 1, item 4). */
    val orientation: Int,
)

sealed interface RawFrameMapping {
    /** [notes] names each optional value that was left out, and why, for the log. */
    data class Mapped(val image: DngImage, val notes: List<String>) : RawFrameMapping

    /** A value the file needs was missing or did not make sense; [reason] says which, for the log. */
    data class Unsupported(val reason: String) : RawFrameMapping
}

fun mapRawFrame(r: RawFrameReadings): RawFrameMapping {
    fun unsupported(reason: String) = RawFrameMapping.Unsupported(reason)
    val notes = mutableListOf<String>()

    if (r.width <= 0 || r.height <= 0) return unsupported("the frame's size ${r.width}x${r.height} is not a picture")
    if (r.samples.size.toLong() != r.width.toLong() * r.height) {
        return unsupported("the frame has ${r.samples.size} samples for ${r.width}x${r.height}")
    }
    val cfa = when (r.colorFilterArrangement) {
        0 -> CfaPattern.RGGB
        1 -> CfaPattern.GRBG
        2 -> CfaPattern.GBRG
        3 -> CfaPattern.BGGR
        null -> return unsupported("the camera reported no colour filter arrangement")
        else -> return unsupported("the colour filter arrangement ${r.colorFilterArrangement} is not a Bayer pattern")
    }
    val blackLevel = when {
        r.dynamicBlackLevel != null && r.dynamicBlackLevel.size == 4 -> r.dynamicBlackLevel.toList()
        r.staticBlackLevel != null && r.staticBlackLevel.size == 4 -> {
            if (r.dynamicBlackLevel != null) notes += "dynamic black level had ${r.dynamicBlackLevel.size} values; used the static pattern"
            r.staticBlackLevel.map { it.toFloat() }
        }
        else -> return unsupported("the camera reported no four-value black level")
    }
    val whiteLevel = when {
        r.dynamicWhiteLevel != null && r.dynamicWhiteLevel > 0 -> r.dynamicWhiteLevel
        r.staticWhiteLevel != null && r.staticWhiteLevel > 0 -> r.staticWhiteLevel
        else -> return unsupported("the camera reported no white level")
    }
    val activeArea = r.preCorrectionActiveArray?.let { a ->
        if (a.size != 4) return unsupported("the pre-correction active array has ${a.size} values")
        val width = a[2] - a[0]
        val height = a[3] - a[1]
        // DngCreator's rule: a buffer that is exactly the pre-correction rectangle starts at its origin; otherwise the
        // buffer is the pixel array and the rectangle sits at its own offset.
        val (top, left) = if (r.width == width && r.height == height) 0 to 0 else a[1] to a[0]
        if (top < 0 || left < 0 || width <= 0 || height <= 0 || top + height > r.height || left + width > r.width) {
            return unsupported("the active array ${a.joinToString(",")} does not fit the ${r.width}x${r.height} frame")
        }
        intArrayOf(top, left, top + height, left + width)
    } ?: run {
        notes += "no pre-correction active array; ActiveArea left out"
        null
    }
    val illuminant1 = r.referenceIlluminant1 ?: return unsupported("the camera reported no reference illuminant")
    val colorMatrix1 = r.colorTransform1?.takeIf { it.size == 9 } ?: return unsupported("the camera reported no colour matrix")
    val calibration1 = DngCalibration(
        illuminant = illuminant1,
        colorMatrix = colorMatrix1,
        forwardMatrix = r.forwardMatrix1?.takeIf { it.size == 9 },
        cameraCalibration = r.calibrationTransform1?.takeIf { it.size == 9 },
    )
    // A second illuminant only with its own colour matrix, as DngCreator: one without the other describes nothing.
    val illuminant2 = r.referenceIlluminant2
    val colorMatrix2 = r.colorTransform2?.takeIf { it.size == 9 }
    val calibration2 = if (illuminant2 != null && colorMatrix2 != null) {
        DngCalibration(
            illuminant = illuminant2,
            colorMatrix = colorMatrix2,
            forwardMatrix = r.forwardMatrix2?.takeIf { it.size == 9 },
            cameraCalibration = r.calibrationTransform2?.takeIf { it.size == 9 },
        )
    } else {
        if (illuminant2 != null) notes += "second illuminant without a colour matrix; left out"
        null
    }
    val neutral = r.neutralColorPoint?.takeIf { it.size == 3 }
        ?: return unsupported("the capture result carried no neutral colour point")
    val noiseProfile = r.noiseProfile?.let { pairs ->
        if (pairs.size == 4) pairs.flatMap { (s, o) -> listOf(s, o) } else {
            notes += "noise profile had ${pairs.size} pairs, not 4; left out"
            null
        }
    } ?: run {
        if (r.noiseProfile == null) notes += "no noise profile in the capture result"
        null
    }
    val lensShading = if (r.lensShadingColumns != null && r.lensShadingRows != null && r.lensShadingGains != null) {
        if (r.lensShadingGains.size.toLong() == r.lensShadingColumns.toLong() * r.lensShadingRows * 4) {
            LensShadingMap(columns = r.lensShadingColumns, rows = r.lensShadingRows, gains = r.lensShadingGains)
        } else {
            notes += "lens shading map had ${r.lensShadingGains.size} gains for ${r.lensShadingColumns}x${r.lensShadingRows}; left out"
            null
        }
    } else {
        notes += "no lens shading map in the capture result"
        null
    }
    // DngCreator: post-RAW gain is boost / 100, written in EV with a denominator of 100.
    val baselineExposure = r.postRawSensitivityBoost?.takeIf { it > 0 }?.let { boost ->
        SRational(((ln(boost / 100.0) / ln(2.0)) * 100).roundToInt(), 100)
    }
    if (r.orientation !in 1..8) return unsupported("the orientation ${r.orientation} is not a TIFF orientation")

    // Part B's reading of the contract (Amendment 2, item 4): `cfa` and `blackLevel` describe the 2x2 cell at the
    // top-left of the active area, where the DNG specification starts the pattern. The camera reports both for the cell
    // at the top-left of its buffer, so an active area starting on an odd row or column moves the phase by one.
    val rowShift = (activeArea?.get(0) ?: 0) % 2
    val colShift = (activeArea?.get(1) ?: 0) % 2
    if (rowShift != 0 || colShift != 0) notes += "active area starts at an odd row or column; CFA and black levels re-phased"

    return RawFrameMapping.Mapped(
        DngImage(
            width = r.width,
            height = r.height,
            samples = r.samples,
            cfa = cfa.shifted(rowShift, colShift),
            blackLevel = blackLevel.shifted(rowShift, colShift),
            whiteLevel = whiteLevel,
            activeArea = activeArea,
            calibration1 = calibration1,
            calibration2 = calibration2,
            asShotNeutral = neutral,
            noiseProfile = noiseProfile,
            lensShading = lensShading,
            orientation = r.orientation,
            baselineExposure = baselineExposure,
        ),
        notes,
    )
}

/** The colours of a 2x2 CFA cell, row-major. */
private fun CfaPattern.cell(): String = when (this) {
    CfaPattern.RGGB -> "RGGB"
    CfaPattern.GRBG -> "GRBG"
    CfaPattern.GBRG -> "GBRG"
    CfaPattern.BGGR -> "BGGR"
}

/** The pattern whose cell starts [rowShift] rows and [colShift] columns into this one's. */
private fun CfaPattern.shifted(rowShift: Int, colShift: Int): CfaPattern {
    val cell = cell().toList().shifted(rowShift, colShift).joinToString("")
    return CfaPattern.entries.first { it.cell() == cell }
}

/** A row-major 2x2 cell, read from [rowShift] rows and [colShift] columns in. */
private fun <T> List<T>.shifted(rowShift: Int, colShift: Int): List<T> =
    List(4) { i -> this[((i / 2 + rowShift) % 2) * 2 + (i % 2 + colShift) % 2] }

