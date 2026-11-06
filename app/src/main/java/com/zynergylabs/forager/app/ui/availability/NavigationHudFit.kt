package com.zynergylabs.forager.app.ui.availability

/**
 * T11 fixes (dispatch 2026-10-09-02, RECORD -766): what the navigation display drops, and in what order, when its text does
 * not fit. The owner chose "Distance first, short status (Recommended)", option A of P1 and P2 in
 * `docs/navigation/2026-10-09-t11-hud-landscape-check.md`. The order and the short strings below were proposed by the coder and
 * confirmed by the owner (RECORD -768: "Yes, use these (Recommended)"; the evening line and "No route": "Yes (Recommended)").
 * The pure half: every choice here is a function of measured widths,
 * so a wrong order is a pinned-literal test failure (`NavigationHudFitTest`), not a visual one.
 *
 * What is kept longest, first to last:
 * 1. the distance figure, never cut while a shorter form or a lower item can give way;
 * 2. the needle, never removed (it is an icon beside the turn words, and the words give way, not it);
 * 3. the fix-age warning, lost or stale, in its short form if it must ("45 s old", "No fix 6 min");
 * 4. what the figure measures, "by trail" or "straight" (beside the figure, or leading the status line, RECORD -713);
 * 5. the turn words, which shrink to the bearing alone ("169°") and then go;
 * 6. "Approaching";
 * 7. the straight-line note on the way home ("≈ 1250 ft straight", then "≈ 1250 ft").
 *
 * A status line with no empty form among its [NavigationHudReadout.statusForms] (a warning, or a message such as "No
 * location") is never dropped: it is shortened, the "By trail ·" lead goes before it, and only then does "…" cut it.
 */

/** The no-fix message's short form (T11 fixes; [NO_FIX_MESSAGE] is "Location services unavailable"). */
internal const val NO_FIX_SHORT_TEXT = "No location"

/** "No origin waypoint for this track", shortened (T11 fixes). */
internal const val NO_ORIGIN_TEXT = "No origin waypoint for this track"
internal const val NO_ORIGIN_SHORT_TEXT = "No start point"

/** [ROUTE_UNAVAILABLE_TEXT]'s short form, beside "Try again" in landscape when the whole message does not fit (T11 fixes). */
internal const val ROUTE_UNAVAILABLE_SHORT_TEXT = "No route"

/** A stale fix's age, short: "45 s old" for "Last fix 45 s ago" (the owner's chosen option A of P2). */
internal fun staleFixShortText(ageMillis: Long): String = "${formatFixAge(ageMillis)} old"

/** A lost fix's age, short: "No fix 6 min" for "No fix for 6 min". */
internal fun lostFixShortText(ageMillis: Long): String = "No fix ${formatFixAge(ageMillis)}"

/**
 * The bearing alone, "169°", from turn words "sharp right 169°" ([com.zynergylabs.forager.app.domain.turnText]); `null`
 * when [turnText] is not turn words (empty, "Target"). Read from the text rather than stored beside it, so a readout copied
 * with new turn words (the approximate-position display's) shortens the words it actually shows. Dispatch 2026-10-12-06: the
 * words lost their " · " separator, so the degrees are the last word; read before that by the separator, which would have
 * returned `null` for every turn under the new wording and silently skipped the degrees-only form.
 */
internal fun turnShortForm(turnText: String): String? {
    if (!turnText.contains(' ')) return null
    val degrees = turnText.substringAfterLast(' ')
    return degrees.takeIf { it.length > 1 && it.endsWith("°") && it.dropLast(1).all(Char::isDigit) }
}

/** "≤ 16 ft" for "within 16 ft" ([com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy]); `null` for any other figure. */
internal fun distanceShortForm(distanceText: String): String? =
    if (distanceText.startsWith(WITHIN_PREFIX)) "≤ " + distanceText.removePrefix(WITHIN_PREFIX) else null

private const val WITHIN_PREFIX = "within "

/** The first row as drawn: the turn column's text, the large slot's text, and whether "by trail" or "straight" leads the status line. */
internal data class FirstRowFit(val turnText: String, val distanceText: String, val kindInStatus: Boolean)

/**
 * The first row, given [sharedPx]: the width the turn column and the distance column share (the row less the north arrow,
 * the exit, the three-dot button when it is there, and the gaps between them). The turn column is as wide as its text, or
 * [needlePx] (the needle's icon) when the text is narrower.
 *
 * In order: the whole turn and the kind beside the figure; the kind moves to the status line (RECORD -713, nothing lost); the
 * turn words shrink to the bearing; the turn words go; only then the figure's short form, and "…" past that.
 */
internal fun firstRowFit(
    readout: NavigationHudReadout,
    sharedPx: Int,
    needlePx: Int,
    kindGapPx: Int,
    turnWidth: (String) -> Int,
    figureWidth: (String) -> Int,
    kindWidth: (String) -> Int,
    /**
     * The narrowest the status line may be ([statusMinWidth]): a warning or a message ranks above the turn words, so the turn
     * also gives way until the status's shortest form fits under the figure. 0 when the status may be dropped.
     */
    statusMinPx: Int = 0,
): FirstRowFit {
    fun columnPx(turn: String) = sharedPx - maxOf(needlePx, turnWidth(turn))
    val kind = readout.distanceKindText
    val figurePx = figureWidth(readout.distanceText)
    val whole = readout.targetText
    if (kind == null || figurePx + kindGapPx + kindWidth(kind) <= columnPx(whole)) {
        if (figurePx <= columnPx(whole) && statusMinPx <= columnPx(whole)) return FirstRowFit(whole, readout.distanceText, kindInStatus = false)
    }
    val kindInStatus = kind != null
    val turns = listOfNotNull(whole, turnShortForm(whole), "").distinct()
    turns.firstOrNull { figurePx <= columnPx(it) && statusMinPx <= columnPx(it) }?.let { return FirstRowFit(it, readout.distanceText, kindInStatus) }
    turns.firstOrNull { figurePx <= columnPx(it) }?.let { return FirstRowFit(it, readout.distanceText, kindInStatus) }
    return FirstRowFit("", distanceShortForm(readout.distanceText) ?: readout.distanceText, kindInStatus)
}

/** The width the status line needs at the least: its shortest form when it may not be dropped (a warning or a message), else 0. */
internal fun statusMinWidth(readout: NavigationHudReadout, widthOf: (String) -> Int): Int {
    val forms = readout.statusForms()
    return if (forms.contains("")) 0 else widthOf(forms.last())
}

/** The status line as drawn: the kind's lead ("By trail"), or `null`, and the status itself, "" for none. */
internal data class StatusLineFit(val kindLead: String?, val status: String)

/**
 * The status line in [maxPx]. [leadSeparatorPx] is what the " · " between the lead and the status costs. The status's forms
 * ([NavigationHudReadout.statusForms]) are tried longest first beside the lead; a status that may be dropped (it has an
 * empty form) gives way entirely before the lead does; one that may not keeps its shortest form and the lead goes.
 */
internal fun statusLineFit(
    readout: NavigationHudReadout,
    kindInStatus: Boolean,
    maxPx: Int,
    leadSeparatorPx: Int,
    widthOf: (String) -> Int,
): StatusLineFit {
    val lead = if (kindInStatus) readout.distanceKindText?.replaceFirstChar { it.uppercase() } else null
    val forms = readout.statusForms(kindInStatus)
    fun fits(withLead: String?, status: String): Boolean {
        val leadPx = withLead?.let { widthOf(it) + if (status.isEmpty()) 0 else leadSeparatorPx } ?: 0
        val statusPx = if (status.isEmpty()) 0 else widthOf(status)
        return leadPx + statusPx <= maxPx
    }
    forms.firstOrNull { fits(lead, it) }?.let { return StatusLineFit(lead, it) }
    // Droppable (6 and 7 rank below the lead): the lead stays, alone, and is cut with "…" if it must be.
    if (forms.contains("")) return StatusLineFit(lead, "")
    // Not droppable (a warning, or a message): the lead goes before the status is cut.
    forms.firstOrNull { fits(null, it) }?.let { return StatusLineFit(null, it) }
    return StatusLineFit(null, forms.last())
}

/**
 * Whether "Try again" can sit beside the route message in landscape (T11 fixes, the owner's "Combine lines, allow the
 * rest"): it can when [columnPx] holds the button and at least the message's short form in the status line's type. When it
 * cannot (font 2.0 in a narrow window), it keeps its own line under the message, as before.
 */
internal fun retryFitsBeside(columnPx: Int, retryPx: Int, gapPx: Int, shortMessagePx: Int): Boolean = shortMessagePx + gapPx + retryPx <= columnPx

/** The route message in landscape: the whole message in the large type, then in the status type, then "No route" in the status type; "No route" if none fits. */
internal data class RouteMessageFit(val text: String, val inStatusType: Boolean)

internal fun routeMessageFit(availablePx: Int, widthInLargeType: (String) -> Int, widthInStatusType: (String) -> Int): RouteMessageFit {
    val candidates = listOf(
        RouteMessageFit(ROUTE_UNAVAILABLE_TEXT, inStatusType = false),
        RouteMessageFit(ROUTE_UNAVAILABLE_TEXT, inStatusType = true),
        // The short form only in the status line's type: in the large type it made the display 10 dp taller at font 2.0 (first
        // build, 160 to 170 dp), which pushed the icon bar 50 dp under it at 780, past the Fullscreen row the owner accepted.
        RouteMessageFit(ROUTE_UNAVAILABLE_SHORT_TEXT, inStatusType = true),
    )
    return candidates.firstOrNull { (if (it.inStatusType) widthInStatusType(it.text) else widthInLargeType(it.text)) <= availablePx } ?: candidates.last()
}

/**
 * RECORD -770 (the owner: "Drop the evening line then (Recommended)"): whether the evening line may stay on the landscape
 * display. With [limitPx] (the bar's lowest top plus its Fullscreen row, while the L is below the display), the display with
 * the line, the rows' bottom [rowsBottomPx] plus the line and the display's bottom padding, must end within it; without a
 * limit, or before the rows are measured, it always may.
 */
/**
 * RECORD -955 ("C yes"): for the landscape card on the bottom edge (RECORD -953), whether the card, [heightWithStackPx] tall with
 * the evening stack, keeps its top at or below [limitTopPx] from its fixed [bottomPx]. `null` limit, or not yet measured: it fits.
 */
internal fun eveningStackFitsBelow(bottomPx: Float, heightWithStackPx: Float, limitTopPx: Float?): Boolean =
    limitTopPx == null || bottomPx.isNaN() || bottomPx - heightWithStackPx >= limitTopPx - 0.5f

internal fun eveningLineFits(rowsBottomPx: Float, lineHeightPx: Float, bottomPaddingPx: Float, limitPx: Float?): Boolean =
    limitPx == null || rowsBottomPx.isNaN() || rowsBottomPx + lineHeightPx + bottomPaddingPx <= limitPx + 0.5f
