package com.zynergylabs.forager.app.domain.nextturn

/**
 * The arrow the next-turn space shows (dispatch 2026-11-06-01, Amendment 1, C1 to C3, and J, 2b-2 step 1). One value per
 * Material icon, so the drawing is chosen by the UI ([com.zynergylabs.forager.app.ui.availability.nextTurnIcon]) and the
 * choice stays here, free of the UI framework (CLAUDE.md, Architecture).
 *
 * - **C1, from A2's three words** (the owner: "Angled to the turn": "The arrow bends as much as the turn does: slight,
 *   square or sharp, left or right."): a [NextTurn.Turn]'s [TurnBand] and [TurnSide], slight below 45°, plain from 45° to
 *   134°, sharp from 135° ([bandOf]). There is no U-turn arrow.
 * - **C2, from B3** (the owner: "A keep-left/right arrow"): a [NextTurn.Fork]'s side to keep to.
 * - **C3, from A3** (the owner: "A straight-ahead arrow": "An up arrow with the distance to the target."): [NextTurn.NoTurnLeft].
 */
enum class NextTurnArrow {
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    FORK_LEFT,
    FORK_RIGHT,
    STRAIGHT,
}

/** The arrow for [space], or `null` where the space is blank (E1 to E3: it keeps its place, empty). */
fun nextTurnArrow(space: NextTurn): NextTurnArrow? = when (space) {
    is NextTurn.Turn -> turnArrow(space.band, space.side)
    is NextTurn.Fork -> if (space.keep == TurnSide.LEFT) NextTurnArrow.FORK_LEFT else NextTurnArrow.FORK_RIGHT
    NextTurn.NoTurnLeft -> NextTurnArrow.STRAIGHT
    is NextTurn.Blank -> null
}

private fun turnArrow(band: TurnBand, side: TurnSide): NextTurnArrow = when (side) {
    TurnSide.LEFT -> when (band) {
        TurnBand.SLIGHT -> NextTurnArrow.SLIGHT_LEFT
        TurnBand.PLAIN -> NextTurnArrow.LEFT
        TurnBand.SHARP -> NextTurnArrow.SHARP_LEFT
    }
    TurnSide.RIGHT -> when (band) {
        TurnBand.SLIGHT -> NextTurnArrow.SLIGHT_RIGHT
        TurnBand.PLAIN -> NextTurnArrow.RIGHT
        TurnBand.SHARP -> NextTurnArrow.SHARP_RIGHT
    }
}
