package com.forager.app.ui.motion

/**
 * Precedence and degradation ordering from docs/motion-spec.md §1 and §3. Pure Kotlin, no
 * Android/Compose imports, so the ordering and budget logic are JVM-testable without a device
 * or Robolectric.
 */
object MotionPrecedence {

    /** docs/motion-spec.md §1. Earlier entries win when motion principles conflict. */
    enum class Principle { LEGIBILITY, PERFORMANCE, CALM }

    val PRECEDENCE_ORDER: List<Principle> = listOf(Principle.LEGIBILITY, Principle.PERFORMANCE, Principle.CALM)

    /**
     * docs/motion-spec.md §3. The order continuous animation is stripped away under load or
     * battery-saver. [LOCATION_INDICATOR] is last on purpose — it is the one tier the spec
     * allows to run continuously, and losing it removes the answer to "where am I," a
     * legibility failure rather than a calm one. See docs/adr/0001-motion-precedence.md.
     */
    enum class DegradationTier {
        DECORATIVE_CONTINUOUS_LOOPS,
        ROUTE_PROGRESSIVE_DETAIL,
        MARKER_ENTRANCE_ANIMATIONS,
        LOCATION_INDICATOR,
    }

    /** Degradation order, first-to-be-dropped first. Kotlin enum declaration order enforces this. */
    val DEGRADATION_ORDER: List<DegradationTier> = DegradationTier.entries

    /** docs/motion-spec.md §3: "Maximum 8-12 simultaneously, continuously animated objects." */
    const val MAX_CONTINUOUS_ANIMATED_OBJECTS: Int = 12

    /**
     * Marker count at which clustering engages. PROVISIONAL — see docs/motion-spec.md §6 open
     * question 1 and docs/adr/0001-motion-precedence.md "Provisional constants". Set to the low
     * end of the [MAX_CONTINUOUS_ANIMATED_OBJECTS] budget as a starting point for tuning against
     * dense-map fixtures on target hardware, not a measured value.
     */
    const val MARKER_CLUSTERING_THRESHOLD: Int = 8

    init {
        require(MARKER_CLUSTERING_THRESHOLD <= MAX_CONTINUOUS_ANIMATED_OBJECTS) {
            "MARKER_CLUSTERING_THRESHOLD must engage before MAX_CONTINUOUS_ANIMATED_OBJECTS is reached"
        }
    }

    /** docs/motion-spec.md §3: cluster or stagger once [visibleMarkerCount] reaches the threshold. */
    fun shouldClusterMarkers(visibleMarkerCount: Int): Boolean = visibleMarkerCount >= MARKER_CLUSTERING_THRESHOLD

    /**
     * Which [DegradationTier]s stay animated given [animatedObjectCounts] (the count of
     * continuously-animated objects currently wanting to run in each tier) and whether the
     * device is [underLoad] (sustained low frame rate or battery-saver).
     *
     * Implements docs/motion-spec.md §3's degradation order: [underLoad] drops
     * [DegradationTier.DECORATIVE_CONTINUOUS_LOOPS] outright, regardless of budget. Beyond that,
     * tiers are dropped in [DEGRADATION_ORDER] — cheapest-precedence first — until the total of
     * the remaining tiers' counts fits [MAX_CONTINUOUS_ANIMATED_OBJECTS].
     * [DegradationTier.LOCATION_INDICATOR] is never dropped, matching "reserved for the user's
     * own location indicator."
     */
    fun activeTiers(
        animatedObjectCounts: Map<DegradationTier, Int>,
        underLoad: Boolean,
    ): Set<DegradationTier> {
        var remaining = if (underLoad) {
            DEGRADATION_ORDER.filter { it != DegradationTier.DECORATIVE_CONTINUOUS_LOOPS }
        } else {
            DEGRADATION_ORDER
        }

        while (remaining.size > 1 && remaining.sumOf { animatedObjectCounts[it] ?: 0 } > MAX_CONTINUOUS_ANIMATED_OBJECTS) {
            remaining = remaining.drop(1)
        }

        return remaining.toSet()
    }
}
