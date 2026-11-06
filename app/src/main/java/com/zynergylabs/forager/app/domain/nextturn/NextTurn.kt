package com.zynergylabs.forager.app.domain.nextturn

/**
 * What the navigation card's next-turn space holds (dispatch 2026-11-06-01, Phase 2a; RECORD -2183; Amendment 1). The
 * domain's answer only: the arrow, its words and the figure's text are Phase 2b's, after #498's fixed spaces.
 *
 * The owner, verbatim: "In the degrees and target needle area, have the distance to the next turn be calculated. Count
 * only angled turns like junctions or forks, but not winding paths."
 *
 * Four kinds, each its own type, so "no turn left" can never stand in for "unknown" (CLAUDE.md: an unsupported case is
 * said as such, never a plausible stand-in):
 * - [Turn]: the next angled turn (B1, B2), with its band and side (C1);
 * - [Fork]: a fork the route keeps straight on through, with the side to keep to (B3, C2);
 * - [NoTurnLeft]: every junction left before the target is known and none is a turn, so the straight-ahead arrow shows
 *   (A3, C3), with the top line's own figure (A3: "the distance is the top line's own string");
 * - [Blank]: the space keeps its place, empty (E1, E2), with the reason.
 */
sealed interface NextTurn {
    /**
     * The next turn: [band] and [side] from the route's own change of direction there (C1), [metersAway] along the line
     * from the needle's place, never below 0 (D1). [sameSidePathsBefore] is how many other paths leave on [side] between
     * the walker's place and this turn, at junctions the route passes straight through (the owner's TalkBack ruling, "When
     * paths come first (Recommended)": 0 reads "turn left", 1 "take the second left"). Given for every band; which bands'
     * words use it is the TalkBack layer's (Phase 2b; the planner's reading, plain turns only, is open to the owner).
     */
    data class Turn(val band: TurnBand, val side: TurnSide, val metersAway: Double, val sameSidePathsBefore: Int) : NextTurn

    /** A fork the route keeps straight on through (B3): keep to [keep], [metersAway] from the needle's place (D1). */
    data class Fork(val keep: TurnSide, val metersAway: Double) : NextTurn

    /** No junction left before the target is a turn or a fork, and every one of them is known (A3, Q3). */
    data object NoTurnLeft : NextTurn

    /** Nothing to show, for [reason] (E1, E2). */
    data class Blank(val reason: NextTurnBlank) : NextTurn
}

/** Which way a turn goes, or which side a fork keeps to. */
enum class TurnSide { LEFT, RIGHT }

/**
 * How much a turn bends (C1, from the owner's "slight, square or sharp"): [SLIGHT] below [SLIGHT_BELOW_DEGREES], [PLAIN]
 * from there to below [SHARP_FROM_DEGREES], [SHARP] from there. There is no U-turn band (C1).
 */
enum class TurnBand { SLIGHT, PLAIN, SHARP }

/** Why the next-turn space is blank. Each is its own value, so a log or a test can tell them apart. */
enum class NextTurnBlank {
    /** E1: the top line reads "Arrived". Said by the card ([NextTurnCardState.ARRIVED]). */
    ARRIVED,

    /** E1: the top line reads "—" (a lost fix, or no figure). Said by the card ([NextTurnCardState.NO_DISTANCE]). */
    NO_DISTANCE,

    /** E1: there is no GPS fix. Said by the card ([NextTurnCardState.NO_FIX]). */
    NO_FIX,

    /** E1: the walker is within the fix's own circle of the target. Said by the card ([NextTurnCardState.WITHIN_FIX_CIRCLE]). */
    WITHIN_FIX_CIRCLE,

    /** E1: the route is held back, the walker being off it by more than the far band ([com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress.OffLine]). */
    ROUTE_WITHHELD,

    /** E2 and the dispatch: the way has no trail route (the straight line, the way home along the walker's track). */
    NO_TRAIL_ROUTE,

    /**
     * E2, the planner's reading: on the way home, no turn the data knows is left before the walker's own track to the
     * start, or the walker is past the last joint onto it. The track's own turns are not in any data.
     */
    TRACK_TURNS_UNKNOWN,

    /** The trail route is still being worked out, its junctions are still being read, or the walker is not yet placed on it. */
    NOT_YET_KNOWN,

    /** The route data's ways along the route could not be read, so no junction is known. */
    WAYS_UNREAD,

    /** The next junction ahead could not be matched to the route data's ways, so whether it is a turn is unknown. */
    JUNCTION_UNREAD,
}
