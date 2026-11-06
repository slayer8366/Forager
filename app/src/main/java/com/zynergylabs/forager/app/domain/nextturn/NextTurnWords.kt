package com.zynergylabs.forager.app.domain.nextturn

/**
 * What TalkBack says for the next-turn space (dispatch 2026-11-06-01, Amendment 1, G3, and J, 2b-2 step 1), or `null` where
 * the space is blank and says nothing. [spokenDistance] is the space's figure as it is spoken ([spokenFigure]), "400 feet".
 *
 * G3, the owner: "1 turn left / keep straight / keep left / make a sharp right / take the first left"; to the slight turn,
 * "Option 2. It's probably not a good idea to mention "bear" in a forest no matter our intent." (Option 2: "slight left");
 * and to "take the first left", "When paths come first (Recommended)": "If other paths leave on the same side before your
 * turn, it counts them: "take the second left". Otherwise "turn left". The arrow on screen is the same either way."
 *
 * So, each followed by the distance, "in 400 feet":
 * - a plain turn: "turn left", or with same-side paths first ([NextTurn.Turn.sameSidePathsBefore], walkable ways only, B5)
 *   "take the second left", "take the third left";
 * - a sharp turn: "make a sharp left";
 * - a slight turn: "slight left";
 * - a fork: "keep left" (C2's side to keep to);
 * - the straight-ahead arrow: "keep straight".
 *
 * **The count is for plain turns only** (the planner's reading in G3, as the dispatch for 2b-2 restates it): a sharp or
 * slight turn says its band's words whatever paths come first.
 */
fun nextTurnWords(space: NextTurn, spokenDistance: String): String? {
    val words = when (space) {
        is NextTurn.Turn -> turnWords(space)
        is NextTurn.Fork -> "keep ${sideWord(space.keep)}"
        NextTurn.NoTurnLeft -> "keep straight"
        is NextTurn.Blank -> return null
    }
    return "$words in $spokenDistance"
}

private fun turnWords(turn: NextTurn.Turn): String {
    val side = sideWord(turn.side)
    return when (turn.band) {
        TurnBand.PLAIN -> if (turn.sameSidePathsBefore > 0) "take the ${ordinalWord(turn.sameSidePathsBefore + 1)} $side" else "turn $side"
        TurnBand.SHARP -> "make a sharp $side"
        TurnBand.SLIGHT -> "slight $side"
    }
}

private fun sideWord(side: TurnSide): String = if (side == TurnSide.LEFT) "left" else "right"

/** "second" to "tenth" in words; past that, the figure with its suffix ("11th", "21st"), which TalkBack reads as an ordinal. */
internal fun ordinalWord(n: Int): String {
    ORDINAL_WORDS.getOrNull(n - 1)?.let { return it }
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private val ORDINAL_WORDS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")

/**
 * [figure], a distance as the card writes it ([com.zynergylabs.forager.app.domain.model.formatDistanceMeters]: "400 ft",
 * "0.3 mi", "412 m", "1.2 km"), with its unit spelled out as G3's "in 400 feet" spells it: "400 feet", "0.3 miles",
 * "412 meters", "1.2 kilometers"; a figure of exactly 1 takes the singular ("1 foot"). `null` for a figure in any other form,
 * so the caller can say so rather than guess.
 */
fun spokenFigure(figure: String): String? {
    val space = figure.lastIndexOf(' ')
    if (space <= 0) return null
    val number = figure.substring(0, space)
    if (number.toDoubleOrNull() == null) return null
    val (one, many) = UNIT_WORDS[figure.substring(space + 1)] ?: return null
    return "$number ${if (number == "1") one else many}"
}

private val UNIT_WORDS = mapOf(
    "ft" to ("foot" to "feet"),
    "mi" to ("mile" to "miles"),
    "m" to ("meter" to "meters"),
    "km" to ("kilometer" to "kilometers"),
)
