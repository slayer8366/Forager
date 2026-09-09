package com.forager.app.domain

import com.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.PriorityQueue
import java.util.Random
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * [joinedTrackHome] on the pre-build report's synthetic fixtures — path-home join dispatch.
 *
 * **Where every expected value comes from.** The GPX exports are gitignored and were not on this
 * machine, so the fixtures are the report's synthetic ones (`2026-09-08-path-home-ratio-
 * discriminators.py`: the switchback, the 1 km loop, the 3.4 km "genuinely distant" walker D and
 * its return leg), built here point for point with the same formulas, plus a deterministic patch
 * and a hairpin pair. Every expected metre, join count and route join count below was computed
 * **outside the code under test**, by the report script's own `network_home` — its haversine, its
 * O(n²) pair scan, its Dijkstra — ported line for line to a standalone Kotlin script and run on
 * these exact fixtures (the run is quoted in the dispatch's completion report). The values are
 * pinned as literals; nothing here is derived from [joinedTrackHome] itself. The one exception
 * is the grid-versus-naive property test at the end, whose reference is an independent all-pairs
 * implementation in this file.
 *
 * Points are placed by metres east and north of 45° N 122° W through the script's projection
 * (one degree of latitude is π·R/180 = 111 195.08 m on the IUGG radius; longitude scaled by
 * cos 45°). The projection is only how the fixtures are *built*; every distance is geodesic.
 */
class TrackSelfJoinTest {

    // ── The switchback sets ε's floor ───────────────────────────────────────────────────────

    /**
     * Two 220 m legs 10 m apart, then 220 m east: the pre-build report's own hazard case, joined
     * at 12 m and not at 8. The legs are 10 m apart by construction (closest cross-leg pair
     * 9.99966 m), so 10 m is the smallest ε that closes it and 9 m — or 9.99 m — leaves the full
     * 650.28 m retrace. That is why [SELF_JOIN_EPSILON_METERS] is 10.
     */
    @Test
    fun `the switchback closes at 10 m and not below - the floor the constant sits on`() {
        val zig = switchback()
        assertEquals(118, zig.size)

        val open = joinedTrackHome(zig, epsilonMeters = 9.0)
        assertEquals(650.2778, open.meters, 0.001)
        assertEquals(0, open.joinEdgeCount)
        assertEquals(0, open.joinsOnRoute)
        assertEquals(650.2778, joinedTrackHome(zig, epsilonMeters = 9.99).meters, 0.001)

        val closed = joinedTrackHome(zig, epsilonMeters = 10.0)
        assertEquals(232.2782, closed.meters, 0.001)
        assertEquals(38, closed.joinEdgeCount)
        assertEquals(1, closed.joinsOnRoute)

        // And the constant itself is the value the floor was set at.
        assertEquals(10.0, SELF_JOIN_EPSILON_METERS, 0.0)
        assertEquals(232.2782, joinedTrackHome(zig).meters, 0.001)
    }

    /** At 12 m the diagonal (10, 5.5)→(0, 0) opens too and the route cuts 11.4 m of unwalked ground once — the report's 228 m. Pinned so a raised ε is visible as a changed number, not a silent one. */
    @Test
    fun `at 12 m the switchback reads the report's 228 m through more joins`() {
        val closed = joinedTrackHome(switchback(), epsilonMeters = 12.0)

        assertEquals(228.1909, closed.meters, 0.001)
        assertEquals(227, closed.joinEdgeCount)
        assertEquals(20, closed.joinsOnRoute)
    }

    // ── The disqualifying case: the loop refuses the chord ─────────────────────────────────

    /**
     * A 1 km-radius loop walked from a point on its rim (1,143 points, 5.5 m legs). Three-quarters
     * round the walker is 1.41 km from the car across the interior — ground they have not walked.
     * No two non-consecutive points are within ε (the closest, two legs on, are 11.0 m apart), so
     * there is no join and the way home is the full 4,715 m retrace; 77 m from closing it is still
     * the full 6,206 m. Only when the rim is regained — the last point *is* the first — does it
     * read zero. The ratio-threshold switch the report disqualified sent this walker across the
     * 1.4 km chord; this does not, by construction.
     */
    @Test
    fun `the loop reads its full retrace until the rim is regained, never the chord`() {
        val loop = loop()
        assertEquals(1143, loop.size)

        val threeQuarters = joinedTrackHome(loop.take(858))
        assertEquals(4715.0298, threeQuarters.meters, 0.001)
        assertEquals(0, threeQuarters.joinEdgeCount)
        assertTrue("must exceed the 1412 m chord by the whole retrace", threeQuarters.meters > 4700.0)

        val nearlyClosed = joinedTrackHome(loop.take(1129))
        assertEquals(6206.1507, nearlyClosed.meters, 0.001)
        assertEquals(0, nearlyClosed.joinEdgeCount)

        val closed = joinedTrackHome(loop)
        assertEquals(0.0, closed.meters, 1e-6)
        assertEquals(3, closed.joinEdgeCount)
        assertEquals(1, closed.joinsOnRoute)
    }

    // ── The case that must not change: the genuinely distant walker ────────────────────────

    /**
     * D: 3.4 km of wiggly track, 3.0 km straight (546 points, legs 6.23 m with a 2.9 m sideways
     * alternation). Outbound, the way home is the full retrace: 3,396.99 m, no joins — the
     * zig-zag's second-neighbour spacing is 11.0 m, above ε. At 12 m it would have closed at every
     * step and shaved 12 % (2,998 m through 272 joins), which is the other reason ε stops at the
     * floor.
     */
    @Test
    fun `the distant walker's outbound track is the full retrace at 10 m - and would not be at 12`() {
        val d = distantWalker()
        assertEquals(546, d.size)

        val home = joinedTrackHome(d)
        assertEquals(3396.9897, home.meters, 0.001)
        assertEquals(0, home.joinEdgeCount)
        assertEquals(0, home.joinsOnRoute)

        val at12 = joinedTrackHome(d, epsilonMeters = 12.0)
        assertEquals(2998.2327, at12.meters, 0.001)
        assertEquals(544, at12.joinEdgeCount)
        assertEquals(272, at12.joinsOnRoute)
    }

    /**
     * **Monotonicity — the defect being fixed.** The same walker turns round and comes back over
     * their own points. With the plain sum of the legs the estimate reads 6,794 m beside the car
     * (the report's §C.3). Joined, each returning point lands on an outbound point at 0 m and the
     * way home is the outbound ground still ahead: 2,767 m after 101 returning points, 1,521 m
     * after 301, 368 m with 60 to go, one leg with one to go, zero at the car — strictly decreasing
     * at every one of the 545 returning points.
     */
    @Test
    fun `on the return leg the way home decreases at every point and reaches zero at the car`() {
        val d = distantWalker()
        val back = d + d.dropLast(1).reversed()
        assertEquals(1091, back.size)

        fun homeAt(index: Int) = joinedTrackHome(back.take(index + 1))
        assertEquals(3396.990, homeAt(545).meters, 0.001)
        assertEquals(2767.483, homeAt(646).meters, 0.001)
        assertEquals(1, homeAt(646).joinsOnRoute)
        assertEquals(1520.898, homeAt(846).meters, 0.001)
        assertEquals(367.765, homeAt(1031).meters, 0.001)
        assertEquals(6.233, homeAt(1089).meters, 0.001)
        assertEquals(0.0, homeAt(1090).meters, 1e-6)

        var previous = homeAt(545).meters
        for (index in 546 until back.size) {
            val now = homeAt(index).meters
            assertTrue("way home must fall at returning point $index: $previous → $now", now < previous)
            previous = now
        }
    }

    // ── Monotonicity where the return leg is *not* made of the outbound points ─────────────

    /**
     * **The ε neither existing monotonicity test exercises.** The test above builds its return leg
     * from the outbound points verbatim — `d + d.dropLast(1).reversed()` — and so does
     * `PathHomeTest`'s. Exact duplicates join at any ε at all, including zero
     * ([MIN_CELL_METERS]'s own comment says so), so **both of those tests pass at ε = 0** and
     * neither is evidence that ε does anything. They are not wrong and they stay: they bite on the
     * defect the dispatch named. The gap is that they were the only monotonicity coverage, and a
     * real return leg never re-occupies a stored point exactly, in any mode.
     *
     * This fixture is that realistic case: the walker comes back on a leg
     * [RETURN_LATERAL_METERS] m to one side ([returnBeside]), with the returning points **half a
     * spacing out of phase** with the outbound ones — so the nearest stored point is
     * `hypot(lateral, spacing/2)` away, the worst along-track alignment rather than the flattering
     * one. The return leg stops short of the origin by construction; a walker's last fix is not
     * the car, so the way home ends at a few metres, not at zero.
     *
     * **The spacings, and what they stand for.** `HIGH_ACCURACY` gates on 5 s *and* 5 m
     * (`TrackRecordingMode` line 27, enforced by `LocationSampler.isFarEnough`), so realised
     * spacing is `max(5 m, speed × 5 s)`. **That is an inference from the two thresholds, not a
     * measurement** — no track recorded at a known pace was available. On that inference the three
     * fixtured spacings stand for: **5 m**, 1 m/s and below, where the distance floor governs (the
     * one real track available, 733 m over 135 points at ~5.4 m, is slow patch work and sits
     * here); **7 m**, an ordinary walking 1.4 m/s, where the 5 s interval governs; **10 m**, a
     * brisk 2 m/s.
     *
     * **Both sides of ε are pinned, and the pair is the evidence.** At ε = 0 the fixture must not
     * collapse — no pair joins, the way home is the full sum of the legs (checked against
     * [retrace], computed in this file independently of the production scan) and it *rises* at
     * every returning point, the defect reproduced. At ε = [SELF_JOIN_EPSILON_METERS] it must fall
     * at every returning point, through a join. A revert check was deliberately not used to
     * establish this: a parameter the function already exposes is better evidence and carries none
     * of the stale-artifact hazard CLAUDE.md records twice for that tool.
     */
    @Test
    fun `a return leg beside the outbound one falls at every point at 10 m and rises at every point at 0 m`() {
        for ((spacing, pace) in HIGH_ACCURACY_SPACINGS) {
            val track = returnBeside(spacingMeters = spacing, lateralMeters = RETURN_LATERAL_METERS)
            assertEquals("$spacing m ($pace)", 2 * OUTBOUND_POINTS - 1, track.size)

            // ε = 0 — nothing joins, so the way home is the whole walk and only grows.
            assertEquals(
                "at ε = 0 nothing may join at $spacing m spacing ($pace)",
                0, joinedTrackHome(track, epsilonMeters = 0.0).joinEdgeCount,
            )
            assertEquals(
                "at ε = 0 the way home is the full leg sum at $spacing m spacing ($pace)",
                retrace(track), joinedTrackHome(track, epsilonMeters = 0.0).meters, 1e-6,
            )
            var atZero = joinedTrackHome(track.take(OUTBOUND_POINTS), epsilonMeters = 0.0).meters
            for (index in OUTBOUND_POINTS until track.size) {
                val now = joinedTrackHome(track.take(index + 1), epsilonMeters = 0.0).meters
                assertTrue(
                    "at ε = 0 the way home must RISE at returning point $index ($spacing m, $pace): $atZero → $now",
                    now > atZero,
                )
                atZero = now
            }

            // ε = 10 — every returning point joins the outbound leg beside it and the way home falls.
            var previous = joinedTrackHome(track.take(OUTBOUND_POINTS)).meters
            for (index in OUTBOUND_POINTS until track.size) {
                val now = joinedTrackHome(track.take(index + 1))
                assertTrue(
                    "at ε = 10 the way home must FALL at returning point $index ($spacing m, $pace): $previous → ${now.meters}",
                    now.meters < previous,
                )
                assertTrue(
                    "the route must cross a join at returning point $index ($spacing m, $pace)",
                    now.joinsOnRoute > 0,
                )
                previous = now.meters
            }
        }
    }

    /**
     * **The lateral-offset measurement — printed for the completion report, not asserted into
     * silence.** How far to one side the return leg can drift before the join stops firing at
     * ε = 10 m, per spacing. This is the number the deferred point-to-segment question turns on; it
     * replaces an inference from two enum thresholds with a measurement, and a test that merely
     * went green would have consumed it rather than reported it.
     *
     * **Three criteria, because they give three different numbers and only the strictest is the
     * safety-relevant one.** Per spacing and phasing, the largest lateral offset (to 5 mm, by
     * bisection) at which:
     * - `join` — every returning point still has *some* non-consecutive outbound point within ε.
     *   This is the literal reading of "the join stops firing", and it is the loosest: it is the
     *   nearest-stored-point bound, `hypot(lateral, spacing/2) <= ε`. Measured against this file's
     *   own [haversine], independently of the production scan.
     * - `after` — the way home falls at every returning point *after* the first: the steady state,
     *   once the walker is properly under way back.
     * - `turn` — the way home falls at every returning point *including the first*: the turnaround,
     *   the moment the walker turns round and the number must start coming down.
     *
     * Two phasings, because the answer depends on where the returning fixes land between the
     * outbound ones: **worst**, half a spacing out of phase, nearest stored point
     * `hypot(lateral, spacing/2)` away; **best**, in phase, nearest stored point exactly `lateral`
     * away. Real walking falls between them, and a safety input should be read against the worst
     * column.
     *
     * **`turn` is `none` for the whole best-phased column, and that is geometry rather than a
     * defect.** In phase, the first returning fix sits at the *same along-track position* as the
     * last outbound one, so the walker has made no progress home and no offset — not even zero —
     * can make the estimate fall there. Reported as an explicit "none" rather than a number.
     *
     * **One `none` in the table is a knife-edge, not an absence.** Best-phased `join` at 10 m
     * spacing is `sqrt(ε² − spacing²) = 0` exactly: in phase at that spacing the nearest
     * non-consecutive outbound point is one full 10 m spacing back, which equals ε, so whether it
     * joins at zero offset is decided by the last bit of the haversine. It reads `none` here. The
     * other `none`s — best-phased `turn` — are the geometric fact above and are absences.
     *
     * Located by bisection, which assumes each property is monotone in the offset — it is, by
     * construction: the fixture's join distances rise strictly with the offset and nothing else
     * about the track changes. Both ends of every bracket are confirmed, so each printed number is
     * a checked value and not just the last thing a loop happened to hold.
     */
    @Test
    fun `measured lateral offset at which the join stops firing at 10 m - printed for the report`() {
        for ((spacing, pace) in HIGH_ACCURACY_SPACINGS) {
            for ((phasing, halfPhase) in listOf("worst" to true, "best " to false)) {
                val join = largestHoldingOffset(spacing, halfPhase, ::everyReturningPointHasAJoin)
                val after = largestHoldingOffset(spacing, halfPhase, ::fallsAfterTheTurnaround)
                val turn = largestHoldingOffset(spacing, halfPhase, ::fallsAtEveryReturningPoint)
                println(
                    "PATH-HOME-JOIN offset spacing=${"%4.1f".format(spacing)} m phasing=$phasing " +
                        "join<=${format(join)} after<=${format(after)} turn<=${format(turn)} " +
                        "(pace: $pace)",
                )
            }
        }
    }

    /** `none` when the property never held, so an unmeasurable case reads as unsupported rather than as a plausible number. */
    private fun format(offsetMeters: Double?) = offsetMeters?.let { "${"%5.2f".format(it)} m" } ?: " none "

    /**
     * The largest lateral offset at which [holds] still holds for a [returnBeside] track at
     * [spacing], bracketed to 5 mm; `null` when it does not hold even head-on at zero offset. Both
     * ends of the bracket are asserted, so the returned figure is checked rather than assumed.
     */
    private fun largestHoldingOffset(spacing: Double, halfPhase: Boolean, holds: (List<TrackPoint>) -> Boolean): Double? {
        var low = 0.0
        var high = 20.0
        if (!holds(returnBeside(spacing, low, halfPhase))) return null
        assertTrue("$spacing m, halfPhase=$halfPhase: the sweep's far end must fail, or the bracket is meaningless", !holds(returnBeside(spacing, high, halfPhase)))
        while (high - low > 0.005) {
            val mid = (low + high) / 2
            if (holds(returnBeside(spacing, mid, halfPhase))) low = mid else high = mid
        }
        assertTrue("$spacing m, halfPhase=$halfPhase: the bracket's holding end must hold", holds(returnBeside(spacing, low, halfPhase)))
        assertTrue("$spacing m, halfPhase=$halfPhase: the bracket's failing end must fail", !holds(returnBeside(spacing, high, halfPhase)))
        return low
    }

    // ── The patch: what the ruling was asked for ───────────────────────────────────────────

    /**
     * A deterministic stand-in for the real patch tracks (which are gitignored): a boustrophedon
     * over a 48 × 38 m box at 5.5 m legs, then a walk back to 6.4 m from the start — 78 points,
     * 421.6 m walked. Joined at 10 m the way home is the 6.4 m straight line exactly, the same
     * collapse the planner measured on the real tracks (733 → 6.4 m, 92 → 3.0 m); at 4 m this
     * synthetic patch is only partly joined (94.4 m), unlike the real ones, which saturate at 4 —
     * a difference in the fixture's regularity, recorded, not a claim about the real tracks.
     */
    @Test
    fun `the patch collapses to its straight line at 10 m`() {
        val patch = patch()
        assertEquals(78, patch.size)
        assertEquals(421.5990, retrace(patch), 0.001)

        val home = joinedTrackHome(patch)
        assertEquals(6.4000, home.meters, 0.001)
        assertEquals(205, home.joinEdgeCount)
        assertEquals(1, home.joinsOnRoute)

        val at4 = joinedTrackHome(patch, epsilonMeters = 4.0)
        assertEquals(94.4000, at4.meters, 0.001)
        assertEquals(6, at4.joinEdgeCount)
    }

    /** Park, walk 99 m in, work the patch: the report's "patch 100 m from the car" — 520.6 m walked, 105.4 m home along the approach. */
    @Test
    fun `a patch 100 m from the car reads the approach plus the straight line`() {
        val patch = patch()
        val approach = (0 until 19).map { xy(0.0, it * 5.5) }
        val shiftNorth = 18 * 5.5
        val track = approach + patch.drop(1).map { p -> shifted(p, patch.first(), northMeters = shiftNorth) }
        assertEquals(96, track.size)
        assertEquals(520.5935, retrace(track), 0.001)

        val home = joinedTrackHome(track)
        assertEquals(105.4000, home.meters, 0.001)
        assertEquals(206, home.joinEdgeCount)
        assertEquals(1, home.joinsOnRoute)
    }

    // ── The boundary, pinned on both sides ─────────────────────────────────────────────────

    /** Out 104.5 m north and back down a parallel leg: 10.5 m over, no join, the full 215.35 m; 9.5 m over, joined, 9.5 m home. */
    @Test
    fun `parallel legs join at 9 point 5 m apart and not at 10 point 5`() {
        val apart = joinedTrackHome(hairpin(offsetEast = 10.5))
        assertEquals(215.3531, apart.meters, 0.001)
        assertEquals(0, apart.joinEdgeCount)

        val joined = joinedTrackHome(hairpin(offsetEast = 9.5))
        assertEquals(9.5000, joined.meters, 0.001)
        assertEquals(19, joined.joinEdgeCount)
        assertEquals(1, joined.joinsOnRoute)
    }

    @Test
    fun `one point is a zero-length way home, and a negative epsilon is refused`() {
        val single = joinedTrackHome(listOf(xy(0.0, 0.0)))
        assertEquals(0.0, single.meters, 0.0)
        assertEquals(0, single.joinEdgeCount)

        try {
            joinedTrackHome(listOf(xy(0.0, 0.0), xy(0.0, 5.5)), epsilonMeters = -1.0)
            throw AssertionError("a negative ε must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("epsilonMeters"))
        }
        try {
            joinedTrackHome(emptyList())
            throw AssertionError("an empty track must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("at least one point"))
        }
    }

    // ── The grid hash against the all-pairs scan it replaces ───────────────────────────────

    /**
     * The grid finds exactly the pairs the naive O(n²) scan finds and the same shortest distance,
     * on a seeded confined random walk (1,500 points, 5.5 m legs in a 150 m box — dense enough
     * that every cell has neighbours) at four values of ε including ε = 0 (exact duplicates only)
     * and one wider than a cell's worth of points. The reference is [naiveJoinedHome] below,
     * independent of the production scan. Join counts must match exactly; distances to a micron.
     */
    @Test
    fun `the grid scan finds the same joins and distance as the all-pairs scan`() {
        val walk = confinedRandomWalk(points = 1_500, boxMeters = 150.0, seed = 42L)

        for (epsilon in listOf(0.0, 4.0, 10.0, 25.0)) {
            val grid = joinedTrackHome(walk, epsilonMeters = epsilon)
            val (naiveMeters, naiveJoins) = naiveJoinedHome(walk, epsilon)
            assertEquals("joins at ε = $epsilon", naiveJoins, grid.joinEdgeCount)
            assertEquals("metres at ε = $epsilon", naiveMeters, grid.meters, 1e-6)
        }
    }

    /**
     * The measurement the dispatch asked for — printed, never asserted on: a timing assertion is
     * a flaky test, and the figure that matters is the one in the completion report, read from
     * this test's stdout in the JUnit XML. Three tracks: the real patch's size (135 points), a
     * thousand, and the four-hour HIGH_ACCURACY cap of 2,880 points (2,000 of wiggly outbound
     * at 5 m legs, then 880 of patch). Each is checked against the naive scan for equality, so
     * the timing is of a result that is known to be right. **A desktop JVM figure, not a device
     * one** — no device was available to this dispatch.
     */
    @Test
    fun `measured cost at 135, 1000 and 2880 points - printed for the report, equal to the naive scan`() {
        for (n in listOf(135, 1_000, 2_880)) {
            val track = fourHourShapedTrack(n)
            assertEquals(n, track.size)
            repeat(3) { joinedTrackHome(track) } // warm-up
            val gridNanos = LongArray(10) { measureNanos { joinedTrackHome(track) } }.sorted()
            val naiveStart = System.nanoTime()
            val (naiveMeters, naiveJoins) = naiveJoinedHome(track, SELF_JOIN_EPSILON_METERS)
            val naiveNanos = System.nanoTime() - naiveStart
            val result = joinedTrackHome(track)
            assertEquals(naiveJoins, result.joinEdgeCount)
            assertEquals(naiveMeters, result.meters, 1e-6)
            println(
                "PATH-HOME-JOIN cost n=$n joins=${result.joinEdgeCount} home=${"%.1f".format(result.meters)} m " +
                    "grid median=${"%.2f".format(gridNanos[5] / 1e6)} ms min=${"%.2f".format(gridNanos[0] / 1e6)} ms max=${"%.2f".format(gridNanos[9] / 1e6)} ms " +
                    "naive=${"%.1f".format(naiveNanos / 1e6)} ms",
            )
        }
    }

    // ── Fixtures, built with the report script's formulas ──────────────────────────────────

    private fun switchback(): List<TrackPoint> = buildList {
        for (i in 0 until 40) add(xy(0.0, i * 5.5))
        for (i in 1 until 40) add(xy(10.0, 220.0 - i * 5.5))
        for (i in 1 until 40) add(xy(10.0 + i * 5.5, 0.0))
    }

    private fun loop(radius: Double = 1_000.0, leg: Double = 5.5): List<TrackPoint> {
        val n = (2 * PI * radius / leg).toInt()
        return (0..n).map { i -> xy(radius * cos(2 * PI * i / n) - radius, radius * sin(2 * PI * i / n)) }
    }

    private fun distantWalker(meters: Double = 3_400.0, straight: Double = 3_000.0, leg: Double = 5.5): List<TrackPoint> {
        val amplitude = sqrt(maxOf(0.0, (meters / straight) * (meters / straight) - 1))
        val n = (straight / leg).toInt()
        return (0..n).map { i -> xy(amplitude * leg * (i % 2), i * leg) }
    }

    /** Boustrophedon over 48 × 38 m at 5.5 m legs in 8 rows, then straight back toward the start, the last point pulled to 6.4 m from it. */
    private fun patch(): List<TrackPoint> {
        val xy = mutableListOf<Pair<Double, Double>>()
        var direction = 1
        for (row in 0 until 8) {
            val y = row * (38.0 / 7)
            var xs = (0..(48 / 5.5).toInt()).map { it * 5.5 }
            if (direction < 0) xs = xs.reversed()
            for (x in xs) xy += x to y
            direction = -direction
        }
        val (lx, ly) = xy.last()
        val length = hypot(lx, ly)
        val steps = (length / 5.5).toInt()
        for (s in 1..steps) {
            val f = 1 - s * 5.5 / length
            xy += lx * f to ly * f
        }
        val (ex, ey) = xy.last()
        val end = hypot(ex, ey)
        xy[xy.size - 1] = ex * 6.4 / end to ey * 6.4 / end
        return xy.map { (x, y) -> xy(x, y) }
    }

    /**
     * Out due north from the origin at [spacingMeters] for [OUTBOUND_POINTS] points, then back on a
     * leg [lateralMeters] east of it, one point short of the origin. [halfPhase] puts the returning
     * points half a spacing out of phase with the outbound ones — the worst along-track alignment,
     * nearest stored point `hypot(lateral, spacing/2)` away; in phase they are `lateral` away.
     * Unlike [distantWalker]'s reversed tail, no returning point is ever a stored outbound point,
     * so nothing here joins at ε = 0.
     */
    private fun returnBeside(spacingMeters: Double, lateralMeters: Double, halfPhase: Boolean = true): List<TrackPoint> {
        val phase = if (halfPhase) 0.5 else 0.0
        val far = (OUTBOUND_POINTS - 1) * spacingMeters
        val outbound = (0 until OUTBOUND_POINTS).map { xy(0.0, it * spacingMeters) }
        val back = (0 until OUTBOUND_POINTS - 1).map { xy(lateralMeters, far - (it + phase) * spacingMeters) }
        return outbound + back
    }

    /** The way home at ε = [SELF_JOIN_EPSILON_METERS] strictly falls at every returning point of a [returnBeside] track, the turnaround included. */
    private fun fallsAtEveryReturningPoint(track: List<TrackPoint>) = fallsFrom(track, OUTBOUND_POINTS)

    /** As [fallsAtEveryReturningPoint], but with the turnaround point excluded — the steady state of the return leg. */
    private fun fallsAfterTheTurnaround(track: List<TrackPoint>) = fallsFrom(track, OUTBOUND_POINTS + 1)

    private fun fallsFrom(track: List<TrackPoint>, firstIndex: Int): Boolean {
        var previous = joinedTrackHome(track.take(firstIndex)).meters
        for (index in firstIndex until track.size) {
            val now = joinedTrackHome(track.take(index + 1)).meters
            if (now >= previous) return false
            previous = now
        }
        return true
    }

    /**
     * Every returning point has at least one non-consecutive outbound point within ε — "the join
     * fires at all", tested with this file's own [haversine] rather than through the production
     * scan, so the measurement is independent of the code it is measuring.
     */
    private fun everyReturningPointHasAJoin(track: List<TrackPoint>): Boolean =
        (OUTBOUND_POINTS until track.size).all { i ->
            (0 until OUTBOUND_POINTS).any { j -> j < i - 1 && haversine(track[i], track[j]) <= SELF_JOIN_EPSILON_METERS }
        }

    private fun hairpin(offsetEast: Double): List<TrackPoint> = buildList {
        for (i in 0 until 20) add(xy(0.0, i * 5.5))
        for (i in 1 until 20) add(xy(offsetEast, 104.5 - i * 5.5))
    }

    private fun confinedRandomWalk(points: Int, boxMeters: Double, seed: Long, leg: Double = 5.5): List<TrackPoint> {
        val random = Random(seed)
        var x = boxMeters / 2
        var y = boxMeters / 2
        var heading = random.nextDouble() * 2 * PI
        return List(points) {
            heading += random.nextGaussian() * 1.1
            var nx = x + leg * cos(heading)
            var ny = y + leg * sin(heading)
            if (nx < 0 || nx > boxMeters) { heading = PI - heading; nx = nx.coerceIn(0.0, boxMeters) }
            if (ny < 0 || ny > boxMeters) { heading = -heading; ny = ny.coerceIn(0.0, boxMeters) }
            x = nx; y = ny
            xy(x, y)
        }
    }

    /** [n] points: about 70 % wiggly outbound at 5 m legs (2 m sideways alternation), the rest a confined patch at the far end. */
    private fun fourHourShapedTrack(n: Int): List<TrackPoint> {
        val outbound = (n * 7) / 10
        val walk = (0 until outbound).map { i -> xy(2.0 * (i % 2), i * 5.0) }
        val patch = confinedRandomWalk(points = n - outbound, boxMeters = 60.0, seed = 7L)
        val north = (outbound - 1) * 5.0
        return walk + patch.map { p -> shifted(p, patch.first(), northMeters = north) }
    }

    // ── The projection the script builds fixtures with, and its haversine as the reference ─

    private fun xy(eastMeters: Double, northMeters: Double) = TrackPoint(
        lat = LAT0 + northMeters / METERS_PER_DEGREE_LAT,
        lng = LNG0 + eastMeters / METERS_PER_DEGREE_LNG,
        altitude = null,
        accuracyMeters = null,
        timestampEpochMillis = 0L,
    )

    /** [point] moved so that [anchor] lands [northMeters] north of the fixture origin — the script's "shift the patch to the end of the approach". */
    private fun shifted(point: TrackPoint, anchor: TrackPoint, northMeters: Double): TrackPoint {
        val target = xy(0.0, northMeters)
        return point.copy(lat = point.lat + (target.lat - anchor.lat), lng = point.lng + (target.lng - anchor.lng))
    }

    private fun haversine(a: TrackPoint, b: TrackPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val halfDLat = Math.toRadians(b.lat - a.lat) / 2
        val halfDLng = Math.toRadians(b.lng - a.lng) / 2
        val h = sin(halfDLat) * sin(halfDLat) + cos(lat1) * cos(lat2) * sin(halfDLng) * sin(halfDLng)
        return 2 * EARTH_RADIUS * asin(min(1.0, sqrt(h)))
    }

    private fun retrace(points: List<TrackPoint>) = (1 until points.size).sumOf { haversine(points[it - 1], points[it]) }

    /** The report script's `network_home`, as an independent reference: every pair scanned, Dijkstra over the result. Returns metres home and the join count. */
    private fun naiveJoinedHome(points: List<TrackPoint>, epsilon: Double): Pair<Double, Int> {
        val n = points.size
        val adjacency = List(n) { mutableListOf<Pair<Int, Double>>() }
        for (i in 1 until n) {
            val d = haversine(points[i - 1], points[i])
            adjacency[i - 1] += i to d
            adjacency[i] += (i - 1) to d
        }
        var joins = 0
        for (i in 0 until n) {
            for (j in i + 2 until n) {
                val d = haversine(points[i], points[j])
                if (d <= epsilon) {
                    adjacency[i] += j to d
                    adjacency[j] += i to d
                    joins++
                }
            }
        }
        val distance = DoubleArray(n) { Double.POSITIVE_INFINITY }
        distance[n - 1] = 0.0
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        queue += 0.0 to (n - 1)
        while (queue.isNotEmpty()) {
            val (d, u) = queue.poll()
            if (d > distance[u]) continue
            for ((v, w) in adjacency[u]) {
                if (d + w < distance[v]) {
                    distance[v] = d + w
                    queue += distance[v] to v
                }
            }
        }
        return distance[0] to joins
    }

    private inline fun measureNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private companion object {
        /** Points on the outbound leg of a [returnBeside] track; the return leg has one fewer. */
        const val OUTBOUND_POINTS = 60

        /**
         * The lateral offset of the realistic return leg. A walker retracing a path does not
         * re-occupy their own fixes: this is a few metres to one side, of the order of the only
         * GPS accuracy figure this project has (a constant 3.79 m placeholder on the one device
         * measured — see [SELF_JOIN_EPSILON_METERS]'s doc, which is why it is a placeholder and not
         * a measurement of real fix noise).
         */
        const val RETURN_LATERAL_METERS = 3.0

        /** Realised `HIGH_ACCURACY` spacings and the pace each stands for — see the monotonicity test's doc for the inference. */
        val HIGH_ACCURACY_SPACINGS = listOf(
            5.0 to "1 m/s and below, the 5 m distance floor governing",
            7.0 to "1.4 m/s, ordinary walking, the 5 s interval governing",
            10.0 to "2 m/s, brisk",
        )

        const val LAT0 = 45.0
        const val LNG0 = -122.0
        const val EARTH_RADIUS = 6_371_008.8
        val METERS_PER_DEGREE_LAT = PI * EARTH_RADIUS / 180
        val METERS_PER_DEGREE_LNG = METERS_PER_DEGREE_LAT * cos(Math.toRadians(LAT0))
    }
}
