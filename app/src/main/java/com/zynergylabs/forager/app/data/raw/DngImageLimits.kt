package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.DngCalibration
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.SRational

/**
 * What the writer refuses before writing a byte (dispatch 2026-10-15-02, "Operating limit"): an image above the
 * operating limit, or any array whose size does not match what it describes. Returns the problem, or null.
 *
 * The limit is 12.6 MP, an explicit operating limit rather than whatever the camera reports (CLAUDE.md, Architecture):
 * the standard 12 MP RAW size only (RECORD -655, -975). Both test phones' RAW sizes, 4000 x 3000 and 4080 x 3060
 * (RECORD -996, -998), are inside it.
 */
internal object DngImageLimits {

    const val MAX_PIXELS = 12_600_000L
    private const val MAX_UINT32 = 0xFFFFFFFFL

    fun problemWith(image: DngImage): String? {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return "dimensions $w x $h are not positive"
        if (w.toLong() * h > MAX_PIXELS) return "image is $w x $h, above the 12.6 MP operating limit"
        if (image.samples.size.toLong() != w.toLong() * h) {
            return "samples holds ${image.samples.size} values, $w x $h needs ${w.toLong() * h}"
        }
        if (image.blackLevel.size != 4) return "black level has ${image.blackLevel.size} values, not 4"
        if (image.blackLevel.any { !it.isFinite() || it < 0f || it > 65535f }) return "black level is outside 0..65535"
        if (image.whiteLevel !in 1..65535) return "white level ${image.whiteLevel} is outside 1..65535"
        image.activeArea?.let { a ->
            if (a.size != 4) return "active area has ${a.size} values, not 4"
            val (top, left, bottom, right) = a
            if (top < 0 || left < 0 || top >= bottom || left >= right || bottom > h || right > w) {
                return "active area $top, $left, $bottom, $right does not fit inside $w x $h"
            }
        }
        calibrationProblem("calibration 1", image.calibration1)?.let { return it }
        image.calibration2?.let { c -> calibrationProblem("calibration 2", c)?.let { return it } }
        if (image.asShotNeutral.size != 3) return "AsShotNeutral has ${image.asShotNeutral.size} values, not 3"
        if (image.asShotNeutral.any { it.numerator !in 0..MAX_UINT32 || it.denominator !in 1..MAX_UINT32 }) {
            return "AsShotNeutral has a value that is not an unsigned rational with a non-zero denominator"
        }
        image.noiseProfile?.let { n ->
            if (n.size != 8) return "NoiseProfile has ${n.size} values, not 8 (an S, O pair per CFA channel)"
            for (i in 0 until 4) {
                val s = n[2 * i]
                val o = n[2 * i + 1]
                // DNG 1.4, NoiseProfile: each S must be positive and each O non-negative.
                if (!s.isFinite() || !o.isFinite() || s <= 0.0 || o < 0.0) {
                    return "NoiseProfile pair $i ($s, $o) breaks the DNG rule S > 0, O >= 0"
                }
            }
        }
        image.lensShading?.let { m ->
            if (m.columns < 1 || m.rows < 1) return "lens shading map is ${m.columns} x ${m.rows}"
            if (m.gains.size.toLong() != m.columns.toLong() * m.rows * 4) {
                return "lens shading map holds ${m.gains.size} gains, ${m.columns} x ${m.rows} x 4 needs ${m.columns.toLong() * m.rows * 4}"
            }
            if (m.gains.any { !it.isFinite() }) return "lens shading map has a gain that is not finite"
        }
        if (image.orientation !in 1..8) return "orientation ${image.orientation} is outside 1..8"
        image.baselineExposure?.let { if (it.denominator == 0) return "BaselineExposure has a zero denominator" }
        return null
    }

    private fun calibrationProblem(name: String, c: DngCalibration): String? {
        if (c.illuminant !in 0..65535) return "$name illuminant ${c.illuminant} is outside 0..65535"
        matrixProblem("$name colour matrix", c.colorMatrix)?.let { return it }
        c.forwardMatrix?.let { m -> matrixProblem("$name forward matrix", m)?.let { return it } }
        c.cameraCalibration?.let { m -> matrixProblem("$name camera calibration", m)?.let { return it } }
        return null
    }

    private fun matrixProblem(name: String, m: List<SRational>): String? {
        if (m.size != 9) return "$name has ${m.size} values, not 9"
        if (m.any { it.denominator == 0 }) return "$name has a zero denominator"
        return null
    }
}
