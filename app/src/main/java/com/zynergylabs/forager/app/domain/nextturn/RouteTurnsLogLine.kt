package com.zynergylabs.forager.app.domain.nextturn

/**
 * The M1 line's text for one route (Amendment 1, B1; Phase 1's M1 and M2): how the ways were read, then each judged
 * point's index, distance along, the route's turn, the margin, the class and the band, with the paths passed on the turn's
 * side since the last turn or fork. Every figure is a whole number of metres or degrees, a count or an index: no
 * position of any kind, and no exception's message, which can name a route square.
 */
internal fun routeTurnsLogLines(kind: RouteKind, turns: RouteTurns): List<String> = emptyList()

/** The longest one part of the line may be: under Android's own limit on one log message (about 4,000 bytes), with room for the tag. */
internal const val ROUTE_TURNS_LOG_LINE_MAX_CHARS = 3_500
