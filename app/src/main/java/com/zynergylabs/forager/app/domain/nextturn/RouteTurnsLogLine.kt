package com.zynergylabs.forager.app.domain.nextturn

/**
 * The M1 line's text for one route (Amendment 1, B1; Phase 1's M1 and M2): how the ways were read, then each judged
 * point's index, distance along, the route's turn, the margin, the class and the band, with the paths passed on the turn's
 * side since the last turn or fork and, beside them, the ways there that are not paths (B5). Every figure is a whole number of metres or degrees, a count or an index: no
 * position of any kind, and no exception's message, which can name a route square.
 */
internal fun routeTurnsLogLines(kind: RouteKind, turns: RouteTurns): List<String> {
    val head = "M1 next turns, ${kind.words}"
    val reading = turns.reading
    val read = when (turns) {
        is RouteTurns.Unread -> return listOf("$head: ways unread (${turns.why}) after ${reading.millis} ms (${count(reading.reads, "read")}).")
        is RouteTurns.Read -> turns
    }
    val classes = read.junctions.groupingBy { it.judgement.junctionClass }.eachCount()
    fun n(c: JunctionClass) = classes[c] ?: 0
    val summary = "ways read in ${reading.millis} ms (${count(reading.reads, "read")}, ${count(reading.ways, "way")}); ${read.junctions.size} judged: " +
        "${count(n(JunctionClass.TURN), "turn")}, ${count(n(JunctionClass.FORK), "fork")}, ${n(JunctionClass.STRAIGHT_ON)} straight on, " +
        "${n(JunctionClass.NO_LEG)} no leg, ${n(JunctionClass.UNMATCHED)} unmatched."
    // The paths passed on each side since the last turn or fork, or the last unknown junction: what the count would be
    // as each turn becomes the next one, the walker just past the one before. Beside them, the ways there that are not
    // paths (B5), left out of the count.
    var left = 0
    var right = 0
    var leftNot = 0
    var rightNot = 0
    val entries = read.junctions.mapIndexed { i, junction ->
        val j = junction.judgement
        val where = when (junction.kind) {
            JunctionKind.JUNCTION -> "junction"
            JunctionKind.JOIN_START -> "join (start)"
            JunctionKind.JOIN_END -> "join (end)"
        }
        val angles = "turn ${whole(j.routeTurnDegrees)}°, margin ${whole(j.marginDegrees)}°"
        val body = when (j.junctionClass) {
            JunctionClass.NO_LEG, JunctionClass.SHORT_LEG, JunctionClass.UNMATCHED -> j.junctionClass.name
            JunctionClass.TURN -> {
                val same = if (j.side == TurnSide.LEFT) left else right
                val notCounted = if (j.side == TurnSide.LEFT) leftNot else rightNot
                "$angles, TURN, ${j.band?.name?.lowercase()} ${j.side?.name?.lowercase()}, $same same-side paths before, ${count(notCounted, "same-side way")} not counted"
            }
            JunctionClass.FORK -> "$angles, FORK, keep ${j.keep?.name?.lowercase()}"
            JunctionClass.STRAIGHT_ON -> "$angles, STRAIGHT_ON, paths ${j.leftPaths} left ${j.rightPaths} right, not paths ${j.leftNotPaths} left ${j.rightNotPaths} right"
        }
        when (j.junctionClass) {
            JunctionClass.STRAIGHT_ON -> {
                left += j.leftPaths
                right += j.rightPaths
                leftNot += j.leftNotPaths
                rightNot += j.rightNotPaths
            }
            JunctionClass.TURN, JunctionClass.FORK, JunctionClass.UNMATCHED -> {
                left = 0
                right = 0
                leftNot = 0
                rightNot = 0
            }
            JunctionClass.NO_LEG, JunctionClass.SHORT_LEG -> Unit
        }
        "#${i + 1} node ${junction.node}, ${whole(junction.alongMeters)} m, $where of ${count(junction.ways, "way")}: $body"
    }
    val single = "$head: $summary" + if (entries.isEmpty()) "" else " " + entries.joinToString(" | ")
    if (single.length <= ROUTE_TURNS_LOG_LINE_MAX_CHARS) return listOf(single)
    // Too long for one log message: numbered parts, the summary first, each point whole in one part.
    val budget = ROUTE_TURNS_LOG_LINE_MAX_CHARS - "$head (9999 of 9999): ".length
    val parts = mutableListOf(StringBuilder(summary))
    for (entry in entries) {
        val last = parts.last()
        val separator = if (last.toString() == summary) " " else " | "
        if (last.length + separator.length + entry.length <= budget) last.append(separator).append(entry) else parts += StringBuilder(entry)
    }
    return parts.mapIndexed { i, part -> "$head (${i + 1} of ${parts.size}): $part" }
}

private fun count(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

/** A figure as a whole number: no decimal point anywhere in the line. */
private fun whole(x: Double?): String = x?.let { Math.round(it).toString() } ?: "-"

/** The longest one part of the line may be: under Android's own limit on one log message (about 4,000 bytes), with room for the tag. */
internal const val ROUTE_TURNS_LOG_LINE_MAX_CHARS = 3_500
