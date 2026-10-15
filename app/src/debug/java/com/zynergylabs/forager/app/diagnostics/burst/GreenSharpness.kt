package com.zynergylabs.forager.app.diagnostics.burst

/** A frame's sharpness figure, or why it has none. */
internal sealed interface Sharpness {
    data class Value(val meanAbsGradient: Double, val gridWidth: Int, val gridHeight: Int) : Sharpness
    data class Unsupported(val reason: String) : Sharpness
}

/** STUB (tests-first commit): not built yet. */
internal fun greenSharpness(
    samples: ShortArray,
    width: Int,
    height: Int,
    colorFilterArrangement: Int?,
    blackLevel: List<Float>,
    whiteLevel: Int,
    cellsPerBlock: Int = SHARPNESS_CELLS_PER_BLOCK,
): Sharpness = Sharpness.Value(0.0, 0, 0)

internal const val SHARPNESS_CELLS_PER_BLOCK = 4
