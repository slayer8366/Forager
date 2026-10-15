package com.zynergylabs.forager.app.diagnostics.burst

/*
 * The probe's per-frame sharpness figure (dispatch 2026-10-15-06): "the mean absolute green gradient on a downsampled green
 * plane". It exists so the planner can see, on real bursts, whether the frames of one burst differ in sharpness, before
 * anyone decides how the engine picks its reference frame (the research audit's D8: the published pipeline picks "the
 * sharpest frame … according to a simple metric based on gradients in the green channel", Hasinoff et al. 2016 §4).
 *
 * It is a comparison between frames of one burst, not an absolute measure. Downsampling first means sensor noise, which
 * averages away over a block, moves it less than scene detail does; how much less on these phones is not measured here.
 */

/** A frame's sharpness figure, or why it has none. */
internal sealed interface Sharpness {
    data class Value(val meanAbsGradient: Double, val gridWidth: Int, val gridHeight: Int) : Sharpness
    data class Unsupported(val reason: String) : Sharpness
}

/**
 * The mean absolute gradient of the green plane, after each 2×2 cell's green (the mean of its two green photosites, each
 * normalised by its own channel's black level, (x − black) / (white − black)) is averaged over blocks of
 * [cellsPerBlock] × [cellsPerBlock] cells. The figure is the mean of |ΔG| over every horizontal and every vertical pair
 * of neighbouring blocks.
 *
 * [samples] is the RAW plane row-major, unsigned 16-bit. [blackLevel] is the camera's four levels in its own 2×2
 * row-major order, as `SENSOR_DYNAMIC_BLACK_LEVEL` and `SENSOR_BLACK_LEVEL_PATTERN` give them. [colorFilterArrangement] is
 * `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`: the four Bayer layouts are read; anything else (RGB, MONO, NIR, or unread) has
 * no Bayer greens and is returned as [Sharpness.Unsupported], never as a figure.
 */
internal fun greenSharpness(
    samples: ShortArray,
    width: Int,
    height: Int,
    colorFilterArrangement: Int?,
    blackLevel: List<Float>,
    whiteLevel: Int,
    cellsPerBlock: Int = SHARPNESS_CELLS_PER_BLOCK,
): Sharpness {
    // The two green sites of a cell, as indices into the 2×2 pattern (row-major: 0 top-left, 1 top-right, 2 bottom-left,
    // 3 bottom-right). The constants are camera2's COLOR_FILTER_ARRANGEMENT values.
    val greens = when (colorFilterArrangement) {
        CFA_RGGB, CFA_BGGR -> intArrayOf(1, 2)
        CFA_GRBG, CFA_GBRG -> intArrayOf(0, 3)
        null -> return Sharpness.Unsupported("the colour filter arrangement was not read")
        else -> return Sharpness.Unsupported("colour filter arrangement $colorFilterArrangement has no Bayer greens")
    }
    if (blackLevel.size != 4) return Sharpness.Unsupported("${blackLevel.size} black levels, not 4")
    require(samples.size >= width * height) { "${samples.size} samples for a ${width}x$height plane" }
    require(cellsPerBlock >= 1) { "cellsPerBlock $cellsPerBlock" }

    val gridWidth = (width / 2) / cellsPerBlock
    val gridHeight = (height / 2) / cellsPerBlock
    if (gridWidth < 1 || gridHeight < 1 || (gridWidth < 2 && gridHeight < 2)) {
        return Sharpness.Unsupported("a ${width}x$height frame in blocks of $cellsPerBlock cells has no neighbouring blocks")
    }
    val ranges = DoubleArray(4) { site -> whiteLevel - blackLevel[site].toDouble() }
    if (greens.any { ranges[it] <= 0.0 }) return Sharpness.Unsupported("white level $whiteLevel is not above the green black levels")

    val siteDx = IntArray(2) { greens[it] % 2 }
    val siteDy = IntArray(2) { greens[it] / 2 }
    val blocks = DoubleArray(gridWidth * gridHeight)
    val cellsInBlock = (cellsPerBlock * cellsPerBlock).toDouble()
    for (by in 0 until gridHeight) {
        for (bx in 0 until gridWidth) {
            var sum = 0.0
            for (cy in by * cellsPerBlock until (by + 1) * cellsPerBlock) {
                for (cx in bx * cellsPerBlock until (bx + 1) * cellsPerBlock) {
                    var cell = 0.0
                    for (g in 0..1) {
                        val x = 2 * cx + siteDx[g]
                        val y = 2 * cy + siteDy[g]
                        val site = greens[g]
                        val raw = samples[y * width + x].toInt() and 0xFFFF
                        cell += (raw - blackLevel[site].toDouble()) / ranges[site]
                    }
                    sum += cell / 2.0
                }
            }
            blocks[by * gridWidth + bx] = sum / cellsInBlock
        }
    }

    var total = 0.0
    var pairs = 0
    for (by in 0 until gridHeight) {
        for (bx in 0 until gridWidth) {
            val here = blocks[by * gridWidth + bx]
            if (bx + 1 < gridWidth) {
                total += kotlin.math.abs(blocks[by * gridWidth + bx + 1] - here)
                pairs++
            }
            if (by + 1 < gridHeight) {
                total += kotlin.math.abs(blocks[(by + 1) * gridWidth + bx] - here)
                pairs++
            }
        }
    }
    return Sharpness.Value(total / pairs, gridWidth, gridHeight)
}

/**
 * Blocks of 4×4 cells, 8×8 photosites: a 4000×3000 frame gives a 500×375 grid. Chosen so the grid still resolves the
 * structure a close-up has (gills, needles) while each block averages 32 green samples. A choice for the probe, not a
 * tuned value; the reference-frame piece decides whether it is the right one.
 */
internal const val SHARPNESS_CELLS_PER_BLOCK = 4

private const val CFA_RGGB = 0
private const val CFA_GRBG = 1
private const val CFA_GBRG = 2
private const val CFA_BGGR = 3
